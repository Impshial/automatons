package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.items.ItemStackHandler;

/** Bounded geometry, permissions and harvest transactions. No client-supplied block targets. */
public final class MachineTerrain {
  public record Plan(BlockPos destination, List<BlockPos> cuts, String error) {}

  private record Shape(double minX, double minZ, double maxX, double maxZ, double height) {}

  private static List<Shape> shapes(Machine m, int attachment) {
    List<Shape> result = new ArrayList<>();
    result.add(new Shape(-1.49, -1.49, 1.49, 1.49, 1.99));
    if (!m.getPassengers().isEmpty()) result.add(new Shape(-.34, .3, .34, 1.0, 2.45));
    if (attachment > 0)
      result.add(new Shape(-1.49, 1.49, 1.49, 2.49, attachment == 2 ? 2.99 : 1.99));
    return result;
  }

  private static Set<BlockPos> occupied(Machine m, BlockPos pos, double angle, int attachment) {
    Set<BlockPos> result = new LinkedHashSet<>();
    double sin = Math.sin(angle), cos = Math.cos(angle);
    // Local positive Z is forward. SAT tests each candidate unit voxel against the rotated box.
    for (Shape s : shapes(m, attachment))
      for (int dx = -4; dx <= 4; dx++)
        for (int dz = -4; dz <= 4; dz++) {
          double cx = (s.minX + s.maxX) / 2, cz = (s.minZ + s.maxZ) / 2;
          double hx = (s.maxX - s.minX) / 2, hz = (s.maxZ - s.minZ) / 2;
          double wx = cx * cos + cz * sin, wz = -cx * sin + cz * cos;
          double rx = dx - wx, rz = dz - wz;
          if (Math.abs(rx) >= hx * Math.abs(cos) + hz * Math.abs(sin) + .499
              || Math.abs(rz) >= hx * Math.abs(sin) + hz * Math.abs(cos) + .499) continue;
          if (Math.abs(rx * cos - rz * sin) >= hx + .499 * (Math.abs(cos) + Math.abs(sin))
              || Math.abs(rx * sin + rz * cos) >= hz + .499 * (Math.abs(cos) + Math.abs(sin)))
            continue;
          for (int y = 0; y < Math.ceil(s.height); y++) result.add(pos.offset(dx, y, dz));
        }
    return result;
  }

  private static double angle(Direction heading) {
    return Math.atan2(heading.getStepX(), heading.getStepZ());
  }

  public static Set<BlockPos> cells(Machine m, BlockPos pos, Direction heading, int attachment) {
    return occupied(m, pos, angle(heading), attachment);
  }

  public static String boundary(Machine m, Collection<BlockPos> positions) {
    if (m.pedestal() == null) return "Linked pedestal unavailable";
    for (BlockPos p : positions) {
      if (!m.level().hasChunkAt(p)) return "Terrain unloaded";
      if (m.level().isOutsideBuildHeight(p)) return "World height reached";
      // Include all four corners of each occupied cell, rather than its center alone.
      double dx = Math.abs(p.getX() - m.home.getX()) + .5,
          dz = Math.abs(p.getZ() - m.home.getZ()) + .5;
      if (dx * dx + dz * dz > MachineRules.RANGE * MachineRules.RANGE)
        return "Pedestal range reached";
    }
    return "";
  }

  private static boolean obstructs(Machine m, BlockPos p) {
    BlockState s = m.level().getBlockState(p);
    return !s.getFluidState().isEmpty() || !s.getCollisionShape(m.level(), p).isEmpty();
  }

  public static String clearance(Machine m, BlockPos pos, Direction facing, int attachment) {
    var cells = cells(m, pos, facing, attachment);
    String error = boundary(m, cells);
    // An unlinked, parked Machine may be equipped, but not moved.
    if (error.equals("Linked pedestal unavailable") && m.homeId == null) error = "";
    if (!error.isEmpty()) return error;
    for (BlockPos p : cells)
      if (obstructs(m, p))
        return "Clearance blocked: " + m.level().getBlockState(p).getBlock().getName().getString();
    return "";
  }

