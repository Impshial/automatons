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
      TOTEM_BUDGET;
  public static final ForgeConfigSpec.DoubleValue WALK;

  static {
    var b = new ForgeConfigSpec.Builder();
    b.comment("Provisional first-playable defaults. Speed is subject to in-game calibration.")
        .push("provisional");
    STORAGE = b.defineInRange("storageRadius", 4, 1, 16);
    LUMBER = b.defineInRange("lumberRadius", 16, 4, 48);
    HEIGHT = b.defineInRange("storageHeight", 4, 1, 16);
    CARGO = b.defineInRange("cargoSlots", 9, 9, 9);
    WALK =
        b.comment(
                "Navigation attribute; provisional target is about 2 blocks/second, not a measured"
                    + " conversion.")
            .defineInRange("walkingAttribute", .215, .05, .5);
    PREP = b.defineInRange("ironAxePreparationTicksPerBaseLog", 80, 20, 1200);
    PROCESS = b.defineInRange("ironAxeProcessingTicks", 40, 10, 600);
    FALL = b.defineInRange("fallAnimationTicks", 30, 20, 100);
    SCAN_BUDGET = b.defineInRange("serverScanBudgetPerTick", 512, 256, 4096);
    TOTEM_BUDGET = b.defineInRange("totemScanBudgetPerTick", 32, 4, 128);
    b.pop();
    SPEC = b.build();
  }
}
