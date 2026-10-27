package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
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
    if (tick == start + 200) {
      System.out.println("AUTOMATONS_WORKSITE_VISUAL_COMPLETE");
      mc.stop();
    }
  }
}
