package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Physical builder executor. The accepted project and generation belong to its destination Totem.
 */
public final class Building {
  private final Lumberton w;
  private int lookUntil = -1;
  private AABB perimeterRoute;
  private BlockPos idleSpot;
  private UUID idleShed;
  private long nextIdlePath;

  public Building(Lumberton worker) {
    w = worker;
  }

  public void lookAt(BlockPos target) {
    lookUntil = w.tickCount + 2;
    w.getLookControl().setLookAt(target.getX() + .5, target.getY() + .5, target.getZ() + .5);
  }

  public boolean controlsLook() {
    return w.authorized && w.job() == WorkerJob.BUILDINGTON && w.tickCount <= lookUntil;
  }

  public void perimeterRoute(AABB footprint) {
    perimeterRoute = footprint;
  }

  public boolean blocksRoute(AABB box) {
    return w.authorized
        && w.job() == WorkerJob.BUILDINGTON
        && perimeterRoute != null
        && box.maxX > perimeterRoute.minX
        && box.minX < perimeterRoute.maxX
        && box.maxZ > perimeterRoute.minZ
        && box.minZ < perimeterRoute.maxZ;
  }

  public BuildingProject active() {
    if (!(w.level() instanceof ServerLevel l)) return null;
    return WorldData.get(l).buildings.values().stream()
        .filter(p -> w.getUUID().equals(p.builder))
        .findFirst()
        .orElse(null);
  }

  public void release() {
    perimeterRoute = null;
    idleSpot = null;
    idleShed = null;
    lookUntil = -1;
    if (!(w.level() instanceof ServerLevel l)) return;
    for (BuildingProject p : WorldData.get(l).buildings.values()) p.release(w.getUUID());
    WorldData.get(l).setDirty();
  }

  public void tick(Station supply) {
    // Supply trips and escape from an initial indoor position use ordinary navigation.
    perimeterRoute = null;
    ServerLevel l = (ServerLevel) w.level();
    if (supply.purpose != WorksitePurpose.BUILDER) {
      w.state("Assign to a Builder Totem");
      return;
    }
    BuildingProject p = active();
    if (p == null) {
      if (!supply.worksiteReady()) p = supply.project();
      else
        p =
            WorldData.get(l).buildings.values().stream()
                .filter(
                    q ->
                        (!q.ready || q.supplies.chest != null)
                            && !q.paused
                            && q.builder == null
                            && Objects.equals(q.home, w.homeId)
                            && Objects.equals(q.owner, w.owner)
                            && l.hasChunkAt(q.anchor)
                            && l.getBlockEntity(q.anchor) instanceof Station s
                            && s.identity.equals(q.totem))
                .sorted(
                    Comparator.comparingLong((BuildingProject q) -> q.created)
                        .thenComparing(q -> q.id))
                .findFirst()
                .orElse(null);
      if (p == null || !p.claim(w)) {
        if (WorkSupplies.deposit(w, supply, Map.of())) return;
        waitInShed(supply);
        return;
      }
    }
    idleSpot = null;
    idleShed = null;
    if (!l.hasChunkAt(p.anchor)) {
      w.state("Construction site unloaded");
      return;
    }
    if (!(l.getBlockEntity(p.anchor) instanceof Station destination)
        || !destination.identity.equals(p.totem)
        || !Objects.equals(destination.owner, w.owner)) {
      p.paused = true;
      p.reason = "Worksite Totem missing";
      release();
      return;
    }
    if (p.home == null) p.home = destination.logisticsHome;
    if (!Objects.equals(p.home, w.homeId)) {
      release();
      w.state("Worksite belongs to another Network");
      return;
    }
    if (p.paused) {
      w.getNavigation().stop();
      w.state("Construction suspended: " + p.reason);
      return;
    }
    if (p.ready) {
      p.supplies.cleanup(w);
      return;
    }
    if (!Inventories.tool(WorkerJob.BUILDINGTON, w.activeTool())) {
      if (!WorkSupplies.tool(w, supply)) w.state("Needs Hammer in tool slots or worksite chest");
      return;
    }
    w.alerts = 0;
    if (!p.supplies.prepare(w, destination)) {
      p.reason = w.status();
      return;
    }
    p.tick(w, destination);
    if (!p.ready
        && WorkSupplies.full(w)
        && (w.status().equals("Unload construction cargo")
            || w.status().startsWith("Waiting for materials:"))) {
      Map<Item, Integer> keep = new LinkedHashMap<>(p.remaining(l));
      // Unload only when cargo prevents progress, retaining all needed material types.
      keep.replaceAll((item, count) -> Math.min(item.getMaxStackSize(), count));
      WorkSupplies.deposit(w, supply, keep);
    }
    if (!p.ready && !p.paused) p.reason = w.status();
    if (p.ready) p.supplies.cleanup(w);
  }

  private boolean freeIdleSpot(ServerLevel level, BlockPos pos) {
    return level.hasChunkAt(pos)
        && level.hasChunkAt(pos.above())
        && MiningRules.supported(level, pos)
        && level.getFluidState(pos).isEmpty()
        && !(level.getBlockState(pos).getBlock() instanceof DoorBlock)
        && level.noCollision(
            w, w.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(pos)));
  }

  private void waitInShed(Station supply) {
    ServerLevel level = (ServerLevel) w.level();
    BuildingProject shed = supply.project();
    if (shed == null || !shed.ready) {
      w.running.idle("Waiting for construction requests");
      return;
    }
    if (!shed.id.equals(idleShed)) {
      idleShed = shed.id;
      idleSpot = null;
      nextIdlePath = 0;
    }
    if (idleSpot != null && freeIdleSpot(level, idleSpot)) {
      // Navigation may finish a little over half a block from the node center.
      if (w.position().distanceToSqr(Vec3.atBottomCenterOf(idleSpot)) < .36) {
        w.getNavigation().stop();
        ((WorkerMoveControl) w.getMoveControl()).stop();
        w.state("Waiting for construction requests");
        return;
      }
      if (w.getNavigation().isInProgress() && level.getGameTime() < nextIdlePath) {
        w.state("Returning to Builder shed");
        return;
      }
    } else idleSpot = null;
    if (level.getGameTime() < nextIdlePath) {
      w.state("Waiting for access to Builder shed");
      return;
    }
    nextIdlePath = level.getGameTime() + 20;
    var template = shed.template(level);
    List<BlockPos> candidates = new ArrayList<>();
    for (int x = 2; x < template.size.getX() - 2; x++)
      for (int z = 2; z < template.size.getZ() - 2; z++) {
        BlockPos pos = shed.at(level, x, template.anchor.getY(), z);
        if (freeIdleSpot(level, pos)) candidates.add(pos);
      }
    Vec3 center = shed.bounds(level).getCenter();
    candidates.sort(
        Comparator.comparingDouble(
                (BlockPos p) ->
                    p.equals(idleSpot) ? -1 : p.distToCenterSqr(center.x, p.getY() + .5, center.z))
            .thenComparingLong(BlockPos::asLong));
    for (BlockPos pos : candidates) {
      if (!WorldEvents.pathBudget()) break;
      var path = w.getNavigation().createPath(pos, 0);
      if (path != null && path.canReach()) {
        idleSpot = pos;
        w.getNavigation().moveTo(path, 1);
        w.state("Returning to Builder shed");
        return;
      }
    }
    w.getNavigation().stop();
    ((WorkerMoveControl) w.getMoveControl()).stop();
    w.state("Waiting for access to Builder shed");
  }
}
