package dev.automatons;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.network.NetworkHooks;

public class StationBlock extends BaseEntityBlock {
  private final boolean totem;

  public StationBlock(boolean totem) {
    super(
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5f)
            .sound(totem ? SoundType.WOOD : SoundType.STONE)
            .noOcclusion()
            .lightLevel(s -> totem ? 10 : 0));
    this.totem = totem;
  }

  @Override
  public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(
      BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.entity.Mob mob) {
    // Thin decorative bases otherwise look walkable to the node evaluator but trap wide mobs.
    return net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED;
  }

  @Override
  public RenderShape getRenderShape(BlockState s) {
    return RenderShape.MODEL;
  }

  @Override
  public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
    return totem
        ? Shapes.or(box(3, 0, 3, 13, 3, 13), box(5, 3, 5, 11, 14, 11), box(4, 14, 4, 12, 16, 12))
        : Shapes.or(box(1, 0, 1, 15, 3, 15), box(4, 3, 4, 12, 10, 12), box(0, 10, 0, 16, 13, 16));
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
    return new Station(p, s);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level l, BlockState s, BlockEntityType<T> t) {
    return l.isClientSide ? null : createTickerHelper(t, Automatons.STATION.get(), Station::tick);
  }

  @Override
  public void setPlacedBy(Level l, BlockPos p, BlockState s, LivingEntity who, ItemStack stack) {
    if (!l.isClientSide
        && who instanceof Player
        && l.getBlockEntity(p) instanceof Station station) {
      station.owner = who.getUUID();
      station.changed();
      station.registerHome();
    }
  }

  @Override
  public InteractionResult use(
      BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
    if (player.getItemInHand(hand).getItem() instanceof WandItem) return InteractionResult.PASS;
    if (!l.isClientSide && l.getBlockEntity(p) instanceof Station station)
      NetworkHooks.openScreen(
          (ServerPlayer) player,
          station,
          b -> {
            b.writeBoolean(false);
            b.writeBlockPos(p);
          });
    return InteractionResult.sidedSuccess(l.isClientSide);
  }

  @Override
  public void onRemove(
      BlockState old, Level l, BlockPos p, BlockState replacement, boolean moving) {
    if (!old.is(replacement.getBlock()) && l.getBlockEntity(p) instanceof Station station) {
      station.destroyed();
      for (int i = 0; i < station.inventory.getSlots(); i++) {
        Containers.dropItemStack(
            l, p.getX() + .5, p.getY() + .5, p.getZ() + .5, station.inventory.getStackInSlot(i));
        station.inventory.setStackInSlot(i, ItemStack.EMPTY);
      }
    }
    super.onRemove(old, l, p, replacement, moving);
  }
}
