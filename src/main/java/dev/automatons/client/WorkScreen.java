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
  private final List<Button> chestButtons = new ArrayList<>(),
      memberButtons = new ArrayList<>(),
      editButtons = new ArrayList<>();
  private Button previous, next, back, unbind, ranges;
  private int scroll;

  public WorkScreen(WorkMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 300;
    imageHeight = 234;
    inventoryLabelY = 140;
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
    chestButtons.clear();
    memberButtons.clear();
    editButtons.clear();
    if (menu.isWorker) {
      editButtons.add(button(186, 158, 40, "None", 7));
      editButtons.add(button(228, 158, 64, "Lumberton", 8));
      editButtons.add(button(186, 181, 106, "Start Work", 6));
      editButtons.add(button(186, 201, 106, "Rebind", 3));
    } else if (!menu.isTotem) {
      editButtons.add(button(8, 112, 77, "Call All Home", 1));
      editButtons.add(button(89, 112, 77, "Resume Work", 2));
    } else {
      for (int row = 0; row < 3; row++) {
        chestButtons.add(button(89, 66 + row * 18, 36, "Out", 20 + row * 2));
        chestButtons.add(button(129, 66 + row * 18, 37, "Tool", 21 + row * 2));
      }
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
                  .bounds(leftPos + 82, topPos + 123, 88, 16)
                  .build());
      ranges.setTooltip(
          Tooltip.create(
              Component.literal(
                  "Green: logging | Blue: storage. Terrain markers are one air block high.")));
      ranges.setMessage(
          Component.literal(
              RangeOverlay.shown(menu.station) ? "Hide Work Area" : "Show Work Area"));
      button(8, 123, 28, "<", 4);
      button(51, 123, 28, ">", 5);
      for (int row = 0; row < 4; row++)
        memberButtons.add(button(186, 29 + row * 19, 106, "Worker", 100 + row));
      previous = button(186, 109, 28, "<", 9);
      next = button(264, 109, 28, ">", 10);
      back = button(186, 209, 48, "Back", 12);
      unbind = button(238, 209, 54, "Unbind", 11);
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    boolean editable = menu.snapshot.getBoolean("Editable");
    editButtons.forEach(b -> b.active = editable);
    ListTag chests = menu.snapshot.getList("Chests", 10);
    for (int i = 0; i < chestButtons.size(); i++) {
      int row = i / 2;
      Button b = chestButtons.get(i);
      b.active = editable && row < chests.size();
      int flags = row < chests.size() ? chests.getCompound(row).getInt("Flags") : 0;
      b.setMessage(
          Component.literal(
              (i % 2 == 0 ? "Out" : "Tool") + ((flags & (i % 2 == 0 ? 1 : 2)) != 0 ? " +" : " -")));
    }
    boolean selected = menu.snapshot.getBoolean("HasSelected");
    ListTag members = menu.snapshot.getList("Members", 10);
    for (int i = 0; i < memberButtons.size(); i++) {
      Button b = memberButtons.get(i);
      b.visible = !selected;
      b.active = i < members.size();
      if (i < members.size()) {
        CompoundTag n = members.getCompound(i);
        String label =
            (n.getInt("Job") == 1 ? "Lumberton" : "Unassigned")
                + (n.getString("State").equals("Packed") ? " [P]" : "");
        String id = n.hasUUID("Id") ? n.getUUID("Id").toString().substring(0, 4) : "????";
        String display = id + " " + label;
        if (n.contains("Name")) {
          Component name = Component.Serializer.fromJson(n.getString("Name"));
          if (name != null) display = name.getString() + " " + id;
        }
        b.setMessage(Component.literal(font.plainSubstrByWidth(display, 98)));
        b.setTooltip(
            Tooltip.create(
                Component.literal(
                    display + " - " + n.getString("State") + " - " + n.getString("Status"))));
      } else b.setMessage(Component.literal("-"));
    }
    if (back != null) {
      back.visible = selected;
      unbind.visible = selected;
      unbind.active = editable;
      previous.visible = !selected;
      next.visible = !selected;
    }
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff272c2c);
    g.fill(leftPos + 2, topPos + 2, leftPos + 176, topPos + imageHeight - 2, 0xffc9c2ae);
    g.fill(leftPos + 180, topPos + 2, leftPos + 298, topPos + 232, 0xff36333f);
    g.fill(leftPos + 2, topPos + 23, leftPos + 176, topPos + 25, 0xffa1783d);
    for (var slot : menu.slots) {
      g.fill(
          leftPos + slot.x - 1,
          topPos + slot.y - 1,
          leftPos + slot.x + 17,
          topPos + slot.y + 17,
          0xff675f51);
      g.fill(
          leftPos + slot.x,
          topPos + slot.y,
          leftPos + slot.x + 16,
          topPos + slot.y + 16,
          0xff8e8777);
    }
    if (menu.isWorker && menu.snapshot.getInt("Job") == 1) {
      int x = 26 + 42 * menu.snapshot.getInt("Active");
      g.fill(leftPos + x - 1, topPos + 62, leftPos + x + 17, topPos + 64, 0xffffbc36);
    }
    if (!menu.isWorker && !menu.isTotem)
      MagicaHud.bar(
          g,
          leftPos + 187,
          topPos + 87,
          104,
          menu.snapshot.getInt("Magica"),
          4,
          menu.snapshot.getInt("MagicaThirds"));
  }

  private void lines(GuiGraphics g, ListTag lines, int start, int end) {
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++)
      for (var text : font.split(Component.literal(lines.getString(i)), 104)) {
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
          text.startsWith("!") ? 0xffffc861 : 0xffeee8f6,
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
    g.drawString(font, title, 8, 9, 0xff312719, false);
    g.drawString(font, playerInventoryTitle, 8, 140, 0xff423d34, false);
    if (menu.isWorker) {
      g.drawString(
          font,
          menu.snapshot.getInt("Job") == 1
              ? "Axes - active slot marked"
              : "Tools inactive (Unassigned)",
          8,
          30,
          0xff443c2f,
          false);
      g.drawString(font, "Cargo", 8, 66, 0xff443c2f, false);
      lines(g, menu.snapshot.getList("Lines", 8), 10, 150);
    } else if (menu.isTotem) {
      g.drawString(font, "Shared upgrades", 8, 30, 0xff443c2f, false);
      ListTag chests = menu.snapshot.getList("Chests", 10);
      for (int i = 0; i < chests.size(); i++)
        g.drawString(
            font, chests.getCompound(i).getString("Pos"), 8, 70 + i * 18, 0xff443c2f, false);
      g.drawCenteredString(font, "" + menu.snapshot.getInt("Page"), 44, 126, 0xff443c2f);
      if (menu.snapshot.getBoolean("HasSelected")) {
        g.drawString(font, "Worker details", 187, 10, 0xffeee8f6, false);
        lines(g, menu.snapshot.getList("Details", 8), 30, 200);
      } else {
        g.drawString(
            font, "Workers: " + menu.snapshot.getInt("WorkerCount"), 187, 10, 0xffeee8f6, false);
        g.drawCenteredString(font, "" + menu.snapshot.getInt("WorkerPage"), 239, 112, 0xffeee8f6);
        lines(g, menu.snapshot.getList("Lines", 8), 139, 224);
      }
    } else {
      if (net.minecraftforge.fml.ModList.get().isLoaded("jei"))
        g.drawString(
            font, Component.translatable("jei.automatons.recipes"), 130, 9, 0xff654582, false);
      g.drawCenteredString(font, "Result", 238, 30, 0xffeee8f6);
      g.drawCenteredString(font, "Take to assemble", 238, 69, 0xffeee8f6);
      g.drawString(font, "Magica", 187, 77, 0xffe8d7ff, false);
      g.drawString(
          font,
          Integer.toString(menu.snapshot.getInt("Magica")) + " points",
          187,
          94,
          0xffeee8f6,
          false);
      lines(g, menu.snapshot.getList("Lines", 8), 116, 224);
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double delta) {
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
}
