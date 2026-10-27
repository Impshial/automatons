package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/** One shared production budget per producer per interval, across all active collectors. */
public final class LuminaCollection {
  private static final Set<Reservoir> loaded = Collections.newSetFromMap(new WeakHashMap<>());

  public static void loaded(Reservoir machine) {
    if (!machine.getLevel().isClientSide) loaded.add(machine);
  }

  public static void unloaded(Reservoir machine) {
    loaded.remove(machine);
  }

  public static AABB bounds(BlockPos p, int reach) {
    return new AABB(
        p.getX() - 1,
        p.getY() - reach,
        p.getZ() - 1,
        p.getX() + 3,
        p.getY() + reach + 1,
        p.getZ() + 3);
  }

  public static boolean includes(BlockPos collector, BlockPos producer, int reach) {
    int x = producer.getX() - collector.getX(), z = producer.getZ() - collector.getZ();
    return x >= -1
        && x <= 2
        && z >= -1
        && z <= 2
        && Math.abs(producer.getY() - collector.getY()) <= reach;
  }

  public static String category(ServerLevel level, BlockPos pos, BlockState state) {
    if (DeadVegetation.isDead(state)) return "";
    if (state.hasProperty(DoublePlantBlock.HALF)
        && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) return "";
    if (LuminaHarvest.tagged(state, "logs"))
      return TreeFinder.recognizeNatural(level, pos) ? "logs" : "";
    if (LuminaHarvest.tagged(state, "crops")) {
      if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) return "crops";
      if (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) == 3) return "crops";
      return "";
    }
    if (LuminaHarvest.tagged(state, "berries"))
      return state.hasProperty(SweetBerryBushBlock.AGE)
              && state.getValue(SweetBerryBushBlock.AGE) == 3
          ? "berries"
          : "";
    for (String category : List.of("fruit", "flowers", "stems"))
      if (LuminaHarvest.tagged(state, category)) return category;
    if (LuminaHarvest.tagged(state, "leaves"))
      return state.hasProperty(LeavesBlock.PERSISTENT)
              && !state.getValue(LeavesBlock.PERSISTENT)
              && state.getValue(LeavesBlock.DISTANCE) < 7
          ? "leaves"
          : "";
    return LuminaHarvest.tagged(state, "grass") ? "grass" : "";
  }

  @SubscribeEvent
  public static void tick(TickEvent.LevelTickEvent event) {
    if (event.phase != TickEvent.Phase.END
        || !(event.level instanceof ServerLevel level)
        || level.getGameTime() % Balance.COLLECTOR_INTERVAL.get() != 0) return;
    List<Reservoir> active =
        loaded.stream()
            .filter(
                machine ->
                    !machine.isRemoved()
                        && machine.getLevel() == level
                        && machine.lastTick == level.getGameTime()
                        && level.hasChunkAt(machine.getBlockPos())
                        && machine.space() > 0)
            .sorted(Comparator.comparingLong(machine -> machine.getBlockPos().asLong()))
            .toList();
    collect(level, active, level.getGameTime() / Balance.COLLECTOR_INTERVAL.get());
  }

  public static void collect(ServerLevel level, List<Reservoir> machines, long cycle) {
    Map<BlockPos, List<Reservoir>> producers =
        new TreeMap<>(Comparator.comparingLong(BlockPos::asLong));
    Map<Reservoir, Integer> budgets = new HashMap<>();
    int height = Balance.COLLECTOR_HEIGHT.get();
    for (Reservoir machine : machines) {
      if (machine.space() <= 0 || machine.isRemoved()) continue;
      budgets.put(machine, Math.min(machine.space(), Balance.COLLECTOR_MAX.get()));
      BlockPos origin = machine.getBlockPos();
      for (BlockPos pos :
          BlockPos.betweenClosed(origin.offset(-1, -height, -1), origin.offset(2, height, 2))) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) continue;
        producers.computeIfAbsent(pos.immutable(), ignored -> new ArrayList<>()).add(machine);
      }
    }
    // The volume is refreshed once per configurable interval, never every tick. A shared
    // position is inspected once even when several collectors overlap it.
    for (var entry : producers.entrySet()) {
      BlockPos pos = entry.getKey();
      BlockState before = level.getBlockState(pos);
      String category = category(level, pos, before);
      var configured = Balance.COLLECTOR_YIELDS.get(category);
      if (configured == null) {
        if (before.isAir() || DeadVegetation.isDead(before)) SourceReserves.get(level).forget(pos);
        continue;
      }
      if (entry.getValue().stream().noneMatch(m -> budgets.getOrDefault(m, 0) > 0 && m.space() > 0))
        continue;
      int available = SourceReserves.get(level).available(pos, before, category, configured.get());
      int produced = 0;
      List<Reservoir> candidates = entry.getValue();
      int start = SourceReserves.get(level).recipient(pos, before, category, candidates.size());
      int next = start;
      while (available > 0) {
        boolean accepted = false;
        for (int i = 0; i < candidates.size() && available > 0; i++) {
          Reservoir machine = candidates.get((start + i) % candidates.size());
          int budget = budgets.getOrDefault(machine, 0);
          if (budget <= 0 || machine.space() <= 0) continue;
          int moved =
              machine.tank.fill(new FluidStack(Luminall.SOURCE.get(), 1), FluidAction.EXECUTE);
          if (moved == 0) continue;
          budgets.put(machine, budget - moved);
          available -= moved;
          produced += moved;
          next = (start + i + 1) % candidates.size();
          accepted = true;
          machine.lastProducer = pos;
          machine.lastCollection = level.getGameTime();
        }
        if (!accepted) break;
      }
      if (produced > 0) SourceReserves.get(level).nextRecipient(pos, before, category, next);
      SourceReserves.get(level).debit(level, pos, before, category, produced);
    }
    for (Reservoir machine : machines) machine.synchronize();
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
    loaded.clear();
  }
}
