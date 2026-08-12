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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The only class that touches the MBedwars API. Everything it hands out is
 * plain Bukkit data, so the rest of the plugin loads and runs fine on servers
 * without MBedwars — every entry point checks {@link #available()} first, and
 * callers must too before this class is ever loaded.
 */
public final class MBedwarsHook {

    /** What one MBedwars arena looks like to the importer: Bukkit types only. */
    public record ImportedArena(String name, String displayName, World world,
                                BoundingBox region, String status,
                                List<ImportedTeam> teams, List<ImportedSpawner> spawners,
                                List<Location> dealers) {
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
                arena.getStatus().name(), teams, spawners, dealers);
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
    public static RushShopData shopSnapshot(Player player) {
        List<RushShopData.Page> pages = new ArrayList<>();
        for (ShopPage page : BedwarsAPI.getGameAPI().getShopPages()) {
            List<RushShopData.Entry> entries = new ArrayList<>();
            for (ShopItem item : page.getItems()) {
                RushShopData.Entry entry = entryOf(item, player);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            if (!entries.isEmpty()) {
                pages.add(new RushShopData.Page(page.getName(), safeIcon(page.getIcon()), entries));
            }
        }
        return new RushShopData(pages);
    }

    private static RushShopData.Entry entryOf(ShopItem item, Player player) {
        List<RushShopData.Price> prices = new ArrayList<>();
        for (ShopPrice price : item.getPrices()) {
            Material material = priceMaterial(price);
            int amount = price.getGeneralAmount();
            if (material == null || amount <= 0) {
                return null;
            }
            prices.add(new RushShopData.Price(material, amount));
        }
        List<ItemStack> products = productsOf(item, player);
        if (prices.isEmpty() || products.isEmpty()) {
            return null;
        }
        return new RushShopData.Entry(safeIcon(item.getIcon()), prices, products);
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

    private static List<ItemStack> productsOf(ShopItem item, Player player) {
        // Identical stacks are merged so a "16 wool" product is one stack.
        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        for (ShopProduct product : item.getProducts()) {
            for (ItemStack stack : resolve(product, player)) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                ItemStack key = stack.asOne();
                merged.merge(key, stack.getAmount(), Integer::sum);
            }
        }
        List<ItemStack> products = new ArrayList<>();
        merged.forEach((stack, amount) -> {
            while (amount > 0) {
                int size = Math.min(amount, stack.getMaxStackSize());
                products.add(stack.asQuantity(size));
                amount -= size;
            }
        });
        return products;
    }

    private static List<ItemStack> resolve(ShopProduct product, Player player) {
        if (product instanceof ItemShopProduct itemProduct) {
            ItemStack[] stacks = itemProduct.getItemStacks();
            if (stacks == null) {
                return List.of();
            }
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : stacks) {
                if (stack != null) {
                    copies.add(stack.clone());
                }
            }
            return copies;
        }
        if (product instanceof SpawnerItemShopProduct spawnerProduct) {
            for (ItemStack drop : spawnerProduct.getDropType().getDroppingMaterials()) {
                if (!drop.getType().isAir()) {
                    return List.of(drop.clone().asQuantity(Math.max(1, product.getAmount())));
                }
            }
        }
        // Special items and command products need a running MBedwars game.
        return List.of();
    }

    private static ItemStack safeIcon(ItemStack icon) {
        return icon == null || icon.getType().isAir()
                ? new ItemStack(Material.CHEST) : icon.clone();
    }
}
