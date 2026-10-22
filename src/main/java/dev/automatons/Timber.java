package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

public class Timber extends Entity implements IEntityAdditionalSpawnData {
  private static final EntityDataAccessor<Integer> PROGRESS =
      SynchedEntityData.defineId(Timber.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<CompoundTag> CONSUMED =
      SynchedEntityData.defineId(Timber.class, EntityDataSerializers.COMPOUND_TAG);
  public final List<BlockPos> sources = new ArrayList<>(), targets = new ArrayList<>();
  public final List<BlockState> wood = new ArrayList<>();
  public final Set<BlockPos> sweep = new HashSet<>();
  public final List<ItemStack> crownLoot = new ArrayList<>();
  public List<ItemStack> pendingLoot;
  public int pendingIndex = -1;
  public UUID owner, worker;
  public Direction direction = Direction.NORTH;
  public int duration = 30;
  public boolean paused;
  private long playerAction;

  public Timber(EntityType<? extends Timber> type, Level l) {
    super(type, l);
    noPhysics = true;
  }

  @Override
  protected void defineSynchedData() {
    entityData.define(PROGRESS, 0);
    entityData.define(CONSUMED, new CompoundTag());
  }

  public int progress() {
    return entityData.get(PROGRESS);
  }

  public boolean consumed(int i) {
    return entityData.get(CONSUMED).getBoolean("s" + i);
  }

  public boolean finished() {
    for (int i = 0; i < wood.size(); i++) if (!consumed(i)) return false;
    return true;
  }

  public int remaining() {
    int n = 0;
    for (int i = 0; i < wood.size(); i++) if (!consumed(i)) n++;
    return n;
  }

  public int next() {
    for (int i = 0; i < wood.size(); i++) if (!consumed(i)) return i;
    return -1;
  }

  public static Timber commit(
      ServerLevel l, TreeFinder.Tree tree, TreeFinder.Landing landing, Lumberton w, Station s) {
    if (!TreeFinder.revalidate(l, tree, s, w.owner)
        || !TreeFinder.landingStillSafe(l, tree, landing, s, w.owner, w)) return null;
    // All source transfers occur on the server thread in one tick; no block loot is emitted here.
    List<BlockPos> crown = TreeFinder.exclusiveCrown(l, tree);
    if (crown == null) return null;
    for (BlockPos p : crown)
      if (!Permissions.allowed(l, p, w.owner, Permissions.Action.CUT)) return null;
    Timber t = Automatons.TIMBER.get().create(l);
    if (t == null) return null;
    t.owner = w.owner;
    t.worker = w.getUUID();
    t.direction = landing.direction();
    t.duration = Balance.FALL.get();
    t.sources.addAll(tree.logs());
    t.targets.addAll(landing.targets());
    t.sweep.addAll(landing.sweep());
    t.sweep.addAll(crown);
    for (BlockPos p : crown)
      t.crownLoot.addAll(Block.getDrops(l.getBlockState(p), l, p, null, w, w.activeTool()));
    for (BlockPos p : tree.logs()) t.wood.add(l.getBlockState(p));
    t.setPos(Vec3.atBottomCenterOf(tree.root()));
    t.refreshBounds();
    if (!l.addFreshEntity(t)) return null;
    for (BlockPos p : tree.logs()) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    for (BlockPos p : crown) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    // Shared canopy stays in the world. Owned foliage loot is held until a permitted timber
    // harvest.
    return t;
  }

  public boolean safe(Station s) {
    if (!(level() instanceof ServerLevel l) || owner == null) return false;
    if (progress() < duration) {
      for (BlockPos p : sweep)
        if (!l.hasChunkAt(p)
            || WorldData.get(l).protectedAt(p)
            || (s != null && !TreeFinder.within(s, p))
            || (!l.getBlockState(p).isAir()
                && !(l.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES)
                    && !l.getBlockState(p).getValue(LeavesBlock.PERSISTENT)))) return false;
    }
    for (int i = 0; i < targets.size(); i++)
      if (!consumed(i)) {
        BlockPos p = targets.get(i);
        if (!l.hasChunkAt(p)
            || WorldData.get(l).protectedAt(p)
            || (s != null && !TreeFinder.within(s, p))
            || !l.getBlockState(p).isAir()) return false;
      }
    return true;
  }

