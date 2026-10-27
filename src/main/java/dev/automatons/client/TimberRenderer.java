package dev.automatons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.automatons.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class TimberRenderer extends EntityRenderer<Timber> {
  public TimberRenderer(EntityRendererProvider.Context c) {
    super(c);
  }

  @Override
  public void render(
      Timber t, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
    if (t.isRemoved() || t.hasPlacedBlocks() || t.finished()) return;
    float progress = t.layoutReady() ? Math.min(1, t.visualProgress(partial) / t.duration) : 0;
    for (int i = 0; i < t.wood.size(); i++) {
      if (t.consumed(i)) continue;
      pose.pushPose();
      var from = t.sources.get(i);
      var to = t.targets.get(i);
      var state = t.wood.get(i);
      if (progress >= 1) {
        pose.translate(to.getX() - t.getX(), to.getY() - t.getY(), to.getZ() - t.getZ());
        state = TreeFinder.fallenState(state, t.direction);
      } else if (t.rigidFall) {
        var root = t.blockPosition();
        pose.translate(
            root.getX() + .5 - t.getX(),
            root.getY() + .5 - t.getY() + TreeFinder.fallLift(t.landingLift(), progress),
            root.getZ() + .5 - t.getZ());
        pose.mulPose(
            t.direction.getAxis() == net.minecraft.core.Direction.Axis.X
                ? com.mojang.math.Axis.ZP.rotationDegrees(-t.direction.getStepX() * 90 * progress)
                : com.mojang.math.Axis.XP.rotationDegrees(t.direction.getStepZ() * 90 * progress));
        pose.translate(
            from.getX() - root.getX() - .5,
            from.getY() - root.getY() - .5,
            from.getZ() - root.getZ() - .5);
      } else {
        pose.translate(from.getX() - t.getX(), from.getY() - t.getY(), from.getZ() - t.getZ());
      }

      Minecraft.getInstance()
          .getBlockRenderer()
          .renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
      if (progress >= 1 && t.workIndex() == i && t.crackStage() >= 0) {
        var crack =
            buffers.getBuffer(
                net.minecraft.client.resources.model.ModelBakery.DESTROY_TYPES.get(t.crackStage()));
        var projected =
            new com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator(
                crack, pose.last().pose(), pose.last().normal(), 1f);
        Minecraft.getInstance()
            .getBlockRenderer()
            .getModelRenderer()
            .renderModel(
                pose.last(),
                projected,
                state,
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state),
                1,
                1,
                1,
                light,
                OverlayTexture.NO_OVERLAY);
      }
      pose.popPose();
    }
    super.render(t, yaw, partial, pose, buffers, light);
  }

  @Override
  public ResourceLocation getTextureLocation(Timber t) {
    return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;
  }
}
