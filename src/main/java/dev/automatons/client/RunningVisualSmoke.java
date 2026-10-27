package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Explicit opt-in, isolated-world views of the packaged runtime rig and imported structures. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
    modid = Automatons.ID,
    value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RunningVisualSmoke {
  private static final BlockPos HOME = new BlockPos(-3, 64, 0), ANCHOR = new BlockPos(8, 64, -2);
  private static final List<UUID> models = new ArrayList<>();
  private static UUID courierId, recipientId;
  private static volatile int passagePhase, finishAt;
  private static volatile String pendingShot = "";
  private static boolean doorOpened;
  private static final BlockPos PASSAGE_DOOR = new BlockPos(-8, 64, 5);

  @net.minecraftforge.eventbus.api.SubscribeEvent
  public static void observeDoor(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
    if (!Boolean.getBoolean("automatons.runningReview")
        || courierId == null
        || event.phase != net.minecraftforge.event.TickEvent.Phase.END
        || doorOpened) return;
    var server = Minecraft.getInstance().getSingleplayerServer();
    if (server == null) return;
    var state = server.overworld().getBlockState(PASSAGE_DOOR);
    if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN)) {
      doorOpened = true;
      System.out.println("AUTOMATONS_RUNNING_DOOR_OPEN " + state);
    }
  }

  private static void server(java.util.function.Consumer<ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    mc.getSingleplayerServer()
        .execute(
            () -> {
              var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
              if (p != null) action.accept(p);
            });
  }

  private static void shot(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "automatons-running-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("AUTOMATONS_RUNNING_IMAGE " + name));
  }

  private static void camera(double x, double y, double z, float yaw, float pitch) {
    server(p -> p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch));
  }

  public static void tick(int tick) {
    var mc = Minecraft.getInstance();
    if (tick == 5)
      server(
          p -> {
            var l = p.serverLevel();
            l.getEntitiesOfClass(Lumberton.class, new AABB(-20, 60, -20, 24, 90, 24))
                .forEach(Lumberton::discard);
            Station home = (Station) l.getBlockEntity(HOME);
            home.showNames(false);
            l.setBlock(ANCHOR, Automatons.TOTEM.get().defaultBlockState(), 3);
            Station anchor = (Station) l.getBlockEntity(ANCHOR);
            anchor.owner = p.getUUID();
            anchor.logisticsHome = home.identity;
            BuildingProject warehouse = new BuildingProject();
            warehouse.anchor = ANCHOR;
            warehouse.home = home.identity;
            warehouse.owner = p.getUUID();
            warehouse.totem = anchor.identity;
            var template = warehouse.template(l);
            for (var c : template.cells) {
              BlockPos at = template.world(ANCHOR, Direction.NORTH, c.local());
              if (at.equals(ANCHOR)) continue;
              l.setBlock(at, c.state(), 2);
              if (!c.state().isAir()) warehouse.completed.add(at.asLong());
            }
            warehouse.ready = warehouse.approved = true;
            warehouse.reason = "Warehouse ready";
            WorldData.get(l).buildings.put(anchor.identity, warehouse);
            for (BlockPos at : warehouse.chests(l))
              if (warehouse.handler(l, at) == null)
                throw new IllegalStateException("Client warehouse lid blocked");
            MinePlan mine = new MinePlan();
            mine.rustic = true;
            mine.entrance = new BlockPos(-8, 64, -8);
            mine.stage = MinePlan.Stage.READY;
            var minehead = mine.minehead(l);
            for (var c : minehead.cells)
              l.setBlock(minehead.world(mine.entrance, Direction.NORTH, c.local()), c.state(), 2);
            for (int job = 0; job <= 3; job++) {
              Lumberton w = Automatons.LUMBERTON.get().create(l);
              w.owner = p.getUUID();
              w.homeId = home.identity;
              w.home = HOME;
              w.homeDimension = l.dimension().location().toString();
              w.setJob(job);
              w.setNoAi(true);
              w.showNames(false);
              w.moveTo(-6 + job * 4 + .5, 64, 4.5, 0, 0);
              l.addFreshEntity(w);
              w.assign(anchor);
              w.authorized = false;
              w.state("Waiting for deliveries");
              home.workers.add(w.getUUID());
              models.add(w.getUUID());
              if (job == 3)
                for (int i = 0; i < 18; i++)
                  w.cargo.setStackInSlot(
                      i, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.TORCH, i + 1));
            }
            LogisticsNetwork.get((Lumberton) l.getEntity(models.get(3))).refresh(l);
            System.out.println(
                "AUTOMATONS_RUNNING_FIXTURE chestBlocks="
                    + warehouse.chests(l).size()
                    + " anchorPreserved="
                    + (l.getBlockEntity(ANCHOR) == anchor));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.teleportTo(l, .5, 66, 14, 180, 15);
          });
    if (tick == 40) mc.options.hideGui = true;
    if (tick == 70) shot("all-jobs");
    if (tick == 85) camera(-5.5, 64, 8, 180, 6);
    if (tick == 110) shot("unassigned-face");
    if (tick == 125) camera(-1.5, 64, 8, 180, 6);
    if (tick == 150) shot("lumberton");
    if (tick == 165) camera(2.5, 64, 8, 180, 6);
    if (tick == 190) shot("minington");
    if (tick == 205) camera(6.5, 64, 8, 180, 6);
    if (tick == 230) shot("runnington-front");
    if (tick == 245) camera(6.5, 64, 1, 0, 6);
    if (tick == 270) shot("runnington-backpack");
    if (tick == 285) camera(-1, 67, 0, 135, 24);
    if (tick == 315) shot("minehead-ground");
    if (tick == 330) camera(14, 68, -14, 35, 28);
    if (tick == 360) shot("warehouse-exterior");
    if (tick == 375) camera(8.5, 64, -8.2, 0, 0);
    if (tick == 405) shot("warehouse-interior");
    if (tick == 420) camera(8.5, 64, -3.1, 180, 0);
    if (tick == 450) shot("warehouse-door-and-chests");
    if (tick == 452)
      server(
          p -> {
            var l = p.serverLevel();
            var anchor = (Station) l.getBlockEntity(ANCHOR);
            WorldData.get(l).buildings.get(anchor.identity).ready = false;
            anchor.changed();
          });
    if (tick == 454) camera(15, 68, -14, 35, 28);
    if (tick == 463) shot("yellow-construction-outline");
    if (tick == 465) {
      mc.options.hideGui = false;
      server(
          p -> {
            Lumberton runner = (Lumberton) p.serverLevel().getEntity(models.get(3));
            p.teleportTo(p.serverLevel(), 6.5, 64, 7, 180, 6);
            NetworkHooks.openScreen(
                p,
                runner,
                b -> {
                  b.writeBoolean(true);
                  b.writeInt(runner.getId());
                  b.writeVarInt(runner.cargo.getSlots());
                });
          });
    }
    if (tick == 495) {
      if (!(mc.player.containerMenu instanceof WorkMenu menu) || menu.ownSlots != 21)
        throw new IllegalStateException("Runner GUI missing backpack slots");
      shot("backpack-menu");
    }
    if (tick == 480 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().endsWith(" v"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 484) shot("profession-list");
    if (tick == 486 && mc.screen instanceof WorkScreen screen) {
      int x = (mc.getWindow().getGuiScaledWidth() - 300) / 2,
          y = (mc.getWindow().getGuiScaledHeight() - 234) / 2;
      screen.mouseClicked(x + 200, y + 166 + 2 * 18 + 6, 0);
    }
    if (tick == 488 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().equals("Apply Job"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 491) shot("profession-confirmation");
    if (tick == 492 && mc.screen != null) mc.screen.keyPressed(256, 0, 0);
    if (tick == 499 && mc.screen instanceof WorkScreen screen) {
      screen.buildPopup = true;
      screen.warehousePopup = true;
    }
    if (tick == 505) shot("construction-build-list");
    if (tick == 510)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), 8.5, 64, -4, 0, 0);
            Station anchor = (Station) p.serverLevel().getBlockEntity(ANCHOR);
            NetworkHooks.openScreen(
                p,
                anchor,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(ANCHOR);
                });
          });
    if (tick == 530 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().equals("Logistics"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 555) shot("logistics-menu");
    if (tick == 539 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().equals("Profession Settings"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 545) shot("profession-settings-mine");
    if (tick == 552 && mc.screen != null) mc.screen.keyPressed(256, 0, 0);
    if (tick == 556)
      server(
          p -> {
            p.closeContainer();
            Lumberton unassigned = (Lumberton) p.serverLevel().getEntity(models.get(0));
            p.teleportTo(
                p.serverLevel(),
                unassigned.getX(),
                unassigned.getY(),
                unassigned.getZ() + 2,
                180,
                0);
            NetworkHooks.openScreen(
                p,
                unassigned,
                b -> {
                  b.writeBoolean(true);
                  b.writeInt(unassigned.getId());
                  b.writeVarInt(unassigned.cargo.getSlots());
                });
          });
    if (tick == 563 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().endsWith(" v"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 565 && mc.screen instanceof WorkScreen screen) {
      int x = (mc.getWindow().getGuiScaledWidth() - 300) / 2,
          y = (mc.getWindow().getGuiScaledHeight() - 234) / 2;
      screen.mouseClicked(x + 200, y + 166 + 18 + 6, 0);
    }
    if (tick == 567 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().equals("Apply Job"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 573) {
      if (!(mc.screen instanceof WorkScreen screen)
          || screen.hasPopup()
          || screen.getMenu().snapshot.getInt("Job") != 1)
        throw new IllegalStateException("First job was not applied without confirmation");
      System.out.println("AUTOMATONS_FIRST_JOB noConfirmation=true job=Lumberton");
      shot("first-job-no-confirmation");
    }
    if (tick == 575)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), 6.5, 64, 8, 180, 6);
            Lumberton runner = (Lumberton) p.serverLevel().getEntity(models.get(3));
            p.getInventory().setItem(0, WorkerIdentity.item(WorkerIdentity.portable(runner)));
          });
    if (tick == 585) camera(-3, 65, 3.5, 180, 15);
    if (tick == 595) shot("pedestal-texture");
    if (tick == 600)
      mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
    if (tick == 625) shot("packed-runner-item");
    if (tick == 630)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), -3, 64, 2, 180, 0);
            Station home = (Station) p.serverLevel().getBlockEntity(HOME);
            NetworkHooks.openScreen(
                p,
                home,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(HOME);
                });
          });
    if (tick == 648) shot("pedestal-menu");
    if (tick == 655) {
      mc.options.hideGui = true;
      server(
          p -> {
            p.closeContainer();
            var l = p.serverLevel();
            for (UUID id : models) if (l.getEntity(id) != null) l.getEntity(id).discard();
            Station home = (Station) l.getBlockEntity(HOME),
                anchor = (Station) l.getBlockEntity(ANCHOR);
            WorldData.get(l).buildings.get(anchor.identity).ready = true;
            anchor.changed();
            for (int x = -15; x <= -2; x++)
              for (int z = 4; z <= 18; z++) {
                l.setBlock(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 64; y <= 65; y++)
                  l.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
              }
            for (int x = -14; x <= -2; x++)
              for (int y = 64; y <= 65; y++)
                l.setBlock(new BlockPos(x, y, 5), Blocks.AIR.defaultBlockState(), 3);
            for (int z = 5; z <= 17; z++)
              for (int y = 64; y <= 65; y++)
                l.setBlock(new BlockPos(-14, y, z), Blocks.AIR.defaultBlockState(), 3);
            var door =
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            l.setBlock(PASSAGE_DOOR, door, 2);
            l.setBlock(
                PASSAGE_DOOR.above(),
                door.setValue(
                    DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
                2);
            BlockPos site = new BlockPos(-14, 64, 18);
            l.setBlock(site, Automatons.TOTEM.get().defaultBlockState(), 3);
            Station work = (Station) l.getBlockEntity(site);
            work.owner = home.owner;
            Lumberton recipient = Automatons.LUMBERTON.get().create(l);
            recipient.owner = home.owner;
            recipient.homeId = home.identity;
            recipient.home = HOME;
            recipient.homeDimension = l.dimension().location().toString();
            recipient.setJob(1);
            recipient.setNoAi(true);
            recipient.showNames(false);
            recipient.moveTo(-13.5, 64, 17.5, 180, 0);
            l.addFreshEntity(recipient);
            recipient.assign(work);
            recipientId = recipient.getUUID();
            Lumberton courier = Automatons.LUMBERTON.get().create(l);
            courier.owner = home.owner;
            courier.homeId = home.identity;
            courier.home = HOME;
            courier.homeDimension = l.dimension().location().toString();
            courier.setJob(3);
            courier.showNames(false);
            courier.moveTo(8.5, 64, -5.5, 0, 0);
            l.addFreshEntity(courier);
            courier.assign(anchor);
            courierId = courier.getUUID();
            Balance.DEBUG_WORK.set(true);
            BuildingProject warehouse = WorldData.get(l).buildings.get(anchor.identity);
            RunningGameTests.carriedParcel(courier, recipient, new ItemStack(Items.IRON_AXE));
          });
    }
    if (tick > 660 && tick % 5 == 0 && finishAt == 0)
      server(
          p -> {
            var l = p.serverLevel();
            Lumberton courier = (Lumberton) l.getEntity(courierId),
                recipient = (Lumberton) l.getEntity(recipientId);
            if (courier == null || recipient == null) return;
            if (tick % 200 == 0)
              System.out.println(
                  "AUTOMATONS_RUNNING_ROUTE tick="
                      + tick
                      + " status="
                      + courier.status()
                      + " position="
                      + courier.position()
                      + " path="
                      + courier.getNavigation().getPath()
                      + " cargo="
                      + courier.cargo.serializeNBT()
                      + " reports="
                      + LogisticsNetwork.get(courier).reports.values().stream()
                          .filter(r -> r.loaded)
                          .count());
            p.teleportTo(l, courier.getX(), courier.getY() + 6, courier.getZ() + 4, 180, 58);
            doorOpened |= l.getBlockState(PASSAGE_DOOR).getValue(DoorBlock.OPEN);
            if (passagePhase == 0
                && courier.getX() < -5
                && courier.getZ() > 4
                && courier.getZ() < 6) {
              passagePhase = 1;
              pendingShot = "corridor-door";
            }
            if (passagePhase == 1 && courier.getX() < -12 && courier.getZ() > 7) {
              passagePhase = 2;
              pendingShot = "corridor-corner";
            }
            if (Inventories.axe(recipient.activeTool())) {
              if (!doorOpened || passagePhase != 2 || courier.distanceToSqr(recipient) > 30) {
                System.out.println(
                    "AUTOMATONS_RUNNING_PASSAGE_FAILED door="
                        + doorOpened
                        + " phase="
                        + passagePhase
                        + " distance="
                        + courier.distanceToSqr(recipient)
                        + " courier="
                        + courier.position()
                        + " recipient="
                        + recipient.position());
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
                return;
              }
              courier.setNoAi(true);
              pendingShot = "physical-handoff";
              finishAt = tick + 30;
              System.out.println(
                  "AUTOMATONS_RUNNING_PASSAGE door=true corner=true physicalHandoff=true width="
                      + courier.getBbWidth()
                      + " height="
                      + courier.getBbHeight());
            }
          });
    if (!pendingShot.isEmpty()) {
      shot(pendingShot);
      pendingShot = "";
    }
    if (finishAt > 0 && tick >= finishAt) {
      System.out.println("AUTOMATONS_RUNNING_VISUAL_COMPLETE");
      mc.stop();
    }
    if (tick == 2400) {
      System.out.println("AUTOMATONS_RUNNING_PASSAGE_FAILED timeout");
      mc.stop();
    }
  }
}
