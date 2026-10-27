package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.*;

/** Pick at most one reachable flower along an existing route; never create a detour. */
public final class OpportunisticFlowers {
  public static void tick(Lumberton worker) {
    if (worker.tickCount % 10 != 0 || worker.owner == null || !worker.authorized || worker.job() == 0
        || worker.operating() <= WorkerEnergy.LOW || worker.threats.escaping || worker.combat.busy()
        || worker.onClimbable() || worker.isPassenger() || worker.mining.travelControlled()
        || !worker.getNavigation().isInProgress()) return;
    collectNearby(worker);
  }
  static boolean collectNearby(Lumberton worker) {
    ServerLevel level = (ServerLevel)worker.level();
    List<BlockPos> candidates = new ArrayList<>();
    for (BlockPos p : BlockPos.betweenClosed(worker.blockPosition().offset(-2, -1, -2), worker.blockPosition().offset(2, 1, 2)))
      if (worker.position().distanceToSqr(Vec3.atCenterOf(p)) <= 4) candidates.add(p.immutable());
    candidates.sort(Comparator.comparingDouble(p -> worker.position().distanceToSqr(Vec3.atCenterOf(p))));
    for (BlockPos pos : candidates) {
      if (!level.hasChunkAt(pos)) continue;
      var state = level.getBlockState(pos);
      if (!state.is(BlockTags.FLOWERS) || DeadVegetation.isDead(state)
          || state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) continue;
      var hit = level.clip(new net.minecraft.world.level.ClipContext(worker.getEyePosition(), Vec3.atCenterOf(pos),
          net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, worker));
      if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(pos)) continue;
      List<ItemStack> drops = Block.getDrops(state, level, pos, null, worker, ItemStack.EMPTY);
      if (!Inventories.fits(worker.cargo, drops) || !Permissions.allowed(level, pos, worker.owner, Permissions.Action.CUT)) continue;
      if (state.hasProperty(DoublePlantBlock.HALF) && level.getBlockState(pos.above()).is(state.getBlock())) {
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2); SourceReserves.get(level).forget(pos.above());
      }
      if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) continue;
      SourceReserves.get(level).forget(pos);
      for (ItemStack drop : drops) {
        if (!Inventories.insert(worker.cargo, drop, false).isEmpty())
          throw new IllegalStateException("Flower inventory changed during server-thread commit");
      }
      worker.swing(InteractionHand.MAIN_HAND); return true;
    }
    return false;
  }
}
