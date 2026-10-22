package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;

public class WorkMenu extends AbstractContainerMenu {
  public final Station station;
  public final Lumberton worker;
  public final Player player;
  public final boolean isWorker, isTotem;
  public final int ownSlots;
  public CompoundTag snapshot = new CompoundTag();
  private int ticks;
  public int chestPage;

  public WorkMenu(int id, Inventory inv, FriendlyByteBuf b) {
    this(id, inv, readTarget(inv, b));
  }

  private WorkMenu(int id, Inventory inv, Object target) {
    this(id, inv, target instanceof Station s ? s : null, target instanceof Lumberton w ? w : null);
  }

  private static Object readTarget(Inventory inv, FriendlyByteBuf b) {
    return b.readBoolean()
        ? inv.player.level().getEntity(b.readInt())
        : inv.player.level().getBlockEntity(b.readBlockPos());
  }

  public WorkMenu(int id, Inventory inv, Station station, Lumberton worker) {
    super(Automatons.MENU.get(), id);
    this.player = inv.player;
    this.station = station;
    this.worker = worker;
    isWorker = worker != null;
    isTotem = station != null && station.isTotem();
    ownSlots = isWorker ? 12 : 4;
    if (isWorker) {
      for (int i = 0; i < 3; i++) addSlot(ownedSlot(worker.tools, i, 26 + i * 42, 44));
      for (int i = 0; i < 9; i++) addSlot(ownedSlot(worker.cargo, i, 8 + (i % 9) * 18, 84));
    } else {
      IItemHandler inventory = station == null ? new ItemStackHandler(4) : station.inventory;
      for (int i = 0; i < 4; i++) addSlot(ownedSlot(inventory, i, 17 + i * 36, 44));
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 210));
  }

  private Slot ownedSlot(IItemHandler handler, int index, int x, int y) {
    return new SlotItemHandler(handler, index, x, y) {
      @Override
      public boolean mayPlace(ItemStack s) {
        return canEdit() && super.mayPlace(s);
      }

      @Override
      public boolean mayPickup(Player p) {
        return canEdit() && super.mayPickup(p);
      }
    };
  }

  public boolean canEdit() {
    if (player.level().isClientSide) return snapshot.getBoolean("Editable");
    return worker != null ? worker.owns(player) : station != null && station.owns(player);
  }

  @Override
  public boolean stillValid(Player p) {
    if (worker != null)
      return worker.isAlive() && worker.level() == p.level() && worker.distanceToSqr(p) < 64;
    return station != null
        && !station.isRemoved()
        && station.getLevel() == p.level()
        && p.distanceToSqr(
                station.getBlockPos().getX() + .5,
                station.getBlockPos().getY() + .5,
                station.getBlockPos().getZ() + .5)
            < 64;
  }

  @Override
  public ItemStack quickMoveStack(Player p, int i) {
    if (!canEdit() || i < 0 || i >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(i);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack current = slot.getItem(), copy = current.copy();
    boolean moved;
    if (i < ownSlots) moved = moveItemStackTo(current, ownSlots, slots.size(), true);
    else if (isWorker)
      moved =
          Inventories.axe(current)
              ? moveItemStackTo(current, 0, 3, false)
              : moveItemStackTo(current, 3, 12, false);
    else moved = moveItemStackTo(current, 0, 4, false);
    if (!moved) return ItemStack.EMPTY;
    if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(p, current);
    return copy;
  }

  @Override
  public boolean clickMenuButton(Player p, int action) {
    if (!canEdit() || !stillValid(p) || p.level().isClientSide) return false;
    if (station != null && !isTotem) {
      if (action == 0) station.create((ServerPlayer) p);
      if (action == 1) station.recall(true);
      if (action == 2) station.recall(false);
    }
    if (worker != null && action == 3) {
      ItemStack wand = WandItem.held(p);
      if (wand.isEmpty()) {
        p.displayClientMessage(
            Component.literal("Hold your wand to select a replacement pedestal"), true);
        return false;
      }
      WandItem.select(wand, worker, true);
      p.closeContainer();
      p.displayClientMessage(
          Component.literal("Rebind selected: right-click your replacement pedestal"), true);
    }
    if (station != null && isTotem) {
      if (action == 4) chestPage = Math.max(0, chestPage - 1);
      if (action == 5)
        chestPage = Math.min(Math.max(0, (station.chests.size() - 1) / 4), chestPage + 1);
      if (action >= 20 && action < 28) {
        int row = (action - 20) / 2;
        station.toggleChest(chestPage * 4 + row, (action - 20) % 2 == 0 ? 1 : 2);
      }
    }
    broadcastChanges();
    return true;
  }

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (player instanceof ServerPlayer p && ticks++ % 10 == 0)
      Network.snapshot(p, containerId, describe());
  }

  public CompoundTag describe() {
    CompoundTag n = new CompoundTag();
    n.putBoolean("Editable", canEdit());
    ListTag lines = new ListTag();
    Lumberton w = worker != null ? worker : station == null ? null : station.loadedWorker();
    if (w != null) {
      add(lines, w.getDisplayName().getString());
      add(lines, w.status());
      add(lines, "Home: " + (w.home == null ? "missing" : xyz(w.home)));
      add(lines, "Totem: " + (w.totem == null ? "unassigned" : xyz(w.totem)));
      n.putInt("Active", w.activeSlot());
      for (int i = 0; i < 3; i++) {
        ItemStack t = w.tools.getStackInSlot(i);
        add(
            lines,
            "Tool "
                + (i + 1)
                + (w.activeSlot() == i ? " * " : ": ")
                + (t.isEmpty()
                    ? "empty"
                    : t.getHoverName().getString()
                        + " "
                        + (t.isDamageableItem()
                            ? (t.getMaxDamage() - t.getDamageValue()) + "/" + t.getMaxDamage()
                            : "")));
      }
      int a = w.alerts;
      for (int bit : new int[] {1, 2, 4, 8})
        if ((a & bit) != 0)
          add(
              lines,
              switch (bit) {
                case 1 -> "! No usable tools";
                case 2 -> "! No eligible wood";
                case 4 -> "! Storage full";
                default -> "! Cargo full";
              });
    }
    if (station != null) {
      if (isTotem) {
        add(
            lines,
            "Storage: "
                + station.storageRange()
                + " outward / "
                + (station.storageRange() * 2 + 1)
                + " x "
                + (station.storageRange() * 2 + 1));
        add(
            lines,
            "Lumber: "
                + station.lumberRange()
                + " outward / "
                + (station.lumberRange() * 2 + 1)
                + " x "
                + (station.lumberRange() * 2 + 1));
        add(lines, "Trees queued: " + station.trees.size());
        if (w == null)
          add(lines, station.worker == null ? "Unassigned totem" : "Worker temporarily unloaded");
        add(lines, station.scanReason);
      } else {
        add(lines, station.recalled ? "Workers recalled" : "Work enabled");
        add(lines, "Workers registered: " + station.workers.size());
        var c =
            new net.minecraft.world.SimpleContainer(
                station.inventory.getStackInSlot(0),
                station.inventory.getStackInSlot(1),
                station.inventory.getStackInSlot(2),
                station.inventory.getStackInSlot(3));
        boolean ready =
            station
                .getLevel()
                .getRecipeManager()
                .getRecipeFor(Automatons.CREATION.get(), c, station.getLevel())
                .isPresent();
        n.putBoolean("Ready", ready);
        add(lines, ready ? "Recipe ready" : "6 oak logs + 4 iron ingots");
        if (!ready) add(lines, "1 amethyst + 1 iron axe");
      }
      ListTag chests = new ListTag();
      for (int i = chestPage * 4; i < Math.min(station.chests.size(), chestPage * 4 + 4); i++) {
        BlockPos pos = station.chests.get(i);
        CompoundTag c = new CompoundTag();
        c.putString("Pos", xyz(pos));
        c.putInt("Flags", station.flags(pos));
        chests.add(c);
      }
      n.put("Chests", chests);
      n.putInt("Page", chestPage + 1);
      n.putInt("ChestCount", station.chests.size());
    }
    n.put("Lines", lines);
    return n;
  }

  private static void add(ListTag l, String text) {
    l.add(StringTag.valueOf(text));
  }

  public static String xyz(BlockPos p) {
    return p.getX() + ", " + p.getY() + ", " + p.getZ();
  }
}
