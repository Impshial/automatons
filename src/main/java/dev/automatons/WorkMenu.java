package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;

public class WorkMenu extends AbstractContainerMenu {
  public final Station station;
  public final Lumberton worker;
  public final Player player;
  public final boolean isWorker, isTotem;
  public final int ownSlots, resultSlot;
  public CompoundTag snapshot = new CompoundTag();
  private final SimpleContainer preview = new SimpleContainer(1);
  private int ticks;
  public int chestPage, workerPage;
  public UUID selectedWorker;

  public WorkMenu(int id, Inventory inv, FriendlyByteBuf b) {
    this(id, inv, readTarget(inv, b));
  }

  private WorkMenu(int id, Inventory inv, Object target) {
    this(id, inv, target instanceof Station s ? s : null, target instanceof Lumberton w ? w : null);
  }

  private static Object readTarget(Inventory inv, FriendlyByteBuf b) {
    if (!b.readBoolean()) return inv.player.level().getBlockEntity(b.readBlockPos());
    var entity = inv.player.level().getEntity(b.readInt());
    int slots = b.readVarInt();
    if (entity instanceof Lumberton w && slots >= 1 && slots <= 27 && w.cargo.getSlots() != slots)
      w.cargo.setSize(slots);
    return entity;
  }

  public WorkMenu(int id, Inventory inv, Station station, Lumberton worker) {
    super(Automatons.MENU.get(), id);
    this.player = inv.player;
    this.station = station;
    this.worker = worker;
    isWorker = worker != null;
    isTotem = station != null && station.isTotem();
    resultSlot = !isWorker && !isTotem ? 6 : -1;
    ownSlots = isWorker ? 3 + worker.cargo.getSlots() : isTotem ? 4 : 7;
    if (isWorker) {
      for (int i = 0; i < 3; i++) addSlot(ownedSlot(worker.tools, i, 26 + i * 42, 44));
      for (int i = 0; i < worker.cargo.getSlots(); i++)
        addSlot(ownedSlot(worker.cargo, i, 8 + (i % 9) * 18, 76 + (i / 9) * 18));
    } else if (isTotem) {
      for (int i = 0; i < 4; i++) addSlot(ownedSlot(station.inventory, i, 17 + i * 36, 44));
    } else {
      IItemHandler input = station == null ? new ItemStackHandler(6) : station.inventory;
      int[][] positions = {{80, 31}, {44, 57}, {80, 57}, {116, 57}, {62, 83}, {98, 83}};
      for (int i = 0; i < 6; i++) addSlot(ownedSlot(input, i, positions[i][0], positions[i][1]));
      addSlot(
          new Slot(preview, 0, 230, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
              return false;
            }

            @Override
            public boolean mayPickup(Player p) {
              return false;
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 210));
    refreshPreview();
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
    return worker != null
        ? worker.isAlive() && worker.level() == p.level() && worker.distanceToSqr(p) < 64
        : station != null
            && !station.isRemoved()
            && station.getLevel() == p.level()
            && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(station.getBlockPos()))
                < 64;
  }

  private void refreshPreview() {
    if (resultSlot >= 0 && !player.level().isClientSide)
      preview.setItem(
          0,
          station != null && station.ready()
              ? new ItemStack(Automatons.AUTOMATON_ITEM.get())
              : ItemStack.EMPTY);
  }

  /** No preview stack ever enters a cursor/inventory. Commit only after checking destination. */
  private ItemStack takeResult(Player p, boolean quick) {
    if (!(p instanceof ServerPlayer server)
        || !canEdit()
        || !stillValid(p)
        || station == null
        || !station.ready()) return ItemStack.EMPTY;
    int destination = quick ? p.getInventory().getFreeSlot() : -1;
    if (quick && destination < 0 || !quick && !getCarried().isEmpty()) return ItemStack.EMPTY;
    ItemStack result = station.assemble(server);
    if (result.isEmpty()) return ItemStack.EMPTY;
    if (quick) p.getInventory().setItem(destination, result);
    else setCarried(result);
    refreshPreview();
    broadcastChanges();
    return result.copy();
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player p) {
    if (slot == resultSlot && resultSlot >= 0) {
      if (type == ClickType.PICKUP) takeResult(p, false);
      else if (type == ClickType.QUICK_MOVE) takeResult(p, true);
      return;
    }
    super.clicked(slot, button, type, p);
    refreshPreview();
  }

