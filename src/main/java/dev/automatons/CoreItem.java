package dev.automatons;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class CoreItem extends Item {
  public static final int CAPACITY = 50;

  public CoreItem() {
    super(new Properties());
  }

  public static int charge(ItemStack stack) {
    return stack.is(Automatons.MAGICA_CORE.get())
        ? CAPACITY
        : Math.min(CAPACITY - 1, Math.max(0, stack.hasTag() ? stack.getTag().getInt("Magica") : 0));
  }

  public static ItemStack vessel(int charge) {
    if (charge >= CAPACITY) return new ItemStack(Automatons.MAGICA_CORE.get());
    ItemStack s = new ItemStack(Automatons.EMPTY_CORE.get());
    if (charge > 0) s.getOrCreateTag().putInt("Magica", Math.min(CAPACITY - 1, charge));
    return s;
  }

  @Override
  public int getMaxStackSize(ItemStack stack) {
    return charge(stack) > 0 ? 1 : 64;
  }

  @Override
  public void appendHoverText(ItemStack s, Level l, List<Component> lines, TooltipFlag flag) {
    lines.add(Component.literal(charge(s) + "/50 Magica"));
  }

  @Override
  public boolean isBarVisible(ItemStack s) {
    return charge(s) > 0;
  }

  @Override
  public int getBarWidth(ItemStack s) {
    return Math.round(13f * charge(s) / CAPACITY);
  }

  @Override
  public int getBarColor(ItemStack s) {
    return 0xc4a0ff;
  }
}
