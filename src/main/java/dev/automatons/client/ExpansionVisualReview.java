package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkHooks;

/** Opt-in review fixture in the disposable Lab. Never enabled in ordinary play. */
public final class ExpansionVisualReview {
  private static final BlockPos HOME = new BlockPos(-3, 64, 0), SITE = new BlockPos(6, 64, 0);
  private static UUID shownWorker;

  private static void server(Consumer<ServerPlayer> action) {
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
    if (!(mc.screen instanceof ProfessionScreen))
      throw new IllegalStateException(
          "Missing expansion menu for "
              + name
              + ": screen="
              + mc.screen
              + ", menu="
              + mc.player.containerMenu
              + ", feet="
              + mc.player.position());
    Screenshot.grab(
        mc.gameDirectory,
        "automatons-expansion-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("AUTOMATONS_EXPANSION_IMAGE " + name));
  }

  private static void press(String label) {
    var mc = Minecraft.getInstance();
    if (mc.screen == null) throw new IllegalStateException("Missing screen for " + label);
    mc.screen.children().stream()
        .filter(c -> c instanceof Button b && b.getMessage().getString().equals(label) && b.active)
        .map(c -> (Button) c)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing button " + label))
        .onPress();
  }

  private static void site(ServerPlayer p, WorksitePurpose purpose) {
    var l = p.serverLevel();
    p.closeContainer();
    if (l.getBlockEntity(SITE) instanceof Station old) {
      if (old.mine != null) WorldData.get(l).mines.remove(old.mine.id);
      WorldData.get(l).buildings.remove(old.identity);
      WorldData.get(l).farms.remove(old.identity);
      old.workers.clear();
      old.guards.clear();
    }
    l.setBlock(SITE, Blocks.AIR.defaultBlockState(), 3);
    l.setBlock(SITE, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station s = (Station) l.getBlockEntity(SITE), home = (Station) l.getBlockEntity(HOME);
    s.owner = p.getUUID();
    s.logisticsHome = home.identity;
    s.purpose = purpose;
    s.customName =
        purpose == WorksitePurpose.UNCONFIGURED ? "New Worksite" : purpose.label + " at Eastfield";
    if (purpose != WorksitePurpose.UNCONFIGURED) {
      BuildingProject project = new BuildingProject();
      project.owner = p.getUUID();
      project.home = home.identity;
      project.totem = s.identity;
      project.anchor = SITE;
      project.front = Direction.SOUTH;
      project.purpose = purpose;
      project.approved = true;
      project.reason = "Waiting for construction supplies";
      WorldData.get(l).buildings.put(s.identity, project);
      if (purpose == WorksitePurpose.FARM) {
        s.farm = new FarmPlan();
        s.farm.select(0, new ItemStack(Items.WHEAT));
        s.farm.select(1, new ItemStack(Items.CARROT));
      }
      if (purpose == WorksitePurpose.CRAFT) {
        s.craftOrders.put(Items.IRON_PICKAXE, 2);
        s.craftOrders.put(Items.TORCH, 32);
        s.craftOrders.put(Items.OAK_PLANKS, 48);
      }
      if (purpose == WorksitePurpose.MINE) {
        s.mine = new MinePlan();
        s.mine.entrance = SITE;
        s.mine.forward = Direction.SOUTH;
        s.mine.strip = new StripMine();
        s.mine.stage = MinePlan.Stage.READY;
        s.mine.depotBuilt = true;
        s.mine.strip.current().corridor = 150;
        s.mine.strip.current().branches.get(0).cell = 48;
        s.mine.strip.current().branches.get(1).stopped = "Water at passage 2";
        s.mine.reason = "Mining fixed passages";
      }
      if (purpose == WorksitePurpose.QUARRY) {
        s.mine = new MinePlan();
        s.mine.entrance = new SiteLayout(project, l).shaft();
        s.mine.forward = Direction.SOUTH;
        s.mine.quarry = QuarryPlan.create(l, project);
        s.mine.quarry.layer = 4;
        s.mine.quarry.done.set(0, 37);
        s.mine.shaftDepth = 5;
        s.mine.stage = MinePlan.Stage.READY;
        s.mine.reason = "Waiting for ladders";
      }
    }
    s.changed();
    p.teleportTo(l, 7.5, 64, 2.5, 140, 10);
    p.getInventory().setItem(9, new ItemStack(Items.WHEAT));
    p.getInventory().setItem(10, new ItemStack(Items.CARROT));
    p.getInventory().setItem(11, new ItemStack(Items.POTATO));
    p.getInventory().setItem(12, new ItemStack(Items.BEETROOT));
  }

  private static void openSite(ServerPlayer p) {
    Station s = (Station) p.serverLevel().getBlockEntity(SITE);
    NetworkHooks.openScreen(
        p,
        s,
        b -> {
          b.writeBoolean(false);
          b.writeBlockPos(SITE);
        });
    System.out.println(
        "EXPANSION_REVIEW_OPEN "
            + s.purpose
            + " menu="
            + p.containerMenu.containerId
            + " valid="
            + p.containerMenu.stillValid(p));
  }

  private static void worker(ServerPlayer p, int job) {
    var l = p.serverLevel();
    p.closeContainer();
    if (shownWorker != null && l.getEntity(shownWorker) instanceof Lumberton old) {
      old.unassign();
      old.discard();
    }
    Station home = (Station) l.getBlockEntity(HOME);
    Lumberton w =
        (job == WorkerJob.GUARDINGTON ? Automatons.GUARDINGTON : Automatons.LUMBERTON)
            .get()
            .create(l);
    w.owner = p.getUUID();
    w.homeId = home.identity;
    w.home = HOME;
    w.setJob(job);
    w.setNoAi(true);
    w.authorized = true;
    w.operating(148500);
    w.serviceReserve(job == WorkerJob.LUMINARITON ? 287000 : 0);
    w.setHealth(w.getMaxHealth() * .7f);
    w.moveTo(6.5, 64, 2.5, 180, 0);
    w.state(
        job == WorkerJob.GUARDINGTON
            ? "Patrolling network"
            : job == WorkerJob.LUMINARITON ? "Filling service reserve" : "Waiting for assignment");
    w.cargo.setStackInSlot(0, new ItemStack(Luminall.BOTTLE.get()));
    w.cargo.setStackInSlot(1, new ItemStack(Items.GLASS_BOTTLE, 7));
    if (job == WorkerJob.MININGTON) w.tools.setStackInSlot(0, new ItemStack(Items.IRON_PICKAXE));
    l.addFreshEntity(w);
    WorkerIdentity.update(w, "Deployed");
    shownWorker = w.getUUID();
    p.teleportTo(l, 8.5, 64, 2.5, 90, 0);
  }

  private static void openWorker(ServerPlayer p) {
    Lumberton w = (Lumberton) p.serverLevel().getEntity(shownWorker);
    NetworkHooks.openScreen(
        p,
        w,
        b -> {
          b.writeBoolean(true);
          b.writeInt(w.getId());
          b.writeVarInt(w.cargo.getSlots());
        });
  }

  private static void roster(ServerPlayer p) {
    site(p, WorksitePurpose.LUMINA);
    Station s = (Station) p.serverLevel().getBlockEntity(SITE),
        home = (Station) p.serverLevel().getBlockEntity(HOME);
    for (int i = 0; i < 6; i++) {
      Lumberton w =
          (i < 4 ? Automatons.LUMBERTON : Automatons.GUARDINGTON).get().create(p.serverLevel());
      w.owner = p.getUUID();
      w.homeId = home.identity;
      w.home = HOME;
      w.setNoAi(true);
      w.moveTo(6.5 + i, 64, 4.5);
      p.serverLevel().addFreshEntity(w);
      w.assign(s);
      w.operating(120000 + i * 7000);
      w.state(i < 4 ? "Ready for service" : "Protecting Eastfield");
      WorkerIdentity.update(w, "Deployed");
    }
  }

  public static void tick(int tick) {
    var mc = Minecraft.getInstance();
    if (tick == 5)
      server(
          p -> {
            var l = p.serverLevel();
            l.getEntitiesOfClass(
                    Lumberton.class, new net.minecraft.world.phys.AABB(-20, 60, -20, 30, 85, 30))
                .forEach(
                    w -> {
                      w.unassign();
                      w.discard();
                    });
            WorldData.get(l).buildings.clear();
            WorldData.get(l).farms.clear();
            for (int x = 0; x < 14; x++)
              for (int z = -5; z < 10; z++) {
                l.setBlock(new BlockPos(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int y = 64; y < 76; y++)
                  l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
              }
            Station home = (Station) l.getBlockEntity(HOME);
            home.owner = p.getUUID();
            home.workers.clear();
            home.registerHome();
            site(p, WorksitePurpose.UNCONFIGURED);
          });
    if (tick < 300) return;
    tick -= 300;
    if (tick == 60) server(ExpansionVisualReview::openSite);
    if (tick == 90) shot("00-choose-profession");
    for (int i = 1; i < WorksitePurpose.values().length; i++) {
      final int index = i;
      int start = 120 + (i - 1) * 80;
      if (tick == start) server(p -> site(p, WorksitePurpose.of(index)));
      if (tick == start + 10) server(ExpansionVisualReview::openSite);
      if (tick == start + 25)
        shot(
            String.format(
                Locale.ROOT,
                "%02d-%s-overview",
                i,
                WorksitePurpose.of(i).name().toLowerCase(Locale.ROOT)));
      if (tick == start + 35) press("Settings");
      if (tick == start + 55)
        shot(
            String.format(
                Locale.ROOT,
                "%02d-%s-settings",
                i,
                WorksitePurpose.of(i).name().toLowerCase(Locale.ROOT)));
    }
    int start = 1040;
    if (tick == start) server(p -> worker(p, WorkerJob.MININGTON));
    if (tick == start + 10) server(ExpansionVisualReview::openWorker);
    if (tick == start + 30) shot("worker-status");
    if (tick == start + 40) press("Inventory");
    if (tick == start + 60) shot("worker-inventory");
    if (tick == start + 80) server(p -> worker(p, WorkerJob.GUARDINGTON));
    if (tick == start + 90) server(ExpansionVisualReview::openWorker);
    if (tick == start + 110) shot("guard-patrol");
    if (tick == start + 140) server(p -> worker(p, WorkerJob.LUMINARITON));
    if (tick == start + 150) server(ExpansionVisualReview::openWorker);
    if (tick == start + 170) shot("mage-reserves");
    if (tick == start + 200) server(ExpansionVisualReview::roster);
    if (tick == start + 210) server(ExpansionVisualReview::openSite);
    if (tick == start + 230) press("Workers");
    if (tick == start + 250) shot("roster-production-and-guards");
    if (tick == start + 260) press(">");
    if (tick == start + 280) shot("roster-second-page");
    if (tick == start + 290) press("Name");
    if (tick == start + 310)
      mc.screen.children().stream()
          .filter(c -> c instanceof EditBox)
          .map(c -> (EditBox) c)
          .findFirst()
          .orElseThrow()
          .setValue("Eastfield Service");
    if (tick == start + 320) shot("rename-worksite");
    if (tick == start + 330) press("Save name");
    if (tick == start + 350) shot("renamed-roster");
    if (tick == start + 360)
      server(
          p -> {
            Station s = (Station) p.serverLevel().getBlockEntity(SITE);
            for (UUID id : List.copyOf(s.workers)) s.removeMember(id);
            for (UUID id : List.copyOf(s.guards))
              if (p.serverLevel().getEntity(id) instanceof Lumberton guard) guard.guarding.clear();
          });
    if (tick == start + 380) press("Settings");
    if (tick == start + 390) press("Change profession");
    if (tick == start + 410) shot("change-profession-chooser");
    if (tick == start + 420) press(WorksitePurpose.FARM.label);
    if (tick == start + 440) shot("change-profession-confirmation");
    if (tick == start + 450) press("Keep current profession");
    if (tick == start + 470) shot("change-profession-cancelled");
    if (tick == start + 480) press(WorksitePurpose.FARM.label);
    if (tick == start + 500) press("Confirm profession change");
    if (tick == start + 520) shot("changed-profession-paused");
    if (tick == start + 540) {
      mc.player.closeContainer();
      mc.setScreen(new AppearanceReviewScreen(false));
    }
    if (tick == start + 560)
      Screenshot.grab(
          mc.gameDirectory,
          "automatons-expansion-profession-appearances.png",
          mc.getMainRenderTarget(),
          c -> {});
    if (tick == start + 580) mc.setScreen(new AppearanceReviewScreen(true));
    if (tick == start + 600)
      Screenshot.grab(
          mc.gameDirectory,
          "automatons-expansion-powered-down-and-seated.png",
          mc.getMainRenderTarget(),
          c -> {});
    if (tick == start + 620) {
      System.out.println("AUTOMATONS_WORKSITE_VISUAL_COMPLETE");
      mc.stop();
    }
  }
}
