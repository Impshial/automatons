package dev.automatons;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

public final class ReservoirItem extends BlockItem {
  public ReservoirItem(Block block) {
    super(block, new Properties().stacksTo(1));
  }

  @Override
  public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
    if (getBlock() != Luminall.TANK.get()) return super.initCapabilities(stack, nbt);
    IFluidHandlerItem handler =
        new IFluidHandlerItem() {
          private FluidStack stored() {
            CompoundTag data = getBlockEntityData(stack);
            return data == null
                ? FluidStack.EMPTY
                : FluidStack.loadFluidStackFromNBT(data.getCompound("Tank"));
          }

          private void store(FluidStack fluid) {
            CompoundTag data = getBlockEntityData(stack);
            data = data == null ? new CompoundTag() : data.copy();
            data.put("Tank", fluid.writeToNBT(new CompoundTag()));
            setBlockEntityData(stack, Luminall.TANK_BE.get(), data);
          }

          public ItemStack getContainer() {
            return stack;
          }

          public int getTanks() {
            return 1;
          }

          public FluidStack getFluidInTank(int index) {
            return index == 0 ? stored() : FluidStack.EMPTY;
          }

          public int getTankCapacity(int index) {
            return Balance.TANK_CAPACITY.get();
          }

          public boolean isFluidValid(int index, FluidStack fluid) {
            return index == 0 && !fluid.isEmpty();
          }

          public int fill(FluidStack fluid, FluidAction action) {
            FluidStack current = stored();
            if (fluid.isEmpty() || !current.isEmpty() && !current.isFluidEqual(fluid)) return 0;
            int moved =
                Math.max(0, Math.min(fluid.getAmount(), getTankCapacity(0) - current.getAmount()));
            if (action.execute() && moved > 0)
              store(new FluidStack(fluid, current.getAmount() + moved));
            return moved;
          }

          public FluidStack drain(FluidStack fluid, FluidAction action) {
            return stored().isFluidEqual(fluid)
                ? drain(fluid.getAmount(), action)
                : FluidStack.EMPTY;
          }

          public FluidStack drain(int maximum, FluidAction action) {
            FluidStack current = stored();
            int moved = Math.max(0, Math.min(maximum, current.getAmount()));
            if (moved == 0) return FluidStack.EMPTY;
            FluidStack result = new FluidStack(current, moved);
            if (action.execute()) {
              current.shrink(moved);
              store(current);
            }
            return result;
          }
        };
    return new ICapabilityProvider() {
      final LazyOptional<IFluidHandlerItem> capability = LazyOptional.of(() -> handler);

      public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.FLUID_HANDLER_ITEM
            ? capability.cast()
            : LazyOptional.empty();
      }
    };
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> text, TooltipFlag flag) {
    text.add(
        Component.translatable(
            getBlock() == Luminall.COLLECTOR.get()
                ? "tooltip.automatons.collector"
                : "tooltip.automatons.tank"));
    var data = getBlockEntityData(stack);
    if (data != null) {
      if (data.getInt("MachineFuelThirds") > 0)
        text.add(
            Component.literal(
                "Stored Machine fuel: " + data.getInt("MachineFuelThirds") / 3.0 + " Lumina"));
      FluidStack stored = FluidStack.loadFluidStackFromNBT(data.getCompound("Tank"));
      if (!stored.isEmpty())
        text.add(Component.literal(stored.getAmount() + " mB ").append(stored.getDisplayName()));
      ItemStack bottle = ItemStack.of(data.getCompound("Bottle"));
      if (!bottle.isEmpty())
        text.add(
            Component.translatable(
                "tooltip.automatons.stored_bottle", LuminallBottle.amount(bottle)));
    }
  }
}
