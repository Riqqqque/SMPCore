package me.rique.smpcore.story;

import me.rique.smpcore.SMPCore;
import me.rique.smpcore.database.DatabaseManager;
import me.rique.smpcore.power.SuperpowerType;
import me.rique.smpcore.util.BedrockCompat;
import me.rique.smpcore.util.MenuDupeGuardListener;
import me.rique.smpcore.util.MenuItemUtil;
import me.rique.smpcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class StoryService implements Listener {
    private static final String OATHKEEPER_ID = "corrupted_oathkeeper";
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final Map<String, String> OBJECTIVES = Map.ofEntries(
        Map.entry("PROLOGUE_FIND_MIRA", "Find Mira and listen for the bell beneath Veilward."),
        Map.entry("PROLOGUE_MEET_BAH", "Speak with Mayor Bah about Veilward's defenses."),
        Map.entry("PROLOGUE_MEET_KEEPERS", "Meet Orin, Malakar, and Father Aldren."),
        Map.entry("ACT_1_WITNESSES", "Recover the Marshal and Cindervale memories."),
        Map.entry("ACT_2_WITNESSES", "Learn who the Dominion called monsters."),
        Map.entry("ACT_3_WITNESSES", "Trace mercy and the Veil back to their machinery."),
        Map.entry("ACT_4_WITNESSES", "Recover Morvessa and Noctyr's buried truths."),
        Map.entry("ACT_5_OATHKEEPER", "Enter the final chamber and address Aurel Voss by name."),
        Map.entry("ACT_5_CHOOSE", "Decide what should become of the Oath Engine."),
        Map.entry("EPILOGUE_SPEAK_TO_SILAS", "Ask Silas about the blank card."),
        Map.entry("EPILOGUE", "Follow the second bell beyond what Veilward remembers.")
    );

    private final SMPCore plugin;
    private final Map<UUID, StoryProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();
    private final Map<UUID, Long> queuedSaveRevisions = new ConcurrentHashMap<>();
    private final Map<UUID, List<BukkitTask>> sequences = new HashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final AtomicLong contentLoadGeneration = new AtomicLong();
    private final ExecutorService contentExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SMPCore-Story-Content");
        thread.setDaemon(true);
        return thread;
    });
    private volatile StoryContent content;
    private volatile boolean shuttingDown;

    public StoryService(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload(false);
    }

    public void shutdown() {
        shuttingDown = true;
        contentLoadGeneration.incrementAndGet();
        cancelAllSequences();
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (StoryProfile profile : profiles.values()) saves.add(save(profile));
        try {
            CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().severe("Timed out while saving Eleventh Oath profiles: " + exception.getMessage());
        }
        contentExecutor.shutdownNow();
        profiles.clear();
        loading.clear();
        saveChains.clear();
        queuedSaveRevisions.clear();
    }

    public void reload(boolean notifyStaff) {
        if (shuttingDown) return;
        cancelAllSequences();
        long generation = contentLoadGeneration.incrementAndGet();
        CompletableFuture.supplyAsync(() -> StoryContent.load(plugin), contentExecutor)
            .whenComplete((loaded, failure) -> runSync(() -> {
                if (!isCurrentContentLoad(generation, contentLoadGeneration.get(), shuttingDown)) return;
                if (failure != null || loaded == null) {
                    plugin.getLogger().severe("Could not load The Eleventh Oath content: " + (failure == null ? "unknown error" : failure.getMessage()));
                    return;
                }
                content = loaded;
                plugin.getLogger().info("Loaded The Eleventh Oath: " + loaded.dialogueNodes().size() + " dialogue nodes, "
                    + loaded.entries().size() + " journal entries, and " + loaded.bosses().size() + " bosses.");
                for (Player player : Bukkit.getOnlinePlayers()) loadPlayer(player, true);
                if (notifyStaff) {
                    Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission("smpcore.story.admin"))
                        .forEach(player -> player.sendMessage(MessageUtil.success("Reloaded The Eleventh Oath story files.")));
                }
            }));
    }

    public boolean ready() {
        return content != null && content.enabled() && !shuttingDown;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (content != null) loadPlayer(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unload(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        World.Environment environment = event.getPlayer().getWorld().getEnvironment();
        if (environment != World.Environment.NETHER && environment != World.Environment.THE_END) return;
        String id = environment == World.Environment.NETHER ? "nether" : "end";
        mutate(event.getPlayer().getUniqueId(), profile -> {
            if (!profile.claimEvent("dimension:" + id)) return;
            profile.setFlag("dimension." + id, "unlocked");
            profile.unlockEntry("threads.dimension_seals");
            trigger(event.getPlayer(), "DIMENSION_UNLOCKED", id);
        });
    }

    private void unload(Player player) {
        UUID playerId = player.getUniqueId();
        cancelSequence(playerId);
        StoryProfile profile = profiles.get(playerId);
        if (profile == null) return;
        save(profile).whenComplete((ignored, failure) -> runSync(() -> {
            if (Bukkit.getPlayer(playerId) == null) profiles.remove(playerId, profile);
        }));
    }

    private void loadPlayer(Player player, boolean triggerJoin) {
        UUID playerId = player.getUniqueId();
        if (!loading.add(playerId)) return;
        CompletableFuture<Optional<DatabaseManager.StoryProfileRecord>> stored = plugin.getDatabase().loadStoryProfile(playerId);
        CompletableFuture<Set<String>> history = plugin.getDatabase().loadSuccessfulBossIds(playerId);
        stored.thenCombine(history, LoadedProfile::new).whenComplete((loaded, failure) -> runSync(() -> {
            loading.remove(playerId);
            if (shuttingDown || failure != null || loaded == null) {
                if (failure != null) plugin.getLogger().warning("Could not load story profile for " + playerId + ": " + failure.getMessage());
                return;
            }
            StoryProfile profile = loaded.record().map(this::fromRecord).orElseGet(() -> new StoryProfile(playerId));
            boolean changed = profile.migrate();
            if (!profile.flagBoolean("history_migrated")) {
                for (String bossId : loaded.bossHistory()) changed |= profile.unlockMemory(bossId);
                profile.setFlag("history_migrated", "true");
                changed = true;
            }
            Player current = Bukkit.getPlayer(playerId);
            if (!shouldRetainLoadedProfile(shuttingDown, current != null && current.isOnline())) {
                return;
            }
            profiles.put(playerId, profile);
            if (current != null && plugin.getMayorQuestManager() != null) {
                Set<String> completed = plugin.getMayorQuestManager().completedQuestIds(current);
                for (String questId : completed) profile.setFlag("quest.mayor." + StoryProfile.normalize(questId), "complete");
                if (completed.contains("veil_supplies")) profile.unlockEntry("people.mira");
                if (completed.contains("marshal_proof")) profile.setStage("ACT_1_WITNESSES");
                if (completed.contains("gloam_cinders")) profile.setStage("ACT_2_WITNESSES");
                if (completed.contains("rift_witness")) profile.setStage("ACT_3_WITNESSES");
                if (completed.contains("argent_briar")) profile.setStage("ACT_4_WITNESSES");
                if (completed.contains("oathkeeper_pact") && !profile.bossMemories().contains(OATHKEEPER_ID)) profile.setStage("ACT_5_OATHKEEPER");
                changed |= profile.dirty();
            }
            if (changed || loaded.record().isEmpty()) save(profile);
            if (triggerJoin && current != null && current.isOnline()) trigger(current, "FIRST_STORY_JOIN", "join");
        }));
    }

    private StoryProfile fromRecord(DatabaseManager.StoryProfileRecord record) {
        return new StoryProfile(
            record.playerUuid(), record.storyVersion(), StoryChapter.parse(record.chapter()), record.mainStage(),
            StoryProfile.decodeSet(record.bossMemories()), StoryProfile.decodeSet(record.codexEntries()),
            StoryProfile.decodeSet(record.seenDialogue()), StoryProfile.decodeSet(record.processedEvents()),
            StoryProfile.decodeMap(record.storyFlags()), StoryProfile.decodeLongMap(record.lastDialogue()),
            record.awakeningUses(), record.corruptionUses(), record.corruptionFailures(), StoryAlignment.parse(record.alignment())
        );
    }

    private DatabaseManager.StoryProfileRecord toRecord(StoryProfile.Snapshot profile) {
        return new DatabaseManager.StoryProfileRecord(
            profile.playerId(), profile.storyVersion(), profile.chapter().name(), profile.mainStage(),
            StoryProfile.encodeSet(profile.bossMemories()), StoryProfile.encodeSet(profile.codexEntries()),
            StoryProfile.encodeSet(profile.seenDialogue()), StoryProfile.encodeSet(profile.processedEvents()),
            StoryProfile.encodeMap(profile.flags()), StoryProfile.encodeMap(profile.lastDialogue()), profile.awakeningUses(),
            profile.corruptionUses(), profile.corruptionFailures(), profile.alignment().name(), System.currentTimeMillis()
        );
    }

    private CompletableFuture<Void> save(StoryProfile profile) {
        if (profile == null) return CompletableFuture.completedFuture(null);
        StoryProfile.Snapshot snapshot = profile.snapshot();
        long revision = profile.revision();
        CompletableFuture<Void> queued = saveChains.get(profile.playerId());
        Long queuedRevision = queuedSaveRevisions.get(profile.playerId());
        if (queued != null && queuedRevision != null && queuedRevision >= revision) return queued;
        queuedSaveRevisions.put(profile.playerId(), revision);
        return saveChains.compute(profile.playerId(), (playerId, previous) -> {
            CompletableFuture<Void> start = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((ignored, failure) -> null);
            CompletableFuture<Void> next = start.thenCompose(ignored -> plugin.getDatabase().saveStoryProfile(toRecord(snapshot)));
            next.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    plugin.getLogger().warning("Could not save story profile " + playerId + ": " + failure.getMessage());
                } else {
                    profile.markClean(revision);
                }
                queuedSaveRevisions.remove(playerId, revision);
                saveChains.remove(playerId, next);
            });
            return next;
        });
    }

    public Optional<StoryProfile.Snapshot> profile(UUID playerId) {
        StoryProfile profile = profiles.get(playerId);
        return profile == null ? Optional.empty() : Optional.of(profile.snapshot());
    }

    public void onNpcInteract(Player player, String existingNpcId) {
        if (!ready() || player == null) return;
        String npcId = content.storyNpcId(existingNpcId);
        StoryProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) return;
        long now = System.currentTimeMillis();
        long repeatCooldown = content.npcRepeatCooldownMillis();
        if (repeatCooldown <= 0L || now - profile.lastDialogueAt(npcId) >= repeatCooldown) {
            trigger(player, "NPC_INTERACT", npcId);
        }
        if (npcId.equals("mira") && profile.mainStage().equals("PROLOGUE_FIND_MIRA")) profile.setStage("PROLOGUE_MEET_BAH");
        else if (npcId.equals("mayor_bah") && profile.mainStage().equals("PROLOGUE_MEET_BAH")) profile.setStage("PROLOGUE_MEET_KEEPERS");
        else if (npcId.equals("silas") && profile.chapter() == StoryChapter.EPILOGUE) profile.setStage("EPILOGUE");
        if (profile.dirty()) save(profile);
    }

    public void onQuestStage(Player player, String questSource, String questId, int stage) {
        if (player == null || !ready()) return;
        StoryProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) return;
        String source = StoryProfile.normalize(questSource);
        String id = StoryProfile.normalize(questId);
        String eventKey = "quest:" + source + ":" + id + ":" + stage;
        if (!profile.claimEvent(eventKey)) return;
        profile.setFlag("quest." + source + "." + id, String.valueOf(stage));
        switch (id) {
            case "veil_supplies" -> profile.unlockEntry("people.mira");
            case "marshal_proof" -> profile.setStage("ACT_1_WITNESSES");
            case "gloam_cinders" -> profile.setStage("ACT_2_WITNESSES");
            case "rift_witness" -> profile.setStage("ACT_3_WITNESSES");
            case "argent_briar" -> profile.setStage("ACT_4_WITNESSES");
            case "oathkeeper_pact" -> profile.setStage("ACT_5_OATHKEEPER");
            case "redstone_pulse" -> profile.unlockEntry("threads.redstone_heart");
            case "harvest_moon" -> profile.unlockEntry("threads.memorial_grove");
            case "moonlit_thesis" -> profile.unlockEntry("principles.ethical_stabilization");
            case "authority" -> profile.unlockEntry("principles.veil_authority");
            default -> { }
        }
        save(profile);
        trigger(player, "QUEST_STAGE_CHANGED", source + ":" + id);
    }

    public void onFamiliarUnlocked(Player player, String familiarId) {
        if (player == null || !ready()) return;
        mutate(player.getUniqueId(), profile -> {
            String id = StoryProfile.normalize(familiarId);
            if (!profile.claimEvent("familiar:" + id)) return;
            profile.setFlag("familiar." + id, "true");
            profile.unlockEntry("principles.familiars");
            trigger(player, "FAMILIAR_UNLOCKED", id);
        });
    }

    public void onTavernMilestone(Player player, String milestone) {
        if (player == null || !ready()) return;
        mutate(player.getUniqueId(), profile -> {
            String id = StoryProfile.normalize(milestone);
            if (!profile.claimEvent("tavern:" + id)) return;
            profile.unlockEntry(id.contains("rook") ? "people.rook" : "people.bram");
            trigger(player, "TAVERN_GAME_COMPLETED", id);
        });
    }

    public void onSoulImprintDiscovered(Player player) {
        if (player == null || !ready()) return;
        mutate(player.getUniqueId(), profile -> {
            if (!profile.claimEvent("item:soul_imprint")) return;
            profile.unlockEntry("principles.soul_imprints");
            trigger(player, "SOUL_IMPRINT_DISCOVERED", "soul_imprint");
        });
    }

    public void onItemAwakened(Player player, String eventId) {
        if (player == null || !ready()) return;
        mutate(player.getUniqueId(), profile -> {
            String key = "awakening:" + StoryProfile.normalizeDotted(eventId);
            if (!profile.claimEvent(key)) return;
            boolean first = profile.awakeningUses() == 0;
            profile.recordAwakening();
            if (first) profile.unlockEntry("principles.awakening");
            trigger(player, "ITEM_AWAKENED", first ? "first" : "repeat");
        });
    }

    public void onItemCorruption(Player player, boolean successful, boolean destructiveFailure, String eventId) {
        if (player == null || !ready() || (!successful && !destructiveFailure)) return;
        mutate(player.getUniqueId(), profile -> {
            String key = "corruption:" + StoryProfile.normalizeDotted(eventId);
            if (!profile.claimEvent(key)) return;
            boolean firstSuccess = successful && profile.corruptionUses() == 0;
            boolean firstLoss = destructiveFailure && profile.corruptionFailures() == 0;
            profile.recordCorruption(destructiveFailure);
            if (firstSuccess) profile.unlockEntry("principles.corruption");
            trigger(player, destructiveFailure ? "ITEM_CORRUPTION_FAILED" : "ITEM_CORRUPTED", firstSuccess || firstLoss ? "first" : "repeat");
        });
    }

    private void mutate(UUID playerId, Consumer<StoryProfile> action) {
        StoryProfile profile = profiles.get(playerId);
        if (profile == null) return;
        action.accept(profile);
        if (profile.dirty()) save(profile);
    }

    public String bossEntrance(String bossId, String fallback) { return bossText(bossId).map(StoryContent.BossText::entrance).filter(s -> !s.isBlank()).orElse(fallback); }
    public String bossPhase(String bossId, int phase, String fallback) { return bossText(bossId).map(text -> text.phase(phase)).filter(s -> !s.isBlank()).orElse(fallback); }
    public String bossLowHealth(String bossId) { return bossText(bossId).map(StoryContent.BossText::lowHealth).orElse(""); }
    public String bossDefeat(String bossId, String fallback) { return bossText(bossId).map(StoryContent.BossText::defeat).filter(s -> !s.isBlank()).orElse(fallback); }
    public double bossLowHealthThreshold() { return content == null ? 0.20 : content.lowHealthThreshold(); }

    private Optional<StoryContent.BossText> bossText(String bossId) {
        StoryContent current = content;
        return current == null ? Optional.empty() : Optional.ofNullable(current.bosses().get(StoryProfile.normalize(bossId)));
    }

    public void onBossDefeated(String bossId, UUID encounterId, Set<UUID> participantIds) {
        if (!ready() || bossId == null || participantIds == null || participantIds.isEmpty()) return;
        String id = StoryProfile.normalize(bossId);
        String event = "boss:" + id + ":" + (encounterId == null ? "unknown" : encounterId);
        for (UUID participantId : new LinkedHashSet<>(participantIds)) {
            StoryProfile profile = profiles.get(participantId);
            if (profile != null) {
                awardBossMemory(profile, id, event, Bukkit.getPlayer(participantId));
                continue;
            }
            loadOfflineAndMutate(participantId, loaded -> awardBossMemory(loaded, id, event, Bukkit.getPlayer(participantId)));
        }
    }

    private void awardBossMemory(StoryProfile profile, String bossId, String eventKey, Player player) {
        if (!profile.claimEvent(eventKey)) return;
        boolean first = profile.unlockMemory(bossId);
        if (!first) {
            save(profile);
            return;
        }
        updateObjectiveForMemory(profile);
        if (bossId.equals(OATHKEEPER_ID)) {
            profile.setFlag("final_choice_pending", "true");
            profile.setStage("ACT_5_CHOOSE");
        }
        save(profile);
        if (player == null || !player.isOnline()) return;
        StoryContent.BossText text = bossText(bossId).orElse(null);
        if (text != null) {
            player.sendMessage(MessageUtil.parse("<dark_gray>[<light_purple>Recovered Memory</light_purple>]</dark_gray> <white>" + text.memory() + "</white>"));
            player.showTitle(Title.title(
                MessageUtil.parse("<light_purple>MEMORY RECOVERED</light_purple>"),
                MessageUtil.parse("<gray>" + text.memory() + "</gray>"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))
            ));
        }
        playAmbient(player, bossId);
        if (bossId.equals(OATHKEEPER_ID)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && profiles.get(player.getUniqueId()) != null
                    && profiles.get(player.getUniqueId()).alignment() == StoryAlignment.UNDECIDED) openChoice(player);
            }, 80L);
        }
    }

    private void loadOfflineAndMutate(UUID playerId, Consumer<StoryProfile> action) {
        plugin.getDatabase().loadStoryProfile(playerId).thenCombine(plugin.getDatabase().loadSuccessfulBossIds(playerId), LoadedProfile::new)
            .whenComplete((loaded, failure) -> runSync(() -> {
                if (failure != null || loaded == null || shuttingDown) return;
                StoryProfile profile = loaded.record().map(this::fromRecord).orElseGet(() -> new StoryProfile(playerId));
                for (String boss : loaded.bossHistory()) profile.unlockMemory(boss);
                profile.setFlag("history_migrated", "true");
                action.accept(profile);
            }));
    }

    private void updateObjectiveForMemory(StoryProfile profile) {
        StoryChapter chapter = profile.chapter();
        profile.setStage(switch (chapter) {
            case PROLOGUE, ACT_1 -> "ACT_1_WITNESSES";
            case ACT_2 -> "ACT_2_WITNESSES";
            case ACT_3 -> "ACT_3_WITNESSES";
            case ACT_4 -> "ACT_4_WITNESSES";
            case ACT_5 -> profile.alignment() == StoryAlignment.UNDECIDED && profile.bossMemories().contains(OATHKEEPER_ID)
                ? "ACT_5_CHOOSE" : "ACT_5_OATHKEEPER";
            case EPILOGUE -> "EPILOGUE_SPEAK_TO_SILAS";
        });
    }

    private void playAmbient(Player player, String bossId) {
        StoryContent current = content;
        StoryContent.AmbientEffect effect = current == null ? null : current.ambientEffects().get(bossId);
        if (effect == null) return;
        try {
            Sound sound = configuredSound(effect.sound());
            if (sound != null) player.playSound(player.getLocation(), sound, effect.volume(), effect.pitch());
        } catch (RuntimeException ignored) { }
        try {
            if (!effect.particle().isBlank() && effect.particleCount() > 0) {
                player.spawnParticle(Particle.valueOf(effect.particle().toUpperCase(Locale.ROOT)), player.getLocation().add(0, 1, 0), effect.particleCount(), 1.2, 0.8, 1.2, 0.02);
            }
        } catch (IllegalArgumentException ignored) { }
    }

    public void trigger(Player player, String trigger, String context) {
        StoryContent current = content;
        StoryProfile profile = player == null ? null : profiles.get(player.getUniqueId());
        if (current == null || profile == null || !current.enabled()) return;
        DialogueEngine.select(current.dialogueNodes(), profile.snapshot(), trigger, context, System.currentTimeMillis())
            .ifPresent(node -> playNode(player, profile, node, deliveryMode(current.dialogueMode(), trigger)));
    }

    public boolean replay(Player player, String nodeId) {
        StoryContent current = content;
        StoryProfile profile = player == null ? null : profiles.get(player.getUniqueId());
        if (current == null || profile == null) return false;
        Optional<DialogueNode> node = current.dialogueNodes().stream().filter(candidate -> candidate.id().equalsIgnoreCase(nodeId)).findFirst();
        if (node.isEmpty()) return false;
        profile.forgetDialogue(node.get().id());
        playNode(player, profile, node.get(), deliveryMode(current.dialogueMode(), "REPLAY"));
        return true;
    }

    static String deliveryMode(String configuredMode, String trigger) {
        if (trigger != null && trigger.equalsIgnoreCase("NPC_INTERACT")) return "chat-only";
        return configuredMode == null || configuredMode.isBlank()
            ? "chat-only"
            : configuredMode.trim().toLowerCase(Locale.ROOT);
    }

    private void playNode(Player player, StoryProfile profile, DialogueNode node, String deliveryMode) {
        cancelSequence(player.getUniqueId());
        if (node.once()) profile.markDialogueSeen(node.id());
        long now = System.currentTimeMillis();
        profile.markDialogueTime(node.speaker(), now);
        if (node.context() != null && !node.context().isBlank()
            && !StoryProfile.normalize(node.context()).equals(StoryProfile.normalize(node.speaker()))) {
            profile.markDialogueTime(node.context(), now);
        }
        for (String action : node.actions()) executeAction(profile, action);
        save(profile);

        List<BukkitTask> tasks = new ArrayList<>();
        long finalDelay = 0L;
        for (DialogueNode.Line line : node.lines()) {
            finalDelay = Math.max(finalDelay, line.delayTicks());
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || sequences.get(player.getUniqueId()) != tasks) return;
                String text = replace(line.text(), player, profile);
                if (deliveryMode.equals("chat") || deliveryMode.equals("chat-only") || deliveryMode.equals("both")) {
                    player.sendMessage(MessageUtil.parse("<dark_gray>[<light_purple>" + node.speaker() + "</light_purple>]</dark_gray> " + text));
                }
                if (deliveryMode.equals("subtitles") || deliveryMode.equals("subtitles-only") || deliveryMode.equals("both")) {
                    player.sendActionBar(MessageUtil.parse("<light_purple>" + node.speaker() + "</light_purple><dark_gray>:</dark_gray> " + text));
                }
                if (!line.sound().isBlank()) {
                    Sound sound = configuredSound(line.sound());
                    if (sound != null) player.playSound(player.getLocation(), sound, 0.65f, 0.9f);
                }
            }, line.delayTicks()));
        }
        sequences.put(player.getUniqueId(), tasks);
        long cleanupDelay = finalDelay + 1L;
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin,
            () -> sequences.remove(player.getUniqueId(), tasks), cleanupDelay));
    }

    private String replace(String text, Player player, StoryProfile profile) {
        return text.replace("{player}", player.getName()).replace("{objective}", objective(profile));
    }

    private void executeAction(StoryProfile profile, String rawAction) {
        if (rawAction == null || rawAction.isBlank()) return;
        String[] parts = rawAction.split(":", 3);
        String action = parts[0].trim().toLowerCase(Locale.ROOT);
        switch (action) {
            case "setflag" -> { if (parts.length >= 3) profile.setFlag(parts[1], parts[2]); }
            case "setstage" -> { if (parts.length >= 2) profile.setStage(parts[1]); }
            case "setchapter" -> { if (parts.length >= 2) profile.setChapter(StoryChapter.parse(parts[1])); }
            case "unlockcodex" -> { if (parts.length >= 2) profile.unlockEntry(parts[1]); }
            default -> plugin.getLogger().warning("Unknown story action in story-dialogue.yml: " + rawAction);
        }
    }

    public void skip(Player player) {
        if (player == null) return;
        if (cancelSequence(player.getUniqueId())) player.sendMessage(MessageUtil.info("Skipped your current story dialogue."));
        else player.sendMessage(MessageUtil.info("You do not have an active story dialogue."));
    }

    private boolean cancelSequence(UUID playerId) {
        List<BukkitTask> tasks = sequences.remove(playerId);
        if (tasks == null) return false;
        tasks.forEach(BukkitTask::cancel);
        return true;
    }

    private void cancelAllSequences() {
        new HashSet<>(sequences.keySet()).forEach(this::cancelSequence);
    }

    public String objective(UUID playerId) {
        StoryProfile profile = profiles.get(playerId);
        return profile == null ? "Your story is still loading." : objective(profile);
    }

    private String objective(StoryProfile profile) {
        return OBJECTIVES.getOrDefault(profile.mainStage(), "Review the recovered truths in /veil.");
    }

    public void sendObjective(Player player) {
        player.sendMessage(MessageUtil.info("Current objective: <white>" + objective(player.getUniqueId()) + "</white>"));
    }

    public void sendMemories(Player player) {
        StoryProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) { player.sendMessage(MessageUtil.warn("Your story is still loading.")); return; }
        player.sendMessage(MessageUtil.parse("<light_purple><bold>Recovered Memories</bold></light_purple>"));
        StoryContent current = content;
        if (current == null) return;
        current.bosses().values().stream().sorted(Comparator.comparingInt(text -> bossOrder(text.id()))).forEach(text -> {
            boolean unlocked = profile.bossMemories().contains(text.id());
            player.sendMessage(MessageUtil.parse(unlocked
                ? "<gray>• <white>" + text.display() + "</white> — " + text.memory() + "</gray>"
                : "<dark_gray>• ??? — Memory not recovered</dark_gray>"));
        });
    }

    public void sendTextJournal(Player player) {
        StoryProfile profile = profiles.get(player.getUniqueId());
        StoryContent current = content;
        if (profile == null || current == null) { player.sendMessage(MessageUtil.warn("Your story is still loading.")); return; }
        player.sendMessage(MessageUtil.parse("<light_purple><bold>The Eleventh Oath</bold></light_purple>"));
        player.sendMessage(MessageUtil.info("Chapter: <white>" + readable(profile.chapter().name()) + "</white>"));
        player.sendMessage(MessageUtil.info("Objective: <white>" + objective(profile) + "</white>"));
        player.sendMessage(MessageUtil.info("Veilmark: <white>" + veilmarkName(player) + "</white> · Familiars: <white>" + familiarNames(player) + "</white>"));
        player.sendMessage(MessageUtil.info("Memories: <white>" + profile.bossMemories().size() + "/10</white> · Alignment: <white>" + readable(profile.alignment().name()) + "</white>"));
        if (profile.flagBoolean("final_choice_pending") && profile.alignment() == StoryAlignment.UNDECIDED) {
            player.sendMessage(MessageUtil.warn("The Engine is waiting. Use <white>/veil choose mend|bind|sever</white>."));
        }
        player.sendMessage(MessageUtil.info("Use <white>/veil memories</white> for recovered witness statements."));
    }

    public void openJournal(Player player) {
        StoryProfile profile = profiles.get(player.getUniqueId());
        StoryContent current = content;
        if (profile == null || current == null) { player.sendMessage(MessageUtil.warn("Your story is still loading.")); return; }
        Inventory inventory = Bukkit.createInventory(new JournalHolder(player.getUniqueId(), "", 0), 54,
            BedrockCompat.menuTitle(player, MessageUtil.parse("<dark_purple>The Eleventh Oath</dark_purple>"), "The Eleventh Oath"));
        fill(inventory);
        List<StoryContent.CodexCategory> categories = current.categories().values().stream().sorted(Comparator.comparingInt(StoryContent.CodexCategory::order)).toList();
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        for (int i = 0; i < Math.min(slots.length, categories.size()); i++) {
            StoryContent.CodexCategory category = categories.get(i);
            long unlocked = current.entries().values().stream().filter(entry -> entry.category().equals(category.id()) && profile.codexEntries().contains(entry.id())).count();
            long total = current.entries().values().stream().filter(entry -> entry.category().equals(category.id())).count();
            List<String> categoryLore = new ArrayList<>(List.of("<gray>" + category.description() + "</gray>", "", "<white>" + unlocked + "/" + total + " unlocked</white>"));
            if (category.id().equals("veilmark")) {
                categoryLore.add("<gray>Class: <white>" + veilmarkName(player) + "</white></gray>");
                categoryLore.add("<gray>Alignment: <white>" + readable(profile.alignment().name()) + "</white></gray>");
            }
            categoryLore.add("<yellow>" + BedrockCompat.menuActionWord(player) + " to open</yellow>");
            inventory.setItem(slots[i], item(category.icon(), "<light_purple>" + category.name() + "</light_purple>",
                categoryLore));
        }
        inventory.setItem(22, item(Material.COMPASS, "<gold>Current Objective</gold>", List.of("<white>" + objective(profile) + "</white>", "", "<gray>Chapter: " + readable(profile.chapter().name()) + "</gray>")));
        inventory.setItem(49, item(Material.WRITABLE_BOOK, "<aqua>Text View</aqua>", List.of("<gray>Simple chat fallback for Bedrock.</gray>")));
        if (profile.flagBoolean("final_choice_pending") && profile.alignment() == StoryAlignment.UNDECIDED) {
            inventory.setItem(53, item(Material.RECOVERY_COMPASS, "<red>The Engine Waits</red>", List.of("<gray>Choose what becomes of the Veil.</gray>")));
        }
        player.openInventory(inventory);
    }

    private void openCategory(Player player, String categoryId, int page) {
        StoryContent current = content;
        StoryProfile profile = profiles.get(player.getUniqueId());
        StoryContent.CodexCategory category = current == null ? null : current.categories().get(categoryId);
        if (current == null || profile == null || category == null) return;
        List<StoryContent.CodexEntry> entries = current.entries().values().stream().filter(entry -> entry.category().equals(categoryId))
            .sorted(Comparator.comparingInt(StoryContent.CodexEntry::order).thenComparing(StoryContent.CodexEntry::id)).toList();
        int maxPage = Math.max(0, (entries.size() - 1) / CONTENT_SLOTS.length);
        int safePage = Math.clamp(page, 0, maxPage);
        Inventory inventory = Bukkit.createInventory(new JournalHolder(player.getUniqueId(), categoryId, safePage), 54,
            BedrockCompat.menuTitle(player, MessageUtil.parse("<dark_purple>" + category.name() + "</dark_purple>"), category.name()));
        fill(inventory);
        int start = safePage * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length && start + i < entries.size(); i++) {
            StoryContent.CodexEntry entry = entries.get(start + i);
            boolean unlocked = profile.codexEntries().contains(entry.id());
            inventory.setItem(CONTENT_SLOTS[i], unlocked
                ? item(entry.icon(), "<light_purple>" + entry.name() + "</light_purple>", trim(entry.text(), 5))
                : item(Material.GRAY_DYE, "<dark_gray>???</dark_gray>", List.of("<dark_gray>This entry has not been recovered.</dark_gray>")));
        }
        inventory.setItem(45, item(Material.ARROW, "<yellow>Back</yellow>", List.of("<gray>Return to the journal.</gray>")));
        if (safePage > 0) inventory.setItem(48, item(Material.SPECTRAL_ARROW, "<yellow>Previous Page</yellow>", List.of()));
        inventory.setItem(49, item(Material.PAPER, "<white>Page " + (safePage + 1) + "/" + (maxPage + 1) + "</white>", List.of()));
        if (safePage < maxPage) inventory.setItem(50, item(Material.SPECTRAL_ARROW, "<yellow>Next Page</yellow>", List.of()));
        player.openInventory(inventory);
    }

    public void openChoice(Player player) {
        StoryProfile profile = profiles.get(player.getUniqueId());
        if (profile == null || profile.alignment() != StoryAlignment.UNDECIDED || !profile.bossMemories().contains(OATHKEEPER_ID)) {
            player.sendMessage(MessageUtil.warn("The Engine is not waiting for a choice from you."));
            return;
        }
        Inventory inventory = Bukkit.createInventory(new ChoiceHolder(player.getUniqueId()), 27,
            BedrockCompat.menuTitle(player, MessageUtil.parse("<dark_purple>The Engine Waits</dark_purple>"), "Choose the Oath"));
        fill(inventory);
        inventory.setItem(4, item(Material.ECHO_SHARD, "<light_purple>The Engine waits...</light_purple>",
            List.of("<gray>It asks for an instruction</gray>", "<gray>it no longer has the right to demand.</gray>")));
        inventory.setItem(11, item(Material.AMETHYST_SHARD, "<green>Mend</green>", List.of("<gray>Preserve the Veil.</gray>", "<gray>Release the Imprints slowly.</gray>")));
        inventory.setItem(13, item(Material.IRON_BARS, "<gold>Bind</gold>", List.of("<gray>Take stewardship.</gray>", "<gray>Build a replacement first.</gray>")));
        inventory.setItem(15, item(Material.NETHERITE_SCRAP, "<red>Sever</red>", List.of("<gray>Break the Engine.</gray>", "<gray>Release the stolen dead now.</gray>")));
        player.openInventory(inventory);
        long timeoutTicks = Math.max(20L, content.choiceTimeoutMillis() / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !(player.getOpenInventory().getTopInventory().getHolder(false) instanceof ChoiceHolder holder)
                || !holder.playerId().equals(player.getUniqueId())) return;
            player.closeInventory();
            player.sendMessage(MessageUtil.info("The choice remains unresolved. Reopen it with <white>/veil</white> or use <white>/veil choose</white>."));
        }, timeoutTicks);
    }

    public boolean choose(Player player, StoryAlignment choice) {
        StoryProfile profile = profiles.get(player.getUniqueId());
        if (profile == null || !profile.bossMemories().contains(OATHKEEPER_ID) || !profile.chooseAlignment(choice)) return false;
        save(profile);
        player.closeInventory();
        String response = switch (choice) {
            case MEND -> "Safety bought with delay. Mercy bought with continued machinery.";
            case BIND -> "Responsibility accepted. Authority remains dangerous, even in careful hands.";
            case SEVER -> "Freedom without a map. The world will learn what the Veil was holding.";
            case UNDECIDED -> "";
        };
        player.sendMessage(MessageUtil.parse("<dark_gray>[<light_purple>The Engine</light_purple>]</dark_gray> <white>" + response + "</white>"));
        player.showTitle(Title.title(MessageUtil.parse("<gold><bold>THE ELEVENTH OATH</bold></gold>"),
            MessageUtil.parse("<white>What was erased will have a witness.</white>"),
            Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(MessageUtil.parse("<light_purple><italic>What is remembered will not be owned.</italic></light_purple>"));
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 0.55f);
        }, 45L);
        trigger(player, "ALIGNMENT_CHOSEN", choice.name().toLowerCase(Locale.ROOT));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder rawHolder = top.getHolder(false);
        if (!(rawHolder instanceof JournalHolder) && !(rawHolder instanceof ChoiceHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize() || !MenuItemUtil.isVisibleItem(event.getCurrentItem())) return;
        if (rawHolder instanceof ChoiceHolder holder) {
            if (!holder.playerId().equals(player.getUniqueId())) return;
            StoryAlignment choice = switch (slot) { case 11 -> StoryAlignment.MEND; case 13 -> StoryAlignment.BIND; case 15 -> StoryAlignment.SEVER; default -> StoryAlignment.UNDECIDED; };
            if (choice != StoryAlignment.UNDECIDED && !choose(player, choice)) player.sendMessage(MessageUtil.warn("That choice is no longer available."));
            return;
        }
        JournalHolder holder = (JournalHolder) rawHolder;
        if (!holder.playerId().equals(player.getUniqueId())) return;
        if (holder.categoryId().isBlank()) {
            if (slot == 49) { player.closeInventory(); sendTextJournal(player); return; }
            if (slot == 53) { openChoice(player); return; }
            List<StoryContent.CodexCategory> categories = content.categories().values().stream().sorted(Comparator.comparingInt(StoryContent.CodexCategory::order)).toList();
            int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
            for (int i = 0; i < Math.min(slots.length, categories.size()); i++) if (slot == slots[i]) openCategory(player, categories.get(i).id(), 0);
            return;
        }
        if (slot == 45) { openJournal(player); return; }
        if (slot == 48) { openCategory(player, holder.categoryId(), holder.page() - 1); return; }
        if (slot == 50) { openCategory(player, holder.categoryId(), holder.page() + 1); return; }
        int index = indexOf(CONTENT_SLOTS, slot);
        if (index < 0) return;
        List<StoryContent.CodexEntry> entries = content.entries().values().stream().filter(entry -> entry.category().equals(holder.categoryId()))
            .sorted(Comparator.comparingInt(StoryContent.CodexEntry::order).thenComparing(StoryContent.CodexEntry::id)).toList();
        int absolute = holder.page() * CONTENT_SLOTS.length + index;
        StoryProfile profile = profiles.get(player.getUniqueId());
        if (profile == null || absolute >= entries.size() || !profile.codexEntries().contains(entries.get(absolute).id())) return;
        player.closeInventory();
        StoryContent.CodexEntry entry = entries.get(absolute);
        player.sendMessage(MessageUtil.parse("<light_purple><bold>" + entry.name() + "</bold></light_purple>"));
        entry.text().forEach(line -> player.sendMessage(MessageUtil.parse("<gray>" + line + "</gray>")));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof JournalHolder || holder instanceof ChoiceHolder) event.setCancelled(true);
    }

    public String status(Player target) {
        StoryProfile profile = profiles.get(target.getUniqueId());
        if (profile == null) return "Story profile is still loading.";
        return "chapter=" + profile.chapter() + ", stage=" + profile.mainStage() + ", memories=" + profile.bossMemories().size()
            + "/10, entries=" + profile.codexEntries().size() + ", awakening=" + profile.awakeningUses() + ", corruption="
            + profile.corruptionUses() + ", failures=" + profile.corruptionFailures() + ", alignment=" + profile.alignment();
    }

    public boolean adminSetChapter(Player target, StoryChapter chapter) { return adminMutate(target, profile -> profile.setChapter(chapter)); }
    public boolean adminSetStage(Player target, String stage) { return adminMutate(target, profile -> profile.setStage(stage)); }
    public boolean adminSetFlag(Player target, String key, String value) { return adminMutate(target, profile -> profile.setFlag(key, value)); }
    public boolean adminUnlock(Player target, String entry) { return adminMutate(target, profile -> profile.unlockEntry(entry)); }
    public boolean adminLock(Player target, String entry) { return adminMutate(target, profile -> profile.lockEntry(entry)); }
    public boolean adminDebug(Player target, boolean enabled) { return adminMutate(target, profile -> profile.setFlag("debug", String.valueOf(enabled))); }
    public boolean adminReset(Player target, boolean keepBossHistory) { return adminMutate(target, profile -> profile.reset(keepBossHistory)); }

    public void adminMigrate(Player target) {
        StoryProfile profile = profiles.get(target.getUniqueId());
        if (profile == null) return;
        plugin.getDatabase().loadSuccessfulBossIds(target.getUniqueId()).whenComplete((bosses, failure) -> runSync(() -> {
            if (failure != null) return;
            bosses.forEach(profile::unlockMemory);
            profile.setFlag("history_migrated", "true");
            updateObjectiveForMemory(profile);
            save(profile);
            target.sendMessage(MessageUtil.success("Story migration checked recorded boss victories."));
        }));
    }

    private boolean adminMutate(Player target, Consumer<StoryProfile> action) {
        StoryProfile profile = profiles.get(target.getUniqueId());
        if (profile == null) return false;
        action.accept(profile);
        save(profile);
        return true;
    }

    private static void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray> </dark_gray>", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parse(MenuItemUtil.visibleMiniName(name)));
            meta.lore(MenuItemUtil.visibleMiniLore(name, lore).stream().map(MessageUtil::parse).toList());
        });
        return item;
    }

    private static List<String> trim(List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) return List.of("<gray>No recovered text.</gray>");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(max, lines.size()); i++) out.add("<gray>" + lines.get(i) + "</gray>");
        if (lines.size() > max) out.add("<dark_gray>Click to read the full entry.</dark_gray>");
        return out;
    }

    private static String readable(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return -1;
    }

    private void runSync(Runnable task) {
        if (task == null || shuttingDown || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!shuttingDown && plugin.isEnabled()) task.run();
        });
    }

    static boolean isCurrentContentLoad(long completedGeneration, long currentGeneration, boolean shuttingDown) {
        return !shuttingDown && completedGeneration == currentGeneration;
    }

    static boolean shouldRetainLoadedProfile(boolean shuttingDown, boolean playerOnline) {
        return !shuttingDown && playerOnline;
    }

    private static int bossOrder(String id) {
        return switch (id) {
            case "yule_the_minion" -> 1;
            case "kael_the_ashen" -> 2;
            case "vesper_the_widow_queen" -> 3;
            case "mirewood_the_root_tyrant" -> 4;
            case "nereida_the_abyss_mother" -> 5;
            case "iron_saint" -> 6;
            case "aurelion_the_rift_seraph" -> 7;
            case "morvessa_the_runebloom_witch" -> 8;
            case "voralith_the_crimson_warden" -> 9;
            case "corrupted_oathkeeper" -> 10;
            default -> 99;
        };
    }

    private String veilmarkName(Player player) {
        if (plugin.getSuperpowerManager() == null) return "Unmarked";
        SuperpowerType type = plugin.getSuperpowerManager().powerOf(player);
        return type == null ? "Unmarked" : type.displayName();
    }

    private String familiarNames(Player player) {
        List<String> names = new ArrayList<>();
        if (plugin.getMayorQuestManager() != null && plugin.getMayorQuestManager().hasPetUnlocked(player)) names.add("Veil Wisp");
        if (plugin.getMinerManager() != null && plugin.getMinerManager().hasMinerPet(player)) names.add("Miner");
        if (plugin.getFarmerManager() != null && plugin.getFarmerManager().hasTiller(player)) names.add("Tiller");
        if (plugin.getWitchManager() != null && plugin.getWitchManager().hasMorrow(player)) names.add("Morrow");
        return names.isEmpty() ? "None" : String.join(", ", names);
    }

    private static Sound configuredSound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String enumName = raw.trim().toLowerCase(Locale.ROOT);
        return Registry.SOUNDS.keyStream()
            .filter(key -> key.getKey().replace('.', '_').equals(enumName))
            .map(Registry.SOUNDS::get)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private record LoadedProfile(Optional<DatabaseManager.StoryProfileRecord> record, Set<String> bossHistory) { }
    private record JournalHolder(UUID playerId, String categoryId, int page) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private record ChoiceHolder(UUID playerId) implements InventoryHolder, MenuDupeGuardListener.ReadOnlyMenuHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
