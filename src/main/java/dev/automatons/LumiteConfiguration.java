package dev.automatons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.*;

/** Data-driven normal generation parameters, also used by the bundled JER estimate. */
public record LumiteConfiguration(OreConfiguration ore, int minimum, int maximum, int attempts)
    implements FeatureConfiguration {
  public static final Codec<LumiteConfiguration> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      OreConfiguration.CODEC.fieldOf("ore").forGetter(LumiteConfiguration::ore),
                      Codec.intRange(-64, 320)
                          .fieldOf("min_y")
                          .forGetter(LumiteConfiguration::minimum),
                      Codec.intRange(-64, 320)
                          .fieldOf("max_y")
                          .forGetter(LumiteConfiguration::maximum),
                      Codec.intRange(1, 64)
                          .fieldOf("attempts")
                          .forGetter(LumiteConfiguration::attempts))
                  .apply(i, LumiteConfiguration::new));
}
