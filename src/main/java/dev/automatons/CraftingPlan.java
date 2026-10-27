package dev.automatons;

import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.items.ItemStackHandler;

/** Bounded dependency search. A plan contains no items; commits use the actual carried inputs. */
public final class CraftingPlan {
  public record Step(Recipe<?> recipe, List<ItemStack> inputs, ItemStack output) {
    public boolean cooking() { return recipe instanceof AbstractCookingRecipe; }
    public Map<Item, Integer> ingredients() {
      Map<Item, Integer> result = new LinkedHashMap<>();
      for (ItemStack item : inputs) if (!item.isEmpty()) result.merge(item.getItem(), item.getCount(), Integer::sum);
      return result;
    }
  }
  public record Result(Step step, Map<Item, Integer> missing, String reason, Map<Item, Integer> held) {
    public Result(Step step, Map<Item, Integer> missing, String reason) { this(step, missing, reason, Map.of()); }
  }
  private static final class Budget { int left = 128; }
  private static final AbstractContainerMenu GRID_MENU = new AbstractContainerMenu(null, -1) {
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return false; }
  };
  public static CraftingContainer grid(Recipe<?> recipe, List<ItemStack> inputs) {
    CraftingContainer grid = new TransientCraftingContainer(GRID_MENU, 3, 3);
    for (int i = 0; i < inputs.size() && i < 9; i++) {
      int slot = recipe instanceof ShapedRecipe shaped ? (i / shaped.getWidth()) * 3 + i % shaped.getWidth() : i;
      if (slot < 9) grid.setItem(slot, inputs.get(i).copy());
    }
    return grid;
  }
  public static boolean ordinary(ItemStack stack) {
    return !stack.hasTag() && !stack.isDamaged() && !stack.isEnchanted() && !stack.hasCustomHoverName();
  }
  private static boolean eligible(Ingredient ingredient, ItemStack stack) {
    if (stack.isEmpty() || !ingredient.test(stack)) return false;
    if (ordinary(stack)) return true;
    // Explicitly tagged ingredients may intentionally need custom data. Generic ingredients may not.
    return Arrays.stream(ingredient.getItems()).anyMatch(s -> s.hasTag() && ItemStack.isSameItemSameTags(s, stack));
  }
  public static Result next(ServerLevel level, Item wanted, List<ItemStack> available) {
    return next(level, wanted, available, new HashSet<>(), 0, new Budget());
  }
  private static Result next(ServerLevel level, Item wanted, List<ItemStack> available, Set<Item> visiting, int depth, Budget budget) {
    if (depth >= 8 || budget.left-- <= 0 || !visiting.add(wanted)) return new Result(null, Map.of(wanted, 1), "Recipe dependency cycle or limit");
    try {
      List<Recipe<?>> recipes = new ArrayList<>();
      recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING));
      recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING));
      recipes.addAll(level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
      recipes = recipes.stream().filter(r -> !r.isSpecial() && r.getResultItem(level.registryAccess()).is(wanted))
          .sorted(Comparator.<Recipe<?>>comparingInt(r -> -availableIngredients(r, available))
              .thenComparingInt(r -> r.getType() == RecipeType.BLASTING ? 0 : r.getType() == RecipeType.SMELTING ? 1 : 2)
              .thenComparing(r -> r.getId().toString())).limit(64).toList();
      if (recipes.isEmpty()) return new Result(null, Map.of(wanted, 1), depth == 0 ? "Unsupported recipe" : "Needs ingredients");
      Result best = null;
      for (Recipe<?> recipe : recipes) {
        if (budget.left-- <= 0) break;
        if (recipe.getIngredients().size() > 9 || recipe.getIngredients().isEmpty()) continue;
        List<ItemStack> stock = available.stream().map(ItemStack::copy).toList();
        List<ItemStack> input = new ArrayList<>(); List<Ingredient> missing = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
          if (ingredient.isEmpty()) { input.add(ItemStack.EMPTY); continue; }
          ItemStack found = stock.stream().filter(s -> eligible(ingredient, s)).findFirst().orElse(null);
          if (found == null) { missing.add(ingredient); input.add(ItemStack.EMPTY); }
          else { input.add(found.copyWithCount(1)); found.shrink(1); }
        }
        if (missing.isEmpty()) {
          if (recipe instanceof CraftingRecipe crafting) {
            CraftingContainer grid = grid(recipe, input);
            if (!crafting.matches(grid, level)) continue;
            ItemStack result = crafting.assemble(grid, level.registryAccess());
            if (!result.is(wanted)) continue;
            return new Result(new Step(recipe, input, result), Map.of(), "Ready to craft");
          }
          return new Result(new Step(recipe, input, recipe.getResultItem(level.registryAccess()).copy()), Map.of(), "Ready to cook");
        }
        Map<Item, Integer> leaves = new LinkedHashMap<>();
        for (Ingredient ingredient : missing) {
          Result ingredientBest = null;
          for (ItemStack option : Arrays.stream(ingredient.getItems()).filter(CraftingPlan::ordinary).limit(32).toList()) {
            Result dependency = next(level, option.getItem(), stock, visiting, depth + 1, budget);
            if (dependency.step != null) {
              Map<Item, Integer> held = new LinkedHashMap<>();
              for (ItemStack selected : input) if (!selected.isEmpty()) held.merge(selected.getItem(), 1, Integer::sum);
              for (Ingredient absent : missing) {
                ItemStack selected = absent.test(option) ? option : Arrays.stream(absent.getItems()).filter(CraftingPlan::ordinary).findFirst().orElse(ItemStack.EMPTY);
                if (!selected.isEmpty()) held.merge(selected.getItem(), 1, Integer::sum);
              }
              dependency.held.forEach((item, count) -> held.merge(item, count, Math::max));
              return new Result(dependency.step, dependency.missing, dependency.reason, held);
            }
            if (ingredientBest == null || score(dependency) < score(ingredientBest)) ingredientBest = dependency;
            if (budget.left <= 0) break;
          }
          if (ingredientBest != null) ingredientBest.missing.forEach((item, count) -> leaves.merge(item, count, Integer::sum));
        }
        Result candidate = new Result(null, leaves, leaves.isEmpty() ? "Unsupported recipe" : "Needs ingredients");
        if (best == null || score(candidate) < score(best)) best = candidate;
      }
      return best == null ? new Result(null, Map.of(wanted, 1), "Unsupported recipe") : best;
    } finally { visiting.remove(wanted); }
  }
  private static int availableIngredients(Recipe<?> recipe, List<ItemStack> stock) {
    return (int)recipe.getIngredients().stream().filter(i -> !i.isEmpty() && stock.stream().anyMatch(s -> eligible(i, s))).count();
  }
  private static int score(Result result) {
    return result.missing.isEmpty() ? Integer.MAX_VALUE : result.missing.values().stream().mapToInt(Integer::intValue).sum();
  }
  public static List<ItemStack> contents(ItemStackHandler cargo) {
    List<ItemStack> result = new ArrayList<>();
    for (int i = 0; i < cargo.getSlots(); i++) if (!cargo.getStackInSlot(i).isEmpty()) result.add(cargo.getStackInSlot(i).copy());
    return result;
  }
  /** Full simulation includes recipe-specific remainders before any real input is consumed. */
  public static boolean craft(ServerLevel level, ItemStackHandler cargo, Step step) {
    if (!(step.recipe instanceof CraftingRecipe recipe)) return false;
    CraftingContainer grid = grid(recipe, step.inputs);
    if (!recipe.matches(grid, level)) return false;
    ItemStack result = recipe.assemble(grid, level.registryAccess());
    if (result.isEmpty() || !ItemStack.isSameItemSameTags(result, step.output) || result.getCount() != step.output.getCount()) return false;
    ItemStackHandler after = new ItemStackHandler(cargo.getSlots()); after.deserializeNBT(cargo.serializeNBT());
    for (ItemStack input : step.inputs) {
      if (input.isEmpty()) continue;
      boolean removed = false;
      for (int i = 0; i < after.getSlots(); i++) if (ItemStack.isSameItemSameTags(input, after.getStackInSlot(i))) {
        after.extractItem(i, 1, false); removed = true; break;
      }
      if (!removed) return false;
    }
    NonNullList<ItemStack> remainders = recipe.getRemainingItems(grid);
    if (!Inventories.insert(after, result, false).isEmpty()) return false;
    for (ItemStack remainder : remainders) if (!Inventories.insert(after, remainder, false).isEmpty()) return false;
    cargo.deserializeNBT(after.serializeNBT()); return true;
  }
}
