package dev.automatons.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;

/** The same material table is used by home, Totem and assigned builder dialogs. */
public final class BuildListDialog {
  public static int render(
      GuiGraphics g,
      Font font,
      int width,
      int height,
      String title,
      ListTag rows,
      int scroll,
      int mouseX,
      int mouseY) {
    int x = (width - 320) / 2, y = (height - 216) / 2;
    g.pose().pushPose();
    g.pose().translate(0, 0, 400);
    g.fill(0, 0, width, height, 0xa0000000);
    g.fill(x, y, x + 320, y + 216, 0xffeadfc8);
    g.fill(x + 1, y + 1, x + 319, y + 25, 0xff3e3545);
    g.drawString(font, font.plainSubstrByWidth(title, 287), x + 9, y + 9, 0xffffe89a, false);
    g.drawString(font, "X", x + 305, y + 9, 0xffffffff, false);
    scroll = rows(g, font, x, y, rows, scroll, mouseX, mouseY);
    if (rows.isEmpty())
      g.drawString(font, "No active construction assignment", x + 12, y + 82, 0xff443c35, false);
    g.drawString(font, "Scroll for more  |  ESC to close", x + 9, y + 202, 0xff574d40, false);
    g.pose().popPose();
    return scroll;
  }

  public static int rows(
      GuiGraphics g, Font font, int x, int y, ListTag rows, int scroll, int mouseX, int mouseY) {
    g.drawString(font, "Available: worker cargo + supply chests", x + 9, y + 31, 0xff443c35, false);
    g.drawString(font, "Block", x + 9, y + 48, 0xff443c35, false);
    String[] labels = {"Total", "Left", "Have", "Need"};
    for (int col = 0; col < 4; col++)
      g.drawString(font, labels[col], x + 169 + 35 * col, y + 48, 0xff443c35, false);

    scroll = Math.min(scroll, Math.max(0, rows.size() - 6));
    for (int row = 0; row < 6 && row + scroll < rows.size(); row++) {
      CompoundTag data = rows.getCompound(row + scroll);
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
      int thumb = y + 61 + 110 * scroll / Math.max(1, rows.size() - 6);
      g.fill(x + 312, thumb, x + 316, thumb + 24, 0xff6a546f);
    }
    return scroll;
  }
}
