package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.*;

/** A real, paid-for site chest. The journal stores ownership, never a second item inventory. */
public final class ConstructionSupply {
  private static final String OWNER = "AutomatonsConstructionChest";
  private final BuildingProject project;
  public BlockPos chest;
  public boolean stocked;
  private BlockPos planned;
  private BlockPos fetching, depositing;
  private Item wanted;

  private boolean travel(Lumberton worker, BlockPos pos, String moving, String arrived) {
    worker.building.perimeterRoute(null);
    if (worker.building.access.active() && worker.building.access.advance(project)) return false;
    if (worker.travelStalled(pos)
        && project.bounds((ServerLevel) worker.level()).inflate(4).contains(worker.position())
        && project.bounds((ServerLevel) worker.level()).inflate(4).contains(Vec3.atCenterOf(pos))) {
      if (worker.building.access.chest(project, pos)) return false;
      if (!worker.workIssue.isEmpty()) return false;
    }
    return worker.travelTo(pos, moving, arrived);
  }

  public ConstructionSupply(BuildingProject project) {
    this.project = project;
  }

  public IItemHandler handler(ServerLevel level) {
    if (chest == null
        || !level.hasChunkAt(chest)
        || !(level.getBlockEntity(chest) instanceof ChestBlockEntity entity)
        || !entity.getPersistentData().hasUUID(OWNER)
        || !project.id.equals(entity.getPersistentData().getUUID(OWNER))) return null;
    var state = level.getBlockState(chest);
    if (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) return null;
    var container = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, state, level, chest, false);
    return container == null ? null : new net.minecraftforge.items.wrapper.InvWrapper(container);
  }

  /** Reserved even when accessed through another overlapping Totem's chest scan. */
  public static boolean reserved(ServerLevel level, BlockPos pos) {
    return WorldData.get(level).buildings.values().stream()
        .anyMatch(
            p ->
                p.supplies.chest != null
                    && (pos.equals(p.supplies.chest)
                        || level.hasChunkAt(p.supplies.chest)
                            && Storage.canonical(level, pos)
                                .equals(Storage.canonical(level, p.supplies.chest))));
  }

  public int count(ServerLevel level, Item item) {
    var inventory = handler(level);
    int count = 0;
    if (inventory != null)
      for (int slot = 0; slot < inventory.getSlots(); slot++)
        if (inventory.getStackInSlot(slot).is(item))
          count += inventory.getStackInSlot(slot).getCount();
    return count;
  }

  public Map<Item, Integer> needed(ServerLevel level) {
    var result = project.remaining(level);
    if (!project.ready && chest == null) result.merge(Items.CHEST, 1, Integer::sum);
    return result;
  }

  private void changed(ServerLevel level) {
    WorldData.get(level).setDirty();
    WorldData.get(level).logistics.values().forEach(n -> n.changed(level));
  }

  public boolean prepare(Lumberton worker, Station site) {
    ServerLevel level = (ServerLevel) worker.level();
    project.issue = new CompoundTag();
    var stock = handler(level);
    if (chest != null && !level.hasChunkAt(chest)) {
      worker.state("Construction chest unloaded");
      return false;
    }
    if (chest != null && stock == null) {
      // A blocked lid or player-joined double chest is not a missing chest.
      if (level.getBlockEntity(chest) instanceof ChestBlockEntity entity
          && entity.getPersistentData().hasUUID(OWNER)
          && project.id.equals(entity.getPersistentData().getUUID(OWNER))) {
        project.blocked(
            worker,
            chest,
            "Construction materials chest cannot open",
            "Clear its lid and keep this temporary chest separate from adjacent chests.");
        return false;
      }
      chest = null;
      stocked = false;
      changed(level);
    }
    if (chest == null) {
      if (worker.mining.carried(Items.CHEST) == 0) {
        if (WorkSupplies.full(worker) && depositHome(worker)) return false;
        if (!fetch(worker, site, Map.of(Items.CHEST, 1))) waitFor(worker, Items.CHEST);
        return false;
      }
      if (planned == null || !validSpot(level, site, planned)) planned = chooseSpot(worker, site);
      if (planned == null) {
        worker.state("Needs clear ground outside construction for supply chest");
        return false;
      }
      if (!travel(
          worker,
          planned,
          "Placing construction supply chest",
          "Placing construction supply chest")) return false;
      if (!validSpot(level, site, planned)
          || !MiningRules.place(
              worker,
              planned,
              Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, project.front)))
        return false;
      BuildingProject.consume(worker, Items.CHEST, 1);
      chest = planned;
      planned = null;
      level.getBlockEntity(chest).getPersistentData().putUUID(OWNER, project.id);
      level.getBlockEntity(chest).setChanged();
      site.chests.add(chest);
      stocked = false;
      changed(level);
      return false;
    }
    var required = project.remaining(level);
    if (wanted != null) {
      // Retain the precise shortage across ticks. Unrelated stock must not send us
      // back through the same site-chest load/unload cycle.
      if (!required.containsKey(wanted)
          || worker.mining.carried(wanted) + count(level, wanted)
              >= project.minimumWorkingAmount(level, wanted)) {
        wanted = null;
        stocked = true;
        changed(level);
        worker.state("Construction materials ready");
        return true;
      }
      if (!fetch(
          worker, site, Map.of(wanted, Math.min(wanted.getMaxStackSize(), required.get(wanted)))))
        waitFor(worker, wanted);
      return false;
    }
    if (stocked) return true;
    // Stage the arriving load, then work with it immediately. The full project bill
    // is a logistics request, not a prerequisite for placing the first blocks.
    if (!worker.cargoEmpty()) {
      if (!travel(worker, chest, "Stocking construction chest", "Stocking construction chest"))
        return false;
      int moved = 0;
      for (int slot = 0; slot < worker.cargo.getSlots(); slot++)
        moved += BuildingProject.move(worker.cargo, slot, stock, 64);
      if (moved > 0) changed(level);
      if (!worker.cargoEmpty()) {
        chestFull(worker, Map.of());
        return false;
      }
    }
    Map<Item, Integer> missing = new LinkedHashMap<>();
    required.forEach(
        (item, amount) -> {
          int shortfall = amount - count(level, item);
          if (shortfall > 0) missing.put(item, shortfall);
        });
    if (missing.isEmpty()
        || required.keySet().stream()
            .anyMatch(item -> count(level, item) >= project.minimumWorkingAmount(level, item))) {
      stocked = true;
      changed(level);
      worker.state("Construction materials ready");
      return true;
    }
    if (!fetch(worker, site, missing)) waitFor(worker, missing.keySet().iterator().next());
    return false;
  }

  private void waitFor(Lumberton worker, Item item) {
    worker.getNavigation().stop();
    ((WorkerMoveControl) worker.getMoveControl()).stop();
    worker.state("Waiting for materials: " + new ItemStack(item).getHoverName().getString());
    worker.alerts = 0;
  }

  private boolean validSpot(ServerLevel level, Station site, BlockPos pos) {
    return Storage.inRange(site, pos)
        && level.hasChunkAt(pos)
        && level.getBlockState(pos).isAir()
        && level.getBlockState(pos.above()).isAir()
        && level.isUnobstructed(
            Blocks.CHEST.defaultBlockState(),
            pos,
            net.minecraft.world.phys.shapes.CollisionContext.empty())
        && MiningRules.supported(level, pos)
        && !WorldData.get(level).protectedAt(pos)
        && !WorldData.get(level).mineProtected(pos, null)
        && WorldData.get(level).buildings.values().stream().noneMatch(p -> p.contains(level, pos))
        && java.util.stream.StreamSupport.stream(Direction.Plane.HORIZONTAL.spliterator(), false)
            .noneMatch(d -> level.getBlockState(pos.relative(d)).getBlock() instanceof ChestBlock);
  }

  private BlockPos chooseSpot(Lumberton worker, Station site) {
    ServerLevel level = (ServerLevel) worker.level();
    var box = project.bounds(level);
    List<BlockPos> choices = new ArrayList<>();
    for (int x = (int) box.minX - 2; x <= (int) box.maxX + 1; x++)
      for (int z = (int) box.minZ - 2; z <= (int) box.maxZ + 1; z++)
        for (int y = project.anchor.getY() - 3; y <= project.anchor.getY() + 3; y++) {
          BlockPos pos = new BlockPos(x, y, z);
          if (validSpot(level, site, pos)) choices.add(pos);
        }
    choices.sort(Comparator.comparingDouble(p -> worker.distanceToSqr(Vec3.atCenterOf(p))));
    for (BlockPos pos : choices) {
      if (worker.canReach(pos)) return pos;
      if (worker.pathDeferred) break;
    }
    return null;
  }

  private boolean fetch(Lumberton worker, Station site, Map<Item, Integer> needed) {
    ServerLevel level = (ServerLevel) worker.level();
    Map<BlockPos, Station> candidates = new LinkedHashMap<>();
    if (worker.station() != null)
      for (BlockPos pos : worker.station().chests)
        candidates.put(Storage.canonical(level, pos), worker.station());
    for (BlockPos pos : site.chests) candidates.putIfAbsent(Storage.canonical(level, pos), site);
    List<BlockPos> ordered =
        candidates.keySet().stream()
            .sorted(
                Comparator.comparingDouble(
                        (BlockPos pos) ->
                            pos.equals(fetching) ? -1 : worker.distanceToSqr(Vec3.atCenterOf(pos)))
                    .thenComparingLong(BlockPos::asLong))
            .toList();
    BlockPos inaccessible = null;
    for (BlockPos pos : ordered) {
      Station source = candidates.get(pos);
      if (reserved(level, pos) || (source.flags(pos) & 2) == 0) continue;
      var inventory = Storage.handler(level, pos, source);
      if (inventory == null) continue;
      boolean useful = false;
      for (int slot = 0; slot < inventory.getSlots(); slot++) {
        ItemStack item = inventory.getStackInSlot(slot);
        if (needed.getOrDefault(item.getItem(), 0) > worker.mining.carried(item.getItem())
            && Storage.accepts(worker.cargo, item)) useful = true;
      }
      if (!useful) continue;
      if (pos.equals(fetching)) {
        if (!travel(worker, pos, "Gathering construction kit", "Gathering construction kit"))
          return true;
      } else {
        if (!worker.canReach(pos)) {
          if (worker.pathDeferred) {
            worker.state("Planning construction supply route");
            return true;
          }
          if (inaccessible == null) inaccessible = pos;
          continue;
        }
        fetching = pos;
      }
      if (travel(worker, pos, "Gathering construction kit", "Gathering construction kit")) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
          Item item = inventory.getStackInSlot(slot).getItem();
          BuildingProject.move(
              inventory,
              slot,
              worker.cargo,
              needed.getOrDefault(item, 0) - worker.mining.carried(item));
        }
        changed(level);
        fetching = null;
      }
      return true;
    }
    if (inaccessible != null) {
      fetching = inaccessible;
      travel(worker, inaccessible, "Gathering construction kit", "Gathering construction kit");
      return true;
    }
    fetching = null;
    return false;
  }

  private void chestFull(Lumberton worker, Map<Item, Integer> keep) {
    List<String> blocked = new ArrayList<>();
    Set<Item> seen = new HashSet<>();
    for (int slot = 0; slot < worker.cargo.getSlots(); slot++) {
      ItemStack stack = worker.cargo.getStackInSlot(slot);
      int excess = worker.mining.carried(stack.getItem()) - keep.getOrDefault(stack.getItem(), 0);
      if (!stack.isEmpty() && excess > 0 && seen.add(stack.getItem()))
        blocked.add(excess + " × " + stack.getHoverName().getString());
    }
    worker.getNavigation().stop();
    ((WorkerMoveControl) worker.getMoveControl()).stop();
    project.blocked(
        worker,
        chest,
        "Construction materials chest full — cannot store carried items",
        "Make space in this chest for "
            + String.join(", ", blocked)
            + ". The builder is keeping these items and will retry.");
  }

  public boolean refill(Lumberton worker, Item item) {
    ServerLevel level = (ServerLevel) worker.level();
    var stock = handler(level);
    var required = project.remaining(level);
    if (stock == null) {
      stocked = false;
      changed(level);
      return false;
    }
    worker.building.perimeterRoute(null);
    if (!travel(
        worker, chest, "Collecting from construction chest", "Collecting from construction chest"))
      return true;
    // Leave surplus at the site, then take the next working load in blueprint order.
    Map<Item, Integer> retained = new LinkedHashMap<>(required);
    retained.replaceAll((material, count) -> Math.min(material.getMaxStackSize(), count));
    if (!Storage.accepts(worker.cargo, new ItemStack(item))) {
      for (int slot = worker.cargo.getSlots() - 1; slot >= 0; slot--) {
        ItemStack held = worker.cargo.getStackInSlot(slot);
        if (!held.isEmpty() && !held.is(item)) {
          retained.put(
              held.getItem(), Math.max(0, worker.mining.carried(held.getItem()) - held.getCount()));
          break;
        }
      }
    }
    for (int slot = 0; slot < worker.cargo.getSlots(); slot++) {
      Item held = worker.cargo.getStackInSlot(slot).getItem();
      BuildingProject.move(
          worker.cargo,
          slot,
          stock,
          Math.max(0, worker.mining.carried(held) - retained.getOrDefault(held, 0)));
    }
    for (int slot = 0; slot < worker.cargo.getSlots(); slot++) {
      ItemStack held = worker.cargo.getStackInSlot(slot);
      if (!held.isEmpty()
          && worker.mining.carried(held.getItem()) > retained.getOrDefault(held.getItem(), 0)) {
        chestFull(worker, retained);
        return true;
      }
    }
    if (!canRefill(worker, level, item)) {
      wanted = item;
      stocked = false;
      changed(level);
      return false;
    }
    for (int slot = 0; slot < stock.getSlots(); slot++)
      if (stock.getStackInSlot(slot).is(item))
        BuildingProject.move(
            stock,
            slot,
            worker.cargo,
            Math.min(item.getMaxStackSize(), required.getOrDefault(item, 0))
                - worker.mining.carried(item));
    project.collectBuildKit(worker, stock);
    changed(level);
    return true;
  }

  private boolean canRefill(Lumberton worker, ServerLevel level, Item item) {
    int available = count(level, item);
    return available > 0
        && available + worker.mining.carried(item) >= project.minimumWorkingAmount(level, item);
  }

  private boolean depositHome(Lumberton worker) {
    ServerLevel level = (ServerLevel) worker.level();
    Station site = worker.station();
    // The assigned Builder worksite is the builder's home storage. Never put cleanup
    // cargo back into this or another project's temporary supply chest.
    for (BlockPos pos :
        site.chests.stream()
            .sorted(
                Comparator.comparingDouble(
                        (BlockPos p) ->
                            p.equals(depositing) ? -1 : worker.distanceToSqr(Vec3.atCenterOf(p)))
                    .thenComparingLong(BlockPos::asLong))
            .toList()) {
      if (reserved(level, pos) || (site.flags(pos) & 1) == 0) continue;
      var stock = Storage.handler(level, pos, site);
      if (stock == null) continue;
      boolean fits = false;
      for (int slot = 0; slot < worker.cargo.getSlots(); slot++)
        if (!worker.cargo.getStackInSlot(slot).isEmpty()
            && Storage.accepts(stock, worker.cargo.getStackInSlot(slot))) fits = true;
      if (!fits) continue;
      if (!pos.equals(depositing) && !worker.canReach(pos)) {
        if (worker.pathDeferred) return true;
        continue;
      }
      depositing = pos;
      if (travel(
          worker, pos, "Returning construction supplies home", "Storing construction supplies")) {
        for (int slot = 0; slot < worker.cargo.getSlots(); slot++)
          BuildingProject.move(worker.cargo, slot, stock, 64);
        depositing = null;
      }
      return true;
    }
    return false;
  }

  public void cleanup(Lumberton worker) {
    ServerLevel level = (ServerLevel) worker.level();
    if (!worker.cargoEmpty()) {
      if (!depositHome(worker)) worker.state("Needs home storage for construction leftovers");
      return;
    }
    if (chest != null) {
      if (!level.hasChunkAt(chest)) {
        worker.state("Construction chest unloaded");
        return;
      }
      var stock = handler(level);
      if (stock == null) {
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity entity
            && entity.getPersistentData().hasUUID(OWNER)
            && project.id.equals(entity.getPersistentData().getUUID(OWNER))) {
          project.blocked(
              worker,
              chest,
              "Construction materials chest cannot open",
              "Clear its lid and keep this temporary chest separate from adjacent chests.");
          return;
        }
        chest = null;
        changed(level);
        return;
      }
      if (!travel(worker, chest, "Packing construction chest", "Packing construction chest"))
        return;
      for (int slot = 0; slot < stock.getSlots(); slot++)
        BuildingProject.move(stock, slot, worker.cargo, 64);
      boolean empty = true;
      for (int slot = 0; slot < stock.getSlots(); slot++)
        if (!stock.getStackInSlot(slot).isEmpty()) empty = false;
      if (empty && Inventories.fits(worker.cargo, List.of(new ItemStack(Items.CHEST)))) {
        var old = level.getBlockState(chest);
        if (WorldData.get(level).protectedAt(chest)
            || MiningRules.breakPermission(worker, chest, old) < 0) {
          worker.state("Construction chest removal protected");
          return;
        }
        if (level.setBlock(chest, Blocks.AIR.defaultBlockState(), 3)) {
          Inventories.insert(worker.cargo, new ItemStack(Items.CHEST), false);
          level.levelEvent(2001, chest, Block.getId(old));
          chest = null;
          changed(level);
        }
      }
      return;
    }
    worker.running.idle("Construction complete");
    if (worker.status().equals("Construction complete")) {
      project.release(worker.getUUID());
      changed(level);
    }
  }

  public void abandon(ServerLevel level) {
    if (chest != null
        && level.hasChunkAt(chest)
        && level.getBlockEntity(chest) instanceof ChestBlockEntity entity
        && entity.getPersistentData().hasUUID(OWNER)
        && project.id.equals(entity.getPersistentData().getUUID(OWNER))) {
      entity.getPersistentData().remove(OWNER);
      entity.setChanged();
    }
  }

  public CompoundTag save() {
    CompoundTag tag = new CompoundTag();
    if (chest != null) tag.putLong("Chest", chest.asLong());
    tag.putBoolean("Stocked", stocked);
    if (wanted != null)
      tag.putString(
          "Wanted", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(wanted).toString());
    return tag;
  }

  public void load(CompoundTag tag) {
    chest = tag.contains("Chest") ? BlockPos.of(tag.getLong("Chest")) : null;
    stocked = tag.getBoolean("Stocked");
    var itemId = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("Wanted"));
    wanted =
        itemId == null
            ? null
            : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
  }
}
