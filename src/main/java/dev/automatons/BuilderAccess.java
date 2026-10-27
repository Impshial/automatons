package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

/** Bounded access clearing: real movement and block breaks, never a world paste or teleport. */
public final class BuilderAccess {
  private final Lumberton worker;
  private final Deque<BlockPos> route = new ArrayDeque<>();
  private long nextPlan, nextBreak;
  private UUID projectId;
  private Vec3 progress;
  private long progressAt;

  public BuilderAccess(Lumberton worker) {
    this.worker = worker;
  }

  public void reset() {
    route.clear();
    projectId = null;
    nextPlan = 0;
  }

  public boolean active() {
    return !route.isEmpty();
  }

  private boolean removable(BuildingProject project, BlockPos pos) {
    ServerLevel level = (ServerLevel) worker.level();
    if (!level.hasChunkAt(pos)) return false;
    var state = level.getBlockState(pos);
    if (state.hasBlockEntity()
        || !state.getFluidState().isEmpty()
        || state.getDestroySpeed(level, pos) < 0
        || WorldData.get(level).protectedAt(pos)
        || WorldData.get(level).mineProtected(pos, null)) return false;
    for (BuildingProject built : WorldData.get(level).buildings.values())
      if (built.structure(level, pos)) {
        for (var cell : built.template(level).cells)
          if (built.template(level).world(built.anchor, built.front, cell.local()).equals(pos)
              && Construction.matches(
                  state, cell.state().rotate(built.template(level).rotationFor(built.front))))
            return false;
      }
    return Construction.naturalObstacle(state);
  }

