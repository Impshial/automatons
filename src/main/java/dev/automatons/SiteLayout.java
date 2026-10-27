package dev.automatons;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Semantic geometry always comes from the supplied manifest, transformed with its blueprint. */
public final class SiteLayout {
  private final BuildingProject project;
  private final ServerLevel level;
  private final BuildingTemplate template;
  public SiteLayout(BuildingProject project, ServerLevel level) {
    this.project = project; this.level = level; this.template = project.template(level);
  }
  public BlockPos logical(BlockPos p) { return template.logicalWorld(project.anchor, project.front, p); }
  public BlockPos feature(String name) { return template.feature(project.anchor, project.front, name, project.anchor); }
  public AABB rectangle(JsonArray pair, int y, int height) {
    var first = pair.get(0).getAsJsonArray(); var second = pair.get(1).getAsJsonArray();
    BlockPos a = logical(new BlockPos(first.get(0).getAsInt(), y, first.get(1).getAsInt()));
    BlockPos b = logical(new BlockPos(second.get(0).getAsInt(), y, second.get(1).getAsInt()));
    return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
        Math.max(a.getX(), b.getX()) + 1, a.getY() + height, Math.max(a.getZ(), b.getZ()) + 1);
  }
  public List<AABB> beds() {
    List<AABB> result = new ArrayList<>();
    if (template.worksite().has("collector_beds"))
      for (JsonElement value : template.worksite().getAsJsonArray("collector_beds"))
        result.add(rectangle(value.getAsJsonObject().getAsJsonArray("bounds_xz"), 0, 3));
    return result;
  }
  public List<BlockPos> collectors() {
    return beds().stream().map(b -> new BlockPos((int)b.minX + 1, (int)b.minY, (int)b.minZ + 1)).toList();
  }
  public List<BlockPos> plantingCells() {
    List<BlockPos> result = new ArrayList<>();
    for (AABB bed : beds()) {
      BlockPos collector = new BlockPos((int)bed.minX + 1, (int)bed.minY, (int)bed.minZ + 1);
      for (BlockPos p : BlockPos.betweenClosed(new BlockPos((int)bed.minX, (int)bed.minY, (int)bed.minZ),
          new BlockPos((int)bed.maxX - 1, (int)bed.minY, (int)bed.maxZ - 1)))
        if (!p.equals(collector)) result.add(p.immutable());
    }
    return result;
  }
  public boolean waterValid() {
    if (!template.worksite().has("source_water_required")) return true;
    JsonArray pair = template.worksite().getAsJsonObject("source_water_required").getAsJsonArray("bounds_inclusive");
    BlockPos a = logical(BuildingTemplate.point(pair.get(0))), b = logical(BuildingTemplate.point(pair.get(1)));
    for (BlockPos p : BlockPos.betweenClosed(a, b))
      if (!level.hasChunkAt(p) || !level.getFluidState(p).is(FluidTags.WATER) || !level.getFluidState(p).isSource()) return false;
    return true;
  }
  public List<BlockPos> dockSlots() {
    List<BlockPos> result = new ArrayList<>();
    if (template.worksite().has("dock"))
      for (JsonElement slot : template.worksite().getAsJsonObject("dock").getAsJsonArray("slots"))
        result.add(logical(BuildingTemplate.point(slot.getAsJsonObject().get("pos"))));
    return result;
  }
  public AABB pen() {
    return template.worksite().has("pen") ? rectangle(template.worksite().getAsJsonObject("pen").getAsJsonArray("interior_xz"), 0, 4) : new AABB(project.anchor);
  }
  public AABB quarry() {
    return template.worksite().has("quarry") ? rectangle(template.worksite().getAsJsonObject("quarry").getAsJsonArray("footprint_xz"), 0, 1) : new AABB(project.anchor);
  }
  public BlockPos shaft() {
    if (!template.worksite().has("shaft")) return project.anchor;
    JsonArray p = template.worksite().getAsJsonObject("shaft").getAsJsonArray("xz");
    return logical(new BlockPos(p.get(0).getAsInt(), 0, p.get(1).getAsInt()));
  }
  public static boolean protectedBed(ServerLevel level, BlockPos pos) {
    for (BuildingProject p : WorldData.get(level).buildings.values())
      if (p.approved && p.purpose == WorksitePurpose.LUMINA
          && new SiteLayout(p, level).beds().stream().anyMatch(b -> b.intersects(new AABB(pos)))) return true;
    return false;
  }
}
