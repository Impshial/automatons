package dev.automatons;

import java.util.*;
import net.minecraft.core.Direction;

/** Pure bounded parser shared by validation, execution and the route preview. */
public final class MachineProgram {
  public enum Kind {
    MOVE,
    TURN,
    WAIT,
    DROPOFF
  }

  public record Instruction(Kind kind, Direction direction, int amount, int offset) {
    public String description() {
      return switch (kind) {
        case MOVE -> "Face " + direction.getName() + " and move " + amount + " blocks.";
        case TURN -> "Face " + direction.getName() + ".";
        case WAIT -> "Wait " + amount / 20 + " seconds.";
        case DROPOFF -> "Return to the saved start and unload cargo.";
      };
    }
  }

  public record Result(List<Instruction> instructions, String error, int offset) {
    public boolean valid() {
      return error.isEmpty();
    }
  }

  private static Result error(String text, int at, String message) {
    int line = 1, column = 1;
    for (int i = 0; i < Math.min(at, text.length()); i++) {
      if (text.charAt(i) == '\n') {
        line++;
        column = 1;
      } else column++;
    }
    return new Result(List.of(), "Line " + line + ", character " + column + ": " + message, at);
  }

  public static Result parse(String original) {
    if (original.length() > MachineRules.MAX_TEXT) return error(original, 0, "Program is too long");
    String text = original.toUpperCase(Locale.ROOT);
    List<Instruction> result = new ArrayList<>();
    int i = 0;
    while (i < text.length()) {
      if (Character.isWhitespace(text.charAt(i))) {
        i++;
        continue;
      }
      int start = i;
      if (text.startsWith("DROPOFF", i)) {
        result.add(new Instruction(Kind.DROPOFF, null, 0, i));
        i += 7;
      } else if (text.charAt(i) == '.') {
        while (i < text.length() && text.charAt(i) == '.') i++;
        result.add(new Instruction(Kind.WAIT, null, (i - start) * 20, start));
      } else {
        Direction direction =
            switch (text.charAt(i++)) {
              case 'N' -> Direction.NORTH;
              case 'S' -> Direction.SOUTH;
              case 'E' -> Direction.EAST;
              case 'W' -> Direction.WEST;
              default -> null;
            };
        if (direction == null)
          return error(original, start, "Expected N, S, E, W, a period, or DROPOFF");
        int numberStart = i, distance = 0;
        while (i < text.length() && Character.isDigit(text.charAt(i))) {
          char digit = text.charAt(i++);
          if (digit < '0' || digit > '9') return error(original, i - 1, "Use digits 0–9");
          distance = distance * 10 + digit - '0';
          if (distance > MachineRules.MAX_DISTANCE)
            return error(
                original, numberStart, "Distance must be at most " + MachineRules.MAX_DISTANCE);
        }
        if (numberStart != i && distance == 0)
          return error(original, numberStart, "Distance must be positive");
        if (i < text.length()
            && (text.charAt(i) == '-'
                || text.charAt(i) == '+'
                || text.charAt(i) == '.'
                    && i + 1 < text.length()
                    && Character.isDigit(text.charAt(i + 1))))
          return error(original, i, "Distance must be a positive whole number; periods are waits");
        result.add(
            new Instruction(numberStart == i ? Kind.TURN : Kind.MOVE, direction, distance, start));
      }
      if (result.size() > MachineRules.MAX_INSTRUCTIONS)
        return error(original, start, "Too many instructions");
    }
    return result.isEmpty()
        ? error(original, 0, "Add at least one instruction")
        : new Result(List.copyOf(result), "", 0);
  }
}