  @Override
  public void tick() {
    super.tick();
    setDeltaMovement(Vec3.ZERO);
    if (!level().isClientSide) {
      paused = !safe(null);
      if (!paused && progress() < duration) {
        boolean occupied = false;
        ServerLevel l = (ServerLevel) level();
        for (BlockPos p : sweep)
          if (!l.getEntities(
                  this, new AABB(p), e -> e instanceof LivingEntity && !e.getUUID().equals(worker))
              .isEmpty()) {
            occupied = true;
            break;
          }
        if (!occupied) entityData.set(PROGRESS, progress() + 1);
      }
      if (finished()) discard();
    }
  }

  public List<ItemStack> drops(int index, ItemStack tool, Entity actor) {
    return Block.getDrops(
        wood.get(index), (ServerLevel) level(), targets.get(index), null, actor, tool);
  }

  public boolean process(int index, Lumberton w, Station s) {
    if (index < 0
        || index >= wood.size()
        || consumed(index)
        || progress() < duration
        || !safe(s)
        || !Inventories.axe(w.activeTool())
        || w.distanceToSqr(Vec3.atCenterOf(targets.get(index))) > 10) return false;
    if (!Permissions.allowed(
        (ServerLevel) level(), targets.get(index), w.owner, Permissions.Action.PROCESS))
      return false;
    if (pendingLoot == null || pendingIndex != index) {
      pendingLoot = new ArrayList<>(drops(index, w.activeTool(), w));
      pendingLoot.addAll(crownLoot);
      crownLoot.clear();
      pendingIndex = index;
    }
    List<ItemStack> drops = pendingLoot;
    if (!Inventories.fits(w.cargo, drops)) {
      w.alerts |= Lumberton.CARGO;
      return false;
    }
    mark(index);
    for (ItemStack drop : drops) Inventories.insert(w.cargo, drop, false);
    pendingLoot = null;
    pendingIndex = -1;
    crownLoot.clear();
    w.wear(1);
    return true;
  }

  private void mark(int index) {
    CompoundTag n = entityData.get(CONSUMED).copy();
    n.putBoolean("s" + index, true);
    entityData.set(CONSUMED, n);
  }

  @Override
  public boolean isPickable() {
    return true;
  }

