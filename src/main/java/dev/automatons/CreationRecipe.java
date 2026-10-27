package dev.automatons;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Six body positions; the displayed result has no identity until a player takes it. */
public record CreationRecipe(ResourceLocation id, List<Ingredient> components)
    implements Recipe<SimpleContainer> {
  public CreationRecipe {
    if (components.size() != 6)
      throw new IllegalArgumentException("Assembly requires exactly six components");
    components = List.copyOf(components);
  }

  @Override
  public boolean matches(SimpleContainer c, Level l) {
    if (c.getContainerSize() < 6) return false;
    for (int i = 0; i < 6; i++) if (!components.get(i).test(c.getItem(i))) return false;
    return true;
  }

  @Override
  public ItemStack assemble(SimpleContainer c, RegistryAccess a) {
    return getResultItem(a);
  }

  @Override
  public boolean canCraftInDimensions(int w, int h) {
    return w * h >= 6;
  }

  @Override
  public ItemStack getResultItem(RegistryAccess a) {
    ItemStack result = new ItemStack(Automatons.AUTOMATON_ITEM.get());
    if (components.get(2).test(new ItemStack(Automatons.ARMORED_BODY.get()))) {
      result.getOrCreateTag().putBoolean("Armored", true);
      result.getOrCreateTag().putInt("Job", WorkerJob.GUARDINGTON);
    }
    return result;
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

  @Override
  public boolean isSpecial() {
    return true;
  }

  @Override
  public NonNullList<Ingredient> getIngredients() {
    NonNullList<Ingredient> out = NonNullList.create();
    out.addAll(components);
    return out;
  }

  public static class Serializer implements RecipeSerializer<CreationRecipe> {
    public CreationRecipe fromJson(ResourceLocation id, JsonObject n) {
      List<Ingredient> items = new ArrayList<>();
      for (JsonElement e : n.getAsJsonArray("components")) items.add(Ingredient.fromJson(e));
      return new CreationRecipe(id, items);
    }

    public CreationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf b) {
      List<Ingredient> items = new ArrayList<>();
      for (int i = 0; i < 6; i++) items.add(Ingredient.fromNetwork(b));
      return new CreationRecipe(id, items);
    }

    public void toNetwork(FriendlyByteBuf b, CreationRecipe r) {
      r.components.forEach(i -> i.toNetwork(b));
    }
  }
}
