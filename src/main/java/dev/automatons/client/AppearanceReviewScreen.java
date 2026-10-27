package dev.automatons.client;

import dev.automatons.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Opt-in development contact sheet, reached only by the disposable review harness. */
final class AppearanceReviewScreen extends Screen {
  private final List<Lumberton> workers = new ArrayList<>();
  private final boolean states;

  AppearanceReviewScreen(boolean states) {
    super(Component.literal(states ? "Operating states" : "Thirteen professions"));
    this.states = states;
    for (int job = 1; job <= 13; job++) {
      Lumberton w =
          (job == WorkerJob.GUARDINGTON ? Automatons.GUARDINGTON : Automatons.LUMBERTON)
              .get()
              .create(Minecraft.getInstance().level);
      w.setNoAi(true);
      w.setJob(job);
      w.showNames(false);
      w.tickCount = 40;
      // A stationary preview should not capture one profession mid-blink forever.
      while (Math.floorMod(w.tickCount + w.getId() * 17, 100) < 3) w.tickCount++;
      if (states) {
        if (job == WorkerJob.FISHERTON) w.fishingPose(2);
        else w.operating(0);
      }
      workers.add(w);
    }
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    g.fill(0, 0, width, height, 0xff242933);
    g.drawCenteredString(font, title, width / 2, 8, 0xffeee5ce);
    int cellWidth = width / 7, rowHeight = (height - 30) / 2;
    for (int i = 0; i < workers.size(); i++) {
      int x = (i % 7) * cellWidth, y = 25 + (i / 7) * rowHeight;
      int bottom = y + rowHeight - 23;
      Lumberton w = workers.get(i);
      g.fill(x + 2, y, x + cellWidth - 2, y + rowHeight - 4, 0xffc6c6c6);
      InventoryScreen.renderEntityInInventoryFollowsMouse(
          g, x + cellWidth / 2, bottom, Math.min(38, cellWidth * 3 / 5), 14, -3, w);
      g.pose().pushPose();
      g.pose().translate(x + cellWidth / 2f, bottom + 7, 0);
      g.pose().scale(.8f, .8f, 1);
      g.drawCenteredString(font, WorkerJob.name(w.job()), 0, 0, 0xff282b32);
      g.pose().popPose();
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
