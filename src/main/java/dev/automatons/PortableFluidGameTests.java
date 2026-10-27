package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_machine")
@PrefixGameTestTemplate(false)
public final class PortableFluidGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void operator_station_seats_from_all_exposed_faces_and_orientations(
      GameTestHelper h) {
    Machine m = MachineGameTests.machine(h, 0);
    var p = player(h, m.owner);
    double[][] views = {
      {3, .72, 1, .4375, .72, 1},
      {-3, .72, 1, -.4375, .72, 1},
      {0, 3, 1, 0, .8125, 1},
      {0, 1, 3, 0, 1, 1.5},
      {0, 1, .32, 0, 1, .375}
    };
    for (Direction heading : Direction.Plane.HORIZONTAL) {
      m.heading = heading;
      m.snap();
      for (double[] v : views) {
        Vec3 eye =
            m.position()
                .add(
                    v[0] * heading.getStepZ() + v[2] * heading.getStepX(),
                    v[1],
                    -v[0] * heading.getStepX() + v[2] * heading.getStepZ());
        Vec3 target =
            m.position()
                .add(
                    v[3] * heading.getStepZ() + v[5] * heading.getStepX(),
                    v[4],
                    -v[3] * heading.getStepX() + v[5] * heading.getStepZ());
        p.setPos(eye.x, eye.y - p.getEyeHeight(), eye.z);
        Vec3 d = target.subtract(eye);
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))));
        h.assertTrue(
            MachinePicking.station(m, p),
            "Station face not selectable: " + heading + " " + java.util.Arrays.toString(v));
        m.interactAt(p, target.subtract(m.position()), InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() == m, "Station click did not seat owner");
        p.stopRiding();
      }
    }
    h.succeed();
  }

  static ServerPlayer player(GameTestHelper h, UUID id) {
    var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(id, "PortableTest"));
    p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    p.getInventory().clearContent();
    return p;
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void wand_packs_and_reforms_machine_with_all_contents(GameTestHelper h) {
    Machine m = MachineGameTests.machine(h, 2);
    var p = player(h, m.owner);
    p.setPos(m.getX() + 6, m.getY(), m.getZ());
    m.cargo.setStackInSlot(3, new ItemStack(Items.DIAMOND, 17));
    m.equipment.setStackInSlot(1, new ItemStack(MachineParts.item("program")));
    m.equipment.getStackInSlot(1).getOrCreateTag().putString("Text", "S3 T2");
    m.fuel = 987;
    m.fuelFraction = .5;
    m.tankData.put(
        "Tank", new FluidStack(Luminall.SOURCE.get(), 1234).writeToNBT(new CompoundTag()));
    BlockPos pos = m.grid;
    p.setShiftKeyDown(true);
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Automatons.WAND.get()));
    m.interactAt(p, new Vec3(0, 1, 0), InteractionHand.MAIN_HAND);
    h.assertTrue(m.isRemoved(), "Wand click on seat boarded instead of packing");
    ItemStack packed = ItemStack.EMPTY;
    for (var stack : p.getInventory().items)
      if (stack.is(MachineParts.PACKED.get())) packed = stack;
    h.assertTrue(!packed.isEmpty(), "Missing packed Machine");
    h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
    h.assertTrue(
        !MachineItem.place(h.getLevel(), p, packed, pos).isEmpty() && packed.getCount() == 1,
        "Blocked placement consumed item");
    h.getLevel().removeBlock(pos, false);
    String result = MachineItem.place(h.getLevel(), p, packed, pos);
    h.assertTrue(result.isEmpty() && packed.isEmpty(), "Reformation failed: " + result);
    var machines = h.getLevel().getEntitiesOfClass(Machine.class, new AABB(pos).inflate(1));
    h.assertTrue(machines.size() == 1, "Machine duplicated or missing");
    Machine restored = machines.get(0);
    h.assertTrue(
        restored.cargo.getStackInSlot(3).getCount() == 17
            && restored.attachment() == 2
            && restored.equipment.getStackInSlot(1).getTag().getString("Text").equals("S3 T2")
            && restored.fuel == 987
            && restored.fuelFraction == .5
            && restored.tankData.getCompound("Tank").getInt("Amount") == 1234
            && restored.engine == Machine.Engine.OFF,
        "Packing lost contents or started engine");
    h.assertTrue(
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2))
            .isEmpty(),
        "Packing also dropped components");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void engine_start_displays_wait_and_completes_once(GameTestHelper h) {
    Machine m = MachineGameTests.machine(h, 0);
    m.startEngine();
    h.assertTrue(
        m.engine == Machine.Engine.STARTING && m.status.equals("Starting... Please wait"),
        "No startup feedback");
    for (int i = 0; i < 20; i++) m.startEngine();
    h.runAfterDelay(
        MachineRules.START_TICKS + 2,
        () -> {
          h.assertTrue(
              m.engine == Machine.Engine.RUNNING && m.status.equals("Engine running"),
              "Repeated start reset startup");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void tank_wand_preserves_fluid_ordinary_break_spills_and_evaporates(
      GameTestHelper h) {
    BlockPos pos = h.absolutePos(new BlockPos(5, 2, 5));
    h.getLevel().setBlock(pos, Luminall.TANK.get().defaultBlockState(), 3);
    Reservoir tank = (Reservoir) h.getLevel().getBlockEntity(pos);
    tank.tank.fill(new FluidStack(Luminall.SOURCE.get(), 500), FluidAction.EXECUTE);
    var p = player(h, UUID.randomUUID());
    p.setPos(pos.getX() + 3, pos.getY(), pos.getZ());
    h.assertTrue(ReservoirBlock.pack(p, pos), "Wand failed to pack tank");
    ItemStack packed = ItemStack.EMPTY;
    for (var item : p.getInventory().items) if (item.is(Luminall.TANK_ITEM.get())) packed = item;
    h.assertTrue(
        !packed.isEmpty()
            && BlockItem.getBlockEntityData(packed).getCompound("Tank").getInt("Amount") == 500,
        "Wand lost fluid");
    h.getLevel().setBlock(pos, Luminall.TANK.get().defaultBlockState(), 3);
    ((Reservoir) h.getLevel().getBlockEntity(pos))
        .tank.fill(new FluidStack(Luminall.SOURCE.get(), 500), FluidAction.EXECUTE);
    h.getLevel().destroyBlock(pos, true, p);
    h.runAfterDelay(
        3,
        () -> {
          h.assertTrue(
              h.getLevel().getBlockState(pos).is(Luminall.SPILL.get()), "No temporary spill");
          for (var drop :
              h.getLevel()
                  .getEntitiesOfClass(
                      net.minecraft.world.entity.item.ItemEntity.class, new AABB(pos).inflate(2)))
            if (drop.getItem().is(Luminall.TANK_ITEM.get()))
              h.assertTrue(
                  BlockItem.getBlockEntityData(drop.getItem()) == null,
                  "Ordinary break retained fluid");
        });
    h.runAfterDelay(
        70,
        () -> {
          h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "Spill persisted");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void drain_conserves_lumina_and_stops_when_full_or_incompatible(GameTestHelper h) {
    BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
    h.getLevel().setBlock(pos, Luminall.TANK.get().defaultBlockState(), 3);
    Reservoir tank = (Reservoir) h.getLevel().getBlockEntity(pos);
    var p = player(h, UUID.randomUUID());
    Lumina.clear(p);
    Lumina.addThirds(p, 35);
    h.assertTrue(
        LuminaDrain.transfer(p, tank) == 10 && Lumina.thirds(p) == 20,
        "Drain conversion incorrect");
    h.assertTrue(
        LuminaDrain.transfer(p, tank) == 10 && Lumina.thirds(p) == 5, "Second transfer incorrect");
    h.assertTrue(LuminaDrain.transfer(p, tank) == 2 && Lumina.thirds(p) == 2, "Remainder lost");
    h.assertTrue(LuminaDrain.transfer(p, tank) == 0, "Drain overdrew player");
    Lumina.addThirds(p, 300);
    tank.tank.fill(new FluidStack(Luminall.SOURCE.get(), tank.capacity()), FluidAction.EXECUTE);
    long before = Lumina.thirds(p);
    h.assertTrue(
        LuminaDrain.transfer(p, tank) == 0 && Lumina.thirds(p) == before,
        "Full tank charged player");
    tank.tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
    tank.tank.fill(
        new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100), FluidAction.EXECUTE);
    h.assertTrue(
        LuminaDrain.transfer(p, tank) == 0 && Lumina.thirds(p) == before,
        "Mixed fluids consumed Lumina");
    h.getLevel().setBlock(pos.above(), Luminall.DRAIN.get().defaultBlockState(), 3);
    h.getLevel().destroyBlock(pos, true);
    h.assertTrue(
        !h.getLevel().getBlockState(pos.above()).is(Luminall.DRAIN.get()),
        "Drain floated after Tank removal");
    h.succeed();
  }
}
