package me.rique.smpcore.command;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The staff-facing command index used by /manager.
 *
 * Keep command families together here. A family may expose several related roots
 * when staff benefit from seeing them as one tool, such as the game-mode shortcuts.
 */
public final class StaffCommandCatalog {

    private static final List<Entry> ENTRIES = buildEntries();

    private StaffCommandCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry byId(String id) {
        if (id == null) return null;
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    public static List<Entry> inCategory(Category category) {
        return ENTRIES.stream().filter(entry -> entry.category() == category).toList();
    }

    public static List<Entry> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return ENTRIES.stream().filter(entry -> entry.searchText().contains(needle)).toList();
    }

    public static Set<String> coveredRoots() {
        return ENTRIES.stream().flatMap(entry -> entry.roots().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<Entry> buildEntries() {
        List<Entry> entries = new ArrayList<>();

        add(entries, "staff-mode", "Staff Mode", Material.ENDER_EYE, Category.MODERATION,
            "Deliberately enable or disable staff bypasses while keeping normal operator gameplay as the default.",
            List.of("/staffmode on", "/staffmode off", "/staffmode status"), "smpcore.staffmode",
            Risk.ROUTINE, "/staffmode status", true, Set.of("staffmode", "opmode"), "bypass operator");
        add(entries, "fly", "Flight", Material.ELYTRA, Category.MODERATION,
            "Toggle staff flight for yourself or another player.", List.of("/fly [player]"),
            "smpcore.fly", Risk.ROUTINE, "/fly", true, Set.of("fly"), "flight");
        add(entries, "speed", "Movement Speed", Material.SUGAR, Category.MODERATION,
            "Set walking or flying speed. Other-player changes require the matching others permission.",
            List.of("/speed walk <1-10> [player]", "/speed fly <1-10> [player]"), "smpcore.speed",
            Risk.CAUTION, "/speed ", false, Set.of("speed", "flyspeed", "wspeed"), "movement");
        add(entries, "heal", "Heal", Material.GOLDEN_APPLE, Category.MODERATION,
            "Restore health and clear fire for yourself or a selected player.", List.of("/heal [player]"),
            "smpcore.heal", Risk.ROUTINE, "/heal", true, Set.of("heal"), "health");
        add(entries, "feed", "Feed", Material.COOKED_BEEF, Category.MODERATION,
            "Restore food and saturation for yourself or a selected player.", List.of("/feed [player]"),
            "smpcore.feed", Risk.ROUTINE, "/feed", true, Set.of("feed"), "hunger saturation");
        add(entries, "god", "God Mode", Material.TOTEM_OF_UNDYING, Category.MODERATION,
            "Toggle damage immunity for yourself or another player.", List.of("/god [player]"),
            "smpcore.god", Risk.CAUTION, "/god", true, Set.of("god"), "invulnerable");
        add(entries, "vanish", "Vanish", Material.GLASS, Category.MODERATION,
            "Hide from players or enter trace-free spectator flight.",
            List.of("/vanish", "/vanish spectator", "/vanish off"), "smpcore.vanish",
            Risk.CAUTION, "/vanish", true, Set.of("vanish", "v", "svanish", "specvanish"), "invisible spectator");
        add(entries, "force-unvanish", "Force Unvanish", Material.ENDER_EYE, Category.MODERATION,
            "Owner-only recovery for a player whose vanish state cannot be cleared normally.",
            List.of("/forceunvanish <player> (owner only)"), "", Risk.OWNER_ONLY,
            "/forceunvanish ", false, Set.of("forceunvanish", "adminunvanish", "revealplayer"), "stuck invisible");
        add(entries, "freeze", "Freeze Player", Material.PACKED_ICE, Category.MODERATION,
            "Freeze or release a player during an investigation.", List.of("/freeze <player>", "/unfreeze <player>"),
            "smpcore.freeze", Risk.CAUTION, "/freeze ", false, Set.of("freeze", "unfreeze", "uf"), "investigation");
        add(entries, "inventory", "Inspect Inventory", Material.CHEST, Category.MODERATION,
            "Open a live player's inventory. Changes affect the real inventory.", List.of("/invsee <player>"),
            "smpcore.invsee", Risk.CAUTION, "/invsee ", false, Set.of("invsee"), "inventory inspect");
        add(entries, "team-vault-inspect", "Inspect Team Vault", Material.ENDER_CHEST, Category.MODERATION,
            "Owner-only read and recovery access to a team's live vault. Treat every slot as player property.",
            List.of("/viewteamvault <team> (owner only)"), "", Risk.OWNER_ONLY, "/viewteamvault ", false,
            Set.of("viewteamvault", "teamvaultsee", "tvaultsee"), "team vault recovery");
        add(entries, "nickname", "Nickname", Material.NAME_TAG, Category.MODERATION,
            "Set or clear a player's server nickname.", List.of("/nick <player> <name|off>"),
            "smpcore.nickname", Risk.CAUTION, "/nick ", false, Set.of("nick", "nickname"), "display name");
        add(entries, "ban", "Ban Player", Material.BARRIER, Category.MODERATION,
            "Permanently block one or more player profiles. The reason is optional.",
            List.of("/ban <player> [reason]"), "smpcore.ban", Risk.DANGEROUS,
            "/ban ", false, Set.of("ban"), "punish permanent reason");
        add(entries, "kick", "Kick Player", Material.IRON_BOOTS, Category.MODERATION,
            "Disconnect one or more online players without banning them. The reason is optional.",
            List.of("/kick <player> [reason]"), "smpcore.kick", Risk.CAUTION,
            "/kick ", false, Set.of("kick"), "disconnect remove reason");
        add(entries, "unban", "Unban Player", Material.WRITABLE_BOOK, Category.MODERATION,
            "Remove a name ban after staff have confirmed the appeal.", List.of("/unban <player>"),
            "smpcore.unban", Risk.CAUTION, "/unban ", false, Set.of("unban", "pardon"), "ban appeal");
        add(entries, "announce", "Announcement", Material.BELL, Category.MODERATION,
            "Broadcast an official server message to every online player.", List.of("/announce <message>"),
            "smpcore.announce", Risk.CAUTION, "/announce ", false, Set.of("announce", "broadcast"), "message");
        add(entries, "smite", "Harmless Smite", Material.LIGHTNING_ROD, Category.MODERATION,
            "Play a harmless lightning effect on a player.", List.of("/smite <player>"),
            "smpcore.smite", Risk.ROUTINE, "/smite ", false, Set.of("smite"), "lightning");
        add(entries, "gmc", "Creative Mode", Material.CRAFTING_TABLE, Category.MODERATION,
            "Switch yourself or another player to Creative mode.", List.of("/gmc [player]"),
            "smpcore.gmc", Risk.CAUTION, "/gmc", true, Set.of("gmc", "gamemodecreative"), "gamemode creative");
        add(entries, "gms", "Survival Mode", Material.IRON_SWORD, Category.MODERATION,
            "Switch yourself or another player to Survival mode.", List.of("/gms [player]"),
            "smpcore.gms", Risk.ROUTINE, "/gms", true, Set.of("gms", "gamemodesurvival"), "gamemode survival");
        add(entries, "gma", "Adventure Mode", Material.MAP, Category.MODERATION,
            "Switch yourself or another player to Adventure mode.", List.of("/gma [player]"),
            "smpcore.gma", Risk.CAUTION, "/gma", true, Set.of("gma", "gamemodeadventure"), "gamemode adventure");
        add(entries, "gmsp", "Spectator Mode", Material.ENDER_PEARL, Category.MODERATION,
            "Switch yourself or another player to Spectator mode.", List.of("/gmsp [player]"),
            "smpcore.gmsp", Risk.CAUTION, "/gmsp", true, Set.of("gmsp", "gamemodespectator"), "gamemode spectator");

        add(entries, "launch-access", "Launch Access", Material.TRIPWIRE_HOOK, Category.WORLD,
            "Inspect, open, or lock the public launch gate and manage temporary owner access.",
            List.of("/launchaccess status", "/launchaccess open", "/launchaccess lock", "/launchaccess allowme"),
            "smpcore.launchaccess.admin", Risk.DANGEROUS, "/launchaccess status", true,
            Set.of("launchaccess", "launchgate", "serveraccess"), "whitelist gate public");
        add(entries, "smp-start", "SMP Start Controls", Material.BEACON, Category.WORLD,
            "Inspect or control launch staging, the barrier, grace period, and dimension unlocks.",
            List.of("/startsmp status", "/startsmp [graceMinutes]", "/startsmp barrier", "/startsmp preview",
                "/startsmp lock", "/startsmp reset", "/startsmp unlock nether|end"),
            "smpcore.startsmp", Risk.DANGEROUS, "/startsmp status", true,
            Set.of("startsmp", "smpstart"), "launch barrier nether end dimension");
        add(entries, "set-spawn", "Set Server Spawn", Material.RECOVERY_COMPASS, Category.WORLD,
            "Replace the exact server spawn with your current position and facing.", List.of("/setspawn"),
            "smpcore.setspawn", Risk.DANGEROUS, "/setspawn", false, Set.of("setspawn"), "location");
        add(entries, "spawn-protection", "Spawn Protection", Material.SHIELD, Category.WORLD,
            "Inspect or configure the protected spawn cuboid and its build rules.",
            List.of("/spawnprotect", "/spawnprotect check", "/spawnprotect status", "/spawnprotect pos1",
                "/spawnprotect pos2", "/spawnprotect enable|disable"),
            "smpcore.spawnprotect.admin", Risk.DANGEROUS, "/spawnprotect check", true,
            Set.of("spawnprotect", "sprotect", "adminspawnstick"), "region cuboid build");
        add(entries, "warps", "Public Warps", Material.LODESTONE, Category.WORLD,
            "List, create, move, or remove public server warps.", List.of("/warp list", "/warp create <name>",
                "/warp info <name>", "/warp move <name>", "/warp delete <name>"), "smpcore.warp.admin", Risk.DANGEROUS,
            "/warp list", true, Set.of("warp", "warps"), "travel location");
        add(entries, "time", "World Time", Material.CLOCK, Category.WORLD,
            "Set the current world's time to day or night.", List.of("/day", "/night"),
            "smpcore.time", Risk.ROUTINE, "/day", true, Set.of("day", "night"), "world");
        add(entries, "weather", "World Weather", Material.SUNFLOWER, Category.WORLD,
            "Clear the weather or start a storm in the current world.", List.of("/sun", "/storm"),
            "smpcore.weather", Risk.ROUTINE, "/sun", true, Set.of("sun", "storm"), "rain thunder");

        add(entries, "boss-dungeon", "Boss Dungeon", Material.REINFORCED_DEEPSLATE, Category.BOSSES,
            "Configure dungeon points, inspect queue state, test bosses, join, spectate, or reset a fight.",
            List.of("/bossdungeon", "/bossdungeon setentry|setfight|setspectator|setboss|setkeeper",
                "/bossdungeon tp <point>", "/bossdungeon test <boss>", "/bossdungeon join|spectate|reset"),
            "smpcore.dungeon.admin", Risk.DANGEROUS, "/bossdungeon", true,
            Set.of("bossdungeon", "bdungeon", "bossjoin", "bossqueue", "bosskeeper"), "arena fight queue");
        add(entries, "boss-loadout", "Boss Test Loadout", Material.NETHERITE_CHESTPLATE, Category.BOSSES,
            "Open the staff test-loadout menu for one boss tier.", List.of("/bossloadout [boss]"),
            "smpcore.dungeon.admin", Risk.CAUTION, "/bossloadout", true,
            Set.of("bossloadout", "bossgear", "testgear"), "armor testing");
        add(entries, "boss-controls", "Boss Controls", Material.WITHER_SKELETON_SKULL, Category.BOSSES,
            "Open boss admin controls or inspect configured boss definitions.", List.of("/bosses"),
            "smpcore.boss.admin", Risk.CAUTION, "/bosses", true, Set.of("bosses", "boss"), "summon despawn");
        add(entries, "boss-mastery", "Boss Mastery Admin", Material.NETHER_STAR, Category.BOSSES,
            "Inspect or correct player boss mastery and ledger progress.",
            List.of("/bossmasteryadmin setkills <player> <boss> <amount>",
                "/bossmasteryadmin reset <player>", "/bossledgeradmin ..."),
            "smpcore.bossmastery.admin", Risk.DANGEROUS, "/bossmasteryadmin ", false,
            Set.of("bossmasteryadmin", "bossledgeradmin"), "progress ledger");

        guide(entries, "spawnnpc", "Main Guide", Material.COMPASS, "Main guide and newcomer help.");
        guide(entries, "corruptionwarden", "Corruption Warden", Material.CRYING_OBSIDIAN, "Corruption guide and station access.");
        guide(entries, "mayor", "Mayor Bah", Material.EMERALD, "Mayor quest and familiar introduction.");
        guide(entries, "artificer", "Artificer", Material.SMITHING_TABLE, "Gear, crafting, and upgrade guide.");
        guide(entries, "dungeonkeeper", "Malakar", Material.WITHER_SKELETON_SKULL, "Boss Dungeon keeper and boss field guide.");
        guide(entries, "brewmaster", "Brewmaster", Material.BREWING_STAND, "Boss-brew guide.");
        guide(entries, "adventurer", "Adventurer", Material.SPYGLASS, "Travel and exploration guide.");
        guide(entries, "dealer", "Card Dealer", Material.PAPER, "Tavern card-game NPC.");
        guide(entries, "croupier", "Croupier", Material.GOLD_INGOT, "Roulette NPC.");
        guide(entries, "duelmaster", "Duelmaster", Material.IRON_SWORD, "Duel queue and arena NPC.");
        guide(entries, "goblinhunter", "Goblin Hunter", Material.CROSSBOW, "Goblin Hunt NPC.");
        guide(entries, "miner", "Miner", Material.IRON_PICKAXE, "Miner quest and familiar NPC.");
        guide(entries, "farmer", "Farmer", Material.WHEAT, "Farmer quest and familiar NPC.");
        guide(entries, "witch", "Witch", Material.CAULDRON, "Witch quest, familiar, and brewing NPC.");
        guide(entries, "overseer", "Overseer", Material.ENDER_EYE, "Progression overseer NPC.");
        guide(entries, "beastwarden", "Beastwarden", Material.SADDLE, "Familiar training and mount NPC.");
        guide(entries, "bossbroker", "Boss Broker", Material.GOLD_NUGGET, "Boss-material exchange NPC.");
        guide(entries, "blackmarket", "Black Market", Material.ENDER_CHEST, "Black Market NPC.");
        guide(entries, "fisher", "Fisher", Material.FISHING_ROD, "Fishing quest NPC.");
        add(entries, "reforger", "Brannik the Reforger", Material.ANVIL, Category.NPCS,
            "Spawn, remove, list, or refresh the Reforger NPC; also access staff reforge items.",
            List.of("/reforger spawn|remove|list|refresh", "/reforger stone [player]"),
            "smpcore.reforge.admin", Risk.CAUTION, "/reforger list", true,
            Set.of("reforger", "dwarfreforger", "dwarfnpc"), "npc stone");
        add(entries, "priest", "Father Aldren", Material.ENCHANTING_TABLE, Category.NPCS,
            "Spawn, remove, list, or refresh Priest NPCs.",
            List.of("/priest spawn|remove|list|refresh"),
            "smpcore.priest.admin", Risk.CAUTION, "/priest list", true,
            Set.of("priest", "spawnpriest", "veilpriest", "priestnpc"), "npc ledger");

        add(entries, "custom-item", "Custom Items", Material.COMMAND_BLOCK, Category.ITEMS,
            "Give a registered SMPCore item to a player. Use the in-command suggestions for valid IDs.",
            List.of("/customitem give <item> [player]", "/customitem revoke <item> <player>",
                "/customitem take <item> <player>"),
            "smpcore.customitem.admin", Risk.CAUTION, "/customitem give ", false,
            Set.of("customitem", "citem"), "give item registry");
        add(entries, "legendary", "Legendary Items", Material.END_CRYSTAL, Category.ITEMS,
            "Give a registered Legendary item. Tab completion lists valid item IDs.",
            List.of("/legendary give <item> [player]"),
            "smpcore.legendary.admin", Risk.CAUTION, "/legendary give ", false,
            Set.of("legendary"), "relic give");
        add(entries, "enchant-books", "Custom Enchant Books", Material.ENCHANTED_BOOK, Category.ITEMS,
            "Give staff-only custom enchant books. Each command targets a player and optional level.",
            List.of("/delicatebook <player>", "/telekinesisbook <player>", "/smeltingtouchbook <player>",
                "/wisebook <player>", "/doublejumpbook <player> [level]", "/dashbook <player> [level]"),
            "smpcore.customenchant.admin", Risk.CAUTION,
            "/doublejumpbook ", false, Set.of("delicatebook", "telekinesisbook", "telekenesisbook",
                "smeltingtouchbook", "smeltingbook", "wisebook", "doublejumpbook", "djbook", "dashbook"), "book give");
        add(entries, "replenish-book", "Replenish Book", Material.ENCHANTED_BOOK, Category.ITEMS,
            "Give the separate Replenish enchant book to yourself or a selected player.",
            List.of("/replenishbook [player]"), "smpcore.replenish.admin", Risk.CAUTION,
            "/replenishbook ", false, Set.of("replenishbook"), "crop enchant give");
        add(entries, "essence-admin", "Essence Admin", Material.ECHO_SHARD, Category.ITEMS,
            "Inspect, add, remove, set, or reset a player's Essence and progression.",
            List.of("/essenceadmin balance <player>", "/essenceadmin progress <player>",
                "/essenceadmin give|take|set <player> <amount>", "/essenceadmin reset <player> confirm"),
            "smpcore.essence.admin", Risk.DANGEROUS, "/essenceadmin balance ", false,
            Set.of("essenceadmin", "essadmin"), "currency progress");
        add(entries, "backpack-admin", "Backpack Recovery", Material.BUNDLE, Category.ITEMS,
            "List, view, and safely restore recorded backpack snapshots.",
            List.of("/backpackadmin list <player> [page]", "/backpackadmin view <player> <latest|id>",
                "/backpackadmin restore <player> <latest|id>", "/backpackadmin confirm|cancel"),
            "smpcore.backpack.admin", Risk.DANGEROUS, "/backpackadmin list ", false,
            Set.of("backpackadmin", "backpackrestore", "bprestore"), "snapshot restore");
        add(entries, "item-recovery", "Item Recovery", Material.RECOVERY_COMPASS, Category.ITEMS,
            "Inspect exact recovery records, verify the item is not still live, then stage a single-item restore.",
            List.of("/itemrecovery list <player> [page]", "/itemrecovery view <player> <latest|id>",
                "/itemrecovery verify <player> <id> <item-number>", "/itemrecovery restore <player> <id> <item-number>",
                "/itemrecovery confirm|cancel"), "smpcore.inventoryrecovery.admin", Risk.DANGEROUS,
            "/itemrecovery list ", false, Set.of("itemrecovery", "invrecovery", "guirecovery"), "verify duplicate restore");
        add(entries, "death-inventory", "Death Inventory", Material.SKELETON_SKULL, Category.ITEMS,
            "Inspect and safely restore exact death-inventory snapshots.",
            List.of("/deathinventory list <player> [page]", "/deathinventory view <player> [snapshot]",
                "/deathinventory restore <player> [snapshot]", "/deathinventory confirm|cancel"),
            "smpcore.admin.deathinventory", Risk.DANGEROUS, "/deathinventory list ", false,
            Set.of("deathinventory", "deathinv", "invrestore"), "snapshot recovery");
        add(entries, "item-audit", "Item Audit", Material.SPYGLASS, Category.ITEMS,
            "Search a player's tracked inventory history for an item or current holdings.",
            List.of("/itemaudit <player> [item]"), "smpcore.itemaudit.admin", Risk.READ_ONLY,
            "/itemaudit ", false, Set.of("itemaudit", "audititem", "itemtrace"), "history investigation");
        add(entries, "familiar-admin", "Familiar Admin", Material.ALLAY_SPAWN_EGG, Category.ITEMS,
            "List registered familiars or give and remove any familiar from a player.",
            List.of("/familiaradmin list", "/familiaradmin give <player> <familiar>",
                "/familiaradmin take <player> <familiar>"),
            "smpcore.familiar.admin", Risk.DANGEROUS, "/familiaradmin ", false,
            Set.of("familiaradmin", "famadmin", "adminfamiliar"), "pet unlock");
        add(entries, "mayor-familiar", "Mayor Familiar Admin", Material.AMETHYST_SHARD, Category.ITEMS,
            "Give or remove the Veil Wisp reward through the Mayor's focused recovery command.",
            List.of("/mayorfamiliar give <player>", "/mayorfamiliar take <player>"),
            "smpcore.mayor.admin", Risk.DANGEROUS, "/mayorfamiliar ", false,
            Set.of("mayorfamiliar", "adminfamiliar"), "veil wisp pet unlock");
        add(entries, "beastwarden-admin", "Beastwarden Admin", Material.GOAT_HORN, Category.ITEMS,
            "Inspect, preview, test, or correct Beastwarden familiar-training progression.",
            List.of("/beastwardenadmin progress <player>",
                "/beastwardenadmin preview [main|familiars|tree|evolution]",
                "/beastwardenadmin complete|reset|armor <player>"),
            "smpcore.beastwarden.admin", Risk.DANGEROUS, "/beastwardenadmin ", false,
            Set.of("beastwardenadmin", "beastadmin"), "pet skill tree");
        add(entries, "set-power", "Assign Superpower", Material.AMETHYST_SHARD, Category.ITEMS,
            "Assign or clear a player's class. This directly changes progression behavior.",
            List.of("/setpower <player> <class|none>"), "smpcore.superpower.assign", Risk.DANGEROUS,
            "/setpower ", false, Set.of("setpower", "powerset"), "class assign");
        add(entries, "reward-admin", "Admin Rewards", Material.CHEST_MINECART, Category.ITEMS,
            "Grant supported one-time or recovery rewards through the audited admin reward flow.",
            List.of("/admin reward ..."), "smpcore.reward.admin", Risk.DANGEROUS,
            "/admin reward ", false, Set.of("admin"), "give sigil reward");
        add(entries, "corruption-station", "Corruption Station", Material.RESPAWN_ANCHOR, Category.ITEMS,
            "Give, place, or manage Corruption Anchors and station tools.",
            List.of("/corruptionstation list", "/corruptionstation give [player]"),
            "smpcore.corruption.admin", Risk.CAUTION,
            "/corruptionstation list", true, Set.of("corruptionstation", "corruptor", "corruptionanchor"), "anchor");

        add(entries, "stalls", "Market Stalls", Material.OAK_SIGN, Category.ECONOMY,
            "Create, price, inspect, snapshot, transfer, or restore protected spawn market stalls.",
            List.of("/stall admin wand", "/stall admin create <id> <price>", "/stall admin setprice <id> <price>",
                "/stall admin list", "/stall admin restore <id>", "/stall admin snapshot <id> confirm",
                "/stall admin snapshotall confirm"), "smpcore.stall.admin", Risk.DANGEROUS,
            "/stall admin list", true, Set.of("stall", "stalls", "marketstall"), "market shop template");
        add(entries, "shops", "Player Shops", Material.CHEST, Category.ECONOMY,
            "Open the shop hub and access admin-shop controls exposed by the chest-shop menus.",
            List.of("/shops", "/shops help"), "smpcore.shop.admin", Risk.CAUTION,
            "/shops", true, Set.of("shops", "shop"), "chest infinite payments");

        add(entries, "duel-admin", "Duel Administration", Material.DIAMOND_SWORD, Category.GAMES,
            "Inspect live matches, configure arena points, clean up fights, and manage duel leaderboards.",
            List.of("/duel admin status", "/duel admin setpoint <point>", "/duel admin ..."),
            "smpcore.duel.admin", Risk.DANGEROUS, "/duel admin status", true,
            Set.of("duel", "duels", "arena"), "match arena bet");
        add(entries, "tavern-admin", "Tavern Administration", Material.HONEY_BOTTLE, Category.GAMES,
            "Place and manage tavern stations, game tables, and leaderboards.",
            List.of("/tavernadmin list", "/tavernadmin set|remove <type>",
                "/tavernadmin leaderboard spawn <game>", "/tavernadmin leaderboard remove",
                "/tavernadmin sober <player>"), "smpcore.tavern.admin", Risk.DANGEROUS,
            "/tavernadmin list", true, Set.of("tavernadmin", "tavernsetup", "tavernstations"), "games");

        add(entries, "spawners", "Spawner Diagnostics", Material.SPAWNER, Category.DIAGNOSTICS,
            "Inspect the looked-at custom spawner and run administrative maintenance.",
            List.of("/spawner info", "/spawner ..."), "smpcore.spawner.admin", Risk.CAUTION,
            "/spawner info", true, Set.of("spawner", "spawnermgr"), "hologram refresh");
        add(entries, "goblins", "Goblin Diagnostics", Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, Category.DIAGNOSTICS,
            "Audit goblin state, reveal nearby goblins to staff, or manage hunt data.",
            List.of("/goblins audit", "/goblins see", "/goblins ..."), "smpcore.goblins.admin",
            Risk.CAUTION, "/goblins audit", true, Set.of("goblins", "goblinhunt"), "hunt reveal");
        add(entries, "spawn-life", "Spawn Life", Material.VILLAGER_SPAWN_EGG, Category.DIAGNOSTICS,
            "List, place, refresh, or remove ambient spawn characters and animals.",
            List.of("/spawnlife list", "/spawnlife spawn ...", "/spawnlife refresh", "/spawnlife remove ..."),
            "smpcore.spawnlife.admin", Risk.CAUTION, "/spawnlife list", true,
            Set.of("spawnlife", "ambientnpc", "townnpc"), "npc animal");
        add(entries, "bedrock-skulls", "Bedrock Skull Bridge", Material.PLAYER_HEAD, Category.DIAGNOSTICS,
            "Inspect and repair Bedrock-compatible custom-head conversion state.",
            List.of("/bedrockskulls status", "/bedrockskulls ..."), "smpcore.bedrockskulls.admin",
            Risk.CAUTION, "/bedrockskulls status", true, Set.of("bedrockskulls", "bedrockheads"), "geyser floodgate head");
        add(entries, "crossplay", "Crossplay Diagnostics", Material.NAUTILUS_SHELL, Category.DIAGNOSTICS,
            "Show Geyser/Floodgate integration state and Bedrock safety diagnostics.",
            List.of("/smpcore bedrock"), "smpcore.reload", Risk.READ_ONLY,
            "/smpcore bedrock", true, Set.of("smpcore", "bedrock", "crossplay"), "geyser floodgate");
        add(entries, "legendary-altar", "Legendary Altar Diagnostics", Material.END_PORTAL_FRAME, Category.DIAGNOSTICS,
            "Inspect or deliberately repair the active Legendary Altar encounter state.",
            List.of("/smpcore altar status", "/smpcore altar clear",
                "/smpcore altar spawn [legendary]", "/smpcore altar spawnready [legendary]"),
            "smpcore.reload", Risk.DANGEROUS, "/smpcore altar status", true,
            Set.of("smpcore"), "legendary altar encounter");
        add(entries, "veil-admin", "Veil Story Admin", Material.WRITABLE_BOOK, Category.DIAGNOSTICS,
            "Inspect or correct The Eleventh Oath story state.", List.of("/veil admin ..."),
            "smpcore.story.admin", Risk.DANGEROUS, "/veil admin ", false,
            Set.of("veil", "veiljournal"), "story oath memory");
        add(entries, "reload", "Reload SMPCore", Material.REDSTONE_TORCH, Category.DIAGNOSTICS,
            "Reload supported SMPCore configuration without restarting Paper. This is not a full plugin reload.",
            List.of("/smpcore reload"), "smpcore.reload", Risk.CAUTION,
            "/smpcore reload", false, Set.of("smpcore", "core"), "config");
        add(entries, "changelog-reload", "Reload Changelog", Material.KNOWLEDGE_BOOK, Category.DIAGNOSTICS,
            "Reload the player-facing changelog file after an intentional server-side edit.",
            List.of("/changelog reload"), "smpcore.changelog.reload", Risk.CAUTION,
            "/changelog reload", false, Set.of("changelog", "changes", "updates"), "config");

        return List.copyOf(entries);
    }

    private static void guide(List<Entry> entries, String root, String title, Material icon, String description) {
        add(entries, "npc-" + root, title, icon, Category.NPCS,
            description + " The common NPC controls spawn at your position, remove, list, or refresh.",
            List.of("/" + root + " spawn", "/" + root + " remove", "/" + root + " list", "/" + root + " refresh"),
            "smpcore.guide.admin", Risk.CAUTION, "/" + root + " list", true, guideRoots(root), "guide npc");
    }

    private static Set<String> guideRoots(String root) {
        return switch (root) {
            case "spawnnpc" -> Set.of(root, "spawnguide", "guide");
            case "corruptionwarden" -> Set.of(root, "wardennpc", "warden");
            case "mayor" -> Set.of(root, "mayornpc");
            case "artificer" -> Set.of(root, "gearguide", "gearmentor");
            case "dungeonkeeper" -> Set.of(root, "dungeonnpc", "bosskeeper");
            case "brewmaster" -> Set.of(root, "brewmasternpc");
            case "adventurer" -> Set.of(root, "cardsharp", "cardsharpnpc", "adventurernpc");
            case "dealer" -> Set.of(root, "dealernpc", "blackjackdealer");
            case "croupier" -> Set.of(root, "roulettecroupier", "roulettedealer");
            case "duelmaster" -> Set.of(root, "duelnpc", "fightmaster");
            case "goblinhunter" -> Set.of(root, "goblinhunternpc");
            case "miner" -> Set.of(root, "minernpc");
            case "farmer" -> Set.of(root, "farmernpc");
            case "witch" -> Set.of(root, "witchnpc");
            case "overseer" -> Set.of(root, "overseernpc");
            case "beastwarden" -> Set.of(root, "beastwardennpc", "beastnpc");
            case "bossbroker" -> Set.of(root, "bossbrokernpc", "mogrik");
            case "blackmarket" -> Set.of(root, "blackmarketnpc", "curiobroker", "sable");
            case "fisher" -> Set.of(root, "fishernpc");
            default -> Set.of(root);
        };
    }

    private static void add(
        List<Entry> entries,
        String id,
        String title,
        Material icon,
        Category category,
        String description,
        List<String> usages,
        String permission,
        Risk risk,
        String preparedCommand,
        boolean directRun,
        Set<String> roots,
        String keywords
    ) {
        entries.add(new Entry(id, title, icon, category, description, List.copyOf(usages), permission,
            risk, preparedCommand, directRun, Set.copyOf(roots), keywords));
    }

    public enum Category {
        MODERATION("Moderation & Players", Material.SHIELD, "Player controls, investigations, and operator tools."),
        WORLD("World & Access", Material.COMPASS, "Launch gates, spawn, warps, time, and weather."),
        BOSSES("Bosses & Dungeon", Material.WITHER_SKELETON_SKULL, "Boss testing, dungeon setup, and mastery."),
        NPCS("NPCs & Spawn", Material.VILLAGER_SPAWN_EGG, "Every staff-managed guide and story NPC."),
        ITEMS("Items & Recovery", Material.ENDER_CHEST, "Custom items, progression, audits, and safe recovery."),
        ECONOMY("Economy & Shops", Material.EMERALD, "Player shops and protected market stalls."),
        GAMES("Duels & Games", Material.DIAMOND_SWORD, "Arena and tavern administration."),
        DIAGNOSTICS("Diagnostics & Maintenance", Material.COMPARATOR, "Runtime checks, bridges, story state, and reloads.");

        private final String title;
        private final Material icon;
        private final String description;

        Category(String title, Material icon, String description) {
            this.title = title;
            this.icon = icon;
            this.description = description;
        }

        public String title() {
            return title;
        }

        public Material icon() {
            return icon;
        }

        public String description() {
            return description;
        }
    }

    public enum Risk {
        READ_ONLY("Read only", "<green>"),
        ROUTINE("Routine", "<aqua>"),
        CAUTION("Use carefully", "<yellow>"),
        DANGEROUS("High impact", "<red>"),
        OWNER_ONLY("Owner only", "<dark_red>");

        private final String label;
        private final String color;

        Risk(String label, String color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public String color() {
            return color;
        }
    }

    public record Entry(
        String id,
        String title,
        Material icon,
        Category category,
        String description,
        List<String> usages,
        String permission,
        Risk risk,
        String preparedCommand,
        boolean directRun,
        Set<String> roots,
        String keywords
    ) {
        private String searchText() {
            return (id + " " + title + " " + description + " " + String.join(" ", usages) + " "
                + String.join(" ", roots) + " " + keywords).toLowerCase(Locale.ROOT);
        }
    }
}
