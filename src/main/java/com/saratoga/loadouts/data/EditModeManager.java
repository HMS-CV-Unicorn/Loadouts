package com.saratoga.loadouts.data;

import com.saratoga.loadouts.Loadouts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the "Edit Mode" state for loadout editing.
 * Handles inventory backup, restoration, and restrictions during editing.
 */
public class EditModeManager implements Listener {

    private final Loadouts plugin;

    // Players currently in edit mode
    private final Set<UUID> playersInEditMode = new HashSet<>();

    // Backup of player inventories before entering edit mode
    private final Map<UUID, ItemStack[]> inventoryBackups = new HashMap<>();
    private final Map<UUID, ItemStack[]> armorBackups = new HashMap<>();
    private final Map<UUID, ItemStack> offhandBackups = new HashMap<>();

    public EditModeManager(Loadouts plugin) {
        this.plugin = plugin;
    }

    /**
     * Start edit mode for a player.
     * Backs up their current inventory and clears it.
     */
    public void startEditMode(Player player) {
        UUID uuid = player.getUniqueId();

        // Deep clone current inventory (each ItemStack must be cloned individually)
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] clonedContents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clonedContents[i] = contents[i] != null ? contents[i].clone() : null;
        }
        inventoryBackups.put(uuid, clonedContents);

        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack[] clonedArmor = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            clonedArmor[i] = armor[i] != null ? armor[i].clone() : null;
        }
        armorBackups.put(uuid, clonedArmor);

        ItemStack offhand = player.getInventory().getItemInOffHand();
        offhandBackups.put(uuid, offhand.getType().isAir() ? null : offhand.clone());

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Add to edit mode list
        playersInEditMode.add(uuid);

        plugin.getLogger().info("Player " + player.getName() + " entered edit mode (inventory backed up)");
    }

    /**
     * End edit mode for a player after saving.
     * Clears edit mode inventory and restores original.
     */
    public void endEditMode(Player player, boolean saveSuccessful) {
        UUID uuid = player.getUniqueId();

        if (!playersInEditMode.contains(uuid)) {
            return;
        }

        // Clear current (edit mode) inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Restore backed up inventory
        restoreInventory(player);

        // Remove from edit mode
        playersInEditMode.remove(uuid);

        if (saveSuccessful) {
            player.sendMessage(Component.text("ロードアウトを保存しました。元のインベントリを復元しました。", NamedTextColor.GREEN));
        }

        plugin.getLogger().fine("Player " + player.getName() + " exited edit mode");
    }

    /**
     * Cancel edit mode without saving.
     * Restores original inventory.
     */
    public void cancelEditMode(Player player, String reason) {
        UUID uuid = player.getUniqueId();

        if (!playersInEditMode.contains(uuid)) {
            return;
        }

        // Clear current inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        // Restore backed up inventory
        restoreInventory(player);

        // Remove from edit mode
        playersInEditMode.remove(uuid);

        player.sendMessage(Component.text("編集がキャンセルされました: " + reason, NamedTextColor.RED));
        player.sendMessage(Component.text("元のインベントリを復元しました。", NamedTextColor.YELLOW));

        plugin.getLogger().info("Player " + player.getName() + " edit mode cancelled: " + reason);
    }

    /**
     * Restore player's backed up inventory
     */
    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();

        ItemStack[] contents = inventoryBackups.remove(uuid);
        ItemStack[] armor = armorBackups.remove(uuid);
        ItemStack offhand = offhandBackups.remove(uuid);

        if (contents != null) {
            player.getInventory().setContents(contents);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }

        player.updateInventory();
    }

    /**
     * Check if a player is in edit mode
     */
    public boolean isInEditMode(UUID uuid) {
        return playersInEditMode.contains(uuid);
    }

    /**
     * Check if a player is in edit mode
     */
    public boolean isInEditMode(Player player) {
        return isInEditMode(player.getUniqueId());
    }

    // ==================== Event Listeners ====================

    /**
     * Prevent item drops during edit mode
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isInEditMode(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("編集モード中はアイテムを捨てられません。", NamedTextColor.RED));
        }
    }

    /**
     * Prevent item pickup during edit mode
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isInEditMode(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Block all commands except /loadout save during edit mode
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInEditMode(player)) {
            return;
        }

        String command = event.getMessage().toLowerCase();

        // Allow /loadout save and /loadout cancel
        if (command.startsWith("/loadout save") || command.startsWith("/loadout cancel")) {
            return;
        }

        // Block all other commands
        event.setCancelled(true);
        player.sendMessage(Component.text("編集モード中は他のコマンドを使用できません。", NamedTextColor.RED));
        player.sendMessage(Component.text("/loadout save で保存するか、/loadout cancel でキャンセルしてください。", NamedTextColor.YELLOW));
    }

    /**
     * Prevent inventory interactions with non-player inventories (chests, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!isInEditMode(player)) {
            return;
        }

        // Allow player inventory interactions
        if (event.getInventory().getType() == InventoryType.PLAYER ||
                event.getInventory().getType() == InventoryType.CRAFTING) {
            return;
        }

        // Block interactions with other inventories (chests, etc.)
        // But only if they're trying to move items
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().getType() != InventoryType.PLAYER) {
            event.setCancelled(true);
            player.sendMessage(Component.text("編集モード中は他のインベントリを使用できません。", NamedTextColor.RED));
        }
    }

    /**
     * Handle player disconnect - restore original inventory
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (playersInEditMode.contains(uuid)) {
            // Restore inventory (items will be saved to player data)
            restoreInventory(player);
            playersInEditMode.remove(uuid);
            plugin.getLogger()
                    .info("Player " + player.getName() + " disconnected during edit mode - inventory restored");
        }
    }

    /**
     * Handle server shutdown - restore all inventories
     */
    public void onServerShutdown() {
        for (UUID uuid : new HashSet<>(playersInEditMode)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreInventory(player);
                player.sendMessage(Component.text("サーバー停止のため編集がキャンセルされました。", NamedTextColor.YELLOW));
            }
        }
        playersInEditMode.clear();
        inventoryBackups.clear();
        armorBackups.clear();
        offhandBackups.clear();
    }
}
