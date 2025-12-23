package com.saratoga.loadouts.integration;

import com.saratoga.loadouts.Loadouts;
import com.saratoga.loadouts.LoadoutsConfig;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.info.InfoHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Integration with WeaponMechanics plugin for weapon data and generation.
 * Uses YAML parsing to correctly identify weapon IDs from their root keys.
 */
public class WeaponMechanicsIntegration {

    private final Loadouts plugin;

    // Category -> List of weapon titles
    private final Map<String, List<String>> categorizedWeapons = new LinkedHashMap<>();

    // Weapon title -> Category mapping for quick lookup
    private final Map<String, String> weaponCategories = new HashMap<>();

    // Weapon title -> Ammo types (from YAML parsing)
    private final Map<String, List<String>> weaponAmmoTypes = new HashMap<>();

    // All weapon titles
    private final List<String> allWeapons = new ArrayList<>();

    // Attachment category -> List of attachment IDs
    private final Map<String, List<String>> categorizedAttachments = new LinkedHashMap<>();

    // Attachment ID -> Category mapping
    private final Map<String, String> attachmentCategories = new HashMap<>();

    // All attachment IDs
    private final List<String> allAttachments = new ArrayList<>();

    public WeaponMechanicsIntegration(Loadouts plugin) {
        this.plugin = plugin;
    }

