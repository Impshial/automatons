package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;

/** Persistent, bounded work journals for the supplied twenty-passage strip layout. */
public final class StripMine {
  public static final int FIRST_DEPTH = 20, LEVEL_STEP = 4;

  public record BranchShape(int firstX, int lastX, int z) {
    public int length() {
      return Math.abs(lastX - firstX) + 1;
    }

    public int x(int column) {
      return firstX + Integer.signum(lastX - firstX) * column;
    }
  }

  public static final class Branch {
    public int cell;
    public String stopped = "";
    public UUID worker;
    public long until;

    public boolean done(BranchShape shape) {
      return cell >= shape.length() * 2 || !stopped.isEmpty();
    }
  }

  public static final class Floor {
    public int corridor;
    public final List<Branch> branches = new ArrayList<>();

    public Floor() {
      for (int i = 0; i < 20; i++) branches.add(new Branch());
    }
  }

  public final List<Floor> floors = new ArrayList<>();
  public final Set<Long> acknowledgedCaves = new HashSet<>(), discoveredCaves = new HashSet<>();
  public boolean complete;
  private BuildingTemplate blueprint, landing;
  private List<BranchShape> shapes;

  public StripMine() {
    floors.add(new Floor());
  }

  public Floor current() {
    return floors.get(floors.size() - 1);
  }

  public int depth() {
    return FIRST_DEPTH + LEVEL_STEP * (floors.size() - 1);
  }

  public BuildingTemplate blueprint(ServerLevel level) {
    if (blueprint == null) blueprint = BuildingTemplate.load(level, "minington_strip_level");
    return blueprint;
  }

  public List<BranchShape> shapes(ServerLevel level) {
    if (shapes == null) {
      List<BranchShape> result = new ArrayList<>();
      for (var entry : blueprint(level).worksite().getAsJsonArray("branches")) {
        var bounds = entry.getAsJsonObject().getAsJsonArray("bounds");
        BlockPos a = BuildingTemplate.point(bounds.get(0)),
            b = BuildingTemplate.point(bounds.get(1));
        int near = Math.abs(a.getX()) < Math.abs(b.getX()) ? a.getX() : b.getX();
        int far = near == a.getX() ? b.getX() : a.getX();
        result.add(new BranchShape(near, far, a.getZ()));
      }
      if (result.size() != 20)
        throw new IllegalStateException("Strip blueprint needs twenty branches");
      shapes = List.copyOf(result);
    }
    return shapes;
  }

  /** The manifest's anchor is the shaft at logical (0,0,-1). */
  public BlockPos at(MinePlan mine, int x, int y, int z) {
    return mine.entrance
        .below(depth())
        .offset(
            new BlockPos(x, y, z + 1)
                .rotate(BuildingTemplate.rotation(mine.forward.getOpposite())));
  }

  public BlockPos local(MinePlan mine, BlockPos p) {
    var rotation = BuildingTemplate.rotation(mine.forward.getOpposite());
    var inverse =
        switch (rotation) {
          case CLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
          case COUNTERCLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
          default -> rotation;
        };
    return p.subtract(mine.entrance.below(depth())).rotate(inverse).offset(0, 0, -1);
  }

  public boolean footprint(MinePlan mine, BlockPos p) {
    var q = local(mine, p);
    return q.getX() >= -26 && q.getX() <= 26 && q.getZ() >= -2 && q.getZ() <= 29;
  }

  public boolean planned(MinePlan mine, BlockPos p) {
    var q = local(mine, p);
    int x = Math.abs(q.getX()), z = q.getZ(), y = q.getY();
    if (x == 0 && z == -1 && y >= 0 && p.getY() < mine.entrance.getY()) return true;
    if (y < 0 || y > 2) return false;
    if (x <= 2 && z >= 0 && z <= 4) return true;
    if (y > 1) return false;
    return x <= 1 && z >= 5 && z <= 29 || x <= 26 && z >= 1 && z <= 28 && (z - 1) % 3 == 0;
  }

  public BlockPos branchCell(MinePlan mine, ServerLevel level, int branch) {
    Branch b = current().branches.get(branch);
    BranchShape shape = shapes(level).get(branch);
    return at(mine, shape.x(b.cell / 2), b.cell % 2 == 0 ? 1 : 0, shape.z());
  }

  public BlockPos corridorCell(MinePlan mine) {
    int cell = current().corridor;
    return at(mine, new int[] {0, -1, 1}[(cell / 2) % 3], cell % 2 == 0 ? 1 : 0, 5 + cell / 6);
  }

  public int claim(Lumberton worker, Station station) {
    var level = (ServerLevel) worker.level();
    long now = level.getGameTime();
    for (int i = 0; i < 20; i++) {
      Branch b = current().branches.get(i);
      if (worker.getUUID().equals(b.worker)
          && station.workers.contains(b.worker)
          && !b.done(shapes(level).get(i))) {
        b.until = now + 1200;
        return i;
      }
    }
    for (int i = 0; i < 20; i++) {
      Branch b = current().branches.get(i);
      if (b.worker != null
          && (!station.workers.contains(b.worker)
              || !(level.getEntity(b.worker) instanceof Lumberton live)
              || !live.isAlive()
              || now >= b.until)) {
        b.worker = null;
        b.until = 0;
      }
    }
    for (int i = 0; i < 20; i++) {
      Branch b = current().branches.get(i);
      if (worker.getUUID().equals(b.worker) && !b.done(shapes(level).get(i))) {
        b.until = now + 1200;
        return i;
      }
    }
    for (int i = 0; i < 20; i++) {
      Branch b = current().branches.get(i);
      if (b.worker == null && !b.done(shapes(level).get(i))) {
        b.worker = worker.getUUID();
        b.until = now + 1200;
        return i;
      }
    }
    return -1;
  }

