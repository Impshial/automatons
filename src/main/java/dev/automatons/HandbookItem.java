package dev.automatons;

import guideme.GuidesCommon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The server sends one GuideME open packet; the book is never consumed. */
public final class HandbookItem extends Item {
  public static final ResourceLocation GUIDE_ID = Automatons.id("handbook");

  public HandbookItem() {
    super(new Item.Properties().stacksTo(1));
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (!level.isClientSide) GuidesCommon.openGuide(player, GUIDE_ID);
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }
}
