package dev.automatons;

import java.util.List;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** A packed controller, with all paid contents retained. */
public final class MachineItem extends Item {
  public MachineItem() {
    super(new Properties().stacksTo(1));
  }

  @Override
  public InteractionResult useOn(UseOnContext c) {
    Player p = c.getPlayer();
    if (p == null || !p.mayBuild() || p.isSpectator()) return InteractionResult.FAIL;
    if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
    BlockPos pos = c.getClickedPos().relative(c.getClickedFace());
    String error = place((ServerLevel) c.getLevel(), p, c.getItemInHand(), pos);
    if (!error.isEmpty()) p.displayClientMessage(Component.literal(error), true);
    return error.isEmpty() ? InteractionResult.CONSUME : InteractionResult.FAIL;
  }

  static String place(ServerLevel level, Player p, ItemStack stack, BlockPos pos) {
    if (!p.mayBuild() || p.isSpectator() || !stack.is(MachineParts.PACKED.get()))
      return "Cannot place here";
    CompoundTag data = stack.hasTag() ? stack.getTag().getCompound("Machine") : new CompoundTag();
    if (!data.hasUUID("Owner") || !data.getUUID("Owner").equals(p.getUUID()))
      return "This Machine belongs to someone else";
    Machine m = MachineParts.ENTITY.get().create(level);
    m.readAdditionalSaveData(data.copy());
    boolean relocated =
        !pos.equals(m.grid)
            || !data.getString("Dimension").equals(level.dimension().location().toString());
    m.grid = pos.immutable();
    m.operator = null;
    // Keep the program itself. A physical return route must never cross a relocation.
    if (relocated) m.job.cancel();
    m.snap();
    for (BlockPos cell : MachineTerrain.cells(m, pos, m.heading, m.attachment())) {
      if (!level.hasChunkAt(cell)
          || level.isOutsideBuildHeight(cell)
          || !level.getWorldBorder().isWithinBounds(cell)
          || !level.mayInteract(p, cell)
          || !Permissions.allowed(level, cell, p.getUUID(), Permissions.Action.PROCESS))
        return "Placement is protected or outside the world";
      if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
          || !level.getFluidState(cell).isEmpty())
        return "Clear space for the Machine and its attachment";
      if (!level
          .getEntities(
              m,
              new AABB(cell).deflate(.01),
              e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.item.ItemEntity))
          .isEmpty()) return "Move away from the placement area";
    }
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        BlockPos floor = pos.offset(x, -1, z);
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP))
          return "The Machine needs a solid 3 × 3 floor";
      }
    m.status = relocated ? "Parked — start a new job here" : "Parked — saved job paused";
    m.sync();
    if (!level.addFreshEntity(m)) return "Placement was denied";
    stack.shrink(1); // Even creative packing must not duplicate the stored cargo or fuel.
    return "";
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(Component.literal("Right-click clear ground to reform The Machine"));
    lines.add(Component.literal("Keeps cargo, equipment, program and fuel; starts parked"));
  }
}
