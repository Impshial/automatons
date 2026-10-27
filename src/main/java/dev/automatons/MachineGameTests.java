package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_machine")
@PrefixGameTestTemplate(false)
public final class MachineGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void hand_refueling_drains_partial_bottles_and_carried_tanks(GameTestHelper h) {
    Machine m = machine(h, 0);
    m.fuel = 0;
    var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(m.owner, "FuelOwner"));
    p.setPos(m.getX() + 2, m.getY(), m.getZ());
    var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
    p.setItemInHand(hand, LuminallBottle.create(125));
    m.interactAt(p, new net.minecraft.world.phys.Vec3(0, 1, -1), hand);
    h.assertTrue(
        p.getMainHandItem().is(Items.GLASS_BOTTLE)
            && Math.abs(m.fuel + m.fuelFraction - 187.5) < 1e-7,
        "Half bottle failed");
    p.setItemInHand(hand, LuminallBottle.create(125));
    m.interact(p, hand);
    h.assertTrue(m.fuel == 375 && m.fuelFraction < 1e-7, "Two halves did not conserve fuel");
    ItemStack tank = new ItemStack(Luminall.TANK_ITEM.get());
    var cap =
        tank.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
            .orElseThrow(IllegalStateException::new);
    cap.fill(
        new net.minecraftforge.fluids.FluidStack(Luminall.SOURCE.get(), 8000),
        net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    p.setItemInHand(hand, tank);
    m.interact(p, hand);
    var remaining =
        p.getMainHandItem()
            .getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM)
            .orElseThrow(IllegalStateException::new);
    h.assertTrue(
        p.getMainHandItem().is(Luminall.TANK_ITEM.get())
            && remaining.getFluidInTank(0).isEmpty()
            && m.fuel == 12375,
        "Bulk Tank did not drain in one interaction");
    h.assertTrue(m.engine == Machine.Engine.OFF, "Refueling auto-started engine");
    p.setItemInHand(hand, new ItemStack(Luminall.BUCKET.get()));
    m.interactAt(p, new net.minecraft.world.phys.Vec3(0, 1, -1.49), hand);
    h.assertTrue(
        p.getMainHandItem().is(Items.BUCKET) && m.fuel == 13875,
        "Right-click bucket did not empty into engine");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void partial_refueling_preserves_overflow_and_fraction_on_reload(GameTestHelper h) {
    Machine m = machine(h, 0);
    m.fuel = MachineRules.FUEL_THIRDS - 2;
    ItemStack result = m.refuelContainer(LuminallBottle.create(125));
    h.assertTrue(
        LuminallBottle.amount(result) == 124
            && m.fuel == MachineRules.FUEL_THIRDS - 1
            && Math.abs(m.fuelFraction - .5) < 1e-7,
        "Capacity rounding lost fluid");
    CompoundTag saved = m.saveWithoutId(new CompoundTag());
    Machine restored = MachineParts.ENTITY.get().create(h.getLevel());
    restored.load(saved);
    h.assertTrue(Math.abs(restored.fuelFraction - .5) < 1e-7, "Fractional fuel lost on reload");
    var stranger =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "FuelStranger"));
    stranger.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND, LuminallBottle.create(125));
    m.interact(stranger, net.minecraft.world.InteractionHand.MAIN_HAND);
    h.assertTrue(
        LuminallBottle.amount(stranger.getMainHandItem()) == 125,
        "Unauthorized interaction drained bottle");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void drinking_partial_bottles_empties_creative_vessels_and_conserves_energy(
      GameTestHelper h) {
    var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PartialDrink"));
    p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
    Lumina.clear(p);
    for (int i = 0; i < 250; i++) {
      var bottle = LuminallBottle.create(1);
      h.assertTrue(
          bottle.finishUsingItem(h.getLevel(), p).is(Items.GLASS_BOTTLE),
          "Creative drink kept full bottle");
    }
    h.assertTrue(Lumina.thirds(p) == 375, "Partial drinks duplicated/lost energy");
    var ledger = Ledger.get(p.server);
    var restored = Ledger.load(ledger.save(new CompoundTag()));
    h.assertTrue(
        restored.bottleFractions.getOrDefault(p.getUUID(), 0d) < 1e-7,
        "Saved drink remainder invalid");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void operator_release_reconciles_both_unloaded_sides(GameTestHelper h) {
    Machine m = machine(h, 0);
    var w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.setNoAi(true);
    w.owner = m.owner;
    w.home = m.home;
    w.homeId = m.homeId;
    w.setJob(6);
    w.setPos(m.getX() + 5, m.getY(), m.getZ());
    h.getLevel().addFreshEntity(w);
    MachineOperator.assign(m, w);
    CompoundTag worker = w.saveWithoutId(new CompoundTag());
    w.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
    MachineOperator.release(m);
    var restored = Automatons.LUMBERTON.get().create(h.getLevel());
    restored.load(worker);
    h.getLevel().addFreshEntity(restored);
    MachineOperator.tick(restored);
    h.assertTrue(
        restored.machineId == null && m.operator == null,
        "Unloaded worker retained released assignment");
    MachineOperator.assign(m, restored);
    CompoundTag machine = m.saveWithoutId(new CompoundTag());
    m.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
    restored.setJob(1);
    Machine loaded = MachineParts.ENTITY.get().create(h.getLevel());
    loaded.load(machine);
    h.getLevel().addFreshEntity(loaded);
    MachineOperator.validate(loaded);
    h.assertTrue(
        loaded.operator == null && restored.machineId == null,
        "Unloaded Machine retained operator after profession change");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 180)
  public static void last_fuel_finishes_paid_step_and_refueling_preserves_resume(GameTestHelper h) {
    Machine m = machine(h, 0);
    BlockPos start = m.grid;
    m.fuel = MachineRules.MOVE_COST;
    program(m, "S3");
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              m.grid.equals(start.south()) && m.fuel == 0 && !m.job.active && m.job.remaining == 2,
              "Last fuel lost a paid step or remaining distance");
          m.fuel = 60;
          m.startEngine();
          h.runAfterDelay(
              40,
              () -> {
                h.assertTrue(
                    m.grid.equals(start.south()) && !m.job.active,
                    "Refueled engine silently resumed");
                m.status = m.job.resume();
                h.runAfterDelay(
                    35,
                    () -> {
                      h.assertTrue(m.grid.equals(start.south(3)), "Resume repeated/lost movement");
                      h.succeed();
                    });
              });
        });
  }

  static Machine machine(GameTestHelper h, int attachment) {
    var l = h.getLevel();
    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(44, 6, 44)))
      h.setBlock(pos, Blocks.AIR);
    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(44, 0, 44)))
      h.setBlock(pos, Blocks.STONE);
    BlockPos home = h.absolutePos(new BlockPos(10, 1, 10));
    l.setBlock(home, Automatons.PEDESTAL.get().defaultBlockState(), 3);
    Station s = (Station) l.getBlockEntity(home);
    s.owner = UUID.randomUUID();
    s.registerHome();
    Machine m = MachineParts.ENTITY.get().create(l);
    m.owner = s.owner;
    m.home = home;
    m.homeId = s.identity;
    m.grid = h.absolutePos(new BlockPos(20, 1, 20));
    m.heading = Direction.SOUTH;
    m.fuel = 1000;
    if (attachment > 0)
      m.equipment.setStackInSlot(
          0,
          new ItemStack(
              MachineParts.item(attachment == 1 ? "blade_attachment" : "bore_attachment")));
    m.snap();
    m.sync();
    l.addFreshEntity(m);
    return m;
  }

  private static void program(Machine m, String text) {
    ItemStack card = new ItemStack(MachineParts.item("program"));
    card.getOrCreateTag().putString("Text", text);
    m.equipment.setStackInSlot(1, card);
    m.mode = Machine.Mode.PROGRAM;
    m.status = m.job.start();
  }

  @GameTest(template = "empty", timeoutTicks = 20)
  public static void program_language_is_bounded_and_unambiguous(GameTestHelper h) {
    var a = MachineProgram.parse("N N1 e20...DROPOFF s2");
    var b = MachineProgram.parse("n\nn1E20...dropoffs2");
    h.assertTrue(
        a.valid() && b.valid() && a.instructions().size() == b.instructions().size(),
        "Compact/case/newline grammar differs");
    h.assertTrue(
        a.instructions().get(0).kind() == MachineProgram.Kind.TURN
            && a.instructions().get(1).amount() == 1,
        "Bare heading moved");
    h.assertTrue(
        a.instructions().get(3).amount() == 60, "Periods do not represent simulation seconds");
    for (String bad :
        List.of("N0", "N-1", "N1.5", "N9999999999999999999", "N1\nZ", "DROPOFF9", "", "N+2"))
      h.assertTrue(!MachineProgram.parse(bad).valid(), "Accepted invalid program: " + bad);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void assembly_all_four_orientations_and_invalid_intact(GameTestHelper h) {
    var l = h.getLevel();
    var p = FakePlayerFactory.get(l, new GameProfile(UUID.randomUUID(), "MachineTest"));
    BlockPos front = h.absolutePos(new BlockPos(22, 2, 22));
    for (Direction facing : Direction.Plane.HORIZONTAL) {
      var cells = MachinePattern.cells(front, facing);
      h.assertTrue(cells.size() == 24, "Wrong assembly size");
      for (var cell : cells) l.setBlock(cell.pos(), cell.block().defaultBlockState(), 3);
      String status = MachinePattern.form(p, front.above());
      h.assertTrue(status.startsWith("The Machine formed"), status);
      var machines =
          l.getEntitiesOfClass(Machine.class, new net.minecraft.world.phys.AABB(front).inflate(8));
      h.assertTrue(
          machines.size() == 1 && machines.get(0).heading == facing,
          "Wrong orientation or duplicate");
      h.assertTrue(
          machines.get(0).cargo.getSlots() == 108
              && machines.get(0).attachment() == 0
              && machines.get(0).engine == Machine.Engine.OFF,
          "Incorrect formed contributions/state");
      machines.get(0).discard();
    }
    var cells = MachinePattern.cells(front, Direction.NORTH);
    for (var c : cells) l.setBlock(c.pos(), c.block().defaultBlockState(), 3);
    l.removeBlock(front, false);
    String status = MachinePattern.form(p, front.above());
    h.assertTrue(status.startsWith("Incomplete"), "Invalid formation accepted");
    for (var c : cells)
      if (!c.pos().equals(front))
        h.assertTrue(
            l.getBlockState(c.pos()).is(c.block()), "Invalid formation removed a component");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 130)
  public static void bore_cuts_nine_pickaxe_blocks_and_keeps_floor(GameTestHelper h) {
    Machine m = machine(h, 2);
    BlockPos start = m.grid;
    for (int x = -1; x <= 1; x++)
      for (int y = 0; y < 3; y++)
        h.getLevel().setBlock(start.offset(x, y, 3), Blocks.STONE.defaultBlockState(), 3);
    program(m, "S1");
    h.runAfterDelay(
        100,
        () -> {
          h.assertTrue(m.grid.equals(start.south()), "Bore did not move: " + m.status);
          int count = 0;
          for (int i = 0; i < 108; i++)
            if (m.cargo.getStackInSlot(i).is(Items.COBBLESTONE))
              count += m.cargo.getStackInSlot(i).getCount();
          h.assertTrue(count == 9, "Nine cuts did not conserve drops: " + count);
          h.assertTrue(
              h.getLevel().getBlockState(start.offset(0, -1, 3)).is(Blocks.STONE),
              "Tunnel floor was mined");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 90)
  public static void attachments_stop_on_wrong_material_without_idle_breaking(GameTestHelper h) {
    Machine m = machine(h, 1);
    BlockPos obstacle = m.grid.south(3);
    h.getLevel().setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
    program(m, "S1");
    BlockPos start = m.grid;
    h.runAfterDelay(
        65,
        () -> {
          h.assertTrue(
              m.grid.equals(start) && h.getLevel().getBlockState(obstacle).is(Blocks.STONE),
              "Blade broke pickaxe material");
          m.halt("Test pause");
          m.job.cancel();
          m.equipment.setStackInSlot(0, new ItemStack(MachineParts.item("bore_attachment")));
          h.getLevel().setBlock(obstacle, Blocks.DIRT.defaultBlockState(), 3);
          m.engine = Machine.Engine.RUNNING;
          m.advance(Direction.SOUTH);
          h.assertTrue(
              h.getLevel().getBlockState(obstacle).is(Blocks.DIRT) && m.motionTicks == 0,
              "Bore cleared soft terrain");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 150)
  public static void program_pause_reload_and_engine_start_do_not_restart_distance(
      GameTestHelper h) {
    Machine m = machine(h, 0);
    BlockPos start = m.grid;
    program(m, "S5...");
    h.runAfterDelay(
        58,
        () -> {
          m.halt("Paused");
          int completed = m.grid.getZ() - start.getZ();
          int remaining = m.job.remaining;
          h.assertTrue(
              completed > 0 && completed < 5 && remaining == 5 - completed,
              "Partial progress incorrect");
          CompoundTag saved = new CompoundTag();
          m.addAdditionalSaveData(saved);
          m.readAdditionalSaveData(saved);
          m.startEngine();
          h.runAfterDelay(
              40,
              () -> {
                h.assertTrue(
                    m.grid.getZ() == start.getZ() + completed
                        && !m.job.active
                        && m.job.remaining == remaining,
                    "Engine-only start resumed job");
                h.assertTrue(m.job.resume().startsWith("Resuming"), "Saved continuation invalid");
                h.runAfterDelay(
                    45,
                    () -> {
                      h.assertTrue(
                          m.grid.equals(start.south(5)), "Resume repeated/skipped distance");
                      h.succeed();
                    });
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 70)
  public static void cargo_capacity_blocks_cut_but_reverse_is_available(GameTestHelper h) {
    Machine m = machine(h, 2);
    for (int i = 0; i < 108; i++) m.cargo.setStackInSlot(i, new ItemStack(Items.DIRT, 64));
    BlockPos start = m.grid, obstacle = start.south(3);
    h.getLevel().setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
    m.engine = Machine.Engine.RUNNING;
    for (int i = 0; i < MachineRules.BREAK_TICKS; i++) m.advance(Direction.SOUTH);
    h.assertTrue(
        h.getLevel().getBlockState(obstacle).is(Blocks.STONE) && m.status.equals("Cargo full"),
        "Full cargo destroyed output");
    m.advance(Direction.NORTH);
    h.runAfterDelay(
        20,
        () -> {
          h.assertTrue(m.grid.equals(start.north()), "Full cargo prevented reversing");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 25)
  public static void range_turn_sweep_and_attachment_swap_are_enforced(GameTestHelper h) {
    Machine m = machine(h, 2);
    m.engine = Machine.Engine.RUNNING;
    BlockPos diagonal = m.grid.offset(2, 0, 2);
    h.getLevel().setBlock(diagonal, Blocks.STONE.defaultBlockState(), 3);
    h.assertTrue(
        !MachineTerrain.turnClearance(m, Direction.EAST).isEmpty(),
        "Turn ignored swept attachment");
    h.getLevel().removeBlock(diagonal, false);
    program(m, "S1");
    h.assertTrue(
        !m.canInstall(ItemStack.EMPTY), "Attachment could be removed between active steps");
    m.halt("Pause");
    h.assertTrue(m.canInstall(ItemStack.EMPTY), "Parked attachment could not be removed");
    m.grid = new BlockPos(m.home.getX() + 46, m.home.getY(), m.home.getZ());
    m.heading = Direction.EAST;
    m.snap();
    h.assertTrue(
        !MachineTerrain.boundary(m, MachineTerrain.cells(m, m.grid, m.heading, 2)).isEmpty(),
        "Only center checked for range");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 25)
  public static void operator_assignment_is_exclusive_and_job_change_releases(GameTestHelper h) {
    Machine m = machine(h, 0);
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.setNoAi(true);
    w.owner = m.owner;
    w.homeId = m.homeId;
    w.home = m.home;
    w.setJob(6);
    w.setPos(m.getX() + 3, m.getY(), m.getZ());
    h.getLevel().addFreshEntity(w);
    h.assertTrue(
        MachineOperator.assign(m, w).startsWith("Operatorton assigned"), "Could not assign");
    h.assertTrue(
        !m.job.active && m.engine == Machine.Engine.OFF && !w.isPassenger(),
        "Assign silently started/teleported driver");
    Machine other = MachineParts.ENTITY.get().create(h.getLevel());
    other.owner = m.owner;
    other.homeId = m.homeId;
    h.assertTrue(
        MachineOperator.assign(other, w).contains("already assigned"),
        "Double assignment accepted");
    w.setJob(1);
    h.assertTrue(m.operator == null && w.machineId == null, "Job change retained assignment");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 250)
  public static void explicit_dropoff_continues_from_saved_start(GameTestHelper h) {
    Machine m = machine(h, 0);
    BlockPos start = m.grid;
    h.getLevel().setBlock(start.east(3), Blocks.CHEST.defaultBlockState(), 3);
    m.cargo.setStackInSlot(0, new ItemStack(Items.DIRT, 37));
    program(m, "S2DROPOFFW1");
    h.runAfterDelay(
        200,
        () -> {
          h.assertTrue(
              m.grid.equals(start.west()),
              "DROPOFF continued from the old work position: " + m.status);
          var chest =
              (net.minecraft.world.level.block.entity.ChestBlockEntity)
                  h.getLevel().getBlockEntity(start.east(3));
          h.assertTrue(
              chest.countItem(Items.DIRT) == 37 && m.usedSlots() == 0,
              "Dock did not conserve cargo");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 300)
  public static void seated_operator_unloads_then_returns_to_exact_work_position(GameTestHelper h) {
    Machine m = machine(h, 1);
    BlockPos start = m.grid;
    for (int x : new int[] {-3, 3})
      for (int z : new int[] {-1, 1})
        h.getLevel().setBlock(start.offset(x, 0, z), Blocks.CHEST.defaultBlockState(), 3);
    for (int i = 0; i < 108; i++) m.cargo.setStackInSlot(i, new ItemStack(Items.DIRT, 64));
    h.getLevel().setBlock(start.south(5), Blocks.DIRT.defaultBlockState(), 3);
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.setNoAi(true);
    w.owner = m.owner;
    w.homeId = m.homeId;
    w.home = m.home;
    w.setJob(6);
    h.getLevel().addFreshEntity(w);
    MachineOperator.assign(m, w);
    w.startRiding(m);
    program(m, "S4");
    h.runAfterDelay(
        250,
        () -> {
          h.assertTrue(
              m.grid.equals(start.south(4)), "Operator detour lost work progress: " + m.status);
          h.assertTrue(
              m.cargo.getStackInSlot(0).is(Items.DIRT) && m.cargo.getStackInSlot(0).getCount() == 1,
              "Pending cut was lost or duplicated");
          int stored = 0;
          for (int x : new int[] {-3, 3})
            for (int z : new int[] {-1, 1})
              stored +=
                  ((net.minecraft.world.level.block.entity.ChestBlockEntity)
                          h.getLevel().getBlockEntity(start.offset(x, 0, z)))
                      .countItem(Items.DIRT);
          h.assertTrue(stored == 108 * 64, "Detour lost deposited cargo");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 65)
  public static void program_removal_halts_motion_and_logistics_uses_real_cargo(GameTestHelper h) {
    Machine m = machine(h, 0);
    program(m, "S20");
    m.cargo.setStackInSlot(0, new ItemStack(Items.DIRT, 8));
    h.runAfterDelay(
        36,
        () -> {
          m.equipment.setStackInSlot(1, ItemStack.EMPTY);
          BlockPos before = m.grid;
          h.runAfterDelay(
              3,
              () -> {
                h.assertTrue(
                    !m.job.active && m.grid.equals(before) && m.motionTicks == 0,
                    "Removed Program kept running");
                MachineHauling.request(m);
                MachineHauling.request(m);
                var n = MachineHauling.network(m);
                h.assertTrue(
                    n.reports.values().stream()
                            .filter(r -> m.getUUID().equals(r.endpoint.worker()))
                            .count()
                        == 1,
                    "Duplicate hauling reports");
                var endpoint = new LogisticsNetwork.Endpoint(m.homeId, m.grid, m.getUUID());
                var handler = n.handler(h.getLevel(), endpoint, ItemStack.EMPTY);
                h.assertTrue(
                    handler == m.cargo
                        && handler.extractItem(0, 3, false).getCount() == 3
                        && m.cargo.getStackInSlot(0).getCount() == 5,
                    "Logistics used a copied inventory");
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 45)
  public static void survival_fuel_returns_container_and_tank_data_survives(GameTestHelper h) {
    Machine m = machine(h, 0);
    m.fuel = 0;
    m.equipment.setStackInSlot(2, LuminallBottle.create(250));
    h.runAfterDelay(
        15,
        () -> {
          h.assertTrue(
              m.fuel == Balance.BOTTLE_LUMINA.get() * 3
                  && m.equipment.getStackInSlot(2).is(Items.GLASS_BOTTLE),
              "Fuel conversion or container wrong");
          h.assertTrue(m.engine == Machine.Engine.OFF, "Refueling started engine");
          h.setBlock(new BlockPos(30, 1, 30), Luminall.TANK.get());
          var tank =
              (Reservoir) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(30, 1, 30)));
          tank.machineFuelThirds = 37;
          CompoundTag n = tank.saveWithoutMetadata();
          tank.machineFuelThirds = 0;
          tank.load(n);
          h.assertTrue(tank.machineFuelThirds == 37, "Dismantled energy lost after placing Tank");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 2600)
  public static void chunk_surface_passes_clear_edges_without_neighbor_harvest(GameTestHelper h) {
    Machine m = machine(h, 1);
    // Chunk alignment varies with the suite's fixture position. Keep the pedestal
    // outside every possible clearing/turning lane, while preserving the 48-block range.
    h.getLevel().removeBlock(m.home, false);
    BlockPos home = h.absolutePos(new BlockPos(43, 1, 20));
    h.getLevel().setBlock(home, Automatons.PEDESTAL.get().defaultBlockState(), 3);
    Station station = (Station) h.getLevel().getBlockEntity(home);
    station.owner = m.owner;
    station.registerHome();
    m.home = home;
    m.homeId = station.identity;
    BlockPos original = m.grid;
    int cx = original.getX() >> 4, cz = original.getZ() >> 4;
    m.job.chunkX = cx;
    m.job.chunkZ = cz;
    m.mode = Machine.Mode.CHUNK;
    List<BlockPos> plants = new ArrayList<>();
    for (int x : new int[] {0, 7, 15})
      for (int z : new int[] {0, 7, 15}) {
        BlockPos p = new BlockPos(cx * 16 + x, original.getY(), cz * 16 + z);
        h.getLevel().setBlock(p.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        h.getLevel().setBlock(p, Blocks.GRASS.defaultBlockState(), 3);
        plants.add(p);
      }
    BlockPos neighbor = new BlockPos(cx * 16 - 1, original.getY(), cz * 16 + 7);
    h.getLevel().setBlock(neighbor.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
    h.getLevel().setBlock(neighbor, Blocks.GRASS.defaultBlockState(), 3);
    m.status = m.job.start();
    h.runAfterDelay(
        2400,
        () -> {
          h.assertTrue(
              m.status.equals("Job complete — parked"),
              "Chunk did not finish: " + m.status + " at " + m.grid);
          for (BlockPos p : plants)
            h.assertTrue(h.getLevel().getBlockState(p).isAir(), "Uncleared edge " + p);
          h.assertTrue(
              h.getLevel().getBlockState(neighbor).is(Blocks.GRASS), "Harvested neighboring chunk");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 220)
  public static void operator_walks_to_board_and_release_keeps_identity(GameTestHelper h) {
    Machine m = machine(h, 0);
    var w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = m.owner;
    w.home = m.home;
    w.homeId = m.homeId;
    w.setJob(6);
    w.setPos(m.getX() + 7, m.getY(), m.getZ());
    w.cargo.setStackInSlot(0, new ItemStack(Items.APPLE, 3));
    h.getLevel().addFreshEntity(w);
    var id = w.getUUID();
    MachineOperator.assign(m, w);
    h.runAfterDelay(
        160,
        () -> {
          h.assertTrue(
              m.hasPilot() && w.getVehicle() == m,
              "Operatorton did not physically board: "
                  + w.status()
                  + " at "
                  + w.blockPosition()
                  + " Machine "
                  + m.grid
                  + " assigned "
                  + w.machineId
                  + " navigation done="
                  + w.getNavigation().isDone());
          h.assertTrue(!m.job.active && m.engine == Machine.Engine.OFF, "Boarding started work");
          MachineOperator.release(m);
          h.assertTrue(
              !w.isPassenger() && w.machineId == null && m.operator == null, "Release failed");
          h.assertTrue(
              w.getUUID().equals(id) && w.cargo.getStackInSlot(0).getCount() == 3,
              "Operator identity/inventory changed");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 25)
  public static void terrain_steps_require_full_width_and_turns_respect_entities(GameTestHelper h) {
    Machine m = machine(h, 0);
    BlockPos start = m.grid;
    for (int x = -1; x <= 1; x++)
      h.getLevel().setBlock(start.offset(x, 0, 2), Blocks.STONE.defaultBlockState(), 3);
    var step = MachineTerrain.plan(m, Direction.SOUTH);
    h.assertTrue(
        step.error().isEmpty() && step.destination().equals(start.south().above()),
        "Full-width step rejected: " + step.error());
    h.getLevel().removeBlock(start.offset(1, 0, 2), false);
    h.assertTrue(
        !MachineTerrain.plan(m, Direction.SOUTH).error().isEmpty(), "Narrow step accepted");
    for (int x = -1; x <= 1; x++) h.getLevel().removeBlock(start.offset(x, 0, 2), false);
    h.getLevel().removeBlock(start.offset(0, -1, 2), false);
    h.assertTrue(
        !MachineTerrain.plan(m, Direction.SOUTH).error().isEmpty(), "Unsupported edge accepted");
    h.getLevel().setBlock(start.offset(0, -1, 2), Blocks.STONE.defaultBlockState(), 3);
    m.equipment.setStackInSlot(0, new ItemStack(MachineParts.item("bore_attachment")));
    var pig = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
    pig.setNoAi(true);
    pig.setPos(m.getX() + 2.2, m.getY(), m.getZ() + 2.2);
    h.getLevel().addFreshEntity(pig);
    h.assertTrue(
        MachineTerrain.turnClearance(m, Direction.EAST).contains("entity"),
        "Turning attachment passed through entity");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 25)
  public static void dismantle_returns_components_cargo_and_fuel_exactly_once(GameTestHelper h) {
    Machine m = machine(h, 2);
    m.cargo.setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
    m.fuel = 123;
    var area = m.getBoundingBox().inflate(2);
    m.recover();
    m.recover();
    int diamonds = 0, tracks = 0, frames = 0, tanks = 0, energy = 0;
    for (var entity :
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
      var item = entity.getItem();
      if (item.is(Items.DIAMOND)) diamonds += item.getCount();
      if (item.is(MachineParts.item("machine_track"))) tracks += item.getCount();
      if (item.is(MachineParts.item("machine_frame"))) frames += item.getCount();
      if (item.is(Luminall.TANK_ITEM.get())) {
        tanks += item.getCount();
        energy += BlockItem.getBlockEntityData(item).getInt("MachineFuelThirds");
      }
    }
    h.assertTrue(
        diamonds == 7 && tracks == 6 && frames == 10 && tanks == 1 && energy == 123,
        "Dismantle lost or duplicated contributions");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 30)
  public static void blade_recognizes_whole_natural_tree_and_bore_earns_no_lumina(
      GameTestHelper h) {
    Machine m = machine(h, 1);
    BlockPos root = m.grid.south(3);
    h.getLevel().setBlock(root.below(), Blocks.DIRT.defaultBlockState(), 3);
    for (int y = 0; y < 5; y++)
      h.getLevel().setBlock(root.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        h.getLevel().setBlock(root.offset(x, 5, z), Blocks.OAK_LEAVES.defaultBlockState(), 3);
    h.assertTrue(MachineTerrain.cut(m, List.of(root)), "Blade rejected tree");
    h.assertTrue(
        h.getLevel().getBlockState(root.above(4)).isAir(), "Blade left upper natural tree behind");
    int logs = 0;
    for (int i = 0; i < 108; i++)
      if (m.cargo.getStackInSlot(i).is(Items.OAK_LOG)) logs += m.cargo.getStackInSlot(i).getCount();
    h.assertTrue(logs == 5, "Tree logs duplicated/lost");
    m.equipment.setStackInSlot(0, new ItemStack(MachineParts.item("bore_attachment")));
    h.getLevel().setBlock(root, Blocks.STONE.defaultBlockState(), 3);
    int fuel = m.fuel;
    h.assertTrue(
        MachineTerrain.cut(m, List.of(root)) && m.fuel == fuel - MachineRules.BREAK_COST,
        "Bore generated Lumina");
    h.succeed();
  }
}
