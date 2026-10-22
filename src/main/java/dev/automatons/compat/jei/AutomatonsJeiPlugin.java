package dev.automatons.compat.jei;

import dev.automatons.*;
import dev.automatons.client.WorkScreen;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Discovered by JEI only; common game code never loads JEI classes. */
@JeiPlugin
public final class AutomatonsJeiPlugin implements IModPlugin {
  public static final RecipeType<CreationRecipe> ASSEMBLY =
      RecipeType.create(Automatons.ID, "assembly", CreationRecipe.class);
  public static final RecipeType<InfusionCategory.Display> INFUSION =
      RecipeType.create(Automatons.ID, "infusion", InfusionCategory.Display.class);
  private static IJeiRuntime runtime;

  @Override
  public ResourceLocation getPluginUid() {
    return new ResourceLocation(Automatons.ID, "jei");
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registration) {
    var gui = registration.getJeiHelpers().getGuiHelper();
    registration.addRecipeCategories(new AssemblyCategory(gui), new InfusionCategory(gui));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    var level = Minecraft.getInstance().level;
    if (level != null) {
      // Use the synchronized recipe manager, including server data-pack replacements.
      registration.addRecipes(
          ASSEMBLY, level.getRecipeManager().getAllRecipesFor(Automatons.CREATION.get()));
    }
    registration.addRecipes(INFUSION, List.of(new InfusionCategory.Display()));
  }

  @Override
  public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
    registration.addRecipeCatalyst(new ItemStack(Automatons.PEDESTAL.get()), ASSEMBLY);
    registration.addRecipeCatalyst(new ItemStack(Automatons.INFUSER.get()), INFUSION);
  }

  private static final IGuiContainerHandler<WorkScreen> SCREENS =
      new IGuiContainerHandler<>() {
        @Override
        public Collection<IGuiClickableArea> getGuiClickableAreas(
            WorkScreen screen, double x, double y) {
          if (screen.getMenu().isWorker || screen.getMenu().isTotem) return List.of();
          return List.of(IGuiClickableArea.createBasic(128, 6, 46, 16, ASSEMBLY));
        }
      };

  @Override
  public void registerGuiHandlers(IGuiHandlerRegistration registration) {
    registration.addGuiContainerHandler(WorkScreen.class, SCREENS);
  }

  @Override
  public void onRuntimeAvailable(IJeiRuntime available) {
    runtime = available;
  }

  @Override
  public void onRuntimeUnavailable() {
    runtime = null;
  }

  /** Invoked only by the opt-in Lab smoke, via reflection to preserve optional loading. */
  public static void review(boolean infusion) {
    if (!Boolean.getBoolean("automatons.visualSmoke") || runtime == null)
      throw new IllegalStateException("JEI review requires the active Lab and JEI runtime");
    var manager = runtime.getRecipeManager();
    var factory = runtime.getJeiHelpers().getFocusFactory();
    ItemStack result =
        infusion
            ? CoreItem.vessel(CoreItem.CAPACITY)
            : new ItemStack(Automatons.AUTOMATON_ITEM.get());
    var focus = factory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, result);
    long matches =
        infusion
            ? manager.createRecipeLookup(INFUSION).limitFocus(List.of(focus)).get().count()
            : manager.createRecipeLookup(ASSEMBLY).limitFocus(List.of(focus)).get().count();
    if (matches < 1) throw new IllegalStateException("JEI output recipe lookup is empty");
    if (!infusion && Minecraft.getInstance().screen instanceof WorkScreen screen) {
      var areas = SCREENS.getGuiClickableAreas(screen, 140, 12);
      if (areas.size() != 1) throw new IllegalStateException("Missing pedestal recipe link");
      areas.iterator().next().onClick(factory, runtime.getRecipesGui());
    } else runtime.getRecipesGui().show(focus);
    System.out.println(
        "AUTOMATONS_JEI " + (infusion ? "infusion" : "assembly") + " outputMatches=" + matches);
  }
}
