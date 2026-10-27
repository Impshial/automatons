package dev.automatons.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.automatons.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Local preview: terrain-following air cells, never actual placed blocks. */
@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class RangeOverlay {
  private static BlockPos selected;
  private static UUID identity;
  private static String dimension = "";
  private static long refreshed = Long.MIN_VALUE;
  private static final List<Cell> cells = new ArrayList<>();
  private static final Set<BlockPos> occupied = new HashSet<>();
  public static boolean forestry = true, mining = true;

  private record Cell(BlockPos pos) {}

  public static boolean shown(Station station) {
    return station != null
        && station.getLevel() != null
        && station.getBlockPos().equals(selected)
        && station.identity.equals(identity)
        && station.getLevel().dimension().location().toString().equals(dimension);
  }

  public static void toggle(Station station) {
    if (shown(station)) {
      clear();
      return;
    }
    if (station == null || !station.isTotem() || station.getLevel() == null) return;
    selected = station.getBlockPos().immutable();
    identity = station.identity;
    dimension = station.getLevel().dimension().location().toString();
    refreshed = Long.MIN_VALUE;
    cells.clear();
  }

  private static void clear() {
    selected = null;
    identity = null;
    dimension = "";
    cells.clear();
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
    clear();
  }

  private static void refresh(Station station) {
    var level = station.getLevel();
    int range = station.workRange(),
        height = Math.max(Balance.ROOT_HEIGHT.get(), Balance.HEIGHT.get());
    cells.clear();
    occupied.clear();
    for (int dx = -range; dx <= range; dx++)
      for (int dz = -range; dz <= range; dz++) {
        for (int y = Math.min(level.getMaxBuildHeight() - 1, selected.getY() + height);
            y >= Math.max(level.getMinBuildHeight() + 1, selected.getY() - height);
            y--) {
          BlockPos at = new BlockPos(selected.getX() + dx, y, selected.getZ() + dz);
          if (!level.hasChunkAt(at)) break;
          if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
            boolean visible =
                forestry
                    && Math.abs(dx) <= range
                    && Math.abs(dz) <= range
                    && Math.abs(y - selected.getY()) <= Balance.ROOT_HEIGHT.get();
            if (!visible) continue;
            cells.add(new Cell(at));
            occupied.add(at);
            break;
          }
        }
      }
    refreshed = level.getGameTime();
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    var mc = Minecraft.getInstance();
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
        && mc.level != null
        && mc.player != null
        && (mc.player.getMainHandItem().is(Automatons.WAND.get())
            || mc.player.getOffhandItem().is(Automatons.WAND.get()))
        && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
        && mc.level.getBlockEntity(hit.getBlockPos()) instanceof Reservoir machine
        && machine.collector) {
      var pose = event.getPoseStack();
      var camera = event.getCamera().getPosition();
      pose.pushPose();
      pose.translate(-camera.x, -camera.y, -camera.z);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      var lines = Tesselator.getInstance().getBuilder();
      lines.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
      var bounds = LuminaCollection.bounds(hit.getBlockPos(), Balance.COLLECTOR_HEIGHT.get());
      lineBox(pose, lines, bounds, .85f, .73f, .96f, .9f);
      lineBox(
          pose,
          lines,
          new net.minecraft.world.phys.AABB(
              bounds.minX,
              hit.getBlockPos().getY() + .03,
              bounds.minZ,
              bounds.maxX,
              hit.getBlockPos().getY() + .03,
              bounds.maxZ),
          .85f,
          .73f,
          .96f,
          1);
      BufferUploader.drawWithShader(lines.end());
      RenderSystem.depthMask(true);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
      pose.popPose();
    }
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
        || selected == null
        || mc.level == null
        || mc.player == null) return;
    if (!mc.level.dimension().location().toString().equals(dimension)) {
      clear();
      return;
    }
    if (!mc.level.hasChunkAt(selected)) return;
    if (!(mc.level.getBlockEntity(selected) instanceof Station station) || !shown(station)) {
      clear();
      return;
    }
    if (mc.level.getGameTime() - refreshed >= 20 || refreshed == Long.MIN_VALUE) refresh(station);
    var camera = event.getCamera().getPosition();
    PoseStack pose = event.getPoseStack();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.enableDepthTest();
    RenderSystem.depthMask(false);
    RenderSystem.disableCull();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    var buffer = Tesselator.getInstance().getBuilder();
    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    for (Cell cell : cells) {
      if (!mc.level.getBlockState(cell.pos).isAir()
          || mc.level.getBlockState(cell.pos.below()).isAir()
          || cell.pos.distToCenterSqr(camera) > 128 * 128) continue;
      cube(buffer, pose.last().pose(), cell);
    }
    BufferUploader.drawWithShader(buffer.end());
    RenderSystem.enableCull();
    RenderSystem.depthMask(true);
    RenderSystem.disableBlend();
    pose.popPose();
    if (station.owns(mc.player)) renderMine(event, station);
  }

  private static void renderMine(RenderLevelStageEvent event, Station station) {
    var mc = Minecraft.getInstance();
    var pose = event.getPoseStack();
    var camera = event.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableDepthTest();
    RenderSystem.depthMask(false);
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    var lines = Tesselator.getInstance().getBuilder();
    lines.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
    int r = station.workRange();
    BlockPos p = station.getBlockPos();
    lineBox(
        pose,
        lines,
        new net.minecraft.world.phys.AABB(
            p.getX() - r,
            p.getY() + .05,
            p.getZ() - r,
            p.getX() + r + 1,
            p.getY() + .15,
            p.getZ() + r + 1),
        1,
        .65f,
        .15f,
        .65f);
    MinePlan m = station.mine;
    if (mining && m != null && m.entrance != null) {
      // Only declared mine geometry is visible; no world/ore scan occurs here.
      lineBox(
          pose,
          lines,
          new net.minecraft.world.phys.AABB(
              m.entrance.offset(-2, 0, -2), m.entrance.offset(3, 6, 3)),
          1,
          .7f,
          .2f,
          .8f);
      lineBox(
          pose,
          lines,
          new net.minecraft.world.phys.AABB(m.entrance.below(m.shaftDepth))
              .expandTowards(0, m.shaftDepth, 0),
          .7f,
          .55f,
          1,
          .8f);
      if (m.mode == MinePlan.Mode.TUNNEL) {
        int side = m.lateral == 0 ? 1 : m.lateral;
        for (int lane = Math.max(0, m.lane - 1); lane <= m.lane + 1; lane++) {
          BlockPos a = m.workAt(lane * 3 * side, -m.shaftDepth, 3),
              b = m.workAt(lane * 3 * side, 1 - m.shaftDepth, 34);
          if (MinePlan.inBounds(station, a) && MinePlan.inBounds(station, b))
            lineBox(pose, lines, box(a, b), 1, .65f, .15f, .75f);
        }
        lineBox(
            pose,
            lines,
            new net.minecraft.world.phys.AABB(m.tunnelColumn()).expandTowards(0, 1, 0),
            1,
            .95f,
            .55f,
            1);
      } else if (m.quarrySide != 0) {
        BlockPos q = m.quarryOrigin();
        lineBox(
            pose,
            lines,
            new net.minecraft.world.phys.AABB(q.offset(-2, -m.quarryLayer, -2), q.offset(3, 2, 3)),
            1,
            .65f,
            .15f,
            .8f);
        for (int d = Math.max(0, m.quarryLayer - 32); d <= m.quarryLayer; d++)
          lineBox(
              pose, lines, new net.minecraft.world.phys.AABB(m.step(d).below()), .7f, .55f, 1, .8f);
      }
    }
    BufferUploader.drawWithShader(lines.end());
    RenderSystem.depthMask(true);
    RenderSystem.enableDepthTest();
    RenderSystem.disableBlend();
    pose.popPose();
  }

  private static void lineBox(
      PoseStack pose,
      BufferBuilder b,
      net.minecraft.world.phys.AABB box,
      float r,
      float g,
      float blue,
      float a) {
    double[][] corners = {
      {box.minX, box.minY, box.minZ},
      {box.maxX, box.minY, box.minZ},
      {box.minX, box.maxY, box.minZ},
      {box.maxX, box.maxY, box.minZ},
      {box.minX, box.minY, box.maxZ},
      {box.maxX, box.minY, box.maxZ},
      {box.minX, box.maxY, box.maxZ},
      {box.maxX, box.maxY, box.maxZ}
    };
    for (int i = 0; i < 8; i++)
      for (int bit : new int[] {1, 2, 4})
        if ((i & bit) == 0) {
          for (int index : new int[] {i, i | bit}) {
            double[] p = corners[index];
            b.vertex(pose.last().pose(), (float) p[0], (float) p[1], (float) p[2])
                .color(r, g, blue, a)
                .endVertex();
          }
        }
  }

  private static net.minecraft.world.phys.AABB box(BlockPos a, BlockPos b) {
    return new net.minecraft.world.phys.AABB(
        Math.min(a.getX(), b.getX()),
        Math.min(a.getY(), b.getY()),
        Math.min(a.getZ(), b.getZ()),
        Math.max(a.getX(), b.getX()) + 1,
        Math.max(a.getY(), b.getY()) + 1,
        Math.max(a.getZ(), b.getZ()) + 1);
  }

  private static final float[][] VERTICES = {
    {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {1, 0, 1}, {0, 0, 1}, {0, 1, 1}, {1, 1, 1},
    {0, 0, 1}, {0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0},
    {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}
  };
  private static final net.minecraft.core.Direction[] SIDES = {
    net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
    net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST
  };

  private static void cube(BufferBuilder b, Matrix4f m, Cell c) {
    float x = c.pos.getX(), y = c.pos.getY(), z = c.pos.getZ();
    for (int face = 0; face < 5; face++) {
      // Omit shared faces so neighboring cells make one seamless translucent area.
      if (face < 4 && occupied.contains(c.pos.relative(SIDES[face]))) continue;
      for (int i = face * 4; i < face * 4 + 4; i++) {
        var v = VERTICES[i];
        b.vertex(m, x + v[0], y + v[1], z + v[2]).color(.2f, 1f, .3f, .22f).endVertex();
      }
    }
  }
}
