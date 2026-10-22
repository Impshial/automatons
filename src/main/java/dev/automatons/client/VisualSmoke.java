package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/** Opt-in development harness. Never runs in normal Minecraft launches. */
@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class VisualSmoke {
  private static boolean opened, setup;
  private static int ticks;
  private static java.util.UUID modelId;
  private static final BlockPos HOME = new BlockPos(-3, 64, 0), TOTEM = new BlockPos(3, 64, 0);

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent e) {
    if (!Boolean.getBoolean("automatons.visualSmoke") || e.phase != TickEvent.Phase.END) return;
    var mc = Minecraft.getInstance();
    mc.options.pauseOnLostFocus = false;
    if (!opened && mc.screen instanceof TitleScreen) {
      opened = true;
      mc.createWorldOpenFlows().loadLevel(mc.screen, "Automatons Lab");
      return;
    }
    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
    if (!setup) {
      setup = true;
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var server = mc.getSingleplayerServer();
                ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
                var l = p.serverLevel();
                l.getEntitiesOfClass(
                        Lumberton.class,
                        new net.minecraft.world.phys.AABB(-16, 60, -16, 16, 76, 16))
                    .forEach(net.minecraft.world.entity.Entity::discard);
                l.getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(-16, 60, -16, 16, 76, 16))
                    .forEach(net.minecraft.world.entity.Entity::discard);
                l.getGameRules()
                    .getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING)
                    .set(3, l.getServer());
                l.setDayTime(6000);
                l.setWeatherParameters(0, 100000, false, false);
                for (int x = -8; x <= 12; x++)
                  for (int z = -8; z <= 12; z++) {
                    for (int y = 64; y < 72; y++)
                      l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    l.setBlock(new BlockPos(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                  }
                l.setBlock(HOME, Automatons.PEDESTAL.get().defaultBlockState(), 3);
                l.setBlock(TOTEM, Automatons.TOTEM.get().defaultBlockState(), 3);
                Station home = (Station) l.getBlockEntity(HOME),
                    totem = (Station) l.getBlockEntity(TOTEM);
                home.owner = p.getUUID();
                totem.owner = p.getUUID();
                home.registerHome();
                home.inventory.setStackInSlot(0, new ItemStack(Items.OAK_LOG, 6));
                home.inventory.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 4));
                home.inventory.setStackInSlot(2, new ItemStack(Items.AMETHYST_SHARD));
                home.inventory.setStackInSlot(3, new ItemStack(Items.IRON_AXE));
                Lumberton w = Automatons.LUMBERTON.get().create(l);
                w.owner = p.getUUID();
                w.home = HOME;
                w.homeId = home.identity;
                w.homeDimension = l.dimension().location().toString();
                w.moveTo(.5, 64, .5, 0, 0);
                w.setNoAi(true);
                w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
                w.tools.setStackInSlot(1, new ItemStack(Items.STONE_AXE));
                w.assign(totem);
                l.addFreshEntity(w);
                modelId = w.getUUID();
                l.setBlock(TOTEM.offset(1, 0, 1), Blocks.CHEST.defaultBlockState(), 3);
                totem.inventory.setStackInSlot(
                    0, new ItemStack(Automatons.UPGRADES.get("storage_1").get()));
                p.setGameMode(GameType.CREATIVE);
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.teleportTo(l, .5, 64, 4.3, 180, 8);
                p.getInventory().setItem(0, new ItemStack(Automatons.WAND.get()));
              });
      return;
    }
    ticks++;
    if (ticks == 100
        || ticks == 180
        || ticks == 260
        || ticks == 340
        || ticks == 420
        || ticks == 480) {
      String name =
          switch (ticks) {
            case 100 -> "01-model";
            case 180 -> "02-worker-menu";
            case 260 -> "03-pedestal-menu";
            case 340 -> "04-totem-menu";
            case 420 -> "05-protect-curtain";
            default -> "06-revoke-curtain";
          };
      Screenshot.grab(
          mc.gameDirectory,
          "automatons-" + name + ".png",
          mc.getMainRenderTarget(),
          c -> System.out.println("AUTOMATONS_VISUAL: " + c.getString()));
    }
    if (ticks == 140 || ticks == 220 || ticks == 300 || ticks == 380 || ticks == 450) {
      int phase = ticks;
      mc.getSingleplayerServer()
          .execute(
              () -> {
                ServerPlayer p =
                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var l = p.serverLevel();
                p.closeContainer();
                if (phase == 140 && l.getEntity(modelId) instanceof Lumberton w)
                  NetworkHooks.openScreen(
                      p,
                      w,
                      b -> {
                        b.writeBoolean(true);
                        b.writeInt(w.getId());
                      });
                if (phase == 220 || phase == 300) {
                  BlockPos pos = phase == 220 ? HOME : TOTEM;
                  Station s = (Station) l.getBlockEntity(pos);
                  p.teleportTo(l, pos.getX() + .5, 64, pos.getZ() + 3.5, 180, 8);
                  NetworkHooks.openScreen(
                      p,
                      s,
                      b -> {
                        b.writeBoolean(false);
                        b.writeBlockPos(pos);
                      });
                }
                if (phase == 380 || phase == 450) {
                  p.teleportTo(l, 7, 70, 24, 180, 8);
                  p.getMainHandItem().getOrCreateTag().putInt("Mode", phase == 380 ? 1 : 2);
                  var d = WorldData.get(l);
                  d.protection.put(net.minecraft.world.level.ChunkPos.asLong(0, 0), p.getUUID());
                  d.protection.put(net.minecraft.world.level.ChunkPos.asLong(1, 0), p.getUUID());
                  Network.protections(p);
                }
              });
    }
    if (ticks == 500) {
      mc.getSingleplayerServer()
          .execute(
              () -> {
                ServerPlayer p =
                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var l = p.serverLevel();
                WorldData.get(l).protection.clear();
                Network.protections(p);
                p.getMainHandItem().getOrCreateTag().putInt("Mode", 0);
                p.teleportTo(l, 1, 67, 7, 180, 20);
                BlockPos base = new BlockPos(-3, 64, -4);
                for (int y = 0; y < 5; y++)
                  l.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 3);
                for (int x = -2; x <= 2; x++)
                  for (int z = -2; z <= 2; z++)
                    for (int y = 3; y <= 5; y++)
                      if (x != 0 || z != 0 || y == 5)
                        l.setBlock(
                            base.offset(x, y, z),
                            Blocks.OAK_LEAVES
                                .defaultBlockState()
                                .setValue(
                                    net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)
                                .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1),
                            3);
                if (l.getEntity(modelId) instanceof Lumberton w) {
                  w.setNoAi(false);
                  Station s = (Station) l.getBlockEntity(TOTEM);
                  s.chests.add(TOTEM.offset(1, 0, 1));
                  s.storageComplete = true;
                  var tree = TreeFinder.inspect(l, base, s);
                  if (tree.tree() != null) s.trees.put(base.asLong(), tree.tree());
                }
              });
    }
    if (ticks >= 530 && ticks <= 1100 && ticks % 30 == 20) {
      Screenshot.grab(
          mc.gameDirectory, "automatons-work-" + ticks + ".png", mc.getMainRenderTarget(), c -> {});
      int now = ticks;
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var w = mc.getSingleplayerServer().overworld().getEntity(modelId);
                if (w instanceof Lumberton worker)
                  System.out.println(
                      "AUTOMATONS_WORK "
                          + now
                          + " "
                          + worker.status()
                          + " "
                          + worker.blockPosition());
              });
    }
    if (ticks == 1140) mc.stop();
  }
}
