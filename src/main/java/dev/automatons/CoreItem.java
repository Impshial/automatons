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
    return stack.is(Automatons.LUMINA_CORE.get())
        ? CAPACITY
        : Math.min(
            CAPACITY - 1, Math.max(0, stack.hasTag() ? LegacyMagic.coreCredit(stack.getTag()) : 0));
  }

  public static ItemStack vessel(int charge) {
    if (charge >= CAPACITY) return new ItemStack(Automatons.LUMINA_CORE.get());
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
    if (charge(s) > 0)
      lines.add(Component.literal("Legacy infusion credit: " + charge(s) + " Lumina"));
  }

  @Override
  public net.minecraft.world.InteractionResultHolder<ItemStack> use(
      Level level,
      net.minecraft.world.entity.player.Player player,
      net.minecraft.world.InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    int credit = charge(stack);
    if (credit == 0 || !stack.is(Automatons.EMPTY_CORE.get()))
      return net.minecraft.world.InteractionResultHolder.pass(stack);
    if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer p) {
      if (!stack.getTag().hasUUID("CreditOwner")
          || !stack.getTag().getUUID("CreditOwner").equals(p.getUUID())) {
        p.displayClientMessage(
            Component.literal(
                "Legacy infusion credit preserved; a matching donor identity is required"),
            true);
      } else if (Lumina.thirds(p) <= Integer.MAX_VALUE * 3L + 2 - credit * 3L) {
        stack.getTag().remove("Magica");
        stack.getTag().remove("CreditOwner");
        if (stack.getTag().isEmpty()) stack.setTag(null);
        Lumina.addThirds(p, credit * 3L);
        p.displayClientMessage(Component.literal("Recovered " + credit + " Lumina"), true);
      }
    }
    return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
