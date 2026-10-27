package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/** A fixed footprint, one complete horizontal layer at a time, with expiring cell leases. */
public final class QuarryPlan {
  public BlockPos minimum, maximum;
  public int topY, layer, bottomY;
  public boolean complete;
  public String completionReason = "";
  public final BitSet done = new BitSet(225), unbreakable = new BitSet(225);
  public final Set<Long> acknowledged = new HashSet<>(), discoveries = new HashSet<>();
  public final Set<Long> plugs = new HashSet<>();
  private final Map<Integer, UUID> claims = new HashMap<>();
  private final Map<Integer, Long> expires = new HashMap<>();
  public static final List<Item> FILLERS =
      List.of(
          Items.COBBLESTONE,
          Items.DIRT,
          Items.COBBLED_DEEPSLATE,
          Items.DEEPSLATE,
          Items.TUFF,
          Items.ANDESITE,
          Items.DIORITE,
          Items.GRANITE,
          Items.STONE);

  public static QuarryPlan create(ServerLevel level, BuildingProject project) {
    QuarryPlan plan = new QuarryPlan();
    var bounds = new SiteLayout(project, level).quarry();
    plan.minimum = new BlockPos((int) bounds.minX, 0, (int) bounds.minZ);
    plan.maximum = new BlockPos((int) bounds.maxX - 1, 0, (int) bounds.maxZ - 1);
    if (plan.maximum.getX() - plan.minimum.getX() != 14
        || plan.maximum.getZ() - plan.minimum.getZ() != 14)
      throw new IllegalStateException("Quarry blueprint must define a 15 by 15 excavation");
    plan.topY = (int) bounds.minY - 1;
    plan.bottomY = level.getMinBuildHeight();
    return plan;
  }

  public int y() {
    return topY - layer;
  }

  public boolean contains(BlockPos p) {
    return p.getX() >= minimum.getX()
        && p.getX() <= maximum.getX()
        && p.getZ() >= minimum.getZ()
        && p.getZ() <= maximum.getZ();
  }

  public boolean accessCell(MinePlan mine, BlockPos p) {
    return p.getX() == mine.entrance.getX() && p.getZ() == mine.entrance.getZ()
        || p.getX() == mine.entrance.relative(mine.forward.getOpposite()).getX()
            && p.getZ() == mine.entrance.relative(mine.forward.getOpposite()).getZ();
  }

  public BlockPos cell(MinePlan mine, int index) {
    int offset = index % 15;
    int side = offset == 0 ? 0 : (offset + 1) / 2 * (offset % 2 == 1 ? -1 : 1);
    return mine.entrance
        .relative(mine.forward, index / 15 + 1)
        .relative(mine.forward.getClockWise(), side)
        .atY(y());
  }

  public int claim(Lumberton worker, Station site) {
    long now = worker.level().getGameTime();
    var level = (ServerLevel) worker.level();
    claims
        .keySet()
        .removeIf(
            i ->
                done.get(i)
                    || expires.getOrDefault(i, 0L) <= now
                    || !site.workers.contains(claims.get(i))
                    || !(level.getEntity(claims.get(i)) instanceof Lumberton live)
                    || !live.isAlive());
    for (var entry : claims.entrySet())
      if (entry.getValue().equals(worker.getUUID())) {
        expires.put(entry.getKey(), now + 100);
        return entry.getKey();
      }
    for (int i = 0; i < 225; i++)
      if (!done.get(i) && !claims.containsKey(i)) {
        claims.put(i, worker.getUUID());
        expires.put(i, now + 100);
        return i;
      }
    return -1;
  }

  public void release(UUID worker) {
    claims.values().removeIf(worker::equals);
  }

  public void finish(int index, boolean bedrock) {
    done.set(index);
    if (bedrock) unbreakable.set(index);
    claims.remove(index);
    expires.remove(index);
  }

  public void next() {
    layer++;
    done.clear();
    unbreakable.clear();
    claims.clear();
    expires.clear();
  }

  public void acknowledge(ServerLevel level) {
    ArrayDeque<BlockPos> todo = new ArrayDeque<>();
    discoveries.forEach(p -> todo.add(BlockPos.of(p)));
    int budget = 4096;
    while (!todo.isEmpty() && budget-- > 0) {
      BlockPos p = todo.removeFirst();
      if (!level.hasChunkAt(p)
          || p.getY() > topY
          || p.getY() < level.getMinBuildHeight()
          || !level.getBlockState(p).isAir()
          || !acknowledged.add(p.asLong())) continue;
      for (Direction d : Direction.values()) {
        BlockPos n = p.relative(d);
        if (n.getX() >= minimum.getX() - 16
            && n.getX() <= maximum.getX() + 16
            && n.getZ() >= minimum.getZ() - 16
            && n.getZ() <= maximum.getZ() + 16) todo.addLast(n);
      }
    }
    discoveries.clear();
  }

  public String cave(ServerLevel level, MinePlan mine, BlockPos target) {
    List<BlockPos> cells = new ArrayList<>(List.of(target, target.below()));
    for (Direction d : Direction.Plane.HORIZONTAL) cells.add(target.relative(d));
    for (BlockPos p : cells) if (!level.hasChunkAt(p)) return "Area unloaded";
    boolean found = false;
    for (BlockPos p : cells) {
      if (p.getY() > topY
          || mine.passage.contains(p.asLong())
          || acknowledged.contains(p.asLong())
          || level.getBlockState(p).is(Blocks.LADDER)) continue;
      if (level.getBlockState(p).isAir()) {
        discoveries.add(p.asLong());
        found = true;
      }
    }
    return found ? "Cave opening — make safe and resume" : "";
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    n.putLong("Minimum", minimum.asLong());
    n.putLong("Maximum", maximum.asLong());
    n.putInt("TopY", topY);
    n.putInt("Layer", layer);
    n.putInt("BottomY", bottomY);
    n.putBoolean("Complete", complete);
    n.putString("CompletionReason", completionReason);
    n.putLongArray("Done", done.toLongArray());
    n.putLongArray("Unbreakable", unbreakable.toLongArray());
    n.putLongArray("Acknowledged", acknowledged.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("Discoveries", discoveries.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("Plugs", plugs.stream().mapToLong(Long::longValue).toArray());
    return n;
  }

  public static QuarryPlan load(CompoundTag n) {
    QuarryPlan plan = new QuarryPlan();
    plan.minimum = BlockPos.of(n.getLong("Minimum"));
    plan.maximum = BlockPos.of(n.getLong("Maximum"));
    plan.topY = n.getInt("TopY");
    plan.layer = Math.max(0, n.getInt("Layer"));
    plan.bottomY = n.getInt("BottomY");
    plan.complete = n.getBoolean("Complete");
    plan.completionReason = n.getString("CompletionReason");
    plan.done.or(BitSet.valueOf(n.getLongArray("Done")));
    plan.unbreakable.or(BitSet.valueOf(n.getLongArray("Unbreakable")));
    for (long p : n.getLongArray("Acknowledged")) plan.acknowledged.add(p);
    for (long p : n.getLongArray("Discoveries")) plan.discoveries.add(p);
    for (long p : n.getLongArray("Plugs")) plan.plugs.add(p);
    return plan;
  }
}
