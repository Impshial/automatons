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
    if (s.buildOutlineMin != null && s.buildOutlineMax != null)
      outline(s, pose, buffers, s.buildOutlineMin, s.buildOutlineMax);
    if (s.mine != null
        && s.mine.entrance != null
        && !s.mine.entranceComplete
        && s.mine.stage == MinePlan.Stage.ARCH)
      outline(s, pose, buffers, s.mine.entrance.offset(-3, 0, -3), s.mine.entrance.offset(4, 0, 4));
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
    return s.alerts != 0
        || s.buildOutlineMin != null
        || s.mine != null && s.mine.stage == MinePlan.Stage.ARCH;
  }

  private static void outline(
      Station s,
      PoseStack pose,
      MultiBufferSource buffers,
      net.minecraft.core.BlockPos min,
      net.minecraft.core.BlockPos max) {
    double x = min.getX() - s.getBlockPos().getX() - .12,
        z = min.getZ() - s.getBlockPos().getZ() - .12;
    double ex = max.getX() - s.getBlockPos().getX() + .12,
        ez = max.getZ() - s.getBlockPos().getZ() + .12;
    double y = min.getY() - s.getBlockPos().getY() + .07;
    var out = buffers.getBuffer(RenderType.lightning());
    for (double[] edge :
        new double[][] {
          {x, z, ex, z + .07}, {x, ez - .07, ex, ez}, {x, z, x + .07, ez}, {ex - .07, z, ex, ez}
        }) {
      for (double[] v :
          new double[][] {
            {edge[0], edge[1]}, {edge[0], edge[3]}, {edge[2], edge[3]}, {edge[2], edge[1]}
          })
        out.vertex(pose.last().pose(), (float) v[0], (float) y, (float) v[1])
            .color(255, 221, 32, 230)
            .endVertex();
    }
  }

  @Override
  public int getViewDistance() {
    return 192;
  }
}
