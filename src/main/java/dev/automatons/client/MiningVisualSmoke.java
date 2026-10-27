package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Opt-in client verification in the existing isolated lab, never a player save. */
public final class MiningVisualSmoke {
  private static final BlockPos HOME = new BlockPos(-3, 64, 0), TOTEM = new BlockPos(3, 64, 0);
  private static UUID workerId, timberId;
  private static float lastVisual;
  private static int samples;

  private static void server(java.util.function.Consumer<ServerPlayer> task) {
    var mc = Minecraft.getInstance();
    mc.getSingleplayerServer()
        .execute(
            () -> {
              var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
              if (p != null) task.accept(p);
            });
  }

  private static void shot(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "automatons-mining-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("AUTOMATONS_MINING_IMAGE " + name));
  }

  private static void tab(String name) {
    var mc = Minecraft.getInstance();
    if (mc.screen != null)
      mc.screen.children().stream()
          .filter(c -> c instanceof Button b && b.getMessage().getString().equals(name))
          .map(c -> (Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
  }

  private static void menu(ServerPlayer p) {
    Station station = (Station) p.serverLevel().getBlockEntity(TOTEM);
    NetworkHooks.openScreen(
        p,
        station,
        b -> {
          b.writeBoolean(false);
          b.writeBlockPos(TOTEM);
        });
  }

  public static void tick(int tick) {
    var mc = Minecraft.getInstance();
    if (tick == 5)
      server(
          p -> {
            var l = p.serverLevel();
            Station station = (Station) l.getBlockEntity(TOTEM),
                home = (Station) l.getBlockEntity(HOME);
            MinePlan m = new MinePlan();
            m.entrance = new BlockPos(5, 64, 6);
            m.forward = Direction.NORTH;
            m.stage = MinePlan.Stage.STORAGE;
            m.lateral = 1;
            m.column = 3;
            for (BlockPos at : BlockPos.betweenClosed(2, 39, -30, 12, 63, 8))
              l.setBlock(at, Blocks.STONE.defaultBlockState(), 3);
            for (var part : MinePlan.arch(l)) {
              var state = part.state();
              if (state.getBlock() instanceof TrapDoorBlock)
                state =
                    state
                        .setValue(TrapDoorBlock.FACING, m.forward)
                        .setValue(TrapDoorBlock.OPEN, true);
              l.setBlock(m.at(part.x(), part.y(), -part.z()), state, 3);
            }
            for (int d = 1; d <= m.shaftDepth; d++) l.setBlock(m.ladder(d), m.ladderState(), 3);
            for (int x = -1; x <= 1; x++)
              for (int z = 0; z <= 2; z++) {
                for (int y = -m.shaftDepth; y <= 2 - m.shaftDepth; y++)
                  if (x != 0 || z != 0)
                    l.setBlock(m.at(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                m.retain(m.at(x, -m.shaftDepth, z));
              }
            for (int z = 3; z < 6; z++) {
              l.setBlock(m.at(0, -m.shaftDepth, z), Blocks.AIR.defaultBlockState(), 3);
              l.setBlock(m.at(0, 1 - m.shaftDepth, z), Blocks.AIR.defaultBlockState(), 3);
              m.retain(m.at(0, -m.shaftDepth, z));
            }
            station.mine = m;
            station.changed();
            WorldData.get(l).mines.put(m.id, m);
            var chest =
                (net.minecraft.world.level.block.entity.ChestBlockEntity)
                    l.getBlockEntity(TOTEM.offset(1, 0, 1));
            chest.setItem(0, new ItemStack(Items.TORCH, 64));
            chest.setItem(1, new ItemStack(Items.IRON_PICKAXE));
            chest.setItem(2, new ItemStack(Items.IRON_SHOVEL));
            Lumberton w = Automatons.LUMBERTON.get().create(l);
            w.owner = p.getUUID();
            w.home = HOME;
            w.homeId = home.identity;
            w.homeDimension = l.dimension().location().toString();
            w.setJob(2);
            w.setCustomName(Component.literal("Élodie du Nord"));
            w.tools.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
            w.tools.setStackInSlot(1, new ItemStack(Items.IRON_SHOVEL));
            w.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 16));
            w.cargo.setStackInSlot(1, new ItemStack(Items.CHEST, 4));
            w.moveTo(5.5, 64, 3.5, 0, 0);
            l.addFreshEntity(w);
            w.assign(station);
            w.authorized = false;
            workerId = w.getUUID();
            WorkerIdentity.update(w, "Deployed");
          });
    if (tick == 10)
      server(
          p -> {
            p.teleportTo(p.serverLevel(), 5.5, 64, 6.3, 180, 6);
            p.serverLevel().setDayTime(1000);
          });
    if (tick == 25) shot("wooden-face");
    if (tick == 30)
      server(
          p -> {
            p.teleportTo(p.serverLevel(), 5.5, 64, 4, 180, 8);
            Lumberton w = (Lumberton) p.serverLevel().getEntity(workerId);
            NetworkHooks.openScreen(
                p,
                w,
                b -> {
                  b.writeBoolean(true);
                  b.writeInt(w.getId());
                  b.writeVarInt(w.cargo.getSlots());
                });
          });
    if (tick == 50) shot("worker-scale3");
    if (tick == 60) {
      mc.options.guiScale().set(4);
      mc.resizeDisplay();
    }
    if (tick == 80) shot("worker-scale4");
    if (tick == 90) {
      mc.options.guiScale().set(3);
      mc.resizeDisplay();
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), 3.5, 64, 3.5, 180, 8);
            menu(p);
          });
    }
    if (tick == 110) shot("mixed-roster");
    if (tick == 120) tab("Mine");
    if (tick == 140) shot("mine-panel");
    if (tick == 150 && mc.screen instanceof WorkScreen screen)
      screen.mouseScrolled(
          mc.getWindow().getGuiScaledWidth() / 2.0 + 140,
          mc.getWindow().getGuiScaledHeight() / 2.0,
          -20);
    if (tick == 160) shot("mine-supplies");
    if (tick == 170) tab("Forestry");
    if (tick == 190) shot("forestry-panel");
    if (tick == 200) {
      tab("Workers");
      server(
          p -> {
            if (p.containerMenu instanceof WorkMenu menu) {
              menu.selectedWorker = workerId;
              menu.broadcastChanges();
            }
          });
    }
    if (tick == 225) shot("worker-find");
    if (tick == 230) tab("Find");
    if (tick == 240) {
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 600);
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
    }
    if (tick == 260) shot("small-window-find");
    if (tick == 270)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), 14, 73, 15, 140, 35);
          });
    if (tick == 275 && mc.level.getBlockEntity(TOTEM) instanceof Station s) RangeOverlay.toggle(s);
    if (tick == 295) shot("mine-overlay-glow");
    if (tick == 300 && mc.level.getBlockEntity(TOTEM) instanceof Station s) RangeOverlay.toggle(s);
    if (tick == 310)
      server(
          p -> {
            Timber timber = Automatons.TIMBER.get().create(p.serverLevel());
            BlockPos root = new BlockPos(9, 64, -3);
            timber.setPos(root.getX(), root.getY(), root.getZ());
            timber.owner = p.getUUID();
            timber.rigidFall = true;
            timber.direction = Direction.EAST;
            timber.duration = 80;
            for (int i = 0; i < 5; i++) {
              timber.sources.add(root.above(i));
              timber.targets.add(root.east(i));
              timber.wood.add(Blocks.OAK_LOG.defaultBlockState());
            }
            CompoundTag saved = timber.saveWithoutId(new CompoundTag());
            timber.load(saved);
            p.serverLevel().addFreshEntity(timber);
            timberId = timber.getUUID();
            p.teleportTo(p.serverLevel(), 16, 68, 3, 135, 22);
          });
    if (tick == 330 || tick == 350 || tick == 370) shot("fall-" + tick);
    if (tick >= 315 && tick <= 420 && timberId != null) {
      for (Timber t : mc.level.getEntitiesOfClass(Timber.class, new AABB(8, 63, -4, 15, 71, 0)))
        if (t.getUUID().equals(timberId)) {
          float v = t.visualProgress(1);
          if (v + 1e-4 < lastVisual || v - lastVisual > 1.501f && samples > 0)
            throw new IllegalStateException("Discontinuous client fall " + lastVisual + " -> " + v);
          lastVisual = v;
          samples++;
        }
    }
    if (tick == 430) {
      var logs = mc.level.getEntitiesOfClass(Timber.class, new AABB(8, 63, -4, 15, 71, 0));
      if (logs.isEmpty()) throw new IllegalStateException("Client timber missing");
      Timber timber = logs.get(0);
      BlockPos log = timber.targets.get(0);
      int collisions = 0;
      for (var shape : mc.level.getBlockCollisions(mc.player, new AABB(log)))
        if (!shape.isEmpty()) collisions++;
      Vec3 before = mc.player.position();
      mc.player.setPos(log.getX() - 1, log.getY(), log.getZ() + .5);
      mc.player.move(MoverType.SELF, new Vec3(2, 0, 0));
      boolean blocked = mc.player.getX() < log.getX();
      mc.player.setPos(before.x, before.y, before.z);
      System.out.println(
          "AUTOMATONS_CLIENT_TIMBER collisions="
              + collisions
              + " blocked="
              + blocked
              + " smoothSamples="
              + samples
              + " finalProgress="
              + lastVisual);
      if (collisions == 0 || !blocked || samples < 60 || lastVisual != 80)
        throw new IllegalStateException("Client timber validation failed");
      shot("landed-solid-logs");
    }
    if (tick == 280)
      server(p -> ((Lumberton) p.serverLevel().getEntity(workerId)).authorized = true);
    if (tick == 440)
      server(
          p -> {
            Lumberton w = (Lumberton) p.serverLevel().getEntity(workerId);
            w.authorized = true;
            p.teleportTo(p.serverLevel(), 5.5, 44, 3.5, 0, 8);
          });
    if (tick == 480 || tick == 540 || tick == 620) shot("miner-work-" + tick);
    if (tick == 640)
      server(
          p -> {
            Station station = (Station) p.serverLevel().getBlockEntity(TOTEM);
            MinePlan mine = station.mine;
            System.out.println(
                "AUTOMATONS_DEPOT depth="
                    + mine.shaftDepth
                    + " built="
                    + mine.depotBuilt
                    + " chests="
                    + mine.placedChests.size());
            if (!mine.depotBuilt)
              throw new IllegalStateException(
                  "Client fixture did not complete underground storage");
          });
    if (tick == 655)
      server(
          p -> {
            Lumberton w = (Lumberton) p.serverLevel().getEntity(workerId);
            w.authorized = false;
            Ledger ledger = Ledger.get(p.server);
            ledger.names.rename(workerId, "Seán-Łukasz of the Northern Mine");
            ledger.setDirty();
            WorkerIdentity.update(w, "Deployed");
            Station home = (Station) p.serverLevel().getBlockEntity(HOME);
            p.teleportTo(p.serverLevel(), -2.5, 64, 2.5, 180, 8);
            NetworkHooks.openScreen(
                p,
                home,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(HOME);
                });
          });
    if (tick == 675) shot("pedestal-names-on");
    if (tick == 680) tab("Show Automaton Names: ON");
    if (tick == 695) shot("pedestal-names-off");
    if (tick == 700)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), 5.5, 64, 6.3, 180, 6);
            Lumberton w = (Lumberton) p.serverLevel().getEntity(workerId);
            w.getNavigation().stop();
            w.moveTo(5.5, 64, 3.5, 0, 0);
          });
    if (tick == 720) shot("labels-off");
    if (tick == 725)
      server(
          p -> {
            Station home = (Station) p.serverLevel().getBlockEntity(HOME);
            home.showNames(true);
          });
    if (tick == 745) shot("labels-on-long-name");
    if (tick == 750)
      server(
          p -> {
            menu(p);
          });
    if (tick == 770) shot("named-roster");
    if (tick == 790) {
      mc.options.hideGui = true;
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), 5.5, 64, 6.3, 180, 4);
            Lumberton w = (Lumberton) p.serverLevel().getEntity(workerId);
            w.setNoAi(true);
            w.getNavigation().stop();
            w.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            w.moveTo(5.5, 64, 3.5, 0, 0);
            w.setYRot(0);
            w.yBodyRot = 0;
            w.yBodyRotO = 0;
            w.yHeadRot = 0;
            w.yHeadRotO = 0;
          });
    }
    if (tick == 815) shot("face-portrait");
    if (tick == 820) mc.options.hideGui = false;
    if (tick == 835) shot("face-and-name");
    if (tick == 845) {
      System.out.println("AUTOMATONS_MINING_VISUAL_COMPLETE");
      mc.stop();
    }
  }
}