  public boolean floorDone(ServerLevel level) {
    for (int i = 0; i < 20; i++)
      if (!current().branches.get(i).done(shapes(level).get(i))) return false;
    return current().corridor >= 150;
  }

  public void release(UUID worker) {
    for (Floor floor : floors)
      for (Branch b : floor.branches) if (worker.equals(b.worker)) b.worker = null;
  }

  public void acknowledge() {
    acknowledgedCaves.addAll(discoveredCaves);
    discoveredCaves.clear();
  }

  public String opening(ServerLevel level, MinePlan mine, BlockPos p, boolean branch) {
    for (Direction d : Direction.values())
      if (!level.hasChunkAt(p.relative(d))) return "Area unloaded";
    if (!level.hasChunkAt(p)) return "Area unloaded";
    if (MiningRules.fluidAhead(level, p)) return "Water or lava";
    List<BlockPos> check = new ArrayList<>();
    check.add(p);
    for (Direction d : Direction.values()) check.add(p.relative(d));
    boolean cave = false;
    for (BlockPos cell : check) {
      // Previously excavated passage and the installed shaft are known, safe access.
      if (mine.passage.contains(cell.asLong())
          || cell.equals(mine.entrance)
          || level.getBlockState(cell).is(Blocks.LADDER)
          || acknowledgedCaves.contains(cell.asLong())) continue;
      if (level.getBlockState(cell).isAir()) {
        cave = true;
        if (!branch) discoveredCaves.add(cell.asLong());
      }
    }
    return cave ? "Cave opening" : "";
  }

  public List<BlockPos> chests(MinePlan mine) {
    List<BlockPos> out = new ArrayList<>();
    for (int y = 0; y < 2; y++) for (int x : new int[] {-2, -1, 1, 2}) out.add(at(mine, x, y, 0));
    return out;
  }

  public List<MinePlan.Task> construction(ServerLevel level, MinePlan mine) {
    List<MinePlan.Task> tasks = new ArrayList<>();
    if (mine.stage == MinePlan.Stage.SHAFT) {
      int start = floors.size() == 1 ? 1 : depth() - 3;
      for (int d = start; d <= depth(); d++) {
        BlockPos p = mine.ladder(d);
        tasks.add(new MinePlan.Task(p, null, "Excavating shaft"));
        tasks.add(
            new MinePlan.Task(
                p.relative(mine.forward.getOpposite()),
                Blocks.COBBLESTONE.defaultBlockState(),
                "Backing ladder"));
        tasks.add(new MinePlan.Task(p, mine.ladderState(), "Installing ladder"));
      }
    } else if (mine.stage == MinePlan.Stage.CHAMBER) {
      for (int z = 0; z <= 4; z++)
        for (int x : new int[] {0, -1, 1, -2, 2}) {
          for (int y : new int[] {1, 2, 0})
            tasks.add(new MinePlan.Task(at(mine, x, y, z), null, "Clearing landing"));
          tasks.add(
              new MinePlan.Task(
                  at(mine, x, -1, z),
                  Blocks.COBBLESTONE.defaultBlockState(),
                  "Supporting landing"));
        }
    } else if (mine.stage == MinePlan.Stage.STORAGE) {
      if (landing == null) landing = BuildingTemplate.load(level, "minington_landing");
      for (var cell : landing.cells)
        if (cell.state().is(Blocks.CHEST)) {
          BlockPos p = landing.world(mine.ladder(depth()), mine.forward, cell.local());
          tasks.add(
              new MinePlan.Task(
                  p,
                  cell.state().rotate(landing.rotationFor(mine.forward)),
                  "Installing mine chest"));
        }
    }
    return tasks;
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    ListTag list = new ListTag();
    for (Floor floor : floors) {
      CompoundTag f = new CompoundTag();
      f.putInt("Corridor", floor.corridor);
      ListTag branches = new ListTag();
      for (Branch b : floor.branches) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Cell", b.cell);
        tag.putString("Stopped", b.stopped);
        if (b.worker != null) tag.putUUID("Worker", b.worker);
        branches.add(tag);
      }
      f.put("Branches", branches);
      list.add(f);
    }
    n.put("Floors", list);
    n.putBoolean("Complete", complete);
    n.putLongArray(
        "AcknowledgedCaves", acknowledgedCaves.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray(
        "DiscoveredCaves", discoveredCaves.stream().mapToLong(Long::longValue).toArray());
    return n;
  }

  public static StripMine load(CompoundTag n) {
    StripMine mine = new StripMine();
    mine.floors.clear();
    for (Tag entry : n.getList("Floors", Tag.TAG_COMPOUND)) {
      CompoundTag f = (CompoundTag) entry;
      Floor floor = new Floor();
      floor.corridor = Math.max(0, Math.min(150, f.getInt("Corridor")));
      ListTag branches = f.getList("Branches", Tag.TAG_COMPOUND);
      for (int i = 0; i < Math.min(20, branches.size()); i++) {
        CompoundTag b = branches.getCompound(i);
        Branch branch = floor.branches.get(i);
        branch.cell = Math.max(0, Math.min(50, b.getInt("Cell")));
        branch.stopped = b.getString("Stopped");
        branch.worker = b.hasUUID("Worker") ? b.getUUID("Worker") : null;
        // Keep the worker's preference, but leave the expired lease reclaimable.
      }
      mine.floors.add(floor);
    }
    if (mine.floors.isEmpty()) mine.floors.add(new Floor());
    mine.complete = n.getBoolean("Complete");
    for (long p : n.getLongArray("AcknowledgedCaves")) mine.acknowledgedCaves.add(p);
    for (long p : n.getLongArray("DiscoveredCaves")) mine.discoveredCaves.add(p);
    return mine;
  }
}
