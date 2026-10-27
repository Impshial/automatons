package dev.automatons;

import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public final class LuminaDrain extends Block {
  public LuminaDrain() {
    super(Properties.copy(Blocks.IRON_BARS).noOcclusion());
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return box(0, 0, 0, 16, 2, 16);
  }

  @Override
  public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
    return l.getBlockState(p.below()).is(Luminall.TANK.get());
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext c) {
    return canSurvive(defaultBlockState(), c.getLevel(), c.getClickedPos())
        ? defaultBlockState()
        : null;
  }

  @Override
  public BlockState updateShape(
      BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
    return !canSurvive(s, l, p)
        ? Blocks.AIR.defaultBlockState()
        : super.updateShape(s, d, n, l, p, np);
  }

  @Override
  public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
    if (!l.isClientSide) l.scheduleTick(p, this, 20);
  }

  @Override
  public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) {
    if (!(l.getBlockEntity(p.below()) instanceof Reservoir tank) || tank.collector) return;
    for (ServerPlayer player :
        l.getEntitiesOfClass(ServerPlayer.class, new AABB(p).inflate(0, .3, 0))) {
      if (player.isSpectator()
          || player.getVehicle() != null
          || !player.onGround()
          || player.getY() < p.getY() + .1
          || player.getY() > p.getY() + .2
          || player.getX() < p.getX()
          || player.getX() > p.getX() + 1
          || player.getZ() < p.getZ()
          || player.getZ() > p.getZ() + 1) continue;
      transfer(player, tank);
    }
    l.scheduleTick(p, this, 20);
  }

  static int transfer(ServerPlayer player, Reservoir tank) {
    if (player.isSpectator() || tank.collector) return 0;
    // Integer mB/third quanta conserve energy under configurable conversion rates.
    int volume = Balance.BOTTLE_VOLUME.get(), energy = Balance.BOTTLE_LUMINA.get() * 3;
    int a = volume, b = energy;
    while (b != 0) {
      int r = a % b;
      a = b;
      b = r;
    }
    int fluidUnit = volume / a, energyUnit = energy / a;
    long units = Math.min(Lumina.thirds(player) / energyUnit, Math.max(1, 15 / energyUnit));
    units = Math.min(units, tank.space() / fluidUnit);
    if (units <= 0) return 0;
    int offered = (int) units * fluidUnit;
    int accepted =
        tank.tank.fill(new FluidStack(Luminall.SOURCE.get(), offered), FluidAction.SIMULATE);
    int count = accepted / fluidUnit;
    if (count == 0) return 0;
    int moved =
        tank.tank.fill(
            new FluidStack(Luminall.SOURCE.get(), count * fluidUnit), FluidAction.EXECUTE);
    Lumina.addThirds(player, -(long) (moved / fluidUnit) * energyUnit);
    tank.synchronize();
    return moved;
  }
}
