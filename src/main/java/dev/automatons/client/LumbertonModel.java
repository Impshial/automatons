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
    PartDefinition headPart =
        root.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -8f, -4f, 9f, 8f, 8f),
            PartPose.offset(0f, 6f, 0f));
    PartDefinition bodyPart =
        root.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(36, 0).addBox(-4.5f, 0f, -3f, 9f, 9f, 6f),
            PartPose.offset(0f, 7f, 0f));
    PartDefinition right_armPart =
        root.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(68, 0).addBox(-3f, 0f, -2f, 3f, 13f, 4f),
            PartPose.offset(-4.5f, 7f, 0f));
    PartDefinition left_armPart =
        root.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(84, 0).addBox(0f, 0f, -2f, 3f, 13f, 4f),
            PartPose.offset(4.5f, 7f, 0f));
    PartDefinition right_legPart =
        root.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(100, 0).addBox(-2f, 0f, -2f, 3f, 8f, 4f),
            PartPose.offset(-2f, 16f, 0f));
    PartDefinition left_legPart =
        root.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 19).addBox(-1f, 0f, -2f, 3f, 8f, 4f),
            PartPose.offset(2f, 16f, 0f));
    PartDefinition neckPart =
        root.addOrReplaceChild(
            "neck",
            CubeListBuilder.create().texOffs(16, 19).addBox(-2f, 6f, -2f, 4f, 1f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition shoulder_rPart =
        root.addOrReplaceChild(
            "shoulder_r",
            CubeListBuilder.create().texOffs(34, 19).addBox(-4.5f, 7f, -2f, 1f, 3f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition shoulder_lPart =
        root.addOrReplaceChild(
            "shoulder_l",
            CubeListBuilder.create().texOffs(46, 19).addBox(3.5f, 7f, -2f, 1f, 3f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition lumber_apronPart =
        bodyPart.addOrReplaceChild(
            "lumber_apron",
            CubeListBuilder.create().texOffs(58, 19).addBox(-3f, 2f, -4f, 6f, 6f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition lumber_shoulderPart =
        bodyPart.addOrReplaceChild(
            "lumber_shoulder",
            CubeListBuilder.create().texOffs(74, 19).addBox(-4.5f, 0f, -3.5f, 2f, 2f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition axe_beltPart =
        bodyPart.addOrReplaceChild(
            "axe_belt",
            CubeListBuilder.create().texOffs(94, 19).addBox(2f, 6f, -4.5f, 2f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_helmetPart =
        headPart.addOrReplaceChild(
            "miner_helmet",
            CubeListBuilder.create().texOffs(0, 33).addBox(-5f, -9f, -4.5f, 10f, 3f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_lampPart =
        headPart.addOrReplaceChild(
            "miner_lamp",
            CubeListBuilder.create().texOffs(40, 33).addBox(-1.5f, -8.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_apronPart =
        bodyPart.addOrReplaceChild(
            "miner_apron",
            CubeListBuilder.create().texOffs(50, 33).addBox(-3f, 2f, -4f, 6f, 6f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition backpackPart =
        bodyPart.addOrReplaceChild(
            "backpack",
            CubeListBuilder.create().texOffs(66, 33).addBox(-3.5f, 0.5f, 3f, 7f, 8f, 3f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_rightPart =
        bodyPart.addOrReplaceChild(
            "strap_right",
            CubeListBuilder.create().texOffs(88, 33).addBox(-3.5f, 0f, -4f, 1f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_leftPart =
        bodyPart.addOrReplaceChild(
            "strap_left",
            CubeListBuilder.create().texOffs(94, 33).addBox(2.5f, 0f, -4f, 1f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_top_rightPart =
        bodyPart.addOrReplaceChild(
            "strap_top_right",
            CubeListBuilder.create().texOffs(100, 33).addBox(-3.5f, -0.5f, -3f, 1f, 1f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_top_leftPart =
        bodyPart.addOrReplaceChild(
            "strap_top_left",
            CubeListBuilder.create().texOffs(0, 47).addBox(2.5f, -0.5f, -3f, 1f, 1f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition parcelPart =
        bodyPart.addOrReplaceChild(
            "parcel",
            CubeListBuilder.create().texOffs(16, 47).addBox(-1.5f, 3f, 6f, 3f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_apronPart =
        bodyPart.addOrReplaceChild(
            "builder_apron",
            CubeListBuilder.create().texOffs(26, 47).addBox(-3f, 1f, -4f, 6f, 7f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_beltPart =
        bodyPart.addOrReplaceChild(
            "builder_belt",
            CubeListBuilder.create().texOffs(42, 47).addBox(-4.5f, 6f, -4f, 9f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_bucklePart =
        bodyPart.addOrReplaceChild(
            "builder_buckle",
            CubeListBuilder.create().texOffs(64, 47).addBox(-1f, 6f, -5f, 2f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_brimPart =
        headPart.addOrReplaceChild(
            "farmer_brim",
            CubeListBuilder.create().texOffs(72, 47).addBox(-6f, -8.5f, -5.5f, 12f, 1f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_crownPart =
        headPart.addOrReplaceChild(
            "farmer_crown",
            CubeListBuilder.create().texOffs(0, 61).addBox(-4f, -10.5f, -3.5f, 8f, 2f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_apronPart =
        bodyPart.addOrReplaceChild(
            "farmer_apron",
            CubeListBuilder.create().texOffs(32, 61).addBox(-3f, 1f, -4f, 6f, 7f, 1f),
            PartPose.offset(0f, 0f, 0f));
    headPart.addOrReplaceChild("operator_cap", CubeListBuilder.create().texOffs(26,47).addBox(-4.5f,-9f,-4f,9,2,8).addBox(-4.5f,-7.5f,-6f,9,1,2), PartPose.ZERO);
    root.addOrReplaceChild("fishing_chair", CubeListBuilder.create().texOffs(36, 0)
        .addBox(-5, 17, -3, 10, 1, 8).addBox(-5, 18, -3, 1, 6, 1).addBox(4, 18, -3, 1, 6, 1)
        .addBox(-5, 18, 4, 1, 6, 1).addBox(4, 18, 4, 1, 6, 1).addBox(-5, 10, 4, 10, 7, 1), PartPose.ZERO);
    return LayerDefinition.create(mesh, 128, 128);
  }

  @Override
  public void setupAnim(Lumberton w, float limb, float amount, float age, float yaw, float pitch) {
    // Job equipment visibility is derived from the synchronized, persistent job.
    body.getChild("lumber_apron").visible = w.job() == 1;
    body.getChild("lumber_shoulder").visible = w.job() == 1;
    body.getChild("axe_belt").visible = w.job() == 1;
    head.getChild("miner_helmet").visible = w.job() == 2;
    head.getChild("miner_lamp").visible = w.job() == 2;
    body.getChild("miner_apron").visible = w.job() == 2;
    body.getChild("backpack").visible = w.job() == 3;
    body.getChild("strap_right").visible = w.job() == 3;
    body.getChild("strap_left").visible = w.job() == 3;
    body.getChild("strap_top_right").visible = w.job() == 3;
    body.getChild("strap_top_left").visible = w.job() == 3;
    body.getChild("parcel").visible = w.job() == 3;
    body.getChild("builder_apron").visible = w.job() == 4;
    body.getChild("builder_belt").visible = w.job() == 4;
    body.getChild("builder_buckle").visible = w.job() == 4;
    head.getChild("farmer_brim").visible = w.job() == 5;
    head.getChild("farmer_crown").visible = w.job() == 5;
    body.getChild("farmer_apron").visible = w.job() == 5;
    head.getChild("operator_cap").visible = w.job() == 6;
    head.yRot = yaw * Mth.DEG_TO_RAD;
    head.xRot = pitch * Mth.DEG_TO_RAD;
    float walk = Mth.cos(limb * .6662f) * .55f * amount;
    rightLeg.xRot = walk;
    leftLeg.xRot = -walk;
    rightArm.xRot = -walk * .7f;
    leftArm.xRot = walk * .7f;
    rightArm.zRot = .015f + Mth.cos(age * .07f) * .01f;
    leftArm.zRot = -rightArm.zRot;
    body.yRot = 0;
    root.getChild("fishing_chair").visible = w.fishingPose() == 2;
    if (w.fishingPose() > 0) { rightArm.xRot = -.8f; leftArm.xRot = -.35f; }
    if (w.fishingPose() == 2) rightLeg.xRot = leftLeg.xRot = -1.25f;
    if (w.isPassenger()) { rightLeg.xRot = leftLeg.xRot = -1.25f; rightArm.xRot = leftArm.xRot = -.6f; }
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
