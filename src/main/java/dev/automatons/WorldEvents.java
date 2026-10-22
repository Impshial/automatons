package dev.automatons;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class WorldEvents {
  @SubscribeEvent
  public static void reload(net.minecraftforge.event.AddReloadListenerEvent e) {
    e.addListener(new TreeSpecies());
  }

  private static long tick;
  private static int spent;
  private static final java.util.ArrayDeque<Station> scanners = new java.util.ArrayDeque<>();
  private static final java.util.Set<java.util.UUID> queued = new java.util.HashSet<>();

  public static void requestScan(Station station) {
    if (queued.add(station.identity)) scanners.addLast(station);
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
    scanners.clear();
    queued.clear();
    spent = 0;
  }

  public static boolean budget(int amount) {
    if (spent + amount > Balance.SCAN_BUDGET.get()) return false;
    spent += amount;
    return true;
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent e) {
    if (e.phase == TickEvent.Phase.START) {
      tick++;
      spent = 0;
      return;
    }
    int count = scanners.size();
    while (count-- > 0 && spent < Balance.SCAN_BUDGET.get() - 80) {
      Station station = scanners.removeFirst();
      queued.remove(station.identity);
      if (!station.isRemoved()
          && station.worker != null
          && station.getLevel() instanceof ServerLevel l
          && l.hasChunkAt(station.getBlockPos())) station.scanWork(l);
    }
  }

  @SubscribeEvent
  public static void placed(BlockEvent.EntityPlaceEvent e) {
    if (e.getLevel() instanceof ServerLevel l
        && e.getEntity() instanceof Player
        && e.getPlacedBlock().is(BlockTags.LOGS)) {
      WorldData.get(l).placedLogs.add(e.getPos().asLong());
      WorldData.get(l).setDirty();
    }
  }

  @SubscribeEvent
  public static void broken(BlockEvent.BreakEvent e) {
    if (!e.isCanceled()
        && e.getLevel() instanceof ServerLevel l
        && !(e.getPlayer() instanceof net.minecraftforge.common.util.FakePlayer)) {
      WorldData.get(l).placedLogs.remove(e.getPos().asLong());
      WorldData.get(l).setDirty();
    }
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent e) {
    if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) Network.protections(p);
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
    if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) Network.protections(p);
  }
}
