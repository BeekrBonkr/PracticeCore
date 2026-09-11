package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One-shot text prompts, asked through an anvil: its name box is the text
 * field. The question is the anvil's title, the paper on the left carries
 * the current value with the hint in its lore, and clicking the result hands
 * the typed text to a callback on the main thread. Closing the anvil cancels.
 * Nothing goes through chat, so nothing can leak into it.
 *
 * The admin overload wears the setup GUI's fixed English styling (R25); the
 * player-facing one takes a title and hint already rendered from messages.yml.
 */
public final class AnvilPrompts implements Listener {

    /** The most a vanilla anvil lets anyone type. */
    public static final int MAX_LENGTH = 50;

    /** Raw slot of the anvil's result. */
    private static final int RESULT_SLOT = 2;

    private final PracticeCorePlugin plugin;
    private final NamespacedKey marker;
    private final Map<UUID, Pending> pending = new HashMap<>();

    private record Pending(AnvilView view, ItemStack paper, Consumer<String> callback) {
    }

    public AnvilPrompts(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "prompt");
    }

    /**
     * An admin prompt with fixed English text: a short title (the anvil has
     * room for about twenty characters), the value already in the box, and
     * gray hint lines under it.
     */
    public void prompt(Player player, String title, String initial, List<String> hint,
                       Consumer<String> callback) {
        List<Component> lines = new ArrayList<>();
        for (String line : hint) {
            lines.add(line.isEmpty() ? Component.empty() : Component.text(line, NamedTextColor.GRAY));
        }
        prompt(player, Component.text(title, NamedTextColor.WHITE),
                initial, lines, callback);
    }

    /** A player-facing prompt whose title and hint come from messages.yml. */
    public void prompt(Player player, Component title, String initial, List<Component> hint,
                       Consumer<String> callback) {
        cancel(player.getUniqueId());
        String text = initial == null ? "" : initial;
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        ItemBuilder paper = ItemBuilder.of(Material.PAPER)
                .name(Component.text(text, NamedTextColor.WHITE))
                .lore(hint)
                .edit(meta -> meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1));
        if (!hint.isEmpty()) {
            paper.lore(Component.empty());
        }
        ItemStack item = paper.lore(plugin.messages().lore("gui.prompt.cancel")).build();

        AnvilView view = MenuType.ANVIL.create(player, title);
        pending.put(player.getUniqueId(), new Pending(view, item, callback));
        view.getTopInventory().setFirstItem(item);
        player.openInventory(view);
        plugin.sounds().play(player, "menu.open");
    }

    /** Whether this player has a prompt open right now. */
    public boolean isPrompting(UUID id) {
        return pending.containsKey(id);
    }

    /** Drops a pending prompt without answering it. */
    private void cancel(UUID id) {
        Pending dropped = pending.remove(id);
        if (dropped != null) {
            dropped.view().getTopInventory().clear();
        }
    }

    /** Closes every open prompt — nothing is handed back and nothing runs. */
    public void closeAll() {
        for (UUID id : List.copyOf(pending.keySet())) {
            Pending dropped = pending.remove(id);
            dropped.view().getTopInventory().clear();
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.getOpenInventory().getTopInventory()
                    .equals(dropped.view().getTopInventory())) {
                player.closeInventory();
            }
        }
    }

    /** Whether this inventory is one of our prompts: its paper wears the marker. */
    private boolean ours(Inventory inventory) {
        if (!(inventory instanceof AnvilInventory anvil)) {
            return false;
        }
        ItemStack first = anvil.getFirstItem();
        return first != null && first.hasItemMeta()
                && first.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    private Pending pendingFor(Player player, Inventory top) {
        Pending entry = pending.get(player.getUniqueId());
        return entry != null && ours(top) ? entry : null;
    }

    // Every keystroke reaches the server as a rename, and each one rebuilds
    // the result: the paper again, named with what is in the box, at no
    // level cost. Vanilla would leave the slot empty while the name is
    // unchanged or blank, which reads as "nothing to take".
    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        Pending entry = pendingFor(player, event.getInventory());
        if (entry == null) {
            return;
        }
        String text = typed(event.getView(), entry);
        ItemStack result = ItemBuilder.of(Material.PAPER)
                .name(Component.text(text, NamedTextColor.WHITE))
                .lore(plugin.messages().lore("gui.hint.click.confirm"))
                .build();
        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    /** What is in the box right now; before the first keystroke, the paper's own name. */
    private static String typed(AnvilView view, Pending entry) {
        String text = view.getRenameText();
        if (text == null) {
            Component name = entry.paper().getItemMeta().displayName();
            text = name == null ? "" : net.kyori.adventure.text.serializer.plain
                    .PlainTextComponentSerializer.plainText().serialize(name);
        }
        return text.trim();
    }

    // Vanilla refuses to hand over a free result, so the click is never a
    // real pickup — it is the confirm gesture, and the answer is read from
    // the box rather than the item. Every other click is swallowed: nothing
    // may move into the anvil (a shift-click would replace the paper) or
    // out of it.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Pending entry = pendingFor(player, event.getView().getTopInventory());
        if (entry == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != RESULT_SLOT) {
            return;
        }
        String answer = typed(entry.view(), entry);
        if (answer.isEmpty()) {
            plugin.sounds().play(player, "menu.deny");
            return;
        }
        pending.remove(player.getUniqueId());
        plugin.sounds().play(player, "menu.select");
        // Bukkit is still unwinding the click; the close and whatever the
        // callback opens have to wait a tick. The paper goes first so the
        // close hands nothing back.
        Bukkit.getScheduler().runTask(plugin, () -> {
            entry.view().getTopInventory().clear();
            player.closeInventory();
            if (player.isOnline()) {
                entry.callback().accept(answer);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (pendingFor(player, event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    /** Closing the anvil is the cancel gesture. The paper is ours; it is not handed back. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Pending entry = pendingFor(player, event.getInventory());
        if (entry == null) {
            return;
        }
        pending.remove(player.getUniqueId());
        event.getInventory().clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }
}
