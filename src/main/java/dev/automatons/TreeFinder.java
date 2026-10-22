package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class TreeFinder {
  public record Tree(
      BlockPos root,
      List<BlockPos> logs,
      List<BlockPos> leaves,
      int baseCount,
      int minX,
      int maxX,
      int minZ,
      int maxZ) {
    public Tree(BlockPos root, List<BlockPos> logs, List<BlockPos> leaves, int baseCount) {
      this(
          root,
          logs,
          leaves,
          baseCount,
          logs.stream().mapToInt(BlockPos::getX).min().orElse(root.getX()) - 1,
          logs.stream().mapToInt(BlockPos::getX).max().orElse(root.getX()) + 1,
          logs.stream().mapToInt(BlockPos::getZ).min().orElse(root.getZ()) - 1,
          logs.stream().mapToInt(BlockPos::getZ).max().orElse(root.getZ()) + 1);
    }

    public double centerX() {
      return root.getX() + (baseCount == 4 ? 1 : .5);
    }

    public double centerZ() {
      return root.getZ() + (baseCount == 4 ? 1 : .5);
    }

    public double distanceFrom(Station s) {
      double x = centerX() - s.getBlockPos().getX() - .5,
          z = centerZ() - s.getBlockPos().getZ() - .5;
      return x * x + z * z;
    }
  }

  public record Result(Tree tree, String reason) {}

  public record Landing(
      List<BlockPos> targets, Set<BlockPos> sweep, Direction direction, boolean segmented) {
    public Landing(List<BlockPos> targets, Set<BlockPos> sweep, Direction direction) {
      this(targets, sweep, direction, false);
    }
  }

  public static final TagKey<Block> SUPPORTED =
      TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Automatons.id("tree_logs"));

  public static boolean supported(BlockState s) {
    return TreeSpecies.forLog(s.getBlock()) != null;
  }

  public static boolean soil(BlockState s) {
    return s.is(BlockTags.DIRT) || s.is(Blocks.MUD) || s.is(Blocks.MUDDY_MANGROVE_ROOTS);
  }

  private static boolean mangrove(BlockState state) {
    var d = TreeSpecies.forLog(state.getBlock());
    return d != null && d.logs().contains(Blocks.MANGROVE_LOG);
  }

  public static boolean isRoot(ServerLevel l, BlockPos p) {
    if (!l.hasChunkAt(p) || !supported(l.getBlockState(p))) return false;
    if (soil(l.getBlockState(p.below()))) return true;
    if (!mangrove(l.getBlockState(p)) || !l.getBlockState(p.below()).is(Blocks.MANGROVE_ROOTS))
      return false;
    ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    Set<BlockPos> seen = new HashSet<>();
    pending.add(p.below());
    while (!pending.isEmpty() && seen.size() < 160) {
      BlockPos q = pending.removeFirst();
      if (!seen.add(q)
          || !l.hasChunkAt(q)
          || Math.abs(q.getX() - p.getX()) > 6
          || Math.abs(q.getZ() - p.getZ()) > 6
          || p.getY() - q.getY() > 8) continue;
      BlockState state = l.getBlockState(q);
      if (soil(state)) return true;
      if (!state.is(Blocks.MANGROVE_ROOTS)) continue;
      pending.add(q.below());
      for (Direction direction : Direction.Plane.HORIZONTAL) pending.add(q.relative(direction));
    }
    return false;
  }

  public static boolean within(Station s, BlockPos p) {
    return Math.abs(p.getX() - s.getBlockPos().getX()) <= s.lumberRange()
        && Math.abs(p.getZ() - s.getBlockPos().getZ()) <= s.lumberRange();
  }

  public static boolean rootWithin(Station s, Tree t) {
    return Math.abs(t.centerX() - s.getBlockPos().getX() - .5) <= s.lumberRange()
        && Math.abs(t.centerZ() - s.getBlockPos().getZ() - .5) <= s.lumberRange()
        && Math.abs(t.root().getY() - s.getBlockPos().getY()) <= Balance.ROOT_HEIGHT.get();
  }

  private static boolean workFootprint(Station s, Tree t, BlockPos p) {
    if (s == null)
      return Math.abs(p.getX() - t.root.getX()) <= 64 && Math.abs(p.getZ() - t.root.getZ()) <= 64;
    if (within(s, p)) return true;
    return p.getX() >= t.minX && p.getX() <= t.maxX && p.getZ() >= t.minZ && p.getZ() <= t.maxZ;
  }

  public static boolean loose(Tree tree) {
    return tree.logs.size() == 1 && tree.leaves.isEmpty();
  }

  /** Natural trees retain felling; other in-range logs are ordinary one-block jobs. */
  public static Result work(ServerLevel l, BlockPos at, Station station) {
    if (!l.getBlockState(at).is(BlockTags.LOGS)) return new Result(null, "Not a log");
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    Set<BlockPos> seen = new HashSet<>();
    queue.add(at);
    while (!queue.isEmpty() && seen.size() < 512) {
      BlockPos p = queue.removeFirst();
      if (!seen.add(p) || !l.hasChunkAt(p) || !l.getBlockState(p).is(BlockTags.LOGS)) continue;
      if (isRoot(l, p)) {
        Result natural = inspect(l, p, station);
        if (natural.tree != null && natural.tree.logs.contains(at)) return natural;
      }
      for (Direction d : Direction.values()) {
        BlockPos q = p.relative(d);
        if (Math.abs(q.getX() - at.getX()) <= 12
            && Math.abs(q.getZ() - at.getZ()) <= 12
            && Math.abs(q.getY() - at.getY()) <= 48) queue.add(q);
      }
    }
    Tree single = new Tree(at.immutable(), List.of(at.immutable()), List.of(), 1);
    return rootWithin(station, single)
            && Permissions.allowed(l, at, station.owner, Permissions.Action.CUT)
        ? new Result(single, "Log")
        : new Result(null, "Log outside range or protected");
  }

  public static Result inspect(ServerLevel l, BlockPos candidate, Station station) {
    BlockPos root = candidate.immutable();
    if (!isRoot(l, root)) return new Result(null, "Not a rooted tree");
    var definition = TreeSpecies.forLog(l.getBlockState(root).getBlock());
    if (definition == null || !definition.supported())
      return new Result(null, "Unsupported tree species");
    boolean mangrove = definition.logs().contains(Blocks.MANGROVE_LOG);
    Set<BlockPos> logs = new HashSet<>(), leaves = new HashSet<>();
    List<BlockPos> roots = new ArrayList<>();
    ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    pending.add(root);
    WorldData data = WorldData.get(l);
    while (!pending.isEmpty()) {
      BlockPos pos = pending.removeFirst();
      if (logs.contains(pos)) continue;
      if (!l.hasChunkAt(pos)) return new Result(null, "Tree crosses unloaded chunks");
      if (!definition.logs().contains(l.getBlockState(pos).getBlock())) continue;
      if (pos.getY() < root.getY() - (mangrove ? 3 : 0)
          || pos.getY() > root.getY() + definition.height()
          || Math.abs(pos.getX() - root.getX()) > definition.spread()
          || Math.abs(pos.getZ() - root.getZ()) > definition.spread()
          || logs.size() >= definition.maximumLogs())
        return new Result(null, "Connected wood exceeds species bounds");
      if (data.placedLogs.contains(pos.asLong()))
        return new Result(null, "Player-placed structural wood");
      if (station != null && data.protectedAt(pos)) return new Result(null, "Protected tree");
      if (isRoot(l, pos)) roots.add(pos);
      logs.add(pos.immutable());
      for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
          for (int z = -1; z <= 1; z++)
            if (x != 0 || y != 0 || z != 0) pending.add(pos.offset(x, y, z));
      for (BlockPos q : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
        if (!l.hasChunkAt(q)) return new Result(null, "Canopy crosses unloaded chunks");
        BlockState state = l.getBlockState(q);
        if (definition.leaves().contains(state.getBlock())
            && state.hasProperty(LeavesBlock.PERSISTENT)
            && !state.getValue(LeavesBlock.PERSISTENT)
            && !data.placedPlants.contains(q.asLong())) leaves.add(q.immutable());
      }
    }
    if (logs.size() < 3 || leaves.size() < 4 || roots.isEmpty())
      return new Result(null, "No convincing natural canopy");
    roots.sort(
        Comparator.comparingInt((BlockPos p) -> p.getY())
            .thenComparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getZ));
    BlockPos canonical = roots.get(0);
    if (!mangrove && roots.size() > 1) {
      int minX = roots.stream().mapToInt(BlockPos::getX).min().orElse(0),
          minZ = roots.stream().mapToInt(BlockPos::getZ).min().orElse(0);
      if (!definition.largeTrunk()
          || roots.size() != 4
          || roots.stream()
              .anyMatch(p -> p.getY() != root.getY() || p.getX() > minX + 1 || p.getZ() > minZ + 1))
        return new Result(null, "Separate rooted trunks touch");
      canonical = new BlockPos(minX, canonical.getY(), minZ);
    }
    if (mangrove
        && roots.stream()
            .anyMatch(
                p -> Math.abs(p.getX() - root.getX()) > 2 || Math.abs(p.getZ() - root.getZ()) > 2))
      return new Result(null, "Separate mangrove trunks touch");
    List<BlockPos> ordered = new ArrayList<>(logs);
    ordered.sort(
        Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingLong(BlockPos::asLong));
    leaves.addAll(attachments(l, logs, leaves, definition.logs().contains(Blocks.MANGROVE_LOG)));
    Tree tree = new Tree(canonical, List.copyOf(ordered), List.copyOf(leaves), roots.size());
    if (station != null && !rootWithin(station, tree))
      return new Result(null, "Root outside work range");
    return new Result(tree, "Available");
  }

  private static boolean naturalAttachment(ServerLevel level, BlockPos pos) {
    WorldData data = WorldData.get(level);
    if (data.placedPlants.contains(pos.asLong()) || data.placedLogs.contains(pos.asLong()))
      return false;
    BlockState state = level.getBlockState(pos);
    if (state.is(BlockTags.LEAVES)) return !state.getValue(LeavesBlock.PERSISTENT);
    return state.is(Blocks.VINE)
        || state.is(Blocks.MANGROVE_ROOTS)
        || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
        || state.is(Blocks.MOSS_CARPET)
        || state.is(Blocks.MANGROVE_PROPAGULE) && state.getValue(MangrovePropaguleBlock.HANGING);
  }

  private static Set<BlockPos> attachments(
      ServerLevel level, Set<BlockPos> logs, Set<BlockPos> foliage, boolean mangrove) {
    Set<BlockPos> result = new HashSet<>();
    WorldData data = WorldData.get(level);
    ArrayDeque<BlockPos> roots = new ArrayDeque<>();
    for (BlockPos log : logs) for (Direction d : Direction.values()) roots.add(log.relative(d));
    for (BlockPos leaf : foliage)
      for (Direction d : Direction.values()) roots.add(leaf.relative(d));
    Set<BlockPos> seen = new HashSet<>();
    int minY = logs.stream().mapToInt(BlockPos::getY).min().orElse(0);
    while (!roots.isEmpty() && result.size() < 512) {
      BlockPos p = roots.removeFirst();
      if (!seen.add(p)
          || !level.hasChunkAt(p)
          || data.placedPlants.contains(p.asLong())
          || data.placedLogs.contains(p.asLong())) continue;
      BlockState state = level.getBlockState(p);
      boolean root =
          mangrove && (state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS));
      boolean vine = state.is(Blocks.VINE);
      boolean decoration =
          mangrove
              && (state.is(Blocks.MOSS_CARPET)
                      && (result.contains(p.below())
                          || level.getBlockState(p.below()).is(Blocks.MANGROVE_ROOTS))
                  || state.is(Blocks.MANGROVE_PROPAGULE)
                      && state.getValue(MangrovePropaguleBlock.HANGING)
                      && foliage.contains(p.above()));
      if (!root && !vine && !decoration) continue;
      if (p.getY() < minY - 10
          || logs.stream()
              .noneMatch(
                  log ->
                      Math.abs(log.getX() - p.getX()) <= 6 && Math.abs(log.getZ() - p.getZ()) <= 6))
        continue;
      result.add(p.immutable());
      if (root) for (Direction d : Direction.values()) roots.add(p.relative(d));
      else roots.add(p.below());
    }
    return result;
  }

  /** Shared structural recognition for player log rewards, before the source is removed. */
  public static boolean recognizeNatural(ServerLevel level, BlockPos source) {
    if (!supported(level.getBlockState(source))) return false;
    var definition = TreeSpecies.forLog(level.getBlockState(source).getBlock());
    java.util.Set<BlockPos> seen = new java.util.HashSet<>();
    java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
    queue.add(source);
    while (!queue.isEmpty() && seen.size() < definition.maximumLogs()) {
      BlockPos pos = queue.removeFirst();
      if (!seen.add(pos)
          || !level.hasChunkAt(pos)
          || !definition.logs().contains(level.getBlockState(pos).getBlock())) continue;
      if (isRoot(level, pos)) {
        Tree tree = inspect(level, pos, null).tree();
        if (tree != null && tree.logs().contains(source)) {
          WorldData data = WorldData.get(level);
          for (BlockPos log : tree.logs()) data.naturalLogs.add(log.asLong());
          data.setDirty();
          return true;
        }
      }
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++)
          for (int y = -1; y <= 1; y++)
            if (x != 0 || y != 0 || z != 0) {
              BlockPos q = pos.offset(x, y, z);
              if (level.hasChunkAt(q)
                  && definition.logs().contains(level.getBlockState(q).getBlock())) queue.add(q);
            }
    }
    return false;
  }

  public static boolean clearTimberSpace(BlockState state) {
    return state.isAir() || state.is(Blocks.WATER);
  }

  public static Landing landing(
      ServerLevel l,
      Tree tree,
      Station station,
      UUID owner,
      net.minecraft.world.entity.Entity worker) {
    List<BlockPos> crown = exclusiveCrown(l, tree);
    if (crown == null) {
      if (station != null) station.scanReason = "Canopy crosses unavailable area";
      return null;
    }
    for (BlockPos p : crown)
      if (!Permissions.allowed(l, p, owner, Permissions.Action.CUT)) {
        if (station != null) station.scanReason = "Protected tree attachments";
        return null;
      }
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
          if (!source.contains(p.below())
              && !canopy.contains(p.below())
              && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)
              && clearTimberSpace(l.getBlockState(p))) {
            target = p;
            break;
          }
        }
        if (target == null
            || Timber.reserves(l, target, worker)
            || !workFootprint(station, tree, target)
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
            if (!workFootprint(station, tree, p)
                || !l.hasChunkAt(p)
                || WorldData.get(l).protectedAt(p)
                || l.isOutsideBuildHeight(p)) {
              valid = false;
              break;
            }
            if (!source.contains(p)
                && !canopy.contains(p)
                && !clearTimberSpace(l.getBlockState(p))) {
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
    return compact(l, tree, station, owner, worker);
  }

  private static Landing compact(
      ServerLevel l,
      Tree tree,
      Station station,
      UUID owner,
      net.minecraft.world.entity.Entity worker) {
    Set<BlockPos> source = new HashSet<>(tree.logs), canopy = new HashSet<>(tree.leaves);
    List<BlockPos> candidates = new ArrayList<>();
    int reach = Math.min(32, Math.max(3, (int) Math.ceil(Math.sqrt(tree.logs.size())) + 2));
    for (int radius = 0; radius <= reach; radius++)
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
          for (int dy = 2; dy >= -8; dy--) {
            BlockPos target = tree.root.offset(dx, dy, dz);
            if (!l.hasChunkAt(target)
                || l.isOutsideBuildHeight(target)
                || source.contains(target.below())
                || canopy.contains(target.below())
                || !l.getBlockState(target.below()).isFaceSturdy(l, target.below(), Direction.UP)
                || (!clearTimberSpace(l.getBlockState(target))
                    && !source.contains(target)
                    && !(canopy.contains(target)
                        && naturalAttachment(l, target)
                        && l.getFluidState(target).isEmpty()))
                || !workFootprint(station, tree, target)
                || Timber.reserves(l, target, worker)
                || !Permissions.allowed(l, target, owner, Permissions.Action.FALL)
                || !l.getEntities(
                        worker,
                        new AABB(target).inflate(.1),
                        e -> e instanceof net.minecraft.world.entity.LivingEntity)
                    .isEmpty()) continue;
            candidates.add(target);
            break;
          }
        }
    List<BlockPos> targets = new ArrayList<>();
    Set<BlockPos> used = new HashSet<>(), sweep = new HashSet<>();
    for (BlockPos start : tree.logs) {
      boolean placed = false;
      for (BlockPos target : candidates) {
        if (used.contains(target)) continue;
        Set<BlockPos> path = new HashSet<>();
        boolean valid = true;
        int steps = Math.max(4, (int) Math.ceil(Math.sqrt(start.distSqr(target)) * 2));
        for (int step = 0; step <= steps && valid; step++) {
          Vec3 point =
              Vec3.atLowerCornerOf(start).lerp(Vec3.atLowerCornerOf(target), step / (double) steps);
          for (BlockPos q :
              BlockPos.betweenClosed(
                  BlockPos.containing(point), BlockPos.containing(point.add(.999, .999, .999)))) {
            BlockPos at = q.immutable();
            path.add(at);
            if (!l.hasChunkAt(at)
                || l.isOutsideBuildHeight(at)
                || WorldData.get(l).protectedAt(at)
                || !workFootprint(station, tree, at)
                || (!source.contains(at)
                    && !canopy.contains(at)
                    && !clearTimberSpace(l.getBlockState(at)))
                || !l.getEntities(
                        worker,
                        new AABB(at),
                        e -> e instanceof net.minecraft.world.entity.LivingEntity)
                    .isEmpty()) {
              valid = false;
              break;
            }
          }
        }
        if (valid) {
          targets.add(target);
          used.add(target);
          sweep.addAll(path);
          placed = true;
          break;
        }
      }
      if (!placed) {
        if (station != null) station.scanReason = "No distinct safe landing space";
        return null;
      }
    }
    return new Landing(targets, sweep, Direction.NORTH, true);
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
      if (!naturalAttachment(l, p)) continue;
      if (state.is(BlockTags.LEAVES)) {
        if (state.getValue(LeavesBlock.PERSISTENT)) continue;
      } else if (!state.is(Blocks.VINE)
          && !state.is(Blocks.MANGROVE_ROOTS)
          && !state.is(Blocks.MUDDY_MANGROVE_ROOTS)
          && !state.is(Blocks.MOSS_CARPET)
          && !state.is(Blocks.MANGROVE_PROPAGULE)) continue;
      boolean shared = false;
      for (BlockPos q : otherLogs)
        if (p.distManhattan(q) <= 6) {
          shared = true;
          break;
        }
      if (!shared) result.add(p);
    }
    return result.size() > 4096 ? null : result;
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
      ServerLevel l,
      Tree tree,
      Landing plan,
      Station s,
      UUID owner,
      net.minecraft.world.entity.Entity worker) {
    if (new HashSet<>(plan.targets).size() != plan.targets.size()) return false;
    Set<BlockPos> source = new HashSet<>(tree.logs);
    Set<BlockPos> canopy = new HashSet<>(tree.leaves);
    for (BlockPos p : plan.sweep) {
      if (!workFootprint(s, tree, p) || !Permissions.allowed(l, p, owner, Permissions.Action.FALL))
        return false;
      if (!source.contains(p)
          && !clearTimberSpace(l.getBlockState(p))
          && !(canopy.contains(p) && naturalAttachment(l, p))) return false;
      if (!l.getEntities(
              worker, new AABB(p), e -> e instanceof net.minecraft.world.entity.LivingEntity)
          .isEmpty()) return false;
    }
    for (BlockPos p : plan.targets)
      if (!workFootprint(s, tree, p)
          || Timber.reserves(l, p, worker)
          || !Permissions.allowed(l, p, owner, Permissions.Action.FALL)
          || (!clearTimberSpace(l.getBlockState(p))
              && !(plan.segmented
                  && (source.contains(p)
                      || canopy.contains(p)
                          && naturalAttachment(l, p)
                          && l.getFluidState(p).isEmpty())))
          || !l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) return false;
    return true;
  }

  public static boolean revalidate(ServerLevel l, Tree tree, Station s, UUID owner) {
    if (loose(tree))
      return l.hasChunkAt(tree.root)
          && l.getBlockState(tree.root).is(BlockTags.LOGS)
          && rootWithin(s, tree)
          && Permissions.allowed(l, tree.root, owner, Permissions.Action.CUT);
    if (!isRoot(l, tree.root) || !rootWithin(s, tree)) return false;
    long remainingLeaves =
        tree.leaves.stream()
            .filter(
                p ->
                    l.hasChunkAt(p)
                        && l.getBlockState(p).is(BlockTags.LEAVES)
                        && !l.getBlockState(p).getValue(LeavesBlock.PERSISTENT))
            .count();
    if (remainingLeaves < 4) return false;
    for (BlockPos p : tree.logs)
      if (!l.hasChunkAt(p)
          || !supported(l.getBlockState(p))
          || WorldData.get(l).placedLogs.contains(p.asLong())
          || !Permissions.allowed(l, p, owner, Permissions.Action.CUT)) return false;
    for (BlockPos p : tree.leaves) if (!l.hasChunkAt(p)) return false;
    return true;
  }
}
