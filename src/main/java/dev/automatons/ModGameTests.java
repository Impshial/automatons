package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
  private static final UUID OWNER = UUID.fromString("7fd34b50-4e12-4a70-b3bb-7c1b0f7a3c49");

  private static ServerPlayer owner(ServerLevel l, BlockPos p) {
    ServerPlayer player = FakePlayerFactory.get(l, new GameProfile(OWNER, "AutomatonsTest"));
    player.setPos(p.getX() + .5, p.getY(), p.getZ() + .5);
    return player;
  }

  private static Station station(GameTestHelper h, BlockPos pos, boolean totem) {
    h.setBlock(pos, totem ? Automatons.TOTEM.get() : Automatons.PEDESTAL.get());
    Station s = (Station) h.getLevel().getBlockEntity(h.absolutePos(pos));
    s.owner = OWNER;
    s.registerHome();
    return s;
  }

  private static void floor(GameTestHelper h) {
    for (int x = 0; x < 28; x++)
      for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
  }

  private static Lumberton worker(GameTestHelper h, Station home, BlockPos p) {
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = OWNER;
    w.home = home.getBlockPos();
    w.homeId = home.identity;
    w.homeDimension = h.getLevel().dimension().location().toString();
    w.moveTo(
        h.absolutePos(p).getX() + .5, h.absolutePos(p).getY(), h.absolutePos(p).getZ() + .5, 0, 0);
    h.getLevel().addFreshEntity(w);
    return w;
  }

  private static void tree(GameTestHelper h, BlockPos base) {
    for (int y = 0; y < 5; y++) h.setBlock(base.above(y), Blocks.OAK_LOG);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        for (int y = 3; y <= 5; y++)
          if (x != 0 || z != 0 || y == 5)
            h.setBlock(
                base.offset(x, y, z),
                Blocks.OAK_LEAVES
                    .defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false)
                    .setValue(LeavesBlock.DISTANCE, 1));
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exact_tools_and_upgrade_limits(GameTestHelper h) {
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    h.assertTrue(
        w.tools.getSlots() == 3 && w.cargo.getSlots() == 9, "3 tool and 9 cargo slots required");
    h.assertTrue(
        !w.tools.insertItem(0, new ItemStack(Items.DIAMOND_SWORD), false).isEmpty(),
        "Sword accepted");
    ItemStack axe = new ItemStack(Items.IRON_AXE);
    axe.setDamageValue(123);
    axe.enchant(Enchantments.BLOCK_EFFICIENCY, 2);
    w.tools.insertItem(0, axe, false);
    h.assertTrue(w.activeTool().getDamageValue() == 123, "Axe durability lost");
    h.assertTrue(
        w.getItemBySlot(EquipmentSlot.MAINHAND) == w.tools.getStackInSlot(0),
        "Rendered tool is a separate copy");
    h.assertTrue(
        !w.cargo.insertItem(0, new ItemStack(Items.IRON_AXE), false).isEmpty(),
        "Fourth axe can be inserted into cargo");
    Station s = station(h, new BlockPos(3, 1, 3), true);
    s.inventory.insertItem(0, new ItemStack(Automatons.UPGRADES.get("storage_1").get()), false);
    h.assertTrue(
        !s.inventory
            .insertItem(1, new ItemStack(Automatons.UPGRADES.get("storage_2").get()), false)
            .isEmpty(),
        "Conflicting upgrade accepted");
    h.assertTrue(s.storageRange() == 8, "Storage upgrade not applied");
    CompoundTag saved = new CompoundTag();
    w.addAdditionalSaveData(saved);
    Lumberton copy = Automatons.LUMBERTON.get().create(h.getLevel());
    copy.readAdditionalSaveData(saved);
    h.assertTrue(
        copy.tools.getStackInSlot(0).getDamageValue() == 123, "Inventory reload lost durability");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void pedestal_transfers_real_axe_once(GameTestHelper h) {
    floor(h);
    Station s = station(h, new BlockPos(3, 1, 3), false);
    ServerPlayer p = owner(h.getLevel(), s.getBlockPos());
    s.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 6));
    s.inventory.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 4));
    s.inventory.setStackInSlot(2, new ItemStack(Items.AMETHYST_SHARD));
    ItemStack axe = new ItemStack(Items.IRON_AXE);
    axe.setDamageValue(111);
    axe.enchant(Enchantments.UNBREAKING, 2);
    s.inventory.setStackInSlot(3, axe);
    h.assertTrue(s.create(p), "Creation failed");
    h.assertTrue(s.workers.size() == 1, "Wrong worker count");
    Lumberton w = (Lumberton) h.getLevel().getEntity(s.workers.iterator().next());
    h.assertTrue(w != null && w.activeTool().getDamageValue() == 111, "Actual axe not transferred");
    h.assertTrue(w.homeId.equals(s.identity), "Home identity missing");
    h.assertTrue(!s.create(p), "Recipe consumed twice");
    for (int i = 0; i < 4; i++)
      h.assertTrue(s.inventory.getStackInSlot(i).isEmpty(), "Ingredients remain");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void negative_chunk_protection_and_recall_persist(GameTestHelper h) {
    ServerLevel l = h.getLevel();
    WorldData data = WorldData.get(l);
    BlockPos p = new BlockPos(-1, l.getMinBuildHeight(), -17);
    data.protection.put(new net.minecraft.world.level.ChunkPos(p).toLong(), OWNER);
    h.assertTrue(
        data.protectedAt(new BlockPos(-16, l.getMaxBuildHeight() - 1, -32)),
        "Full-height negative chunk lookup failed");
    h.assertTrue(
        !Permissions.allowed(l, p, OWNER, Permissions.Action.CUT), "Protected mutation allowed");
    Station home = station(h, new BlockPos(4, 1, 4), false);
    home.recall(true);
    WorldData copy = WorldData.load(data.save(new CompoundTag()));
    h.assertTrue(copy.homes.get(home.identity).recalled(), "Recall lost on save");
    h.assertTrue(copy.protectedAt(p), "Protection lost on save");
    data.protection.remove(new net.minecraft.world.level.ChunkPos(p).toLong());
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void construction_and_protection_rejected(GameTestHelper h) {
    floor(h);
    Station s = station(h, new BlockPos(12, 1, 12), true);
    BlockPos base = new BlockPos(15, 1, 12);
    tree(h, base);
    BlockPos actual = h.absolutePos(base);
    h.assertTrue(
        TreeFinder.inspect(h.getLevel(), actual, s).tree() != null, "Natural tree not recognized");
    WorldData d = WorldData.get(h.getLevel());
    d.placedLogs.add(actual.above(2).asLong());
    h.assertTrue(
        TreeFinder.inspect(h.getLevel(), actual, s).tree() == null,
        "Player construction recognized as tree");
    d.placedLogs.remove(actual.above(2).asLong());
    long chunk = new net.minecraft.world.level.ChunkPos(actual).toLong();
    d.protection.put(chunk, OWNER);
    h.assertTrue(
        TreeFinder.inspect(h.getLevel(), actual, s).tree() == null, "Protected tree queued");
    d.protection.remove(chunk);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 1800)
  public static void fell_process_unload(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(11, 1, 10), true);
    BlockPos chest = new BlockPos(10, 1, 10);
    h.setBlock(chest, Blocks.CHEST);
    ChestBlockEntity storage = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chest));
    Lumberton w = worker(h, home, new BlockPos(10, 1, 12));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.assign(s);
    s.chests.add(h.absolutePos(chest));
    s.storageComplete = true;
    BlockPos base = new BlockPos(13, 1, 12);
    tree(h, base);
    var result = TreeFinder.inspect(h.getLevel(), h.absolutePos(base), s);
    h.assertTrue(result.tree() != null, "Tree rejected: " + result.reason());
    s.trees.put(h.absolutePos(base).asLong(), result.tree());
    h.succeedWhen(
        () -> {
          int count = 0;
          for (int i = 0; i < storage.getContainerSize(); i++)
            if (storage.getItem(i).is(Items.OAK_LOG)) count += storage.getItem(i).getCount();
          h.assertTrue(
              count == 5,
              "Expected 5 logs in chest, got "
                  + count
                  + "; state="
                  + w.status()
                  + " cargo="
                  + w.cargo.serializeNBT()
                  + " pos="
                  + w.blockPosition()
                  + " scan="
                  + s.scanReason);
          h.assertTrue(h.getBlockState(base).isAir(), "Source trunk remains");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 350)
  public static void independent_alerts_and_recall_recovery(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(4, 1, 4), false),
        s = station(h, new BlockPos(9, 1, 9), true);
    BlockPos chest = new BlockPos(10, 1, 9);
    h.setBlock(chest, Blocks.CHEST);
    ChestBlockEntity storage = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chest));
    for (int i = 0; i < 27; i++) storage.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    Lumberton w = worker(h, home, new BlockPos(5, 1, 5));
    w.assign(s);
    for (int i = 0; i < 9; i++) w.cargo.setStackInSlot(i, new ItemStack(Items.OAK_LOG, 64));
    home.recall(true);
    h.runAfterDelay(
        220,
        () -> {
          h.assertTrue(
              (w.alerts & 13) == 13,
              "Independent tool/cargo/storage blockers missing: " + w.alerts);
          h.assertTrue(w.status().equals("Recalled"), "Recall must override alerts: " + w.status());
          w.tools.insertItem(0, new ItemStack(Items.IRON_AXE), false);
        });
    h.runAfterDelay(
        250,
        () -> {
          h.assertTrue(
              (w.alerts & 1) == 0 && (w.alerts & 12) == 12,
              "Tool recovery erased unrelated alerts: " + w.alerts);
          for (int i = 0; i < 9; i++) w.cargo.setStackInSlot(i, ItemStack.EMPTY);
          storage.setItem(0, ItemStack.EMPTY);
        });
    h.runAfterDelay(
        290,
        () -> {
          h.assertTrue((w.alerts & 13) == 0, "Restored cargo/storage did not recover: " + w.alerts);
          h.assertTrue(w.status().equals("Recalled"), "Recovery cancelled recall");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void reservations_and_identity_replacement(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(9, 1, 9), true);
    Lumberton w = worker(h, home, new BlockPos(4, 1, 3));
    w.assign(s);
    UUID old = s.identity;
    var data = WorldData.get(h.getLevel());
    var logs =
        List.of(h.absolutePos(new BlockPos(15, 1, 15)), h.absolutePos(new BlockPos(15, 2, 15)));
    UUID other = UUID.randomUUID();
    h.assertTrue(data.reserve(logs, w.getUUID()), "First reservation rejected");
    h.assertTrue(!data.reserve(logs, other), "Overlapping reservation accepted");
    data.release(w.getUUID());
    h.assertTrue(data.reserve(logs, other), "Released reservation stayed locked");
    data.release(other);
    h.setBlock(new BlockPos(9, 1, 9), Blocks.AIR);
    Station replacement = station(h, new BlockPos(9, 1, 9), true);
    h.assertTrue(
        !replacement.identity.equals(old) && replacement.worker == null,
        "Replacement adopted prior identity");
    h.assertTrue(w.totemId == null, "Destroyed totem kept worker binding");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void partial_unload_and_item_data(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(6, 1, 6), true);
    BlockPos chest = new BlockPos(7, 1, 6);
    h.setBlock(chest, Blocks.CHEST);
    ChestBlockEntity c = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chest));
    for (int i = 0; i < 27; i++) c.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    c.setItem(0, new ItemStack(Items.OAK_LOG, 62));
    Lumberton w = worker(h, home, new BlockPos(7, 1, 7));
    w.assign(s);
    w.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 10));
    Storage.transfer(w, s, h.absolutePos(chest));
    h.assertTrue(
        c.getItem(0).getCount() == 64 && w.cargo.getStackInSlot(0).getCount() == 8,
        "Partial insertion lost or duplicated cargo");
    c.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
    ItemStack tool = new ItemStack(Items.IRON_AXE);
    tool.setDamageValue(99);
    tool.setHoverName(net.minecraft.network.chat.Component.literal("My axe"));
    c.setItem(2, tool);
    Storage.transfer(w, s, h.absolutePos(chest));
    h.assertTrue(c.getItem(1).is(Items.DIAMOND_SWORD), "Wrong tool withdrawn");
    h.assertTrue(
        w.activeTool().getDamageValue() == 99 && w.activeTool().hasCustomHoverName(),
        "Replacement data lost");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 150)
  public static void fall_protection_and_timber_reload(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(12, 1, 12), true);
    Lumberton w = worker(h, home, new BlockPos(12, 1, 14));
    w.setNoAi(true);
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.assign(s);
    BlockPos base = new BlockPos(14, 1, 12);
    tree(h, base);
    var detected = TreeFinder.inspect(h.getLevel(), h.absolutePos(base), s).tree();
    h.assertTrue(detected != null, "No tree");
    var landing = TreeFinder.landing(h.getLevel(), detected, s, OWNER, w);
    h.assertTrue(landing != null, "No landing");
    Timber timber = Timber.commit(h.getLevel(), detected, landing, w, s);
    h.assertTrue(timber != null, "No committed timber");
    long chunk = new net.minecraft.world.level.ChunkPos(landing.targets().get(0)).toLong();
    int[] stopped = {0};
    h.runAfterDelay(
        10,
        () -> {
          WorldData.get(h.getLevel()).protection.put(chunk, OWNER);
          stopped[0] = timber.progress();
        });
    h.runAfterDelay(
        35,
        () -> {
          h.assertTrue(timber.progress() == stopped[0], "Protected falling job kept moving");
          h.assertTrue(w.cargoEmpty(), "Protected fall yielded items");
          WorldData.get(h.getLevel()).protection.remove(chunk);
        });
    h.runAfterDelay(
        80,
        () -> {
          h.assertTrue(timber.progress() >= timber.duration, "Fall did not recover");
          BlockPos target = timber.targets.get(0);
          w.moveTo(target.getX() + .5, target.getY(), target.getZ() + 1.5, 0, 0);
          h.assertTrue(timber.process(0, w, s), "Timber could not process");
          h.assertTrue(!timber.process(0, w, s), "Timber paid twice");
          CompoundTag tag = timber.saveWithoutId(new CompoundTag());
          Timber copy = Automatons.TIMBER.get().create(h.getLevel());
          copy.load(tag);
          h.assertTrue(
              copy.consumed(0) && copy.remaining() == 4, "Saved timber lost section identity");
          h.assertTrue(!copy.process(0, w, s), "Reload paid consumed section again");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 110)
  public static void measure_level_walking_speed(GameTestHelper h) {
    floor(h);
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    BlockPos start = h.absolutePos(new BlockPos(3, 1, 12));
    w.moveTo(start.getX() + .5, start.getY(), start.getZ() + .5, 0, 0);
    h.getLevel().addFreshEntity(w);
    double[] x = {0};
    h.runAfterDelay(
        10,
        () -> w.getNavigation().moveTo(start.getX() + 20.5, start.getY(), start.getZ() + .5, 1));
    h.runAfterDelay(30, () -> x[0] = w.getX());
    h.runAfterDelay(
        70,
        () -> {
          double bps = (w.getX() - x[0]) / 2;
          System.out.println("AUTOMATONS_MEASURE walking_blocks_per_second=" + bps);
          h.assertTrue(bps > 1.5 && bps < 2.5, "Walking speed outside provisional target: " + bps);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void missing_home_requires_explicit_rebind(GameTestHelper h) {
    floor(h);
    BlockPos homePos = new BlockPos(3, 1, 3);
    Station home = station(h, homePos, false);
    Lumberton w = worker(h, home, new BlockPos(4, 1, 3));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    UUID prior = w.homeId;
    h.setBlock(homePos, Blocks.AIR);
    Station replacement = station(h, homePos, false);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(
              prior.equals(w.homeId) && w.status().equals("Home missing"),
              "Replacement home silently adopted");
          h.assertTrue(Inventories.axe(w.activeTool()), "Missing home lost tool");
          h.assertTrue(
              w.rebind(replacement).equals("Home rebound") && w.homeId.equals(replacement.identity),
              "Explicit rebind failed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void unloaded_dependency_preserves_binding(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false);
    Lumberton w = worker(h, home, new BlockPos(4, 1, 3));
    BlockPos distant = new BlockPos(120000, 64, 120000);
    h.assertTrue(!h.getLevel().hasChunkAt(distant), "Test dependency unexpectedly loaded");
    UUID identity = UUID.randomUUID();
    w.totem = distant;
    w.totemId = identity;
    h.runAfterDelay(
        30,
        () -> {
          h.assertTrue(
              identity.equals(w.totemId) && w.status().equals("Dependency unloaded"),
              "Unloaded dependency treated as destroyed");
          h.assertTrue(!h.getLevel().hasChunkAt(distant), "Worker force-loaded dependency");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void tool_break_switching_and_death_drops(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false);
    Lumberton w = worker(h, home, new BlockPos(5, 1, 5));
    w.setNoAi(true);
    for (int slot = 0; slot < 3; slot++) {
      ItemStack axe = new ItemStack(Items.IRON_AXE);
      axe.setDamageValue(249);
      w.tools.setStackInSlot(slot, axe);
    }
    w.wear(1);
    h.assertTrue(
        w.activeSlot() == 1 && w.tools.getStackInSlot(0).isEmpty(),
        "First broken axe not replaced");
    w.wear(1);
    h.assertTrue(w.activeSlot() == 2, "Second broken axe not replaced");
    w.wear(1);
    h.assertTrue(w.activeTool().isEmpty(), "Last tool did not break");
    for (int slot = 0; slot < 3; slot++)
      w.tools.setStackInSlot(slot, new ItemStack(Items.IRON_AXE));
    w.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 10));
    w.hurt(w.damageSources().generic(), 1000);
    h.runAfterDelay(
        5,
        () -> {
          int axes = 0, logs = 0;
          for (var item :
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class,
                      w.getBoundingBox().inflate(3))) {
            if (item.getItem().is(Items.IRON_AXE)) axes += item.getItem().getCount();
            if (item.getItem().is(Items.OAK_LOG)) logs += item.getItem().getCount();
          }
          h.assertTrue(
              axes == 3 && logs == 10,
              "Death dropped wrong quantities: axes=" + axes + " logs=" + logs);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1800)
  public static void full_storage_recovers_automatically(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(11, 1, 10), true);
    BlockPos chest = new BlockPos(10, 1, 10);
    h.setBlock(chest, Blocks.CHEST);
    ChestBlockEntity c = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chest));
    for (int slot = 0; slot < 27; slot++) c.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
    Lumberton w = worker(h, home, new BlockPos(10, 1, 12));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.assign(s);
    tree(h, new BlockPos(13, 1, 12));
    h.runAfterDelay(
        220,
        () -> {
          h.assertTrue((w.alerts & Lumberton.STORAGE) != 0, "Full chest did not signal");
          for (int slot = 0; slot < 27; slot++) c.setItem(slot, ItemStack.EMPTY);
        });
    h.succeedWhen(
        () -> {
          int count = 0;
          for (int slot = 0; slot < 27; slot++)
            if (c.getItem(slot).is(Items.OAK_LOG)) count += c.getItem(slot).getCount();
          h.assertTrue(count == 5, "Storage did not recover: " + w.status());
        });
  }

  @GameTest(template = "empty", timeoutTicks = 3400)
  public static void no_wood_discovery_recovers_without_resume(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(11, 1, 10), true);
    BlockPos chest = new BlockPos(10, 1, 10);
    h.setBlock(chest, Blocks.CHEST);
    ChestBlockEntity c = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chest));
    Lumberton w = worker(h, home, new BlockPos(10, 1, 12));
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.assign(s);
    h.runAfterDelay(
        1450,
        () -> {
          h.assertTrue(
              (w.alerts & Lumberton.WOOD) != 0,
              "No-work condition was not established after complete scan: " + w.status());
          tree(h, new BlockPos(13, 1, 12));
        });
    h.succeedWhen(
        () -> {
          int count = 0;
          for (int slot = 0; slot < 27; slot++)
            if (c.getItem(slot).is(Items.OAK_LOG)) count += c.getItem(slot).getCount();
          h.assertTrue(count == 5, "New tree did not recover: " + w.status() + " scan=" + s.cursor);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void scan_budget_is_fair_across_many_totems(GameTestHelper h) {
    java.util.List<Station> stations = new java.util.ArrayList<>();
    for (int x = 0; x < 10; x++)
      for (int z = 0; z < 5; z++) {
        Station station = station(h, new BlockPos(2 + x * 2, 1, 2 + z * 2), true);
        station.worker = UUID.randomUUID();
        stations.add(station);
      }
    h.runAfterDelay(
        100,
        () -> {
          for (Station station : stations) {
            h.assertTrue(station.cursor > 0, "A loaded totem starved under the global scan budget");
            station.worker = null;
          }
          System.out.println("AUTOMATONS_MEASURE scan_fairness_totems=50 all_progressed=true");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void staged_crown_loot_is_owned_once(GameTestHelper h) {
    floor(h);
    Station home = station(h, new BlockPos(3, 1, 3), false),
        s = station(h, new BlockPos(12, 1, 12), true);
    Lumberton w = worker(h, home, new BlockPos(12, 1, 14));
    w.setNoAi(true);
    ItemStack axe = new ItemStack(Items.IRON_AXE);
    axe.enchant(Enchantments.SILK_TOUCH, 1);
    w.tools.setStackInSlot(0, axe);
    w.assign(s);
    BlockPos base = new BlockPos(14, 1, 12);
    tree(h, base);
    var t = TreeFinder.inspect(h.getLevel(), h.absolutePos(base), s).tree();
    var landing = TreeFinder.landing(h.getLevel(), t, s, OWNER, w);
    Timber timber = Timber.commit(h.getLevel(), t, landing, w, s);
    h.assertTrue(
        timber != null && !timber.crownLoot.isEmpty(), "Exclusive crown was not transferred");
    int expected = timber.crownLoot.stream().mapToInt(ItemStack::getCount).sum();
    h.runAfterDelay(
        45,
        () -> {
          BlockPos target = timber.targets.get(0);
          w.moveTo(target.getX() + .5, target.getY(), target.getZ() + 1.5, 0, 0);
          for (int slot = 0; slot < 9; slot++)
            w.cargo.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
          h.assertTrue(!timber.process(0, w, s), "Full cargo accepted a timber payout");
          h.assertTrue(
              timber.crownLoot.isEmpty() && timber.pendingLoot != null,
              "Foliage has two authoritative owners");
          CompoundTag saved = timber.saveWithoutId(new CompoundTag());
          Timber copy = Automatons.TIMBER.get().create(h.getLevel());
          copy.load(saved);
          h.assertTrue(
              copy.crownLoot.isEmpty() && copy.pendingLoot != null,
              "Staged foliage did not persist");
          for (int slot = 0; slot < 9; slot++) w.cargo.setStackInSlot(slot, ItemStack.EMPTY);
          h.assertTrue(timber.process(0, w, s), "Staged payout did not recover");
          int count = 0;
          for (int slot = 0; slot < 9; slot++)
            if (w.cargo.getStackInSlot(slot).is(Items.OAK_LEAVES))
              count += w.cargo.getStackInSlot(slot).getCount();
          h.assertTrue(count == expected, "Crown yield changed or duplicated");
          h.succeed();
        });
  }
}
