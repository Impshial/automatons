package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.*;

public final class Storage {
  public static final net.minecraft.tags.TagKey<Block> CHESTS =
      net.minecraft.tags.TagKey.create(
          net.minecraft.core.registries.Registries.BLOCK, Automatons.id("storage_chests"));

  public static boolean inRange(Station s, BlockPos p) {
    return Math.abs(p.getX() - s.getBlockPos().getX()) <= range(s)
        && Math.abs(p.getZ() - s.getBlockPos().getZ()) <= range(s)
        && Math.abs(p.getY() - s.getBlockPos().getY()) <= Balance.HEIGHT.get();
  }

  public static int range(Station station) {
    // The 96-block hunt area must not make every network chest into hunting-shed stock.
    return station.purpose == WorksitePurpose.HUNT ? 8 : station.workRange();
  }

  public static boolean isChest(ServerLevel l, BlockPos p) {
    return l.getBlockState(p).getBlock() instanceof ChestBlock || l.getBlockState(p).is(CHESTS);
  }

  public static BlockPos canonical(ServerLevel l, BlockPos p) {
    var s = l.getBlockState(p);
    if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
      BlockPos q = p.relative(ChestBlock.getConnectedDirection(s));
      return p.asLong() < q.asLong() ? p : q;
    }
    return p;
  }

  public static IItemHandler handler(ServerLevel l, BlockPos p, Station station) {
    if (!inRange(station, p)) return null;
    BuildingProject warehouse = WorldData.get(l).buildings.get(station.identity);
    if (warehouse != null
        && warehouse.ready
        && Objects.equals(warehouse.owner, station.owner)
        && warehouse.chests(l).contains(p)) return warehouse.handler(l, p);
    if (!l.hasChunkAt(p) || !isChest(l, p) || !inRange(station, p)) return null;
    var s = l.getBlockState(p);
    if (s.getBlock() instanceof ChestBlock chest) {
      if (s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
        BlockPos q = p.relative(ChestBlock.getConnectedDirection(s));
        if (!l.hasChunkAt(q) || !inRange(station, q)) return null;
      }
      Container c = ChestBlock.getContainer(chest, s, l, p, false);
      return c == null ? null : new net.minecraftforge.items.wrapper.InvWrapper(c);
    }
    var be = l.getBlockEntity(p);
    return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
  }

  public static boolean accepts(IItemHandler h, ItemStack stack) {
    for (int i = 0; i < h.getSlots(); i++)
      if (h.insertItem(i, stack, true).getCount() < stack.getCount()) return true;
    return false;
  }

  /** Only this mine's four placed chest cells bypass the Totem surface range. */
  public static IItemHandler depotHandler(ServerLevel l, BlockPos p, MinePlan mine) {
    if (!mine.depotBuilt || !mine.depotCell(p) || !l.hasChunkAt(p)) return null;
    var state = l.getBlockState(p);
    if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
    BlockPos other = p.relative(ChestBlock.getConnectedDirection(state));
    if (!mine.depotCell(other)
        || !l.hasChunkAt(other)
        || !mine.placedChests.contains(other.asLong())) return null;
    Container container = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, state, l, p, false);
    return container == null || container.getContainerSize() != 54
        ? null
        : new net.minecraftforge.items.wrapper.InvWrapper(container);
  }

  public static boolean usable(Lumberton w, BlockPos p) {
    return w.canReach(p);
  }

  public static BlockPos choose(Lumberton w, Station s, boolean supply) {
    ServerLevel l = (ServerLevel) w.level();
    w.pathDeferred = false;
    for (BlockPos p :
        s.chests.stream()
            .sorted(
                Comparator.comparingDouble(
                        (BlockPos p) ->
                            w.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)))
                    .thenComparingLong(BlockPos::asLong))
            .toList()) {
      if ((s.flags(p) & (supply ? 2 : 1)) == 0) continue;
      IItemHandler h = handler(l, p, s);
      if (h == null) continue;
      boolean relevant = false;
      if (supply) {
        for (int i = 0; i < h.getSlots(); i++)
          if (w.job() == 2
              ? w.mining.wants(h.extractItem(i, 64, true))
              : Inventories.tool(w.job(), h.extractItem(i, 1, true))) relevant = true;
      } else {
        for (int i = 0; i < w.cargo.getSlots(); i++)
          if (!w.cargo.getStackInSlot(i).isEmpty()
              && w.mining.carried(w.cargo.getStackInSlot(i).getItem())
                  > w.cargoKeep(w.cargo.getStackInSlot(i))
              && accepts(h, w.cargo.getStackInSlot(i))) relevant = true;
      }
      if (relevant && usable(w, p)) return p;
      if (w.pathDeferred) return null;
    }
    return null;
  }

  public static boolean anyOutput(Station s, ServerLevel l) {
    return s.chests.stream()
        .anyMatch(p -> (s.flags(p) & 1) != 0 && inRange(s, p) && l.hasChunkAt(p) && isChest(l, p));
  }

  public static boolean anyOpenOutput(Station s, ServerLevel l) {
    return s.chests.stream().anyMatch(p -> (s.flags(p) & 1) != 0 && handler(l, p, s) != null);
  }

  public static boolean anySpace(Lumberton w, Station s) {
    for (BlockPos p : s.chests) {
      if ((s.flags(p) & 1) == 0) continue;
      IItemHandler h = handler((ServerLevel) w.level(), p, s);
      if (h == null) continue;
      // A bounded next output must be acceptable; unrelated partial stacks are not capacity.
      if (accepts(h, w.expectedOutput())) return true;
    }
    return false;
  }

  public static void transfer(Lumberton w, Station s, BlockPos p) {
    if (!w.inReach(p)) return;
    IItemHandler h = handler((ServerLevel) w.level(), p, s);
    transfer(w, s, p, h, (s.flags(p) & 1) != 0, (s.flags(p) & 2) != 0);
  }

  public static void transfer(
      Lumberton w, Station s, BlockPos p, IItemHandler h, boolean output, boolean supply) {
    if (!w.inReach(p)) return;
    if (h == null) return;
    if (output) {
      Map<Item, Integer> retained = new HashMap<>();
      for (int i = 0; i < w.cargo.getSlots(); i++) {
        ItemStack stack = w.cargo.getStackInSlot(i);
        if (stack.isEmpty()) continue;
        int keep =
            Math.min(
                stack.getCount(),
                Math.max(0, w.cargoKeep(stack) - retained.getOrDefault(stack.getItem(), 0)));
        retained.merge(stack.getItem(), keep, Integer::sum);
        ItemStack rest = stack.copyWithCount(stack.getCount() - keep);
        for (int j = 0; j < h.getSlots() && !rest.isEmpty(); j++)
          rest = h.insertItem(j, rest, false);
        if (keep > 0) rest = stack.copyWithCount(rest.getCount() + keep);
        w.cargo.setStackInSlot(i, rest);
      }
    }
    if (w.job() != 0 && w.job() != 3 && supply) {
      for (int slot = 0; slot < 3; slot++)
        if (w.tools.getStackInSlot(slot).isEmpty())
          for (int i = 0; i < h.getSlots(); i++) {
            ItemStack preview = h.extractItem(i, 1, true);
            if (!Inventories.tool(w.job(), preview)) continue;
            ItemStack actual = h.extractItem(i, 1, false);
            ItemStack rest = w.tools.insertItem(slot, actual, false);
            if (!rest.isEmpty()) {
              rest = h.insertItem(i, rest, false);
              if (!rest.isEmpty()) w.spawnAtLocation(rest);
            }
            break;
          }
      if (w.job() == 2) {
        for (int source = 0; source < h.getSlots(); source++) {
          ItemStack preview = h.extractItem(source, 1, true);
          int slot = w.mining.replacementSlot(preview);
          if (slot < 0) continue;
          ItemStack actual = h.extractItem(source, 1, false);
          if (w.mining.replacementSlot(actual) != slot) {
            ItemStack rest = h.insertItem(source, actual, false);
            if (!rest.isEmpty()) w.spawnAtLocation(rest);
            continue;
          }
          ItemStack old = w.tools.getStackInSlot(slot);
          ItemStack rest = h.insertItem(source, old.copy(), true);
          if (!rest.isEmpty()) {
            rest = h.insertItem(source, actual, false);
            if (!rest.isEmpty()) w.spawnAtLocation(rest);
            continue;
          }
          rest = h.insertItem(source, old.copy(), false);
          if (rest.isEmpty()) w.tools.setStackInSlot(slot, actual);
          else {
            rest = h.insertItem(source, actual, false);
            if (!rest.isEmpty()) w.spawnAtLocation(rest);
          }
          break;
        }
        for (var need : w.mining.reserve().entrySet()) {
          int missing = need.getValue() - w.mining.carried(need.getKey());
          for (int i = 0; i < h.getSlots() && missing > 0; i++) {
            ItemStack preview = h.extractItem(i, missing, true);
            if (!preview.is(need.getKey())) continue;
            int amount = preview.getCount() - Inventories.insert(w.cargo, preview, true).getCount();
            if (amount <= 0) continue;
            ItemStack actual = h.extractItem(i, amount, false);
            ItemStack rest = Inventories.insert(w.cargo, actual, false);
            if (!rest.isEmpty()) {
              rest = h.insertItem(i, rest, false);
              if (!rest.isEmpty()) w.spawnAtLocation(rest);
            }
            missing = need.getValue() - w.mining.carried(need.getKey());
          }
        }
      }
    }
    w.selectTool();
    w.smile();
  }
}
