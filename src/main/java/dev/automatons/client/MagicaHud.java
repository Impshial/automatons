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
    bar(g, x, y, width, points, height, 0);
  }

  public static void bar(
      GuiGraphics g, int x, int y, int width, int points, int height, int thirds) {
    int[] display = Magica.display(Math.max(0, points));
    int fill = Math.round((width - 2) * ((display[1] + thirds / 3f) / display[2]));
    g.fill(x, y, x + width, y + height, 0xff17121e);
    g.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xff40324f);
    if (fill > 0) {
      g.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, 0xffa65ce1);
      g.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0xfff4eaff);
      if (height >= 6) g.fill(x + 1, y + height - 2, x + 1 + fill, y + height - 1, 0xff69358c);
    }
    for (int mark = 18; mark < width - 1; mark += 18)
      g.fill(x + mark, y + height - 2, x + mark + 1, y + height - 1, 0xff22162e);
  }

  private static boolean experienceShifted;

  private static boolean visible() {
    Minecraft mc = Minecraft.getInstance();
    return mc.player != null && !mc.options.hideGui && !mc.player.isSpectator();
  }

  @SubscribeEvent
  public static void reserveSpace(RenderGuiEvent.Pre event) {
    if (visible()
        && Minecraft.getInstance().gui
            instanceof net.minecraftforge.client.gui.overlay.ForgeGui gui) {
      gui.leftHeight += 9;
      gui.rightHeight += 9;
    }
  }

  @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
  public static void liftExperience(
      net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
    if (experienceShifted) {
      event.getGuiGraphics().pose().popPose();
      experienceShifted = false;
    }
    if (visible() && experience(event.getOverlay().id())) {
      experienceShifted = true;
      event.getGuiGraphics().pose().pushPose();
      event.getGuiGraphics().pose().translate(0, -9, 0);
    }
  }

  @SubscribeEvent
  public static void finishExperience(
      net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
    if (experienceShifted && experience(event.getOverlay().id())) {
      event.getGuiGraphics().pose().popPose();
      experienceShifted = false;
    }
  }

  private static boolean experience(net.minecraft.resources.ResourceLocation id) {
    return id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.EXPERIENCE_BAR.id())
        || id.equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.JUMP_BAR.id());
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post event) {
    if (experienceShifted) {
      event.getGuiGraphics().pose().popPose();
      experienceShifted = false;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
    GuiGraphics g = event.getGuiGraphics();
    int x = mc.getWindow().getGuiScaledWidth() / 2 - 91,
        y = mc.getWindow().getGuiScaledHeight() - 31;
    bar(g, x, y, 182, ClientSetup.magica, 7, ClientSetup.magicaThirds);
    String points = Magica.format(ClientSetup.magica, ClientSetup.magicaThirds);
    g.drawString(mc.font, points, x + 185, y - 1, 0xffe5d2ff, false);
  }
}
