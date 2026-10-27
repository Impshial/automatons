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
  private final boolean armored;
  private int appearance;

  public LumbertonModel(ModelPart root) {
    this(root, false);
  }

  public LumbertonModel(ModelPart root, boolean armored) {
    this.root = root;
    this.armored = armored;
    appearance = armored ? 13 : 0;
    String prefix = armored ? "guard_" : "";
    head = root.getChild(prefix + "head");
    body = root.getChild(prefix + "body");
    leftArm = root.getChild(prefix + "left_arm");
    rightArm = root.getChild(prefix + "right_arm");
    leftLeg = root.getChild(prefix + "left_leg");
    rightLeg = root.getChild(prefix + "right_leg");
    for (String part :
        new String[] {"head", "body", "left_arm", "right_arm", "left_leg", "right_leg", "neck"}) {
      root.getChild(part).visible = !armored;
      root.getChild("guard_" + part).visible = armored;
    }
    root.getChild("shoulder_r").visible = root.getChild("shoulder_l").visible = !armored;
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
            CubeListBuilder.create().texOffs(116, 0).addBox(-1f, 0f, -2f, 3f, 8f, 4f),
            PartPose.offset(2f, 16f, 0f));
    PartDefinition neckPart =
        root.addOrReplaceChild(
            "neck",
            CubeListBuilder.create().texOffs(132, 0).addBox(-2f, 6f, -2f, 4f, 1f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition shoulder_rPart =
        root.addOrReplaceChild(
            "shoulder_r",
            CubeListBuilder.create().texOffs(150, 0).addBox(-4.5f, 7f, -2f, 1f, 3f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition shoulder_lPart =
        root.addOrReplaceChild(
            "shoulder_l",
            CubeListBuilder.create().texOffs(162, 0).addBox(3.5f, 7f, -2f, 1f, 3f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition boot_rPart =
        right_legPart.addOrReplaceChild(
            "boot_r",
            CubeListBuilder.create().texOffs(174, 0).addBox(-2.5f, 4f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition boot_lPart =
        left_legPart.addOrReplaceChild(
            "boot_l",
            CubeListBuilder.create().texOffs(194, 0).addBox(-1.5f, 4f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition lumber_shirtPart =
        bodyPart.addOrReplaceChild(
            "lumber_shirt",
            CubeListBuilder.create().texOffs(214, 0).addBox(-5f, 0f, -3.5f, 10f, 9f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition lumber_sleeve_rPart =
        right_armPart.addOrReplaceChild(
            "lumber_sleeve_r",
            CubeListBuilder.create().texOffs(0, 19).addBox(-3.5f, 0f, -2.5f, 4f, 6f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition lumber_sleeve_lPart =
        left_armPart.addOrReplaceChild(
            "lumber_sleeve_l",
            CubeListBuilder.create().texOffs(20, 19).addBox(-0.5f, 0f, -2.5f, 4f, 6f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition lumber_beltPart =
        bodyPart.addOrReplaceChild(
            "lumber_belt",
            CubeListBuilder.create().texOffs(40, 19).addBox(-5f, 7f, -4f, 10f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition axe_beltPart =
        bodyPart.addOrReplaceChild(
            "axe_belt",
            CubeListBuilder.create().texOffs(64, 19).addBox(2f, 6f, -4.5f, 2f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_apronPart =
        bodyPart.addOrReplaceChild(
            "miner_apron",
            CubeListBuilder.create().texOffs(72, 19).addBox(-4f, 1f, -4f, 8f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_knee_rPart =
        right_legPart.addOrReplaceChild(
            "miner_knee_r",
            CubeListBuilder.create().texOffs(92, 19).addBox(-2.5f, 2f, -3f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_knee_lPart =
        left_legPart.addOrReplaceChild(
            "miner_knee_l",
            CubeListBuilder.create().texOffs(104, 19).addBox(-1.5f, 2f, -3f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_apronPart =
        bodyPart.addOrReplaceChild(
            "quarry_apron",
            CubeListBuilder.create().texOffs(116, 19).addBox(-4f, 1f, -4f, 8f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_knee_rPart =
        right_legPart.addOrReplaceChild(
            "quarry_knee_r",
            CubeListBuilder.create().texOffs(136, 19).addBox(-2.5f, 2f, -3f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_knee_lPart =
        left_legPart.addOrReplaceChild(
            "quarry_knee_l",
            CubeListBuilder.create().texOffs(148, 19).addBox(-1.5f, 2f, -3f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_helmetPart =
        headPart.addOrReplaceChild(
            "miner_helmet",
            CubeListBuilder.create().texOffs(160, 19).addBox(-5f, -9f, -4.5f, 10f, 3f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_brimPart =
        headPart.addOrReplaceChild(
            "miner_brim",
            CubeListBuilder.create().texOffs(200, 19).addBox(-5.5f, -7f, -5.5f, 11f, 1f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition miner_lampPart =
        headPart.addOrReplaceChild(
            "miner_lamp",
            CubeListBuilder.create().texOffs(246, 19).addBox(-1.5f, -8.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_bandPart =
        headPart.addOrReplaceChild(
            "quarry_band",
            CubeListBuilder.create().texOffs(0, 33).addBox(-5f, -8f, -4.5f, 10f, 1f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_rim_rPart =
        headPart.addOrReplaceChild(
            "quarry_rim_r",
            CubeListBuilder.create().texOffs(40, 33).addBox(-4.5f, -9f, -5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_lens_rPart =
        headPart.addOrReplaceChild(
            "quarry_lens_r",
            CubeListBuilder.create().texOffs(52, 33).addBox(-4f, -8.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_rim_lPart =
        headPart.addOrReplaceChild(
            "quarry_rim_l",
            CubeListBuilder.create().texOffs(62, 33).addBox(0.5f, -9f, -5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition quarry_lens_lPart =
        headPart.addOrReplaceChild(
            "quarry_lens_l",
            CubeListBuilder.create().texOffs(74, 33).addBox(1f, -8.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition backpackPart =
        bodyPart.addOrReplaceChild(
            "backpack",
            CubeListBuilder.create().texOffs(84, 33).addBox(-4f, 0f, 3f, 8f, 10f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_rightPart =
        bodyPart.addOrReplaceChild(
            "strap_right",
            CubeListBuilder.create().texOffs(110, 33).addBox(-3.5f, 0f, -4f, 1f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_top_rightPart =
        bodyPart.addOrReplaceChild(
            "strap_top_right",
            CubeListBuilder.create().texOffs(116, 33).addBox(-3.5f, -0.5f, -3f, 1f, 1f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_leftPart =
        bodyPart.addOrReplaceChild(
            "strap_left",
            CubeListBuilder.create().texOffs(132, 33).addBox(2.5f, 0f, -4f, 1f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_top_leftPart =
        bodyPart.addOrReplaceChild(
            "strap_top_left",
            CubeListBuilder.create().texOffs(138, 33).addBox(2.5f, -0.5f, -3f, 1f, 1f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition strap_crossPart =
        bodyPart.addOrReplaceChild(
            "strap_cross",
            CubeListBuilder.create().texOffs(154, 33).addBox(-3.5f, 4f, -4.5f, 7f, 1f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition parcelPart =
        bodyPart.addOrReplaceChild(
            "parcel",
            CubeListBuilder.create().texOffs(172, 33).addBox(-2f, 5f, 7f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition runner_capPart =
        headPart.addOrReplaceChild(
            "runner_cap",
            CubeListBuilder.create().texOffs(184, 33).addBox(-5f, -9f, -4.5f, 10f, 3f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition runner_peakPart =
        headPart.addOrReplaceChild(
            "runner_peak",
            CubeListBuilder.create().texOffs(224, 33).addBox(-4f, -7f, -7.5f, 8f, 1f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_apronPart =
        bodyPart.addOrReplaceChild(
            "builder_apron",
            CubeListBuilder.create().texOffs(0, 49).addBox(-3.5f, 1f, -4f, 7f, 7f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_beltPart =
        bodyPart.addOrReplaceChild(
            "builder_belt",
            CubeListBuilder.create().texOffs(18, 49).addBox(-4.5f, 6f, -4.5f, 9f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_bucklePart =
        bodyPart.addOrReplaceChild(
            "builder_buckle",
            CubeListBuilder.create().texOffs(40, 49).addBox(-1f, 6f, -5f, 2f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition builder_pouchPart =
        bodyPart.addOrReplaceChild(
            "builder_pouch",
            CubeListBuilder.create().texOffs(48, 49).addBox(2f, 6f, -5f, 3f, 3f, 2f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_brimPart =
        headPart.addOrReplaceChild(
            "farmer_brim",
            CubeListBuilder.create().texOffs(60, 49).addBox(-6f, -8.5f, -5.5f, 12f, 1f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_crownPart =
        headPart.addOrReplaceChild(
            "farmer_crown",
            CubeListBuilder.create().texOffs(108, 49).addBox(-4f, -10.5f, -3.5f, 8f, 2f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_overallsPart =
        bodyPart.addOrReplaceChild(
            "farmer_overalls",
            CubeListBuilder.create().texOffs(140, 49).addBox(-3.5f, 1f, -4f, 7f, 8f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_patchPart =
        bodyPart.addOrReplaceChild(
            "farmer_patch",
            CubeListBuilder.create().texOffs(158, 49).addBox(1f, 5f, -4.5f, 2f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_trouser_rPart =
        right_legPart.addOrReplaceChild(
            "farmer_trouser_r",
            CubeListBuilder.create().texOffs(166, 49).addBox(-2.5f, 0f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_trouser_lPart =
        left_legPart.addOrReplaceChild(
            "farmer_trouser_l",
            CubeListBuilder.create().texOffs(186, 49).addBox(-1.5f, 0f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_strap_rPart =
        bodyPart.addOrReplaceChild(
            "farmer_strap_r",
            CubeListBuilder.create().texOffs(206, 49).addBox(-3f, 0f, -4f, 1f, 4f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition farmer_strap_lPart =
        bodyPart.addOrReplaceChild(
            "farmer_strap_l",
            CubeListBuilder.create().texOffs(212, 49).addBox(2f, 0f, -4f, 1f, 4f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_bandPart =
        headPart.addOrReplaceChild(
            "operator_band",
            CubeListBuilder.create().texOffs(218, 49).addBox(-5f, -8f, -4.5f, 10f, 1f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_rim_rPart =
        headPart.addOrReplaceChild(
            "operator_rim_r",
            CubeListBuilder.create().texOffs(0, 63).addBox(-4.5f, -9f, -5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_lens_rPart =
        headPart.addOrReplaceChild(
            "operator_lens_r",
            CubeListBuilder.create().texOffs(12, 63).addBox(-4f, -8.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_rim_lPart =
        headPart.addOrReplaceChild(
            "operator_rim_l",
            CubeListBuilder.create().texOffs(22, 63).addBox(0.5f, -9f, -5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_lens_lPart =
        headPart.addOrReplaceChild(
            "operator_lens_l",
            CubeListBuilder.create().texOffs(34, 63).addBox(1f, -8.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_glove_rPart =
        right_armPart.addOrReplaceChild(
            "operator_glove_r",
            CubeListBuilder.create().texOffs(44, 63).addBox(-3.5f, 9f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition operator_glove_lPart =
        left_armPart.addOrReplaceChild(
            "operator_glove_l",
            CubeListBuilder.create().texOffs(64, 63).addBox(-0.5f, 9f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_shirtPart =
        bodyPart.addOrReplaceChild(
            "hunter_shirt",
            CubeListBuilder.create().texOffs(84, 63).addBox(-5f, 0f, -3.5f, 10f, 9f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_sleeve_rPart =
        right_armPart.addOrReplaceChild(
            "hunter_sleeve_r",
            CubeListBuilder.create().texOffs(120, 63).addBox(-3.5f, 0f, -2.5f, 4f, 6f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_sleeve_lPart =
        left_armPart.addOrReplaceChild(
            "hunter_sleeve_l",
            CubeListBuilder.create().texOffs(140, 63).addBox(-0.5f, 0f, -2.5f, 4f, 6f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_capPart =
        headPart.addOrReplaceChild(
            "hunter_cap",
            CubeListBuilder.create().texOffs(160, 63).addBox(-5f, -9f, -4.5f, 10f, 3f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_peakPart =
        headPart.addOrReplaceChild(
            "hunter_peak",
            CubeListBuilder.create().texOffs(200, 63).addBox(-4f, -7f, -6.5f, 8f, 1f, 3f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_quiverPart =
        bodyPart.addOrReplaceChild(
            "hunter_quiver",
            CubeListBuilder.create().texOffs(224, 63).addBox(1f, -1f, 3.5f, 3f, 10f, 3f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_arrow_1Part =
        bodyPart.addOrReplaceChild(
            "hunter_arrow_1",
            CubeListBuilder.create().texOffs(238, 63).addBox(1f, -4f, 4.5f, 1f, 5f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_feather_1Part =
        bodyPart.addOrReplaceChild(
            "hunter_feather_1",
            CubeListBuilder.create().texOffs(244, 63).addBox(0.5f, -4f, 4f, 2f, 2f, 2f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_arrow_3Part =
        bodyPart.addOrReplaceChild(
            "hunter_arrow_3",
            CubeListBuilder.create().texOffs(0, 81).addBox(3f, -4f, 4.5f, 1f, 5f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition hunter_feather_3Part =
        bodyPart.addOrReplaceChild(
            "hunter_feather_3",
            CubeListBuilder.create().texOffs(6, 81).addBox(2.5f, -4f, 4f, 2f, 2f, 2f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_apronPart =
        bodyPart.addOrReplaceChild(
            "crafter_apron",
            CubeListBuilder.create().texOffs(16, 81).addBox(-4f, 0f, -4.5f, 8f, 11f, 2f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_pocketPart =
        bodyPart.addOrReplaceChild(
            "crafter_pocket",
            CubeListBuilder.create().texOffs(38, 81).addBox(-2f, 4f, -5.5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_bandPart =
        headPart.addOrReplaceChild(
            "crafter_band",
            CubeListBuilder.create().texOffs(50, 81).addBox(-5f, -9f, -4.5f, 10f, 1f, 9f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_rim_rPart =
        headPart.addOrReplaceChild(
            "crafter_rim_r",
            CubeListBuilder.create().texOffs(90, 81).addBox(-4.5f, -10f, -5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_lens_rPart =
        headPart.addOrReplaceChild(
            "crafter_lens_r",
            CubeListBuilder.create().texOffs(102, 81).addBox(-4f, -9.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_rim_lPart =
        headPart.addOrReplaceChild(
            "crafter_rim_l",
            CubeListBuilder.create().texOffs(112, 81).addBox(0.5f, -10f, -5f, 4f, 3f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_lens_lPart =
        headPart.addOrReplaceChild(
            "crafter_lens_l",
            CubeListBuilder.create().texOffs(124, 81).addBox(1f, -9.5f, -5.5f, 3f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_glove_rPart =
        right_armPart.addOrReplaceChild(
            "crafter_glove_r",
            CubeListBuilder.create().texOffs(134, 81).addBox(-3.5f, 9f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition crafter_glove_lPart =
        left_armPart.addOrReplaceChild(
            "crafter_glove_l",
            CubeListBuilder.create().texOffs(154, 81).addBox(-0.5f, 9f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_shirtPart =
        bodyPart.addOrReplaceChild(
            "fisher_shirt",
            CubeListBuilder.create().texOffs(174, 81).addBox(-5f, 0f, -3.5f, 10f, 9f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_sleeve_rPart =
        right_armPart.addOrReplaceChild(
            "fisher_sleeve_r",
            CubeListBuilder.create().texOffs(210, 81).addBox(-3.5f, 0f, -2.5f, 4f, 6f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_sleeve_lPart =
        left_armPart.addOrReplaceChild(
            "fisher_sleeve_l",
            CubeListBuilder.create().texOffs(230, 81).addBox(-0.5f, 0f, -2.5f, 4f, 6f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_brimPart =
        headPart.addOrReplaceChild(
            "fisher_brim",
            CubeListBuilder.create().texOffs(0, 99).addBox(-6f, -7.5f, -5.5f, 12f, 1f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_hatPart =
        headPart.addOrReplaceChild(
            "fisher_hat",
            CubeListBuilder.create().texOffs(48, 99).addBox(-4.5f, -10.5f, -4f, 9f, 3f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_wadersPart =
        bodyPart.addOrReplaceChild(
            "fisher_waders",
            CubeListBuilder.create().texOffs(84, 99).addBox(-4f, 4f, -4.5f, 8f, 5f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_wader_rPart =
        right_legPart.addOrReplaceChild(
            "fisher_wader_r",
            CubeListBuilder.create().texOffs(104, 99).addBox(-2.5f, 0f, -2.5f, 4f, 8f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_wader_lPart =
        left_legPart.addOrReplaceChild(
            "fisher_wader_l",
            CubeListBuilder.create().texOffs(124, 99).addBox(-1.5f, 0f, -2.5f, 4f, 8f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_lure_redPart =
        bodyPart.addOrReplaceChild(
            "fisher_lure_red",
            CubeListBuilder.create().texOffs(144, 99).addBox(-3f, 1f, -4.5f, 1f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fisher_lure_goldPart =
        bodyPart.addOrReplaceChild(
            "fisher_lure_gold",
            CubeListBuilder.create().texOffs(150, 99).addBox(2f, 1f, -4.5f, 1f, 2f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition ranch_brimPart =
        headPart.addOrReplaceChild(
            "ranch_brim",
            CubeListBuilder.create().texOffs(156, 99).addBox(-6.5f, -8.5f, -5.5f, 13f, 1f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition ranch_crownPart =
        headPart.addOrReplaceChild(
            "ranch_crown",
            CubeListBuilder.create().texOffs(206, 99).addBox(-4f, -11.5f, -3.5f, 8f, 3f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition ranch_bandPart =
        headPart.addOrReplaceChild(
            "ranch_band",
            CubeListBuilder.create().texOffs(0, 114).addBox(-4.5f, -9.5f, -4f, 9f, 1f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition ranch_glove_rPart =
        right_armPart.addOrReplaceChild(
            "ranch_glove_r",
            CubeListBuilder.create().texOffs(36, 114).addBox(-3.5f, 9f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition ranch_glove_lPart =
        left_armPart.addOrReplaceChild(
            "ranch_glove_l",
            CubeListBuilder.create().texOffs(56, 114).addBox(-0.5f, 9f, -2.5f, 4f, 4f, 5f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_robePart =
        bodyPart.addOrReplaceChild(
            "mage_robe",
            CubeListBuilder.create().texOffs(76, 114).addBox(-5f, 0f, -4f, 10f, 9f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_trimPart =
        bodyPart.addOrReplaceChild(
            "mage_trim",
            CubeListBuilder.create().texOffs(114, 114).addBox(-1f, 0f, -4.5f, 2f, 9f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_sleeve_rPart =
        right_armPart.addOrReplaceChild(
            "mage_sleeve_r",
            CubeListBuilder.create().texOffs(122, 114).addBox(-4f, 0f, -3f, 5f, 12f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_sleeve_lPart =
        left_armPart.addOrReplaceChild(
            "mage_sleeve_l",
            CubeListBuilder.create().texOffs(146, 114).addBox(-1f, 0f, -3f, 5f, 12f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_brimPart =
        headPart.addOrReplaceChild(
            "mage_brim",
            CubeListBuilder.create().texOffs(170, 114).addBox(-6f, -8.5f, -5.5f, 12f, 1f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_hat_0Part =
        headPart.addOrReplaceChild(
            "mage_hat_0",
            CubeListBuilder.create().texOffs(218, 114).addBox(-4.0f, -10.5f, -4.0f, 8f, 2f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_hat_1Part =
        headPart.addOrReplaceChild(
            "mage_hat_1",
            CubeListBuilder.create().texOffs(0, 134).addBox(-3.0f, -12.5f, -3.0f, 6f, 2f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_hat_2Part =
        headPart.addOrReplaceChild(
            "mage_hat_2",
            CubeListBuilder.create().texOffs(26, 134).addBox(-2.0f, -14.5f, -2.0f, 4f, 2f, 4f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition mage_hat_3Part =
        headPart.addOrReplaceChild(
            "mage_hat_3",
            CubeListBuilder.create().texOffs(44, 134).addBox(-1.0f, -16.5f, -1.0f, 2f, 2f, 2f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_headPart =
        root.addOrReplaceChild(
            "guard_head",
            CubeListBuilder.create().texOffs(54, 134).addBox(-5.5f, -11f, -5f, 11f, 11f, 10f),
            PartPose.offset(0f, -5f, 0f));
    PartDefinition guard_bodyPart =
        root.addOrReplaceChild(
            "guard_body",
            CubeListBuilder.create().texOffs(98, 134).addBox(-6f, 0f, -4f, 12f, 16f, 8f),
            PartPose.offset(0f, -4f, 0f));
    PartDefinition guard_right_armPart =
        root.addOrReplaceChild(
            "guard_right_arm",
            CubeListBuilder.create().texOffs(140, 134).addBox(-5f, 0f, -3f, 5f, 21f, 6f),
            PartPose.offset(-6f, -4f, 0f));
    PartDefinition guard_left_armPart =
        root.addOrReplaceChild(
            "guard_left_arm",
            CubeListBuilder.create().texOffs(164, 134).addBox(0f, 0f, -3f, 5f, 21f, 6f),
            PartPose.offset(6f, -4f, 0f));
    PartDefinition guard_right_legPart =
        root.addOrReplaceChild(
            "guard_right_leg",
            CubeListBuilder.create().texOffs(188, 134).addBox(-2.5f, 0f, -3f, 5f, 12f, 6f),
            PartPose.offset(-3.5f, 12f, 0f));
    PartDefinition guard_left_legPart =
        root.addOrReplaceChild(
            "guard_left_leg",
            CubeListBuilder.create().texOffs(212, 134).addBox(-2.5f, 0f, -3f, 5f, 12f, 6f),
            PartPose.offset(3.5f, 12f, 0f));
    PartDefinition guard_neckPart =
        root.addOrReplaceChild(
            "guard_neck",
            CubeListBuilder.create().texOffs(0, 163).addBox(-3f, -5f, -3f, 6f, 1f, 6f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_helmet_topPart =
        guard_headPart.addOrReplaceChild(
            "guard_helmet_top",
            CubeListBuilder.create().texOffs(26, 163).addBox(-6.5f, -11f, -5.5f, 13f, 2f, 11f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_helmet_backPart =
        guard_headPart.addOrReplaceChild(
            "guard_helmet_back",
            CubeListBuilder.create().texOffs(76, 163).addBox(-6.5f, -9f, 4.5f, 13f, 9f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_helmet_rPart =
        guard_headPart.addOrReplaceChild(
            "guard_helmet_r",
            CubeListBuilder.create().texOffs(106, 163).addBox(-6.5f, -9f, -5.5f, 2f, 9f, 10f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_helmet_lPart =
        guard_headPart.addOrReplaceChild(
            "guard_helmet_l",
            CubeListBuilder.create().texOffs(132, 163).addBox(4.5f, -9f, -5.5f, 2f, 9f, 10f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_chestplatePart =
        guard_bodyPart.addOrReplaceChild(
            "guard_chestplate",
            CubeListBuilder.create().texOffs(158, 163).addBox(-6.5f, 1f, -5f, 13f, 12f, 2f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_corePart =
        guard_bodyPart.addOrReplaceChild(
            "guard_core",
            CubeListBuilder.create().texOffs(190, 163).addBox(-1.5f, 3f, -5.5f, 3f, 4f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_shoulder_rightPart =
        guard_right_armPart.addOrReplaceChild(
            "guard_shoulder_right",
            CubeListBuilder.create().texOffs(200, 163).addBox(-5.5f, -1f, -4f, 6f, 6f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_hand_rightPart =
        guard_right_armPart.addOrReplaceChild(
            "guard_hand_right",
            CubeListBuilder.create().texOffs(230, 163).addBox(-5.5f, 16f, -3.5f, 6f, 5f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_boot_rightPart =
        guard_right_legPart.addOrReplaceChild(
            "guard_boot_right",
            CubeListBuilder.create().texOffs(0, 184).addBox(-3f, 7f, -4f, 6f, 5f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_shoulder_leftPart =
        guard_left_armPart.addOrReplaceChild(
            "guard_shoulder_left",
            CubeListBuilder.create().texOffs(30, 184).addBox(-0.5f, -1f, -4f, 6f, 6f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_hand_leftPart =
        guard_left_armPart.addOrReplaceChild(
            "guard_hand_left",
            CubeListBuilder.create().texOffs(60, 184).addBox(-0.5f, 16f, -3.5f, 6f, 5f, 7f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition guard_boot_leftPart =
        guard_left_legPart.addOrReplaceChild(
            "guard_boot_left",
            CubeListBuilder.create().texOffs(88, 184).addBox(-3f, 7f, -4f, 6f, 5f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition fishing_chairPart =
        root.addOrReplaceChild(
            "fishing_chair",
            CubeListBuilder.create().texOffs(118, 184).addBox(-5f, 17f, -3f, 10f, 1f, 8f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition chair_rlPart =
        fishing_chairPart.addOrReplaceChild(
            "chair_rl",
            CubeListBuilder.create().texOffs(156, 184).addBox(-5f, 18f, -3f, 1f, 6f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition chair_llPart =
        fishing_chairPart.addOrReplaceChild(
            "chair_ll",
            CubeListBuilder.create().texOffs(162, 184).addBox(4f, 18f, -3f, 1f, 6f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition chair_rbPart =
        fishing_chairPart.addOrReplaceChild(
            "chair_rb",
            CubeListBuilder.create().texOffs(168, 184).addBox(-5f, 18f, 4f, 1f, 6f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition chair_lbPart =
        fishing_chairPart.addOrReplaceChild(
            "chair_lb",
            CubeListBuilder.create().texOffs(174, 184).addBox(4f, 18f, 4f, 1f, 6f, 1f),
            PartPose.offset(0f, 0f, 0f));
    PartDefinition chair_backPart =
        fishing_chairPart.addOrReplaceChild(
            "chair_back",
            CubeListBuilder.create().texOffs(180, 184).addBox(-5f, 10f, 4f, 10f, 7f, 1f),
            PartPose.offset(0f, 0f, 0f));
    return LayerDefinition.create(mesh, 256, 256);
  }

  @Override
  public void setupAnim(Lumberton w, float limb, float amount, float age, float yaw, float pitch) {
    appearance = armored ? 13 : Math.max(0, Math.min(12, w.job()));
    // Sleeves and shoulder armor need real joint clearance from the torso throughout a swing.
    float shoulder = WorkerSurfaces.shoulder(appearance);
    rightArm.x = -shoulder;
    leftArm.x = shoulder;
    // Job equipment visibility is derived from the synchronized, persistent job.
    if (!armored) {
      body.getChild("lumber_shirt").visible = w.job() == 1;
      rightArm.getChild("lumber_sleeve_r").visible = w.job() == 1;
      leftArm.getChild("lumber_sleeve_l").visible = w.job() == 1;
      body.getChild("lumber_belt").visible = w.job() == 1;
      body.getChild("axe_belt").visible = w.job() == 1;
      body.getChild("miner_apron").visible = w.job() == 2;
      rightLeg.getChild("miner_knee_r").visible = w.job() == 2;
      leftLeg.getChild("miner_knee_l").visible = w.job() == 2;
      body.getChild("quarry_apron").visible = w.job() == 11;
      rightLeg.getChild("quarry_knee_r").visible = w.job() == 11;
      leftLeg.getChild("quarry_knee_l").visible = w.job() == 11;
      head.getChild("miner_helmet").visible = w.job() == 2;
      head.getChild("miner_brim").visible = w.job() == 2;
      head.getChild("miner_lamp").visible = w.job() == 2;
      head.getChild("quarry_band").visible = w.job() == 11;
      head.getChild("quarry_rim_r").visible = w.job() == 11;
      head.getChild("quarry_lens_r").visible = w.job() == 11;
      head.getChild("quarry_rim_l").visible = w.job() == 11;
      head.getChild("quarry_lens_l").visible = w.job() == 11;
      body.getChild("backpack").visible = w.job() == 3;
      body.getChild("strap_right").visible = w.job() == 3;
      body.getChild("strap_top_right").visible = w.job() == 3;
      body.getChild("strap_left").visible = w.job() == 3;
      body.getChild("strap_top_left").visible = w.job() == 3;
      body.getChild("strap_cross").visible = w.job() == 3;
      body.getChild("parcel").visible = w.job() == 3;
      head.getChild("runner_cap").visible = w.job() == 3;
      head.getChild("runner_peak").visible = w.job() == 3;
      body.getChild("builder_apron").visible = w.job() == 4;
      body.getChild("builder_belt").visible = w.job() == 4;
      body.getChild("builder_buckle").visible = w.job() == 4;
      body.getChild("builder_pouch").visible = w.job() == 4;
      head.getChild("farmer_brim").visible = w.job() == 5;
      head.getChild("farmer_crown").visible = w.job() == 5;
      body.getChild("farmer_overalls").visible = w.job() == 5;
      body.getChild("farmer_patch").visible = w.job() == 5;
      rightLeg.getChild("farmer_trouser_r").visible = w.job() == 5;
      leftLeg.getChild("farmer_trouser_l").visible = w.job() == 5;
      body.getChild("farmer_strap_r").visible = w.job() == 5;
      body.getChild("farmer_strap_l").visible = w.job() == 5;
      head.getChild("operator_band").visible = w.job() == 6;
      head.getChild("operator_rim_r").visible = w.job() == 6;
      head.getChild("operator_lens_r").visible = w.job() == 6;
      head.getChild("operator_rim_l").visible = w.job() == 6;
      head.getChild("operator_lens_l").visible = w.job() == 6;
      rightArm.getChild("operator_glove_r").visible = w.job() == 6;
      leftArm.getChild("operator_glove_l").visible = w.job() == 6;
      body.getChild("hunter_shirt").visible = w.job() == 7;
      rightArm.getChild("hunter_sleeve_r").visible = w.job() == 7;
      leftArm.getChild("hunter_sleeve_l").visible = w.job() == 7;
      head.getChild("hunter_cap").visible = w.job() == 7;
      head.getChild("hunter_peak").visible = w.job() == 7;
      body.getChild("hunter_quiver").visible = w.job() == 7;
      body.getChild("hunter_arrow_1").visible = w.job() == 7;
      body.getChild("hunter_feather_1").visible = w.job() == 7;
      body.getChild("hunter_arrow_3").visible = w.job() == 7;
      body.getChild("hunter_feather_3").visible = w.job() == 7;
      body.getChild("crafter_apron").visible = w.job() == 8;
      body.getChild("crafter_pocket").visible = w.job() == 8;
      head.getChild("crafter_band").visible = w.job() == 8;
      head.getChild("crafter_rim_r").visible = w.job() == 8;
      head.getChild("crafter_lens_r").visible = w.job() == 8;
      head.getChild("crafter_rim_l").visible = w.job() == 8;
      head.getChild("crafter_lens_l").visible = w.job() == 8;
      rightArm.getChild("crafter_glove_r").visible = w.job() == 8;
      leftArm.getChild("crafter_glove_l").visible = w.job() == 8;
      body.getChild("fisher_shirt").visible = w.job() == 9;
      rightArm.getChild("fisher_sleeve_r").visible = w.job() == 9;
      leftArm.getChild("fisher_sleeve_l").visible = w.job() == 9;
      head.getChild("fisher_brim").visible = w.job() == 9;
      head.getChild("fisher_hat").visible = w.job() == 9;
      body.getChild("fisher_waders").visible = w.job() == 9;
      rightLeg.getChild("fisher_wader_r").visible = w.job() == 9;
      leftLeg.getChild("fisher_wader_l").visible = w.job() == 9;
      body.getChild("fisher_lure_red").visible = w.job() == 9;
      body.getChild("fisher_lure_gold").visible = w.job() == 9;
      head.getChild("ranch_brim").visible = w.job() == 10;
      head.getChild("ranch_crown").visible = w.job() == 10;
      head.getChild("ranch_band").visible = w.job() == 10;
      rightArm.getChild("ranch_glove_r").visible = w.job() == 10;
      leftArm.getChild("ranch_glove_l").visible = w.job() == 10;
      body.getChild("mage_robe").visible = w.job() == 12;
      body.getChild("mage_trim").visible = w.job() == 12;
      rightArm.getChild("mage_sleeve_r").visible = w.job() == 12;
      leftArm.getChild("mage_sleeve_l").visible = w.job() == 12;
      head.getChild("mage_brim").visible = w.job() == 12;
      head.getChild("mage_hat_0").visible = w.job() == 12;
      head.getChild("mage_hat_1").visible = w.job() == 12;
      head.getChild("mage_hat_2").visible = w.job() == 12;
      head.getChild("mage_hat_3").visible = w.job() == 12;
    }
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
    if (w.fishingPose() > 0) {
      rightArm.xRot = -.8f;
      leftArm.xRot = -.35f;
    }
    if (w.fishingPose() == 2) rightLeg.xRot = leftLeg.xRot = -1.25f;
    if (w.isPassenger()) {
      rightLeg.xRot = leftLeg.xRot = -1.25f;
      rightArm.xRot = leftArm.xRot = -.6f;
    }
    if (w.chopTarget() != null) {
      float swing = .5f + .5f * Mth.sin(age * .65f);
      rightArm.xRot = -.45f - swing * 1.65f;
      body.yRot = -swing * .06f;
    } else if (w.status().equals("Unloading") || w.status().equals("Returning totem")) {
      leftArm.xRot -= .12f;
      rightArm.xRot -= .12f;
    }
    if (w.operating() == 0) {
      head.xRot = .65f;
      head.yRot = 0;
      body.yRot = 0;
      leftArm.xRot = rightArm.xRot = .04f;
      leftArm.zRot = -.025f;
      rightArm.zRot = .025f;
      leftLeg.xRot = rightLeg.xRot = 0;
      root.getChild("fishing_chair").visible = false;
    }
  }

  @Override
  public void renderToBuffer(
      PoseStack p, VertexConsumer v, int light, int overlay, float r, float g, float b, float a) {
    WorkerSurfaces.render(root, appearance, p, v, light, overlay, r, g, b, a);
  }

  @Override
  public void translateToHand(HumanoidArm arm, PoseStack pose) {
    (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(pose);
    pose.translate(
        (arm == HumanoidArm.RIGHT ? -.06 : .06) * (armored ? 2 : 1), armored ? .5 : .125, 0);
    // Vanilla hand-layer rotations already orient a handheld axe correctly.

  }
}
