package dev.automatons;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;

/** Pick the rendered parts inside the vehicle's broad collision box, from any face. */
final class MachinePicking {
  private record Part(AABB box, boolean station) {}

  private static final Map<String, List<Part>> models = new HashMap<>();

  private static List<Part> parts(String name) {
    return models.computeIfAbsent(
        name,
        key -> {
          try (var reader =
              new InputStreamReader(
                  MachinePicking.class.getResourceAsStream(
                      "/assets/automatons/machine/" + key + ".json"),
                  java.nio.charset.StandardCharsets.UTF_8)) {
            var result = new ArrayList<Part>();
            for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
              var o = element.getAsJsonObject();
              var a = o.getAsJsonArray("from");
              var b = o.getAsJsonArray("to");
              result.add(
                  new Part(
                      new AABB(
                          a.get(0).getAsDouble() / 16,
                          a.get(1).getAsDouble() / 16,
                          a.get(2).getAsDouble() / 16,
                          b.get(0).getAsDouble() / 16,
                          b.get(1).getAsDouble() / 16,
                          b.get(2).getAsDouble() / 16),
                      o.has("station") && o.get("station").getAsBoolean()));
            }
            return result;
          } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private static Vec3 local(Machine m, Vec3 world) {
    Vec3 v = world.subtract(m.position());
    return new Vec3(
        v.x * m.heading.getStepZ() - v.z * m.heading.getStepX(),
        v.y,
        v.x * m.heading.getStepX() + v.z * m.heading.getStepZ());
  }

  static boolean station(Machine m, Player p) {
    Vec3 eye = local(m, p.getEyePosition());
    Vec3 end = local(m, p.getEyePosition().add(p.getLookAngle().scale(6)));
    var candidates = new ArrayList<>(parts("chassis"));
    candidates.add(new Part(new AABB(-.5, 1, -.72, .5, 2, .28), false));
    if (m.attachment() != 0) candidates.addAll(parts(m.attachment() == 1 ? "blade" : "bore"));
    double closest = Double.MAX_VALUE;
    boolean station = false;
    for (var part : candidates) {
      var hit = part.box.clip(eye, end);
      if (hit.isPresent() && eye.distanceToSqr(hit.get()) < closest) {
        closest = eye.distanceToSqr(hit.get());
        station = part.station;
      }
    }
    return station;
  }
}
