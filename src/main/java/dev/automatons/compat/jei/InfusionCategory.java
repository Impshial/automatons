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
import net.minecraft.world.item.ItemStack;

final class InfusionCategory implements IRecipeCategory<InfusionCategory.Display> {
  record Display() {}

  private final IDrawable icon;

  InfusionCategory(IGuiHelper gui) {
    icon =
        gui.createDrawableIngredient(
            VanillaTypes.ITEM_STACK, new ItemStack(Automatons.INFUSER.get()));
  }

  @Override
  public RecipeType<Display> getRecipeType() {
    return AutomatonsJeiPlugin.INFUSION;
  }

  @Override
  public Component getTitle() {
    return Component.translatable("jei.automatons.infusion");
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
  public void setRecipe(IRecipeLayoutBuilder builder, Display recipe, IFocusGroup focuses) {
    builder
        .addSlot(RecipeIngredientRole.INPUT, 26, 16)
        .setStandardSlotBackground()
        .addItemStack(new ItemStack(Automatons.LUMITE_SHARD.get()));
    builder
        .addSlot(RecipeIngredientRole.OUTPUT, 130, 16)
        .setOutputSlotBackground()
        .addItemStack(new ItemStack(Automatons.INFUSED_LUMITE.get()));
  }

  @Override
  public void draw(Display recipe, IRecipeSlotsView slots, GuiGraphics g, double x, double y) {
    AssemblyCategory.arrow(g, 74, 23);
    var font = Minecraft.getInstance().font;
    Component cost = Component.translatable("jei.automatons.infusion.cost", LumiteShardItem.COST);
    g.drawString(font, cost, (170 - font.width(cost)) / 2, 46, 0xff6d408d, false);
    g.drawWordWrap(
        font,
        Component.translatable("jei.automatons.infusion.help", Balance.INFUSE_RANGE.get()),
        4,
        62,
        162,
        0xff444444);
  }
}
