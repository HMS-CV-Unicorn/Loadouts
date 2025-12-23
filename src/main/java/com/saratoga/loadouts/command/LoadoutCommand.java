package com.saratoga.loadouts.command;

import com.saratoga.loadouts.Loadouts;
import com.saratoga.loadouts.LoadoutsConfig;
import com.saratoga.loadouts.data.Loadout;
import com.saratoga.loadouts.data.LoadoutManager;
import com.saratoga.loadouts.gui.GuiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Handles /loadout command with subcommands.
 * Updated for slot-based (1-5) loadout management.
 */
public class LoadoutCommand implements CommandExecutor, TabCompleter {

    private final Loadouts plugin;
    private final LoadoutManager loadoutManager;
    private final GuiManager guiManager;
    private final LoadoutsConfig config;

    // For delete confirmation
    private final Map<UUID, String> pendingDeletes = new HashMap<>();

    public LoadoutCommand(Loadouts plugin) {
        this.plugin = plugin;
        this.loadoutManager = plugin.getLoadoutManager();
        this.guiManager = plugin.getGuiManager();
        this.config = plugin.getLoadoutsConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getMessageComponent("player-only"));
            return true;
        }

        if (!player.hasPermission("loadouts.use")) {
            player.sendMessage(config.getMessageComponent("no-permission"));
            return true;
        }

        // Default command (no args) opens main menu
        if (args.length == 0) {
            guiManager.openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "menu" -> guiManager.openMainMenu(player);
            case "edit" -> handleEdit(player, args);
            case "save" -> handleSave(player);
            case "cancel" -> handleCancel(player);
            case "give" -> handleGive(player, args);
            case "list" -> handleList(player);
            case "delete" -> handleDelete(player, args);
            case "reload" -> handleReload(player);
            case "syncwm" -> handleSyncWm(player);
            default -> player.sendMessage(config.getMessageComponent("invalid-usage"));
        }

        return true;
    }

    /**
     * /loadout edit [slotNumber]
     */
    private void handleEdit(Player player, String[] args) {
        if (args.length >= 2) {
            // Slot number specified
            try {
                int slotNumber = Integer.parseInt(args[1]);
                if (slotNumber < 1 || slotNumber > 5) {
                    player.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                    return;
                }
                guiManager.openCategoryMenu(player, slotNumber);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("無効なスロット番号です: " + args[1], NamedTextColor.RED));
            }
        } else {
            // Open slot selection GUI
            guiManager.openSlotSelectionMenu(player);
        }
    }

    /**
     * /loadout save
     * Saves current inventory to the slot being edited
     */
    private void handleSave(Player player) {
        // Check if in edit mode
        if (!plugin.getEditModeManager().isInEditMode(player)) {
            player.sendMessage(Component.text("編集モード中ではありません。先に /loadout edit で編集を開始してください。",
                    NamedTextColor.RED));
            return;
        }

        LoadoutManager.LoadoutEditSession session = loadoutManager.getEditSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("編集セッションが見つかりません。",
                    NamedTextColor.RED));
            plugin.getEditModeManager().cancelEditMode(player, "セッションエラー");
            return;
        }

        String slotNumber = String.valueOf(session.getEditingSlotNumber());
        player.sendMessage(Component.text("ロードアウトをスロット " + slotNumber + " に保存中...",
                NamedTextColor.YELLOW));

        loadoutManager.saveCurrentInventory(player, slotNumber)
                .thenAccept(success -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // End edit mode and restore original inventory
                        plugin.getEditModeManager().endEditMode(player, success);
                        loadoutManager.endEditSession(player.getUniqueId());

                        if (success) {
                            player.sendMessage(config.getMessageComponent("loadout-saved",
                                    Map.of("name", "スロット " + slotNumber)));
                        } else {
                            player.sendMessage(Component.text("ロードアウトの保存に失敗しました。",
                                    NamedTextColor.RED));
                        }
                    });
                });
    }

    /**
     * /loadout cancel
     * Cancels edit mode and restores original inventory
     */
    private void handleCancel(Player player) {
        if (!plugin.getEditModeManager().isInEditMode(player)) {
            player.sendMessage(Component.text("編集モード中ではありません。",
                    NamedTextColor.RED));
            return;
        }

        // Cancel edit mode and restore inventory
        plugin.getEditModeManager().cancelEditMode(player, "ユーザーによるキャンセル");
        loadoutManager.endEditSession(player.getUniqueId());
    }

    /**
     * /loadout give <slotNumber>
     */
    private void handleGive(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /loadout give <1-5>", NamedTextColor.RED));
            return;
        }

        try {
            int slotNumber = Integer.parseInt(args[1]);
            if (slotNumber < 1 || slotNumber > 5) {
                player.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                return;
            }

            String slotName = String.valueOf(slotNumber);
            boolean applied = loadoutManager.applyLoadout(player, slotName);

            if (applied) {
                player.sendMessage(config.getMessageComponent("loadout-applied",
                        Map.of("name", "スロット " + slotNumber)));
            } else {
                player.sendMessage(config.getMessageComponent("no-loadout",
                        Map.of("name", "スロット " + slotNumber)));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("無効なスロット番号です: " + args[1], NamedTextColor.RED));
        }
    }

    /**
     * /loadout list
     */
    private void handleList(Player player) {
        player.sendMessage(config.getMessageComponent("loadout-list-header"));

        boolean hasAny = false;
        for (int i = 1; i <= 5; i++) {
            Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), String.valueOf(i));
            if (loadout != null && loadout.hasFinalItems()) {
                hasAny = true;
                String date = java.text.DateFormat.getDateInstance()
                        .format(new java.util.Date(loadout.getCreatedAt()));

                player.sendMessage(config.getMessageComponent("loadout-list-item",
                        Map.of("name", "スロット " + i, "date", date)));
            }
        }

        if (!hasAny) {
            player.sendMessage(config.getMessageComponent("loadout-list-empty"));
        }
    }

    /**
     * /loadout delete <slotNumber>
     */
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /loadout delete <1-5>", NamedTextColor.RED));
            return;
        }

        try {
            int slotNumber = Integer.parseInt(args[1]);
            if (slotNumber < 1 || slotNumber > 5) {
                player.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                return;
            }

            String slotName = String.valueOf(slotNumber);
            UUID uuid = player.getUniqueId();

            // Check if pending confirmation
            String pending = pendingDeletes.get(uuid);
            if (pending != null && pending.equals(slotName)) {
                // Confirmed, delete
                pendingDeletes.remove(uuid);

                loadoutManager.deleteLoadout(uuid, slotName)
                        .thenAccept(success -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (success) {
                                    player.sendMessage(config.getMessageComponent("loadout-deleted",
                                            Map.of("name", "スロット " + slotNumber)));
                                } else {
                                    player.sendMessage(config.getMessageComponent("no-loadout",
                                            Map.of("name", "スロット " + slotNumber)));
                                }
                            });
                        });
            } else {
                // Request confirmation
                pendingDeletes.put(uuid, slotName);
                player.sendMessage(config.getMessageComponent("confirm-delete",
                        Map.of("name", "スロット " + slotNumber)));

                // Clear pending after 10 seconds
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (slotName.equals(pendingDeletes.get(uuid))) {
                        pendingDeletes.remove(uuid);
                    }
                }, 200L);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("無効なスロット番号です: " + args[1], NamedTextColor.RED));
        }
    }

    /**
     * /loadout reload
     */
    private void handleReload(Player player) {
        if (!player.hasPermission("loadouts.admin")) {
            player.sendMessage(config.getMessageComponent("no-permission"));
            return;
        }

        plugin.reloadConfig();
        plugin.getWmIntegration().scanWeapons();
        loadoutManager.clearAllCaches();

        player.sendMessage(Component.text("設定をリロードしました。", NamedTextColor.GREEN));
    }

    /**
     * /loadout syncwm
     * Re-sync weapon list from WeaponMechanics
     */
    private void handleSyncWm(Player player) {
        if (!player.hasPermission("loadouts.admin")) {
            player.sendMessage(config.getMessageComponent("no-permission"));
            return;
        }

        player.sendMessage(Component.text("WeaponMechanicsから武器リストを再取得中...", NamedTextColor.YELLOW));

        // Re-scan weapons
        plugin.getWmIntegration().scanWeapons();

        int weaponCount = plugin.getWmIntegration().getTotalWeaponCount();
        player.sendMessage(Component.text("武器リストを同期しました。" + weaponCount + "個の武器を読み込みました。",
                NamedTextColor.GREEN));

        // Log category breakdown
        for (Map.Entry<String, java.util.List<String>> entry : plugin.getWmIntegration().getCategorizedWeapons()
                .entrySet()) {
            plugin.getLogger().info("Category '" + entry.getKey() + "': " + entry.getValue().size() + " weapons");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartsWith(args[0],
                    Arrays.asList("menu", "edit", "save", "cancel", "give", "list", "delete", "reload", "syncwm"));
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("edit") || subCommand.equals("give") || subCommand.equals("delete")) {
                return filterStartsWith(args[1], Arrays.asList("1", "2", "3", "4", "5"));
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(String prefix, List<String> options) {
        String lowerPrefix = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerPrefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
