package dev.automatons;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class Infuser extends BlockEntity {
  public ItemStack core = ItemStack.EMPTY;
  public UUID donor;
  public boolean transferring;
  public long lastTransfer;
  private int transferTicks;

  public Infuser(BlockPos pos, BlockState state) {
    super(Automatons.INFUSER_BE.get(), pos, state);
  }

  public Vec3 corePosition() {
    return Vec3.atBottomCenterOf(worldPosition).add(0, .9, 0);
  }

  public void changed() {
    setChanged();
    if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
  }

  public static void tick(Level level, BlockPos pos, BlockState state, Infuser s) {
    if (!(level instanceof ServerLevel server)) return;
    ServerPlayer player =
        s.donor == null ? null : server.getServer().getPlayerList().getPlayer(s.donor);
    s.advance(player);
  }

  void advance(ServerPlayer player) {
    if (!(level instanceof ServerLevel server)) return;
    Infuser s = this;
    BlockPos pos = worldPosition;
    boolean valid =
        !s.core.isEmpty()
            && s.core.is(Automatons.EMPTY_CORE.get())
            && player != null
            && player.getUUID().equals(s.donor)
            && player.isAlive()
            && player.level() == level
            && player.distanceToSqr(s.corePosition())
                <= Balance.INFUSE_RANGE.get() * Balance.INFUSE_RANGE.get()
            && Magica.balance(player) > 0;
    if (s.transferring != valid) {
      s.transferring = valid;
      s.changed();
    }
    if (!valid) {
      s.transferTicks = 0;
      return;
    }
    if (++s.transferTicks < Balance.INFUSE_INTERVAL.get()) return;
    s.transferTicks = 0;
    int charge = CoreItem.charge(s.core);
    if (charge < CoreItem.CAPACITY && Magica.spend(player, 1)) {
      s.core = CoreItem.vessel(charge + 1);
      s.lastTransfer = level.getGameTime();
      if (charge + 1 == CoreItem.CAPACITY) {
        s.transferring = false;
        server.sendParticles(
            net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
            pos.getX() + .5,
            pos.getY() + .9,
            pos.getZ() + .5,
            10,
            .2,
            .15,
            .2,
            .05);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .8f, 1.2f);
      }
      s.changed();
    }
  }

  @Override
  protected void saveAdditional(CompoundTag n) {
    super.saveAdditional(n);
    n.put("Core", core.save(new CompoundTag()));
    if (donor != null) n.putUUID("Donor", donor);
    n.putInt("Version", 2);
  }

  @Override
  public void load(CompoundTag n) {
    super.load(n);
    core = ItemStack.of(n.getCompound("Core"));
    if (!core.isEmpty()) core.setCount(1);
    donor = n.hasUUID("Donor") ? n.getUUID("Donor") : null;
    transferring = n.getBoolean("Transferring");
    lastTransfer = n.getLong("LastTransfer");
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag n = saveWithoutMetadata();
    n.putBoolean("Transferring", transferring);
    n.putLong("LastTransfer", lastTransfer);
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
