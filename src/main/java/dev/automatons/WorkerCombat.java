package dev.automatons;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.*;

/** Shared real equipment and conservative friendly-fire checks for hunters and guards. */
public final class WorkerCombat {
  private final Lumberton worker;
  private int cooldown, draw;
  private int lastFight = -100;
  public boolean busy() { return worker.tickCount - lastFight < 40; }
  public WorkerCombat(Lumberton worker) { this.worker = worker; }
  private int equipment(Class<?> kind) {
    for (int i = 0; i < worker.tools.getSlots(); i++) {
      ItemStack stack = worker.tools.getStackInSlot(i);
      if (!stack.isEmpty() && kind.isInstance(stack.getItem()) && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage())) return i;
    }
    return -1;
  }
  public int arrowSlot() {
    for (int i = 0; i < worker.cargo.getSlots(); i++)
      if (worker.cargo.getStackInSlot(i).getItem() instanceof ArrowItem) return i;
    return -1;
  }
  public boolean shotClear(LivingEntity target) {
    if (!worker.hasLineOfSight(target)) return false;
    Vec3 from = worker.getEyePosition(), to = target.getEyePosition();
    // Include the rising arrow arc, not just a zero-width ray through the target.
    AABB corridor = new AABB(from, to).inflate(1.25, 3, 1.25);
    for (LivingEntity entity : worker.level().getEntitiesOfClass(LivingEntity.class, corridor)) {
      if (entity == worker || entity == target) continue;
      if (entity instanceof Player || entity instanceof Lumberton || entity instanceof Animal)
        if (entity.getBoundingBox().inflate(1, 3, 1).clip(from, to).isPresent()) return false;
    }
    return true;
  }
  public void fight(LivingEntity target) {
    lastFight = worker.tickCount;
    if (worker.operating() <= WorkerEnergy.LOW || !target.isAlive()) { worker.stopUsingItem(); draw = 0; return; }
    worker.getLookControl().setLookAt(target, 30, 30);
    int bow = equipment(BowItem.class), sword = equipment(SwordItem.class), ammo = arrowSlot();
    double distance = worker.distanceToSqr(target);
    if (bow >= 0 && ammo >= 0 && distance > 9 && distance <= 24 * 24) {
      worker.activateTool(bow);
      worker.state("Aiming bow");
      worker.getNavigation().stop();
      if (!shotClear(target)) { worker.stopUsingItem(); draw = 0; return; }
      if (worker.tickCount < cooldown) return;
      if (!worker.isUsingItem()) { worker.startUsingItem(InteractionHand.MAIN_HAND); draw = worker.tickCount; return; }
      if (worker.tickCount - draw < 20) return;
      ItemStack arrows = worker.cargo.getStackInSlot(ammo), weapon = worker.activeTool();
      AbstractArrow arrow = ProjectileUtil.getMobArrow(worker, arrows.copyWithCount(1), 1);
      arrow = ((BowItem)weapon.getItem()).customArrow(arrow);
      double dx = target.getX() - worker.getX(), dz = target.getZ() - worker.getZ();
      double dy = target.getY(.333) - arrow.getY() + Math.sqrt(dx * dx + dz * dz) * .2;
      arrow.shoot(dx, dy, dz, 1.6f, 2);
      if (worker.level().addFreshEntity(arrow)) {
        boolean infinity = arrows.is(Items.ARROW) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0;
        if (infinity) arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        else worker.cargo.extractItem(ammo, 1, false);
        worker.wear(1);
        cooldown = worker.tickCount + 20;
        worker.playSound(net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1, 1);
      }
      worker.stopUsingItem(); draw = 0; return;
    }
    worker.stopUsingItem(); draw = 0;
    if (sword >= 0) worker.activateTool(sword);
    else {
      // A real empty equipment slot is used for fists, retaining an unusable bow in cargo/tools.
      for (int i = 0; i < worker.tools.getSlots(); i++)
        if (worker.tools.getStackInSlot(i).isEmpty()) { worker.activateTool(i); break; }
    }
    if (distance > Math.pow(worker.getBbWidth() + target.getBbWidth() + .8, 2) || !worker.hasLineOfSight(target)) {
      WorkerMovement.walk(worker, target.blockPosition(), "Defending network"); return;
    }
    worker.getNavigation().stop(); worker.state("Defending network");
    if (worker.tickCount < cooldown) return;
    cooldown = worker.tickCount + 20;
    worker.swing(InteractionHand.MAIN_HAND);
    if (sword >= 0) { if (worker.doHurtTarget(target)) worker.wear(1); }
    else target.hurt(worker.damageSources().mobAttack(worker), 2);
  }
}
