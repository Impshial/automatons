package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.*;

/** Fallen timber is rendered as an entity, but must still obstruct every route node. */
public final class LumbertonNavigation extends GroundPathNavigation {
  public LumbertonNavigation(Lumberton worker, Level level) {
    super(worker, level);
    setMaxVisitedNodesMultiplier(2);
  }

  @Override
  protected PathFinder createPathFinder(int maximumNodes) {
    nodeEvaluator =
        new WalkNodeEvaluator() {
          @Override
          public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z, Mob mob) {
            if (mob instanceof Lumberton worker && worker.navigationBlocked(new BlockPos(x, y, z)))
              return BlockPathTypes.BLOCKED;
            return super.getBlockPathType(world, x, y, z, mob);
          }
        };
    nodeEvaluator.setCanPassDoors(true);
    nodeEvaluator.setCanOpenDoors(true);
    return new PathFinder(nodeEvaluator, maximumNodes);
  }
}