  @Override
  public InteractionResult interact(Player p, InteractionHand hand) {
    if (!Inventories.axe(p.getItemInHand(hand))) return InteractionResult.PASS;
    if (!level().isClientSide && progress() >= duration && level().getGameTime() >= playerAction) {
      for (int i = 0; i < targets.size(); i++)
        if (!consumed(i)
            && p.distanceToSqr(Vec3.atCenterOf(targets.get(i))) < 20
            && Permissions.allowed(
                (ServerLevel) level(), targets.get(i), p.getUUID(), Permissions.Action.PROCESS)) {
          if (!safe(null)) break;
          List<ItemStack> drops =
              pendingLoot != null && pendingIndex == i
                  ? pendingLoot
                  : new ArrayList<>(drops(i, p.getItemInHand(hand), p));
          if (pendingLoot == null || pendingIndex != i) drops.addAll(crownLoot);
          crownLoot.clear();
          mark(i);
          if (pendingIndex == i) {
            pendingLoot = null;
            pendingIndex = -1;
          }
          for (ItemStack stack : drops) {
            var item =
                new net.minecraft.world.entity.item.ItemEntity(
                    level(),
                    targets.get(i).getX() + .5,
                    targets.get(i).getY() + .5,
                    targets.get(i).getZ() + .5,
                    stack);
            level().addFreshEntity(item);
          }
          p.getItemInHand(hand).hurtAndBreak(1, p, e -> e.broadcastBreakEvent(hand));
          playerAction = level().getGameTime() + 10;
          break;
        }
    }
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  private void refreshBounds() {
    AABB bounds = new AABB(blockPosition());
    for (BlockPos p : sources) bounds = bounds.minmax(new AABB(p));
    for (BlockPos p : targets) bounds = bounds.minmax(new AABB(p));
    for (BlockPos p : sweep) bounds = bounds.minmax(new AABB(p));
    setBoundingBox(bounds.inflate(.1));
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag n) {
    if (owner != null) n.putUUID("Owner", owner);
    if (worker != null) n.putUUID("Worker", worker);
    ListTag foliage = new ListTag();
    for (ItemStack stack : crownLoot) foliage.add(stack.save(new CompoundTag()));
    n.put("CrownLoot", foliage);
    if (pendingLoot != null) {
      ListTag loot = new ListTag();
      for (ItemStack item : pendingLoot) loot.add(item.save(new CompoundTag()));
      n.put("PendingLoot", loot);
      n.putInt("PendingIndex", pendingIndex);
    }
    n.putInt("Direction", direction.get3DDataValue());
    n.putInt("Duration", duration);
    n.putInt("Progress", progress());
    n.put("Consumed", entityData.get(CONSUMED));
    ListTag list = new ListTag();
    for (int i = 0; i < wood.size(); i++) {
      CompoundTag c = new CompoundTag();
      c.putLong("Source", sources.get(i).asLong());
      c.putLong("Target", targets.get(i).asLong());
      c.put("Wood", NbtUtils.writeBlockState(wood.get(i)));
      list.add(c);
    }
    n.put("Sections", list);
    n.putLongArray("Sweep", sweep.stream().mapToLong(BlockPos::asLong).toArray());
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag n) {
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    worker = n.hasUUID("Worker") ? n.getUUID("Worker") : null;
    crownLoot.clear();
    for (Tag tag : n.getList("CrownLoot", 10)) crownLoot.add(ItemStack.of((CompoundTag) tag));
    pendingLoot = null;
    pendingIndex = -1;
    if (n.contains("PendingLoot")) {
      pendingLoot = new ArrayList<>();
      for (Tag tag : n.getList("PendingLoot", 10)) pendingLoot.add(ItemStack.of((CompoundTag) tag));
      pendingIndex = n.getInt("PendingIndex");
    }
    direction = Direction.from3DDataValue(n.getInt("Direction"));
    duration = Math.max(20, n.getInt("Duration"));
    entityData.set(PROGRESS, n.getInt("Progress"));
    entityData.set(CONSUMED, n.getCompound("Consumed"));
    sources.clear();
    targets.clear();
    wood.clear();
    for (Tag tag : n.getList("Sections", 10)) {
      CompoundTag c = (CompoundTag) tag;
      sources.add(BlockPos.of(c.getLong("Source")));
      targets.add(BlockPos.of(c.getLong("Target")));
      wood.add(
          NbtUtils.readBlockState(
              level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
              c.getCompound("Wood")));
    }
    sweep.clear();
    for (long p : n.getLongArray("Sweep")) sweep.add(BlockPos.of(p));
    refreshBounds();
  }

  @Override
  public Packet<ClientGamePacketListener> getAddEntityPacket() {
    return NetworkHooks.getEntitySpawningPacket(this);
  }

  @Override
  public void writeSpawnData(FriendlyByteBuf b) {
    CompoundTag n = new CompoundTag();
    addAdditionalSaveData(n);
    b.writeNbt(n);
  }

  @Override
  public void readSpawnData(FriendlyByteBuf b) {
    readAdditionalSaveData(b.readNbt());
  }
}
