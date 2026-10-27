package dev.automatons;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** The totem's authoritative, compact construction and excavation journal. */
public final class MinePlan {
  public enum Stage {
    SITE,
    ARCH,
    SHAFT,
    CHAMBER,
    STORAGE,
    READY
  }

  public enum Mode {
    TUNNEL,
    QUARRY
  }

  public record Task(BlockPos pos, BlockState place, String activity) {
    public boolean placement() {
      return place != null;
    }
  }

  public record Part(int x, int y, int z, BlockState state) {}

  public static final class Tunnel {
    public UUID worker;
    public int route, lateral, lane, column, row, connector;
    public boolean finished, wet;

    public BlockPos at(MinePlan m, int right, int front) {
      return m.workAt(route, right, -m.shaftDepth, front);
    }

    public BlockPos face(MinePlan m) {
      return at(
          m,
          connector < lane * 3 ? connector * lateral : lane * 3 * lateral,
          connector < lane * 3 ? 3 : 3 + column);
    }

    public CompoundTag save() {
      CompoundTag n = new CompoundTag();
      if (worker != null) n.putUUID("Worker", worker);
      n.putInt("Route", route);
      n.putInt("Lateral", lateral);
      n.putInt("Lane", lane);
      n.putInt("Column", column);
      n.putInt("Row", row);
      n.putInt("Connector", connector);
      n.putBoolean("Finished", finished);
      n.putBoolean("Wet", wet);
      return n;
    }

    public static Tunnel load(CompoundTag n) {
      Tunnel t = new Tunnel();
      t.worker = n.hasUUID("Worker") ? n.getUUID("Worker") : null;
      t.route = n.getInt("Route");
      t.lateral = n.getInt("Lateral");
      t.lane = n.getInt("Lane");
      t.column = n.getInt("Column");
      t.row = n.getInt("Row");
      t.connector = n.getInt("Connector");
      t.finished = n.getBoolean("Finished");
      t.wet = n.getBoolean("Wet");
      return t;
    }
  }

  public final List<Tunnel> tunnels = new ArrayList<>();
  public UUID leadMiner;

  public Tunnel tunnel(UUID worker) {
    return tunnels.stream().filter(t -> worker.equals(t.worker)).findFirst().orElse(null);
  }

  public Tunnel claimTunnel(Lumberton w, Station station) {
    Tunnel own = tunnel(w.getUUID());
    if (own != null && !own.finished) return own;
    if (own != null) own.worker = null;
    // Retain an unloaded worker's face; release only a known reassignment/packing/destruction.
    for (Tunnel t : tunnels) {
      if (t.worker == null) continue;
      CompoundTag record = Ledger.get(w.getServer()).workers.get(t.worker);
      if (record != null
          && (!record.getString("State").equals("Deployed")
              || record.getInt("Job") != 2
              || !record.hasUUID("TotemId")
              || !station.identity.equals(record.getUUID("TotemId")))) t.worker = null;
    }
    for (Tunnel t : tunnels)
      if (t.worker == null && !t.finished && !t.wet) {
        t.worker = w.getUUID();
        return t;
      }
    if (tunnels.isEmpty()) {
      Tunnel first = new Tunnel();
      if (quarrySide != 0 && quarryRoute < 0) quarryRoute = route;
      first.route = edgeQuarry && quarryLayer > 0 ? (route + 1) % 3 : route;
      first.lateral = lateral == 0 ? 1 : lateral;
      first.lane = lane;
      // Reuse all excavated cells when migrating a serpentine journal. Starting at the
      // near connector verifies existing air before continuing the unfinished branch.
      first.column = lane % 2 == 0 && turn == 0 ? column : 0;
      first.row = lane % 2 == 0 && turn == 0 ? tunnelRow : 0;
      first.connector = 0;
      for (int completed = 0; completed < lane; completed++) {
        Tunnel old = new Tunnel();
        old.route = route;
        old.lateral = first.lateral;
        old.lane = completed;
        old.column = 32;
        old.finished = true;
        tunnels.add(old);
      }
      first.worker = w.getUUID();
      tunnels.add(first);
      leadMiner = w.getUUID();
      return first;
    }
    int startRoute = own != null && own.wet ? (own.route + 1) % 3 : route;
    for (int direction = 0; direction < 3; direction++) {
      int candidateRoute = (startRoute + direction) % 3;
      for (int index = 0; index <= station.miningRange() / 3; index++)
        for (int side : new int[] {lateral == 0 ? 1 : lateral, lateral == 0 ? -1 : -lateral}) {
          if (index == 0 && side < 0) continue;
          final int candidateSide = side, candidateIndex = index;
          if (tunnels.stream()
              .anyMatch(
                  t ->
                      t.route == candidateRoute
                          && t.lane == candidateIndex
                          && (candidateIndex == 0 || t.lateral == candidateSide))) continue;
          if (!inBounds(station, workAt(candidateRoute, side * index * 3, -shaftDepth, 3))
              || !inBounds(station, workAt(candidateRoute, side * index * 3, -shaftDepth, 34)))
            continue;
          if (candidateRoute == route && quarrySide != 0 && side == quarrySide && index > 0)
            continue;
          Tunnel next = new Tunnel();
          next.route = candidateRoute;
          next.lateral = side;
          next.lane = index;
          next.worker = w.getUUID();
          tunnels.add(next);
          return next;
        }
    }
    return null;
  }

