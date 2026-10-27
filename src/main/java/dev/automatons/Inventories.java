package dev.automatons;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.ItemStackHandler;

public final class Inventories {
  public static final TagKey<Item> AXES = TagKey.create(Registries.ITEM, Automatons.id("axes"));
  public static final TagKey<Item> PICKS =
      TagKey.create(Registries.ITEM, Automatons.id("pickaxes"));
  public static final TagKey<Item> SHOVELS =
      TagKey.create(Registries.ITEM, Automatons.id("shovels"));

  public static boolean pick(ItemStack s) {
    return usable(s)
        && !s.is(Automatons.HAMMER.get())
        && (s.getItem() instanceof PickaxeItem
            || s.is(PICKS)
            || s.canPerformAction(net.minecraftforge.common.ToolActions.PICKAXE_DIG));
  }

  public static boolean shovel(ItemStack s) {
    return usable(s)
        && (s.getItem() instanceof ShovelItem
            || s.is(SHOVELS)
            || s.canPerformAction(net.minecraftforge.common.ToolActions.SHOVEL_DIG));
  }

  private static boolean usable(ItemStack s) {
    return !s.isEmpty() && (!s.isDamageableItem() || s.getDamageValue() < s.getMaxDamage());
  }

  public static boolean tool(int job, ItemStack s) {
    if (job == WorkerJob.BUILDINGTON) return usable(s) && s.is(Automatons.HAMMER.get());
    if (job == WorkerJob.FARMINGTON)
      return usable(s)
          && (s.getItem() instanceof HoeItem
              || s.canPerformAction(net.minecraftforge.common.ToolActions.HOE_TILL));
    return job == 1
        ? axe(s)
        : job == 2 ? pick(s) || shovel(s) : job == 3 && (axe(s) || pick(s) || shovel(s));
  }

  public static int cargoSlots(int job) {
    return Balance.CARGO.get() * (job == WorkerJob.RUNNINGTON ? 2 : 1);
  }

  /** Commit a shrink only after a complete metadata-preserving packing simulation. */
  public static boolean resize(ItemStackHandler inventory, int size) {
    if (inventory.getSlots() == size) return true;
    ItemStackHandler packed = new ItemStackHandler(size);
    if (size > inventory.getSlots()) {
      for (int i = 0; i < inventory.getSlots(); i++)
        packed.setStackInSlot(i, inventory.getStackInSlot(i).copy());
    } else {
      for (int i = 0; i < inventory.getSlots(); i++)
        if (!insert(packed, inventory.getStackInSlot(i), false).isEmpty()) return false;
    }
    inventory.deserializeNBT(packed.serializeNBT());
    return true;
  }

  public static boolean axe(ItemStack s) {
    return !s.isEmpty()
        && (s.getItem() instanceof AxeItem || s.is(AXES))
        && (!s.isDamageableItem() || s.getDamageValue() < s.getMaxDamage());
  }

  public static class Tools extends ItemStackHandler {
    private final Runnable changed;
    private final java.util.function.IntSupplier job;

    public Tools(Runnable changed) {
      this(changed, () -> true);
    }

    public Tools(Runnable changed, java.util.function.BooleanSupplier enabled) {
      this(changed, (java.util.function.IntSupplier) () -> enabled.getAsBoolean() ? 1 : 0);
    }

    public Tools(Runnable changed, java.util.function.IntSupplier job) {
      super(3);
      this.changed = changed;
      this.job = job;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack s) {
      return tool(job.getAsInt(), s);
    }

    @Override
    public int getSlotLimit(int slot) {
      return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
      changed.run();
    }
  }

  public static ItemStack insert(ItemStackHandler target, ItemStack stack, boolean simulate) {
    ItemStack rest = stack.copy();
    for (int i = 0; i < target.getSlots() && !rest.isEmpty(); i++)
      rest = target.insertItem(i, rest, simulate);
    return rest;
  }

  public static boolean fits(ItemStackHandler inventory, java.util.List<ItemStack> drops) {
    ItemStackHandler copy = new ItemStackHandler(inventory.getSlots());
    copy.deserializeNBT(inventory.serializeNBT());
    for (ItemStack s : drops) if (!insert(copy, s, false).isEmpty()) return false;
    return true;
  }

  public static int duration(ItemStack axe, int base, int speed, int bonus) {
    float quality = axe.getItem() instanceof TieredItem t ? t.getTier().getSpeed() : 6;
    int efficiency =
        Math.max(
            EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, axe), bonus);
    int extra =
        axe.getItem() instanceof TieredItem t && t.getTier().getLevel() >= Tiers.DIAMOND.getLevel()
            ? 10
            : axe.getItem() instanceof TieredItem t
                    && t.getTier().getLevel() < Tiers.STONE.getLevel()
                ? 0
                : 20;
    double unenchanted = base * 6.0 / Math.max(1, quality) + extra;
    double effective = quality + (efficiency > 0 ? efficiency * efficiency + 1 : 0);
    // Work is scheduled every five ticks. Round to that interval, not a hidden ceiling.
    return Math.max(
        10,
        5
            * Math.round(
                (float)
                    (unenchanted
                        * quality
                        / Math.max(1, effective)
                        * Math.max(.2, 1 - .2 * speed)
                        / 5.0)));
  }
}
