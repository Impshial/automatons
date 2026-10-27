package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class RunningGameTests {
  @GameTest(template = "mine_test", timeoutTicks = 1800)
  public static void runnington_physically_collects_machine_cargo(GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    var warehouse = built(h, r);
    Machine m = MachineParts.ENTITY.get().create(l);
    m.owner = r.home.owner;
    m.homeId = r.home.identity;
    m.home = r.home.getBlockPos();
    m.grid = h.absolutePos(new BlockPos(5, 2, 12));
    m.heading = Direction.NORTH;
    m.snap();
    l.addFreshEntity(m);
    m.cargo.setStackInSlot(0, new ItemStack(Items.IRON_ORE, 64));
    m.cargo.setStackInSlot(1, new ItemStack(Items.IRON_ORE, 32));
    m.cargo.setStackInSlot(2, new ItemStack(Items.TORCH, 16));
    m.cargo.setStackInSlot(3, new ItemStack(Items.OAK_SAPLING, 4));
    MachineHauling.request(m);
    boolean[] carried = {false};
    h.onEachTick(
        () -> {
          if (r.runner.mining.carried(Items.IRON_ORE) > 0) {
            carried[0] = true;
            h.assertTrue(
                m.cargo.getStackInSlot(0).isEmpty(),
                "Runner copied rather than withdrew source cargo");
          }
          int stored = 0;
          for (BlockPos p : warehouse.chests(l))
            if (Storage.canonical(l, p).equals(p)) {
              var stock = warehouse.handler(l, p);
              if (stock != null)
                for (int i = 0; i < stock.getSlots(); i++)
                  if (stock.getStackInSlot(i).is(Items.IRON_ORE))
                    stored += stock.getStackInSlot(i).getCount();
            }
          if (stored != 96) return;
          h.assertTrue(
              carried[0] && r.runner.mining.carried(Items.IRON_ORE) == 0,
              "No physical pickup/delivery");
          h.assertTrue(
              m.cargo.getStackInSlot(0).isEmpty()
                  && m.cargo.getStackInSlot(1).isEmpty()
                  && m.cargo.getStackInSlot(2).getCount() == 16
                  && m.cargo.getStackInSlot(3).getCount() == 4,
              "Cargo conservation or supply exclusions failed");
          r.runner.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 300)
  public static void runnington_leaves_worker_supplies_in_all_chests(GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r);
    var stock = warehouse.handler(l, warehouse.chests(l).get(0));
    Item[] supplies = {
      Items.DIAMOND_PICKAXE,
      Items.IRON_AXE,
      Items.IRON_SHOVEL,
      Items.SHEARS,
      Items.FISHING_ROD,
      Items.FLINT_AND_STEEL,
      Items.BRUSH,
      Items.TORCH,
      Items.SOUL_TORCH,
      Items.REDSTONE_TORCH,
      Items.OAK_SAPLING
    };
    for (int i = 0; i < supplies.length; i++)
      stock.insertItem(i, new ItemStack(supplies[i]), false);
    BlockPos site = r.anchor.getBlockPos().west(12);
    l.setBlock(site, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(site);
    work.owner = r.home.owner;
    Lumberton recipient = worker(l, r.home, work, site.north());
    recipient.setJob(1);
    recipient.tools.setStackInSlot(0, ItemStack.EMPTY);
    recipient.setNoAi(true);
    WorkerIdentity.update(recipient, "Deployed");
    BlockPos source = site.east(2);
    l.setBlock(source, Blocks.CHEST.defaultBlockState(), 3);
    work.chests.add(source);
    work.storageComplete = true;
    var local = Storage.handler(l, source, work);
    for (int i = 0; i < supplies.length; i++)
      local.insertItem(i, new ItemStack(supplies[i]), false);
    local.insertItem(20, new ItemStack(Items.IRON_ORE, 32), false);
    h.runAtTickTime(
        250,
        () -> {
          for (int i = 0; i < supplies.length; i++) {
            h.assertTrue(
                stock.getStackInSlot(i).is(supplies[i]), "Warehouse supply taken: " + supplies[i]);
            h.assertTrue(
                local.getStackInSlot(i).is(supplies[i]), "Pickup supply taken: " + supplies[i]);
          }
          var n = LogisticsNetwork.get(r.runner);
          n.changed(l);
          n.refresh(l);
          h.assertTrue(
              n.demands.values().stream().anyMatch(d -> recipient.getUUID().equals(d.worker)),
              "Worker shortage disappeared from requests");
          h.assertTrue(
              n.demands.values().stream()
                  .noneMatch(
                      d ->
                          d.source != null
                              && d.item != null
                              && LogisticsNetwork.leaveInChest(new ItemStack(d.item))),
              "Worker supply advertised for collection");
          r.runner.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void runnington_backpack_is_one_persistent_dynamic_inventory(GameTestHelper h) {
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.setNoAi(true);
    var handler =
        w.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
            .orElseThrow(IllegalStateException::new);
    h.assertTrue(handler.getSlots() == 12, "Base capability size");
    h.assertTrue(
        w.setJob(3) && w.cargo.getSlots() == 18 && handler.getSlots() == 21,
        "Backpack did not expand cached handler");
    ItemStack tool = new ItemStack(Items.DIAMOND_AXE);
    tool.setDamageValue(37);
    tool.setHoverName(net.minecraft.network.chat.Component.literal("Parcel axe"));
    tool.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 2);
    h.assertTrue(
        handler.insertItem(20, tool, true).isEmpty() && w.cargoEmpty(), "Simulation mutated cargo");
    h.assertTrue(
        handler.insertItem(20, tool, false).isEmpty() && w.tools.getStackInSlot(0).isEmpty(),
        "Cargo equipped a delivery tool");
    CompoundTag saved = w.saveWithoutId(new CompoundTag());
    Lumberton loaded = Automatons.LUMBERTON.get().create(h.getLevel());
    loaded.load(saved);
    h.assertTrue(
        loaded.job() == 3
            && loaded.cargo.getSlots() == 18
            && ItemStack.isSameItemSameTags(tool, loaded.cargo.getStackInSlot(17)),
        "Cargo metadata/capacity lost");
    for (int i = 0; i < 10; i++)
      loaded.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
    h.assertTrue(
        !loaded.setJob(2) && loaded.job() == 3 && loaded.cargo.getSlots() == 18,
        "Overflowing backpack switched job");
    for (int i = 0; i < 18; i++) loaded.cargo.setStackInSlot(i, ItemStack.EMPTY);
    loaded.cargo.setStackInSlot(17, tool.copy());
    h.assertTrue(
        loaded.setJob(2)
            && loaded.cargo.getSlots() == 9
            && ItemStack.isSameItemSameTags(loaded.cargo.getStackInSlot(0), tool),
        "Consolidation lost metadata");
    h.succeed();
  }

  @GameTest(template = "mine_test", timeoutTicks = 40)
  public static void runnington_imported_blueprints_have_real_lids_and_anchors(GameTestHelper h) {
    ServerLevel l = h.getLevel();
    BuildingTemplate t = BuildingTemplate.load(l, "warehouse");
    h.assertTrue(
        t.materials.get(Items.CHEST) == 17
            && t.materials.get(Items.OAK_DOOR) == 1
            && t.materials.get(Items.OAK_LOG) == 38
            && t.materials.get(Items.OAK_SLAB) == 56,
        "Warehouse BOM mismatch");
    BlockPos origin = h.absolutePos(new BlockPos(15, 2, 12));
    l.setBlock(origin, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station station = (Station) l.getBlockEntity(origin);
    UUID identity = station.identity;
    station.owner = UUID.randomUUID();
    BuildingProject plan = new BuildingProject();
    plan.anchor = origin;
    plan.owner = station.owner;
    plan.home = UUID.randomUUID();
    plan.totem = identity;
    for (var c : t.cells) {
      BlockPos p = t.world(origin, Direction.NORTH, c.local());
      if (p.equals(origin)) continue;
      l.setBlock(p, c.state(), 2);
      if (!c.state().isAir()) plan.completed.add(p.asLong());
    }
    plan.ready = true;
    plan.approved = true;
    WorldData.get(l).buildings.put(identity, plan);
    h.assertTrue(
        l.getBlockEntity(origin) == station && station.identity.equals(identity),
        "Anchor overwritten");
    Set<BlockPos> unique = new HashSet<>();
    for (BlockPos p : plan.chests(l)) {
      var handler = plan.handler(l, p);
      h.assertTrue(
          handler != null && handler.getSlots() == 54, "Invalid/blocked double chest at " + p);
      unique.add(Storage.canonical(l, p));
    }
    h.assertTrue(unique.size() == 8, "Chest halves counted twice");
    for (int x = 2; x <= 4; x++)
      for (int z = 2; z <= 7; z++)
        for (int y = 1; y <= 4; y++)
          h.assertTrue(
              l.getBlockState(plan.at(l, x, y, z))
                  .getCollisionShape(l, plan.at(l, x, y, z))
                  .isEmpty(),
              "Aisle obstructed");
    var mine = BuildingTemplate.load(l, "minehead");
    h.assertTrue(
        mine.materials.values().stream().mapToInt(Integer::intValue).sum() == 152
            && mine.materials.get(Items.LANTERN) == 4
            && mine.materials.get(Items.OAK_LOG) == 33,
        "Minehead BOM mismatch");
    h.succeed();
  }

  private record Rig(Station home, Station anchor, Lumberton runner) {}

  /** A parcel already picked up by a previous version; no chest withdrawal is performed. */
  public static void carriedParcel(Lumberton runner, Lumberton recipient, ItemStack item) {
    ServerLevel l = (ServerLevel) runner.level();
    WorkerIdentity.update(recipient, "Deployed");
    LogisticsNetwork n = LogisticsNetwork.get(runner);
    n.changed(l);
    n.refresh(l);
    var demand =
        n.demands.values().stream()
            .filter(d -> recipient.getUUID().equals(d.worker) && n.matches(l, d, item))
            .findFirst()
            .orElseThrow();
    var parcel = new LogisticsNetwork.Parcel();
    parcel.runner = runner.getUUID();
    parcel.request = demand.id;
    parcel.source = new LogisticsNetwork.Endpoint(runner.totemId, runner.totem, null);
    parcel.destination =
        new LogisticsNetwork.Endpoint(recipient.totemId, null, recipient.getUUID());
    parcel.item = item.copyWithCount(1);
    parcel.quantity = parcel.picked = item.getCount();
    parcel.phase = LogisticsNetwork.Phase.CARRYING;
    parcel.touched = l.getGameTime();
    Inventories.insert(runner.cargo, item.copy(), false);
    n.parcels.put(runner.getUUID(), parcel);
  }

  @GameTest(template = "mine_test", timeoutTicks = 240)
  public static void runnington_does_not_claim_or_build_surface_project(GameTestHelper h) {
    Rig r = rig(h);
    h.assertTrue(r.anchor.configure(WorksitePurpose.WAREHOUSE), "Cannot configure warehouse");
    r.runner.cargo.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 32));
    h.runAtTickTime(
        200,
        () -> {
          BuildingProject p = r.anchor.project();
          h.assertTrue(
              p.builder == null && p.completed.isEmpty() && !p.ready,
              "Courier took over construction");
          h.assertTrue(r.runner.mining.carried(Items.OAK_PLANKS) == 32, "Unassigned cargo lost");
          r.runner.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 40)
  public static void construction_list_counts_real_stock_and_preserves_project_identity(
      GameTestHelper h) {
    Rig r = rig(h);
    r.runner.setNoAi(true);
    r.runner.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 7));
    var rows = BuildMaterials.describe(r.anchor, r.runner, true);
    for (Tag tag : rows) {
      CompoundTag row = (CompoundTag) tag;
      if (ItemStack.of(row.getCompound("Item")).is(Items.OAK_LOG))
        h.assertTrue(
            row.getInt("Total") == 38 && row.getInt("Have") == 7 && row.getInt("Missing") == 31,
            "Wrong live build counts");
    }
    BuildingProject plan = built(h, r);
    CompoundTag old = plan.save();
    old.remove("BlueprintVersion");
    BuildingProject legacy = BuildingProject.load(old);
    h.assertTrue(
        legacy.id.equals(plan.id)
            && legacy.purpose == plan.purpose
            && legacy.completed.equals(plan.completed),
        "Worksite identity or completed journal lost on reload");
    h.succeed();
  }

  private static Rig rig(GameTestHelper h) {
    return rig(h, 2);
  }

  private static Rig rig(GameTestHelper h, int standing) {
    ServerLevel l = h.getLevel();
    for (int x = 0; x < 52; x++)
      for (int z = 0; z < 30; z++)
        for (int y = standing - 2; y < standing; y++)
          h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    BlockPos homePos = h.absolutePos(new BlockPos(29, standing, 12)),
        anchorPos = h.absolutePos(new BlockPos(17, standing, 12));
    l.setBlock(homePos, Automatons.PEDESTAL.get().defaultBlockState(), 3);
    l.setBlock(anchorPos, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station home = (Station) l.getBlockEntity(homePos),
        anchor = (Station) l.getBlockEntity(anchorPos);
    home.owner = anchor.owner = UUID.randomUUID();
    home.registerHome();
    Lumberton runner = worker(l, home, anchor, anchorPos.west(3));
    return new Rig(home, anchor, runner);
  }

  @GameTest(template = "mine_test", timeoutTicks = 6500)
  public static void runnington_collects_depot_via_ladder_and_recall_keeps_parcel(
      GameTestHelper h) {
    depotCollection(h, false);
  }

  @GameTest(template = "mine_test", timeoutTicks = 6500)
  public static void runnington_collects_mine_chests_on_warehouse_totem(GameTestHelper h) {
    depotCollection(h, true);
  }

  private static void depotCollection(GameTestHelper h, boolean sharedTotem) {
    Rig r = rig(h, 41);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r);
    BlockPos site = h.absolutePos(new BlockPos(4, 41, 12));
    l.setBlock(site, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(site);
    work.owner = r.home.owner;
    MinePlan mine = new MinePlan();
    mine.rustic = true;
    mine.entrance = site.east(3);
    mine.stage = MinePlan.Stage.READY;
    mine.depotBuilt = true;
    work.mine = mine;
    l.setBlock(
        mine.hatch(),
        Blocks.OAK_TRAPDOOR
            .defaultBlockState()
            .setValue(TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)
            .setValue(TrapDoorBlock.OPEN, true),
        3);
    for (int d = mine.firstLadder(); d <= mine.shaftDepth; d++) {
      l.setBlock(mine.ladder(d).south(), Blocks.COBBLESTONE.defaultBlockState(), 3);
      l.setBlock(mine.ladder(d), mine.ladderState(), 3);
    }
    for (int x = -2; x <= 2; x++)
      for (int z = 0; z <= 2; z++) {
        l.setBlock(mine.at(x, -21, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
        for (int y = -20; y <= -18; y++)
          if (x != 0 || z != 0) l.setBlock(mine.at(x, y, z), Blocks.AIR.defaultBlockState(), 3);
      }
    List<BlockPos> positions = mine.depotPositions();
    for (int pair = 0; pair < 4; pair += 2) {
      Direction facing = pair == 0 ? Direction.EAST : Direction.WEST;
      BlockPos first = positions.get(pair), second = positions.get(pair + 1);
      Direction connection =
          Direction.fromDelta(second.getX() - first.getX(), 0, second.getZ() - first.getZ());
      var type =
          connection == facing.getClockWise()
              ? net.minecraft.world.level.block.state.properties.ChestType.LEFT
              : net.minecraft.world.level.block.state.properties.ChestType.RIGHT;
      l.setBlock(
          first,
          Blocks.CHEST
              .defaultBlockState()
              .setValue(ChestBlock.FACING, facing)
              .setValue(ChestBlock.TYPE, type),
          2);
      l.setBlock(
          second,
          Blocks.CHEST
              .defaultBlockState()
              .setValue(ChestBlock.FACING, facing)
              .setValue(ChestBlock.TYPE, type.getOpposite()),
          2);
      mine.placedChests.add(first.asLong());
      mine.placedChests.add(second.asLong());
    }
    WorldData.get(l).mines.put(mine.id, mine);
    var source = Storage.depotHandler(l, positions.get(0), mine);
    h.assertTrue(source != null, "Fixture depot invalid");
    source.insertItem(0, new ItemStack(Items.IRON_ORE, 64), false);
    source.insertItem(1, new ItemStack(Items.IRON_ORE, 64), false);
    Lumberton miner = worker(l, r.home, work, site.west());
    miner.setJob(2);
    miner.setNoAi(true);
    miner.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
    WorkerIdentity.update(miner, "Deployed");
    if (sharedTotem) {
      r.anchor.mine = mine;
      miner.assign(r.anchor);
      work.mine = null;
      WorkerIdentity.update(miner, "Deployed");
    }
    boolean[] descended = {false}, recalled = {false}, resumed = {false};
    h.onEachTick(
        () -> {
          if (r.runner.getY() < mine.entrance.getY() - 10) descended[0] = true;
          if (!recalled[0] && r.runner.mining.carried(Items.IRON_ORE) > 0) {
            r.home.recall(true);
            recalled[0] = true;
          }
          if (recalled[0] && !resumed[0] && r.runner.status().equals("Recalled")) {
            h.assertTrue(
                r.runner.mining.carried(Items.IRON_ORE) == 128, "Recall lost reserved cargo");
            r.home.recall(false);
            resumed[0] = true;
          }
          if (h.getTick() % 500 == 0)
            System.out.println(
                "RUNNER_DEPOT "
                    + h.getTick()
                    + " "
                    + r.runner.status()
                    + " pos="
                    + r.runner.position()
                    + " recalled="
                    + recalled[0]
                    + " resumed="
                    + resumed[0]);
          int count = 0;
          for (BlockPos p : warehouse.chests(l))
            if (Storage.canonical(l, p).equals(p)) {
              var dest = warehouse.handler(l, p);
              if (dest != null)
                for (int i = 0; i < dest.getSlots(); i++)
                  if (dest.getStackInSlot(i).is(Items.IRON_ORE))
                    count += dest.getStackInSlot(i).getCount();
            }
          if (count != 128) return;
          h.assertTrue(
              descended[0]
                  && recalled[0]
                  && resumed[0]
                  && r.runner.mining.carried(Items.IRON_ORE) == 0,
              "Collection bypassed route/recall");
          // Nearby cleanup may also recover the removed mine hatch. Allow the next
          // unreserved-cargo trip to store it after delivering the reserved ore.
          if (!r.runner.cargoEmpty()) return;
          h.assertTrue(
              source.getStackInSlot(0).isEmpty() && source.getStackInSlot(1).isEmpty(),
              "Depot output duplicated");
          r.runner.setNoAi(true);
          h.succeed();
        });
  }

  private static Lumberton worker(ServerLevel l, Station home, Station anchor, BlockPos at) {
    Lumberton w = Automatons.LUMBERTON.get().create(l);
    w.owner = home.owner;
    w.homeId = home.identity;
    w.home = home.getBlockPos();
    w.homeDimension = l.dimension().location().toString();
    w.setJob(3);
    w.moveTo(Vec3.atBottomCenterOf(at));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
    l.addFreshEntity(w);
    w.assign(anchor);
    home.workers.add(w.getUUID());
    return w;
  }

  private static BuildingProject built(GameTestHelper h, Rig r) {
    return built(h, r, Direction.EAST);
  }

  private static BuildingProject built(GameTestHelper h, Rig r, Direction front) {
    ServerLevel l = h.getLevel();
    BuildingProject p = new BuildingProject();
    p.anchor = r.anchor.getBlockPos();
    p.totem = r.anchor.identity;
    p.home = r.home.identity;
    p.owner = r.home.owner;
    p.front = front;
    var t = p.template(l);
    for (var c : t.cells) {
      // Match construction: template air never excavates an exterior foundation trench.
      if (c.local().getY() == 0 && c.state().isAir()) continue;
      BlockPos at = t.world(p.anchor, p.front, c.local());
      if (at.equals(p.anchor)) continue;
      l.setBlock(at, c.state().rotate(BuildingTemplate.rotation(p.front)), 2);
      if (!c.state().isAir()) p.completed.add(at.asLong());
    }
    r.anchor.purpose = WorksitePurpose.WAREHOUSE;
    p.ready = p.approved = true;
    p.reason = "Warehouse ready";
    WorldData.get(l).buildings.put(p.totem, p);
    return p;
  }

  @GameTest(template = "mine_test", timeoutTicks = 2000)
  public static void runnington_leaves_north_warehouse_from_off_center_door(GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r, Direction.NORTH);
    BlockPos job = h.absolutePos(new BlockPos(5, 2, 18));
    l.setBlock(job, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(job);
    work.owner = r.home.owner;
    Lumberton recipient = worker(l, r.home, work, job.north());
    recipient.setJob(1);
    recipient.tools.setStackInSlot(0, ItemStack.EMPTY);
    recipient.setNoAi(true);
    WorkerIdentity.update(recipient, "Deployed");
    var stock = warehouse.handler(l, warehouse.chests(l).get(0));
    stock.insertItem(0, new ItemStack(Items.IRON_AXE), false);
    carriedParcel(r.runner, recipient, stock.extractItem(0, 1, false));
    BlockPos door = warehouse.at(l, 3, 1, 1);
    // The feet are outside the door cell, but the back of the body is still inside it.
    var doorState = l.getBlockState(door);
    ((DoorBlock) doorState.getBlock()).setOpen(null, l, doorState, door, true);
    r.runner.moveTo(door.getX() + .604957, door.getY(), door.getZ() - .284811, 180, 0);
    r.runner
        .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
        .setBaseValue(8);
    boolean[] opened = {false}, crossed = {false}, guided = {false}, partial = {false};
    h.onEachTick(
        () -> {
          opened[0] |= l.getBlockState(door).getValue(DoorBlock.OPEN);
          crossed[0] |= r.runner.getZ() < door.getZ() - .4;
          var exitPath = r.runner.getNavigation().getPath();
          partial[0] |= exitPath != null && !exitPath.canReach() && exitPath.getNodeCount() > 1;
          guided[0] |=
              exitPath != null
                  && exitPath.getEndNode() != null
                  && exitPath
                      .getEndNode()
                      .asBlockPos()
                      .equals(warehouse.entrance(l).relative(warehouse.front, 3));
          if (h.getTick() % 100 == 0) {
            var path = r.runner.getNavigation().getPath();
            var move = r.runner.getMoveControl();
            System.out.println(
                "RUNNER_NORTH "
                    + h.getTick()
                    + " pos="
                    + r.runner.position()
                    + " status="
                    + r.runner.status()
                    + " door="
                    + l.getBlockState(door)
                    + " path="
                    + path
                    + " next="
                    + (path == null ? -1 : path.getNextNodeIndex())
                    + " end="
                    + (path == null ? null : path.getEndNode())
                    + " wanted="
                    + move.getWantedX()
                    + ","
                    + move.getWantedY()
                    + ","
                    + move.getWantedZ()
                    + " active="
                    + move.hasWanted()
                    + " speed="
                    + r.runner.getSpeed()
                    + " collision="
                    + r.runner.horizontalCollision
                    + " velocity="
                    + r.runner.getDeltaMovement());
          }
          if (!Inventories.axe(recipient.activeTool())) return;
          h.assertTrue(
              opened[0] && crossed[0] && guided[0] && partial[0],
              "Runner skipped the full-body exit or bounded partial route");
          h.assertTrue(
              r.runner.cargoEmpty()
                  && stock.getStackInSlot(0).isEmpty()
                  && r.runner.distanceToSqr(recipient) < 30,
              "Door delivery lost ownership or bypassed travel");
          r.runner.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 4500)
  public static void runnington_existing_carried_tool_is_delivered_once_by_physical_runner(
      GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r);
    var stock = warehouse.handler(l, warehouse.chests(l).get(0));
    ItemStack axe = new ItemStack(Items.IRON_AXE);
    axe.setDamageValue(12);
    axe.setHoverName(net.minecraft.network.chat.Component.literal("Workshop axe"));
    stock.insertItem(0, axe.copy(), false);
    // A covered, one-block L passage forces a real turn and single-door crossing.
    for (int x = 2; x <= 14; x++)
      for (int z = 4; z <= 18; z++)
        for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    for (int x = 3; x <= 14; x++)
      for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(x, y, 5), Blocks.AIR);
    for (int z = 5; z <= 17; z++)
      for (int y = 2; y <= 3; y++) h.setBlock(new BlockPos(3, y, z), Blocks.AIR);
    BlockPos door = h.absolutePos(new BlockPos(9, 2, 5));
    var doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
    l.setBlock(door, doorState, 2);
    l.setBlock(
        door.above(),
        doorState.setValue(
            DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
        2);
    BlockPos job = h.absolutePos(new BlockPos(3, 2, 18));
    l.setBlock(job, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(job);
    work.owner = r.home.owner;
    Lumberton recipient = worker(l, r.home, work, job.north());
    recipient.tools.setStackInSlot(0, ItemStack.EMPTY);
    recipient.setJob(1);
    recipient.setNoAi(true);
    Lumberton second = worker(l, r.home, r.anchor, r.anchor.getBlockPos().west(4));
    r.runner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 5))));
    second.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(16, 2, 6))));
    carriedParcel(r.runner, recipient, stock.extractItem(0, 1, false));
    boolean[] opened = {false}, turned = {false};
    h.onEachTick(
        () -> {
          opened[0] |= l.getBlockState(door).getValue(DoorBlock.OPEN);
          for (Lumberton courier : List.of(r.runner, second))
            if (courier.getX() < job.getX() + 1 && courier.getZ() > job.getZ() - 8)
              turned[0] = true;
          if (h.getTick() % 500 == 0)
            System.out.println(
                "RUNNER_DELIVERY "
                    + h.getTick()
                    + " "
                    + r.runner.status()
                    + " second="
                    + second.status()
                    + " recipient="
                    + recipient.status());
          if (!Inventories.axe(recipient.activeTool())) return;
          h.assertTrue(
              ItemStack.isSameItemSameTags(recipient.activeTool(), axe),
              "Delivery metadata changed");
          h.assertTrue(stock.getStackInSlot(0).isEmpty(), "Tool was created remotely");
          h.assertTrue(
              r.runner.distanceToSqr(recipient) < 30 || second.distanceToSqr(recipient) < 30,
              "Remote handoff");
          h.assertTrue(opened[0] && turned[0], "Runner bypassed the narrow corner/door route");
          int total = 0;
          for (Lumberton w : List.of(r.runner, second, recipient)) {
            for (int i = 0; i < w.cargo.getSlots(); i++)
              if (w.cargo.getStackInSlot(i).is(Items.IRON_AXE))
                total += w.cargo.getStackInSlot(i).getCount();
            for (int i = 0; i < 3; i++) if (w.tools.getStackInSlot(i).is(Items.IRON_AXE)) total++;
            w.setNoAi(true);
          }
          h.assertTrue(total == 1, "Two runners duplicated limited tool");
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 4500)
  public static void runnington_manual_resupply_returns_in_transit_tool(GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r);
    var stock = warehouse.handler(l, warehouse.chests(l).get(0));
    stock.insertItem(0, new ItemStack(Items.DIAMOND_AXE), false);
    BlockPos job = h.absolutePos(new BlockPos(5, 2, 12));
    l.setBlock(job, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(job);
    work.owner = r.home.owner;
    Lumberton recipient = worker(l, r.home, work, job.south(2));
    recipient.tools.setStackInSlot(0, ItemStack.EMPTY);
    recipient.setJob(1);
    carriedParcel(r.runner, recipient, stock.extractItem(0, 1, false));
    boolean[] manual = {false};
    h.onEachTick(
        () -> {
          var network = LogisticsNetwork.get(r.runner);
          var parcel = network.parcels.get(r.runner.getUUID());
          if (h.getTick() % 500 == 0)
            System.out.println(
                "RUNNER_CANCEL "
                    + h.getTick()
                    + " manual="
                    + manual[0]
                    + " status="
                    + r.runner.status()
                    + " parcel="
                    + (parcel == null
                        ? "none"
                        : parcel.phase
                            + "/"
                            + parcel.picked
                            + "/"
                            + parcel.delivered
                            + "/"
                            + parcel.returning)
                    + " cargo="
                    + r.runner.cargo.serializeNBT());
          if (!manual[0] && parcel != null && parcel.outstanding() > 0) {
            recipient.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
            manual[0] = true;
          }
          if (!manual[0] || !r.runner.cargoEmpty()) return;
          int returned = 0;
          for (BlockPos p : warehouse.chests(l))
            if (Storage.canonical(l, p).equals(p)) {
              var handler = warehouse.handler(l, p);
              for (int i = 0; i < handler.getSlots(); i++)
                if (handler.getStackInSlot(i).is(Items.DIAMOND_AXE))
                  returned += handler.getStackInSlot(i).getCount();
            }
          if (returned != 1) return;
          for (int i = 0; i < 3; i++)
            h.assertTrue(
                !recipient.tools.getStackInSlot(i).is(Items.DIAMOND_AXE),
                "Canceled delivery still handed off");
          r.runner.setNoAi(true);
          recipient.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 40)
  public static void construction_claim_is_exclusive_across_save_and_release(GameTestHelper h) {
    Rig r = rig(h);
    r.runner.setNoAi(true);
    r.runner.setJob(4);
    Lumberton peer = worker(h.getLevel(), r.home, r.anchor, r.anchor.getBlockPos().west(5));
    peer.setNoAi(true);
    peer.setJob(4);
    BuildingProject p = new BuildingProject();
    p.anchor = r.anchor.getBlockPos();
    p.owner = r.home.owner;
    p.home = r.home.identity;
    p.totem = r.anchor.identity;
    h.assertTrue(p.claim(r.runner) && !p.claim(peer), "Concurrent builders owned one project");
    p.completed.add(p.anchor.below().asLong());
    BuildingProject restored = BuildingProject.load(p.save());
    h.assertTrue(
        restored.completed.equals(p.completed) && !restored.claim(peer),
        "Reload lost claim or completed cells");
    restored.release(r.runner.getUUID());
    h.assertTrue(
        restored.claim(peer) && restored.completed.equals(p.completed),
        "Takeover reset completed work");
    h.succeed();
  }

  @GameTest(template = "mine_test", timeoutTicks = 40)
  public static void runnington_source_and_capacity_reservations_survive_save(GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r);
    boolean space = true;
    for (BlockPos p : warehouse.chests(l))
      if (Storage.canonical(l, p).equals(p)) {
        var handler = warehouse.handler(l, p);
        for (int i = 0; i < handler.getSlots(); i++)
          handler.insertItem(i, new ItemStack(Items.COBBLESTONE, space ? 63 : 64), false);
        space = false;
      }
    // Leave exactly one item of compatible space, across all eight double chests.
    var first = warehouse.handler(l, Storage.canonical(l, warehouse.chests(l).get(0)));
    for (int i = 1; i < first.getSlots(); i++)
      first.insertItem(i, new ItemStack(Items.COBBLESTONE), false);
    BlockPos site = h.absolutePos(new BlockPos(5, 2, 12));
    l.setBlock(site, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(site);
    work.owner = r.home.owner;
    BlockPos chest = site.north(2);
    l.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    work.chests.add(chest);
    work.storageComplete = true;
    var source = Storage.handler(l, chest, work);
    source.insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
    Lumberton member = worker(l, r.home, work, site.south(2));
    member.setJob(1);
    member.setNoAi(true);
    WorkerIdentity.update(member, "Deployed");
    Lumberton other = worker(l, r.home, r.anchor, r.anchor.getBlockPos().west(5));
    other.setNoAi(true);
    r.runner.setNoAi(true);
    // A second Lumber Totem sees the same chests through its ordinary base range.
    BlockPos aliasPos = r.anchor.getBlockPos().south(5);
    l.setBlock(aliasPos, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station alias = (Station) l.getBlockEntity(aliasPos);
    alias.owner = r.home.owner;
    alias.purpose = WorksitePurpose.LUMBER;
    alias.chests.addAll(warehouse.chests(l));
    alias.storageComplete = true;
    Lumberton aliasMember = worker(l, r.home, alias, aliasPos.south(2));
    aliasMember.setJob(1);
    aliasMember.setNoAi(true);
    WorkerIdentity.update(aliasMember, "Deployed");
    LogisticsNetwork network = LogisticsNetwork.get(r.runner);
    while (WorldEvents.budget(1)) {} // Busy forestry must not starve warehouse telemetry.
    network.refresh(l);
    h.assertTrue(
        network.reports.values().stream().filter(report -> report.loaded).count() == 9,
        "Overlapping Totems duplicated container reports");
    h.assertTrue(
        network.reports.values().stream()
                .filter(
                    report ->
                        report.loaded
                            && report.warehouse
                            && report.supplySites.contains(alias.identity))
                .count()
            == 8,
        "Shared warehouse reports lost classification or local supply aliases");
    var one = network.claim(r.runner);
    var two = network.claim(other);
    h.assertTrue(
        one != null && one.quantity == 1 && two == null,
        "Runners overreserved destination capacity");
    h.assertTrue(
        network.capacity(
                l,
                new LogisticsNetwork.Endpoint(alias.identity, one.destination.pos(), null),
                new ItemStack(Items.COBBLESTONE),
                other.getUUID())
            == 0,
        "Another Totem alias bypassed the destination reservation");
    h.assertTrue(
        source.getStackInSlot(0).getCount() == 2 && r.runner.cargoEmpty(),
        "Reservation moved real items");
    LogisticsNetwork restored = LogisticsNetwork.load(network.save());
    h.assertTrue(
        restored.parcels.size() == 1 && restored.parcels.get(r.runner.getUUID()).picked == 0,
        "Reserved parcel save changed owner");
    one.picked = 1;
    one.phase = LogisticsNetwork.Phase.CARRYING;
    restored = LogisticsNetwork.load(network.save());
    h.assertTrue(
        restored.parcels.get(r.runner.getUUID()).outstanding() == 1
            && restored.parcels.get(r.runner.getUUID()).phase == LogisticsNetwork.Phase.CARRYING,
        "In-transit parcel save lost state");
    h.assertTrue(
        source.getStackInSlot(0).getCount() == 2, "Loading parcel created or extracted items");
    h.succeed();
  }

  @GameTest(template = "mine_test", timeoutTicks = 40)
  public static void runnington_rejects_blocked_sites_and_foreign_networks(GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    r.runner.setNoAi(true);
    BuildingProject plan = new BuildingProject();
    plan.owner = r.home.owner;
    plan.home = r.home.identity;
    plan.totem = r.anchor.identity;
    plan.anchor = r.anchor.getBlockPos();
    plan.front = Direction.EAST;
    BlockPos obstruction = plan.at(l, 2, 1, 2);
    l.setBlock(obstruction, Blocks.CHEST.defaultBlockState(), 3);
    h.assertTrue(
        plan.validate(r.runner, r.anchor) && l.getBlockState(obstruction).is(Blocks.CHEST),
        "Site registration changed an existing container");
    l.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
    long chunk = new net.minecraft.world.level.ChunkPos(plan.anchor).toLong();
    WorldData.get(l).protection.put(chunk, r.home.owner);
    plan.approved = false;
    h.assertTrue(!plan.validate(r.runner, r.anchor) && !plan.approved, "Protected site approved");
    WorldData.get(l).protection.remove(chunk);
    UUID home = r.runner.homeId;
    r.runner.homeId = UUID.randomUUID();
    h.assertTrue(
        r.runner.assign(r.anchor).equals("Totem belongs to another home"),
        "Same-owner homes merged");
    r.runner.homeId = home;
    var foreign =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            l, new GameProfile(UUID.randomUUID(), "OtherOwner"));
    foreign.moveTo(r.runner.position());
    WorkMenu menu = new WorkMenu(4, foreign.getInventory(), null, r.runner);
    h.assertTrue(
        !menu.clickMenuButton(foreign, 7) && r.runner.job() == 3, "Unauthorized job change");
    LogisticsNetwork network = LogisticsNetwork.get(r.runner);
    Lumberton stranger = Automatons.LUMBERTON.get().create(l);
    stranger.homeId = home;
    stranger.owner = foreign.getUUID();
    h.assertTrue(!network.member(stranger), "Foreign owner joined network");
    h.succeed();
  }

  @GameTest(template = "mine_test", timeoutTicks = 80)
  public static void runnington_packing_drops_all_eighteen_slots_once_and_redeploys(
      GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    Lumberton w = r.runner;
    w.setNoAi(true);
    for (int i = 0; i < 18; i++) {
      ItemStack axe = new ItemStack(Items.IRON_AXE);
      axe.setDamageValue(i);
      axe.setHoverName(net.minecraft.network.chat.Component.literal("Parcel " + i));
      w.cargo.setStackInSlot(i, axe);
    }
    var player =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            l, new GameProfile(r.home.owner, "RunnerOwner"));
    player.moveTo(w.position().add(0, 0, 2));
    var network = LogisticsNetwork.get(w);
    var parcel = new LogisticsNetwork.Parcel();
    parcel.runner = w.getUUID();
    parcel.request = "fixture";
    parcel.source =
        parcel.destination =
            new LogisticsNetwork.Endpoint(r.anchor.identity, r.anchor.getBlockPos(), null);
    parcel.item = new ItemStack(Items.IRON_AXE);
    parcel.quantity = parcel.picked = 1;
    network.parcels.put(w.getUUID(), parcel);
    String name = w.getDisplayName().getString();
    UUID identity = w.getUUID();
    h.assertTrue(WorkerIdentity.pack(w, player), "Packing rejected");
    h.assertTrue(!WorkerIdentity.pack(w, player), "Packed twice");
    h.assertTrue(!network.parcels.containsKey(identity), "Packing left reservations");
    var drops =
        l.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            new AABB(w.blockPosition()).inflate(3));
    Set<String> names = new HashSet<>();
    int cargoCount = 0;
    net.minecraft.world.entity.item.ItemEntity portable = null;
    for (var drop : drops) {
      if (drop.getItem().is(Items.IRON_AXE)) {
        cargoCount += drop.getItem().getCount();
        names.add(drop.getItem().getHoverName().getString());
      }
      if (drop.getItem().is(Automatons.AUTOMATON_ITEM.get())) portable = drop;
    }
    h.assertTrue(
        cargoCount == 18 && names.size() == 18 && portable != null,
        "Backpack drop count/metadata mismatch");
    ItemStack packed = portable.getItem().copy();
    portable.discard();
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, packed);
    BlockPos ground = h.absolutePos(new BlockPos(12, 1, 10));
    player.moveTo(Vec3.atBottomCenterOf(ground.above().south(2)));
    h.runAfterDelay(
        5,
        () -> {
          var result =
              Automatons.AUTOMATON_ITEM
                  .get()
                  .useOn(
                      new net.minecraft.world.item.context.UseOnContext(
                          player,
                          net.minecraft.world.InteractionHand.MAIN_HAND,
                          new BlockHitResult(
                              Vec3.atCenterOf(ground), Direction.UP, ground, false)));
          h.assertTrue(
              result.consumesAction() && l.getEntity(identity) instanceof Lumberton,
              "Redeployment failed");
          Lumberton deployed = (Lumberton) l.getEntity(identity);
          h.assertTrue(
              deployed.job() == 3
                  && deployed.cargo.getSlots() == 18
                  && deployed.cargoEmpty()
                  && deployed.homeId.equals(r.home.identity)
                  && deployed.totemId.equals(r.anchor.identity)
                  && deployed.getDisplayName().getString().equals(name),
              "Redeployment lost identity/capacity or duplicated cargo");
          deployed.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 4500)
  public static void runnington_full_changed_destination_and_reload_retain_real_cargo(
      GameTestHelper h) {
    Rig r = rig(h);
    var l = h.getLevel();
    BuildingProject warehouse = built(h, r);
    BlockPos site = h.absolutePos(new BlockPos(5, 2, 12));
    l.setBlock(site, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station work = (Station) l.getBlockEntity(site);
    work.owner = r.home.owner;
    BlockPos chest = site.north(2);
    l.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    work.chests.add(chest);
    work.storageComplete = true;
    var source = Storage.handler(l, chest, work);
    source.insertItem(0, new ItemStack(Items.IRON_ORE, 64), false);
    source.insertItem(1, new ItemStack(Items.IRON_ORE, 64), false);
    Lumberton member = worker(l, r.home, work, site.south(2));
    member.setJob(1);
    member.setNoAi(true);
    WorkerIdentity.update(member, "Deployed");
    Lumberton[] courier = {r.runner};
    int[] phase = {0};
    var destination = warehouse.handler(l, Storage.canonical(l, warehouse.chests(l).get(0)));
    h.onEachTick(
        () -> {
          Lumberton w = courier[0];
          if (w == null) return;
          var network = LogisticsNetwork.get(w);
          var parcel = network.parcels.get(w.getUUID());
          if (phase[0] == 0 && parcel != null && parcel.outstanding() == 128) {
            for (BlockPos pos : warehouse.chests(l))
              if (Storage.canonical(l, pos).equals(pos)) {
                var inv = warehouse.handler(l, pos);
                for (int j = 0; j < inv.getSlots(); j++)
                  inv.insertItem(j, new ItemStack(Items.COBBLESTONE, 64), false);
              }
            CompoundTag saved = w.saveWithoutId(new CompoundTag()), queue = network.save();
            w.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            courier[0] = null;
            WorldData.get(l).logistics.put(r.home.identity, LogisticsNetwork.load(queue));
            phase[0] = 1;
            h.runAfterDelay(
                3,
                () -> {
                  Lumberton restored = Automatons.LUMBERTON.get().create(l);
                  restored.load(saved);
                  h.assertTrue(l.addFreshEntity(restored), "Cargo reload failed");
                  courier[0] = restored;
                });
            return;
          }
          if (phase[0] == 1 && w.status().equals("Warehouse full")) {
            h.assertTrue(
                w.mining.carried(Items.IRON_ORE) == 128, "Full warehouse lost cargo on reload");
            destination.extractItem(0, 64, false);
            destination.insertItem(0, new ItemStack(Items.IRON_ORE, 63), false);
            phase[0] = 2;
          }
          if (phase[0] == 2 && w.mining.carried(Items.IRON_ORE) == 127) {
            h.assertTrue(
                destination.getStackInSlot(0).getCount() == 64,
                "Partial delivery count differs from real insertion");
            destination.extractItem(1, 64, false);
            destination.extractItem(2, 64, false);
            phase[0] = 3;
          }
          if (phase[0] != 3 || !w.cargoEmpty()) return;
          int stored = 0;
          for (BlockPos pos : warehouse.chests(l))
            if (Storage.canonical(l, pos).equals(pos)) {
              var inv = warehouse.handler(l, pos);
              for (int j = 0; j < inv.getSlots(); j++)
                if (inv.getStackInSlot(j).is(Items.IRON_ORE))
                  stored += inv.getStackInSlot(j).getCount();
            }
          h.assertTrue(
              stored == 191
                  && source.getStackInSlot(0).isEmpty()
                  && source.getStackInSlot(1).isEmpty(),
              "Cargo lost/duplicated across partial recovery");
          w.setNoAi(true);
          h.succeed();
        });
  }
}
