package dev.automatons.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import dev.automatons.Automatons;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;

/** Baked exposed surfaces of each clothed bone; hidden body/outfit faces are omitted. */
final class WorkerSurfaces {
  private static List<Map<String, List<float[]>>> variants;
  private static final float[] shoulders = new float[14];

  static float shoulder(int job) {
    meshes();
    return shoulders[job];
  }

  static void reload() {
    variants = null;
  }

  private static List<Map<String, List<float[]>>> meshes() {
    if (variants != null) return variants;
    List<Map<String, List<float[]>>> loaded = new ArrayList<>();
    try (var input =
            Minecraft.getInstance()
                .getResourceManager()
                .open(Automatons.id("geometry/worker_surfaces.json"));
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      var data = JsonParser.parseReader(reader).getAsJsonObject();
      for (int i = 0; i < shoulders.length; i++)
        shoulders[i] = data.getAsJsonArray("shoulder_offsets").get(i).getAsFloat();
      for (var variant : data.getAsJsonArray("variants")) {
        Map<String, List<float[]>> bones = new LinkedHashMap<>();
        for (var entry : variant.getAsJsonObject().entrySet()) {
          List<float[]> faces = new ArrayList<>();
          for (var face : entry.getValue().getAsJsonArray()) {
            var values = face.getAsJsonObject().getAsJsonArray("vertices");
            if (values.size() != 23) throw new IOException("Invalid worker face");
            float[] vertices = new float[23];
            for (int i = 0; i < vertices.length; i++) vertices[i] = values.get(i).getAsFloat();
            faces.add(vertices);
          }
          bones.put(entry.getKey(), faces);
        }
        loaded.add(bones);
      }
      if (loaded.size() != 14) throw new IOException("Missing worker profession meshes");
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load Automaton geometry", exception);
    }
    return variants = loaded;
  }

  static void render(
      ModelPart root,
      int job,
      PoseStack pose,
      VertexConsumer out,
      int light,
      int overlay,
      float r,
      float g,
      float b,
      float a) {
    pose.pushPose();
    root.translateAndRotate(pose);
    for (var bone : meshes().get(job).entrySet()) {
      String name = (job == 13 ? "guard_" : "") + bone.getKey();
      ModelPart part = root.getChild(name);
      if (!part.visible) continue;
      pose.pushPose();
      part.translateAndRotate(pose);
      var transform = pose.last();
      for (float[] face : bone.getValue()) {
        for (int i = 3; i < 23; i += 5)
          out.vertex(transform.pose(), face[i] / 16, face[i + 1] / 16, face[i + 2] / 16)
              .color(r, g, b, a)
              .uv(face[i + 3], face[i + 4])
              .overlayCoords(overlay)
              .uv2(light)
              .normal(transform.normal(), face[0], face[1], face[2])
              .endVertex();
      }
      pose.popPose();
    }
    pose.popPose();
  }
}
