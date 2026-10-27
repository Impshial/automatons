package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Vanilla text editing with an explicit, scrolling source-line gutter. */
public final class ProgramEditor extends AbstractScrollWidget {
  private final Font font;

  private static final class SourceField extends MultilineTextField {
    record Line(int beginIndex, int endIndex) {}

    SourceField(Font font, int width) {
      super(font, width);
    }

    int selectionStart() {
      return getSelected().beginIndex();
    }

    int selectionEnd() {
      return getSelected().endIndex();
    }

    void firstLine() {
      seekCursor(Whence.ABSOLUTE, 0);
    }

    java.util.List<Line> lines() {
      var result = new java.util.ArrayList<Line>();
      for (var line : iterateLines()) result.add(new Line(line.beginIndex(), line.endIndex()));
      return result;
    }
  }

  private final SourceField field;

  public ProgramEditor(Font font, int x, int y, int width, int height) {
    super(x, y, width, height, Component.literal("Program instructions"));
    this.font = font;
    field = new SourceField(font, width - 34);
    field.setCharacterLimit(MachineRules.MAX_TEXT);
    field.setCursorListener(
        () -> {
          int y0 = field.getLineAtCursor() * 9;
          if (y0 < scrollAmount()) setScrollAmount(y0);
          if (y0 + 13 > scrollAmount() + getHeight() - 8) setScrollAmount(y0 + 21 - getHeight());
        });
  }

  public String getValue() {
    return field.value();
  }

  public void setValue(String value) {
    field.setValue(value);
    field.firstLine();
    setScrollAmount(0);
  }

  public void insert(String text) {
    if (active) field.insertText(text);
  }

  public String description() {
    var result = MachineProgram.parse(getValue());
    if (!result.valid()) return result.error();
    MachineProgram.Instruction selected = result.instructions().get(0);
    for (var instruction : result.instructions())
      if (instruction.offset() <= field.cursor()) selected = instruction;
      else break;
    return selected.description();
  }

  @Override
  public int getInnerHeight() {
    return Math.max(getHeight() - 8, field.getLineCount() * 9);
  }

  @Override
  protected double scrollRate() {
    return 9;
  }

  @Override
  protected boolean scrollbarVisible() {
    return getInnerHeight() > getHeight() - 8;
  }

  @Override
  protected void renderContents(GuiGraphics g, int mx, int my, float partial) {
    int display = 0, lineNumber = 1, previousEnd = 0;
    for (var line : field.lines()) {
      for (int i = previousEnd; i < line.beginIndex(); i++)
        if (getValue().charAt(i) == '\n') lineNumber++;
      int y = getY() + 4 + display * 9;
      boolean continuation =
          line.beginIndex() > 0 && getValue().charAt(line.beginIndex() - 1) != '\n';
      g.drawString(font, continuation ? "·" : "" + lineNumber, getX() + 3, y, 0xff9a8ca8, false);
      int from = Math.max(line.beginIndex(), field.selectionStart()),
          to = Math.min(line.endIndex(), field.selectionEnd());
      if (to > from)
        g.fill(
            getX() + 26 + font.width(getValue().substring(line.beginIndex(), from)),
            y,
            getX() + 26 + font.width(getValue().substring(line.beginIndex(), to)),
            y + 9,
            0xff57466e);
      g.drawString(
          font,
          getValue().substring(line.beginIndex(), line.endIndex()),
          getX() + 26,
          y,
          active ? 0xffeeeeee : 0xffb0a8b9,
          false);
      if (isFocused() && field.cursor() >= line.beginIndex() && field.cursor() <= line.endIndex()) {
        int cx = getX() + 26 + font.width(getValue().substring(line.beginIndex(), field.cursor()));
        g.fill(cx, y, cx + 1, y + 9, 0xffeadbff);
      }
      previousEnd = line.endIndex();
      display++;
    }
  }

  private void seek(double x, double y) {
    field.seekCursorToPoint(x - getX() - 26, y - getY() - 4 + scrollAmount());
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (!visible || !active || button != 0 || !isMouseOver(x, y)) return false;
    if (x >= getX() + getWidth()) return super.mouseClicked(x, y, button);
    field.setSelecting(Screen.hasShiftDown());
    seek(x, y);
    return true;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (!active) return false;
    if (super.mouseDragged(x, y, button, dx, dy)) return true;
    if (isFocused() && button == 0) {
      field.setSelecting(true);
      seek(x, y);
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    field.setSelecting(false);
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (!active || !isFocused()) return false;
    return field.keyPressed(key);
  }

  @Override
  public boolean charTyped(char c, int modifiers) {
    if (!active || !isFocused() || !net.minecraft.SharedConstants.isAllowedChatCharacter(c))
      return false;
    field.insertText(String.valueOf(c));
    return true;
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput out) {
    out.add(NarratedElementType.TITLE, getMessage());
    out.add(NarratedElementType.HINT, Component.literal(description()));
  }
}
