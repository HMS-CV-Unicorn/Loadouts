package com.saratoga.loadouts.data;

import com.saratoga.loadouts.Loadouts;
import com.saratoga.loadouts.LoadoutsConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages database connections and operations using HikariCP.
 */
public class DatabaseManager {

    private final Loadouts plugin;
    private HikariDataSource dataSource;
    private boolean useMysql;

    public DatabaseManager(Loadouts plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize database connection and create tables
     */
    public void initialize() throws SQLException {
        LoadoutsConfig config = plugin.getLoadoutsConfig();
        useMysql = config.getDatabaseType().equalsIgnoreCase("mysql");

        HikariConfig hikariConfig = new HikariConfig();

        if (useMysql) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" +
                    config.getMysqlPort() + "/" + config.getMysqlDatabase() +
                    "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true");
            hikariConfig.setUsername(config.getMysqlUsername());
            hikariConfig.setPassword(config.getMysqlPassword());
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File dbFile = new File(plugin.getDataFolder(), config.getSqliteFile());
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        }

        hikariConfig.setPoolName("Loadouts-Pool");
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setMaxLifetime(600000);

        dataSource = new HikariDataSource(hikariConfig);

        createTables();
    }

    /**
     * Create database tables if they don't exist
     */
    private void createTables() throws SQLException {
        String createLoadoutsTable;
        String createItemsTable;
        String createSlotsTable;

        if (useMysql) {
            createLoadoutsTable = """
                    CREATE TABLE IF NOT EXISTS loadouts (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        name VARCHAR(64) NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        UNIQUE KEY unique_player_name (player_uuid, name),
                        INDEX idx_player (player_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;

            createSlotsTable = """
                    CREATE TABLE IF NOT EXISTS loadout_slots (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        loadout_id INT NOT NULL,
                        slot_type VARCHAR(32) NOT NULL,
                        weapon_title VARCHAR(128) NOT NULL,
                        category VARCHAR(64),
                        is_wm_weapon BOOLEAN NOT NULL DEFAULT TRUE,
                        ammo_amount INT NOT NULL DEFAULT 0,
                        FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE,
                        UNIQUE KEY unique_loadout_slot (loadout_id, slot_type)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;

            createItemsTable = """
                    CREATE TABLE IF NOT EXISTS loadout_items (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        loadout_id INT NOT NULL,
                        slot_index INT NOT NULL,
                        item_data MEDIUMBLOB NOT NULL,
                        FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE,
                        UNIQUE KEY unique_loadout_slot_index (loadout_id, slot_index)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        } else {
            createLoadoutsTable = """
                    CREATE TABLE IF NOT EXISTS loadouts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        UNIQUE(player_uuid, name)
                    )
                    """;

            createSlotsTable = """
                    CREATE TABLE IF NOT EXISTS loadout_slots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        loadout_id INTEGER NOT NULL,
                        slot_type TEXT NOT NULL,
                        weapon_title TEXT NOT NULL,
                        category TEXT,
                        is_wm_weapon INTEGER NOT NULL DEFAULT 1,
                        ammo_amount INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE,
                        UNIQUE(loadout_id, slot_type)
                    )
                    """;

            createItemsTable = """
                    CREATE TABLE IF NOT EXISTS loadout_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        loadout_id INTEGER NOT NULL,
                        slot_index INTEGER NOT NULL,
                        item_data BLOB NOT NULL,
                        FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE,
                        UNIQUE(loadout_id, slot_index)
                    )
                    """;
        }

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createLoadoutsTable);
            stmt.execute(createSlotsTable);
            stmt.execute(createItemsTable);
        }
    }

    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Save a loadout to the database
     */
    public void saveLoadout(Loadout loadout) throws SQLException {
        String upsertLoadout = useMysql
                ? "INSERT INTO loadouts (player_uuid, name, created_at, updated_at) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)"
                : "INSERT OR REPLACE INTO loadouts (player_uuid, name, created_at, updated_at) VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insert/update loadout
                int loadoutId;
                if (loadout.isSaved()) {
                    loadoutId = loadout.getId();
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE loadouts SET updated_at = ? WHERE id = ?")) {
                        stmt.setLong(1, loadout.getUpdatedAt());
                        stmt.setInt(2, loadoutId);
                        stmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(upsertLoadout,
                            Statement.RETURN_GENERATED_KEYS)) {
                        stmt.setString(1, loadout.getPlayerUUID().toString());
                        stmt.setString(2, loadout.getName());
                        stmt.setLong(3, loadout.getCreatedAt());
                        stmt.setLong(4, loadout.getUpdatedAt());
                        stmt.executeUpdate();

                        // Get generated ID or existing ID
                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                loadoutId = rs.getInt(1);
                            } else {
                                loadoutId = getLoadoutId(conn, loadout.getPlayerUUID(), loadout.getName());
                            }
                        }
                    }
                    loadout.setId(loadoutId);
                }

                // Clear existing slots and items
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loadout_slots WHERE loadout_id = ?")) {
                    stmt.setInt(1, loadoutId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loadout_items WHERE loadout_id = ?")) {
                    stmt.setInt(1, loadoutId);
                    stmt.executeUpdate();
                }

                // Insert slots
                String insertSlot = "INSERT INTO loadout_slots (loadout_id, slot_type, weapon_title, category, is_wm_weapon, ammo_amount) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSlot)) {
                    for (LoadoutSlot slot : loadout.getSlots().values()) {
                        stmt.setInt(1, loadoutId);
                        stmt.setString(2, slot.getSlotType());
                        stmt.setString(3, slot.getWeaponTitle());
                        stmt.setString(4, slot.getCategory());
                        stmt.setBoolean(5, slot.isWmWeapon());
                        stmt.setInt(6, slot.getAmmoAmount());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                // Insert final items
                if (loadout.hasFinalItems()) {
                    String insertItem = "INSERT INTO loadout_items (loadout_id, slot_index, item_data) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertItem)) {
                        List<ItemStack> items = loadout.getFinalItems();
                        for (int i = 0; i < items.size(); i++) {
                            ItemStack item = items.get(i);
                            if (item != null) {
                                stmt.setInt(1, loadoutId);
                                stmt.setInt(2, i);
                                stmt.setBytes(3, serializeItem(item));
                                stmt.addBatch();
                            }
                        }
                        stmt.executeBatch();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Get a loadout by player and name
     */
    public Loadout getLoadout(UUID playerUUID, String name) throws SQLException {
        String query = "SELECT id, player_uuid, name, created_at, updated_at FROM loadouts WHERE player_uuid = ? AND name = ?";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Loadout loadout = new Loadout(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("name"),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at"));
                    loadSlots(conn, loadout);
                    loadItems(conn, loadout);
                    return loadout;
                }
            }
        }
        return null;
    }

    /**
     * Get all loadouts for a player
     */
    public List<Loadout> getPlayerLoadouts(UUID playerUUID) throws SQLException {
        List<Loadout> loadouts = new ArrayList<>();
        String query = "SELECT id, player_uuid, name, created_at, updated_at FROM loadouts WHERE player_uuid = ? ORDER BY name";

        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Loadout loadout = new Loadout(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("name"),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at"));
                    loadSlots(conn, loadout);
                    loadItems(conn, loadout);
                    loadouts.add(loadout);
                }
            }
        }
        return loadouts;
    }

    /**
     * Delete a loadout by ID
     */
    public void deleteLoadout(int loadoutId) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM loadouts WHERE id = ?")) {
            stmt.setInt(1, loadoutId);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete a loadout by player and name
     */
    public boolean deleteLoadout(UUID playerUUID, String name) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn
                        .prepareStatement("DELETE FROM loadouts WHERE player_uuid = ? AND name = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Get loadout count for a player
     */
    public int getLoadoutCount(UUID playerUUID) throws SQLException {
        try (Connection conn = getConnection();
                PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM loadouts WHERE player_uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Load slots for a loadout
     */
    private void loadSlots(Connection conn, Loadout loadout) throws SQLException {
        String query = "SELECT slot_type, weapon_title, category, is_wm_weapon, ammo_amount FROM loadout_slots WHERE loadout_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, loadout.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LoadoutSlot slot = new LoadoutSlot(
                            rs.getString("slot_type"),
                            rs.getString("weapon_title"),
                            rs.getString("category"),
                            rs.getBoolean("is_wm_weapon"),
                            rs.getInt("ammo_amount"));
                    loadout.setSlot(slot.getSlotType(), slot);
                }
            }
        }
    }

    /**
     * Load items for a loadout
     */
    private void loadItems(Connection conn, Loadout loadout) throws SQLException {
        String query = "SELECT slot_index, item_data FROM loadout_items WHERE loadout_id = ? ORDER BY slot_index";
        List<ItemStack> items = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, loadout.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int index = rs.getInt("slot_index");
                    byte[] data = rs.getBytes("item_data");

                    // Ensure list is large enough
                    while (items.size() <= index) {
                        items.add(null);
                    }

                    try {
                        items.set(index, deserializeItem(data));
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to deserialize item at index " + index, e);
                    }
                }
            }
        }

        if (!items.isEmpty()) {
            loadout.setFinalItems(items);
        }
    }

    /**
     * Get loadout ID by player and name
     */
    private int getLoadoutId(Connection conn, UUID playerUUID, String name) throws SQLException {
        String query = "SELECT id FROM loadouts WHERE player_uuid = ? AND name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    /**
     * Serialize an ItemStack to bytes
     */
    private byte[] serializeItem(ItemStack item) throws SQLException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SQLException("Failed to serialize ItemStack", e);
        }
    }

    /**
     * Deserialize bytes to an ItemStack
     */
    private ItemStack deserializeItem(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize ItemStack", e);
        }
    }

    /**
     * Close the database connection pool
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
