package dev.automatons;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Balance {
  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.IntValue STORAGE,
      LUMBER,
      HEIGHT,
      CARGO,
      PREP,
      PROCESS,
      FALL,
      SCAN_BUDGET,
      TOTEM_BUDGET,
      ROOT_HEIGHT,
      INFUSE_INTERVAL,
      INFUSE_RANGE;
  public static final ForgeConfigSpec.DoubleValue WALK,
      DISPLAY_TILT,
      DISPLAY_PERIOD,
      DISPLAY_SCALE,
      DISPLAY_BOB,
      DISPLAY_HEIGHT;
  public static final ForgeConfigSpec.BooleanValue KEEP_MAGICA, DEBUG_WORK;
  public static final java.util.Map<String, ForgeConfigSpec.IntValue[]> YIELDS =
      new java.util.LinkedHashMap<>();

  static {
    var b = new ForgeConfigSpec.Builder();
    b.comment("Provisional first-playable defaults. Speed is subject to in-game calibration.")
        .push("provisional");
    STORAGE = b.defineInRange("storageRadius", 4, 1, 16);
    LUMBER = b.defineInRange("lumberRadius", 16, 4, 48);
    HEIGHT = b.defineInRange("storageHeight", 4, 1, 16);
    CARGO = b.defineInRange("cargoSlots", 9, 1, 27);
    WALK =
        b.comment(
                "Navigation attribute; provisional target is about 2 blocks/second, not a measured"
                    + " conversion.")
            .defineInRange("walkingAttribute", .215, .05, .5);
    PREP = b.defineInRange("ironAxePreparationTicksPerBaseLog", 80, 20, 1200);
    PROCESS = b.defineInRange("ironAxeProcessingTicks", 40, 10, 600);
    FALL = b.defineInRange("fallAnimationTicks", 30, 20, 100);
    SCAN_BUDGET = b.defineInRange("serverScanBudgetPerTick", 4096, 256, 16384);
    TOTEM_BUDGET = b.defineInRange("totemScanBudgetPerTick", 256, 4, 2048);
    ROOT_HEIGHT = b.defineInRange("rootHeight", 16, 1, 64);
    INFUSE_INTERVAL = b.defineInRange("infusionTicksPerPoint", 4, 1, 200);
    INFUSE_RANGE = b.defineInRange("infusionRange", 4, 1, 16);
    KEEP_MAGICA = b.define("retainMagicaOnDeath", true);
    DEBUG_WORK = b.define("logWorkTransitions", false);
    DISPLAY_TILT = b.defineInRange("displayTiltDegrees", 30.0, 0, 60);
    DISPLAY_PERIOD = b.defineInRange("displayRotationSeconds", 8.0, 1, 60);
    DISPLAY_SCALE = b.defineInRange("displayScale", .7, .1, 1.5);
    DISPLAY_BOB = b.defineInRange("displayBobAmplitude", .04, 0, .2);
    DISPLAY_HEIGHT = b.defineInRange("displayHeight", 1.75, 1.2, 3);
    b.pop();
    b.push("magicaYields");
    addYield(b, "logs", 3, 7);
    addYield(b, "crops", 2, 4);
    addYield(b, "fruit", 2, 4);
    addYield(b, "berries", 1, 3);
    addYield(b, "flowers", 1, 2);
    addYield(b, "stems", 0, 2);
    addYield(b, "leaves", 0, 1);
    addYield(b, "grass", 0, 1);
    b.pop();
    SPEC = b.build();
  }

  private static void addYield(ForgeConfigSpec.Builder b, String name, int min, int max) {
    b.push(name);
    YIELDS.put(
        name,
        new ForgeConfigSpec.IntValue[] {
          b.defineInRange("minimum", min, 0, 10000), b.defineInRange("maximum", max, 0, 10000)
        });
    b.pop();
  }

  public static int roll(String category, net.minecraft.util.RandomSource random) {
    var range = YIELDS.get(category);
    if (range == null) return 0;
    int min = range[0].get(), max = range[1].get();
    if (min > max) {
      max = min;
      range[1].set(max);
      org.slf4j.LoggerFactory.getLogger("automatons")
          .warn("Magica maximum for {} was below minimum; corrected to {}", category, max);
    }
    return min + random.nextInt(max - min + 1);
  }
}
