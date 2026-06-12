package me.rique.smpcore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.rique.smpcore.SMPCore;
import me.rique.smpcore.boss.BossRecord;
import me.rique.smpcore.home.HomeEntry;
import me.rique.smpcore.spawner.SpawnerData;
import me.rique.smpcore.waystone.WaystoneEntry;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Async SQLite backend using HikariCP.
 * All public methods return CompletableFutures and run on a dedicated thread pool,
 * keeping the main thread free.
 */
public final class DatabaseManager {

    private final SMPCore plugin;
    private HikariDataSource dataSource;

    /** Dedicated DB thread pool — keeps DB I/O isolated from the common fork-join pool. */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SMPCore-DB");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager(SMPCore plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void init() {
        plugin.getDataFolder().mkdirs();
        File dbFile = new File(plugin.getDataFolder(), "data.db");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(20_000);
        cfg.setIdleTimeout(300_000);
        cfg.setMaxLifetime(600_000);
        cfg.setConnectionInitSql(
            "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; " +
            "PRAGMA cache_size=2000; PRAGMA temp_store=MEMORY;"
        );

        dataSource = new HikariDataSource(cfg);
        try {
            createTables();
        } catch (SQLException e) {
            if (dataSource != null && !dataSource.isClosed()) dataSource.close();
            throw new RuntimeException("Failed to create database tables", e);
        }
    }

    public CompletableFuture<Void> initAsync() {
        return CompletableFuture.runAsync(() -> {
            plugin.getDataFolder().mkdirs();
            File dbFile = new File(plugin.getDataFolder(), "data.db");

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // Do NOT set driverClassName here — the string literal "org.sqlite.JDBC" would
            // be wrong after Shadow relocates the package. JDBC 4.0 SPI auto-discovers the
            // driver via META-INF/services/java.sql.Driver (merged by mergeServiceFiles()).
            cfg.setMaximumPoolSize(4);
            cfg.setMinimumIdle(1);
            cfg.setConnectionTimeout(20_000);
            cfg.setIdleTimeout(300_000);
            cfg.setMaxLifetime(600_000);
            // WAL mode for concurrent read+write; NORMAL sync is safe & fast enough
            cfg.setConnectionInitSql(
                "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; " +
                "PRAGMA cache_size=2000; PRAGMA temp_store=MEMORY;"
            );

            dataSource = new HikariDataSource(cfg);
            try {
                createTables();
            } catch (SQLException e) {
                if (dataSource != null && !dataSource.isClosed()) dataSource.close();
                throw new RuntimeException("Failed to create database tables", e);
            }
        }, executor);
    }

    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    private Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException {
        String spawners = """
            CREATE TABLE IF NOT EXISTS spawners (
                world               TEXT    NOT NULL,
                x                   INTEGER NOT NULL,
                y                   INTEGER NOT NULL,
                z                   INTEGER NOT NULL,
                entity_type         TEXT    NOT NULL DEFAULT 'PIG',
                stack_count         INTEGER NOT NULL DEFAULT 1,
                sugar_count         INTEGER NOT NULL DEFAULT 0,
                redstone_controlled INTEGER NOT NULL DEFAULT 0,
                ai_nerfed           INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (world, x, y, z)
            )""";

        String homes = """
            CREATE TABLE IF NOT EXISTS homes (
                player_uuid TEXT NOT NULL,
                name        TEXT NOT NULL,
                world       TEXT NOT NULL,
                x           REAL NOT NULL,
                y           REAL NOT NULL,
                z           REAL NOT NULL,
                yaw         REAL NOT NULL DEFAULT 0,
                pitch       REAL NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, name)
            )""";

        String players = """
            CREATE TABLE IF NOT EXISTS players (
                uuid        TEXT PRIMARY KEY,
                username    TEXT NOT NULL,
                nickname    TEXT,
                first_join  INTEGER,
                last_seen   INTEGER,
                join_count  INTEGER NOT NULL DEFAULT 1
            )""";

        String teams = """
            CREATE TABLE IF NOT EXISTS teams (
                name        TEXT PRIMARY KEY COLLATE NOCASE,
                owner_uuid  TEXT NOT NULL,
                color       TEXT NOT NULL DEFAULT 'gold',
                created_at  INTEGER NOT NULL
            )""";

        String teamMembers = """
            CREATE TABLE IF NOT EXISTS team_members (
                team_name   TEXT NOT NULL COLLATE NOCASE,
                player_uuid TEXT NOT NULL,
                PRIMARY KEY (team_name, player_uuid)
            )""";

        String teamMembersByPlayer = """
            CREATE INDEX IF NOT EXISTS idx_team_members_player
            ON team_members(player_uuid)
            """;

        String teamVaults = """
            CREATE TABLE IF NOT EXISTS team_vaults (
                team_name TEXT PRIMARY KEY COLLATE NOCASE,
                contents  BLOB NOT NULL
            )""";

        String waystones = """
            CREATE TABLE IF NOT EXISTS waystones (
                world       TEXT    NOT NULL,
                x           INTEGER NOT NULL,
                y           INTEGER NOT NULL,
                z           INTEGER NOT NULL,
                name        TEXT    NOT NULL,
                created_by  TEXT,
                created_at  INTEGER NOT NULL,
                PRIMARY KEY (world, x, y, z)
            )""";

        String waystoneNames = """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_waystones_name
            ON waystones(name COLLATE NOCASE)
            """;

        String waystoneKnown = """
            CREATE TABLE IF NOT EXISTS waystone_known (
                player_uuid TEXT    NOT NULL,
                world       TEXT    NOT NULL,
                x           INTEGER NOT NULL,
                y           INTEGER NOT NULL,
                z           INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, world, x, y, z)
            )""";

        String waystoneKnownByPlayer = """
            CREATE INDEX IF NOT EXISTS idx_waystone_known_player
            ON waystone_known(player_uuid)
            """;

        String legendaryAltar = """
            CREATE TABLE IF NOT EXISTS legendary_altar (
                id            INTEGER PRIMARY KEY CHECK (id = 1),
                legendary_id  TEXT,
                world         TEXT,
                x             INTEGER,
                y             INTEGER,
                z             INTEGER,
                spawned_at    INTEGER NOT NULL DEFAULT 0,
                activates_at  INTEGER NOT NULL DEFAULT 0,
                expires_at    INTEGER NOT NULL DEFAULT 0,
                last_roll_day INTEGER NOT NULL DEFAULT -1
            )""";

        String legendaryClaimed = """
            CREATE TABLE IF NOT EXISTS legendary_claimed (
                legendary_id TEXT PRIMARY KEY COLLATE NOCASE,
                claimed_at   INTEGER NOT NULL,
                claimed_by   TEXT
            )""";

        String legendaryInstances = """
            CREATE TABLE IF NOT EXISTS legendary_instances (
                instance_id TEXT PRIMARY KEY,
                legendary_id TEXT NOT NULL COLLATE NOCASE,
                claimed_at INTEGER NOT NULL,
                owner_uuid TEXT,
                source_key TEXT
            )""";

        String legendaryInstancesByType = """
            CREATE INDEX IF NOT EXISTS idx_legendary_instances_type
            ON legendary_instances(legendary_id COLLATE NOCASE)
            """;

        String customBosses = """
            CREATE TABLE IF NOT EXISTS custom_bosses (
                entity_uuid TEXT PRIMARY KEY,
                boss_id     TEXT    NOT NULL COLLATE NOCASE,
                world       TEXT    NOT NULL,
                x           REAL    NOT NULL,
                y           REAL    NOT NULL,
                z           REAL    NOT NULL,
                chunk_x     INTEGER NOT NULL,
                chunk_z     INTEGER NOT NULL,
                spawned_at  INTEGER NOT NULL
            )""";

        String customBossesByType = """
            CREATE INDEX IF NOT EXISTS idx_custom_bosses_type
            ON custom_bosses(boss_id COLLATE NOCASE)
            """;

        String managedItemInstances = """
            CREATE TABLE IF NOT EXISTS managed_item_instances (
                instance_id        TEXT PRIMARY KEY,
                item_key           TEXT NOT NULL,
                created_at         INTEGER NOT NULL,
                created_method     TEXT NOT NULL,
                created_by_uuid    TEXT,
                created_by_name    TEXT,
                current_owner_uuid TEXT,
                current_owner_name TEXT,
                first_seen_at      INTEGER NOT NULL,
                last_seen_at       INTEGER NOT NULL
            )""";

        String managedItemInstancesByOwner = """
            CREATE INDEX IF NOT EXISTS idx_managed_item_instances_owner
            ON managed_item_instances(current_owner_uuid)
            """;

        String managedItemEvents = """
            CREATE TABLE IF NOT EXISTS managed_item_events (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                logged_at   INTEGER NOT NULL,
                instance_id TEXT NOT NULL,
                item_key    TEXT NOT NULL,
                subject_uuid TEXT,
                subject_name TEXT,
                actor_uuid   TEXT,
                actor_name   TEXT,
                event_type   TEXT NOT NULL,
                method       TEXT NOT NULL,
                details      TEXT
            )""";

        String managedItemEventsBySubject = """
            CREATE INDEX IF NOT EXISTS idx_managed_item_events_subject
            ON managed_item_events(subject_uuid, logged_at DESC)
            """;

        String managedItemEventsByInstance = """
            CREATE INDEX IF NOT EXISTS idx_managed_item_events_instance
            ON managed_item_events(instance_id, logged_at DESC)
            """;

        String leaderboardStats = """
            CREATE TABLE IF NOT EXISTS leaderboard_stats (
                player_uuid  TEXT PRIMARY KEY,
                player_name  TEXT NOT NULL,
                player_kills INTEGER NOT NULL DEFAULT 0,
                deaths       INTEGER NOT NULL DEFAULT 0,
                boss_kills   INTEGER NOT NULL DEFAULT 0,
                boss_damage  INTEGER NOT NULL DEFAULT 0,
                boss_fights  INTEGER NOT NULL DEFAULT 0,
                mob_kills    INTEGER NOT NULL DEFAULT 0,
                playtime_seconds INTEGER NOT NULL DEFAULT 0,
                updated_at   INTEGER NOT NULL DEFAULT 0
            )""";

        String leaderboardPlayerKills = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_player_kills
            ON leaderboard_stats(player_kills DESC)
            """;

        String leaderboardDeaths = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_deaths
            ON leaderboard_stats(deaths DESC)
            """;

        String leaderboardBossKills = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_boss_kills
            ON leaderboard_stats(boss_kills DESC)
            """;

        String leaderboardMobKills = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_mob_kills
            ON leaderboard_stats(mob_kills DESC)
            """;

        String leaderboardBossDamage = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_boss_damage
            ON leaderboard_stats(boss_damage DESC)
            """;

        String leaderboardBossFights = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_boss_fights
            ON leaderboard_stats(boss_fights DESC)
            """;

        String leaderboardPlaytime = """
            CREATE INDEX IF NOT EXISTS idx_leaderboard_playtime
            ON leaderboard_stats(playtime_seconds DESC)
            """;

        String bossFights = """
            CREATE TABLE IF NOT EXISTS boss_fights (
                fight_id     TEXT PRIMARY KEY,
                boss_id      TEXT    NOT NULL COLLATE NOCASE,
                outcome      TEXT    NOT NULL,
                started_at   INTEGER NOT NULL,
                ended_at     INTEGER NOT NULL,
                duration_ms  INTEGER NOT NULL,
                double_drops INTEGER NOT NULL DEFAULT 0,
                total_damage REAL    NOT NULL DEFAULT 0,
                total_healing REAL   NOT NULL DEFAULT 0
            )""";

        String bossFightParticipants = """
            CREATE TABLE IF NOT EXISTS boss_fight_participants (
                fight_id         TEXT    NOT NULL,
                player_uuid      TEXT    NOT NULL,
                player_name      TEXT    NOT NULL,
                damage_done      REAL    NOT NULL DEFAULT 0,
                healing_received REAL    NOT NULL DEFAULT 0,
                rank             INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (fight_id, player_uuid)
            )""";

        String bossFightParticipantsByPlayer = """
            CREATE INDEX IF NOT EXISTS idx_boss_fight_participants_player
            ON boss_fight_participants(player_uuid, fight_id)
            """;

        String bossFightsByEndedAt = """
            CREATE INDEX IF NOT EXISTS idx_boss_fights_ended_at
            ON boss_fights(ended_at DESC)
            """;

        try (Connection conn = connection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(spawners);
            stmt.executeUpdate(homes);
            stmt.executeUpdate(players);
            stmt.executeUpdate(teams);
            stmt.executeUpdate(teamMembers);
            stmt.executeUpdate(teamMembersByPlayer);
            stmt.executeUpdate(teamVaults);
            stmt.executeUpdate(waystones);
            stmt.executeUpdate(waystoneNames);
            stmt.executeUpdate(waystoneKnown);
            stmt.executeUpdate(waystoneKnownByPlayer);
            stmt.executeUpdate(legendaryAltar);
            stmt.executeUpdate(legendaryClaimed);
            stmt.executeUpdate(legendaryInstances);
            stmt.executeUpdate(legendaryInstancesByType);
            stmt.executeUpdate(customBosses);
            stmt.executeUpdate(customBossesByType);
            stmt.executeUpdate(managedItemInstances);
            stmt.executeUpdate(managedItemInstancesByOwner);
            stmt.executeUpdate(managedItemEvents);
            stmt.executeUpdate(managedItemEventsBySubject);
            stmt.executeUpdate(managedItemEventsByInstance);
            stmt.executeUpdate(leaderboardStats);
            stmt.executeUpdate(leaderboardPlayerKills);
            stmt.executeUpdate(leaderboardDeaths);
            stmt.executeUpdate(leaderboardBossKills);
            stmt.executeUpdate(leaderboardMobKills);
            stmt.executeUpdate(bossFights);
            stmt.executeUpdate(bossFightParticipants);
            stmt.executeUpdate(bossFightParticipantsByPlayer);
            stmt.executeUpdate(bossFightsByEndedAt);
            ensureColumn(conn, "teams", "color", "TEXT NOT NULL DEFAULT 'gold'");
            ensureColumn(conn, "legendary_instances", "source_key", "TEXT");
            ensureColumn(conn, "leaderboard_stats", "boss_damage", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(conn, "leaderboard_stats", "boss_fights", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(conn, "leaderboard_stats", "playtime_seconds", "INTEGER NOT NULL DEFAULT 0");
            stmt.executeUpdate(leaderboardBossDamage);
            stmt.executeUpdate(leaderboardBossFights);
            stmt.executeUpdate(leaderboardPlaytime);
        }
    }

    private void ensureColumn(Connection conn, String table, String column, String definition) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    // ── Spawner Queries ───────────────────────────────────────────────────────

    public CompletableFuture<List<SpawnerData>> loadAllSpawners() {
        return CompletableFuture.supplyAsync(() -> {
            List<SpawnerData> list = new ArrayList<>();
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM spawners");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(spawnerFromRs(rs));
            } catch (SQLException e) {
                plugin.getLogger().severe("loadAllSpawners: " + e.getMessage());
                throw new RuntimeException("loadAllSpawners failed", e);
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Void> saveSpawner(SpawnerData d) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO spawners (world,x,y,z,entity_type,stack_count,sugar_count,redstone_controlled,ai_nerfed)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(world,x,y,z) DO UPDATE SET
                    entity_type=excluded.entity_type,
                    stack_count=excluded.stack_count,
                    sugar_count=excluded.sugar_count,
                    redstone_controlled=excluded.redstone_controlled,
                    ai_nerfed=excluded.ai_nerfed
                """;
            try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, d.world());
                ps.setInt(2, d.x());
                ps.setInt(3, d.y());
                ps.setInt(4, d.z());
                ps.setString(5, d.entityType());
                ps.setInt(6, d.stackCount());
                ps.setInt(7, d.sugarCount());
                ps.setInt(8, d.redstoneControlled() ? 1 : 0);
                ps.setInt(9, d.aiNerfed() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveSpawner: " + e.getMessage());
                throw new RuntimeException("saveSpawner failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteSpawner(String world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM spawners WHERE world=? AND x=? AND y=? AND z=?")) {
                ps.setString(1, world); ps.setInt(2, x); ps.setInt(3, y); ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteSpawner: " + e.getMessage());
                throw new RuntimeException("deleteSpawner failed", e);
            }
        }, executor);
    }

    private SpawnerData spawnerFromRs(ResultSet rs) throws SQLException {
        return new SpawnerData(
            rs.getString("world"),
            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
            rs.getString("entity_type"),
            rs.getInt("stack_count"),
            rs.getInt("sugar_count"),
            rs.getInt("redstone_controlled") == 1,
            rs.getInt("ai_nerfed") == 1
        );
    }

    // ── Home Queries ──────────────────────────────────────────────────────────

    public CompletableFuture<List<HomeEntry>> loadHomes(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<HomeEntry> list = new ArrayList<>();
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM homes WHERE player_uuid=?")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(homeFromRs(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadHomes: " + e.getMessage());
                throw new RuntimeException("loadHomes failed", e);
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Void> saveHome(UUID playerUuid, HomeEntry home) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO homes (player_uuid,name,world,x,y,z,yaw,pitch)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(player_uuid,name) DO UPDATE SET
                    world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                    yaw=excluded.yaw, pitch=excluded.pitch
                """;
            try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, home.name());
                ps.setString(3, home.world());
                ps.setDouble(4, home.x()); ps.setDouble(5, home.y()); ps.setDouble(6, home.z());
                ps.setFloat(7, home.yaw()); ps.setFloat(8, home.pitch());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveHome: " + e.getMessage());
                throw new RuntimeException("saveHome failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteHome(UUID playerUuid, String name) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM homes WHERE player_uuid=? AND name=?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, name);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteHome: " + e.getMessage());
                throw new RuntimeException("deleteHome failed", e);
            }
        }, executor);
    }

    private HomeEntry homeFromRs(ResultSet rs) throws SQLException {
        return new HomeEntry(
            rs.getString("name"),
            rs.getString("world"),
            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
            rs.getFloat("yaw"), rs.getFloat("pitch")
        );
    }

    // —— Waystone Queries ———————————————————————————————————————————————————————————————————————

    public CompletableFuture<List<WaystoneEntry>> loadAllWaystones() {
        return CompletableFuture.supplyAsync(() -> {
            List<WaystoneEntry> list = new ArrayList<>();
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name, world, x, y, z FROM waystones");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new WaystoneEntry(
                        rs.getString("name"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadAllWaystones: " + e.getMessage());
                throw new RuntimeException("loadAllWaystones failed", e);
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Optional<WaystoneEntry>> getWaystoneByLocation(String world, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name, world, x, y, z FROM waystones WHERE world=? AND x=? AND y=? AND z=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new WaystoneEntry(
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getWaystoneByLocation: " + e.getMessage());
                throw new RuntimeException("getWaystoneByLocation failed", e);
            }
            return Optional.empty();
        }, executor);
    }

    public CompletableFuture<Boolean> createWaystone(WaystoneEntry entry, UUID createdBy) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO waystones (world, x, y, z, name, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entry.world());
                ps.setInt(2, entry.x());
                ps.setInt(3, entry.y());
                ps.setInt(4, entry.z());
                ps.setString(5, entry.name());
                ps.setString(6, createdBy == null ? null : createdBy.toString());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (isConstraintViolation(e)) return false;
                plugin.getLogger().severe("createWaystone: " + e.getMessage());
                throw new RuntimeException("createWaystone failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Set<String>> loadKnownWaystoneKeys(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> keys = new LinkedHashSet<>();
            String sql = "SELECT world, x, y, z FROM waystone_known WHERE player_uuid=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        keys.add(WaystoneEntry.key(
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadKnownWaystoneKeys: " + e.getMessage());
                throw new RuntimeException("loadKnownWaystoneKeys failed", e);
            }
            return keys;
        }, executor);
    }

    public CompletableFuture<List<WaystoneEntry>> loadKnownWaystones(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<WaystoneEntry> list = new ArrayList<>();
            String sql = """
                SELECT w.name, w.world, w.x, w.y, w.z
                FROM waystone_known k
                JOIN waystones w
                  ON w.world = k.world AND w.x = k.x AND w.y = k.y AND w.z = k.z
                WHERE k.player_uuid = ?
                ORDER BY w.name COLLATE NOCASE ASC
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new WaystoneEntry(
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadKnownWaystones: " + e.getMessage());
                throw new RuntimeException("loadKnownWaystones failed", e);
            }
            return list;
        }, executor);
    }

    public CompletableFuture<Boolean> addKnownWaystone(UUID playerUuid, WaystoneEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO waystone_known (player_uuid, world, x, y, z)
                VALUES (?, ?, ?, ?, ?)
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, entry.world());
                ps.setInt(3, entry.x());
                ps.setInt(4, entry.y());
                ps.setInt(5, entry.z());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (isConstraintViolation(e)) return false;
                plugin.getLogger().severe("addKnownWaystone: " + e.getMessage());
                throw new RuntimeException("addKnownWaystone failed", e);
            }
        }, executor);
    }

    // —— Team Queries ——————————————————————————————————————————————————————————————————————————————

    public CompletableFuture<Void> deleteWaystone(String world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement psKnown = conn.prepareStatement(
                        "DELETE FROM waystone_known WHERE world=? AND x=? AND y=? AND z=?");
                     PreparedStatement psWaystone = conn.prepareStatement(
                        "DELETE FROM waystones WHERE world=? AND x=? AND y=? AND z=?")) {
                    psKnown.setString(1, world);
                    psKnown.setInt(2, x);
                    psKnown.setInt(3, y);
                    psKnown.setInt(4, z);
                    psKnown.executeUpdate();

                    psWaystone.setString(1, world);
                    psWaystone.setInt(2, x);
                    psWaystone.setInt(3, y);
                    psWaystone.setInt(4, z);
                    psWaystone.executeUpdate();
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteWaystone: " + e.getMessage());
                throw new RuntimeException("deleteWaystone failed", e);
            }
        }, executor);
    }

    public CompletableFuture<List<TeamRecord>> loadTeams() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, TeamRecord> byName = new LinkedHashMap<>();
            try (Connection conn = connection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT name, owner_uuid, color FROM teams");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                        String color = rs.getString("color");
                        byName.put(name.toLowerCase(Locale.ROOT), new TeamRecord(name, owner, color, new LinkedHashSet<>()));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT team_name, player_uuid FROM team_members");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String teamName = rs.getString("team_name");
                        UUID member = UUID.fromString(rs.getString("player_uuid"));
                        TeamRecord record = byName.get(teamName.toLowerCase(Locale.ROOT));
                        if (record != null) {
                            record.members().add(member);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadTeams: " + e.getMessage());
                throw new RuntimeException("loadTeams failed", e);
            }

            return new ArrayList<>(byName.values());
        }, executor);
    }

    public CompletableFuture<Boolean> createTeam(String name, UUID ownerUuid, String color) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO teams (name, owner_uuid, color, created_at) VALUES (?, ?, ?, ?)";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, ownerUuid.toString());
                ps.setString(3, color == null || color.isBlank() ? "gold" : color);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (isConstraintViolation(e)) return false;
                plugin.getLogger().severe("createTeam: " + e.getMessage());
                throw new RuntimeException("createTeam failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteTeam(String name) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement psMembers = conn.prepareStatement("DELETE FROM team_members WHERE team_name=?");
                     PreparedStatement psVault = conn.prepareStatement("DELETE FROM team_vaults WHERE team_name=?");
                     PreparedStatement psTeam = conn.prepareStatement("DELETE FROM teams WHERE name=?")) {
                    psMembers.setString(1, name);
                    psMembers.executeUpdate();
                    psVault.setString(1, name);
                    psVault.executeUpdate();
                    psTeam.setString(1, name);
                    psTeam.executeUpdate();
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteTeam: " + e.getMessage());
                throw new RuntimeException("deleteTeam failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> addTeamMember(String teamName, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO team_members (team_name, player_uuid) VALUES (?, ?)";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, teamName);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                if (isConstraintViolation(e)) return;
                plugin.getLogger().severe("addTeamMember: " + e.getMessage());
                throw new RuntimeException("addTeamMember failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> removeTeamMember(String teamName, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM team_members WHERE team_name=? AND player_uuid=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, teamName);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("removeTeamMember: " + e.getMessage());
                throw new RuntimeException("removeTeamMember failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> setTeamOwner(String teamName, UUID ownerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE teams SET owner_uuid=? WHERE name=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, teamName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("setTeamOwner: " + e.getMessage());
                throw new RuntimeException("setTeamOwner failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> setTeamColor(String teamName, String color) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE teams SET color=? WHERE name=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, color == null || color.isBlank() ? "gold" : color);
                ps.setString(2, teamName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("setTeamColor: " + e.getMessage());
                throw new RuntimeException("setTeamColor failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> renameTeam(String oldName, String newName) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement psTeam = conn.prepareStatement("UPDATE teams SET name=? WHERE name=?");
                     PreparedStatement psMembers = conn.prepareStatement("UPDATE team_members SET team_name=? WHERE team_name=?");
                     PreparedStatement psVault = conn.prepareStatement("UPDATE team_vaults SET team_name=? WHERE team_name=?")) {
                    psTeam.setString(1, newName);
                    psTeam.setString(2, oldName);
                    int updated = psTeam.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        return false;
                    }

                    psMembers.setString(1, newName);
                    psMembers.setString(2, oldName);
                    psMembers.executeUpdate();

                    psVault.setString(1, newName);
                    psVault.setString(2, oldName);
                    psVault.executeUpdate();

                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    if (isConstraintViolation(e)) {
                        return false;
                    }
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("renameTeam: " + e.getMessage());
                throw new RuntimeException("renameTeam failed", e);
            }
        }, executor);
    }

    // ── Player Queries ────────────────────────────────────────────────────────

    public CompletableFuture<byte[]> loadTeamVault(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT contents FROM team_vaults WHERE team_name=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, teamName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new byte[0];
                    }
                    byte[] raw = rs.getBytes("contents");
                    return raw == null ? new byte[0] : raw;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadTeamVault: " + e.getMessage());
                throw new RuntimeException("loadTeamVault failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveTeamVault(String teamName, byte[] contents) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO team_vaults (team_name, contents)
                VALUES (?, ?)
                ON CONFLICT(team_name) DO UPDATE SET
                    contents = excluded.contents
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, teamName);
                ps.setBytes(2, contents == null ? new byte[0] : contents);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveTeamVault: " + e.getMessage());
                throw new RuntimeException("saveTeamVault failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteTeamVault(String teamName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM team_vaults WHERE team_name=?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, teamName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteTeamVault: " + e.getMessage());
                throw new RuntimeException("deleteTeamVault failed", e);
            }
        }, executor);
    }

    public CompletableFuture<LegendaryAltarRecord> loadLegendaryAltar() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT legendary_id, world, x, y, z, spawned_at, activates_at, expires_at, last_roll_day
                FROM legendary_altar
                WHERE id = 1
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LegendaryAltarRecord(
                        rs.getString("legendary_id"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getLong("spawned_at"),
                        rs.getLong("activates_at"),
                        rs.getLong("expires_at"),
                        rs.getLong("last_roll_day")
                    );
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadLegendaryAltar: " + e.getMessage());
                throw new RuntimeException("loadLegendaryAltar failed", e);
            }
            return LegendaryAltarRecord.empty();
        }, executor);
    }

    public CompletableFuture<Map<String, UUID>> loadClaimedLegendaryOwners() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, UUID> owners = new LinkedHashMap<>();
            String sql = "SELECT legendary_id, claimed_by FROM legendary_claimed";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String legendaryId = rs.getString("legendary_id");
                    String claimedBy = rs.getString("claimed_by");
                    if (legendaryId == null || legendaryId.isBlank() || claimedBy == null || claimedBy.isBlank()) {
                        continue;
                    }
                    try {
                        owners.put(legendaryId.trim().toLowerCase(Locale.ROOT), UUID.fromString(claimedBy));
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed rows rather than locking all legendary claims behind bad data.
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadClaimedLegendaryOwners: " + e.getMessage());
                throw new RuntimeException("loadClaimedLegendaryOwners failed", e);
            }
            return owners;
        }, executor);
    }

    public CompletableFuture<Set<String>> loadClaimedLegendaryIds() {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> ids = new LinkedHashSet<>();
            String sql = "SELECT legendary_id FROM legendary_claimed";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String legendaryId = rs.getString("legendary_id");
                    if (legendaryId != null && !legendaryId.isBlank()) {
                        ids.add(legendaryId.trim().toLowerCase(Locale.ROOT));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadClaimedLegendaryIds: " + e.getMessage());
                throw new RuntimeException("loadClaimedLegendaryIds failed", e);
            }
            return ids;
        }, executor);
    }

    public CompletableFuture<Map<String, LegendaryClaimedInstanceRecord>> loadClaimedLegendaryInstances() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, LegendaryClaimedInstanceRecord> instances = new LinkedHashMap<>();
            String sql = "SELECT instance_id, legendary_id, owner_uuid, source_key FROM legendary_instances";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String instanceId = rs.getString("instance_id");
                    String legendaryId = rs.getString("legendary_id");
                    String ownerUuid = rs.getString("owner_uuid");
                    if (instanceId == null || instanceId.isBlank() || legendaryId == null || legendaryId.isBlank()) {
                        continue;
                    }
                    UUID ownerId = null;
                    if (ownerUuid != null && !ownerUuid.isBlank()) {
                        try {
                            ownerId = UUID.fromString(ownerUuid);
                        } catch (IllegalArgumentException ignored) {
                            ownerId = null;
                        }
                    }
                    instances.put(
                        instanceId,
                        new LegendaryClaimedInstanceRecord(
                            instanceId,
                            legendaryId.trim().toLowerCase(Locale.ROOT),
                            ownerId,
                            normalizedSourceKey(rs.getString("source_key"))
                        )
                    );
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadClaimedLegendaryInstances: " + e.getMessage());
                throw new RuntimeException("loadClaimedLegendaryInstances failed", e);
            }
            return instances;
        }, executor);
    }

    public CompletableFuture<Void> saveClaimedLegendaryOwner(String legendaryId, UUID claimedBy) {
        return CompletableFuture.runAsync(() -> {
            if (legendaryId == null || legendaryId.isBlank()) {
                return;
            }
            String sql = """
                INSERT INTO legendary_claimed (legendary_id, claimed_at, claimed_by)
                VALUES (?, ?, ?)
                ON CONFLICT(legendary_id) DO UPDATE SET
                    claimed_at = CASE
                        WHEN excluded.claimed_by IS NULL AND legendary_claimed.claimed_by IS NOT NULL
                            THEN legendary_claimed.claimed_at
                        ELSE excluded.claimed_at
                    END,
                    claimed_by = COALESCE(excluded.claimed_by, legendary_claimed.claimed_by)
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, legendaryId.trim().toLowerCase(Locale.ROOT));
                ps.setLong(2, System.currentTimeMillis());
                if (claimedBy == null) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, claimedBy.toString());
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveClaimedLegendaryOwner: " + e.getMessage());
                throw new RuntimeException("saveClaimedLegendaryOwner failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveClaimedLegendaryInstance(String instanceId, String legendaryId, UUID ownerId) {
        return saveClaimedLegendaryInstance(instanceId, legendaryId, ownerId, null);
    }

    public CompletableFuture<Void> saveClaimedLegendaryInstance(String instanceId, String legendaryId, UUID ownerId, String sourceKey) {
        return CompletableFuture.runAsync(() -> {
            if (instanceId == null || instanceId.isBlank() || legendaryId == null || legendaryId.isBlank()) {
                return;
            }
            String sql = """
                INSERT INTO legendary_instances (instance_id, legendary_id, claimed_at, owner_uuid, source_key)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(instance_id) DO UPDATE SET
                    legendary_id = excluded.legendary_id,
                    claimed_at = excluded.claimed_at,
                    owner_uuid = excluded.owner_uuid,
                    source_key = excluded.source_key
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, instanceId.trim());
                ps.setString(2, legendaryId.trim().toLowerCase(Locale.ROOT));
                ps.setLong(3, System.currentTimeMillis());
                if (ownerId == null) {
                    ps.setNull(4, Types.VARCHAR);
                } else {
                    ps.setString(4, ownerId.toString());
                }
                String normalizedSource = normalizedSourceKey(sourceKey);
                if (normalizedSource == null) {
                    ps.setNull(5, Types.VARCHAR);
                } else {
                    ps.setString(5, normalizedSource);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveClaimedLegendaryInstance: " + e.getMessage());
                throw new RuntimeException("saveClaimedLegendaryInstance failed", e);
            }
        }, executor);
    }

    private String normalizedSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return null;
        }
        String normalized = sourceKey.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public CompletableFuture<Void> deleteClaimedLegendary(String legendaryId) {
        return CompletableFuture.runAsync(() -> {
            if (legendaryId == null || legendaryId.isBlank()) {
                return;
            }
            String sql = "DELETE FROM legendary_claimed WHERE legendary_id = ?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, legendaryId.trim().toLowerCase(Locale.ROOT));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteClaimedLegendary: " + e.getMessage());
                throw new RuntimeException("deleteClaimedLegendary failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteClaimedLegendaryInstance(String instanceId) {
        return CompletableFuture.runAsync(() -> {
            if (instanceId == null || instanceId.isBlank()) {
                return;
            }
            String sql = "DELETE FROM legendary_instances WHERE instance_id = ?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, instanceId.trim());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteClaimedLegendaryInstance: " + e.getMessage());
                throw new RuntimeException("deleteClaimedLegendaryInstance failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveLegendaryAltar(LegendaryAltarRecord record) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO legendary_altar (id, legendary_id, world, x, y, z, spawned_at, activates_at, expires_at, last_roll_day)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    legendary_id = excluded.legendary_id,
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    spawned_at = excluded.spawned_at,
                    activates_at = excluded.activates_at,
                    expires_at = excluded.expires_at,
                    last_roll_day = excluded.last_roll_day
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                if (record.legendaryId() == null) {
                    ps.setNull(1, Types.VARCHAR);
                } else {
                    ps.setString(1, record.legendaryId());
                }
                if (record.world() == null) {
                    ps.setNull(2, Types.VARCHAR);
                    ps.setNull(3, Types.INTEGER);
                    ps.setNull(4, Types.INTEGER);
                    ps.setNull(5, Types.INTEGER);
                } else {
                    ps.setString(2, record.world());
                    ps.setInt(3, record.x());
                    ps.setInt(4, record.y());
                    ps.setInt(5, record.z());
                }
                ps.setLong(6, record.spawnedAt());
                ps.setLong(7, record.activatesAt());
                ps.setLong(8, record.expiresAt());
                ps.setLong(9, record.lastRollDay());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveLegendaryAltar: " + e.getMessage());
                throw new RuntimeException("saveLegendaryAltar failed", e);
            }
        }, executor);
    }

    public CompletableFuture<List<BossRecord>> loadAllBosses() {
        return CompletableFuture.supplyAsync(() -> {
            List<BossRecord> bosses = new ArrayList<>();
            String sql = """
                SELECT entity_uuid, boss_id, world, x, y, z, chunk_x, chunk_z, spawned_at
                FROM custom_bosses
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidText = rs.getString("entity_uuid");
                    String bossId = rs.getString("boss_id");
                    String world = rs.getString("world");
                    if (uuidText == null || uuidText.isBlank() || bossId == null || bossId.isBlank() || world == null || world.isBlank()) {
                        continue;
                    }
                    UUID entityUuid;
                    try {
                        entityUuid = UUID.fromString(uuidText);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    bosses.add(new BossRecord(
                        entityUuid,
                        bossId.trim().toLowerCase(Locale.ROOT),
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getInt("chunk_x"),
                        rs.getInt("chunk_z"),
                        rs.getLong("spawned_at")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadAllBosses: " + e.getMessage());
                throw new RuntimeException("loadAllBosses failed", e);
            }
            return bosses;
        }, executor);
    }

    public CompletableFuture<Void> saveBossRecord(BossRecord record) {
        return CompletableFuture.runAsync(() -> {
            if (record == null || record.entityUuid() == null || record.bossId() == null || record.bossId().isBlank() || record.world() == null || record.world().isBlank()) {
                return;
            }
            String sql = """
                INSERT INTO custom_bosses (entity_uuid, boss_id, world, x, y, z, chunk_x, chunk_z, spawned_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(entity_uuid) DO UPDATE SET
                    boss_id = excluded.boss_id,
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    chunk_x = excluded.chunk_x,
                    chunk_z = excluded.chunk_z,
                    spawned_at = excluded.spawned_at
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.entityUuid().toString());
                ps.setString(2, record.bossId().trim().toLowerCase(Locale.ROOT));
                ps.setString(3, record.world());
                ps.setDouble(4, record.x());
                ps.setDouble(5, record.y());
                ps.setDouble(6, record.z());
                ps.setInt(7, record.chunkX());
                ps.setInt(8, record.chunkZ());
                ps.setLong(9, record.spawnedAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveBossRecord: " + e.getMessage());
                throw new RuntimeException("saveBossRecord failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> deleteBossRecord(UUID entityUuid) {
        return CompletableFuture.runAsync(() -> {
            if (entityUuid == null) {
                return;
            }
            String sql = "DELETE FROM custom_bosses WHERE entity_uuid = ?";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entityUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("deleteBossRecord: " + e.getMessage());
                throw new RuntimeException("deleteBossRecord failed", e);
            }
        }, executor);
    }

    public CompletableFuture<List<ManagedItemInstanceRecord>> loadManagedItemInstances() {
        return CompletableFuture.supplyAsync(() -> {
            List<ManagedItemInstanceRecord> records = new ArrayList<>();
            String sql = """
                SELECT instance_id, item_key, created_at, created_method, created_by_uuid, created_by_name,
                       current_owner_uuid, current_owner_name, first_seen_at, last_seen_at
                FROM managed_item_instances
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new ManagedItemInstanceRecord(
                        rs.getString("instance_id"),
                        rs.getString("item_key"),
                        rs.getLong("created_at"),
                        rs.getString("created_method"),
                        parseUuid(rs.getString("created_by_uuid")),
                        rs.getString("created_by_name"),
                        parseUuid(rs.getString("current_owner_uuid")),
                        rs.getString("current_owner_name"),
                        rs.getLong("first_seen_at"),
                        rs.getLong("last_seen_at")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadManagedItemInstances: " + e.getMessage());
                throw new RuntimeException("loadManagedItemInstances failed", e);
            }
            return records;
        }, executor);
    }

    public CompletableFuture<Void> saveManagedItemInstance(ManagedItemInstanceRecord record) {
        return CompletableFuture.runAsync(() -> {
            if (record == null || record.instanceId() == null || record.instanceId().isBlank() || record.itemKey() == null || record.itemKey().isBlank()) {
                return;
            }
            String sql = """
                INSERT INTO managed_item_instances (
                    instance_id, item_key, created_at, created_method, created_by_uuid, created_by_name,
                    current_owner_uuid, current_owner_name, first_seen_at, last_seen_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(instance_id) DO UPDATE SET
                    item_key = excluded.item_key,
                    created_at = excluded.created_at,
                    created_method = excluded.created_method,
                    created_by_uuid = excluded.created_by_uuid,
                    created_by_name = excluded.created_by_name,
                    current_owner_uuid = excluded.current_owner_uuid,
                    current_owner_name = excluded.current_owner_name,
                    first_seen_at = excluded.first_seen_at,
                    last_seen_at = excluded.last_seen_at
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.instanceId());
                ps.setString(2, record.itemKey());
                ps.setLong(3, record.createdAt());
                ps.setString(4, record.createdMethod() == null || record.createdMethod().isBlank() ? "unknown" : record.createdMethod());
                setUuid(ps, 5, record.createdByUuid());
                ps.setString(6, record.createdByName());
                setUuid(ps, 7, record.currentOwnerUuid());
                ps.setString(8, record.currentOwnerName());
                ps.setLong(9, record.firstSeenAt());
                ps.setLong(10, record.lastSeenAt());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveManagedItemInstance: " + e.getMessage());
                throw new RuntimeException("saveManagedItemInstance failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveManagedItemEvent(ManagedItemEventRecord record) {
        return CompletableFuture.runAsync(() -> {
            if (record == null || record.itemKey() == null || record.itemKey().isBlank() || record.eventType() == null || record.eventType().isBlank()) {
                return;
            }
            String sql = """
                INSERT INTO managed_item_events (
                    logged_at, instance_id, item_key, subject_uuid, subject_name,
                    actor_uuid, actor_name, event_type, method, details
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, record.loggedAt());
                ps.setString(2, record.instanceId() == null ? "" : record.instanceId());
                ps.setString(3, record.itemKey());
                setUuid(ps, 4, record.subjectUuid());
                ps.setString(5, record.subjectName());
                setUuid(ps, 6, record.actorUuid());
                ps.setString(7, record.actorName());
                ps.setString(8, record.eventType());
                ps.setString(9, record.method() == null || record.method().isBlank() ? "unknown" : record.method());
                ps.setString(10, record.details());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("saveManagedItemEvent: " + e.getMessage());
                throw new RuntimeException("saveManagedItemEvent failed", e);
            }
        }, executor);
    }

    public CompletableFuture<List<ManagedItemEventRecord>> loadManagedItemEvents(UUID subjectUuid, String itemKeyFilter, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<ManagedItemEventRecord> records = new ArrayList<>();
            if (subjectUuid == null) {
                return records;
            }

            String sql = """
                SELECT id, logged_at, instance_id, item_key, subject_uuid, subject_name,
                       actor_uuid, actor_name, event_type, method, details
                FROM managed_item_events
                WHERE subject_uuid = ?
                  AND (? IS NULL OR item_key = ?)
                ORDER BY logged_at DESC, id DESC
                LIMIT ?
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, subjectUuid.toString());
                if (itemKeyFilter == null || itemKeyFilter.isBlank()) {
                    ps.setNull(2, Types.VARCHAR);
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(2, itemKeyFilter);
                    ps.setString(3, itemKeyFilter);
                }
                ps.setInt(4, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(new ManagedItemEventRecord(
                            rs.getLong("id"),
                            rs.getLong("logged_at"),
                            rs.getString("instance_id"),
                            rs.getString("item_key"),
                            parseUuid(rs.getString("subject_uuid")),
                            rs.getString("subject_name"),
                            parseUuid(rs.getString("actor_uuid")),
                            rs.getString("actor_name"),
                            rs.getString("event_type"),
                            rs.getString("method"),
                            rs.getString("details")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadManagedItemEvents: " + e.getMessage());
                throw new RuntimeException("loadManagedItemEvents failed", e);
            }
            return records;
        }, executor);
    }

    public CompletableFuture<Void> incrementLeaderboardStat(UUID playerUuid, String playerName, String statColumn, int amount) {
        return CompletableFuture.runAsync(() -> {
            String column = leaderboardColumn(statColumn);
            if (playerUuid == null || column == null || amount <= 0) {
                return;
            }

            String safeName = playerName == null || playerName.isBlank() ? "Unknown" : playerName;
            String sql = """
                INSERT INTO leaderboard_stats (player_uuid, player_name, %s, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    %s = leaderboard_stats.%s + excluded.%s,
                    updated_at = excluded.updated_at
                """.formatted(column, column, column, column);
            try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, safeName);
                ps.setInt(3, amount);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("incrementLeaderboardStat: " + e.getMessage());
                throw new RuntimeException("incrementLeaderboardStat failed", e);
            }
        }, executor);
    }

    public CompletableFuture<List<LeaderboardEntry>> loadLeaderboard(String statColumn, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> records = new ArrayList<>();
            String column = leaderboardColumn(statColumn);
            if (column == null) {
                return records;
            }

            String sql = """
                SELECT player_uuid, player_name, %s AS value
                FROM leaderboard_stats
                WHERE %s > 0
                ORDER BY %s DESC, updated_at ASC
                LIMIT ?
                """.formatted(column, column, column);
            try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, Math.min(100, limit)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(new LeaderboardEntry(
                            parseUuid(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getLong("value")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadLeaderboard: " + e.getMessage());
                throw new RuntimeException("loadLeaderboard failed", e);
            }
            return records;
        }, executor);
    }

    public CompletableFuture<Long> loadLeaderboardStat(UUID playerUuid, String statColumn) {
        return CompletableFuture.supplyAsync(() -> {
            String column = leaderboardColumn(statColumn);
            if (playerUuid == null || column == null) {
                return 0L;
            }

            String sql = "SELECT " + column + " AS value FROM leaderboard_stats WHERE player_uuid = ?";
            try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Math.max(0L, rs.getLong("value")) : 0L;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadLeaderboardStat: " + e.getMessage());
                throw new RuntimeException("loadLeaderboardStat failed", e);
            }
        }, executor);
    }

    public CompletableFuture<Map<UUID, LeaderboardStatsRecord>> loadLeaderboardStats(Collection<UUID> playerUuids) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, LeaderboardStatsRecord> records = new HashMap<>();
            if (playerUuids == null || playerUuids.isEmpty()) {
                return records;
            }

            List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(playerUuids));
            String sqlPrefix = """
                SELECT player_uuid, player_name, player_kills, deaths, boss_kills, mob_kills, boss_damage, boss_fights, playtime_seconds
                FROM leaderboard_stats
                WHERE player_uuid IN (
                """;
            try (Connection conn = connection()) {
                for (int offset = 0; offset < ids.size(); offset += 900) {
                    List<UUID> chunk = ids.subList(offset, Math.min(ids.size(), offset + 900));
                    String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
                    try (PreparedStatement ps = conn.prepareStatement(sqlPrefix + placeholders + ")")) {
                        for (int i = 0; i < chunk.size(); i++) {
                            ps.setString(i + 1, chunk.get(i).toString());
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                UUID playerUuid = parseUuid(rs.getString("player_uuid"));
                                if (playerUuid == null) {
                                    continue;
                                }
                                records.put(playerUuid, new LeaderboardStatsRecord(
                                    playerUuid,
                                    rs.getString("player_name"),
                                    rs.getLong("player_kills"),
                                    rs.getLong("deaths"),
                                    rs.getLong("boss_kills"),
                                    rs.getLong("mob_kills"),
                                    rs.getLong("boss_damage"),
                                    rs.getLong("boss_fights"),
                                    rs.getLong("playtime_seconds")
                                ));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadLeaderboardStats: " + e.getMessage());
                throw new RuntimeException("loadLeaderboardStats failed", e);
            }
            return records;
        }, executor);
    }

    private static String leaderboardColumn(String statColumn) {
        if (statColumn == null) {
            return null;
        }
        return switch (statColumn.toLowerCase(Locale.ROOT)) {
            case "player_kills", "kills", "pkills" -> "player_kills";
            case "deaths" -> "deaths";
            case "boss_kills", "bosses" -> "boss_kills";
            case "boss_damage", "bossdamage", "damage" -> "boss_damage";
            case "boss_fights", "bossfights", "fights" -> "boss_fights";
            case "mob_kills", "mobs" -> "mob_kills";
            case "playtime_seconds", "playtime", "time_played", "timeplayed" -> "playtime_seconds";
            default -> null;
        };
    }

    public CompletableFuture<Void> saveBossFightReport(BossFightRecord fight, List<BossFightParticipantRecord> participants) {
        return CompletableFuture.runAsync(() -> {
            if (fight == null || fight.fightId() == null || fight.fightId().isBlank()
                || fight.bossId() == null || fight.bossId().isBlank()) {
                return;
            }

            String fightSql = """
                INSERT INTO boss_fights (
                    fight_id, boss_id, outcome, started_at, ended_at, duration_ms,
                    double_drops, total_damage, total_healing
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(fight_id) DO UPDATE SET
                    boss_id = excluded.boss_id,
                    outcome = excluded.outcome,
                    started_at = excluded.started_at,
                    ended_at = excluded.ended_at,
                    duration_ms = excluded.duration_ms,
                    double_drops = excluded.double_drops,
                    total_damage = excluded.total_damage,
                    total_healing = excluded.total_healing
                """;
            String participantSql = """
                INSERT INTO boss_fight_participants (
                    fight_id, player_uuid, player_name, damage_done, healing_received, rank
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(fight_id, player_uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    damage_done = excluded.damage_done,
                    healing_received = excluded.healing_received,
                    rank = excluded.rank
                """;

            try (Connection conn = connection()) {
                boolean oldAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try (PreparedStatement fightPs = conn.prepareStatement(fightSql);
                     PreparedStatement participantPs = conn.prepareStatement(participantSql)) {
                    fightPs.setString(1, fight.fightId());
                    fightPs.setString(2, fight.bossId().trim().toLowerCase(Locale.ROOT));
                    fightPs.setString(3, fight.outcome() == null || fight.outcome().isBlank() ? "unknown" : fight.outcome());
                    fightPs.setLong(4, fight.startedAt());
                    fightPs.setLong(5, fight.endedAt());
                    fightPs.setLong(6, Math.max(0L, fight.durationMs()));
                    fightPs.setInt(7, fight.doubleDrops() ? 1 : 0);
                    fightPs.setDouble(8, Math.max(0.0, fight.totalDamage()));
                    fightPs.setDouble(9, Math.max(0.0, fight.totalHealing()));
                    fightPs.executeUpdate();

                    if (participants != null) {
                        for (BossFightParticipantRecord participant : participants) {
                            if (participant == null || participant.playerUuid() == null) {
                                continue;
                            }
                            participantPs.setString(1, fight.fightId());
                            participantPs.setString(2, participant.playerUuid().toString());
                            participantPs.setString(3, participant.playerName() == null || participant.playerName().isBlank() ? "Unknown" : participant.playerName());
                            participantPs.setDouble(4, Math.max(0.0, participant.damageDone()));
                            participantPs.setDouble(5, Math.max(0.0, participant.healingReceived()));
                            participantPs.setInt(6, Math.max(0, participant.rank()));
                            participantPs.addBatch();
                        }
                        participantPs.executeBatch();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(oldAutoCommit);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("saveBossFightReport: " + e.getMessage());
                throw new RuntimeException("saveBossFightReport failed", e);
            }
        }, executor);
    }

    public CompletableFuture<List<BossFightMenuEntry>> loadPlayerBossFightReports(UUID playerUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<BossFightMenuEntry> records = new ArrayList<>();
            if (playerUuid == null) {
                return records;
            }

            String sql = """
                SELECT f.fight_id, f.boss_id, f.outcome, f.started_at, f.ended_at, f.duration_ms,
                       f.double_drops, f.total_damage, f.total_healing,
                       p.damage_done, p.healing_received, p.rank
                FROM boss_fight_participants p
                JOIN boss_fights f ON f.fight_id = p.fight_id
                WHERE p.player_uuid = ?
                ORDER BY f.ended_at DESC
                LIMIT ?
                """;
            try (Connection conn = connection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setInt(2, Math.max(1, Math.min(50, limit)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        records.add(new BossFightMenuEntry(
                            rs.getString("fight_id"),
                            rs.getString("boss_id"),
                            rs.getString("outcome"),
                            rs.getLong("started_at"),
                            rs.getLong("ended_at"),
                            rs.getLong("duration_ms"),
                            rs.getInt("double_drops") == 1,
                            rs.getDouble("total_damage"),
                            rs.getDouble("total_healing"),
                            rs.getDouble("damage_done"),
                            rs.getDouble("healing_received"),
                            rs.getInt("rank")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("loadPlayerBossFightReports: " + e.getMessage());
                throw new RuntimeException("loadPlayerBossFightReports failed", e);
            }
            return records;
        }, executor);
    }

    /** Upsert a player record; returns the join count AFTER this visit. */
    public CompletableFuture<Integer> upsertPlayer(UUID uuid, String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection()) {
                String upsert = """
                    INSERT INTO players (uuid, username, first_join, last_seen, join_count)
                    VALUES (?, ?, ?, ?, 1)
                    ON CONFLICT(uuid) DO UPDATE SET
                        username   = excluded.username,
                        first_join = COALESCE(players.first_join, excluded.first_join),
                        last_seen  = excluded.last_seen,
                        join_count = CASE
                            WHEN players.join_count < 1 THEN 1
                            ELSE players.join_count + 1
                        END
                    """;
                long now = System.currentTimeMillis();
                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setLong(3, now); ps.setLong(4, now);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT join_count FROM players WHERE uuid=?")) {
                    ps2.setString(1, uuid.toString());
                    try (ResultSet rs = ps2.executeQuery()) {
                        return rs.next() ? rs.getInt("join_count") : 1;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("upsertPlayer: " + e.getMessage());
                return 1;
            }
        }, executor);
    }

    public CompletableFuture<Optional<String>> getNickname(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT nickname FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String nick = rs.getString("nickname");
                        return Optional.ofNullable(nick);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("getNickname: " + e.getMessage());
            }
            return Optional.empty();
        }, executor);
    }

    public CompletableFuture<Void> setNickname(UUID uuid, String username, String nickname) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO players (uuid, username, nickname, first_join, last_seen, join_count)
                VALUES (?, ?, ?, NULL, ?, 0)
                ON CONFLICT(uuid) DO UPDATE SET
                    username  = excluded.username,
                    nickname  = excluded.nickname,
                    last_seen = excluded.last_seen
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, nickname);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("setNickname: " + e.getMessage());
                throw new RuntimeException("setNickname failed", e);
            }
        }, executor);
    }

    private static void setUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        if (uuid == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, uuid.toString());
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return "23000".equals(state) || e.getMessage().toLowerCase(Locale.ROOT).contains("constraint");
    }

    public record TeamRecord(String name, UUID ownerUuid, String color, Set<UUID> members) {}
    public record LegendaryClaimedInstanceRecord(String instanceId, String legendaryId, UUID ownerUuid, String sourceKey) {}
    public record ManagedItemInstanceRecord(
        String instanceId,
        String itemKey,
        long createdAt,
        String createdMethod,
        UUID createdByUuid,
        String createdByName,
        UUID currentOwnerUuid,
        String currentOwnerName,
        long firstSeenAt,
        long lastSeenAt
    ) {}
    public record ManagedItemEventRecord(
        long id,
        long loggedAt,
        String instanceId,
        String itemKey,
        UUID subjectUuid,
        String subjectName,
        UUID actorUuid,
        String actorName,
        String eventType,
        String method,
        String details
    ) {}
    public record LeaderboardEntry(UUID playerUuid, String playerName, long value) {}
    public record LeaderboardStatsRecord(
        UUID playerUuid,
        String playerName,
        long playerKills,
        long deaths,
        long bossKills,
        long mobKills,
        long bossDamage,
        long bossFights,
        long playtimeSeconds
    ) {}
    public record BossFightRecord(
        String fightId,
        String bossId,
        String outcome,
        long startedAt,
        long endedAt,
        long durationMs,
        boolean doubleDrops,
        double totalDamage,
        double totalHealing
    ) {}
    public record BossFightParticipantRecord(
        String fightId,
        UUID playerUuid,
        String playerName,
        double damageDone,
        double healingReceived,
        int rank
    ) {}
    public record BossFightMenuEntry(
        String fightId,
        String bossId,
        String outcome,
        long startedAt,
        long endedAt,
        long durationMs,
        boolean doubleDrops,
        double totalDamage,
        double totalHealing,
        double damageDone,
        double healingReceived,
        int rank
    ) {}
    public record LegendaryAltarRecord(
        String legendaryId,
        String world,
        int x,
        int y,
        int z,
        long spawnedAt,
        long activatesAt,
        long expiresAt,
        long lastRollDay
    ) {
        public static LegendaryAltarRecord empty() {
            return new LegendaryAltarRecord(null, null, 0, 0, 0, 0L, 0L, 0L, -1L);
        }

        public boolean hasActiveAltar() {
            return legendaryId != null && world != null && !world.isBlank();
        }
    }
}
