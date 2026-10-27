package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraftforge.items.IItemHandler;

/** Machine cargo is a real logistics endpoint, never a second copy of its contents. */
public final class MachineHauling {
  public static LogisticsNetwork network(Machine m) {
    if (m.homeId == null || m.owner == null) return null;
    return WorldData.get((ServerLevel) m.level())
        .logistics
        .computeIfAbsent(
            m.homeId,
            id -> {
              LogisticsNetwork n = new LogisticsNetwork();
              n.home = id;
              n.owner = m.owner;
              return n;
            });
  }

  public static void request(Machine m) {
    LogisticsNetwork network = network(m);
    if (network == null) return;
    publish(network, m);
    WorldData.get((ServerLevel) m.level()).setDirty();
  }

  public static void publish(LogisticsNetwork n, Machine m) {
    String key = "entity:" + m.getUUID();
    var report = n.reports.computeIfAbsent(key, k -> new LogisticsNetwork.Report());
    report.endpoint = new LogisticsNetwork.Endpoint(m.homeId, m.grid, m.getUUID());
    report.loaded = !m.isRemoved();
    report.output = true;
    report.supply = true;
    report.warehouse = false;
    report.usableSlots = m.cargo.getSlots() - m.usedSlots();
    List<ItemStack> items = new ArrayList<>();
    for (int i = 0; i < m.cargo.getSlots(); i++)
      if (!m.cargo.getStackInSlot(i).isEmpty()) items.add(m.cargo.getStackInSlot(i).copy());
    report.contents = items;
  }

  public static List<IItemHandler> docks(Machine m, BlockPos start) {
    List<IItemHandler> result = new ArrayList<>();
    ServerLevel l = (ServerLevel) m.level();
    List<Station> sites = new ArrayList<>();
    if (m.pedestal() != null) sites.add(m.pedestal());
    for (BuildingProject project : WorldData.get(l).buildings.values())
      if (Objects.equals(project.home, m.homeId)
          && Objects.equals(project.owner, m.owner)
          && l.hasChunkAt(project.anchor)
          && l.getBlockEntity(project.anchor) instanceof Station s) sites.add(s);
    Set<BlockPos> seen = new HashSet<>();
    for (Station s : sites)
      for (BlockPos p : s.chests)
        if (p.distSqr(start) <= 36 && seen.add(Storage.canonical(l, p))) {
          IItemHandler handler = Storage.handler(l, p, s);
          if (handler != null) result.add(handler);
        }
    for (BlockPos cell : BlockPos.betweenClosed(start.offset(-4, -2, -4), start.offset(4, 2, 4))) {
      if (!l.hasChunkAt(cell) || !Storage.isChest(l, cell)) continue;
      BlockPos p = Storage.canonical(l, cell);
      if (!seen.add(p) || !Permissions.allowed(l, p, m.owner, Permissions.Action.INVENTORY))
        continue;
      UUID protector =
          WorldData.get(l).protection.get(new net.minecraft.world.level.ChunkPos(p).toLong());
      if (protector != null && !protector.equals(m.owner)) continue;
      var state = l.getBlockState(p);
      if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest) {
        var container =
            net.minecraft.world.level.block.ChestBlock.getContainer(chest, state, l, p, false);
        if (container != null)
          result.add(new net.minecraftforge.items.wrapper.InvWrapper(container));
      } else if (l.getBlockEntity(p) != null)
        l.getBlockEntity(p)
            .getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
            .ifPresent(result::add);
    }
    return result;
  }

  private MachineHauling() {}
}
