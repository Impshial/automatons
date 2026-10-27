package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class CurrentRunGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void infuser_collision_matches_rendered_solids(GameTestHelper h) throws Exception {
    var state = Automatons.INFUSER.get().defaultBlockState();
    var rendered = net.minecraft.world.phys.shapes.Shapes.empty();
    try (var input =
        CurrentRunGameTests.class.getResourceAsStream(
            "/assets/automatons/models/block/infusing_pedestal.json")) {
      var model =
          com.google.gson.JsonParser.parseReader(
                  new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonObject();
      for (var value : model.getAsJsonArray("elements")) {
        var box = value.getAsJsonObject();
        var from = box.getAsJsonArray("from");
        var to = box.getAsJsonArray("to");
        rendered =
            net.minecraft.world.phys.shapes.Shapes.or(
                rendered,
                Block.box(
                    from.get(0).getAsDouble(),
                    from.get(1).getAsDouble(),
                    from.get(2).getAsDouble(),
                    to.get(0).getAsDouble(),
                    to.get(1).getAsDouble(),
                    to.get(2).getAsDouble()));
      }
    }
    var collision = state.getCollisionShape(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5)));
    h.assertTrue(
        !net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
            rendered, collision, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),
        "Infuser collision disagrees with visible solids");
    h.succeed();
  }

  private static TransientCraftingContainer grid() {
    return new TransientCraftingContainer(
        new AbstractContainerMenu(null, 0) {
          @Override
          public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int s) {
            return ItemStack.EMPTY;
          }

          @Override
          public boolean stillValid(net.minecraft.world.entity.player.Player p) {
            return true;
          }
        },
        3,
        3);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void fuel_recipe_conservation_and_tag_isolation(GameTestHelper h) {
    var recipes = h.getLevel().getRecipeManager();
    for (var pair :
        List.of(
            new Item[] {Items.COAL, Automatons.COAL_PIECE.get()},
            new Item[] {Items.CHARCOAL, Automatons.CHARCOAL_PIECE.get()})) {
      var input = grid();
      input.setItem(4, new ItemStack(pair[0]));
      var split = recipes.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
      ItemStack result = split.assemble(input, h.getLevel().registryAccess());
      h.assertTrue(
          result.is(pair[1]) && result.getCount() == 8, "Fuel split quantity/item mismatch");
      input.clearContent();
      for (int i = 0; i < 8; i++) input.setItem(i, new ItemStack(pair[1]));
      var combine = recipes.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
      result = combine.assemble(input, h.getLevel().registryAccess());
      h.assertTrue(
          result.is(pair[0]) && result.getCount() == 1 && combine.getIngredients().size() == 8,
          "Recombination loses/gains value");
      for (var type : List.of(RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING))
        h.assertTrue(
            ForgeHooks.getBurnTime(new ItemStack(pair[1]), type) * 8
                == ForgeHooks.getBurnTime(new ItemStack(pair[0]), type),
            "Fuel does not track its parent");
      h.assertTrue(
          !new ItemStack(pair[1]).is(net.minecraft.tags.ItemTags.COALS),
          "Piece is broadly tagged as a full coal");
      input.setItem(
          0,
          new ItemStack(
              pair[1] == Automatons.COAL_PIECE.get()
                  ? Automatons.CHARCOAL_PIECE.get()
                  : Automatons.COAL_PIECE.get()));
      h.assertTrue(
          recipes.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).isEmpty(),
          "Mixed pieces recombined");
      input.clearContent();
      input.setItem(0, new ItemStack(pair[1]));
      input.setItem(3, new ItemStack(Items.STICK));
      h.assertTrue(
          recipes.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).isEmpty(),
          "One fuel piece makes full-coal torches");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void fuel_pieces_smelt_exactly_one_in_each_vanilla_machine(GameTestHelper h) {
    List<AbstractFurnaceBlockEntity> machines = new ArrayList<>();
    Block[] blocks = {Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER};
    Item[] inputs = {Items.COBBLESTONE, Items.RAW_IRON, Items.POTATO};
    Item[] outputs = {Items.STONE, Items.IRON_INGOT, Items.BAKED_POTATO};
    for (int fuel = 0; fuel < 2; fuel++)
      for (int machine = 0; machine < 3; machine++) {
        BlockPos at = new BlockPos(4 + machine * 3, 1, 4 + fuel * 3);
        h.setBlock(at.below(), Blocks.STONE);
        h.setBlock(at, blocks[machine]);
        var be = (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(at));
        be.setItem(0, new ItemStack(inputs[machine], 2));
        be.setItem(
            1,
            new ItemStack(
                fuel == 0 ? Automatons.COAL_PIECE.get() : Automatons.CHARCOAL_PIECE.get()));
        machines.add(be);
      }
    h.runAfterDelay(
        250,
        () -> {
          for (int i = 0; i < machines.size(); i++) {
            var be = machines.get(i);
            h.assertTrue(
                be.getItem(0).getCount() == 1
                    && be.getItem(1).isEmpty()
                    && be.getItem(2).is(outputs[i % 3])
                    && be.getItem(2).getCount() == 1,
                "Actual machine fuel/output mismatch: " + i);
          }
          h.succeed();
        });
  }

  private static void ground(GameTestHelper h) {
    for (int x = 1; x < 31; x++)
      for (int z = 1; z < 31; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
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

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void first_second_third_trunk_cuts_include_stump(GameTestHelper h) {
    fell(h, 0);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void second_trunk_cut_preserves_logs_and_ignores_entities(GameTestHelper h) {
    fell(h, 1);
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void third_trunk_cut_preserves_logs_and_ignores_entities(GameTestHelper h) {
    fell(h, 2);
  }

  private static void fell(GameTestHelper h, int height) {
    ground(h);
    BlockPos local = new BlockPos(16, 1, 16);
    tree(h, local);
    BlockPos root = h.absolutePos(local), cut = root.above(height);
    var player =
        new ServerPlayer(
            h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "UpperCut"));
    ItemStack axe = new ItemStack(Items.STONE_AXE);
    player.setItemInHand(InteractionHand.MAIN_HAND, axe);
    player.setPos(Vec3.atBottomCenterOf(root));
    var found = TreeFinder.atTrunkCut(h.getLevel(), cut, null).tree();
    h.assertTrue(
        found != null && TreeFinder.trunkCut(h.getLevel(), found, cut), "Valid trunk cut rejected");
    var plan = TreeFinder.landing(h.getLevel(), found, null, player.getUUID(), player);
    h.assertTrue(plan != null, "No landing on clear ground");
    List<Lumberton> occupants = new ArrayList<>();
    for (BlockPos target : plan.targets()) {
      var occupant = Automatons.LUMBERTON.get().create(h.getLevel());
      occupant.setNoAi(true);
      occupant.setNoGravity(true);
      occupant.setInvulnerable(true);
      occupant.setPos(Vec3.atBottomCenterOf(target));
      h.getLevel().addFreshEntity(occupant);
      occupants.add(occupant);
    }
    h.assertTrue(
        TreeFinder.landingStillSafe(h.getLevel(), found, plan, null, player.getUUID(), player),
        "Entity occupancy vetoed initiation");
    var event =
        new BlockEvent.BreakEvent(h.getLevel(), cut, h.getLevel().getBlockState(cut), player);
    PlayerFelling.broken(event);
    PlayerFelling.broken(event);
    axe.mineBlock(h.getLevel(), h.getLevel().getBlockState(cut), cut, player);
    h.getLevel().destroyBlock(cut, true, player);
    Timber[] job = {null};
    h.runAfterDelay(
        3,
        () -> {
          var jobs =
              h.getLevel()
                  .getEntitiesOfClass(
                      Timber.class,
                      new AABB(root).inflate(12),
                      t -> player.getUUID().equals(t.owner));
          h.assertTrue(
              jobs.size() == 1 && jobs.get(0).wood.size() == 4, "Remaining tree lost/duplicated");
          job[0] = jobs.get(0);
          h.assertTrue(h.getLevel().getBlockState(root).isAir(), "Upper cut left a stump");
          h.assertTrue(axe.getDamageValue() == 1, "Initiating axe wear duplicated");
        });
    h.runAfterDelay(
        12,
        () -> {
          h.assertTrue(job[0] != null && job[0].progress() >= 8, "Entities paused falling timber");
          for (int i = 0; i < occupants.size(); i++)
            occupants
                .get(i)
                .setPos(Vec3.atBottomCenterOf(job[0].targets.get(i % job[0].targets.size())));
        });
    h.runAfterDelay(
        75,
        () -> {
          h.assertTrue(job[0] != null && job[0].isRemoved(), "Entities delayed landing");
          for (BlockPos target : job[0].targets) {
            h.assertTrue(
                h.getLevel().getBlockState(target).is(Blocks.OAK_LOG), "Missing landed log");
            h.getLevel().destroyBlock(target, true, player);
          }
          int logs =
              h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(14)).stream()
                  .filter(e -> e.getItem().is(Items.OAK_LOG))
                  .mapToInt(e -> e.getItem().getCount())
                  .sum();
          h.assertTrue(
              logs == 5 && axe.getDamageValue() == 1,
              "Cut/landing duplicated drops or wear: " + logs);
          occupants.forEach(e -> e.discard());
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void fourth_level_branch_and_placed_logs_are_not_trunk_cuts(GameTestHelper h) {
    ground(h);
    BlockPos local = new BlockPos(16, 1, 16);
    tree(h, local);
    BlockPos root = h.absolutePos(local);
    h.assertTrue(
        TreeFinder.atTrunkCut(h.getLevel(), root.above(3), null).tree() == null,
        "Fourth trunk level fells the tree");
    h.setBlock(local.offset(1, 2, 0), Blocks.OAK_LOG);
    h.assertTrue(
        TreeFinder.atTrunkCut(h.getLevel(), root.offset(1, 2, 0), null).tree() == null,
        "Side branch counted as rooted trunk");
    WorldData.get(h.getLevel()).placedLogs.add(root.asLong());
    h.assertTrue(
        TreeFinder.atTrunkCut(h.getLevel(), root, null).tree() == null,
        "Placed building log became a natural tree");
    h.succeed();
  }
}
