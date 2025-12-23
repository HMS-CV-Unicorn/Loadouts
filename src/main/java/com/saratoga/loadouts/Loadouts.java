package com.saratoga.loadouts;

import com.saratoga.loadouts.command.LoadoutCommand;
import com.saratoga.loadouts.data.DatabaseManager;
import com.saratoga.loadouts.data.EditModeManager;
import com.saratoga.loadouts.data.LoadoutManager;
import com.saratoga.loadouts.gui.GuiManager;
import com.saratoga.loadouts.integration.WeaponMechanicsIntegration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class Loadouts extends JavaPlugin {

    private static Loadouts instance;

    private LoadoutsConfig config;
    private DatabaseManager databaseManager;
    private LoadoutManager loadoutManager;
    private GuiManager guiManager;
    private WeaponMechanicsIntegration wmIntegration;
    private EditModeManager editModeManager;

    @Override
    public void onEnable() {
        instance = this;

        // Check for WeaponMechanics
        if (getServer().getPluginManager().getPlugin("WeaponMechanics") == null) {
            getLogger().severe("WeaponMechanics not found! This plugin requires WeaponMechanics.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Save default config
        saveDefaultConfig();

        // Initialize configuration
        this.config = new LoadoutsConfig(this);

        // Initialize database
        this.databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
            getLogger().info("Database connection established.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize WeaponMechanics integration (will scan later)
        this.wmIntegration = new WeaponMechanicsIntegration(this);

        // Initialize managers
        this.loadoutManager = new LoadoutManager(this);
        this.guiManager = new GuiManager(this);
        this.editModeManager = new EditModeManager(this);

        // Register commands
        LoadoutCommand commandExecutor = new LoadoutCommand(this);
        getCommand("loadout").setExecutor(commandExecutor);
        getCommand("loadout").setTabCompleter(commandExecutor);

        // Register listeners
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(editModeManager, this);

        // Delay weapon and attachment scanning to ensure WeaponMechanics has finished
        // loading
        getServer().getScheduler().runTaskLater(this, () -> {
            wmIntegration.scanWeapons();
            wmIntegration.scanAttachments();
            getLogger().info("Loaded " + wmIntegration.getTotalWeaponCount() + " weapons and " +
                    wmIntegration.getTotalAttachmentCount() + " attachments from WeaponMechanics.");
        }, 40L);

        getLogger().info("Loadouts plugin enabled. Weapon/attachment scan will complete shortly...");
    }

    @Override
    public void onDisable() {
        // Restore all players in edit mode
        if (editModeManager != null) {
            editModeManager.onServerShutdown();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("Loadouts plugin disabled.");
    }

    public static Loadouts getInstance() {
        return instance;
    }

    public LoadoutsConfig getLoadoutsConfig() {
        return config;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LoadoutManager getLoadoutManager() {
        return loadoutManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public WeaponMechanicsIntegration getWmIntegration() {
        return wmIntegration;
    }

    public EditModeManager getEditModeManager() {
        return editModeManager;
    }
}
