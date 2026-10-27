package dev.automatons;

import java.util.*;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class Infuser extends BlockEntity {
  public final ItemStack[] shards = {
    ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
  };
  public final UUID[] donors = new UUID[4];
  public final long[] lastTransfer = {-1000, -1000, -1000, -1000};
  public final boolean[] transferring = new boolean[4];
  public ItemStack recovery = ItemStack.EMPTY;
  public UUID recoveryDonor;
  private int transferTicks, nextCorner;

  public Infuser(BlockPos pos, BlockState state) {
    super(Automatons.INFUSER_BE.get(), pos, state);
  }

  public static Vec3 offset(int corner) {
    return new Vec3((corner & 1) == 0 ? .25 : .75, 1.17 + 3.0 / 16, (corner & 2) == 0 ? .25 : .75);
  }

  public Vec3 shardPosition(int corner) {
    return Vec3.atLowerCornerOf(worldPosition).add(offset(corner));
  }

  public void changed() {
    setChanged();
    if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
  }

  public static void tick(Level level, BlockPos pos, BlockState state, Infuser s) {
    if (level instanceof ServerLevel server)
      s.advance(id -> server.getServer().getPlayerList().getPlayer(id));
  }

  private boolean eligible(int i, ServerPlayer p) {
    return shards[i].is(Automatons.LUMITE_SHARD.get())
        && p != null
        && p.getUUID().equals(donors[i])
        && p.isAlive()
        && p.level() == level
        && p.distanceToSqr(shardPosition(i)) <= Math.pow(Balance.INFUSE_RANGE.get(), 2)
        && Lumina.thirds(p) > 0;
  }

  void advance(Function<UUID, ServerPlayer> players) {
    if (!(level instanceof ServerLevel server)) return;
    ServerPlayer[] owners = new ServerPlayer[4];
    boolean any = false, update = false;
    for (int i = 0; i < 4; i++) {
      owners[i] = donors[i] == null ? null : players.apply(donors[i]);
      boolean valid = eligible(i, owners[i]);
      any |= valid;
      if (!valid && transferring[i]) {
        transferring[i] = false;
        update = true;
      }
    }
    if (!any) transferTicks = 0;
    else if (++transferTicks >= Balance.INFUSE_INTERVAL.get()) {
      transferTicks = 0;
      // One point TOTAL per interval. Three indivisible thirds rotate fairly over eligible corners.
      int budget = 3;
      while (budget > 0) {
        int selected = -1;
        for (int n = 0; n < 4; n++) {
          int i = (nextCorner + n) % 4;
          if (eligible(i, owners[i])) {
            selected = i;
            break;
          }
        }
        if (selected < 0) break;
        int i = selected;
        nextCorner = (i + 1) % 4;
        if (-Lumina.addThirds(owners[i], -1) != 1) break;
        shards[i] = LumiteShardItem.charge(shards[i], 1);
        lastTransfer[i] = level.getGameTime();
        transferring[i] = !shards[i].is(Automatons.INFUSED_LUMITE.get());
        budget--;
        update = true;
        if (shards[i].is(Automatons.INFUSED_LUMITE.get())) {
          Vec3 at = shardPosition(i);
          server.sendParticles(
              net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
              at.x,
              at.y,
              at.z,
              8,
              .08,
              .12,
              .08,
              .02);
          level.playSound(
              null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .5f, 1.2f);
        }
      }
    }
    if (update) changed();
    else if (any) setChanged();
  }

  @Override
  protected void saveAdditional(CompoundTag n) {
    super.saveAdditional(n);
    n.putInt("Version", 3);
    n.putInt("TransferTicks", transferTicks);
    n.putInt("NextCorner", nextCorner);
    ListTag contents = new ListTag();
    for (int i = 0; i < 4; i++) {
      CompoundTag c = new CompoundTag();
      c.put("Item", shards[i].save(new CompoundTag()));
      if (donors[i] != null) c.putUUID("Donor", donors[i]);
      contents.add(c);
    }
    n.put("Corners", contents);
    n.put("Recovery", recovery.save(new CompoundTag()));
    if (recoveryDonor != null) n.putUUID("RecoveryDonor", recoveryDonor);
  }

  @Override
  public void load(CompoundTag n) {
    super.load(n);
    Arrays.fill(shards, ItemStack.EMPTY);
    Arrays.fill(donors, null);
    Arrays.fill(transferring, false);
    transferTicks =
        Math.max(0, Math.min(Balance.INFUSE_INTERVAL.get() - 1, n.getInt("TransferTicks")));
    nextCorner = Math.floorMod(n.getInt("NextCorner"), 4);
    ListTag list = n.getList("Corners", 10);
    for (int i = 0; i < Math.min(4, list.size()); i++) {
      CompoundTag c = list.getCompound(i);
      shards[i] = ItemStack.of(c.getCompound("Item").copy());
      donors[i] = c.hasUUID("Donor") ? c.getUUID("Donor") : null;
      transferring[i] = c.getBoolean("Transferring");
      lastTransfer[i] = c.contains("LastTransfer") ? c.getLong("LastTransfer") : -1000;
    }
    recovery = ItemStack.of(n.getCompound(n.getInt("Version") < 3 ? "Core" : "Recovery").copy());
    String key = n.getInt("Version") < 3 ? "Donor" : "RecoveryDonor";
    recoveryDonor = n.hasUUID(key) ? n.getUUID(key) : null;
    if (!recovery.isEmpty()
        && recoveryDonor != null
        && CoreItem.charge(recovery) > 0
        && recovery.is(Automatons.EMPTY_CORE.get()))
      recovery.getOrCreateTag().putUUID("CreditOwner", recoveryDonor);
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag n = saveWithoutMetadata();
    ListTag list = n.getList("Corners", 10);
    for (int i = 0; i < 4; i++) {
      list.getCompound(i).putBoolean("Transferring", transferring[i]);
      list.getCompound(i).putLong("LastTransfer", lastTransfer[i]);
    }
    return n;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public AABB getRenderBoundingBox() {
    return new AABB(worldPosition).inflate(Balance.INFUSE_RANGE.get() + 1);
  }
}
