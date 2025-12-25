package com.saratoga.loadouts.data;

import com.saratoga.loadouts.Loadouts;
import com.saratoga.loadouts.LoadoutsConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages loadout operations with caching and async database operations.
 * Updated to support slot-based naming (1-5) instead of arbitrary names.
 */
public class LoadoutManager {

    private final Loadouts plugin;
    private final DatabaseManager databaseManager;
    private final LoadoutsConfig config;

    // Special UUID for global (server-wide) loadouts
    public static final UUID GLOBAL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // Cache of loaded loadouts: playerUUID -> (slotNumber -> Loadout)
    private final Map<UUID, Map<String, Loadout>> loadoutCache = new ConcurrentHashMap<>();

    // Active edit sessions
    private final Map<UUID, LoadoutEditSession> editSessions = new ConcurrentHashMap<>();

    public LoadoutManager(Loadouts plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.config = plugin.getLoadoutsConfig();
    }

    // ==================== Loadout Operations ====================

    /**
     * Get a loadout by slot number (1-5)
     */
    public Loadout getLoadout(UUID playerUUID, String slotNumber) {
        Map<String, Loadout> playerLoadouts = loadoutCache.get(playerUUID);
        if (playerLoadouts != null && playerLoadouts.containsKey(slotNumber)) {
            return playerLoadouts.get(slotNumber);
        }

        // Try to load from database
        try {
            Loadout loadout = databaseManager.getLoadout(playerUUID, slotNumber);
            if (loadout != null && playerLoadouts == null) {
                playerLoadouts = new ConcurrentHashMap<>();
                loadoutCache.put(playerUUID, playerLoadouts);
            }
            if (loadout != null) {
                playerLoadouts.put(slotNumber, loadout);
            }
            return loadout;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load loadout", e);
            return null;
        }
    }

