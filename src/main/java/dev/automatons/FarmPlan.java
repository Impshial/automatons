package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Shared field board owned by one Totem; positions identify soil, not the crop above it. */
public final class FarmPlan {
  public final Set<Long> cells = new LinkedHashSet<>();
  public final Map<Long, Item> replant = new HashMap<>();
  private final Map<Long, UUID> claims = new HashMap<>();
  public BlockPos water;
  public boolean generated, discovered, boneMeal = true;
  public int preference;
  public String reason = "Surveying farmland";
  private int scan, candidates, scanRange = -1;
  private boolean unknown;
  private long lastScan = -1, retry;
  public Item defaultPlant;
  public static final List<Item> PLANTS =
      List.of(Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS);

  public static Item planting(BlockState state) {
    if (state.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
    if (state.is(Blocks.CARROTS)) return Items.CARROT;
    if (state.is(Blocks.POTATOES)) return Items.POTATO;
    if (state.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
    return Items.AIR;
  }

  public static BlockState crop(Item item) {
    return (item == Items.CARROT
            ? Blocks.CARROTS
            : item == Items.POTATO
                ? Blocks.POTATOES
                : item == Items.BEETROOT_SEEDS ? Blocks.BEETROOTS : Blocks.WHEAT)
        .defaultBlockState();
  }

  public static boolean soil(BlockState state) {
    return state.is(Blocks.DIRT)
        || state.is(Blocks.GRASS_BLOCK)
        || state.is(Blocks.DIRT_PATH)
        || state.is(Blocks.FARMLAND);
  }

  public boolean contains(BlockPos p) {
    return cells.contains(p.asLong())
        || cells.contains(p.below().asLong())
        || water != null && (water.equals(p) || water.above().equals(p));
  }

  public boolean inRange(Station s, BlockPos p) {
    return Math.max(
            Math.abs(p.getX() - s.getBlockPos().getX()),
            Math.abs(p.getZ() - s.getBlockPos().getZ()))
        <= s.workRange();
  }

  private boolean available(ServerLevel l, Station station, BlockPos p) {
    if (!inRange(station, p)
        || WorldData.get(l).protectedAt(p)
        || WorldData.get(l).mineProtected(p, null)) return false;
    if (WorldData.get(l).buildings.values().stream()
        .anyMatch(b -> b.contains(l, p) || b.contains(l, p.above()))) return false;
    return WorldData.get(l).farms.entrySet().stream()
        .noneMatch(e -> !e.getKey().equals(station.identity) && e.getValue().contains(p));
  }

  public void discover(ServerLevel l, Station station, Lumberton worker) {
    if (discovered || lastScan == l.getGameTime() || l.getGameTime() < retry) return;
    lastScan = l.getGameTime();
    int radius = station.workRange(), side = radius * 2 + 1;
    if (scanRange != radius) {
      scanRange = radius;
      scan = 0;
      candidates = 0;
      unknown = false;
      cells.removeIf(p -> !inRange(station, BlockPos.of(p)));
    }
    int total = side * side * 7;
    for (int budget = 0; budget < 192 && scan < total && WorldEvents.budget(1); budget++) {
      int i = scan++;
      BlockPos p =
          station
              .getBlockPos()
              .offset(i % side - radius, i / (side * side) - 4, i / side % side - radius);
      if (!l.hasChunkAt(p)) {
        unknown = true;
        continue;
      }
      if (l.getBlockState(p).is(Blocks.FARMLAND) && available(l, station, p)) cells.add(p.asLong());
    }
    if (scan < total) return;
    if (!cells.isEmpty()) {
      discovered = true;
      reason = "Tending existing farmland";
      changed(l, station);
      return;
    }
    if (unknown) {
      scan = 0;
      unknown = false;
      retry = l.getGameTime() + 200;
      reason = "Farmland survey has unloaded areas";
      return;
    }
    List<BlockPos> centers = new ArrayList<>();
    for (int x = -radius + 4; x <= radius - 4; x++)
      for (int z = -radius + 4; z <= radius - 4; z++)
        centers.add(station.getBlockPos().offset(x, -1, z));
    centers.sort(
        Comparator.comparingDouble((BlockPos p) -> p.distSqr(station.getBlockPos()))
            .thenComparingLong(BlockPos::asLong));
    for (int budget = 0; budget < 4 && candidates < centers.size(); budget++, candidates++) {
      BlockPos center = centers.get(candidates);
      boolean valid = true;
      for (int x = -4; x <= 4 && valid; x++)
        for (int z = -4; z <= 4 && valid; z++) {
          BlockPos p = center.offset(x, 0, z);
          if (!l.hasChunkAt(p)
              || !available(l, station, p)
              || !(soil(l.getBlockState(p))
                  || p.equals(center)
                      && l.getBlockState(p).is(Blocks.WATER)
                      && l.getFluidState(p).isSource())
              || !l.getBlockState(p.above()).isAir() && !l.getBlockState(p.above()).canBeReplaced()
              || !l.getFluidState(p.above()).isEmpty()
              || !l.getBlockState(p.above(2)).isAir()) valid = false;
        }
      if (!valid) continue;
      if (!worker.canReach(center.above())) {
        if (worker.pathDeferred) return;
        continue;
      }
      water = center;
      generated = discovered = true;
      for (int x = -4; x <= 4; x++)
        for (int z = -4; z <= 4; z++)
          if (x != 0 || z != 0) cells.add(center.offset(x, 0, z).asLong());
      reason = "Preparing shared 9 x 9 field";
      changed(l, station);
      return;
    }
    if (candidates >= centers.size()) {
      candidates = 0;
      retry = l.getGameTime() + 200;
      reason = "No clear, level 9 x 9 field in range";
    }
  }

  public Item wanted(ServerLevel l, long soil) {
    if (replant.containsKey(soil)) return replant.get(soil);
    Item current = planting(l.getBlockState(BlockPos.of(soil).above()));
    if (current != Items.AIR) return current;
    return preference > 0
        ? PLANTS.get(Math.min(3, preference - 1))
        : defaultPlant == null ? Items.AIR : defaultPlant;
  }

  public Map<Item, Integer> reserves(ServerLevel l) {
    Map<Item, Integer> stock = new LinkedHashMap<>();
    for (long cell : cells) {
      Item item = l.hasChunkAt(BlockPos.of(cell)) ? wanted(l, cell) : defaultPlant;
      if (item != null && item != Items.AIR) stock.merge(item, 1, Integer::sum);
    }
    return stock;
  }

  public boolean claim(BlockPos p, UUID worker) {
    UUID owner = claims.get(p.asLong());
    if (owner != null && !owner.equals(worker)) return false;
    claims.put(p.asLong(), worker);
    return true;
  }

  public boolean owned(BlockPos p, UUID worker) {
    return worker.equals(claims.get(p.asLong()));
  }

  public void release(UUID worker) {
    claims.values().removeIf(worker::equals);
  }

  public void changed(ServerLevel l, Station station) {
    station.setChanged();
    WorldData.get(l).setDirty();
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    n.putLongArray("Cells", cells.stream().mapToLong(Long::longValue).toArray());
    if (water != null) n.putLong("Water", water.asLong());
    n.putBoolean("Generated", generated);
    n.putBoolean("Discovered", discovered);
    n.putBoolean("BoneMeal", boneMeal);
    n.putInt("Preference", preference);
    n.putString("Reason", reason);
    if (defaultPlant != null)
      n.putString(
          "DefaultPlant",
          net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(defaultPlant).toString());
    ListTag pending = new ListTag();
    replant.forEach(
        (p, item) -> {
          CompoundTag cell = new CompoundTag();
          cell.putLong("Pos", p);
          cell.putString(
              "Item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
          pending.add(cell);
        });
    n.put("Replant", pending);
    return n;
  }

  public static FarmPlan load(CompoundTag n) {
    FarmPlan f = new FarmPlan();
    for (long p : n.getLongArray("Cells")) f.cells.add(p);
    if (n.contains("Water")) f.water = BlockPos.of(n.getLong("Water"));
    f.generated = n.getBoolean("Generated");
    f.discovered = n.getBoolean("Discovered");
    f.boneMeal = n.getBoolean("BoneMeal");
    f.preference = Math.max(0, Math.min(4, n.getInt("Preference")));
    f.reason = n.getString("Reason");
    if (n.contains("DefaultPlant"))
      f.defaultPlant =
          net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
              new net.minecraft.resources.ResourceLocation(n.getString("DefaultPlant")));
    for (Tag t : n.getList("Replant", 10)) {
      CompoundTag c = (CompoundTag) t;
      Item item =
          net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
              new net.minecraft.resources.ResourceLocation(c.getString("Item")));
      if (PLANTS.contains(item)) f.replant.put(c.getLong("Pos"), item);
    }
    return f;
  }
}