  private boolean empty(BlockPos pos) {
    var level = worker.level();
    var state = level.getBlockState(pos);
    return state.getFluidState().isEmpty()
        && (state.getCollisionShape(level, pos).isEmpty()
            || state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR));
  }

  private int cost(BuildingProject project, BlockPos feet) {
    ServerLevel level = (ServerLevel) worker.level();
    if (!level.hasChunkAt(feet)
        || !level.hasChunkAt(feet.above())
        || !MiningRules.supported(level, feet)) return -1;
    int cost = 1;
    for (BlockPos pos : List.of(feet, feet.above())) {
      if (empty(pos)) continue;
      if (!removable(project, pos)) return -1;
      cost += 8;
    }
    return cost;
  }

  public Set<BlockPos> perimeter(BuildingProject project) {
    ServerLevel level = (ServerLevel) worker.level();
    var box = project.bounds(level);
    Set<BlockPos> goals = new HashSet<>();
    for (int x = (int) box.minX - 2; x <= (int) box.maxX + 1; x++)
      for (int z = (int) box.minZ - 2; z <= (int) box.maxZ + 1; z++) {
        if (x >= box.minX && x < box.maxX && z >= box.minZ && z < box.maxZ) continue;
        for (int y = project.anchor.getY() - 2; y <= project.anchor.getY() + 2; y++) {
          BlockPos p = new BlockPos(x, y, z);
          if (cost(project, p) >= 0) goals.add(p);
        }
      }
    return goals;
  }

  public boolean chest(BuildingProject project, BlockPos chest) {
    Set<BlockPos> goals = new HashSet<>();
    for (Direction direction : Direction.Plane.HORIZONTAL)
      for (int dy = -1; dy <= 0; dy++) goals.add(chest.relative(direction).offset(0, dy, 0));
    return clear(project, goals);
  }

  public boolean advance(BuildingProject project) {
    if (!Objects.equals(project.id, projectId)) {
      reset();
      return false;
    }
    ServerLevel level = (ServerLevel) worker.level();
    worker.building.perimeterRoute(null);
    while (!route.isEmpty()
        && worker.position().distanceToSqr(Vec3.atBottomCenterOf(route.peek())) < .65)
      route.remove();
    if (route.isEmpty()) return false;
    BlockPos next = route.peek();
    if (progress == null || worker.position().distanceToSqr(progress) > .25) {
      progress = worker.position();
      progressAt = level.getGameTime();
    } else if (level.getGameTime() - progressAt > 120) {
      reset();
      project.blocked(
          worker,
          next,
          "Construction access route obstructed",
          "The worker cannot enter this route step. Check its headroom, footing and nearby"
              + " entities.");
      return true;
    }
    if (!MiningRules.supported(level, next)) {
      reset();
      return false;
    }
    for (BlockPos pos : List.of(next, next.above())) {
      if (empty(pos)) continue;
      if (!removable(project, pos)) {
        reset();
        project.blocked(
            worker,
            pos,
            "Construction access changed",
            "Move the obstructing structure or container, or restore a safe route. No valuable"
                + " blocks were removed.");
        return true;
      }
      if (!worker.inReach(pos)) {
        worker.travelTo(
            pos, "Approaching construction obstruction", "Clearing construction access");
        return true;
      }
      worker.getNavigation().stop();
      if (level.getGameTime() >= nextBreak) {
        project.clearObstacle(worker, pos, level.getBlockState(pos));
        nextBreak = level.getGameTime() + 10;
        if (level.getBlockState(pos).isAir()) {
          worker.resetWorkPath();
          worker.state("Clearing construction access");
        }
      }
      return true;
    }
    WorkerMovement.walk(worker, next, "Opening construction access");
    return true;
  }

  public boolean clear(BuildingProject project, Set<BlockPos> goals) {
    if (active() && advance(project)) return true;
    ServerLevel level = (ServerLevel) worker.level();
    if (level.getGameTime() < nextPlan) return false;
    if (!WorldEvents.pathBudget()) return false;
    nextPlan = level.getGameTime() + 40;
    projectId = project.id;
    progress = worker.position();
    progressAt = level.getGameTime();
    BlockPos start = worker.blockPosition();
    var bounds = project.bounds(level).inflate(4, 4, 4);
    record Node(BlockPos pos, int cost) {}
    PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::cost));
    Map<BlockPos, Integer> costs = new HashMap<>();
    Map<BlockPos, BlockPos> before = new HashMap<>();
    open.add(new Node(start, 0));
    costs.put(start, 0);
    BlockPos blocked = null;
    int visited = 0;
    while (!open.isEmpty() && visited++ < 1800) {
      Node node = open.remove();
      if (node.cost != costs.get(node.pos)) continue;
      if (!node.pos.equals(start) && goals.contains(node.pos)) {
        BlockPos p = node.pos;
        while (!p.equals(start)) {
          route.addFirst(p);
          p = before.get(p);
        }
        return advance(project);
      }
      for (Direction direction : Direction.Plane.HORIZONTAL)
        for (int dy : new int[] {0, 1, -1}) {
          BlockPos next = node.pos.relative(direction).offset(0, dy, 0);
          if (!bounds.contains(Vec3.atCenterOf(next)) || next.distSqr(start) > 32 * 32) continue;
          int step = cost(project, next);
          // A step up also needs headroom above the previous node.
          if (dy == 1 && !empty(node.pos.above(2))) continue;
          if (step < 0) {
            if (blocked == null && level.hasChunkAt(next) && (!empty(next) || !empty(next.above())))
              blocked = !empty(next) ? next : next.above();
            continue;
          }
          int total = node.cost + step + Math.abs(dy);
          if (total >= costs.getOrDefault(next, Integer.MAX_VALUE)) continue;
          costs.put(next, total);
          before.put(next, node.pos);
          open.add(new Node(next, total));
        }
    }
    if (blocked != null)
      project.blocked(
          worker,
          blocked,
          "No safe construction access route",
          "The route search encountered this obstruction. Move it or provide supported,"
              + " two-block-high access around the worksite.");
    else
      project.blocked(
          worker,
          goals.stream().min(Comparator.comparingDouble(p -> p.distSqr(start))).orElse(start),
          "No supported construction footing",
          "Provide solid ground and two blocks of headroom outside the planned building.");
    return false;
  }
}
