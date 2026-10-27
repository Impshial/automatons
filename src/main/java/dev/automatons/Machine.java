package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.*;
import net.minecraftforge.network.NetworkHooks;

/** One server controller owns all player, unattended and Operatorton driving. */
public final class Machine extends Entity implements MenuProvider {
  private static final EntityDataAccessor<CompoundTag> VIEW =
      SynchedEntityData.defineId(Machine.class, EntityDataSerializers.COMPOUND_TAG);

  public enum Engine {
    OFF,
    STARTING,
    RUNNING,
    STOPPING
  }

  public enum Mode {
    MANUAL,
    CHUNK,
    PROGRAM
  }

  public UUID owner, homeId, operator;
  public BlockPos home, grid = BlockPos.ZERO;
  public Direction heading = Direction.NORTH;
  public Engine engine = Engine.OFF;
  public Mode mode = Mode.MANUAL;
  public int fuel, engineTicks, motionTicks, workTicks;
  public double fuelFraction;
  public String status = "Parked";
  public CompoundTag tankData = new CompoundTag();
  public final ItemStackHandler cargo = new ItemStackHandler(MachineRules.CARGO_SLOTS);

  /** Attachment, Program and refueling container; never exposed as cargo capabilities. */
  public final ItemStackHandler equipment = new ItemStackHandler(3);

  public final MachineJob job = new MachineJob(this);
  private final LazyOptional<IItemHandler> inventory = LazyOptional.of(() -> cargo);
  private BlockPos motionTarget;
  private Direction turnTarget;
  private int manualInput, inputAge;
  private boolean recovered, loading;
  private double lerpX, lerpY, lerpZ;
  private float lerpYaw;
  private int lerpSteps;

  public Machine(EntityType<? extends Machine> type, Level level) {
    super(type, level);
    blocksBuilding = true;
  }

  @Override
  protected void defineSynchedData() {
    entityData.define(VIEW, new CompoundTag());
  }

  public CompoundTag view() {
    return entityData.get(VIEW);
  }

  public int attachment() {
    if (level().isClientSide) return view().getInt("Attachment");
    ItemStack item = equipment.getStackInSlot(0);
    return item.is(MachineParts.item("blade_attachment"))
        ? 1
        : item.is(MachineParts.item("bore_attachment")) ? 2 : 0;
  }

  public boolean owns(Player p) {
    return owner != null && owner.equals(p.getUUID());
  }

  public boolean locked() {
    return job.active || motionTicks > 0 || workTicks > 0;
  }

  public boolean stopped() {
    return motionTicks == 0 && workTicks == 0;
  }

  public boolean hasPilot() {
    return getFirstPassenger() instanceof Lumberton w
        && operator != null
        && operator.equals(w.getUUID());
  }

  public Station pedestal() {
    if (!(level() instanceof ServerLevel l) || home == null || !l.hasChunkAt(home)) return null;
    return l.getBlockEntity(home) instanceof Station s
            && !s.isTotem()
            && Objects.equals(s.identity, homeId)
            && Objects.equals(s.owner, owner)
        ? s
        : null;
  }

  public void linkNearest() {
    if (!(level() instanceof ServerLevel l)) return;
    WorldData.get(l).homes.entrySet().stream()
        .filter(e -> e.getValue().exists() && Objects.equals(e.getValue().owner(), owner))
        .sorted(Comparator.comparingDouble(e -> e.getValue().pos().distSqr(grid)))
        .filter(e -> e.getValue().pos().distSqr(grid) <= MachineRules.RANGE * MachineRules.RANGE)
        .findFirst()
        .ifPresent(
            e -> {
              homeId = e.getKey();
              home = e.getValue().pos();
            });
  }

  public boolean link(UUID id) {
    if (locked() || !(level() instanceof ServerLevel l)) return false;
    var h = WorldData.get(l).homes.get(id);
    if (h == null || !h.exists() || !Objects.equals(h.owner(), owner) || !l.hasChunkAt(h.pos()))
      return false;
    homeId = id;
    home = h.pos();
    job.cancel();
    sync();
    return true;
  }

