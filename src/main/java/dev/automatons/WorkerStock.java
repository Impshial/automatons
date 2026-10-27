package dev.automatons;

import java.util.*;
import net.minecraft.world.item.*;

/** Shared supply requirements for couriers, worksite reserves and the crafting queue. */
public final class WorkerStock {
  public static Station site(Lumberton worker) {
    Station assigned = worker.station();
    if (assigned != null) return assigned;
    if (worker.isGuard() && worker.guarding.assigned() != null) return worker.guarding.assigned();
    return worker.home != null
            && worker.level().hasChunkAt(worker.home)
            && worker.level().getBlockEntity(worker.home) instanceof Station home
            && Objects.equals(worker.owner, home.owner)
        ? home
        : null;
  }

  public static UUID siteId(Lumberton worker) {
    Station site = site(worker);
    return site == null ? null : site.identity;
  }

  public static boolean needsTool(Lumberton worker) {
    if (worker.excavator()) return worker.mining.needsTool();
    if (worker.job() == WorkerJob.UNASSIGNED
        || worker.job() == WorkerJob.RUNNERTON
        || worker.job() == WorkerJob.CRAFTERTON
        || worker.job() == WorkerJob.LUMINARITON
        || worker.job() == WorkerJob.OPERATORTON) return false;
    for (int i = 0; i < worker.tools.getSlots(); i++)
      if (Inventories.tool(worker.job(), worker.tools.getStackInSlot(i))) return false;
    return true;
  }

  public static Map<Item, Integer> required(Lumberton worker) {
    Map<Item, Integer> need = new LinkedHashMap<>();
    need.put(Luminall.BOTTLE.get(), worker.job() == WorkerJob.LUMINARITON ? 3 : 1);
    switch (worker.job()) {
      case WorkerJob.MININGTON -> need.putAll(worker.mining.requiredStock());
      case WorkerJob.FARMINGTON -> need.putAll(worker.farming.reserve());
      case WorkerJob.HUNTERTON, WorkerJob.GUARDINGTON -> need.put(Items.ARROW, 32);
      case WorkerJob.RANCHERTON -> {
        need.put(Items.BUCKET, 1);
        need.put(Items.LEAD, 1);
        need.put(Items.WHEAT, 16);
        need.put(Items.WHEAT_SEEDS, 16);
        need.put(Items.CARROT, 16);
      }
      case WorkerJob.LUMINARITON -> {
        need.put(Items.GLASS_BOTTLE, 16);
        need.put(Items.BUCKET, 1);
        need.put(Automatons.LUMITE_SHARD.get(), 8);
      }
      case WorkerJob.CRAFTERTON -> need.putAll(worker.crafting.requested);
      case WorkerJob.QUARRYTON -> need.putAll(worker.mining.requiredStock());
      default -> {}
    }
    return need;
  }

  public static int carried(Lumberton worker, Item item) {
    if (item != Luminall.BOTTLE.get()) return worker.mining.carried(item);
    int full = 0;
    for (int i = 0; i < worker.cargo.getSlots(); i++)
      if (WorkerEnergy.fullBottle(worker.cargo.getStackInSlot(i)))
        full += worker.cargo.getStackInSlot(i).getCount();
    return full;
  }

  public static int plants(Lumberton worker) {
    int count = 0;
    for (int i = 0; i < worker.cargo.getSlots(); i++)
      if (LuminaService.plantingStock(worker.cargo.getStackInSlot(i)))
        count += worker.cargo.getStackInSlot(i).getCount();
    return count;
  }

  public static boolean accepts(Lumberton worker, ItemStack stack) {
    return required(worker).containsKey(stack.getItem())
            && (stack.getItem() != Luminall.BOTTLE.get() || WorkerEnergy.fullBottle(stack))
        || worker.job() == WorkerJob.LUMINARITON && LuminaService.plantingStock(stack);
  }
}