  public void reportTunnel(Lumberton w, Tunnel t) {
    if (leadMiner == null) leadMiner = w.getUUID();
    if (leadMiner.equals(w.getUUID())) {
      route = t.route;
      lateral = t.lateral;
      lane = t.lane;
      column = Math.min(31, t.column);
      tunnelRow = t.row;
      turn = 0;
    }
  }

  public UUID id = UUID.randomUUID();
  public BlockPos entrance;
  public Direction forward = Direction.NORTH;
  public int shaftDepth = 20;
  public boolean rustic;
  public boolean entranceComplete;
  public boolean openAccess = true;
  private BuildingTemplate minehead;
  public boolean depotRequired = true, depotBuilt;
  public final Set<Long> placedChests = new HashSet<>();
  public int route; // forward, clockwise, counterclockwise from the original entrance
  public boolean redirecting;
  public final Set<Integer> wetRoutes = new HashSet<>();
  public final List<CompoundTag> abandonedRoutes = new ArrayList<>();
  public Stage stage = Stage.SITE;
  public Mode mode = Mode.TUNNEL;
  public boolean edgeQuarry = true;
  public boolean paused;
  public int revision, construction, lane, column, tunnelRow, turn, lateral, quarrySide;
  public int quarryLayer, quarryCell, quarryStep;
  public int quarryRoute = -1;
  public String reason = "Finding mine site";
  public final Set<Long> exclusions = new HashSet<>();
  public final Set<Long> supports = new HashSet<>();
  public final Set<Long> passage = new HashSet<>();
  public final List<Long> lightCells = new ArrayList<>();
  public final Set<Long> torches = new HashSet<>();
  public final Set<Long> torchSupports = new HashSet<>();
  public BlockPos relocatingTorch, relocationFloor, excavationConflict;
  public boolean replacementPlaced;
  public BlockPos pendingLight;
  public long lightWait;
  public int lightCursor;
  // Transient leases. They are deliberately not restored as permanent locks after load.
  public UUID builder, traveller;
  public long builderUntil, travelUntil;
  public int search;
  public long nextSearch;
  private static List<Part> arch;

