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
  public final Map<UUID, MinePlan> mines = new HashMap<>();
  public final Map<UUID, BuildingProject> buildings = new HashMap<>();
  public final Map<UUID, FarmPlan> farms = new HashMap<>();
  public final Map<UUID, LogisticsNetwork> logistics = new HashMap<>();

  public boolean mineProtected(BlockPos p, UUID except) {
    for (MinePlan mine : mines.values())
      if (!mine.id.equals(except) && mine.protectedInfrastructure(p)) return true;
    return false;
  }

  public boolean buildingProtected(ServerLevel level, BlockPos pos) {
    return buildings.values().stream().anyMatch(p -> p.approved && p.structure(level, pos));
  }

  public boolean siteAvailable(BlockPos p) {
    for (MinePlan mine : mines.values())
      if (mine.entrance != null
          && Math.abs(mine.entrance.getX() - p.getX()) <= 8
          && Math.abs(mine.entrance.getZ() - p.getZ()) <= 8
          && Math.abs(mine.entrance.getY() - p.getY()) <= 12) return false;
    return true;
  }

  public final Set<Long> placedLogs = new HashSet<>(),
      placedPlants = new HashSet<>(),
      naturalLogs = new HashSet<>();
  public int protectionRevision;

  public static WorldData get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(WorldData::load, WorldData::new, "automatons");
  }

  public boolean protectedAt(BlockPos p) {
    return protection.containsKey(new ChunkPos(p).toLong());
  }

  public boolean farmProtected(BlockPos p) {
    return farms.values().stream().anyMatch(f -> f.contains(p));
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
    for (Tag t : n.getList("Farms", 10)) {
      CompoundTag f = (CompoundTag) t;
      d.farms.put(f.getUUID("Totem"), FarmPlan.load(f.getCompound("Field")));
    }
    for (Tag t : n.getList("Logistics", 10)) {
      LogisticsNetwork network = LogisticsNetwork.load((CompoundTag) t);
      d.logistics.put(network.home, network);
    }
    for (Tag t : n.getList("Warehouses", 10)) {
      BuildingProject p = BuildingProject.load((CompoundTag) t);
      d.buildings.put(p.totem, p);
    }
    for (Tag t : n.getList("Mines", 10)) {
      MinePlan mine = MinePlan.load((CompoundTag) t);
      d.mines.put(mine.id, mine);
    }
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
    // Standing-tree claims are leases rebuilt by resumed workers. World-owned timber persists its
    // own identity.
    for (long p : n.getLongArray("PlacedLogs")) d.placedLogs.add(p);
    for (long p : n.getLongArray("PlacedPlants")) d.placedPlants.add(p);
    for (long p : n.getLongArray("NaturalLogs")) d.naturalLogs.add(p);
    return d;
  }

  @Override
  public CompoundTag save(CompoundTag n) {
    ListTag fields = new ListTag();
    farms.forEach(
        (totem, farm) -> {
          CompoundTag f = new CompoundTag();
          f.putUUID("Totem", totem);
          f.put("Field", farm.save());
          fields.add(f);
        });
    n.put("Farms", fields);
    ListTag networks = new ListTag();
    logistics.values().forEach(p -> networks.add(p.save()));
    n.put("Logistics", networks);
    ListTag buildingTags = new ListTag();
    buildings.values().forEach(p -> buildingTags.add(p.save()));
    n.put("Warehouses", buildingTags);
    ListTag minesTag = new ListTag();
    for (MinePlan mine : mines.values()) minesTag.add(mine.save());
    n.put("Mines", minesTag);
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
    n.putLongArray("PlacedPlants", placedPlants.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("NaturalLogs", naturalLogs.stream().mapToLong(Long::longValue).toArray());
    n.putInt("Version", 2);
    return n;
  }
}
