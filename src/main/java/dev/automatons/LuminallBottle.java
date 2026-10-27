package dev.automatons;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

/** Each bottle keeps its fill volume and reward, including across config changes. */
public final class LuminallBottle extends Item {
  public LuminallBottle() {
    super(new Properties().stacksTo(1));
  }

  public static ItemStack create(int amount) {
    ItemStack stack = new ItemStack(Luminall.BOTTLE.get());
    stack.getOrCreateTag().putInt("Volume", Balance.BOTTLE_VOLUME.get());
    stack.getOrCreateTag().putInt("Lumina", Balance.BOTTLE_LUMINA.get());
    setAmount(stack, amount);
    return stack;
  }

  public static int volume(ItemStack stack) {
    return stack.hasTag() && stack.getTag().contains("Volume")
        ? Math.max(1, Math.min(1000, stack.getTag().getInt("Volume")))
        : Balance.BOTTLE_VOLUME.get();
  }

  public static int amount(ItemStack stack) {
    if (!stack.is(Luminall.BOTTLE.get())) return 0;
    return stack.hasTag() && stack.getTag().contains("Amount")
        ? Math.max(0, Math.min(volume(stack), stack.getTag().getInt("Amount")))
        : volume(stack);
  }

  public static int reward(ItemStack stack) {
    return stack.hasTag() && stack.getTag().contains("Lumina")
        ? Math.max(1, Math.min(1000, stack.getTag().getInt("Lumina")))
        : Balance.BOTTLE_LUMINA.get();
  }

  public static void setAmount(ItemStack stack, int amount) {
    int volume = volume(stack), reward = reward(stack);
    stack.getOrCreateTag().putInt("Volume", volume);
    stack.getOrCreateTag().putInt("Lumina", reward);
    stack.getOrCreateTag().putInt("Amount", Math.max(0, Math.min(volume, amount)));
  }

  @Override
  public int getUseDuration(ItemStack stack) {
    return 32;
  }

  @Override
  public UseAnim getUseAnimation(ItemStack stack) {
    return UseAnim.DRINK;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (amount(stack) == 0) return InteractionResultHolder.fail(stack);
    if (player instanceof ServerPlayer p && !canDrink(p, stack))
      return InteractionResultHolder.fail(stack);
    return ItemUtils.startUsingInstantly(level, player, hand);
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
    if (entity instanceof ServerPlayer player && amount(stack) > 0 && canDrink(player, stack)) {
      Ledger ledger = Ledger.get(player.server);
      double total = energy(stack) + ledger.bottleFractions.getOrDefault(player.getUUID(), 0d);
      long whole = (long) Math.floor(total + 1e-9);
      ledger.bottleFractions.put(player.getUUID(), Math.max(0, total - whole));
      Lumina.addThirds(player, whole);
      player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
      // Drinking always empties the vessel, including in creative mode.
      return new ItemStack(Items.GLASS_BOTTLE);
    }
    return stack;
  }

  private static double energy(ItemStack stack) {
    return amount(stack) * (double) reward(stack) * 3 / volume(stack);
  }

  private static boolean canDrink(ServerPlayer player, ItemStack stack) {
    return energy(stack)
            + Ledger.get(player.server).bottleFractions.getOrDefault(player.getUUID(), 0d)
        <= Integer.MAX_VALUE * 3L + 2 - Lumina.thirds(player) + 1e-9;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
    lines.add(
        Component.translatable("tooltip.automatons.fluid_amount", amount(stack), volume(stack)));
    lines.add(Component.translatable("tooltip.automatons.luminall_bottle", reward(stack)));
  }

  @Override
  public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
    IFluidHandlerItem handler =
        new IFluidHandlerItem() {
          public ItemStack getContainer() {
            return amount(stack) == 0 ? new ItemStack(Items.GLASS_BOTTLE) : stack;
          }

          public int getTanks() {
            return 1;
          }

          public FluidStack getFluidInTank(int tank) {
            return new FluidStack(Luminall.SOURCE.get(), amount(stack));
          }

          public int getTankCapacity(int tank) {
            return volume(stack);
          }

          public boolean isFluidValid(int tank, FluidStack fluid) {
            return fluid.getFluid().isSame(Luminall.SOURCE.get());
          }

          public int fill(FluidStack fluid, FluidAction action) {
            if (!isFluidValid(0, fluid)) return 0;
            int moved = Math.min(volume(stack) - amount(stack), fluid.getAmount());
            if (action.execute() && moved > 0) setAmount(stack, amount(stack) + moved);
            return moved;
          }

          public FluidStack drain(FluidStack fluid, FluidAction action) {
            return isFluidValid(0, fluid) ? drain(fluid.getAmount(), action) : FluidStack.EMPTY;
          }

          public FluidStack drain(int maximum, FluidAction action) {
            int moved = Math.max(0, Math.min(amount(stack), maximum));
            if (action.execute() && moved > 0) setAmount(stack, amount(stack) - moved);
            return new FluidStack(Luminall.SOURCE.get(), moved);
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
}