  @Override
  public ItemStack quickMoveStack(Player p, int index) {
    if (index == resultSlot && resultSlot >= 0) return takeResult(p, true);
    if (!canEdit() || !stillValid(p) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasItem()) return ItemStack.EMPTY;
    ItemStack current = slot.getItem(), copy = current.copy();
    boolean moved;
    if (index < ownSlots) moved = moveItemStackTo(current, ownSlots, slots.size(), true);
    else if (isWorker)
      moved =
          Inventories.axe(current)
              ? moveItemStackTo(current, 0, 3, false)
              : moveItemStackTo(current, 3, ownSlots, false);
    else moved = moveItemStackTo(current, 0, isTotem ? 4 : 6, false);
    if (!moved) return ItemStack.EMPTY;
    if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
    else slot.setChanged();
    slot.onTake(p, current);
    refreshPreview();
    return copy;
  }

  private List<UUID> roster() {
    return station == null ? List.of() : station.workers.stream().sorted().toList();
  }

  @Override
  public boolean clickMenuButton(Player p, int action) {
    if (!canEdit() || !stillValid(p) || p.level().isClientSide) return false;
    if (station != null && !isTotem) {
      if (action == 1) station.recall(true);
      if (action == 2) station.recall(false);
    }
    if (worker != null) {
      if (action == 6) {
        worker.authorized = true;
        WorkerIdentity.update(worker, "Deployed");
      }
      if (action == 7 || action == 8) {
        worker.setJob(action == 8 ? 1 : 0);
        WorkerIdentity.update(worker, "Deployed");
      }
      if (action == 3) {
        ItemStack wand = WandItem.held(p);
        if (wand.isEmpty()) {
          p.displayClientMessage(
              Component.literal("Hold your wand to select a replacement pedestal"), true);
          return false;
        }
        WandItem.select(wand, worker, true);
        p.closeContainer();
        p.displayClientMessage(
            Component.literal("Right-click your replacement Automaton Pedestal"), true);
      }
    }
    if (isTotem) {
      if (action == 4) chestPage = Math.max(0, chestPage - 1);
      if (action == 5)
        chestPage = Math.min(Math.max(0, (station.chests.size() - 1) / 3), chestPage + 1);
      if (action >= 20 && action < 26)
        station.toggleChest(chestPage * 3 + (action - 20) / 2, (action - 20) % 2 == 0 ? 1 : 2);
      if (action == 9) {
        workerPage = Math.max(0, workerPage - 1);
        selectedWorker = null;
      }
      if (action == 10) {
        workerPage = Math.min(Math.max(0, (station.workers.size() - 1) / 4), workerPage + 1);
        selectedWorker = null;
      }
      if (action >= 100 && action < 104) {
        List<UUID> ids = roster();
        int row = workerPage * 4 + action - 100;
        if (row < ids.size()) selectedWorker = ids.get(row);
      }
      if (action == 11 && selectedWorker != null) {
        station.removeMember(selectedWorker);
        selectedWorker = null;
      }
      if (action == 12) selectedWorker = null;
    }
    ticks = 0;
    broadcastChanges();
    return true;
  }

  @Override
  public void broadcastChanges() {
    refreshPreview();
    super.broadcastChanges();
    if (player instanceof ServerPlayer p && ticks++ % 5 == 0)
      Network.snapshot(p, containerId, describe());
  }

