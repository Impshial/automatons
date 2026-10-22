package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class MagicaHud {
  public static void bar(GuiGraphics g, int x, int y, int width, int points, int height) {
    int[] display = Magica.display(Math.max(0, points));
    int fill = Math.round((width - 2) * (display[1] / (float) display[2]));
    g.fill(x, y, x + width, y + height, 0xff332a43);
    g.fill(x + 1, y + 1, x + 1 + fill, y + height, 0xffad78e8);
    g.fill(x + 1, y, x + 1 + fill, y + 1, 0xfff0e8ff);
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
    GuiGraphics g = event.getGuiGraphics();
    int x = mc.getWindow().getGuiScaledWidth() / 2 - 91,
        y = mc.getWindow().getGuiScaledHeight() - 26;
    bar(g, x, y, 182, ClientSetup.magica, 3);
    g.fill(x - 5, y, x - 2, y + 3, 0xffd8bdff);
    String points = "" + ClientSetup.magica;
    g.drawString(mc.font, points, x + 185, y - 3, 0xffe5d2ff, true);
  }
}
