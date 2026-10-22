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

  private record Cell(BlockPos pos, boolean storage) {}

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
    int range = station.lumberRange(),
        storage = station.storageRange(),
        height = Math.max(Balance.ROOT_HEIGHT.get(), Balance.HEIGHT.get()),
        extent = Math.max(range, storage);
    cells.clear();
    occupied.clear();
    for (int dx = -extent; dx <= extent; dx++)
      for (int dz = -extent; dz <= extent; dz++) {
        for (int y = Math.min(level.getMaxBuildHeight() - 1, selected.getY() + height);
            y >= Math.max(level.getMinBuildHeight() + 1, selected.getY() - height);
            y--) {
          BlockPos at = new BlockPos(selected.getX() + dx, y, selected.getZ() + dz);
          if (!level.hasChunkAt(at)) break;
          if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
            boolean blue =
                Math.abs(dx) <= storage
                    && Math.abs(dz) <= storage
                    && Math.abs(y - selected.getY()) <= Balance.HEIGHT.get();
            boolean green =
                Math.abs(dx) <= range
                    && Math.abs(dz) <= range
                    && Math.abs(y - selected.getY()) <= Balance.ROOT_HEIGHT.get();
            if (!blue && !green) continue;
            cells.add(new Cell(at, blue));
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
        b.vertex(m, x + v[0], y + v[1], z + v[2])
            .color(.2f, c.storage ? .55f : 1f, c.storage ? 1f : .3f, .22f)
            .endVertex();
      }
    }
  }
}