  public CompoundTag describe() {
    CompoundTag n = new CompoundTag();
    n.putBoolean("Editable", canEdit());
    n.putInt("Magica", Magica.balance(player));
    n.putInt("MagicaThirds", Magica.remainder(player));
    ListTag lines = new ListTag();
    if (worker != null) {
      add(lines, worker.getDisplayName().getString());
      add(lines, "Job: " + (worker.job() == 1 ? "Lumberton" : "Unassigned"));
      add(lines, worker.status());
      add(lines, "Home: " + (worker.home == null ? "missing" : xyz(worker.home)));
      add(lines, "Totem: " + (worker.totem == null ? "unassigned" : xyz(worker.totem)));
      n.putInt("Active", worker.activeSlot());
      n.putInt("Job", worker.job());
      n.putBoolean("Authorized", worker.authorized);
      for (int i = 0; i < 3; i++) {
        ItemStack tool = worker.tools.getStackInSlot(i);
        add(
            lines,
            "Tool "
                + (i + 1)
                + (worker.job() == 0 ? " (inactive)" : worker.activeSlot() == i ? " *" : "")
                + ": "
                + (tool.isEmpty()
                    ? "empty"
                    : tool.getHoverName().getString()
                        + " "
                        + (tool.isDamageableItem()
                            ? (tool.getMaxDamage() - tool.getDamageValue())
                                + "/"
                                + tool.getMaxDamage()
                            : "")));
      }
      blockers(lines, worker.alerts);
    }
    if (station != null && !isTotem) {
      n.putBoolean("Ready", station.ready());
      add(lines, station.ready() ? "Components ready" : "Add six body parts");
      add(lines, "Taking the result assembles one Automaton.");
      add(lines, "No extra Magica payment");
      add(lines, station.recalled ? "All home workers recalled" : "Work enabled");
      add(lines, "Registered: " + station.workers.size());
    }
    if (isTotem) {
      add(lines, "Storage: " + station.storageRange() + " outward");
      add(lines, "Lumber: " + station.lumberRange() + " outward");
      add(lines, "Trees: " + station.trees.size() + " / timber: " + station.timberJobs().size());
      if (!station.trees.isEmpty())
        add(
            lines,
            "Next: "
                + String.format(
                    java.util.Locale.ROOT,
                    "%.1f",
                    Math.sqrt(station.orderedTrees().get(0).distanceFrom(station)))
                + " blocks from totem");
      add(lines, station.scanComplete ? station.scanReason : "Searching for trees");
      ListTag chests = new ListTag();
      for (int i = chestPage * 3; i < Math.min(station.chests.size(), chestPage * 3 + 3); i++) {
        BlockPos pos = station.chests.get(i);
        CompoundTag c = new CompoundTag();
        c.putString("Pos", xyz(pos));
        c.putInt("Flags", station.flags(pos));
        chests.add(c);
      }
      n.put("Chests", chests);
      n.putInt("Page", chestPage + 1);
      n.putInt("ChestCount", station.chests.size());
      List<UUID> ids = roster();
      ListTag members = new ListTag();
      Ledger ledger = Ledger.get(player.getServer());
      for (int i = workerPage * 4; i < Math.min(ids.size(), workerPage * 4 + 4); i++) {
        UUID id = ids.get(i);
        CompoundTag record = ledger.workers.get(id),
            entry = record == null ? new CompoundTag() : record.copy();
        entry.putUUID("Id", id);
        if (record == null
            || record.getString("State").equals("Deployed")
                && ((ServerLevel) player.level()).getEntity(id) == null)
          entry.putString("Status", "Unverified (unloaded)");
        members.add(entry);
      }
      n.put("Members", members);
      n.putInt("WorkerPage", workerPage + 1);
      n.putInt("WorkerCount", ids.size());
      if (selectedWorker != null && station.workers.contains(selectedWorker)) {
        CompoundTag record = ledger.workers.get(selectedWorker);
        n.putBoolean("HasSelected", true);
        if (record != null) {
          ListTag details = new ListTag();
          add(details, "Worker " + selectedWorker.toString().substring(0, 8));
          add(details, record.getInt("Job") == 1 ? "Lumberton" : "Unassigned");
          add(details, record.getString("State"));
          add(details, record.getString("Status"));
          add(details, record.getBoolean("ToolsReady") ? "Axe ready" : "No active axe");
          if (record.contains("Home"))
            add(details, "Home: " + xyz(BlockPos.of(record.getLong("Home"))));
          blockers(details, record.getInt("Alerts"));
          n.put("Details", details);
        }
      }
      blockers(lines, station.alerts);
    }
    n.put("Lines", lines);
    return n;
  }

  private static void blockers(ListTag lines, int flags) {
    for (int bit : new int[] {1, 2, 4, 8})
      if ((flags & bit) != 0)
        add(
            lines,
            switch (bit) {
              case 1 -> "! No usable tools";
              case 2 -> "! No eligible wood";
              case 4 -> "! Storage full";
              default -> "! Cargo full";
            });
  }

  private static void add(ListTag list, String text) {
    list.add(StringTag.valueOf(text));
  }

  public static String xyz(BlockPos p) {
    return p.getX() + ", " + p.getY() + ", " + p.getZ();
  }
}
