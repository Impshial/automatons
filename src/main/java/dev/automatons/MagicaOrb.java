package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** XP-orb motion/denominations, with independent identity, merging and accounting. */
public final class MagicaOrb extends Entity {
  private static final EntityDataAccessor<Integer> VALUE =
      SynchedEntityData.defineId(MagicaOrb.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Boolean> SMALL =
      SynchedEntityData.defineId(MagicaOrb.class, EntityDataSerializers.BOOLEAN);
  public int age, count = 1;
  private int health = 5;
  private Player following;

  public MagicaOrb(EntityType<? extends MagicaOrb> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData() {
    entityData.define(VALUE, 1);
    entityData.define(SMALL, false);
  }

  public int value() {
    return entityData.get(VALUE);
  }

  public void setValue(int value) {
    entityData.set(VALUE, Math.max(1, value));
  }

  public boolean small() {
    return entityData.get(SMALL);
  }

  public void setSmall(boolean value) {
    entityData.set(SMALL, value);
  }

  public long valueThirds() {
    return value() * (small() ? 1L : 3L);
  }

  public long totalThirds() {
    return valueThirds() * count;
  }

  public double total() {
    return totalThirds() / 3.0;
  }

  public static void drop(ServerLevel level, Vec3 position, int largeValue, boolean small) {
    if (largeValue <= 0) return;
    MagicaOrb orb = Automatons.MAGICA_ORB.get().create(level);
    orb.setValue(largeValue);
    orb.setSmall(small);
    orb.setPos(position);
    orb.setYRot(level.random.nextFloat() * 360);
    orb.setDeltaMovement(
        level.random.nextDouble() * .4 - .2,
        level.random.nextDouble() * .4,
        level.random.nextDouble() * .4 - .2);
    level.addFreshEntity(orb);
  }

  public static void award(ServerLevel level, Vec3 position, int amount) {
    while (amount > 0) {
      int value = ExperienceOrb.getExperienceValue(amount);
      amount -= value;
      int group = level.random.nextInt(40);
      var matches =
          level.getEntitiesOfClass(
              MagicaOrb.class,
              AABB.ofSize(position, 1, 1, 1),
              e ->
                  !e.isRemoved()
                      && !e.small()
                      && e.count < Integer.MAX_VALUE
                      && (e.getId() - group) % 40 == 0
                      && e.value() == value);
      if (!matches.isEmpty()) {
        MagicaOrb orb = matches.get(0);
        orb.count++;
        orb.age = 0;
        continue;
      }
      MagicaOrb orb = Automatons.MAGICA_ORB.get().create(level);
      orb.setValue(value);
      orb.setPos(position);
      orb.setYRot(level.random.nextFloat() * 360);
      orb.setDeltaMovement(
          (level.random.nextDouble() * .2 - .1) * 2,
          level.random.nextDouble() * .4,
          (level.random.nextDouble() * .2 - .1) * 2);
      level.addFreshEntity(orb);
    }
  }

  public boolean merge(MagicaOrb other) {
    if (other == this
        || other.isRemoved()
        || value() != other.value()
        || small() != other.small()
        || (other.getId() - getId()) % 40 != 0
        || (long) count + other.count > Integer.MAX_VALUE) return false;
    count += other.count;
    age = Math.min(age, other.age);
    other.discard();
    return true;
  }

  @Override
  protected MovementEmission getMovementEmission() {
    return MovementEmission.NONE;
  }

  @Override
  protected void doWaterSplashEffect() {}

  @Override
  protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
    return getOnPos(.999999f);
  }

  @Override
  public void tick() {
    super.tick();
    xo = getX();
    yo = getY();
    zo = getZ();
    Vec3 movement = getDeltaMovement();
    if (isEyeInFluid(FluidTags.WATER))
      setDeltaMovement(movement.x * .99, Math.min(movement.y + .0005, .06), movement.z * .99);
    else if (!isNoGravity()) setDeltaMovement(movement.add(0, -.03, 0));
    if (level().getFluidState(blockPosition()).is(FluidTags.LAVA))
      setDeltaMovement(
          (random.nextFloat() - random.nextFloat()) * .2,
          .2,
          (random.nextFloat() - random.nextFloat()) * .2);
    if (!level().noCollision(getBoundingBox()))
      moveTowardsClosestSpace(getX(), (getBoundingBox().minY + getBoundingBox().maxY) / 2, getZ());
    if (tickCount % 20 == 1) {
      if (following == null || following.distanceToSqr(this) > 64)
        following = level().getNearestPlayer(this, 8);
      if (!level().isClientSide)
        for (MagicaOrb orb :
            level().getEntitiesOfClass(MagicaOrb.class, getBoundingBox().inflate(.5))) merge(orb);
    }
    if (following != null && (following.isSpectator() || following.isDeadOrDying()))
      following = null;
    if (following != null) {
      Vec3 delta =
          following.position().add(0, following.getEyeHeight() / 2, 0).subtract(position());
      if (delta.lengthSqr() < 64) {
        double factor = 1 - delta.length() / 8;
        setDeltaMovement(getDeltaMovement().add(delta.normalize().scale(factor * factor * .1)));
      }
    }
    move(MoverType.SELF, getDeltaMovement());
    float friction = .98f;
    if (onGround()) {
      BlockPos below = getBlockPosBelowThatAffectsMyMovement();
      friction = level().getBlockState(below).getFriction(level(), below, this) * .98f;
    }
    setDeltaMovement(getDeltaMovement().multiply(friction, .98, friction));
    if (onGround()) setDeltaMovement(getDeltaMovement().multiply(1, -.9, 1));
    if (++age >= 6000) discard();
  }

  @Override
  public void playerTouch(Player player) {
    if (!(player instanceof ServerPlayer p) || isRemoved() || !p.isAlive() || p.isSpectator())
      return;
    long now = p.serverLevel().getGameTime();
    if (p.getPersistentData().getLong("MagicaPickupAt") > now) return;
    if (Magica.thirds(p) > Integer.MAX_VALUE * 3L + 2 - valueThirds()) return;
    p.getPersistentData().putLong("MagicaPickupAt", now + 2);
    Magica.addThirds(p, valueThirds());
    Network.pickup(this, p);
    level()
        .playSound(
            null,
            position().x,
            position().y,
            position().z,
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS,
            .1f,
            (random.nextFloat() - random.nextFloat()) * .35f + .9f);
    if (--count <= 0) discard();
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
    markHurt();
    health = (int) (health - amount);
    if (health <= 0) discard();
    return true;
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag n) {
    n.putInt("Value", value());
    n.putBoolean("Small", small());
    n.putInt("Count", count);
    n.putInt("Age", age);
    n.putInt("Health", health);
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag n) {
    setValue(n.getInt("Value"));
    setSmall(n.getBoolean("Small"));
    count = Math.max(1, n.getInt("Count"));
    age = Math.max(0, n.getInt("Age"));
    health = n.getInt("Health");
  }

  @Override
  public Packet<ClientGamePacketListener> getAddEntityPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }
}
