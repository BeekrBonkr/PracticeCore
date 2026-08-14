package me.beekrbonkr.practicecore.pvpbot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The built-in PvP kit presets, browsed and picked in the kit gallery. Each
 * defines the player's loadout (inventory slots 0-35, armor 36-39), the
 * consumables the refill task keeps topped up, and the armor/weapon the bot
 * mirrors. Some kits carry blocks — placing is allowed in pvpbot arenas and
 * every stock reset reverts what was built.
 *
 * Slot 8 is left free everywhere so the practice menu item keeps its hotbar
 * home; kits that genuinely need all nine hotbar slots may take it, and the
 * menu item then falls back to the first free slot.
 *
 * All gear is unbreakable — an endless spar must never grind a sword away.
 */
public enum PvpKit {

    /** Sword + gapples, iron armor — pure melee combo practice. */
    SWORD,
    /** Full diamond, sword and a bar of healing pots — the classic pot duel. */
    NODEBUFF,
    /** A lone iron sword, no armor, no healing — aim and strafe training. */
    BOXING,
    /** Sword, rod, bow, gapples and cobble over iron — the full duel kit. */
    BUILDUHC,
    /** Stone sword and nothing else on your body — knockback reads clean. */
    COMBO,
    /** Full diamond and a mountain of gapples — long grinding fights. */
    GAPPLE,
    /** The honest iron kit. */
    IRON,
    /** The honest diamond kit. */
    DIAMOND,
    /** Leather, stone sword and a bow — old server classic. */
    CLASSIC,
    /** Iron sword and an inventory of stew — soup PvP. */
    SOUP,
    /** A bow first, a sword second. */
    ARCHER,
    /** The axe kit — heavier swings, chainmail. */
    AXE,
    /** Stone sword, two stacks of wool and shears — bridge and brawl. */
    BEDWARS,
    /** Sandstone, snowballs and a stone sword — island fighting. */
    SKYWARS;

    /** Armor contents slots, matching {@code PlayerInventory#setItem}. */
    public static final int BOOTS = 36;
    public static final int LEGGINGS = 37;
    public static final int CHESTPLATE = 38;
    public static final int HELMET = 39;

    /** The player's loadout. Slot 8 stays free for the practice menu item. */
    public Map<Integer, ItemStack> kit() {
        Map<Integer, ItemStack> kit = new LinkedHashMap<>();
        switch (this) {
            case SWORD -> {
                kit.put(0, unbreakable(Material.DIAMOND_SWORD));
                kit.put(1, new ItemStack(Material.GOLDEN_APPLE, 8));
                armor(kit, "IRON");
            }
            case NODEBUFF -> {
                kit.put(0, unbreakable(Material.DIAMOND_SWORD));
                for (int slot = 1; slot <= 7; slot++) {
                    kit.put(slot, healthPotion());
                }
                for (int slot = 18; slot <= 26; slot++) {
                    kit.put(slot, healthPotion());
                }
                kit.put(27, speedPotion());
                kit.put(28, speedPotion());
                armor(kit, "DIAMOND");
            }
            case BOXING -> kit.put(0, unbreakable(Material.IRON_SWORD));
            case BUILDUHC -> {
                kit.put(0, unbreakable(Material.DIAMOND_SWORD));
                kit.put(1, unbreakable(Material.FISHING_ROD));
                kit.put(2, unbreakable(Material.BOW));
                kit.put(3, new ItemStack(Material.GOLDEN_APPLE, 6));
                kit.put(4, new ItemStack(Material.COBBLESTONE, 64));
                kit.put(9, new ItemStack(Material.ARROW, 16));
                kit.put(10, new ItemStack(Material.COBBLESTONE, 64));
                armor(kit, "IRON");
            }
            case COMBO -> {
                kit.put(0, unbreakable(Material.STONE_SWORD));
                kit.put(1, new ItemStack(Material.GOLDEN_APPLE, 4));
            }
            case GAPPLE -> {
                kit.put(0, unbreakable(Material.DIAMOND_SWORD));
                kit.put(1, new ItemStack(Material.GOLDEN_APPLE, 64));
                armor(kit, "DIAMOND");
            }
            case IRON -> {
                kit.put(0, unbreakable(Material.IRON_SWORD));
                kit.put(1, new ItemStack(Material.GOLDEN_APPLE, 3));
                armor(kit, "IRON");
            }
            case DIAMOND -> {
                kit.put(0, unbreakable(Material.DIAMOND_SWORD));
                kit.put(1, new ItemStack(Material.GOLDEN_APPLE, 3));
                armor(kit, "DIAMOND");
            }
            case CLASSIC -> {
                kit.put(0, unbreakable(Material.STONE_SWORD));
                kit.put(1, unbreakable(Material.BOW));
                kit.put(9, new ItemStack(Material.ARROW, 16));
                armor(kit, "LEATHER");
            }
            case SOUP -> {
                kit.put(0, unbreakable(Material.IRON_SWORD));
                for (int slot = 1; slot <= 7; slot++) {
                    kit.put(slot, new ItemStack(Material.MUSHROOM_STEW));
                }
                for (int slot = 9; slot <= 26; slot++) {
                    kit.put(slot, new ItemStack(Material.MUSHROOM_STEW));
                }
                armor(kit, "IRON");
            }
            case ARCHER -> {
                kit.put(0, unbreakable(Material.BOW));
                kit.put(1, unbreakable(Material.STONE_SWORD));
                kit.put(2, new ItemStack(Material.ARROW, 64));
                armor(kit, "LEATHER");
            }
            case AXE -> {
                kit.put(0, unbreakable(Material.IRON_AXE));
                kit.put(1, new ItemStack(Material.GOLDEN_APPLE, 4));
                armor(kit, "CHAINMAIL");
            }
            case BEDWARS -> {
                kit.put(0, unbreakable(Material.STONE_SWORD));
                kit.put(1, new ItemStack(Material.WHITE_WOOL, 64));
                kit.put(2, new ItemStack(Material.WHITE_WOOL, 64));
                kit.put(3, unbreakable(Material.SHEARS));
                armor(kit, "LEATHER");
            }
            case SKYWARS -> {
                kit.put(0, unbreakable(Material.STONE_SWORD));
                kit.put(1, new ItemStack(Material.SANDSTONE, 64));
                kit.put(2, new ItemStack(Material.SNOWBALL, 16));
                kit.put(3, new ItemStack(Material.GOLDEN_APPLE, 2));
                armor(kit, "IRON");
            }
        }
        return kit;
    }

