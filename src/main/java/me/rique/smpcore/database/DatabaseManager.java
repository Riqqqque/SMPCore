package me.rique.smpcore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.rique.smpcore.SMPCore;
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
                try (PreparedStatement ps = conn.prepareStatement("SELECT name, owner_uuid FROM teams");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("name");
                        UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                        byName.put(name.toLowerCase(Locale.ROOT), new TeamRecord(name, owner, new LinkedHashSet<>()));
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

    public CompletableFuture<Boolean> createTeam(String name, UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO teams (name, owner_uuid, created_at) VALUES (?, ?, ?)";
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, ownerUuid.toString());
                ps.setLong(3, System.currentTimeMillis());
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

    public CompletableFuture<Void> markLegendaryClaimed(String legendaryId, UUID claimedBy) {
        return CompletableFuture.runAsync(() -> {
            if (legendaryId == null || legendaryId.isBlank()) {
                return;
            }
            String sql = """
                INSERT OR IGNORE INTO legendary_claimed (legendary_id, claimed_at, claimed_by)
                VALUES (?, ?, ?)
                """;
            try (Connection conn = connection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, legendaryId.trim().toLowerCase(Locale.ROOT));
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, claimedBy == null ? null : claimedBy.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("markLegendaryClaimed: " + e.getMessage());
                throw new RuntimeException("markLegendaryClaimed failed", e);
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

    private static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return "23000".equals(state) || e.getMessage().toLowerCase(Locale.ROOT).contains("constraint");
    }

    public record TeamRecord(String name, UUID ownerUuid, Set<UUID> members) {}
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
