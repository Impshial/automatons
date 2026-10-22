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

  private static final int MAX_MOTES = 48;
  private static final float[][] QUAD = {
    {-1, -1, 0, .25f}, {1, -1, .25f, .25f}, {1, 1, .25f, 0}, {-1, 1, 0, 0}
  };
  private final Map<Infuser, Spray> sprays = new WeakHashMap<>();

  private static final class Spray {
    UUID donor;
    long lastBurst = Long.MIN_VALUE;
    final List<Mote> motes = new ArrayList<>();
  }

  private record Mote(
      Vec3 origin,
      Vec3 launch,
      Vec3 approach,
      double born,
      double lifetime,
      double acceleration,
      float size,
      int color) {}

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
    double time = s.getLevel().getGameTime() + (double) partial;
    double bob = Math.sin(time / 24) * .025;
    Vec3 end = new Vec3(.5, .9 + bob, .5);
    int charge = CoreItem.charge(s.core);
    float pulse = (float) Math.max(0, 1 - (time - s.lastTransfer) / 10) * .06f;
    pose.pushPose();
    pose.translate(end.x, end.y, end.z);
    pose.mulPose(Axis.YP.rotationDegrees((float) (time * 2 % 360)));
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
    spray(s, partial, time, end, pose, buffers);
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

  private void spray(
      Infuser s, float partial, double time, Vec3 end, PoseStack pose, MultiBufferSource buffers) {
    Spray spray = sprays.computeIfAbsent(s, key -> new Spray());
    if (!Objects.equals(spray.donor, s.donor)) {
      spray.motes.clear();
      spray.donor = s.donor;
      spray.lastBurst = Long.MIN_VALUE;
    }
    spray.motes.removeIf(mote -> time - mote.born >= mote.lifetime || time < mote.born);
    var donor = s.donor == null ? null : s.getLevel().getPlayerByUUID(s.donor);
    long burst = (long) Math.floor(time / 3);
    if (s.transferring
        && donor != null
        && burst != spray.lastBurst
        && time - s.lastTransfer < Math.max(12, Balance.INFUSE_INTERVAL.get() + 2)) {
      spray.lastBurst = burst;
      Random random =
          new Random(
              s.getBlockPos().asLong()
                  ^ s.donor.getLeastSignificantBits()
                  ^ burst * 0x9E3779B97F4A7C15L);
      double yaw =
          Math.toRadians(net.minecraft.util.Mth.rotLerp(partial, donor.yBodyRotO, donor.yBodyRot));
      Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
      Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
      Vec3 base = donor.getPosition(partial).subtract(Vec3.atLowerCornerOf(s.getBlockPos()));
      int count = 2 + random.nextInt(3);
      for (int i = 0; i < count && spray.motes.size() < MAX_MOTES; i++) {
        // Sample the upper chest and both shoulders in body-local coordinates, above the waist.
        Vec3 origin =
            base.add(right.scale((random.nextDouble() - .5) * .5))
                .add(forward.scale(.18 + random.nextDouble() * .08))
                .add(0, donor.getBbHeight() * (.72 + random.nextDouble() * .13), 0);
        // Each mote first lifts away from the chest, then curves in from its own approach angle.
        Vec3 launch =
            origin
                .add(forward.scale(.15 + random.nextDouble() * .35))
                .add(right.scale((random.nextDouble() - .5) * 1.4))
                .add(0, .25 + random.nextDouble() * .65, 0);
        double angle = random.nextDouble() * Math.PI * 2, radius = .25 + random.nextDouble() * .65;
        Vec3 approach =
            new Vec3(
                Math.cos(angle) * radius,
                .12 + random.nextDouble() * .55,
                Math.sin(angle) * radius);
        double lifetime =
            net.minecraft.util.Mth.clamp(
                (9 + origin.distanceTo(end) * 3) / (.65 + random.nextDouble() * .9), 12, 32);
        int color =
            switch (random.nextInt(3)) {
              case 0 -> 0xfff5eeff;
              case 1 -> 0xffd7b3ff;
              default -> 0xffa66be1;
            };
        spray.motes.add(
            new Mote(
                origin,
                launch,
                approach,
                time,
                lifetime,
                .75 + random.nextDouble() * .6,
                .018f + random.nextFloat() * .04f,
                color));
      }
    }
    // Birth positions are fixed: moving the donor does not drag already-emitted particles around.
    for (Mote mote : spray.motes) {
      double age = (time - mote.born) / mote.lifetime,
          f = Math.pow(age, mote.acceleration),
          inv = 1 - f;
      Vec3 point =
          mote.origin
              .scale(inv * inv * inv)
              .add(mote.launch.scale(3 * inv * inv * f))
              .add(end.add(mote.approach).scale(3 * inv * f * f))
              .add(end.scale(f * f * f));
      int alpha = (int) (220 * Math.min(1, age * 8) * Math.min(1, (1 - age) * 6));
      float size = mote.size * (float) (.75 + .25 * Math.sin(age * Math.PI));
      dot(pose, buffers, point, size, (mote.color & 0xffffff) | (alpha << 24));
    }
  }

  private static void dot(
      PoseStack pose, MultiBufferSource buffers, Vec3 p, float size, int color) {
    pose.pushPose();
    pose.translate(p.x, p.y, p.z);
    pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
    VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(MagicaOrbRenderer.TEXTURE));
    int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
    for (float[] v : QUAD)
      out.vertex(pose.last().pose(), v[0] * size, v[1] * size, 0)
          .color(r, g, b, Math.min(220, color >>> 24))
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
