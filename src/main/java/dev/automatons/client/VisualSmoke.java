package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/** Opt-in development-only scenario; it touches only the separate Automatons Lab save. */
@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class VisualSmoke {
  private static boolean opened, setup;
  private static int ticks, sounds;
  private static java.util.UUID modelId;
  private static final BlockPos HOME = new BlockPos(-3, 64, 0),
      TOTEM = new BlockPos(3, 64, 0),
      INFUSER = new BlockPos(-3, 64, 4);

  private static void server(java.util.function.Consumer<ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    mc.getSingleplayerServer()
        .execute(
            () -> {
              var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
              if (p != null) action.accept(p);
            });
  }

  private static void image(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "automatons-redesign-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("AUTOMATONS_VISUAL " + name));
  }

  private static void menu(ServerPlayer p, Station station) {
    NetworkHooks.openScreen(
        p,
        station,
        b -> {
          b.writeBoolean(false);
          b.writeBlockPos(station.getBlockPos());
        });
  }

  @SubscribeEvent
  public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e) {
    if (!Boolean.getBoolean("automatons.visualSmoke") || e.getSound() == null) return;
    String name = e.getSound().getLocation().toString();
    if (name.contains("wood.hit")
        || name.contains("wood.break")
        || name.contains("amethyst_block.chime")) {
      sounds++;
      System.out.println(
          "AUTOMATONS_SOUND tick="
              + ticks
              + " name="
              + name
              + " xyz="
              + e.getSound().getX()
              + ","
              + e.getSound().getY()
              + ","
              + e.getSound().getZ());
    }
  }

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
      mc.options.guiScale().set(3);
      mc.resizeDisplay();
      server(
          p -> {
            var l = p.serverLevel();
            AABB area = new AABB(-20, 60, -20, 24, 90, 24);
            l.getEntitiesOfClass(
                    net.minecraft.world.entity.Entity.class,
                    area,
                    e2 ->
                        e2 instanceof Lumberton
                            || e2 instanceof Timber
                            || e2 instanceof net.minecraft.world.entity.item.ItemEntity
                            || e2 instanceof MagicaOrb)
                .forEach(net.minecraft.world.entity.Entity::discard);
            WorldData.get(l).protection.clear();
            Network.protections(p);
            l.setDayTime(6000);
            l.setWeatherParameters(0, 100000, false, false);
            for (int x = -12; x <= 16; x++)
              for (int z = -12; z <= 16; z++) {
                for (int y = 64; y < 84; y++)
                  l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(new BlockPos(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
              }
            l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)
                .forEach(net.minecraft.world.entity.Entity::discard);
            l.setBlock(HOME, Automatons.PEDESTAL.get().defaultBlockState(), 3);
            l.setBlock(TOTEM, Automatons.TOTEM.get().defaultBlockState(), 3);
            l.setBlock(INFUSER, Automatons.INFUSER.get().defaultBlockState(), 3);
            Station home = (Station) l.getBlockEntity(HOME),
                totem = (Station) l.getBlockEntity(TOTEM);
            home.owner = p.getUUID();
            home.registerHome();
            totem.owner = p.getUUID();
            Item[] parts = {
              Automatons.HEAD.get(),
              Automatons.ARM.get(),
              Automatons.BODY.get(),
              Automatons.ARM.get(),
              Automatons.LEG.get(),
              Automatons.LEG.get()
            };
            for (int i = 0; i < 6; i++)
              home.inventory.setStackInSlot(i, new ItemStack(parts[i], 2));
            for (int i = 0; i < 3; i++) {
              Lumberton w = Automatons.LUMBERTON.get().create(l);
              w.owner = p.getUUID();
              w.home = HOME;
              w.homeId = home.identity;
              w.homeDimension = l.dimension().location().toString();
              w.moveTo(.5 + i * 1.6, 64, .5, 0, 0);
              w.authorized = false;
              w.tools.setStackInSlot(0, new ItemStack(Items.IRON_AXE));
              w.tools.setStackInSlot(1, new ItemStack(Items.STONE_AXE));
              w.assign(totem);
              w.authorized = false;
              home.workers.add(w.getUUID());
              l.addFreshEntity(w);
              WorkerIdentity.update(w, "Deployed");
              if (i == 0) modelId = w.getUUID();
            }
            l.setBlock(TOTEM.offset(1, 0, 1), Blocks.CHEST.defaultBlockState(), 3);
            totem.inventory.setStackInSlot(
                0, new ItemStack(Automatons.UPGRADES.get("storage_1").get()));
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(l, .5, 64, 5.8, 180, 8);
            p.getInventory().clearContent();
            p.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Automatons.WAND.get()));
            Magica.add(p, 500 - Magica.balance(p));
            p.giveExperiencePoints(140);
          });
      return;
    }
    ticks++;
    if (ticks == 80) {
      image("01-model-hud");
      server(
          p ->
              System.out.println(
                  "AUTOMATONS_XP level=" + p.experienceLevel + " total=" + p.totalExperience));
    }
    if (ticks == 100)
      server(
          p -> {
            if (p.serverLevel().getEntity(modelId) instanceof Lumberton w)
              NetworkHooks.openScreen(
                  p,
                  w,
                  b -> {
                    b.writeBoolean(true);
                    b.writeInt(w.getId());
                    b.writeVarInt(w.cargo.getSlots());
                  });
          });
    if (ticks == 130) image("02-worker-gui");
    if (ticks == 150)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), HOME.getX() + .5, 64, HOME.getZ() + 3.5, 180, 8);
            menu(p, (Station) p.serverLevel().getBlockEntity(HOME));
          });
    if (ticks == 180) image("03-assembly-gui");
    if (ticks == 190) {
      mc.options.guiScale().set(4);
      mc.resizeDisplay();
    }
    if (ticks == 210) image("04-assembly-smallest-gui");
    if (ticks == 230) {
      mc.options.guiScale().set(3);
      mc.resizeDisplay();
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), TOTEM.getX() + .5, 64, TOTEM.getZ() + 3.5, 180, 8);
            menu(p, (Station) p.serverLevel().getBlockEntity(TOTEM));
          });
    }
    if (ticks == 260) image("05-shared-totem-gui");
    if (ticks == 280)
      server(
          p -> {
            p.closeContainer();
            p.teleportTo(p.serverLevel(), -3, 64, 3.3, 180, -5);
            Station home = (Station) p.serverLevel().getBlockEntity(HOME);
            for (int i = 0; i < 6; i++) home.inventory.setStackInSlot(i, ItemStack.EMPTY);
            Magica.add(p, -Magica.balance(p));
          });
    if (ticks == 310) image("06-empty-pedestal-hologram");
    if (ticks == 330)
      server(
          p -> {
            p.teleportTo(p.serverLevel(), -2, 64, 7.5, 165, 20);
            Magica.add(p, 50);
            Infuser inf = (Infuser) p.serverLevel().getBlockEntity(INFUSER);
            inf.core = CoreItem.vessel(0);
            inf.donor = p.getUUID();
            inf.changed();
          });
    if (ticks == 370) image("07-infusion-stream");
    if (ticks == 430) server(p -> p.teleportTo(p.serverLevel(), -3.3, 64, 7.5, 180, 20));
    if (ticks == 450) image("08-moving-donor-stream");
    if (ticks == 550) {
      image("09-completed-core");
      server(
          p -> {
            Infuser inf = (Infuser) p.serverLevel().getBlockEntity(INFUSER);
            System.out.println(
                "AUTOMATONS_INFUSION charge="
                    + CoreItem.charge(inf.core)
                    + " balance="
                    + Magica.balance(p));
          });
    }
    if (ticks == 552)
      server(p -> MagicaOrb.award(p.serverLevel(), p.position().add(0, .3, -1.3), 7));
    if (ticks == 560) image("12-magica-orbs");
    if (ticks == 568)
      server(
          p ->
              System.out.println(
                  "AUTOMATONS_MAGICA_PICKUP points="
                      + Magica.balance(p)
                      + " xp="
                      + p.totalExperience));
    if (ticks == 570)
      server(
          p -> {
            p.teleportTo(p.serverLevel(), 7, 70, 24, 180, 8);
            p.getMainHandItem().getOrCreateTag().putInt("Mode", 1);
            WorldData.get(p.serverLevel())
                .protection
                .put(net.minecraft.world.level.ChunkPos.asLong(0, 0), p.getUUID());
            WorldData.get(p.serverLevel())
                .protection
                .put(net.minecraft.world.level.ChunkPos.asLong(1, 0), p.getUUID());
            Network.protections(p);
          });
    if (ticks == 600) image("10-protect-curtain");
    if (ticks == 620) server(p -> p.getMainHandItem().getOrCreateTag().putInt("Mode", 2));
    if (ticks == 650) image("11-revoke-curtain");
    if (ticks == 670)
      server(
          p -> {
            var l = p.serverLevel();
            WorldData.get(l).protection.clear();
            WorldEvents.protectionChanged(l);
            Network.protections(p);
            p.getMainHandItem().getOrCreateTag().putInt("Mode", 0);
            p.teleportTo(l, 0, 67, 7, 180, 20);
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
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)
                            .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1),
                        3);
            if (l.getEntity(modelId) instanceof Lumberton w) {
              w.setNoAi(false);
              w.authorized = true;
            }
            WorldEvents.changedNear(l, base);
          });
    if (ticks >= 700 && ticks <= 3000 && ticks % 30 == 10) {
      image("work-" + ticks);
      int now = ticks;
      server(
          p -> {
            if (p.serverLevel().getEntity(modelId) instanceof Lumberton w)
              System.out.println("AUTOMATONS_WORK " + now + " " + w.diagnostic());
          });
    }
    if (ticks == 3010)
      server(
          p -> {
            if (p.serverLevel().getEntity(modelId) instanceof Lumberton w) {
              int cargo = 0;
              for (int slot = 0; slot < w.cargo.getSlots(); slot++)
                cargo += w.cargo.getStackInSlot(slot).getCount();
              int logs = 0;
              var be = p.serverLevel().getBlockEntity(TOTEM.offset(1, 0, 1));
              if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
                for (int slot = 0; slot < chest.getContainerSize(); slot++)
                  if (chest.getItem(slot).is(Items.OAK_LOG)) logs += chest.getItem(slot).getCount();
              System.out.println(
                  "AUTOMATONS_FINAL chestOakLogs="
                      + logs
                      + " cargoItems="
                      + cargo
                      + " state="
                      + w.status()
                      + " xp="
                      + p.totalExperience);
            }
          });
    if (ticks == Integer.getInteger("automatons.smokeTicks", 3030)) {
      System.out.println("AUTOMATONS_SOUND_EVENTS " + sounds);
      mc.stop();
    }
  }
}
