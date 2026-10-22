package dev.automatons;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.ItemStackHandler;

public final class Inventories {
  public static final TagKey<Item> AXES = TagKey.create(Registries.ITEM, Automatons.id("axes"));

  public static boolean axe(ItemStack s) {
    return !s.isEmpty()
        && (s.getItem() instanceof AxeItem || s.is(AXES))
        && (!s.isDamageableItem() || s.getDamageValue() < s.getMaxDamage());
  }

  public static class Tools extends ItemStackHandler {
    private final Runnable changed;
    private final java.util.function.BooleanSupplier enabled;

    public Tools(Runnable changed) {
      this(changed, () -> true);
    }

    public Tools(Runnable changed, java.util.function.BooleanSupplier enabled) {
      super(3);
      this.changed = changed;
      this.enabled = enabled;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack s) {
      return enabled.getAsBoolean() && axe(s);
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
    return Math.max(
        10,
        Math.round(
            (float)
                (base
                    * 6.0
                    / Math.max(1, quality + (efficiency > 0 ? efficiency * efficiency + 1 : 0))
                    * Math.max(.2, 1 - .2 * speed))));
  }
}
