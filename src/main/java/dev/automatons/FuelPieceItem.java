package dev.automatons;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

/** A reversible eighth of the parent fuel; never tagged as a whole coal ingredient. */
public final class FuelPieceItem extends Item {
  private final Item parent;

  public FuelPieceItem(Item parent) {
    super(new Properties());
    this.parent = parent;
  }

  @Override
  public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
    return ForgeHooks.getBurnTime(new ItemStack(parent), recipeType) / 8;
  }
}
