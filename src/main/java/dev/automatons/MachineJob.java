package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;

/** Saved instruction cursor and recorded physical return route, independent of the Program item. */
public final class MachineJob {
  public record Pose(BlockPos pos, Direction heading) {
    CompoundTag save() {
      CompoundTag n = new CompoundTag();
      n.putLong("Pos", pos.asLong());
      n.putInt("Heading", heading.get2DDataValue());
      return n;
    }

    static Pose load(CompoundTag n) {
      return new Pose(
          BlockPos.of(n.getLong("Pos")), Direction.from2DDataValue(n.getInt("Heading")));
    }
  }

  private final Machine m;
  public boolean active, fuelWaiting;
  public BlockPos start;
  public Direction startHeading;
  public UUID startHome;
  public int cursor, remaining, waitTicks, chunkX, chunkZ;
  public String text = "";
  public int attachment;
  public final List<Pose> trail = new ArrayList<>();
  private List<MachineProgram.Instruction> instructions = List.of();
  private final List<BlockPos> chunkTargets = new ArrayList<>();
  private int phase, routeIndex, retries;
  private boolean explicitDrop, awaitingMove;
  private Pose workPose;
  private Machine.Mode savedMode = Machine.Mode.PROGRAM;

  public MachineJob(Machine m) {
    this.m = m;
  }

  public String start() {
    if (m.locked()) return "Pause before starting a new job";
    if (m.pedestal() == null) return "Select a loaded pedestal";
    if (m.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player)
      return "Dismount before starting automatic work";
    if (m.operator != null && !m.hasPilot()) return "Waiting for assigned Operatorton to board";
    if (m.mode == Machine.Mode.MANUAL) return "Select Chunk or Program mode";
    if (m.mode == Machine.Mode.CHUNK && m.attachment() != 1)
      return "Chunk mode requires the Blade Attachment";
    if (m.mode == Machine.Mode.PROGRAM) {
      if (!m.equipment.getStackInSlot(1).is(MachineParts.item("program")))
        return "Insert a Program";
      text = m.equipment.getStackInSlot(1).getOrCreateTag().getString("Text");
      var parsed = MachineProgram.parse(text);
      if (!parsed.valid()) return parsed.error();
      instructions = parsed.instructions();
    }
    start = m.grid;
    startHeading = m.heading;
    startHome = m.homeId;
    attachment = m.attachment();
    savedMode = m.mode;
    cursor = 0;
    remaining = 0;
    waitTicks = 0;
    phase = 0;
    awaitingMove = false;
    retries = 0;
    trail.clear();
    trail.add(new Pose(start, startHeading));
    if (m.mode == Machine.Mode.CHUNK) buildChunkTargets();
    active = true;
    m.startEngine();
    m.sync();
    return "Starting work";
  }

  public String resume() {
    if (start == null) return "No saved job — Start Work creates a new job";
    if (m.motionTicks > 0 || m.workTicks > 0) return "Wait until parked";
    if (!Objects.equals(startHome, m.homeId)) return "Pedestal changed — start a new job";
    if (m.attachment() != attachment) return "Restore the job's attachment or start a new job";
    if (m.operator != null && !m.hasPilot()) return "Waiting for assigned Operatorton to board";
    if (!programValid()) return "Program changed or missing — start a new job";
    Pose expected = phase == 0 && !trail.isEmpty() ? trail.get(trail.size() - 1) : workPose;
    if (phase == 0
        && expected != null
        && (!m.grid.equals(expected.pos) || m.heading != expected.heading))
      return "Machine moved from saved work position — return there or start a new job";
    m.mode = savedMode;
    active = true;
    fuelWaiting = false;
    m.startEngine();
    m.sync();
    return "Resuming saved work";
  }

  public void cancel() {
    active = false;
    fuelWaiting = false;
    start = null;
    trail.clear();
    instructions = List.of();
    cursor = remaining = waitTicks = phase = 0;
    awaitingMove = false;
    workPose = null;
  }

  public void programChanged() {
    if (start != null) {
      cancel();
      m.status = "Program changed — Start Work creates a new job";
    }
  }

  public boolean programValid() {
    return savedMode != Machine.Mode.PROGRAM
        || m.equipment.getStackInSlot(1).is(MachineParts.item("program"))
            && m.equipment.getStackInSlot(1).getOrCreateTag().getString("Text").equals(text);
  }

