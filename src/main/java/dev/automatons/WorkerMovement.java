package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Arrival at a floor position, rather than the longer reach used for block work. */
public final class WorkerMovement {
  private static final Map<Lumberton, Long> retries = new WeakHashMap<>();

  public static boolean walk(Lumberton worker, BlockPos feet, String activity) {
    if (worker.worksiteAccess.travel(feet)) return false;
    return walkGround(worker, feet, activity);
  }

  static boolean walkGround(Lumberton worker, BlockPos feet, String activity) {
    if (!(worker.level() instanceof ServerLevel level) || worker.operating() == 0) return false;
    worker.state(activity);
    Vec3 point = Vec3.atBottomCenterOf(feet);
    if (worker.position().distanceToSqr(point) < .49) {
      worker.getNavigation().stop();
      return true;
    }
    if (!level.hasChunkAt(feet)) {
      worker.state("Destination unloaded");
      return false;
    }
    // Vanilla navigation may finish just outside this interaction's arrival tolerance.
    // Finish the short, level, collision-free approach through ordinary MoveControl.
    Vec3 remaining = point.subtract(worker.position());
    if (worker.getNavigation().isDone()
        && remaining.lengthSqr() < 2.25
        && Math.abs(remaining.y) < .2
        && MiningRules.supported(level, feet)
        && !level
            .getBlockCollisions(worker, worker.getBoundingBox().expandTowards(remaining))
            .iterator()
            .hasNext()) {
      worker.getMoveControl().setWantedPosition(point.x, point.y, point.z, 1);
      return false;
    }
    if (level.getGameTime() < retries.getOrDefault(worker, 0L)) return false;
    retries.put(worker, level.getGameTime() + 20);
    if (!WorldEvents.pathBudget()) return false;
    var path = worker.getNavigation().createPath(feet, 0);
    if (path != null && path.canReach()) worker.getNavigation().moveTo(path, 1);
    else worker.state("Waiting for clear access");
    return false;
  }
}
