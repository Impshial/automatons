package dev.automatons;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;

/** Builder equipment; a player's emergency wooden-tier pick with half bare-hand base speed. */
public final class HammerItem extends PickaxeItem {
  public HammerItem() {
    super(Tiers.WOOD, 1, -2.8f, new Item.Properties().durability(2000));
  }

  @Override
  public float getDestroySpeed(ItemStack stack, BlockState state) {
    return .5f;
  }
}
