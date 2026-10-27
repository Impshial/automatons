package dev.automatons;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;

/** Small, periodic checks of known timber identities; never deletes arbitrary world blocks. */
@Mod.EventBusSubscriber(modid = Automatons.ID)
public final class TimberMaintenance {
  public static final int INTERVAL = 100, BATCH = 32;

  public record Identity(int id, UUID uuid) {}

  public record Query(String dimension, List<Identity> entries) {
    public void encode(FriendlyByteBuf b) {
      b.writeUtf(dimension, 256);
      b.writeVarInt(entries.size());
      for (Identity entry : entries) {
        b.writeInt(entry.id());
        b.writeUUID(entry.uuid());
      }
    }

    public static Query decode(FriendlyByteBuf b) {
      String dimension = b.readUtf(256);
      int count = b.readVarInt();
      if (count < 0 || count > BATCH) throw new IllegalArgumentException("Timber sweep size");
      List<Identity> entries = new ArrayList<>();
      for (int i = 0; i < count; i++) entries.add(new Identity(b.readInt(), b.readUUID()));
      return new Query(dimension, List.copyOf(entries));
    }
  }

  private static final Map<ServerPlayer, Long> lastQuery = new WeakHashMap<>();

  public static CompoundTag inspect(ServerLevel level, Identity identity) {
    CompoundTag n = new CompoundTag();
    n.putInt("Id", identity.id());
    n.putUUID("UUID", identity.uuid());
    var entity = level.getEntity(identity.id());
    if (entity instanceof Timber timber
        && timber.getUUID().equals(identity.uuid())
        && !timber.isRemoved()
        && !timber.finished()) {
      n.putBoolean("Present", true);
      n.put("State", timber.auditState());
    }
    return n;
  }

  public static void answer(ServerPlayer player, Query query) {
    ServerLevel level = player.serverLevel();
    long now = level.getGameTime();
    if (!level.dimension().location().toString().equals(query.dimension())
        || query.entries().size() > BATCH
        || now - lastQuery.getOrDefault(player, now - INTERVAL) < 20) return;
    lastQuery.put(player, now);
    ListTag entries = new ListTag();
    for (Identity identity : query.entries()) entries.add(inspect(level, identity));
    CompoundTag n = new CompoundTag();
    n.putString("Dimension", query.dimension());
    n.put("Entries", entries);
    Network.CHANNEL.send(
        PacketDistributor.PLAYER.with(() -> player), new Network.ClientData(5, 0, n));
  }

  @SubscribeEvent
  public static void sweep(TickEvent.LevelTickEvent event) {
    if (event.phase != TickEvent.Phase.END
        || !(event.level instanceof ServerLevel level)
        || level.getGameTime() % INTERVAL != 0) return;
    List<Timber> jobs = new ArrayList<>();
    for (var entity : level.getAllEntities()) if (entity instanceof Timber t) jobs.add(t);
    for (Timber t : jobs) {
      t.sweepPlacedLogs();
      if (t.finished() && !t.isRemoved()) t.discard();
    }
  }
}
