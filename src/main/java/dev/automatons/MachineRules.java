package dev.automatons;

/** All new vehicle balancing values live here; fuel is stored in thirds of one Lumina. */
public final class MachineRules {
  public static final int RANGE = 48, CARGO_SLOTS = 108, FUEL_THIRDS = 30000;
  public static final int START_TICKS = 30, STOP_TICKS = 12, MOVE_TICKS = 12, TURN_TICKS = 12;
  public static final int MOVE_COST = 1, TURN_COST = 1, BREAK_COST = 1, BREAK_TICKS = 4;
  public static final int MAX_TEXT = 8192, MAX_DISTANCE = 4096, MAX_INSTRUCTIONS = 2048;
  public static final int MAX_ROUTE = 16384, RETRY_TICKS = 40;

  private MachineRules() {}
}
