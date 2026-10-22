package dev.automatons.compat.jei;

import dev.automatons.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class AssemblyCategory implements IRecipeCategory<CreationRecipe> {
  private final IDrawable icon;
  private static final int[][] POSITIONS = {
    {52, 4}, {28, 28}, {52, 28}, {76, 28}, {40, 52}, {64, 52}
  };

  AssemblyCategory(IGuiHelper gui) {
    icon =
        gui.createDrawableIngredient(
            VanillaTypes.ITEM_STACK, new ItemStack(Automatons.PEDESTAL.get()));
  }

  @Override
  public RecipeType<CreationRecipe> getRecipeType() {
    return AutomatonsJeiPlugin.ASSEMBLY;
  }

  @Override
  public Component getTitle() {
    return Component.translatable("jei.automatons.assembly");
  }

  @Override
  public int getWidth() {
    return 170;
  }

  @Override
  public int getHeight() {
    return 108;
  }

  @Override
  public IDrawable getIcon() {
    return icon;
  }

  @Override
  public ResourceLocation getRegistryName(CreationRecipe recipe) {
    return recipe.id();
  }

  @Override
  public void setRecipe(IRecipeLayoutBuilder builder, CreationRecipe recipe, IFocusGroup focuses) {
    for (int i = 0; i < 6; i++)
      builder
          .addSlot(RecipeIngredientRole.INPUT, POSITIONS[i][0], POSITIONS[i][1])
          .setStandardSlotBackground()
          .addIngredients(recipe.components().get(i));
    // This is an identity-free preview. The server still creates ownership only on pickup.
    builder
        .addSlot(RecipeIngredientRole.OUTPUT, 140, 28)
        .setOutputSlotBackground()
        .addItemStack(new ItemStack(Automatons.AUTOMATON_ITEM.get()));
  }

  @Override
  public void draw(
      CreationRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double x, double y) {
    arrow(g, 104, 35);
    var font = Minecraft.getInstance().font;
    g.drawWordWrap(
        font, Component.translatable("jei.automatons.assembly.help"), 4, 78, 162, 0xff444444);
  }

  static void arrow(GuiGraphics g, int x, int y) {
    g.fill(x, y, x + 20, y + 3, 0xff9272ad);
    for (int i = 0; i < 5; i++) g.fill(x + 19 + i, y - 4 + i, x + 20 + i, y + 7 - i, 0xff9272ad);
  }
}
