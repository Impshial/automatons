package dev.automatons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.automatons.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

/** Packed workers use the same rig, expressions and job accessories as deployed workers. */
public final class PackedAutomatonRenderer extends BlockEntityWithoutLevelRenderer {
  private Lumberton preview;

  public PackedAutomatonRenderer() {
    super(
        Minecraft.getInstance().getBlockEntityRenderDispatcher(),
        Minecraft.getInstance().getEntityModels());
  }

  @Override
  public void renderByItem(
      ItemStack stack,
      ItemDisplayContext context,
      PoseStack poses,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) return;
    var stored =
        stack.hasTag()
            ? stack.getTag().getCompound("Automaton")
            : new net.minecraft.nbt.CompoundTag();
    boolean armored = stored.getBoolean("Armored");
    if (preview == null || preview.level() != mc.level || preview.isGuard() != armored) {
      preview = (armored ? Automatons.GUARDINGTON : Automatons.LUMBERTON).get().create(mc.level);
      preview.setNoAi(true);
    }
    int job = armored ? WorkerJob.GUARDINGTON : stored.getInt("Job");
    if (preview.job() != job) preview.setJob(job);
    var energy = stored.getCompound("Energy");
    preview.operating(
        energy.contains("Operating") ? energy.getInt("Operating") : WorkerEnergy.CAPACITY);
    preview.showNames(false);
    poses.pushPose();
    poses.translate(.5, .06, .5);
    float scale = armored ? .37f : .55f;
    poses.scale(scale, scale, scale);
    poses.mulPose(Axis.YP.rotationDegrees(180));
    mc.getEntityRenderDispatcher().render(preview, 0, 0, 0, 0, 0, poses, buffers, light);
    poses.popPose();
  }
}
