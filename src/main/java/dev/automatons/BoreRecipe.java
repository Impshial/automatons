package dev.automatons;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class BoreRecipe extends ShapedRecipe {
  public BoreRecipe(ShapedRecipe recipe) {
    super(
        recipe.getId(),
        recipe.getGroup(),
        recipe.category(),
        recipe.getWidth(),
        recipe.getHeight(),
        recipe.getIngredients(),
        recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY),
        recipe.showNotification());
  }

  @Override
  public boolean matches(CraftingContainer input, Level level) {
    if (!super.matches(input, level)) return false;
    for (int i = 0; i < input.getContainerSize(); i++) {
      ItemStack s = input.getItem(i);
      if (s.is(Items.DIAMOND_PICKAXE) && s.isDamaged()) return false;
    }
    return true;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return SERIALIZER.get();
  }

  public static final net.minecraftforge.registries.RegistryObject<RecipeSerializer<BoreRecipe>>
      SERIALIZER =
          Automatons.SERIALIZERS.register(
              "bore",
              () ->
                  new RecipeSerializer<>() {
                    private final ShapedRecipe.Serializer shaped = new ShapedRecipe.Serializer();

                    public BoreRecipe fromJson(ResourceLocation id, JsonObject json) {
                      return new BoreRecipe(shaped.fromJson(id, json));
                    }

                    public BoreRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
                      return new BoreRecipe(shaped.fromNetwork(id, buf));
                    }

                    public void toNetwork(FriendlyByteBuf buf, BoreRecipe recipe) {
                      shaped.toNetwork(buf, recipe);
                    }
                  });

  public static void register() {}
}
