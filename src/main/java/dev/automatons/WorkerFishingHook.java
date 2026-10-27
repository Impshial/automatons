package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.network.NetworkHooks;

/** Vanilla bite, weather, lure and open-water logic, with an isolated non-owner fishing actor. */
public final class WorkerFishingHook extends FishingHook {
  private static final EntityDataAccessor<Integer> WORKER = SynchedEntityData.defineId(WorkerFishingHook.class, EntityDataSerializers.INT);
  // Forge resolves this stable SRG name to nibble in the development mappings.
  private static final java.lang.reflect.Field NIBBLE = ObfuscationReflectionHelper.findField(FishingHook.class, "f_37089_");
  private FakePlayer actor;
  private ItemStack castRod = ItemStack.EMPTY;
  private boolean reeled;
  public WorkerFishingHook(EntityType<? extends WorkerFishingHook> type, Level level) { super(type, level); }
  private WorkerFishingHook(Lumberton worker, FakePlayer actor) {
    super(actor, worker.level(), EnchantmentHelper.getFishingLuckBonus(worker.activeTool()), EnchantmentHelper.getFishingSpeedBonus(worker.activeTool()));
    this.actor = actor; castRod = worker.activeTool().copy(); entityData.set(WORKER, worker.getId()); setOwner(worker);
  }
  public static WorkerFishingHook cast(Lumberton worker, Vec3 water) {
    UUID identity = UUID.nameUUIDFromBytes(("automatons:fishing:" + worker.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    FakePlayer actor = FakePlayerFactory.get((ServerLevel)worker.level(), new GameProfile(identity, "[Fisherton]"));
    actor.setPos(worker.position());
    Vec3 direction = water.subtract(actor.getEyePosition());
    actor.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
    actor.setXRot((float)-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
    actor.setItemInHand(InteractionHand.MAIN_HAND, worker.activeTool().copy());
    return new WorkerFishingHook(worker, actor);
  }
  @Override public EntityType<?> getType() { return Automatons.FISHING_HOOK.get(); }
  @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(WORKER, -1); }
  public Lumberton worker() { return level().getEntity(entityData.get(WORKER)) instanceof Lumberton worker ? worker : null; }
  @Override public Player getPlayerOwner() { return actor == null ? super.getPlayerOwner() : actor; }
  @Override protected boolean canHitEntity(Entity target) { return false; }
  public boolean bitingNow() {
    try { return NIBBLE.getInt(this) > 0; }
    catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read vanilla fishing bite state", error); }
  }
  @Override public void tick() {
    if (level().isClientSide) { baseTick(); return; }
    Lumberton worker = worker();
    if (worker == null || !worker.isAlive() || worker.job() != WorkerJob.FISHERTON || worker.fishingPose() == 0
        || actor == null || !ItemStack.isSameItemSameTags(worker.activeTool(), castRod) || tickCount > 1600) { discard(); return; }
    actor.setPos(worker.position()); actor.setItemInHand(InteractionHand.MAIN_HAND, worker.activeTool().copy()); actor.fishing = this;
    super.tick();
  }
  @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
    setPos(x, y, z); setYRot(yaw); setXRot(pitch);
  }
  public boolean reel() {
    Lumberton worker = worker();
    if (reeled || worker == null || !bitingNow() || !(level() instanceof ServerLevel level)) return false;
    reeled = true;
    ItemStack rod = worker.activeTool();
    LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position())
        .withParameter(LootContextParams.TOOL, rod).withParameter(LootContextParams.THIS_ENTITY, this)
        .withLuck(EnchantmentHelper.getFishingLuckBonus(rod)).create(LootContextParamSets.FISHING);
    List<ItemStack> loot = new ArrayList<>(level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params));
    ItemFishedEvent event = new ItemFishedEvent(loot, onGround() ? 2 : 1, this);
    if (!MinecraftForge.EVENT_BUS.post(event)) {
      for (ItemStack catchItem : event.getDrops()) {
        if (catchItem.isEmpty()) continue;
        ItemEntity drop = new ItemEntity(level, getX(), getY(), getZ(), catchItem.copy());
        Vec3 delta = worker.position().subtract(position());
        drop.setDeltaMovement(delta.x * .1, delta.y * .1 + Math.sqrt(delta.length()) * .08, delta.z * .1);
        level.addFreshEntity(drop);
      }
    }
    // No owner inventory, statistics, advancements or player experience are used by this adapter.
    worker.wear(Math.max(0, event.getRodDamage())); discard(); return true;
  }
  @Override public void remove(RemovalReason reason) {
    boolean ownActor = actor != null && (actor.fishing == null || actor.fishing == this);
    super.remove(reason);
    if (ownActor) { actor.fishing = null; actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); }
  }
  @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
