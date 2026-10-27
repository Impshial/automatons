package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.*;

/** Fast regression group for the late forestry reach and Collector appearance changes. */
@GameTestHolder("automatons_focus")
@PrefixGameTestTemplate(false)
public final class ForestryFocusedGameTests {
  @GameTest(template = "empty", timeoutTicks = 2400)
  public static void netherite_axe_delivers_harvest(GameTestHelper h) {
    RedesignGameTests.netherite_axe_fells_processes_and_delivers(h);
  }
  @GameTest(template = "empty", timeoutTicks = 2500)
  public static void clears_elevated_fall_obstacles(GameTestHelper h) {
    TimberGameTests.lumberton_clears_fall_obstacles_and_finishes_tree(h);
  }

  @GameTest(template = "empty", timeoutTicks = 3500)
  public static void requests_miner_for_stone_then_fells(GameTestHelper h) {
    TimberGameTests.lumberton_calls_miner_then_finishes_obstructed_tree(h);
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void collector_has_open_basin_and_solid_rim_and_pillar(GameTestHelper h) {
    var state = Luminall.COLLECTOR.get().defaultBlockState();
    var shape = state.getShape(h.getLevel(), BlockPos.ZERO);
    h.assertTrue(
        !Shapes.joinIsNotEmpty(shape, Block.box(0, 0, 0, 3, 9, 3), BooleanOp.AND),
        "Old full-footprint tank body remains");
    h.assertTrue(
        !Shapes.joinIsNotEmpty(shape, Block.box(3, 13, 3, 5, 15, 5), BooleanOp.AND),
        "Basin interior is solid");
    for (var cell :
        new VoxelShape[] {
          Block.box(7, 5, 7, 9, 9, 9), Block.box(0, 13, 5, 1, 15, 7), Block.box(7, 13, 7, 9, 15, 9)
        })
      h.assertTrue(
          !Shapes.joinIsNotEmpty(cell, shape, BooleanOp.ONLY_FIRST),
          "Pillar, rim or bottle stand lacks collision");
    h.succeed();
  }
}
