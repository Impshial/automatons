package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_expansion")
@PrefixGameTestTemplate(false)
public final class ExpansionGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exhaustion_clears_tall_sources_without_healthy_drops(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos base = h.absolutePos(new BlockPos(6, 3, 6));
    level.setBlock(base.below(), Blocks.SAND.defaultBlockState(), 2);
    for (int y = 0; y < 3; y++) level.setBlock(base.above(y), Blocks.CACTUS.defaultBlockState(), 2);
    var reserves = SourceReserves.get(level);
    var cactus = Blocks.CACTUS.defaultBlockState();
    reserves.available(base, cactus, "stems", 1);
    reserves.debit(level, base, cactus, "stems", Balance.PLANT_RESERVE.get() * 2);
    h.assertTrue(level.getBlockState(base).is(DeadVegetation.PLANT.get())
        && level.isEmptyBlock(base.above()) && level.isEmptyBlock(base.above(2)), "Exhausted cactus left unsupported living cells");
    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(2, 4, 2)).isEmpty(), "Exhaustion produced healthy cactus drops");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void supplied_collector_beds_rotate_with_world_aligned_coverage(GameTestHelper h) {
    for (Direction front : Direction.Plane.HORIZONTAL) {
      BuildingProject project = new BuildingProject();
      project.purpose = WorksitePurpose.LUMINA;
      project.anchor = h.absolutePos(new BlockPos(12, 3, 12));
      project.front = front;
      var layout = new SiteLayout(project, h.getLevel());
      h.assertTrue(layout.beds().size() == 12 && layout.plantingCells().size() == 180, "Mage beds lost planting cells");
      var template = project.template(h.getLevel());
      Set<BlockPos> actual = new HashSet<>();
      for (var cell : template.cells)
        if (cell.state().is(Luminall.COLLECTOR.get())) actual.add(template.world(project.anchor, front, cell.local()));
      h.assertTrue(actual.equals(new HashSet<>(layout.collectors())), "Collector rotation no longer covers its authored bed");
      for (BlockPos cell : layout.plantingCells())
        h.assertTrue(actual.stream().filter(c -> LuminaCollection.includes(c, cell, 4)).count() == 1, "Planted bed cell has missing or overlapping collector coverage");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void actual_death_keeps_exact_items_and_only_one_grave(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
    Lumberton worker = Automatons.LUMBERTON.get().create(level);
    worker.owner = UUID.randomUUID(); worker.setNoAi(true);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    ItemStack cargo = new ItemStack(Items.DIAMOND, 13);
    cargo.setHoverName(net.minecraft.network.chat.Component.literal("Exact recovery"));
    worker.cargo.setStackInSlot(0, cargo.copy());
    ItemStack tool = new ItemStack(Items.DIAMOND_AXE); tool.setDamageValue(57);
    worker.tools.setStackInSlot(0, tool.copy());
    level.addFreshEntity(worker);
    worker.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
    h.assertTrue(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "Death did not create ordinary chest at death position");
    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
    h.assertTrue(chest.getContainerSize() == 27 && level.getBlockState(pos.above()).is(Graves.STONE.get()), "Grave shape or capacity incorrect");
    int diamonds = 0, axes = 0, torso = 0, head = 0, arms = 0, legs = 0;
    for (int i = 0; i < 27; i++) {
      ItemStack stack = chest.getItem(i);
      if (stack.is(Items.DIAMOND)) { diamonds += stack.getCount(); h.assertTrue(ItemStack.isSameItemSameTags(stack, cargo), "Cargo NBT lost"); }
      if (stack.is(Items.DIAMOND_AXE)) { axes++; h.assertTrue(ItemStack.isSameItemSameTags(stack, tool), "Tool durability lost"); }
      if (stack.is(Automatons.BODY.get())) torso += stack.getCount();
      if (stack.is(Automatons.HEAD.get())) head += stack.getCount();
      if (stack.is(Automatons.ARM.get())) arms += stack.getCount();
      if (stack.is(Automatons.LEG.get())) legs += stack.getCount();
    }
    h.assertTrue(diamonds == 13 && axes == 1 && torso == 1 && head <= 1 && arms <= 2 && legs <= 2
        && torso + head + arms + legs >= 3 && torso + head + arms + legs <= 6, "Recovery lost inventory or exceeded body part counts");
    CompoundTag before = chest.saveWithFullMetadata();
    worker.dropCustomDeathLoot(level.damageSources().genericKill(), 0, false);
    Graves.get(level).retry(level);
    h.assertTrue(before.equals(chest.saveWithFullMetadata()), "Repeated death callback duplicated recovery");
    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "Death scattered duplicate loot");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void blocked_grave_retains_items_across_reload_and_lava_keeps_location(GameTestHelper h) {
    var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(9, 4, 9));
    for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) level.setBlock(p, Blocks.BEDROCK.defaultBlockState(), 2);
    Lumberton worker = Automatons.GUARDINGTON.get().create(level);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    worker.cargo.setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
    var journal = new Graves(); journal.capture(worker);
    h.assertTrue(journal.pendingCount() == 1 && worker.cargoEmpty(), "Blocked recovery was lost instead of journaled");
    h.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK), "Recovery destroyed bedrock");
    var restored = Graves.load(journal.save(new CompoundTag()));
    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 2);
    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
    restored.retry(level);
    h.assertTrue(restored.pendingCount() == 0 && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "Lava recovery moved away from death position");
    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
    int emeralds = 0, torso = 0;
    for (int i = 0; i < 27; i++) {
      if (chest.getItem(i).is(Items.EMERALD)) emeralds += chest.getItem(i).getCount();
      if (chest.getItem(i).is(Automatons.ARMORED_BODY.get())) torso += chest.getItem(i).getCount();
      h.assertTrue(!chest.getItem(i).is(Automatons.BODY.get()), "Guard recovered ordinary torso");
    }
    h.assertTrue(emeralds == 7 && torso == 1, "Pending grave lost cargo or armored torso");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void loaded_energy_clock_and_whole_bottle_preserve_health(GameTestHelper h) {
    Lumberton worker = Automatons.LUMBERTON.get().create(h.getLevel());
    for (int i = 0; i < 1199; i++) worker.energy.tick();
    Lumberton restored = Automatons.LUMBERTON.get().create(h.getLevel());
    restored.energy.load(worker.energy.save()); restored.energy.tick();
    h.assertTrue(restored.operating() == 199000, "Partial energy interval reset on reload");
    restored.operating(1000); restored.setHealth(7);
    restored.cargo.setStackInSlot(0, LuminallBottle.create(249));
    h.assertTrue(!restored.energy.drink(false), "Worker consumed a partial bottle");
    restored.cargo.setStackInSlot(0, LuminallBottle.create(250));
    for (int i = 0; i < 32; i++) restored.energy.drink(false);
    h.assertTrue(restored.operating() == 126000 && restored.getHealth() == 7
        && restored.cargo.getStackInSlot(0).is(Items.GLASS_BOTTLE), "Bottle did not return glass with exactly 125 Lumina and no healing");
    restored.operating(190000); restored.cargo.setStackInSlot(0, LuminallBottle.create(250));
    for (int i = 0; i < 32; i++) restored.energy.drink(false);
    h.assertTrue(restored.operating() == 200000 && restored.cargo.getStackInSlot(0).is(Items.GLASS_BOTTLE), "Bottle overflow did not consume the whole vessel");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void dead_vegetation_breaks_without_any_loot(GameTestHelper h) {
    var level = h.getLevel();
    int x = 3;
    for (Block block : List.of(DeadVegetation.PLANT.get(), DeadVegetation.CROP.get())) {
      BlockPos local = new BlockPos(x++, 2, 3), pos = h.absolutePos(local);
      h.setBlock(local.below(), block == DeadVegetation.CROP.get() ? Blocks.FARMLAND : Blocks.DIRT);
      h.setBlock(local, block);
      ItemStack shears = new ItemStack(Items.SHEARS);
      ItemStack silk = new ItemStack(Items.DIAMOND_HOE); silk.enchant(Enchantments.SILK_TOUCH, 1);
      ItemStack fortune = new ItemStack(Items.DIAMOND_HOE); fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
      for (ItemStack tool : List.of(ItemStack.EMPTY, shears, silk, fortune))
        h.assertTrue(Block.getDrops(block.defaultBlockState(), level, pos, null, null, tool).isEmpty(), "Dead vegetation yielded loot with " + tool);
      h.assertTrue(LuminaCollection.category(level, pos, block.defaultBlockState()).isEmpty(), "Dead vegetation fed a collector");
      h.assertTrue(LuminaHarvest.category(level, pos, block.defaultBlockState()).isEmpty(), "Dead vegetation produced harvest Lumina");
      h.assertTrue(!(block instanceof BonemealableBlock), "Dead vegetation accepts bone meal");
      level.destroyBlock(pos, true);
      h.assertTrue(level.isEmptyBlock(pos), "Broken dead vegetation remained");
      h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.4)).isEmpty(), "Breaking dead vegetation spawned items");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void worker_clears_dead_crop_without_inventory_or_loot(GameTestHelper h) {
    BlockPos local = new BlockPos(3, 2, 3), pos = h.absolutePos(local);
    h.setBlock(local.below(), Blocks.FARMLAND);
    h.setBlock(local, DeadVegetation.CROP.get());
    Lumberton worker = Automatons.LUMBERTON.get().create(h.getLevel());
    worker.owner = UUID.randomUUID();
    worker.setJob(WorkerJob.FARMINGTON);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
    h.assertTrue(DeadVegetation.clear(worker, pos), "Worker could not clear dead crop");
    h.assertTrue(h.getLevel().isEmptyBlock(pos), "Worker left dead crop behind");
    for (int i = 0; i < worker.cargo.getSlots(); i++) h.assertTrue(worker.cargo.getStackInSlot(i).isEmpty(), "Clearing manufactured an item");
    h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Worker scattered dead crop loot");
    h.setBlock(local, Blocks.WHEAT);
    h.assertTrue(!DeadVegetation.clear(worker, pos), "Dead-clear operation destroyed a living crop");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exhaustion_survives_growth_and_never_exhausts_trees(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos local = new BlockPos(4, 2, 4), pos = h.absolutePos(local);
    h.setBlock(local.below(), Blocks.FARMLAND);
    var mature = ((CropBlock)Blocks.WHEAT).getStateForAge(7);
    level.setBlock(pos, mature, 3);
    var reserves = SourceReserves.get(level);
    reserves.available(pos, mature, "crops", 4);
    reserves.debit(level, pos, mature, "crops", 1);
    int remaining = reserves.remaining(pos);
    var saved = SourceReserves.load(reserves.save(new CompoundTag()));
    var young = ((CropBlock)Blocks.WHEAT).getStateForAge(1);
    level.setBlock(pos, young, 3);
    saved.available(pos, young, "crops", 4);
    h.assertTrue(saved.remaining(pos) == remaining, "Age change or reload replenished crop reserve");
    saved.debit(level, pos, young, "crops", remaining);
    h.assertTrue(level.getBlockState(pos).is(DeadVegetation.CROP.get()), "Exhausted crop did not become dead crop");
    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Exhaustion paid living crop loot");
    level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), 3);
    saved.available(pos, Blocks.OAK_LOG.defaultBlockState(), "logs", 4);
    saved.debit(level, pos, Blocks.OAK_LOG.defaultBlockState(), "logs", 100000);
    h.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG), "Collector exhausted a tree");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void supplied_field_has_three_persistent_ghost_selected_strips(GameTestHelper h) {
    for (Direction front : Direction.Plane.HORIZONTAL) {
      FarmPlan f = new FarmPlan();
      BlockPos center = h.absolutePos(new BlockPos(10, 1, 10));
      f.defineField(h.getLevel(), center, front);
      h.assertTrue(f.cells.size() == 80 && !f.cells.contains(center.asLong()), "Field included irrigation or lost a cell");
      for (int i = 0; i < 3; i++) {
        final int strip = i;
        long count = f.strips.values().stream().filter(s -> s == strip).count();
        h.assertTrue(count == (i == 1 ? 26 : 27), "Incorrect strip geometry");
      }
      ItemStack sample = new ItemStack(Items.BEETROOT, 7);
      h.assertTrue(f.select(0, sample) && sample.getCount() == 7, "Ghost selector consumed sample");
      f.select(1, new ItemStack(Items.CARROT));
      h.assertTrue(!f.select(2, new ItemStack(Items.POISONOUS_POTATO)), "Unsupported sample accepted");
      FarmPlan restored = FarmPlan.load(f.save());
      for (var cell : restored.strips.entrySet()) {
        Item expected = cell.getValue() == 0 ? Items.BEETROOT_SEEDS : cell.getValue() == 1 ? Items.CARROT : Items.AIR;
        h.assertTrue(restored.wanted(h.getLevel(), cell.getKey()) == expected, "Strip selection changed on reload");
      }
    }
    h.succeed();
  }
}
