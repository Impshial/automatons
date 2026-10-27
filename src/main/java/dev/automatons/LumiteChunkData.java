package dev.automatons;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Serialized WITH each chunk, so progress and modified blocks cannot save independently. */
@AutoRegisterCapability
@Mod.EventBusSubscriber(modid = Automatons.ID)
public final class LumiteChunkData implements ICapabilitySerializable<CompoundTag> {
  public static final Capability<LumiteChunkData> CAP =
      CapabilityManager.get(new CapabilityToken<>() {});
  public static final ResourceLocation ID = Automatons.id("lumite");
  public boolean normal;
  public UUID job;
  public int next, placed;
  public boolean completed;
  public final Set<Long> playerPlaced = new HashSet<>();
  private final LazyOptional<LumiteChunkData> optional = LazyOptional.of(() -> this);

  public static LumiteChunkData get(LevelChunk chunk) {
    return chunk
        .getCapability(CAP)
        .orElseThrow(() -> new IllegalStateException("Missing Lumite chunk journal"));
  }

  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
    return cap == CAP ? optional.cast() : LazyOptional.empty();
  }

  @Override
  public CompoundTag serializeNBT() {
    CompoundTag n = new CompoundTag();
    n.putInt("Version", 1);
    n.putBoolean("Normal", normal);
    if (job != null) n.putUUID("Job", job);
    n.putInt("Next", next);
    n.putInt("Placed", placed);
    n.putBoolean("Completed", completed);
    n.putLongArray("PlayerPlaced", playerPlaced.stream().mapToLong(Long::longValue).toArray());
    return n;
  }

  @Override
  public void deserializeNBT(CompoundTag n) {
    normal = n.getBoolean("Normal");
    job = n.hasUUID("Job") ? n.getUUID("Job") : null;
    next = Math.max(0, n.getInt("Next"));
    placed = Math.max(0, n.getInt("Placed"));
    completed = n.getBoolean("Completed");
    playerPlaced.clear();
    for (long p : n.getLongArray("PlayerPlaced")) playerPlaced.add(p);
  }

  @SubscribeEvent
  public static void attach(AttachCapabilitiesEvent<LevelChunk> e) {
    LumiteChunkData data = new LumiteChunkData();
    e.addCapability(ID, data);
    e.addListener(data.optional::invalidate);
  }

  @SubscribeEvent
  public static void load(ChunkEvent.Load e) {
    if (e.getChunk() instanceof LevelChunk chunk
        && e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
      if (e.isNewChunk()) {
        get(chunk).normal = true;
        chunk.setUnsaved(true);
      }
    }
  }

  @SubscribeEvent
  public static void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e) {
    if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel l) {
      if (e instanceof net.minecraftforge.event.level.BlockEvent.EntityMultiPlaceEvent multi) {
        for (var snapshot : multi.getReplacedBlockSnapshots()) track(l, snapshot.getPos());
      } else track(l, e.getPos());
    }
  }

  private static void track(
      net.minecraft.server.level.ServerLevel l, net.minecraft.core.BlockPos pos) {
    if (pos.getY() < -64 || pos.getY() > 320) return;
    LevelChunk chunk = l.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    if (chunk != null) {
      get(chunk).playerPlaced.add(pos.asLong());
      chunk.setUnsaved(true);
    }
  }
}
