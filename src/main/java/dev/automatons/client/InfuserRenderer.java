package dev.automatons.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.automatons.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class InfuserRenderer implements BlockEntityRenderer<Infuser> {
  private static Object visibleLevel;
  private static final Map<Long, double[]> visible = new HashMap<>();

  public InfuserRenderer(BlockEntityRendererProvider.Context c) {}

  private boolean effects(Infuser s) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) return false;
    if (visibleLevel != s.getLevel()) {
      visible.clear();
      visibleLevel = s.getLevel();
    }
    long now = s.getLevel().getGameTime(), key = s.getBlockPos().asLong();
    double distance = mc.player.distanceToSqr(Vec3.atCenterOf(s.getBlockPos()));
    visible.entrySet().removeIf(e -> now - e.getValue()[1] > 20);
    visible.put(key, new double[] {distance, now});
    return distance < 1024 && visible.values().stream().filter(v -> v[0] < distance).count() < 16;
  }

  @Override
  public void render(
      Infuser s, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    if (s.core.isEmpty() || s.getLevel() == null) return;
    float time = s.getLevel().getGameTime() + partial;
    double bob = Math.sin(time / 24) * .025;
    Vec3 end = new Vec3(.5, .9 + bob, .5);
    int charge = CoreItem.charge(s.core);
    float pulse = (float) Math.max(0, 1 - (time - s.lastTransfer) / 10) * .06f;
    pose.pushPose();
    pose.translate(end.x, end.y, end.z);
    pose.mulPose(Axis.YP.rotationDegrees(time * 2));
    pose.scale(.45f + pulse, .45f + pulse, .45f + pulse);
    int glow =
        LightTexture.pack(Math.max((light >> 4) & 15, 5 + charge * 10 / 50), (light >> 20) & 15);
    Minecraft.getInstance()
        .getItemRenderer()
        .renderStatic(
            s.core,
            ItemDisplayContext.FIXED,
            glow,
            OverlayTexture.NO_OVERLAY,
            pose,
            buffers,
            s.getLevel(),
            0);
    pose.popPose();
    if (!effects(s)) return;
    if (s.transferring && time - s.lastTransfer < Math.max(12, Balance.INFUSE_INTERVAL.get() + 2)) {
      var donor = s.donor == null ? null : s.getLevel().getPlayerByUUID(s.donor);
      if (donor != null) {
        Vec3 start =
            donor
                .getPosition(partial)
                .add(0, donor.getBbHeight() * .65, 0)
                .subtract(Vec3.atLowerCornerOf(s.getBlockPos()));
        for (int i = 0; i < 8; i++) {
          double f = (time / 16 + i / 8.0) % 1;
          Vec3 point =
              start.lerp(end, f).add(0, Math.sin(f * Math.PI) * .3, Math.sin(f * Math.PI) * .12);
          dot(pose, buffers, point, .025f, i % 3 == 0 ? 0xffeee7ff : 0xffae71e8);
        }
      }
    }
    if (charge == 50)
      for (int i = 0; i < 4; i++) {
        double a = time / 18 + i * Math.PI / 2;
        dot(
            pose,
            buffers,
            end.add(Math.cos(a) * .19, Math.sin(a * 2) * .05, Math.sin(a) * .19),
            .018f,
            0xff8df0a4);
      }
  }

  private static void dot(
      PoseStack pose, MultiBufferSource buffers, Vec3 p, float size, int color) {
    pose.pushPose();
    pose.translate(p.x, p.y, p.z);
    pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(MagicaOrbRenderer.TEXTURE));
    int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
    for (float[] v :
        new float[][] {
          {-size, -size, 0, .25f},
          {size, -size, .25f, .25f},
          {size, size, .25f, 0},
          {-size, size, 0, 0}
        })
      out.vertex(pose.last().pose(), v[0], v[1], 0)
          .color(r, g, b, 220)
          .uv(v[2], v[3])
          .overlayCoords(OverlayTexture.NO_OVERLAY)
          .uv2(15728880)
          .normal(pose.last().normal(), 0, 1, 0)
          .endVertex();
    pose.popPose();
  }

  @Override
  public int getViewDistance() {
    return 48;
  }
}
