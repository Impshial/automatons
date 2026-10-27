package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
  private EditBox name;
  private ProgramEditor editor;
  private final List<AbstractWidget> controls = new ArrayList<>(),
      program = new ArrayList<>(),
      storage = new ArrayList<>();
  private String validation = "";
  private int directionIndex;
  private EditBox distance;
  private Button direction;
  private String loadedProgram;
  private String observedText = "";
  private ListTag choices;
  private int choiceAction, choiceScroll;

  public MachineScreen(MachineMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 344;
    imageHeight = 280;
    inventoryLabelY = 166;
  }

  private void send(int action, String value, String label) {
    validation = "";
    Network.CHANNEL.sendToServer(
        new MachineActions.Action(menu.machine.getId(), action, value, label));
  }

  private Button button(
      int x, int y, int w, String label, Runnable action, List<AbstractWidget> group) {
    Button b =
        addRenderableWidget(
            Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(leftPos + x, topPos + y, w, 20)
                .build());
    if (group != null) group.add(b);
    return b;
  }

  @Override
  protected void init() {
    super.init();
    controls.clear();
    program.clear();
    storage.clear();
    for (int i = 0; i < 3; i++) {
      final int tab = i;
      button(
          8 + i * 110,
          20,
          108,
          new String[] {"Controls", "Program", "Storage"}[i],
          () -> {
            menu.tab = tab;
            send(13, "" + tab, "");
            visibility();
          },
          null);
    }
    button(
        190,
        45,
        145,
        "Mode",
        () -> {
          String mode = menu.snapshot.getString("Mode");
          send(
              0, mode.equals("MANUAL") ? "CHUNK" : mode.equals("CHUNK") ? "PROGRAM" : "MANUAL", "");
        },
        controls);
    button(190, 68, 145, "Start / Stop engine", () -> send(1, "", ""), controls);
    button(190, 91, 71, "Start Work", () -> send(2, "", ""), controls);
    button(264, 91, 71, "Pause", () -> send(3, "", ""), controls);
    button(190, 114, 71, "Resume", () -> send(4, "", ""), controls);
    button(264, 114, 71, "Cancel Job", () -> send(5, "", ""), controls);
    button(8, 132, 174, "Select pedestal", () -> choose("Homes", 11), controls);
    button(190, 137, 145, "Select current chunk", () -> send(14, "", ""), controls);
    button(190, 178, 145, "Assign operator", () -> choose("Operators", 9), controls);
    button(190, 201, 145, "Release operator", () -> send(10, "", ""), controls);
    button(190, 224, 145, "Board empty seat", () -> send(15, "", ""), controls);
    name =
        addRenderableWidget(
            new EditBox(
                font, leftPos + 30, topPos + 44, 201, 18, Component.literal("Program name")));
    name.setMaxLength(80);
    name.setValue(
        menu.machine.equipment.getStackInSlot(1).getOrCreateTag().getString("ProgramName"));
    program.add(name);
    editor = addRenderableWidget(new ProgramEditor(font, leftPos + 8, topPos + 66, 223, 86));
    editor.setValue(menu.machine.equipment.getStackInSlot(1).getOrCreateTag().getString("Text"));
    program.add(editor);
    button(240, 44, 46, "Save", () -> send(6, editor.getValue(), name.getValue()), program);
    button(
        289,
        44,
        46,
        "Check",
        () ->
            validation =
                MachineProgram.parse(editor.getValue()).valid()
                    ? "Program valid"
                    : MachineProgram.parse(editor.getValue()).error(),
        program);
    direction =
        button(
            240,
            68,
            46,
            "N",
            () -> {
              directionIndex = (directionIndex + 1) % 4;
              direction.setMessage(
                  Component.literal(new String[] {"N", "E", "S", "W"}[directionIndex]));
            },
            program);
    distance =
        addRenderableWidget(
            new EditBox(
                font,
                leftPos + 290,
                topPos + 69,
                43,
                18,
                Component.literal("Distance or seconds")));
    distance.setValue("10");
    distance.setMaxLength(4);
    program.add(distance);
    button(
        240,
        91,
        46,
        "Move",
        () -> append(direction.getMessage().getString() + distance.getValue()),
        program);
    button(289, 91, 46, "Turn", () -> append(direction.getMessage().getString()), program);
    button(
        240,
        114,
        46,
        "Wait",
        () -> {
          try {
            append(".".repeat(Math.max(1, Math.min(300, Integer.parseInt(distance.getValue())))));
          } catch (NumberFormatException ignored) {
            validation = "Enter a whole number of seconds";
          }
        },
        program);
    button(289, 114, 46, "Drop off", () -> append("DROPOFF"), program);
    button(
        240,
        137,
        95,
        "Save & Start Work",
        () -> {
          send(0, "PROGRAM", "");
          send(6, editor.getValue(), name.getValue());
          send(2, "", "");
        },
        program);
    button(190, 178, 71, "Pause", () -> send(3, "", ""), program);
    button(264, 178, 71, "Resume", () -> send(4, "", ""), program);
    button(190, 201, 145, "Cancel Job", () -> send(5, "", ""), program);
    button(190, 69, 145, "Previous cargo rows", () -> page(0), storage);
    button(190, 92, 145, "Next cargo rows", () -> page(1), storage);
    visibility();
  }

  private void choose(String key, int action) {
    ListTag options = menu.snapshot.getList(key, 10);
    if (options.isEmpty()) {
      validation = "No eligible " + (action == 11 ? "pedestal" : "Operatorton") + " nearby";
      return;
    }
    choices = options.copy();
    choiceAction = action;
    choiceScroll = 0;
  }

  private void page(int page) {
    menu.page = page;
    send(12, "" + page, "");
  }

  private void append(String instruction) {
    editor.insert(instruction + "\n");
  }

  private void visibility() {
    controls.forEach(w -> w.visible = menu.tab == 0);
    program.forEach(w -> w.visible = menu.tab == 1);
    storage.forEach(w -> w.visible = menu.tab == 2);
  }

  @Override
  public void containerTick() {
    super.containerTick();
    boolean locked = menu.snapshot.getBoolean("Locked");
    name.setEditable(!locked);
    editor.active = !locked;
    if (!observedText.equals(editor.getValue())) {
      observedText = editor.getValue();
      validation = "";
    }
    var card = menu.machine.equipment.getStackInSlot(1);
    String signature = card.isEmpty() ? "" : card.getOrCreateTag().toString();
    if (!Objects.equals(signature, loadedProgram)) {
      loadedProgram = signature;
      String label = card.isEmpty() ? "" : card.getOrCreateTag().getString("ProgramName");
      String source = card.isEmpty() ? "" : card.getOrCreateTag().getString("Text");
      if (!name.getValue().equals(label)) name.setValue(label);
      if (!editor.getValue().equals(source)) editor.setValue(source);
    }
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
    g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + 17, 0xffe2e2e2);
    for (var slot : menu.slots)
      if (slot.isActive()) {
        g.fill(
            leftPos + slot.x - 1,
            topPos + slot.y - 1,
            leftPos + slot.x + 17,
            topPos + slot.y + 17,
            0xff373737);
        g.fill(
            leftPos + slot.x,
            topPos + slot.y,
            leftPos + slot.x + 16,
            topPos + slot.y + 16,
            0xff8b8b8b);
      }
    if (menu.tab == 0) {
      text(g, "Program", 30, 49);
      text(g, "Attachment", 30, 77);
      text(g, "Refuel container", 30, 107);
      text(g, menu.snapshot.getString("Mode") + " / " + menu.snapshot.getString("Engine"), 8, 157);
      text(g, "Lumina: " + menu.snapshot.getInt("Fuel") / 3.0, 30, 120);
      text(g, "Range: 48 blocks", 190, 249);
      text(
          g,
          menu.snapshot.hasUUID("HomeId")
              ? "Home: "
                  + net.minecraft.core.BlockPos.of(menu.snapshot.getLong("Home")).toShortString()
              : "Home: unlinked",
          190,
          160);
    } else if (menu.tab == 1) {
      text(
          g,
          "Instruction "
              + (menu.snapshot.getInt("Instruction") + 1)
              + " · "
              + menu.snapshot.getInt("Remaining")
              + " blocks left",
          8,
          156);
      drawRoute(g);
    } else {
      text(g, "Cargo " + menu.snapshot.getInt("Used") + " / 108 slots", 190, 49);
      text(g, "Rows " + (menu.page * 6 + 1) + "–" + (menu.page * 6 + 6) + " of 12", 190, 120);
      g.fill(leftPos + 177, topPos + 50, leftPos + 182, topPos + 158, 0xff6b6b6b);
      g.fill(
          leftPos + 177,
          topPos + 50 + menu.page * 54,
          leftPos + 182,
          topPos + 104 + menu.page * 54,
          0xffeee6fa);
    }
    String status = validation.isEmpty() ? menu.snapshot.getString("Status") : validation;
    g.drawString(
        font,
        font.plainSubstrByWidth(
            menu.tab == 1 && validation.isEmpty() ? editor.description() : status, 328),
        leftPos + 8,
        topPos + 266,
        0xff4a315d,
        false);
  }

  private void text(GuiGraphics g, String s, int x, int y) {
    g.drawString(font, s, leftPos + x, topPos + y, 0xff333333, false);
  }

  private void drawRoute(GuiGraphics g) {
    var parsed = MachineProgram.parse(editor.getValue());
    if (!parsed.valid()) return;
    List<int[]> route = new ArrayList<>();
    int x = 0, z = 0, max = 1;
    route.add(new int[] {0, 0});
    for (var step : parsed.instructions()) {
      if (step.kind() == MachineProgram.Kind.MOVE) {
        x += step.direction().getStepX() * step.amount();
        z += step.direction().getStepZ() * step.amount();
      }
      if (step.kind() == MachineProgram.Kind.DROPOFF) {
        x = z = 0;
      }
      route.add(new int[] {x, z});
      max = Math.max(max, Math.max(Math.abs(x), Math.abs(z)));
    }
    double scale = Math.min(3, 17.0 / max);
    int cx = leftPos + 214, cy = topPos + 237;
    for (int i = 1; i < route.size(); i++) {
      int[] a = route.get(i - 1), b = route.get(i);
      int x0 = cx + (int) (a[0] * scale),
          x1 = cx + (int) (b[0] * scale),
          y0 = cy + (int) (a[1] * scale),
          y1 = cy + (int) (b[1] * scale);
      int width = Math.max(1, (int) (3 * scale / 2));
      g.fill(
          Math.min(x0, x1) - width,
          Math.min(y0, y1) - width,
          Math.max(x0, x1) + width + 1,
          Math.max(y0, y1) + width + 1,
          0xff9575b1);
    }
    text(g, "Route preview", 245, 228);
    text(g, "Terrain may block", 245, 240);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double delta) {
    if (choices != null) {
      choiceScroll =
          Math.max(
              0, Math.min(Math.max(0, choices.size() - 6), choiceScroll + (delta < 0 ? 1 : -1)));
      return true;
    }
    if (menu.tab == 2 && x < leftPos + 185 && y < topPos + 164) {
      page(delta < 0 ? 1 : 0);
      return true;
    }
    return super.mouseScrolled(x, y, delta);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (choices != null) {
      if (key == 256) choices = null;
      return true;
    }
    if (menu.tab == 1 && (name.isFocused() || editor.isFocused()) && key != 256) {
      getFocused().keyPressed(key, scan, modifiers);
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    renderBackground(g);
    super.render(g, x, y, partial);
    if (choices != null) {
      int px = (width - 230) / 2, py = (height - 174) / 2;
      g.pose().pushPose();
      g.pose().translate(0, 0, 400);
      g.fill(0, 0, width, height, 0x99000000);
      g.fill(px, py, px + 230, py + 174, 0xffc6c6c6);
      g.drawString(
          font,
          choiceAction == 11 ? "Select home Pedestal" : "Assign Operatorton",
          px + 9,
          py + 9,
          0xff333333,
          false);
      g.drawString(font, "×", px + 215, py + 9, 0xff333333, false);
      for (int row = 0; row < 6 && row + choiceScroll < choices.size(); row++) {
        int y0 = py + 26 + row * 22;
        boolean hovered = x >= px + 6 && x < px + 224 && y >= y0 && y < y0 + 20;
        g.fill(px + 6, y0, px + 224, y0 + 20, hovered ? 0xff9d86b6 : 0xff888888);
        g.drawString(
            font,
            font.plainSubstrByWidth(choices.getCompound(row + choiceScroll).getString("Name"), 202),
            px + 12,
            y0 + 6,
            0xffffffff,
            false);
      }
      g.drawString(
          font,
          choices.size() > 6 ? "Scroll for more · Esc to close" : "Choose a row · Esc to close",
          px + 9,
          py + 162,
          0xff4a315d,
          false);
      g.pose().popPose();
      return;
    }
    renderTooltip(g, x, y);
    if (y >= topPos + 260 && y <= topPos + 280 && x >= leftPos && x <= leftPos + imageWidth)
      g.renderTooltip(
          font,
          font.split(
              Component.literal(
                  menu.tab == 1 && validation.isEmpty()
                      ? editor.description()
                      : validation.isEmpty() ? menu.snapshot.getString("Status") : validation),
              280),
          x,
          y);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (choices == null) return super.mouseClicked(x, y, button);
    int px = (width - 230) / 2, py = (height - 174) / 2;
    if (x < px || x > px + 230 || y < py || y > py + 174 || x > px + 208 && y < py + 24) {
      choices = null;
      return true;
    }
    int row = (int) (y - py - 26) / 22;
    if (button == 0
        && x >= px + 6
        && x < px + 224
        && y >= py + 26
        && row >= 0
        && row < 6
        && row + choiceScroll < choices.size()) {
      var selected = choices.getCompound(row + choiceScroll);
      send(choiceAction, selected.getUUID("Id").toString(), "");
      choices = null;
    }
    return true;
  }
}