  public void snap() {
    setPos(grid.getX() + .5, grid.getY(), grid.getZ() + .5);
    setYRot(heading.toYRot());
  }

  @Override
  protected AABB makeBoundingBox() {
    if (heading == null || equipment == null) return super.makeBoundingBox();
    int kind = attachment();
    double x0 = getX() - 1.49, x1 = getX() + 1.49, z0 = getZ() - 1.49, z1 = getZ() + 1.49;
    Direction facing =
        level().isClientSide ? Direction.from2DDataValue(view().getInt("Heading")) : heading;
    if (kind != 0) {
      if (facing == Direction.EAST) x1++;
      if (facing == Direction.WEST) x0--;
      if (facing == Direction.SOUTH) z1++;
      if (facing == Direction.NORTH) z0--;
    }
    return new AABB(x0, getY(), z0, x1, getY() + (kind == 2 ? 2.99 : 1.99), z1);
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
    super.onSyncedDataUpdated(key);
    if (key.equals(VIEW) && equipment != null) setBoundingBox(makeBoundingBox());
  }

  public void sync() {
    if (level().isClientSide) return;
    CompoundTag n = new CompoundTag();
    n.putString("Engine", engine.name());
    n.putInt("EngineTicks", engineTicks);
    n.putInt("Fuel", fuel);
    n.putInt("Attachment", attachment());
    n.putString("Mode", mode.name());
    n.putString("Status", status);
    n.putBoolean("Moving", motionTicks > 0);
    n.putBoolean("Working", workTicks > 0);
    n.putBoolean("Locked", locked());
    n.putBoolean("Active", job.active);
    n.putInt("Used", usedSlots());
    n.putInt("Instruction", job.cursor);
    n.putInt("Remaining", job.remaining);
    n.putLong("Grid", grid.asLong());
    n.putInt("Heading", heading.get2DDataValue());
    if (owner != null) n.putUUID("Owner", owner);
    if (operator != null) n.putUUID("Operator", operator);
    if (homeId != null) {
      n.putUUID("HomeId", homeId);
      n.putLong("Home", home.asLong());
    }
    if (job.start != null) n.putLong("Start", job.start.asLong());
    n.putInt("ChunkX", job.chunkX);
    n.putInt("ChunkZ", job.chunkZ);
    n.putBoolean("HasJob", job.start != null);
    entityData.set(VIEW, n);
    setBoundingBox(makeBoundingBox());
  }

  public int usedSlots() {
    int count = 0;
    for (int i = 0; i < cargo.getSlots(); i++) if (!cargo.getStackInSlot(i).isEmpty()) count++;
    return count;
  }

  public void startEngine() {
    if (engine == Engine.RUNNING || engine == Engine.STARTING) return;
    if (fuel <= 0) {
      status = "Out of Lumina";
      sync();
      return;
    }
    engine = Engine.STARTING;
    status = "Starting... Please wait";
    engineTicks = MachineRules.START_TICKS;
    sound(SoundEvents.PISTON_EXTEND, .8f, .7f);
    sync();
  }

  public void halt(String reason) {
    job.active = false;
    manualInput = 0;
    workTicks = 0;
    motionTicks = 0;
    motionTarget = null;
    turnTarget = null;
    snap();
    status = reason;
    if (engine != Engine.OFF && engine != Engine.STOPPING) {
      engine = Engine.STOPPING;
      engineTicks = MachineRules.STOP_TICKS;
      sound(SoundEvents.PISTON_CONTRACT, .8f, .7f);
    }
    sync();
  }

  public void input(int input) {
    if (input < 0 || input > 4 || !(getFirstPassenger() instanceof Player)) return;
    manualInput = input;
    inputAge = 0;
  }

