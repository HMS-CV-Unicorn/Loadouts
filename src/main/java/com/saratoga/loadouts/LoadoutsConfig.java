package com.saratoga.loadouts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Configuration handler for the Loadouts plugin.
 */
public class LoadoutsConfig {

    private final Loadouts plugin;

    // Database settings
    private String databaseType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String sqliteFile;

    // Ammo multipliers
    private final Map<String, Integer> ammoMultipliers = new HashMap<>();
    private int defaultAmmoMultiplier;

    // Item amounts (for consumables without ammo)
    private final Map<String, Integer> itemAmounts = new HashMap<>();
    private int defaultItemAmount;

    // Slot configuration
    private final Map<String, SlotConfig> slots = new LinkedHashMap<>();

    // Attachment slots configuration
    private final Map<String, AttachmentSlotConfig> attachmentSlots = new LinkedHashMap<>();

    // Custom items
    private final Map<String, CustomItemConfig> customItems = new HashMap<>();

    // General settings
    private int maxLoadouts;

    // GUI settings
    private String categoryMenuTitle;
    private int categoryMenuSize;
    private String weaponSelectTitle;
    private int itemsPerPage;

    // Messages
    private final Map<String, String> messages = new HashMap<>();

    // Permissions
    private String permUseMenu;
    private String permEditLoadout;
    private String permSaveLoadout;
    private String permCancelLoadout;
    private String permGiveLoadout;
    private String permListLoadout;
    private String permDeleteLoadout;
    private String permRename;
    private String permAdminOpenOther;
    private String permEditGlobal;
    private String permReload;
    private String permSyncwm;

