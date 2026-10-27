package dev.automatons;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Read the bundled generation definition itself, not a separately maintained display config. */
public final class LumiteDefaults {
  private LumiteDefaults() {}

  public static LumiteConfiguration read() {
    return read(false);
  }

  public static LumiteConfiguration read(boolean deepslate) {
    String resource =
        "/data/automatons/worldgen/configured_feature/"
            + (deepslate ? "deepslate_lumite" : "lumite")
            + ".json";
    try (var stream = LumiteDefaults.class.getResourceAsStream(resource)) {
      if (stream == null) throw new IllegalStateException("Missing " + resource);
      var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      return LumiteConfiguration.CODEC
          .parse(JsonOps.INSTANCE, json.getAsJsonObject().get("config"))
          .getOrThrow(
              false,
              message -> {
                throw new IllegalStateException(message);
              });
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot read Lumite defaults", e);
    }
  }
}
