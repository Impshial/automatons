package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class RedesignGameTests {
  private record Rig(ServerPlayer player, Station home, Station totem) {}

  private static Rig rig(GameTestHelper h) {
    for (int x = 0; x < 28; x++)
      for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
    ServerPlayer p =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "RedesignTest"));
    h.setBlock(new BlockPos(3, 1, 3), Automatons.PEDESTAL.get());
    h.setBlock(new BlockPos(14, 1, 14), Automatons.TOTEM.get());
    Station home = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 1, 3))),
        totem = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(14, 1, 14)));
    home.owner = p.getUUID();
    home.registerHome();
    totem.owner = p.getUUID();
    p.moveTo(Vec3.atBottomCenterOf(home.getBlockPos().south()));
    return new Rig(p, home, totem);
  }

  private static Lumberton worker(GameTestHelper h, Rig r, BlockPos at) {
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = r.player.getUUID();
    w.home = r.home.getBlockPos();
    w.homeId = r.home.identity;
    w.homeDimension = h.getLevel().dimension().location().toString();
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(at)));
    h.getLevel().addFreshEntity(w);
    w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
    w.assign(r.totem);
    return w;
  }

  private static void parts(Station s, int count) {
    Item[] parts = {
      Automatons.HEAD.get(),
      Automatons.ARM.get(),
      Automatons.BODY.get(),
      Automatons.ARM.get(),
      Automatons.LEG.get(),
      Automatons.LEG.get()
    };
    for (int i = 0; i < 6; i++) s.inventory.setStackInSlot(i, new ItemStack(parts[i], count));
  }

  private static ChestBlockEntity chest(GameTestHelper h, Rig r, BlockPos at) {
    h.setBlock(at, Blocks.CHEST);
    BlockPos p = h.absolutePos(at);
    r.totem.chests.add(p);
    r.totem.storageComplete = true;
    return (ChestBlockEntity) h.getLevel().getBlockEntity(p);
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

  private static int count(ChestBlockEntity c, Item item) {
    int n = 0;
    for (int i = 0; i < c.getContainerSize(); i++)
      if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
    return n;
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void infusion_exact_cost_pause_competition_and_reload(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos at = h.absolutePos(new BlockPos(4, 1, 3));
    h.getLevel().setBlock(at, Automatons.INFUSER.get().defaultBlockState(), 3);
    Infuser inf = (Infuser) h.getLevel().getBlockEntity(at);
    r.player.moveTo(inf.corePosition().add(1, 0, 0));
    Magica.add(r.player, 50);
    inf.core = CoreItem.vessel(0);
    inf.donor = r.player.getUUID();
    for (int i = 0; i < 199; i++) inf.advance(r.player);
    h.assertTrue(
        CoreItem.charge(inf.core) == 49 && Magica.balance(r.player) == 1, "Wrong transfer cadence");
    CompoundTag saved = inf.saveWithoutMetadata();
    Infuser loaded = new Infuser(at, inf.getBlockState());
    loaded.setLevel(h.getLevel());
    loaded.load(saved);
    h.assertTrue(
        CoreItem.charge(loaded.core) == 49 && loaded.donor.equals(r.player.getUUID()),
        "Partial infusion did not save");
    r.player.moveTo(inf.corePosition().add(8, 0, 0));
    for (int i = 0; i < 40; i++) inf.advance(r.player);
    h.assertTrue(Magica.balance(r.player) == 1, "Spent out of range");
    inf.advance(null);
    r.player.moveTo(inf.corePosition());
    for (int i = 0; i < 4; i++) {
      inf.advance(r.player);
      loaded.advance(r.player);
    }
    h.assertTrue(
        Magica.balance(r.player) == 0
            && CoreItem.charge(inf.core) + CoreItem.charge(loaded.core) == 99,
        "Competing infusers overspent");
    h.assertTrue(
        inf.core.is(Automatons.MAGICA_CORE.get()) && !inf.transferring, "Completion not ready");
    for (int i = 0; i < 40; i++) inf.advance(r.player);
    h.assertTrue(CoreItem.charge(inf.core) == 50, "Full core changed");
    h.assertTrue(
        CoreItem.vessel(12).getMaxStackSize() == 1
            && CoreItem.vessel(0).getMaxStackSize() > 1
            && CoreItem.vessel(50).getMaxStackSize() > 1,
        "Charge stacking rules");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void infusion_hand_priority_retrieval_and_break_once(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos p = h.absolutePos(new BlockPos(4, 1, 3));
    h.getLevel().setBlock(p, Automatons.INFUSER.get().defaultBlockState(), 3);
    Infuser inf = (Infuser) h.getLevel().getBlockEntity(p);
    var block = inf.getBlockState();
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false);
    r.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    r.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Automatons.EMPTY_CORE.get(), 2));
    h.assertTrue(
        block.use(h.getLevel(), r.player, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
        "Empty main hand swallowed offhand");
    block.use(h.getLevel(), r.player, InteractionHand.OFF_HAND, hit);
    block.use(h.getLevel(), r.player, InteractionHand.OFF_HAND, hit);
    h.assertTrue(
        r.player.getOffhandItem().getCount() == 1 && !inf.core.isEmpty(),
        "Repeated insert consumed twice");
    block.use(h.getLevel(), r.player, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        r.player.getMainHandItem().is(Automatons.EMPTY_CORE.get()) && inf.core.isEmpty(),
        "Retrieve failed");
    block.use(h.getLevel(), r.player, InteractionHand.MAIN_HAND, hit);
    h.getLevel().destroyBlock(p, false);
    int count =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2)).stream()
            .filter(e -> e.getItem().is(Automatons.EMPTY_CORE.get()))
            .mapToInt(e -> e.getItem().getCount())
            .sum();
    h.assertTrue(count == 1, "Break duplicated or lost core");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void magica_orbs_do_not_grant_xp_or_mend(GameTestHelper h) {
    Rig r = rig(h);
    ItemStack axe = new ItemStack(Items.IRON_AXE);
    axe.enchant(Enchantments.MENDING, 1);
    axe.setDamageValue(100);
    r.player.setItemInHand(InteractionHand.MAIN_HAND, axe);
    int xp = r.player.totalExperience;
    MagicaOrb orb = Automatons.MAGICA_ORB.get().create(h.getLevel());
    orb.setValue(7);
    orb.count = 2;
    h.getLevel().addFreshEntity(orb);
    orb.playerTouch(r.player);
    orb.playerTouch(r.player);
    h.assertTrue(Magica.balance(r.player) == 7 && orb.count == 1, "Pickup delay ignored");
    h.assertTrue(
        r.player.totalExperience == xp && axe.getDamageValue() == 100, "Magica altered XP/Mending");
    CompoundTag n = orb.saveWithoutId(new CompoundTag());
    MagicaOrb loaded = Automatons.MAGICA_ORB.get().create(h.getLevel());
    loaded.load(n);
    h.assertTrue(loaded.total() == 7, "Orb save changed value");
    orb.discard();
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void magica_categories_and_inclusive_zero_ranges(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos relative = new BlockPos(10, 1, 10), p = h.absolutePos(relative);
    tree(h, relative);
    h.assertTrue(
        MagicaHarvest.category(h.getLevel(), p, h.getLevel().getBlockState(p)).equals("logs"),
        "Natural log reward missing");
    MagicaHarvest.markPlaced(h.getLevel(), p, h.getLevel().getBlockState(p));
    h.assertTrue(
        MagicaHarvest.category(h.getLevel(), p, h.getLevel().getBlockState(p)).isEmpty(),
        "Construction loop pays");
    h.setBlock(relative, Blocks.DANDELION);
    WorldData.get(h.getLevel()).placedLogs.remove(p.asLong());
    WorldData.get(h.getLevel()).placedPlants.remove(p.asLong());
    h.assertTrue(
        MagicaHarvest.category(h.getLevel(), p, h.getLevel().getBlockState(p)).equals("flowers"),
        "Flower precedence wrong");
    h.assertTrue(
        MagicaHarvest.category(h.getLevel(), p, Blocks.WHEAT.defaultBlockState()).isEmpty(),
        "Immature crop pays");
    h.assertTrue(
        MagicaHarvest.category(h.getLevel(), p, ((CropBlock) Blocks.WHEAT).getStateForAge(7))
            .equals("crops"),
        "Mature crop rejected");
    var random = net.minecraft.util.RandomSource.create(121);
    for (var entry : Balance.YIELDS.entrySet()) {
      Set<Integer> results = new HashSet<>();
      int min = entry.getValue()[0].get(), max = entry.getValue()[1].get();
      for (int i = 0; i < 1000; i++) {
        int n = Balance.roll(entry.getKey(), random);
        h.assertTrue(n >= min && n <= max, "Roll outside range");
        results.add(n);
      }
      h.assertTrue(results.contains(min) && results.contains(max), "Inclusive endpoint missing");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void successful_break_only_one_reward_and_fake_player_exclusion(GameTestHelper h) {
    Rig r = rig(h);
    ServerPlayer real =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(UUID.randomUUID(), "BreakTest"));
    BlockPos p = h.absolutePos(new BlockPos(8, 1, 8)), q = p.east(3);
    h.getLevel().setBlock(p, Blocks.DANDELION.defaultBlockState(), 3);
    h.getLevel().setBlock(q, Blocks.DANDELION.defaultBlockState(), 3);
    BlockEvent.BreakEvent event =
        new BlockEvent.BreakEvent(h.getLevel(), p, h.getLevel().getBlockState(p), real);
    MagicaHarvest.broken(event);
    MagicaHarvest.broken(event);
    h.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    BlockEvent.BreakEvent canceled =
        new BlockEvent.BreakEvent(h.getLevel(), q, h.getLevel().getBlockState(q), real);
    canceled.setCanceled(true);
    MagicaHarvest.broken(canceled);
    MagicaHarvest.broken(
        new BlockEvent.BreakEvent(h.getLevel(), q, h.getLevel().getBlockState(q), r.player));
    h.getLevel().setBlock(q, Blocks.AIR.defaultBlockState(), 3);
    h.runAfterDelay(
        2,
        () -> {
          long value =
              h.getLevel().getEntitiesOfClass(MagicaOrb.class, new AABB(p).inflate(2)).stream()
                  .mapToLong(MagicaOrb::totalThirds)
                  .sum();
          h.assertTrue(value >= 1 && value <= 2, "Duplicate or missing flower reward: " + value);
          h.assertTrue(
              h.getLevel().getEntitiesOfClass(MagicaOrb.class, new AABB(q).inflate(1)).isEmpty(),
              "Canceled/fake break paid");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void assembly_full_inventory_and_concurrent_views(GameTestHelper h) {
    Rig r = rig(h);
    parts(r.home, 1);
    WorkMenu one = new WorkMenu(1, r.player.getInventory(), r.home, null),
        two = new WorkMenu(2, r.player.getInventory(), r.home, null);
    for (int i = 0; i < 36; i++)
      r.player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    one.quickMoveStack(r.player, 6);
    h.assertTrue(
        r.home.ready() && r.home.workers.isEmpty(), "Failed quick move consumed ingredients");
    r.player.getInventory().setItem(0, ItemStack.EMPTY);
    one.quickMoveStack(r.player, 6);
    two.clicked(6, 0, ClickType.PICKUP, r.player);
    h.assertTrue(
        r.player.getInventory().getItem(0).is(Automatons.AUTOMATON_ITEM.get())
            && two.getCarried().isEmpty()
            && r.home.workers.size() == 1,
        "Two viewers duplicated assembly");
    h.assertTrue(Magica.balance(r.player) == 0, "Assembly required Magica");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void packing_identity_health_inventory_and_stale_membership(GameTestHelper h) {
    Rig r = rig(h);
    Lumberton w = worker(h, r, new BlockPos(5, 1, 5));
    w.setNoAi(true);
    w.setHealth(11);
    w.tools.getStackInSlot(0).setDamageValue(75);
    w.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 17));
    UUID id = w.getUUID();
    h.assertTrue(
        WorkerIdentity.pack(w, r.player) && !WorkerIdentity.pack(w, r.player), "Packing repeated");
    List<ItemEntity> drops =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, w.getBoundingBox().inflate(2));
    h.assertTrue(
        drops.stream()
                .filter(e -> e.getItem().is(Items.OAK_LOG))
                .mapToInt(e -> e.getItem().getCount())
                .sum()
            == 17,
        "Cargo duplicated");
    ItemStack packed =
        drops.stream()
            .map(ItemEntity::getItem)
            .filter(i -> i.is(Automatons.AUTOMATON_ITEM.get()))
            .findFirst()
            .orElseThrow()
            .copy();
    CompoundTag data = packed.getTag().getCompound("Automaton");
    h.assertTrue(
        !data.contains("Cargo") && !data.contains("Tools") && data.getFloat("Health") == 11,
        "Packed hidden inventory/heal");
    r.totem.removeMember(id);
    BlockPos floor = h.absolutePos(new BlockPos(8, 0, 8));
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
    r.player.setItemInHand(InteractionHand.MAIN_HAND, packed.copy());
    h.assertTrue(
        packed
            .getItem()
            .useOn(new UseOnContext(r.player, InteractionHand.MAIN_HAND, hit))
            .consumesAction(),
        "Deployment failed");
    Lumberton deployed = (Lumberton) h.getLevel().getEntity(id);
    h.assertTrue(
        deployed != null
            && deployed.getHealth() == 11
            && deployed.cargoEmpty()
            && deployed.activeTool().isEmpty()
            && deployed.totemId == null
            && !deployed.authorized,
        "Deployment lost state/restored stale binding");
    r.player.setItemInHand(InteractionHand.MAIN_HAND, packed.copy());
    h.assertTrue(
        packed.getItem().useOn(new UseOnContext(r.player, InteractionHand.MAIN_HAND, hit))
            == InteractionResult.FAIL,
        "Copied identity deployed twice");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void placement_pause_yields_to_persistent_home_recall(GameTestHelper h) {
    Rig r = rig(h);
    Lumberton w = worker(h, r, new BlockPos(5, 1, 3));
    w.authorized = false;
    r.home.recall(true);
    w.setJob(0);
    h.runAfterDelay(
        50,
        () -> {
          h.assertTrue(
              w.status().equals("Recalled"), "Placement pause blocked home recall: " + w.status());
          h.assertTrue(
              w.alerts == 0 && !w.tools.getStackInSlot(0).isEmpty() && w.activeTool().isEmpty(),
              "Unassigned job lost tools or contributed alerts");
          h.assertTrue(
              !w.tools.insertItem(1, new ItemStack(Items.IRON_AXE), false).isEmpty(),
              "Unassigned tool filter accepts axes");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void compact_fall_preserves_obstacles_and_all_logs(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos base = new BlockPos(17, 1, 14);
    tree(h, base);
    for (Direction d : Direction.Plane.HORIZONTAL) h.setBlock(base.relative(d, 3), Blocks.STONE);
    Lumberton w = worker(h, r, new BlockPos(15, 1, 16));
    w.setNoAi(true);
    var tree = TreeFinder.inspect(h.getLevel(), h.absolutePos(base), r.totem).tree();
    var plan = TreeFinder.landing(h.getLevel(), tree, r.totem, r.player.getUUID(), w);
    h.assertTrue(
        plan != null && plan.segmented(), "Long-line obstruction did not use compact fall");
    Timber timber = Timber.commit(h.getLevel(), tree, plan, w, r.totem);
    h.assertTrue(timber != null && timber.wood.size() == 5, "Conversion lost wood");
    h.assertTrue(
        new HashSet<>(timber.targets).size() == 5, "Fallback stacked logs at one coordinate");
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(timber.progress() >= timber.duration, "Compact timber did not settle");
          for (Direction d : Direction.Plane.HORIZONTAL)
            h.assertTrue(
                h.getBlockState(base.relative(d, 3)).is(Blocks.STONE), "Fall overwrote obstacle");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1200)
  public static void three_shared_workers_take_distinct_nearest_totem_jobs(GameTestHelper h) {
    Rig r = rig(h);
    chest(h, r, new BlockPos(13, 1, 14));
    BlockPos[] roots = {new BlockPos(14, 1, 18), new BlockPos(7, 1, 14), new BlockPos(14, 1, 4)};
    for (BlockPos p : roots) tree(h, p);
    List<Lumberton> workers = new ArrayList<>();
    workers.add(worker(h, r, new BlockPos(14, 1, 7)));
    workers.add(worker(h, r, new BlockPos(12, 1, 12)));
    workers.add(worker(h, r, new BlockPos(16, 1, 12)));
    Set<UUID> claimed = new HashSet<>();
    h.onEachTick(
        () -> {
          for (var entry : WorldData.get(h.getLevel()).reservations.entrySet())
            if (workers.stream().anyMatch(w -> w.getUUID().equals(entry.getValue())))
              claimed.add(entry.getValue());
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              claimed.size() == 3, "Workers did not receive distinct jobs: " + claimed.size());
          h.assertTrue(r.totem.workers.size() == 3, "Shared roster replaced a worker");
          for (Lumberton w : workers)
            h.assertTrue((w.alerts & Lumberton.WOOD) == 0, "Claimed work produced no-wood alert");
        });
  }

  // Two full trees, navigation and an empty-area scan share budgets with the other fixtures.
  @GameTest(template = "empty", timeoutTicks = 3400)
  public static void spare_cargo_continues_between_trees_then_no_wood_unloads(GameTestHelper h) {
    Rig r = rig(h);
    ChestBlockEntity c = chest(h, r, new BlockPos(13, 1, 14));
    BlockPos a = new BlockPos(18, 1, 14), b = new BlockPos(14, 1, 21);
    tree(h, a);
    tree(h, b);
    Lumberton w = worker(h, r, new BlockPos(16, 1, 14));
    boolean[] checked = {false};
    h.onEachTick(
        () -> {
          if (!checked[0] && h.getBlockState(b).isAir()) {
            checked[0] = true;
            h.assertTrue(
                count(c, Items.OAK_LOG) == 0, "Dedicated unload occurred between small trees");
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              checked[0] && count(c, Items.OAK_LOG) == 10,
              "No-wood return did not unload both trees: "
                  + w.diagnostic()
                  + " logs="
                  + count(c, Items.OAK_LOG));
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void runtime_survival_recipes_require_exact_full_cores(GameTestHelper h) {
    Rig r = rig(h);
    WorkMenu menu = new WorkMenu(1, r.player.getInventory(), r.home, null);
    for (var recipe :
        h.getLevel()
            .getRecipeManager()
            .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
      if (!recipe.getId().getNamespace().equals(Automatons.ID)) continue;
      TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
      var ingredients = recipe.getIngredients();
      int width =
          recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped
              ? shaped.getWidth()
              : 3;
      int coreSlot = -1;
      for (int i = 0; i < ingredients.size(); i++) {
        ItemStack[] choices = ingredients.get(i).getItems();
        if (choices.length == 0) continue;
        int slot = (i / width) * 3 + i % width;
        grid.setItem(slot, choices[0].copyWithCount(1));
        if (choices[0].is(Automatons.MAGICA_CORE.get())) coreSlot = slot;
      }
      h.assertTrue(
          recipe.matches(grid, h.getLevel())
              && !recipe.assemble(grid, h.getLevel().registryAccess()).isEmpty(),
          "Uncraftable recipe " + recipe.getId());
      if (coreSlot >= 0) {
        grid.setItem(coreSlot, CoreItem.vessel(49));
        h.assertTrue(
            !recipe.matches(grid, h.getLevel()), "Partial core substituted in " + recipe.getId());
      }
      if (recipe.getId().getPath().endsWith("_2")) {
        int cores = 0, prior = 0;
        for (var ingredient : ingredients)
          for (ItemStack choice : ingredient.getItems()) {
            if (choice.is(Automatons.MAGICA_CORE.get())) cores++;
            if (choice.getItem() instanceof UpgradeItem u && u.tier == 1) prior++;
          }
        h.assertTrue(
            cores == 2 && prior == 1, "Tier II did not consume exactly 2 full cores + predecessor");
      }
    }
    h.assertTrue(
        Inventories.duration(new ItemStack(Items.IRON_AXE), 80, 2, 0) == 60,
        "Speed II does not round to the nearest work interval");
    ItemStack enchanted = new ItemStack(Items.IRON_AXE);
    enchanted.enchant(Enchantments.BLOCK_EFFICIENCY, 2);
    int before = Inventories.duration(enchanted, 80, 0, 0);
    h.assertTrue(Inventories.duration(enchanted, 80, 0, 2) == before, "Efficiency bonuses stacked");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void nearest_totem_order_ignores_worker_and_insertion_order(GameTestHelper h) {
    Rig r = rig(h);
    Lumberton w = worker(h, r, new BlockPos(5, 1, 14));
    w.setNoAi(true);
    w.setOnGround(true);
    BlockPos near = new BlockPos(17, 1, 14), far = new BlockPos(6, 1, 14);
    tree(h, near);
    tree(h, far);
    var a = TreeFinder.inspect(h.getLevel(), h.absolutePos(near), r.totem).tree();
    var b = TreeFinder.inspect(h.getLevel(), h.absolutePos(far), r.totem).tree();
    r.totem.trees.put(b.root().asLong(), b);
    r.totem.trees.put(a.root().asLong(), a);
    r.totem.scanComplete = true;
    h.succeedWhen(
        () -> {
          w.wantsJob = true;
          w.acceptNext(r.totem);
          h.assertTrue(
              w.getUUID().equals(WorldData.get(h.getLevel()).reservations.get(a.root().asLong()))
                  && !WorldData.get(h.getLevel()).reservations.containsKey(b.root().asLong()),
              "Chose worker-nearest/insertion-first tree: "
                  + w.diagnostic()
                  + " deferred="
                  + w.pathDeferred
                  + " near="
                  + a.root()
                  + " reservation="
                  + WorldData.get(h.getLevel()).reservations.get(a.root().asLong())
                  + " station="
                  + (w.station() == r.totem));
        });
  }

  @GameTest(template = "empty", timeoutTicks = 700)
  public static void service_uses_alternatives_partial_stacks_and_restocks(GameTestHelper h) {
    Rig r = rig(h);
    ChestBlockEntity first = chest(h, r, new BlockPos(13, 1, 14)),
        second = chest(h, r, new BlockPos(16, 1, 14));
    for (int i = 0; i < 27; i++) {
      first.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
      second.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    }
    first.setItem(0, new ItemStack(Items.OAK_LOG, 62));
    second.setItem(0, new ItemStack(Items.OAK_LOG, 55));
    second.setItem(1, new ItemStack(Items.IRON_AXE));
    Lumberton w = worker(h, r, new BlockPos(14, 1, 16));
    w.tools.setStackInSlot(0, ItemStack.EMPTY);
    w.cargo.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 10));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              w.cargoEmpty() && count(first, Items.OAK_LOG) + count(second, Items.OAK_LOG) == 127,
              "Did not unload across both partial stacks: " + w.diagnostic());
          h.assertTrue(Inventories.axe(w.activeTool()), "Visit did not restock axe");
          h.assertTrue((w.alerts & Lumberton.TOOLS) == 0, "Resolved tool alert stayed latched");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void legacy_roster_and_inventory_migrate_without_loss(GameTestHelper h) {
    Rig r = rig(h);
    Lumberton w = worker(h, r, new BlockPos(5, 1, 5));
    w.setNoAi(true);
    w.cargo.setStackInSlot(8, new ItemStack(Items.CHERRY_LOG, 47));
    CompoundTag wn = w.saveWithoutId(new CompoundTag());
    wn.remove("Job");
    wn.remove("Authorized");
    wn.remove("TotemDimension");
    Lumberton restored = Automatons.LUMBERTON.get().create(h.getLevel());
    restored.load(wn);
    h.assertTrue(
        restored.job() == 1
            && restored.authorized
            && restored.cargo.getStackInSlot(8).getCount() == 47,
        "Legacy worker reset");
    CompoundTag sn = r.totem.saveWithoutMetadata();
    sn.remove("Workers");
    sn.putUUID("Worker", w.getUUID());
    sn.putUUID("WorkerHome", r.home.identity);
    Station loaded = new Station(r.totem.getBlockPos(), r.totem.getBlockState());
    loaded.load(sn);
    h.assertTrue(loaded.workers.contains(w.getUUID()), "Legacy roster omitted worker");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void waterlogged_roots_preserve_water_and_settle_without_extra_drops(
      GameTestHelper h) {
    Rig r = rig(h);
    BlockPos p = h.absolutePos(new BlockPos(18, 1, 18));
    h.getLevel().setBlock(p, Blocks.MANGROVE_PROPAGULE.defaultBlockState(), 3);
    for (int attempt = 0; attempt < 12; attempt++) {
      var state = h.getLevel().getBlockState(p);
      if (state.getBlock() instanceof SaplingBlock grow)
        grow.advanceTree(
            h.getLevel(), p, state, net.minecraft.util.RandomSource.create(53 + attempt));
    }
    List<BlockPos> wet = new ArrayList<>();
    for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, 0, -6), p.offset(6, 12, 6)))
      if (h.getLevel().getBlockState(q).is(Blocks.MANGROVE_ROOTS)) {
        BlockPos at = q.immutable();
        h.getLevel()
            .setBlock(
                at,
                h.getLevel()
                    .getBlockState(at)
                    .setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties
                            .WATERLOGGED,
                        true),
                18);
        wet.add(at);
      }
    TreeFinder.Tree found = null;
    for (BlockPos q : BlockPos.betweenClosed(p.offset(-6, 0, -6), p.offset(6, 12, 6)))
      if (TreeFinder.isRoot(h.getLevel(), q)) {
        var tree = TreeFinder.inspect(h.getLevel(), q, r.totem).tree();
        if (tree != null) {
          found = tree;
          break;
        }
      }
    h.assertTrue(found != null && !wet.isEmpty(), "No wet mangrove fixture");
    Lumberton w = worker(h, r, new BlockPos(4, 1, 4));
    w.setNoAi(true);
    var landing = TreeFinder.landing(h.getLevel(), found, r.totem, r.player.getUUID(), w);
    h.assertTrue(landing != null, "No grounded wet fall");
    Timber timber = Timber.commit(h.getLevel(), found, landing, w, r.totem);
    h.assertTrue(timber != null, "Wet conversion rejected");
    for (BlockPos at : wet)
      h.assertTrue(
          !h.getLevel().getFluidState(at).isEmpty(),
          "Conversion deleted water at " + at + " state=" + h.getLevel().getBlockState(at));
    h.runAfterDelay(
        45,
        () -> {
          h.assertTrue(
              timber.progress() >= timber.duration, "Retained water blocked a non-mutating fall");
          for (BlockPos at : wet)
            h.assertTrue(
                !h.getLevel().getFluidState(at).isEmpty(),
                "Water changed after settling at "
                    + at
                    + " state="
                    + h.getLevel().getBlockState(at)
                    + " above="
                    + h.getLevel().getBlockState(at.above())
                    + " below="
                    + h.getLevel().getBlockState(at.below()));
          h.assertTrue(
              h.getLevel()
                  .getEntitiesOfClass(
                      ItemEntity.class,
                      new AABB(p).inflate(8),
                      e ->
                          e.getItem().is(Items.MANGROVE_ROOTS) || e.getItem().is(Items.MOSS_CARPET))
                  .isEmpty(),
              "Root cleanup emitted duplicate ordinary drops");
          h.succeed();
        });
  }

  private static void grownSpecies(GameTestHelper h, Block sapling, boolean large) {
    Rig r = rig(h);
    BlockPos base = new BlockPos(18, 1, 18);
    for (int x = 0; x < (large ? 2 : 1); x++)
      for (int z = 0; z < (large ? 2 : 1); z++) h.setBlock(base.offset(x, 0, z), sapling);
    BlockPos absolute = h.absolutePos(base);
    for (int attempt = 0; attempt < 12; attempt++) {
      var state = h.getLevel().getBlockState(absolute);
      if (state.getBlock() instanceof SaplingBlock grow)
        grow.advanceTree(
            h.getLevel(), absolute, state, net.minecraft.util.RandomSource.create(53 + attempt));
    }
    List<TreeFinder.Tree> found = new ArrayList<>();
    for (BlockPos p : BlockPos.betweenClosed(absolute.offset(-6, 0, -6), absolute.offset(6, 12, 6)))
      if (TreeFinder.isRoot(h.getLevel(), p)) {
        var result = TreeFinder.inspect(h.getLevel(), p, r.totem);
        if (result.tree() != null) found.add(result.tree());
      }
    h.assertTrue(!found.isEmpty(), "Grown " + sapling + " has no recognized root");
    h.assertTrue(found.get(0).logs().size() >= 3, "Generated tree lost logs");
    TreeFinder.Tree detected = found.get(0);
    chest(h, r, new BlockPos(13, 1, 14));
    Lumberton w = worker(h, r, new BlockPos(4, 1, 4));
    h.succeedWhen(
        () -> {
          var jobs =
              h.getLevel()
                  .getEntitiesOfClass(
                      Timber.class,
                      new AABB(absolute).inflate(40),
                      t -> r.player.getUUID().equals(t.owner));
          h.assertTrue(
              !jobs.isEmpty(),
              "Generated "
                  + sapling
                  + " did not reach felling: "
                  + w.diagnostic()
                  + " reason="
                  + r.totem.scanReason);
          h.assertTrue(
              jobs.get(0).wood.size() == detected.logs().size(),
              "Generated conversion changed wood count");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_oak(GameTestHelper h) {
    grownSpecies(h, Blocks.OAK_SAPLING, false);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_birch(GameTestHelper h) {
    grownSpecies(h, Blocks.BIRCH_SAPLING, false);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_spruce_two_by_two(GameTestHelper h) {
    grownSpecies(h, Blocks.SPRUCE_SAPLING, true);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_jungle_two_by_two(GameTestHelper h) {
    grownSpecies(h, Blocks.JUNGLE_SAPLING, true);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_dark_oak(GameTestHelper h) {
    grownSpecies(h, Blocks.DARK_OAK_SAPLING, true);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_acacia(GameTestHelper h) {
    grownSpecies(h, Blocks.ACACIA_SAPLING, false);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_mangrove(GameTestHelper h) {
    grownSpecies(h, Blocks.MANGROVE_PROPAGULE, false);
  }

  @GameTest(template = "empty", timeoutTicks = 1600)
  public static void grown_cherry(GameTestHelper h) {
    grownSpecies(h, Blocks.CHERRY_SAPLING, false);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void all_axe_tiers_have_distinct_real_work_intervals(GameTestHelper h) {
    Item[] axes = {
      Items.WOODEN_AXE,
      Items.STONE_AXE,
      Items.IRON_AXE,
      Items.DIAMOND_AXE,
      Items.NETHERITE_AXE,
      Items.GOLDEN_AXE
    };
    int[] prep = {240, 140, 100, 70, 65, 40}, process = {120, 80, 60, 40, 35, 20};
    for (int i = 0; i < axes.length; i++) {
      ItemStack axe = new ItemStack(axes[i]);
      h.assertTrue(Inventories.axe(axe), "Axe rejected: " + axes[i]);
      h.assertTrue(
          Inventories.duration(axe, Balance.PREP.get(), 0, 0) == prep[i],
          "Wrong preparation: " + axes[i]);
      h.assertTrue(
          Inventories.duration(axe, Balance.PROCESS.get(), 0, 0) == process[i],
          "Wrong processing: " + axes[i]);
      h.assertTrue(
          prep[i] % 5 == 0 && process[i] % 5 == 0, "Timing not representable by worker clock");
      h.assertTrue(
          PlayerFelling.eligible(axe) == (i >= 1 && i <= 4),
          "Player tier filter wrong: " + axes[i]);
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void translated_arm_and_leg_recipes(GameTestHelper h) {
    Rig r = rig(h);
    var menu = new WorkMenu(1, r.player.getInventory(), r.home, null);
    var arm =
        (net.minecraft.world.item.crafting.CraftingRecipe)
            h.getLevel().getRecipeManager().byKey(Automatons.id("automaton_arm")).orElseThrow();
    var leg =
        (net.minecraft.world.item.crafting.CraftingRecipe)
            h.getLevel().getRecipeManager().byKey(Automatons.id("automaton_leg")).orElseThrow();
    for (int x = 0; x < 3; x++) {
      for (int y = 0; y < 2; y++) {
        var grid = new TransientCraftingContainer(menu, 3, 3);
        grid.setItem(y * 3 + x, new ItemStack(Items.OAK_LOG));
        grid.setItem((y + 1) * 3 + x, new ItemStack(Items.IRON_INGOT));
        h.assertTrue(arm.matches(grid, h.getLevel()), "Missing translated arm recipe");
      }
      var grid = new TransientCraftingContainer(menu, 3, 3);
      grid.setItem(x, new ItemStack(Items.BIRCH_LOG));
      grid.setItem(x + 3, new ItemStack(Items.BIRCH_PLANKS));
      grid.setItem(x + 6, new ItemStack(Items.IRON_INGOT));
      h.assertTrue(leg.matches(grid, h.getLevel()), "Missing translated leg recipe");
    }
    var horizontal = new TransientCraftingContainer(menu, 3, 3);
    horizontal.setItem(0, new ItemStack(Items.OAK_LOG));
    horizontal.setItem(1, new ItemStack(Items.IRON_INGOT));
    h.assertTrue(!arm.matches(horizontal, h.getLevel()), "Horizontal arm recipe accepted");
    h.succeed();
  }

  private static void harvestWithAxe(GameTestHelper h, Item axe) {
    Rig r = rig(h);
    var c = chest(h, r, new BlockPos(13, 1, 14));
    tree(h, new BlockPos(20, 1, 14));
    var w = worker(h, r, new BlockPos(16, 1, 14));
    w.tools.setStackInSlot(0, new ItemStack(axe));
    boolean[] aimed = {false};
    h.onEachTick(
        () -> {
          if (w.chopTarget() != null) {
            Vec3 delta = Vec3.atCenterOf(w.chopTarget()).subtract(w.getEyePosition());
            float yaw = (float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(w.getYHeadRot() - yaw)) < 5)
              aimed[0] = true;
          }
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(
              count(c, Items.OAK_LOG) == 5,
              "Axe did not complete delivery: " + axe + " " + w.diagnostic());
          h.assertTrue(aimed[0], "Worker never aimed at chopping target");
          h.assertTrue(w.chopTarget() == null, "Chopping overlay persisted after delivery");
          h.assertTrue(
              w.activeTool().is(axe) && w.activeTool().getDamageValue() >= 6,
              "Axe not used or no durability consumed");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void diamond_axe_fells_processes_and_delivers(GameTestHelper h) {
    harvestWithAxe(h, Items.DIAMOND_AXE);
  }

  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void netherite_axe_fells_processes_and_delivers(GameTestHelper h) {
    harvestWithAxe(h, Items.NETHERITE_AXE);
  }

  private static void terraceHarvest(GameTestHelper h, boolean downhill) {
    Rig old = rig(h);
    for (int x = 0; x < 28; x++)
      for (int z = 0; z < 28; z++) {
        int height = downhill ? Math.max(0, Math.min(3, 13 - z)) : Math.max(0, Math.min(3, z - 18));
        for (int y = 1; y <= height; y++) h.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
      }
    Rig r = old;
    if (downhill) {
      h.setBlock(new BlockPos(3, 4, 3), Automatons.PEDESTAL.get());
      h.setBlock(new BlockPos(14, 1, 14), Blocks.AIR);
      h.setBlock(new BlockPos(14, 4, 8), Automatons.TOTEM.get());
      Station home = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(3, 4, 3)));
      Station totem = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(14, 4, 8)));
      home.owner = old.player.getUUID();
      home.registerHome();
      totem.owner = old.player.getUUID();
      r = new Rig(old.player, home, totem);
    }
    var c = chest(h, r, downhill ? new BlockPos(13, 4, 8) : new BlockPos(13, 1, 14));
    tree(h, downhill ? new BlockPos(14, 1, 20) : new BlockPos(14, 4, 23));
    var w = worker(h, r, downhill ? new BlockPos(14, 4, 6) : new BlockPos(14, 1, 12));
    w.tools.setStackInSlot(0, new ItemStack(Items.STONE_AXE));
    double baseY = h.absolutePos(BlockPos.ZERO).getY();
    boolean[] visited = {false};
    h.onEachTick(
        () -> {
          if (downhill ? w.getY() < baseY + 1.2 : w.getY() > baseY + 3.8) visited[0] = true;
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(visited[0], "Worker never traversed terrace: " + w.diagnostic());
          h.assertTrue(
              count(c, Items.OAK_LOG) == 5, "Terrace harvest not delivered: " + w.diagnostic());
          h.assertTrue(
              downhill ? w.getY() > baseY + 3.8 : w.getY() < baseY + 1.2,
              "Worker did not return to storage elevation");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 3400)
  public static void stone_axe_uphill_three_steps_and_return(GameTestHelper h) {
    terraceHarvest(h, false);
  }

  @GameTest(template = "empty", timeoutTicks = 3400)
  public static void stone_axe_downhill_three_steps_and_return(GameTestHelper h) {
    terraceHarvest(h, true);
  }

  @GameTest(template = "empty", timeoutTicks = 2800)
  public static void clears_low_natural_leaves_to_reach_trunk(GameTestHelper h) {
    Rig r = rig(h);
    var c = chest(h, r, new BlockPos(13, 1, 14));
    BlockPos root = new BlockPos(20, 1, 14);
    tree(h, root);
    List<BlockPos> blockers = new ArrayList<>();
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        for (int y = 0; y <= 2; y++) {
          if (x == 0 && z == 0) continue;
          BlockPos p = root.offset(x, y, z);
          blockers.add(p);
          h.setBlock(
              p,
              Blocks.OAK_LEAVES
                  .defaultBlockState()
                  .setValue(LeavesBlock.PERSISTENT, false)
                  .setValue(LeavesBlock.DISTANCE, 1));
        }
    var w = worker(h, r, new BlockPos(16, 1, 14));
    boolean[] clearing = {false};
    h.onEachTick(
        () -> {
          if (w.status().equals("Clearing leaves") && w.chopTarget() != null) clearing[0] = true;
        });
    h.succeedWhen(
        () -> {
          h.assertTrue(clearing[0], "Did not chop blocking leaves: " + w.diagnostic());
          h.assertTrue(
              count(c, Items.OAK_LOG) == 5, "Leaf-covered tree did not deliver: " + w.diagnostic());
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void player_felling_only_completed_break_and_conserves_logs(GameTestHelper h) {
    rig(h);
    BlockPos root = h.absolutePos(new BlockPos(18, 1, 14));
    tree(h, new BlockPos(18, 1, 14));
    ServerPlayer p =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(UUID.randomUUID(), "FellingTest"));
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
    p.setPos(Vec3.atBottomCenterOf(root.west(3)));
    var event = new BlockEvent.BreakEvent(h.getLevel(), root, h.getLevel().getBlockState(root), p);
    event.setCanceled(true);
    PlayerFelling.broken(event);
    event.setCanceled(false);
    PlayerFelling.broken(event);
    h.runAfterDelay(
        2,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(root.above()).is(Blocks.OAK_LOG),
              "Uncompleted/canceled event felled tree");
          PlayerFelling.broken(event);
          PlayerFelling.broken(event);
          h.getLevel().destroyBlock(root, true, p);
        });
    h.runAfterDelay(
        5,
        () -> {
          var jobs =
              h.getLevel()
                  .getEntitiesOfClass(
                      Timber.class, new AABB(root).inflate(10), t -> p.getUUID().equals(t.owner));
          h.assertTrue(
              jobs.size() == 1 && jobs.get(0).wood.size() == 4,
              "Player felling lost/duplicated remaining logs");
          int drops =
              h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(3)).stream()
                  .filter(e -> e.getItem().is(Items.OAK_LOG))
                  .mapToInt(e -> e.getItem().getCount())
                  .sum();
          h.assertTrue(drops == 1, "Base log was not paid exactly once");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void placed_and_stripped_logs_through_grass_and_flowers(GameTestHelper h) {
    Rig r = rig(h);
    var c = chest(h, r, new BlockPos(13, 1, 14));
    for (int x = 15; x <= 22; x++)
      for (int z = 10; z <= 18; z++)
        h.setBlock(new BlockPos(x, 1, z), (x + z) % 2 == 0 ? Blocks.GRASS : Blocks.DANDELION);
    BlockPos a = new BlockPos(20, 1, 13), b = new BlockPos(20, 1, 16);
    h.setBlock(a, Blocks.OAK_LOG);
    h.setBlock(b, Blocks.STRIPPED_BIRCH_LOG);
    MagicaHarvest.markPlaced(h.getLevel(), h.absolutePos(a), h.getBlockState(a));
    MagicaHarvest.markPlaced(h.getLevel(), h.absolutePos(b), h.getBlockState(b));
    var w = worker(h, r, new BlockPos(16, 1, 14));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              count(c, Items.OAK_LOG) == 1 && count(c, Items.STRIPPED_BIRCH_LOG) == 1,
              "Loose placed logs not delivered through vegetation: " + w.diagnostic());
          h.assertTrue(
              h.getBlockState(new BlockPos(17, 1, 10)).is(Blocks.DANDELION),
              "Walk-through vegetation was unnecessarily destroyed");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void loose_log_jobs_respect_range_and_protection(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos at = h.absolutePos(new BlockPos(20, 1, 14));
    h.getLevel().setBlock(at, Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 3);
    var job = TreeFinder.work(h.getLevel(), at, r.totem).tree();
    h.assertTrue(job != null && TreeFinder.loose(job), "Placed/loose log unavailable");
    WorldData.get(h.getLevel())
        .protection
        .put(
            net.minecraft.world.level.ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4),
            r.player.getUUID());
    h.assertTrue(
        !TreeFinder.revalidate(h.getLevel(), job, r.totem, r.player.getUUID()),
        "Protected log became destructive work");
    WorldData.get(h.getLevel()).protection.clear();
    BlockPos far = r.totem.getBlockPos().east(r.totem.lumberRange() + 1);
    h.getLevel().setBlock(far, Blocks.OAK_LOG.defaultBlockState(), 3);
    h.assertTrue(
        TreeFinder.work(h.getLevel(), far, r.totem).tree() == null,
        "Log beyond totem range accepted");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void landing_rejects_duplicates_and_other_timber_reservations(GameTestHelper h) {
    Rig r = rig(h);
    tree(h, new BlockPos(18, 1, 14));
    var w = worker(h, r, new BlockPos(4, 1, 4));
    w.setNoAi(true);
    var tree =
        TreeFinder.inspect(h.getLevel(), h.absolutePos(new BlockPos(18, 1, 14)), r.totem).tree();
    var plan = TreeFinder.landing(h.getLevel(), tree, r.totem, w.owner, w);
    h.assertTrue(
        plan != null && new HashSet<>(plan.targets()).size() == tree.logs().size(),
        "Invalid test layout");
    var stacked =
        new TreeFinder.Landing(
            Collections.nCopies(tree.logs().size(), plan.targets().get(0)),
            plan.sweep(),
            plan.direction(),
            true);
    h.assertTrue(
        Timber.commit(h.getLevel(), tree, stacked, w, r.totem) == null,
        "Duplicate layout committed");
    Timber first = Timber.commit(h.getLevel(), tree, plan, w, r.totem);
    h.assertTrue(first != null, "Unique layout rejected");
    h.assertTrue(
        !TreeFinder.landingStillSafe(h.getLevel(), tree, plan, r.totem, w.owner, w),
        "Existing timber's cells were reused");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void no_space_waits_instead_of_stacking(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos base = new BlockPos(18, 1, 14);
    tree(h, base);
    var w = worker(h, r, new BlockPos(4, 1, 4));
    w.setNoAi(true);
    var tree = TreeFinder.inspect(h.getLevel(), h.absolutePos(base), r.totem).tree();
    for (int dx = -5; dx <= 5; dx++)
      for (int dz = -5; dz <= 5; dz++) {
        if (dx == 0 && dz == 0) continue;
        for (int y = 0; y <= 6; y++) h.setBlock(base.offset(dx, y, dz), Blocks.STONE);
      }
    h.assertTrue(
        TreeFinder.landing(h.getLevel(), tree, r.totem, w.owner, w) == null,
        "Packed logs into unavailable space");
    h.assertTrue(h.getBlockState(base).is(Blocks.OAK_LOG), "Planning removed source log");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 160)
  public static void legacy_stacked_timber_relayouts_without_losing_sections(GameTestHelper h) {
    Rig r = rig(h);
    BlockPos base = new BlockPos(18, 1, 14);
    tree(h, base);
    var w = worker(h, r, new BlockPos(4, 1, 4));
    w.setNoAi(true);
    var tree = TreeFinder.inspect(h.getLevel(), h.absolutePos(base), r.totem).tree();
    var plan = TreeFinder.landing(h.getLevel(), tree, r.totem, w.owner, w);
    Timber original = Timber.commit(h.getLevel(), tree, plan, w, r.totem);
    h.assertTrue(original != null, "Missing fixture");
    CompoundTag saved = original.saveWithoutId(new CompoundTag());
    original.discard();
    ListTag sections = saved.getList("Sections", 10);
    for (Tag tag : sections) ((CompoundTag) tag).putLong("Target", plan.targets().get(0).asLong());
    saved.putBoolean("Segmented", true);
    saved.putInt("Progress", 30);
    Timber restored = Automatons.TIMBER.get().create(h.getLevel());
    restored.load(saved);
    h.getLevel().addFreshEntity(restored);
    h.assertTrue(!restored.layoutReady(), "Legacy overlapping layout was marked ready");
    h.succeedWhen(
        () -> {
          h.assertTrue(
              restored.layoutReady() && restored.progress() >= restored.duration,
              "Legacy layout did not find distinct landing cells");
          h.assertTrue(
              restored.remaining() == 5 && new HashSet<>(restored.targets).size() == 5,
              "Migration duplicated/lost/stacked wood");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void small_magica_exact_thirds_and_saved_balance(GameTestHelper h) {
    Rig r = rig(h);
    int xp = r.player.totalExperience;
    for (int i = 0; i < 3; i++) {
      r.player.getPersistentData().putLong("MagicaPickupAt", 0);
      MagicaOrb orb = Automatons.MAGICA_ORB.get().create(h.getLevel());
      orb.setValue(1);
      orb.setSmall(true);
      h.getLevel().addFreshEntity(orb);
      h.assertTrue(orb.totalThirds() == 1, "Small orb was not one third");
      orb.playerTouch(r.player);
      h.assertTrue(Magica.thirds(r.player) == i + 1, "Fractional pickup rounded away");
    }
    h.assertTrue(
        Magica.balance(r.player) == 1 && Magica.remainder(r.player) == 0,
        "Three thirds did not become one point");
    Magica.addThirds(r.player, 2);
    Ledger copy = Ledger.load(Ledger.get(h.getLevel().getServer()).save(new CompoundTag()));
    h.assertTrue(
        copy.magica.get(r.player.getUUID()) == 1 && copy.magicaThirds.get(r.player.getUUID()) == 2,
        "Saved remainder lost");
    h.assertTrue(
        Magica.spend(r.player, 1) && Magica.thirds(r.player) == 2,
        "Whole-point spending consumed remainder");
    h.assertTrue(!Magica.spend(r.player, 1), "Fraction paid for a whole point");
    h.assertTrue(r.player.totalExperience == xp, "Small Magica changed XP");
    Magica.clear(r.player);
    h.assertTrue(Magica.thirds(r.player) == 0, "Clear retained fraction");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void magica_drop_chances_sizes_and_legacy_orbs(GameTestHelper h) {
    int cropBig = 0, logBig = 0;
    for (int roll = 0; roll < 100; roll++) {
      var crops = MagicaHarvest.drops("crops", 3, roll);
      cropBig += crops.stream().filter(d -> !d.small()).count();
      var logs = MagicaHarvest.drops("logs", 6, roll);
      logBig += logs.stream().filter(d -> !d.small()).count();
      h.assertTrue(
          logs.stream().filter(MagicaHarvest.Drop::small).count() == 2, "Logs need two small orbs");
      h.assertTrue(logs.stream().allMatch(d -> d.largeValue() == 6), "Large log value changed");
      h.assertTrue(
          MagicaHarvest.drops("flowers", 2, roll).get(0).small()
              && MagicaHarvest.drops("grass", 1, roll).get(0).small(),
          "Flowers/grass dropped large orb");
    }
    h.assertTrue(cropBig == 10 && logBig == 40, "Incorrect size probabilities");
    h.assertTrue(MagicaHarvest.drops("grass", 0, 0).isEmpty(), "Zero yield produced value");
    MagicaOrb old = Automatons.MAGICA_ORB.get().create(h.getLevel());
    CompoundTag n = old.saveWithoutId(new CompoundTag());
    n.remove("Small");
    n.putInt("Value", 7);
    old.load(n);
    h.assertTrue(!old.small() && old.totalThirds() == 21, "Old large orb value changed");
    MagicaOrb small = Automatons.MAGICA_ORB.get().create(h.getLevel());
    small.setValue(7);
    small.setSmall(true);
    h.assertTrue(!old.merge(small), "Small/large kinds merged");
    CompoundTag saved = small.saveWithoutId(new CompoundTag());
    MagicaOrb loaded = Automatons.MAGICA_ORB.get().create(h.getLevel());
    loaded.load(saved);
    h.assertTrue(loaded.small() && loaded.totalThirds() == 7, "Small orb save changed value/type");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 2200)
  public static void dropped_logs_collected_and_delivered_inside_totem_only(GameTestHelper h) {
    Rig r = rig(h);
    var c = chest(h, r, new BlockPos(13, 1, 14));
    Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(20, 1, 14)));
    ItemEntity logs =
        new ItemEntity(h.getLevel(), at.x, at.y + .1, at.z, new ItemStack(Items.OAK_LOG, 5));
    logs.setDeltaMovement(Vec3.ZERO);
    h.getLevel().addFreshEntity(logs);
    Vec3 outside = Vec3.atBottomCenterOf(r.totem.getBlockPos().west(r.totem.lumberRange() + 1));
    ItemEntity excluded =
        new ItemEntity(
            h.getLevel(), outside.x, outside.y + .1, outside.z, new ItemStack(Items.BIRCH_LOG, 3));
    excluded.setDeltaMovement(Vec3.ZERO);
    h.getLevel().addFreshEntity(excluded);
    var w = worker(h, r, new BlockPos(16, 1, 14));
    h.succeedWhen(
        () -> {
          h.assertTrue(
              count(c, Items.OAK_LOG) == 5 && logs.isRemoved(),
              "Loose item logs not delivered: " + w.diagnostic());
          h.assertTrue(
              !excluded.isRemoved()
                  && excluded.getItem().getCount() == 3
                  && count(c, Items.BIRCH_LOG) == 0,
              "Collected logs outside range");
        });
  }
}
