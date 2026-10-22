package dev.automatons.client;

import com.mojang.blaze3d.vertex.*;
import dev.automatons.MagicaOrb;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class MagicaOrbRenderer extends EntityRenderer<MagicaOrb> {
  public static final ResourceLocation TEXTURE =
      new ResourceLocation("textures/entity/experience_orb.png");

  public MagicaOrbRenderer(EntityRendererProvider.Context context) {
    super(context);
    shadowRadius = .15f;
    shadowStrength = .75f;
  }

  @Override
  public ResourceLocation getTextureLocation(MagicaOrb orb) {
    return TEXTURE;
  }

  @Override
  protected int getBlockLightLevel(MagicaOrb orb, net.minecraft.core.BlockPos pos) {
    return Math.min(15, super.getBlockLightLevel(orb, pos) + 7);
  }

  public static int icon(int value) {
    int[] cuts = {3, 7, 17, 37, 73, 149, 307, 617, 1237, 2477};
    int icon = 0;
    for (int cut : cuts) if (value >= cut) icon++;
    return icon;
  }

  @Override
  public void render(
      MagicaOrb orb,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    int icon = icon(orb.value());
    float u = (icon % 4) * .25f, v = (icon / 4) * .25f;
    pose.pushPose();
    pose.translate(0, .1, 0);
    pose.mulPose(entityRenderDispatcher.cameraOrientation());
    float size = orb.small() ? .15f : .3f;
    pose.scale(size, size, size);
    float pulse = (float) (.5 + .5 * Math.sin((orb.tickCount + partial) / 2));
    int red = (int) (180 + 75 * pulse), green = (int) (110 + 145 * pulse);
    VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
    vertex(out, pose, -.5f, -.25f, u, v + .25f, red, green, light);
    vertex(out, pose, .5f, -.25f, u + .25f, v + .25f, red, green, light);
    vertex(out, pose, .5f, .75f, u + .25f, v, red, green, light);
    vertex(out, pose, -.5f, .75f, u, v, red, green, light);
    pose.popPose();
    super.render(orb, yaw, partial, pose, buffers, light);
  }

  private static void vertex(
      VertexConsumer out,
      PoseStack pose,
      float x,
      float y,
      float u,
      float v,
      int red,
      int green,
      int light) {
    out.vertex(pose.last().pose(), x, y, 0)
        .color(red, green, 255, 200)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(light)
        .normal(pose.last().normal(), 0, 1, 0)
        .endVertex();
  }
}
