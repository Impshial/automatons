package dev.automatons;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in disposable-world integration review. Never enabled by installation or normal gameplay. */
@Mod.EventBusSubscriber(modid = Automatons.ID)
public final class LuminaWorldSmoke {
  private static int ticks, index;
  private static boolean finished;
  private static final JsonObject report = new JsonObject();
  private static final int[][] SAMPLES = {
    {0, 0},
    {4, 4},
    {-4, 4},
    {4, -4},
    {-4, -4},
    {12, 0},
    {0, 12},
    {-12, 0},
    {0, -12},
    {12, 12},
    {-12, -12},
    {12, -12}
  };
  private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

  private static String mode() {
    return System.getProperty("automatons.luminaWorldSmoke", "");
  }

  private static Path directory(ServerLevel l) {
    return l.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent();
  }

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }

  private static void write(ServerLevel l, String file, JsonObject data) throws Exception {
    Files.writeString(directory(l).resolve(file), JSON.toJson(data));
  }

  private static boolean ore(net.minecraft.world.level.block.state.BlockState s) {
    return s.is(Automatons.LUMITE_ORE.get()) || s.is(Automatons.DEEPSLATE_LUMITE_ORE.get());
  }

  private static int count(LevelChunk chunk, JsonObject entry) {
    int total = 0, regular = 0, deep = 0;
    Set<BlockPos> orePositions = new HashSet<>();
    int[] bands = new int[9];
    for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++)
      for (int x = 0; x < 16; x++)
        for (int z = 0; z < 16; z++) {
          BlockPos p =
              new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
          var state = chunk.getBlockState(p);
          if (!ore(state)) continue;
          check(
              state.is(Automatons.DEEPSLATE_LUMITE_ORE.get())
                  ? y >= -64 && y <= 0
                  : y >= -32 && y <= 64,
              "Actual ore outside its variant's vertical range");
          if (state.is(Automatons.LUMITE_ORE.get())) regular++;
          else deep++;
          total++;
          orePositions.add(p);
          bands[Math.min(8, (y + 64) / 16)]++;
        }
    entry.addProperty("oreBlocks", total);
    entry.addProperty("stoneVariant", regular);
    entry.addProperty("deepslateVariant", deep);
    entry.add("heightBandsStartingMinus64", JSON.toJsonTree(bands));
    List<Integer> clusters = new ArrayList<>();
    while (!orePositions.isEmpty()) {
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      queue.add(orePositions.iterator().next());
      int size = 0;
      while (!queue.isEmpty()) {
        BlockPos p = queue.removeFirst();
        if (!orePositions.remove(p)) continue;
        size++;
        for (Direction direction : Direction.values()) queue.add(p.relative(direction));
      }
      clusters.add(size);
    }
    clusters.sort(Integer::compare);
    entry.add("faceConnectedClustersWithinChunk", JSON.toJsonTree(clusters));
    return total;
  }

  private static void sample(ServerLevel l) throws Exception {
    if (index < SAMPLES.length) {
      int[] p = SAMPLES[index++];
      var chunk = l.getChunk(p[0], p[1]);
      JsonObject entry = new JsonObject();
      entry.addProperty("x", p[0]);
      entry.addProperty("z", p[1]);
      entry.addProperty(
          "surfaceBiome",
          l.getBiome(new BlockPos(p[0] * 16 + 8, 64, p[1] * 16 + 8))
              .unwrapKey()
              .orElseThrow()
              .location()
              .toString());
      count(chunk, entry);
      check(LumiteChunkData.get(chunk).normal, "New chunk lacks normal-generation provenance");
      if (!report.has("chunks")) report.add("chunks", new JsonArray());
      report.getAsJsonArray("chunks").add(entry);
      System.out.println("LUMITE_WORLD_SAMPLE " + entry);
      return;
    }
    for (var key : List.of(Level.NETHER, Level.END)) {
      ServerLevel other = l.getServer().getLevel(key);
      var chunk = other.getChunk(0, 0);
      JsonObject entry = new JsonObject();
      check(count(chunk, entry) == 0, "Lumite generated outside Overworld");
    }
    report.addProperty("seed", l.getSeed());
    report.addProperty("configuredSize", LumiteFeature.configured(l.registryAccess()).ore().size);
    if (Files.exists(directory(l).resolve("pending-history-before.nbt"))) {
      CompoundTag before;
      try (var input =
          new java.io.DataInputStream(
              Files.newInputStream(directory(l).resolve("pending-history-before.nbt")))) {
        before = NbtIo.read(input);
      }
      check(
          LumiteRetrogen.get(l).save(new CompoundTag()).equals(before.getCompound("data")),
          "Legacy pending history mutated");
      check(l.getForcedChunks().isEmpty(), "Unexpected forced chunks while loading legacy history");
      report.addProperty("pendingHistoryPreserved", true);
    }
    report.addProperty("success", true);
    report.addProperty("mode", "sample");
    write(l, "lumina-world-report.json", report);
    finished = true;
    System.out.println(
        "LUMINA_WORLD_SAMPLE_COMPLETE seed=" + l.getSeed() + " chunks=" + SAMPLES.length);
    l.getServer()
        .reloadResources(l.getServer().getPackRepository().getSelectedIds())
        .whenComplete(
            (unused, failure) -> {
              try {
                if (failure != null) throw new IllegalStateException("Data reload failed", failure);
                var dispatcher = l.getServer().getCommands().getDispatcher();
                check(
                    dispatcher.getRoot().getChild("automaton") == null
                        && dispatcher.getRoot().getChild("automatons") == null,
                    "Retired command reappeared after reload");
                check(
                    dispatcher.getRoot().getChild("give") != null
                        && dispatcher.getRoot().getChild("reload") != null,
                    "Vanilla commands disappeared");
                var suggestions =
                    dispatcher
                        .getCompletionSuggestions(
                            dispatcher.parse("automaton", l.getServer().createCommandSourceStack()))
                        .join();
                check(
                    suggestions.getList().stream()
                        .noneMatch(s -> s.getText().startsWith("automaton")),
                    "Retired command suggested after reload");
                report.addProperty("commandsAbsentAfterReload", true);
                write(l, "lumina-world-report.json", report);
              } catch (Throwable error) {
                try {
                  report.addProperty("success", false);
                  report.addProperty("failure", error.toString());
                  write(l, "lumina-world-failure.json", report);
                } catch (Exception ignored) {
                }
              } finally {
                l.getServer().halt(false);
              }
            });
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent e) {
    if (mode().isEmpty() || e.phase != TickEvent.Phase.END || finished) return;
    ServerLevel l = e.getServer().overworld();
    if (!directory(l).toString().contains("lumina-verification"))
      throw new IllegalStateException(
          "Lumina world smoke restricted to disposable verification directories");
    if (++ticks < 40 || ticks % 20 != 0) return;
    try {
      if (mode().equals("sample")) sample(l);
      else throw new IllegalArgumentException("Retrogen review is retired; use sample mode");
      if (ticks > 36000) throw new IllegalStateException("World verification exceeded 30 minutes");
    } catch (Throwable error) {
      finished = true;
      report.addProperty("success", false);
      report.addProperty("failure", error.toString());
      try {
        write(l, "lumina-world-failure.json", report);
      } catch (Exception ignored) {
      }
      org.slf4j.LoggerFactory.getLogger("automatons.verification")
          .error("LUMINA_WORLD_FAILED", error);
      l.getServer().halt(false);
    }
  }
}
