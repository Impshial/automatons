package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Opt-in packaged-client review in the disposable Lab only. */
public final class MachineVisualSmoke {
  private static java.util.UUID machineId;

  private static void server(
      java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    mc.getSingleplayerServer()
        .execute(
            () ->
                action.accept(
                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
  }

  private static void shot(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "automatons-machine-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("MACHINE_IMAGE " + name));
  }

  private static void button(String label) {
    var mc = Minecraft.getInstance();
    if (!(mc.screen instanceof MachineScreen screen))
      throw new IllegalStateException("Machine screen missing");
    screen.children().stream()
        .filter(
            c ->
                c instanceof net.minecraft.client.gui.components.Button b
                    && b.visible
                    && b.getMessage().getString().equals(label))
        .map(c -> (net.minecraft.client.gui.components.Button) c)
        .findFirst()
        .orElseThrow()
        .onPress();
  }

  public static void tick(int tick) {
    var mc = Minecraft.getInstance();
    if (tick == 5) {
      mc.options.hideGui = true;
      server(
          p -> {
            var l = p.serverLevel();
            l.getEntitiesOfClass(
                    net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(-12, 60, -12, 24, 90, 24),
                    e -> !(e instanceof net.minecraft.world.entity.player.Player))
                .forEach(net.minecraft.world.entity.Entity::discard);
            for (BlockPos at : BlockPos.betweenClosed(-10, 63, -10, 22, 70, 18))
              l.setBlock(
                  at,
                  at.getY() == 63
                      ? Blocks.STONE.defaultBlockState()
                      : Blocks.AIR.defaultBlockState(),
                  3);
            BlockPos at = new BlockPos(-7, 64, -6);
            l.setBlock(at, Automatons.PEDESTAL.get().defaultBlockState(), 3);
            Station home = (Station) l.getBlockEntity(at);
            home.owner = p.getUUID();
            home.registerHome();
            for (int i = 0; i < 3; i++) {
              Machine m = MachineParts.ENTITY.get().create(l);
              m.owner = p.getUUID();
              m.homeId = home.identity;
              m.home = at;
              m.grid = new BlockPos(-4 + i * 7, 64, 0);
              m.heading = Direction.SOUTH;
              m.fuel = 18000;
              if (i > 0)
                m.equipment.setStackInSlot(
                    0,
                    new ItemStack(
                        MachineParts.item(i == 1 ? "blade_attachment" : "bore_attachment")));
              ItemStack card = new ItemStack(MachineParts.item("program"));
              card.getOrCreateTag().putString("ProgramName", "Garden route");
              card.getOrCreateTag()
                  .putString("Text", "S10\nE\nE3\n...\nN10\nW\nW3\nDROPOFF\nS5\nE5\nN5\nW5");
              m.equipment.setStackInSlot(1, card);
              for (int slot = 0; slot < 108; slot++)
                m.cargo.setStackInSlot(
                    slot,
                    new ItemStack(slot % 2 == 0 ? Items.COBBLESTONE : Items.DIRT, slot % 64 + 1));
              m.snap();
              m.sync();
              l.addFreshEntity(m);
              if (i == 1) machineId = m.getUUID();
            }
            p.teleportTo(l, 16, 70, 13, 136, 23);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
          });
    }
    if (tick == 80)
      server(
          p -> {
            p.getInventory().clearContent();
            p.serverLevel()
                .getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(-12, 60, -12, 24, 90, 24))
                .forEach(net.minecraft.world.entity.Entity::discard);
          });
    if (tick == 90) shot("models");
    if (tick == 100) {
      mc.options.hideGui = false;
      server(
          p -> {
            var m = (Machine) p.serverLevel().getEntity(machineId);
            p.teleportTo(p.serverLevel(), 3.5, 64, 5.8, 180, 10);
            m.open(p);
          });
    }
    if (tick == 125) shot("controls");
    if (tick == 130) button("Select pedestal");
    if (tick == 135) {
      shot("pedestal-selection");
      mc.screen.mouseClicked(
          (mc.screen.width - 230) / 2.0 + 30, (mc.screen.height - 174) / 2.0 + 34, 0);
    }
    if (tick == 140) button("Program");
    if (tick == 165) {
      var editor =
          mc.screen.children().stream()
              .filter(c -> c instanceof ProgramEditor)
              .map(c -> (ProgramEditor) c)
              .findFirst()
              .orElseThrow();
      if (!editor.getValue().contains("DROPOFF"))
        throw new IllegalStateException("Program item text was not synchronized into editor");
      shot("program");
      editor.mouseScrolled(editor.getX() + 40, editor.getY() + 30, -5);
    }
    if (tick == 185) shot("program-scrolled");
    if (tick == 200) button("Storage");
    if (tick == 220) {
      shot("storage-first");
      button("Next cargo rows");
    }
    if (tick == 240) {
      shot("storage-last");
      button("Controls");
    }
    if (tick == 260) button("Board empty seat");
    if (tick == 290) {
      if (!(mc.player.getVehicle() instanceof Machine))
        throw new IllegalStateException("GUI boarding failed");
      shot("driver-hud");
      MachineClient.ENGINE.setDown(true);
    }
    // Simulate OS key repeat while R remains held through the complete startup.
    if (tick >= 291 && tick <= 329) KeyMapping.click(MachineClient.ENGINE.getKey());
    if (tick == 305) {
      if (!(mc.player.getVehicle() instanceof Machine starting)
          || !starting.view().getString("Status").equals("Starting... Please wait"))
        throw new IllegalStateException("Missing delayed startup feedback");
      shot("starting-hud");
    }
    if (tick == 329) {
      MachineClient.ENGINE.setDown(false);
      if (!(mc.player.getVehicle() instanceof Machine running)
          || !running.view().getString("Engine").equals("RUNNING"))
        throw new IllegalStateException("Holding R cycled the engine");
    }
    if (tick == 330) mc.options.keyUp.setDown(true);
    if (tick == 360) mc.options.keyUp.setDown(false);
    if (tick == 380) {
      if (!(mc.player.getVehicle() instanceof Machine driven)
          || net.minecraft.core.BlockPos.of(driven.view().getLong("Grid")).getZ() < 2)
        throw new IllegalStateException("Actual forward key failed to drive Machine");
      shot("driving-hud");
      server(
          p -> {
            p.stopRiding();
            var m = (Machine) p.serverLevel().getEntity(machineId);
            var w = Automatons.LUMBERTON.get().create(p.serverLevel());
            w.owner = p.getUUID();
            w.home = m.home;
            w.homeId = m.homeId;
            w.setJob(6);
            w.setPos(m.getX() + 5, m.getY(), m.getZ());
            p.serverLevel().addFreshEntity(w);
            MachineOperator.assign(m, w);
            p.teleportTo(p.serverLevel(), 8, 66, m.getZ() + 7, 142, 18);
          });
    }
    if (tick == 510) {
      boolean boarded = false;
      for (var entity : mc.level.entitiesForRendering())
        if (entity.getUUID().equals(machineId) && entity.getFirstPassenger() instanceof Lumberton)
          boarded = true;
      if (!boarded)
        throw new IllegalStateException("Packaged Operatorton did not board after manual driving");
      shot("operator");
    }
    if (tick == 525) {
      mc.options.hideGui = true;
      server(
          p -> {
            var l = p.serverLevel();
            for (int i = 0; i < 2; i++) {
              BlockPos pos = new BlockPos(16 + i * 2, 64, 14);
              l.setBlock(pos, Luminall.TANK.get().defaultBlockState(), 3);
              var tank = (Reservoir) l.getBlockEntity(pos);
              tank.tank.fill(
                  new net.minecraftforge.fluids.FluidStack(Luminall.SOURCE.get(), 4000),
                  net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
              tank.synchronize();
              if (i == 1) l.setBlock(pos.above(), Luminall.DRAIN.get().defaultBlockState(), 3);
            }
            p.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(MachineParts.PACKED.get()));
            p.teleportTo(l, 21, 68, 19, 137, 25);
          });
    }
    if (tick == 575) shot("tank-drain");
    if (tick == 580)
      server(
          p -> {
            Lumina.clear(p);
            Lumina.add(p, 100);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            p.teleportTo(p.serverLevel(), 18.5, 65.125, 14.5, 180, 40);
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
          });
    if (tick == 625)
      server(
          p -> {
            Reservoir tank = (Reservoir) p.serverLevel().getBlockEntity(new BlockPos(18, 64, 14));
            int amount = tank.tank.getFluidAmount();
            if (amount <= 4000 || 100 - Lumina.balance(p) != (amount - 4000) / 50)
              throw new IllegalStateException(
                  "Standing on Drain did not conserve player Lumina: "
                      + amount
                      + " / "
                      + Lumina.balance(p));
            p.getPersistentData().putInt("DrainReviewAmount", amount);
            p.teleportTo(p.serverLevel(), 20.5, 64, 14.5, 90, 0);
            System.out.println(
                "DRAIN_STANDING_PASS fluid=" + amount + " balance=" + Lumina.balance(p));
          });
    if (tick == 670)
      server(
          p -> {
            Reservoir tank = (Reservoir) p.serverLevel().getBlockEntity(new BlockPos(18, 64, 14));
            if (tank.tank.getFluidAmount() != p.getPersistentData().getInt("DrainReviewAmount"))
              throw new IllegalStateException("Drain continued after stepping off");
            System.out.println("DRAIN_STEP_OFF_PASS");
          });
    if (tick == 695) {
      System.out.println("MACHINE_VISUAL_COMPLETE");
      mc.stop();
    }
  }

  private MachineVisualSmoke() {}
}
