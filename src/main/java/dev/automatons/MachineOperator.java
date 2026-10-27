package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Assignment is persistent and exclusive; only a real passenger grants piloted behavior. */
public final class MachineOperator {
  public static boolean eligible(Machine m, Lumberton w) {
    return m.owner != null
        && m.homeId != null
        && w.isAlive()
        && !w.packing
        && w.job() == WorkerJob.OPERATORTON
        && Objects.equals(m.owner, w.owner)
        && Objects.equals(m.homeId, w.homeId)
        && w.level() == m.level();
  }

  public static String assign(Machine m, Lumberton w) {
    if (m.locked() || !m.getPassengers().isEmpty())
      return "Park The Machine and empty its seat first";
    if (w.isGuard() || !w.isAlive() || w.packing || w.level() != m.level()
        || m.owner == null || m.homeId == null || !Objects.equals(m.owner, w.owner)
        || !Objects.equals(m.homeId, w.homeId)) return "Choose an ordinary automaton on this pedestal network";
    if (m.operator != null && !m.operator.equals(w.getUUID())
        || w.machineId != null && !w.machineId.equals(m.getUUID()))
      return "Operator or Machine already assigned";
    if (!w.setJob(WorkerJob.OPERATORTON)) return "Make cargo space for the worker's tools";
    w.unassign();
    m.operator = w.getUUID();
    w.machineId = m.getUUID();
    w.authorized = true;
    WorkerIdentity.update(w, "Deployed");
    m.sync();
    return "Operatorton assigned — waiting to board";
  }

  public static void release(Machine m) {
    m.halt("Operator released — job paused");
    if (m.operator != null && m.getServer() != null) {
      var ledger = Ledger.get(m.getServer());
      var record = ledger.workers.get(m.operator);
      if (record != null
          && record.hasUUID("Machine")
          && record.getUUID("Machine").equals(m.getUUID())) {
        record.remove("Machine");
        ledger.setDirty();
      }
    }
    if (m.operator != null
        && m.level() instanceof ServerLevel l
        && l.getEntity(m.operator) instanceof Lumberton w) {
      if (w.getVehicle() == m) {
        Vec3 pos = m.getDismountLocationForPassenger(w);
        w.stopRiding();
        w.setPos(pos);
      }
      if (Objects.equals(w.machineId, m.getUUID())) w.machineId = null;
      WorkerIdentity.update(w, "Deployed");
    }
    m.operator = null;
    m.sync();
  }

  public static void leave(Lumberton w) {
    if (w.machineId != null
        && w.level() instanceof ServerLevel l
        && l.getEntity(w.machineId) instanceof Machine m
        && Objects.equals(m.operator, w.getUUID())) release(m);
    w.machineId = null;
    if (w.getServer() != null) {
      var ledger = Ledger.get(w.getServer());
      var record = ledger.workers.get(w.getUUID());
      if (record != null) {
        record.remove("Machine");
        ledger.setDirty();
      }
    }
  }

  public static void validate(Machine m) {
    if (m.operator == null || !(m.level() instanceof ServerLevel l)) return;
    var record = Ledger.get(l.getServer()).workers.get(m.operator);
    if (record != null
        && (!record.hasUUID("Machine")
            || !record.getUUID("Machine").equals(m.getUUID())
            || record.getInt("Job") != WorkerJob.OPERATORTON
            || !record.getString("State").equals("Deployed"))) {
      release(m);
      return;
    }
    var e = l.getEntity(m.operator);
    if (e instanceof Lumberton w) {
      if (!eligible(m, w) || !Objects.equals(w.machineId, m.getUUID())) release(m);
      else if (m.job.active && (!w.authorized || !m.hasPilot()))
        m.halt("Driver unavailable — job paused");
    } else if (m.job.active) m.halt("Driver unloaded — job paused");
  }

  public static void tick(Lumberton w) {
    if (w.machineId != null) {
      var record = WorkerIdentity.record(w);
      if (record != null
          && (!record.hasUUID("Machine") || !record.getUUID("Machine").equals(w.machineId))) {
        w.machineId = null;
        w.state("Machine assignment ended");
        return;
      }
    }
    if (w.machineId == null) {
      w.getNavigation().stop();
      w.state("Waiting for Machine assignment");
      return;
    }
    if (!(((ServerLevel) w.level()).getEntity(w.machineId) instanceof Machine m)) {
      w.state("Assigned Machine unloaded");
      return;
    }
    if (!eligible(m, w) || !Objects.equals(m.operator, w.getUUID())) {
      w.machineId = null;
      w.state("Machine assignment ended");
      return;
    }
    if (w.getVehicle() == m) {
      w.getNavigation().stop();
      w.state(m.status);
      return;
    }
    if (!m.stopped() || m.job.active || !m.getPassengers().isEmpty()) {
      w.state("Waiting to board");
      return;
    }
    for (Direction d :
        List.of(m.heading.getClockWise(), m.heading.getCounterClockWise(), m.heading.getOpposite())
            .stream()
            .sorted(
                Comparator.comparingDouble(
                    direction ->
                        w.distanceToSqr(Vec3.atBottomCenterOf(m.grid.relative(direction, 2)))))
            .toList()) {
      BlockPos approach = m.grid.relative(d, 2);
      if (w.blockPosition().distSqr(approach) <= 2.25 && w.distanceToSqr(m) < 12) {
        w.getNavigation().stop();
        if (w.startRiding(m)) w.state("Parked");
        return;
      }
      if (w.level().hasChunkAt(approach)
          && w.level()
              .getBlockState(approach.below())
              .isFaceSturdy(w.level(), approach.below(), Direction.UP)) {
        if (w.tickCount % 20 != 0) {
          w.state("Going to Machine");
          return;
        }
        var path = w.getNavigation().createPath(approach, 0);
        if (path != null && path.canReach() && w.getNavigation().moveTo(path, 1)) {
          w.state("Going to Machine");
          return;
        }
      }
    }
    w.state("Waiting for boarding path");
  }

  private MachineOperator() {}
}
