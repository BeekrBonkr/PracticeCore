package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.message.Messages;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The one way a menu control is assembled (UI style guide, sections 4, 5
 * and 7). Every button and tile goes through here so the lore skeleton,
 * the click-hint vocabulary and the disabled/locked treatment are the same
 * in every menu instead of being re-typed per class.
 *
 * <p>Lore is built in groups separated by one blank line:
 * <pre>
 *   description and state   ← the menu's own lore key, verbatim
 *   (blank)
 *   Click to …               ← hints from gui.hint.*, or
 *   Unavailable: reason      ← when disabled, or
 *   Locked: reason           ← when locked
 * </pre>
 *
 * <p>A disabled control swaps to a light-gray pane and a locked one to iron
 * bars; both keep their label, in gray, so the player still knows what the
 * slot is. Glow means exactly one thing — selected, or on.
 */
public final class Button {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Messages msg;
    private final ItemBuilder item;
    private Component name = Component.empty();
    private final List<Component> body = new ArrayList<>();
    private final List<Component> hints = new ArrayList<>();
    private Component reason;
    private Material reasonMaterial;
    private boolean glow;

    private Button(PracticeCorePlugin plugin, Material material, int amount) {
        this.msg = plugin.messages();
        this.item = ItemBuilder.of(material, amount);
    }

    public static Button of(PracticeCorePlugin plugin, Material material) {
        return new Button(plugin, material, 1);
    }

    /** Amount is only ever a page number or a literal quantity (R36). */
    public static Button of(PracticeCorePlugin plugin, Material material, int amount) {
        return new Button(plugin, material, amount);
    }

    // ----------------------------------------------------------------- text

    public Button name(String key, String... placeholders) {
        this.name = msg.name(key, placeholders);
        return this;
    }

    public Button name(String key, TagResolver extra, String... placeholders) {
        this.name = msg.name(key, extra, placeholders);
        return this;
    }

    public Button name(Component name) {
        this.name = name;
        return this;
    }

    /** Description and state lines, exactly as configured under the key. */
    public Button lore(String key, String... placeholders) {
        body.addAll(msg.lore(key, placeholders));
        return this;
    }

    public Button lore(String key, TagResolver extra, String... placeholders) {
        body.addAll(msg.lore(key, extra, placeholders));
        return this;
    }

    public Button lore(List<Component> lines) {
        body.addAll(lines);
        return this;
    }

    public Button line(Component line) {
        body.add(line);
        return this;
    }

    // ---------------------------------------------------------------- hints

    /** {@code Click to <verb>} — one of the closed set under gui.hint.click (R32). */
    public Button hint(String verb) {
        return hintKey("gui.hint.click." + verb);
    }

    /** {@code Right-click to <verb>} — the secondary action, always after the primary. */
    public Button rightHint(String verb) {
        return hintKey("gui.hint.right." + verb);
    }

    /** {@code Shift-click to <verb>} — tertiary, de-emphasized. */
    public Button shiftHint(String verb) {
        return hintKey("gui.hint.shift." + verb);
    }

    private Button hintKey(String key) {
        if (!msg.silenced(key)) {
            hints.add(msg.name(key));
        }
        return this;
    }

    // ---------------------------------------------------------------- state

    /** Selected, or on. The only thing glow ever means (R37). */
    public Button glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    /**
     * Exists but cannot act right now (R51). The reason is a short lowercase
     * phrase from gui.reason.*, e.g. {@code only-one-team}.
     */
    public Button disabled(String reasonKey, String... placeholders) {
        return unavailable("gui.unavailable", Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                reasonKey, placeholders);
    }

    /** The viewer lacks permission (R52). */
    public Button locked(String reasonKey, String... placeholders) {
        return unavailable("gui.locked", Material.IRON_BARS, reasonKey, placeholders);
    }

    /**
     * Disabled, but the icon stays — for a product the player cannot afford,
     * where the item itself is the information (R54).
     */
    public Button disabledKeepIcon(String reasonKey, String... placeholders) {
        return unavailable("gui.unavailable", null, reasonKey, placeholders);
    }

    private Button unavailable(String lineKey, Material swap, String reasonKey,
                               String... placeholders) {
        this.reason = msg.name(lineKey, msg.ref("reason", reasonKey, placeholders));
        this.reasonMaterial = swap;
        this.glow = false;
        return this;
    }

    public Button hideAttributes() {
        item.hideAttributes();
        return this;
    }

    // ---------------------------------------------------------------- build

    public ItemStack build() {
        ItemBuilder target = item;
        if (reason != null && reasonMaterial != null) {
            target = ItemBuilder.of(reasonMaterial);
        }
        target.name(reason != null ? grayed(name) : name);
        List<Component> lore = new ArrayList<>(body);
        List<Component> tail = reason != null ? List.of(reason) : hints;
        if (!tail.isEmpty()) {
            if (!lore.isEmpty() && !isBlank(lore.get(lore.size() - 1))) {
                lore.add(Component.empty());
            }
            lore.addAll(tail);
        }
        target.lore(lore);
        target.glow(glow);
        return target.build();
    }

    /** The label, its own colors dropped, in gray bold — still readable, clearly off. */
    private static Component grayed(Component name) {
        return Component.text(PLAIN.serialize(name), NamedTextColor.GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static boolean isBlank(Component line) {
        return PLAIN.serialize(line).isBlank();
    }
}
