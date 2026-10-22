package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class TreeFinder {
  public record Tree(BlockPos root, List<BlockPos> logs, List<BlockPos> leaves, int baseCount) {}

  public record Result(Tree tree, String reason) {}

  public record Landing(List<BlockPos> targets, Set<BlockPos> sweep, Direction direction) {}

  public static final TagKey<Block> SUPPORTED =
      TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Automatons.id("tree_logs"));

  public static boolean supported(BlockState s) {
    return TreeSpecies.forLog(s.getBlock()) != null;
  }

  public static boolean soil(BlockState s) {
    return s.is(BlockTags.DIRT) || s.is(Blocks.MUD) || s.is(Blocks.MUDDY_MANGROVE_ROOTS);
  }

  public static boolean isRoot(ServerLevel l, BlockPos p) {
    return supported(l.getBlockState(p)) && soil(l.getBlockState(p.below()));
  }

  public static boolean within(Station s, BlockPos p) {
    return Math.abs(p.getX() - s.getBlockPos().getX()) <= s.lumberRange()
        && Math.abs(p.getZ() - s.getBlockPos().getZ()) <= s.lumberRange();
  }

  public static Result inspect(ServerLevel l, BlockPos root, Station s) {
    if (!isRoot(l, root)) return new Result(null, "Not a rooted tree");
    Block species = l.getBlockState(root).getBlock();
    var definition = TreeSpecies.forLog(species);
    if (definition == null) return new Result(null, "Unsupported tree species");
    if (!definition.supported())
      return new Result(null, "Mangrove root shapes need manual harvesting");
    Set<BlockPos> visited = new HashSet<>();
    ArrayDeque<BlockPos> todo = new ArrayDeque<>();
    todo.add(root);
    Set<BlockPos> leaves = new HashSet<>();
    int base = 0;
    var data = WorldData.get(l);
    while (!todo.isEmpty()) {
      BlockPos p = todo.removeFirst();
      if (visited.contains(p)) continue;
      if (!l.hasChunkAt(p)) return new Result(null, "Tree crosses unloaded chunks");
      if (l.getBlockState(p).getBlock() != species) continue;
      if (p.getY() < root.getY()
          || p.getY() > root.getY() + definition.height()
          || Math.abs(p.getX() - root.getX()) > definition.spread()
          || Math.abs(p.getZ() - root.getZ()) > definition.spread()
          || visited.size() >= definition.maximumLogs())
        return new Result(null, "Ambiguous or oversized tree");
      if (!within(s, p)) return new Result(null, "Tree crosses lumber boundary");
      if (data.protectedAt(p)) return new Result(null, "Protected tree");
      if (data.placedLogs.contains(p.asLong()))
        return new Result(null, "Player-placed structural wood");
      if (isRoot(l, p)) {
        if (p.getY() != root.getY()
            || Math.abs(p.getX() - root.getX()) > 1
            || Math.abs(p.getZ() - root.getZ()) > 1)
          return new Result(null, "Touching trees: harvest separately");
        base++;
      }
      visited.add(p.immutable());
      for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
          for (int z = -1; z <= 1; z++) if (x != 0 || y != 0 || z != 0) todo.add(p.offset(x, y, z));
      if (p.getY() > root.getY() + 1)
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 2, 2))) {
          if (!l.hasChunkAt(q)) return new Result(null, "Canopy crosses unloaded chunks");
          BlockState state = l.getBlockState(q);
          if (definition.leaves().contains(state.getBlock())
              && state.hasProperty(LeavesBlock.PERSISTENT)
              && !state.getValue(LeavesBlock.PERSISTENT)) leaves.add(q.immutable());
        }
    }
    if (visited.size() < 3 || leaves.size() < 8 || base < 1 || base > 4)
      return new Result(null, "No convincing natural canopy");
    int maxY = visited.stream().mapToInt(BlockPos::getY).max().orElse(root.getY());
    if (maxY - root.getY() < 2) return new Result(null, "Trunk too short");
    for (BlockPos p : leaves)
      if (data.protectedAt(p) || !within(s, p))
        return new Result(null, "Canopy crosses protected or out-of-range space");
    // A multi-root candidate must be a solid 2x2, never a loose pair of neighboring trees.
    if (base > 1 && !definition.largeTrunk())
      return new Result(null, "Neighboring single-trunk trees");
    if (base > 1) {
      var roots = visited.stream().filter(p -> isRoot(l, p)).toList();
      int minX = roots.stream().mapToInt(BlockPos::getX).min().orElse(0),
          minZ = roots.stream().mapToInt(BlockPos::getZ).min().orElse(0);
      if (base != 4 || roots.stream().anyMatch(p -> p.getX() > minX + 1 || p.getZ() > minZ + 1))
        return new Result(null, "Separate rooted trunks");
    }
    List<BlockPos> logs = new ArrayList<>(visited);
    logs.sort(
        Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingLong(BlockPos::asLong));
    return new Result(new Tree(root, List.copyOf(logs), List.copyOf(leaves), base), "Available");
  }

  public static Landing landing(
      ServerLevel l, Tree tree, Station station, UUID owner, Lumberton worker) {
    for (Direction d : Direction.Plane.HORIZONTAL) {
      List<BlockPos> targets = new ArrayList<>();
      Set<BlockPos> sweep = new HashSet<>();
      boolean valid = true;
      int width = tree.logs.size() > 32 ? 4 : tree.baseCount > 1 ? 2 : 1;
      Set<BlockPos> source = new HashSet<>(tree.logs);
      Set<BlockPos> canopy = new HashSet<>(tree.leaves);
      for (int i = 0; i < tree.logs.size() && valid; i++) {
        BlockPos start = tree.logs.get(i);
        int along = i / width + 1, side = i % width - width / 2;
        BlockPos xz = tree.root.relative(d, along).relative(d.getClockWise(), side);
        BlockPos target = null;
        for (int dy = 2; dy >= -3; dy--) {
          BlockPos p = xz.offset(0, dy, 0);
          if (!l.hasChunkAt(p)) break;
          if (l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)
              && l.getBlockState(p).isAir()) {
            target = p;
            break;
          }
        }
        if (target == null
            || !within(station, target)
            || !Permissions.allowed(l, target, owner, Permissions.Action.FALL)) {
          valid = false;
          break;
        }
        targets.add(target);
        // Sample the tipping arc at <= half-block spacing; include the full log cube at each
        // sample.
        int steps =
            Math.max(
                16,
                4 * (start.getY() - tree.root.getY() + 2 + (int) Math.sqrt(start.distSqr(target))));
        for (int step = 0; step <= steps && valid; step++) {
          double f = step / (double) steps;
          Vec3 point = fallCorner(start, target, tree.root, d, f);
          double px = point.x, py = point.y, pz = point.z;
          for (BlockPos q :
              BlockPos.betweenClosed(
                  BlockPos.containing(px, py, pz),
                  BlockPos.containing(px + .999, py + .999, pz + .999))) {
            BlockPos p = q.immutable();
            sweep.add(p);
            if (!within(station, p)
                || !l.hasChunkAt(p)
                || WorldData.get(l).protectedAt(p)
                || l.isOutsideBuildHeight(p)) {
              valid = false;
              break;
            }
            if (!source.contains(p) && !canopy.contains(p) && !l.getBlockState(p).isAir()) {
              valid = false;
              break;
            }
          }
        }
        if (l.getEntities(
                    worker,
                    new AABB(target).inflate(.2),
                    e -> e instanceof net.minecraft.world.entity.LivingEntity)
                .size()
            > 0) valid = false;
      }
      if (valid) {
        for (BlockPos p : sweep)
          if (!l.getEntities(
                  worker,
                  new AABB(p).inflate(.1),
                  e -> e instanceof net.minecraft.world.entity.LivingEntity)
              .isEmpty()) {
            valid = false;
            break;
          }
      }
      if (valid) return new Landing(targets, sweep, d);
    }
    return null;
  }

  /** Only foliage with no other possible log support is transferred into the job. */
  public static List<BlockPos> exclusiveCrown(ServerLevel l, Tree tree) {
    if (tree.leaves.isEmpty()) return List.of();
    int minX = Integer.MAX_VALUE,
        minY = Integer.MAX_VALUE,
        minZ = Integer.MAX_VALUE,
        maxX = Integer.MIN_VALUE,
        maxY = Integer.MIN_VALUE,
        maxZ = Integer.MIN_VALUE;
    for (BlockPos p : tree.leaves) {
      minX = Math.min(minX, p.getX());
      minY = Math.min(minY, p.getY());
      minZ = Math.min(minZ, p.getZ());
      maxX = Math.max(maxX, p.getX());
      maxY = Math.max(maxY, p.getY());
      maxZ = Math.max(maxZ, p.getZ());
    }
    Set<BlockPos> own = new HashSet<>(tree.logs);
    List<BlockPos> otherLogs = new ArrayList<>();
    for (BlockPos p :
        BlockPos.betweenClosed(
            minX - 6,
            Math.max(l.getMinBuildHeight(), minY - 6),
            minZ - 6,
            maxX + 6,
            Math.min(l.getMaxBuildHeight() - 1, maxY + 6),
            maxZ + 6)) {
      if (!l.hasChunkAt(p)) return null;
      if (l.getBlockState(p).is(BlockTags.LOGS) && !own.contains(p)) otherLogs.add(p.immutable());
    }
    List<BlockPos> result = new ArrayList<>();
    for (BlockPos p : tree.leaves) {
      BlockState state = l.getBlockState(p);
      if (!state.is(BlockTags.LEAVES) || state.getValue(LeavesBlock.PERSISTENT)) continue;
      boolean shared = false;
      for (BlockPos q : otherLogs)
        if (p.distManhattan(q) <= 6) {
          shared = true;
          break;
        }
      if (!shared) result.add(p);
    }
    return result.size() > 384 ? null : result;
  }

  public static Vec3 fallCorner(
      BlockPos source, BlockPos target, BlockPos root, Direction direction, double f) {
    double height = source.getY() - root.getY(),
        arc = Math.sin(f * Math.PI / 2),
        settle = Math.max(0, Math.min(1, (f - .7) / .3));
    double x = source.getX() + direction.getStepX() * height * arc,
        z = source.getZ() + direction.getStepZ() * height * arc,
        y = root.getY() + height * Math.cos(f * Math.PI / 2);
    return new Vec3(
        x + (target.getX() - x) * settle,
        y + (target.getY() - y) * settle,
        z + (target.getZ() - z) * settle);
  }

  public static boolean landingStillSafe(
      ServerLevel l, Tree tree, Landing plan, Station s, UUID owner, Lumberton worker) {
    Set<BlockPos> source = new HashSet<>(tree.logs);
    Set<BlockPos> canopy = new HashSet<>(tree.leaves);
    for (BlockPos p : plan.sweep) {
      if (!within(s, p) || !Permissions.allowed(l, p, owner, Permissions.Action.FALL)) return false;
      if (!source.contains(p)
          && !l.getBlockState(p).isAir()
          && !(canopy.contains(p)
              && l.getBlockState(p).is(BlockTags.LEAVES)
              && !l.getBlockState(p).getValue(LeavesBlock.PERSISTENT))) return false;
      if (!l.getEntities(
              worker, new AABB(p), e -> e instanceof net.minecraft.world.entity.LivingEntity)
          .isEmpty()) return false;
    }
    for (BlockPos p : plan.targets)
      if (!within(s, p)
          || !Permissions.allowed(l, p, owner, Permissions.Action.FALL)
          || !l.getBlockState(p).isAir()
          || !l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) return false;
    return true;
  }

  public static boolean revalidate(ServerLevel l, Tree tree, Station s, UUID owner) {
    if (!isRoot(l, tree.root)) return false;
    long remainingLeaves =
        tree.leaves.stream()
            .filter(
                p ->
                    l.hasChunkAt(p)
                        && l.getBlockState(p).is(BlockTags.LEAVES)
                        && !l.getBlockState(p).getValue(LeavesBlock.PERSISTENT))
            .count();
    if (remainingLeaves < 6) return false;
    for (BlockPos p : tree.logs)
      if (!l.hasChunkAt(p)
          || !supported(l.getBlockState(p))
          || !within(s, p)
          || WorldData.get(l).placedLogs.contains(p.asLong())
          || !Permissions.allowed(l, p, owner, Permissions.Action.CUT)) return false;
    for (BlockPos p : tree.leaves)
      if (!l.hasChunkAt(p) || !within(s, p) || WorldData.get(l).protectedAt(p)) return false;
    return true;
  }
}
