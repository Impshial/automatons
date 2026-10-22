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
    if (!s.isTotem() || s.alerts == 0 || s.getLevel() == null) return;
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
