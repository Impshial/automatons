package dev.automatons;

/** Save IDs are explicit: never renumber existing portable workers. */
public final class WorkerJob {
  public static final int UNASSIGNED = 0,
      LUMBERTON = 1,
      MININGTON = 2,
      RUNNINGTON = 3,
      BUILDINGTON = 4,
      FARMINGTON = 5,
      OPERATORTON = 6;

  public static int valid(int id) {
    return id >= 0 && id <= 6 ? id : 0;
  }

  public static String name(int id) {
    return switch (id) {
      case 1 -> "Lumberton";
      case 2 -> "Minington";
      case 3 -> "Runnington";
      case 4 -> "Buildington";
      case 5 -> "Farmington";
      case 6 -> "Operatorton";
      default -> "Unassigned";
    };
  }
}
