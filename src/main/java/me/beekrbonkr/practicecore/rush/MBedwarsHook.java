package me.beekrbonkr.practicecore.rush;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.Team;
import de.marcely.bedwars.api.game.shop.ShopItem;
import de.marcely.bedwars.api.game.shop.ShopPage;
import de.marcely.bedwars.api.game.shop.price.ItemShopPrice;
import de.marcely.bedwars.api.game.shop.price.ShopPrice;
import de.marcely.bedwars.api.game.shop.price.SpawnerItemShopPrice;
import de.marcely.bedwars.api.game.shop.product.ItemShopProduct;
import de.marcely.bedwars.api.game.shop.product.ShopProduct;
import de.marcely.bedwars.api.game.shop.product.SpawnerItemShopProduct;
import de.marcely.bedwars.api.game.shop.product.SpecialItemShopProduct;
import de.marcely.bedwars.api.game.specialitem.SpecialItem;
import de.marcely.bedwars.api.game.spawner.DropType;
import de.marcely.bedwars.api.game.spawner.Spawner;
import de.marcely.bedwars.api.world.WorldStorage;
import de.marcely.bedwars.api.world.hologram.HologramControllerType;
import de.marcely.bedwars.api.world.hologram.HologramEntity;
import de.marcely.bedwars.tools.location.XYZ;
import de.marcely.bedwars.tools.location.XYZD;
import de.marcely.bedwars.tools.location.XYZYP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The only class that touches the MBedwars API. Everything it hands out is
 * plain Bukkit data, so the rest of the plugin loads and runs fine on servers
 * without MBedwars — every entry point checks {@link #available()} first, and
 * callers must too before this class is ever loaded.
 */
public final class MBedwarsHook {

    /**
     * What one MBedwars arena looks like to the importer: Bukkit types only.
     * {@code icon} is the arena's own selector icon, or null when it has none.
     */
    public record ImportedArena(String name, String displayName, World world,
                                BoundingBox region, String status,
                                List<ImportedTeam> teams, List<ImportedSpawner> spawners,
                                List<Location> dealers, Material icon) {
    }

    /** {@code bedBlock} is wherever MBedwars says the bed is; may not be a bed block. */
    public record ImportedTeam(String name, Location spawn, Location bedBlock) {
    }

    public record ImportedSpawner(String type, Location location) {
    }

    private MBedwarsHook() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("MBedwars");
    }

    // --------------------------------------------------------------- arenas

    public static List<String> arenaNames() {
        List<String> names = new ArrayList<>();
        for (Arena arena : BedwarsAPI.getGameAPI().getArenas()) {
            names.add(arena.getName());
        }
        return names;
    }

    /** One line of arena metadata, enough to filter a mass import by shape. */
    public record ArenaSummary(String name, int teams, int playersPerTeam) {
    }

    /** Every MBedwars arena with its team count and team size. */
    public static List<ArenaSummary> arenaSummaries() {
        List<ArenaSummary> summaries = new ArrayList<>();
        for (Arena arena : BedwarsAPI.getGameAPI().getArenas()) {
            summaries.add(new ArenaSummary(arena.getName(),
                    arena.getEnabledTeams().size(), arena.getPlayersPerTeam()));
        }
        return summaries;
    }

    /**
     * Reads everything the importer needs about one MBedwars arena, or throws
     * {@link IllegalStateException} with an admin-readable reason.
     */
    public static ImportedArena read(String arenaName) {
        GameAPI api = BedwarsAPI.getGameAPI();
        Arena arena = api.getArenaByExactName(arenaName);
        if (arena == null) {
            arena = api.getArenaByName(arenaName);
        }
        if (arena == null) {
            throw new IllegalStateException("MBedwars has no arena named '" + arenaName + "'.");
        }
        World world = arena.getGameWorld();
        if (world == null) {
            throw new IllegalStateException("The world of MBedwars arena '" + arena.getName()
                    + "' (" + arena.getGameWorldName() + ") is not loaded.");
        }
        XYZ min = arena.getMinRegionCorner();
        XYZ max = arena.getMaxRegionCorner();
        if (min == null || max == null) {
            throw new IllegalStateException("MBedwars arena '" + arena.getName()
                    + "' has no region corners set — set them in MBedwars first.");
        }
        BoundingBox region = new BoundingBox(
                Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()),
                Math.max(min.getX(), max.getX()) + 1, Math.max(min.getY(), max.getY()) + 1,
                Math.max(min.getZ(), max.getZ()) + 1);

        List<ImportedTeam> teams = new ArrayList<>();
        for (Team team : arena.getEnabledTeams()) {
            XYZYP spawn = arena.getTeamSpawn(team);
            XYZD bed = arena.getBedLocation(team);
            if (spawn == null || bed == null) {
                continue;
            }
            teams.add(new ImportedTeam(team.name(),
                    spawn.toLocation(world), bed.toLocation(world)));
        }

        List<ImportedSpawner> spawners = new ArrayList<>();
        for (Spawner spawner : arena.getSpawners()) {
            String type = normalizeDropType(spawner.getDropType());
            if (type != null) {
                spawners.add(new ImportedSpawner(type, spawner.getLocation().toLocation(world)));
            }
        }

        List<Location> dealers = new ArrayList<>();
        WorldStorage storage = BedwarsAPI.getWorldStorage(world);
        if (storage != null) {
            for (HologramEntity hologram : storage.getHolograms(HologramControllerType.DEALER)) {
                Location loc = hologram.getLocation();
                if (region.contains(loc.getX(), loc.getY(), loc.getZ())) {
                    dealers.add(loc.clone());
                }
            }
        }

        return new ImportedArena(arena.getName(), arena.getDisplayName(), world, region,
                arena.getStatus().name(), teams, spawners, dealers,
                iconMaterial(arena.getIcon()));
    }

    /**
     * The selector icon MBedwars shows for an arena, as a plain material, or
     * null when the arena is unknown or has no usable icon. This is what the
     * practice menus mirror so an imported map keeps its familiar face.
     */
    public static Material arenaIconMaterial(String arenaName) {
        GameAPI api = BedwarsAPI.getGameAPI();
        Arena arena = api.getArenaByExactName(arenaName);
        if (arena == null) {
            arena = api.getArenaByName(arenaName);
        }
        return arena == null ? null : iconMaterial(arena.getIcon());
    }

    /** An icon stack reduced to a menu-safe material, or null. */
    private static Material iconMaterial(ItemStack icon) {
        return icon == null || icon.getType().isAir() || !icon.getType().isItem()
                ? null : icon.getType();
    }

    /**
     * Our four generator ids from an MBedwars drop type — by what it actually
     * drops first, falling back to its id. Null for modded types we can't use.
     */
    private static String normalizeDropType(DropType type) {
        for (ItemStack drop : type.getDroppingMaterials()) {
            switch (drop.getType()) {
                case IRON_INGOT: return "iron";
                case GOLD_INGOT: return "gold";
                case DIAMOND: return "diamond";
                case EMERALD: return "emerald";
                default: break;
            }
        }
        String id = type.getId().toLowerCase(Locale.ROOT);
        for (String known : List.of("iron", "gold", "diamond", "emerald")) {
            if (id.contains(known)) {
                return known;
            }
        }
        return null;
    }

    // ----------------------------------------------------------------- shop

    /**
     * A snapshot of the MBedwars item shop as plain items and prices. Entries
     * whose price or products can't be resolved outside a running game are
     * skipped rather than sold broken.
     */
    public static RushShopData shopSnapshot() {
        List<RushShopData.Page> pages = new ArrayList<>();
        for (ShopPage page : BedwarsAPI.getGameAPI().getShopPages()) {
            List<RushShopData.Entry> entries = new ArrayList<>();
            for (ShopItem item : page.getItems()) {
                RushShopData.Entry entry;
                try {
                    entry = entryOf(item);
                } catch (RuntimeException e) {
                    // One unreadable entry (an addon product with surprises in
                    // it) must cost that item, never the whole shop.
                    Bukkit.getLogger().warning("Skipping MBedwars shop item '"
                            + item.getId() + "' on page '" + page.getName()
                            + "' — it could not be read: " + e);
                    continue;
                }
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (!entries.isEmpty()) {
                pages.add(new RushShopData.Page(page.getName(), page.getDisplayName(),
                        safeIcon(page.getIcon()), entries));
            }
        }
        return new RushShopData(pages);
    }

    private static RushShopData.Entry entryOf(ShopItem item) {
        List<RushShopData.Price> prices = new ArrayList<>();
        for (ShopPrice price : item.getPrices()) {
            Material material = priceMaterial(price);
            int amount = price.getGeneralAmount();
            if (material == null || amount <= 0) {
                return null;
            }
            prices.add(new RushShopData.Price(material, amount));
        }
        List<RushShopData.Product> products = productsOf(item);
        if (prices.isEmpty() || products.isEmpty()) {
            return null;
        }
        return new RushShopData.Entry(item.getId(), safeIcon(item.getIcon()),
                item.getForceSlot(), prices, products);
    }

    // ------------------------------------------------------------- quick buy

    /**
     * The player's MBedwars HypixelV2 quick-buy pins, positional (null =
     * empty slot), as shop-item ids the snapshot's entries carry too. Null
     * while MBedwars has not loaded this player's properties yet — the
     * async {@link #loadQuickBuy} path covers that.
     */
    public static List<String> quickBuyIds(Player player) {
        var properties = BedwarsAPI.getPlayerDataAPI()
                .getPropertiesCached(player.getUniqueId());
        if (properties.isEmpty()) {
            return null;
        }
        ShopItem[] items = properties.get().getShopHypixelV2QuickBuyItems();
        List<String> ids = new ArrayList<>(items.length);
        for (ShopItem item : items) {
            ids.add(item == null ? null : item.getId());
        }
        return ids;
    }

    /**
     * Requests the player's MBedwars properties (a possibly-async fetch) and
     * hands the quick-buy ids to {@code callback} on the main thread.
     */
    public static void loadQuickBuy(org.bukkit.plugin.Plugin plugin, Player player,
                                    java.util.function.Consumer<List<String>> callback) {
        BedwarsAPI.getPlayerDataAPI().getProperties(player.getUniqueId(), properties -> {
            ShopItem[] items = properties.getShopHypixelV2QuickBuyItems();
            List<String> ids = new ArrayList<>(items.length);
            for (ShopItem item : items) {
                ids.add(item == null ? null : item.getId());
            }
            if (Bukkit.isPrimaryThread()) {
                callback.accept(ids);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(ids));
            }
        });
    }

    /**
     * Writes the quick-buy pins back into the player's MBedwars profile, so
     * pinning here and pinning in a real game are the same list.
     */
    public static void saveQuickBuy(Player player, List<String> ids) {
        var properties = BedwarsAPI.getPlayerDataAPI()
                .getPropertiesCached(player.getUniqueId());
        if (properties.isEmpty()) {
            return; // never loaded — nothing sane to overwrite
        }
        ShopItem[] items = new ShopItem[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            items[i] = ids.get(i) == null ? null : shopItemById(ids.get(i));
        }
        properties.get().setShopHypixelV2QuickBuyItems(items);
    }

    private static ShopItem shopItemById(String id) {
        for (ShopPage page : BedwarsAPI.getGameAPI().getShopPages()) {
            for (ShopItem item : page.getItems()) {
                if (item.getId().equals(id)) {
                    return item;
                }
            }
        }
        return null;
    }

    private static Material priceMaterial(ShopPrice price) {
        if (price instanceof SpawnerItemShopPrice spawner) {
            for (ItemStack drop : spawner.getDropType().getDroppingMaterials()) {
                if (!drop.getType().isAir()) {
                    return drop.getType();
                }
            }
            return null;
        }
        if (price instanceof ItemShopPrice itemPrice) {
            for (ItemStack stack : itemPrice.getItemStacks()) {
                if (stack != null && !stack.getType().isAir()) {
                    return stack.getType();
                }
            }
        }
        return null;
    }

    private static List<RushShopData.Product> productsOf(ShopItem item) {
        List<RushShopData.Product> products = new ArrayList<>();
        for (ShopProduct product : item.getProducts()) {
            for (RushShopData.Product resolved : resolve(product)) {
                // Oversized amounts split into legal stacks.
                ItemStack stack = resolved.stack();
                int amount = stack.getAmount();
                while (amount > 0) {
                    int size = Math.min(amount, stack.getMaxStackSize());
                    products.add(new RushShopData.Product(stack.asQuantity(size),
                            resolved.autoWear(), resolved.specialType()));
                    amount -= size;
                }
            }
        }
        return products;
    }

    private static List<RushShopData.Product> resolve(ShopProduct product) {
        // Special items resolve to the exact stack MBedwars would hand out,
        // remembered by type so use-time behavior (fireball launch, bridge
        // egg, …) can be emulated without a running game. Checked before
        // ItemShopProduct — SpecialItemShopProduct extends it.
        if (product instanceof SpecialItemShopProduct specialProduct) {
            SpecialItem special = specialProduct.getSpecialItem();
            if (special == null) {
                return List.of(); // configured id no longer registered
            }
            ItemStack stack = special.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                return List.of();
            }
            // Ids normalized to lowercase: MBedwars spells them CamelCase
            // ("Fireball", "RescuePlatform") and the use-time switch matches
            // lowercase — the mismatch is what once made every special item
            // land in the "unsupported" branch. The PLUGIN type (addon-made
            // specials) carries a null id, so both the type and its id need
            // the fallback or one addon item takes the whole shop down.
            String typeId = special.getType() == null ? null : special.getType().getId();
            return List.of(new RushShopData.Product(
                    stack.clone().asQuantity(Math.max(1, product.getAmount())),
                    product.isAutoWear(),
                    typeId == null || typeId.isBlank()
                            ? "plugin" : typeId.toLowerCase(Locale.ROOT)));
        }
        if (product instanceof ItemShopProduct itemProduct) {
            ItemStack[] stacks = itemProduct.getItemStacks();
            if (stacks == null) {
                return List.of();
            }
            List<RushShopData.Product> copies = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.getType().isAir()) {
                    copies.add(new RushShopData.Product(stack.clone(),
                            product.isAutoWear(), null));
                }
            }
            return copies;
        }
        if (product instanceof SpawnerItemShopProduct spawnerProduct) {
            for (ItemStack drop : spawnerProduct.getDropType().getDroppingMaterials()) {
                if (!drop.getType().isAir()) {
                    return List.of(new RushShopData.Product(
                            drop.clone().asQuantity(Math.max(1, product.getAmount())),
                            false, null));
                }
            }
        }
        // Command products genuinely need a running MBedwars game.
        return List.of();
    }

    private static ItemStack safeIcon(ItemStack icon) {
        return icon == null || icon.getType().isAir()
                ? new ItemStack(Material.CHEST) : icon.clone();
    }
}
