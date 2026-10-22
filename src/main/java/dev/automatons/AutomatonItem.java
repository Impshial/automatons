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

public final class AutomatonItem extends Item {
  public AutomatonItem() {
    super(new Properties().stacksTo(1));
  }

  @Override
  public InteractionResult useOn(UseOnContext c) {
    Player p = c.getPlayer();
    if (p == null) return InteractionResult.PASS;
    if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
    ServerLevel l = (ServerLevel) c.getLevel();
    ItemStack stack = c.getItemInHand();
    CompoundTag stored = stack.getOrCreateTag().getCompound("Automaton");
    Ledger ledger = Ledger.get(l.getServer());
    CompoundTag data;
    if (!stored.hasUUID("Id")) {
      if (!p.getAbilities().instabuild) return InteractionResult.FAIL;
      Lumberton fresh = Automatons.LUMBERTON.get().create(l);
      fresh.owner = p.getUUID();
      fresh.setJob(0);
      data = WorkerIdentity.portable(fresh);
      data.putString("State", "Packed");
    } else {
      data = ledger.workers.get(stored.getUUID("Id"));
      if (data == null
          || !data.getString("State").equals("Packed")
          || data.getLong("Generation") != stored.getLong("Generation")) {
        p.displayClientMessage(
            Component.literal("This Automaton is already deployed or its item is outdated"), true);
        return InteractionResult.FAIL;
      }
    }
    if (!data.hasUUID("Owner")
        || (!data.getUUID("Owner").equals(p.getUUID()) && !p.hasPermissions(2)))
      return InteractionResult.FAIL;
    for (ServerLevel dimension : l.getServer().getAllLevels())
      if (dimension.getEntity(data.getUUID("Id")) != null) return InteractionResult.FAIL;
    BlockPos pos = c.getClickedPos().relative(c.getClickedFace());
    if (l.isOutsideBuildHeight(pos)
        || l.isOutsideBuildHeight(pos.above())
        || !l.hasChunkAt(pos)
        || !l.getBlockState(pos.below()).isFaceSturdy(l, pos.below(), Direction.UP))
      return InteractionResult.FAIL;
    Lumberton worker = Automatons.LUMBERTON.get().create(l);
    WorkerIdentity.apply(worker, data);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, p.getYRot(), 0);
    if (!l.getWorldBorder().isWithinBounds(worker.getBoundingBox())
        || !l.noCollision(worker)
        || !l.getEntities(worker, worker.getBoundingBox()).isEmpty()
        || !l.getFluidState(pos).isEmpty()) {
      p.displayClientMessage(Component.literal("Clear a safe space for the Automaton"), true);
      return InteractionResult.FAIL;
    }
    worker.deploying = true;
    if (!l.addFreshEntity(worker)) return InteractionResult.FAIL;
    worker.deploying = false;
    WorkerIdentity.update(worker, "Deployed");
    stack.shrink(1);
    p.displayClientMessage(
        Component.literal(
            "Automaton placed. Choose a job, add tools, then assign a totem or Start Work."),
        true);
    return InteractionResult.CONSUME;
  }

  @Override
  public void appendHoverText(ItemStack s, Level l, List<Component> lines, TooltipFlag flag) {
    CompoundTag n = s.hasTag() ? s.getTag().getCompound("Automaton") : new CompoundTag();
    lines.add(Component.literal(n.getInt("Job") == 1 ? "Job: Lumberton" : "Job: Unassigned"));
    lines.add(Component.literal("Place on solid ground; starts paused"));
    if (n.hasUUID("HomeId"))
      lines.add(Component.literal("Home: " + WorkMenu.xyz(BlockPos.of(n.getLong("Home")))));
    if (n.hasUUID("TotemId"))
      lines.add(Component.literal("Saved totem link (checked on placement)"));
  }
}