  public static List<Part> arch(ServerLevel level) {
    if (arch != null) return arch;
    List<Part> result = new ArrayList<>();
    try (InputStream in = MinePlan.class.getResourceAsStream("/data/automatons/mine/arch.json")) {
      if (in == null) throw new IOException("Missing mine arch");
      JsonObject data =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
      for (JsonElement value : data.getAsJsonArray("blocks")) {
        JsonObject b = value.getAsJsonObject();
        BlockState state =
            BlockStateParser.parseForBlock(
                    level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),
                    b.get("state").getAsString(),
                    false)
                .blockState();
        if (state.getBlock() instanceof TrapDoorBlock) state = Blocks.AIR.defaultBlockState();
        result.add(
            new Part(
                b.get("x").getAsInt() - 2,
                b.get("y").getAsInt(),
                b.get("z").getAsInt() - 2,
                state));
      }
    } catch (Exception e) {
      throw new IllegalStateException("Invalid packaged arch", e);
    }
    arch = List.copyOf(result);
    return arch;
  }

  public BlockPos at(int right, int up, int front) {
    return entrance.relative(forward.getClockWise(), right).relative(forward, front).above(up);
  }

  public BlockPos chamber() {
    return at(0, -shaftDepth, 1);
  }

  public BlockPos exit() {
    return workAt(0, -shaftDepth, 3);
  }

  public BlockPos ladder(int depth) {
    return entrance.below(depth);
  }

  public int firstLadder() {
    return 1;
  }

  public int ladderCount() {
    return shaftDepth - firstLadder() + 1;
  }

  public BlockPos hatch() {
    return rustic ? entrance.below() : entrance;
  }

  public BuildingTemplate minehead(ServerLevel l) {
    if (minehead == null) minehead = BuildingTemplate.load(l, "minehead");
    return minehead;
  }

  public BlockState ladderState() {
    return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, forward);
  }

  public Rotation rotation() {
    return switch (forward) {
      case EAST -> Rotation.CLOCKWISE_90;
      case SOUTH -> Rotation.CLOCKWISE_180;
      case WEST -> Rotation.COUNTERCLOCKWISE_90;
      default -> Rotation.NONE;
    };
  }

  public static boolean inBounds(Station s, BlockPos p) {
    return Math.max(
            Math.abs(p.getX() - s.getBlockPos().getX()),
            Math.abs(p.getZ() - s.getBlockPos().getZ()))
        <= s.miningRange();
  }

  public boolean laneFits(Station s, int index, int side) {
    int x = index * 3 * side;
    if (!inBounds(s, workAt(x, -shaftDepth, 3)) || !inBounds(s, workAt(x, -shaftDepth, 34)))
      return false;
    // A quarry and its connector must not undermine the tunnel floor.
    return quarrySide == 0 || index == 0 || side != quarrySide;
  }

  public BlockPos tunnelColumn() {
    int x = lane * 3 * lateral;
    int front = lane % 2 == 0 ? 3 + column : 34 - column;
    return workAt(x, -shaftDepth, front);
  }

  public Direction quarryFacing() {
    return facing(quarryRoute < 0 ? route : quarryRoute);
  }

  public BlockPos quarryAt(int right, int up, int front) {
    return workAt(quarryRoute < 0 ? route : quarryRoute, right, up, front);
  }

  public BlockPos quarryOrigin() {
    return quarryAt(edgeQuarry ? 0 : quarrySide * 5, -shaftDepth, 5);
  }

  public BlockPos step(int depth) {
    // Clockwise 16-cell ring, beginning at the chamber-facing middle cell.
    int[][] ring = {
      {0, -2}, {1, -2}, {2, -2}, {2, -1}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2},
      {-2, 1}, {-2, 0}, {-2, -1}, {-2, -2}, {-1, -2}
    };
    int[] p = ring[Math.floorMod(depth, 16)];
    return quarryOrigin()
        .relative(quarryFacing().getClockWise(), p[0] * quarrySide)
        .relative(quarryFacing(), p[1])
        .below(depth);
  }

  public void retain(BlockPos feet) {
    supports.add(feet.below().asLong());
    exposed(feet);
  }

  public void exposed(BlockPos feet) {
    if (passage.add(feet.asLong())) lightCells.add(feet.asLong());
    if (passage.add(feet.above().asLong())) lightCells.add(feet.above().asLong());
    if (pendingLight == null) pendingLight = feet.immutable();
  }

  public boolean protectedInfrastructure(BlockPos p) {
    if (entrance == null) return false;
    if (supports.contains(p.asLong()) || torchSupports.contains(p.asLong()) || depotCell(p))
      return true;
    for (int i = 1; i <= shaftDepth; i++)
      if (p.equals(ladder(i)) || p.equals(ladder(i).relative(forward.getOpposite()))) return true;
    if (rustic
        && p.getY() >= entrance.getY() - 1
        && p.getY() <= entrance.getY() + 5
        && Math.abs(p.getX() - entrance.getX()) <= 3
        && Math.abs(p.getZ() - entrance.getZ()) <= 3) return true;
    if (p.getY() >= entrance.getY()
        && p.getY() <= entrance.getY() + 5
        && Math.abs(p.getX() - entrance.getX()) <= 2
        && Math.abs(p.getZ() - entrance.getZ()) <= 2) return true;
    return false;
  }

  public List<Task> constructionTasks(ServerLevel l) {
    if (entrance == null) return List.of();
    List<Task> out = new ArrayList<>();
    if (stage == Stage.ARCH && rustic) {
      BuildingTemplate template = minehead(l);
      for (var c : template.cells)
        if (c.state().isAir())
          out.add(
              new Task(template.world(entrance, forward, c.local()), null, "Clearing entrance"));
      for (var c : template.placements()) {
        BlockPos p = template.world(entrance, forward, c.local());
        out.add(new Task(p, null, "Excavating foundation"));
        out.add(new Task(p, c.state().rotate(rotation()), "Building entrance"));
      }
      return out;
    }
    if (stage == Stage.ARCH) {
      for (Part p : arch(l))
        if (p.state.isAir()) out.add(new Task(at(p.x, p.y, -p.z), null, "Clearing entrance"));
      // The four plank feet provide real standing positions. Build the roof before
      // closing its pillars so every placement retains a clear line of sight.
      List<Part> placements =
          arch(l).stream()
              .filter(p -> !p.state.isAir())
              .sorted(Comparator.comparingInt((Part p) -> p.y == 0 ? -10 : -p.y))
              .toList();
      for (Part p : placements) {
        BlockState state = p.state.rotate(rotation());
        if (state.getBlock() instanceof TrapDoorBlock)
          state = state.setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, forward);
        out.add(new Task(at(p.x, p.y, -p.z), state, "Building entrance"));
      }
    } else if (stage == Stage.SHAFT) {
      for (int depth = firstLadder(); depth <= shaftDepth; depth++) {
        BlockPos p = ladder(depth);
        out.add(new Task(p, null, "Excavating shaft"));
        out.add(
            new Task(
                p.relative(forward.getOpposite()),
                Blocks.COBBLESTONE.defaultBlockState(),
                "Backing ladder"));
        out.add(new Task(p, ladderState(), "Installing ladder"));
      }
    } else if (stage == Stage.CHAMBER) {
      for (int front = 0; front <= 2; front++)
        for (int right : new int[] {0, -1, 1})
          for (int up : new int[] {1 - shaftDepth, 2 - shaftDepth, -shaftDepth}) {
            BlockPos p = at(right, up, front);
            if (right == 0 && front == 0) continue;
            out.add(new Task(p, null, "Clearing chamber"));
          }
    }
    if (stage == Stage.STORAGE) {
      for (BlockPos pos : depotPositions()) {
        if (placedChests.contains(pos.asLong()) && l.getBlockState(pos).is(Blocks.CHEST)) continue;
        out.add(new Task(pos.above(), null, "Clearing chest alcove"));
        out.add(new Task(pos, null, "Clearing chest alcove"));
        out.add(
            new Task(pos.below(), Blocks.COBBLESTONE.defaultBlockState(), "Supporting storage"));
        Direction facing =
            pos.equals(depotPositions().get(0)) || pos.equals(depotPositions().get(1))
                ? forward.getClockWise()
                : forward.getCounterClockWise();
        out.add(
            new Task(
                pos.relative(facing.getOpposite()),
                Blocks.COBBLESTONE.defaultBlockState(),
                "Supporting storage"));
        out.add(
            new Task(
                pos,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing),
                "Installing mine chest"));
      }
    }
    return out;
  }

  public Direction workFacing() {
    return route == 1
        ? forward.getClockWise()
        : route == 2 ? forward.getCounterClockWise() : forward;
  }

  public BlockPos workOrigin() {
    return at(0, 0, 2);
  }

  public BlockPos workAt(int right, int up, int front) {
    return workAt(route, right, up, front);
  }

  public Direction facing(int route) {
    return route == 1
        ? forward.getClockWise()
        : route == 2 ? forward.getCounterClockWise() : forward;
  }

  public BlockPos workAt(int route, int right, int up, int front) {
    return workOrigin()
        .relative(facing(route).getClockWise(), right)
        .relative(facing(route), front - 2)
        .above(up);
  }

  public List<BlockPos> depotPositions() {
    return List.of(
        at(-2, -shaftDepth, 0),
        at(-2, -shaftDepth, 1),
        at(2, -shaftDepth, 0),
        at(2, -shaftDepth, 1));
  }

  public boolean depotCell(BlockPos pos) {
    return depotRequired && entrance != null && depotPositions().contains(pos);
  }

  public boolean plannedExcavation(BlockPos p) {
    if (entrance == null) return false;
    for (Tunnel t : tunnels) {
      Direction direction = facing(t.route), side = direction.getClockWise();
      int x =
          (p.getX() - workOrigin().getX()) * side.getStepX()
              + (p.getZ() - workOrigin().getZ()) * side.getStepZ();
      int z =
          2
              + (p.getX() - workOrigin().getX()) * direction.getStepX()
              + (p.getZ() - workOrigin().getZ()) * direction.getStepZ();
      if (p.getY() >= entrance.getY() - shaftDepth
          && p.getY() <= entrance.getY() - shaftDepth + 1
          && ((x == t.lane * 3 * t.lateral && z >= 3 && z <= 34)
              || z == 3 && x * t.lateral >= 0 && x * t.lateral <= t.lane * 3)) return true;
    }
    int dx = p.getX() - workOrigin().getX(), dz = p.getZ() - workOrigin().getZ();
    int right =
        dx * workFacing().getClockWise().getStepX() + dz * workFacing().getClockWise().getStepZ();
    int front = 2 + dx * workFacing().getStepX() + dz * workFacing().getStepZ();
    int up = p.getY() - entrance.getY();
    if (up >= -shaftDepth
        && up <= 2 - shaftDepth
        && Math.abs(right) <= 1
        && front >= 0
        && front <= 2) return true;
    if (quarrySide != 0) {
      int quarryRight =
          dx * quarryFacing().getClockWise().getStepX()
              + dz * quarryFacing().getClockWise().getStepZ();
      int quarryFront = 2 + dx * quarryFacing().getStepX() + dz * quarryFacing().getStepZ();
      if (!edgeQuarry
          && up >= -shaftDepth
          && up <= 1 - shaftDepth
          && quarryFront == 3
          && quarryRight * quarrySide >= 1
          && quarryRight * quarrySide <= 5) return true;
      if (up <= 1 - shaftDepth
          && Math.abs(quarryRight - (edgeQuarry ? 0 : quarrySide * 5)) <= 1
          && quarryFront >= 4
          && quarryFront <= 6) return true;
      for (int depth = Math.max(0, -up - shaftDepth);
          depth <= Math.max(0, -up - shaftDepth + 2);
          depth++)
        if (p.equals(step(depth))
            || p.equals(step(depth).above())
            || p.equals(step(depth).above(2))) return true;
    }
    if (up >= -shaftDepth && up <= 1 - shaftDepth && front >= 3 && front <= 34) {
      if (right == 0) return true;
      int side = lateral == 0 ? (quarrySide == 0 ? 1 : -quarrySide) : lateral;
      if (right * side > 0 && (right % 3 == 0 || front == 3 || front == 34)) return true;
    }
    return false;
  }

  public boolean claim(Lumberton w) {
    long now = w.level().getGameTime();
    if (builder != null && !builder.equals(w.getUUID()) && now < builderUntil) return false;
    builder = w.getUUID();
    builderUntil = now + 60;
    return true;
  }

  public void release(UUID worker) {
    if (worker.equals(builder)) builder = null;
    if (worker.equals(traveller)) traveller = null;
  }

  public void switchMode(Mode requested) {
    if (mode != requested) {
      mode = requested;
      revision++;
      builder = null;
      reason = "Planning access";
    }
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    n.putUUID("Id", id);
    if (leadMiner != null) n.putUUID("LeadMiner", leadMiner);
    ListTag fronts = new ListTag();
    tunnels.forEach(t -> fronts.add(t.save()));
    n.put("Tunnels", fronts);
    n.putInt("ShaftDepth", shaftDepth);
    n.putBoolean("Rustic", rustic);
    n.putBoolean("OpenAccess", openAccess);
    n.putBoolean("FullLadder", true);
    n.putBoolean("EntranceComplete", entranceComplete || stage.ordinal() > Stage.ARCH.ordinal());
    n.putBoolean("DepotRequired", depotRequired);
    n.putBoolean("DepotBuilt", depotBuilt);
    n.putLongArray("PlacedChests", placedChests.stream().mapToLong(Long::longValue).toArray());
    n.putInt("Route", route);
    n.putBoolean("Redirecting", redirecting);
    n.putIntArray("WetRoutes", wetRoutes.stream().mapToInt(Integer::intValue).toArray());
    ListTag oldRoutes = new ListTag();
    abandonedRoutes.forEach(r -> oldRoutes.add(r.copy()));
    n.put("AbandonedRoutes", oldRoutes);
    if (builder != null) n.putUUID("Builder", builder);
    if (entrance != null) n.putLong("Entrance", entrance.asLong());
    n.putString("Stage", stage.name());
    n.putString("Mode", mode.name());
    n.putBoolean("EdgeQuarry", edgeQuarry);
    n.putInt("Facing", forward.get2DDataValue());
    n.putBoolean("Paused", paused);
    n.putInt("Revision", revision);
    n.putInt("Construction", construction);
    n.putInt("Lane", lane);
    n.putInt("Column", column);
    n.putInt("Row", tunnelRow);
    n.putInt("Turn", turn);
    n.putInt("Lateral", lateral);
    n.putInt("QuarrySide", quarrySide);
    n.putInt("QuarryRoute", quarryRoute);
    n.putInt("Layer", quarryLayer);
    n.putInt("Cell", quarryCell);
    n.putInt("Step", quarryStep);
    n.putString("Reason", reason);
    n.putLongArray("Supports", supports.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("Passage", passage.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("Exclusions", exclusions.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("Torches", torches.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("TorchSupports", torchSupports.stream().mapToLong(Long::longValue).toArray());
    if (relocatingTorch != null) {
      n.putLong("RelocatingTorch", relocatingTorch.asLong());
      n.putLong("RelocationFloor", relocationFloor.asLong());
      n.putLong("ExcavationConflict", excavationConflict.asLong());
      n.putBoolean("ReplacementPlaced", replacementPlaced);
    }
    if (pendingLight != null) n.putLong("PendingLight", pendingLight.asLong());
    return n;
  }

  public static MinePlan load(CompoundTag n) {
    MinePlan p = new MinePlan();
    if (n.hasUUID("Id")) p.id = n.getUUID("Id");
    if (n.hasUUID("LeadMiner")) p.leadMiner = n.getUUID("LeadMiner");
    for (Tag t : n.getList("Tunnels", Tag.TAG_COMPOUND))
      p.tunnels.add(Tunnel.load((CompoundTag) t));
    if (n.hasUUID("Builder")) p.builder = n.getUUID("Builder");
    if (n.contains("Entrance")) p.entrance = BlockPos.of(n.getLong("Entrance"));
    p.rustic = n.getBoolean("Rustic");
    p.edgeQuarry = n.getBoolean("EdgeQuarry") || !n.contains("Entrance");
    try {
      p.stage = Stage.valueOf(n.getString("Stage"));
      p.mode = Mode.TUNNEL;
    } catch (IllegalArgumentException ignored) {
    }
    p.shaftDepth =
        n.contains("ShaftDepth")
            ? Math.max(5, Math.min(20, n.getInt("ShaftDepth")))
            : p.entrance == null ? 20 : 5;
    p.depotRequired = !n.contains("DepotRequired") || n.getBoolean("DepotRequired");
    p.depotBuilt = n.getBoolean("DepotBuilt");
    for (long pos : n.getLongArray("PlacedChests")) p.placedChests.add(pos);
    p.route = Math.max(0, Math.min(2, n.getInt("Route")));
    p.redirecting = n.getBoolean("Redirecting");
    for (int route : n.getIntArray("WetRoutes")) p.wetRoutes.add(route);
    for (Tag t : n.getList("AbandonedRoutes", Tag.TAG_COMPOUND))
      p.abandonedRoutes.add(((CompoundTag) t).copy());
    if (p.stage == Stage.READY && p.depotRequired && !p.depotBuilt) {
      p.stage = Stage.STORAGE;
    }
    p.forward = Direction.from2DDataValue(n.getInt("Facing"));
    p.paused = n.getBoolean("Paused");
    p.revision = n.getInt("Revision");
    p.construction = p.stage == Stage.STORAGE ? 0 : Math.max(0, n.getInt("Construction"));
    // Reconcile the new top rung before resuming an older partial shaft.
    if (!n.getBoolean("FullLadder") && p.stage == Stage.SHAFT) p.construction = 0;
    p.entranceComplete =
        n.getBoolean("EntranceComplete") || p.stage.ordinal() > Stage.ARCH.ordinal();
    // Reconcile old partial entrances against the revised hatch-free cell list.
    if (!n.getBoolean("OpenAccess") && p.stage == Stage.ARCH) p.construction = 0;
    p.lane = Math.max(0, n.getInt("Lane"));
    p.column = Math.max(0, Math.min(31, n.getInt("Column")));
    p.tunnelRow = n.getInt("Row");
    p.turn = n.getInt("Turn");
    p.lateral = Integer.signum(n.getInt("Lateral"));
    p.quarrySide = Integer.signum(n.getInt("QuarrySide"));
    p.quarryRoute =
        n.contains("QuarryRoute") ? n.getInt("QuarryRoute") : p.quarrySide == 0 ? -1 : p.route;
    p.quarryLayer = Math.max(0, n.getInt("Layer"));
    p.quarryCell = Math.max(0, n.getInt("Cell"));
    p.quarryStep = Math.max(0, n.getInt("Step"));
    p.reason = n.getString("Reason");
    for (long v : n.getLongArray("Supports")) p.supports.add(v);
    for (long v : n.getLongArray("Passage")) p.passage.add(v);
    p.lightCells.addAll(p.passage);
    for (long v : n.getLongArray("Exclusions")) p.exclusions.add(v);
    for (long v : n.getLongArray("Torches")) p.torches.add(v);
    for (long v : n.getLongArray("TorchSupports")) p.torchSupports.add(v);
    if (n.contains("RelocatingTorch")) {
      p.relocatingTorch = BlockPos.of(n.getLong("RelocatingTorch"));
      p.relocationFloor = BlockPos.of(n.getLong("RelocationFloor"));
      p.excavationConflict = BlockPos.of(n.getLong("ExcavationConflict"));
      p.replacementPlaced = n.getBoolean("ReplacementPlaced");
    }
    if (n.contains("PendingLight")) p.pendingLight = BlockPos.of(n.getLong("PendingLight"));
    return p;
  }
}
