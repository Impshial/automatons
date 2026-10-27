package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class LuminallGameTests {
  private static Reservoir machine(GameTestHelper h, BlockPos local, boolean collector) {
    h.setBlock(local, collector ? Luminall.COLLECTOR.get() : Luminall.TANK.get());
    return (Reservoir) h.getLevel().getBlockEntity(h.absolutePos(local));
  }

  private static ServerPlayer player(GameTestHelper h, BlockPos pos) {
    var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "LuminallTest"));
    p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    p.moveTo(Vec3.atBottomCenterOf(pos));
    return p;
  }

  private static void plant(GameTestHelper h, BlockPos local, Block block) {
    h.setBlock(local.below(), Blocks.GRASS_BLOCK);
    h.setBlock(local, block);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void collector_exact_footprint_and_planted_producers(GameTestHelper h) {
    var origin = new BlockPos(8, 5, 8);
    var machine = machine(h, origin, true);
    plant(h, origin.offset(-1, 0, -1), Blocks.DANDELION);
    plant(h, origin.offset(2, 0, 2), Blocks.POPPY);
    plant(h, origin.offset(3, 0, 0), Blocks.ALLIUM);
    plant(h, origin.offset(0, 5, 0), Blocks.POPPY);
    LuminaHarvest.markPlaced(
        h.getLevel(),
        h.absolutePos(origin.offset(-1, 0, -1)),
        Blocks.DANDELION.defaultBlockState());
    LuminaCollection.collect(h.getLevel(), List.of(machine), 0);
    h.assertTrue(
        machine.tank.getFluidAmount() == 4,
        "Scan included outside boundary or excluded planted flower");
    var box = LuminaCollection.bounds(machine.getBlockPos(), 4);
    h.assertTrue(
        box.getXsize() == 4 && box.getZsize() == 4 && box.getYsize() == 9,
        "Wrong displayed footprint");
    for (int x = -3; x <= 4; x++)
      for (int z = -3; z <= 4; z++)
        for (int y = -5; y <= 5; y++) {
          var p = machine.getBlockPos().offset(x, y, z);
          h.assertTrue(
              box.contains(Vec3.atCenterOf(p))
                  == LuminaCollection.includes(machine.getBlockPos(), p, 4),
              "Display differs from scan");
        }
    h.assertTrue(
        h.getBlockState(origin.offset(-1, 0, -1)).is(Blocks.DANDELION),
        "Collection consumed plant");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void collector_shared_output_full_tanks_and_rate_cap(GameTestHelper h) {
    var a = machine(h, new BlockPos(8, 1, 8), true);
    var b = machine(h, new BlockPos(9, 1, 8), true);
    plant(h, new BlockPos(8, 1, 9), Blocks.POPPY);
    LuminaCollection.collect(h.getLevel(), List.of(a, b), 0);
    h.assertTrue(
        a.tank.getFluidAmount() == 1 && b.tank.getFluidAmount() == 1,
        "Overlap multiplied or failed to share output");
    a.tank.fill(new FluidStack(Luminall.SOURCE.get(), a.capacity()), FluidAction.EXECUTE);
    int before = b.tank.getFluidAmount();
    LuminaCollection.collect(h.getLevel(), List.of(a, b), 1);
    h.assertTrue(b.tank.getFluidAmount() == before + 2, "Full collector monopolized producer");
    for (int x = -1; x <= 2; x++)
      for (int z = -1; z <= 2; z++) plant(h, new BlockPos(8 + x, 3, 8 + z), Blocks.POPPY);
    b.tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
    LuminaCollection.collect(h.getLevel(), List.of(b), 2);
    h.assertTrue(
        b.tank.getFluidAmount() == Balance.COLLECTOR_MAX.get(),
        "Collector exceeded/did not reach configured cap");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void collector_maturity_tree_and_building_recognition(GameTestHelper h) {
    var p = h.absolutePos(new BlockPos(8, 1, 8));
    h.assertTrue(
        LuminaCollection.category(h.getLevel(), p, Blocks.WHEAT.defaultBlockState()).isEmpty(),
        "Immature crop paid");
    h.assertTrue(
        LuminaCollection.category(h.getLevel(), p, ((CropBlock) Blocks.WHEAT).getStateForAge(7))
            .equals("crops"),
        "Mature crop missing");
    for (Block block : List.of(Blocks.MELON, Blocks.PUMPKIN))
      h.assertTrue(
          LuminaCollection.category(h.getLevel(), p, block.defaultBlockState()).equals("fruit"),
          "Fruit missing");
    h.setBlock(new BlockPos(8, 0, 8), Blocks.DIRT);
    for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(8, y, 8), Blocks.OAK_LOG);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        if (x != 0 || z != 0)
          h.setBlock(
              new BlockPos(8 + x, 4, 8 + z),
              Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
    h.assertTrue(
        LuminaCollection.category(h.getLevel(), p, Blocks.OAK_LOG.defaultBlockState())
            .equals("logs"),
        "Living grown tree missing");
    LuminaHarvest.markPlaced(h.getLevel(), p, Blocks.OAK_LOG.defaultBlockState());
    h.assertTrue(
        LuminaCollection.category(h.getLevel(), p, Blocks.OAK_LOG.defaultBlockState()).isEmpty(),
        "Structural wood generates fluid");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void bottles_partial_filling_retrieval_persistence_and_break(GameTestHelper h) {
    var m = machine(h, new BlockPos(8, 1, 8), true);
    var p = player(h, m.getBlockPos().south());
    var hit =
        new BlockHitResult(Vec3.atCenterOf(m.getBlockPos()), Direction.UP, m.getBlockPos(), false);
    m.tank.fill(new FluidStack(Luminall.SOURCE.get(), 400), FluidAction.EXECUTE);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
    m.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    for (int i = 0; i < 20; i++) m.fillBottle();
    h.assertTrue(
        p.getMainHandItem().getCount() == 1
            && LuminallBottle.amount(m.bottle) == 100
            && m.tank.getFluidAmount() == 300,
        "Bottle placement/fill accounting");
    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    m.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        LuminallBottle.amount(p.getMainHandItem()) == 100 && m.bottle.isEmpty(),
        "Partial retrieval loses fluid");
    m.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    var saved = m.saveWithoutMetadata();
    var restored = new Reservoir(m.getBlockPos(), m.getBlockState());
    restored.load(saved);
    h.assertTrue(
        restored.tank.getFluidAmount() == 300 && LuminallBottle.amount(restored.bottle) == 100,
        "Reload loses reservoir or partial bottle");
    h.getLevel().destroyBlock(m.getBlockPos(), true);
    var drops =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(m.getBlockPos()).inflate(1));
    var packed = drops.stream().filter(e -> e.getItem().is(Luminall.COLLECTOR_ITEM.get())).toList();
    h.assertTrue(packed.size() == 1, "Breaking duplicated/lost collector item");
    var data = BlockItem.getBlockEntityData(packed.get(0).getItem());
    restored.load(data);
    h.assertTrue(
        restored.tank.getFluidAmount() == 300 && LuminallBottle.amount(restored.bottle) == 100,
        "Break lost fluid or bottle");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void full_bottle_drinking_ignores_hunger_and_obeys_bank_capacity(GameTestHelper h) {
    var p = player(h, h.absolutePos(new BlockPos(8, 1, 8)));
    p.getFoodData().setFoodLevel(20);
    var full = LuminallBottle.create(Balance.BOTTLE_VOLUME.get());
    p.setItemInHand(InteractionHand.MAIN_HAND, full);
    h.assertTrue(
        full.use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(),
        "Full hunger prevented drinking");
    var result = full.finishUsingItem(h.getLevel(), p);
    h.assertTrue(
        result.is(Items.GLASS_BOTTLE) && Lumina.balance(p) == Balance.BOTTLE_LUMINA.get(),
        "Drink reward/empty bottle mismatch");
    var partial = LuminallBottle.create(100);
    p.setItemInHand(InteractionHand.MAIN_HAND, partial);
    h.assertTrue(
        partial.use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(),
        "Partial bottle cannot be drunk");
    h.assertTrue(
        partial.finishUsingItem(h.getLevel(), p).is(Items.GLASS_BOTTLE),
        "Partial drink did not empty bottle");
    h.assertTrue(
        Lumina.thirds(p)
            == Balance.BOTTLE_LUMINA.get() * 3
                + 100 * Balance.BOTTLE_LUMINA.get() * 3 / Balance.BOTTLE_VOLUME.get(),
        "Partial bottle reward not proportional");
    Lumina.add(p, Integer.MAX_VALUE);
    p.setItemInHand(InteractionHand.MAIN_HAND, full);
    h.assertTrue(
        !full.use(h.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction()
            && full.finishUsingItem(h.getLevel(), p) == full,
        "Full bank consumed bottle");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void bucket_pipe_bottle_accounting_and_capability_sides(GameTestHelper h) {
    var m = machine(h, new BlockPos(8, 1, 8), true);
    h.assertTrue(
        !m.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(),
        "Top exposes extraction port");
    var pipe =
        m.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN)
            .orElseThrow(IllegalStateException::new);
    h.assertTrue(
        pipe.fill(new FluidStack(Luminall.SOURCE.get(), 1000), FluidAction.EXECUTE) == 0,
        "Collector accepts pipe input");
    m.tank.fill(new FluidStack(Luminall.SOURCE.get(), 1100), FluidAction.EXECUTE);
    h.assertTrue(
        pipe.drain(500, FluidAction.SIMULATE).getAmount() == 500 && m.tank.getFluidAmount() == 1100,
        "Simulated drain changed fluid");
    var p = player(h, m.getBlockPos().south());
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
    h.assertTrue(
        FluidUtil.interactWithFluidHandler(
            p, InteractionHand.MAIN_HAND, h.getLevel(), m.getBlockPos(), Direction.NORTH),
        "Bucket fill failed");
    h.assertTrue(
        p.getMainHandItem().is(Luminall.BUCKET.get()) && m.tank.getFluidAmount() == 100,
        "Bucket not exactly 1000 mB");
    h.assertTrue(pipe.drain(80, FluidAction.EXECUTE).getAmount() == 80, "Pipe drain failed");
    m.bottle = new ItemStack(Items.GLASS_BOTTLE);
    for (int i = 0; i < 8; i++) m.fillBottle();
    h.assertTrue(
        LuminallBottle.amount(m.bottle) == 20 && pipe.drain(100, FluidAction.EXECUTE).isEmpty(),
        "Competing extraction duplicated/lost fluid");
    var bottleCap = FluidUtil.getFluidHandler(m.bottle).orElseThrow(IllegalStateException::new);
    h.assertTrue(
        bottleCap.drain(7, FluidAction.EXECUTE).getAmount() == 7
            && LuminallBottle.amount(m.bottle) == 13,
        "Bottle fluid capability accounting");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void luminall_bucket_placement_pickup_and_no_infinite_sources(GameTestHelper h) {
    var p = player(h, h.absolutePos(new BlockPos(8, 2, 8)));
    for (int x = 6; x <= 10; x++)
      for (int z = 6; z <= 10; z++) {
        h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        if (z != 8 || x == 6 || x == 10) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
      }
    var at = h.absolutePos(new BlockPos(8, 1, 8));
    var bucket = (BucketItem) Luminall.BUCKET.get();
    h.assertTrue(bucket.emptyContents(p, h.getLevel(), at, null), "Bucket placement failed");
    var picked =
        Luminall.LIQUID.get().pickupBlock(h.getLevel(), at, h.getLevel().getBlockState(at));
    h.assertTrue(
        picked.is(Luminall.BUCKET.get()) && h.getLevel().getFluidState(at).isEmpty(),
        "Source pickup duplicated/lost bucket");
    bucket.emptyContents(p, h.getLevel(), at.west(), null);
    bucket.emptyContents(p, h.getLevel(), at.east(), null);
    // Seed flowing fluid in the enclosed channel. LiquidBlock's pickup contract
    // requires its own block state, not an unrelated air state.
    h.getLevel()
        .setBlock(at, Luminall.LIQUID.get().defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 3);
    h.runAfterDelay(
        40,
        () -> {
          h.assertTrue(
              !h.getLevel().getFluidState(at).isSource(),
              "Luminall regenerated an infinite source");
          h.assertTrue(
              h.getLevel().getBlockState(at).is(Luminall.LIQUID.get()),
              "Enclosed flowing fluid disappeared");
          h.assertTrue(
              Luminall.LIQUID
                  .get()
                  .pickupBlock(h.getLevel(), at, h.getLevel().getBlockState(at))
                  .isEmpty(),
              "Flowing fluid yields a full bucket");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 260)
  public static void collector_runs_on_server_ticks_and_resumes_after_extraction(GameTestHelper h) {
    var m = machine(h, new BlockPos(8, 1, 8), true);
    plant(h, new BlockPos(9, 1, 8), Blocks.POPPY);
    int interval = Balance.COLLECTOR_INTERVAL.get();
    h.runAfterDelay(
        interval + 5,
        () -> {
          h.assertTrue(
              m.tank.getFluidAmount() > 0,
              "Loaded Collector did not run through the level tick event");
          m.tank.fill(new FluidStack(Luminall.SOURCE.get(), m.capacity()), FluidAction.EXECUTE);
          m.tank.drain(10, FluidAction.EXECUTE);
          int before = m.tank.getFluidAmount();
          h.runAfterDelay(
              interval + 5,
              () -> {
                h.assertTrue(
                    m.tank.getFluidAmount() > before && m.tank.getFluidAmount() <= m.capacity(),
                    "Collector failed to resume after extraction");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void tank_accepts_standard_fluids_and_retains_overcapacity_contents(
      GameTestHelper h) {
    var tank = machine(h, new BlockPos(8, 1, 8), false);
    var port =
        tank.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)
            .orElseThrow(IllegalStateException::new);
    h.assertTrue(
        port.fill(
                new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
                FluidAction.EXECUTE)
            == 1000,
        "Tank rejects ordinary Forge fluid");
    h.assertTrue(
        port.fill(new FluidStack(Luminall.SOURCE.get(), 1000), FluidAction.EXECUTE) == 0,
        "Tank mixed fluids");
    port.drain(1000, FluidAction.EXECUTE);
    port.fill(new FluidStack(Luminall.SOURCE.get(), 1500), FluidAction.EXECUTE);
    var data = tank.saveWithoutMetadata();
    data.getCompound("Tank").putInt("Amount", tank.capacity() + 500);
    tank.load(data);
    h.assertTrue(
        port.fill(new FluidStack(Luminall.SOURCE.get(), 1000), FluidAction.EXECUTE) == 0
            && tank.tank.getFluidAmount() == tank.capacity() + 500,
        "Reduced capacity destroyed fluid");
    h.assertTrue(
        port.drain(Integer.MAX_VALUE, FluidAction.EXECUTE).getAmount() == tank.capacity() + 500,
        "Overflow not recoverable");
    h.succeed();
  }

  private static CraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, -1) {
          public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int slot) {
            return ItemStack.EMPTY;
          }

          public boolean stillValid(net.minecraft.world.entity.player.Player p) {
            return true;
          }
        },
        3,
        3);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exact_tank_collector_recipes_and_full_tank_crafting_rejection(
      GameTestHelper h) {
    var grid = grid();
    for (int i = 0; i < 9; i++)
      grid.setItem(i, new ItemStack(i == 4 ? Automatons.EMPTY_CORE.get() : Items.GLASS));
    var recipes = h.getLevel().getRecipeManager();
    var tank = recipes.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).orElseThrow();
    h.assertTrue(
        tank.assemble(grid, h.getLevel().registryAccess()).is(Luminall.TANK_ITEM.get()),
        "Tank recipe mismatch");
    for (int i = 0; i < 9; i++)
      grid.setItem(
          i,
          new ItemStack(
              i == 4
                  ? Luminall.TANK_ITEM.get()
                  : i % 2 == 0 ? Items.STONE : Automatons.LUMINA_CORE.get()));
    var collector = recipes.getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).orElseThrow();
    h.assertTrue(
        collector.assemble(grid, h.getLevel().registryAccess()).is(Luminall.COLLECTOR_ITEM.get()),
        "Collector recipe does not use four infused cores and Tank");
    var data = new CompoundTag();
    data.put("Tank", new FluidStack(Luminall.SOURCE.get(), 500).writeToNBT(new CompoundTag()));
    BlockItem.setBlockEntityData(grid.getItem(4), Luminall.TANK_BE.get(), data);
    h.assertTrue(!collector.matches(grid, h.getLevel()), "Crafting silently destroys stored fluid");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void infused_shards_never_auto_retrieve_to_offhand_and_float_higher(
      GameTestHelper h) {
    h.setBlock(new BlockPos(8, 1, 8), Automatons.INFUSER.get());
    var inf = (Infuser) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(8, 1, 8)));
    var p = player(h, inf.getBlockPos().south());
    inf.shards[0] = new ItemStack(Automatons.INFUSED_LUMITE.get());
    inf.donors[0] = p.getUUID();
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Automatons.INFUSED_LUMITE.get()));
    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    var hit = new BlockHitResult(inf.shardPosition(0), Direction.UP, inf.getBlockPos(), false);
    inf.getBlockState().use(h.getLevel(), p, InteractionHand.OFF_HAND, hit);
    h.assertTrue(
        p.getOffhandItem().isEmpty() && !inf.shards[0].isEmpty(),
        "Automatic offhand retrieval persists");
    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    inf.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        p.getMainHandItem().is(Automatons.INFUSED_LUMITE.get()) && inf.shards[0].isEmpty(),
        "Main-hand retrieval broke");
    h.assertTrue(
        Math.abs(Infuser.offset(0).y - (1.17 + 3.0 / 16)) < .00001,
        "Shard height not raised three pixels");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void deepslate_lumite_separate_range_peak_and_replacement(GameTestHelper h) {
    var config = LumiteFeature.configured(h.getLevel().registryAccess(), true);
    var stone = LumiteFeature.configured(h.getLevel().registryAccess(), false);
    h.assertTrue(
        config.minimum() == -64
            && config.maximum() == 0
            && (config.minimum() + config.maximum()) / 2 == -32,
        "Deepslate range/peak incorrect");
    h.assertTrue(
        config.ore().targetStates.size() == 1 && stone.ore().targetStates.size() == 1,
        "Shared variant distribution remains");
    int[] bands = new int[4];
    for (int i = 0; i < 1024; i++)
      for (var p : LumiteFeature.positions(4217, new ChunkPos(i % 32, i / 32), config)) {
        h.assertTrue(p.getY() >= -64 && p.getY() <= 0, "Deepslate ore escapes requested range");
        bands[Math.min(3, (p.getY() + 64) / 16)]++;
      }
    h.assertTrue(bands[1] + bands[2] > bands[0] + bands[3], "Deepslate density fails to favor -32");
    h.assertTrue(
        LumiteFeature.replacement(Blocks.STONE.defaultBlockState(), config, 0, BlockPos.ZERO)
            == null,
        "Deepslate feature replaces stone");
    h.assertTrue(
        LumiteFeature.replacement(Blocks.DEEPSLATE.defaultBlockState(), stone, 0, BlockPos.ZERO)
            == null,
        "Old feature still generates deepslate");
    h.succeed();
  }
}
