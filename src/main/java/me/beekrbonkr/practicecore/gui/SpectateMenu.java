package me.beekrbonkr.practicecore.gui;

import me.beekrbonkr.practicecore.PracticeCorePlugin;
import me.beekrbonkr.practicecore.session.PracticeSession;
import me.beekrbonkr.practicecore.session.SessionState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Everyone currently practicing, one head per player — click to spectate.
 * Reached from the main menu's Spectate button, /practice spectate, and the
 * spyglass in a spectator's hotbar (to switch targets).
 */
public final class SpectateMenu extends PagedMenu<PracticeSession> {

    public SpectateMenu(PracticeCorePlugin plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return text("gui.spectate.title");
    }

    @Override
    protected List<PracticeSession> entries() {
        List<PracticeSession> out = new ArrayList<>();
        for (PracticeSession session : plugin.sessions().all()) {
            SessionState state = session.state();
            if (state != SessionState.READY && state != SessionState.ACTIVE
                    && state != SessionState.RESETTING) {
                continue;
            }
            if (session.playerId().equals(viewer.getUniqueId())) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            out.add(session);
        }
        out.sort(Comparator.comparing(session -> {
            Player player = Bukkit.getPlayer(session.playerId());
            return player != null ? player.getName() : "";
        }, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Override
    protected ItemStack icon(PracticeSession session) {
        Player target = Bukkit.getPlayer(session.playerId());
        String name = target != null ? target.getName() : "?";
        int watchers = plugin.spectate().watcherCount(session.playerId());
        ItemStack head = Button.of(plugin, Material.PLAYER_HEAD)
                .name("gui.spectate.entry.name", "player", name)
                .lore("gui.spectate.entry.lore",
                        "arena", session.template().displayName(),
                        "mode", session.mode().displayName(),
                        "watchers", String.valueOf(watchers))
                .hint("view")
                .build();
        if (target != null && head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
            head.setItemMeta(skull);
        }
        return head;
    }

    @Override
    protected void onEntryClick(PracticeSession session, InventoryClickEvent event) {
        Player target = Bukkit.getPlayer(session.playerId());
        if (target == null || !target.isOnline()) {
            // The tile went stale between render and click (R55).
            deny();
            refresh();
            return;
        }
        sound("menu.select");
        later(() -> {
            viewer.closeInventory();
            plugin.spectate().start(viewer, target);
        });
    }

    @Override
    protected ItemStack emptyIcon() {
        return emptyIcon("gui.spectate.empty");
    }
}
