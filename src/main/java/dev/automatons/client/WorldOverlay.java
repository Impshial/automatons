package dev.automatons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.automatons.*;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class WorldOverlay {
  private static final List<net.minecraft.world.phys.AABB> conflicts = new ArrayList<>();
  private static String conflictDimension = "";
  private static long conflictsUntil;

  public static void buildConflicts(CompoundTag data) {
    conflicts.clear();
    conflictDimension = data.getString("Dimension");
    conflictsUntil = Util.getMillis() + 60_000;
    for (Tag entry : data.getList("Boxes", 10)) {
      CompoundTag box = (CompoundTag) entry;
      BlockPos min = BlockPos.of(box.getLong("Min")), max = BlockPos.of(box.getLong("Max"));
      conflicts.add(new net.minecraft.world.phys.AABB(min, max.offset(1, 1, 1)));
    }
  }

  @SubscribeEvent
  public static void scroll(InputEvent.MouseScrollingEvent e) {
    var p = Minecraft.getInstance().player;
    if (p != null && p.isShiftKeyDown() && !WandItem.held(p).isEmpty() && e.getScrollDelta() != 0) {
      Network.CHANNEL.sendToServer(new Network.Mode(e.getScrollDelta() > 0 ? 1 : -1));
      e.setCanceled(true);
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    var mc = Minecraft.getInstance();
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
        || mc.player == null
        || mc.level == null) return;
    String dimension = mc.level.dimension().location().toString();
    var wand = WandItem.held(mc.player);
    boolean wandActive =
        dimension.equals(ClientSetup.protectionDimension)
            && !wand.isEmpty()
            && (WandItem.mode(wand) == 1 || WandItem.mode(wand) == 2);
    if (!dimension.equals(conflictDimension) || Util.getMillis() >= conflictsUntil)
      conflicts.clear();
    if (!wandActive && conflicts.isEmpty()) return;
    boolean green = WandItem.mode(wand) == 1;
    var cam = e.getCamera().getPosition();
    PoseStack pose = e.getPoseStack();
    pose.pushPose();
    pose.translate(-cam.x, -cam.y, -cam.z);
    Matrix4f matrix = pose.last().pose();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.enableDepthTest();
    RenderSystem.depthMask(false);
    RenderSystem.disableCull();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    BufferBuilder b = Tesselator.getInstance().getBuilder();
    b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    int cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4;
    float base = (float) Math.floor(mc.player.getY()) - 8;
    if (wandActive)
      for (int x = cx - 8; x <= cx + 8; x++)
        for (int z = cz - 8; z <= cz + 8; z++) {
          if (!ClientSetup.protectedChunks.contains(ChunkPos.asLong(x, z))) continue;
          float xx = x * 16, zz = z * 16;
          if (!ClientSetup.protectedChunks.contains(ChunkPos.asLong(x - 1, z)))
            wall(b, matrix, xx, zz, xx, zz + 16, base, green);
          if (!ClientSetup.protectedChunks.contains(ChunkPos.asLong(x + 1, z)))
            wall(b, matrix, xx + 16, zz + 16, xx + 16, zz, base, green);
          if (!ClientSetup.protectedChunks.contains(ChunkPos.asLong(x, z - 1)))
            wall(b, matrix, xx + 16, zz, xx, zz, base, green);
          if (!ClientSetup.protectedChunks.contains(ChunkPos.asLong(x, z + 1)))
            wall(b, matrix, xx, zz + 16, xx + 16, zz + 16, base, green);
        }
    for (var box : conflicts) {
      float x = (float) box.minX, z = (float) box.minZ;
      float x2 = (float) box.maxX, z2 = (float) box.maxZ;
      wall(b, matrix, x, z, x, z2, base, true);
      wall(b, matrix, x, z2, x2, z2, base, true);
      wall(b, matrix, x2, z2, x2, z, base, true);
      wall(b, matrix, x2, z, x, z, base, true);
    }
    BufferUploader.drawWithShader(b.end());
    RenderSystem.enableCull();
    RenderSystem.depthMask(true);
    RenderSystem.disableBlend();
    pose.popPose();
  }

  private static void wall(
      BufferBuilder b, Matrix4f m, float x, float z, float x2, float z2, float y, boolean green) {
    float r = green ? .2f : 1f, g = green ? 1f : .2f;
    vertex(b, m, x, y, z, r, g, .25f);
    vertex(b, m, x2, y, z2, r, g, .25f);
    vertex(b, m, x2, y + 64, z2, r, g, 0);
    vertex(b, m, x, y + 64, z, r, g, 0);
  }

  private static void vertex(
      BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float a) {
    b.vertex(m, x, y, z).color(r, g, .18f, a).endVertex();
  }
}
