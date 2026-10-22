package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WorkScreen extends AbstractContainerScreen<WorkMenu> {
  private final List<Button> chestButtons = new ArrayList<>();
  private Button create;

  public WorkScreen(WorkMenu m, Inventory inv, Component title) {
    super(m, inv, title);
    imageWidth = 358;
    imageHeight = 234;
    inventoryLabelY = 140;
  }

  private Button button(int x, int y, int width, String title, int action) {
    return addRenderableWidget(
        Button.builder(
                Component.literal(title),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action))
            .bounds(leftPos + x, topPos + y, width, 16)
            .build());
  }

  @Override
  protected void init() {
    super.init();
    chestButtons.clear();
    if (menu.isWorker) button(8, 112, 158, "Rebind missing home", 3);
    else if (!menu.isTotem) {
      create = button(8, 72, 158, "Create Lumberton", 0);
      button(8, 96, 77, "Call home", 1);
      button(89, 96, 77, "Resume", 2);
    } else {
      for (int row = 0; row < 4; row++) {
        chestButtons.add(button(89, 64 + row * 18, 36, "Out", 20 + row * 2));
        chestButtons.add(button(129, 64 + row * 18, 37, "Tool", 21 + row * 2));
      }
      button(8, 119, 28, "<", 4);
      button(51, 119, 28, ">", 5);
    }
  }

  @Override
  public void containerTick() {
    super.containerTick();
    if (create != null)
      create.active = menu.snapshot.getBoolean("Ready") && menu.snapshot.getBoolean("Editable");
    ListTag chests = menu.snapshot.getList("Chests", 10);
    for (int i = 0; i < chestButtons.size(); i++) {
      int row = i / 2;
      Button b = chestButtons.get(i);
      b.active = row < chests.size() && menu.snapshot.getBoolean("Editable");
      int flag = row < chests.size() ? chests.getCompound(row).getInt("Flags") : 0;
      String title = i % 2 == 0 ? "Out" : "Tool";
      b.setMessage(Component.literal(title + ((flag & (i % 2 == 0 ? 1 : 2)) != 0 ? " +" : " -")));
    }
  }

  @Override
  protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff272c2c);
    g.fill(leftPos + 2, topPos + 2, leftPos + 176, topPos + imageHeight - 2, 0xffc9c2ae);
    g.fill(
        leftPos + 180, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xff363c3b);
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
    if (menu.isWorker) {
      int x = 26 + 42 * menu.snapshot.getInt("Active");
      g.fill(leftPos + x - 1, topPos + 62, leftPos + x + 17, topPos + 64, 0xffffbc36);
    }
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 8, 9, 0xff312719, false);
    g.drawString(font, playerInventoryTitle, 8, 140, 0xff423d34, false);
    if (menu.isWorker) {
      g.drawString(font, "Axes (active slot underlined)", 8, 30, 0xff443c2f, false);
      g.drawString(font, "Cargo", 8, 72, 0xff443c2f, false);
    } else if (menu.isTotem) {
      g.drawString(font, "Four universal upgrade slots", 8, 30, 0xff443c2f, false);
      ListTag chests = menu.snapshot.getList("Chests", 10);
      for (int i = 0; i < chests.size(); i++)
        g.drawString(
            font, chests.getCompound(i).getString("Pos"), 8, 69 + i * 18, 0xff443c2f, false);
      g.drawCenteredString(font, "" + menu.snapshot.getInt("Page"), 44, 122, 0xff443c2f);
    } else g.drawString(font, "Logs    Iron   Core    Axe", 10, 30, 0xff443c2f, false);
    ListTag lines = menu.snapshot.getList("Lines", 8);
    int yy = 10;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.getString(i);
      for (var text : font.split(Component.literal(line), 164)) {
        g.drawString(font, text, 187, yy, line.startsWith("!") ? 0xffffc861 : 0xffeee8d8, false);
        yy += 10;
      }
      yy += 2;
      if (yy > 223) break;
    }
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float delta) {
    renderBackground(g);
    super.render(g, x, y, delta);
    renderTooltip(g, x, y);
  }
}
