package dev.automatons.client;

import com.mojang.blaze3d.vertex.*;
import dev.automatons.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/** Tank fluid behind glass; Collector fluid in an open stone basin around a bottle stand. */
public final class ReservoirRenderer implements BlockEntityRenderer<Reservoir> {
  public ReservoirRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public void render(
      Reservoir machine,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    if (!machine.tank.isEmpty()) {
      var fluid = machine.tank.getFluid();
      var properties = IClientFluidTypeExtensions.of(fluid.getFluid());
      float fraction = Math.min(1, (float) fluid.getAmount() / machine.capacity());
      if (machine.collector) {
        // Four nonoverlapping sections leave the raised stone bottle stand dry.
        float[][] sections = {
          {2.05f, 2.05f, 13.95f, 6f},
          {2.05f, 10f, 13.95f, 13.95f},
          {2.05f, 6f, 6f, 10f},
          {10f, 6f, 13.95f, 10f}
        };
        for (float[] section : sections)
          cuboid(
              pose,
              buffers,
              sprite(properties.getStillTexture(fluid)),
              section[0] / 16,
              12.05f / 16,
              section[1] / 16,
              section[2] / 16,
              (12.05f + 3.5f * fraction) / 16,
              section[3] / 16,
              properties.getTintColor(fluid),
              light);
      } else
        cuboid(
            pose,
            buffers,
            sprite(properties.getStillTexture(fluid)),
            1f / 16,
            1f / 16,
            1f / 16,
            15f / 16,
            (1f + 14f * fraction) / 16,
            15f / 16,
            properties.getTintColor(fluid),
            light);
    }
    if (machine.collector && !machine.bottle.isEmpty()) {
      var highlight = sprite(new ResourceLocation("automatons:block/white_stone"));
      for (float x : new float[] {6f, 9.65f})
        for (float z : new float[] {6f, 9.65f})
          cuboid(
              pose,
              buffers,
              highlight,
              x / 16,
              16.1f / 16,
              z / 16,
              (x + .35f) / 16,
              21.1f / 16,
              (z + .35f) / 16,
              0xcceef6ff,
              light);
      for (float x : new float[] {7f, 8.65f})
        for (float z : new float[] {7f, 8.65f})
          cuboid(
              pose,
              buffers,
              highlight,
              x / 16,
              21.1f / 16,
              z / 16,
              (x + .35f) / 16,
              23.1f / 16,
              (z + .35f) / 16,
              0xcceef6ff,
              light);
      // Glass occupies a separate shell around the fluid, avoiding coplanar faces.
      var glass = sprite(new ResourceLocation("minecraft:block/glass"));
      cuboid(
          pose,
          buffers,
          glass,
          6f / 16,
          16.1f / 16,
          6f / 16,
          10f / 16,
          21.1f / 16,
          10f / 16,
          0xbaffffff,
          light);
      cuboid(
          pose,
          buffers,
          sprite(new ResourceLocation("minecraft:block/stripped_oak_log_top")),
          7f / 16,
          23.1f / 16,
          7f / 16,
          9f / 16,
          24.1f / 16,
          9f / 16,
          0xffffffff,
          light);
      cuboid(
          pose,
          buffers,
          glass,
          7f / 16,
          21.1f / 16,
          7f / 16,
          9f / 16,
          23.1f / 16,
          9f / 16,
          0xbaffffff,
          light);
      float fraction =
          (float) LuminallBottle.amount(machine.bottle) / LuminallBottle.volume(machine.bottle);
      if (fraction > 0)
        cuboid(
            pose,
            buffers,
            sprite(new ResourceLocation("minecraft:block/water_still")),
            6.3f / 16,
            16.4f / 16,
            6.3f / 16,
            9.7f / 16,
            (16.4f + 4.4f * fraction) / 16,
            9.7f / 16,
            Luminall.COLOR,
            light);
    }
    if (machine.collector
        && machine.getLevel() != null
        && machine.lastProducer != null
        && machine.space() > 0) {
      double age = machine.getLevel().getGameTime() + partial - machine.lastCollection;
      if (age >= 0 && age < 30) {
        Vec3 from =
            Vec3.atCenterOf(machine.lastProducer)
                .subtract(Vec3.atLowerCornerOf(machine.getBlockPos()));
        var dot = sprite(new ResourceLocation("automatons:block/white_stone"));
        for (int i = 0; i < 2; i++) {
          double t = (age - i * 7) / 23;
          if (t < 0 || t > 1) continue;
          Vec3 at = from.lerp(new Vec3(.5, 1.05, .5), t);
          float x = (float) at.x, y = (float) at.y, z = (float) at.z, r = .025f;
          cuboid(
              pose,
              buffers,
              dot,
              x - r,
              y - r,
              z - r,
              x + r,
              y + r,
              z + r,
              Luminall.COLOR,
              LightTexture.FULL_BRIGHT);
        }
      }
    }
  }

  private static TextureAtlasSprite sprite(ResourceLocation location) {
    return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(location);
  }

  private static void cuboid(
      PoseStack pose,
      MultiBufferSource buffers,
      TextureAtlasSprite sprite,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      int color,
      int light) {
    var out = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
    float[][] corners = {
      {x0, y0, z0},
      {x1, y0, z0},
      {x1, y1, z0},
      {x0, y1, z0},
      {x0, y0, z1},
      {x1, y0, z1},
      {x1, y1, z1},
      {x0, y1, z1}
    };
    int[][] faces = {
      {1, 0, 3, 2}, {4, 5, 6, 7}, {0, 4, 7, 3}, {5, 1, 2, 6}, {3, 7, 6, 2}, {0, 1, 5, 4}
    };
    float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    for (int f = 0; f < 6; f++) {
      float width = (f < 2 || f > 3 ? x1 - x0 : z1 - z0) * 16;
      float height = (f > 3 ? z1 - z0 : y1 - y0) * 16;
      float[][] uv = {{0, height}, {width, height}, {width, 0}, {0, 0}};
      for (int i = 0; i < 4; i++) {
        var p = corners[faces[f][i]];
        var n = normals[f];
        out.vertex(pose.last().pose(), p[0], p[1], p[2])
            .color((color >> 16) & 255, (color >> 8) & 255, color & 255, (color >>> 24) & 255)
            .uv(sprite.getU(uv[i][0]), sprite.getV(uv[i][1]))
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.last().normal(), n[0], n[1], n[2])
            .endVertex();
      }
    }
  }
}
