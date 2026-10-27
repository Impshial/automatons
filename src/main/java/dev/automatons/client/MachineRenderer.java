package dev.automatons.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.automatons.*;
import java.io.InputStreamReader;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Every face is tiled in <=16-pixel quads, so a three-block panel never stretches one texel map.
 */
public final class MachineRenderer extends EntityRenderer<Machine> {
  private static final Map<String, JsonArray> models = new HashMap<>();

  public MachineRenderer(EntityRendererProvider.Context context) {
    super(context);
    shadowRadius = 1.5f;
  }

  public static void clearModels() {
    models.clear();
  }

  private static JsonArray model(String name) {
    return models.computeIfAbsent(
        name,
        key -> {
          try (var reader =
              new InputStreamReader(
                  Minecraft.getInstance()
                      .getResourceManager()
                      .getResource(Automatons.id("machine/" + key + ".json"))
                      .orElseThrow()
                      .open(),
                  java.nio.charset.StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
          } catch (java.io.IOException e) {
            throw new IllegalStateException("Missing Machine model " + key, e);
          }
        });
  }

  @Override
  public ResourceLocation getTextureLocation(Machine machine) {
    return InventoryMenu.BLOCK_ATLAS;
  }

  @Override
  public void render(
      Machine m, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
    pose.pushPose();
    pose.mulPose(Axis.YP.rotationDegrees(-yaw));
    draw(model("chassis"), m, partial, pose, buffers, light);
    if (m.attachment() != 0)
      draw(model(m.attachment() == 1 ? "blade" : "bore"), m, partial, pose, buffers, light);
    pose.pushPose();
    pose.translate(-.5, 1, -.72);
    Minecraft.getInstance()
        .getBlockRenderer()
        .renderSingleBlock(
            Luminall.TANK.get().defaultBlockState(),
            pose,
            buffers,
            light,
            OverlayTexture.NO_OVERLAY);
    pose.popPose();
    pose.popPose();
    super.render(m, yaw, partial, pose, buffers, light);
  }

  private static void draw(
      JsonArray model,
      Machine m,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    for (JsonElement entry : model) {
      JsonObject box = entry.getAsJsonObject();
      float[] lo = vec(box.getAsJsonArray("from")), hi = vec(box.getAsJsonArray("to"));
      pose.pushPose();
      String group = box.has("group") ? box.get("group").getAsString() : "";
      if (group.equals("tread") && m.view().getBoolean("Moving"))
        pose.translate(0, 0, ((m.tickCount + partial) % 4) / 16.0);
      if (group.equals("drill") && m.view().getBoolean("Working")) {
        float[] pivot = vec(box.getAsJsonArray("pivot"));
        pose.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
        pose.mulPose(Axis.ZP.rotationDegrees((m.tickCount + partial) * 24));
        pose.translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
      }
      ResourceLocation texture = new ResourceLocation(box.get("texture").getAsString());
      var sprite =
          Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
      int tint =
          box.has("color") ? (int) Long.parseLong(box.get("color").getAsString(), 16) : 0xffffffff;
      if (box.has("glow")) {
        float power =
            switch (m.view().getString("Engine")) {
              case "STARTING" -> 1
                  - m.view().getInt("EngineTicks") / (float) MachineRules.START_TICKS;
              case "STOPPING" -> m.view().getInt("EngineTicks") / (float) MachineRules.STOP_TICKS;
              case "RUNNING" -> 1;
              default -> 0;
            };
        float brightness = .3f + .7f * net.minecraft.util.Mth.clamp(power, 0, 1);
        tint =
            0xff000000
                | (int) ((tint >> 16 & 255) * brightness) << 16
                | (int) ((tint >> 8 & 255) * brightness) << 8
                | (int) ((tint & 255) * brightness);
      }
      cuboid(
          pose,
          buffers,
          sprite,
          lo,
          hi,
          box.getAsJsonArray("faces"),
          tint,
          box.has("glow") ? LightTexture.FULL_BRIGHT : light);
      pose.popPose();
    }
  }

  private static float[] vec(JsonArray n) {
    return new float[] {n.get(0).getAsFloat(), n.get(1).getAsFloat(), n.get(2).getAsFloat()};
  }

  private static void cuboid(
      PoseStack pose,
      MultiBufferSource buffers,
      TextureAtlasSprite sprite,
      float[] lo,
      float[] hi,
      JsonArray faces,
      int color,
      int light) {
    var out = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
    for (JsonElement entry : faces) {
      JsonObject face = entry.getAsJsonObject();
      int axis = face.get("axis").getAsInt(), sign = face.get("sign").getAsInt();
      JsonArray rect = face.getAsJsonArray("rect");
      float startU = rect.get(0).getAsFloat(), startV = rect.get(1).getAsFloat();
      float limitU = rect.get(2).getAsFloat(), limitV = rect.get(3).getAsFloat();
      int u = (axis + 1) % 3, v = (axis + 2) % 3;
      for (float a = startU; a < limitU - .001; ) {
        float endA = Math.min(limitU, (float) (Math.floor(a / 16) + 1) * 16);
        for (float b = startV; b < limitV - .001; ) {
          float endB = Math.min(limitV, (float) (Math.floor(b / 16) + 1) * 16);
          float[][] points = {{a, b}, {endA, b}, {endA, endB}, {a, endB}};
          for (int j = 0; j < 4; j++) {
            int index = sign > 0 ? j : 3 - j;
            float[] p = new float[3];
            p[axis] = sign > 0 ? hi[axis] : lo[axis];
            p[u] = points[index][0];
            p[v] = points[index][1];
            float tx = points[index][0] - (float) Math.floor(a / 16) * 16,
                ty = points[index][1] - (float) Math.floor(b / 16) * 16;
            out.vertex(pose.last().pose(), p[0] / 16, p[1] / 16, p[2] / 16)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24 & 255)
                .uv(sprite.getU(tx), sprite.getV(16 - ty))
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(
                    pose.last().normal(),
                    axis == 0 ? sign : 0,
                    axis == 1 ? sign : 0,
                    axis == 2 ? sign : 0)
                .endVertex();
          }
          b = endB;
        }
        a = endA;
      }
    }
  }
}
