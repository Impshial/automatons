package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A generation token consumes each portable identity once. The ledger owns current links. */
public final class WorkerIdentity {
  public static CompoundTag portable(Lumberton w) {
    CompoundTag n = new CompoundTag();
    n.putInt("Version", 3);
    n.putUUID("Id", w.getUUID());
    if (w.owner != null) n.putUUID("Owner", w.owner);
    if (w.homeId != null) n.putUUID("HomeId", w.homeId);
    if (w.home != null) n.putLong("Home", w.home.asLong());
    n.putString("HomeDimension", w.homeDimension);
    if (w.totemId != null) n.putUUID("TotemId", w.totemId);
    if (w.totem != null) n.putLong("Totem", w.totem.asLong());
    n.putString("TotemDimension", w.totemDimension);
    n.putInt("Job", w.job());
    n.putFloat("Health", w.getHealth());
    if (w.hasCustomName()) WorkerNames.writeName(n, w.getCustomName().getString());
    n.putBoolean("ShowNames", w.showNames());
    n.put("ForestryAccess", w.forestryAccess.save());
    return n;
  }

  public static void apply(Lumberton w, CompoundTag n) {
    w.setUUID(n.getUUID("Id"));
    w.owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    w.homeId = n.hasUUID("HomeId") ? n.getUUID("HomeId") : null;
    w.home = n.contains("Home") ? BlockPos.of(n.getLong("Home")) : null;
    w.homeDimension = n.getString("HomeDimension");
    w.totemId = n.hasUUID("TotemId") ? n.getUUID("TotemId") : null;
    w.totem = n.contains("Totem") ? BlockPos.of(n.getLong("Totem")) : null;
    w.totemDimension = n.getString("TotemDimension");
    w.setJob(n.getInt("Job"));
    w.setHealth(Math.max(.1f, Math.min(w.getMaxHealth(), n.getFloat("Health"))));
    if (n.contains("Name")) w.setCustomName(Component.Serializer.fromJson(n.getString("Name")));
    w.showNames(!n.contains("ShowNames") || n.getBoolean("ShowNames"));
    w.forestryAccess.load(n.getCompound("ForestryAccess"));
    w.forestryAccess.finish();
    w.authorized = false;
  }

  public static CompoundTag record(Lumberton w) {
    return Ledger.get(w.getServer()).workers.get(w.getUUID());
  }

  public static void update(Lumberton w, String state) {
    if (w.getServer() == null) return;
    WorkerNames.sync(w);
    Ledger ledger = Ledger.get(w.getServer());
    CompoundTag old = ledger.workers.get(w.getUUID());
    CompoundTag n = portable(w);
    n.putLong("Generation", old == null ? 0 : old.getLong("Generation"));
    n.putString("State", state);
    n.putString("Dimension", w.level().dimension().location().toString());
    n.putLong("Position", w.blockPosition().asLong());
    n.putString("Status", w.status());
    n.putBoolean("Authorized", w.authorized);
    n.putInt("Alerts", w.job() != 0 && w.authorized ? w.alerts : 0);
    if (w.machineId != null) n.putUUID("Machine", w.machineId);
    n.putBoolean(
        "ToolsReady",
        w.job() == WorkerJob.OPERATORTON || Inventories.tool(w.job(), w.activeTool()));
    n.putInt("Torches", w.mining.carried(net.minecraft.world.item.Items.TORCH));
    if (!n.equals(old)) {
      ledger.workers.put(w.getUUID(), n);
      ledger.setDirty();
    }
  }

  public static ItemStack assemble(Player p, Station home) {
    Lumberton w = Automatons.LUMBERTON.get().create(p.level());
    w.owner = p.getUUID();
    w.homeId = home.identity;
    w.home = home.getBlockPos();
    w.homeDimension = p.level().dimension().location().toString();
    w.setJob(0);
    w.authorized = false;
    home.registerHome();
    update(w, "Packed");
    CompoundTag n = record(w).copy();
    home.workers.add(w.getUUID());
    home.changed();
    return item(n);
  }

  public static ItemStack item(CompoundTag n) {
    ItemStack stack = new ItemStack(Automatons.AUTOMATON_ITEM.get());
    stack.getOrCreateTag().put("Automaton", n.copy());
    return stack;
  }

  public static boolean pack(Lumberton w, Player player) {
    if (!(w.level() instanceof ServerLevel l)
        || !w.isAlive()
        || w.isRemoved()
        || w.packing
        || !w.owns(player)) return false;
    CompoundTag previous = record(w);
    if (previous != null && !previous.getString("State").equals("Deployed")) return false;
    WorkerNames.sync(w);
    w.packing = true;
    try {
      CompoundTag packed = portable(w);
      packed.putString("State", "Packed");
      packed.putLong("Generation", previous == null ? 1 : previous.getLong("Generation") + 1);
      packed.putInt("Alerts", 0);
      packed.putString("Status", "Packed");
      List<ItemStack> stacks = new ArrayList<>();
      for (int i = 0; i < 3; i++)
        if (!w.tools.getStackInSlot(i).isEmpty()) stacks.add(w.tools.getStackInSlot(i).copy());
      for (int i = 0; i < w.cargo.getSlots(); i++)
        if (!w.cargo.getStackInSlot(i).isEmpty()) stacks.add(w.cargo.getStackInSlot(i).copy());
      stacks.add(item(packed));
      List<ItemEntity> drops = new ArrayList<>();
      for (ItemStack stack : stacks) {
        ItemEntity drop = new ItemEntity(l, w.getX(), w.getY() + .2, w.getZ(), stack);
        drop.setDefaultPickUpDelay();
        if (!l.addFreshEntity(drop)) {
          drops.forEach(ItemEntity::discard);
          return false;
        }
        drops.add(drop);
      }
      for (int i = 0; i < 3; i++) w.tools.setStackInSlot(i, ItemStack.EMPTY);
      for (int i = 0; i < w.cargo.getSlots(); i++) w.cargo.setStackInSlot(i, ItemStack.EMPTY);
      w.suspendWork();
      Ledger ledger = Ledger.get(l.getServer());
      ledger.workers.put(w.getUUID(), packed);
      ledger.setDirty();
      w.discard();
      return true;
    } finally {
      w.packing = false;
    }
  }

  public static void unbind(ServerLevel level, UUID id, UUID totem) {
    Ledger ledger = Ledger.get(level.getServer());
    CompoundTag n = ledger.workers.get(id);
    for (ServerLevel l : level.getServer().getAllLevels())
      if (l.getEntity(id) instanceof Lumberton w && totem.equals(w.totemId)) {
        w.unassign();
        return;
      }
    if (n == null || !n.hasUUID("TotemId") || !totem.equals(n.getUUID("TotemId"))) return;
    n.remove("TotemId");
    n.remove("Totem");
    n.remove("TotemDimension");
    ledger.setDirty();
  }
}
