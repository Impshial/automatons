package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class MiningGameTests {
  @GameTest(template = "empty", timeoutTicks = 650)
  public static void totem_find_is_owned_and_expires_after_thirty_seconds(GameTestHelper h) {
    BlockPos p = h.absolutePos(new BlockPos(5, 1, 5));
    h.getLevel().setBlock(p, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station station = (Station) h.getLevel().getBlockEntity(p);
    var owner =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FindOwner"));
    owner.moveTo(Vec3.atCenterOf(p));
    station.owner = owner.getUUID();
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = owner.getUUID();
    w.setNoAi(true);
    w.moveTo(Vec3.atBottomCenterOf(p.east(2)));
    h.getLevel().addFreshEntity(w);
    w.assign(station);
    WorkMenu menu = new WorkMenu(1, owner.getInventory(), station, null);
    menu.selectedWorker = w.getUUID();
    h.assertTrue(menu.clickMenuButton(owner, 14), "Owner Find rejected");
    var glow = w.getEffect(net.minecraft.world.effect.MobEffects.GLOWING);
    h.assertTrue(glow != null && glow.getDuration() == 600, "Find duration is not 30 seconds");
    var stranger =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FindStranger"));
    stranger.moveTo(Vec3.atCenterOf(p));
    WorkMenu foreign = new WorkMenu(2, stranger.getInventory(), station, null);
    foreign.selectedWorker = w.getUUID();
    h.assertTrue(!foreign.clickMenuButton(stranger, 14), "Nonowner used Find");
    h.runAtTickTime(
        602,
        () -> {
          h.assertTrue(
              !w.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "Glow did not expire");
          h.succeed();
        });
  }

  record Rig(Station totem, Station home, Lumberton worker, ChestBlockEntity chest) {}

  @GameTest(template = "mine_test", timeoutTicks = 6000)
  public static void minington_returns_to_saved_tunnel_after_forestry_help(GameTestHelper h) {
    Rig r = rig(h, true, 20);
    BlockPos sitePos = h.absolutePos(new BlockPos(25, 41, 15));
    BlockPos obstacle = h.absolutePos(new BlockPos(19, 41, 15));
    h.getLevel().setBlock(sitePos, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station forest = (Station) h.getLevel().getBlockEntity(sitePos);
    forest.owner = r.worker.owner;
    WorksiteGameTests.readyFixture(forest, WorksitePurpose.LUMBER);
    Lumberton lumber = Automatons.LUMBERTON.get().create(h.getLevel());
    lumber.owner = r.worker.owner;
    lumber.homeId = r.worker.homeId;
    lumber.home = r.worker.home;
    lumber.homeDimension = r.worker.homeDimension;
    lumber.moveTo(Vec3.atBottomCenterOf(sitePos.south(3)));
    h.getLevel().addFreshEntity(lumber);
    lumber.assign(forest);
    lumber.setNoAi(true);
    WorkerIdentity.update(lumber, "Deployed");
    int[] saved = {-1};
    boolean[] cleared = {false};
    h.onEachTick(
        () -> {
          MinePlan m = r.totem.mine;
          if (saved[0] < 0 && m.column >= 5 && r.worker.getY() < m.entrance.getY() - 18) {
            h.getLevel().setBlock(obstacle, Blocks.IRON_ORE.defaultBlockState(), 3);
            ForestryHelp.request(lumber, obstacle, null);
            h.assertTrue(
                !LogisticsNetwork.get(lumber).obstacles.isEmpty(), "Surface request rejected");
            saved[0] = m.column;
          }
          if (saved[0] >= 0 && h.getLevel().getBlockState(obstacle).isAir()) cleared[0] = true;
          if (cleared[0] && m.column > saved[0] + 2 && r.worker.getY() < m.entrance.getY() - 18) {
            h.assertTrue(m.tunnel(r.worker.getUUID()) != null, "Original tunnel assignment lost");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 6000)
  public static void minington_hauls_to_second_underground_double_chest(GameTestHelper h) {
    Rig r = rig(h, true, 20);
    MinePlan m = r.totem.mine;
    m.depotRequired = true;
    m.stage = MinePlan.Stage.STORAGE;
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.CHEST, 4));
    r.worker.cargo.setStackInSlot(1, new ItemStack(Items.TORCH, 16));
    boolean[] filled = {false};
    int[] surfaceBefore = {0};
    int[] cycle = {0};
    h.onEachTick(
        () -> {
          if (!m.depotBuilt || m.column < 2) return;
          var first = Storage.depotHandler(h.getLevel(), m.depotPositions().get(0), m);
          var second = Storage.depotHandler(h.getLevel(), m.depotPositions().get(2), m);
          h.assertTrue(
              first != null && first.getSlots() == 54 && second != null && second.getSlots() == 54,
              "Mine storage is not two double chests");
          if (!filled[0]) {
            for (int i = 0; i < 54; i++)
              first.insertItem(i, new ItemStack(Items.COBBLESTONE, 64), false);
            for (int i = 0; i < 8; i++)
              r.worker.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            r.worker.cargo.setStackInSlot(8, new ItemStack(Items.TORCH, 16));
            surfaceBefore[0] = count(r.chest, Items.COBBLESTONE);
            filled[0] = true;
          } else {
            int stored = 0;
            for (int i = 0; i < 54; i++)
              if (second.getStackInSlot(i).is(Items.COBBLESTONE))
                stored += second.getStackInSlot(i).getCount();
            if (stored < 512 || m.column < 5) return;
            if (cycle[0] == 0) {
              for (int i = 0; i < 8; i++)
                r.worker.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
              r.worker.cargo.setStackInSlot(8, new ItemStack(Items.TORCH, 16));
              cycle[0]++;
              return;
            }
            if (stored < 1024 || m.column < 8) return;
            h.assertTrue(
                count(r.chest, Items.COBBLESTONE) == surfaceBefore[0],
                "Mined cargo went to surface storage");
            h.assertTrue(
                r.worker.getY() < m.entrance.getY() - 4,
                "Worker climbed out instead of using platform storage");
            var loaded = MinePlan.load(m.save());
            h.assertTrue(
                loaded.depotBuilt && loaded.placedChests.equals(m.placedChests),
                "Depot journal lost on reload");
            h.succeed();
          }
        });
  }

  private static int count(ChestBlockEntity chest, Item item) {
    int result = 0;
    for (int i = 0; i < chest.getContainerSize(); i++)
      if (chest.getItem(i).is(item)) result += chest.getItem(i).getCount();
    return result;
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_retained_quarry_geometry_starts_at_platform_edge(GameTestHelper h) {
    h.assertTrue(
        new MinePlan().mode == MinePlan.Mode.TUNNEL, "New mine does not default to tunnel");
    Rig r = rig(h, true, 20);
    MinePlan m = r.totem.mine;
    m.mode = MinePlan.Mode.QUARRY;
    m.edgeQuarry = true;
    m.quarrySide = 1;
    h.assertTrue(m.step(0).equals(m.exit()), "Quarry does not start at outer platform edge");
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 32));
    h.onEachTick(
        () -> {
          if (m.quarryLayer < 3) return;
          for (int x = -1; x <= 1; x++)
            for (int z = 0; z <= 2; z++)
              h.assertTrue(
                  h.getLevel().getBlockState(m.at(x, -m.shaftDepth - 1, z)).is(Blocks.STONE),
                  "Quarry removed platform floor");
          r.worker.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 9000)
  public static void minington_returns_to_face_after_surface_unload_and_pause(GameTestHelper h) {
    Rig r = rig(h, true, 20);
    MinePlan m = r.totem.mine;
    int[] phase = {0};
    long[] pausedAt = {0};
    Vec3[] held = {null};
    h.onEachTick(
        () -> {
          if (h.getTick() % 500 == 0)
            System.out.println(
                "MINING_RESUME phase="
                    + phase[0]
                    + " column="
                    + m.column
                    + " "
                    + r.worker.diagnostic());
          if (phase[0] == 0 && m.column >= 2) {
            for (int i = 0; i < 8; i++)
              r.worker.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            r.worker.cargo.setStackInSlot(8, new ItemStack(Items.TORCH, 16));
            phase[0] = 1;
          }
          if (phase[0] == 1 && count(r.chest, Items.COBBLESTONE) >= 512 && m.column >= 5) {
            m.paused = true;
            held[0] = r.worker.position();
            pausedAt[0] = h.getTick();
            phase[0] = 2;
          }
          if (phase[0] == 2 && h.getTick() > pausedAt[0] + 100) {
            h.assertTrue(
                r.worker.position().distanceToSqr(held[0]) < 1, "Paused miner made a return trip");
            m.paused = false;
            phase[0] = 3;
          }
          if (phase[0] == 3 && m.column >= 8) {
            h.assertTrue(
                r.worker.getY() < m.entrance.getY() - 18, "Mining did not resume at depth");
            r.worker.setNoAi(true);
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 9000)
  public static void minington_second_worker_joins_completed_mine(GameTestHelper h) {
    Rig r = rig(h, true, 20);
    MinePlan m = r.totem.mine;
    Lumberton peer = Automatons.LUMBERTON.get().create(h.getLevel());
    peer.owner = r.worker.owner;
    peer.home = r.worker.home;
    peer.homeId = r.worker.homeId;
    peer.homeDimension = r.worker.homeDimension;
    peer.setJob(2);
    peer.tools.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
    peer.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
    peer.moveTo(Vec3.atBottomCenterOf(r.totem.getBlockPos().east(2)));
    h.getLevel().addFreshEntity(peer);
    peer.assign(r.totem);
    h.onEachTick(
        () -> {
          if (h.getTick() % 500 == 0)
            System.out.println(
                "MINING_PEERS column="
                    + m.column
                    + " first="
                    + r.worker.diagnostic()
                    + " second="
                    + peer.diagnostic());
          if (m.column < 8
              || r.worker.mining.carried(Items.COBBLESTONE) == 0
              || peer.mining.carried(Items.COBBLESTONE) == 0) return;
          h.assertTrue(
              m.entranceComplete && MinePlan.load(m.save()).entranceComplete,
              "Completed entrance not remembered");
          h.assertTrue(
              !r.worker.status().equals("Waiting for builder")
                  && !peer.status().equals("Waiting for builder"),
              "Builder lock held after completion");
          MinePlan.Tunnel first = m.tunnel(r.worker.getUUID()), second = m.tunnel(peer.getUUID());
          h.assertTrue(
              first != null
                  && second != null
                  && first != second
                  && !first.face(m).equals(second.face(m)),
              "Miners share one tunnel face");
          MinePlan restored = MinePlan.load(m.save());
          h.assertTrue(
              restored.tunnel(peer.getUUID()).column == second.column
                  && restored.tunnel(r.worker.getUUID()).column == first.column,
              "Independent tunnel progress lost on reload");
          r.worker.setNoAi(true);
          peer.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 6000)
  public static void minington_mines_falling_gravel_from_safe_distance(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    BlockPos face = m.exit().above();
    for (int y = 0; y < 4; y++)
      h.getLevel().setBlock(face.above(y), Blocks.GRAVEL.defaultBlockState(), 3);
    r.worker.tools.setStackInSlot(1, new ItemStack(Items.IRON_SHOVEL));
    float health = r.worker.getHealth();
    boolean[] sawWork = {false};
    h.onEachTick(
        () -> {
          if (h.getTick() % 200 == 0)
            org.slf4j.LoggerFactory.getLogger("automatons.test")
                .info(
                    "GRAVEL {} column={} state={}",
                    r.worker.diagnostic(),
                    m.column,
                    h.getLevel().getBlockState(face));
          if (face.equals(r.worker.chopTarget())) {
            sawWork[0] = true;
            h.assertTrue(
                MiningRules.safeFallingStance(r.worker, face, r.worker.position()),
                "Mined below falling gravel");
          }
          if (m.column < 2) return;
          h.assertTrue(sawWork[0], "Never mined gravel");
          h.assertTrue(r.worker.getHealth() == health, "Falling material hurt the miner");
          h.assertTrue(
              r.worker.mining.carried(Items.GRAVEL) + r.worker.mining.carried(Items.FLINT) == 4,
              "Falling-block loot lost or duplicated");
          h.assertTrue(
              !MiningRules.fallingNow(h.getLevel(), face), "Advanced before gravel settled");
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_returns_to_platform_and_redirects_around_water(GameTestHelper h) {
    fluidDetour(h, Blocks.WATER);
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_returns_to_platform_and_redirects_around_lava(GameTestHelper h) {
    fluidDetour(h, Blocks.LAVA);
  }

  private static void fluidDetour(GameTestHelper h, Block fluid) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    BlockPos source = m.at(0, -4, 8), barrier = m.at(0, -4, 7);
    h.getLevel().setBlock(source, fluid.defaultBlockState(), 3);
    UUID id = m.id;
    boolean[] returned = {false}, observed = {false};
    h.onEachTick(
        () -> {
          MinePlan.Tunnel current = m.tunnel(r.worker.getUUID());
          if (current != null && current.wet) {
            observed[0] = true;
            if (r.worker.position().distanceToSqr(Vec3.atBottomCenterOf(m.chamber())) < 2)
              returned[0] = true;
            var restored = MinePlan.load(m.save());
            h.assertTrue(
                restored.tunnel(r.worker.getUUID()).wet && restored.wetRoutes.contains(0),
                "Fluid avoidance not saved");
          }
          if (m.route == 0 || m.column < 4) return;
          h.assertTrue(
              observed[0] && returned[0], "Changed direction without returning to platform");
          h.assertTrue(
              m.id.equals(id) && m.abandonedRoutes.size() == 1, "Fluid created a replacement mine");
          h.assertTrue(
              h.getLevel().getBlockState(source).is(fluid)
                  && h.getLevel().getBlockState(barrier).is(Blocks.STONE),
              "Breached the fluid boundary");
          h.assertTrue(
              !r.worker.isInWaterOrBubble() && !r.worker.isInLava(), "Worker entered fluid");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void minington_player_tool_timing_and_legacy_depth_migration(GameTestHelper h) {
    BlockPos pos = h.absolutePos(new BlockPos(5, 1, 5));
    h.getLevel().setBlock(pos, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station station = (Station) h.getLevel().getBlockEntity(pos);
    var player =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "MiningTiming"));
    player.setOnGround(true);
    for (Item pick :
        List.of(
            Items.WOODEN_PICKAXE,
            Items.STONE_PICKAXE,
            Items.IRON_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.NETHERITE_PICKAXE,
            Items.GOLDEN_PICKAXE)) {
      for (int efficiency : new int[] {0, 1, 3}) {
        ItemStack tool = new ItemStack(pick);
        if (efficiency > 0)
          tool.enchant(
              net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY, efficiency);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
        var stone = Blocks.STONE.defaultBlockState();
        int expected = (int) Math.ceil(1.0 / stone.getDestroyProgress(player, h.getLevel(), pos));
        h.assertTrue(
            MiningRules.duration(tool, stone, h.getLevel(), pos, station) == expected,
            "Tool timing differs from player: " + pick + " efficiency " + efficiency);
      }
    }
    ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
    station.inventory.setStackInSlot(0, new ItemStack(Automatons.UPGRADES.get("speed_1").get()));
    h.assertTrue(
        MiningRules.duration(iron, Blocks.STONE.defaultBlockState(), h.getLevel(), pos, station)
            == 8,
        "Retired Speed still changes mining time");
    station.inventory.setStackInSlot(
        1, new ItemStack(Automatons.UPGRADES.get("efficiency_2").get()));
    h.assertTrue(
        MiningRules.duration(iron, Blocks.STONE.defaultBlockState(), h.getLevel(), pos, station)
            == 8,
        "Retired Efficiency still changes mining time");
    CompoundTag legacy = new CompoundTag();
    legacy.putLong("Entrance", pos.asLong());
    legacy.putString("Stage", "READY");
    var old = MinePlan.load(legacy);
    h.assertTrue(
        old.shaftDepth == 5 && old.stage == MinePlan.Stage.STORAGE,
        "Old mine was moved or depot migration skipped");
    h.assertTrue(new MinePlan().shaftDepth == 20, "New mine does not descend twenty blocks");
    h.succeed();
  }

  @GameTest(template = "mine_test", timeoutTicks = 6000)
  public static void minington_harvests_lumite_variants_with_real_tool_and_silk(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    BlockPos first = m.exit().above(), second = m.exit();
    h.getLevel().setBlock(first, Automatons.LUMITE_ORE.get().defaultBlockState(), 3);
    h.getLevel().setBlock(second, Automatons.DEEPSLATE_LUMITE_ORE.get().defaultBlockState(), 3);
    ItemStack pick = r.worker.tools.getStackInSlot(0);
    pick.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
    pick.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, 3);
    boolean[] present = {true, true}, seen = {false, false};
    int[] previousDamage = {pick.getDamageValue()};
    h.onEachTick(
        () -> {
          BlockPos[] targets = {first, second};
          for (int i = 0; i < 2; i++) {
            boolean now =
                h.getLevel()
                    .getBlockState(targets[i])
                    .is(
                        i == 0
                            ? Automatons.LUMITE_ORE.get()
                            : Automatons.DEEPSLATE_LUMITE_ORE.get());
            if (present[i] && !now) {
              h.assertTrue(
                  pick.getDamageValue() == previousDamage[0] + 1,
                  "Ore broke with duplicated/missing tool wear");
              seen[i] = true;
            }
            present[i] = now;
          }
          previousDamage[0] = pick.getDamageValue();
          if (!seen[0] || !seen[1]) return;
          h.assertTrue(
              r.worker.mining.carried(Automatons.LUMITE_ORE.get().asItem()) == 1
                  && r.worker.mining.carried(Automatons.DEEPSLATE_LUMITE_ORE.get().asItem()) == 1
                  && r.worker.mining.carried(Automatons.LUMITE_SHARD.get()) == 0,
              "Worker silk loot lost, doubled or ignored");
          r.worker.setNoAi(true);
          h.succeed();
        });
  }

  private static final class DenyBreak {
    final BlockPos target;
    boolean denied;

    DenyBreak(BlockPos target) {
      this.target = target;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void deny(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
      if (event.getPos().equals(target)) {
        event.setCanceled(true);
        denied = true;
      }
    }
  }

  private static Rig rig(GameTestHelper h, boolean underground) {
    return rig(h, underground, 5);
  }

  static Rig rig(GameTestHelper h, boolean underground, int shaftDepth) {
    var l = h.getLevel();
    for (int x = 0; x < 48; x++)
      for (int z = 0; z < 24; z++)
        for (int y = 0; y <= 40; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
    var owner = UUID.randomUUID();
    h.setBlock(new BlockPos(4, 41, 4), Automatons.PEDESTAL.get());
    h.setBlock(new BlockPos(8, 41, 4), Automatons.TOTEM.get());
    h.setBlock(new BlockPos(8, 41, 6), Blocks.CHEST);
    Station home = (Station) l.getBlockEntity(h.absolutePos(new BlockPos(4, 41, 4)));
    Station totem = (Station) l.getBlockEntity(h.absolutePos(new BlockPos(8, 41, 4)));
    totem.mine = new MinePlan();
    totem.mine.mode = MinePlan.Mode.TUNNEL;
    home.owner = totem.owner = owner;
    WorksiteGameTests.readyFixture(totem, WorksitePurpose.MINE);
    home.registerHome();
    var chest = (ChestBlockEntity) l.getBlockEntity(h.absolutePos(new BlockPos(8, 41, 6)));
    totem.chests.add(chest.getBlockPos());
    totem.storageComplete = true;
    chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
    chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 8));
    chest.setItem(2, new ItemStack(Items.OAK_TRAPDOOR));
    chest.setItem(3, new ItemStack(Items.LADDER, 20));
    chest.setItem(8, new ItemStack(Items.CHEST, 4));
    chest.setItem(4, new ItemStack(Items.DIRT, 4));
    chest.setItem(5, new ItemStack(Items.DIAMOND_PICKAXE));
    chest.setItem(6, new ItemStack(Items.IRON_SHOVEL));
    chest.setItem(7, new ItemStack(Items.TORCH, 64));
    chest.setItem(9, new ItemStack(Items.OAK_LOG, 33));
    chest.setItem(10, new ItemStack(Items.COBBLESTONE_STAIRS, 28));
    chest.setItem(11, new ItemStack(Items.COBBLESTONE_SLAB, 10));
    chest.setItem(12, new ItemStack(Items.LANTERN, 4));
    Lumberton w = Automatons.LUMBERTON.get().create(l);
    w.owner = owner;
    w.home = home.getBlockPos();
    w.homeId = home.identity;
    w.homeDimension = l.dimension().location().toString();
    w.setJob(2);
    w.tools.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 41, 5))));
    l.addFreshEntity(w);
    w.assign(totem);
    if (!underground) {
      MinePlan m = totem.mine;
      m.rustic = true;
      m.entranceComplete = true;
      m.openAccess = true;
      m.forward = Direction.EAST;
      m.entrance = totem.getBlockPos().east(2);
      m.stage = MinePlan.Stage.SHAFT;
      // Move registered supplies before handover clears the old schematic footprint.
      BlockPos supply = totem.getBlockPos().west(2);
      l.setBlock(supply, Blocks.CHEST.defaultBlockState(), 3);
      var relocated = (ChestBlockEntity) l.getBlockEntity(supply);
      for (int i = 0; i < chest.getContainerSize(); i++) {
        relocated.setItem(i, chest.getItem(i).copy());
        chest.setItem(i, ItemStack.EMPTY);
      }
      chest = relocated;
      totem.chests.clear();
      totem.chests.add(supply);
      var template = BuildingTemplate.load(l, "minehead");
      for (var cell : template.cells) {
        BlockPos p = template.world(totem.getBlockPos(), m.forward, cell.local());
        if (!p.equals(totem.getBlockPos()))
          l.setBlock(p, cell.state().rotate(BuildingTemplate.rotation(m.forward)), 2);
      }
      WorldData.get(l).mines.put(m.id, m);
    }
    if (underground) {
      MinePlan m = new MinePlan();
      m.mode = MinePlan.Mode.TUNNEL;
      m.edgeQuarry = false;
      m.shaftDepth = shaftDepth;
      m.depotRequired = false;
      m.entrance = h.absolutePos(new BlockPos(4, 41, 10));
      m.forward = Direction.EAST;
      m.stage = MinePlan.Stage.READY;
      m.lateral = 1;
      totem.mine = m;
      l.setBlock(
          m.entrance,
          Blocks.OAK_TRAPDOOR
              .defaultBlockState()
              .setValue(TrapDoorBlock.OPEN, true)
              .setValue(TrapDoorBlock.FACING, m.forward),
          3);
      for (int d = 1; d <= shaftDepth; d++) l.setBlock(m.ladder(d), m.ladderState(), 3);
      for (int x = -1; x <= 1; x++)
        for (int z = 0; z <= 2; z++) {
          m.retain(m.at(x, -shaftDepth, z));
          for (int y = -shaftDepth; y <= 2 - shaftDepth; y++)
            if (x != 0 || z != 0) l.setBlock(m.at(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
      WorldData.get(l).mines.put(m.id, m);
    }
    return new Rig(totem, home, w, chest);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void minington_job_tools_tiers_identity_and_save(GameTestHelper h) {
    var l = h.getLevel();
    Lumberton w = Automatons.LUMBERTON.get().create(l);
    w.owner = UUID.randomUUID();
    w.home = new BlockPos(-12, 70, -14);
    w.homeId = UUID.randomUUID();
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.cargo.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
    UUID identity = w.getUUID();
    w.setJob(2);
    h.assertTrue(
        w.activeTool().isEmpty() && w.tools.getStackInSlot(0).is(Items.IRON_AXE),
        "Retained axe used or lost");
    h.assertTrue(
        !w.tools.isItemValid(1, new ItemStack(Items.IRON_AXE))
            && w.tools.isItemValid(1, new ItemStack(Items.IRON_PICKAXE))
            && w.tools.isItemValid(2, new ItemStack(Items.IRON_SHOVEL)),
        "Wrong mining filters");
    h.assertTrue(
        !MiningRules.capable(
            new ItemStack(Items.STONE_PICKAXE), Blocks.DIAMOND_ORE.defaultBlockState()),
        "Weak pick can destroy diamond ore");
    h.assertTrue(
        MiningRules.capable(
            new ItemStack(Items.IRON_PICKAXE), Blocks.DIAMOND_ORE.defaultBlockState()),
        "Iron pick rejected");
    CompoundTag saved = w.saveWithoutId(new CompoundTag());
    Lumberton copy = Automatons.LUMBERTON.get().create(l);
    copy.load(saved);
    h.assertTrue(
        copy.job() == 2
            && copy.home.equals(w.home)
            && copy.homeId.equals(w.homeId)
            && copy.cargo.getStackInSlot(0).getCount() == 7,
        "Mining worker persistence");
    w.setJob(1);
    h.assertTrue(
        w.getUUID().equals(identity) && w.activeTool().is(Items.IRON_AXE),
        "Job change changed identity/tools");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void mining_arch_geometry_lane_endpoints_and_persistence(GameTestHelper h) {
    var arch = MinePlan.arch(h.getLevel());
    h.assertTrue(
        arch.stream().filter(p -> p.state().is(Blocks.COBBLESTONE)).count() == 38,
        "Arch cobble cost");
    h.assertTrue(
        arch.stream().filter(p -> p.state().is(Blocks.OAK_PLANKS)).count() == 4, "Arch feet");
    for (Direction d : Direction.Plane.HORIZONTAL) {
      MinePlan m = new MinePlan();
      m.shaftDepth = 5;
      m.depotRequired = false;
      m.entrance = new BlockPos(-130, 80, -270);
      m.forward = d;
      m.lateral = -1;
      m.stage = MinePlan.Stage.READY;
      Set<BlockPos> cells = new HashSet<>();
      for (int c = 0; c < 32; c++) {
        m.column = c;
        cells.add(m.tunnelColumn());
      }
      h.assertTrue(
          cells.size() == 32 && cells.contains(m.at(0, -5, 3)) && cells.contains(m.at(0, -5, 34)),
          "Tunnel off by one " + d);
      m.lane = 1;
      m.column = 31;
      h.assertTrue(m.tunnelColumn().equals(m.at(-3, -5, 3)), "Return plane drift");
      m.quarrySide = 1;
      for (int n = 1; n < 50; n++) {
        var a = m.step(n - 1);
        var b = m.step(n);
        h.assertTrue(
            a.getY() - b.getY() == 1
                && Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) == 1,
            "Stair discontinuity");
        m.retain(b);
      }
      var loaded = MinePlan.load(m.save());
      h.assertTrue(
          loaded.id.equals(m.id)
              && loaded.lateral == -1
              && loaded.supports.equals(m.supports)
              && loaded.tunnelColumn().equals(m.tunnelColumn()),
          "Mine reload geometry");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void fallen_timber_collision_blocks_player_movement(GameTestHelper h) {
    for (int x = 0; x < 12; x++)
      for (int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    Timber t = Automatons.TIMBER.get().create(h.getLevel());
    t.owner = UUID.randomUUID();
    BlockPos p = h.absolutePos(new BlockPos(6, 1, 6));
    t.setPos(p.getX(), p.getY(), p.getZ());
    t.sources.add(p);
    t.targets.add(p);
    t.wood.add(Blocks.OAK_LOG.defaultBlockState());
    t.rigidFall = true;
    CompoundTag n = t.saveWithoutId(new CompoundTag());
    n.putInt("Progress", 30);
    t.load(n);
    h.getLevel().addFreshEntity(t);
    t.tick();
    var player =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "CollisionTest"));
    player.moveTo(p.getX() - 1, p.getY(), p.getZ() + .5);
    double before = player.getX();
    player.move(MoverType.SELF, new Vec3(2, 0, 0));
    h.assertTrue(
        player.getX() < p.getX() && player.getX() > before,
        "Player walked through fallen timber: " + player.position());
    h.assertTrue(
        h.getLevel().getBlockState(p).is(Blocks.OAK_LOG)
            && !h.getLevel().noCollision(player, new AABB(p)),
        "No ordinary log block collision shape");
    t.discard();
    h.succeed();
  }

  @GameTest(template = "mine_test", timeoutTicks = 4000)
  public static void minington_descends_mines_solid_face_and_recalls_with_cargo(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    boolean[] recalled = {false}, descended = {false};
    h.onEachTick(
        () -> {
          if (h.getTick() % 100 == 0)
            org.slf4j.LoggerFactory.getLogger("automatons.test")
                .info("MINE DESCENT {}", r.worker.diagnostic());
          if (r.worker.getY() < m.entrance.getY() - 4) descended[0] = true;
          if (m.column >= 3 && !recalled[0]) {
            h.getLevel()
                .setBlock(
                    m.entrance,
                    Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, false),
                    3);
            r.home.recall(true);
            recalled[0] = true;
          }
          if (recalled[0] && r.worker.status().equals("Recalled")) {
            h.assertTrue(descended[0], "Never actually descended");
            h.assertTrue(
                r.worker.getY() >= m.entrance.getY() - .5, "Recalled without climbing out");
            h.assertTrue(!r.worker.cargoEmpty(), "Recall lost mined cargo");
            h.assertTrue(
                h.getLevel().getBlockState(m.exit().below()).is(Blocks.STONE),
                "Tunnel removed floor");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_builds_shared_access_after_surface_handover(GameTestHelper h) {
    Rig r = rig(h, false);
    r.chest.setItem(8, new ItemStack(Items.CHEST, 3));
    h.runAtTickTime(
        200,
        () -> {
          h.assertTrue(r.totem.mine.construction == 0, "Started shaft without four chests");
          r.chest.setItem(8, new ItemStack(Items.CHEST, 4));
        });
    h.onEachTick(
        () -> {
          if (h.getTick() % 100 == 0)
            org.slf4j.LoggerFactory.getLogger("automatons.test")
                .info(
                    "MINE BUILD {} stage={} step={}",
                    r.worker.diagnostic(),
                    r.totem.mine == null ? null : r.totem.mine.stage,
                    r.totem.mine == null ? 0 : r.totem.mine.construction);
          MinePlan m = r.totem.mine;
          if (m != null && m.stage == MinePlan.Stage.READY && m.column >= 2) {
            h.assertTrue(m.entrance != null, "No registered entrance");
            h.assertTrue(
                m.shaftDepth == 20 && m.chamber().getY() == m.entrance.getY() - 20,
                "Wrong new mine depth");
            h.assertTrue(
                m.depotBuilt && m.placedChests.size() == 4, "Four real chests were not installed");
            for (BlockPos pos : m.depotPositions())
              h.assertTrue(
                  Storage.depotHandler(h.getLevel(), pos, m) != null
                      && Storage.depotHandler(h.getLevel(), pos, m).getSlots() == 54,
                  "Not two usable double chests");
            for (int d = m.firstLadder(); d <= m.shaftDepth; d++)
              h.assertTrue(
                  h.getLevel().getBlockState(m.ladder(d)).is(Blocks.LADDER), "Missing ladder " + d);
            for (var cell : m.minehead(h.getLevel()).cells)
              if (!cell.state().isAir()) {
                BlockPos pos =
                    m.minehead(h.getLevel()).world(r.totem.getBlockPos(), m.forward, cell.local());
                h.assertTrue(
                    h.getLevel().getBlockState(pos).getBlock() == cell.state().getBlock(),
                    "New minehead shape mismatch");
              }
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 9000)
  public static void minington_two_workers_resume_one_saved_partial_shaft(GameTestHelper h) {
    Rig r = rig(h, false);
    Lumberton colleague = Automatons.LUMBERTON.get().create(h.getLevel());
    colleague.owner = r.worker.owner;
    colleague.home = r.worker.home;
    colleague.homeId = r.worker.homeId;
    colleague.homeDimension = r.worker.homeDimension;
    colleague.setJob(2);
    colleague.tools.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
    colleague.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 41, 8))));
    h.getLevel().addFreshEntity(colleague);
    colleague.assign(r.totem);
    for (Lumberton worker : List.of(r.worker, colleague)) {
      worker.cargo.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 38));
      worker.cargo.setStackInSlot(1, new ItemStack(Items.OAK_PLANKS, 4));
      worker.cargo.setStackInSlot(2, new ItemStack(Items.OAK_TRAPDOOR));
      worker.cargo.setStackInSlot(3, new ItemStack(Items.LADDER, 20));
      worker.cargo.setStackInSlot(5, new ItemStack(Items.CHEST, 4));
      worker.cargo.setStackInSlot(4, new ItemStack(Items.TORCH, 16));
    }
    UUID[] mineId = {null};
    BlockPos[] entrance = {null};
    Lumberton[] successor = {null};
    h.onEachTick(
        () -> {
          MinePlan m = r.totem.mine;
          if (m == null) return;
          if (mineId[0] == null) mineId[0] = m.id;
          h.assertTrue(m.id.equals(mineId[0]), "Competing miners created a second mine");
          if (m.entrance != null && entrance[0] == null) entrance[0] = m.entrance;
          if (entrance[0] != null)
            h.assertTrue(entrance[0].equals(m.entrance), "Mine entrance moved during takeover");
          if (successor[0] == null && m.stage == MinePlan.Stage.SHAFT && m.construction >= 12) {
            Lumberton builder = m.builder.equals(r.worker.getUUID()) ? r.worker : colleague;
            successor[0] = builder == r.worker ? colleague : r.worker;
            h.assertTrue(
                successor[0].status().equals("Waiting for builder") && successor[0].alerts == 0,
                "Second builder did not wait neutrally: " + successor[0].diagnostic());
            builder.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            MinePlan restored = MinePlan.load(m.save());
            h.assertTrue(restored.construction == m.construction, "Partial build progress lost");
            r.totem.mine = restored;
            WorldData.get(h.getLevel()).mines.put(restored.id, restored);
            r.totem.changed();
          } else if (successor[0] != null && m.stage == MinePlan.Stage.READY && m.column >= 2) {
            h.assertTrue(successor[0].getY() < m.entrance.getY() - 4, "Successor did not descend");
            h.assertTrue(WorldData.get(h.getLevel()).mines.get(m.id) == m, "Mine journal diverged");
            for (var cell : m.minehead(h.getLevel()).cells)
              if (!cell.state().isAir()) {
                BlockPos pos =
                    m.minehead(h.getLevel()).world(r.totem.getBlockPos(), m.forward, cell.local());
                h.assertTrue(
                    h.getLevel().getBlockState(pos).getBlock() == cell.state().getBlock(),
                    "Successor damaged minehead");
              }
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 5000)
  public static void minington_repairs_reachable_ladder_with_real_supply(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.LADDER));
    r.worker.cargo.setStackInSlot(1, new ItemStack(Items.TORCH, 16));
    boolean[] damaged = {false}, repaired = {false}, recalled = {false};
    h.onEachTick(
        () -> {
          if (!damaged[0] && m.column >= 1) {
            h.assertTrue(r.worker.mining.carried(Items.LADDER) == 1, "Repair supply lost");
            h.getLevel().setBlock(m.ladder(4), Blocks.AIR.defaultBlockState(), 3);
            damaged[0] = true;
          } else if (damaged[0]
              && !repaired[0]
              && h.getLevel().getBlockState(m.ladder(4)).is(Blocks.LADDER)) {
            h.assertTrue(
                r.worker.mining.carried(Items.LADDER) == 0, "Repair did not consume ladder");
            repaired[0] = true;
          }
          if (repaired[0] && m.column >= 3 && !recalled[0]) {
            r.home.recall(true);
            recalled[0] = true;
          }
          if (recalled[0] && r.worker.status().equals("Recalled")) {
            h.assertTrue(
                r.worker.getY() >= m.entrance.getY() - .5, "Repaired access was not climbed");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 5000)
  public static void minington_real_ore_hooks_enchantments_wear_and_experience(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    BlockPos ore = m.exit().above();
    h.getLevel().setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
    h.getLevel().setBlock(ore.below(), Blocks.DIAMOND_ORE.defaultBlockState(), 3);
    ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
    silk.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
    r.worker.tools.setStackInSlot(0, silk);
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
    DenyBreak guard = new DenyBreak(ore);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(guard);
    int[] phase = {0};
    h.onEachTick(
        () -> {
          if (phase[0] == 0 && guard.denied) {
            h.assertTrue(
                h.getLevel().getBlockState(ore).is(Blocks.DIAMOND_ORE), "Denied ore destroyed");
            h.assertTrue(
                silk.getDamageValue() == 0 && r.worker.mining.carried(Items.DIAMOND_ORE) == 0,
                "Denied mining paid or damaged tool");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(guard);
            phase[0] = 1;
          }
          if (phase[0] == 1 && h.getLevel().getBlockState(ore).isAir()) {
            h.assertTrue(
                r.worker.mining.carried(Items.DIAMOND_ORE) == 1 && silk.getDamageValue() == 1,
                "Silk/wear not exactly once");
            h.assertTrue(
                h.getLevel()
                    .getEntitiesOfClass(ExperienceOrb.class, new AABB(ore).inflate(4))
                    .isEmpty(),
                "Silk ore granted XP");
            ItemStack fortune = new ItemStack(Items.IRON_PICKAXE);
            fortune.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, 3);
            r.worker.tools.setStackInSlot(0, fortune);
            phase[0] = 2;
          }
          if (phase[0] == 2 && h.getLevel().getBlockState(ore.below()).isAir()) {
            r.worker.authorized = false;
            int diamonds = r.worker.mining.carried(Items.DIAMOND);
            h.assertTrue(diamonds >= 1 && diamonds <= 4, "Fortune diamond loot invalid");
            h.assertTrue(
                r.worker.tools.getStackInSlot(0).getDamageValue() == 1,
                "Fortune pick damaged more than once");
            int xp =
                h
                    .getLevel()
                    .getEntitiesOfClass(ExperienceOrb.class, new AABB(ore).inflate(5))
                    .stream()
                    .mapToInt(ExperienceOrb::getValue)
                    .sum();
            h.assertTrue(xp >= 3 && xp <= 7, "Ordinary ore XP missing: " + xp);
            h.assertTrue(
                h.getLevel()
                    .getEntitiesOfClass(LuminaOrb.class, new AABB(ore).inflate(5))
                    .isEmpty(),
                "Ore created Lumina");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_mode_changes_keep_one_mine_and_both_routes(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    UUID id = m.id;
    BlockPos entrance = m.entrance;
    int[] phase = {0};
    h.onEachTick(
        () -> {
          if (phase[0] == 0 && m.column >= 3) {
            m.switchMode(MinePlan.Mode.QUARRY);
            phase[0] = 1;
          }
          if (phase[0] == 1 && m.quarryLayer >= 6) {
            m.switchMode(MinePlan.Mode.TUNNEL);
            phase[0] = 2;
          }
          if (phase[0] == 2 && m.column >= 5) {
            m.switchMode(MinePlan.Mode.QUARRY);
            phase[0] = 3;
          }
          if (phase[0] == 3 && m.quarryLayer >= 8) {
            h.assertTrue(
                r.totem.mine == m && m.id.equals(id) && m.entrance.equals(entrance),
                "Mode switch replaced mine");
            for (int d = 0; d < 8; d++)
              h.assertTrue(
                  MiningRules.supported(h.getLevel(), m.step(d)),
                  "Mode change removed quarry access");
            for (int z = 3; z < 8; z++)
              h.assertTrue(
                  MiningRules.supported(h.getLevel(), m.at(0, -5, z)),
                  "Mode change removed tunnel floor");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 4000)
  public static void minington_measured_stone_work_with_three_pick_tiers(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    Item[] picks = {Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE};
    BlockPos[] targets = {m.exit().above(), m.exit(), m.exit().relative(m.forward).above()};
    int[] expected = {12, 8, 6}, phase = {0};
    long[] began = {-1};
    r.worker.tools.setStackInSlot(0, new ItemStack(picks[0]));
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
    r.chest.setItem(5, ItemStack.EMPTY);
    r.chest.setItem(6, ItemStack.EMPTY);
    h.onEachTick(
        () -> {
          int step = phase[0];
          if (step >= 3) return;
          BlockPos p = targets[step];
          if (began[0] < 0 && p.equals(r.worker.chopTarget())) began[0] = h.getTick() - 1;
          if (began[0] < 0 || !h.getLevel().getBlockState(p).isAir()) return;
          long elapsed = h.getTick() - began[0];
          h.assertTrue(
              elapsed == expected[step],
              "Unexpected measured mining ticks " + picks[step] + ": " + elapsed);
          org.slf4j.LoggerFactory.getLogger("automatons.test")
              .info("MEASURED STONE {} = {} work ticks", picks[step], elapsed);
          if (++phase[0] == 3) {
            r.worker.authorized = false;
            h.succeed();
            return;
          }
          began[0] = -1;
          r.worker.tools.setStackInSlot(0, new ItemStack(picks[phase[0]]));
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_relocates_real_torch_before_support_and_survives_reload(
      GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan initial = r.totem.mine;
    BlockPos light = initial.at(0, -4, 2), support = initial.at(0, -4, 3);
    h.getLevel()
        .setBlock(
            light,
            Blocks.WALL_TORCH
                .defaultBlockState()
                .setValue(WallTorchBlock.FACING, initial.forward.getOpposite()),
            3);
    initial.torches.add(light.asLong());
    initial.torchSupports.add(support.asLong());
    r.worker.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
    boolean[] replaced = {false}, reloaded = {false};
    h.onEachTick(
        () -> {
          MinePlan m = r.totem.mine;
          if (h.getTick() % 500 == 0)
            org.slf4j.LoggerFactory.getLogger("automatons.test")
                .info(
                    "MINE RELOCATE {} light={} replacement={} pending={} column={}",
                    r.worker.diagnostic(),
                    m.relocatingTorch,
                    m.replacementPlaced,
                    m.pendingLight,
                    m.column);
          if (m.replacementPlaced) {
            replaced[0] = true;
            h.assertTrue(
                h.getLevel().getBlockState(light).is(Blocks.WALL_TORCH),
                "Old light removed before replacement settled");
            if (!reloaded[0]) {
              r.totem.mine = MinePlan.load(m.save());
              reloaded[0] = true;
            }
          }
          if (m.column < 2 || m.relocatingTorch != null) return;
          h.assertTrue(replaced[0] && reloaded[0], "Relocation and saved task not exercised");
          h.assertTrue(h.getLevel().getBlockState(light).isAir(), "Old torch not recovered");
          h.assertTrue(
              h.getLevel().getBlockState(support).isAir(), "Scheduled excavation did not resume");
          long placed =
              m.torches.stream()
                  .map(BlockPos::of)
                  .filter(
                      p ->
                          h.getLevel().getBlockState(p).is(Blocks.TORCH)
                              || h.getLevel().getBlockState(p).is(Blocks.WALL_TORCH))
                  .count();
          int stock = 0;
          for (int i = 0; i < r.chest.getContainerSize(); i++)
            if (r.chest.getItem(i).is(Items.TORCH)) stock += r.chest.getItem(i).getCount();
          h.assertTrue(
              placed + stock + r.worker.mining.carried(Items.TORCH) == 81,
              "Torch relocation lost or duplicated stock");
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 9000)
  public static void minington_resumes_second_lane_after_surface_service(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    int[] phase = {0};
    h.onEachTick(
        () -> {
          if (phase[0] == 0 && m.lane == 1 && m.column >= 4) {
            r.home.recall(true);
            phase[0] = 1;
          }
          if (phase[0] == 1 && r.worker.status().equals("Recalled")) {
            r.home.recall(false);
            phase[0] = 2;
          }
          if (phase[0] == 2 && m.lane == 1 && m.column >= 8) {
            h.assertTrue(r.worker.getY() < m.entrance.getY() - 4, "Resumed without descending");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 9000)
  public static void minington_resumes_deep_quarry_after_surface_service(GameTestHelper h) {
    Rig r = rig(h, true, 20);
    MinePlan m = r.totem.mine;
    m.mode = MinePlan.Mode.QUARRY;
    int[] phase = {0};
    h.onEachTick(
        () -> {
          if (phase[0] == 0 && m.quarryLayer >= 18) {
            r.home.recall(true);
            phase[0] = 1;
          }
          if (phase[0] == 1 && r.worker.status().equals("Recalled")) {
            r.home.recall(false);
            phase[0] = 2;
          }
          if (phase[0] == 2 && m.quarryLayer >= 20) {
            h.assertTrue(
                r.worker.getY() < m.chamber().getY() - 15, "Deep quarry did not physically resume");
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 14000)
  public static void minington_two_real_tunnel_legs_turn_and_return(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    h.onEachTick(
        () -> {
          if (h.getTick() % 500 == 0)
            org.slf4j.LoggerFactory.getLogger("automatons.test")
                .info(
                    "MINE TWO LEGS {} lane={} column={} turn={}",
                    r.worker.diagnostic(),
                    m.lane,
                    m.column,
                    m.turn);
          if (m.lane < 2) return;
          r.home.recall(true);
          if (!r.worker.status().equals("Recalled")) return;
          for (int lane = 0; lane < 2; lane++)
            for (int col = 3; col <= 34; col++) {
              BlockPos feet = m.at(lane * 3 * m.lateral, -5, col);
              h.assertTrue(
                  h.getLevel().getBlockState(feet).getCollisionShape(h.getLevel(), feet).isEmpty(),
                  "Incomplete leg at " + feet);
              h.assertTrue(MiningRules.supported(h.getLevel(), feet), "Missing tunnel floor");
              h.assertTrue(
                  h.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet)
                      >= Balance.MINE_LIGHT.get(),
                  "Dark completed tunnel at " + feet);
            }
          h.assertTrue(r.worker.getY() >= m.entrance.getY() - .5, "Recall skipped ladder");
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 6500)
  public static void runnerton_existing_carried_torches_finish_delivery_without_new_pickups(
      GameTestHelper h) {
    Rig r = rig(h, true);
    var l = h.getLevel();
    r.chest.setItem(7, ItemStack.EMPTY);
    BlockPos anchor = h.absolutePos(new BlockPos(30, 41, 12));
    l.setBlock(anchor, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station station = (Station) l.getBlockEntity(anchor);
    station.owner = r.home.owner;
    BuildingProject warehouse = new BuildingProject();
    warehouse.home = r.home.identity;
    warehouse.owner = r.home.owner;
    warehouse.totem = station.identity;
    warehouse.anchor = anchor;
    warehouse.front = Direction.EAST;
    var template = warehouse.template(l);
    for (var c : template.cells) {
      BlockPos at = template.world(anchor, warehouse.front, c.local());
      if (at.equals(anchor)) continue;
      l.setBlock(at, c.state().rotate(BuildingTemplate.rotation(warehouse.front)), 2);
      if (!c.state().isAir()) warehouse.completed.add(at.asLong());
    }
    warehouse.ready = warehouse.approved = true;
    WorldData.get(l).buildings.put(station.identity, warehouse);
    var stock = warehouse.handler(l, warehouse.chests(l).get(0));
    stock.insertItem(0, new ItemStack(Items.TORCH, 16), false);
    Lumberton courier = Automatons.LUMBERTON.get().create(l);
    courier.owner = r.home.owner;
    courier.homeId = r.home.identity;
    courier.home = r.home.getBlockPos();
    courier.homeDimension = l.dimension().location().toString();
    courier.setJob(3);
    courier.moveTo(Vec3.atBottomCenterOf(anchor.west(2)));
    l.addFreshEntity(courier);
    courier.assign(station);
    courier.setNoAi(true);
    boolean[] shortage = {false}, accepted = {false}, carried = {false};
    String[] request = {null};
    h.onEachTick(
        () -> {
          var network = LogisticsNetwork.get(courier);
          network.refresh(l);
          if (!shortage[0] && (r.worker.alerts & 64) != 0) {
            shortage[0] = true;
            RunningGameTests.carriedParcel(courier, r.worker, stock.extractItem(0, 16, false));
            courier.setNoAi(false);
          }
          if (!shortage[0]) return;
          for (var demand : network.demands.values())
            if ("torch".equals(demand.kind) && r.worker.getUUID().equals(demand.worker)) {
              if (request[0] == null) request[0] = demand.id;
              else h.assertTrue(request[0].equals(demand.id), "Shortage request identity changed");
            }
          var parcel = network.parcels.get(courier.getUUID());
          if (parcel != null && parcel.item.is(Items.TORCH)) {
            accepted[0] = true;
            if (parcel.outstanding() > 0) carried[0] = true;
          }
          if (h.getTick() % 500 == 0)
            System.out.println(
                "RUNNER_TORCH "
                    + h.getTick()
                    + " "
                    + courier.status()
                    + " miner="
                    + r.worker.status());
          if (r.totem.mine.column < 1
              || r.totem.mine.torches.isEmpty()
              || (r.worker.alerts & 64) != 0) return;
          h.assertTrue(
              accepted[0] && carried[0] && request[0] != null,
              "Miner resumed without physical courier delivery");
          int total =
              courier.mining.carried(Items.TORCH)
                  + r.worker.mining.carried(Items.TORCH)
                  + count(r.chest, Items.TORCH);
          for (long cell : r.totem.mine.torches)
            if (l.getBlockState(BlockPos.of(cell)).is(Blocks.TORCH)
                || l.getBlockState(BlockPos.of(cell)).is(Blocks.WALL_TORCH)) total++;
          for (BlockPos pos : warehouse.chests(l))
            if (Storage.canonical(l, pos).equals(pos)) {
              var inv = warehouse.handler(l, pos);
              for (int j = 0; j < inv.getSlots(); j++)
                if (inv.getStackInSlot(j).is(Items.TORCH))
                  total += inv.getStackInSlot(j).getCount();
            }
          h.assertTrue(total == 16, "Torch delivery duplicated or lost stock");
          courier.setNoAi(true);
          r.worker.setNoAi(true);
          h.succeed();
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 6500)
  public static void minington_torch_shortage_alternate_supply_and_light_repair(GameTestHelper h) {
    Rig r = rig(h, true);
    MinePlan m = r.totem.mine;
    r.chest.setItem(7, ItemStack.EMPTY);
    BlockPos alternate = h.absolutePos(new BlockPos(10, 41, 6));
    h.getLevel().setBlock(alternate, Blocks.CHEST.defaultBlockState(), 3);
    var supply = (ChestBlockEntity) h.getLevel().getBlockEntity(alternate);
    r.totem.chests.add(alternate);
    int[] phase = {0};
    long[] removed = {0};
    h.onEachTick(
        () -> {
          if (phase[0] == 0 && h.getTick() > 200 && (r.worker.alerts & 64) != 0) {
            h.assertTrue(
                m.column == 0 && m.torches.isEmpty(), "Excavated darkness without a torch");
            supply.setItem(0, new ItemStack(Items.TORCH, 1));
            phase[0] = 1;
          }
          if (phase[0] == 1 && !m.torches.isEmpty() && m.column >= 1) {
            h.assertTrue(
                r.worker.mining.carried(Items.TORCH) == 0, "One-torch supply created extra items");
            removed[0] = m.torches.iterator().next();
            h.getLevel().setBlock(BlockPos.of(removed[0]), Blocks.AIR.defaultBlockState(), 3);
            phase[0] = 2;
          }
          if (phase[0] == 2 && (r.worker.alerts & 64) != 0) {
            supply.setItem(0, new ItemStack(Items.TORCH, 2));
            phase[0] = 3;
          }
          if (phase[0] == 3
              && m.column >= 2
              && r.worker.status().equals("Mining")
              && m.pendingLight == null
              && h.getLevel().getGameTime() > m.lightWait + 5
              && (r.worker.alerts & 64) == 0) {
            long placed =
                m.torches.stream()
                    .map(BlockPos::of)
                    .filter(
                        p ->
                            h.getLevel().getBlockState(p).is(Blocks.TORCH)
                                || h.getLevel().getBlockState(p).is(Blocks.WALL_TORCH))
                    .count();
            int chestTorches = 0;
            for (int i = 0; i < supply.getContainerSize(); i++)
              if (supply.getItem(i).is(Items.TORCH)) chestTorches += supply.getItem(i).getCount();
            h.assertTrue(
                placed + r.worker.mining.carried(Items.TORCH) + chestTorches == 2,
                "Torch repair lost or duplicated items");
            for (long cell : m.passage) {
              BlockPos feet = BlockPos.of(cell);
              if (MiningRules.supported(h.getLevel(), feet))
                h.assertTrue(
                    h.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, feet)
                        >= Balance.MINE_LIGHT.get(),
                    "Repair left a dark floor");
            }
            h.succeed();
          }
        });
  }

  @GameTest(template = "mine_test", timeoutTicks = 7000)
  public static void minington_quarry_real_steps_multiple_turns(GameTestHelper h) {
    Rig r = rig(h, true, 20);
    MinePlan m = r.totem.mine;
    m.mode = MinePlan.Mode.QUARRY;
    // Deep test terrain stays inside this isolated template and away from other tests.

    h.onEachTick(
        () -> {
          if (h.getTick() % 100 == 0)
            org.slf4j.LoggerFactory.getLogger("automatons.test")
                .info(
                    "MINE QUARRY {} depth={} cell={}",
                    r.worker.diagnostic(),
                    m.quarryLayer,
                    m.quarryCell);
          if (m.quarryLayer >= 18) {
            for (int d = 0; d < 18; d++)
              h.assertTrue(
                  MiningRules.supported(h.getLevel(), m.step(d)), "Lost stair support " + d);
            r.home.recall(true);
            if (r.worker.status().equals("Recalled")) h.succeed();
          }
        });
  }
}
