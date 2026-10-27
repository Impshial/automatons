package dev.automatons.client;

import dev.automatons.*;
import java.util.function.Consumer;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
    modid = Automatons.ID,
    bus = Mod.EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT)
public final class LuminallClient {
  public static void fluid(Consumer<IClientFluidTypeExtensions> consumer) {
    // Vanilla animated still/flow textures retain Minecraft's pixel scale and animation.
    consumer.accept(
        new IClientFluidTypeExtensions() {
          public ResourceLocation getStillTexture() {
            return new ResourceLocation("minecraft:block/water_still");
          }

          public ResourceLocation getFlowingTexture() {
            return new ResourceLocation("minecraft:block/water_flow");
          }

          public int getTintColor() {
            return Luminall.COLOR;
          }
        });
  }

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          ItemBlockRenderTypes.setRenderLayer(Luminall.SOURCE.get(), RenderType.translucent());
          ItemBlockRenderTypes.setRenderLayer(Luminall.FLOWING.get(), RenderType.translucent());
        });
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(Luminall.TANK_BE.get(), ReservoirRenderer::new);
    event.registerBlockEntityRenderer(Luminall.COLLECTOR_BE.get(), ReservoirRenderer::new);
  }

  @SubscribeEvent
  public static void blockColors(RegisterColorHandlersEvent.Block event) {
    event.register(
        (state, level, pos, tint) ->
            switch (state.getValue(FluidSpill.KIND)) {
              case 0 -> Luminall.COLOR;
              case 1 -> 0xff3f76e4;
              default -> -1;
            },
        Luminall.SPILL.get());
  }

  @SubscribeEvent
  public static void colors(RegisterColorHandlersEvent.Item event) {
    event.register((stack, tint) -> tint == 0 ? Luminall.COLOR : -1, Luminall.BOTTLE.get());
    event.register(
        new net.minecraftforge.client.model.DynamicFluidContainerModel.Colors(),
        Luminall.BUCKET.get());
  }
}
