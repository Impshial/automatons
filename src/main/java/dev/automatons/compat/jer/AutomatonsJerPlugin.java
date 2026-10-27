package dev.automatons.compat.jer;

import dev.automatons.*;
import jeresources.api.*;
import jeresources.api.conditionals.Conditional;
import jeresources.api.distributions.DistributionBase;
import jeresources.api.drop.LootDrop;
import jeresources.api.restrictions.Restriction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Optional client integration. No common mod entry point references JER classes. */
@JERPlugin
public final class AutomatonsJerPlugin implements IJERPlugin {
  private static boolean registered;

  public static void initialize() {
    // Forge 1.4.0.247 scans for IJERPlugin instead of @JERPlugin. Its public API singleton
    // is the same instance passed by discovery; use it after common setup as a fallback.
    new AutomatonsJerPlugin().receive(jeresources.compatibility.api.JERAPI.getInstance());
  }

  @Override
  public void receive(IJERAPI api) {
    if (FMLEnvironment.dist != Dist.CLIENT || registered) return;
    registered = true;
    register(api, false);
    register(api, true);
  }

  private void register(IJERAPI api, boolean deepslate) {
    var config = LumiteDefaults.read(deepslate);
    var estimate = new PlacementEstimate(config);
    api.getWorldGenRegistry()
        .register(
            new ItemStack(
                deepslate ? Automatons.DEEPSLATE_LUMITE_ORE.get() : Automatons.LUMITE_ORE.get()),
            estimate,
            Restriction.OVERWORLD,
            true,
            new LootDrop(
                Automatons.LUMITE_SHARD.get(),
                1,
                3,
                Conditional.affectedByFortune,
                new Conditional("jer.automatons.unenchanted"),
                new Conditional("jer.automatons.tool"),
                new Conditional("jer.automatons.estimate")));
    org.slf4j.LoggerFactory.getLogger("automatons.jer")
        .info(
            "Registered Lumite JER candidate estimate: attempts={}, size={}, Y={}..{}, configured"
                + " peak={}",
            config.attempts(),
            config.ore().size,
            config.minimum(),
            config.maximum(),
            estimate.getBestHeight());
  }

  /**
   * Candidate positions from the real cluster algorithm, before terrain/biome replacement. This is
   * explicitly a configured estimate, never presented as a surveyed-world histogram.
   */
  private static final class PlacementEstimate extends DistributionBase {
    PlacementEstimate(LumiteConfiguration config) {
      super(estimate(config));
      // The configured triangular mode is exact; finite sampling noise in the
      // displayed candidate histogram must not shift the advertised mining height.
      bestHeight = (config.minimum() + config.maximum()) / 2;
    }

    private static float[] estimate(LumiteConfiguration config) {
      float[] histogram = new float[320];
      int samples = 4096;
      for (int i = 0; i < samples; i++)
        for (var pos : LumiteFeature.positions(0, new ChunkPos(i % 64, i / 64), config))
          if (pos.getY() >= -64 && pos.getY() < 256) histogram[pos.getY() + 64]++;
      for (int i = 0; i < histogram.length; i++) histogram[i] /= samples * 256f;
      return histogram;
    }
  }
}