    /**
     * Load weapons from WeaponMechanics using YAML parsing for correct ID
     * detection.
     */
    public void scanWeapons() {
        categorizedWeapons.clear();
        weaponCategories.clear();
        weaponAmmoTypes.clear();
        allWeapons.clear();

        try {
            // Get registered weapon list from WM API
            InfoHandler infoHandler = WeaponMechanics.getInstance().getWeaponHandler().getInfoHandler();
            List<String> registeredWeapons = infoHandler.getSortedWeaponList();

            plugin.getLogger()
                    .info("Found " + registeredWeapons.size() + " registered weapons from WeaponMechanics API");

            if (registeredWeapons.isEmpty()) {
                plugin.getLogger().warning("No weapons found in WeaponMechanics! Make sure WM is properly configured.");
                return;
            }

            // Build a map from YAML root key (weapon ID) to folder category
            Map<String, String> weaponIdToCategory = scanWeaponFilesWithYamlParsing();
            plugin.getLogger().info("Scanned folder structure with YAML parsing, found " + weaponIdToCategory.size()
                    + " weapon definitions");

            // Categorize each registered weapon based on YAML root key mapping
            for (String weaponTitle : registeredWeapons) {
                String category = weaponIdToCategory.get(weaponTitle);

                if (category == null) {
                    category = "uncategorized";
                    plugin.getLogger().warning("Weapon '" + weaponTitle + "' not found in any YAML file root key!");
                }

                categorizedWeapons.computeIfAbsent(category, k -> new ArrayList<>()).add(weaponTitle);
                weaponCategories.put(weaponTitle, category);
                allWeapons.add(weaponTitle);
            }

            // Sort weapons in each category
            for (List<String> weapons : categorizedWeapons.values()) {
                Collections.sort(weapons);
            }

            // Log summary
            for (Map.Entry<String, List<String>> entry : categorizedWeapons.entrySet()) {
                plugin.getLogger().info("Category '" + entry.getKey() + "': " + entry.getValue().size() + " weapons - "
                        + entry.getValue());
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to scan weapons from WeaponMechanics", e);
        }
    }

    /**
     * Scan the WeaponMechanics weapons folder and parse each YAML file to extract
     * the root key.
     * Returns: weaponId (root key) -> folderCategory
     */
    private Map<String, String> scanWeaponFilesWithYamlParsing() {
        Map<String, String> result = new HashMap<>();

        File wmDataFolder = WeaponMechanics.getInstance().getDataFolder();
        File weaponsFolder = new File(wmDataFolder, "weapons");

        if (!weaponsFolder.exists() || !weaponsFolder.isDirectory()) {
            plugin.getLogger().warning("WeaponMechanics weapons folder not found: " + weaponsFolder.getPath());
            return result;
        }

        // Scan subfolders as categories
        File[] categoryFolders = weaponsFolder.listFiles(File::isDirectory);
        if (categoryFolders == null) {
            plugin.getLogger().warning("No category folders found in WeaponMechanics weapons folder");
            return result;
        }

        for (File categoryFolder : categoryFolders) {
            String categoryName = categoryFolder.getName();

            // Scan yml files directly in this category folder
            scanFolderWithYamlParsing(categoryFolder, categoryName, result);

            // Also scan subfolders (some setups may have nested structure)
            File[] subFolders = categoryFolder.listFiles(File::isDirectory);
            if (subFolders != null) {
                for (File subFolder : subFolders) {
                    // Still use the parent category name, not the subfolder name
                    scanFolderWithYamlParsing(subFolder, categoryName, result);
                }
            }
        }

        return result;
    }

    /**
     * Scan a folder for weapon yml files, parse each one to get the root key
     * (weapon ID) and cache ammo types.
     */
    private void scanFolderWithYamlParsing(File folder, String categoryName, Map<String, String> result) {
        File[] weaponFiles = folder
                .listFiles((dir, name) -> name.endsWith(".yml") && !name.endsWith(".backup") && !name.startsWith("_"));

        if (weaponFiles != null) {
            for (File weaponFile : weaponFiles) {
                try {
                    // Parse the YAML file
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(weaponFile);

                    // Get the root keys
                    Set<String> rootKeys = yaml.getKeys(false);

                    if (rootKeys.isEmpty()) {
                        plugin.getLogger().fine("No root keys found in: " + weaponFile.getName());
                        continue;
                    }

                    // Each root key is a weapon ID defined in this file
                    for (String weaponId : rootKeys) {
                        // Skip system keys or empty keys
                        if (weaponId == null || weaponId.isEmpty()) {
                            continue;
                        }

                        result.put(weaponId, categoryName);

                        // Extract and cache ammo types from this weapon's config
                        List<String> ammoList = yaml.getStringList(weaponId + ".Reload.Ammo.Ammos");
                        if (ammoList != null && !ammoList.isEmpty()) {
                            weaponAmmoTypes.put(weaponId, new ArrayList<>(ammoList));
                            plugin.getLogger().fine("Cached ammo for " + weaponId + ": " + ammoList);
                        } else {
                            // Try single ammo key
                            String singleAmmo = yaml.getString(weaponId + ".Reload.Ammo.Ammo");
                            if (singleAmmo != null && !singleAmmo.isEmpty()) {
                                weaponAmmoTypes.put(weaponId, List.of(singleAmmo));
                                plugin.getLogger().fine("Cached single ammo for " + weaponId + ": " + singleAmmo);
                            }
                        }

                        plugin.getLogger().fine("Parsed weapon: " + weaponId + " from file " +
                                weaponFile.getName() + " in category: " + categoryName);
                    }

                } catch (Exception e) {
                    plugin.getLogger()
                            .warning("Failed to parse YAML file: " + weaponFile.getPath() + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * Get all categorized weapons
     */
    public Map<String, List<String>> getCategorizedWeapons() {
        return Collections.unmodifiableMap(categorizedWeapons);
    }

    /**
     * Get weapons in a specific category
     */
    public List<String> getWeaponsInCategory(String category) {
        return categorizedWeapons.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Get the category of a weapon
     */
    public String getWeaponCategory(String weaponTitle) {
        return weaponCategories.get(weaponTitle);
    }

    /**
     * Get all weapon titles
     */
    public List<String> getAllWeapons() {
        return Collections.unmodifiableList(allWeapons);
    }

    /**
     * Get total weapon count
     */
    public int getTotalWeaponCount() {
        return allWeapons.size();
    }

    /**
     * Get all category names
     */
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(categorizedWeapons.keySet());
    }

    /**
     * Check if a weapon exists
     */
    public boolean weaponExists(String weaponTitle) {
        return weaponCategories.containsKey(weaponTitle);
    }

    /**
     * Generate a weapon ItemStack
     */
    public ItemStack generateWeapon(String weaponTitle) {
        try {
            return WeaponMechanicsAPI.generateWeapon(weaponTitle);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate weapon: " + weaponTitle, e);
            return null;
        }
    }

    /**
     * Give weapon to a player
     */
    public void giveWeapon(String weaponTitle, Player player) {
        try {
            WeaponMechanicsAPI.giveWeapon(weaponTitle, player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to give weapon: " + weaponTitle, e);
        }
    }

    /**
     * Get the magazine size for a weapon
     */
    public int getMagazineSize(String weaponTitle) {
        try {
            return WeaponMechanics.getInstance()
                    .getWeaponConfigurations()
                    .getInt(weaponTitle + ".Reload.Magazine_Size");
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to get magazine size for: " + weaponTitle, e);
            return 0;
        }
    }

    /**
     * Calculate ammo amount based on category multiplier
     */
    public int calculateAmmo(String weaponTitle) {
        String category = getWeaponCategory(weaponTitle);
        int magazineSize = getMagazineSize(weaponTitle);
        int multiplier = plugin.getLoadoutsConfig().getAmmoMultiplier(category);
        return magazineSize * multiplier;
    }

    /**
     * Get the ammo types used by a weapon.
     * Uses cached data from YAML parsing during scanWeapons.
     * Returns list of ammo item IDs (e.g., ["12shell", "9mm"])
     */
    public List<String> getAmmoTypes(String weaponTitle) {
        List<String> cached = weaponAmmoTypes.get(weaponTitle);
        if (cached != null && !cached.isEmpty()) {
            return new ArrayList<>(cached);
        }

        // No cached data - weapon might use a magazine-less system
        plugin.getLogger().fine("No ammo types cached for weapon: " + weaponTitle);
        return Collections.emptyList();
    }

    /**
     * Generate ammo ItemStacks for a weapon with calculated amounts.
     * Returns a list of ammo items to give to the player.
     */
    public List<ItemStack> generateAmmoItems(String weaponTitle) {
        List<ItemStack> ammoItems = new ArrayList<>();

        int totalAmmo = calculateAmmo(weaponTitle);

        if (totalAmmo <= 0) {
            // Weapon doesn't use ammo (grenade, knife, etc.)
            return ammoItems;
        }

        List<String> ammoTypes = getAmmoTypes(weaponTitle);
        if (ammoTypes.isEmpty()) {
            plugin.getLogger().warning("No ammo types cached for weapon: " + weaponTitle +
                    ". Run /loadout syncwm to refresh.");
            return ammoItems;
        }

        // Use the first ammo type (primary)
        String primaryAmmo = ammoTypes.get(0);

        try {
            // Generate ammo using WM API (non-magazine version)
            ItemStack ammoItem = WeaponMechanicsAPI.generateAmmo(primaryAmmo, false);

            if (ammoItem != null) {
                // Set the amount based on calculated ammo
                int maxStack = ammoItem.getMaxStackSize();
                int remaining = totalAmmo;

                while (remaining > 0) {
                    ItemStack stack = ammoItem.clone();
                    int amount = Math.min(remaining, maxStack);
                    stack.setAmount(amount);
                    ammoItems.add(stack);
                    remaining -= amount;
                }

                plugin.getLogger().info("Generated " + totalAmmo + " " + primaryAmmo + " for " + weaponTitle);
            } else {
                plugin.getLogger().warning("WeaponMechanicsAPI.generateAmmo returned null for: " + primaryAmmo);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate ammo for: " + weaponTitle, e);
        }

        return ammoItems;
    }

    /**
     * Get weapon title from ItemStack
     */
    public String getWeaponTitle(ItemStack item) {
        if (item == null)
            return null;
        try {
            return WeaponMechanicsAPI.getWeaponTitle(item);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generate ammo for a weapon
     */
    public ItemStack generateAmmo(String ammoTitle, boolean magazine) {
        try {
            return WeaponMechanicsAPI.generateAmmo(ammoTitle, magazine);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to generate ammo: " + ammoTitle, e);
            return null;
        }
    }

    /**
     * Get weapons allowed for a specific slot type (strict YAML-based matching)
     */
    public Map<String, List<String>> getWeaponsForSlot(String slotType) {
        LoadoutsConfig.SlotConfig slotConfig = plugin.getLoadoutsConfig().getSlot(slotType);
        if (slotConfig == null) {
            plugin.getLogger().warning("No slot config found for: " + slotType);
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();

        // Only include weapons that are in the allowed categories
        for (String allowedCategory : slotConfig.allowedCategories()) {
            List<String> weaponsInCategory = categorizedWeapons.get(allowedCategory);
            if (weaponsInCategory != null && !weaponsInCategory.isEmpty()) {
                result.put(allowedCategory, new ArrayList<>(weaponsInCategory));
            }
        }

        int totalWeapons = result.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().fine("getWeaponsForSlot(" + slotType + "): " + totalWeapons + " weapons from "
                + result.size() + " categories");

        return result;
    }

    /**
     * Get flat list of weapons for a slot type
     */
    public List<String> getFlatWeaponsForSlot(String slotType) {
        List<String> result = new ArrayList<>();
        Map<String, List<String>> categorized = getWeaponsForSlot(slotType);
        for (List<String> weapons : categorized.values()) {
            result.addAll(weapons);
        }
        return result;
    }

    // ==================== Attachment Management ====================

    /**
     * Scan attachments from WeaponMechanics/attachments folder.
     * One YAML file can contain multiple attachment definitions.
     */
    public void scanAttachments() {
        categorizedAttachments.clear();
        attachmentCategories.clear();
        allAttachments.clear();

        File wmDataFolder = WeaponMechanics.getInstance().getDataFolder();
        File attachmentsFolder = new File(wmDataFolder, "attachments");

        if (!attachmentsFolder.exists() || !attachmentsFolder.isDirectory()) {
            plugin.getLogger().info("Attachments folder not found: " + attachmentsFolder.getPath());
            return;
        }

        scanAttachmentFolderRecursive(attachmentsFolder, null);

        // Sort attachments in each category
        for (List<String> attachments : categorizedAttachments.values()) {
            Collections.sort(attachments);
        }

        plugin.getLogger().info("Loaded " + allAttachments.size() + " attachments from WeaponMechanics.");
        for (Map.Entry<String, List<String>> entry : categorizedAttachments.entrySet()) {
            plugin.getLogger().fine("Attachment category '" + entry.getKey() + "': " + entry.getValue().size());
        }
    }

    /**
     * Recursively scan folder for attachment YAML files
     */
    private void scanAttachmentFolderRecursive(File folder, String parentCategory) {
        // Use this folder's name as category if we're in a subfolder
        String categoryName = (parentCategory == null) ? folder.getName() : parentCategory;

        // If this is a direct subfolder of attachments folder, use its name as category
        File attachmentsRoot = new File(WeaponMechanics.getInstance().getDataFolder(), "attachments");
        if (folder.getParentFile().equals(attachmentsRoot)) {
            categoryName = folder.getName().toLowerCase();
        }

        // Scan yml files in this folder
        File[] ymlFiles = folder
                .listFiles((dir, name) -> name.endsWith(".yml") && !name.endsWith(".backup") && !name.startsWith("_"));

        if (ymlFiles != null) {
            for (File ymlFile : ymlFiles) {
                parseAttachmentFile(ymlFile, categoryName);
            }
        }

        // Scan subfolders
        File[] subFolders = folder.listFiles(File::isDirectory);
        if (subFolders != null) {
            for (File subFolder : subFolders) {
                // Use subfolder name as new category
                scanAttachmentFolderRecursive(subFolder, subFolder.getName().toLowerCase());
            }
        }
    }

    /**
     * Parse a single attachment YAML file (may contain multiple attachments)
     */
    private void parseAttachmentFile(File ymlFile, String categoryName) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(ymlFile);
            Set<String> rootKeys = yaml.getKeys(false);

            for (String attachmentId : rootKeys) {
                if (attachmentId == null || attachmentId.isEmpty())
                    continue;

                categorizedAttachments.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(attachmentId);
                attachmentCategories.put(attachmentId, categoryName);
                allAttachments.add(attachmentId);

                plugin.getLogger().fine("Parsed attachment: " + attachmentId +
                        " from " + ymlFile.getName() + " in category: " + categoryName);
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to parse attachment file: " + ymlFile.getPath() + " - " + e.getMessage());
        }
    }

    /**
     * Get attachments for specific categories (used by attachment slots)
     */
    public List<String> getAttachmentsForCategories(List<String> categories) {
        List<String> result = new ArrayList<>();
        for (String category : categories) {
            List<String> attachments = categorizedAttachments.get(category.toLowerCase());
            if (attachments != null) {
                result.addAll(attachments);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Get all categorized attachments
     */
    public Map<String, List<String>> getCategorizedAttachments() {
        return Collections.unmodifiableMap(categorizedAttachments);
    }

    /**
     * Get total attachment count
     */
    public int getTotalAttachmentCount() {
        return allAttachments.size();
    }

    /**
     * Generate attachment ItemStack using WMP API.
     * Returns null if attachment doesn't exist or WMP is not available.
     */
    public ItemStack generateAttachmentItem(String attachmentId) {
        try {
            // Use WMP API to generate attachment
            Class<?> wmpApiClass = Class.forName("me.cjcrafter.weaponmechanicsplus.WeaponMechanicsPlusAPI");
            java.lang.reflect.Method generateMethod = wmpApiClass.getMethod("generateAttachment", String.class);
            Object result = generateMethod.invoke(null, attachmentId);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().fine("WeaponMechanicsPlus not available for attachment generation");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate attachment: " + attachmentId, e);
        }
        return null;
    }

    /**
     * Generate consumable items (for weapons without ammo like grenades).
     * Uses item-amounts config to determine quantity.
     */
    public List<ItemStack> generateConsumableItems(String weaponTitle, int amount) {
        List<ItemStack> items = new ArrayList<>();

        if (amount <= 0) {
            amount = 1;
        }

        try {
            ItemStack baseItem = generateWeapon(weaponTitle);
            if (baseItem != null) {
                int maxStack = baseItem.getMaxStackSize();
                int remaining = amount;

                while (remaining > 0) {
                    ItemStack stack = baseItem.clone();
                    int stackAmount = Math.min(remaining, maxStack);
                    stack.setAmount(stackAmount);
                    items.add(stack);
                    remaining -= stackAmount;
                }

                plugin.getLogger().info("Generated " + amount + " x " + weaponTitle + " (consumable)");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate consumable: " + weaponTitle, e);
        }

        return items;
    }

    /**
     * Check if a weapon has ammo configuration (i.e., not a consumable)
     */
    public boolean hasAmmoConfig(String weaponTitle) {
        return weaponAmmoTypes.containsKey(weaponTitle) && !weaponAmmoTypes.get(weaponTitle).isEmpty();
    }
}
