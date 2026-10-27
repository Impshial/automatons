package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;

/** Cosmetic, uncollectable puddles: no spreading source blocks or persistent fluid duplication. */
public final class FluidSpill extends Block {
  public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);

  private record Pending(ServerLevel level, BlockPos pos, int kind) {}

  private static final List<Pending> pending = new ArrayList<>();

  public FluidSpill() {
    super(Properties.of().noCollission().noOcclusion().replaceable().instabreak().noLootTable());
    registerDefaultState(stateDefinition.any().setValue(KIND, 0));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(KIND);
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return Shapes.empty();
  }

  @Override
  public void onPlace(BlockState s, Level l, BlockPos p, BlockState old, boolean moving) {
    if (!l.isClientSide) l.scheduleTick(p, this, 60);
  }

  @Override
  public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource random) {
    l.removeBlock(p, false);
  }

  public static void queue(ServerLevel l, BlockPos p, FluidStack fluid) {
    if (fluid.isEmpty()) return;
    int kind =
        fluid.getFluid().isSame(Luminall.SOURCE.get())
            ? 0
            : fluid.getFluid().isSame(Fluids.LAVA) ? 2 : 1;
    pending.add(new Pending(l, p.immutable(), kind));
  }

  @SubscribeEvent
  public static void endTick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    var batch = new ArrayList<>(pending);
    pending.clear();
    for (var spill : batch)
      if (spill.level.hasChunkAt(spill.pos) && spill.level.getBlockState(spill.pos).isAir())
        spill.level.setBlock(
            spill.pos, Luminall.SPILL.get().defaultBlockState().setValue(KIND, spill.kind), 3);
  }
}
