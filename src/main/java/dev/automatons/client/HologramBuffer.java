package dev.automatons.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.automatons.Automatons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.inventory.InventoryMenu;

/** Recolors the actual item model through a translucent atlas render type. */
public final class HologramBuffer implements MultiBufferSource {
  private final MultiBufferSource target;

  public HologramBuffer(MultiBufferSource target) {
    this.target = target;
  }

  @Override
  public VertexConsumer getBuffer(RenderType ignored) {
    var sprite =
        Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(Automatons.id("item/automaton"));
    VertexConsumer out =
        target.getBuffer(
            RenderType.entityTranslucent(Automatons.id("textures/item/automaton_display.png")));
    return new VertexConsumer() {
      public VertexConsumer vertex(double x, double y, double z) {
        out.vertex(x, y, z);
        return this;
      }

      public VertexConsumer color(int r, int g, int b, int a) {
        out.color(255, 255, 255, 165);
        return this;
      }

      public VertexConsumer uv(float u, float v) {
        out.uv(
            (u - sprite.getU0()) / (sprite.getU1() - sprite.getU0()),
            (v - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
        return this;
      }

      public VertexConsumer overlayCoords(int u, int v) {
        out.overlayCoords(u, v);
        return this;
      }

      public VertexConsumer uv2(int u, int v) {
        out.uv2(u, v);
        return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
        out.normal(x, y, z);
        return this;
      }

      public void endVertex() {
        out.endVertex();
      }

      public void defaultColor(int r, int g, int b, int a) {
        out.defaultColor(210, 175, 255, 150);
      }

      public void unsetDefaultColor() {
        out.unsetDefaultColor();
      }
    };
  }
}
