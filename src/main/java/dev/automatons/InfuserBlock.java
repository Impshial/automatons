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
        box(0, 0, 0, 16, 3, 16),
        box(1, 3, 1, 15, 4, 15),
        box(3, 4, 3, 13, 11, 13),
        box(1, 11, 1, 15, 14, 15),
        box(2, 14, 2, 6, 16, 6),
        box(10, 14, 2, 14, 16, 6),
        box(2, 14, 10, 6, 16, 14),
        box(10, 14, 10, 14, 16, 14));
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
    // Vanilla retries the offhand when the main hand already holds a completed shard.
    // Never make that fallback silently retrieve another completed shard into the offhand.
    if (hand == InteractionHand.OFF_HAND
        && held.isEmpty()
        && (infuser.recovery.is(Automatons.INFUSED_LUMITE.get())
            || infuser.shards[corner(hit, p)].is(Automatons.INFUSED_LUMITE.get())))
      return InteractionResult.CONSUME;
    if (!held.isEmpty() && !held.is(Automatons.LUMITE_SHARD.get())) return InteractionResult.PASS;
    if (p.isSpectator() || !p.mayBuild() || !l.mayInteract(p, pos)) return InteractionResult.FAIL;
    if (l.isClientSide) return InteractionResult.SUCCESS;
    if (held.isEmpty() && !infuser.recovery.isEmpty()) {
      if (infuser.recoveryDonor != null
          && !p.getUUID().equals(infuser.recoveryDonor)
          && !p.hasPermissions(2)) return InteractionResult.CONSUME;
      p.setItemInHand(hand, infuser.recovery);
      infuser.recovery = ItemStack.EMPTY;
      infuser.recoveryDonor = null;
      infuser.changed();
      return InteractionResult.CONSUME;
    }
    int corner = corner(hit, p);
    if (infuser.shards[corner].isEmpty()) {
      if (held.isEmpty()) return InteractionResult.PASS;
      infuser.shards[corner] = held.copyWithCount(1);
      held.shrink(1);
      infuser.donors[corner] = p.getUUID();
      infuser.transferring[corner] = false;
      infuser.lastTransfer[corner] = -1000;
      infuser.changed();
    } else if (held.isEmpty()) {
      if (!ownsDonor(p, infuser.donors[corner]) && !p.hasPermissions(2)) {
        p.displayClientMessage(Component.literal("This shard belongs to its donor"), true);
        return InteractionResult.CONSUME;
      }
      p.setItemInHand(hand, infuser.shards[corner]);
      infuser.shards[corner] = ItemStack.EMPTY;
      infuser.donors[corner] = null;
      infuser.transferring[corner] = false;
      infuser.changed();
    } else p.displayClientMessage(Component.literal("That corner is occupied"), true);
    return InteractionResult.CONSUME;
  }

  private static boolean ownsDonor(Player player, java.util.UUID donor) {
    if (player.getUUID().equals(donor)) return true;
    if (donor == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
    var worker = Ledger.get(level.getServer()).workers.get(donor);
    return worker != null && worker.hasUUID("Owner") && player.getUUID().equals(worker.getUUID("Owner"));
  }

  public static int corner(BlockHitResult hit, Player player) {
    double x = hit.getLocation().x - hit.getBlockPos().getX();
    double z = hit.getLocation().z - hit.getBlockPos().getZ();
    // Center-line hits choose the player's side; each raised prong has an unambiguous quadrant.
    if (Math.abs(x - .5) < .001) x = player.getX() - hit.getBlockPos().getX();
    if (Math.abs(z - .5) < .001) z = player.getZ() - hit.getBlockPos().getZ();
    return (x >= .5 ? 1 : 0) | (z >= .5 ? 2 : 0);
  }

  @Override
  public void onRemove(BlockState old, Level l, BlockPos p, BlockState next, boolean moving) {
    if (!old.is(next.getBlock()) && l.getBlockEntity(p) instanceof Infuser s) {
      if (!l.isClientSide) {
        for (int i = 0; i < 4; i++) {
          ItemStack stack = s.shards[i];
          s.shards[i] = ItemStack.EMPTY;
          s.transferring[i] = false;
          Containers.dropItemStack(l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, stack);
        }
        ItemStack recovered = s.recovery;
        s.recovery = ItemStack.EMPTY;
        Containers.dropItemStack(l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, recovered);
      }
    }
    super.onRemove(old, l, p, next, moving);
  }
}
