package dev.automatons.client;

import dev.automatons.*;
import guideme.Guide;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;
import net.minecraft.network.chat.Component;

/** Client registration only; common item/server code never loads GuideME's layout classes. */
public final class Handbook {
  private static Guide guide;

  private Handbook() {}

  public static void register() {
    if (guide != null) throw new IllegalStateException("Handbook registered twice");
    guide =
        Guide.builder(HandbookItem.GUIDE_ID)
            .extension(
                RecipeTypeMappingSupplier.EXTENSION_POINT,
                mappings ->
                    mappings.add(
                        Automatons.CREATION.get(),
                        recipe -> {
                          var grid = new LytSlotGrid(3, 3);
                          int[][] slots = {{1, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {2, 2}};
                          for (int i = 0; i < slots.length; i++)
                            grid.setIngredient(
                                slots[i][0], slots[i][1], recipe.components().get(i));
                          return LytStandardRecipeBox.builder()
                              .title(
                                  Component.translatable("guide.automatons.assembly").getString())
                              .icon(Automatons.PEDESTAL.get())
                              .input(grid)
                              .outputFromResultOf(recipe)
                              .build(recipe);
                        }))
            .build();
  }
}