  public static String turnClearance(Machine m, Direction target) {
    double begin = angle(m.heading),
        delta = Math.atan2(Math.sin(angle(target) - begin), Math.cos(angle(target) - begin));
    Set<BlockPos> swept = new HashSet<>();
    for (int i = 0; i <= 90; i++)
      swept.addAll(occupied(m, m.grid, begin + delta * i / 90.0, m.attachment()));
    String error = boundary(m, swept);
    if (!error.isEmpty()) return error;
    for (BlockPos p : swept)
      if (obstructs(m, p))
        return "Turn blocked: " + m.level().getBlockState(p).getBlock().getName().getString();
    for (BlockPos p : swept)
      if (entities(m, new AABB(p).deflate(.01))) return "Turn blocked by entity";
    return "";
  }

  private static boolean solid(Machine m, BlockPos p) {
    return m.level().hasChunkAt(p)
        && m.level().getBlockState(p).isFaceSturdy(m.level(), p, Direction.UP);
  }

  private static boolean supported(Machine m, BlockPos pos, Direction travel) {
    for (int side = -1; side <= 1; side++)
      if (!solid(m, pos.relative(travel).relative(travel.getClockWise(), side).below()))
        return false;
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        BlockPos floor = pos.offset(x, -1, z);
        if (!solid(m, floor) && !solid(m, floor.below())) return false;
      }
    return true;
  }

  private static boolean entities(Machine m, BlockPos pos, Direction facing, int attachment) {
    AABB body =
        new AABB(
            pos.getX() - .99,
            pos.getY() + .01,
            pos.getZ() - .99,
            pos.getX() + 1.99,
            pos.getY() + 1.99,
            pos.getZ() + 1.99);
    if (attachment != 0)
      body = body.expandTowards(facing.getStepX(), attachment == 2 ? 1 : 0, facing.getStepZ());
    return entities(m, body);
  }

  private static boolean entities(Machine m, AABB body) {
    return !m.level()
        .getEntities(
            m,
            body,
            e ->
                !m.isPassengerOfSameVehicle(e)
                    && e.isPickable()
                    && !(e instanceof net.minecraft.world.entity.item.ItemEntity)
                    && !(e instanceof LuminaOrb))
        .isEmpty();
  }

  public static boolean compatible(Machine m, BlockPos p, BlockState state) {
    boolean pickaxe = state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    if (m.attachment() == 1)
      return !pickaxe
          && (!state.requiresCorrectToolForDrops()
              || new ItemStack(Items.DIAMOND_AXE).isCorrectToolForDrops(state)
              || new ItemStack(Items.DIAMOND_SHOVEL).isCorrectToolForDrops(state));
    return m.attachment() == 2
        && pickaxe
        && (!state.requiresCorrectToolForDrops()
            || new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state));
  }

  public static Plan plan(Machine m, Direction travel) {
    BlockPos base = m.grid.relative(travel);
    Plan failure = new Plan(base, List.of(), "Unsupported terrain");
    for (int dy : new int[] {0, 1, -1}) {
      BlockPos dest = base.above(dy);
      if (!supported(m, dest, travel)) continue;
      var occupied = cells(m, dest, m.heading, m.attachment());
      String boundary = boundary(m, occupied);
      if (!boundary.isEmpty()) return new Plan(dest, List.of(), boundary);
      if (entities(m, dest, m.heading, m.attachment()))
        return new Plan(dest, List.of(), "Path blocked by entity");
      List<BlockPos> cuts = new ArrayList<>();
      String error = "";
      for (BlockPos p : occupied) {
        var state = m.level().getBlockState(p);
        if (state.isAir()) continue;
        if (!state.getFluidState().isEmpty()) {
          error = "Path blocked by fluid";
          break;
        }
        int forward =
            (p.getX() - dest.getX()) * m.heading.getStepX()
                + (p.getZ() - dest.getZ()) * m.heading.getStepZ();
        boolean inFace =
            travel == m.heading
                && m.attachment() != 0
                && forward >= 1
                && p.getY() < dest.getY() + (m.attachment() == 2 ? 3 : 2);
        if (!inFace) {
          if (obstructs(m, p)) {
            error = "Path blocked: " + state.getBlock().getName().getString();
            break;
          } else continue;
        }
        if (m.job.active && m.mode == Machine.Mode.CHUNK && !m.job.insideChunk(p)) {
          if (obstructs(m, p)) {
            error = "Chunk edge obstructed";
            break;
          } else continue;
        }
        if (state.hasBlockEntity()
            || state.getDestroySpeed(m.level(), p) < 0
            || !compatible(m, p, state)) {
          error = "Attachment blocked: " + state.getBlock().getName().getString();
          break;
        }
        if (!Permissions.allowed((ServerLevel) m.level(), p, m.owner, Permissions.Action.PROCESS)) {
          error = "Protected block";
          break;
        }
        cuts.add(p);
      }
      if (error.isEmpty()) return new Plan(dest, cuts, "");
      failure = new Plan(dest, List.of(), error);
      // Never climb a two-high obstruction or use step logic to tunnel with the wrong tool.
      if (!cuts.isEmpty() || dy != 0) break;
    }
    return failure;
  }

  public static boolean cut(Machine m, List<BlockPos> positions) {
    ServerLevel level = (ServerLevel) m.level();
    Set<BlockPos> expanded = new LinkedHashSet<>(positions);
    if (m.attachment() == 1)
      for (BlockPos p : positions) {
        if (!TreeFinder.isRoot(level, p)) continue;
        var tree = TreeFinder.inspect(level, p, null).tree();
        if (tree == null) continue;
        // Species-bounded natural recognition refuses structures and touching independent trees.
        List<BlockPos> whole = new ArrayList<>(tree.logs());
        whole.addAll(tree.leaves());
        if (whole.size() > 512
            || !boundary(m, whole).isEmpty()
            || m.mode == Machine.Mode.CHUNK
                && m.job.active
                && whole.stream().anyMatch(q -> !m.job.insideChunk(q))) {
          m.status = "Tree crosses work boundary";
          return false;
        }
        expanded.addAll(whole);
      }
    ItemStack tool = new ItemStack(m.attachment() == 2 ? Items.DIAMOND_PICKAXE : Items.DIAMOND_AXE);
    ItemStackHandler trial = new ItemStackHandler(MachineRules.CARGO_SLOTS);
    trial.deserializeNBT(m.cargo.serializeNBT());
    record Cut(BlockPos pos, BlockState state, List<ItemStack> drops, String category) {}
    List<Cut> transaction = new ArrayList<>();
    for (BlockPos p : expanded) {
      BlockState s = level.getBlockState(p);
      if (!compatible(m, p, s)
          || s.hasBlockEntity()
          || s.getDestroySpeed(level, p) < 0
          || !Permissions.allowed(level, p, m.owner, Permissions.Action.PROCESS)) {
        m.status = "Work area changed";
        return false;
      }
      List<ItemStack> drops = Block.getDrops(s, level, p, null, m, tool);
      for (ItemStack stack : drops)
        if (!Inventories.insert(trial, stack.copy(), false).isEmpty()) {
          m.status = "Cargo full";
          m.job.cargoFull();
          return false;
        }
      transaction.add(
          new Cut(p, s, drops, m.attachment() == 1 ? LuminaHarvest.category(level, p, s) : ""));
    }
    if (m.fuel < transaction.size() * MachineRules.BREAK_COST) {
      m.job.fuelWaiting = m.job.active;
      m.halt("Out of Lumina");
      return false;
    }
    for (Cut c : transaction) {
      if (!level.getBlockState(c.pos).equals(c.state)) continue;
      if (!level.setBlock(c.pos, Blocks.AIR.defaultBlockState(), 3)) continue;
      for (ItemStack drop : c.drops) Inventories.insert(m.cargo, drop, false);
      m.fuel -= MachineRules.BREAK_COST;
      for (var reward :
          LuminaHarvest.drops(
              c.category, Balance.roll(c.category, level.random), level.random.nextInt(100)))
        m.fuel =
            Math.min(
                MachineRules.FUEL_THIRDS, m.fuel + reward.largeValue() * (reward.small() ? 1 : 3));
      var world = WorldData.get(level);
      world.placedLogs.remove(c.pos.asLong());
      world.placedPlants.remove(c.pos.asLong());
      world.naturalLogs.remove(c.pos.asLong());
      world.setDirty();
      WorldEvents.changedNear(level, c.pos);
      level.levelEvent(2001, c.pos, Block.getId(c.state));
    }
    m.sound(net.minecraft.sounds.SoundEvents.STONE_BREAK, .7f, .8f);
    return true;
  }

  private MachineTerrain() {}
}
