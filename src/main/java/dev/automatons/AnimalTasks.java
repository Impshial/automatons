package dev.automatons;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;

/** Entity-persisted livestock identity and short, exclusive task leases. Never force-load a worker. */
public final class AnimalTasks {
  private static final String MANAGED = "AutomatonsLivestock", CLAIM = "AutomatonsAnimalTask";

  public static boolean managed(Animal animal) { return animal.getPersistentData().contains(MANAGED); }

  public static void register(Animal animal, Station ranch) {
    if (managed(animal)) return;
    var n = new net.minecraft.nbt.CompoundTag();
    n.putUUID("Ranch", ranch.identity);
    if (ranch.owner != null) n.putUUID("Owner", ranch.owner);
    animal.getPersistentData().put(MANAGED, n);
  }

  public static boolean belongs(Animal animal, Station ranch) {
    var n = animal.getPersistentData().getCompound(MANAGED);
    return n.hasUUID("Ranch") && n.getUUID("Ranch").equals(ranch.identity);
  }

  public static boolean available(Animal animal, Lumberton worker) {
    var n = animal.getPersistentData().getCompound(CLAIM);
    if (!n.hasUUID("Worker") || n.getUUID("Worker").equals(worker.getUUID())) return true;
    if (!(animal.level() instanceof ServerLevel level)) return false;
    return n.getLong("Until") <= level.getGameTime()
        || !(level.getEntity(n.getUUID("Worker")) instanceof Lumberton other) || !other.isAlive();
  }

  public static boolean claim(Animal animal, Lumberton worker, String task) {
    if (!available(animal, worker)) return false;
    var n = new net.minecraft.nbt.CompoundTag();
    n.putUUID("Worker", worker.getUUID()); n.putString("Task", task);
    n.putLong("Until", animal.level().getGameTime() + 100);
    animal.getPersistentData().put(CLAIM, n);
    return true;
  }

  public static void release(Animal animal, UUID worker) {
    var n = animal.getPersistentData().getCompound(CLAIM);
    if (n.hasUUID("Worker") && n.getUUID("Worker").equals(worker)) animal.getPersistentData().remove(CLAIM);
  }

  public static long milkedAt(Animal animal) { return animal.getPersistentData().getLong("AutomatonsMilkedAt"); }
  public static boolean canMilk(Animal animal) {
    return !animal.getPersistentData().contains("AutomatonsMilkedAt")
        || animal.level().getGameTime() - milkedAt(animal) >= 24000;
  }
  public static void milked(Animal animal) { animal.getPersistentData().putLong("AutomatonsMilkedAt", animal.level().getGameTime()); }
}