  public boolean insideChunk(BlockPos p) {
    return p.getX() >> 4 == chunkX && p.getZ() >> 4 == chunkZ;
  }

  private void buildChunkTargets() {
    chunkTargets.clear();
    int x = chunkX * 16, z = chunkZ * 16;
    int[] offsets = {1, 4, 7, 10, 13, 14};
    for (int i = 0; i < offsets.length; i++) {
      int from = i % 2 == 0 ? -2 : 17, to = i % 2 == 0 ? 14 : 1;
      chunkTargets.add(new BlockPos(x + offsets[i], start.getY(), z + from));
      chunkTargets.add(new BlockPos(x + offsets[i], start.getY(), z + to));
    }
  }

  public void tick() {
    if (!active) return;
    if (m.operator != null && !m.hasPilot()) {
      m.halt("Driver unavailable — job paused");
      return;
    }
    if (!programValid()) {
      m.halt("Program removed or changed — job paused");
      return;
    }
    if (m.pedestal() == null || m.pedestal().recalled) {
      m.halt("Pedestal unavailable or recalled");
      return;
    }
    if (retries > 0) {
      retries--;
      return;
    }
    if (trail.size() >= MachineRules.MAX_ROUTE) {
      m.halt("Return route limit reached — start a new job");
      return;
    }
    if (phase > 0) {
      detour();
      return;
    }
    if (m.mode == Machine.Mode.CHUNK) {
      if (cursor >= chunkTargets.size()) {
        complete();
        return;
      }
      BlockPos target = chunkTargets.get(cursor);
      if (m.grid.getX() == target.getX() && m.grid.getZ() == target.getZ()) {
        cursor++;
        return;
      }
      stepToward(target, false);
      return;
    }
    if (cursor >= instructions.size()) {
      complete();
      return;
    }
    var instruction = instructions.get(cursor);
    switch (instruction.kind()) {
      case TURN -> {
        if (m.turn(instruction.direction())) cursor++;
      }
      case MOVE -> {
        if (remaining == 0) remaining = instruction.amount();
        if (m.turn(instruction.direction())) {
          awaitingMove = true;
          m.advance(instruction.direction());
        }
      }
      case WAIT -> {
        if (waitTicks == 0) waitTicks = instruction.amount();
        m.status = "Waiting: " + (waitTicks + 19) / 20 + " s";
        if (--waitTicks == 0) cursor++;
      }
      case DROPOFF -> beginDetour(true);
    }
  }

  private void complete() {
    m.halt("Job complete — parked");
    start = null;
  }

  public void completedMotion() {
    if (!active || start == null) return;
    if (phase == 0) {
      trail.add(new Pose(m.grid, m.heading));
      if (awaitingMove
          && m.mode == Machine.Mode.PROGRAM
          && remaining > 0
          && cursor < instructions.size()
          && instructions.get(cursor).kind() == MachineProgram.Kind.MOVE) {
        // A turn never consumes movement. Only a changed grid cell completes a block.
        Pose previous = trail.size() > 1 ? trail.get(trail.size() - 2) : trail.get(0);
        if (!previous.pos.equals(m.grid) && --remaining == 0) cursor++;
      }
      awaitingMove = false;
    }
  }

  public void cargoFull() {
    if (!active || phase > 0) return;
    if (m.hasPilot()) beginDetour(false);
    else {
      m.status = "Cargo full — requesting Runnerton";
      MachineHauling.request(m);
      retries = MachineRules.RETRY_TICKS;
    }
  }

  private void beginDetour(boolean explicit) {
    explicitDrop = explicit;
    workPose = new Pose(m.grid, m.heading);
    phase = 1;
    routeIndex = trail.size() - 1;
    awaitingMove = false;
    m.workTicks = 0;
    m.status = "Returning to unload";
  }

  private boolean pose(Pose target) {
    if (!m.grid.equals(target.pos)) {
      int dx = target.pos.getX() - m.grid.getX(), dz = target.pos.getZ() - m.grid.getZ();
      if (Math.abs(dx) + Math.abs(dz) != 1 || Math.abs(target.pos.getY() - m.grid.getY()) > 1) {
        m.status = "Saved return route changed";
        return false;
      }
      Direction travel =
          dx > 0
              ? Direction.EAST
              : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
      if (travel.getAxis() != m.heading.getAxis()) {
        m.status = "Saved return heading blocked";
        return false;
      }
      m.advance(travel);
      return false;
    }
    return m.turn(target.heading);
  }

