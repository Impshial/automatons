package dev.automatons;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
  public static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(Automatons.id("main"), () -> "10", "10"::equals, "10"::equals);

  public static void pickup(LuminaOrb orb, net.minecraft.world.entity.player.Player player) {
    CompoundTag n = new CompoundTag();
    n.putInt("Orb", orb.getId());
    n.putInt("Player", player.getId());
    CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> orb), new ClientData(4, 0, n));
  }

  public record Mode(int delta) {}

  public record Confirm(UUID token, boolean yes) {}

  public record ClientData(int kind, int menu, CompoundTag data) {}

  private record Pending(
      UUID token, BlockPos selected, boolean protect, String dimension, long expires) {}

  private static final Map<UUID, Pending> pending = new HashMap<>();

  public static void init() {
    MachineActions.register();
    CHANNEL.registerMessage(
        3,
        TimberMaintenance.Query.class,
        TimberMaintenance.Query::encode,
        TimberMaintenance.Query::decode,
        (message, context) -> {
          var c = context.get();
          c.enqueueWork(
              () -> {
                if (c.getSender() != null) TimberMaintenance.answer(c.getSender(), message);
              });
          c.setPacketHandled(true);
        },
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        0,
        Mode.class,
        (m, b) -> b.writeInt(m.delta),
        b -> new Mode(b.readInt()),
        Network::mode,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        1,
        Confirm.class,
        (m, b) -> {
          b.writeUUID(m.token);
          b.writeBoolean(m.yes);
        },
        b -> new Confirm(b.readUUID(), b.readBoolean()),
        Network::confirm,
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
    CHANNEL.registerMessage(
        2,
        ClientData.class,
        (m, b) -> {
          b.writeInt(m.kind);
          b.writeInt(m.menu);
          b.writeNbt(m.data);
        },
        b -> new ClientData(b.readInt(), b.readInt(), b.readNbt()),
        Network::client,
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  }

  private static void mode(Mode m, Supplier<NetworkEvent.Context> ctx) {
    var c = ctx.get();
    c.enqueueWork(
        () -> {
          ServerPlayer p = c.getSender();
          if (p == null || !p.isShiftKeyDown() || Math.abs(m.delta) != 1) return;
          var s = WandItem.held(p);
          if (s.isEmpty()) return;
          s.getOrCreateTag().putInt("Mode", Math.floorMod(WandItem.mode(s) + m.delta, 4));
          WandItem.cancel(s);
          pending.remove(p.getUUID());
          p.displayClientMessage(
              Component.literal("Wand: " + WandItem.name(WandItem.mode(s))), true);
        });
    c.setPacketHandled(true);
  }

  private static void confirm(Confirm m, Supplier<NetworkEvent.Context> ctx) {
    var c = ctx.get();
    c.enqueueWork(
        () -> {
          ServerPlayer p = c.getSender();
          if (p == null) return;
          Pending req = pending.remove(p.getUUID());
          if (req == null || !req.token.equals(m.token) || !m.yes) return;
          ServerLevel l = p.serverLevel();
          var wand = WandItem.held(p);
          if (wand.isEmpty()
              || WandItem.mode(wand) != (req.protect ? 1 : 2)
              || !req.dimension.equals(l.dimension().location().toString())
              || l.getGameTime() > req.expires
              || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(req.selected)) > 100
              || !l.hasChunkAt(req.selected)) return;
          WorldData data = WorldData.get(l);
          long chunk = new ChunkPos(req.selected).toLong();
          UUID creator = data.protection.get(chunk);
          if (req.protect) {
            if (creator != null) return;
            data.protection.put(chunk, p.getUUID());
          } else {
            if (creator == null) return;
            if (!creator.equals(p.getUUID()) && !p.hasPermissions(2)) {
              p.displayClientMessage(
                  Component.literal("Only its creator or an operator can revoke this protection"),
                  true);
              return;
            }
            data.protection.remove(chunk);
          }
          data.protectionRevision++;
          WorldEvents.protectionChanged(l);
          data.setDirty();
          p.displayClientMessage(
              Component.literal(req.protect ? "Chunk protected" : "Chunk protection revoked"),
              true);
          for (ServerPlayer other : l.players()) protections(other);
        });
    c.setPacketHandled(true);
  }

  private static void client(ClientData m, Supplier<NetworkEvent.Context> ctx) {
    var c = ctx.get();
    c.enqueueWork(
        () ->
            DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> dev.automatons.client.ClientSetup.receive(m)));
    c.setPacketHandled(true);
  }

  private static void send(ServerPlayer p, ClientData data) {
    CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), data);
  }

  public static void balance(ServerPlayer p, CompoundTag n) {
    send(p, new ClientData(3, 0, n));
  }

  public static void snapshot(ServerPlayer p, int id, CompoundTag n) {
    send(p, new ClientData(0, id, n));
  }

  public static void protections(ServerPlayer p) {
    CompoundTag n = new CompoundTag();
    n.putString("Dimension", p.level().dimension().location().toString());
    n.putLongArray(
        "Chunks",
        WorldData.get(p.serverLevel()).protection.keySet().stream()
            .mapToLong(Long::longValue)
            .toArray());
    send(p, new ClientData(1, 0, n));
  }

  public static void requestProtection(ServerPlayer p, BlockPos selected, boolean protect) {
    WorldData d = WorldData.get(p.serverLevel());
    boolean exists = d.protectedAt(selected);
    if (protect == exists) {
      p.displayClientMessage(
          Component.literal(exists ? "Chunk is already protected" : "Chunk is not protected"),
          true);
      return;
    }
    UUID creator = d.protection.get(new ChunkPos(selected).toLong());
    if (!protect && !p.getUUID().equals(creator) && !p.hasPermissions(2)) {
      p.displayClientMessage(
          Component.literal("Only its creator or an operator can revoke protection"), true);
      return;
    }
    UUID token = UUID.randomUUID();
    pending.put(
        p.getUUID(),
        new Pending(
            token,
            selected.immutable(),
            protect,
            p.level().dimension().location().toString(),
            p.level().getGameTime() + 200));
    CompoundTag n = new CompoundTag();
    n.putUUID("Token", token);
    n.putBoolean("Protect", protect);
    n.putInt("X", selected.getX() >> 4);
    n.putInt("Z", selected.getZ() >> 4);
    send(p, new ClientData(2, 0, n));
  }
}