    public LoadoutsConfig(Loadouts plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Database settings
        databaseType = config.getString("database.type", "mysql");
        mysqlHost = config.getString("database.mysql.host", "localhost");
        mysqlPort = config.getInt("database.mysql.port", 3306);
        mysqlDatabase = config.getString("database.mysql.database", "loadouts");
        mysqlUsername = config.getString("database.mysql.username", "root");
        mysqlPassword = config.getString("database.mysql.password", "");
        sqliteFile = config.getString("database.sqlite.file", "loadouts.db");

        // Ammo multipliers
        ammoMultipliers.clear();
        ConfigurationSection ammoSection = config.getConfigurationSection("ammo-multipliers");
        if (ammoSection != null) {
            for (String key : ammoSection.getKeys(false)) {
                if (key.equals("default")) {
                    defaultAmmoMultiplier = ammoSection.getInt(key, 4);
                } else {
                    ammoMultipliers.put(key, ammoSection.getInt(key, 4));
                }
            }
        }
        if (defaultAmmoMultiplier == 0) {
            defaultAmmoMultiplier = 4;
        }

        // Item amounts (for consumables without ammo)
        itemAmounts.clear();
        ConfigurationSection itemAmountSection = config.getConfigurationSection("item-amounts");
        if (itemAmountSection != null) {
            for (String key : itemAmountSection.getKeys(false)) {
                if (key.equals("default")) {
                    defaultItemAmount = itemAmountSection.getInt(key, 1);
                } else {
                    itemAmounts.put(key, itemAmountSection.getInt(key, 1));
                }
            }
        }
        if (defaultItemAmount == 0) {
            defaultItemAmount = 1;
        }

        // Slot configuration
        slots.clear();
        ConfigurationSection slotsSection = config.getConfigurationSection("slots");
        if (slotsSection != null) {
            for (String slotKey : slotsSection.getKeys(false)) {
                ConfigurationSection slotSection = slotsSection.getConfigurationSection(slotKey);
                if (slotSection != null) {
                    String displayName = slotSection.getString("display-name", slotKey);
                    Material icon = Material.matchMaterial(slotSection.getString("icon", "STONE"));
                    if (icon == null)
                        icon = Material.STONE;
                    List<String> allowedCategories = slotSection.getStringList("allowed-categories");

                    slots.put(slotKey, new SlotConfig(slotKey, displayName, icon, allowedCategories));
                }
            }
        }

        // Custom items
        customItems.clear();
        ConfigurationSection customSection = config.getConfigurationSection("custom-items");
        if (customSection != null) {
            for (String itemKey : customSection.getKeys(false)) {
                ConfigurationSection itemSection = customSection.getConfigurationSection(itemKey);
                if (itemSection != null) {
                    String slotType = itemSection.getString("slot-type", "tactical");
                    String displayName = itemSection.getString("display-name", itemKey);
                    Material material = Material.matchMaterial(itemSection.getString("material", "STONE"));
                    if (material == null)
                        material = Material.STONE;
                    int amount = itemSection.getInt("amount", 1);

                    customItems.put(itemKey, new CustomItemConfig(itemKey, slotType, displayName, material, amount));
                }
            }
        }

        // General settings
        maxLoadouts = config.getInt("max-loadouts", 5);

        // Attachment slots configuration
        attachmentSlots.clear();
        ConfigurationSection attachSection = config.getConfigurationSection("attachment-slots");
        if (attachSection != null) {
            for (String slotKey : attachSection.getKeys(false)) {
                ConfigurationSection slotSection = attachSection.getConfigurationSection(slotKey);
                if (slotSection != null) {
                    String displayName = slotSection.getString("display-name", slotKey);
                    Material icon = Material.matchMaterial(slotSection.getString("icon", "IRON_NUGGET"));
                    if (icon == null)
                        icon = Material.IRON_NUGGET;
                    List<String> categories = slotSection.getStringList("categories");
                    attachmentSlots.put(slotKey, new AttachmentSlotConfig(slotKey, displayName, icon, categories));
                }
            }
        }

        // GUI settings
        categoryMenuTitle = config.getString("gui.category-menu.title", "&8&lロードアウト編集");
        categoryMenuSize = config.getInt("gui.category-menu.size", 54);
        weaponSelectTitle = config.getString("gui.weapon-select.title", "&8&l%category% を選択");
        itemsPerPage = config.getInt("gui.weapon-select.items-per-page", 45);

        // Messages
        messages.clear();
        ConfigurationSection msgSection = config.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                messages.put(key, msgSection.getString(key, ""));
            }
        }

        // Permissions
        permUseMenu = config.getString("permissions.use-menu", "loadouts.user");
        permEditLoadout = config.getString("permissions.edit-loadout", "loadouts.edit");
        permSaveLoadout = config.getString("permissions.save-loadout", "loadouts.save");
        permCancelLoadout = config.getString("permissions.cancel-loadout", "loadouts.cancel");
        permGiveLoadout = config.getString("permissions.give-loadout", "loadouts.give");
        permListLoadout = config.getString("permissions.list-loadout", "loadouts.list");
        permDeleteLoadout = config.getString("permissions.delete-loadout", "loadouts.delete");
        permRename = config.getString("permissions.rename", "loadouts.rename");
        permAdminOpenOther = config.getString("permissions.admin-open-other", "loadouts.admin.open");
        permEditGlobal = config.getString("permissions.edit-global", "loadouts.admin.global");
        permReload = config.getString("permissions.reload", "loadouts.admin.reload");
        permSyncwm = config.getString("permissions.syncwm", "loadouts.admin.syncwm");
    }

    // Message helper methods
    public String getMessage(String key) {
        String prefix = messages.getOrDefault("prefix", "");
        return prefix + messages.getOrDefault(key, key);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }

    public Component getMessageComponent(String key) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getMessage(key));
    }

    public Component getMessageComponent(String key, Map<String, String> placeholders) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(getMessage(key, placeholders));
    }

    // Getters
    public String getDatabaseType() {
        return databaseType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public int getAmmoMultiplier(String category) {
        return ammoMultipliers.getOrDefault(category, defaultAmmoMultiplier);
    }

    public Map<String, SlotConfig> getSlots() {
        return Collections.unmodifiableMap(slots);
    }

    public SlotConfig getSlot(String slotKey) {
        return slots.get(slotKey);
    }

    public Map<String, CustomItemConfig> getCustomItems() {
        return Collections.unmodifiableMap(customItems);
    }

    public List<CustomItemConfig> getCustomItemsForSlot(String slotType) {
        return customItems.values().stream()
                .filter(item -> item.slotType().equals(slotType))
                .toList();
    }

    public int getMaxLoadouts() {
        return maxLoadouts;
    }

    public Map<String, AttachmentSlotConfig> getAttachmentSlots() {
        return Collections.unmodifiableMap(attachmentSlots);
    }

    public AttachmentSlotConfig getAttachmentSlot(String slotKey) {
        return attachmentSlots.get(slotKey);
    }

    /**
     * Get item amount for a weapon (for consumables without ammo).
     * First checks for weapon-specific override, then category, then default.
     */
    public int getItemAmount(String weaponTitle, String category) {
        // Check weapon-specific first
        if (itemAmounts.containsKey(weaponTitle)) {
            return itemAmounts.get(weaponTitle);
        }
        // Then category
        if (category != null && itemAmounts.containsKey(category)) {
            return itemAmounts.get(category);
        }
        return defaultItemAmount;
    }

    public String getCategoryMenuTitle() {
        return categoryMenuTitle;
    }

    public int getCategoryMenuSize() {
        return categoryMenuSize;
    }

    public String getWeaponSelectTitle() {
        return weaponSelectTitle;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    // Permission getters
    public String getPermUseMenu() {
        return permUseMenu;
    }

    public String getPermEditLoadout() {
        return permEditLoadout;
    }

    public String getPermSaveLoadout() {
        return permSaveLoadout;
    }

    public String getPermCancelLoadout() {
        return permCancelLoadout;
    }

    public String getPermGiveLoadout() {
        return permGiveLoadout;
    }

    public String getPermListLoadout() {
        return permListLoadout;
    }

    public String getPermDeleteLoadout() {
        return permDeleteLoadout;
    }

    public String getPermRename() {
        return permRename;
    }

    public String getPermAdminOpenOther() {
        return permAdminOpenOther;
    }

    public String getPermEditGlobal() {
        return permEditGlobal;
    }

    public String getPermReload() {
        return permReload;
    }

    public String getPermSyncwm() {
        return permSyncwm;
    }

    // Inner classes for configuration data
    public record SlotConfig(String key, String displayName, Material icon, List<String> allowedCategories) {
        public Component getDisplayNameComponent() {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(displayName);
        }
    }

    public record CustomItemConfig(String key, String slotType, String displayName, Material material, int amount) {
        public Component getDisplayNameComponent() {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(displayName);
        }
    }

    public record AttachmentSlotConfig(String key, String displayName, Material icon, List<String> categories) {
        public Component getDisplayNameComponent() {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(displayName);
        }
    }
}