    /**
     * What the refill task keeps in stock: for each template item, the total
     * amount of its material the inventory should always hold. Empty for
     * kits whose whole point is having no healing.
     */
    public Map<ItemStack, Integer> refills() {
        Map<ItemStack, Integer> refills = new LinkedHashMap<>();
        switch (this) {
            case SWORD -> refills.put(new ItemStack(Material.GOLDEN_APPLE), 8);
            case NODEBUFF -> {
                refills.put(healthPotion(), 16);
                refills.put(speedPotion(), 2);
            }
            case BOXING -> {
            }
            case BUILDUHC -> {
                refills.put(new ItemStack(Material.GOLDEN_APPLE), 6);
                refills.put(new ItemStack(Material.ARROW), 16);
                refills.put(new ItemStack(Material.COBBLESTONE), 128);
            }
            case COMBO -> refills.put(new ItemStack(Material.GOLDEN_APPLE), 4);
            case GAPPLE -> refills.put(new ItemStack(Material.GOLDEN_APPLE), 64);
            case IRON, DIAMOND -> refills.put(new ItemStack(Material.GOLDEN_APPLE), 3);
            case CLASSIC -> refills.put(new ItemStack(Material.ARROW), 16);
            case SOUP -> refills.put(new ItemStack(Material.MUSHROOM_STEW), 25);
            case ARCHER -> refills.put(new ItemStack(Material.ARROW), 64);
            case AXE -> refills.put(new ItemStack(Material.GOLDEN_APPLE), 4);
            case BEDWARS -> refills.put(new ItemStack(Material.WHITE_WOOL), 128);
            case SKYWARS -> {
                refills.put(new ItemStack(Material.SANDSTONE), 64);
                refills.put(new ItemStack(Material.SNOWBALL), 16);
                refills.put(new ItemStack(Material.GOLDEN_APPLE), 2);
            }
        }
        return refills;
    }

    /** The bot's mirrored loadout: [helmet, chestplate, leggings, boots, weapon]. */
    public ItemStack[] botGear() {
        Map<Integer, ItemStack> kit = kit();
        // The weapon is whatever sits in slot 0 — sword, axe or bow-first
        // kits still hand the bot something to swing.
        ItemStack weapon = kit.get(0);
        if (weapon != null && weapon.getType() == Material.BOW) {
            weapon = kit.getOrDefault(1, weapon);
        }
        return new ItemStack[]{
                kit.get(HELMET), kit.get(CHESTPLATE), kit.get(LEGGINGS), kit.get(BOOTS), weapon};
    }

    /** The material shown for this kit in the gallery. */
    public Material icon() {
        return switch (this) {
            case SWORD -> Material.DIAMOND_SWORD;
            case NODEBUFF -> Material.SPLASH_POTION;
            case BOXING -> Material.IRON_SWORD;
            case BUILDUHC -> Material.LAVA_BUCKET;
            case COMBO -> Material.SLIME_BALL;
            case GAPPLE -> Material.GOLDEN_APPLE;
            case IRON -> Material.IRON_CHESTPLATE;
            case DIAMOND -> Material.DIAMOND_CHESTPLATE;
            case CLASSIC -> Material.LEATHER_CHESTPLATE;
            case SOUP -> Material.MUSHROOM_STEW;
            case ARCHER -> Material.BOW;
            case AXE -> Material.IRON_AXE;
            case BEDWARS -> Material.WHITE_WOOL;
            case SKYWARS -> Material.SANDSTONE;
        };
    }

    /** Whether this kit brings blocks to place. Shown in the gallery lore. */
    public boolean hasBlocks() {
        return this == BUILDUHC || this == BEDWARS || this == SKYWARS;
    }

    // ------------------------------------------------------------- building

    private static void armor(Map<Integer, ItemStack> kit, String prefix) {
        kit.put(HELMET, unbreakable(Material.valueOf(prefix + "_HELMET")));
        kit.put(CHESTPLATE, unbreakable(Material.valueOf(prefix + "_CHESTPLATE")));
        kit.put(LEGGINGS, unbreakable(Material.valueOf(prefix + "_LEGGINGS")));
        kit.put(BOOTS, unbreakable(Material.valueOf(prefix + "_BOOTS")));
    }

    private static ItemStack unbreakable(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.setUnbreakable(true));
        return stack;
    }

    private static ItemStack healthPotion() {
        ItemStack stack = new ItemStack(Material.SPLASH_POTION);
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.STRONG_HEALING));
        return stack;
    }

    private static ItemStack speedPotion() {
        ItemStack stack = new ItemStack(Material.POTION);
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.SWIFTNESS));
        return stack;
    }
}