  public void sound(SoundEvent sound, float volume, float pitch) {
    level().playSound(null, blockPosition(), sound, SoundSource.BLOCKS, volume, pitch);
  }

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide) {
      if (lerpSteps > 0) {
        setPos(
            getX() + (lerpX - getX()) / lerpSteps,
            getY() + (lerpY - getY()) / lerpSteps,
            getZ() + (lerpZ - getZ()) / lerpSteps);
        setYRot(getYRot() + net.minecraft.util.Mth.wrapDegrees(lerpYaw - getYRot()) / lerpSteps);
        lerpSteps--;
      }
      return;
    }
    if (tickCount % 10 == 0) {
      refuel();
      MachineOperator.validate(this);
    }
    if (job.active && !job.programValid()) halt("Program removed or changed — job paused");
    if (engine == Engine.STARTING && --engineTicks <= 0) {
      engine = Engine.RUNNING;
      status = "Engine running";
    } else if (engine == Engine.STOPPING && --engineTicks <= 0) engine = Engine.OFF;
    if (engine == Engine.RUNNING) {
      // A move/turn is paid before interpolation starts. Finish that paid step,
      // then stop at its committed grid position if it used the last fuel.
      if (motionTicks > 0) animateMotion();
      else if (fuel <= 0) {
        job.fuelWaiting = job.active;
        halt("Out of Lumina");
      } else if (job.active) job.tick();
      else if (getFirstPassenger() instanceof Player && ++inputAge <= 10 && manualInput > 0) {
        if (manualInput == 1 || manualInput == 2)
          advance(manualInput == 1 ? heading : heading.getOpposite());
        else {
          turn(manualInput == 3 ? heading.getCounterClockWise() : heading.getClockWise());
          manualInput = 0;
        }
      }
    }
    if (tickCount % 5 == 0) sync();
  }

  private void animateMotion() {
    if (turnTarget != null) {
      String changed = MachineTerrain.turnClearance(this, turnTarget);
      if (!changed.isEmpty()) {
        halt(changed + " — job paused");
        return;
      }
    }
    if (motionTarget != null) {
      String changed = MachineTerrain.clearance(this, motionTarget, heading, attachment());
      if (!changed.isEmpty()) {
        halt(changed + " — job paused");
        return;
      }
    }
    int duration = turnTarget == null ? MachineRules.MOVE_TICKS : MachineRules.TURN_TICKS;
    int elapsed = duration - --motionTicks;
    if (turnTarget != null)
      setYRot(
          heading.toYRot()
              + net.minecraft.util.Mth.wrapDegrees(turnTarget.toYRot() - heading.toYRot())
                  * elapsed
                  / duration);
    else if (motionTarget != null)
      setPos(
          grid.getX() + .5 + (motionTarget.getX() - grid.getX()) * (double) elapsed / duration,
          grid.getY() + (motionTarget.getY() - grid.getY()) * (double) elapsed / duration,
          grid.getZ() + .5 + (motionTarget.getZ() - grid.getZ()) * (double) elapsed / duration);
    if (motionTicks == 0) {
      if (turnTarget != null) heading = turnTarget;
      else if (motionTarget != null) grid = motionTarget;
      turnTarget = null;
      motionTarget = null;
      snap();
      job.completedMotion();
      sync();
    }
  }

  public boolean turn(Direction direction) {
    if (heading == direction) return true;
    if (engine != Engine.RUNNING || !stopped()) return false;
    Direction next = direction == heading.getOpposite() ? heading.getClockWise() : direction;
    String obstruction = MachineTerrain.turnClearance(this, next);
    if (!obstruction.isEmpty()) {
      status = obstruction;
      sync();
      return false;
    }
    fuel -= MachineRules.TURN_COST;
    turnTarget = next;
    motionTicks = MachineRules.TURN_TICKS;
    status = "Turning";
    sync();
    return false;
  }

  public boolean advance(Direction direction) {
    if (engine != Engine.RUNNING || motionTicks > 0 || direction.getAxis() != heading.getAxis())
      return false;
    var plan = MachineTerrain.plan(this, direction);
    if (!plan.error().isEmpty()) {
      workTicks = 0;
      status = plan.error();
      if (status.equals("Cargo full")) job.cargoFull();
      sync();
      return false;
    }
    if (!plan.cuts().isEmpty()) {
      if (++workTicks < MachineRules.BREAK_TICKS) {
        status = "Working";
        return false;
      }
      workTicks = 0;
      if (!MachineTerrain.cut(this, plan.cuts())) {
        sync();
        return false;
      }
      return false; // Revalidate after neighbor updates, fluids or falling blocks.
    }
    workTicks = 0;
    fuel -= MachineRules.MOVE_COST;
    motionTarget = plan.destination();
    motionTicks = MachineRules.MOVE_TICKS;
    status = direction == heading ? "Driving" : "Reversing";
    sound(SoundEvents.MINECART_INSIDE, .22f, .65f);
    sync();
    return false;
  }

  public boolean canInstall(ItemStack stack) {
    if (locked()) return false;
    int kind =
        stack.isEmpty()
            ? 0
            : stack.is(MachineParts.item("blade_attachment"))
                ? 1
                : stack.is(MachineParts.item("bore_attachment")) ? 2 : -1;
    return kind >= 0 && MachineTerrain.clearance(this, grid, heading, kind).isEmpty();
  }

  private static double fraction(double value) {
    return Double.isFinite(value) ? Math.max(0, Math.min(.999999999, value)) : 0;
  }

  private double creditFuel(double offered) {
    double accepted =
        Math.max(0, Math.min(offered, MachineRules.FUEL_THIRDS - fuel - fuelFraction));
    double total = fuel + fuelFraction + accepted;
    fuel = Math.min(MachineRules.FUEL_THIRDS, (int) Math.floor(total + 1e-9));
    fuelFraction = Math.max(0, total - fuel);
    return accepted;
  }

  private void takePackedFuel(CompoundTag data) {
    double stored =
        Math.max(0, data.getInt("MachineFuelThirds"))
            + fraction(data.getDouble("MachineFuelFraction"));
    double left = Math.max(0, stored - creditFuel(stored));
    int whole = (int) Math.floor(left + 1e-9);
    data.putInt("MachineFuelThirds", whole);
    data.putDouble("MachineFuelFraction", Math.max(0, left - whole));
  }

  private int fluidRoom(int volume, int thirds) {
    return (int)
        Math.min(
            Integer.MAX_VALUE,
            Math.floor(
                Math.max(0, MachineRules.FUEL_THIRDS - fuel - fuelFraction) * volume / thirds
                    + 1e-8));
  }

  /** One real transaction handles hand use and the GUI refueling slot. */
  public ItemStack refuelContainer(ItemStack original) {
    ItemStack stack = original.copy();
    if (stack.is(Luminall.TANK_ITEM.get())) {
      CompoundTag data = BlockItem.getBlockEntityData(stack);
      if (data != null) {
        data = data.copy();
        takePackedFuel(data);
        BlockItem.setBlockEntityData(stack, Luminall.TANK_BE.get(), data);
      }
    }
    int volume =
        stack.is(Luminall.BOTTLE.get())
            ? LuminallBottle.volume(stack)
            : Balance.BOTTLE_VOLUME.get();
    int thirds =
        3
            * (stack.is(Luminall.BOTTLE.get())
                ? LuminallBottle.reward(stack)
                : Balance.BOTTLE_LUMINA.get());
    var handler = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
    if (handler == null) return stack;
    FluidStack trial =
        handler.drain(
            new FluidStack(Luminall.SOURCE.get(), fluidRoom(volume, thirds)),
            IFluidHandler.FluidAction.SIMULATE);
    if (trial.isEmpty() || !trial.getFluid().isSame(Luminall.SOURCE.get())) return stack;
    FluidStack moved =
        handler.drain(
            new FluidStack(Luminall.SOURCE.get(), trial.getAmount()),
            IFluidHandler.FluidAction.EXECUTE);
    if (!moved.isEmpty() && moved.getFluid().isSame(Luminall.SOURCE.get()))
      creditFuel(moved.getAmount() * (double) thirds / volume);
    return handler.getContainer();
  }

  private void refuel() {
    takePackedFuel(tankData);
    FluidStack assembled = FluidStack.loadFluidStackFromNBT(tankData.getCompound("Tank"));
    if (assembled.getFluid().isSame(Luminall.SOURCE.get())) {
      int volume = Balance.BOTTLE_VOLUME.get(), thirds = Balance.BOTTLE_LUMINA.get() * 3;
      int moved = Math.min(assembled.getAmount(), fluidRoom(volume, thirds));
      if (moved > 0) {
        creditFuel(moved * (double) thirds / volume);
        assembled.shrink(moved);
        tankData.put("Tank", assembled.writeToNBT(new CompoundTag()));
      }
    }
    ItemStack stack = equipment.getStackInSlot(2);
    if (!stack.isEmpty()) {
      ItemStack result = refuelContainer(stack);
      if (!ItemStack.matches(stack, result)) equipment.setStackInSlot(2, result);
    }
  }

  private boolean handRefuel(Player player, InteractionHand hand) {
    if (player.isSpectator() || !player.mayBuild()) return false;
    ItemStack held = player.getItemInHand(hand);
    boolean packed = held.is(Luminall.TANK_ITEM.get());
    boolean fluid =
        held.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
            .map(
                h ->
                    !h.drain(
                            new FluidStack(Luminall.SOURCE.get(), Integer.MAX_VALUE),
                            IFluidHandler.FluidAction.SIMULATE)
                        .isEmpty())
            .orElse(false);
    if (!packed && !fluid) return false;
    if (level().isClientSide) return true;
    double before = fuel + fuelFraction;
    player.setItemInHand(hand, refuelContainer(held));
    if (fuel + fuelFraction > before) {
      level()
          .playSound(null, blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, .7f, 1);
      sync();
      player.displayClientMessage(
          Component.literal(
              "Machine refueled: " + Lumina.format(fuel / 3, fuel % 3) + " / 10000 Lumina"),
          true);
    } else
      player.displayClientMessage(
          Component.literal(
              fuel >= MachineRules.FUEL_THIRDS - 1
                  ? "Machine fuel tank full"
                  : "No Luminall in this container"),
          true);
    return true;
  }

  public void open(ServerPlayer p) {
    if (owns(p)) NetworkHooks.openScreen(p, this, b -> b.writeInt(getId()));
  }

  @Override
  public Component getDisplayName() {
    return Component.literal("The Machine");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new MachineMenu(id, inv, this);
  }

  @Override
  public InteractionResult interactAt(Player p, Vec3 hit, InteractionHand hand) {
    if (!owns(p) && !level().isClientSide) return InteractionResult.FAIL;
    if (p.isShiftKeyDown() && p.getItemInHand(hand).is(Automatons.WAND.get())) {
      if (!level().isClientSide) pack(p);
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    if (handRefuel(p, hand)) return InteractionResult.sidedSuccess(level().isClientSide);
    if (!level().isClientSide && p instanceof ServerPlayer sp) {
      if (MachinePicking.station(this, p)) {
        if (getPassengers().isEmpty() && operator == null) {
          halt("Manual control");
          mode = Mode.MANUAL;
          p.startRiding(this);
        } else
          p.displayClientMessage(
              Component.literal("Operator Station occupied — release the operator in Controls"),
              true);
      } else if (p.getItemInHand(hand).is(Automatons.WAND.get())
          && p.getItemInHand(hand).hasTag()
          && p.getItemInHand(hand).getTag().hasUUID("Worker")) {
        var entity =
            ((ServerLevel) level()).getEntity(p.getItemInHand(hand).getTag().getUUID("Worker"));
        if (entity instanceof Lumberton w)
          p.displayClientMessage(Component.literal(MachineOperator.assign(this, w)), true);
      } else open(sp);
    }
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  public InteractionResult interact(Player p, InteractionHand hand) {
    if (!owns(p) && !level().isClientSide) return InteractionResult.FAIL;
    if (p.isShiftKeyDown() && p.getItemInHand(hand).is(Automatons.WAND.get())) {
      if (!level().isClientSide) pack(p);
      return InteractionResult.sidedSuccess(level().isClientSide);
    }
    if (handRefuel(p, hand)) return InteractionResult.sidedSuccess(level().isClientSide);
    if (p instanceof ServerPlayer sp) open(sp);
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  protected boolean canAddPassenger(Entity passenger) {
    return getPassengers().isEmpty();
  }

  @Override
  public double getPassengersRidingOffset() {
    return .55;
  }

  @Override
  protected void positionRider(Entity passenger, MoveFunction move) {
    if (hasPassenger(passenger))
      move.accept(
          passenger,
          getX() - Math.sin(Math.toRadians(getYRot())) * .65,
          getY() + .55 + passenger.getMyRidingOffset(),
          getZ() + Math.cos(Math.toRadians(getYRot())) * .65);
  }

  @Override
  public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
    for (int r = 2; r <= 6; r++)
      for (Direction d : Direction.Plane.HORIZONTAL)
        for (int dy = 1; dy >= -1; dy--) {
          BlockPos pos = grid.relative(d, r).above(dy);
          AABB box =
              passenger.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(pos));
          if (level().hasChunkAt(pos)
              && level().noCollision(passenger, box)
              && level()
                  .getBlockState(pos.below())
                  .isFaceSturdy(level(), pos.below(), Direction.UP))
            return Vec3.atBottomCenterOf(pos);
        }
    return new Vec3(getX(), getY() + 2.1, getZ());
  }

  @Override
  protected void removePassenger(Entity passenger) {
    super.removePassenger(passenger);
    if (!level().isClientSide && !loading) halt("Driver dismounted — job paused");
  }

  @Override
  public boolean isPickable() {
    return !isRemoved();
  }

  @Override
  public boolean canBeCollidedWith() {
    return true;
  }

  @Override
  public boolean canCollideWith(Entity other) {
    return !isPassengerOfSameVehicle(other) && other.canBeCollidedWith();
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    if (level().isClientSide
        || isRemoved()
        || !(source.getEntity() instanceof Player p)
        || !owns(p)) return false;
    if (!locked() && getPassengers().isEmpty() && p.isShiftKeyDown()) {
      recover();
      return true;
    }
    return false;
  }

  public boolean pack(Player p) {
    if (recovered || level().isClientSide || !owns(p) || p.isSpectator() || !p.mayBuild())
      return false;
    if (getPassengers().stream().anyMatch(e -> e instanceof Player)) {
      p.displayClientMessage(Component.literal("Dismount before packing The Machine"), true);
      return false;
    }
    halt("Packed");
    MachineOperator.release(this);
    ItemStack packed = new ItemStack(MachineParts.PACKED.get());
    CompoundTag data = new CompoundTag();
    addAdditionalSaveData(data);
    data.putString("Dimension", level().dimension().location().toString());
    packed.getOrCreateTag().put("Machine", data);
    if (!p.getInventory().add(packed)) p.drop(packed, false);
    recovered = true;
    discard();
    p.displayClientMessage(
        Component.literal("Machine packed — place on clear, solid ground"), true);
    return true;
  }

  public void recover() {
    if (recovered || level().isClientSide) return;
    recovered = true;
    halt("Dismantled");
    MachineOperator.release(this);
    for (int i = 0; i < cargo.getSlots(); i++) {
      spawnAtLocation(cargo.getStackInSlot(i));
      cargo.setStackInSlot(i, ItemStack.EMPTY);
    }
    for (int i = 0; i < equipment.getSlots(); i++) {
      spawnAtLocation(equipment.getStackInSlot(i));
      equipment.setStackInSlot(i, ItemStack.EMPTY);
    }
    for (var e :
        Map.of(
                "machine_track",
                6,
                "machine_frame",
                10,
                "cargo_compartment",
                4,
                "attachment_mount",
                1,
                "operator_station",
                1,
                "lumina_engine",
                1)
            .entrySet())
      spawnAtLocation(new ItemStack(MachineParts.item(e.getKey()), e.getValue()));
    ItemStack tank = new ItemStack(Luminall.TANK_ITEM.get());
    // Remaining engine energy is returned as a reusable fuel capsule carried by the Tank.
    tankData.putInt("MachineFuelThirds", fuel + tankData.getInt("MachineFuelThirds"));
    tankData.putDouble(
        "MachineFuelFraction", fuelFraction + tankData.getDouble("MachineFuelFraction"));
    BlockItem.setBlockEntityData(tank, Luminall.TANK_BE.get(), tankData);
    spawnAtLocation(tank);
    discard();
  }

  @Override
  public void remove(RemovalReason reason) {
    if (reason.shouldDestroy() && !level().isClientSide && !recovered && equipment != null) {
      recover();
      return;
    }
    super.remove(reason);
  }

  @Override
  public void lerpTo(
      double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
    lerpX = x;
    lerpY = y;
    lerpZ = z;
    lerpYaw = yaw;
    lerpSteps = Math.max(1, Math.min(3, steps));
  }

  @Override
  public Packet<ClientGamePacketListener> getAddEntityPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
    return cap == ForgeCapabilities.ITEM_HANDLER
        ? inventory.cast()
        : super.getCapability(cap, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    inventory.invalidate();
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag n) {
    if (owner != null) n.putUUID("Owner", owner);
    if (homeId != null) {
      n.putUUID("HomeId", homeId);
      n.putLong("Home", home.asLong());
    }
    if (operator != null) n.putUUID("Operator", operator);
    n.putLong("Grid", grid.asLong());
    n.putInt("Heading", heading.get2DDataValue());
    n.putInt("Fuel", fuel);
    n.putDouble("FuelFraction", fuelFraction);
    n.putString("Engine", engine.name());
    n.putString("Mode", mode.name());
    n.putString("Status", status);
    n.put("Cargo", cargo.serializeNBT());
    n.put("Equipment", equipment.serializeNBT());
    n.put("TankData", tankData.copy());
    n.put("Job", job.save());
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag n) {
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    homeId = n.hasUUID("HomeId") ? n.getUUID("HomeId") : null;
    home = homeId == null ? null : BlockPos.of(n.getLong("Home"));
    operator = n.hasUUID("Operator") ? n.getUUID("Operator") : null;
    grid = BlockPos.of(n.getLong("Grid"));
    heading = Direction.from2DDataValue(n.getInt("Heading"));
    fuel = Math.max(0, n.getInt("Fuel"));
    fuelFraction = fraction(n.getDouble("FuelFraction"));
    try {
      mode = Mode.valueOf(n.getString("Mode"));
    } catch (IllegalArgumentException e) {
      mode = Mode.MANUAL;
    }
    cargo.deserializeNBT(n.getCompound("Cargo"));
    equipment.deserializeNBT(n.getCompound("Equipment"));
    tankData = n.getCompound("TankData").copy();
    job.load(n.getCompound("Job"));
    // A saved incomplete visual movement rolls back to the last completed grid step.
    job.active = false;
    engine = Engine.OFF;
    status = "Saved job paused — Resume when ready";
    snap();
    sync();
  }
}
