package me.beekrbonkr.practicecore.pvpbot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One PvP kit exactly as pvpbot.yml defines it: the player's loadout
 * (inventory slots 0-35, armor 36-39), the consumables the refill task keeps
 * topped up, and the armor and weapon the bot mirrors.
 *
 * Kits used to be a fixed enum; they are data now, so a server can retune the
 * shipped ones, drop them, or add its own. The <em>id</em> is what player
 * preferences and remembered kit layouts are stored under, so renaming a kit's
 * display text is free while changing its id starts those players over.
 *
 * Slot 8 is left free in the bundled kits so the practice menu item keeps its
 * hotbar home; a kit that genuinely needs all nine may take it, and the menu
 * item then falls back to the first free slot.
 */
public record PvpKit(String id, String configuredName, Material icon, boolean hasBlocks,
                     Map<Integer, ItemStack> contents, Map<Material, Integer> refillTargets,
                     Material botWeapon) {

    /** Armor contents slots, matching {@code PlayerInventory#setItem}. */
    public static final int BOOTS = 36;
    public static final int LEGGINGS = 37;
    public static final int CHESTPLATE = 38;
    public static final int HELMET = 39;

    /** The player's loadout. The returned map is a fresh copy of fresh clones. */
    public Map<Integer, ItemStack> kit() {
        Map<Integer, ItemStack> copy = new LinkedHashMap<>();
        contents.forEach((slot, item) -> copy.put(slot, item.clone()));
        return copy;
    }

    /**
     * What the refill task keeps in stock: for each material, the total amount
     * the inventory should always hold, paired with the kit's own stack of
     * that material so a refilled potion keeps its potion type.
     */
    public Map<ItemStack, Integer> refills() {
        Map<ItemStack, Integer> refills = new LinkedHashMap<>();
        refillTargets.forEach((material, amount) -> {
            ItemStack template = templateFor(material);
            if (template != null && amount > 0) {
                refills.put(template, amount);
            }
        });
        return refills;
    }

    /**
     * The kit's own stack of a material, so refills hand out exactly what the
     * kit contained. A material the kit does not carry still refills, as a
     * plain stack — useful for arrows a bow-only kit expects to be given.
     */
    private ItemStack templateFor(Material material) {
        for (ItemStack item : contents.values()) {
            if (item.getType() == material) {
                return item.asOne();
            }
        }
        return material.isItem() ? new ItemStack(material) : null;
    }

    /** The bot's mirrored loadout: [helmet, chestplate, leggings, boots, weapon]. */
    public ItemStack[] botGear() {
        ItemStack weapon = botWeapon != null ? new ItemStack(botWeapon) : mirroredWeapon();
        return new ItemStack[]{
                clone(contents.get(HELMET)), clone(contents.get(CHESTPLATE)),
                clone(contents.get(LEGGINGS)), clone(contents.get(BOOTS)), weapon};
    }

    /**
     * With no explicit {@code bot-weapon}, the bot swings whatever sits in the
     * first hotbar slot — except a bow, which it cannot melee with, so a
     * bow-first kit hands it slot 1 instead.
     */
    private ItemStack mirroredWeapon() {
        ItemStack first = contents.get(0);
        if (first != null && first.getType() == Material.BOW) {
            ItemStack second = contents.get(1);
            return clone(second != null ? second : first);
        }
        return clone(first);
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /** A readable name for a kit with no configured or translated one. */
    public String prettyId() {
        String cleaned = id.replace('_', ' ').replace('-', ' ');
        return cleaned.isEmpty() ? id
                : cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    /** The messages.yml key this kit's display name lives under, if any. */
    public String messageKey() {
        return "gui.pvpbot.kit.option." + id;
    }
}
