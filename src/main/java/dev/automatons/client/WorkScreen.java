package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WorkScreen extends AbstractContainerScreen<WorkMenu> {
  private final List<Button> memberButtons = new ArrayList<>(), editButtons = new ArrayList<>();
  private Button previous, next, back, unbind, ranges, find;
  private Button nameLabels;
  private int scroll;
  private int panel;
  private int selectedJob, professionTab = 3, professionScroll, selectedPurpose = 1;
  public boolean professionPopup;
  private boolean jobList, jobConfirmation;
  private Button professionSelect, applyJob, buildList;
  public boolean buildPopup;
  public boolean warehousePopup;
  private int buildScroll;
  private Button networkBuildLists;
  private int projectScroll;
  private String selectedBuild = "";

  public WorkScreen(WorkMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 300;
    imageHeight = 234 + menu.cargoLayoutExtra;
    inventoryLabelY = 140 + menu.cargoLayoutExtra;
  }

  public boolean hasPopup() {
    return professionPopup || buildPopup || jobList || jobConfirmation;
  }

  private void applySelectedJob() {
    if (!menu.snapshot.getBoolean("Editable")) return;
    int[] actions = {7, 8, 13, 16, 17, 18, 90};
    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, actions[selectedJob]);
    jobConfirmation = false;
  }

  private Button button(int x, int y, int width, String title, int action) {
    return addRenderableWidget(
        Button.builder(
                Component.literal(title),
                b -> {
                  scroll = 0;
                  minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
                })
            .bounds(leftPos + x, topPos + y, width, 16)
            .build());
  }

  @Override
  protected void init() {
    super.init();
    memberButtons.clear();
    editButtons.clear();
    if (menu.isWorker) {
      buildList =
          addRenderableWidget(
              Button.builder(
                      Component.literal("Build List"),
                      b -> {
                        warehousePopup = menu.snapshot.getInt("Job") != 2;
                        buildPopup = true;
                        buildScroll = 0;
                      })
                  .bounds(leftPos + 8, topPos + 116 + menu.cargoLayoutExtra, 158, 16)
                  .build());
    }
    if (menu.isWorker) {
      selectedJob = menu.worker == null ? 0 : menu.worker.job();
      professionSelect =
          addRenderableWidget(
              Button.builder(
                      Component.literal(WorkerJob.name(selectedJob) + " v"),
                      b -> jobList = !jobList)
                  .bounds(leftPos + 186, topPos + 148, 106, 18)
                  .build());
      applyJob =
          addRenderableWidget(
              Button.builder(
                      Component.literal("Apply Job"),
                      b -> {
                        if (menu.snapshot.getInt("Job") == 0) applySelectedJob();
                        else jobConfirmation = true;
                      })
                  .bounds(leftPos + 186, topPos + 174, 106, 18)
                  .build());
      editButtons.add(professionSelect);
      editButtons.add(applyJob);
      editButtons.add(button(186, 208, 106, "Start Work", 6));
    } else if (!menu.isTotem) {
      networkBuildLists =
          addRenderableWidget(
              Button.builder(
                      Component.literal("Build Lists"),
                      b -> {
                        selectedBuild = "";
                        projectScroll = buildScroll = 0;
                        buildPopup = true;
                      })
                  .bounds(leftPos + 186, topPos + 76, 106, 18)
                  .build());
      editButtons.add(button(8, 109, 77, "Call All Home", 1));
      editButtons.add(button(89, 109, 77, "Resume Work", 2));
      nameLabels = button(8, 126, 158, "Show Automaton Names: ON", 15);
      nameLabels.setHeight(13);
      editButtons.add(nameLabels);
    } else {
      addTab("Workers", 0, 186, 8, 52);
      addTab("Logistics", 3, 240, 8, 52);
      addRenderableWidget(
          Button.builder(
                  Component.literal("Worksite Settings"),
                  b -> {
                    professionPopup = true;
                    professionScroll = 0;
                  })
              .bounds(leftPos + 8, topPos + 80, 158, 20)
              .build());
      ranges =
          addRenderableWidget(
              Button.builder(
                      Component.literal("Show Work Area"),
                      b -> {
                        RangeOverlay.toggle(menu.station);
                        b.setMessage(
                            Component.literal(
                                RangeOverlay.shown(menu.station)
                                    ? "Hide Work Area"
                                    : "Show Work Area"));
                      })
                  .bounds(leftPos + 8, topPos + 116, 158, 18)
                  .build());
      ranges.setMessage(
          Component.literal(
              RangeOverlay.shown(menu.station) ? "Hide Work Area" : "Show Work Area"));
      for (int row = 0; row < 4; row++)
        memberButtons.add(button(186, 48 + row * 19, 106, "Worker", 100 + row));
      previous = button(186, 128, 28, "<", 9);
      next = button(264, 128, 28, ">", 10);
      back = button(186, 209, 48, "Back", 12);
      unbind = button(238, 209, 54, "Unbind", 11);
      find = button(186, 189, 106, "Find", 14);
      find.setTooltip(
          Tooltip.create(
              Component.literal(
                  "Highlight this Automaton for 30 seconds. Requires a loaded worker.")));
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    boolean editable = menu.snapshot.getBoolean("Editable");
    editButtons.forEach(b -> b.active = editable);
    if (buildList != null) buildList.visible = menu.snapshot.getInt("Job") >= 2;
    if (networkBuildLists != null)
      networkBuildLists.setMessage(
          Component.literal("Build Lists (" + activeBuilds().size() + ")"));
    if (applyJob != null) applyJob.active = editable && selectedJob != menu.snapshot.getInt("Job");
    if (professionSelect != null)
      professionSelect.setMessage(Component.literal(WorkerJob.name(selectedJob) + " v"));
    if (nameLabels != null)
      nameLabels.setMessage(
          Component.literal(
              "Show Automaton Names: " + (menu.snapshot.getBoolean("ShowNames") ? "ON" : "OFF")));
    boolean selected = menu.snapshot.getBoolean("HasSelected");
    ListTag members = menu.snapshot.getList("Members", 10);
    for (int i = 0; i < memberButtons.size(); i++) {
      Button b = memberButtons.get(i);
      b.visible = !selected && panel == 0;
      b.active = i < members.size();
      if (i < members.size()) {
        CompoundTag n = members.getCompound(i);
        String label =
            WorkerJob.name(n.getInt("Job")) + (n.getString("State").equals("Packed") ? " [P]" : "");
        String display = WorkerNames.recordName(n);
        if (display.isEmpty()) display = "Unverified worker";
        b.setMessage(
            Component.literal(
                font.width(display) <= 98
                    ? display
                    : font.plainSubstrByWidth(display, 89) + "..."));
        b.setTooltip(
            Tooltip.create(
                Component.literal(
                    display
                        + " | "
                        + label
                        + " | "
                        + n.getString("State")
                        + " | "
                        + n.getString("Status"))));
      } else b.setMessage(Component.literal("-"));
    }
    if (back != null) {
      back.visible = selected && panel == 0;
      unbind.visible = selected && panel == 0;
      unbind.active = editable;
      find.visible = selected && panel == 0;
      find.active = editable && menu.snapshot.getBoolean("Findable");
      previous.visible = !selected && panel == 0;
      next.visible = !selected && panel == 0;
    }
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff101010);
    g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xff555555);
    g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xffffffff);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xffc6c6c6);
    g.fill(leftPos + 178, topPos + 7, leftPos + 179, topPos + 227, 0xff8b8b8b);
    for (var slot : menu.slots) {
      g.fill(
          leftPos + slot.x - 1,
          topPos + slot.y - 1,
          leftPos + slot.x + 17,
          topPos + slot.y + 17,
          0xffffffff);
      g.fill(
          leftPos + slot.x,
          topPos + slot.y,
          leftPos + slot.x + 16,
          topPos + slot.y + 16,
          0xff8b8b8b);
      g.fill(
          leftPos + slot.x - 1,
          topPos + slot.y - 1,
          leftPos + slot.x + 16,
          topPos + slot.y,
          0xff373737);
      g.fill(
          leftPos + slot.x - 1,
          topPos + slot.y,
          leftPos + slot.x,
          topPos + slot.y + 16,
          0xff373737);
    }
    if (menu.isWorker && menu.snapshot.getInt("Job") != 0) {
      int x = 26 + 42 * menu.snapshot.getInt("Active");
      g.fill(leftPos + x - 1, topPos + 62, leftPos + x + 17, topPos + 64, 0xffffbc36);
    }
    if (!menu.isWorker && !menu.isTotem)
      LuminaHud.bar(
          g,
          leftPos + 187,
          topPos + 116,
          104,
          menu.snapshot.getInt("Lumina"),
          4,
          menu.snapshot.getInt("LuminaThirds"));
  }

  private void lines(GuiGraphics g, ListTag lines, int start, int end) {
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++)
      for (var text :
          font.split(
              lines.getString(i).startsWith("\u001e")
                  ? Component.Serializer.fromJson(lines.getString(i).substring(1))
                  : Component.literal(lines.getString(i)),
              104)) {
        StringBuilder out = new StringBuilder();
        text.accept(
            (a, b, c) -> {
              out.appendCodePoint(c);
              return true;
            });
        rows.add(out.toString());
      }
    int shown = Math.max(1, (end - start) / 10);
    scroll = Math.min(scroll, Math.max(0, rows.size() - shown));
    for (int i = scroll; i < Math.min(rows.size(), scroll + shown); i++) {
      String text = rows.get(i);
      g.drawString(
          font,
          text,
          187,
          start + (i - scroll) * 10,
          text.startsWith("!") ? 0xff8b3000 : 0xff404040,
          false);
    }
    if (rows.size() > shown) {
      int y = start + (end - start - 8) * scroll / Math.max(1, rows.size() - shown);
      g.fill(295, start, 296, end, 0xff66606c);
      g.fill(295, y, 297, y + 8, 0xffd8c1f0);
    }
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    g.drawString(font, font.plainSubstrByWidth(title.getString(), 164), 8, 9, 0xff404040, false);
    g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xff423d34, false);
    if (menu.isWorker) {
      g.drawString(
          font,
          menu.snapshot.getInt("Job") == 1
              ? "Axes - active slot marked"
              : menu.snapshot.getInt("Job") == 2
                  ? "Picks / shovels"
                  : menu.snapshot.getInt("Job") == 3
                      ? Component.translatable("automatons.runner.tools").getString()
                      : menu.snapshot.getInt("Job") == 4
                          ? "Hammers"
                          : menu.snapshot.getInt("Job") == 5
                              ? "Hoes"
                              : menu.snapshot.getInt("Job") == 6
                                  ? "Operator — no tools required"
                                  : "Tools inactive (Unassigned)",
          8,
          30,
          0xff443c2f,
          false);
      g.drawString(font, "Cargo", 8, 66, 0xff443c2f, false);
      lines(g, menu.snapshot.getList("Lines", 8), 10, 134);
    } else if (menu.isTotem) {
      g.drawString(
          font,
          menu.station == null ? "Worksite" : menu.station.purpose.label,
          8,
          30,
          0xff443c2f,
          false);
      g.drawString(
          font,
          "Work and chest range: " + (menu.station == null ? 0 : menu.station.workRange()),
          8,
          45,
          0xff443c2f,
          false);
      g.drawString(font, "All chests supply and store", 8, 60, 0xff443c2f, false);
      if (panel == 3) {
        lines(g, menu.snapshot.getList("LogisticsLines", 8), 36, 224);

      } else if (menu.snapshot.getBoolean("HasSelected")) {
        lines(g, menu.snapshot.getList("Details", 8), 48, 184);
      } else {
        g.drawCenteredString(font, "" + menu.snapshot.getInt("WorkerPage"), 239, 131, 0xff404040);
        lines(g, menu.snapshot.getList("Lines", 8), 151, 224);
      }
    } else {
      g.drawString(font, "Lumina", 187, 105, 0xff633b8b, false);
      g.drawString(
          font,
          Integer.toString(menu.snapshot.getInt("Lumina")) + " points",
          187,
          123,
          0xff404040,
          false);
      lines(g, menu.snapshot.getList("Lines", 8), 145, 224);
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double delta) {
    if (jobConfirmation || jobList) return true;
    if (professionPopup && !buildPopup) {
      professionScroll = Math.max(0, professionScroll - (int) Math.signum(delta));
      return true;
    }
    if (buildPopup) {
      if (networkBuildLists != null && selectedBuild.isEmpty()) {
        projectScroll =
            net.minecraft.util.Mth.clamp(
                projectScroll - (int) Math.signum(delta),
                0,
                Math.max(0, activeBuilds().size() - 5));
        return true;
      }
      buildScroll =
          net.minecraft.util.Mth.clamp(
              buildScroll - (int) Math.signum(delta), 0, Math.max(0, buildRows().size() - 6));
      return true;
    }
    if (x >= leftPos + 180 && x < leftPos + 300) {
      scroll = Math.max(0, scroll - (int) Math.signum(delta));
      return true;
    }
    return super.mouseScrolled(x, y, delta);
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    renderBackground(g);
    super.render(g, x, y, partial);
    if (professionPopup) renderProfessionPopup(g);
    if (jobList) renderJobList(g);
    if (jobConfirmation) renderJobConfirmation(g);
    if (buildPopup) {
      renderBuildList(g, x, y);
      return;
    }
    if (professionPopup || jobList || jobConfirmation) return;
    renderTooltip(g, x, y);
    if (!menu.isWorker
        && !menu.isTotem
        && hoveredSlot != null
        && hoveredSlot.index < 6
        && !hoveredSlot.hasItem()) {
      String[] names = {
        "Head", "Left arm", "Automaton Torso", "Right arm", "Left leg", "Right leg"
      };
      g.renderTooltip(font, Component.literal(names[hoveredSlot.index]), x, y);
    }
  }

  private ListTag buildRows() {
    if (networkBuildLists != null) return selectedBuild().getList("Rows", 10);
    return menu.snapshot.getList(warehousePopup ? "WarehouseBuild" : "MineBuild", 10);
  }

  private ListTag activeBuilds() {
    return menu.snapshot.getList("ActiveBuilds", 10);
  }

  private CompoundTag selectedBuild() {
    for (var value : activeBuilds()) {
      var project = (CompoundTag) value;
      if (project.getString("Key").equals(selectedBuild)) return project;
    }
    return new CompoundTag();
  }

  private void renderActiveBuilds(GuiGraphics g, int x, int y) {
    g.drawString(font, "Active Build Lists", x + 9, y + 9, 0xffffe89a, false);
    g.drawString(
        font, "Choose a project in this Pedestal's network", x + 9, y + 31, 0xff443c35, false);
    ListTag projects = activeBuilds();
    projectScroll = Math.min(projectScroll, Math.max(0, projects.size() - 5));
    if (projects.isEmpty())
      g.drawString(font, "No active construction projects", x + 12, y + 66, 0xff443c35, false);
    for (int row = 0; row < 5 && row + projectScroll < projects.size(); row++) {
      var project = projects.getCompound(row + projectScroll);
      int ry = y + 49 + row * 29;
      g.fill(x + 7, ry, x + 309, ry + 27, 0xffd8cdb8);
      g.drawString(
          font,
          font.plainSubstrByWidth(
              project.getString("Title") + "  (" + project.getString("Location") + ")", 287),
          x + 12,
          ry + 3,
          0xff302b28,
          false);
      g.drawString(
          font,
          font.plainSubstrByWidth(project.getString("Status"), 287),
          x + 12,
          ry + 15,
          0xff665441,
          false);
    }
    g.drawString(font, "Scroll for more  |  ESC to close", x + 9, y + 202, 0xff574d40, false);
  }

  private void addTab(String label, int value, int x, int y, int w) {
    addRenderableWidget(
        new Button(
            leftPos + x,
            topPos + y,
            w,
            20,
            Component.literal(label),
            b -> {
              panel = value;
              scroll = 0;
            },
            message -> message.get()) {
          @Override
          public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean selected = panel == value;
            g.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                selected ? 0xffe2d9c9 : 0xff99909f);
            g.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + 2,
                selected ? 0xff8661a6 : 0xff63556c);
            if (!selected)
              g.fill(
                  getX(),
                  getY() + getHeight() - 1,
                  getX() + getWidth(),
                  getY() + getHeight(),
                  0xff706676);
            g.drawCenteredString(
                font,
                getMessage(),
                getX() + getWidth() / 2,
                getY() + 6,
                selected ? 0xff302738 : 0xff403647);
          }
        });
  }

  private void renderJobList(GuiGraphics g) {
    g.pose().pushPose();
    g.pose().translate(0, 0, 350);
    for (int job = 0; job < 7; job++) {
      int y = topPos + 96 + job * 18;
      g.fill(leftPos + 186, y, leftPos + 292, y + 18, job == selectedJob ? 0xffbaa5ce : 0xffeee7dd);
      g.drawString(font, WorkerJob.name(job), leftPos + 191, y + 5, 0xff332a3b, false);
    }
    g.pose().popPose();
  }

  private void popupButton(GuiGraphics g, String text, int x, int y, int w, boolean active) {
    g.fill(x, y, x + w, y + 20, active ? 0xff675076 : 0xffb7afa5);
    g.drawCenteredString(font, text, x + w / 2, y + 6, active ? 0xfff7efff : 0xff635d57);
  }

  private void renderJobConfirmation(GuiGraphics g) {
    int x = (width - 300) / 2, y = (height - 146) / 2;
    g.pose().pushPose();
    g.pose().translate(0, 0, 380);
    g.fill(0, 0, width, height, 0xa0000000);
    g.fill(x, y, x + 300, y + 146, 0xffeee3ce);
    g.drawString(
        font,
        "Change profession to " + WorkerJob.name(selectedJob) + "?",
        x + 12,
        y + 12,
        0xff442d56,
        false);
    int row = 0;
    for (var line :
        font.split(
            Component.literal(
                "This will interrupt the Automaton and reset its current workload. Carried items"
                    + " are kept. Shared mines and buildings are preserved."),
            272)) g.drawString(font, line, x + 12, y + 38 + 12 * row++, 0xff563c2e, false);
    popupButton(g, "Cancel", x + 12, y + 112, 130, true);
    popupButton(g, "Confirm change", x + 158, y + 112, 130, menu.snapshot.getBoolean("Editable"));
    g.pose().popPose();
  }

  private int professionWidth() {
    return Math.min(420, width - 16);
  }

  private int professionHeight() {
    return Math.min(260, height - 16);
  }

  private void renderProfessionPopup(GuiGraphics g) {
    int w = professionWidth(), h = professionHeight(), x = (width - w) / 2, y = (height - h) / 2;
    g.pose().pushPose();
    g.pose().translate(0, 0, 300);
    g.fill(0, 0, width, height, 0xa0000000);
    g.fill(x, y, x + w, y + h, 0xffe5ddcf);
    g.fill(x, y, x + w, y + 26, 0xff3e3545);
    g.drawString(font, "Worksite Settings", x + 12, y + 9, 0xffffe89a, false);
    g.drawString(font, "X", x + w - 17, y + 9, 0xffffffff, false);
    String[] names = {"Forestry", "Mine", "Network", "Worksite", "Farm"};
    int tabWidth = (w - 24) / 5;
    for (int tab = 0; tab < 5; tab++) {
      int tx = x + 12 + tab * tabWidth;
      g.fill(tx, y + 31, tx + tabWidth - 2, y + 53, professionTab == tab ? 0xfff5ecda : 0xffb2a6b8);
      g.fill(tx, y + 31, tx + tabWidth - 2, y + 33, professionTab == tab ? 0xff8661a6 : 0xff827287);
      g.drawString(font, names[tab], tx + 8, y + 39, 0xff382c40, false);
    }
    ListTag lines =
        menu.snapshot.getList(
            professionTab == 0
                ? "ForestryLines"
                : professionTab == 1
                    ? "MiningLines"
                    : professionTab == 3
                        ? "WorksiteLines"
                        : professionTab == 4 ? "FarmLines" : "LogisticsLines",
            8);
    List<net.minecraft.util.FormattedCharSequence> rows = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String value = lines.getString(i);
      Component text =
          value.startsWith("\u001e")
              ? Component.Serializer.fromJson(value.substring(1))
              : Component.literal(value);
      rows.addAll(font.split(text, w - 36));
    }
    int shown =
        professionTab == 3 && !menu.snapshot.getBoolean("Configured")
            ? 2
            : Math.max(1, (h - 126) / 12);
    professionScroll = Math.min(professionScroll, Math.max(0, rows.size() - shown));
    for (int row = 0; row < shown && row + professionScroll < rows.size(); row++)
      g.drawString(
          font, rows.get(row + professionScroll), x + 12, y + 62 + row * 12, 0xff3d3541, false);
    if (rows.size() > shown) {
      g.fill(x + w - 10, y + 62, x + w - 7, y + h - 66, 0xffa398ae);
      int at = y + 62 + (h - 146) * professionScroll / Math.max(1, rows.size() - shown);
      g.fill(x + w - 10, at, x + w - 7, at + 18, 0xff6b537e);
    }
    boolean editable = menu.snapshot.getBoolean("Editable");
    int bw = (w - 30) / (professionTab == 1 ? 2 : 4);
    if (professionTab == 1 || professionTab == 3 && menu.snapshot.getBoolean("Configured")) {
      String[] controls =
          professionTab == 1
              ? new String[] {"Resume", "Pause"}
              : new String[] {"Resume", "Pause", "Fixed facing", "Repair"};
      for (int i = 0; i < controls.length; i++)
        popupButton(g, controls[i], x + 12 + i * (bw + 2), y + h - 55, bw, editable);
      popupButton(g, "Build List", x + 12, y + h - 29, 110, true);
    }
    if (professionTab == 3 && !menu.snapshot.getBoolean("Configured")) {
      for (int i = 1; i <= 5; i++) {
        int py = y + 97 + (i - 1) * 18;
        g.fill(x + 12, py, x + w - 12, py + 17, selectedPurpose == i ? 0xffaaa0b6 : 0xffc6c6c6);
        g.drawString(font, WorksitePurpose.of(i).label, x + 18, py + 4, 0xff303030, false);
      }
      popupButton(
          g,
          "Facing: " + menu.snapshot.getString("Orientation"),
          x + 12,
          y + h - 29,
          144,
          editable);
      popupButton(g, "Create Worksite", x + w - 158, y + h - 29, 146, editable);
    }
    if (professionTab == 4) {
      popupButton(g, "Crop preference", x + 12, y + h - 55, 144, editable);
      popupButton(g, "Toggle bone meal", x + w - 158, y + h - 55, 146, editable);
      popupButton(g, "Shed Build List", x + 12, y + h - 29, 130, true);
    }
    if (professionTab == 0 || professionTab == 1)
      popupButton(g, "Toggle area overlay", x + w - 168, y + h - 29, 156, true);
    g.pose().popPose();
  }

  private void clickProfessionPopup(double mx, double my) {
    int w = professionWidth(), h = professionHeight(), x = (width - w) / 2, y = (height - h) / 2;
    if (mx >= x + w - 28 && mx < x + w && my >= y && my < y + 26) {
      professionPopup = false;
      return;
    }
    if (my >= y + 31 && my < y + 53 && mx >= x + 12 && mx < x + w - 12) {
      professionTab = Math.min(4, (int) (mx - x - 12) / ((w - 24) / 5));
      professionScroll = 0;
      return;
    }
    if (professionTab == 3 && !menu.snapshot.getBoolean("Configured")) {
      if (mx >= x + 12 && mx < x + w - 12 && my >= y + 97 && my < y + 187)
        selectedPurpose = Math.min(5, 1 + (int) (my - y - 97) / 18);
      if (my >= y + h - 29 && my < y + h - 9 && menu.snapshot.getBoolean("Editable")) {
        if (mx >= x + 12 && mx < x + 156)
          minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 66);
        if (mx >= x + w - 158 && mx < x + w - 12)
          minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 60 + selectedPurpose);
      }
      return;
    }
    if (professionTab == 4
        && my >= y + h - 55
        && my < y + h - 35
        && menu.snapshot.getBoolean("Editable")) {
      if (mx >= x + 12 && mx < x + 156)
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 71);
      if (mx >= x + w - 158 && mx < x + w - 12)
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 70);
      return;
    }
    int bw = (w - 30) / (professionTab == 1 ? 2 : 4);
    if ((professionTab == 1 || professionTab == 3 && menu.snapshot.getBoolean("Configured"))
        && menu.snapshot.getBoolean("Editable")
        && my >= y + h - 55
        && my < y + h - 35
        && mx >= x + 12
        && mx < x + w - 12) {
      int index = Math.min(professionTab == 1 ? 1 : 3, (int) (mx - x - 12) / (bw + 2));
      minecraft.gameMode.handleInventoryButtonClick(
          menu.containerId, (professionTab == 1 ? 32 : 40) + index);
    }
    if (my >= y + h - 29 && my < y + h - 9) {
      if (professionTab != 0 && mx >= x + 12 && mx < x + 122) {
        warehousePopup = professionTab != 1;
        buildPopup = true;
        buildScroll = 0;
      }
      if (professionTab < 2 && mx >= x + w - 168 && mx < x + w - 12) {
        if (professionTab == 0) RangeOverlay.forestry = !RangeOverlay.forestry;
        else RangeOverlay.mining = !RangeOverlay.mining;
        if (!RangeOverlay.shown(menu.station)) RangeOverlay.toggle(menu.station);
      }
    }
  }

  private void renderBuildList(GuiGraphics g, int mouseX, int mouseY) {
    int x = (width - 320) / 2, y = (height - 216) / 2;
    g.pose().pushPose();
    g.pose().translate(0, 0, 400);
    g.fill(0, 0, width, height, 0xa0000000);
    g.fill(x, y, x + 320, y + 216, 0xffeadfc8);
    g.fill(x + 1, y + 1, x + 319, y + 25, 0xff3e3545);
    g.drawString(font, "X", x + 305, y + 9, 0xffffffff, false);
    if (networkBuildLists != null && (selectedBuild.isEmpty() || selectedBuild().isEmpty())) {
      selectedBuild = "";
      renderActiveBuilds(g, x, y);
      g.pose().popPose();
      return;
    }
    g.drawString(
        font,
        networkBuildLists != null
            ? selectedBuild().getString("Title") + " — Build List"
            : warehousePopup
                ? menu.snapshot.getString("BuildTitle")
                : "Mine Infrastructure — Build List",
        x + 9,
        y + 9,
        0xffffe89a,
        false);
    g.drawString(font, "X", x + 305, y + 9, 0xffffffff, false);
    if (networkBuildLists != null && !selectedBuild().getBoolean("Loaded")) {
      g.drawString(font, "Worksite unloaded", x + 12, y + 52, 0xff443c35, false);
      g.drawString(
          font, "Material counts update when its chunks load.", x + 12, y + 69, 0xff443c35, false);
      g.drawString(font, "< Back", x + 9, y + 202, 0xff574d40, false);
      g.pose().popPose();
      return;
    }
    g.drawString(font, "Available: worker cargo + supply chests", x + 9, y + 31, 0xff443c35, false);
    g.drawString(font, "Block", x + 9, y + 48, 0xff443c35, false);
    String[] labels = {"Total", "Left", "Have", "Need"};
    for (int col = 0; col < 4; col++)
      g.drawString(font, labels[col], x + 169 + 35 * col, y + 48, 0xff443c35, false);
    ListTag rows = buildRows();
    buildScroll = Math.min(buildScroll, Math.max(0, rows.size() - 6));
    for (int row = 0; row < 6 && row + buildScroll < rows.size(); row++) {
      CompoundTag data = rows.getCompound(row + buildScroll);
      var item = net.minecraft.world.item.ItemStack.of(data.getCompound("Item"));
      int ry = y + 63 + row * 22;
      g.fill(x + 5, ry - 2, x + 309, ry + 19, row % 2 == 0 ? 0xffd8cdb8 : 0xffe4d9c2);
      g.renderItem(item, x + 9, ry);
      g.drawString(
          font,
          font.plainSubstrByWidth(item.getHoverName().getString(), 132),
          x + 30,
          ry + 4,
          0xff302b28,
          false);
      String[] keys = {"Total", "Left", "Have", "Missing"};
      for (int col = 0; col < 4; col++)
        g.drawString(
            font,
            Integer.toString(data.getInt(keys[col])),
            x + 169 + 35 * col,
            ry + 4,
            col == 3 ? data.getInt("Missing") > 0 ? 0xff9d281c : 0xff286234 : 0xff302b28,
            false);
      if (mouseX >= x + 9 && mouseX < x + 164 && mouseY >= ry && mouseY < ry + 18)
        g.renderTooltip(font, item.getHoverName(), mouseX, mouseY);
    }
    if (rows.size() > 6) {
      g.fill(x + 312, y + 61, x + 316, y + 195, 0xffa89b84);
      int thumb = y + 61 + 110 * buildScroll / Math.max(1, rows.size() - 6);
      g.fill(x + 312, thumb, x + 316, thumb + 24, 0xff6a546f);
    }
    g.drawString(
        font,
        networkBuildLists != null
            ? "< Back     Scroll for more  |  ESC to close"
            : "Scroll for more  |  ESC to close",
        x + 9,
        y + 202,
        0xff574d40,
        false);
    g.pose().popPose();
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (!buildPopup) {
      if (jobConfirmation) {
        int px = (width - 300) / 2, py = (height - 146) / 2;
        if (y >= py + 112 && y < py + 134) {
          if (x >= px + 12 && x < px + 142) jobConfirmation = false;
          if (x >= px + 158 && x < px + 288 && menu.snapshot.getBoolean("Editable")) {
            applySelectedJob();
          }
        }
        return true;
      }
      if (jobList) {
        if (x >= leftPos + 186 && x < leftPos + 292 && y >= topPos + 96 && y < topPos + 222)
          selectedJob = Math.min(6, (int) ((y - topPos - 96) / 18));
        jobList = false;
        return true;
      }
      if (professionPopup) {
        clickProfessionPopup(x, y);
        return true;
      }
      return super.mouseClicked(x, y, button);
    }
    int px = (width - 320) / 2, py = (height - 216) / 2;
    if (x >= px + 299 && x < px + 320 && y >= py && y < py + 25) buildPopup = false;
    if (networkBuildLists != null) {
      if (selectedBuild.isEmpty() && x >= px + 7 && x < px + 309 && y >= py + 49 && y < py + 194) {
        int row = (int) (y - py - 49) / 29 + projectScroll;
        if (row < activeBuilds().size()) {
          selectedBuild = activeBuilds().getCompound(row).getString("Key");
          buildScroll = 0;
        }
      } else if (!selectedBuild.isEmpty()
          && x >= px + 9
          && x < px + 54
          && y >= py + 198
          && y < py + 216) {
        selectedBuild = "";
      }
    }
    return true;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    return buildPopup
        || professionPopup
        || jobList
        || jobConfirmation
        || super.mouseReleased(x, y, button);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    return buildPopup
        || professionPopup
        || jobList
        || jobConfirmation
        || super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (!buildPopup) {
      if (jobConfirmation || jobList || professionPopup) {
        if (key == 256 || minecraft.options.keyInventory.matches(key, scan)) {
          jobConfirmation = false;
          jobList = false;
          professionPopup = false;
        }
        return true;
      }
      return super.keyPressed(key, scan, modifiers);
    }
    if (key == 256 || minecraft.options.keyInventory.matches(key, scan)) {
      if (networkBuildLists != null && !selectedBuild.isEmpty()) selectedBuild = "";
      else buildPopup = false;
    }
    if (key == 264 || key == 267) mouseScrolled(0, 0, -1);
    if (key == 265 || key == 266) mouseScrolled(0, 0, 1);
    return true;
  }
}
