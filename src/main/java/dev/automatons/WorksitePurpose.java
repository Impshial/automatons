package dev.automatons;

/** Saved purpose is independent of every worker's profession. */
public enum WorksitePurpose {
  UNCONFIGURED("Unconfigured", ""),
  MINE("Mine", "minehead"),
  LUMBER("Lumber Yard", "lumber"),
  FARM("Farm", "farm"),
  WAREHOUSE("Warehouse", "warehouse"),
  BUILDER("Builder", "builder");
  public final String label, blueprint;

  WorksitePurpose(String label, String blueprint) {
    this.label = label;
    this.blueprint = blueprint;
  }

  public static WorksitePurpose of(int value) {
    return value >= 0 && value < values().length ? values()[value] : UNCONFIGURED;
  }

  public int radius(Station s) {
    int base =
        switch (this) {
          case MINE -> Balance.MINE_SITE_RANGE.get();
          case LUMBER -> Balance.LUMBER.get();
          case FARM -> Balance.FARM_RANGE.get();
          case BUILDER -> Balance.BUILDER_RANGE.get();
          case WAREHOUSE -> Balance.WAREHOUSE_RANGE.get();
          default -> 8;
        };
    return base;
  }
}
