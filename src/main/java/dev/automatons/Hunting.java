package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;

public final class Hunting {
  public static final TagKey<EntityType<?>> PREY = TagKey.create(Registries.ENTITY_TYPE, Automatons.id("hunting_prey"));
  public static final TagKey<EntityType<?>> EXCLUDED = TagKey.create(Registries.ENTITY_TYPE, Automatons.id("hunting_excluded"));
  private final Lumberton worker;
  private UUID target;
  private BlockPos drops;
  private long nextSearch, collectUntil;
  public Hunting(Lumberton worker) { this.worker = worker; }
  public CompoundTag save() {
    var n = new CompoundTag();
    if (target != null) n.putUUID("Target", target);
    if (drops != null) { n.putLong("Drops", drops.asLong()); n.putLong("CollectUntil", collectUntil); }
    return n;
  }
  public void load(CompoundTag n) {
    target = n.hasUUID("Target") ? n.getUUID("Target") : null;
    drops = n.contains("Drops") ? BlockPos.of(n.getLong("Drops")) : null;
    collectUntil = n.getLong("CollectUntil");
  }
  public void release() {
    if (worker.level() instanceof ServerLevel level && target != null && level.getEntity(target) instanceof Animal animal)
      AnimalTasks.release(animal, worker.getUUID());
    target = null; worker.stopUsingItem();
  }
  public static boolean eligible(Animal animal, Station station) {
    if (!animal.isAlive() || animal.isBaby() || animal instanceof AbstractHorse || animal instanceof Cat
        || animal instanceof Wolf || animal instanceof TamableAnimal || animal.getType().is(EXCLUDED)
        || animal instanceof OwnableEntity owned && owned.getOwnerUUID() != null || AnimalTasks.managed(animal)) return false;
    if (animal.getType().getCategory() != MobCategory.CREATURE && !animal.getType().is(PREY)) return false;
    double dx = animal.getX() - (station.getBlockPos().getX() + .5), dz = animal.getZ() - (station.getBlockPos().getZ() + .5);
    return dx * dx + dz * dz <= 96 * 96;
  }
  private boolean has(Class<?> kind) {
    for (int i = 0; i < worker.tools.getSlots(); i++) {
      ItemStack s = worker.tools.getStackInSlot(i);
      if (kind.isInstance(s.getItem()) && Inventories.tool(worker.job(), s)) return true;
    }
    return false;
  }
  public void tick(Station station) {
    ServerLevel level = (ServerLevel)worker.level();
    Animal animal = target != null && level.getEntity(target) instanceof Animal a ? a : null;
    if (animal != null && !animal.isAlive()) {
      drops = animal.blockPosition(); collectUntil = level.getGameTime() + 100; release(); animal = null;
    }
    if (animal != null && (!eligible(animal, station) || !AnimalTasks.claim(animal, worker, "Hunt"))) { release(); animal = null; }
    if (drops != null) {
      var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(drops).inflate(5), worker::canCollectDrop);
      for (ItemEntity item : items.stream().sorted(Comparator.comparingDouble(worker::distanceToSqr)).toList()) {
        if (!Storage.accepts(worker.cargo, item.getItem())) break;
        if (worker.distanceToSqr(item) < 4) worker.pickupDrop(item);
        else WorkerMovement.walk(worker, item.blockPosition(), "Collecting hunt drops");
        return;
      }
      if (level.getGameTime() < collectUntil && !WorkSupplies.full(worker)) { WorkerMovement.walk(worker, drops, "Collecting hunt drops"); return; }
      drops = null;
    }
    if (WorkSupplies.full(worker)) {
      release();
      if (!WorkSupplies.deposit(worker, station, Map.of(Items.ARROW, 32, Luminall.BOTTLE.get(), 1))) worker.state("Hunting storage full");
      return;
    }
    boolean sword = has(SwordItem.class), bow = has(BowItem.class);
    if (!sword && (!bow || worker.combat.arrowSlot() < 0)) {
      release();
      if (WorkSupplies.equipment(worker, station, s -> s.getItem() instanceof SwordItem)) return;
      if (!bow && WorkSupplies.equipment(worker, station, s -> s.getItem() instanceof BowItem)) return;
      if (bow && WorkSupplies.take(worker, station, Items.ARROW, 32)) return;
      worker.travelTo(station.getBlockPos(), "Returning for hunting equipment", "Needs sword or bow and arrows"); return;
    }
    if (animal != null) {
      // Recheck every tick, including the tick on which a shot or melee action is committed.
      if (eligible(animal, station)) worker.combat.fight(animal);
      return;
    }
    if (target != null) release();
    if (level.getGameTime() < nextSearch) { worker.state("Looking for unprotected adult prey"); return; }
    nextSearch = level.getGameTime() + 40;
    if (WorkSupplies.deposit(worker, station, Map.of(Items.ARROW, 32, Luminall.BOTTLE.get(), 1))) { nextSearch = 0; return; }
    if (bow && worker.combat.arrowSlot() < 0 && WorkSupplies.take(worker, station, Items.ARROW, 32)) { nextSearch = 0; return; }
    AABB area = new AABB(station.getBlockPos()).inflate(96, 32, 96);
    for (Animal candidate : level.getEntitiesOfClass(Animal.class, area, a -> eligible(a, station) && AnimalTasks.available(a, worker))
        .stream().sorted(Comparator.comparingDouble(worker::distanceToSqr)).limit(12).toList()) {
      if (!worker.canReach(candidate.blockPosition()) || !AnimalTasks.claim(candidate, worker, "Hunt")) continue;
      target = candidate.getUUID(); return;
    }
    worker.travelTo(station.getBlockPos(), "Returning to hunting shed", "No eligible prey in hunting area");
  }
}
