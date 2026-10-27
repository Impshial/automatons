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
  public final Map<Long, Integer> strips = new HashMap<>();
  public final int[] crops = new int[3];
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

  public static int sample(ItemStack stack) {
    if (stack.is(Items.WHEAT) || stack.is(Items.WHEAT_SEEDS)) return 1;
    if (stack.is(Items.CARROT)) return 2;
    if (stack.is(Items.POTATO)) return 3;
    if (stack.is(Items.BEETROOT) || stack.is(Items.BEETROOT_SEEDS)) return 4;
    return 0;
  }

  public boolean select(int strip, ItemStack sample) {
    if (strip < 0 || strip >= 3 || !sample.isEmpty() && sample(sample) == 0) return false;
    crops[strip] = sample(sample);
    return true;
  }

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
    // Adopt existing farmland before choosing a new default field. The crop selectors still
    // govern planting; surveying must not silently replace a player's irregular field.
    int total = side * side * 7;
    for (int budget = 0; budget < 192 && scan < total && WorldEvents.budget(1); budget++) {
      int i = scan++;
      BlockPos p = station.getBlockPos().offset(i % side - radius, i / (side * side) - 4, i / side % side - radius);
      if (!l.hasChunkAt(p)) { unknown = true; continue; }
      if (l.getBlockState(p).is(Blocks.FARMLAND) && available(l, station, p)) cells.add(p.asLong());
    }
    if (scan < total) return;
    if (!cells.isEmpty()) {
      Direction across = station.orientation.getCounterClockWise();
      java.util.function.LongToIntFunction coordinate = key -> {
        BlockPos pos = BlockPos.of(key);
        return pos.getX() * across.getStepX() + pos.getZ() * across.getStepZ();
      };
      int min = cells.stream().mapToInt(coordinate::applyAsInt).min().orElse(0);
      int width = cells.stream().mapToInt(coordinate::applyAsInt).max().orElse(min) - min + 1;
      for (long cell : cells) strips.put(cell, Math.min(2, (coordinate.applyAsInt(cell) - min) * 3 / width));
      discovered = true; reason = "Tending existing farmland"; changed(l, station); return;
    }
    if (unknown) {
      scan = 0; unknown = false; retry = l.getGameTime() + 200;
      reason = "Farmland survey has unloaded areas"; return;
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
                  && !(p.equals(center) && l.getBlockState(p.above()).is(Blocks.GLASS))
                  && !(l.getBlockState(p.above()).getBlock() instanceof CropBlock)
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
      defineField(l, center, station.orientation);
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

  /** The authored field defines the cells and strip ordering, including the missing E5 crop. */
  public void defineField(ServerLevel level, BlockPos center, Direction front) {
    BuildingTemplate layout = BuildingTemplate.load(level, "farmington_field");
    BlockPos irrigation = BuildingTemplate.point(layout.metadata.getAsJsonObject("features").get("covered_irrigation"));
    cells.clear(); strips.clear();
    var rows = layout.worksite().getAsJsonArray("crop_strips");
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i).getAsJsonObject();
      for (var point : row.getAsJsonArray("planting_cells")) {
        BlockPos soil = center.offset(BuildingTemplate.point(point).below().subtract(irrigation).rotate(layout.rotationFor(front)));
        if (!soil.equals(center)) {
          cells.add(soil.asLong());
          strips.put(soil.asLong(), i);
        }
      }
    }
    water = center;
  }

  public Item wanted(ServerLevel l, long soil) {
    int value = crops[strips.getOrDefault(soil, 1)];
    return value == 0 ? Items.AIR : PLANTS.get(value - 1);
  }

  public Map<Item, Integer> reserves(ServerLevel l) {
    Map<Item, Integer> stock = new LinkedHashMap<>();
    for (long cell : cells) {
      Item item = wanted(l, cell);
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
    n.putIntArray("Crops", crops);
    ListTag stripCells = new ListTag();
    strips.forEach((pos, strip) -> {
      CompoundTag c = new CompoundTag(); c.putLong("Pos", pos); c.putInt("Strip", strip); stripCells.add(c);
    });
    n.put("Strips", stripCells);
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
    int[] selected = n.getIntArray("Crops");
    for (int i = 0; i < Math.min(3, selected.length); i++) f.crops[i] = Math.max(0, Math.min(4, selected[i]));
    for (Tag value : n.getList("Strips", 10)) {
      CompoundTag c = (CompoundTag)value;
      if (c.getInt("Strip") >= 0 && c.getInt("Strip") < 3) f.strips.put(c.getLong("Pos"), c.getInt("Strip"));
    }
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
