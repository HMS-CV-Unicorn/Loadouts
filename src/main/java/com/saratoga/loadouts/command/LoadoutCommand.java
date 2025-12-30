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

        // Handle console-only commands requiring player target
        boolean isConsole = !(sender instanceof Player);

        // No args: open main menu for self (player only)
        if (args.length == 0) {
            if (isConsole) {
                sender.sendMessage(Component.text("Usage: /loadout open <player>", NamedTextColor.RED));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(config.getPermUseMenu())) {
                player.sendMessage(config.getMessageComponent("no-permission"));
                return true;
            }
            guiManager.openMainMenu(player, true);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "menu" -> {
                if (isConsole) {
                    sender.sendMessage(Component.text("Use: /loadout open <player>", NamedTextColor.RED));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermUseMenu())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                guiManager.openMainMenu(player, true);
            }
            case "open" -> handleOpen(sender, args, isConsole);
            case "edit" -> handleEdit(sender, args, isConsole);
            case "save" -> {
                if (isConsole) {
                    sender.sendMessage(config.getMessageComponent("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermSaveLoadout())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                handleSave(player);
            }
            case "cancel" -> {
                if (isConsole) {
                    sender.sendMessage(config.getMessageComponent("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermCancelLoadout())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                boolean silent = args.length >= 2 && args[1].equalsIgnoreCase("-s");
                handleCancel(player, silent);
            }
            case "give" -> {
                if (isConsole) {
                    sender.sendMessage(config.getMessageComponent("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermGiveLoadout())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                handleGive(player, args);
            }
            case "list" -> {
                if (isConsole) {
                    sender.sendMessage(config.getMessageComponent("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermListLoadout())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                handleList(player);
            }
            case "delete" -> {
                if (isConsole) {
                    sender.sendMessage(config.getMessageComponent("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermDeleteLoadout())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                handleDelete(player, args);
            }
            case "rename" -> {
                if (isConsole) {
                    sender.sendMessage(config.getMessageComponent("player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission(config.getPermRename())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                handleRename(player, args);
            }
            case "reload" -> handleReload(sender);
            case "syncwm" -> {
                // Admin command - check permission
                if (sender instanceof Player player && !player.hasPermission(config.getPermSyncwm())) {
                    player.sendMessage(config.getMessageComponent("no-permission"));
                    return true;
                }
                handleSyncWm(sender);
            }
            default -> sender.sendMessage(config.getMessageComponent("invalid-usage"));
        }

        return true;
    }

    /**
     * /loadout open [player] - Open selection menu for self or target player
     */
    private void handleOpen(CommandSender sender, String[] args, boolean isConsole) {
        if (args.length < 2) {
            // No target - open for self if player
            if (isConsole) {
                sender.sendMessage(Component.text("Usage: /loadout open <player>", NamedTextColor.RED));
                return;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(config.getPermUseMenu())) {
                player.sendMessage(config.getMessageComponent("no-permission"));
                return;
            }
            guiManager.openMainMenu(player, true);
            return;
        }

        // Target player specified - need admin permission
        if (!isConsole && !((Player) sender).hasPermission(config.getPermAdminOpenOther())) {
            sender.sendMessage(config.getMessageComponent("no-permission"));
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(config.getMessageComponent("player-not-found", Map.of("player", args[1])));
            return;
        }

        guiManager.openMainMenu(target, true);
        sender.sendMessage(config.getMessageComponent("opened-for-player", Map.of("player", target.getName())));
    }

    /**
     * /loadout edit [slotNumber] - Edit own loadout
     * /loadout edit <player> <slotNumber> - Edit other player's loadout (admin)
     * /loadout edit global <slotNumber> - Edit global loadout (admin)
     */
    private void handleEdit(CommandSender sender, String[] args, boolean isConsole) {
        // Global edit: /loadout edit global <slot>
        if (args.length >= 2 && args[1].equalsIgnoreCase("global")) {
            if (isConsole) {
                sender.sendMessage(config.getMessageComponent("player-only"));
                return;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(config.getPermEditGlobal())) {
                player.sendMessage(Component.text("グローバルロードアウトの編集には管理者権限が必要です。", NamedTextColor.RED));
                return;
            }
            if (args.length < 3) {
                player.sendMessage(Component.text("使用法: /loadout edit global <1-5>", NamedTextColor.RED));
                return;
            }
            try {
                int slotNumber = Integer.parseInt(args[2]);
                if (slotNumber < 1 || slotNumber > 5) {
                    player.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                    return;
                }
                guiManager.openCategoryMenuForGlobal(player, slotNumber);
                player.sendMessage(Component.text("グローバルロードアウト スロット " + slotNumber + " を編集中...", NamedTextColor.GOLD));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("無効なスロット番号です: " + args[2], NamedTextColor.RED));
            }
            return;
        }

        // Check if targeting another player: /loadout edit <player> [slot]
        if (args.length >= 2 && !isConsole) {
            // Try to parse as slot number first (self edit)
            try {
                int slotNumber = Integer.parseInt(args[1]);
                if (slotNumber >= 1 && slotNumber <= 5) {
                    Player player = (Player) sender;
                    if (!player.hasPermission(config.getPermEditLoadout())) {
                        player.sendMessage(config.getMessageComponent("no-permission"));
                        return;
                    }
                    guiManager.openCategoryMenu(player, slotNumber);
                    return;
                }
            } catch (NumberFormatException e) {
                // Not a number - treat as player name
            }
        }

        // /loadout edit <player> - open slot selection for target player
        if (args.length == 2) {
            // Admin targeting another player (slot selection menu)
            if (!isConsole && !((Player) sender).hasPermission(config.getPermAdminOpenOther())) {
                sender.sendMessage(config.getMessageComponent("no-permission"));
                return;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(config.getMessageComponent("player-not-found", Map.of("player", args[1])));
                return;
            }
            guiManager.openSlotSelectionMenu(target);
            sender.sendMessage(Component.text(target.getName() + " の編集スロット選択画面を開きました。", NamedTextColor.GREEN));
            return;
        }

        // /loadout edit <player> <slot> - Admin targeting another player with specific
        // slot
        if (args.length >= 3) {
            if (!isConsole && !((Player) sender).hasPermission(config.getPermAdminOpenOther())) {
                sender.sendMessage(config.getMessageComponent("no-permission"));
                return;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(config.getMessageComponent("player-not-found", Map.of("player", args[1])));
                return;
            }
            try {
                int slotNumber = Integer.parseInt(args[2]);
                if (slotNumber < 1 || slotNumber > 5) {
                    sender.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                    return;
                }
                guiManager.openCategoryMenu(target, slotNumber);
                sender.sendMessage(Component.text(target.getName() + " のスロット " + slotNumber + " を編集開始しました。",
                        NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("無効なスロット番号です: " + args[2], NamedTextColor.RED));
            }
            return;
        }

        // Self edit (player only) - no args
        if (isConsole) {
            sender.sendMessage(Component.text("Usage: /loadout edit <player> [slot]", NamedTextColor.RED));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(config.getPermEditLoadout())) {
            player.sendMessage(config.getMessageComponent("no-permission"));
            return;
        }
        guiManager.openSlotSelectionMenu(player);
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
     * /loadout cancel [-s]
     * Cancels edit mode and restores original inventory
     * 
     * @param silent If true, silently does nothing when not in edit mode (for
     *               preventive calls)
     */
    private void handleCancel(Player player, boolean silent) {
        if (!plugin.getEditModeManager().isInEditMode(player)) {
            // Silent mode: don't show error message
            if (!silent) {
                player.sendMessage(Component.text("編集モード中ではありません。",
                        NamedTextColor.RED));
            }
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
     * /loadout rename <slot> <name> - Rename personal loadout
     * /loadout rename global <slot> <name> - Rename global loadout (admin)
     */
    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /loadout rename <1-5> <名前>", NamedTextColor.RED));
            player.sendMessage(Component.text("管理者: /loadout rename global <1-5> <名前>", NamedTextColor.GRAY));
            return;
        }

        boolean isGlobal = args[1].equalsIgnoreCase("global");

        if (isGlobal) {
            // Global rename: /loadout rename global <slot> <name>
            if (!player.hasPermission("loadouts.admin")) {
                player.sendMessage(Component.text("グローバルロードアウトの編集には管理者権限が必要です。", NamedTextColor.RED));
                return;
            }
            if (args.length < 4) {
                player.sendMessage(Component.text("使用法: /loadout rename global <1-5> <名前>", NamedTextColor.RED));
                return;
            }
            try {
                int slotNumber = Integer.parseInt(args[2]);
                if (slotNumber < 1 || slotNumber > 5) {
                    player.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                    return;
                }
                // Join remaining args as name (supports spaces)
                String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                // Convert color codes (& -> §)
                newName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .serialize(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(newName));

                Loadout loadout = loadoutManager.getGlobalLoadout(String.valueOf(slotNumber));
                if (loadout == null) {
                    player.sendMessage(
                            Component.text("グローバルスロット " + slotNumber + " にはロードアウトがありません。", NamedTextColor.RED));
                    return;
                }
                loadout.setDisplayName(newName);
                loadoutManager.saveLoadout(loadout);
                player.sendMessage(Component.text("[Global] スロット " + slotNumber + " を \"" + newName + "\" に名前変更しました。",
                        NamedTextColor.GOLD));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("無効なスロット番号です: " + args[2], NamedTextColor.RED));
            }
        } else {
            // Personal rename: /loadout rename <slot> <name>
            try {
                int slotNumber = Integer.parseInt(args[1]);
                if (slotNumber < 1 || slotNumber > 5) {
                    player.sendMessage(Component.text("スロット番号は1〜5を指定してください", NamedTextColor.RED));
                    return;
                }
                // Join remaining args as name
                String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                // Convert color codes
                newName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                        .serialize(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(newName));

                Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), String.valueOf(slotNumber));
                if (loadout == null) {
                    player.sendMessage(Component.text("スロット " + slotNumber + " にはロードアウトがありません。", NamedTextColor.RED));
                    return;
                }
                loadout.setDisplayName(newName);
                loadoutManager.saveLoadout(loadout);
                player.sendMessage(Component.text("スロット " + slotNumber + " を \"" + newName + "\" に名前変更しました。",
                        NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("無効なスロット番号です: " + args[1], NamedTextColor.RED));
            }
        }
    }

    /**
     * /loadout reload
     */
    private void handleReload(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission(config.getPermReload())) {
            player.sendMessage(config.getMessageComponent("no-permission"));
            return;
        }

        plugin.reloadConfig();
        plugin.getWmIntegration().scanWeapons();
        loadoutManager.clearAllCaches();

        sender.sendMessage(Component.text("設定をリロードしました。", NamedTextColor.GREEN));
    }

    /**
     * /loadout syncwm
     * Re-sync weapon and attachment list from WeaponMechanics
     */
    private void handleSyncWm(CommandSender sender) {
        sender.sendMessage(Component.text("WeaponMechanicsから武器・アタッチメントリストを再取得中...", NamedTextColor.YELLOW));

        // Re-scan weapons and attachments
        plugin.getWmIntegration().scanWeapons();
        plugin.getWmIntegration().scanAttachments();

        int weaponCount = plugin.getWmIntegration().getTotalWeaponCount();
        int attachmentCount = plugin.getWmIntegration().getTotalAttachmentCount();

        sender.sendMessage(Component.text("同期完了: " + weaponCount + "個の武器、" + attachmentCount + "個のアタッチメント",
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
        if (!(sender instanceof Player player)) {
            // Console gets all commands
            if (args.length == 1) {
                return filterStartsWith(args[0],
                        Arrays.asList("open", "edit", "reload", "syncwm"));
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Build subcommand list based on permissions
            List<String> available = new ArrayList<>();

            // User commands - only show if player has permission
            if (player.hasPermission(config.getPermUseMenu())) {
                available.add("menu");
                available.add("open");
            }
            if (player.hasPermission(config.getPermEditLoadout())) {
                available.add("edit");
            }
            if (player.hasPermission(config.getPermSaveLoadout())) {
                available.add("save");
            }
            if (player.hasPermission(config.getPermCancelLoadout())) {
                available.add("cancel");
            }
            if (player.hasPermission(config.getPermGiveLoadout())) {
                available.add("give");
            }
            if (player.hasPermission(config.getPermListLoadout())) {
                available.add("list");
            }
            if (player.hasPermission(config.getPermDeleteLoadout())) {
                available.add("delete");
            }
            if (player.hasPermission(config.getPermRename())) {
                available.add("rename");
            }

            // Admin commands - hidden from non-admins
            if (player.hasPermission(config.getPermReload())) {
                available.add("reload");
            }
            if (player.hasPermission(config.getPermSyncwm())) {
                available.add("syncwm");
            }

            return filterStartsWith(args[0], available);
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
