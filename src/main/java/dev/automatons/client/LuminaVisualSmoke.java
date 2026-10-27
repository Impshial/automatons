package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** Opt-in isolated packaged-client visual evidence; no normal-game behavior. */
public final class LuminaVisualSmoke {
  private static final BlockPos INFUSER = new BlockPos(-3, 64, 4), TOTEM = new BlockPos(3, 64, 0);

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
        "automatons-lumina-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("AUTOMATONS_LUMINA_IMAGE " + name));
  }

  private static void camera(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
    p.closeContainer();
    p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
  }

  public static void tick(int t) {
    var mc = Minecraft.getInstance();
    if (t == 5)
      server(
          p -> {
            var l = p.serverLevel();
            l.getEntitiesOfClass(Lumberton.class, new AABB(-20, 60, -20, 24, 90, 24))
                .forEach(
                    w -> {
                      w.unassign();
                      w.discard();
                    });
            for (int x = -6; x <= -1; x++)
              for (int y = 64; y <= 66; y++)
                l.setBlock(
                    new BlockPos(x, y, -5),
                    (x == -5 || x == -4
                            ? Automatons.LUMITE_ORE.get()
                            : x == -3 || x == -2
                                ? Automatons.DEEPSLATE_LUMITE_ORE.get()
                                : Blocks.STONE)
                        .defaultBlockState(),
                    3);
            // Opaque enclosed room; no lamps. Adjacent ore and ordinary rock share zero block
            // light.
            for (int x = 0; x <= 8; x++)
              for (int y = 63; y <= 69; y++)
                for (int z = -10; z <= -2; z++)
                  l.setBlock(
                      new BlockPos(x, y, z),
                      (x == 0 || x == 8 || y == 63 || y == 69 || z == -10 || z == -2
                              ? Blocks.STONE
                              : Blocks.AIR)
                          .defaultBlockState(),
                      3);
            for (int x = 2; x <= 6; x++)
              for (int y = 64; y <= 66; y++)
                l.setBlock(
                    new BlockPos(x, y, -10),
                    (x == 2 || x == 3
                            ? Automatons.LUMITE_ORE.get()
                            : x == 4 || x == 5
                                ? Automatons.DEEPSLATE_LUMITE_ORE.get()
                                : Blocks.STONE)
                        .defaultBlockState(),
                    3);
            // Opaque foreground blocks cover some ore faces to expose culling artifacts.
            l.setBlock(new BlockPos(2, 64, -9), Blocks.STONE.defaultBlockState(), 3);
            l.setBlock(new BlockPos(5, 64, -9), Blocks.DEEPSLATE.defaultBlockState(), 3);
            Lumberton w = Automatons.LUMBERTON.get().create(l);
            w.owner = p.getUUID();
            w.setJob(4);
            w.setNoAi(true);
            w.showNames(false);
            w.tools.setStackInSlot(0, new ItemStack(Automatons.HAMMER.get()));
            w.moveTo(6.5, 64, 6.5, 0, 0);
            l.addFreshEntity(w);
            l.setBlock(new BlockPos(8, 65, 5), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            var frame =
                new net.minecraft.world.entity.decoration.ItemFrame(
                    l, new BlockPos(8, 65, 6), Direction.SOUTH);
            frame.setItem(new ItemStack(Automatons.HAMMER.get()), false);
            l.addFreshEntity(frame);
            p.getInventory().clearContent();
            Item[] items = {
              Automatons.HAMMER.get(),
              Automatons.LUMITE_SHARD.get(),
              Automatons.INFUSED_LUMITE.get(),
              Automatons.LUMITE_ORE.get().asItem(),
              Automatons.DEEPSLATE_LUMITE_ORE.get().asItem(),
              Automatons.EMPTY_CORE.get(),
              Automatons.LUMINA_CORE.get(),
              Automatons.INFUSER.get().asItem()
            };
            for (int i = 0; i < items.length; i++)
              p.getInventory().setItem(i, new ItemStack(items[i]));
            p.getInventory().selected = 0;
            camera(p, -3.5, 64, 0, 180, 0);
            Lumina.clear(p);
            Lumina.add(p, 100);
          });
    if (t == 500) mc.options.hideGui = true;
    if (t == 530) shot("01-ore-daylight-adjacent");
    if (t == 545) server(p -> camera(p, 4.5, 64.5, -4.5, 180, 0));
    if (t == 580) shot("02-ore-zero-light-culling");
    if (t == 590)
      server(
          p -> {
            var l = p.serverLevel();
            BlockPos p1 = new BlockPos(3, 65, -9);
            System.out.println(
                "LUMINA_DARK_REVIEW blockLight="
                    + l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p1)
                    + " skyLight="
                    + l.getBrightness(net.minecraft.world.level.LightLayer.SKY, p1));
            camera(p, 6.6, 64, 9.5, 180, 4);
          });
    if (t == 625) shot("03-hammer-worker-frame");
    if (t == 635) {
      mc.options.hideGui = false;
      mc.options.guiScale().set(1);
      mc.resizeDisplay();
      mc.setScreen(new InventoryScreen(mc.player));
    }
    if (t == 655) shot("04-inventory-native-16px");
    if (t == 665) {
      mc.options.guiScale().set(3);
      mc.resizeDisplay();
      mc.setScreen(new InventoryScreen(mc.player));
    }
    if (t == 685) shot("05-inventory-items");
    if (t == 695) {
      mc.player.closeContainer();
      mc.options.hideGui = true;
      server(p -> camera(p, -.6, 65, 6.7, 138, 30));
    }
    for (int corner = 0; corner < 4; corner++) {
      final int i = corner;
      if (t == 710 + corner * 40)
        server(
            p -> {
              var inf = (Infuser) p.serverLevel().getBlockEntity(INFUSER);
              inf.shards[i] = new ItemStack(Automatons.LUMITE_SHARD.get());
              inf.changed();
            });
      if (t == 735 + corner * 40) shot("06-prongs-" + (corner + 1));
    }
    if (t == 870)
      server(
          p -> {
            var inf = (Infuser) p.serverLevel().getBlockEntity(INFUSER);
            for (int i = 0; i < 4; i++) inf.donors[i] = p.getUUID();
            inf.changed();
          });
    if (t == 940) shot("07-four-corner-transfer");
    if (t == 1030) server(p -> camera(p, 5, 65, 7, 138, 30));
    if (t == 1065) shot("08-transfer-paused-distance");
    if (t == 1080) server(p -> camera(p, -.6, 65, 6.7, 138, 30));
    if (t == 1315 || t == 1322 || t == 1330) shot("09-completion-" + t);
    if (t == 1350)
      server(
          p -> {
            var inf = (Infuser) p.serverLevel().getBlockEntity(INFUSER);
            if (Lumina.thirds(p) != 0
                || Arrays.stream(inf.shards).anyMatch(s -> !s.is(Automatons.INFUSED_LUMITE.get())))
              throw new IllegalStateException(
                  "Packaged infusion did not spend exactly 100 Lumina for four shards");
            System.out.println("LUMINA_CLIENT_BATCH fourInfused=true balanceThirds=0");
          });
    if (t == 1360) {
      mc.options.hideGui = false;
      server(
          p -> {
            camera(p, 4, 64, 3, 180, 10);
            var s = (Station) p.serverLevel().getBlockEntity(TOTEM);
            s.recoverRetired(p);
            NetworkHooks.openScreen(
                p,
                s,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(TOTEM);
                });
          });
    }
    if (t == 1385) shot("10-totem-no-upgrade-slots");
    if (t == 1400)
      try {
        Class.forName("dev.automatons.compat.jei.AutomatonsJeiPlugin")
            .getMethod("review", boolean.class)
            .invoke(null, true);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("JEI infusion review failed", failure);
      }
    if (t == 1425) shot("11-jei-infusion");
    if (t == 1440) {
      mc.player.closeContainer();
      mc.stop();
    }
  }
}
