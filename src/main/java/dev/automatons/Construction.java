package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Natural surface cover may change while a roof is built (grass, snow, tall plants). */
public final class Construction {
  public static boolean matches(BlockState actual, BlockState target) {
    if (target.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && FarmPlan.soil(actual))
      return true;
    if (actual.getBlock() != target.getBlock()) return false;
    for (var property : target.getProperties()) {
      String name = property.getName();
      // Neighbor connections and player-operated doors are not blueprint changes.
      if (name.equals("shape") || name.equals("open") || name.equals("powered")) continue;
      if (!actual.getValue(property).equals(target.getValue(property))) return false;
    }
    return true;
  }

  public static boolean soft(BlockState state) {
    return !state.hasBlockEntity()
        && state.getFluidState().isEmpty()
        && !state.requiresCorrectToolForDrops()
        && (state.isAir() || state.canBeReplaced() || state.is(MiningRules.TERRAIN));
  }

  public static boolean naturalChange(BlockState before, BlockState after) {
    return before != null && soft(before) && soft(after);
  }

  public static boolean naturalObstacle(BlockState state) {
    return !state.isAir()
        && (soft(state)
            || state.is(net.minecraft.tags.BlockTags.LOGS)
            || state.is(net.minecraft.tags.BlockTags.LEAVES)
            || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
            || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER));
  }

  public static void placed(Lumberton worker, BlockPos pos, BlockState state) {
    var sound = state.getSoundType(worker.level(), pos, worker);
    ((ServerLevel) worker.level())
        .playSound(
            null,
            pos,
            sound.getPlaceSound(),
            net.minecraft.sounds.SoundSource.BLOCKS,
            (sound.getVolume() + 1) / 2,
            sound.getPitch() * .8f);
    worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
  }
}
