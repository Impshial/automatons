package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** Exact reserved regions, shared by rejection diagnostics and the player's green overlay. */
public final class BuildConflicts {
  public record Conflict(String name, AABB bounds) {}

  public static List<Conflict> find(ServerLevel level, BuildingProject plan) {
    WorldData data = WorldData.get(level);
    List<Conflict> result = new ArrayList<>();
    AABB proposed = plan.bounds(level);
    AABB entrance = new AABB(plan.entrance(level).relative(plan.front)).expandTowards(0, 1, 0);
    for (long key : data.protection.keySet()) {
      ChunkPos chunk = new ChunkPos(key);
      AABB box =
          new AABB(
              chunk.getMinBlockX(),
              level.getMinBuildHeight(),
              chunk.getMinBlockZ(),
              chunk.getMaxBlockX() + 1,
              level.getMaxBuildHeight(),
              chunk.getMaxBlockZ() + 1);
      if (box.intersects(proposed) || box.intersects(entrance))
        result.add(new Conflict("Protected chunk " + chunk.x + ", " + chunk.z, box));
    }
    for (BuildingProject other : data.buildings.values()) {
      if (other == plan) continue;
      AABB box = other.bounds(level);
      AABB door = new AABB(other.entrance(level).relative(other.front)).expandTowards(0, 1, 0);
      if (footprintsOverlap(box, proposed)
          || footprintsOverlap(box, entrance)
          || footprintsOverlap(door, proposed)) {
        result.add(
            new Conflict(
                "Reserved " + other.purpose.label + " at " + other.anchor.toShortString(), box));
        result.add(new Conflict("Reserved worksite entrance", door));
      }
    }
    for (FarmPlan farm : data.farms.values()) {
      Set<Long> cells = new LinkedHashSet<>(farm.cells);
      if (farm.water != null) cells.add(farm.water.asLong());
      boolean overlaps =
          cells.stream()
              .map(BlockPos::of)
              .anyMatch(
                  p ->
                      new AABB(p).expandTowards(0, 1, 0).intersects(proposed)
                          || new AABB(p).expandTowards(0, 1, 0).intersects(entrance));
      if (overlaps)
        for (long cell : cells)
          result.add(
              new Conflict(
                  "Reserved farm field", new AABB(BlockPos.of(cell)).expandTowards(0, 1, 0)));
    }
    for (MinePlan mine : data.mines.values()) {
      if (mine.entrance == null) continue;
      List<AABB> regions = new ArrayList<>();
      int radius = mine.rustic ? 3 : 2;
      regions.add(
          new AABB(
              mine.entrance.offset(-radius, mine.rustic ? -1 : 0, -radius),
              mine.entrance.offset(radius + 1, 6, radius + 1)));
      if (mine.quarry != null) {
        var q = mine.quarry;
        regions.add(
            new AABB(
                q.minimum.getX(),
                q.bottomY,
                q.minimum.getZ(),
                q.maximum.getX() + 1,
                q.topY + 1,
                q.maximum.getZ() + 1));
      }
      Set<Long> cells = new LinkedHashSet<>(mine.supports);
      cells.addAll(mine.torchSupports);
      cells.addAll(mine.placedChests);
      if (mine.depotRequired) for (BlockPos p : mine.depotPositions()) cells.add(p.asLong());
      for (int i = 1; i <= mine.shaftDepth; i++) {
        cells.add(mine.ladder(i).asLong());
        cells.add(mine.ladder(i).relative(mine.forward.getOpposite()).asLong());
      }
      for (long cell : cells) regions.add(new AABB(BlockPos.of(cell)));
      for (AABB box : regions)
        if (box.intersects(proposed) || box.intersects(entrance))
          result.add(new Conflict("Reserved mine at " + mine.entrance.toShortString(), box));
    }
    return result;
  }

  private static boolean footprintsOverlap(AABB a, AABB b) {
    // The visible yellow border extends 0.12 blocks beyond each footprint. Keep those
    // borders separate too, regardless of different surface heights on a hillside.
    return a.maxX + .24 > b.minX
        && a.minX - .24 < b.maxX
        && a.maxZ + .24 > b.minZ
        && a.minZ - .24 < b.maxZ;
  }

  public static CompoundTag packet(ServerLevel level, List<Conflict> conflicts) {
    CompoundTag data = new CompoundTag();
    data.putString("Dimension", level.dimension().location().toString());
    ListTag boxes = new ListTag();
    for (Conflict c : conflicts) {
      CompoundTag box = new CompoundTag();
      box.putString("Name", c.name);
      box.putLong("Min", BlockPos.containing(c.bounds.minX, c.bounds.minY, c.bounds.minZ).asLong());
      box.putLong(
          "Max",
          BlockPos.containing(c.bounds.maxX - 1, c.bounds.maxY - 1, c.bounds.maxZ - 1).asLong());
      boxes.add(box);
    }
    data.put("Boxes", boxes);
    return data;
  }
}
