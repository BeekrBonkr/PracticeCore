package me.beekrbonkr.practicecore.settings;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.UUID;

/**
 * Per-player cosmetic settings: permanent night vision, kit wool color and a
 * client-side arena time of day. Persisted in playerdata prefs; applied when a
 * session starts and immediately when changed mid-session, undone on every
 * session exit (night vision through the snapshot's effect restore, the time
 * by an explicit reset).
 */
public final class SettingsService {

    /** Client-side times of day a player can pin their arena to. */
    public enum TimeOfDay {
        DEFAULT(-1), DAY(1000), NOON(6000), SUNSET(12000), NIGHT(13000), MIDNIGHT(18000);

        private final long ticks;

        TimeOfDay(long ticks) {
            this.ticks = ticks;
        }

        public long ticks() {
            return ticks;
        }

        public TimeOfDay next() {
            TimeOfDay[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        /** messages.yml key under gui.settings.time.option. */
        public String messageKey() {
            return "gui.settings.time.option." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final PracticeCorePlugin plugin;

    public SettingsService(PracticeCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- lookups

    public boolean nightVision(UUID player) {
        return plugin.stats().prefBool(player, "night-vision", false);
    }

    /** The chosen wool color, or null for the kit's own. */
    public DyeColor woolColor(UUID player) {
        String name = plugin.stats().pref(player, "wool-color", "DEFAULT");
        if (name.equalsIgnoreCase("DEFAULT")) {
            return null; // the common case — not worth an exception
        }
        try {
            return DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public TimeOfDay timeOfDay(UUID player) {
        String name = plugin.stats().pref(player, "time-of-day", "DEFAULT");
        try {
            return TimeOfDay.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TimeOfDay.DEFAULT;
        }
    }

    // ------------------------------------------------------------- changes

    public boolean toggleNightVision(Player player) {
        boolean on = !nightVision(player.getUniqueId());
        plugin.stats().setPref(player.getUniqueId(), "night-vision", on);
        if (plugin.sessions().get(player.getUniqueId()) != null) {
            if (on) {
                applyNightVision(player);
            } else {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
        return on;
    }

    /** Cycles the wool color; DEFAULT sits between the last and first color. */
    public DyeColor cycleWoolColor(Player player, boolean forward) {
        DyeColor current = woolColor(player.getUniqueId());
        DyeColor[] all = DyeColor.values();
        int size = all.length + 1; // slot 0 is DEFAULT
        int index = current == null ? 0 : current.ordinal() + 1;
        index = Math.floorMod(index + (forward ? 1 : -1), size);
        DyeColor next = index == 0 ? null : all[index - 1];
        plugin.stats().setPref(player.getUniqueId(), "wool-color",
                next == null ? "DEFAULT" : next.name());
        if (plugin.sessions().get(player.getUniqueId()) != null) {
            recolorInventory(player);
        }
        return next;
    }

    public TimeOfDay cycleTimeOfDay(Player player) {
        TimeOfDay next = timeOfDay(player.getUniqueId()).next();
        plugin.stats().setPref(player.getUniqueId(), "time-of-day", next.name());
        if (plugin.sessions().get(player.getUniqueId()) != null) {
            applyTime(player);
        }
        return next;
    }

    // ------------------------------------------------------------ applying

    /** Everything the player has chosen, applied at session start. */
    public void applyToSession(Player player) {
        if (nightVision(player.getUniqueId())) {
            applyNightVision(player);
        }
        applyTime(player);
    }

    /** Undoes the client-side time; effects are restored by the snapshot. */
    public void clearOnExit(Player player) {
        player.resetPlayerTime();
    }

    private void applyNightVision(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    private void applyTime(Player player) {
        TimeOfDay time = timeOfDay(player.getUniqueId());
        if (time == TimeOfDay.DEFAULT) {
            player.resetPlayerTime();
        } else {
            player.setPlayerTime(time.ticks(), false);
        }
    }

    // ----------------------------------------------------------------- wool

    /**
     * The kit item recolored to the player's chosen wool color. Non-wool
     * items and players without a choice pass through untouched.
     */
    public ItemStack recolor(UUID player, ItemStack item) {
        DyeColor color = woolColor(player);
        if (color == null || item == null) {
            return item;
        }
        Material recolored = woolOf(color);
        if (recolored == null || !item.getType().name().endsWith("_WOOL")
                || item.getType() == recolored) {
            return item;
        }
        return item.withType(recolored);
    }

    /** Swaps every wool stack the player is holding to their chosen color. */
    public void recolorInventory(Player player) {
        DyeColor color = woolColor(player.getUniqueId());
        Material target = color == null ? kitWoolFallback(player) : woolOf(color);
        if (target == null) {
            // Back to DEFAULT with a multi-color (or wool-less) kit: there is
            // no single right color to restore in place — the next kit give
            // hands out the kit's own colors again.
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType().name().endsWith("_WOOL")
                    && item.getType() != target) {
                contents[slot] = item.withType(target);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setContents(contents);
        }
    }

    /**
     * Back to DEFAULT mid-session: the kit's own wool — but only when the kit
     * uses a single color, since an in-place swap can't know which stack was
     * which in a multi-color kit.
     */
    private Material kitWoolFallback(Player player) {
        var session = plugin.sessions().get(player.getUniqueId());
        if (session == null) {
            return null;
        }
        var woolTypes = session.template().kit().values().stream()
                .map(ItemStack::getType)
                .filter(type -> type.name().endsWith("_WOOL"))
                .collect(java.util.stream.Collectors.toSet());
        return woolTypes.size() == 1 ? woolTypes.iterator().next() : null;
    }

    public static Material woolOf(DyeColor color) {
        return Material.matchMaterial(color.name() + "_WOOL");
    }
}
