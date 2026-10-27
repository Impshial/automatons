package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Formation is a single server transaction, after all 24 cells and permissions are checked. */
public final class MachinePattern {
  public record Cell(BlockPos pos, Block block) {}

  public static List<Cell> cells(BlockPos front, Direction facing) {
    List<Cell> cells = new ArrayList<>();
    Direction right = facing.getClockWise();
    for (int y = 0; y < 2; y++)
      for (int row = 0; row < 4; row++)
        for (int side = -1; side <= 1; side++) {
          String name;
          if (y == 0) name = row > 0 && side != 0 ? "machine_track" : "machine_frame";
          else if (row == 0) name = side == 0 ? "attachment_mount" : "machine_frame";
          else if (row == 3) name = side == 0 ? "lumina_engine" : "machine_frame";
          else name = side != 0 ? "cargo_compartment" : row == 1 ? "operator_station" : "tank";
          cells.add(
              new Cell(
                  front.relative(facing.getOpposite(), row).relative(right, side).above(y),
                  name.equals("tank") ? Luminall.TANK.get() : MachineParts.block(name)));
        }
    return cells;
  }

  public static String form(ServerPlayer player, BlockPos clicked) {
    ServerLevel level = player.serverLevel();
    for (Direction direction : Direction.Plane.HORIZONTAL)
      for (int y = 0; y < 2; y++)
        for (int row = 0; row < 4; row++)
          for (int side = -1; side <= 1; side++) {
            BlockPos front =
                clicked.below(y).relative(direction, row).relative(direction.getClockWise(), -side);
            List<Cell> cells = cells(front, direction);
            if (cells.stream()
                .anyMatch(c -> !level.hasChunkAt(c.pos) || !level.getBlockState(c.pos).is(c.block)))
              continue;
            for (Cell cell : cells)
              if (!Permissions.allowed(
                  level, cell.pos, player.getUUID(), Permissions.Action.PROCESS))
                return "Assembly is protected";
            Machine machine = MachineParts.ENTITY.get().create(level);
            if (machine == null) return "Unable to form The Machine";
            machine.owner = player.getUUID();
            machine.grid = front.relative(direction.getOpposite());
            machine.heading = direction;
            machine.snap();
            for (Cell cell : cells) {
              if (level.getBlockEntity(cell.pos) instanceof Reservoir reservoir) {
                machine.tankData = reservoir.saveWithoutMetadata();
              }
            }
            machine.linkNearest();
            machine.sync();
            // Spawn can be vetoed by another mod. Nothing has been removed at that point.
            if (!level.addFreshEntity(machine)) return "Formation was denied";
            for (Cell cell : cells) {
              if (level.getBlockEntity(cell.pos) instanceof Reservoir r) r.bottle = ItemStack.EMPTY;
              level.removeBlockEntity(cell.pos);
              level.setBlock(cell.pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return machine.homeId == null
                ? "The Machine formed — select a pedestal in Controls"
                : "The Machine formed — parked";
          }
    return "Incomplete Machine: check both 3 × 4 layers and all 24 components";
  }

  private MachinePattern() {}
}
