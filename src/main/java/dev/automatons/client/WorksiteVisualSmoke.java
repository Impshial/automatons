package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.network.NetworkHooks;

/** Opt-in packaged-client review; runs only in the isolated Automatons Lab save. */
public final class WorksiteVisualSmoke {
  private static final List<BlockPos> sites = new ArrayList<>();
  private static final List<UUID> workers = new ArrayList<>();
  private static final BlockPos HOME = new BlockPos(-3, 64, 0), SETUP = new BlockPos(14, 64, 15);

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
        "automatons-worksite-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("AUTOMATONS_WORKSITE_IMAGE " + name));
  }

  private static void menu(ServerPlayer p, BlockPos at) {
    p.closeContainer();
    p.teleportTo(p.serverLevel(), at.getX() + 2, 64, at.getZ() + 2, 135, 10);
    var site = (Station) p.serverLevel().getBlockEntity(at);
    NetworkHooks.openScreen(
        p,
        site,
        b -> {
          b.writeBoolean(false);
          b.writeBlockPos(at);
        });
  }

  private static void tab(int index) {
    var mc = Minecraft.getInstance();
    if (!(mc.screen instanceof WorkScreen screen))
      throw new IllegalStateException("Missing worksite screen");
    screen.professionPopup = true;
    int w = Math.min(420, mc.getWindow().getGuiScaledWidth() - 16),
        h = Math.min(260, mc.getWindow().getGuiScaledHeight() - 16);
    int x = (mc.getWindow().getGuiScaledWidth() - w) / 2,
        y = (mc.getWindow().getGuiScaledHeight() - h) / 2;
    screen.mouseClicked(x + 12 + index * ((w - 24) / 5) + 10, y + 40, 0);
  }

  public static void tick(int tick) {
    var mc = Minecraft.getInstance();
    if (tick == 5)
      server(
          p -> {
            var l = p.serverLevel();
            l.getEntitiesOfClass(
                    Lumberton.class, new net.minecraft.world.phys.AABB(-30, 60, -30, 85, 90, 30))
                .forEach(
                    w -> {
                      w.unassign();
                      Ledger.get(l.getServer()).workers.remove(w.getUUID());
                      w.discard();
                    });
            WorldData.get(l).buildings.clear();
            WorldData.get(l).farms.clear();
            for (int x = -30; x < 85; x++)
              for (int z = -30; z < 30; z++) {
                l.setBlock(new BlockPos(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 64; y < 75; y++)
                  if (!new BlockPos(x, y, z).equals(HOME))
                    l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
              }
            var home = (Station) l.getBlockEntity(HOME);
            home.workers.clear();
            home.owner = p.getUUID();
            home.registerHome();
            int index = 0;
            for (var purpose : WorksitePurpose.values()) {
              if (purpose == WorksitePurpose.UNCONFIGURED) continue;
              BlockPos at = new BlockPos(-20 + index++ * 20, 64, -8);
              sites.add(at);
              l.setBlock(at, Automatons.TOTEM.get().defaultBlockState(), 3);
              Station site = (Station) l.getBlockEntity(at);
              site.owner = p.getUUID();
              site.logisticsHome = home.identity;
              if (!site.configure(purpose))
                throw new IllegalStateException("Worksite setup rejected: " + purpose);
              var project = site.project();
              var template = project.template(l);
              for (var cell : template.cells) {
                if (cell.local().getY() == 0 && cell.state().isAir()) continue;
                BlockPos pos = template.world(at, Direction.NORTH, cell.local());
                if (!pos.equals(at)) l.setBlock(pos, cell.state(), 2);
                if (!cell.state().isAir()) project.completed.add(pos.asLong());
              }
              for (int i = 0; i < template.frames.size(); i++) {
                var display = template.frames.get(i);
                var frame =
                    new net.minecraft.world.entity.decoration.ItemFrame(
                        l, template.world(at, Direction.NORTH, display.local()), Direction.NORTH);
                frame.setItem(new ItemStack(display.item()), false);
                l.addFreshEntity(frame);
                project.completedFrames.add(i);
              }
              project.ready = true;
              project.reason = project.purpose.label + " ready";
              if (purpose == WorksitePurpose.FARM) {
                site.farm = new FarmPlan();
                WorldData.get(l).farms.put(site.identity, site.farm);
              }
              site.changed();
            }
            for (int job = 0; job < 6; job++) {
              Lumberton worker = Automatons.LUMBERTON.get().create(l);
              worker.owner = p.getUUID();
              worker.home = HOME;
              worker.homeId = home.identity;
              worker.homeDimension = l.dimension().location().toString();
              worker.setJob(job);
              worker.setNoAi(true);
              worker.showNames(false);
              worker.moveTo(-10 + job * 4 + .5, 64, 12.5, 0, 0);
              l.addFreshEntity(worker);
              worker.assign((Station) l.getBlockEntity(sites.get(job == 4 ? 4 : job == 5 ? 2 : 0)));
              worker.state(job == 0 ? "Unassigned" : "Idle at worksite");
              workers.add(worker.getUUID());
              if (job == 4) worker.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
              if (job == 5) {
                worker.tools.setStackInSlot(0, new ItemStack(Items.IRON_HOE));
                worker.cargo.setStackInSlot(0, new ItemStack(Items.CARROT, 32));
              }
            }
            l.setBlock(SETUP, Automatons.TOTEM.get().defaultBlockState(), 3);
            ((Station) l.getBlockEntity(SETUP)).owner = p.getUUID();
            p.teleportTo(l, 1, 67, 26, 180, 12);
          });
    // Let the packaged renderer rebuild the large fixture before any captures.
    if (tick < 505) return;
    tick -= 500;
    if (tick == 30) mc.options.hideGui = true;
    for (int i = 0; i < 5; i++) {
      final int index = i;
      if (tick == 80 + i * 50)
        server(
            p -> {
              var at = sites.get(index);
              p.teleportTo(p.serverLevel(), at.getX() - 12, 71, at.getZ() - 15, -40, 25);
            });
      if (tick == 115 + i * 50) shot("building-" + WorksitePurpose.of(i + 1).blueprint);
    }
    if (tick == 340) server(p -> p.teleportTo(p.serverLevel(), 6.5, 64, 17, 180, 5));
    if (tick == 365) shot("buildington-hammer");
    if (tick == 380) server(p -> p.teleportTo(p.serverLevel(), 10.5, 64, 17, 180, 5));
    if (tick == 405) shot("farmington-hoe");
    if (tick == 420) {
      mc.options.hideGui = false;
      server(p -> menu(p, SETUP));
    }
    if (tick == 435) tab(3);
    if (tick == 445) shot("purpose-setup");
    if (tick == 460) server(p -> menu(p, sites.get(2)));
    if (tick == 475) tab(3);
    if (tick == 485) shot("farm-worksite");
    if (tick == 495) tab(4);
    if (tick == 505) shot("farm-settings");
    if (tick == 515 && mc.screen instanceof WorkScreen screen) {
      screen.buildPopup = true;
      screen.warehousePopup = true;
    }
    if (tick == 525) shot("farm-build-list");
    if (tick == 540)
      server(
          p -> {
            p.closeContainer();
            var w = (Lumberton) p.serverLevel().getEntity(workers.get(4));
            p.teleportTo(p.serverLevel(), 6.5, 64, 15, 180, 5);
            NetworkHooks.openScreen(
                p,
                w,
                b -> {
                  b.writeBoolean(true);
                  b.writeInt(w.getId());
                  b.writeVarInt(w.cargo.getSlots());
                });
          });
    if (tick == 555 && mc.screen != null)
      mc.screen.children().stream()
          .filter(
              c ->
                  c instanceof net.minecraft.client.gui.components.Button b
                      && b.getMessage().getString().endsWith(" v"))
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .findFirst()
          .orElseThrow()
          .onPress();
    if (tick == 565) shot("profession-list");
    if (tick == 580) server(p -> menu(p, sites.get(4)));
    if (tick == 600) shot("totem-workers-tabs");
    if (tick == 615) server(p -> menu(p, HOME));
    if (tick == 635) shot("pedestal-network");
    if (tick == 650) {
      mc.options.hideGui = true;
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), -3, 65, 4, 180, 15);
          });
    }
    if (tick == 675) shot("pedestal-projection");
    if (tick == 690) {
      var site = (Station) mc.level.getBlockEntity(sites.get(0));
      RangeOverlay.toggle(site);
      server(p -> p.teleportTo(p.serverLevel(), -20, 76, 6, 180, 55));
    }
    if (tick == 725) shot("single-mine-range");
    if (tick == 740) {
      RangeOverlay.toggle((Station) mc.level.getBlockEntity(sites.get(0)));
      server(
          p -> {
            for (int job = 0; job < workers.size(); job++) {
              var w = (Lumberton) p.serverLevel().getEntity(workers.get(job));
              if (w == null) throw new IllegalStateException("Missing review worker " + job);
              w.moveTo(-10 + job * 4 + .5, 64, 12.5, 0, 0);
            }
            p.teleportTo(p.serverLevel(), .5, 66, 28, 180, 10);
          });
    }
    if (tick == 780) shot("six-professions");
    if (tick == 800) {
      System.out.println("AUTOMATONS_WORKSITE_VISUAL_COMPLETE");
      mc.stop();
    }
  }
}
