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
  public static String protectionDimension = "";

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent e) {
    e.enqueueWork(() -> MenuScreens.register(Automatons.MENU.get(), WorkScreen::new));
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
    e.registerEntityRenderer(Automatons.LUMBERTON.get(), LumbertonRenderer::new);
    e.registerEntityRenderer(Automatons.TIMBER.get(), TimberRenderer::new);
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
    if (message.kind() == 0
        && mc.player != null
        && mc.player.containerMenu instanceof WorkMenu menu
        && menu.containerId == message.menu()) menu.snapshot = n;
    if (message.kind() == 1) {
      protectedChunks.clear();
      for (long chunk : n.getLongArray("Chunks")) protectedChunks.add(chunk);
      protectionDimension = n.getString("Dimension");
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
