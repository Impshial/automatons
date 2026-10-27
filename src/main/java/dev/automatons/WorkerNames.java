package dev.automatons;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Canonical display spelling and home visibility, shared by every job and representation. */
public final class WorkerNames {
  public static String recordName(CompoundTag n) {
    if (n == null) return "";
    if (n.contains("PersonalName")) return n.getString("PersonalName");
    try {
      Component name = Component.Serializer.fromJson(n.getString("Name"));
      return name == null ? "" : name.getString();
    } catch (Exception ignored) {
      return "";
    }
  }

  public static void writeName(CompoundTag n, String name) {
    n.putString("PersonalName", name);
    n.putString("Name", Component.Serializer.toJson(Component.literal(name)));
  }

  public static String record(
      Ledger ledger, UUID id, CompoundTag data, net.minecraft.util.RandomSource random) {
    String before = ledger.names.name(id),
        name = ledger.names.resolve(id, recordName(data), random);
    if (before == null || !name.equals(recordName(data))) {
      writeName(data, name);
      ledger.setDirty();
    }
    return name;
  }

  public static void sync(Lumberton w) {
    if (!(w.level() instanceof ServerLevel l)) return;
    Ledger ledger = Ledger.get(l.getServer());
    String before = ledger.names.name(w.getUUID());
    CompoundTag saved = ledger.workers.get(w.getUUID());
    String legacy =
        saved == null
            ? (w.hasCustomName() ? w.getCustomName().getString() : "")
            : recordName(saved);
    String name = ledger.names.resolve(w.getUUID(), legacy, l.random);
    if (before == null) ledger.setDirty();
    if (!w.hasCustomName() || !w.getCustomName().getString().equals(name))
      w.setCustomName(Component.literal(name));
    w.setCustomNameVisible(true);
    if (w.homeId != null && ledger.homeNames.containsKey(w.homeId))
      w.showNames(ledger.homeNames.get(w.homeId));
    if (saved != null && !name.equals(recordName(saved))) {
      writeName(saved, name);
      ledger.setDirty();
    }
  }

  @SubscribeEvent
  public static void nameTag(PlayerInteractEvent.EntityInteract event) {
    if (!(event.getTarget() instanceof Lumberton w)
        || !event.getItemStack().is(Items.NAME_TAG)
        || !event.getItemStack().hasCustomHoverName()) return;
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.SUCCESS);
    if (!(w.level() instanceof ServerLevel l)) return;
    var player = event.getEntity();
    if (!w.owns(player)) {
      event.setCancellationResult(InteractionResult.FAIL);
      return;
    }
    sync(w);
    Ledger ledger = Ledger.get(l.getServer());
    String requested = event.getItemStack().getHoverName().getString();
    if (NameRegistry.key(requested).equals(NameRegistry.key(ledger.names.name(w.getUUID()))))
      return;
    if (!ledger.names.rename(w.getUUID(), requested)) {
      player.displayClientMessage(
          Component.literal("That Automaton name is already reserved or invalid"), true);
      event.setCancellationResult(InteractionResult.FAIL);
      return;
    }
    ledger.setDirty();
    sync(w);
    WorkerIdentity.update(w, "Deployed");
    w.setPersistenceRequired();
    if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);
  }
}
