package dev.automatons;

import java.util.List;
import java.util.UUID;
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
  @Override
  public void initializeClient(
      java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions>
          consumer) {
    consumer.accept(
        new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
          private dev.automatons.client.PackedAutomatonRenderer renderer;

          public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) renderer = new dev.automatons.client.PackedAutomatonRenderer();
            return renderer;
          }
        });
  }

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
      // Unindexed legacy items are adopted once; the generation ledger then rejects copies.
      if (data == null && stored.hasUUID("Owner") && stored.getInt("Version") <= 2) {
        data = stored.copy();
        data.putString("State", "Packed");
      }
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
            worker.getDisplayName().getString()
                + " placed. Choose a job, add tools, then assign a totem or Start Work."),
        true);
    return InteractionResult.CONSUME;
  }

  @Override
  public void appendHoverText(ItemStack s, Level l, List<Component> lines, TooltipFlag flag) {
    CompoundTag n = s.hasTag() ? s.getTag().getCompound("Automaton") : new CompoundTag();
    String name = WorkerNames.recordName(n);
    if (!name.isEmpty()) lines.add(Component.literal(name));
    lines.add(Component.literal("Job: " + WorkerJob.name(n.getInt("Job"))));
    lines.add(Component.literal("Place on solid ground; starts paused"));
    if (n.hasUUID("HomeId"))
      lines.add(Component.literal("Home: " + WorkMenu.xyz(BlockPos.of(n.getLong("Home")))));
    if (n.hasUUID("TotemId"))
      lines.add(Component.literal("Saved totem link (checked on placement)"));
  }

  @Override
  public Component getName(ItemStack stack) {
    String name =
        stack.hasTag() ? WorkerNames.recordName(stack.getTag().getCompound("Automaton")) : "";
    return name.isEmpty() ? super.getName(stack) : Component.literal(name);
  }

  @Override
  public void inventoryTick(
      ItemStack stack,
      Level level,
      net.minecraft.world.entity.Entity holder,
      int slot,
      boolean selected) {
    if (!(level instanceof ServerLevel l) || holder.tickCount % 20 != 0 || !stack.hasTag()) return;
    CompoundTag stored = stack.getTag().getCompound("Automaton");
    if (!stored.hasUUID("Id")) return;
    Ledger ledger = Ledger.get(l.getServer());
    UUID id = stored.getUUID("Id");
    CompoundTag record = ledger.workers.get(id);
    if (record == null && stored.hasUUID("Owner") && stored.getInt("Version") <= 2) {
      record = stored.copy();
      record.putString("State", "Packed");
      ledger.workers.put(id, record);
      ledger.setDirty();
    }
    if (record == null) return;
    String name = WorkerNames.record(ledger, id, record, l.random);
    WorkerNames.writeName(stored, name);
  }
}
