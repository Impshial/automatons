package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;

/** Normal inventory widgets and concise, profession-specific views share one authoritative menu. */
public final class ProfessionScreen extends AbstractContainerScreen<WorkMenu> {
  private final List<Runnable> bindings = new ArrayList<>();
  private int tab, scroll, rosterPage, guardPage, purposeSeen = -1, jobSeen = -1, confirmationSeen;
  private boolean choosing, renaming;
  private UUID selected;
  private EditBox name;

  public ProfessionScreen(WorkMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 360;
    imageHeight = 250 + menu.cargoLayoutExtra;
    inventoryLabelY = 140 + menu.cargoLayoutExtra;
  }

  private CompoundTag data() {
    return menu.snapshot;
  }

  private WorksitePurpose purpose() {
    return WorksitePurpose.of(data().getInt("Purpose"));
  }

  private boolean editable() {
    return data().getBoolean("Editable");
  }

  private void action(int value) {
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, value);
  }

  private void send(String kind, String value) {
    Network.CHANNEL.sendToServer(new WorkActions.Action(menu.containerId, kind, value));
  }

  private void tab(int value) {
    tab = value;
    scroll = 0;
    choosing = renaming = false;
    rebuild();
  }

  private void button(
      int x,
      int y,
      int width,
      Supplier<String> label,
      Runnable click,
      BooleanSupplier enabled,
      String hint) {
    Button b =
        addRenderableWidget(
            Button.builder(Component.literal(label.get()), ignored -> click.run())
                .bounds(leftPos + x, topPos + y, width, 18)
                .build());
    if (!hint.isBlank()) b.setTooltip(Tooltip.create(Component.literal(hint)));
    bindings.add(
        () -> {
          String value = label.get();
          b.setMessage(
              Component.literal(
                  font.width(value) > width - 8
                      ? font.plainSubstrByWidth(value, width - 17) + "…"
                      : value));
          b.active = enabled.getAsBoolean();
          if (hint.isBlank() && font.width(value) > width - 8)
            b.setTooltip(Tooltip.create(Component.literal(value)));
        });
  }

  private void button(int x, int y, int width, String label, Runnable click) {
    button(x, y, width, () -> label, click, this::editable, "");
  }

  @Override
  protected void init() {
    super.init();
    rebuild();
  }

  private ListTag list(String key) {
    return data().getList(key, 10);
  }

  private List<CompoundTag> roster() {
    List<CompoundTag> result = new ArrayList<>();
    for (String key : List.of("Production", "Protectors"))
      for (Tag item : list(key)) result.add((CompoundTag) item);
    return result;
  }

  private CompoundTag selected() {
    return roster().stream()
        .filter(n -> n.hasUUID("Id") && n.getUUID("Id").equals(selected))
        .findFirst()
        .orElse(new CompoundTag());
  }

  private void rebuild() {
    clearWidgets();
    bindings.clear();
    boolean chooser = menu.isTotem && (purpose() == WorksitePurpose.UNCONFIGURED || choosing);
    boolean confirm = data().getInt("PendingProfession") > 0;
    menu.view = chooser || confirm || renaming ? -1 : tab;
    if (confirm) {
      button(12, 152, 160, "Confirm profession change", () -> send("confirm", ""));
      button(188, 152, 160, "Keep current profession", () -> send("cancel", ""));
      return;
    }
    if (renaming) {
      name =
          addRenderableWidget(
              new EditBox(font, leftPos + 12, topPos + 62, 336, 20, Component.literal("Name")));
      name.setMaxLength(40);
      name.setValue(data().getString("Name"));
      setInitialFocus(name);
      button(
          12,
          100,
          160,
          "Save name",
          () -> {
            send("name", name.getValue());
            renaming = false;
            rebuild();
          });
      button(
          188,
          100,
          160,
          "Cancel",
          () -> {
            renaming = false;
            rebuild();
          });
      return;
    }
    if (chooser) {
      for (int i = 1; i < WorksitePurpose.values().length; i++) {
        final int value = i;
        WorksitePurpose p = WorksitePurpose.of(i);
        button(
            34 + ((i - 1) % 2) * 174,
            56 + ((i - 1) / 2) * 25,
            140,
            () -> p.label,
            () -> {
              if (purpose() == WorksitePurpose.UNCONFIGURED) action(160 + value);
              else send("change", Integer.toString(value));
            },
            () -> editable() && p != purpose(),
            "Assigns " + WorkerJob.name(p.job()));
      }
      if (purpose() == WorksitePurpose.UNCONFIGURED)
        button(
            12,
            213,
            162,
            () -> "Entrance: " + data().getString("Orientation"),
            () -> action(66),
            this::editable,
            "Rotate before choosing a profession");
      else
        button(
            188,
            213,
            160,
            "Back to settings",
            () -> {
              choosing = false;
              rebuild();
            });
      return;
    }
    String[] tabs =
        menu.isWorker
            ? new String[] {"Status", "Inventory"}
            : new String[] {"Overview", "Workers", "Settings"};
    for (int i = 0; i < tabs.length; i++) {
      final int index = i;
      button(8 + i * 116, 24, 112, () -> tabs[index], () -> tab(index), () -> tab != index, "");
    }
    button(
        302,
        3,
        50,
        "Name",
        () -> {
          renaming = true;
          rebuild();
        });
    if (menu.isWorker) {
      workerControls();
      return;
    }
    if (tab == 0) {
      button(
          12,
          207,
          160,
          () -> data().getBoolean("WorkPaused") ? "Resume work" : "Pause work",
          () -> action(240),
          this::editable,
          "");
      button(
          188,
          207,
          160,
          () -> data().getBoolean("WarehousePaused") ? "Resume construction" : "Pause construction",
          () -> action(data().getBoolean("WarehousePaused") ? 40 : 41),
          () -> editable() && !data().getBoolean("WorksiteReady"),
          "");
    } else if (tab == 1) {
      for (int column = 0; column < 2; column++)
        for (int i = 0; i < 3; i++) {
          final int row = i;
          final String key = column == 0 ? "Production" : "Protectors";
          button(
              12 + column * 174,
              63 + i * 34,
              162,
              () -> {
                var entries = list(key);
                int at = rosterPage * 3 + row;
                return at < entries.size() ? entries.getCompound(at).getString("Label") : "—";
              },
              () -> {
                var entries = list(key);
                int at = rosterPage * 3 + row;
                if (at < entries.size()) selected = entries.getCompound(at).getUUID("Id");
              },
              () -> rosterPage * 3 + row < list(key).size(),
              "");
        }
      button(
          12,
          177,
          24,
          () -> "<",
          () -> rosterPage = Math.max(0, rosterPage - 1),
          () -> rosterPage > 0,
          "");
      button(
          40,
          177,
          24,
          () -> ">",
          () -> rosterPage++,
          () ->
              (rosterPage + 1) * 3 < Math.max(list("Production").size(), list("Protectors").size()),
          "");
      button(
          70,
          177,
          134,
          () -> "Open worker",
          () -> send("open", selected.toString()),
          () -> editable() && selected().getBoolean("Loaded"),
          "Requires the worker within eight blocks");
      button(
          210,
          177,
          138,
          () -> "Unassign",
          () -> send("unassign", selected.toString()),
          () -> editable() && selected().hasUUID("Id"),
          "");
      button(
          12,
          207,
          24,
          () -> "<",
          () -> guardPage = Math.max(0, guardPage - 1),
          () -> guardPage > 0,
          "");
      button(
          324,
          207,
          24,
          () -> ">",
          () -> guardPage++,
          () -> guardPage + 1 < list("Candidates").size(),
          "");
      button(
          40,
          207,
          280,
          () ->
              list("Candidates").isEmpty()
                  ? "No nearby available workers"
                  : "Assign nearby: "
                      + list("Candidates")
                          .getCompound(Math.min(guardPage, list("Candidates").size() - 1))
                          .getString("Label"),
          () -> {
            if (!list("Candidates").isEmpty())
              send(
                  "assign",
                  list("Candidates")
                      .getCompound(Math.min(guardPage, list("Candidates").size() - 1))
                      .getUUID("Id")
                      .toString());
          },
          () ->
              editable()
                  && !list("Candidates").isEmpty()
                  && (data().getInt("ProductionCount") < 6
                      || list("Candidates")
                              .getCompound(Math.min(guardPage, list("Candidates").size() - 1))
                              .getInt("Job")
                          == WorkerJob.GUARDINGTON),
          "Six production workers maximum; guards have separate assignments");
    } else settingsControls();
  }

  private void workerControls() {
    if (tab != 0) return;
    button(
        12,
        207,
        160,
        () -> data().getBoolean("Authorized") ? "Work enabled" : "Start work",
        () -> action(6),
        () -> editable() && !data().getBoolean("Authorized"),
        "");
    button(188, 207, 160, "Rebind home", () -> action(3));
    if (data().getInt("Job") == WorkerJob.GUARDINGTON) {
      button(
          12,
          145,
          160,
          () -> "Network circuit",
          () -> action(190),
          () -> editable() && !data().getCompound("Guarding").getBoolean("Circuit"),
          "");
      button(
          188,
          145,
          160,
          () -> "Assigned totem",
          () -> action(191),
          () -> editable() && data().getCompound("Guarding").hasUUID("Site"),
          "");
      button(
          12,
          174,
          24,
          () -> "<",
          () -> guardPage = Math.max(0, guardPage - 1),
          () -> guardPage > 0,
          "");
      button(
          324,
          174,
          24,
          () -> ">",
          () -> guardPage++,
          () -> guardPage + 1 < list("GuardSites").size(),
          "");
      button(
          40,
          174,
          280,
          () ->
              list("GuardSites").isEmpty()
                  ? "No configured totems"
                  : list("GuardSites")
                      .getCompound(Math.min(guardPage, list("GuardSites").size() - 1))
                      .getString("Label"),
          () -> {
            var rows = list("GuardSites");
            if (!rows.isEmpty())
              send(
                  "guard",
                  rows.getCompound(Math.min(guardPage, rows.size() - 1)).getUUID("Id").toString());
          },
          () ->
              editable()
                  && !list("GuardSites").isEmpty()
                  && list("GuardSites")
                      .getCompound(Math.min(guardPage, list("GuardSites").size() - 1))
                      .getBoolean("Loaded"),
          "Scroll to choose an owned totem, then click to assign protection");
    }
  }

  private void settingsControls() {
    button(
        188,
        207,
        160,
        () -> "Change profession",
        () -> {
          choosing = true;
          rebuild();
        },
        () -> editable() && data().getString("ChangeIssue").isEmpty(),
        data().getString("ChangeIssue"));
    switch (purpose()) {
      case FARM -> {
        for (int i = 0; i < 3; i++) {
          final int strip = i;
          button(12 + 52 * i, 96, 48, "Clear", () -> action(185 + strip));
        }
        button(
            12,
            119,
            152,
            () -> "Bone meal: " + (data().getBoolean("BoneMeal") ? "ON" : "OFF"),
            () -> action(70),
            this::editable,
            "");
      }
      case RANCH -> {
        for (int i = 0; i < 4; i++) {
          final int species = i;
          button(190, 64 + i * 21, 24, "-", () -> action(210 + species));
          button(253, 64 + i * 21, 24, "+", () -> action(220 + species));
          button(
              285,
              64 + i * 21,
              63,
              () ->
                  data().getCompound("Ranch").getBoolean("Enabled" + species)
                      ? "Enabled"
                      : "Disabled",
              () -> action(200 + species),
              this::editable,
              "");
        }
      }
      case CRAFT -> {
        button(12, 96, 152, "Add held item to orders", () -> action(230));
        button(12, 117, 152, "Remove held item order", () -> action(231));
      }
      case MINE -> button(
          12,
          115,
          152,
          () -> data().getBoolean("MinePaused") ? "Resume mine" : "Pause mine",
          () -> action(data().getBoolean("MinePaused") ? 32 : 33),
          this::editable,
          "");
      case BUILDER -> button(12, 115, 152, "Check building repairs", () -> action(43));
      default -> {}
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    int purpose = data().getInt("Purpose"),
        confirmation = data().getInt("PendingProfession"),
        job = data().getInt("Job");
    if (purpose != purposeSeen || confirmation != confirmationSeen || job != jobSeen) {
      if (purposeSeen >= 0 && purpose != purposeSeen) {
        choosing = false;
        tab = 0;
      }
      purposeSeen = purpose;
      confirmationSeen = confirmation;
      jobSeen = job;
      rebuild();
    }
    bindings.forEach(Runnable::run);
  }

  private void text(GuiGraphics g, String value, int x, int y, int width) {
    g.drawString(font, font.plainSubstrByWidth(value, width), x, y, 0xff38332c, false);
  }

  private void lines(GuiGraphics g, String key, int x, int y, int width, int count) {
    ListTag values = data().getList(key, 8);
    for (int i = 0; i < count && i + scroll < values.size(); i++) {
      String value = values.getString(i + scroll);
      if (value.startsWith("\u001e")) {
        Component c = Component.Serializer.fromJson(value.substring(1));
        value = c == null ? "" : c.getString();
      }
      text(g, value, x, y + i * 12, width);
    }
  }

  private void bar(
      GuiGraphics g, String label, float value, float maximum, int x, int y, int width, int color) {
    text(
        g,
        label + ": " + String.format(Locale.ROOT, "%.1f", value) + " / " + (int) maximum,
        x,
        y,
        width);
    g.fill(x, y + 11, x + width, y + 17, 0xff55514c);
    g.fill(
        x + 1,
        y + 12,
        x + 1 + (int) ((width - 2) * Math.max(0, Math.min(1, value / maximum))),
        y + 16,
        color);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff393939);
    g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xffeeeeee);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xffc6c6c6);
    for (var slot : menu.slots)
      if (slot.isActive()) {
        int x = leftPos + slot.x, y = topPos + slot.y;
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xffeeeeee);
        g.fill(x - 1, y - 1, x + 16, y + 16, 0xff444444);
        g.fill(x, y, x + 16, y + 16, 0xff8b8b8b);
      }
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    text(g, data().getString("Name"), 8, 8, 288);
    if (data().getInt("PendingProfession") > 0) {
      text(
          g,
          "Change to " + WorksitePurpose.of(data().getInt("PendingProfession")).label + "?",
          12,
          54,
          336);
      text(g, "Existing blocks will remain. The new building plan", 12, 82, 336);
      text(g, "starts paused for review before construction.", 12, 96, 336);
      return;
    }
    if (renaming) {
      text(g, "Choose a name", 12, 44, 336);
      return;
    }
    if (menu.isTotem && (purpose() == WorksitePurpose.UNCONFIGURED || choosing)) {
      text(g, "Choose this totem's profession", 12, 34, 336);
      for (int i = 1; i < WorksitePurpose.values().length; i++)
        g.renderItem(
            icon(WorksitePurpose.of(i)), 12 + ((i - 1) % 2) * 174, 57 + ((i - 1) / 2) * 25);
      text(
          g,
          data().getString("Feedback").isEmpty()
              ? data().getString("SiteStatus")
              : data().getString("Feedback"),
          12,
          194,
          336);
      return;
    }
    if (menu.isWorker) renderWorker(g);
    else if (tab == 0) renderOverview(g);
    else if (tab == 1) renderWorkers(g);
    else renderSettings(g);
    if (menu.showInventory()) text(g, "Inventory", 8, inventoryLabelY, 162);
    text(g, data().getString("Feedback"), 12, 235 + menu.cargoLayoutExtra, 336);
  }

  private void renderWorker(GuiGraphics g) {
    if (tab == 1) {
      text(g, "Equipment", 186, 52, 160);
      text(g, "Cargo", 186, 80, 160);
      text(g, WorkerJob.name(data().getInt("Job")), 186, 104, 160);
      return;
    }
    text(g, WorkerJob.name(data().getInt("Job")), 12, 50, 336);
    text(g, data().getString("Assignment"), 12, 65, 336);
    text(g, data().getString("Activity"), 12, 80, 336);
    bar(
        g,
        "Health",
        data().getFloat("Health"),
        data().getFloat("MaxHealth"),
        12,
        98,
        160,
        0xffc84949);
    bar(g, "Operating Lumina", data().getInt("Operating") / 1000f, 200, 188, 98, 160, 0xffd8ac38);
    if (data().getInt("Job") == WorkerJob.LUMINARITON)
      bar(g, "Service reserve", data().getInt("Service") / 1000f, 400, 12, 131, 336, 0xffa569ca);
    if (data().getInt("Job") == WorkerJob.GUARDINGTON) {
      text(g, "Armor", 12, 124, 44);
      // Vanilla armor sprites contain two armor points each.
      for (int i = 0; i < 6; i++)
        g.blit(
            new net.minecraft.resources.ResourceLocation("textures/gui/icons.png"),
            57 + i * 12,
            122,
            34,
            9,
            9,
            9);
    }
  }

  private void renderOverview(GuiGraphics g) {
    g.renderItem(icon(purpose()), 12, 49);
    text(g, purpose().label, 34, 53, 314);
    text(g, data().getString("SiteStatus"), 12, 76, 336);
    text(
        g,
        "Production: "
            + data().getInt("ProductionCount")
            + "/6     Guards: "
            + list("Protectors").size(),
        12,
        94,
        336);
    text(g, "Linked storage: " + data().getInt("StorageCount") + " containers", 12, 110, 336);
    text(g, "Supplies needed", 12, 130, 336);
    int row = 0;
    ListTag needs = list("SupplyNeeds");
    if (!data().getBoolean("WorksiteReady")) {
      for (Tag tag : list("WarehouseBuild")) {
        CompoundTag entry = (CompoundTag) tag;
        if (entry.getInt("Missing") <= 0) continue;
        if (row++ < scroll) continue;
        if (row - scroll > 3) break;
        ItemStack stack = ItemStack.of(entry.getCompound("Item"));
        int y = 145 + (row - scroll - 1) * 18;
        g.renderItem(stack, 12, y);
        text(g, entry.getInt("Missing") + " × " + stack.getHoverName().getString(), 34, y + 4, 314);
      }
    } else
      for (int i = 0; i < 3 && i + scroll < needs.size(); i++) {
        var entry = needs.getCompound(i + scroll);
        ItemStack stack = ItemStack.of(entry);
        g.renderItem(stack, 12, 145 + i * 18);
        text(
            g,
            entry.getInt("Need") + " × " + stack.getHoverName().getString(),
            34,
            149 + i * 18,
            314);
      }
  }

  private void renderWorkers(GuiGraphics g) {
    text(g, "Production workers: " + data().getInt("ProductionCount") + "/6", 12, 49, 162);
    text(g, "Guards: " + list("Protectors").size(), 186, 49, 162);
    for (int column = 0; column < 2; column++) {
      var entries = list(column == 0 ? "Production" : "Protectors");
      for (int i = 0; i < 3 && rosterPage * 3 + i < entries.size(); i++) {
        var entry = entries.getCompound(rosterPage * 3 + i);
        String stats =
            entry.getBoolean("Loaded")
                ? (int) entry.getFloat("Health")
                    + " HP · "
                    + entry.getInt("Operating") / 1000
                    + " Lumina · "
                : "";
        text(g, stats + entry.getString("Activity"), 12 + column * 174, 84 + i * 34, 162);
      }
    }
  }

  private void renderSettings(GuiGraphics g) {
    text(g, purpose().label + " settings", 12, 47, 166);
    switch (purpose()) {
      case FARM -> {
        int[] crops = data().getIntArray("Crops");
        for (int i = 0; i < 3; i++) {
          int x = 28 + 52 * i;
          text(g, new String[] {"Left", "Middle", "Right"}[i], 12 + 52 * i, 64, 50);
          g.fill(x - 1, 77, x + 17, 95, 0xff555555);
          g.fill(x, 78, x + 16, 94, 0xffaaaaaa);
          if (crops.length > i && crops[i] > 0 && crops[i] <= 4)
            g.renderItem(new ItemStack(FarmPlan.PLANTS.get(crops[i] - 1)), x, 78);
        }
        text(g, "Click a crop slot with a sample.", 188, 52, 160);
        text(g, "The sample is kept.", 188, 65, 160);
        text(g, "An empty slot disables planting.", 188, 91, 160);
        lines(g, "FarmLines", 188, 115, 160, 6);
      }
      case RANCH -> {
        int[] adults = data().getIntArray("Adults"),
            babies = data().getIntArray("Babies"),
            targets = data().getCompound("Ranch").getIntArray("Targets");
        for (int i = 0; i < 4; i++) {
          text(
              g,
              new String[] {"Sheep", "Cows", "Chickens", "Pigs"}[i]
                  + ": "
                  + (adults.length > i ? adults[i] : 0)
                  + " adult, "
                  + (babies.length > i ? babies[i] : 0)
                  + " young",
              12,
              68 + i * 21,
              174);
          text(g, Integer.toString(targets.length > i ? targets[i] : 8), 224, 69 + i * 21, 24);
        }
        text(
            g,
            "Buckets: " + data().getInt("Buckets") + " · Leads: " + data().getInt("Leads"),
            188,
            158,
            160);
        text(
            g,
            "Milk: " + data().getInt("Milk") + " / " + data().getInt("MilkCapacity") + " mB",
            188,
            179,
            160);
      }
      case CRAFT -> {
        text(g, "Sample count sets the target.", 12, 70, 164);
        text(g, "The sample is kept.", 12, 84, 164);
        for (int i = 0; i < 6 && i + scroll < list("CraftOrders").size(); i++) {
          ItemStack stack = ItemStack.of(list("CraftOrders").getCompound(i + scroll));
          g.renderItem(stack, 188, 51 + i * 22);
          text(
              g,
              stack.getCount() + " × " + stack.getHoverName().getString(),
              210,
              55 + i * 22,
              138);
        }
      }
      case FISH -> {
        text(g, "Dock slots: " + data().getInt("DockUsed") + "/6", 12, 71, 164);
        text(g, "Stored rods: " + data().getInt("Rods"), 12, 89, 164);
        text(
            g,
            data().getBoolean("WaterValid") ? "Source water ready" : "Needs 7 × 10 source water",
            188,
            54,
            160);
        for (int i = 0; i < 6; i++)
          g.fill(
              190 + i * 26,
              80,
              210 + i * 26,
              100,
              i < data().getInt("DockUsed") ? 0xff7c9b6d : 0xff888888);
      }
      case HUNT -> {
        text(g, "Hunting radius: 96 blocks", 12, 73, 164);
        text(g, "Stored arrows: " + data().getInt("Arrows"), 12, 92, 164);
        lines(g, "LogisticsLines", 188, 52, 160, 11);
      }
      case LUMINA -> {
        text(g, "Stored bottles: " + data().getInt("Bottles"), 12, 72, 164);
        text(g, "Raw shards: " + data().getInt("RawShards"), 12, 90, 164);
        text(g, "Infused shards: " + data().getInt("InfusedShards"), 12, 108, 164);
        lines(g, "LogisticsLines", 188, 52, 160, 11);
      }
      case MINE -> lines(g, "MiningLines", 188, 52, 160, 12);
      case LUMBER -> lines(g, "ForestryLines", 188, 52, 160, 12);
      case QUARRY -> {
        text(g, "Excavation: 15 × 15", 12, 73, 164);
        lines(g, "LogisticsLines", 188, 52, 160, 12);
      }
      default -> lines(g, "LogisticsLines", 188, 52, 160, 12);
    }
    if (!data().getString("ChangeIssue").isEmpty())
      text(g, data().getString("ChangeIssue"), 188, 229, 160);
  }

  private static ItemStack icon(WorksitePurpose p) {
    return new ItemStack(
        switch (p) {
          case LUMBER -> Items.IRON_AXE;
          case MINE -> Items.IRON_PICKAXE;
          case FARM -> Items.WHEAT;
          case WAREHOUSE -> Items.CHEST;
          case BUILDER -> Automatons.HAMMER.get();
          case HUNT -> Items.BOW;
          case CRAFT -> Items.CRAFTING_TABLE;
          case FISH -> Items.FISHING_ROD;
          case RANCH -> Items.LEAD;
          case QUARRY -> Items.DIAMOND_PICKAXE;
          case LUMINA -> Luminall.BOTTLE.get();
          default -> Automatons.TOTEM.get().asItem();
        });
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (menu.isTotem
        && tab == 2
        && purpose() == WorksitePurpose.FARM
        && !choosing
        && !renaming
        && editable())
      for (int i = 0; i < 3; i++)
        if (x >= leftPos + 27 + 52 * i
            && x < leftPos + 45 + 52 * i
            && y >= topPos + 77
            && y < topPos + 95) {
          action(button == 1 ? 185 + i : 180 + i);
          return true;
        }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double amount) {
    int delta = amount > 0 ? -1 : 1;
    if (menu.isWorker && data().getInt("Job") == WorkerJob.GUARDINGTON && tab == 0)
      guardPage =
          Math.max(0, Math.min(Math.max(0, list("GuardSites").size() - 1), guardPage + delta));
    else if (menu.isTotem && tab == 1 && y >= topPos + 202)
      guardPage =
          Math.max(0, Math.min(Math.max(0, list("Candidates").size() - 1), guardPage + delta));
    else scroll = Math.max(0, Math.min(100, scroll + delta));
    return true;
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    renderBackground(g);
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
  }
}
