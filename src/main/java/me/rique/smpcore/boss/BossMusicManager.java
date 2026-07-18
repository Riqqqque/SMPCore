package me.rique.smpcore.boss;

import me.rique.smpcore.SMPCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossMusicManager {

    private static final long CONTEXT_REFRESH_TICKS = 5L;

    private final SMPCore plugin;
    private final Map<UUID, Playback> playbacks = new HashMap<>();
    private BukkitTask task;
    private long elapsedTicks;
    private float volume;

    public BossMusicManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadConfig();
    }

    public void reloadConfig() {
        shutdown();
        boolean enabled = plugin.getConfig().getBoolean("boss-music.enabled", true);
        volume = (float) clamp(plugin.getConfig().getDouble("boss-music.volume", 0.68), 0.05, 1.0);
        if (!enabled) {
            return;
        }
        elapsedTicks = 0L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID playerId : new ArrayList<>(playbacks.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                stopNoteSounds(player);
            }
        }
        playbacks.clear();
        elapsedTicks = 0L;
    }

    public boolean isPlaying(Player player) {
        return player != null && playbacks.containsKey(player.getUniqueId());
    }

    public void onMusicPreferenceChanged(Player player, boolean enabledForPlayer) {
        if (player != null && !enabledForPlayer && isPlaying(player)) {
            stopNoteSounds(player);
        }
    }

    private void tick() {
        if (elapsedTicks % CONTEXT_REFRESH_TICKS == 0L) {
            refreshPlaybacks();
        }
        tickPlaybacks();
        elapsedTicks++;
    }

    private void refreshPlaybacks() {
        BossManager bossManager = plugin.getBossManager();
        if (bossManager == null) {
            stopAll();
            return;
        }

        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            online.add(playerId);
            BossManager.BossMusicContext context = bossManager.bossMusicContext(player);
            BossMusic.Track track = context == null ? null : BossMusic.track(context.bossId());
            Playback current = playbacks.get(playerId);
            if (context == null || track == null) {
                if (current != null) {
                    stopPlayback(player);
                }
                continue;
            }
            if (current != null && current.bossEntityId.equals(context.bossEntityId())) {
                continue;
            }
            stopNoteSounds(player);
            playbacks.put(playerId, new Playback(context.bossEntityId(), track));
        }

        playbacks.keySet().removeIf(playerId -> !online.contains(playerId));
    }

    private void tickPlaybacks() {
        for (Map.Entry<UUID, Playback> entry : new ArrayList<>(playbacks.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Playback playback = entry.getValue();
            Entity boss = Bukkit.getEntity(playback.bossEntityId);
            if (player == null || !player.isOnline() || !(boss instanceof LivingEntity living)
                || living.isDead() || !living.isValid() || player.getWorld() != living.getWorld()) {
                if (player == null) {
                    playbacks.remove(entry.getKey());
                } else {
                    stopPlayback(player);
                }
                continue;
            }

            while (playback.cueIndex < playback.track.cues().size()
                && playback.track.cues().get(playback.cueIndex).tick() <= playback.songTick) {
                playCue(player, playback.track.cues().get(playback.cueIndex));
                playback.cueIndex++;
            }
            playback.songTick++;
            if (playback.songTick >= playback.track.lengthTicks()) {
                playback.songTick = 0;
                playback.cueIndex = 0;
            }
        }
    }

    private void playCue(Player player, BossMusic.Cue cue) {
        if (!isBossMusicEnabled(player)) {
            return;
        }
        player.playSound(
            player.getLocation(),
            soundFor(cue.voice()),
            SoundCategory.RECORDS,
            volume * cue.volume(),
            BossMusic.pitchForNote(cue.note())
        );
    }

    private void stopPlayback(Player player) {
        playbacks.remove(player.getUniqueId());
        stopNoteSounds(player);
    }

    private void stopAll() {
        for (UUID playerId : new ArrayList<>(playbacks.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                stopNoteSounds(player);
            }
        }
        playbacks.clear();
    }

    private void stopNoteSounds(Player player) {
        for (BossMusic.Voice voice : BossMusic.voices()) {
            player.stopSound(soundFor(voice), SoundCategory.RECORDS);
        }
    }

    private boolean isBossMusicEnabled(Player player) {
        return plugin.getPlayerSettingsManager() == null || plugin.getPlayerSettingsManager().isBossMusicEnabled(player);
    }

    private Sound soundFor(BossMusic.Voice voice) {
        return switch (voice) {
            case HARP -> Sound.BLOCK_NOTE_BLOCK_HARP;
            case BASS -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case BASEDRUM -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
            case SNARE -> Sound.BLOCK_NOTE_BLOCK_SNARE;
            case HAT -> Sound.BLOCK_NOTE_BLOCK_HAT;
            case GUITAR -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
            case BELL -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case CHIME -> Sound.BLOCK_NOTE_BLOCK_CHIME;
            case XYLOPHONE -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case IRON_XYLOPHONE -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case COW_BELL -> Sound.BLOCK_NOTE_BLOCK_COW_BELL;
            case DIDGERIDOO -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            case BIT -> Sound.BLOCK_NOTE_BLOCK_BIT;
            case BANJO -> Sound.BLOCK_NOTE_BLOCK_BANJO;
            case PLING -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case FLUTE -> Sound.BLOCK_NOTE_BLOCK_FLUTE;
        };
    }

    private double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Playback {
        private final UUID bossEntityId;
        private final BossMusic.Track track;
        private int songTick;
        private int cueIndex;

        private Playback(UUID bossEntityId, BossMusic.Track track) {
            this.bossEntityId = bossEntityId;
            this.track = track;
        }
    }
}
