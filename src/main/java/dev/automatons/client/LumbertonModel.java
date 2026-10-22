package dev.automatons.client;

import com.mojang.blaze3d.vertex.*;
import dev.automatons.*;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public class LumbertonModel extends EntityModel<Lumberton> implements ArmedModel {
  public static final ModelLayerLocation LAYER =
      new ModelLayerLocation(Automatons.id("lumberton"), "main");
  public final ModelPart root, head, body, leftArm, rightArm, leftLeg, rightLeg;

  public LumbertonModel(ModelPart root) {
    this.root = root;
    head = root.getChild("head");
    body = root.getChild("body");
    leftArm = root.getChild("left_arm");
    rightArm = root.getChild("right_arm");
    leftLeg = root.getChild("left_leg");
    rightLeg = root.getChild("right_leg");
  }

  public static LayerDefinition layer() {
    MeshDefinition mesh = new MeshDefinition();
    PartDefinition root = mesh.getRoot();
    root.addOrReplaceChild(
        "head",
        CubeListBuilder.create().texOffs(0, 0).addBox(-3, -6, -3, 6, 6, 6),
        PartPose.offset(0, 6, 0));
    root.addOrReplaceChild(
        "neck", CubeListBuilder.create().texOffs(0, 34).addBox(-2, 6, -2, 4, 1, 4), PartPose.ZERO);
    root.addOrReplaceChild(
        "body",
        CubeListBuilder.create().texOffs(0, 14).addBox(-4, 0, -3, 8, 9, 6),
        PartPose.offset(0, 7, 0));
    root.addOrReplaceChild(
        "right_arm",
        CubeListBuilder.create().texOffs(32, 0).addBox(-3, 0, -2, 3, 13, 4),
        PartPose.offset(-5, 7, 0));
    root.addOrReplaceChild(
        "left_arm",
        CubeListBuilder.create().texOffs(48, 0).addBox(0, 0, -2, 3, 13, 4),
        PartPose.offset(5, 7, 0));
    root.addOrReplaceChild(
        "right_leg",
        CubeListBuilder.create().texOffs(32, 20).addBox(-2, 0, -2, 3, 8, 4),
        PartPose.offset(-2, 16, 0));
    root.addOrReplaceChild(
        "left_leg",
        CubeListBuilder.create().texOffs(48, 20).addBox(-1, 0, -2, 3, 8, 4),
        PartPose.offset(2, 16, 0));
    root.addOrReplaceChild(
        "shoulders",
        CubeListBuilder.create()
            .texOffs(0, 42)
            .addBox(-5, 7, -2, 1, 3, 4)
            .texOffs(12, 42)
            .addBox(4, 7, -2, 1, 3, 4),
        PartPose.ZERO);
    return LayerDefinition.create(mesh, 64, 64);
  }

  @Override
  public void setupAnim(Lumberton w, float limb, float amount, float age, float yaw, float pitch) {
    head.yRot = yaw * Mth.DEG_TO_RAD;
    head.xRot = pitch * Mth.DEG_TO_RAD;
    float walk = Mth.cos(limb * .6662f) * .55f * amount;
    rightLeg.xRot = walk;
    leftLeg.xRot = -walk;
    rightArm.xRot = -walk * .7f;
    leftArm.xRot = walk * .7f;
    rightArm.zRot = .025f + Mth.cos(age * .07f) * .015f;
    leftArm.zRot = -rightArm.zRot;
    body.yRot = 0;
    if (w.chopTarget() != null) {
      float swing = .5f + .5f * Mth.sin(age * .65f);
      rightArm.xRot = -.45f - swing * 1.65f;
      body.yRot = -swing * .06f;
    } else if (w.status().equals("Unloading") || w.status().equals("Returning totem")) {
      leftArm.xRot -= .12f;
      rightArm.xRot -= .12f;
    }
  }

  @Override
  public void renderToBuffer(
      PoseStack p, VertexConsumer v, int light, int overlay, float r, float g, float b, float a) {
    root.render(p, v, light, overlay, r, g, b, a);
  }

  @Override
  public void translateToHand(HumanoidArm arm, PoseStack pose) {
    (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(pose);
    pose.translate(arm == HumanoidArm.RIGHT ? -.06 : .06, .125, 0);
    // Vanilla hand-layer rotations already orient a handheld axe correctly.

  }
}
