package com.saratoga.loadouts.data;

import java.util.Objects;

/**
 * Represents a single slot in a loadout (e.g., primary weapon, secondary,
 * throwable).
 */
public class LoadoutSlot {

    private final String slotType;
    private final String weaponTitle;
    private final String category;
    private final boolean wmWeapon;
    private final int ammoAmount;

    /**
     * Create a slot for a WeaponMechanics weapon
     */
    public LoadoutSlot(String slotType, String weaponTitle, String category, int ammoAmount) {
        this.slotType = slotType;
        this.weaponTitle = weaponTitle;
        this.category = category;
        this.wmWeapon = true;
        this.ammoAmount = ammoAmount;
    }

    /**
     * Create a slot for a custom (non-WM) item
     */
    public LoadoutSlot(String slotType, String itemId, boolean isWmWeapon) {
        this.slotType = slotType;
        this.weaponTitle = itemId;
        this.category = null;
        this.wmWeapon = isWmWeapon;
        this.ammoAmount = 0;
    }

    /**
     * Full constructor for database reconstruction
     */
    public LoadoutSlot(String slotType, String weaponTitle, String category, boolean wmWeapon, int ammoAmount) {
        this.slotType = slotType;
        this.weaponTitle = weaponTitle;
        this.category = category;
        this.wmWeapon = wmWeapon;
        this.ammoAmount = ammoAmount;
    }

    public String getSlotType() {
        return slotType;
    }

    public String getWeaponTitle() {
        return weaponTitle;
    }

    public String getCategory() {
        return category;
    }

    public boolean isWmWeapon() {
        return wmWeapon;
    }

    public int getAmmoAmount() {
        return ammoAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        LoadoutSlot that = (LoadoutSlot) o;
        return wmWeapon == that.wmWeapon &&
                ammoAmount == that.ammoAmount &&
                Objects.equals(slotType, that.slotType) &&
                Objects.equals(weaponTitle, that.weaponTitle) &&
                Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotType, weaponTitle, category, wmWeapon, ammoAmount);
    }

    @Override
    public String toString() {
        return "LoadoutSlot{" +
                "slotType='" + slotType + '\'' +
                ", weaponTitle='" + weaponTitle + '\'' +
                ", category='" + category + '\'' +
                ", wmWeapon=" + wmWeapon +
                ", ammoAmount=" + ammoAmount +
                '}';
    }
}
