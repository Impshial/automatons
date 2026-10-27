package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class TimberGameTests {
  private record Rig(Lumberton worker, Station station, ChestBlockEntity chest) {}

  private static Rig rig(GameTestHelper h) {
    for (int x = 0; x < 28; x++)
      for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
    UUID owner = UUID.randomUUID();
    h.setBlock(new BlockPos(3, 1, 3), Automatons.PEDESTAL.get());
    h.setBlock(new BlockPos(10, 1, 10), Automatons.TOTEM.get());
    Station home = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 1, 3)));
    Station station = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 1, 10)));
    home.owner = station.owner = owner;
    home.registerHome();
    WorksiteGameTests.readyFixture(station, WorksitePurpose.LUMBER);
    h.setBlock(new BlockPos(10, 1, 12), Blocks.CHEST);
    BlockPos chestPos = h.absolutePos(new BlockPos(10, 1, 12));
    station.chests.add(chestPos);
    station.storageComplete = true;
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = owner;
    w.home = home.getBlockPos();
    w.homeId = home.identity;
    w.homeDimension = h.getLevel().dimension().location().toString();
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10, 1, 11))));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.assign(station);
    w.setNoAi(true);
    h.getLevel().addFreshEntity(w);
    return new Rig(w, station, (ChestBlockEntity) h.getLevel().getBlockEntity(chestPos));
  }

  private static Timber timber(GameTestHelper h, Rig rig, int count) {
    Timber t = Automatons.TIMBER.get().create(h.getLevel());
    t.owner = rig.worker.owner;
    t.worker = rig.worker.getUUID();
    t.rigidFall = true;
    t.direction = Direction.EAST;
    BlockPos root = h.absolutePos(new BlockPos(17, 1, 15));
    for (int i = 0; i < count; i++) {
      t.sources.add(root.above(i));
      t.targets.add(root.east(i));
      t.wood.add(Blocks.OAK_LOG.defaultBlockState());
    }
    t.setPos(Vec3.atBottomCenterOf(root));
    rig.worker.timberId = t.getUUID();
    h.getLevel().addFreshEntity(t);
    return t;
  }

  private static Lumberton miner(GameTestHelper h, Rig r, boolean sameNetwork) {
    BlockPos at = h.absolutePos(new BlockPos(3, 1, 23));
    h.getLevel().setBlock(at, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station site = (Station) h.getLevel().getBlockEntity(at);
    site.owner = r.worker.owner;
    WorksiteGameTests.readyFixture(site, WorksitePurpose.MINE);
    site.mine = new MinePlan();
    Lumberton m = Automatons.LUMBERTON.get().create(h.getLevel());
    m.owner = r.worker.owner;
    m.homeId = sameNetwork ? r.worker.homeId : UUID.randomUUID();
    m.home = r.worker.home;
    m.homeDimension = r.worker.homeDimension;
    m.setJob(WorkerJob.MININGTON);
    m.tools.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
    m.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(18, 1, 19))));
    h.getLevel().addFreshEntity(m);
    m.assign(site);
    m.setNoAi(true);
    WorkerIdentity.update(m, "Deployed");
    return m;
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void forestry_pickaxe_boundary_requests_reload_and_cancel(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos p = h.absolutePos(new BlockPos(19, 1, 19));
    h.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 3);
    h.assertTrue(
        !TreeFinder.canClearFallBlock(h.getLevel(), p, r.worker.owner),
        "Lumberton can clear stone");
    h.assertTrue(
        MiningRules.breakPermission(r.worker, p, Blocks.STONE.defaultBlockState()) < 0,
        "Axe stone break permitted");
    h.assertTrue(
        !ForestryAccess.buildingBlock(new ItemStack(Items.COBBLESTONE)),
        "New stone climbing pillar allowed");
    ForestryHelp.request(r.worker, p, null);
    LogisticsNetwork n = LogisticsNetwork.get(r.worker);
    h.assertTrue(n.obstacles.size() == 1, "Pickaxe request missing without miner");
    LogisticsNetwork saved = LogisticsNetwork.load(n.save());
    h.assertTrue(
        saved.obstacles.size() == 1
            && saved
                .obstacles
                .get(p.asLong())
                .original
                .equals(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState())),
        "Request lost on reload");
    saved.removeSite(r.station.identity);
    h.assertTrue(saved.obstacles.isEmpty(), "Broken totem retained requests");
    h.getLevel().setBlock(p, Blocks.IRON_BLOCK.defaultBlockState(), 3);
    ForestryHelp.maintain(n, h.getLevel());
    h.assertTrue(
        n.obstacles.isEmpty() && h.getLevel().getBlockState(p).is(Blocks.IRON_BLOCK),
        "Replacement was treated as original obstacle");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void minington_help_requires_network_tool_and_exclusive_claim(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos p = h.absolutePos(new BlockPos(19, 1, 19));
    h.getLevel().setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 3);
    ForestryHelp.request(r.worker, p, null);
    Lumberton m = miner(h, r, false);
    h.assertTrue(!m.forestryHelp.tick(), "Foreign network accepted request");
    m.homeId = r.worker.homeId;
    m.station().logisticsHome = m.homeId;
    h.assertTrue(!m.forestryHelp.tick(), "Iron pickaxe accepted obsidian");
    m.tools.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
    h.runAfterDelay(
        21,
        () -> {
          h.assertTrue(m.forestryHelp.tick(), "Capable miner did not accept request");
          ForestryHelp.Request request = LogisticsNetwork.get(r.worker).obstacles.get(p.asLong());
          h.assertTrue(m.getUUID().equals(request.miner), "Miner claim missing");
          Lumberton peer = Automatons.LUMBERTON.get().create(h.getLevel());
          peer.owner = m.owner;
          peer.homeId = m.homeId;
          peer.home = m.home;
          peer.homeDimension = m.homeDimension;
          peer.setJob(WorkerJob.MININGTON);
          peer.tools.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
          peer.moveTo(m.position());
          peer.setNoAi(true);
          h.getLevel().addFreshEntity(peer);
          peer.assign(m.station());
          h.assertTrue(!peer.forestryHelp.tick(), "Two miners claimed one obstacle");
          m.authorized = false;
          ForestryHelp.maintain(LogisticsNetwork.get(r.worker), h.getLevel());
          h.assertTrue(request.miner == null, "Paused miner retained claim");
          h.assertTrue(
              h.getLevel().getBlockState(p).is(Blocks.OBSIDIAN)
                  && m.mining.carried(Items.OBSIDIAN) == 0,
              "Partial work paid loot");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void minington_clears_requested_stone_once_and_keeps_mine_cursor(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos p = h.absolutePos(new BlockPos(19, 1, 19));
    h.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 3);
    ForestryHelp.request(r.worker, p, null);
    Lumberton m = miner(h, r, true);
    m.station().mine.column = 37;
    MinePlan.Tunnel tunnel = new MinePlan.Tunnel();
    tunnel.worker = m.getUUID();
    tunnel.column = 37;
    tunnel.row = 2;
    m.station().mine.tunnels.add(tunnel);
    CompoundTag before = m.station().mine.save();
    h.onEachTick(() -> m.forestryHelp.tick());
    h.succeedWhen(
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(p).isAir(), "Miner never cleared obstacle: " + m.status());
          h.assertTrue(
              m.mining.carried(Items.COBBLESTONE) == 1 && m.activeTool().getDamageValue() == 1,
              "Obstacle payout or wear wrong");
          h.assertTrue(
              LogisticsNetwork.get(r.worker).obstacles.isEmpty(), "Completed request retained");
          h.assertTrue(before.equals(m.station().mine.save()), "Assistance changed mine cursor");
          h.assertTrue(!m.forestryHelp.tick(), "Helper failed to hand control back to mining");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 3500)
  public static void lumberton_calls_miner_then_finishes_obstructed_tree(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos root = enclosedTree(h);
    for (Direction d : Direction.Plane.HORIZONTAL)
      for (int y = 0; y < 10; y++)
        for (int width = -1; width <= 1; width++)
          h.setBlock(root.relative(d, 2).relative(d.getClockWise(), width).above(y), Blocks.STONE);
    h.setBlock(root.west(2), Blocks.AIR);
    h.setBlock(root.west(2).above(), Blocks.AIR);
    Lumberton m = miner(h, r, true);
    m.setNoAi(false);
    m.moveTo(Vec3.atBottomCenterOf(h.absolutePos(root.west(4))));
    r.worker.moveTo(Vec3.atBottomCenterOf(h.absolutePos(root.west())));
    r.worker.setNoAi(false);
    var tree = TreeFinder.inspect(h.getLevel(), h.absolutePos(root), r.station).tree();
    h.assertTrue(tree != null, "Tree fixture missing");
    r.station.trees.put(tree.root().asLong(), tree);
    boolean[] requested = {false}, helped = {false};
    h.onEachTick(
        () -> {
          if (!LogisticsNetwork.get(r.worker).obstacles.isEmpty()) requested[0] = true;
          if (m.mining.carried(Items.COBBLESTONE) > 0) helped[0] = true;
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              requested[0] && helped[0],
              "No successful helper request: " + r.worker.status() + "/" + m.status());
          h.assertTrue(
              r.worker.mining.carried(Items.COBBLESTONE) == 0, "Lumberton mined stone itself");
          h.assertTrue(
              !h.getBlockState(root).is(Blocks.OAK_LOG),
              "Lumberton did not resume tree: " + r.worker.diagnostic());
        });
  }

  private static void beside(Lumberton w, BlockPos p) {
    w.moveTo(Vec3.atBottomCenterOf(p.south()));
  }

  private static BlockPos enclosedTree(GameTestHelper h) {
    BlockPos root = new BlockPos(20, 1, 20);
    for (int y = 0; y < 4; y++) h.setBlock(root.above(y), Blocks.OAK_LOG);
    h.setBlock(
        root.above(4),
        Blocks.OAK_LEAVES
            .defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, false)
            .setValue(LeavesBlock.DISTANCE, 1));
    for (Direction d : Direction.Plane.HORIZONTAL)
      h.setBlock(
          root.above(3).relative(d),
          Blocks.OAK_LEAVES
              .defaultBlockState()
              .setValue(LeavesBlock.PERSISTENT, false)
              .setValue(LeavesBlock.DISTANCE, 1));
    for (Direction d : Direction.Plane.HORIZONTAL)
      for (int y = 0; y < 10; y++)
        for (int width = -1; width <= 1; width++)
          h.setBlock(
              root.relative(d, 2).relative(d.getClockWise(), width).above(y),
              Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
    h.setBlock(root.west(2), Blocks.AIR);
    h.setBlock(root.west(2).above(), Blocks.AIR);
    return root;
  }

  @GameTest(template = "empty", timeoutTicks = 2500)
  public static void lumberton_clears_fall_obstacles_and_finishes_tree(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos root = enclosedTree(h);
    TreeFinder.Tree tree = TreeFinder.inspect(h.getLevel(), h.absolutePos(root), r.station).tree();
    h.assertTrue(tree != null, "Enclosed tree not recognized");
    h.assertTrue(
        TreeFinder.landing(h.getLevel(), tree, r.station, r.worker.owner, r.worker) == null,
        "Fixture already has clear landing");
    TreeFinder.Landing plan =
        TreeFinder.landingWithClearing(h.getLevel(), tree, r.station, r.worker.owner, r.worker);
    h.assertTrue(
        plan != null && !TreeFinder.fallObstructions(h.getLevel(), tree, plan).isEmpty(),
        "No clearing plan through removable cover");
    System.out.println(
        "FALL_CLEAR_PLAN targets="
            + plan.targets()
            + " obstacles="
            + TreeFinder.fallObstructions(h.getLevel(), tree, plan));
    r.worker.moveTo(Vec3.atBottomCenterOf(h.absolutePos(root.west())));
    r.worker.setNoAi(false);
    r.station.trees.put(tree.root().asLong(), tree);
    boolean[] clearing = {false}, fell = {false};
    h.onEachTick(
        () -> {
          if (r.worker.status().equals("Clearing fall area")) clearing[0] = true;
          if (!h.getBlockState(root).is(Blocks.OAK_LOG)) fell[0] = true;
          if (h.getTick() % 200 == 0)
            System.out.println(
                "FALL_CLEAR tick="
                    + h.getTick()
                    + " "
                    + r.worker.diagnostic()
                    + " clearing="
                    + clearing[0]
                    + " fell="
                    + fell[0]);
        });
    h.succeedWhen(
        () -> {
          int logs = r.worker.mining.carried(Items.OAK_LOG);
          for (int i = 0; i < r.chest.getContainerSize(); i++)
            if (r.chest.getItem(i).is(Items.OAK_LOG)) logs += r.chest.getItem(i).getCount();
          h.assertTrue(
              clearing[0] && fell[0] && logs == 4,
              "Clearing did not finish tree: " + r.worker.diagnostic() + " logs=" + logs);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void clearing_respects_protected_and_unbreakable_blocks(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos at = h.absolutePos(new BlockPos(20, 1, 20));
    h.getLevel().setBlock(at, Blocks.DIRT.defaultBlockState(), 3);
    h.assertTrue(
        TreeFinder.canClearFallBlock(h.getLevel(), at, r.worker.owner),
        "Ordinary obstruction refused");
    h.getLevel().setBlock(at, Blocks.BEDROCK.defaultBlockState(), 3);
    h.assertTrue(
        !TreeFinder.canClearFallBlock(h.getLevel(), at, r.worker.owner), "Bedrock clearable");
    h.getLevel().setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
    h.assertTrue(
        !TreeFinder.canClearFallBlock(h.getLevel(), at, r.worker.owner), "Storage clearable");
    h.getLevel().setBlock(at, Blocks.DIRT.defaultBlockState(), 3);
    WorldData.get(h.getLevel())
        .protection
        .put(new net.minecraft.world.level.ChunkPos(at).toLong(), r.worker.owner);
    h.assertTrue(
        !TreeFinder.canClearFallBlock(h.getLevel(), at, r.worker.owner), "Protection ignored");
    WorldData.get(h.getLevel())
        .protection
        .remove(new net.minecraft.world.level.ChunkPos(at).toLong());
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void landed_logs_are_real_blocks_and_chopped_exactly_once(GameTestHelper h) {
    Rig r = rig(h);
    Timber t = timber(h, r, 3);
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(t.hasPlacedBlocks(), "Animation did not place blocks");
          h.assertTrue(
              !t.isMultipartEntity() && !t.isPickable(), "Virtual log collision still active");
          for (int i = 0; i < 3; i++) {
            BlockPos p = t.targets.get(i);
            h.assertTrue(
                h.getLevel().getBlockState(p).is(Blocks.OAK_LOG)
                    && h.getLevel().getBlockState(p).getValue(RotatedPillarBlock.AXIS)
                        == Direction.Axis.X,
                "Missing sideways world log");
            h.assertTrue(
                !h.getLevel().noCollision(r.worker, new AABB(p)), "World log is not solid");
            beside(r.worker, p);
            h.assertTrue(t.process(i, r.worker, r.station), "Worker did not chop its real log");
            h.assertTrue(h.getLevel().getBlockState(p).isAir(), "Chopped log remains in world");
            h.assertTrue(!t.process(i, r.worker, r.station), "Log paid twice");
          }
          t.sweepPlacedLogs();
          h.assertTrue(
              t.isRemoved() && r.worker.mining.carried(Items.OAK_LOG) == 3,
              "Completed job or exact log payout incorrect");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void manually_removed_and_replaced_logs_are_not_harvested_again(GameTestHelper h) {
    Rig r = rig(h);
    Timber t = timber(h, r, 3);
    h.runAfterDelay(
        45,
        () -> {
          h.getLevel().destroyBlock(t.targets.get(0), true);
          h.getLevel().setBlock(t.targets.get(1), Blocks.STONE.defaultBlockState(), 3);
          BlockPos replacement = t.targets.get(2);
          LuminaHarvest.markPlaced(
              h.getLevel(), replacement, h.getLevel().getBlockState(replacement));
          t.sweepPlacedLogs();
          h.assertTrue(t.isRemoved() && t.finished(), "Sweep retained missing/replaced logs");
          h.assertTrue(
              h.getLevel().getBlockState(t.targets.get(1)).is(Blocks.STONE)
                  && h.getLevel().getBlockState(replacement).is(Blocks.OAK_LOG),
              "Sweep deleted player blocks");
          h.assertTrue(r.worker.mining.carried(Items.OAK_LOG) == 0, "Sweep paid virtual logs");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 120)
  public static void real_log_pending_loot_survives_reload_and_sweeps(GameTestHelper h) {
    Rig r = rig(h);
    Timber t = timber(h, r, 2);
    h.runAfterDelay(
        45,
        () -> {
          for (int i = 0; i < r.worker.cargo.getSlots(); i++)
            r.worker.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
          beside(r.worker, t.targets.get(0));
          h.assertTrue(
              !t.process(0, r.worker, r.station) && t.pendingLoot != null,
              "Full inventory lost pending payout");
          h.assertTrue(
              h.getLevel().getBlockState(t.targets.get(0)).isAir(),
              "Harvested block can pay twice");
          CompoundTag saved = t.saveWithoutId(new CompoundTag());
          t.discard();
          Timber loaded = Automatons.TIMBER.get().create(h.getLevel());
          loaded.load(saved);
          h.getLevel().addFreshEntity(loaded);
          loaded.sweepPlacedLogs();
          h.assertTrue(
              loaded.hasPlacedBlocks() && !loaded.consumed(0) && loaded.pendingLoot != null,
              "Reload/sweep discarded uncollected cargo");
          r.worker.cargo.setStackInSlot(0, ItemStack.EMPTY);
          h.assertTrue(loaded.process(0, r.worker, r.station), "Pending cargo did not resume");
          h.assertTrue(
              r.worker.mining.carried(Items.OAK_LOG) == 1, "Pending log duplicated or lost");
          h.assertTrue(
              h.getLevel().getBlockState(loaded.targets.get(1)).is(Blocks.OAK_LOG),
              "Reload erased untouched log");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void timber_audit_repairs_state_and_rejects_stale_identities(GameTestHelper h) {
    Rig r = rig(h);
    Timber t = timber(h, r, 2);
    CompoundTag early = t.saveWithoutId(new CompoundTag());
    h.runAfterDelay(
        45,
        () -> {
          Timber stale = Automatons.TIMBER.get().create(h.getLevel());
          stale.load(early);
          CompoundTag live =
              TimberMaintenance.inspect(
                  h.getLevel(), new TimberMaintenance.Identity(t.getId(), t.getUUID()));
          h.assertTrue(live.getBoolean("Present"), "Live timber falsely classified stale");
          stale.applyAuditState(live.getCompound("State"));
          h.assertTrue(
              stale.hasPlacedBlocks() && stale.progress() == t.duration,
              "Stale animation was not hidden by authoritative state");
          h.assertTrue(
              !TimberMaintenance.inspect(
                      h.getLevel(), new TimberMaintenance.Identity(t.getId(), UUID.randomUUID()))
                  .getBoolean("Present"),
              "Reused entity ID accepted");
          t.discard();
          h.assertTrue(
              !TimberMaintenance.inspect(
                      h.getLevel(), new TimberMaintenance.Identity(t.getId(), t.getUUID()))
                  .getBoolean("Present"),
              "Removed animation still present");
          h.assertTrue(
              h.getLevel().getBlockState(t.targets.get(0)).is(Blocks.OAK_LOG),
              "Audit touched a real log");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void falling_tree_bounds_survive_position_packets(GameTestHelper h) {
    Rig r = rig(h);
    Timber t = timber(h, r, 12);
    t.setPos(t.getX(), t.getY(), t.getZ());
    h.assertTrue(
        t.getBoundingBoxForCulling().contains(Vec3.atCenterOf(t.targets.get(11)))
            && t.getBoundingBoxForCulling().contains(Vec3.atCenterOf(t.sources.get(11))),
        "Position update shrank the visible tree to its root");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void harvest_delivery_batches_and_reserves_survive_reload(GameTestHelper h) {
    Rig r = rig(h);
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 64));
    r.worker.cargo.setStackInSlot(1, new ItemStack(Items.OAK_SAPLING, 16));
    h.assertTrue(!r.worker.harvestDeliveryDue(), "Small fresh load immediately interrupted work");
    CompoundTag n = r.worker.saveWithoutId(new CompoundTag());
    n.putLong("HarvestSince", h.getLevel().getGameTime() - 1201);
    r.worker.load(n);
    h.assertTrue(r.worker.harvestDeliveryDue(), "Older partial load never delivered");
    Storage.transfer(r.worker, r.station, r.chest.getBlockPos());
    h.assertTrue(
        r.worker.mining.carried(Items.OAK_LOG) == 0
            && r.worker.mining.carried(Items.OAK_SAPLING) == 16,
        "Delivery lost replant supplies or kept output");
    h.assertTrue(!r.worker.harvestDeliveryDue(), "Supplies alone trigger repeated trips");
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 64));
    r.worker.cargo.setStackInSlot(2, new ItemStack(Items.OAK_LOG, 64));
    h.assertTrue(r.worker.harvestDeliveryDue(), "Two-stack harvest not ready for collection");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 700)
  public static void lumberton_deposits_partial_cargo_before_accepting_more_work(GameTestHelper h) {
    Rig r = rig(h);
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 64));
    r.worker.cargo.setStackInSlot(1, new ItemStack(Items.OAK_LOG, 64));
    r.worker.cargo.setStackInSlot(2, new ItemStack(Items.OAK_SAPLING, 16));
    h.setBlock(new BlockPos(20, 1, 10), Blocks.BIRCH_LOG);
    r.worker.setNoAi(false);
    h.succeedWhen(
        () -> {
          int logs = 0;
          for (int i = 0; i < r.chest.getContainerSize(); i++)
            if (r.chest.getItem(i).is(Items.OAK_LOG)) logs += r.chest.getItem(i).getCount();
          h.assertTrue(
              logs == 128, "Partial cargo never reached pickup chest: " + r.worker.diagnostic());
          h.assertTrue(
              r.worker.mining.carried(Items.OAK_SAPLING) == 16, "Replant reserve unloaded");
        });
  }
}
