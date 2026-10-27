package dev.automatons.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.automatons.Automatons;
import net.minecraft.client.renderer.*;

/** Draws the packed entity model translucently using its native texture coordinates. */
public final class HologramBuffer implements MultiBufferSource {
  private final MultiBufferSource target;

  public HologramBuffer(MultiBufferSource target) {
    this.target = target;
  }

  @Override
  public VertexConsumer getBuffer(RenderType ignored) {
    VertexConsumer out =
        target.getBuffer(
            RenderType.entityTranslucent(Automatons.id("textures/entity/lumberton_idle.png")));
    return new VertexConsumer() {
      public VertexConsumer vertex(double x, double y, double z) {
        out.vertex(x, y, z);
        return this;
      }

      public VertexConsumer color(int r, int g, int b, int a) {
        out.color(255, 255, 255, 130);
        return this;
      }

      public VertexConsumer uv(float u, float v) {
        out.uv(u, v);
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
        out.defaultColor(255, 255, 255, 130);
      }

      public void unsetDefaultColor() {
        out.unsetDefaultColor();
      }
    };
  }
}