  private void stepToward(BlockPos target, boolean reverse) {
    int dx = target.getX() - m.grid.getX(), dz = target.getZ() - m.grid.getZ();
    Direction direction =
        dx > 0
            ? Direction.EAST
            : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
    if (reverse && direction == m.heading.getOpposite() || m.turn(direction)) m.advance(direction);
  }

  private void detour() {
    if (phase == 1) {
      m.status = "Returning to unload";
      if (routeIndex < 0) {
        phase = 2;
        return;
      }
      if (pose(trail.get(routeIndex))) routeIndex--;
      else if (m.motionTicks == 0 && m.workTicks == 0) MachineHauling.request(m);
    } else if (phase == 2) {
      int before = countCargo();
      for (var endpoint : MachineHauling.docks(m, start)) {
        for (int i = 0; i < m.cargo.getSlots(); i++) {
          ItemStack stack = m.cargo.getStackInSlot(i);
          m.cargo.setStackInSlot(
              i,
              net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(
                  endpoint, stack.copy(), false));
        }
      }
      if (countCargo() > 0) {
        m.status = "Waiting for cargo space at start";
        MachineHauling.request(m);
        retries = MachineRules.RETRY_TICKS;
        return;
      }
      if (explicitDrop) {
        cursor++;
        phase = 0;
        trail.clear();
        trail.add(new Pose(m.grid, m.heading));
      } else {
        phase = 3;
        routeIndex = 0;
      }
    } else {
      m.status = "Returning to work";
      if (routeIndex >= trail.size()) {
        phase = 0;
        return;
      }
      if (pose(trail.get(routeIndex))) routeIndex++;
    }
  }

  private int countCargo() {
    int n = 0;
    for (int i = 0; i < m.cargo.getSlots(); i++) n += m.cargo.getStackInSlot(i).getCount();
    return n;
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    if (start != null) {
      n.putLong("Start", start.asLong());
      n.putInt("StartHeading", startHeading.get2DDataValue());
      if (startHome != null) n.putUUID("Home", startHome);
    }
    n.putString("Text", text);
    n.putInt("Cursor", cursor);
    n.putInt("Remaining", remaining);
    n.putInt("Wait", waitTicks);
    n.putString("SavedMode", savedMode.name());
    n.putInt("Attachment", attachment);
    n.putInt("ChunkX", chunkX);
    n.putInt("ChunkZ", chunkZ);
    n.putInt("Phase", phase);
    n.putInt("RouteIndex", routeIndex);
    n.putBoolean("Explicit", explicitDrop);
    if (workPose != null) n.put("WorkPose", workPose.save());
    ListTag route = new ListTag();
    for (Pose p : trail) route.add(p.save());
    n.put("Trail", route);
    return n;
  }

  public void load(CompoundTag n) {
    start = n.contains("Start") ? BlockPos.of(n.getLong("Start")) : null;
    startHeading = Direction.from2DDataValue(n.getInt("StartHeading"));
    startHome = n.hasUUID("Home") ? n.getUUID("Home") : null;
    text = n.getString("Text");
    instructions = MachineProgram.parse(text).instructions();
    try {
      savedMode = Machine.Mode.valueOf(n.getString("SavedMode"));
    } catch (IllegalArgumentException e) {
      savedMode = m.mode;
    }
    cursor = Math.max(0, n.getInt("Cursor"));
    remaining = Math.max(0, n.getInt("Remaining"));
    waitTicks = Math.max(0, n.getInt("Wait"));
    attachment = n.getInt("Attachment");
    chunkX = n.getInt("ChunkX");
    chunkZ = n.getInt("ChunkZ");
    phase = Math.max(0, Math.min(3, n.getInt("Phase")));
    routeIndex = n.getInt("RouteIndex");
    explicitDrop = n.getBoolean("Explicit");
    workPose = n.contains("WorkPose") ? Pose.load(n.getCompound("WorkPose")) : null;
    trail.clear();
    for (Tag t : n.getList("Trail", 10)) {
      if (trail.size() >= MachineRules.MAX_ROUTE) break;
      trail.add(Pose.load((CompoundTag) t));
    }
    if (start != null && savedMode == Machine.Mode.CHUNK) buildChunkTargets();
  }
}
