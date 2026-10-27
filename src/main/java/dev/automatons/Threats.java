package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Exact vanilla types; neutral daylight spiders and unrelated monsters keep their behavior. */
public final class Threats {
  public static boolean supported(Entity entity) {
    EntityType<?> type = entity.getType();
    return type == EntityType.ZOMBIE || type == EntityType.SKELETON
        || type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER;
  }
  public static boolean aggressive(Mob mob) {
    return supported(mob) && (mob.getType() != EntityType.SPIDER && mob.getType() != EntityType.CAVE_SPIDER
        || mob.getTarget() instanceof Lumberton || mob.getLightLevelDependentMagicValue() < .5f);
  }
  @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
    if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob) || !supported(mob)) return;
    mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<Lumberton>(mob, Lumberton.class, 10, true, false,
        target -> target.isAlive() && aggressive(mob)) {
      @Override public boolean canContinueToUse() { return aggressive(mob) && super.canContinueToUse(); }
    });
  }

  private final Lumberton worker;
  private Mob danger;
  private long clearAt, nextScan;
  private BlockPos shelter;
  public boolean escaping;
  public Threats(Lumberton worker) { this.worker = worker; }
  public Mob nearest(int range) {
    return worker.level().getEntitiesOfClass(Mob.class, worker.getBoundingBox().inflate(range),
        m -> m.isAlive() && aggressive(m) && (m.getTarget() == worker || worker.hasLineOfSight(m)))
        .stream().min(Comparator.comparingDouble(worker::distanceToSqr)).orElse(null);
  }
  public boolean tick() {
    if (worker.isGuard() || worker.owner == null || worker.operating() == 0) return false;
    var level = (ServerLevel)worker.level();
    if (level.getGameTime() >= nextScan) {
      nextScan = level.getGameTime() + 10;
      danger = nearest(16);
      if (danger != null) clearAt = level.getGameTime() + 80;
    }
    if (danger == null && level.getGameTime() >= clearAt) { escaping = false; shelter = null; return false; }
    if (!escaping) {
      worker.clearChop(); worker.farming.release(); worker.building.release(); worker.mining.releaseWork();
      worker.running.release(); worker.getNavigation().stop(); escaping = true;
      if (worker.getVehicle() instanceof Machine machine) {
        machine.halt("Operator escaping danger"); Vec3 exit = machine.getDismountLocationForPassenger(worker);
        worker.stopRiding(); worker.setPos(exit);
      }
    }
    if (worker.mining.returnToSurface()) return true;
    if (shelter == null && level.getGameTime() % 20 == 0) {
      for (BuildingProject building : WorldData.get(level).buildings.values().stream()
          .filter(p -> p.ready && Objects.equals(worker.owner, p.owner) && Objects.equals(worker.homeId, p.home))
          .sorted(Comparator.comparingDouble(p -> worker.distanceToSqr(Vec3.atCenterOf(p.anchor)))).toList()) {
        BlockPos inside = building.entrance(level).relative(building.front.getOpposite(), 2);
        if (!level.hasChunkAt(inside) || !MiningRules.supported(level, inside)
            || !level.noCollision(worker, worker.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(inside)))) continue;
        if (!WorldEvents.pathBudget()) break;
        var path = worker.getNavigation().createPath(inside, 0);
        if (path != null && path.canReach()) { shelter = inside; break; }
      }
    }
    if (shelter != null) {
      if (WorkerMovement.walk(worker, shelter, "Taking shelter")) {
        worker.state("Sheltering from danger");
        for (BlockPos p : BlockPos.betweenClosed(shelter.offset(-3, -1, -3), shelter.offset(3, 1, 3))) {
          var state = level.getBlockState(p);
          if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN)
              && level.getEntitiesOfClass(LivingEntity.class, new AABB(p).inflate(.3)).isEmpty())
            door.setOpen(null, level, state, p, false);
        }
      }
    } else if (danger != null && level.getGameTime() % 20 == 0) {
      Vec3 away = worker.position().subtract(danger.position()).normalize().scale(6);
      BlockPos escape = worker.blockPosition().offset((int)away.x, 0, (int)away.z);
      if (MiningRules.supported(level, escape)) WorkerMovement.walk(worker, escape, "Escaping danger");
      else worker.state("Danger — no safe path");
    }
    return true;
  }
}
