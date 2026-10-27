package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class TimberSweep {
  private static ClientLevel previousLevel;
  private static int ticks, cursor;

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    var mc = Minecraft.getInstance();
    if (event.phase != TickEvent.Phase.END) return;
    if (mc.level != previousLevel) {
      previousLevel = mc.level;
      ticks = cursor = 0;
    }
    if (mc.level == null || mc.isPaused() || ++ticks % TimberMaintenance.INTERVAL != 0) return;
    List<Timber> visible = new ArrayList<>();
    for (Entity entity : mc.level.entitiesForRendering())
      if (entity instanceof Timber t && !t.isRemoved()) visible.add(t);
    visible.sort(Comparator.comparingInt(Entity::getId));
    if (visible.isEmpty()) return;
    List<TimberMaintenance.Identity> batch = new ArrayList<>();
    for (int i = 0; i < Math.min(visible.size(), TimberMaintenance.BATCH); i++) {
      Timber t = visible.get(Math.floorMod(cursor++, visible.size()));
      batch.add(new TimberMaintenance.Identity(t.getId(), t.getUUID()));
    }
    Network.CHANNEL.sendToServer(
        new TimberMaintenance.Query(mc.level.dimension().location().toString(), batch));
  }

  public static void receive(CompoundTag n) {
    var level = Minecraft.getInstance().level;
    if (level == null || !level.dimension().location().toString().equals(n.getString("Dimension")))
      return;
    for (Tag tag : n.getList("Entries", Tag.TAG_COMPOUND)) {
      CompoundTag entry = (CompoundTag) tag;
      Entity entity = level.getEntity(entry.getInt("Id"));
      if (!(entity instanceof Timber t) || !t.getUUID().equals(entry.getUUID("UUID"))) continue;
      if (!entry.getBoolean("Present"))
        level.removeEntity(t.getId(), Entity.RemovalReason.DISCARDED);
      else t.applyAuditState(entry.getCompound("State"));
    }
  }
}
