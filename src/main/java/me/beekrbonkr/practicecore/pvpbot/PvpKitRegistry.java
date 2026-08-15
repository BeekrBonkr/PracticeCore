package me.beekrbonkr.practicecore.pvpbot;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The PvP kits defined in pvpbot.yml, in file order — which is the order the
 * gallery lists them in.
 *
 * The item syntax is deliberately terse, because a kit is mostly a list of
 * "this material, this many, in this slot":
 *
 * <pre>
 * kits:
 *   nodebuff:
 *     name: 'NoDebuff'
 *     icon: SPLASH_POTION
 *     armor: DIAMOND               # fills slots 36-39, unbreakable
 *     items:
 *       0: 'DIAMOND_SWORD'
 *       1-7: 'SPLASH_POTION 1 STRONG_HEALING'
 *       27: 'POTION 1 SWIFTNESS'
 *     refill:
 *       SPLASH_POTION: 16
 * </pre>
 *
 * A slot key may be a single number or an inclusive {@code from-to} range. An
 * item is {@code MATERIAL [amount] [POTION_TYPE]}, or a map when something
 * finer is wanted ({@code {material: …, amount: …, unbreakable: false}}).
 */
public final class PvpKitRegistry {

    private final PracticeCorePlugin plugin;
    private final Map<String, PvpKit> kits = new LinkedHashMap<>();

    public PvpKitRegistry(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** @return notes worth showing an admin (kits that could not be read) */
    List<String> load(ConfigFile file) {
        List<String> notes = new ArrayList<>();
        Map<String, PvpKit> loaded = new LinkedHashMap<>();
        ConfigurationSection section = file.section("kits");
        if (section == null) {
            notes.add("pvpbot.yml has no kits: section — the PvP bot mode has nothing to hand out.");
            kits.clear();
            return notes;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                notes.add("pvpbot.yml: kit '" + id + "' is not a block of settings — skipped.");
                continue;
            }
            try {
                PvpKit kit = read(id.toLowerCase(Locale.ROOT), entry);
                loaded.put(kit.id(), kit);
            } catch (RuntimeException e) {
                notes.add("pvpbot.yml: kit '" + id + "' could not be read (" + e.getMessage()
                        + ") — skipped.");
            }
        }
        if (loaded.isEmpty()) {
            notes.add("pvpbot.yml defines no usable kits — the PvP bot mode has nothing to hand out.");
        }
        // Swapped in whole: a half-loaded gallery mid-reload would hand some
        // players a kit and others nothing.
        kits.clear();
        kits.putAll(loaded);
        return notes;
    }

