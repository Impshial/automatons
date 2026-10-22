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
  private static final EntityDataAccessor<Integer> WORK_INDEX =
      SynchedEntityData.defineId(Timber.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> CRACK =
      SynchedEntityData.defineId(Timber.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<CompoundTag> LAYOUT =
      SynchedEntityData.defineId(Timber.class, EntityDataSerializers.COMPOUND_TAG);
  private static final EntityDataAccessor<Boolean> LAYOUT_READY =
      SynchedEntityData.defineId(Timber.class, EntityDataSerializers.BOOLEAN);
  public final List<BlockPos> sources = new ArrayList<>(), targets = new ArrayList<>();
  public final List<BlockState> wood = new ArrayList<>();
  public final Set<BlockPos> sweep = new HashSet<>();
  public final List<ItemStack> crownLoot = new ArrayList<>();
  public List<ItemStack> pendingLoot;
  public int pendingIndex = -1;
  public UUID owner, worker;
  public Direction direction = Direction.NORTH;
  public int duration = 30;
  public boolean paused, segmented;
  public int processingTicks;
  private long playerAction;
  private int abandonedTicks;

  public Timber(EntityType<? extends Timber> type, Level l) {
    super(type, l);
    noPhysics = true;
  }

  @Override
  protected void defineSynchedData() {
    entityData.define(LAYOUT, new CompoundTag());
    entityData.define(LAYOUT_READY, true);
    entityData.define(WORK_INDEX, -1);
    entityData.define(CRACK, -1);
    entityData.define(PROGRESS, 0);
    entityData.define(CONSUMED, new CompoundTag());
  }

  public static boolean reserves(ServerLevel level, BlockPos pos, Entity except) {
    for (Timber t : level.getEntitiesOfClass(Timber.class, new AABB(pos).inflate(1))) {
      if (t == except || t.isRemoved()) continue;
      for (int i = 0; i < t.targets.size(); i++)
        if (!t.consumed(i) && t.targets.get(i).equals(pos)) return true;
    }
    return false;
  }

  public boolean layoutReady() {
    return entityData.get(LAYOUT_READY);
  }

  private boolean distinctTargets() {
    Set<BlockPos> seen = new HashSet<>();
    for (int i = 0; i < targets.size(); i++)
      if (!consumed(i) && !seen.add(targets.get(i))) return false;
    return true;
  }

  private void repairLegacyLayout() {
    if (layoutReady() || tickCount % 20 != 1 || owner == null) return;
    ServerLevel l = (ServerLevel) level();
    List<Integer> indices = new ArrayList<>();
    List<BlockPos> remaining = new ArrayList<>();
    for (int i = 0; i < sources.size(); i++)
      if (!consumed(i)) {
        indices.add(i);
        remaining.add(sources.get(i));
      }
    var tree = new TreeFinder.Tree(blockPosition(), remaining, List.of(), 1);
    var plan = TreeFinder.landing(l, tree, null, owner, this);
    if (plan == null || !TreeFinder.landingStillSafe(l, tree, plan, null, owner, this)) return;
    for (int i = 0; i < indices.size(); i++) targets.set(indices.get(i), plan.targets().get(i));
    sweep.clear();
    sweep.addAll(plan.sweep());
    direction = plan.direction();
    segmented = plan.segmented();
    entityData.set(PROGRESS, 0);
    entityData.set(LAYOUT_READY, true);
    showProcessing(-1, 0, 1);
    CompoundTag layout = new CompoundTag();
    layout.putLongArray("Targets", targets.stream().mapToLong(BlockPos::asLong).toArray());
    layout.putBoolean("Segmented", segmented);
    layout.putInt("Direction", direction.get3DDataValue());
    entityData.set(LAYOUT, layout);
    refreshBounds();
  }

  @Override
  public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
    super.onSyncedDataUpdated(key);
    if (LAYOUT.equals(key) && level().isClientSide && targets != null) {
      CompoundTag layout = entityData.get(LAYOUT);
      if (layout.contains("Targets")) {
        targets.clear();
        for (long pos : layout.getLongArray("Targets")) targets.add(BlockPos.of(pos));
        segmented = layout.getBoolean("Segmented");
        direction = Direction.from3DDataValue(layout.getInt("Direction"));
        if (!sources.isEmpty()) refreshBounds();
      }
    }
  }

  public int workIndex() {
    return entityData.get(WORK_INDEX);
  }

  public int crackStage() {
    return entityData.get(CRACK);
  }

  public void showProcessing(int index, int ticks, int total) {
    entityData.set(WORK_INDEX, index);
    entityData.set(CRACK, index < 0 ? -1 : Math.min(9, ticks * 10 / Math.max(1, total)));
  }

  public boolean dangerAt(AABB box) {
    if (progress() < duration)
      return sweep.stream().anyMatch(p -> box.intersects(new AABB(p).inflate(.08)));
    for (int i = 0; i < targets.size(); i++)
      if (!consumed(i) && box.intersects(new AABB(targets.get(i)).inflate(.03))) return true;
    return false;
  }

  public static boolean blocksWorker(Level level, AABB box) {
    return !level
        .getEntitiesOfClass(Timber.class, box.inflate(1), t -> !t.finished() && t.dangerAt(box))
        .isEmpty();
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
    return transfer(l, tree, landing, w.owner, w.getUUID(), w, w.activeTool());
  }

  static Timber commitPlayer(
      ServerLevel l,
      TreeFinder.Tree tree,
      TreeFinder.Landing landing,
      net.minecraft.server.level.ServerPlayer player,
      ItemStack tool) {
    if (!TreeFinder.landingStillSafe(l, tree, landing, null, player.getUUID(), player)) return null;
    for (BlockPos p : tree.logs())
      if (!Permissions.allowed(l, p, player.getUUID(), Permissions.Action.CUT)) return null;
    return transfer(l, tree, landing, player.getUUID(), null, player, tool);
  }

  private static Timber transfer(
      ServerLevel l,
      TreeFinder.Tree tree,
      TreeFinder.Landing landing,
      UUID owner,
      UUID worker,
      LivingEntity actor,
      ItemStack tool) {
    // All source transfers occur on the server thread in one tick; no block loot is emitted here.
    List<BlockPos> crown = TreeFinder.exclusiveCrown(l, tree);
    if (crown == null) return null;
    for (BlockPos p : crown)
      if (!Permissions.allowed(l, p, owner, Permissions.Action.CUT)) return null;
    Timber t = Automatons.TIMBER.get().create(l);
    if (t == null) return null;
    t.owner = owner;
    t.worker = worker;
    t.direction = landing.direction();
    t.segmented = landing.segmented();
    t.duration = Balance.FALL.get();
    t.sources.addAll(tree.logs());
    t.targets.addAll(landing.targets());
    t.sweep.addAll(landing.sweep());
    t.sweep.addAll(crown);
    for (BlockPos p : crown)
      t.crownLoot.addAll(Block.getDrops(l.getBlockState(p), l, p, null, actor, tool));
    for (BlockPos p : tree.logs()) t.wood.add(l.getBlockState(p));
    t.setPos(Vec3.atBottomCenterOf(tree.root()));
    t.refreshBounds();
    if (!l.addFreshEntity(t)) return null;
    // Suppress intermediate neighbor destruction: attached plants must not drop once through
    // vanilla physics and a second time through the owned crown ledger.
    for (BlockPos p : tree.logs()) l.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
    for (BlockPos p : crown) l.setBlock(p, l.getFluidState(p).createLegacyBlock(), 18);
    Set<BlockPos> changed = new HashSet<>(tree.logs());
    changed.addAll(crown);
    for (BlockPos p : changed) {
      l.getBlockState(p).updateNeighbourShapes(l, p, 3);
      l.updateNeighborsAt(p, Blocks.AIR);
    }
    // Shared canopy stays in the world. Owned foliage loot is held until a permitted timber
    // harvest.
    return t;
  }

  public boolean safe(Station s) {
    if (!(level() instanceof ServerLevel l)
        || !layoutReady()
        || owner == null
        || (s != null && !TreeFinder.within(s, blockPosition()))) return false;
    if (progress() < duration) {
      for (BlockPos p : sweep)
        if (!l.hasChunkAt(p)
            || WorldData.get(l).protectedAt(p)
            || (!TreeFinder.clearTimberSpace(l.getBlockState(p))
                && !(l.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES)
                    && !l.getBlockState(p).getValue(LeavesBlock.PERSISTENT)))) return false;
    }
    for (int i = 0; i < targets.size(); i++)
      if (!consumed(i)) {
        BlockPos p = targets.get(i);
        if (!l.hasChunkAt(p)
            || WorldData.get(l).protectedAt(p)
            || !TreeFinder.clearTimberSpace(l.getBlockState(p))
            || !l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) return false;
      }
    return true;
  }

  @Override
  public void tick() {
    super.tick();
    setDeltaMovement(Vec3.ZERO);
    if (!level().isClientSide) {
      repairLegacyLayout();
      if (worker != null) {
        var entity = ((ServerLevel) level()).getEntity(worker);
        if (!(entity instanceof Lumberton w)
            || !w.authorized
            || w.job() != 1
            || !getUUID().equals(w.timberId)) {
          if (++abandonedTicks >= 200) {
            worker = null;
            abandonedTicks = 0;
          }
        } else abandonedTicks = 0;
      }
      if (tickCount % 5 == 0)
        for (Lumberton w : level().getEntitiesOfClass(Lumberton.class, getBoundingBox().inflate(1)))
          if (dangerAt(w.getBoundingBox())) w.moveClearOf(this);
      paused = !safe(null);
      if (!paused && progress() < duration) {
        boolean occupied = false;
        ServerLevel l = (ServerLevel) level();
        for (BlockPos p : sweep)
          if (!l.getEntities(this, new AABB(p), e -> e instanceof LivingEntity).isEmpty()) {
            occupied = true;
            break;
          }
        if (!occupied) entityData.set(PROGRESS, progress() + 1);
      }
      if (finished()) discard();
    }
  }

  public boolean claim(Lumberton w) {
    if (owner == null || !owner.equals(w.owner) || finished()) return false;
    if (worker != null && !worker.equals(w.getUUID())) return false;
    worker = w.getUUID();
    abandonedTicks = 0;
    return true;
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
        || !w.getUUID().equals(worker)
        || !Inventories.axe(w.activeTool())
        || !w.inReach(targets.get(index))) return false;
    if (!Permissions.allowed(
        (ServerLevel) level(), targets.get(index), w.owner, Permissions.Action.PROCESS))
      return false;
    if (pendingLoot == null || pendingIndex != index) {
      pendingLoot = new ArrayList<>(drops(index, w.activeTool(), w));
      pendingLoot.addAll(crownLoot);
      crownLoot.clear();
      pendingIndex = index;
    }
    // Persist rejected remainders; a large crown cannot deadlock a smaller cargo inventory.
    for (int i = 0; i < pendingLoot.size(); ) {
      ItemStack rest = Inventories.insert(w.cargo, pendingLoot.get(i), false);
      if (rest.isEmpty()) pendingLoot.remove(i);
      else {
        pendingLoot.set(i, rest);
        i++;
      }
    }
    if (!pendingLoot.isEmpty()) {
      w.alerts |= Lumberton.CARGO;
      return false;
    }
    mark(index);
    pendingLoot = null;
    pendingIndex = -1;
    crownLoot.clear();
    processingTicks = 0;
    level()
        .playSound(
            null,
            targets.get(index),
            wood.get(index).getSoundType().getBreakSound(),
            net.minecraft.sounds.SoundSource.BLOCKS,
            .7f,
            1);
    w.wear(1);
    return true;
  }

  private void mark(int index) {
    showProcessing(-1, 0, 1);
    processingTicks = 0;
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
    n.putBoolean("Segmented", segmented);
    n.putInt("ProcessingTicks", processingTicks);
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
    segmented = n.getBoolean("Segmented");
    processingTicks = Math.max(0, n.getInt("ProcessingTicks"));
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
    entityData.set(LAYOUT_READY, distinctTargets());
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
