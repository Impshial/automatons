package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** Injured workers meet their mage in the authored ground clearing, including armored guards. */
public final class HealingVisit {
  private final Lumberton worker;
  private UUID visiting;
  private long nextSearch;
  public HealingVisit(Lumberton worker) { this.worker = worker; }
  public boolean tick() {
    if (worker.job() == WorkerJob.LUMINARITON || worker.getHealth() >= worker.getMaxHealth()) { visiting = null; return false; }
    if (worker.threats.escaping || worker.isGuard() && worker.threats.nearest(24) != null) return false;
    if (visiting == null && worker.getHealth() > worker.getMaxHealth() / 2) return false;
    ServerLevel level = (ServerLevel)worker.level();
    Lumberton mage = visiting != null && level.getEntity(visiting) instanceof Lumberton w ? w : null;
    if (mage != null && (!mage.luminaService.available() || mage.serviceReserve() <= 0 || !LuminaService.context(worker, mage))) { visiting = null; mage = null; }
    if (mage == null) {
      if (level.getGameTime() < nextSearch) return false;
      nextSearch = level.getGameTime() + 40;
      mage = level.getEntitiesOfClass(Lumberton.class, worker.getBoundingBox().inflate(96),
          w -> w.luminaService.available() && w.serviceReserve() > 0 && LuminaService.context(worker, w)
              && w.station() != null && w.station().worksiteReady()).stream()
          .min(Comparator.comparingDouble(worker::distanceToSqr)).orElse(null);
      if (mage == null) return false;
      visiting = mage.getUUID();
      worker.ranching.release();
      worker.fishing.release();
      worker.hunting.release(); worker.farming.release(); worker.building.release(); worker.mining.releaseWork(); worker.running.release();
      if (worker.getVehicle() instanceof Machine machine) {
        machine.halt("Operator seeking healing"); Vec3 exit = machine.getDismountLocationForPassenger(worker);
        worker.stopRiding(); worker.setPos(exit);
      }
    }
    if (worker.mining.returnToSurface()) return true;
    Station site = mage.station();
    if (site == null || site.project() == null) { visiting = null; return false; }
    BlockPos center = new SiteLayout(site.project(), level).feature("healing_position");
    int start = Math.floorMod(worker.getUUID().hashCode(), 8);
    for (int i = 0; i < 8; i++) {
      double angle = (start + i) * Math.PI / 4;
      BlockPos feet = center.offset((int)Math.round(Math.cos(angle) * 2), 0, (int)Math.round(Math.sin(angle) * 2));
      if (!MiningRules.supported(level, feet) || !level.noCollision(worker, worker.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(feet)))) continue;
      WorkerMovement.walk(worker, feet, "Visiting Luminariton for healing"); return true;
    }
    worker.state("Waiting for space in healing clearing"); return true;
  }
}
