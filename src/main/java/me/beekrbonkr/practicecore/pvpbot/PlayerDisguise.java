package me.beekrbonkr.practicecore.pvpbot;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import me.beekrbonkr.practicecore.PracticeCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side player-model disguise for the PvP bot, the only class that
 * touches ProtocolLib. The husk stays exactly what it is on the server — AI,
 * hitbox, damage all unchanged — but its spawn packet is rewritten into a
 * player entity backed by a fake tab profile (unlisted), so clients render a
 * real player model wearing the practicing player's own skin. Movement,
 * swing, hurt and equipment packets address the same entity id and apply to
 * the player model untouched.
 *
 * Husk-specific metadata indexes (15+) are stripped from outgoing metadata —
 * a player model fed zombie fields would desync the client — and the skin
 * layer byte is injected so hats and jacket render.
 *
 * Fragility note: packet shapes shift between Minecraft versions. Every
 * entry point is wrapped; one failure logs once and turns the disguise off,
 * leaving the plain husk (scaled to player height) as the fallback.
 */
public final class PlayerDisguise {

    /** Player metadata index for displayed skin parts (1.21). */
    private static final int SKIN_PARTS_INDEX = 17;
    /** Metadata indexes above this are entity-type-specific — never shared. */
    private static final int LAST_SHARED_INDEX = 14;

    private final PracticeCorePlugin plugin;
    private final ProtocolManager manager;
    /** Disguised bot entity ids → the fake profile shown for them. */
    private final Map<Integer, WrappedGameProfile> disguised = new ConcurrentHashMap<>();
    private boolean broken;

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
    }

    public PlayerDisguise(PracticeCorePlugin plugin) {
        this.plugin = plugin;
        this.manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SPAWN_ENTITY, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (broken) {
                    return;
                }
                try {
                    if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                        rewriteSpawn(event.getPacket());
                    } else {
                        rewriteMetadata(event.getPacket());
                    }
                } catch (Throwable t) {
                    fail(t);
                }
            }
        });
    }

    /**
     * Disguises a freshly spawned bot. Must run in the spawn tick, before the
     * entity tracker sends the spawn packet: the profile has to reach clients
     * first, and the id has to be mapped before the packet is rewritten.
     */
    public void apply(Entity bot, Player owner) {
        if (broken) {
            return;
        }
        try {
            WrappedGameProfile profile =
                    new WrappedGameProfile(UUID.randomUUID(), "PvPBot");
            // The bot wears the practicing player's own skin — a mirror match.
            WrappedGameProfile ownerProfile = WrappedGameProfile.fromPlayer(owner);
            profile.getProperties().putAll("textures",
                    ownerProfile.getProperties().get("textures"));
            disguised.put(bot.getEntityId(), profile);
            broadcast(infoPacket(profile));
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Removes the disguise bookkeeping and the fake tab profile. */
    public void remove(Entity bot) {
        WrappedGameProfile profile = disguised.remove(bot.getEntityId());
        if (profile == null || broken) {
            return;
        }
        try {
            PacketContainer packet = manager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            packet.getUUIDLists().write(0, List.of(profile.getUUID()));
            broadcast(packet);
        } catch (Throwable t) {
            fail(t);
        }
    }

    // -------------------------------------------------------------- packets

    private void rewriteSpawn(PacketContainer packet) {
        WrappedGameProfile profile = disguised.get(packet.getIntegers().read(0));
        if (profile == null) {
            return;
        }
        packet.getEntityTypeModifier().write(0, EntityType.PLAYER);
        packet.getUUIDs().write(0, profile.getUUID());
    }

    private void rewriteMetadata(PacketContainer packet) {
        if (!disguised.containsKey(packet.getIntegers().read(0))) {
            return;
        }
        List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
        if (values == null) {
            return;
        }
        List<WrappedDataValue> safe = new ArrayList<>();
        for (WrappedDataValue value : values) {
            if (value.getIndex() <= LAST_SHARED_INDEX) {
                safe.add(value);
            }
        }
        // All skin layers on, or the model renders without hat and jacket.
        safe.add(new WrappedDataValue(SKIN_PARTS_INDEX,
                WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x7F));
        packet.getDataValueCollectionModifier().write(0, safe);
    }

    private PacketContainer infoPacket(WrappedGameProfile profile) {
        PacketContainer packet = manager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().write(0, EnumSet.of(
                EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
        packet.getPlayerInfoDataLists().write(1, List.of(new PlayerInfoData(
                profile.getUUID(), 0, false,
                EnumWrappers.NativeGameMode.SURVIVAL, profile, null)));
        return packet;
    }

    /**
     * Profiles go to everyone online (unlisted, so tab stays clean) — anyone
     * near the arena must be able to render the model, and a viewer without
     * the profile would see nothing at all.
     */
    private void broadcast(PacketContainer packet) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            manager.sendServerPacket(online, packet);
        }
    }

    private void fail(Throwable t) {
        if (!broken) {
            broken = true;
            plugin.getLogger().severe("PvP bot player disguise failed — incompatible "
                    + "ProtocolLib/Minecraft version? Falling back to the plain husk. " + t);
        }
    }

    /** Whether disguises are actually being applied right now. */
    public boolean active() {
        return !broken;
    }
}
