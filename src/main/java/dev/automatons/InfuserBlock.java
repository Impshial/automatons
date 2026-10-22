package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class InfuserBlock extends BaseEntityBlock {
  public InfuserBlock() {
    super(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.QUARTZ)
            .strength(2.5f)
            .sound(SoundType.STONE)
            .noOcclusion());
  }

  @Override
  public RenderShape getRenderShape(BlockState s) {
    return RenderShape.MODEL;
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return Shapes.or(
        box(2, 0, 2, 14, 3, 14),
        box(5, 3, 5, 11, 6, 11),
        box(3, 5, 3, 5, 8, 5),
        box(11, 5, 3, 13, 8, 5),
        box(3, 5, 11, 5, 8, 13),
        box(11, 5, 11, 13, 8, 13));
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new Infuser(p, s);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level l, BlockState s, BlockEntityType<T> t) {
    return l.isClientSide
        ? null
        : createTickerHelper(t, Automatons.INFUSER_BE.get(), Infuser::tick);
  }

  @Override
  public InteractionResult use(
      BlockState state, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
    if (!(l.getBlockEntity(pos) instanceof Infuser infuser)) return InteractionResult.PASS;
    ItemStack held = p.getItemInHand(hand);
    if (!held.isEmpty() && !held.is(Automatons.EMPTY_CORE.get())) return InteractionResult.PASS;
    if (infuser.core.isEmpty() && held.isEmpty()) return InteractionResult.PASS;
    if (l.isClientSide) return InteractionResult.SUCCESS;
    // A main-hand PASS may be followed by an off-hand attempt, never two commits.
    if (infuser.core.isEmpty()) {
      if (held.is(Automatons.EMPTY_CORE.get())) {
        infuser.core = held.copyWithCount(1);
        held.shrink(1);
        infuser.donor = p.getUUID();
        infuser.changed();
      }
    } else if (held.isEmpty()) {
      if (!p.getUUID().equals(infuser.donor) && !p.hasPermissions(2)) {
        p.displayClientMessage(Component.literal("This core belongs to its donor"), true);
        return InteractionResult.CONSUME;
      }
      p.setItemInHand(hand, infuser.core);
      infuser.core = ItemStack.EMPTY;
      infuser.donor = null;
      infuser.transferring = false;
      infuser.changed();
    }
    return InteractionResult.CONSUME;
  }

  @Override
  public void onRemove(BlockState old, Level l, BlockPos p, BlockState next, boolean moving) {
    if (!old.is(next.getBlock()) && l.getBlockEntity(p) instanceof Infuser s) {
      ItemStack core = s.core;
      s.core = ItemStack.EMPTY;
      s.transferring = false;
      Containers.dropItemStack(l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, core);
    }
    super.onRemove(old, l, p, next, moving);
  }
}
