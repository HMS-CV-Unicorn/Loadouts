package com.saratoga.loadouts.data;

import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a player's saved loadout.
 */
public class Loadout {

    private int id;
    private UUID playerUUID;
    private String name;
    private final Map<String, LoadoutSlot> slots;
    private final Map<String, String> attachments; // slotKey -> attachmentId
    private List<ItemStack> finalItems;
    private long createdAt;
    private long updatedAt;

    /**
     * Create a new loadout (for creation)
     */
    public Loadout(UUID playerUUID, String name) {
        this.id = -1;
        this.playerUUID = playerUUID;
        this.name = name;
        this.slots = new LinkedHashMap<>();
        this.attachments = new LinkedHashMap<>();
        this.finalItems = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Create a loadout from database
     */
    public Loadout(int id, UUID playerUUID, String name, long createdAt, long updatedAt) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.name = name;
        this.slots = new LinkedHashMap<>();
        this.attachments = new LinkedHashMap<>();
        this.finalItems = new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, LoadoutSlot> getSlots() {
        return slots;
    }

    public void setSlot(String slotType, LoadoutSlot slot) {
        slots.put(slotType, slot);
        updatedAt = System.currentTimeMillis();
    }

    public LoadoutSlot getSlot(String slotType) {
        return slots.get(slotType);
    }

    // Attachment methods
    public Map<String, String> getAttachments() {
        return attachments;
    }

    public void setAttachment(String slotKey, String attachmentId) {
        attachments.put(slotKey, attachmentId);
        updatedAt = System.currentTimeMillis();
    }

    public String getAttachment(String slotKey) {
        return attachments.get(slotKey);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }

    public List<ItemStack> getFinalItems() {
        return finalItems;
    }

    public void setFinalItems(List<ItemStack> finalItems) {
        this.finalItems = new ArrayList<>(finalItems);
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Check if this loadout has been saved to database
     */
    public boolean isSaved() {
        return id > 0;
    }

    /**
     * Check if the loadout has final items (post-arrangement save)
     */
    public boolean hasFinalItems() {
        return finalItems != null && !finalItems.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Loadout loadout = (Loadout) o;
        return id == loadout.id &&
                Objects.equals(playerUUID, loadout.playerUUID) &&
                Objects.equals(name, loadout.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, playerUUID, name);
    }

    @Override
    public String toString() {
        return "Loadout{" +
                "id=" + id +
                ", playerUUID=" + playerUUID +
                ", name='" + name + '\'' +
                ", slots=" + slots.size() +
                ", finalItems=" + (finalItems != null ? finalItems.size() : 0) +
                '}';
    }
}
