package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.*;

/** Small ellipsoidal ore clusters, clipped at the requested height AND owning chunk boundary. */
public final class LumiteFeature extends Feature<LumiteConfiguration> {
  public static final long SALT = 0x4c554d4954450600L;

  public static LumiteConfiguration configured(net.minecraft.core.RegistryAccess registries) {
    return configured(registries, false);
  }

  public static LumiteConfiguration configured(
      net.minecraft.core.RegistryAccess registries, boolean deepslate) {
    var feature =
        registries
            .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
            .get(Automatons.id(deepslate ? "deepslate_lumite" : "lumite"));
    if (feature == null || !(feature.config() instanceof LumiteConfiguration config))
      throw new IllegalStateException("Lumite feature configuration unavailable");
    return config;
  }

  public LumiteFeature() {
    super(LumiteConfiguration.CODEC);
  }

  public static long seed(long worldSeed, ChunkPos chunk) {
    return worldSeed ^ chunk.x * 341873128712L ^ chunk.z * 132897987541L ^ SALT;
  }

  public static List<BlockPos> positions(
      long worldSeed, ChunkPos chunk, LumiteConfiguration config) {
    LinkedHashSet<BlockPos> out = new LinkedHashSet<>();
    RandomSource random = RandomSource.create(seed(worldSeed, chunk));
    int span = Math.max(0, config.maximum() - config.minimum());
    int low = span / 2, high = span - low;
    int size = config.ore().size;
    for (int attempt = 0; attempt < config.attempts(); attempt++) {
      int x = chunk.getMinBlockX() + random.nextInt(16),
          z = chunk.getMinBlockZ() + random.nextInt(16);
      int y = config.minimum() + random.nextInt(low + 1) + random.nextInt(high + 1);
      float angle = random.nextFloat() * (float) Math.PI;
      double dx = Math.sin(angle) * size / 8.0, dz = Math.cos(angle) * size / 8.0;
      // Center on block centers, so the triangular peak is not shifted down by 1.5 blocks.
      double y1 = y + random.nextInt(3) - .5, y2 = y + random.nextInt(3) - .5;
      double[][] spheres = new double[size][4];
      for (int n = 0; n < size; n++) {
        double t = (double) n / size;
        double radius = ((Math.sin(Math.PI * t) + 1) * random.nextDouble() * size / 16.0 + 1) / 2;
        spheres[n] =
            new double[] {
              Mth.lerp(t, x + dx, x - dx), Mth.lerp(t, y1, y2), Mth.lerp(t, z + dz, z - dz), radius
            };
      }
      for (int a = 0; a < size; a++)
        for (int b = a + 1; b < size; b++) {
          double[] u = spheres[a], v = spheres[b];
          if (u[3] < 0 || v[3] < 0) continue;
          double r = u[3] - v[3],
              d = Math.pow(u[0] - v[0], 2) + Math.pow(u[1] - v[1], 2) + Math.pow(u[2] - v[2], 2);
          if (r * r > d) {
            if (r > 0) v[3] = -1;
            else u[3] = -1;
          }
        }
      for (double[] s : spheres) {
        if (s[3] < 0) continue;
        for (int xx = Math.max(chunk.getMinBlockX(), Mth.floor(s[0] - s[3]));
            xx <= Math.min(chunk.getMaxBlockX(), Mth.floor(s[0] + s[3]));
            xx++)
          for (int yy = Math.max(config.minimum(), Mth.floor(s[1] - s[3]));
              yy <= Math.min(config.maximum(), Mth.floor(s[1] + s[3]));
              yy++)
            for (int zz = Math.max(chunk.getMinBlockZ(), Mth.floor(s[2] - s[3]));
                zz <= Math.min(chunk.getMaxBlockZ(), Mth.floor(s[2] + s[3]));
                zz++)
              if (Math.pow((xx + .5 - s[0]) / s[3], 2)
                      + Math.pow((yy + .5 - s[1]) / s[3], 2)
                      + Math.pow((zz + .5 - s[2]) / s[3], 2)
                  < 1) out.add(new BlockPos(xx, yy, zz));
      }
    }
    return List.copyOf(out);
  }

  public static BlockState replacement(
      BlockState current, LumiteConfiguration config, long seed, BlockPos pos) {
    if (current.hasBlockEntity() || !current.getFluidState().isEmpty()) return null;
    RandomSource random = RandomSource.create(seed ^ pos.asLong());
    for (var target : config.ore().targetStates)
      if (target.target.test(current, random)) return target.state;
    return null;
  }

  @Override
  public boolean place(FeaturePlaceContext<LumiteConfiguration> context) {
    WorldGenLevel level = context.level();
    if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
    ChunkPos chunk = new ChunkPos(context.origin());
    int placed = 0;
    for (BlockPos pos : positions(level.getSeed(), chunk, context.config())) {
      if (!level.getBiome(pos).is(BiomeTags.IS_OVERWORLD)) continue;
      BlockState replacement =
          replacement(
              level.getBlockState(pos), context.config(), seed(level.getSeed(), chunk), pos);
      if (replacement != null && level.setBlock(pos, replacement, 2)) placed++;
    }
    return placed > 0;
  }
}
