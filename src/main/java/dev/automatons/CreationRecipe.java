package dev.automatons;

import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public record CreationRecipe(
    ResourceLocation id,
    Ingredient wood,
    int woodCount,
    Ingredient metal,
    int metalCount,
    Ingredient core,
    Ingredient tool)
    implements Recipe<SimpleContainer> {
  @Override
  public boolean matches(SimpleContainer c, Level l) {
    return wood.test(c.getItem(0))
        && c.getItem(0).getCount() >= woodCount
        && metal.test(c.getItem(1))
        && c.getItem(1).getCount() >= metalCount
        && core.test(c.getItem(2))
        && tool.test(c.getItem(3))
        && Inventories.axe(c.getItem(3));
  }

  @Override
  public ItemStack assemble(SimpleContainer c, RegistryAccess a) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean canCraftInDimensions(int w, int h) {
    return w * h >= 4;
  }

  @Override
  public ItemStack getResultItem(RegistryAccess a) {
    return ItemStack.EMPTY;
  }

  @Override
  public ResourceLocation getId() {
    return id;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return Automatons.CREATION_SERIALIZER.get();
  }

  @Override
  public RecipeType<?> getType() {
    return Automatons.CREATION.get();
  }

  public static class Serializer implements RecipeSerializer<CreationRecipe> {
    public CreationRecipe fromJson(ResourceLocation id, JsonObject j) {
      return new CreationRecipe(
          id,
          Ingredient.fromJson(j.get("wood")),
          j.get("wood_count").getAsInt(),
          Ingredient.fromJson(j.get("metal")),
          j.get("metal_count").getAsInt(),
          Ingredient.fromJson(j.get("core")),
          Ingredient.fromJson(j.get("tool")));
    }

    public CreationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf b) {
      return new CreationRecipe(
          id,
          Ingredient.fromNetwork(b),
          b.readVarInt(),
          Ingredient.fromNetwork(b),
          b.readVarInt(),
          Ingredient.fromNetwork(b),
          Ingredient.fromNetwork(b));
    }

    public void toNetwork(FriendlyByteBuf b, CreationRecipe r) {
      r.wood.toNetwork(b);
      b.writeVarInt(r.woodCount);
      r.metal.toNetwork(b);
      b.writeVarInt(r.metalCount);
      r.core.toNetwork(b);
      r.tool.toNetwork(b);
    }
  }
}
