package com.saratoga.loadouts.gui;

import com.saratoga.loadouts.Loadouts;
import com.saratoga.loadouts.LoadoutsConfig;
import com.saratoga.loadouts.data.Loadout;
import com.saratoga.loadouts.data.LoadoutManager;
import com.saratoga.loadouts.data.LoadoutSlot;
import com.saratoga.loadouts.integration.WeaponMechanicsIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Manages all GUI interactions for the loadout system.
 */
public class GuiManager implements Listener {

    private final Loadouts plugin;
    private final LoadoutManager loadoutManager;
    private final WeaponMechanicsIntegration wmIntegration;
    private final LoadoutsConfig config;

    // Track which inventory is which type
    private final Map<UUID, GuiType> openGuis = new HashMap<>();

    // Max loadout slots
    private static final int MAX_SLOTS = 5;

    public GuiManager(Loadouts plugin) {
        this.plugin = plugin;
        this.loadoutManager = plugin.getLoadoutManager();
        this.wmIntegration = plugin.getWmIntegration();
        this.config = plugin.getLoadoutsConfig();
    }

    // ==================== Main Menu (Equipment Selection) ====================

    /**
     * Open the main loadout selection menu (APPLY ONLY - no editing)
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("ロードアウト選択", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        // Loadout slots (1-5) - apply only
        for (int i = 1; i <= MAX_SLOTS; i++) {
            Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), String.valueOf(i));
            ItemStack slotItem = createApplySlotItem(i, loadout);
            inv.setItem(10 + i, slotItem);
        }

        // Fill empty slots
        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);

        player.openInventory(inv);
        openGuis.put(player.getUniqueId(), GuiType.MAIN_MENU);
    }

    /**
     * Create a loadout slot button item (for apply menu)
     */
    private ItemStack createApplySlotItem(int slotNumber, Loadout loadout) {
        boolean hasLoadout = loadout != null && loadout.hasFinalItems();
        Material material = hasLoadout ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        Component title = Component.text("スロット " + slotNumber,
                hasLoadout ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(title);

        List<Component> lore = new ArrayList<>();
        if (hasLoadout) {
            lore.add(Component.text("保存済み", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("クリックで装備", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("空きスロット", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("/loadout edit で編集", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== Slot Selection (for Edit) ====================

    /**
     * Open the slot selection menu for editing
     */
    public void openSlotSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("編集するスロットを選択", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        // Slot buttons (1-5)
        for (int i = 1; i <= MAX_SLOTS; i++) {
            Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), String.valueOf(i));
            ItemStack slotItem = createEditSlotItem(i, loadout);
            inv.setItem(10 + i, slotItem);
        }

        // Back button
        inv.setItem(22, createNavigationItem(Material.BARRIER, "&c戻る"));

        // Fill empty slots
        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);

        player.openInventory(inv);
        openGuis.put(player.getUniqueId(), GuiType.SLOT_SELECT);
    }

    private ItemStack createEditSlotItem(int slotNumber, Loadout loadout) {
        boolean hasLoadout = loadout != null && loadout.hasFinalItems();
        Material material = hasLoadout ? Material.WRITTEN_BOOK : Material.BOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("スロット " + slotNumber + " を編集",
                hasLoadout ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (hasLoadout) {
            lore.add(Component.text("既存のロードアウトを上書きします", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("新規作成", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("クリックして編集開始", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== Category Selection Menu ====================

    /**
     * Open the category selection menu (after selecting a slot)
     */
    public void openCategoryMenu(Player player, int slotNumber) {
        // Get existing session or create new one
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null) {
            session = loadoutManager.startEditSession(player);

            // If editing a slot that already has a saved loadout, restore those selections
            Loadout existingLoadout = loadoutManager.getLoadout(player.getUniqueId(), String.valueOf(slotNumber));
            if (existingLoadout != null && !existingLoadout.getSlots().isEmpty()) {
                session.loadFromLoadout(existingLoadout);
                player.sendMessage(Component.text("保存済みロードアウトから設定を復元しました。", NamedTextColor.GREEN));
            }
        }
        session.setEditingSlotNumber(slotNumber);

        refreshCategoryMenu(player, session);
    }

    /**
     * Refresh the category menu with current session state
     */
    private void refreshCategoryMenu(Player player, LoadoutManager.LoadoutEditSession session) {
        String title = config.getCategoryMenuTitle();
        int size = config.getCategoryMenuSize();

        Inventory inv = Bukkit.createInventory(null, size,
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(title + " (スロット" + session.getEditingSlotNumber() + ")"));

        // Layout weapon slots in top rows (row 2 and row 4)
        Map<String, LoadoutsConfig.SlotConfig> slots = config.getSlots();
        int[] slotPositions = { 10, 12, 14, 16, 28, 30, 32, 34 };

        int i = 0;
        for (Map.Entry<String, LoadoutsConfig.SlotConfig> entry : slots.entrySet()) {
            if (i >= slotPositions.length)
                break;

            String slotKey = entry.getKey();
            LoadoutsConfig.SlotConfig slotConfig = entry.getValue();

            ItemStack icon = createSlotIcon(slotKey, slotConfig, session);
            inv.setItem(slotPositions[i], icon);
            i++;
        }

        // Add attachment slots on row 3 (positions 19-23), directly below primary
        // weapon slots
        Map<String, LoadoutsConfig.AttachmentSlotConfig> attachmentSlots = config.getAttachmentSlots();
        int[] attachmentPositions = { 19, 20, 21, 22, 23 };

        int j = 0;
        for (Map.Entry<String, LoadoutsConfig.AttachmentSlotConfig> entry : attachmentSlots.entrySet()) {
            if (j >= attachmentPositions.length)
                break;

            String slotKey = entry.getKey();
            LoadoutsConfig.AttachmentSlotConfig slotConfig = entry.getValue();

            ItemStack icon = createAttachmentSlotIcon(slotKey, slotConfig, session);
            inv.setItem(attachmentPositions[j], icon);
            j++;
        }

        // Confirm button at bottom center
        inv.setItem(49, createConfirmButton(session));

        // Back button
        inv.setItem(45, createNavigationItem(Material.ARROW, "&7戻る"));

        // Fill empty slots with glass
        fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE);

        player.openInventory(inv);
        openGuis.put(player.getUniqueId(), GuiType.CATEGORY_MENU);
    }

    /**
     * Open weapon selection menu for a slot
     */
    public void openWeaponSelectMenu(Player player, String slotType) {
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(config.getMessageComponent("invalid-usage"));
            return;
        }

        session.setCurrentSlotType(slotType);
        session.setCurrentPage(0);

        updateWeaponSelectMenu(player, session);
    }

    /**
     * Update/refresh the weapon selection menu
     */
    private void updateWeaponSelectMenu(Player player, LoadoutManager.LoadoutEditSession session) {
        String slotType = session.getCurrentSlotType();
        int page = session.getCurrentPage();

        // Get weapons for this slot
        List<String> weapons = wmIntegration.getFlatWeaponsForSlot(slotType);

        // Debug log
        plugin.getLogger().info("Weapon select for " + slotType + ": " + weapons.size() + " weapons found");

        // Add custom items for this slot
        List<LoadoutsConfig.CustomItemConfig> customItems = config.getCustomItemsForSlot(slotType);

        int itemsPerPage = config.getItemsPerPage();
        int totalItems = weapons.size() + customItems.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));

        LoadoutsConfig.SlotConfig slotConfig = config.getSlot(slotType);
        String categoryName = slotConfig != null ? slotConfig.displayName() : slotType;

        String title = config.getWeaponSelectTitle()
                .replace("%category%", categoryName)
                .replace("%page%", String.valueOf(page + 1))
                .replace("%max%", String.valueOf(maxPages));

        Inventory inv = Bukkit.createInventory(null, 54,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        // Show message if no weapons
        if (totalItems == 0) {
            ItemStack noItems = new ItemStack(Material.BARRIER);
            ItemMeta meta = noItems.getItemMeta();
            meta.displayName(Component.text("武器が見つかりません", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("このカテゴリには武器がありません", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("WeaponMechanicsの設定を確認してください", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            noItems.setItemMeta(meta);
            inv.setItem(22, noItems);
        } else {
            // Calculate pagination
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

            // Add items
            int slot = 0;
            for (int i = startIndex; i < endIndex && slot < 45; i++) {
                if (i < weapons.size()) {
                    // WM Weapon
                    String weaponTitle = weapons.get(i);
                    ItemStack weaponItem = createWeaponIcon(weaponTitle, session);
                    if (weaponItem != null) {
                        inv.setItem(slot, weaponItem);
                        slot++;
                    }
                } else {
                    // Custom item
                    int customIndex = i - weapons.size();
                    if (customIndex < customItems.size()) {
                        LoadoutsConfig.CustomItemConfig customItem = customItems.get(customIndex);
                        ItemStack customIcon = createCustomItemIcon(customItem, session);
                        inv.setItem(slot, customIcon);
                        slot++;
                    }
                }
            }
        }

        // Navigation row (bottom)
        // Back button
        inv.setItem(45, createNavigationItem(Material.ARROW, "&7戻る"));

        // Previous page
        if (page > 0) {
            inv.setItem(48, createNavigationItem(Material.ARROW, "&e← 前のページ"));
        }

        // Page indicator
        inv.setItem(49, createPageIndicator(page + 1, maxPages));

        // Next page
        if (page < maxPages - 1) {
            inv.setItem(50, createNavigationItem(Material.ARROW, "&e次のページ →"));
        }

        // Fill remaining bottom row
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }

        player.openInventory(inv);
        openGuis.put(player.getUniqueId(), GuiType.WEAPON_SELECT);
    }

    // ==================== Attachment Selection Menu ====================

    /**
     * Open attachment selection menu for a slot
     */
    public void openAttachmentSelectMenu(Player player, String slotKey) {
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(config.getMessageComponent("invalid-usage"));
            return;
        }

        session.setCurrentAttachmentSlot(slotKey);
        session.setCurrentPage(0);

        updateAttachmentSelectMenu(player, session);
    }

    /**
     * Update/refresh the attachment selection menu
     */
    private void updateAttachmentSelectMenu(Player player, LoadoutManager.LoadoutEditSession session) {
        String slotKey = session.getCurrentAttachmentSlot();
        int page = session.getCurrentPage();

        // Get attachment slot config
        LoadoutsConfig.AttachmentSlotConfig slotConfig = config.getAttachmentSlot(slotKey);
        if (slotConfig == null) {
            player.sendMessage(Component.text("不明なアタッチメントスロットです", NamedTextColor.RED));
            return;
        }

        // Get attachments for this slot's categories
        List<String> attachments = wmIntegration.getAttachmentsForCategories(slotConfig.categories());

        plugin.getLogger().info("Attachment select for " + slotKey + ": " + attachments.size() + " attachments found");

        int itemsPerPage = config.getItemsPerPage();
        int maxPages = Math.max(1, (int) Math.ceil((double) attachments.size() / itemsPerPage));

        String title = "&8&lアタッチメント選択 - " + slotConfig.displayName();
        Inventory inv = Bukkit.createInventory(null, 54,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        if (attachments.isEmpty()) {
            // No attachments message
            ItemStack noItems = new ItemStack(Material.BARRIER);
            ItemMeta meta = noItems.getItemMeta();
            meta.displayName(Component.text("アタッチメントがありません", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("このカテゴリにはアタッチメントがありません", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            noItems.setItemMeta(meta);
            inv.setItem(22, noItems);
        } else {
            // Calculate pagination
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, attachments.size());

            // Add attachment items
            int slot = 0;
            for (int i = startIndex; i < endIndex && slot < 45; i++) {
                String attachmentId = attachments.get(i);
                ItemStack attachmentItem = createAttachmentIcon(attachmentId, session);
                if (attachmentItem != null) {
                    inv.setItem(slot, attachmentItem);
                    slot++;
                }
            }
        }

        // Navigation row (bottom)
        // Back button
        inv.setItem(45, createNavigationItem(Material.ARROW, "&7戻る"));

        // Previous page
        if (page > 0) {
            inv.setItem(48, createNavigationItem(Material.ARROW, "&e← 前のページ"));
        }

        // Page indicator
        int maxPagesDisplay = Math.max(1, (int) Math.ceil((double) attachments.size() / itemsPerPage));
        inv.setItem(49, createPageIndicator(page + 1, maxPagesDisplay));

        // Next page
        if (page < maxPages - 1) {
            inv.setItem(50, createNavigationItem(Material.ARROW, "&e次のページ →"));
        }

        // Fill remaining bottom row
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createFillerItem(Material.BLACK_STAINED_GLASS_PANE));
            }
        }

        player.openInventory(inv);
        openGuis.put(player.getUniqueId(), GuiType.ATTACHMENT_SELECT);
    }

    /**
     * Create icon for an attachment in selection menu
     */
    private ItemStack createAttachmentIcon(String attachmentId, LoadoutManager.LoadoutEditSession session) {
        // Try to generate actual attachment item
        ItemStack item = wmIntegration.generateAttachmentItem(attachmentId);
        if (item == null) {
            item = new ItemStack(Material.IRON_NUGGET);
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        // Check if already selected for current slot
        String currentSlot = session.getCurrentAttachmentSlot();
        String selectedForSlot = session.getAttachment(currentSlot);

        if (attachmentId.equals(selectedForSlot)) {
            lore.add(Component.empty());
            lore.add(Component.text("✓ 選択中", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.setEnchantmentGlintOverride(true);
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("クリックで選択", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== Item Granting ====================

    /**
     * Grant selected items to player with weapons and ammo.
     * Starts edit mode with inventory backup.
     */
    public void grantSelectedItems(Player player) {
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null || session.getSelectedSlots().isEmpty()) {
            player.sendMessage(config.getMessageComponent("invalid-usage"));
            return;
        }

        // Close GUI first
        player.closeInventory();

        // Start edit mode (backs up and clears inventory)
        plugin.getEditModeManager().startEditMode(player);

        // Collect all items to grant
        List<ItemStack> weaponItems = new ArrayList<>();
        List<ItemStack> ammoItems = new ArrayList<>();
        List<ItemStack> consumableItems = new ArrayList<>();
        List<ItemStack> attachmentItems = new ArrayList<>();
        List<ItemStack> otherItems = new ArrayList<>();

        for (LoadoutSlot slot : session.getSelectedSlots().values()) {
            if (slot.isWmWeapon()) {
                String weaponTitle = slot.getWeaponTitle();
                String category = wmIntegration.getWeaponCategory(weaponTitle);

                // Check if weapon has ammo config (gun) or not (consumable)
                if (wmIntegration.hasAmmoConfig(weaponTitle)) {
                    // Gun with ammo
                    ItemStack weapon = wmIntegration.generateWeapon(weaponTitle);
                    if (weapon != null) {
                        weaponItems.add(weapon);

                        // Generate ammo for this weapon
                        List<ItemStack> ammo = wmIntegration.generateAmmoItems(weaponTitle);
                        ammoItems.addAll(ammo);

                        plugin.getLogger().info("Granting weapon: " + weaponTitle +
                                " with " + ammo.stream().mapToInt(ItemStack::getAmount).sum() + " ammo rounds");
                    }
                } else {
                    // Consumable without ammo (grenade, stim, knife, etc.)
                    int amount = config.getItemAmount(weaponTitle, category);
                    List<ItemStack> items = wmIntegration.generateConsumableItems(weaponTitle, amount);
                    consumableItems.addAll(items);
                }
            } else {
                // Custom item
                LoadoutsConfig.CustomItemConfig customConfig = config.getCustomItems().get(slot.getWeaponTitle());
                if (customConfig != null) {
                    ItemStack customItem = new ItemStack(customConfig.material(), customConfig.amount());
                    ItemMeta meta = customItem.getItemMeta();
                    meta.displayName(customConfig.getDisplayNameComponent());
                    customItem.setItemMeta(meta);
                    otherItems.add(customItem);
                }
            }
        }

        // Generate selected attachments
        for (Map.Entry<String, String> entry : session.getSelectedAttachments().entrySet()) {
            String attachmentId = entry.getValue();
            ItemStack attachment = wmIntegration.generateAttachmentItem(attachmentId);
            if (attachment != null) {
                attachmentItems.add(attachment);
                plugin.getLogger().info("Granting attachment: " + attachmentId);
            }
        }

        // Place items in inventory
        // Weapons go in hotbar slots (0-8)
        int hotbarSlot = 0;
        for (ItemStack weapon : weaponItems) {
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, weapon);
                hotbarSlot++;
            }
        }

        // Consumables go after weapons in hotbar
        for (ItemStack item : consumableItems) {
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, item);
                hotbarSlot++;
            } else {
                player.getInventory().addItem(item);
            }
        }

        // Other items go after consumables
        for (ItemStack item : otherItems) {
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, item);
                hotbarSlot++;
            } else {
                player.getInventory().addItem(item);
            }
        }

        // Ammo goes in main inventory (slots 9+)
        for (ItemStack ammo : ammoItems) {
            player.getInventory().addItem(ammo);
        }

        // Attachments go in main inventory
        for (ItemStack attachment : attachmentItems) {
            player.getInventory().addItem(attachment);
        }

        // Update inventory
        player.updateInventory();

        // Send message with save instructions
        int editingSlot = session.getEditingSlotNumber();

        int totalAmmo = ammoItems.stream().mapToInt(ItemStack::getAmount).sum();
        int totalConsumables = consumableItems.stream().mapToInt(ItemStack::getAmount).sum();

        player.sendMessage(Component.text("【編集モード開始】", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        StringBuilder summary = new StringBuilder();
        summary.append("武器: ").append(weaponItems.size()).append("個");
        if (totalAmmo > 0)
            summary.append(" | 弾薬: ").append(totalAmmo).append("発");
        if (totalConsumables > 0)
            summary.append(" | 消耗品: ").append(totalConsumables).append("個");
        if (!attachmentItems.isEmpty())
            summary.append(" | アタッチメント: ").append(attachmentItems.size()).append("個");

        player.sendMessage(Component.text(summary.toString(), NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("アイテムを並べ替え・アタッチメント装着後：", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  /loadout save", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .append(Component.text(" → スロット " + editingSlot + " に保存", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  /loadout cancel", NamedTextColor.RED)
                .append(Component.text(" → キャンセル（元に戻す）", NamedTextColor.GRAY)));

        // Keep session active for save command
    }

    // ==================== Event Handlers ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        GuiType guiType = openGuis.get(player.getUniqueId());
        if (guiType == null)
            return;

        event.setCancelled(true);

        if (event.getClickedInventory() == null ||
                event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        switch (guiType) {
            case MAIN_MENU -> handleMainMenuClick(player, event.getSlot(), clicked, event.isRightClick());
            case SLOT_SELECT -> handleSlotSelectClick(player, event.getSlot(), clicked);
            case CATEGORY_MENU -> handleCategoryMenuClick(player, event.getSlot(), clicked);
            case WEAPON_SELECT -> handleWeaponSelectClick(player, event.getSlot(), clicked);
            case ATTACHMENT_SELECT -> handleAttachmentSelectClick(player, event.getSlot(), clicked);
        }
    }

    private void handleMainMenuClick(Player player, int slot, ItemStack clicked, boolean rightClick) {
        // Slots 11-15 are loadout slots 1-5
        if (slot >= 11 && slot <= 15) {
            int slotNumber = slot - 10;
            Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), String.valueOf(slotNumber));

            // Apply only - no editing from menu
            if (loadout != null && loadout.hasFinalItems()) {
                boolean applied = loadoutManager.applyLoadout(player, String.valueOf(slotNumber));
                if (applied) {
                    player.closeInventory();
                    player.sendMessage(config.getMessageComponent("loadout-applied",
                            Map.of("name", "スロット " + slotNumber)));
                }
            } else {
                // No loadout saved - inform player
                player.sendMessage(Component.text("このスロットにはロードアウトが保存されていません。", NamedTextColor.RED));
                player.sendMessage(Component.text("/loadout edit で編集してください。", NamedTextColor.GRAY));
            }
        }
    }

    private void handleSlotSelectClick(Player player, int slot, ItemStack clicked) {
        // Back button
        if (slot == 22) {
            openMainMenu(player);
            return;
        }

        // Slots 11-15 are edit slots 1-5
        if (slot >= 11 && slot <= 15) {
            int slotNumber = slot - 10;
            openCategoryMenu(player, slotNumber);
        }
    }

    private void handleCategoryMenuClick(Player player, int slot, ItemStack clicked) {
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null)
            return;

        // Back button
        if (slot == 45) {
            openSlotSelectionMenu(player);
            return;
        }

        // Check if confirm button
        if (slot == 49) {
            if (!session.getSelectedSlots().isEmpty()) {
                grantSelectedItems(player);
            }
            return;
        }

        // Check weapon slot positions
        int[] slotPositions = { 10, 12, 14, 16, 28, 30, 32, 34 };
        Map<String, LoadoutsConfig.SlotConfig> slots = config.getSlots();

        int i = 0;
        for (String slotKey : slots.keySet()) {
            if (i < slotPositions.length && slotPositions[i] == slot) {
                openWeaponSelectMenu(player, slotKey);
                return;
            }
            i++;
        }

        // Check attachment slot positions (row 3: 19-23)
        int[] attachmentPositions = { 19, 20, 21, 22, 23 };
        Map<String, LoadoutsConfig.AttachmentSlotConfig> attachmentSlots = config.getAttachmentSlots();

        int j = 0;
        for (String slotKey : attachmentSlots.keySet()) {
            if (j < attachmentPositions.length && attachmentPositions[j] == slot) {
                openAttachmentSelectMenu(player, slotKey);
                return;
            }
            j++;
        }
    }

    private void handleWeaponSelectClick(Player player, int slot, ItemStack clicked) {
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null)
            return;

        // Navigation buttons
        if (slot == 45) {
            // Back button
            openCategoryMenu(player, session.getEditingSlotNumber());
            return;
        }

        if (slot == 48 && session.getCurrentPage() > 0) {
            // Previous page
            session.setCurrentPage(session.getCurrentPage() - 1);
            updateWeaponSelectMenu(player, session);
            return;
        }

        if (slot == 50) {
            // Next page
            session.setCurrentPage(session.getCurrentPage() + 1);
            updateWeaponSelectMenu(player, session);
            return;
        }

        // Weapon selection (slots 0-44)
        if (slot >= 0 && slot < 45) {
            String weaponTitle = wmIntegration.getWeaponTitle(clicked);
            if (weaponTitle != null) {
                String slotType = session.getCurrentSlotType();
                String category = wmIntegration.getWeaponCategory(weaponTitle);
                int ammo = wmIntegration.calculateAmmo(weaponTitle);

                LoadoutSlot loadoutSlot = new LoadoutSlot(slotType, weaponTitle, category, ammo);
                session.setSlot(slotType, loadoutSlot);

                // Send feedback
                Map<String, String> placeholders = Map.of(
                        "slot", config.getSlot(slotType).displayName(),
                        "weapon", weaponTitle);
                player.sendMessage(config.getMessageComponent("slot-selected", placeholders));

                // Go back to category menu
                openCategoryMenu(player, session.getEditingSlotNumber());
            } else {
                // Check for custom item
                String customItemId = getCustomItemIdFromItem(clicked);
                if (customItemId != null) {
                    String slotType = session.getCurrentSlotType();
                    LoadoutSlot loadoutSlot = new LoadoutSlot(slotType, customItemId, false);
                    session.setSlot(slotType, loadoutSlot);

                    Map<String, String> placeholders = Map.of(
                            "slot", config.getSlot(slotType).displayName(),
                            "weapon", customItemId);
                    player.sendMessage(config.getMessageComponent("slot-selected", placeholders));

                    openCategoryMenu(player, session.getEditingSlotNumber());
                }
            }
        }
    }

    private void handleAttachmentSelectClick(Player player, int slot, ItemStack clicked) {
        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null)
            return;

        // Back button
        if (slot == 45) {
            openCategoryMenu(player, session.getEditingSlotNumber());
            return;
        }

        // Previous page
        if (slot == 48 && session.getCurrentPage() > 0) {
            session.setCurrentPage(session.getCurrentPage() - 1);
            updateAttachmentSelectMenu(player, session);
            return;
        }

        // Next page
        if (slot == 50) {
            session.setCurrentPage(session.getCurrentPage() + 1);
            updateAttachmentSelectMenu(player, session);
            return;
        }

        // Skip bottom row navigation
        if (slot >= 45) {
            return;
        }

        // Attachment selection (slots 0-44)
        if (slot < 45) {
            String attachmentSlotKey = session.getCurrentAttachmentSlot();
            LoadoutsConfig.AttachmentSlotConfig slotConfig = config.getAttachmentSlot(attachmentSlotKey);
            if (slotConfig == null)
                return;

            // Get attachments for this slot
            List<String> attachments = wmIntegration.getAttachmentsForCategories(slotConfig.categories());

            int page = session.getCurrentPage();
            int itemsPerPage = config.getItemsPerPage();
            int index = page * itemsPerPage + slot;

            if (index < attachments.size()) {
                String attachmentId = attachments.get(index);

                // Set selected attachment
                session.setAttachment(attachmentSlotKey, attachmentId);

                player.sendMessage(Component.text("アタッチメント選択: ", NamedTextColor.GREEN)
                        .append(Component.text(attachmentId, NamedTextColor.YELLOW)));

                // Go back to category menu
                openCategoryMenu(player, session.getEditingSlotNumber());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openGuis.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openGuis.remove(uuid);
        loadoutManager.endEditSession(uuid);
        loadoutManager.clearCache(uuid);
    }

    // ==================== Helper Methods ====================

    private ItemStack createSlotIcon(String slotKey, LoadoutsConfig.SlotConfig slotConfig,
            LoadoutManager.LoadoutEditSession session) {

        ItemStack item;
        ItemMeta meta;

        // Check if player has selected a weapon for this slot
        if (session.hasSlot(slotKey)) {
            LoadoutSlot selected = session.getSlot(slotKey);

            // Use the actual weapon as the icon
            if (selected.isWmWeapon()) {
                ItemStack weaponItem = wmIntegration.generateWeapon(selected.getWeaponTitle());
                if (weaponItem != null) {
                    item = weaponItem;
                } else {
                    item = new ItemStack(slotConfig.icon());
                }
            } else {
                // Custom item
                LoadoutsConfig.CustomItemConfig customConfig = config.getCustomItems().get(selected.getWeaponTitle());
                if (customConfig != null) {
                    item = new ItemStack(customConfig.material());
                } else {
                    item = new ItemStack(slotConfig.icon());
                }
            }

            meta = item.getItemMeta();

            // Override display name to show slot + weapon name
            meta.displayName(slotConfig.getDisplayNameComponent()
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(selected.getWeaponTitle(), NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("✓ 選択済み", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

            if (selected.getAmmoAmount() > 0) {
                lore.add(Component.text("弾薬: " + selected.getAmmoAmount() + "発", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.empty());
            lore.add(Component.text("クリックで変更", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

            // Add glow effect
            meta.setEnchantmentGlintOverride(true);
            meta.lore(lore);

        } else {
            // No selection - use default category icon
            item = new ItemStack(slotConfig.icon());
            meta = item.getItemMeta();

            meta.displayName(slotConfig.getDisplayNameComponent()
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("未選択", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("クリックして選択", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create attachment slot icon for the category menu
     */
    private ItemStack createAttachmentSlotIcon(String slotKey, LoadoutsConfig.AttachmentSlotConfig slotConfig,
            LoadoutManager.LoadoutEditSession session) {

        ItemStack item;
        ItemMeta meta;

        // Check if player has selected an attachment for this slot
        if (session.hasAttachment(slotKey)) {
            String attachmentId = session.getAttachment(slotKey);

            // Try to generate the actual attachment item
            ItemStack attachmentItem = wmIntegration.generateAttachmentItem(attachmentId);
            if (attachmentItem != null) {
                item = attachmentItem;
            } else {
                item = new ItemStack(slotConfig.icon());
            }

            meta = item.getItemMeta();

            // Override display name
            meta.displayName(slotConfig.getDisplayNameComponent()
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(attachmentId, NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("✓ 選択済み", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("クリックで変更", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

            meta.setEnchantmentGlintOverride(true);
            meta.lore(lore);

        } else {
            // No selection - use default icon
            item = new ItemStack(slotConfig.icon());
            meta = item.getItemMeta();

            meta.displayName(slotConfig.getDisplayNameComponent()
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("未選択", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("クリックして選択", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWeaponIcon(String weaponTitle, LoadoutManager.LoadoutEditSession session) {
        ItemStack weapon = wmIntegration.generateWeapon(weaponTitle);
        if (weapon == null) {
            weapon = new ItemStack(Material.IRON_HOE);
            ItemMeta meta = weapon.getItemMeta();
            meta.displayName(Component.text(weaponTitle, NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            weapon.setItemMeta(meta);
        }

        ItemMeta meta = weapon.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        // Add our info
        lore.add(Component.empty());

        String category = wmIntegration.getWeaponCategory(weaponTitle);
        if (category != null) {
            lore.add(Component.text("カテゴリ: " + category, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        int magazineSize = wmIntegration.getMagazineSize(weaponTitle);
        int ammo = wmIntegration.calculateAmmo(weaponTitle);

        if (magazineSize > 0) {
            lore.add(Component.text("マガジン: " + magazineSize, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("予備弾薬: " + ammo, NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("クリックで選択", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        weapon.setItemMeta(meta);
        return weapon;
    }

    private ItemStack createCustomItemIcon(LoadoutsConfig.CustomItemConfig customItem,
            LoadoutManager.LoadoutEditSession session) {
        ItemStack item = new ItemStack(customItem.material(), customItem.amount());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(customItem.getDisplayNameComponent()
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("カスタムアイテム", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("クリックで選択", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfirmButton(LoadoutManager.LoadoutEditSession session) {
        boolean hasSelections = !session.getSelectedSlots().isEmpty();
        Material material = hasSelections ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (hasSelections) {
            meta.displayName(Component.text("決定 - アイテムを受け取る", NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("選択済み: " + session.getSelectedSlots().size() + " スロット",
                    NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("クリックでアイテムを受け取る", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        } else {
            meta.displayName(Component.text("アイテムを選択してください", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(name)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageIndicator(int current, int max) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("ページ " + current + "/" + Math.max(1, max), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private void fillEmpty(Inventory inv, Material material) {
        ItemStack filler = createFillerItem(material);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private String getCustomItemIdFromItem(ItemStack item) {
        // Match by material and display name
        for (Map.Entry<String, LoadoutsConfig.CustomItemConfig> entry : config.getCustomItems().entrySet()) {
            if (entry.getValue().material() == item.getType()) {
                return entry.getKey();
            }
        }
        return null;
    }

    // ==================== Enums ====================

    public enum GuiType {
        MAIN_MENU,
        SLOT_SELECT,
        CATEGORY_MENU,
        WEAPON_SELECT,
        ATTACHMENT_SELECT
    }
}
