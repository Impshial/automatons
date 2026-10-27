package dev.automatons.client;

import com.mojang.blaze3d.vertex.*;
import dev.automatons.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class WorkerFishingHookRenderer extends EntityRenderer<WorkerFishingHook> {
  private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/fishing_hook.png");
  public WorkerFishingHookRenderer(EntityRendererProvider.Context context) { super(context); }
  @Override public ResourceLocation getTextureLocation(WorkerFishingHook hook) { return TEXTURE; }
  @Override public void render(WorkerFishingHook hook, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
    pose.pushPose(); pose.mulPose(entityRenderDispatcher.cameraOrientation()); pose.scale(.5f, .5f, .5f);
    VertexConsumer sprite = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
    vertex(sprite, pose, -.5f, -.5f, 0, 1, light); vertex(sprite, pose, .5f, -.5f, 1, 1, light);
    vertex(sprite, pose, .5f, .5f, 1, 0, light); vertex(sprite, pose, -.5f, .5f, 0, 0, light); pose.popPose();
    Lumberton worker = hook.worker();
    if (worker != null) {
      Vec3 origin = hook.getPosition(partial), hand = worker.getPosition(partial).add(0, worker.fishingPose() == 2 ? .8 : 1.2, 0);
      Vec3 delta = hand.subtract(origin); VertexConsumer line = buffers.getBuffer(RenderType.lineStrip());
      for (int i = 0; i <= 16; i++) {
        double t = i / 16.0; float x = (float)(delta.x * t), y = (float)(delta.y * t - Math.sin(t * Math.PI) * .25), z = (float)(delta.z * t);
        line.vertex(pose.last().pose(), x, y, z).color(24, 22, 18, 255).normal(pose.last().normal(), 0, 1, 0).endVertex();
      }
    }
    super.render(hook, yaw, partial, pose, buffers, light);
  }
  private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float u, float v, int light) {
    out.vertex(pose.last().pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(light).normal(pose.last().normal(), 0, 1, 0).endVertex();
  }
}
