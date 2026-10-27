package dev.automatons;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Preserve stored fluid: empty the Tank before using it as a crafting ingredient. */
public final class CollectorRecipe extends ShapedRecipe {
  public CollectorRecipe(ShapedRecipe recipe) {
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
      var stack = input.getItem(i);
      if (!stack.is(Luminall.TANK_ITEM.get())) continue;
      var data = BlockItem.getBlockEntityData(stack);
      if (data != null
          && (data.getInt("MachineFuelThirds") > 0 || data.getCompound("Tank").getInt("Amount") > 0
              || !net.minecraft.world.item.ItemStack.of(data.getCompound("Bottle")).isEmpty()))
        return false;
    }
    return true;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return SERIALIZER.get();
  }

  public static final net.minecraftforge.registries.RegistryObject<
          RecipeSerializer<CollectorRecipe>>
      SERIALIZER =
          Automatons.SERIALIZERS.register(
              "collector",
              () ->
                  new RecipeSerializer<>() {
                    private final ShapedRecipe.Serializer shaped = new ShapedRecipe.Serializer();

                    public CollectorRecipe fromJson(ResourceLocation id, JsonObject json) {
                      return new CollectorRecipe(shaped.fromJson(id, json));
                    }

                    public CollectorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
                      return new CollectorRecipe(shaped.fromNetwork(id, buf));
                    }

                    public void toNetwork(FriendlyByteBuf buf, CollectorRecipe recipe) {
                      shaped.toNetwork(buf, recipe);
                    }
                  });

  public static void register() {}
}
