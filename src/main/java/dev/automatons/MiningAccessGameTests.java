package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_mining")
@PrefixGameTestTemplate(false)
public final class MiningAccessGameTests {
  @GameTest(template = "mine_test", timeoutTicks = 5000)
  public static void existing_underground_miner_repairs_missing_top_rung(GameTestHelper h) {
    var r = MiningGameTests.rig(h, true, 20);
    var m = r.totem().mine;
    h.getLevel().setBlock(m.entrance, Blocks.AIR.defaultBlockState(), 3);
    h.getLevel().setBlock(m.ladder(1), Blocks.AIR.defaultBlockState(), 3);
    var w = r.worker();
    w.moveTo(Vec3.atBottomCenterOf(m.chamber()));
    w.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 32));
    w.cargo.setStackInSlot(1, new ItemStack(Items.LADDER));
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0) System.out.println("ACCESS_REPAIR " + w.diagnostic());
          if (m.column >= 2) {
            h.assertTrue(
                h.getLevel().getBlockState(m.ladder(1)).equals(m.ladderState()),
                "Missing top rung was not repaired");
            h.assertTrue(
                w.mining.carried(Items.LADDER) == 0, "Top rung did not consume real ladder");
            h.succeed();
          }
        });
  }

  @GameTest(template = "recall_test", timeoutTicks = 2200)
  public static void distant_lumberton_follows_partial_routes_home(GameTestHelper h) {
    // GameTest's default ticking area is smaller than this deliberately long route.
    BlockPos origin = h.absolutePos(BlockPos.ZERO);
    for (int cx = origin.getX() >> 4; cx <= (origin.getX() + 127) >> 4; cx++)
      for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + 31) >> 4; cz++)
        h.getLevel().setChunkForced(cx, cz, true);
    for (int x = 0; x < 128; x++)
      for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
    BlockPos homePos = h.absolutePos(new BlockPos(4, 1, 16));
    h.getLevel().setBlock(homePos, Automatons.PEDESTAL.get().defaultBlockState(), 3);
    Station home = (Station) h.getLevel().getBlockEntity(homePos);
    home.owner = UUID.randomUUID();
    home.registerHome();
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = home.owner;
    w.home = homePos;
    w.homeId = home.identity;
    w.homeDimension = h.getLevel().dimension().location().toString();
    w.setJob(WorkerJob.LUMBERTON);
    w.authorized = true;
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(121, 1, 16))));
    h.getLevel().addFreshEntity(w);
    WorldData.get(h.getLevel()).setHome(home.identity, w.owner, homePos, true, true);
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0) System.out.println("ACCESS_RECALL " + w.diagnostic());
          if (w.status().equals("Recalled")) {
            h.assertTrue(w.distanceToSqr(Vec3.atCenterOf(homePos)) < 9, "False recall arrival");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void shaft_opens_ground_and_builds_depot_without_surface_chest(GameTestHelper h) {
    var r = MiningGameTests.rig(h, false, 20);
    var m = r.totem().mine;
    h.getLevel().setBlock(r.chest().getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
    r.totem().chests.clear();
    h.getLevel().setBlock(m.ladder(1), Blocks.STONE.defaultBlockState(), 3);
    var w = r.worker();
    w.cargo.setStackInSlot(0, new ItemStack(Items.LADDER, 20));
    w.cargo.setStackInSlot(1, new ItemStack(Items.CHEST, 4));
    w.cargo.setStackInSlot(2, new ItemStack(Items.TORCH, 64));
    boolean[] recalled = {false};
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0)
            System.out.println(
                "ACCESS_BUILD " + m.stage + ":" + m.construction + " " + w.diagnostic());
          h.assertTrue(w.getY() <= m.entrance.getY() + 1, "Miner climbed into the roof");
          if (!recalled[0] && m.stage == MinePlan.Stage.READY && m.column >= 2) {
            for (int d = 1; d <= 20; d++)
              h.assertTrue(
                  h.getLevel().getBlockState(m.ladder(d)).equals(m.ladderState()),
                  "Missing rung " + d);
            h.assertTrue(m.depotBuilt && m.placedChests.size() == 4, "No underground depot");
            WorldData.get(h.getLevel()).setHome(r.home().identity, w.owner, w.home, true, true);
            recalled[0] = true;
          }
          if (recalled[0] && w.status().equals("Recalled")) {
            h.assertTrue(w.getY() >= m.entrance.getY() - .2, "Recall skipped the ladder");
            h.assertTrue(!w.isNoGravity(), "Recall left miner floating");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 5000)
  public static void hillside_approach_walks_to_shaft_before_climbing(GameTestHelper h) {
    var r = MiningGameTests.rig(h, true, 20);
    var m = r.totem().mine;
    // A two-block-lower path, twenty blocks away from the actual mouth.
    for (int x = 18; x <= 31; x++)
      for (int z = 9; z <= 11; z++)
        for (int y = 39; y <= 40; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    for (int z = 9; z <= 11; z++) h.setBlock(new BlockPos(17, 40, z), Blocks.AIR);
    var w = r.worker();
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(24, 39, 10))));
    w.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 32));
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0) System.out.println("ACCESS_SLOPE " + w.diagnostic());
          if (w.position().subtract(Vec3.atBottomCenterOf(m.entrance)).horizontalDistanceSqr() > 9)
            h.assertTrue(!w.isNoGravity(), "Remote ladder motion started on the hillside");
          if (m.column >= 2) h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 5000)
  public static void recall_mid_descent_reverses_without_roof_overshoot(GameTestHelper h) {
    var r = MiningGameTests.rig(h, false, 20);
    var m = r.totem().mine;
    for (int d = 1; d <= 20; d++) h.getLevel().setBlock(m.ladder(d), m.ladderState(), 3);
    m.stage = MinePlan.Stage.READY;
    m.depotRequired = false;
    for (int x = -1; x <= 1; x++)
      for (int z = 0; z <= 2; z++) {
        m.retain(m.at(x, -20, z));
        for (int y = -20; y <= -18; y++)
          if (x != 0 || z != 0)
            h.getLevel().setBlock(m.at(x, y, z), Blocks.AIR.defaultBlockState(), 3);
      }
    var w = r.worker();
    w.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 32));
    boolean[] recalled = {false};
    h.onEachTick(
        () -> {
          if (!recalled[0] && w.getY() < m.entrance.getY() - 8) {
            WorldData.get(h.getLevel()).setHome(r.home().identity, w.owner, w.home, true, true);
            recalled[0] = true;
          }
          h.assertTrue(w.getY() < m.entrance.getY() + 1, "Ladder ascent overshot into ceiling");
          if (recalled[0] && w.status().equals("Recalled")) h.succeed();
        });
  }
}
