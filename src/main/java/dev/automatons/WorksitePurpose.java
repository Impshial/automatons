package dev.automatons;

/** Saved purpose is independent of every worker's profession. */
public enum WorksitePurpose {
  UNCONFIGURED("Unconfigured", ""),
  MINE("Mine", "minehead"),
  LUMBER("Lumber Yard", "lumber"),
  FARM("Farm", "farm"),
  WAREHOUSE("Warehouse", "warehouse"),
  BUILDER("Building", "builder"),
  HUNT("Hunting", "hunterton_shed"),
  CRAFT("Crafting", "crafterton_workshop"),
  FISH("Fishing", "fisherton_hut_dock"),
  RANCH("Ranching", "rancherton_ranch"),
  QUARRY("Quarrying", "quarryton_surface"),
  LUMINA("Lumina service", "luminariton_tower_courtyard");
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
          case HUNT -> 96;
          case LUMINA -> 40;
          case RANCH, QUARRY, FISH -> 32;
          default -> 8;
        };
    return base;
  }

  public int job() {
    return switch (this) {
      case MINE -> WorkerJob.MININGTON;
      case LUMBER -> WorkerJob.LUMBERTON;
      case FARM -> WorkerJob.FARMINGTON;
      case WAREHOUSE -> WorkerJob.RUNNERTON;
      case BUILDER -> WorkerJob.BUILDINGTON;
      case HUNT -> WorkerJob.HUNTERTON;
      case CRAFT -> WorkerJob.CRAFTERTON;
      case FISH -> WorkerJob.FISHERTON;
      case RANCH -> WorkerJob.RANCHERTON;
      case QUARRY -> WorkerJob.QUARRYTON;
      case LUMINA -> WorkerJob.LUMINARITON;
      default -> WorkerJob.UNASSIGNED;
    };
  }
}
