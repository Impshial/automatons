package dev.automatons;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** A partial shard is one physical item. Integer thirds match the saved energy ledger exactly. */
public final class LumiteShardItem extends Item {
  public static final int COST = 25, CAPACITY = COST * 3;

  public LumiteShardItem() {
    super(new Properties());
  }

  public static int paid(ItemStack stack) {
    if (stack.is(Automatons.INFUSED_LUMITE.get())) return CAPACITY;
    return stack.is(Automatons.LUMITE_SHARD.get()) && stack.hasTag()
        ? Math.max(0, Math.min(CAPACITY - 1, stack.getTag().getInt("LuminaPaidThirds")))
        : 0;
  }

  public static ItemStack charge(ItemStack stack, int amount) {
    int next = paid(stack) + amount;
    if (next >= CAPACITY) {
      ItemStack result = new ItemStack(Automatons.INFUSED_LUMITE.get());
      if (stack.hasTag()) result.setTag(stack.getTag().copy());
      if (result.hasTag()) {
        result.getTag().remove("LuminaPaidThirds");
        if (result.getTag().isEmpty()) result.setTag(null);
      }
      return result;
    }
    ItemStack result = stack.copyWithCount(1);
    if (next > 0) result.getOrCreateTag().putInt("LuminaPaidThirds", next);
    return result;
  }

  @Override
  public int getMaxStackSize(ItemStack stack) {
    return paid(stack) > 0 ? 1 : 64;
  }

  @Override
  public boolean isBarVisible(ItemStack stack) {
    return paid(stack) > 0;
  }

  @Override
  public int getBarWidth(ItemStack stack) {
    return Math.round(13f * paid(stack) / CAPACITY);
  }

  @Override
  public int getBarColor(ItemStack stack) {
    return 0xc4a0ff;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    int paid = paid(stack);
    if (paid > 0) lines.add(Component.literal(Lumina.format(paid / 3, paid % 3) + "/25 Lumina"));
  }
}
