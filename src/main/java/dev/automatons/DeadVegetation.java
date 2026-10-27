package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.RegistryObject;

/** Static remains. No crop age, random growth, bonemeal interface or living-source tags. */
public final class DeadVegetation extends BushBlock {
  public static final RegistryObject<Block> PLANT = Automatons.BLOCKS.register("dead_plant", () -> new DeadVegetation(false));
  public static final RegistryObject<Block> CROP = Automatons.BLOCKS.register("dead_crop", () -> new DeadVegetation(true));
  private final boolean crop;
  private DeadVegetation(boolean crop) {
    super(Properties.copy(Blocks.DEAD_BUSH).noCollission().instabreak().noLootTable());
    this.crop = crop;
  }
  @Override
  protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
    return crop ? state.is(Blocks.FARMLAND) || state.is(Blocks.SOUL_SAND)
        : super.mayPlaceOn(state, level, pos) || state.is(net.minecraft.tags.BlockTags.SAND)
            || state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)
            || state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO);
  }
  public static void register() {
    Automatons.ITEMS.register("dead_plant", () -> new BlockItem(PLANT.get(), new Item.Properties()));
    Automatons.ITEMS.register("dead_crop", () -> new BlockItem(CROP.get(), new Item.Properties()));
  }
  public static boolean isDead(BlockState state) { return state.is(PLANT.get()) || state.is(CROP.get()); }

  /** Worker removal deliberately does not run a living plant's harvesting path. */
  public static boolean clear(Lumberton worker, BlockPos pos) {
    if (!(worker.level() instanceof net.minecraft.server.level.ServerLevel level)
        || !level.hasChunkAt(pos)) return false;
    BlockState state = level.getBlockState(pos);
    if (!isDead(state) || MiningRules.breakPermission(worker, pos, state) < 0) return false;
    return level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
  }
}