    private PvpKit read(String id, ConfigurationSection entry) {
        Map<Integer, ItemStack> contents = new LinkedHashMap<>();
        boolean unbreakable = entry.getBoolean("unbreakable", true);

        String armor = entry.getString("armor", "");
        if (!armor.isBlank() && !armor.equalsIgnoreCase("none")) {
            String prefix = armor.trim().toUpperCase(Locale.ROOT);
            put(contents, PvpKit.HELMET, piece(prefix + "_HELMET", unbreakable));
            put(contents, PvpKit.CHESTPLATE, piece(prefix + "_CHESTPLATE", unbreakable));
            put(contents, PvpKit.LEGGINGS, piece(prefix + "_LEGGINGS", unbreakable));
            put(contents, PvpKit.BOOTS, piece(prefix + "_BOOTS", unbreakable));
        }

        ConfigurationSection items = entry.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                int[] range = slots(key);
                ItemStack stack = item(items, key, unbreakable);
                if (stack == null) {
                    continue;
                }
                for (int slot = range[0]; slot <= range[1]; slot++) {
                    contents.put(slot, stack.clone());
                }
            }
        }
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("it has no items and no armor");
        }

        Map<Material, Integer> refills = new LinkedHashMap<>();
        ConfigurationSection refill = entry.getConfigurationSection("refill");
        if (refill != null) {
            for (String key : refill.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("pvpbot.yml: kit '" + id + "' refill entry '"
                            + key + "' is not an item — ignored.");
                    continue;
                }
                refills.put(material, Math.max(0, refill.getInt(key)));
            }
        }

        Material icon = Material.matchMaterial(entry.getString("icon", ""));
        if (icon == null || !icon.isItem()) {
            // A kit is still perfectly playable without a good icon; show the
            // weapon it hands out rather than refusing to load it.
            ItemStack first = contents.get(0);
            icon = first != null ? first.getType() : Material.IRON_SWORD;
        }
        boolean hasBlocks = entry.isSet("blocks")
                ? entry.getBoolean("blocks")
                : contents.values().stream().anyMatch(stack -> stack.getType().isBlock());
        Material botWeapon = Material.matchMaterial(entry.getString("bot-weapon", ""));

        return new PvpKit(id, entry.getString("name", ""), icon, hasBlocks,
                Map.copyOf(contents), Map.copyOf(refills),
                botWeapon != null && botWeapon.isItem() ? botWeapon : null);
    }

    private static void put(Map<Integer, ItemStack> contents, int slot, ItemStack stack) {
        if (stack != null) {
            contents.put(slot, stack);
        }
    }

    /** Highest slot a kit may fill: 0-35 inventory, 36-39 armor, 40 off-hand. */
    private static final int MAX_SLOT = 40;

    /** {@code 12} or {@code 18-26}; anything else is a config typo worth saying so. */
    private static int[] slots(String key) {
        int from;
        int to;
        try {
            int dash = key.indexOf('-', 1);
            if (dash < 0) {
                from = Integer.parseInt(key.trim());
                to = from;
            } else {
                from = Integer.parseInt(key.substring(0, dash).trim());
                to = Integer.parseInt(key.substring(dash + 1).trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' is not a slot or slot range");
        }
        if (to < from) {
            throw new IllegalArgumentException("slot range '" + key + "' counts backwards");
        }
        // Out-of-range slots would land items nowhere a player can reach (or
        // throw on the give), so they are refused rather than clamped into
        // some other slot the admin did not ask for.
        if (from < 0 || to > MAX_SLOT) {
            throw new IllegalArgumentException("slot '" + key + "' is outside 0-" + MAX_SLOT);
        }
        return new int[]{from, to};
    }

    /**
     * {@code MATERIAL [amount] [POTION_TYPE]} in short form, or a map with
     * {@code material} / {@code amount} / {@code potion} / {@code unbreakable}.
     */
    private ItemStack item(ConfigurationSection items, String key, boolean unbreakableDefault) {
        ConfigurationSection map = items.getConfigurationSection(key);
        String material;
        int amount;
        String potion;
        boolean unbreakable;
        if (map != null) {
            material = map.getString("material", "");
            amount = map.getInt("amount", 1);
            potion = map.getString("potion", "");
            unbreakable = map.getBoolean("unbreakable", unbreakableDefault);
        } else {
            String[] parts = items.getString(key, "").trim().split("\\s+");
            if (parts.length == 0 || parts[0].isBlank()) {
                return null;
            }
            material = parts[0];
            amount = parts.length > 1 ? parseAmount(parts[1]) : 1;
            potion = parts.length > 2 ? parts[2] : "";
            unbreakable = unbreakableDefault;
        }
        Material type = Material.matchMaterial(material);
        if (type == null || !type.isItem()) {
            throw new IllegalArgumentException("'" + material + "' at slot " + key + " is not an item");
        }
        ItemStack stack = new ItemStack(type, Math.max(1, amount));
        if (!potion.isBlank()) {
            PotionType potionType;
            try {
                potionType = PotionType.valueOf(potion.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + potion + "' is not a potion type");
            }
            stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(potionType));
        }
        // Gear only: stamping unbreakable onto a stack of wool is harmless but
        // noisy in the tooltip, and an endless spar must never grind a sword away.
        if (unbreakable && type.getMaxDurability() > 0) {
            stack.editMeta(meta -> meta.setUnbreakable(true));
        }
        return stack;
    }

    private static int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not an amount");
        }
    }

    private ItemStack piece(String name, boolean unbreakable) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("there is no " + name);
        }
        ItemStack stack = new ItemStack(material);
        if (unbreakable) {
            stack.editMeta(meta -> meta.setUnbreakable(true));
        }
        return stack;
    }

    // --------------------------------------------------------------- lookups

    /** The kit with this id, or null. */
    public PvpKit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    /** The first kit in the file — the fallback for a player with no choice. */
    public PvpKit first() {
        return kits.isEmpty() ? null : kits.values().iterator().next();
    }

    public List<PvpKit> all() {
        return List.copyOf(kits.values());
    }

    public boolean isEmpty() {
        return kits.isEmpty();
    }

    /**
     * A kit's display name. messages.yml wins so the bundled kits stay
     * translatable; a kit an admin invented has no key there and falls back to
     * its own {@code name}, then to a tidied version of its id.
     */
    public String displayName(PvpKit kit) {
        if (kit == null) {
            return "";
        }
        String translated = plugin.messages().raw(kit.messageKey());
        if (!translated.isEmpty()) {
            return translated;
        }
        return kit.configuredName().isEmpty() ? kit.prettyId() : kit.configuredName();
    }
}
