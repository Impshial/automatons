package dev.automatons;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public final class Reservoir extends BlockEntity {
  public final boolean collector;
  public ItemStack bottle = ItemStack.EMPTY;
  public int machineFuelThirds;
  public double machineFuelFraction;
  public long lastTick = Long.MIN_VALUE, lastCollection = Long.MIN_VALUE;
  public BlockPos lastProducer;
  private boolean dirtyVisuals;
  public final FluidTank tank;
  private LazyOptional<IFluidHandler> capability;

  public Reservoir(BlockPos pos, BlockState state) {
    super(
        state.is(Luminall.COLLECTOR.get()) ? Luminall.COLLECTOR_BE.get() : Luminall.TANK_BE.get(),
        pos,
        state);
    collector = state.is(Luminall.COLLECTOR.get());
    tank =
        new FluidTank(
            capacity(), fluid -> !collector || fluid.getFluid().isSame(Luminall.SOURCE.get())) {
          @Override
          public int fill(FluidStack resource, FluidAction action) {
            // Retain saved contents if a server lowers capacity; accept nothing until below the
            // limit.
            setCapacity(capacity());
            return getSpace() <= 0 ? 0 : super.fill(resource, action);
          }

          @Override
          protected void onContentsChanged() {
            changed();
          }
        };
    capability = LazyOptional.of(() -> collector ? extraction : tank);
  }

  public int capacity() {
    return (collector ? Balance.COLLECTOR_CAPACITY : Balance.TANK_CAPACITY).get();
  }

  public int space() {
    return Math.max(0, capacity() - tank.getFluidAmount());
  }

  public void changed() {
    setChanged();
    dirtyVisuals = true;
  }

  public void synchronize() {
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    dirtyVisuals = false;
  }

  private final IFluidHandler extraction =
      new IFluidHandler() {
        public int getTanks() {
          return 1;
        }

        public FluidStack getFluidInTank(int index) {
          return tank.getFluid().copy();
        }

        public int getTankCapacity(int index) {
          return capacity();
        }

        public boolean isFluidValid(int index, FluidStack stack) {
          return false;
        }

        public int fill(FluidStack stack, FluidAction action) {
          return 0;
        }

        public FluidStack drain(FluidStack stack, FluidAction action) {
          return tank.drain(stack, action);
        }

        public FluidStack drain(int amount, FluidAction action) {
          return tank.drain(Math.max(0, amount), action);
        }
      };

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
    if (cap == ForgeCapabilities.FLUID_HANDLER && (!collector || side != Direction.UP))
      return capability.cast();
    return super.getCapability(cap, side);
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    capability.invalidate();
  }

  @Override
  public void reviveCaps() {
    super.reviveCaps();
    capability = LazyOptional.of(() -> collector ? extraction : tank);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    if (collector) LuminaCollection.loaded(this);
  }

  @Override
  public void setRemoved() {
    LuminaCollection.unloaded(this);
    super.setRemoved();
  }

  @Override
  public void onChunkUnloaded() {
    LuminaCollection.unloaded(this);
    super.onChunkUnloaded();
  }

  public static void tick(Level level, BlockPos pos, BlockState state, Reservoir machine) {
    if (level.isClientSide) return;
    machine.lastTick = level.getGameTime();
    if (machine.collector && !machine.bottle.isEmpty()) machine.fillBottle();
    if (machine.dirtyVisuals && level.getGameTime() % 5 == 0) machine.synchronize();
  }

  public void fillBottle() {
    if (!bottle.is(Items.GLASS_BOTTLE) && !bottle.is(Luminall.BOTTLE.get())) return;
    if (bottle.is(Items.GLASS_BOTTLE)) {
      if (tank.getFluidAmount() == 0) return;
      bottle = LuminallBottle.create(0);
    }
    int missing = LuminallBottle.volume(bottle) - LuminallBottle.amount(bottle);
    int moved =
        tank.drain(
                new FluidStack(Luminall.SOURCE.get(), Math.min(5, missing)),
                IFluidHandler.FluidAction.EXECUTE)
            .getAmount();
    if (moved > 0) {
      LuminallBottle.setAmount(bottle, LuminallBottle.amount(bottle) + moved);
      changed();
    }
  }

  @Override
  protected void saveAdditional(CompoundTag n) {
    super.saveAdditional(n);
    n.put("Tank", tank.writeToNBT(new CompoundTag()));
    n.put("Bottle", bottle.save(new CompoundTag()));
    n.putInt("MachineFuelThirds", machineFuelThirds);
    n.putDouble("MachineFuelFraction", machineFuelFraction);
  }

  @Override
  public void load(CompoundTag n) {
    super.load(n);
    tank.readFromNBT(
        n.getCompound("Tank")); // Do not clamp away paid contents after config changes.
    bottle = ItemStack.of(n.getCompound("Bottle"));
    machineFuelThirds = Math.max(0, n.getInt("MachineFuelThirds"));
    double fraction = n.getDouble("MachineFuelFraction");
    machineFuelFraction =
        Double.isFinite(fraction) ? Math.max(0, Math.min(.999999999, fraction)) : 0;
    lastCollection = n.contains("LastCollection") ? n.getLong("LastCollection") : Long.MIN_VALUE;
    lastProducer = n.contains("LastProducer") ? BlockPos.of(n.getLong("LastProducer")) : null;
  }

  @Override
  public CompoundTag getUpdateTag() {
    var n = saveWithoutMetadata();
    n.putLong("LastCollection", lastCollection);
    if (lastProducer != null) n.putLong("LastProducer", lastProducer.asLong());
    return n;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public AABB getRenderBoundingBox() {
    return new AABB(worldPosition).inflate(collector ? 6 : 0);
  }
}
