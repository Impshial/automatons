package dev.automatons;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Balance {
  public static final ForgeConfigSpec.IntValue PLANT_RESERVE, CROP_RESERVE;
  public static final ForgeConfigSpec SPEC;
  public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>>
      RETROGEN_EXCLUSIONS;
  public static final ForgeConfigSpec.IntValue LUMBER,
      HEIGHT,
      CARGO,
      PREP,
      PROCESS,
      FALL,
      SCAN_BUDGET,
      TOTEM_BUDGET,
      ROOT_HEIGHT,
      MINING_RANGE,
      FARM_RANGE,
      MINE_SITE_RANGE,
      BUILDER_RANGE,
      WAREHOUSE_RANGE,
      MINING_HEIGHT,
      TORCH_STOCK,
      MINE_LIGHT,
      LOGISTICS_INTERVAL,
      LOGISTICS_SLOTS,
      RUNNER_BATCH,
      INFUSE_INTERVAL,
      INFUSE_RANGE;
  public static final ForgeConfigSpec.IntValue COLLECTOR_HEIGHT,
      COLLECTOR_CAPACITY,
      TANK_CAPACITY,
      COLLECTOR_INTERVAL,
      COLLECTOR_MAX,
      BOTTLE_VOLUME,
      BOTTLE_LUMINA;
  public static final java.util.Map<String, ForgeConfigSpec.IntValue> COLLECTOR_YIELDS =
      new java.util.LinkedHashMap<>();
  public static final ForgeConfigSpec.DoubleValue WALK,
      RUNNER_WALK,
      MINING_REACH,
      DISPLAY_TILT,
      DISPLAY_PERIOD,
      DISPLAY_SCALE,
      DISPLAY_BOB,
      DISPLAY_HEIGHT;
  public static final ForgeConfigSpec.BooleanValue KEEP_LUMINA, DEBUG_WORK;
  public static final java.util.Map<String, ForgeConfigSpec.IntValue[]> YIELDS =
      new java.util.LinkedHashMap<>();

  static {
    var b = new ForgeConfigSpec.Builder();
    b.comment("Provisional first-playable defaults. Speed is subject to in-game calibration.")
        .push("provisional");
    LUMBER = b.defineInRange("lumberRadius", 32, 4, 64);
    FARM_RANGE = b.defineInRange("farmRadius", 32, 9, 64);
    MINE_SITE_RANGE = b.defineInRange("mineSurfaceRadius", 6, 4, 16);
    BUILDER_RANGE = b.defineInRange("builderRadius", 8, 4, 32);
    WAREHOUSE_RANGE = b.defineInRange("warehouseRadius", 8, 8, 32);
    HEIGHT = b.defineInRange("storageHeight", 4, 1, 16);
    CARGO = b.defineInRange("cargoSlots", 9, 1, 9);
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
    ROOT_HEIGHT = b.defineInRange("rootHeight", 32, 1, 64);
    MINING_RANGE = b.defineInRange("miningRadius", 32, 32, 64);
    MINING_HEIGHT = b.defineInRange("mineEntranceHeight", 16, 1, 64);
    MINING_REACH = b.defineInRange("miningInteractionReach", 4.5, 2, 4.5);
    TORCH_STOCK = b.defineInRange("miningTorchStock", 16, 1, 64);
    MINE_LIGHT = b.defineInRange("miningBlockLightTarget", 8, 1, 14);
    LOGISTICS_INTERVAL = b.defineInRange("logisticsReconcileTicks", 40, 20, 200);
    LOGISTICS_SLOTS =
        b.comment(
                "Maximum inventory slots sampled per home network per reconciliation; independent"
                    + " of terrain scans.")
            .defineInRange("logisticsSlotsPerReconcile", 512, 54, 4096);
    RUNNER_BATCH = b.defineInRange("runnerCollectionBatch", 256, 1, 1152);
    RUNNER_WALK = b.defineInRange("runnerWalkingAttribute", .215, .05, .5);
    INFUSE_INTERVAL = b.defineInRange("infusionTicksPerPoint", 4, 1, 200);
    INFUSE_RANGE = b.defineInRange("infusionRange", 4, 1, 16);
    KEEP_LUMINA = b.define("retainMagicaOnDeath", true);
    DEBUG_WORK = b.define("logWorkTransitions", false);
    DISPLAY_TILT = b.defineInRange("displayTiltDegrees", 30.0, 0, 60);
    DISPLAY_PERIOD = b.defineInRange("displayRotationSeconds", 8.0, 1, 60);
    DISPLAY_SCALE = b.defineInRange("displayScale", .7, .1, 1.5);
    DISPLAY_BOB = b.defineInRange("displayBobAmplitude", .04, 0, .2);
    DISPLAY_HEIGHT = b.defineInRange("displayHeight", 1.4, 1.0, 3);
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
    b.push("luminall");
    PLANT_RESERVE = b.comment("Lifetime Lumina of an eligible non-tree plant; debited only by actual collection.").defineInRange("plantLifetimeLumina", 200, 1, 1000000);
    CROP_RESERVE = b.comment("Lifetime Lumina of an eligible crop; ordinary growth does not replenish it.").defineInRange("cropLifetimeLumina", 100, 1, 1000000);
    COLLECTOR_HEIGHT =
        b.comment("Vertical reach above AND below the collector; horizontal offsets are -1..2.")
            .defineInRange("verticalReach", 4, 1, 16);
    COLLECTOR_CAPACITY = b.defineInRange("collectorCapacityMb", 4000, 1000, 1000000);
    TANK_CAPACITY = b.defineInRange("tankCapacityMb", 8000, 1000, 1000000);
    COLLECTOR_INTERVAL = b.defineInRange("collectionIntervalTicks", 100, 20, 1200);
    COLLECTOR_MAX =
        b.comment("Maximum mB per collector per collection interval.")
            .defineInRange("maximumMbPerCollection", 5, 1, 1000);
    BOTTLE_VOLUME =
        b.comment(
                "Volume for newly filled bottles. Existing bottles retain their volume and reward.")
            .defineInRange("bottleVolumeMb", 250, 250, 250);
    BOTTLE_LUMINA = b.defineInRange("luminaPerBottle", 125, 125, 125);
    b.push("producerMbPerCollection");
    for (String category : YIELDS.keySet())
      COLLECTOR_YIELDS.put(
          category,
          b.defineInRange(
              category,
              switch (category) {
                case "logs", "fruit", "crops" -> 3;
                case "flowers", "berries" -> 2;
                default -> 1;
              },
              0,
              1000));
    b.pop();
    b.pop();
    // Retained only to preserve existing server configuration; no job can start or resume.
    b.push("lumiteRetrogen");
    RETROGEN_EXCLUSIONS =
        b.comment("Legacy exclusion records retained for compatibility. Retrogen is retired.")
            .defineListAllowEmpty(
                "excludedChunkRectangles",
                java.util.List.of(),
                value ->
                    value instanceof String s
                        && s.matches("-?\\d+,\\s*-?\\d+,\\s*-?\\d+,\\s*-?\\d+"));
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
          .warn("Lumina maximum for {} was below minimum; corrected to {}", category, max);
    }
    return min + random.nextInt(max - min + 1);
  }
}
