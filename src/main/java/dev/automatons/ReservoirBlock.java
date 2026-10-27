package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.fluids.FluidUtil;

public final class ReservoirBlock extends BaseEntityBlock {
  private final boolean collector;

  public ReservoirBlock(boolean collector) {
    super(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.5f)
            .sound(SoundType.STONE)
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK));
    this.collector = collector;
  }

  @Override
  public RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    if (collector)
      return Shapes.or(
          box(4, 0, 4, 12, 2, 12), box(5, 2, 5, 11, 10, 11),
          box(0, 10, 0, 16, 12, 16), box(0, 12, 0, 16, 16, 2),
          box(0, 12, 14, 16, 16, 16), box(0, 12, 2, 2, 16, 14),
          box(14, 12, 2, 16, 16, 14), box(6, 12, 6, 10, 16, 10));
    return Shapes.block();
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new Reservoir(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(
            type,
            collector ? Luminall.COLLECTOR_BE.get() : Luminall.TANK_BE.get(),
            Reservoir::tick);
  }

  @Override
  public InteractionResult use(
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (!(level.getBlockEntity(pos) instanceof Reservoir machine)) return InteractionResult.PASS;
    ItemStack held = player.getItemInHand(hand);
    if (held.is(Automatons.WAND.get())) return InteractionResult.PASS;
    if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos))
      return InteractionResult.FAIL;
    if (collector
        && (held.isEmpty() || held.is(Items.GLASS_BOTTLE) || held.is(Luminall.BOTTLE.get()))) {
      // Do not repeat empty-hand retrieval through vanilla's automatic offhand retry.
      if (hand == InteractionHand.OFF_HAND && held.isEmpty()) return InteractionResult.CONSUME;
      if (level.isClientSide) return InteractionResult.SUCCESS;
      if (held.isEmpty() && !machine.bottle.isEmpty()) {
        player.setItemInHand(hand, machine.bottle);
        machine.bottle = ItemStack.EMPTY;
      } else if (!held.isEmpty() && machine.bottle.isEmpty()) {
        machine.bottle = held.copyWithCount(1);
        held.shrink(1);
      }
      machine.changed();
      machine.synchronize();
      return InteractionResult.CONSUME;
    }
    if (level.isClientSide)
      return FluidUtil.getFluidHandler(held).isPresent()
          ? InteractionResult.SUCCESS
          : InteractionResult.PASS;
    if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, null)) {
      machine.synchronize();
      return InteractionResult.CONSUME;
    }
    return InteractionResult.PASS;
  }

  @Override
  public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    // Creative removal normally omits loot; still recover stored fluid/bottle exactly once.
    if (collector
        && !level.isClientSide
        && player.isCreative()
        && level.getBlockEntity(pos) instanceof Reservoir machine
        && (machine.tank.getFluidAmount() > 0
            || !machine.bottle.isEmpty()
            || machine.machineFuelThirds > 0
            || machine.machineFuelFraction > 0)) {
      ItemStack packed = new ItemStack(asItem());
      BlockItem.setBlockEntityData(packed, machine.getType(), machine.saveWithoutMetadata());
      popResource(level, pos, packed);
    }
    super.playerWillDestroy(level, pos, state, player);
  }

  public static boolean pack(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
    var level = player.serverLevel();
    if (!player.mayBuild()
        || player.isSpectator()
        || !level.mayInteract(player, pos)
        || !(level.getBlockEntity(pos) instanceof Reservoir tank)
        || tank.collector
        || !Permissions.allowed(level, pos, player.getUUID(), Permissions.Action.PROCESS))
      return false;
    ItemStack packed = new ItemStack(Luminall.TANK_ITEM.get());
    BlockItem.setBlockEntityData(packed, tank.getType(), tank.saveWithoutMetadata());
    // Remove the BE first: this authorized move must bypass the normal spill path.
    level.removeBlockEntity(pos);
    level.removeBlock(pos, false);
    if (!player.getInventory().add(packed)) player.drop(packed, false);
    return true;
  }

  @Override
  public void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
    if (!collector
        && !state.is(next.getBlock())
        && level instanceof net.minecraft.server.level.ServerLevel server
        && level.getBlockEntity(pos) instanceof Reservoir tank) {
      FluidSpill.queue(server, pos, tank.tank.getFluid().copy());
    }
    super.onRemove(state, level, pos, next, moving);
  }
}
