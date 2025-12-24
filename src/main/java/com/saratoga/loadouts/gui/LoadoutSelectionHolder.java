package com.saratoga.loadouts.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom InventoryHolder for the loadout selection menu.
 * Used to reliably identify the loadout selection inventory in events.
 */
public class LoadoutSelectionHolder implements InventoryHolder {

    @Override
    public @NotNull Inventory getInventory() {
        // Not used - just for identification purposes
        return null;
    }
}
