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
    return Math.abs(p.getX() - s.getBlockPos().getX()) <= s.storageRange()
        && Math.abs(p.getZ() - s.getBlockPos().getZ()) <= s.storageRange()
        && Math.abs(p.getY() - s.getBlockPos().getY()) <= Balance.HEIGHT.get();
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

  public static boolean usable(Lumberton w, BlockPos p) {
    return w.canReach(p);
  }

  public static BlockPos choose(Lumberton w, Station s, boolean supply) {
    ServerLevel l = (ServerLevel) w.level();
    for (BlockPos p : s.chests) {
      if ((s.flags(p) & (supply ? 2 : 1)) == 0) continue;
      IItemHandler h = handler(l, p, s);
      if (h == null) continue;
      boolean relevant = false;
      if (supply) {
        for (int i = 0; i < h.getSlots(); i++)
          if (Inventories.axe(h.getStackInSlot(i))) relevant = true;
      } else {
        for (int i = 0; i < w.cargo.getSlots(); i++)
          if (!w.cargo.getStackInSlot(i).isEmpty() && accepts(h, w.cargo.getStackInSlot(i)))
            relevant = true;
        if (w.cargoEmpty()) relevant = accepts(h, w.expectedOutput());
      }
      if (relevant && usable(w, p)) return p;
    }
    return null;
  }

  public static boolean anyOutput(Station s, ServerLevel l) {
    return s.chests.stream()
        .anyMatch(p -> (s.flags(p) & 1) != 0 && l.hasChunkAt(p) && isChest(l, p));
  }

  public static boolean anyOpenOutput(Station s, ServerLevel l) {
    return s.chests.stream().anyMatch(p -> (s.flags(p) & 1) != 0 && handler(l, p, s) != null);
  }

  public static boolean anySpace(Lumberton w, Station s) {
    for (BlockPos p : s.chests) {
      if ((s.flags(p) & 1) == 0) continue;
      IItemHandler h = handler((ServerLevel) w.level(), p, s);
      if (h == null) continue;
      if (w.cargoEmpty()) {
        if (accepts(h, w.expectedOutput())) return true;
      } else
        for (int i = 0; i < w.cargo.getSlots(); i++)
          if (!w.cargo.getStackInSlot(i).isEmpty() && accepts(h, w.cargo.getStackInSlot(i)))
            return true;
    }
    return false;
  }

  public static void transfer(Lumberton w, Station s, BlockPos p) {
    if (w.distanceToSqr(p.getX() + .5, p.getY() + .5, p.getZ() + .5) > 9) return;
    IItemHandler h = handler((ServerLevel) w.level(), p, s);
    if (h == null) return;
    if ((s.flags(p) & 1) != 0) {
      for (int i = 0; i < w.cargo.getSlots(); i++) {
        ItemStack stack = w.cargo.getStackInSlot(i);
        if (stack.isEmpty()) continue;
        ItemStack rest = stack.copy();
        for (int j = 0; j < h.getSlots() && !rest.isEmpty(); j++)
          rest = h.insertItem(j, rest, false);
        w.cargo.setStackInSlot(i, rest);
      }
    }
    if ((s.flags(p) & 2) != 0) {
      for (int slot = 0; slot < 3; slot++)
        if (w.tools.getStackInSlot(slot).isEmpty())
          for (int i = 0; i < h.getSlots(); i++) {
            ItemStack preview = h.extractItem(i, 1, true);
            if (!Inventories.axe(preview)) continue;
            ItemStack actual = h.extractItem(i, 1, false);
            ItemStack rest = w.tools.insertItem(slot, actual, false);
            if (!rest.isEmpty()) {
              rest = h.insertItem(i, rest, false);
              if (!rest.isEmpty()) w.spawnAtLocation(rest);
            }
            break;
          }
    }
    w.selectTool();
    w.smile();
  }
}
