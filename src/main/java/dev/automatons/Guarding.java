package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Protection is independent of the production assignment and its six-place roster. */
public final class Guarding {
  private final Lumberton worker;
  public UUID site;
  public BlockPos sitePos;
  public boolean circuit = true;
  public int progress;
  private boolean initialized;
  private long nextRoute;
  private List<BlockPos> route = List.of();
  private List<UUID> peers = List.of();
  private boolean recalledRoute;
  private long restrictionsAt = -1;
  private final List<AABB> exclusions = new ArrayList<>();
  public Guarding(Lumberton worker) { this.worker = worker; }

  public Station assigned() {
    if (site == null || sitePos == null || !worker.level().hasChunkAt(sitePos)) return null;
    return worker.level().getBlockEntity(sitePos) instanceof Station station && station.identity.equals(site)
        && Objects.equals(worker.owner, station.owner) && Objects.equals(worker.homeId, station.logisticsHome) ? station : null;
  }
  public String protect(Station station) {
    if (!worker.isGuard()) return "Only a Guardington can take a protection assignment";
    if (!station.isTotem() || worker.owner == null || !worker.owner.equals(station.owner)
        || station.logisticsHome != null && !station.logisticsHome.equals(worker.homeId)) return "Choose a totem in your own network";
    clear();
    site = station.identity; sitePos = station.getBlockPos(); circuit = false;
    station.guards.add(worker.getUUID()); station.logisticsHome = worker.homeId; station.changed();
    worker.authorized = true; WorkerIdentity.update(worker, "Deployed");
    return "Protection assigned";
  }
  public void clear() {
    Station previous = assigned();
    if (previous != null) { previous.guards.remove(worker.getUUID()); previous.changed(); }
    site = null; sitePos = null; circuit = true; nextRoute = 0; initialized = false; route = List.of();
  }
  public void mode(boolean circuit) {
    this.circuit = circuit; nextRoute = 0; initialized = false;
    WorkerIdentity.update(worker, "Deployed");
  }
  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    if (site != null) n.putUUID("Site", site);
    if (sitePos != null) n.putLong("Position", sitePos.asLong());
    n.putBoolean("Circuit", circuit); n.putInt("Progress", progress); n.putBoolean("Initialized", initialized);
    return n;
  }
  public void load(CompoundTag n) {
    site = n.hasUUID("Site") ? n.getUUID("Site") : null;
    sitePos = n.contains("Position") ? BlockPos.of(n.getLong("Position")) : null;
    circuit = !n.contains("Circuit") || n.getBoolean("Circuit");
    progress = Math.max(0, n.getInt("Progress")); initialized = n.getBoolean("Initialized");
    nextRoute = 0; route = List.of(); peers = List.of();
  }

  private List<BuildingProject> sites() {
    return WorldData.get((ServerLevel)worker.level()).buildings.values().stream()
        .filter(p -> Objects.equals(p.owner, worker.owner) && Objects.equals(p.home, worker.homeId))
        .sorted(Comparator.comparing(p -> p.totem)).toList();
  }
  public Station rechargePoint() {
    Station assigned = assigned();
    if (!circuit && assigned != null) return assigned;
    for (BuildingProject project : sites().stream()
        .sorted(Comparator.comparingDouble(p -> worker.distanceToSqr(Vec3.atCenterOf(p.anchor)))).toList())
      if (worker.level().hasChunkAt(project.anchor) && worker.level().getBlockEntity(project.anchor) instanceof Station station
          && worker.canReach(project.entrance((ServerLevel)worker.level()))) return station;
    return null;
  }

  public boolean surfaceSafe(BlockPos pos) {
    ServerLevel level = (ServerLevel)worker.level();
    if (!level.hasChunkAt(pos) || !MiningRules.supported(level, pos)
        || forbidden(pos) || level.getBlockCollisions(worker,
            worker.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(pos))).iterator().hasNext()) return false;
    return true;
  }
  public boolean forbidden(BlockPos pos) {
    if (!(worker.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) return true;
    if (WorldData.get(level).farmProtected(pos)) return true;
    if (level.getGameTime() >= restrictionsAt) {
      restrictionsAt = level.getGameTime() + 100; exclusions.clear();
      for (BuildingProject project : WorldData.get(level).buildings.values()) {
        SiteLayout layout = new SiteLayout(project, level);
        if (project.purpose == WorksitePurpose.RANCH) exclusions.add(layout.pen());
        if (project.purpose == WorksitePurpose.QUARRY) {
          AABB q = layout.quarry();
          exclusions.add(new AABB(q.minX, level.getMinBuildHeight(), q.minZ, q.maxX, level.getMaxBuildHeight(), q.maxZ));
          BlockPos shaft = layout.shaft();
          exclusions.add(new AABB(shaft.getX(), level.getMinBuildHeight(), shaft.getZ(), shaft.getX() + 1, level.getMaxBuildHeight(), shaft.getZ() + 1));
        }
      }
    }
    return exclusions.stream().anyMatch(b -> b.contains(Vec3.atCenterOf(pos)));
  }
  private void addSurface(List<BlockPos> points, int x, int z, int baseline) {
    ServerLevel level = (ServerLevel)worker.level();
    if (!level.hasChunkAt(new BlockPos(x, baseline, z))) return;
    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    BlockPos feet = new BlockPos(x, y, z);
    if (Math.abs(y - baseline) <= 4 && surfaceSafe(feet) && !points.contains(feet)) points.add(feet);
  }
  private void ring(List<BlockPos> points, BlockPos center, int radius) {
    for (int i = 0; i < 8; i++) {
      double a = i * Math.PI / 4;
      addSurface(points, center.getX() + (int)Math.round(Math.cos(a) * radius),
          center.getZ() + (int)Math.round(Math.sin(a) * radius), center.getY());
    }
  }
  public void tick(boolean recalled) {
    ServerLevel level = (ServerLevel)worker.level();
    Station assigned = assigned();
    if (site != null && sitePos != null && level.hasChunkAt(sitePos) && assigned == null) clear();
    if (recalled && worker.home == null) { worker.state("Home missing"); return; }
    Mob threat = worker.threats.nearest(24);
    if (threat != null && surfaceSafe(threat.blockPosition())
        && Math.abs(threat.getY() - worker.getY()) < 4
        && (!recalled || threat.blockPosition().distSqr(worker.home) <= 100)
        && (recalled || circuit || assigned == null || threat.blockPosition().distSqr(assigned.getBlockPos()) <= Math.pow(assigned.workRange() + 4, 2))) {
      worker.combat.fight(threat); return;
    }
    if (!recalled) {
      Station supplies = rechargePoint();
      if (supplies != null) {
        if (!has(SwordItem.class) && WorkSupplies.equipment(worker, supplies, s -> s.getItem() instanceof SwordItem)) return;
        if (!has(BowItem.class) && WorkSupplies.equipment(worker, supplies, s -> s.getItem() instanceof BowItem)) return;
        if (has(BowItem.class) && worker.combat.arrowSlot() < 0 && WorkSupplies.take(worker, supplies, Items.ARROW, 32)) return;
        if (WorkSupplies.deposit(worker, supplies, Map.of(Items.ARROW, 32, Luminall.BOTTLE.get(), 1))) return;
      }
    }
    if (level.getGameTime() >= nextRoute || recalledRoute != recalled) {
      nextRoute = level.getGameTime() + 200; recalledRoute = recalled;
      List<BlockPos> updated = new ArrayList<>();
      if (recalled) ring(updated, worker.home, 8);
      else if (!circuit && assigned != null) ring(updated, assigned.getBlockPos(), assigned.workRange());
      else for (BuildingProject project : sites()) {
        AABB bounds = project.bounds(level);
        for (int[] corner : new int[][] {{(int)bounds.minX - 2, (int)bounds.minZ - 2}, {(int)bounds.maxX + 1, (int)bounds.minZ - 2},
            {(int)bounds.maxX + 1, (int)bounds.maxZ + 1}, {(int)bounds.minX - 2, (int)bounds.maxZ + 1}})
          addSurface(updated, corner[0], corner[1], project.anchor.getY());
      }
      if (updated.isEmpty() && worker.home != null) ring(updated, worker.home, recalled ? 8 : 14);
      List<UUID> roster = Ledger.get(level.getServer()).workers.keySet().stream()
          .filter(id -> level.getEntity(id) instanceof Lumberton guard && guard.isAlive() && guard.isGuard()
              && Objects.equals(guard.owner, worker.owner) && Objects.equals(guard.homeId, worker.homeId)
              && (recalled || guard.guarding.circuit == circuit && (circuit || Objects.equals(guard.guarding.site, site))))
          .sorted().toList();
      if (!initialized || !peers.isEmpty() && !roster.equals(peers)) {
        int index = Math.max(0, roster.indexOf(worker.getUUID()));
        progress = updated.isEmpty() ? 0 : index * updated.size() / Math.max(1, roster.size()); initialized = true;
      }
      peers = roster; route = List.copyOf(updated);
    }
    if (route.isEmpty()) { worker.getNavigation().stop(); worker.state("No accessible surface patrol route"); return; }
    progress = Math.floorMod(progress, route.size());
    if (WorkerMovement.walk(worker, route.get(progress), recalled ? "Guarding recalled workers" : circuit ? "Patrolling network" : "Patrolling assigned totem"))
      progress = (progress + 1) % route.size();
  }
  private boolean has(Class<?> type) {
    for (int i = 0; i < worker.tools.getSlots(); i++) if (type.isInstance(worker.tools.getStackInSlot(i).getItem())) return true;
    return false;
  }
}
