package dev.automatons;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class WorldEvents {
  @SubscribeEvent
  public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
    NameRegistry.catalog();
    Ledger.get(event.getServer());
  }

  @SubscribeEvent
  public static void reload(net.minecraftforge.event.AddReloadListenerEvent e) {
    e.addListener(new TreeSpecies());
  }

  private static long tick;
  @SubscribeEvent
  public static void graves(TickEvent.LevelTickEvent e) {
    if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel l && l.getGameTime() % 20 == 0)
      Graves.get(l).retry(l);
  }
  private static int spent;
  private static int paths, conversions, plans;
  private static final java.util.Set<Station> loadedStations =
      java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

  public static void stationLoaded(Station s) {
    loadedStations.add(s);
  }

  public static void changedNear(ServerLevel level, net.minecraft.core.BlockPos pos) {
    for (Station s : loadedStations)
      if (!s.isRemoved() && s.getLevel() == level) {
        if (Math.abs(pos.getX() - s.getBlockPos().getX()) <= s.workRange() + 12
            && Math.abs(pos.getZ() - s.getBlockPos().getZ()) <= s.workRange() + 12)
          s.terrainChanged(pos);
        if (Storage.inRange(s, pos)) s.refreshStorage();
      }
  }

  public static void protectionChanged(ServerLevel level) {
    for (Station s : loadedStations)
      if (s.getLevel() == level && !s.isRemoved()) s.invalidateTrees();
  }

  public static boolean pathBudget() {
    return paths++ < 32;
  }

  public static boolean planBudget() {
    return plans++ < 2;
  }

  public static boolean conversionBudget() {
    return conversions++ < 1;
  }

  private static final java.util.ArrayDeque<Station> scanners = new java.util.ArrayDeque<>();
  private static final java.util.Set<java.util.UUID> queued = new java.util.HashSet<>();

  public static void requestScan(Station station) {
    if (queued.add(station.identity)) scanners.addLast(station);
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
    scanners.clear();
    loadedStations.clear();
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
      paths = 0;
      conversions = 0;
      plans = 0;
      return;
    }
    int count = scanners.size();
    while (count-- > 0 && spent < Balance.SCAN_BUDGET.get() - 80) {
      Station station = scanners.removeFirst();
      queued.remove(station.identity);
      if (!station.isRemoved()
          && (!station.workers.isEmpty() || !station.guards.isEmpty()
              || station.worker != null
              || station.project() != null && !station.project().ready)
          && station.getLevel() instanceof ServerLevel l
          && l.hasChunkAt(station.getBlockPos())) station.scanWork(l);
    }
  }

  @SubscribeEvent
  public static void grown(net.minecraftforge.event.level.SaplingGrowTreeEvent e) {
    if (e.getLevel() instanceof ServerLevel l) changedNear(l, e.getPos());
  }

  @SubscribeEvent
  public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
    if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof Lumberton w))
      return;
    for (ServerLevel other : level.getServer().getAllLevels()) {
      var existing = other.getEntity(w.getUUID());
      if (existing != null && existing != w && !existing.isRemoved()) {
        e.setCanceled(true);
        return;
      }
    }
    var record = Ledger.get(level.getServer()).workers.get(w.getUUID());
    if (record != null
        && !w.deploying
        && record.getString("State").equals("Deployed")
        && !record.hasUUID("TotemId")) {
      w.totemId = null;
      w.totem = null;
      w.totemDimension = "";
    }
    if (record != null
        && !w.deploying
        && (record.getString("State").equals("Packed")
            || record.getString("State").equals("Destroyed"))) e.setCanceled(true);
    if (!e.isCanceled()) WorkerNames.sync(w);
  }

  @SubscribeEvent
  public static void leave(net.minecraftforge.event.entity.EntityLeaveLevelEvent e) {
    if (e.getLevel() instanceof ServerLevel l && e.getEntity() instanceof Lumberton w) {
      w.hunting.release();
      w.ranching.release();
      w.clearChop();
      w.farming.release();
      if (w.getRemovalReason() != null && w.getRemovalReason().shouldDestroy()) MachineOperator.leave(w);
      if (w.getRemovalReason() != null && w.getRemovalReason().shouldDestroy())
        w.building.release();
      w.mining.release();
      WorldData.get(l).release(w.getUUID());
    }
  }

  @SubscribeEvent
  public static void workerAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
    if (!(event.getSource().getEntity() instanceof Lumberton worker)) return;
    if (worker.job() == WorkerJob.HUNTERTON) {
      Station site = worker.station();
      if (site == null || !(event.getEntity() instanceof net.minecraft.world.entity.animal.Animal animal)
          || !Hunting.eligible(animal, site)) event.setCanceled(true);
    } else if (worker.isGuard() && !Threats.supported(event.getEntity())) event.setCanceled(true);
  }

  @SubscribeEvent
  public static void trample(net.minecraftforge.event.level.BlockEvent.FarmlandTrampleEvent e) {
    if (e.getEntity() instanceof Lumberton) e.setCanceled(true);
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent e) {
    if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) {
      Network.protections(p);
      Lumina.sync(p);
    }
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
    if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p) {
      Network.protections(p);
      Lumina.sync(p);
    }
  }
}
