package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
    modid = Automatons.ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class ClientSetup {
  public static final Set<Long> protectedChunks = new HashSet<>();
  public static int lumina, luminaThirds;
  public static String protectionDimension = "";

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent e) {
    e.enqueueWork(
        () -> {
          MenuScreens.register(Automatons.MENU.get(), WorkScreen::new);
          MenuScreens.register(MachineParts.MENU.get(), MachineScreen::new);
          if (net.minecraftforge.fml.ModList.get().isLoaded("jeresources"))
            dev.automatons.compat.jer.AutomatonsJerPlugin.initialize();
        });
  }

  @SubscribeEvent
  public static void guides(RegisterClientReloadListenersEvent e) {
    // Register before the first resource reload prepares its guide list. Client setup can run
    // later.
    Handbook.register();
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
    e.registerEntityRenderer(Automatons.LUMINA_ORB.get(), LuminaOrbRenderer::new);
    e.registerEntityRenderer(Automatons.FISHING_HOOK.get(), WorkerFishingHookRenderer::new);
    e.registerBlockEntityRenderer(Automatons.INFUSER_BE.get(), InfuserRenderer::new);
    e.registerEntityRenderer(Automatons.LUMBERTON.get(), LumbertonRenderer::new);
    e.registerEntityRenderer(Automatons.GUARDINGTON.get(), LumbertonRenderer::new);
    e.registerEntityRenderer(Automatons.TIMBER.get(), TimberRenderer::new);
    e.registerEntityRenderer(MachineParts.ENTITY.get(), MachineRenderer::new);
    e.registerBlockEntityRenderer(Automatons.STATION.get(), TotemRenderer::new);
  }

  @SubscribeEvent
  public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
    e.registerLayerDefinition(LumbertonModel.LAYER, LumbertonModel::layer);
  }

  public static void receive(Network.ClientData message) {
    var mc = Minecraft.getInstance();
    CompoundTag n = message.data();
    if (n == null) return;
    if (message.kind() == 6 && mc.player != null && mc.player.containerMenu instanceof MachineMenu menu && menu.containerId == message.menu()) menu.snapshot = n;
    if (message.kind() == 5) TimberSweep.receive(n);
    if (message.kind() == 0
        && mc.player != null
        && mc.player.containerMenu instanceof WorkMenu menu
        && menu.containerId == message.menu()) menu.snapshot = n;
    if (message.kind() == 1) {
      protectedChunks.clear();
      for (long chunk : n.getLongArray("Chunks")) protectedChunks.add(chunk);
      protectionDimension = n.getString("Dimension");
    }
    if (message.kind() == 4 && mc.level != null) {
      var orb = mc.level.getEntity(n.getInt("Orb"));
      var player = mc.level.getEntity(n.getInt("Player"));
      if (orb instanceof LuminaOrb && player instanceof net.minecraft.world.entity.player.Player)
        mc.particleEngine.add(
            new net.minecraft.client.particle.ItemPickupParticle(
                mc.getEntityRenderDispatcher(), mc.renderBuffers(), mc.level, orb, player));
    }
    if (message.kind() == 3) {
      lumina = Math.max(0, n.getInt("Lumina"));
      luminaThirds = Math.max(0, Math.min(2, n.getInt("Thirds")));
    }
    if (message.kind() == 2) {
      Screen previous = mc.screen;
      mc.setScreen(
          new ConfirmScreen(
              yes -> {
                Network.CHANNEL.sendToServer(new Network.Confirm(n.getUUID("Token"), yes));
                mc.setScreen(previous);
              },
              Component.literal(
                  n.getBoolean("Protect") ? "Protect this chunk?" : "Revoke chunk protection?"),
              Component.literal(
                  "Chunk " + n.getInt("X") + ", " + n.getInt("Z") + " — all heights")));
    }
  }
}
