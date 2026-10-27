package dev.automatons.client;

import dev.automatons.*;
import guideme.*;
import guideme.color.SymbolicColor;
import guideme.document.block.*;
import guideme.document.flow.LytFlowContent;
import guideme.indices.ItemIndex;
import guideme.ui.GuideUiHost;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;

/** Explicit disposable Lab review. Uses public GuideME APIs; never active in normal play. */
public final class CurrentRunVisualSmoke {
  private static List<String> pages = List.of();
  private static KeyMapping guideKey;
  private static com.mojang.blaze3d.platform.InputConstants.Key originalGuideKey;
  private static boolean failed;
  private static volatile boolean reloaded;
  private static int reloadOpenedAt = -1;

  private static void check(boolean value, String message) {
    if (!value) throw new IllegalStateException(message);
  }

  private static void server(
      java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
    var mc = Minecraft.getInstance();
    mc.getSingleplayerServer()
        .execute(
            () ->
                action.accept(
                    mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID())));
  }

  private static void shot(String name) {
    var mc = Minecraft.getInstance();
    Screenshot.grab(
        mc.gameDirectory,
        "automatons-current-" + name + ".png",
        mc.getMainRenderTarget(),
        c -> System.out.println("CURRENT_IMAGE " + name));
  }

  private static void navigate(String path) {
    var mc = Minecraft.getInstance();
    check(mc.screen instanceof GuideUiHost, "Handbook screen missing for " + path);
    ((GuideUiHost) mc.screen).navigateTo(Automatons.id(path));
  }

  public static void validate() {
    Guide guide = Guides.getById(HandbookItem.GUIDE_ID);
    check(guide != null, "Guide not registered");
    check(
        Guides.getAll().stream().filter(g -> g.getId().equals(HandbookItem.GUIDE_ID)).count() == 1,
        "Duplicate handbook");
    pages = guide.getPages().stream().map(p -> p.getId().getPath()).sorted().toList();
    check(pages.size() >= 29, "Missing handbook pages: " + pages.size());
    int[] recipes = {0}, scenes = {0};
    for (String path : pages) {
      GuidePage page = guide.getPage(Automatons.id(path));
      check(page != null, "Missing compiled page " + path);
      page.document()
          .visit(
              new LytVisitor() {
                @Override
                public Result beforeFlowContent(LytFlowContent content) {
                  check(
                      content.getStyle().color() != SymbolicColor.ERROR_TEXT,
                      "Guide compiler error on " + path + ": " + page.document().getTextContent());
                  return Result.CONTINUE;
                }

                @Override
                public Result beforeNode(LytNode node) {
                  if (node
                      instanceof guideme.document.block.recipes.LytStandardRecipeBox<?> recipe) {
                    recipes[0]++;
                    if (recipe.getRecipe().getType() == Automatons.CREATION.get())
                      check(
                          recipe.getRecipe().getIngredients().size() == 6,
                          "Custom assembly recipe did not use live" + " ingredients");
                  }
                  if (node instanceof guideme.scene.LytGuidebookScene scene) {
                    check(scene.getScene() != null, "Empty scene on " + path);
                    scenes[0]++;
                  }
                  return Result.CONTINUE;
                }
              },
              true);
    }
    int items = 0;
    for (var item : Automatons.ITEMS.getEntries())
      if (!Automatons.retired(item.get())) {
        check(
            guide.getIndex(ItemIndex.class).get(item.getId()) != null,
            "Missing item association " + item.getId());
        items++;
      }
    check(recipes[0] >= 17 && scenes[0] >= 7, "Missing live recipe or scene layouts");
    System.out.println(
        "CURRENT_GUIDE_VALID pages="
            + pages.size()
            + " recipes="
            + recipes[0]
            + " scenes="
            + scenes[0]
            + " itemLinks="
            + items);
  }

  public static void tick(int t) {
    if (failed) return;
    try {
      review(t);
    } catch (Throwable error) {
      failed = true;
      org.slf4j.LoggerFactory.getLogger("automatons.review").error("CURRENT_REVIEW_FAILED", error);
      try {
        Files.writeString(
            Minecraft.getInstance().gameDirectory.toPath().resolve("current-review-failed.txt"),
            error.toString());
      } catch (Exception ignored) {
      }
      Minecraft.getInstance().stop();
    }
  }

  private static void review(int t) throws Exception {
    var mc = Minecraft.getInstance();
    if (t == 5)
      server(
          p -> {
            p.closeContainer();
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(Automatons.HANDBOOK.get()));
            p.getInventory().setItem(1, new ItemStack(Automatons.COAL_PIECE.get(), 8));
            p.getInventory().setItem(2, new ItemStack(Automatons.CHARCOAL_PIECE.get(), 8));
          });
    if (t == 35) mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    if (t == 65) {
      check(mc.screen instanceof GuideUiHost, "Server item use did not open handbook");
      check(
          mc.player.getMainHandItem().is(Automatons.HANDBOOK.get())
              && mc.player.getMainHandItem().getCount() == 1,
          "Opening consumed book");
      validate();
      navigate("index.md");
    }
    if (t == 80) shot("book-open-scale3");
    if (t >= 100 && t < 100 + pages.size() * 24) {
      int index = (t - 100) / 24;
      if ((t - 100) % 24 == 0) navigate(pages.get(index));
      if ((t - 100) % 24 == 16)
        shot("page-" + pages.get(index).replace('/', '-').replace(".md", ""));
    }
    if (t == 810) {
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
      navigate("network/automaton-pedestal.md");
    }
    if (t == 835) shot("assembly-scale2");
    if (t == 845) {
      mc.options.guiScale().set(4);
      mc.resizeDisplay();
      navigate("magic/infusing-pedestal.md");
    }
    if (t == 870) shot("infusion-scale4");
    if (t == 880) {
      mc.options.guiScale().set(3);
      mc.resizeDisplay();
      navigate("index.md");
      mc.screen.children().stream()
          .filter(e -> e instanceof Button b && b.getMessage().getString().equals("Search"))
          .map(e -> (Button) e)
          .findFirst()
          .orElseThrow()
          .onPress();
    }
    if (t == 890) {
      var edit =
          mc.screen.children().stream()
              .filter(e -> e instanceof EditBox)
              .map(e -> (EditBox) e)
              .findFirst()
              .orElseThrow();
      edit.setValue("torches");
    }
    if (t == 920) shot("search-torches");
    if (t == 930) {
      mc.setScreen(new InventoryScreen(mc.player));
      guideKey =
          Arrays.stream(mc.options.keyMappings)
              .filter(k -> k.getName().equals("key.guideme.guide"))
              .findFirst()
              .orElseThrow();
      originalGuideKey = guideKey.getKey();
      guideKey.setKey(
          com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(
              org.lwjgl.glfw.GLFW.GLFW_KEY_H));
    }
    if (t == 940) {
      var lines =
          new ItemStack(Automatons.COAL_PIECE.get())
              .getTooltipLines(mc.player, TooltipFlag.Default.NORMAL);
      check(
          lines.stream()
              .anyMatch(
                  c ->
                      c.getString().contains("H")
                          && c.getString().toLowerCase(Locale.ROOT).contains("guide")),
          "Rebound item tooltip missing guide instruction: " + lines);
      System.out.println(
          "CURRENT_HOTKEY_TOOLTIP association=true rebound=H" + " physicalKeyPress=not_tested");
      guideKey.setKey(originalGuideKey);
      server(
          p ->
              guideme.GuidesCommon.openGuide(
                  p,
                  HandbookItem.GUIDE_ID,
                  guideme.PageAnchor.page(Automatons.id("items/fuel.md"))));
    }
    if (t == 950) {
      check(mc.screen instanceof GuideUiHost, "Indexed item guide destination failed");
      shot("fuel-item-destination");
    }
    if (t == 965 && ModList.get().isLoaded("jeresources"))
      dev.automatons.compat.jei.AutomatonsJeiPlugin.reviewOre(false);
    if (t == 990 && ModList.get().isLoaded("jeresources")) shot("jer-stone");
    if (t == 1005 && ModList.get().isLoaded("jeresources"))
      dev.automatons.compat.jei.AutomatonsJeiPlugin.reviewOre(true);
    if (t == 1030 && ModList.get().isLoaded("jeresources")) shot("jer-deepslate");
    if (t == 1045)
      server(
          p -> {
            p.closeContainer();
            var home = (Station) p.serverLevel().getBlockEntity(new BlockPos(-3, 64, 0));
            NetworkHooks.openScreen(
                p,
                home,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(home.getBlockPos());
                });
          });
    if (t == 1070) shot("home-gui");
    if (t == 1085)
      server(
          p -> {
            var site = (Station) p.serverLevel().getBlockEntity(new BlockPos(3, 64, 0));
            check(site.configure(WorksitePurpose.BUILDER), "Review worksite setup failed");
            NetworkHooks.openScreen(
                p,
                site,
                b -> {
                  b.writeBoolean(false);
                  b.writeBlockPos(site.getBlockPos());
                });
          });
    if (t == 1110) shot("totem-gui");
    if (t == 1120) ((WorkScreen) mc.screen).professionPopup = true;
    if (t == 1140) shot("worksite-settings");
    if (t == 1150) {
      var screen = (WorkScreen) mc.screen;
      screen.professionPopup = false;
      screen.buildPopup = true;
      screen.warehousePopup = true;
    }
    if (t == 1170) shot("build-list");
    if (t == 1180)
      server(
          p -> {
            p.closeContainer();
            var srv = p.serverLevel().getServer();
            srv.reloadResources(srv.getPackRepository().getSelectedIds())
                .whenComplete(
                    (result, error) -> {
                      if (error != null)
                        throw new IllegalStateException("Review recipe reload failed", error);
                      reloaded = true;
                    });
          });
    if (t >= 1240 && reloadOpenedAt < 0) {
      check(t < 1840, "Recipe reload did not finish within 33 seconds");
      if (!reloaded) return;
      reloadOpenedAt = t;
      server(
          p ->
              guideme.GuidesCommon.openGuide(
                  p,
                  HandbookItem.GUIDE_ID,
                  guideme.PageAnchor.page(Automatons.id("network/automaton-pedestal.md"))));
    }
    if (reloadOpenedAt >= 0 && t == reloadOpenedAt + 40) {
      check(mc.screen instanceof GuideUiHost, "Reopened synchronized guide missing");
      validate();
      shot("assembly-after-reload");
    }
    if (reloadOpenedAt >= 0 && t == reloadOpenedAt + 60) {
      Files.writeString(
          mc.gameDirectory.toPath().resolve("current-review-passed.json"),
          "{\"pages\":"
              + pages.size()
              + ",\"jer\":"
              + ModList.get().isLoaded("jeresources")
              + ",\"success\":true}");
      System.out.println("CURRENT_REVIEW_COMPLETE");
      mc.stop();
    }
  }
}
