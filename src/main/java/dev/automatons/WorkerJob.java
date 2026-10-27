package dev.automatons;

/** Save IDs are explicit: never renumber existing portable workers. */
public final class WorkerJob {
  public static final int UNASSIGNED = 0,
      LUMBERTON = 1,
      MININGTON = 2,
      RUNNERTON = 3,
      BUILDINGTON = 4,
      FARMINGTON = 5,
      OPERATORTON = 6,
      HUNTERTON = 7, CRAFTERTON = 8, FISHERTON = 9, RANCHERTON = 10,
      QUARRYTON = 11, LUMINARITON = 12, GUARDINGTON = 13;

  public static int valid(int id) {
    return id >= 0 && id <= 13 ? id : 0;
  }

  public static String name(int id) {
    return switch (id) {
      case 1 -> "Lumberton";
      case 2 -> "Minington";
      case 3 -> "Runnerton";
      case 4 -> "Buildington";
      case 5 -> "Farmington";
      case 6 -> "Operatorton";
      case 7 -> "Hunterton";
      case 8 -> "Crafterton";
      case 9 -> "Fisherton";
      case 10 -> "Rancherton";
      case 11 -> "Quarryton";
      case 12 -> "Luminariton";
      case 13 -> "Guardington";
      default -> "Unassigned";
    };
  }
}
