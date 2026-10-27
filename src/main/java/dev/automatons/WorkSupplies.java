package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraftforge.items.IItemHandler;

/**
 * Local physical service shared by builders/farmers. Courier restrictions apply to couriers only.
 */
public final class WorkSupplies {
  public static boolean equipment(Lumberton worker, Station station, java.util.function.Predicate<ItemStack> match) {
    for (BlockPos pos : station.chests) {
      if (ConstructionSupply.reserved((ServerLevel)worker.level(), pos)) continue;
      IItemHandler stock = Storage.handler((ServerLevel)worker.level(), pos, station);
      if (stock == null) continue;
      for (int i = 0; i < stock.getSlots(); i++) {
        ItemStack stack = stock.getStackInSlot(i);
        if (!match.test(stack) || !Inventories.tool(worker.job(), stack) || !Storage.accepts(worker.tools, stack) || !worker.canReach(pos)) continue;
        if (worker.travelTo(pos, "Collecting equipment", "Collecting equipment")) BuildingProject.move(stock, i, worker.tools, 1);
        return true;
      }
    }
    return false;
  }
  public static boolean full(Lumberton w) {
    for (int i = 0; i < w.cargo.getSlots(); i++)
      if (w.cargo.getStackInSlot(i).isEmpty()) return false;
    return true;
  }

  public static boolean tool(Lumberton w, Station station) {
    w.selectTool();
    if (Inventories.tool(w.job(), w.activeTool())) return true;
    for (BlockPos p : station.chests) {
      if (ConstructionSupply.reserved((ServerLevel) w.level(), p)) continue;
      IItemHandler h = Storage.handler((ServerLevel) w.level(), p, station);
      if (h == null) continue;
      for (int slot = 0; slot < h.getSlots(); slot++)
        if (Inventories.tool(w.job(), h.getStackInSlot(slot))) {
          if (!w.canReach(p)) continue;
          if (w.travelTo(p, "Collecting tool", "Collecting tool"))
            BuildingProject.move(h, slot, w.tools, 1);
          return true;
        }
    }
    w.alerts = Lumberton.TOOLS;
    return false;
  }

  public static boolean take(Lumberton w, Station station, Item item, int target) {
    if (w.mining.carried(item) >= target) return true;
    for (BlockPos p : station.chests) {
      if (ConstructionSupply.reserved((ServerLevel) w.level(), p)) continue;
      IItemHandler h = Storage.handler((ServerLevel) w.level(), p, station);
      if (h == null) continue;
      for (int slot = 0; slot < h.getSlots(); slot++)
        if (h.getStackInSlot(slot).is(item)) {
          if (!Storage.accepts(w.cargo, h.getStackInSlot(slot)) || !w.canReach(p)) continue;
          if (w.travelTo(p, "Collecting supplies", "Collecting supplies"))
            BuildingProject.move(h, slot, w.cargo, target - w.mining.carried(item));
          return true;
        }
    }
    return false;
  }

  /** Returns true while a real deposit/service trip is in progress. Never discards overflow. */
  public static boolean deposit(Lumberton w, Station station, Map<Item, Integer> keep) {
    for (BlockPos p : station.chests) {
      if (ConstructionSupply.reserved((ServerLevel) w.level(), p)) continue;
      IItemHandler h = Storage.handler((ServerLevel) w.level(), p, station);
      if (h == null) continue;
      for (int slot = 0; slot < w.cargo.getSlots(); slot++) {
        ItemStack stack = w.cargo.getStackInSlot(slot);
        int excess = w.mining.carried(stack.getItem()) - keep.getOrDefault(stack.getItem(), 0);
        if (stack.isEmpty() || excess <= 0 || !Storage.accepts(h, stack) || !w.canReach(p))
          continue;
        if (w.travelTo(p, "Storing cargo", "Storing cargo"))
          BuildingProject.move(w.cargo, slot, h, excess);
        return true;
      }
    }
    return false;
  }
}
