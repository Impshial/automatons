package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public class WorldData extends SavedData {
  public record Home(UUID owner, BlockPos pos, boolean recalled, boolean exists) {}

  public final Map<Long, UUID> protection = new HashMap<>();
  public final Map<UUID, Home> homes = new HashMap<>();
  public final Map<Long, UUID> reservations = new HashMap<>();
  public final Set<Long> placedLogs = new HashSet<>();
  public int protectionRevision;

  public static WorldData get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, "automatons");
  }

  public boolean protectedAt(BlockPos p) {
    return protection.containsKey(new ChunkPos(p).toLong());
  }

  public void setHome(UUID id, UUID owner, BlockPos p, boolean recall, boolean exists) {
    homes.put(id, new Home(owner, p, recall, exists));
    setDirty();
  }

  public boolean reserve(Collection<BlockPos> logs, UUID worker) {
    for (BlockPos p : logs)
      if (reservations.containsKey(p.asLong()) && !worker.equals(reservations.get(p.asLong())))
        return false;
    for (BlockPos p : logs) reservations.put(p.asLong(), worker);
    setDirty();
    return true;
  }

  public void release(UUID worker) {
    reservations.values().removeIf(worker::equals);
    setDirty();
  }

  public static WorldData load(CompoundTag n) {
    WorldData d = new WorldData();
    for (Tag t : n.getList("Protection", 10)) {
      CompoundTag c = (CompoundTag) t;
      d.protection.put(c.getLong("Chunk"), c.getUUID("Owner"));
    }
    for (Tag t : n.getList("Homes", 10)) {
      CompoundTag c = (CompoundTag) t;
      d.homes.put(
          c.getUUID("Id"),
          new Home(
              c.getUUID("Owner"),
              BlockPos.of(c.getLong("Pos")),
              c.getBoolean("Recall"),
              c.getBoolean("Exists")));
    }
    for (Tag t : n.getList("Reservations", 10)) {
      CompoundTag c = (CompoundTag) t;
      d.reservations.put(c.getLong("Pos"), c.getUUID("Worker"));
    }
    for (long p : n.getLongArray("PlacedLogs")) d.placedLogs.add(p);
    return d;
  }

  @Override
  public CompoundTag save(CompoundTag n) {
    ListTag p = new ListTag();
    protection.forEach(
        (chunk, owner) -> {
          CompoundTag c = new CompoundTag();
          c.putLong("Chunk", chunk);
          c.putUUID("Owner", owner);
          p.add(c);
        });
    n.put("Protection", p);
    ListTag h = new ListTag();
    homes.forEach(
        (id, home) -> {
          CompoundTag c = new CompoundTag();
          c.putUUID("Id", id);
          c.putUUID("Owner", home.owner);
          c.putLong("Pos", home.pos.asLong());
          c.putBoolean("Recall", home.recalled);
          c.putBoolean("Exists", home.exists);
          h.add(c);
        });
    n.put("Homes", h);
    ListTag r = new ListTag();
    reservations.forEach(
        (pos, id) -> {
          CompoundTag c = new CompoundTag();
          c.putLong("Pos", pos);
          c.putUUID("Worker", id);
          r.add(c);
        });
    n.put("Reservations", r);
    n.putLongArray("PlacedLogs", placedLogs.stream().mapToLong(Long::longValue).toArray());
    return n;
  }
}
