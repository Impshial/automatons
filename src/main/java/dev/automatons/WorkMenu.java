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
  public final int cargoLayoutExtra;
  public CompoundTag snapshot = new CompoundTag();
  private final SimpleContainer preview = new SimpleContainer(1);
  private int ticks;
  public int chestPage, workerPage;
  public UUID selectedWorker;
  public int view;
  public int pendingProfession;
  public long professionConfirmationUntil;
  public String feedback = "";

  public boolean showInventory() { return !player.level().isClientSide || (!isWorker && !isTotem) || view == (isWorker ? 1 : 2); }

  private Slot playerSlot(Inventory inv, int index, int x, int y) {
    return new Slot(inv, index, x, y) { @Override public boolean isActive() { return showInventory(); } };
  }

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
    if (entity instanceof Lumberton w && slots >= 1 && slots <= 54 && w.cargo.getSlots() != slots)
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
    cargoLayoutExtra = isWorker ? Math.max(0, (worker.cargo.getSlots() + 8) / 9 - 3) * 18 : 0;
    resultSlot = !isWorker && !isTotem ? 6 : -1;
    ownSlots = isWorker ? 3 + worker.cargo.getSlots() : isTotem ? 0 : 7;
    if (isWorker) {
      for (int i = 0; i < 3; i++) addSlot(ownedSlot(worker.tools, i, 26 + i * 42, 44));
      for (int i = 0; i < worker.cargo.getSlots(); i++)
        addSlot(ownedSlot(worker.cargo, i, 8 + (i % 9) * 18, 76 + (i / 9) * 18));
    } else if (isTotem) {
      // No upgrade slots; saved legacy items are returned on the owner's next interaction.
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
        addSlot(playerSlot(inv, col + row * 9 + 9, 8 + col * 18, 152 + cargoLayoutExtra + row * 18));
    for (int col = 0; col < 9; col++)
      addSlot(playerSlot(inv, col, 8 + col * 18, 210 + cargoLayoutExtra));
    refreshPreview();
  }

  private Slot ownedSlot(IItemHandler handler, int index, int x, int y) {
    return new SlotItemHandler(handler, index, x, y) {
      @Override public boolean isActive() { return showInventory(); }
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
          worker.job() != 3
                  && (Inventories.tool(snapshot.getInt("Job"), current)
                      || Inventories.tool(worker.job(), current))
              ? moveItemStackTo(current, 0, 3, false)
              : moveItemStackTo(current, 3, ownSlots, false);
    else moved = !isTotem && moveItemStackTo(current, 0, 6, false);
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
      if (action == 15) station.showNames(!station.showNames);
    }
    if (worker != null) {
      if (worker.isGuard() && action == 190) worker.guarding.mode(true);
      if (worker.isGuard() && action == 191 && worker.guarding.assigned() != null) worker.guarding.mode(false);
      if (action == 6) {
        worker.authorized = true;
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
      if (action >= 161 && action <= 171) station.configure(WorksitePurpose.of(action - 160));
      if (action == 240) { station.recalled = !station.recalled; station.changed(); }
      if (station.purpose == WorksitePurpose.CRAFT && action == 230 && !getCarried().isEmpty()) {
        station.craftOrders.put(getCarried().getItem(), Math.min(64, getCarried().getCount())); station.changed();
      }
      if (station.purpose == WorksitePurpose.CRAFT && action == 231 && !getCarried().isEmpty()) {
        station.craftOrders.remove(getCarried().getItem()); station.changed();
      }
      if (station.purpose == WorksitePurpose.RANCH) {
        if (action >= 200 && action <= 203) { int species = action - 200; station.ranch.enabled[species] = !station.ranch.enabled[species]; station.changed(); }
        if (action >= 210 && action <= 213) { int species = action - 210; station.ranch.targets[species] = Math.max(2, station.ranch.targets[species] - 1); station.changed(); }
        if (action >= 220 && action <= 223) { int species = action - 220; station.ranch.targets[species] = Math.min(64, station.ranch.targets[species] + 1); station.changed(); }
      }
      if (action >= 61 && action <= 65) station.configure(WorksitePurpose.of(action - 60));
      if (action == 66 && station.project() == null) {
        station.orientation = station.orientation.getClockWise();
        station.changed();
      }
      if (action == 70 && station.farm != null) {
        station.farm.boneMeal = !station.farm.boneMeal;
        station.farm.changed((ServerLevel) p.level(), station);
      }
      if (station.purpose == WorksitePurpose.FARM && station.farm != null) {
        if (action >= 180 && action <= 182 && station.farm.select(action - 180, getCarried()))
          station.farm.changed((ServerLevel)p.level(), station);
        if (action >= 185 && action <= 187 && station.farm.select(action - 185, ItemStack.EMPTY))
          station.farm.changed((ServerLevel)p.level(), station);
      }
      if (action >= 40 && action <= 43) {
        var warehouse = WorldData.get((ServerLevel) p.level()).buildings.get(station.identity);
        if (warehouse != null) {
          if (action == 40) warehouse.paused = false;
          if (action == 41) warehouse.paused = true;
          if (action == 42 && !warehouse.approved) warehouse.front = warehouse.front.getClockWise();
          if (action == 43) warehouse.repair((ServerLevel) p.level());
          WorldData.get((ServerLevel) p.level()).setDirty();
          station.changed();
        }
      }
      if (action >= 30 && action <= 33) {
        if (station.mine == null) station.mine = new MinePlan();
        if (action == 30 || action == 31) station.mine.switchMode(MinePlan.Mode.TUNNEL);
        if (action == 32 || action == 33) {
          station.mine.paused = action == 33;
          station.mine.revision++;
          station.mine.builder = null;
        }
        station.changed();
      }
      if (action == 4) chestPage = Math.max(0, chestPage - 1);
      if (action == 5)
        chestPage = Math.min(Math.max(0, (station.chests.size() - 1) / 3), chestPage + 1);
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
      if (action == 14
          && selectedWorker != null
          && station.workers.contains(selectedWorker)
          && p.level() instanceof ServerLevel l
          && l.getEntity(selectedWorker) instanceof Lumberton found
          && found.owns(p)
          && station.identity.equals(found.totemId)
          && found.isAlive()
          && !found.packing) {
        found.addEffect(
            new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING, 600, 0, false, false));
      }
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
    Station buildStation = isTotem ? station : worker == null ? null : worker.station();
    if (worker != null
        && worker.job() == WorkerJob.BUILDINGTON
        && worker.building.active() != null
        && player.level().getBlockEntity(worker.building.active().anchor)
            instanceof Station destination) buildStation = destination;
    if (buildStation != null) {
      n.put("MineBuild", BuildMaterials.describe(buildStation, worker, false));
      n.put("WarehouseBuild", BuildMaterials.describe(buildStation, worker, true));
      n.putString("BuildTitle", buildStation.purpose.label + " — Build List");
    }
    n.putBoolean("Editable", canEdit());
    n.putInt("Lumina", Lumina.balance(player));
    n.putInt("LuminaThirds", Lumina.remainder(player));
    if (station != null && !isTotem) n.putBoolean("ShowNames", station.showNames);
    ListTag lines = new ListTag();
    if (worker != null) {
      add(lines, worker.getDisplayName().getString());
      n.putString("WorkerName", worker.getDisplayName().getString());
      n.putString("Activity", worker.status());
      n.putFloat("Health", worker.getHealth());
      n.putFloat("MaxHealth", worker.getMaxHealth());
      n.putInt("Operating", worker.operating());
      n.putInt("Service", worker.serviceReserve());
      n.putInt("Armor", worker.getArmorValue());
      n.put("Guarding", worker.guarding.save());
      add(lines, "Job: " + WorkerJob.name(worker.job()));
      status(lines, worker.status());
      if (worker.job() == 2)
        add(lines, "Torches: " + worker.mining.carried(net.minecraft.world.item.Items.TORCH));
      if (worker.job() == 3) {
        int occupied = 0;
        for (int i = 0; i < worker.cargo.getSlots(); i++)
          if (!worker.cargo.getStackInSlot(i).isEmpty()) occupied++;
        translated(lines, "automatons.runner.cargo", occupied, worker.cargo.getSlots());
        BuildingProject warehouse = worker.running.warehouse();
        status(lines, warehouse == null ? "Warehouse unassigned" : warehouse.reason);
      }
      if (worker.job() != 3) {
        add(lines, "Home: " + (worker.home == null ? "missing" : xyz(worker.home)));
        add(lines, "Totem: " + (worker.totem == null ? "unassigned" : xyz(worker.totem)));
      }
      n.putInt("Active", worker.activeSlot());
      n.putInt("Job", worker.job());
      n.putBoolean("Authorized", worker.authorized);
      for (int i = 0; i < 3; i++) {
        ItemStack tool = worker.tools.getStackInSlot(i);
        if (worker.job() == 3 || worker.job() == WorkerJob.OPERATORTON) continue;
        add(
            lines,
            "Tool "
                + (i + 1)
                + (!Inventories.tool(worker.job(), tool)
                    ? " (inactive)"
                    : worker.activeSlot() == i ? " *" : "")
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
      n.put("ActiveBuilds", BuildMaterials.network(station));
      n.put("Ranch", station.ranch.save());
      ListTag craftOrders = new ListTag(); station.craftOrders.forEach((item, count) -> craftOrders.add(new ItemStack(item, count).save(new CompoundTag()))); n.put("CraftOrders", craftOrders);
      n.putBoolean("Ready", station.ready());
      add(lines, station.recalled ? "Work disabled" : "Work enabled");
      for (BuildingProject project : WorldData.get((ServerLevel) player.level()).buildings.values())
        if (station.identity.equals(project.home))
          add(lines, project.purpose.label + ": " + project.reason);
      Ledger ledger = Ledger.get(player.getServer());
      for (UUID id : station.workers.stream().sorted().toList()) {
        CompoundTag record = ledger.workers.get(id);
        if (record != null)
          add(lines, WorkerNames.record(ledger, id, record, ((ServerLevel) player.level()).random));
      }
    }
    if (isTotem) {
      n.putInt("Purpose", station.purpose.ordinal());
      n.put("Ranch", station.ranch.save());
      ListTag craftOrders = new ListTag(); station.craftOrders.forEach((item, count) -> craftOrders.add(new ItemStack(item, count).save(new CompoundTag()))); n.put("CraftOrders", craftOrders);
      n.putBoolean("Configured", station.project() != null);
      n.putString("Orientation", station.orientation.getName());
      ListTag worksite = new ListTag();
      if (!station.setupIssue.isEmpty()) add(worksite, "Site unavailable: " + station.setupIssue);
      add(worksite, "Purpose: " + station.purpose.label);
      add(worksite, "Range: " + station.workRange() + " blocks for work and chests");
      BuildingProject project = station.project();
      if (project == null)
        add(worksite, "Choose a purpose and entrance direction, then create the worksite.");
      else {
        add(worksite, project.reason);
        add(worksite, "Entrance: " + project.front.getName());
        add(
            worksite,
            "Built: "
                + project.completed.size()
                + " / "
                + project.template((ServerLevel) player.level()).placements().size());
        if (project.builder != null) {
          CompoundTag record = Ledger.get(player.getServer()).workers.get(project.builder);
          add(
              worksite,
              "Builder: "
                  + (record == null ? "Assigned (unloaded)" : WorkerNames.recordName(record)));
        } else if (!project.ready) add(worksite, "Awaiting Buildington");
      }
      n.put("WorksiteLines", worksite);
      ListTag farming = new ListTag();
      if (station.farm == null) add(farming, "Assign a Farmington after the shed is ready.");
      else {
        FarmPlan f = station.farm;
        add(farming, f.reason);
        add(farming, "Shared crop cells: " + f.cells.size());
        n.putIntArray("Crops", f.crops);
        for (int strip = 0; strip < 3; strip++)
          add(farming, List.of("Left: ", "Middle: ", "Right: ").get(strip)
              + (f.crops[strip] == 0 ? "No planting" : new ItemStack(FarmPlan.PLANTS.get(f.crops[strip] - 1)).getHoverName().getString()));
        add(farming, "Use bone meal: " + (f.boneMeal ? "ON (optional)" : "OFF"));
        f.reserves((ServerLevel) player.level())
            .forEach(
                (item, count) ->
                    add(
                        farming,
                        "Replant reserve: "
                            + count
                            + " "
                            + new ItemStack(item).getHoverName().getString()));
        add(farming, "Field supplies are separate from shed construction.");
      }
      n.put("FarmLines", farming);
      ListTag mining = new ListTag();
      add(
          mining,
          "Underground extent: "
              + station.miningRange()
              + " ("
              + (station.miningRange() * 2 + 1)
              + " x "
              + (station.miningRange() * 2 + 1)
              + ")");
      if (station.mine != null) {
        MinePlan m = station.mine;
        n.putBoolean("HasMine", true);
        n.putBoolean("MinePaused", m.paused);
        n.putInt("MineMode", m.mode.ordinal());
        add(
            mining,
            m.mode == MinePlan.Mode.TUNNEL
                ? "Tunnel: independent 1 x 2, 32-block branches"
                : "Quarry: 5 x 5, 3 x 3 center");
        add(mining, "Entrance: " + (m.entrance == null ? "searching" : xyz(m.entrance)));
        add(mining, "Platform: " + m.shaftDepth + " blocks down");
        add(
            mining,
            m.depotBuilt ? "Storage: 2 underground double chests" : "Storage: needs 4 chests");
        add(
            mining,
            "Direction: " + m.workFacing().getName() + "; wet routes: " + m.wetRoutes.size());
        add(mining, "Stage: " + m.stage.name().toLowerCase(java.util.Locale.ROOT));
        add(mining, m.paused ? "Paused" : m.reason);
        if (m.stage != MinePlan.Stage.SITE && m.stage != MinePlan.Stage.READY)
          add(
              mining,
              "Construction: "
                  + m.construction
                  + "/"
                  + m.constructionTasks((ServerLevel) player.level()).size());
        if (m.mode == MinePlan.Mode.TUNNEL)
          for (MinePlan.Tunnel t : m.tunnels) {
            CompoundTag record =
                t.worker == null ? null : Ledger.get(player.getServer()).workers.get(t.worker);
            add(
                mining,
                (record == null ? "Branch" : WorkerNames.recordName(record))
                    + ": "
                    + m.facing(t.route).getName()
                    + " lane "
                    + (t.lane + 1)
                    + ", "
                    + Math.min(32, t.column)
                    + "/32"
                    + (t.wet ? " (water/lava)" : t.finished ? " (done)" : ""));
          }
        else add(mining, "Quarry depth: " + m.quarryLayer + "; exclusions: " + m.exclusions.size());
        add(
            mining,
            "Miners: " + station.loadedWorkers().stream().filter(w -> w.job() == 2).count());
        Lumberton builder =
            station.loadedWorkers().stream().filter(w -> w.job() == 2).findFirst().orElse(null);
        if (builder != null)
          for (var need : builder.mining.requiredStock().entrySet()) {
            int available = builder.mining.carried(need.getKey());
            for (BlockPos p : station.chests)
              if ((station.flags(p) & 2) != 0) {
                var h = Storage.handler((ServerLevel) player.level(), p, station);
                if (h != null)
                  for (int i = 0; i < h.getSlots(); i++)
                    if (h.getStackInSlot(i).is(need.getKey()))
                      available += h.getStackInSlot(i).getCount();
              }
            add(
                mining,
                new ItemStack(need.getKey()).getHoverName().getString()
                    + ": "
                    + available
                    + "/"
                    + need.getValue()
                    + " (missing "
                    + Math.max(0, need.getValue() - available)
                    + ")");
          }
      } else add(mining, "Assign a Minington to build a shared mine.");
      n.put("MiningLines", mining);
      ListTag forestry = new ListTag();
      add(forestry, "Worksite range: " + station.workRange() + " blocks (work and chests)");
      add(forestry, "Trees: " + station.trees.size() + " / timber: " + station.timberJobs().size());
      if (!station.trees.isEmpty())
        add(
            forestry,
            "Next: "
                + String.format(
                    java.util.Locale.ROOT,
                    "%.1f",
                    Math.sqrt(station.orderedTrees().get(0).distanceFrom(station)))
                + " blocks from totem");
      add(forestry, station.scanComplete ? station.scanReason : "Searching for trees");
      n.put("ForestryLines", forestry);
      List<UUID> ids = roster();
      ListTag members = new ListTag();
      Ledger ledger = Ledger.get(player.getServer());
      for (int i = workerPage * 4; i < Math.min(ids.size(), workerPage * 4 + 4); i++) {
        UUID id = ids.get(i);
        CompoundTag record = ledger.workers.get(id),
            entry = record == null ? new CompoundTag() : record.copy();
        if (record != null)
          WorkerNames.record(ledger, id, entry, ((ServerLevel) player.level()).random);
        else if (ledger.names.name(id) != null) WorkerNames.writeName(entry, ledger.names.name(id));
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
      n.putInt("ProductionCount", Assignments.productionCount(station));
      ListTag guards = new ListTag();
      for (UUID guard : station.guards) {
        CompoundTag record = ledger.workers.get(guard);
        if (record != null && !record.getString("State").equals("Destroyed")) {
          CompoundTag copy = record.copy(); copy.putUUID("Id", guard); guards.add(copy);
        }
      }
      n.put("Guards", guards);
      add(lines, "Workers: " + ids.size());
      if (selectedWorker != null && station.workers.contains(selectedWorker)) {
        CompoundTag record = ledger.workers.get(selectedWorker);
        n.putBoolean("HasSelected", true);
        n.putBoolean(
            "Findable",
            ((ServerLevel) player.level()).getEntity(selectedWorker) instanceof Lumberton found
                && found.isAlive()
                && !found.packing
                && station.identity.equals(found.totemId));
        if (record != null) {
          ListTag details = new ListTag();
          add(
              details,
              WorkerNames.record(
                  ledger, selectedWorker, record, ((ServerLevel) player.level()).random));
          add(details, WorkerJob.name(record.getInt("Job")));
          add(details, record.getString("State"));
          add(details, record.getString("Status"));
          add(details, "Torches: " + record.getInt("Torches"));
          add(details, record.getBoolean("ToolsReady") ? "Tool ready" : "No active tool");
          if (record.contains("Home"))
            add(details, "Home: " + xyz(BlockPos.of(record.getLong("Home"))));
          blockers(details, record.getInt("Alerts"));
          n.put("Details", details);
        }
      }
      blockers(lines, station.alerts);
    }
    if (player.level() instanceof ServerLevel level) {
      ListTag logistics = new ListTag();
      UUID logisticsHome =
          worker != null
              ? worker.homeId
              : station == null ? null : isTotem ? station.logisticsHome : station.identity;
      Ledger workerLedger = Ledger.get(level.getServer());
      add(logistics, "Worker activity");
      workerLedger.workers.entrySet().stream()
          .filter(
              e ->
                  e.getValue().hasUUID("HomeId")
                      && Objects.equals(logisticsHome, e.getValue().getUUID("HomeId")))
          .sorted(Comparator.comparing(e -> WorkerNames.recordName(e.getValue())))
          .forEach(
              e -> {
                CompoundTag record = e.getValue();
                String activity = record.getString("State");
                if (level.getEntity(e.getKey()) instanceof Lumberton live) activity = live.status();
                else if (activity.equals("Deployed")) activity = "Unloaded";
                add(logistics, WorkerNames.recordName(record) + ": " + activity);
              });
      add(logistics, "Requests");
      BuildingProject warehouse =
          isTotem
              ? WorldData.get(level).buildings.get(station.identity)
              : worker != null && worker.job() == 3 ? worker.running.warehouse() : null;
      if (warehouse != null) {
        n.putBoolean("HasWarehouse", true);
        n.putBoolean("WarehouseApproved", warehouse.approved);
        n.putBoolean("WarehousePaused", warehouse.paused);
        status(logistics, warehouse.reason);
        translated(
            logistics,
            "automatons.warehouse.direction",
            Component.translatable("automatons.direction." + warehouse.front.getName()));
        int total =
            warehouse.template(level).materials.values().stream().mapToInt(Integer::intValue).sum();
        int remaining =
            warehouse.remaining(level).values().stream().mapToInt(Integer::intValue).sum();
        translated(logistics, "automatons.warehouse.progress", total - remaining, total);
        if (!warehouse.ready) {
          Map<net.minecraft.world.item.Item, Integer> available = new HashMap<>();
          Station anchor =
              level.hasChunkAt(warehouse.anchor)
                      && level.getBlockEntity(warehouse.anchor) instanceof Station a
                  ? a
                  : null;
          if (anchor != null) {
            for (Lumberton w : anchor.loadedWorkers())
              if (w.job() == 3 && warehouse.home.equals(w.homeId))
                BuildingProject.count(w.cargo, available);
            for (BlockPos p : anchor.chests)
              if ((anchor.flags(p) & 2) != 0) {
                var h = Storage.handler(level, p, anchor);
                if (h != null) BuildingProject.countConstructionSupply(h, available);
              }
          }
          for (var e : warehouse.remaining(level).entrySet())
            translated(
                logistics,
                "automatons.warehouse.material",
                new ItemStack(e.getKey()).getHoverName(),
                e.getValue(),
                Math.max(0, e.getValue() - available.getOrDefault(e.getKey(), 0)));
        }
      }
      UUID networkId =
          worker != null
              ? worker.homeId
              : station == null ? null : isTotem ? station.logisticsHome : station.identity;
      LogisticsNetwork network = WorldData.get(level).logistics.get(networkId);
      if (network != null) {
        network.refresh(level);
        ForestryHelp.maintain(network, level);
        for (var request : network.obstacles.values()) {
          if (isTotem && !station.identity.equals(request.site)) continue;
          add(
              logistics,
              "Pickaxe obstacle at " + request.pos.toShortString() + ": " + request.status);
        }
        translated(
            logistics,
            "automatons.network.overview",
            network.demands.size(),
            network.parcels.size());
        Ledger ledger = Ledger.get(player.getServer());
        for (var d : network.demands.values()) {
          if (isTotem && !station.identity.equals(d.totem) && warehouse == null) continue;
          if (d.source != null) {
            translated(
                logistics,
                "automatons.network." + d.kind,
                new ItemStack(d.item).getHoverName(),
                d.quantity);
            continue;
          }
          CompoundTag record = ledger.workers.get(d.worker);
          String name = record == null ? "Automaton" : WorkerNames.recordName(record);
          translated(
              logistics,
              d.item == null ? "automatons.network.tool" : "automatons.network.need",
              name,
              d.item == null ? "" : new ItemStack(d.item).getHoverName(),
              d.quantity);
        }
        int stock =
            network.reports.values().stream()
                .filter(r -> r.loaded && r.warehouse)
                .flatMap(r -> r.contents.stream())
                .mapToInt(ItemStack::getCount)
                .sum();
        translated(logistics, "automatons.network.stock", stock);
        for (var parcel : network.parcels.values()) {
          CompoundTag record = ledger.workers.get(parcel.runner);
          String name = record == null ? "Runnerton" : WorkerNames.recordName(record);
          translated(
              logistics,
              "automatons.network.delivery",
              name,
              Component.translatable(
                  "automatons.phase." + parcel.phase.name().toLowerCase(Locale.ROOT)),
              parcel.item.getHoverName());
        }
        if (worker != null && worker.job() == 3) {
          var parcel = network.parcels.get(worker.getUUID());
          if (parcel != null) {
            translated(
                lines,
                "automatons.runner.parcel",
                parcel.item.getHoverName(),
                parcel.outstanding());
            if (parcel.destination.worker() != null
                && level.getEntity(parcel.destination.worker()) instanceof Lumberton recipient)
              translated(lines, "automatons.runner.recipient", recipient.getDisplayName());
          }
        }
      } else translated(logistics, "automatons.network.setup");
      n.put("LogisticsLines", logistics);
    }
    n.put("Lines", lines);
    WorkInterface.describe(this, n);
    return n;
  }

  private static void blockers(ListTag lines, int flags) {
    for (int bit : new int[] {1, 2, 4, 8, 16, 32, 64})
      if ((flags & bit) != 0)
        add(
            lines,
            switch (bit) {
              case 1 -> "! No usable tools";
              case 2 -> "! No eligible wood";
              case 4 -> "! Storage full";
              case 16 -> "! Mine needs attention";
              case 32 -> "! Work complete";
              case 64 -> "! Needs torches";
              default -> "! Cargo full";
            });
  }

  private static void add(ListTag list, String text) {
    list.add(StringTag.valueOf(text));
  }

  private static void translated(ListTag list, String key, Object... args) {
    list.add(
        StringTag.valueOf(
            "\u001e" + Component.Serializer.toJson(Component.translatable(key, args))));
  }

  private static void status(ListTag list, String text) {
    for (String prefix : List.of("Collecting ", "Delivering ", "Needs "))
      if (text.startsWith(prefix)) {
        translated(
            list,
            "automatons.status." + prefix.trim().toLowerCase(Locale.ROOT),
            text.substring(prefix.length()));
        return;
      }
    String key =
        "automatons.status."
            + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_$", "");
    list.add(
        StringTag.valueOf(
            "\u001e" + Component.Serializer.toJson(Component.translatableWithFallback(key, text))));
  }

  public static String xyz(BlockPos p) {
    return p.getX() + ", " + p.getY() + ", " + p.getZ();
  }
}
