package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.items.ItemStackHandler;

/** Reassignment packs everything first, then commits to owned linked chests without losing NBT. */
final class JobEquipment {
  private record Chest(Container container, ItemStackHandler after) {}

  static ItemStackHandler pack(Lumberton worker, int next) {
    ItemStackHandler cargo = new ItemStackHandler(Inventories.cargoSlots(next));
    List<ItemStack> overflow = new ArrayList<>();
    for (int i = 0; i < worker.cargo.getSlots(); i++) {
      ItemStack rest = Inventories.insert(cargo, worker.cargo.getStackInSlot(i), false);
      if (!rest.isEmpty()) overflow.add(rest);
    }
    for (int i = 0; i < worker.tools.getSlots(); i++) {
      ItemStack tool = worker.tools.getStackInSlot(i);
      if (Inventories.tool(next, tool)) continue;
      ItemStack rest = Inventories.insert(cargo, tool, false);
      if (!rest.isEmpty()) overflow.add(rest);
    }
    if (overflow.isEmpty()) return cargo;
    if (!(worker.level() instanceof ServerLevel level)
        || worker.owner == null
        || worker.homeId == null) return null;
    LogisticsNetwork network = LogisticsNetwork.get(worker);
    network.changed(level);
    network.refresh(level);
    Set<Station> sites = new LinkedHashSet<>();
    if (worker.station() != null) sites.add(worker.station());
    for (var report : network.reports.values()) {
      Station site = network.station(report.endpoint.totem());
      if (report.loaded && site != null) sites.add(site);
    }
    List<Chest> destinations = new ArrayList<>();
    Set<BlockPos> seen = new HashSet<>();
    for (Station site : sites) {
      if (!worker.owner.equals(site.owner) || !worker.homeId.equals(site.logisticsHome)) continue;
      for (BlockPos pos : site.chests) {
        if (!level.hasChunkAt(pos)
            || ConstructionSupply.reserved(level, pos)
            || (site.flags(pos) & 1) == 0
            || Storage.handler(level, pos, site) == null
            || !seen.add(Storage.canonical(level, pos))) continue;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock block)) continue;
        Container inventory = ChestBlock.getContainer(block, state, level, pos, false);
        if (inventory == null) continue;
        ItemStackHandler after = new ItemStackHandler(inventory.getContainerSize());
        for (int i = 0; i < after.getSlots(); i++)
          after.setStackInSlot(i, inventory.getItem(i).copy());
        destinations.add(new Chest(inventory, after));
      }
    }
    for (ItemStack stack : overflow) {
      ItemStack rest = stack;
      for (Chest chest : destinations) {
        rest = Inventories.insert(chest.after, rest, false);
        if (rest.isEmpty()) break;
      }
      if (!rest.isEmpty()) return null;
    }
    // Vanilla containers are committed together on the server thread, after all capacity checks.
    for (Chest chest : destinations) {
      for (int i = 0; i < chest.after.getSlots(); i++)
        chest.container.setItem(i, chest.after.getStackInSlot(i));
      chest.container.setChanged();
    }
    network.changed(level);
    return cargo;
  }
}
