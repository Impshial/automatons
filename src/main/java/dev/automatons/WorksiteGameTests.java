package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_worksites")
@PrefixGameTestTemplate(false)
public final class WorksiteGameTests {
  private record Rig(Station home, Station site) {}

  /**
   * Existing worker regression fixtures assume a completed worksite without testing its
   * construction.
   */
  public static void readyFixture(Station station, WorksitePurpose purpose) {
    station.purpose = purpose;
    BuildingProject p = new BuildingProject();
    p.anchor = station.getBlockPos();
    p.totem = station.identity;
    p.owner = station.owner;
    p.home = station.logisticsHome;
    p.purpose = purpose;
    p.ready = true;
    WorldData.get((ServerLevel) station.getLevel()).buildings.put(station.identity, p);
  }

  private static Rig rig(GameTestHelper h, WorksitePurpose purpose, boolean ready) {
    for (int x = 0; x < 28; x++)
      for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
    h.setBlock(new BlockPos(24, 1, 24), Automatons.PEDESTAL.get());
    h.setBlock(new BlockPos(15, 1, 15), Automatons.TOTEM.get());
    Station home = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(24, 1, 24)));
    Station site = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(15, 1, 15)));
    home.owner = UUID.randomUUID();
    home.registerHome();
    site.owner = home.owner;
    site.logisticsHome = home.identity;
    h.assertTrue(site.configure(purpose), "Purpose configuration failed");
    if (ready) site.project().ready = true;
    return new Rig(home, site);
  }

  private static Lumberton worker(GameTestHelper h, Rig r, int job, BlockPos at) {
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = r.home.owner;
    w.homeId = r.home.identity;
    w.home = r.home.getBlockPos();
    w.homeDimension = h.getLevel().dimension().location().toString();
    w.setJob(job);
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(at)));
    h.getLevel().addFreshEntity(w);
    w.assign(r.site);
    w.authorized = true;
    WorkerIdentity.update(w, "Deployed");
    return w;
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void five_templates_preserve_anchors_materials_and_open_mine(GameTestHelper h) {
    ServerLevel l = h.getLevel();
    Map<String, Integer> totals =
        Map.of("builder", 257, "farm", 243, "lumber", 203, "warehouse", 236, "minehead", 152);
    for (var entry : totals.entrySet()) {
      var t = BuildingTemplate.load(l, entry.getKey());
      h.assertTrue(
          t.materials.values().stream().mapToInt(Integer::intValue).sum() == entry.getValue(),
          "Incorrect exact BOM: " + entry.getKey());
      h.assertTrue(t.frames.size() == 1, "Missing real identification frame");
      for (Direction front : Direction.Plane.HORIZONTAL) {
        BlockPos origin = h.absolutePos(new BlockPos(14, 1, 14));
        h.assertTrue(t.world(origin, front, t.anchor).equals(origin), "Anchor moves when rotated");
        for (var c : t.cells)
          if (c.state().is(Blocks.CHEST) && !entry.getKey().equals("warehouse"))
            h.assertTrue(
                Math.max(
                        Math.abs(c.local().getX() - t.anchor.getX()),
                        Math.abs(c.local().getZ() - t.anchor.getZ()))
                    <= 4,
                "Chest adjustment not exported");
      }
      if (entry.getKey().equals("minehead"))
        h.assertTrue(
            t.cells.stream().noneMatch(c -> c.state().getBlock() instanceof TrapDoorBlock),
            "Mine hatch returned");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void one_totem_range_covers_work_and_chests(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.LUMBER, true);
    Map<WorksitePurpose, Integer> ranges =
        Map.of(
            WorksitePurpose.LUMBER,
            Balance.LUMBER.get(),
            WorksitePurpose.FARM,
            Balance.FARM_RANGE.get(),
            WorksitePurpose.MINE,
            Balance.MINE_SITE_RANGE.get(),
            WorksitePurpose.BUILDER,
            Balance.BUILDER_RANGE.get(),
            WorksitePurpose.WAREHOUSE,
            Balance.WAREHOUSE_RANGE.get());
    for (var entry : ranges.entrySet()) {
      r.site.purpose = entry.getKey();
      h.assertTrue(
          r.site.workRange() == entry.getValue(), "Wrong purpose range: " + entry.getKey());
      BlockPos edge = r.site.getBlockPos().east(r.site.workRange());
      h.assertTrue(
          Storage.inRange(r.site, edge) && TreeFinder.within(r.site, edge),
          "Chest and work boundary differ");
      h.assertTrue(
          Storage.inRange(r.site, r.site.getBlockPos().east())
              && TreeFinder.within(r.site, r.site.getBlockPos().east()),
          "Inner exclusion remains");
      h.assertTrue(
          !Storage.inRange(r.site, edge.east()) && !TreeFinder.within(r.site, edge.east()),
          "Range leaks outward");
      r.site.inventory.setStackInSlot(0, new ItemStack(Automatons.UPGRADES.get("storage_1").get()));
      h.assertTrue(
          !Storage.inRange(r.site, edge.east(4)) && !TreeFinder.within(r.site, edge.east(4)),
          "Retired range upgrade still takes effect");
      r.site.inventory.setStackInSlot(0, ItemStack.EMPTY);
    }
    r.site.purpose = WorksitePurpose.BUILDER;
    BlockPos chest = r.site.getBlockPos().east(r.site.workRange() + 1);
    h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(chest);
    h.assertTrue(
        Storage.handler(h.getLevel(), chest, r.site) == null,
        "Cached chest outside shared range is usable");
    r.site.inventory.setStackInSlot(0, new ItemStack(Automatons.UPGRADES.get("storage_1").get()));
    h.assertTrue(
        Storage.handler(h.getLevel(), chest, r.site) == null,
        "Retired range upgrade still allows extra chest access");
    r.site.inventory.setStackInSlot(0, ItemStack.EMPTY);
    h.assertTrue(
        Storage.handler(h.getLevel(), chest, r.site) == null,
        "Range reduction retained stale chest access");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void project_claim_survives_save_and_blocks_competing_builder(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    Lumberton a = worker(h, r, 4, new BlockPos(16, 1, 17)),
        b = worker(h, r, 4, new BlockPos(18, 1, 17));
    a.setNoAi(true);
    b.setNoAi(true);
    BuildingProject p = r.site.project();
    h.assertTrue(p.claim(a) && !p.claim(b), "Two builders claimed one site");
    BuildingProject saved = BuildingProject.load(p.save());
    h.assertTrue(
        saved.id.equals(p.id) && saved.builder.equals(a.getUUID()) && !saved.claim(b),
        "Reload discarded project ownership");
    long generation = saved.generation;
    saved.release(a.getUUID());
    h.assertTrue(
        saved.claim(b) && saved.generation > generation, "Ownership generation did not change");
    h.assertTrue(
        !r.site.configure(WorksitePurpose.FARM), "Purpose change overwrote occupied project");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 6000)
  public static void first_buildington_physically_builds_shed_without_runner(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    BlockPos chest = h.absolutePos(new BlockPos(21, 1, 15));
    h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(chest);
    r.site.storageComplete = true;
    var inventory = Storage.handler(h.getLevel(), chest, r.site);
    for (var e : r.site.project().template(h.getLevel()).materials.entrySet()) {
      ItemStack rest =
          net.minecraftforge.items.ItemHandlerHelper.insertItem(
              inventory, new ItemStack(e.getKey(), e.getValue()), false);
      h.assertTrue(rest.isEmpty(), "Bootstrap stock did not fit");
    }
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        inventory, new ItemStack(Items.CHEST), false);
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        inventory, new ItemStack(Automatons.HAMMER.get()), false);
    UUID identity = r.site.identity;
    Lumberton w = worker(h, r, 4, new BlockPos(17, 1, 17));
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0)
            System.out.println(
                "BUILDINGTON_BOOTSTRAP tick="
                    + h.getTick()
                    + " status="
                    + w.status()
                    + " pos="
                    + w.position()
                    + " completed="
                    + r.site.project().completed.size()
                    + " cargo="
                    + w.cargo.serializeNBT());
          if (!r.site.project().ready || r.site.project().builder != null) return;
          h.assertTrue(
              r.site.project().supplies.chest == null && w.cargoEmpty(),
              "Temporary chest or leftovers not returned home");
          h.assertTrue(
              r.site.identity.equals(identity)
                  && h.getLevel().getBlockEntity(r.site.getBlockPos()) == r.site,
              "Live Totem replaced");
          h.assertTrue(
              r.site.project().remaining(h.getLevel()).isEmpty(),
              "Ready with missing construction items");
          h.assertTrue(
              r.site.project().chests(h.getLevel()).stream()
                  .allMatch(p -> r.site.project().handler(h.getLevel(), p) != null),
              "Chest pairs or lids unusable");
          h.assertTrue(w.tools.getStackInSlot(0).getDamageValue() > 0, "Hammer not used");
          w.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 4000)
  public static void builder_fills_air_foundation_from_outside(GameTestHelper h) {
    perimeterBuild(h, null);
  }

  @GameTest(template = "empty", timeoutTicks = 4000)
  public static void builder_leaves_chest_and_finishes_outside(GameTestHelper h) {
    perimeterBuild(h, Blocks.CHEST);
  }

  @GameTest(template = "empty", timeoutTicks = 4000)
  public static void builder_leaves_stonecutter_and_finishes_outside(GameTestHelper h) {
    perimeterBuild(h, Blocks.STONECUTTER);
  }

  @GameTest(template = "empty", timeoutTicks = 4000)
  public static void builder_leaves_crafting_table_and_finishes_outside(GameTestHelper h) {
    perimeterBuild(h, Blocks.CRAFTING_TABLE);
  }

  private static void perimeterBuild(GameTestHelper h, Block furniture) {
    perimeterBuild(h, furniture, false);
  }

  @GameTest(template = "empty", timeoutTicks = 5000)
  public static void builder_preserves_extra_interior_workstations_and_chest(GameTestHelper h) {
    perimeterBuild(h, null, true);
  }

  private static void perimeterBuild(GameTestHelper h, Block furniture, boolean extraInterior) {
    perimeterBuild(h, furniture, extraInterior, false);
  }

  @GameTest(template = "empty", timeoutTicks = 4000)
  public static void builder_demolishes_structural_obstacles_and_preserves_container_loot(
      GameTestHelper h) {
    perimeterBuild(h, null, false, true);
  }

  private static void perimeterBuild(
      GameTestHelper h, Block furniture, boolean extraInterior, boolean obstructed) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    BuildingProject project = r.site.project();
    var template = project.template(h.getLevel());
    BlockPos extraCutter = project.at(h.getLevel(), 5, 1, 2);
    BlockPos extraChest = project.at(h.getLevel(), 4, 1, 3);
    if (extraInterior) {
      h.getLevel().setBlock(extraCutter, Blocks.STONECUTTER.defaultBlockState(), 3);
      h.getLevel().setBlock(extraChest, Blocks.CHEST.defaultBlockState(), 3);
      ((net.minecraft.world.level.block.entity.ChestBlockEntity)
              h.getLevel().getBlockEntity(extraChest))
          .setItem(0, new ItemStack(Items.DIAMOND, 7));
    }
    BlockPos start = h.absolutePos(new BlockPos(21, 1, 17));
    for (var cell : template.cells) {
      BlockPos at = template.world(project.anchor, project.front, cell.local());
      if (cell.local().getY() == 0 && !at.equals(project.anchor.below())) {
        h.getLevel()
            .setBlock(at, furniture == null ? Blocks.AIR.defaultBlockState() : cell.state(), 3);
      }
      if (furniture != null && cell.state().is(furniture)) {
        h.getLevel().setBlock(at, cell.state().rotate(BuildingTemplate.rotation(project.front)), 2);
        start = at.above();
      }
    }
    if (obstructed) {
      var placements = template.placements();
      Block[] obstacles = {Blocks.STONECUTTER, Blocks.OBSIDIAN, Blocks.CHEST};
      for (int index = 0; index < obstacles.length; index++) {
        BlockPos at = template.world(project.anchor, project.front, placements.get(index).local());
        h.getLevel().setBlock(at, obstacles[index].defaultBlockState(), 3);
        if (obstacles[index] == Blocks.CHEST)
          ((net.minecraft.world.level.block.entity.ChestBlockEntity)
                  h.getLevel().getBlockEntity(at))
              .setItem(0, new ItemStack(Items.DIAMOND, 7));
      }
    }
    if (obstructed) {
      for (var cell : template.cells)
        if (cell.state().getBlock() instanceof DoorBlock
            && cell.state().getValue(DoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
          h.getLevel()
              .setBlock(
                  template.world(project.anchor, project.front, cell.local()),
                  Blocks.BARREL.defaultBlockState(),
                  3);
    }
    BlockPos supply = project.anchor.east(6);
    h.getLevel().setBlock(supply, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(supply);
    r.site.storageComplete = true;
    var inventory = Storage.handler(h.getLevel(), supply, r.site);
    for (var entry : template.materials.entrySet())
      h.assertTrue(
          net.minecraftforge.items.ItemHandlerHelper.insertItem(
                  inventory, new ItemStack(entry.getKey(), entry.getValue()), false)
              .isEmpty(),
          "Fixture supplies did not fit");
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        inventory, new ItemStack(Items.CHEST), false);
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        inventory, new ItemStack(Automatons.HAMMER.get()), false);
    Lumberton w = worker(h, r, 4, new BlockPos(21, 1, 17));
    w.setPos(Vec3.atBottomCenterOf(start));
    w.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
    int[] previousWear = {0};
    long[] lastPlacement = {-100};
    Set<BlockPos> workPositions = new HashSet<>();
    Set<String> workSides = new HashSet<>();
    boolean[] collecting = {false};
    int[] supplyTrips = {0};
    h.onEachTick(
        () -> {
          boolean atSupplies = w.status().equals("Collecting construction materials");
          if (atSupplies && !collecting[0]) supplyTrips[0]++;
          collecting[0] = atSupplies;
          int wear = w.tools.getStackInSlot(0).getDamageValue();
          if (wear > previousWear[0]) {
            h.assertTrue(
                h.getTick() - lastPlacement[0] >= 2,
                "Construction was not slowed to one action every two ticks");
            lastPlacement[0] = h.getTick();
            h.assertTrue(
                !w.getBoundingBox().intersects(project.bounds(h.getLevel())),
                "Builder placed while inside footprint/on furniture");
            workPositions.add(w.blockPosition());
            var bounds = project.bounds(h.getLevel());
            if (w.getX() < bounds.minX) workSides.add("west");
            if (w.getX() > bounds.maxX) workSides.add("east");
            if (w.getZ() < bounds.minZ) workSides.add("north");
            if (w.getZ() > bounds.maxZ) workSides.add("south");
            h.assertTrue(!w.getMoveControl().hasWanted(), "Stale movement during placement");
          }
          previousWear[0] = wear;
          if (w.status().equals("Moving outside building")
              && w.building.blocksRoute(new net.minecraft.world.phys.AABB(project.anchor))) {
            var path = w.getNavigation().getPath();
            if (path != null)
              for (int node = path.getNextNodeIndex(); node < path.getNodeCount(); node++)
                h.assertTrue(
                    !w.building.blocksRoute(
                        w.getDimensions(net.minecraft.world.entity.Pose.STANDING)
                            .makeBoundingBox(
                                Vec3.atBottomCenterOf(path.getNode(node).asBlockPos()))),
                    "Perimeter walk cuts through the structure");
          }
          if (h.getTick() % 200 == 0)
            System.out.println(
                "PERIMETER_BUILD "
                    + furniture
                    + " tick="
                    + h.getTick()
                    + " status="
                    + w.status()
                    + " pos="
                    + w.position()
                    + " completed="
                    + project.completed.size());
          if (!project.ready) return;
          if (obstructed) {
            int diamonds =
                h
                    .getLevel()
                    .getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        project.bounds(h.getLevel()).inflate(3))
                    .stream()
                    .filter(e -> e.getItem().is(Items.DIAMOND))
                    .mapToInt(e -> e.getItem().getCount())
                    .sum();
            h.assertTrue(
                diamonds
                        + w.mining.carried(Items.DIAMOND)
                        + project.supplies.count(h.getLevel(), Items.DIAMOND)
                    == 7,
                "Demolished container lost or duplicated contents");
          }
          h.assertTrue(
              supplyTrips[0] <= 6, "Builder made repeated small supply trips: " + supplyTrips[0]);
          h.assertTrue(
              workPositions.size() >= 4 && workSides.size() >= 3,
              "Builder did not walk around the structure: " + workSides + " " + workPositions);
          if (extraInterior) {
            h.assertTrue(
                h.getLevel().getBlockState(extraCutter).is(Blocks.STONECUTTER),
                "Extra workstation demolished");
            var chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity)
                    h.getLevel().getBlockEntity(extraChest);
            h.assertTrue(
                chest != null
                    && chest.getItem(0).is(Items.DIAMOND)
                    && chest.getItem(0).getCount() == 7,
                "Extra chest or contents changed");
          }
          h.assertTrue(
              project.remaining(h.getLevel()).isEmpty(),
              "Incomplete foundation or upper structure");
          for (var cell : template.placements()) {
            BlockPos at = template.world(project.anchor, project.front, cell.local());
            h.assertTrue(
                Construction.matches(
                    h.getLevel().getBlockState(at),
                    cell.state().rotate(BuildingTemplate.rotation(project.front))),
                "Missing blueprint block at " + cell.local());
          }
          w.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void builder_names_unbreakable_obstruction_without_spending_items(
      GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    Lumberton w = worker(h, r, 4, new BlockPos(21, 1, 17));
    w.setNoAi(true);
    w.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
    BuildingProject p = r.site.project();
    var cell = p.template(h.getLevel()).placements().get(0);
    BlockPos at = p.template(h.getLevel()).world(p.anchor, p.front, cell.local());
    ItemStack cost = BuildingTemplate.cost(cell.state());
    w.cargo.setStackInSlot(0, new ItemStack(cost.getItem(), 32));
    h.getLevel().setBlock(at, Blocks.BEDROCK.defaultBlockState(), 3);
    p.tick(w, r.site);
    h.assertTrue(
        w.status()
            .equals("Unbreakable Bedrock at " + at.getX() + ", " + at.getY() + ", " + at.getZ()),
        "Misleading obstruction message: " + w.status());
    h.assertTrue(
        h.getLevel().getBlockState(at).is(Blocks.BEDROCK)
            && w.cargo.getStackInSlot(0).getCount() == 32
            && w.activeTool().getDamageValue() == 0,
        "Blocked construction spent items or broke obstacle");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void builder_keeps_needed_materials_when_all_cargo_slots_are_used(
      GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    BlockPos supply = r.site.getBlockPos().east(6);
    h.getLevel().setBlock(supply, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(supply);
    r.site.storageComplete = true;
    Lumberton w = worker(h, r, 4, new BlockPos(20, 1, 15));
    w.setNoAi(true);
    w.setOnGround(true);
    w.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
    var materials = new ArrayList<>(r.site.project().remaining(h.getLevel()).entrySet());
    for (int slot = 0; slot < w.cargo.getSlots(); slot++) {
      var material = materials.get(slot);
      w.cargo.setStackInSlot(
          slot, new ItemStack(material.getKey(), Math.min(64, material.getValue())));
    }
    h.assertTrue(WorkSupplies.full(w), "Cargo fixture is not full");
    w.building.tick(r.site);
    h.assertTrue(!w.status().equals("Storing cargo"), "Needed materials sent back to storage");
    var stock = Storage.handler(h.getLevel(), supply, r.site);
    for (int slot = 0; slot < stock.getSlots(); slot++)
      h.assertTrue(
          stock.getStackInSlot(slot).isEmpty(), "Construction materials deposited prematurely");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void broken_totem_releases_reservation_and_keeps_supply_items(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    Lumberton w = worker(h, r, 4, new BlockPos(21, 1, 17));
    w.setNoAi(true);
    BuildingProject p = r.site.project();
    p.claim(w);
    BlockPos supply = r.site.getBlockPos().east(6);
    h.getLevel().setBlock(supply, Blocks.CHEST.defaultBlockState(), 3);
    var chest =
        (net.minecraft.world.level.block.entity.ChestBlockEntity)
            h.getLevel().getBlockEntity(supply);
    chest.getPersistentData().putUUID("AutomatonsConstructionChest", p.id);
    chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
    p.supplies.chest = supply;
    p.supplies.stocked = true;
    var saved = BuildingProject.load(p.save());
    h.assertTrue(
        saved.supplies.chest.equals(supply)
            && saved.supplies.stocked
            && saved.supplies.count(h.getLevel(), Items.DIAMOND) == 7,
        "Supply ownership lost on reload");
    h.getLevel().setBlock(r.site.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
    h.assertTrue(
        !WorldData.get(h.getLevel()).buildings.containsKey(r.site.identity)
            && w.building.active() == null,
        "Removed Totem kept project/claim");
    h.assertTrue(
        !ConstructionSupply.reserved(h.getLevel(), supply)
            && !chest.getPersistentData().hasUUID("AutomatonsConstructionChest")
            && chest.getItem(0).getCount() == 7,
        "Cancellation consumed or reserved chest contents");
    h.getLevel().setBlock(r.site.getBlockPos(), Automatons.TOTEM.get().defaultBlockState(), 3);
    Station replacement = (Station) h.getLevel().getBlockEntity(r.site.getBlockPos());
    replacement.owner = r.home.owner;
    replacement.logisticsHome = r.home.identity;
    h.assertTrue(
        replacement.configure(WorksitePurpose.BUILDER), "Deleted reservation blocks new site");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1000)
  public static void workers_await_construction_at_main_pedestal(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.MINE, false);
    Lumberton w = worker(h, r, WorkerJob.MININGTON, new BlockPos(8, 1, 8));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              w.status().contains("Waiting for worksite construction"),
              "Still returning: " + w.status());
          h.assertTrue(
              w.position().distanceToSqr(Vec3.atCenterOf(r.home.getBlockPos())) < 32,
              "Waiting away from main pedestal");
          w.setNoAi(true);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void runnington_stocks_construction_chest_without_collecting_it(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    var level = h.getLevel();
    BuildingProject project = r.site.project();
    Lumberton builder = worker(h, r, WorkerJob.BUILDINGTON, new BlockPos(20, 1, 18));
    builder.setNoAi(true);
    project.claim(builder);
    BlockPos supply = r.site.getBlockPos().east(6), staged = supply.north(3);
    level.setBlock(supply, Blocks.CHEST.defaultBlockState(), 3);
    level.setBlock(staged, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(supply);
    r.site.chests.add(staged);
    r.site.storageComplete = true;
    var staging =
        (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(staged);
    staging.getPersistentData().putUUID("AutomatonsConstructionChest", project.id);
    project.supplies.chest = staged;
    staging.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
    var stock = Storage.handler(level, supply, r.site);
    stock.insertItem(0, new ItemStack(Items.OAK_PLANKS, 32), false);
    stock.insertItem(1, new ItemStack(Items.TORCH, 8), false);
    stock.insertItem(2, new ItemStack(Items.IRON_PICKAXE), false);
    stock.insertItem(3, new ItemStack(Items.OAK_SAPLING, 8), false);
    BlockPos runnerSite = h.absolutePos(new BlockPos(6, 1, 15));
    level.setBlock(runnerSite, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station warehouse = (Station) level.getBlockEntity(runnerSite);
    warehouse.owner = r.home.owner;
    warehouse.logisticsHome = r.home.identity;
    readyFixture(warehouse, WorksitePurpose.WAREHOUSE);
    // A courier can help bootstrap construction before its own warehouse is built.
    warehouse.project().ready = false;
    Lumberton runner =
        worker(h, new Rig(r.home, warehouse), WorkerJob.RUNNINGTON, new BlockPos(19, 1, 20));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              project.supplies.count(level, Items.OAK_PLANKS) == 32,
              "Construction delivery pending: " + runner.status());
          h.assertTrue(
              runner.cargoEmpty() && builder.cargoEmpty(), "Delivery did not reach real chest");
          h.assertTrue(
              stock.getStackInSlot(0).isEmpty()
                  && stock.getStackInSlot(1).getCount() == 8
                  && stock.getStackInSlot(2).is(Items.IRON_PICKAXE)
                  && stock.getStackInSlot(3).getCount() == 8,
              "Construction courier took protected worker supplies");
          var network = LogisticsNetwork.get(runner);
          network.changed(level);
          network.refresh(level);
          h.assertTrue(
              project.supplies.count(level, Items.COBBLESTONE) == 8
                  && network.demands.values().stream()
                      .noneMatch(d -> d.source != null && staged.equals(d.source.pos())),
              "Staged stock advertised or taken as surplus");
          runner.setNoAi(true);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void farm_board_persists_replant_and_releases_only_cell_claims(GameTestHelper h) {
    FarmPlan f = new FarmPlan();
    BlockPos p = h.absolutePos(new BlockPos(4, 0, 4));
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    f.cells.add(p.asLong());
    f.replant.put(p.asLong(), Items.CARROT);
    f.discovered = true;
    h.assertTrue(f.claim(p, a) && !f.claim(p, b), "Shared field double claim");
    FarmPlan saved = FarmPlan.load(f.save());
    h.assertTrue(
        saved.cells.contains(p.asLong()) && saved.replant.get(p.asLong()) == Items.CARROT,
        "Pending replant lost");
    f.release(a);
    h.assertTrue(
        f.claim(p, b) && f.replant.containsKey(p.asLong()),
        "Service visit deleted pending replant");
    h.assertTrue(saved.reserves(h.getLevel()).get(Items.CARROT) == 1, "Dual-use crop not reserved");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1500)
  public static void farmington_harvests_and_replants_existing_irregular_cells(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.FARM, true);
    BlockPos soil = h.absolutePos(new BlockPos(5, 0, 8));
    for (BlockPos p : List.of(soil, soil.east(), soil.south(2))) {
      h.getLevel()
          .setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
      h.getLevel()
          .setBlock(p.above(), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
    }
    Lumberton w = worker(h, r, 5, new BlockPos(7, 1, 8));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_HOE));
    w.cargo.setStackInSlot(0, new ItemStack(Items.CARROT, 3));
    h.succeedWhen(
        () -> {
          h.assertTrue(r.site.farm != null && r.site.farm.discovered, "Field not adopted");
          h.assertTrue(
              !r.site.farm.generated && r.site.farm.cells.size() == 3,
              "Irregular field replaced/expanded");
          h.assertTrue(w.mining.carried(Items.CARROT) > 3, "No real crop drops collected");
          h.assertTrue(r.site.farm.replant.isEmpty(), "Harvest left pending replant");
          h.assertTrue(
              List.of(soil, soil.east(), soil.south(2)).stream()
                  .allMatch(
                      p ->
                          h.getLevel().getBlockState(p.above()).is(Blocks.CARROTS)
                              && h.getLevel().getBlockState(p.above()).getValue(CropBlock.AGE) < 7),
              "Crops not replanted");
          w.setNoAi(true);
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 60)
  public static void five_templates_have_usable_rotated_storage_doors_and_displays(
      GameTestHelper h) {
    var l = h.getLevel();
    int index = 0;
    for (WorksitePurpose purpose : WorksitePurpose.values()) {
      if (purpose == WorksitePurpose.UNCONFIGURED) continue;
      var t = BuildingTemplate.load(l, purpose.blueprint);
      int direction = 0;
      for (Direction front : Direction.Plane.HORIZONTAL) {
        BlockPos anchor = h.absolutePos(new BlockPos(12 + index * 16, 2, 12 + direction++ * 18));
        l.setBlock(anchor, Automatons.TOTEM.get().defaultBlockState(), 3);
        Station site = (Station) l.getBlockEntity(anchor);
        site.owner = UUID.randomUUID();
        BuildingProject project = new BuildingProject();
        project.anchor = anchor;
        project.totem = site.identity;
        project.owner = site.owner;
        project.purpose = purpose;
        project.front = front;
        project.ready = true;
        for (var cell : t.cells)
          project.completed.add(t.world(anchor, front, cell.local()).asLong());
        for (var cell : t.cells)
          if (!cell.local().equals(t.anchor))
            l.setBlock(
                t.world(anchor, front, cell.local()),
                cell.state().rotate(BuildingTemplate.rotation(front)),
                2);
        for (var cell : t.cells)
          if (!cell.state().isAir()) {
            BlockPos at = t.world(anchor, front, cell.local());
            var expected = cell.state().rotate(BuildingTemplate.rotation(front));
            h.assertTrue(
                Construction.matches(l.getBlockState(at), expected),
                "Rotated cell mismatch: " + purpose + " " + front + " " + cell.local());
            h.assertTrue(
                expected.canSurvive(l, at), "Unsupported fixture: " + purpose + " " + cell.local());
          }
        for (BlockPos chest : project.chests(l)) {
          var inv = project.handler(l, chest);
          h.assertTrue(
              inv != null && inv.getSlots() == 54,
              "Unusable double chest: " + purpose + " " + front);
          for (int slot = 0; slot < inv.getSlots(); slot++)
            h.assertTrue(inv.getStackInSlot(slot).isEmpty(), "Schematic container copied loot");
        }
        for (var display : t.frames) {
          var frame =
              new net.minecraft.world.entity.decoration.ItemFrame(
                  l, t.world(anchor, front, display.local()), front);
          frame.setItem(new ItemStack(display.item()), false);
          h.assertTrue(frame.survives(), "Display has no valid support: " + purpose + " " + front);
          l.addFreshEntity(frame);
        }
      }
      index++;
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1800)
  public static void partial_builder_kit_keeps_project_claim_without_door_fault(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    BlockPos chest = r.site.getBlockPos().east(6);
    h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(chest);
    var stock = Storage.handler(h.getLevel(), chest, r.site);
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        stock, new ItemStack(Automatons.HAMMER.get()), false);
    net.minecraftforge.items.ItemHandlerHelper.insertItem(stock, new ItemStack(Items.CHEST), false);
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        stock, new ItemStack(Items.COBBLESTONE, 64), false);
    Lumberton w = worker(h, r, 4, new BlockPos(17, 1, 17));
    h.runAtTickTime(
        1600,
        () -> {
          h.assertTrue(
              !r.site.project().completed.isEmpty()
                  && r.site.project().remaining(h.getLevel()).getOrDefault(Items.COBBLESTONE, 0)
                      == 0,
              "Builder waited for the full kit instead of using available cobblestone");
          h.assertTrue(
              !r.site.project().ready && !r.site.project().paused,
              "Partial kit caused a permanent construction fault");
          h.assertTrue(
              w.status().startsWith("Waiting for materials:"),
              "Missing stock was not identified: " + w.status());
          h.assertTrue(
              w.getUUID().equals(r.site.project().builder),
              "Shortage abandoned the accepted project");
          w.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1400)
  public static void builder_leaves_shed_north_both_hinges(GameTestHelper h) {
    builderDoor(h, Direction.NORTH);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void pedestal_build_lists_scope_progress_and_unloaded_projects(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.BUILDER, false);
    var project = r.site.project();
    var worker = worker(h, r, WorkerJob.BUILDINGTON, new BlockPos(20, 1, 20));
    worker.setNoAi(true);
    project.claim(worker);
    worker.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 13));
    var lists = BuildMaterials.network(r.home);
    h.assertTrue(lists.size() == 1, "Pedestal omitted its active build");
    var rows = lists.getCompound(0).getList("Rows", 10);
    boolean found = false;
    for (var value : rows) {
      var row = (CompoundTag) value;
      if (!ItemStack.of(row.getCompound("Item")).is(Items.COBBLESTONE)) continue;
      found = true;
      h.assertTrue(
          row.getInt("Have") == 13 && row.getInt("Missing") == Math.max(0, row.getInt("Left") - 13),
          "Pedestal stock counts differ from worker build list");
    }
    h.assertTrue(found, "Build materials missing");
    project.ready = true;
    h.assertTrue(BuildMaterials.network(r.home).isEmpty(), "Completed building remains active");
    project.ready = false;
    project.paused = true;
    h.assertTrue(
        BuildMaterials.network(r.home).size() == 1, "Paused unfinished project disappeared");
    UUID own = project.home;
    project.home = UUID.randomUUID();
    h.assertTrue(BuildMaterials.network(r.home).isEmpty(), "Another network's project leaked");
    project.home = own;
    var unloaded = new BuildingProject();
    unloaded.home = r.home.identity;
    unloaded.owner = r.home.owner;
    unloaded.totem = UUID.randomUUID();
    unloaded.anchor = new BlockPos(100000, 64, 100000);
    WorldData.get(h.getLevel()).buildings.put(unloaded.totem, unloaded);
    lists = BuildMaterials.network(r.home);
    h.assertTrue(
        lists.size() == 2 && !h.getLevel().hasChunkAt(unloaded.anchor),
        "Unloaded project was omitted or force-loaded");
    h.assertTrue(
        java.util.stream.StreamSupport.stream(lists.spliterator(), false)
            .map(v -> (CompoundTag) v)
            .anyMatch(n -> !n.getBoolean("Loaded") && n.getList("Rows", 10).isEmpty()),
        "Unloaded stock counts were invented");
    WorldData.get(h.getLevel()).buildings.remove(unloaded.totem);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1400)
  public static void builder_leaves_shed_south_both_hinges(GameTestHelper h) {
    builderDoor(h, Direction.SOUTH);
  }

  @GameTest(template = "empty", timeoutTicks = 1400)
  public static void builder_leaves_shed_east_both_hinges(GameTestHelper h) {
    builderDoor(h, Direction.EAST);
  }

  @GameTest(template = "empty", timeoutTicks = 1400)
  public static void builder_leaves_shed_west_both_hinges(GameTestHelper h) {
    builderDoor(h, Direction.WEST);
  }

  private static void builderDoor(GameTestHelper h, Direction front) {
    Rig rig = rig(h, WorksitePurpose.BUILDER, true);
    rig.home.recall(true);
    var level = h.getLevel();
    var project = rig.site.project();
    project.front = front;
    var template = project.template(level);
    BlockPos door = null;
    for (var cell : template.cells) {
      var at = template.world(project.anchor, front, cell.local());
      if (at.equals(project.anchor)) continue;
      var state = cell.state().rotate(BuildingTemplate.rotation(front));
      level.setBlock(at, state, 2);
      if (state.getBlock() instanceof DoorBlock
          && state.getValue(DoorBlock.HALF)
              == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) door = at;
    }
    h.assertTrue(door != null, "Builder shed fixture has no door");
    final BlockPos doorway = door;
    var worker = worker(h, rig, WorkerJob.BUILDINGTON, new BlockPos(15, 1, 12));
    worker.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
    final int[] crossings = {0}, opens = {0};
    final boolean[] wasOpen = {false};
    java.util.function.Consumer<net.minecraft.world.level.block.state.properties.DoorHingeSide>
        start =
            hinge -> {
              for (var at : List.of(doorway, doorway.above())) {
                var state = level.getBlockState(at);
                level.setBlock(
                    at, state.setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, false), 2);
              }
              var inside = Vec3.atBottomCenterOf(doorway.relative(front.getOpposite(), 1));
              // Offset toward the panel: following the block center alone collides with it.
              worker.moveTo(
                  inside.add(
                      front.getAxis() == Direction.Axis.Z ? .105 : 0,
                      0,
                      front.getAxis() == Direction.Axis.X ? .105 : 0));
              worker.setDeltaMovement(Vec3.ZERO);
              worker.getNavigation().stop();
              ((WorkerMoveControl) worker.getMoveControl()).stop();
              wasOpen[0] = false;
            };
    start.accept(net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT);
    h.onEachTick(
        () -> {
          boolean open = level.getBlockState(doorway).getValue(DoorBlock.OPEN);
          if (open && !wasOpen[0]) opens[0]++;
          wasOpen[0] = open;
          h.assertTrue(
              opens[0] <= 4, "Builder repeatedly reopened the door instead of crossing: " + front);
          if (worker.distanceToSqr(Vec3.atCenterOf(rig.home.getBlockPos())) < 25
              && !worker.getBoundingBox().intersects(project.bounds(level))) {
            if (++crossings[0] == 1) {
              start.accept(net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
            } else {
              h.assertTrue(opens[0] >= 2, "Builder bypassed closed shed doors");
              worker.setNoAi(true);
              h.succeed();
            }
          }
        });
  }

  @GameTest(template = "empty", timeoutTicks = 11000)
  public static void two_farmers_share_one_field_bucket_and_optional_bone_meal(GameTestHelper h) {
    // The GameTest runner's origin ticket does not entity-tick this larger remote field.
    // Keep the isolated fixture ticking; production workers never force-load chunks.
    BlockPos origin = h.absolutePos(BlockPos.ZERO);
    for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 95) >> 4; cx++)
      for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 95) >> 4; cz++)
        h.getLevel().setChunkForced(cx, cz, true);
    for (int x = 0; x < 96; x++)
      for (int z = 0; z < 96; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
    h.setBlock(new BlockPos(55, 1, 55), Automatons.PEDESTAL.get());
    h.setBlock(new BlockPos(48, 1, 48), Automatons.TOTEM.get());
    Station home = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(55, 1, 55)));
    Station site = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(48, 1, 48)));
    home.owner = UUID.randomUUID();
    home.registerHome();
    site.owner = home.owner;
    site.logisticsHome = home.identity;
    h.assertTrue(site.configure(WorksitePurpose.FARM), "Farm site setup failed");
    site.project().ready = true;
    site.farm = new FarmPlan();
    site.farm.boneMeal = false;
    WorldData.get(h.getLevel()).farms.put(site.identity, site.farm);
    BlockPos chest = site.getBlockPos().east(7);
    h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    site.chests.add(chest);
    var stock = Storage.handler(h.getLevel(), chest, site);
    for (ItemStack stack :
        List.of(
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.CARROT, 128),
            new ItemStack(Items.BONE_MEAL, 16)))
      h.assertTrue(
          net.minecraftforge.items.ItemHandlerHelper.insertItem(stock, stack, false).isEmpty(),
          "Farm stock did not fit");
    Rig r = new Rig(home, site);
    Lumberton a = worker(h, r, 5, new BlockPos(42, 1, 51)),
        b = worker(h, r, 5, new BlockPos(43, 1, 52));
    a.tools.setStackInSlot(0, new ItemStack(Items.IRON_HOE));
    b.tools.setStackInSlot(0, new ItemStack(Items.IRON_HOE));
    boolean[] planted = {false};
    h.onEachTick(
        () -> {
          FarmPlan f = site.farm;
          if (h.getTick() % 400 == 0)
            System.out.println(
                "FARM_SHARED tick="
                    + h.getTick()
                    + " cells="
                    + f.cells.size()
                    + " first="
                    + a.status()
                    + " second="
                    + b.status()
                    + " water="
                    + f.water
                    + " diag="
                    + a.diagnostic()
                    + " nav="
                    + a.getNavigation().getPath()
                    + " navdone="
                    + a.getNavigation().isDone()
                    + " wanted="
                    + a.getMoveControl().getWantedX()
                    + ","
                    + a.getMoveControl().getWantedY()
                    + ","
                    + a.getMoveControl().getWantedZ()
                    + " reach="
                    + (f.water != null && a.inReach(f.water))
                    + " cargo="
                    + a.cargo.serializeNBT()
                    + " waterState="
                    + (f.water == null ? "none" : h.getLevel().getBlockState(f.water)));
          if (!f.generated
              || f.cells.size() != 80
              || f.cells.stream()
                  .anyMatch(
                      p -> !h.getLevel().getBlockState(BlockPos.of(p).above()).is(Blocks.CARROTS)))
            return;
          h.assertTrue(
              f.water != null && h.getLevel().getFluidState(f.water).isSource(),
              "Field not irrigated");
          Map<Item, Integer> total = new HashMap<>();
          BuildingProject.count(stock, total);
          BuildingProject.count(a.cargo, total);
          BuildingProject.count(b.cargo, total);
          for (var e :
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      new net.minecraft.world.phys.AABB(f.water).inflate(16)))
            total.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
          h.assertTrue(
              total.getOrDefault(Items.BUCKET, 0) == 1
                  && total.getOrDefault(Items.WATER_BUCKET, 0) == 0,
              "Water bucket was duplicated/lost");
          h.assertTrue(
              a.tools.getStackInSlot(0).getDamageValue()
                      + b.tools.getStackInSlot(0).getDamageValue()
                  == 80,
              "Wrong physical hoe wear");
          if (!planted[0]) {
            h.assertTrue(
                total.getOrDefault(Items.BONE_MEAL, 0) == 16, "Disabled bone meal was consumed");
            planted[0] = true;
            f.boneMeal = true;
            return;
          }
          if (total.getOrDefault(Items.BONE_MEAL, 0) < 16) {
            a.setNoAi(true);
            b.setNoAi(true);
            h.succeed();
          }
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void courier_rechecks_growing_field_reserve_before_pickup(GameTestHelper h) {
    Rig r = rig(h, WorksitePurpose.FARM, true);
    r.site.farm = new FarmPlan();
    r.site.farm.defaultPlant = Items.CARROT;
    WorldData.get(h.getLevel()).farms.put(r.site.identity, r.site.farm);
    for (int n = 0; n < 8; n++)
      r.site.farm.cells.add(r.site.getBlockPos().west(10).offset(n, -1, 0).asLong());
    BlockPos chest = r.site.getBlockPos().east(6);
    h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
    r.site.chests.add(chest);
    var stock = Storage.handler(h.getLevel(), chest, r.site);
    net.minecraftforge.items.ItemHandlerHelper.insertItem(
        stock, new ItemStack(Items.CARROT, 96), false);
    Lumberton farmer = worker(h, r, 5, new BlockPos(16, 1, 18)),
        runner = worker(h, r, 3, new BlockPos(17, 1, 18));
    farmer.setNoAi(true);
    runner.setNoAi(true);
    var network = LogisticsNetwork.get(runner);
    network.refresh(h.getLevel());
    var parcel = new LogisticsNetwork.Parcel();
    parcel.runner = runner.getUUID();
    parcel.item = new ItemStack(Items.CARROT);
    parcel.source = new LogisticsNetwork.Endpoint(r.site.identity, chest, null);
    parcel.destination =
        new LogisticsNetwork.Endpoint(r.site.identity, farmer.blockPosition(), farmer.getUUID());
    parcel.request = "collect:reserve-test";
    parcel.quantity = 32;
    parcel.phase = LogisticsNetwork.Phase.TO_SOURCE;
    network.parcels.put(runner.getUUID(), parcel);
    r.site.farm.cells.clear();
    for (int x = 0; x < 10; x++)
      for (int z = 0; z < 8; z++)
        r.site.farm.cells.add(r.site.getBlockPos().west(12).offset(x, -1, z).asLong());
    h.assertTrue(
        network.pickupAllowance(h.getLevel(), parcel) == 16,
        "Stale parcel would consume the 80-cell replant reserve");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void idle_builder_enters_north_shed_and_resumes_new_job(GameTestHelper h) {
    idleBuilder(h, Direction.NORTH);
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void idle_builder_enters_south_shed_and_resumes_new_job(GameTestHelper h) {
    idleBuilder(h, Direction.SOUTH);
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void idle_builder_enters_east_shed_and_resumes_new_job(GameTestHelper h) {
    idleBuilder(h, Direction.EAST);
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void idle_builder_enters_west_shed_and_resumes_new_job(GameTestHelper h) {
    idleBuilder(h, Direction.WEST);
  }

  private static void idleBuilder(GameTestHelper h, Direction front) {
    Rig r = rig(h, WorksitePurpose.BUILDER, true);
    var level = h.getLevel();
    var shed = r.site.project();
    shed.front = front;
    var template = shed.template(level);
    BlockPos door = null;
    for (var cell : template.cells) {
      BlockPos at = template.world(shed.anchor, front, cell.local());
      if (at.equals(shed.anchor)) continue;
      var state = cell.state().rotate(BuildingTemplate.rotation(front));
      level.setBlock(at, state, 2);
      shed.completed.add(at.asLong());
      if (state.getBlock() instanceof DoorBlock
          && state.getValue(DoorBlock.HALF)
              == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) door = at;
    }
    h.assertTrue(door != null, "Shed has no door");
    var w = worker(h, r, WorkerJob.BUILDINGTON, new BlockPos(24, 1, 24));
    w.moveTo(Vec3.atBottomCenterOf(door.relative(front, 3)));
    w.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
    final long[] arrived = {-1};
    final Vec3[] parked = {null};
    final BuildingProject[] request = {null};
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0)
            System.out.println(
                "IDLE_BUILDER "
                    + front
                    + " tick="
                    + h.getTick()
                    + " status="
                    + w.status()
                    + " pos="
                    + w.position()
                    + " door="
                    + template.cells.stream()
                        .filter(c -> c.state().getBlock() instanceof DoorBlock)
                        .map(c -> template.world(shed.anchor, front, c.local()))
                        .toList()
                    + " arrived="
                    + arrived[0]
                    + " request="
                    + (request[0] == null ? "none" : request[0].builder)
                    + " path="
                    + w.getNavigation().getPath()
                    + " done="
                    + w.getNavigation().isDone());
          if (request[0] != null) {
            if (request[0].builder == null) return;
            h.assertTrue(
                w.getUUID().equals(request[0].builder), "New construction request was abandoned");
            if (!w.getBoundingBox().intersects(shed.bounds(level))) {
              h.assertTrue(
                  w.position().distanceToSqr(parked[0]) > 9, "Builder never left the shed");
              w.setNoAi(true);
              h.succeed();
            }
            return;
          }
          if (!w.status().equals("Waiting for construction requests")) return;
          h.assertTrue(
              shed.bounds(level).deflate(1, 0, 1).contains(w.position()),
              "Waiting outside the shed: " + w.position());
          if (arrived[0] < 0) {
            arrived[0] = level.getGameTime();
            parked[0] = w.position();
          }
          h.assertTrue(w.position().distanceToSqr(parked[0]) < .04, "Idle Builder keeps pacing");
          if (level.getGameTime() - arrived[0] < 30) return;
          BlockPos anchor = h.absolutePos(new BlockPos(25, 1, 8));
          level.setBlock(anchor, Automatons.TOTEM.get().defaultBlockState(), 3);
          Station target = (Station) level.getBlockEntity(anchor);
          target.owner = r.home.owner;
          target.logisticsHome = r.home.identity;
          target.purpose = WorksitePurpose.LUMBER;
          BuildingProject next = new BuildingProject();
          next.owner = target.owner;
          next.home = target.logisticsHome;
          next.totem = target.identity;
          next.anchor = anchor;
          next.purpose = target.purpose;
          next.approved = true;
          WorldData.get(level).buildings.put(target.identity, next);
          BlockPos supply = anchor.west(5);
          level.setBlock(supply, Blocks.CHEST.defaultBlockState(), 3);
          level
              .getBlockEntity(supply)
              .getPersistentData()
              .putUUID("AutomatonsConstructionChest", next.id);
          next.supplies.chest = supply;
          next.supplies.stocked = true;
          w.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
          request[0] = next;
          // The next normal AI tick, not this fixture, must claim the project.
        });
  }
}
