package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/** Opt-in screenshots in the existing disposable development Lab; never runs in normal play. */
public final class LuminallVisualSmoke {
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
        "automatons-luminall-" + name + ".png",
        mc.getMainRenderTarget(),
        text -> System.out.println("LUMINALL_IMAGE " + name));
  }

  public static void tick(int tick) {
    var mc = Minecraft.getInstance();
    if (tick == 5) {
      if (new ItemStack(Automatons.HAMMER.get()).getMaxDamage() != 2000)
        throw new IllegalStateException("Hammer must have 2000 durability");
      mc.options.hideGui = true;
      server(
          p -> {
            var level = p.serverLevel();
            level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(-12, 60, -12, 16, 90, 16),
                    entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                .forEach(net.minecraft.world.entity.Entity::discard);
            for (int x = -5; x <= 6; x++)
              for (int z = -3; z <= 5; z++)
                for (int y = 64; y <= 67; y++)
                  level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            for (int i = 0; i < 3; i++) {
              BlockPos at = new BlockPos(-2 + i * 2, 64, 0);
              level.setBlock(
                  at, (i == 0 ? Luminall.TANK : Luminall.COLLECTOR).get().defaultBlockState(), 3);
              var machine = (Reservoir) level.getBlockEntity(at);
              if (i > 0) {
                machine.tank.fill(
                    new FluidStack(Luminall.SOURCE.get(), i == 1 ? 4000 : 1600),
                    FluidAction.EXECUTE);
                machine.bottle = LuminallBottle.create(250);
                machine.synchronize();
              }
            }
            for (int x = -1; x <= 1; x++)
              level.setBlock(new BlockPos(x, 64, -1), Blocks.POPPY.defaultBlockState(), 3);
            p.teleportTo(level, .5, 65.1, 6.2, 180, 15);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.getInventory().setItem(1, new ItemStack(Luminall.TANK_ITEM.get()));
            p.getInventory().setItem(2, new ItemStack(Luminall.COLLECTOR_ITEM.get()));
            p.getInventory().setItem(3, new ItemStack(Luminall.BUCKET.get()));
            p.getInventory().setItem(4, LuminallBottle.create(250));
            p.getInventory().selected = 0;
            p.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
          });
    }
    if (tick == 100) shot("machines");
    if (tick == 110)
      server(
          p -> {
            p.teleportTo(p.serverLevel(), .5, 64.8, 4.8, 180, 22);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Automatons.WAND.get()));
          });
    if (tick == 150) {
      if (!mc.player.getMainHandItem().is(Automatons.WAND.get())
          || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
          || !(mc.level.getBlockEntity(hit.getBlockPos()) instanceof Reservoir))
        throw new IllegalStateException(
            "Collector boundary screenshot did not target the Collector");
      shot("boundary");
    }
    if (tick == 160) {
      mc.options.hideGui = false;
      server(
          p -> {
            var level = p.serverLevel();
            var at = new BlockPos(0, 64, 3);
            level.setBlock(at, Automatons.INFUSER.get().defaultBlockState(), 3);
            var infuser = (Infuser) level.getBlockEntity(at);
            for (int i = 0; i < 4; i++)
              infuser.shards[i] = new ItemStack(Automatons.INFUSED_LUMITE.get());
            infuser.setChanged();
            level.sendBlockUpdated(at, infuser.getBlockState(), infuser.getBlockState(), 3);
            p.teleportTo(level, .5, 65, 5.9, 180, 25);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
          });
    }
    if (tick == 220) shot("raised-shards-and-items");
    if (tick == 240)
      server(
          p -> {
            var level = p.serverLevel();
            var at = new BlockPos(-3, 64, 4);
            level.setBlock(at, Automatons.PEDESTAL.get().defaultBlockState(), 3);
            var home = (Station) level.getBlockEntity(at);
            home.owner = p.getUUID();
            home.registerHome();
            WorksitePurpose[] purposes = {
              WorksitePurpose.BUILDER, WorksitePurpose.FARM, WorksitePurpose.LUMBER
            };
            for (int i = 0; i < purposes.length; i++) {
              BlockPos pos = new BlockPos(6 + i * 12, 64, -6);
              level.setBlock(pos, Automatons.TOTEM.get().defaultBlockState(), 3);
              var site = (Station) level.getBlockEntity(pos);
              site.owner = p.getUUID();
              site.logisticsHome = home.identity;
              site.purpose = purposes[i];
              var plan = new BuildingProject();
              plan.home = home.identity;
              plan.owner = p.getUUID();
              plan.totem = site.identity;
              plan.anchor = pos;
              plan.purpose = purposes[i];
              plan.created = i;
              plan.reason = i == 0 ? "Waiting for materials" : "Awaiting Buildington";
              WorldData.get(level).buildings.put(site.identity, plan);
            }
            net.minecraftforge.network.NetworkHooks.openScreen(
                p,
                home,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(at);
                });
          });
    if (tick == 280) {
      if (!(mc.screen instanceof WorkScreen screen)
          || screen.getMenu().snapshot.getList("ActiveBuilds", 10).size() != 3)
        throw new IllegalStateException("Pedestal did not receive active build lists");
      shot("pedestal-build-lists-button");
      screen.children().stream()
          .filter(c -> c instanceof net.minecraft.client.gui.components.Button)
          .map(c -> (net.minecraft.client.gui.components.Button) c)
          .filter(b -> b.getMessage().getString().startsWith("Build Lists"))
          .findFirst()
          .orElseThrow()
          .onPress();
    }
    if (tick == 300) {
      shot("pedestal-active-builds");
      var screen = (WorkScreen) mc.screen;
      screen.mouseClicked((screen.width - 320) / 2.0 + 20, (screen.height - 216) / 2.0 + 55, 0);
    }
    if (tick == 320) shot("pedestal-build-materials");
    if (tick == 330) ((WorkScreen) mc.screen).mouseScrolled(0, 0, -4);
    if (tick == 345) shot("pedestal-build-materials-scrolled");
    if (tick == 360) {
      System.out.println("LUMINALL_VISUAL_COMPLETE");
      mc.stop();
    }
  }
}
