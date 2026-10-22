package dev.automatons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.automatons.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;

public class TotemRenderer implements BlockEntityRenderer<Station> {
  private static final ResourceLocation BEAM =
      new ResourceLocation("textures/entity/beacon_beam.png");

  public TotemRenderer(BlockEntityRendererProvider.Context c) {}

  @Override
  public void render(
      Station s, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    if (s.getLevel() == null) return;
    if (!s.isTotem()) {
      double time = s.getLevel().getGameTime() + partial;
      pose.pushPose();
      pose.translate(
          .5, Balance.DISPLAY_HEIGHT.get() + Math.sin(time / 32) * Balance.DISPLAY_BOB.get(), .5);
      pose.mulPose(
          com.mojang.math.Axis.YP.rotationDegrees(
              (float) (time * 360 / (20 * Balance.DISPLAY_PERIOD.get()))));
      pose.mulPose(
          com.mojang.math.Axis.ZP.rotationDegrees(Balance.DISPLAY_TILT.get().floatValue()));
      float scale = Balance.DISPLAY_SCALE.get().floatValue();
      pose.scale(scale, scale, scale);
      net.minecraft.client.Minecraft.getInstance()
          .getItemRenderer()
          .renderStatic(
              new net.minecraft.world.item.ItemStack(Automatons.AUTOMATON_ITEM.get()),
              net.minecraft.world.item.ItemDisplayContext.FIXED,
              15728880,
              net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
              pose,
              new HologramBuffer(buffers),
              s.getLevel(),
              0);
      pose.popPose();
      return;
    }
    if (s.alerts == 0) return;
    BeaconRenderer.renderBeaconBeam(
        pose,
        buffers,
        BEAM,
        partial,
        1,
        s.getLevel().getGameTime(),
        0,
        Math.max(1, s.getLevel().getMaxBuildHeight() - s.getBlockPos().getY()),
        new float[] {1f, .65f, .12f},
        .11f,
        .17f);
  }

  @Override
  public boolean shouldRenderOffScreen(Station s) {
    return s.alerts != 0;
  }

  @Override
  public int getViewDistance() {
    return 192;
  }
}
