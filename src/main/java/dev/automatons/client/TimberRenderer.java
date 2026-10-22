package dev.automatons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.automatons.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RotatedPillarBlock;

public class TimberRenderer extends EntityRenderer<Timber> {
  public TimberRenderer(EntityRendererProvider.Context c) {
    super(c);
  }

  @Override
  public void render(
      Timber t, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
    float progress = Math.min(1, (t.progress() + partial) / t.duration);
    for (int i = 0; i < t.wood.size(); i++) {
      if (t.consumed(i) || t.segmented && progress > .9f && i != t.next()) continue;
      pose.pushPose();
      var from = t.sources.get(i);
      var to = t.targets.get(i);
      var state = t.wood.get(i);
      if (progress >= 1) {
        pose.translate(to.getX() - t.getX() + .5, to.getY() - t.getY(), to.getZ() - t.getZ() + .5);
        if (state.hasProperty(RotatedPillarBlock.AXIS))
          state = state.setValue(RotatedPillarBlock.AXIS, t.direction.getAxis());
      } else {
        var corner =
            t.segmented
                ? net.minecraft.world.phys.Vec3.atLowerCornerOf(from)
                    .lerp(net.minecraft.world.phys.Vec3.atLowerCornerOf(to), progress)
                : TreeFinder.fallCorner(from, to, t.blockPosition(), t.direction, progress);
        pose.translate(corner.x - t.getX() + .5, corner.y - t.getY(), corner.z - t.getZ() + .5);
        if (progress > .85f && state.hasProperty(RotatedPillarBlock.AXIS))
          state = state.setValue(RotatedPillarBlock.AXIS, t.direction.getAxis());
      }

      pose.translate(-.5, 0, -.5);
      Minecraft.getInstance()
          .getBlockRenderer()
          .renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
      pose.popPose();
    }
    super.render(t, yaw, partial, pose, buffers, light);
  }

  @Override
  public ResourceLocation getTextureLocation(Timber t) {
    return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;
  }
}
