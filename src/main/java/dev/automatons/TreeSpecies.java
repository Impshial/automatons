package dev.automatons;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/** Datapack extension point. Unknown vegetation is never inferred from a broad log tag. */
public final class TreeSpecies extends SimpleJsonResourceReloadListener {
  public record Definition(
      Set<Block> logs,
      Set<Block> leaves,
      int height,
      int spread,
      int maximumLogs,
      boolean largeTrunk,
      boolean supported) {}

  private static Map<Block, Definition> definitions = Map.of();

  public TreeSpecies() {
    super(new Gson(), "automatons_species");
  }

  public static Definition forLog(Block block) {
    return definitions.get(block);
  }

  @Override
  protected void apply(
      Map<ResourceLocation, JsonElement> resources,
      ResourceManager manager,
      ProfilerFiller profiler) {
    Map<Block, Definition> next = new HashMap<>();
    resources.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              JsonObject j = entry.getValue().getAsJsonObject();
              Set<Block> logs = blocks(j.getAsJsonArray("logs")),
                  leaves = blocks(j.getAsJsonArray("leaves"));
              Definition d =
                  new Definition(
                      logs,
                      leaves,
                      Math.min(31, Math.max(3, j.get("max_height").getAsInt())),
                      Math.min(7, Math.max(1, j.get("max_spread").getAsInt())),
                      Math.min(128, Math.max(3, j.get("max_logs").getAsInt())),
                      j.get("large_trunk").getAsBoolean(),
                      !j.has("supported") || j.get("supported").getAsBoolean());
              for (Block block : logs) next.put(block, d);
            });
    definitions = Map.copyOf(next);
  }

  private static Set<Block> blocks(JsonArray array) {
    Set<Block> blocks = new HashSet<>();
    for (JsonElement e : array) {
      ResourceLocation id = new ResourceLocation(e.getAsString());
      if (!ForgeRegistries.BLOCKS.containsKey(id))
        throw new JsonParseException("Unknown forestry block: " + id);
      blocks.add(ForgeRegistries.BLOCKS.getValue(id));
    }
    return Set.copyOf(blocks);
  }
}
