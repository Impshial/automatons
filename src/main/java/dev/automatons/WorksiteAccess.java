package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;

/** Physical, exclusive ladder passage between the mage courtyard and its raised workshop. */
public final class WorksiteAccess {
  private record Route(
      BuildingProject project, BlockPos bottom, BlockPos ground, BlockPos exit, Direction face) {}

  private record Lease(UUID worker, long until) {}

  private static final Map<ServerLevel, Map<UUID, Lease>> leases = new WeakHashMap<>();
  private final Lumberton worker;
  private Route active;
  private boolean ascending, dismount;

  public WorksiteAccess(Lumberton worker) {
    this.worker = worker;
  }

  private Route route(BlockPos destination) {
    if (!(worker.level() instanceof ServerLevel level) || worker.isGuard()) return null;
    for (BuildingProject project : WorldData.get(level).buildings.values()) {
      if (!project.ready
          || project.purpose != WorksitePurpose.LUMINA
          || !Objects.equals(project.owner, worker.owner)
          || worker.homeId == null
          || !worker.homeId.equals(project.home)) continue;
      SiteLayout layout = new SiteLayout(project, level);
      BlockPos exit = layout.feature("upper_dismount");
      BlockPos bottom = layout.feature("upper_hatch").atY(project.anchor.getY());
      boolean targetUp = project.contains(level, destination) && destination.getY() >= exit.getY();
      boolean inside = project.bounds(level).inflate(1).contains(worker.position());
      if (targetUp
          ? worker.getY() >= exit.getY() - .2
          : worker.getY() <= bottom.getY() + .2 || !inside) continue;
      if (!level.hasChunkAt(bottom)) continue;
      var state = level.getBlockState(bottom);
      if (!state.is(Blocks.LADDER)) continue;
      BlockPos ground = null;
      for (Direction side : Direction.Plane.HORIZONTAL) {
        BlockPos p = bottom.relative(side);
        if (level.hasChunkAt(p)
            && MiningRules.supported(level, p)
            && level.noCollision(
                worker,
                worker.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(p)))) {
          ground = p;
          break;
        }
      }
      if (ground == null) continue;
      Route route = new Route(project, bottom, ground, exit, state.getValue(LadderBlock.FACING));
      if (valid(route)) return route;
    }
    return null;
  }

  private boolean valid(Route route) {
    ServerLevel level = (ServerLevel) worker.level();
    if (!route.project.ready
        || !Objects.equals(route.project.owner, worker.owner)
        || !Objects.equals(route.project.home, worker.homeId)
        || worker.isGuard()) return false;
    for (int y = route.bottom.getY(); y <= route.exit.getY(); y++) {
      BlockPos p = route.bottom.atY(y);
      if (!level.hasChunkAt(p)) return false;
      var state = level.getBlockState(p);
      if (!state.is(Blocks.LADDER) || state.getValue(LadderBlock.FACING) != route.face)
        return false;
    }
    return level.hasChunkAt(route.exit) && level.hasChunkAt(route.ground);
  }

  public boolean canRoute(BlockPos destination) {
    return active != null || route(destination) != null;
  }

  /** True consumes this travel request while approaching or traversing the ladder. */
  public boolean travel(BlockPos destination) {
    if (active != null) return true;
    Route route = route(destination);
    if (route == null) return false;
    boolean up =
        destination.getY() >= route.exit.getY()
            && route.project.contains((ServerLevel) worker.level(), destination);
    BlockPos approach = up ? route.ground : route.exit;
    Vec3 shaft = Vec3.atBottomCenterOf(route.bottom);
    boolean onLadder =
        worker.position().subtract(shaft).horizontalDistanceSqr() < .7
            && worker.getY() >= route.bottom.getY()
            && worker.getY() <= route.exit.getY();
    if (!onLadder && !WorkerMovement.walkGround(worker, approach, "Approaching workshop ladder"))
      return true;
    if (!claim(route)) {
      worker.state("Waiting for workshop ladder");
      return true;
    }
    active = route;
    ascending = up;
    dismount = false;
    worker.getNavigation().stop();
    ((WorkerMoveControl) worker.getMoveControl()).stop();
    return true;
  }

  private boolean claim(Route route) {
    ServerLevel level = (ServerLevel) worker.level();
    Map<UUID, Lease> map = leases.computeIfAbsent(level, l -> new HashMap<>());
    Lease prior = map.get(route.project.id);
    if (prior != null
        && !prior.worker.equals(worker.getUUID())
        && prior.until > level.getGameTime()
        && level.getEntity(prior.worker) instanceof Lumberton other
        && other.isAlive()
        && other.operating() > 0
        && other.worksiteAccess.active != null) return false;
    map.put(route.project.id, new Lease(worker.getUUID(), level.getGameTime() + 100));
    return true;
  }

  public void release() {
    if (active == null) return;
    if (worker.level() instanceof ServerLevel level) {
      Map<UUID, Lease> map = leases.get(level);
      if (map != null)
        map.computeIfPresent(
            active.project.id, (id, lease) -> lease.worker.equals(worker.getUUID()) ? null : lease);
    }
    active = null;
    worker.setDeltaMovement(Vec3.ZERO);
    worker.setNoGravity(false);
  }

  public void motionTick() {
    if (active == null) return;
    if (!worker.isAlive() || worker.packing || worker.operating() == 0 || !valid(active)) {
      release();
      worker.state("Workshop ladder unavailable");
      return;
    }
    worker.getNavigation().stop();
    ((WorkerMoveControl) worker.getMoveControl()).stop();
    if (!claim(active)) {
      worker.setDeltaMovement(Vec3.ZERO);
      return;
    }
    BlockPos exit = ascending ? active.exit : active.ground;
    Vec3 target =
        dismount
            ? Vec3.atBottomCenterOf(exit)
            : Vec3.atBottomCenterOf(active.bottom.atY(exit.getY()))
                .add(active.face.getStepX() * .12, 0, active.face.getStepZ() * .12);
    Vec3 delta = target.subtract(worker.position());
    if (delta.lengthSqr() < .0016) {
      if (dismount) release();
      else dismount = true;
      return;
    }
    ServerLevel level = (ServerLevel) worker.level();
    if (dismount
        && (!level.noCollision(worker, worker.getDimensions(Pose.STANDING).makeBoundingBox(target))
            || level
                .getBlockState(exit.below())
                .getCollisionShape(level, exit.below())
                .isEmpty())) {
      worker.setDeltaMovement(Vec3.ZERO);
      worker.state("Workshop ladder exit blocked");
      return;
    }
    worker.setNoGravity(true);
    worker.fallDistance = 0;
    double dy =
        dismount || delta.horizontalDistanceSqr() < .01
            ? Math.max(-.14, Math.min(.14, delta.y))
            : 0;
    worker.setDeltaMovement(delta.x * .25, dy, delta.z * .25);
    worker.state(ascending ? "Climbing to workshop" : "Climbing to courtyard");
  }

  public boolean travelControlled() {
    if (active == null || worker.level().isClientSide) return false;
    worker.move(MoverType.SELF, worker.getDeltaMovement());
    worker.setDeltaMovement(Vec3.ZERO);
    worker.fallDistance = 0;
    return true;
  }
}
