package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;

/** A source's remaining physical mB and quarter-mB production fraction survive growth/reloads. */
public final class SourceReserves extends SavedData {
  private static final class Entry {
    String block;
    int remaining, quarters;
    Entry(String block, int remaining) { this.block = block; this.remaining = remaining; }
  }
  private final Map<Long, Entry> sources = new HashMap<>();
  public static SourceReserves get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(SourceReserves::load, SourceReserves::new, "automatons_sources");
  }
  private Entry entry(BlockPos pos, BlockState state, String category) {
    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    Entry entry = sources.get(pos.asLong());
    if (entry == null || !entry.block.equals(id)) {
      // Logs, leaves and roots are never exhausted. Source recognition remains authoritative.
      boolean tree = category.equals("logs") || category.equals("leaves")
          || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
      entry = new Entry(id, tree ? -1 : 2 * (category.equals("crops")
          ? Balance.CROP_RESERVE.get() : Balance.PLANT_RESERVE.get()));
      sources.put(pos.asLong(), entry);
      setDirty();
    }
    return entry;
  }
  public int available(BlockPos pos, BlockState state, String category, int originalYield) {
    Entry entry = entry(pos, state, category);
    int quarters = entry.quarters + originalYield;
    entry.quarters = quarters % 4;
    setDirty();
    return entry.remaining < 0 ? quarters / 4 : Math.min(entry.remaining, quarters / 4);
  }
  public void debit(ServerLevel level, BlockPos pos, BlockState before, String category, int moved) {
    if (moved <= 0) return;
    Entry entry = entry(pos, before, category);
    if (entry.remaining < 0) return;
    entry.remaining = Math.max(0, entry.remaining - moved);
    setDirty();
    if (entry.remaining > 0 || !level.getBlockState(pos).equals(before)) return;
    if (before.hasProperty(DoublePlantBlock.HALF)
        && before.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
      BlockState upper = level.getBlockState(pos.above());
      if (upper.is(before.getBlock()) && upper.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER)
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
    }
    if (before.is(Blocks.SUGAR_CANE) || before.is(Blocks.CACTUS) || before.is(Blocks.BAMBOO)) {
      // A dead base cannot support a live column. Clear its dependent cells without
      // invoking their ordinary healthy-item drops through neighbor updates.
      BlockPos above = pos.above();
      while (above.getY() < level.getMaxBuildHeight() && level.hasChunkAt(above)
          && level.getBlockState(above).is(before.getBlock())) {
        level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        forget(above); above = above.above();
      }
    }
    level.setBlock(pos, (category.equals("crops") ? DeadVegetation.CROP : DeadVegetation.PLANT).get().defaultBlockState(), 3);
    forget(pos);
  }
  public void forget(BlockPos pos) { if (sources.remove(pos.asLong()) != null) setDirty(); }
  public int remaining(BlockPos pos) { Entry entry = sources.get(pos.asLong()); return entry == null ? -1 : entry.remaining; }
  public static SourceReserves load(CompoundTag n) {
    var result = new SourceReserves();
    for (Tag tag : n.getList("Sources", 10)) {
      var saved = (CompoundTag) tag;
      var entry = new Entry(saved.getString("Block"), saved.getInt("Remaining"));
      entry.quarters = Math.floorMod(saved.getInt("Quarters"), 4);
      result.sources.put(saved.getLong("Pos"), entry);
    }
    return result;
  }
  @Override public CompoundTag save(CompoundTag n) {
    var list = new ListTag();
    sources.forEach((pos, entry) -> {
      var saved = new CompoundTag();
      saved.putLong("Pos", pos); saved.putString("Block", entry.block);
      saved.putInt("Remaining", entry.remaining); saved.putInt("Quarters", entry.quarters);
      list.add(saved);
    });
    n.put("Sources", list); return n;
  }
}
