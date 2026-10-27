package dev.automatons.compat.jer;

import dev.automatons.*;
import jeresources.jei.JEIConfig;
import jeresources.jei.worldgen.WorldGenWrapper;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Labels JER's native entry via JEI's public decorator API, without replacing its graph. */
public final class JerEstimateDecorator implements IRecipeCategoryDecorator<WorldGenWrapper> {

  public static void register(IAdvancedRegistration registration) {
    registration.addRecipeCategoryDecorator(JEIConfig.WORLD_GEN_TYPE, new JerEstimateDecorator());
  }

  private boolean lumite(WorldGenWrapper recipe) {
    return recipe.getBlocks().stream()
        .anyMatch(
            s ->
                s.is(Automatons.LUMITE_ORE.get().asItem())
                    || s.is(Automatons.DEEPSLATE_LUMITE_ORE.get().asItem()));
  }

  @Override
  public void draw(
      WorldGenWrapper recipe,
      IRecipeCategory<WorldGenWrapper> category,
      IRecipeSlotsView slots,
      GuiGraphics graphics,
      double mouseX,
      double mouseY) {
    if (lumite(recipe))
      graphics.drawString(
          Minecraft.getInstance().font,
          Component.translatable("jer.automatons.estimate_short"),
          29,
          70,
          0xff423153,
          false);
  }

  @Override
  public void decorateTooltips(
      ITooltipBuilder tooltip,
      WorldGenWrapper recipe,
      IRecipeCategory<WorldGenWrapper> category,
      IRecipeSlotsView slots,
      double mouseX,
      double mouseY) {
    if (!lumite(recipe)) return;
    var config =
        LumiteDefaults.read(
            recipe.getBlocks().stream()
                .anyMatch(s -> s.is(Automatons.DEEPSLATE_LUMITE_ORE.get().asItem())));
    tooltip.add(Component.translatable("jer.automatons.estimate"));
    tooltip.add(
        Component.translatable(
            "jer.automatons.settings",
            config.minimum(),
            config.maximum(),
            config.attempts(),
            config.ore().size));
    tooltip.add(Component.translatable("jer.automatons.tool"));
    tooltip.add(Component.translatable("jer.automatons.unenchanted"));
    tooltip.add(Component.translatable("jer.automatons.silk"));
  }
}