    /**
     * Get all loadouts for a player
     */
    public List<Loadout> getPlayerLoadouts(UUID playerUUID) {
        // Ensure we have cached data
        loadPlayerLoadouts(playerUUID);

        Map<String, Loadout> playerLoadouts = loadoutCache.get(playerUUID);
        if (playerLoadouts == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(playerLoadouts.values());
    }

    /**
     * Load all loadouts for a player into cache
     */
    public void loadPlayerLoadouts(UUID playerUUID) {
        if (loadoutCache.containsKey(playerUUID)) {
            return; // Already loaded
        }

        try {
            List<Loadout> loadouts = databaseManager.getPlayerLoadouts(playerUUID);
            Map<String, Loadout> playerLoadouts = new ConcurrentHashMap<>();
            for (Loadout loadout : loadouts) {
                playerLoadouts.put(loadout.getName(), loadout);
            }
            loadoutCache.put(playerUUID, playerLoadouts);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player loadouts", e);
        }
    }

    /**
     * Save a loadout (async)
     */
    public CompletableFuture<Boolean> saveLoadout(Loadout loadout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                databaseManager.saveLoadout(loadout);

                // Update cache
                Map<String, Loadout> playerLoadouts = loadoutCache.computeIfAbsent(
                        loadout.getPlayerUUID(), k -> new ConcurrentHashMap<>());
                playerLoadouts.put(loadout.getName(), loadout);

                return true;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save loadout", e);
                return false;
            }
        });
    }

    /**
     * Save player's current inventory to a slot number
     */
    public CompletableFuture<Boolean> saveCurrentInventory(Player player, String slotNumber) {
        UUID playerUUID = player.getUniqueId();
        LoadoutEditSession session = getEditSession(playerUUID);

        // Determine the target UUID (player or global)
        UUID targetUUID = (session != null && session.isEditingGlobal()) ? GLOBAL_UUID : playerUUID;
        boolean isGlobal = targetUUID.equals(GLOBAL_UUID);

        // Create or update loadout
        Loadout existing = getLoadout(targetUUID, slotNumber);
        Loadout loadout;

        if (existing != null) {
            loadout = existing;
            loadout.setUpdatedAt(System.currentTimeMillis());
        } else {
            loadout = new Loadout(targetUUID, slotNumber);
        }

        // Get edit session slots and attachments if any
        if (session != null) {
            // Copy weapon slots
            for (LoadoutSlot slot : session.getSelectedSlots().values()) {
                loadout.setSlot(slot.getSlotType(), slot);
            }
            // Copy attachments
            for (Map.Entry<String, String> entry : session.getSelectedAttachments().entrySet()) {
                loadout.setAttachment(entry.getKey(), entry.getValue());
            }
        }

        // Capture current inventory
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            items.add(item != null ? item.clone() : null);
        }
        loadout.setFinalItems(items);

        // End edit session
        endEditSession(playerUUID);

        // Log for global saves
        if (isGlobal) {
            plugin.getLogger().info("Saving global loadout slot " + slotNumber + " by " + player.getName());
        }

        return saveLoadout(loadout);
    }

    /**
     * Apply a loadout to a player
     */
    public boolean applyLoadout(Player player, String slotNumber) {
        Loadout loadout = getLoadout(player.getUniqueId(), slotNumber);
        if (loadout == null || !loadout.hasFinalItems()) {
            return false;
        }

        // Clear and set inventory
        player.getInventory().clear();

        List<ItemStack> items = loadout.getFinalItems();
        for (int i = 0; i < items.size() && i < player.getInventory().getSize(); i++) {
            ItemStack item = items.get(i);
            if (item != null) {
                player.getInventory().setItem(i, item.clone());
            }
        }

        // Full status reset after loadout apply
        resetPlayerStatus(player);

        return true;
    }

    /**
     * Get a global loadout by slot number
     */
    public Loadout getGlobalLoadout(String slotNumber) {
        return getLoadout(GLOBAL_UUID, slotNumber);
    }

    /**
     * Apply a global loadout to a player
     */
    public boolean applyGlobalLoadout(Player player, String slotNumber) {
        Loadout loadout = getGlobalLoadout(slotNumber);
        if (loadout == null || !loadout.hasFinalItems()) {
            return false;
        }

        // Clear and set inventory
        player.getInventory().clear();

        List<ItemStack> items = loadout.getFinalItems();
        for (int i = 0; i < items.size() && i < player.getInventory().getSize(); i++) {
            ItemStack item = items.get(i);
            if (item != null) {
                player.getInventory().setItem(i, item.clone());
            }
        }

        // Full status reset after loadout apply
        resetPlayerStatus(player);

        return true;
    }

    /**
     * Reset player status to full (health, food, remove effects, extinguish fire)
     * Called after loadout is applied
     */
    private void resetPlayerStatus(Player player) {
        // Restore to max health
        org.bukkit.attribute.AttributeInstance healthAttr = player
                .getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            player.setHealth(healthAttr.getValue());
        }

        // Restore food level
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // Remove all potion effects
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // Extinguish fire
        player.setFireTicks(0);
    }

    /**
     * Delete a loadout (async)
     */
    public CompletableFuture<Boolean> deleteLoadout(UUID playerUUID, String slotNumber) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean deleted = databaseManager.deleteLoadout(playerUUID, slotNumber);
                if (deleted) {
                    Map<String, Loadout> playerLoadouts = loadoutCache.get(playerUUID);
                    if (playerLoadouts != null) {
                        playerLoadouts.remove(slotNumber);
                    }
                }
                return deleted;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to delete loadout", e);
                return false;
            }
        });
    }

    // ==================== Edit Sessions ====================

    /**
     * Start an edit session for a player
     */
    public LoadoutEditSession startEditSession(Player player) {
        LoadoutEditSession session = new LoadoutEditSession(player.getUniqueId());
        editSessions.put(player.getUniqueId(), session);
        return session;
    }

    /**
     * Get an edit session for a player
     */
    public LoadoutEditSession getEditSession(UUID playerUUID) {
        return editSessions.get(playerUUID);
    }

    /**
     * End an edit session
     */
    public void endEditSession(UUID playerUUID) {
        editSessions.remove(playerUUID);
    }

    /**
     * Check if player has an active edit session
     */
    public boolean hasEditSession(UUID playerUUID) {
        return editSessions.containsKey(playerUUID);
    }

    // ==================== Cache Management ====================

    /**
     * Clear cache for a player
     */
    public void clearCache(UUID playerUUID) {
        loadoutCache.remove(playerUUID);
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        loadoutCache.clear();
        editSessions.clear();
    }

    /**
     * Get loadout count for a player
     */
    public int getLoadoutCount(UUID playerUUID) {
        Map<String, Loadout> playerLoadouts = loadoutCache.get(playerUUID);
        if (playerLoadouts != null) {
            return (int) playerLoadouts.values().stream()
                    .filter(l -> l != null && l.hasFinalItems())
                    .count();
        }

        try {
            return databaseManager.getLoadoutCount(playerUUID);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get loadout count", e);
            return 0;
        }
    }

    // ==================== Edit Session Class ====================

    /**
     * Represents an active loadout editing session.
     */
    public static class LoadoutEditSession {
        private final UUID playerUUID;
        private int editingSlotNumber = 1; // Which loadout slot (1-5) being edited
        private boolean editingGlobal = false; // Whether editing a global (server-wide) loadout
        private String currentSlotType; // Current weapon category slot being selected
        private String currentAttachmentSlot; // Current attachment slot being selected
        private int currentPage = 0; // Pagination for weapon selection
        private final Map<String, LoadoutSlot> selectedSlots = new LinkedHashMap<>();
        private final Map<String, String> selectedAttachments = new LinkedHashMap<>(); // slotKey -> attachmentId
        private final long startTime;

        public LoadoutEditSession(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.startTime = System.currentTimeMillis();
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public int getEditingSlotNumber() {
            return editingSlotNumber;
        }

        public void setEditingSlotNumber(int editingSlotNumber) {
            this.editingSlotNumber = editingSlotNumber;
        }

        public boolean isEditingGlobal() {
            return editingGlobal;
        }

        public void setEditingGlobal(boolean editingGlobal) {
            this.editingGlobal = editingGlobal;
        }

        public String getCurrentSlotType() {
            return currentSlotType;
        }

        public void setCurrentSlotType(String currentSlotType) {
            this.currentSlotType = currentSlotType;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void setCurrentPage(int currentPage) {
            this.currentPage = Math.max(0, currentPage);
        }

        public Map<String, LoadoutSlot> getSelectedSlots() {
            return selectedSlots;
        }

        public void setSlot(String slotType, LoadoutSlot slot) {
            selectedSlots.put(slotType, slot);
        }

        public LoadoutSlot getSlot(String slotType) {
            return selectedSlots.get(slotType);
        }

        public boolean hasSlot(String slotType) {
            return selectedSlots.containsKey(slotType);
        }

        public void removeSlot(String slotType) {
            selectedSlots.remove(slotType);
        }

        public void clearSelections() {
            selectedSlots.clear();
            selectedAttachments.clear();
        }

        public long getStartTime() {
            return startTime;
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }

        // Attachment methods
        public String getCurrentAttachmentSlot() {
            return currentAttachmentSlot;
        }

        public void setCurrentAttachmentSlot(String currentAttachmentSlot) {
            this.currentAttachmentSlot = currentAttachmentSlot;
        }

        public Map<String, String> getSelectedAttachments() {
            return selectedAttachments;
        }

        public void setAttachment(String slotKey, String attachmentId) {
            selectedAttachments.put(slotKey, attachmentId);
        }

        public String getAttachment(String slotKey) {
            return selectedAttachments.get(slotKey);
        }

        public boolean hasAttachment(String slotKey) {
            return selectedAttachments.containsKey(slotKey);
        }

        /**
         * Load selections from an existing saved loadout.
         * This allows editing an existing loadout without starting from scratch.
         */
        public void loadFromLoadout(Loadout loadout) {
            if (loadout == null) {
                return;
            }

            // Load weapon slots
            selectedSlots.clear();
            for (Map.Entry<String, LoadoutSlot> entry : loadout.getSlots().entrySet()) {
                selectedSlots.put(entry.getKey(), entry.getValue());
            }

            // Load attachments
            selectedAttachments.clear();
            for (Map.Entry<String, String> entry : loadout.getAttachments().entrySet()) {
                selectedAttachments.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
