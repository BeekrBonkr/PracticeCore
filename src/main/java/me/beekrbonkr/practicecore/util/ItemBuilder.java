package me.beekrbonkr.practicecore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Small fluent ItemStack builder for menu icons. */
public final class ItemBuilder {

    private final ItemStack stack;
    private final List<Component> lore = new ArrayList<>();

    private ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material, Math.clamp(amount, 1, 64));
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    public ItemBuilder name(Component name) {
        return edit(meta -> meta.displayName(name.decoration(TextDecoration.ITALIC, false)));
    }

    public ItemBuilder lore(Component line) {
        lore.add(line.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        lines.forEach(this::lore);
        return this;
    }

    /** Enchant shimmer without the enchantment text — used to mark selections. */
    public ItemBuilder glow(boolean glow) {
        if (!glow) {
            return this;
        }
        return edit(meta -> {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
    }

    public ItemBuilder hideAttributes() {
        return edit(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
    }

    public ItemBuilder edit(Consumer<ItemMeta> consumer) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            stack.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) {
            edit(meta -> meta.lore(lore));
        }
        return stack;
    }
}
