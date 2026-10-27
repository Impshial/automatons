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
  private static final EntityDataAccessor<Boolean> PLACED =
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
  public boolean paused, segmented, rigidFall, placeOnLanding;
  public int processingTicks;
  private int abandonedTicks;
  private float previousVisual, currentVisual;
  private AABB timberBounds;

  @Override
  public void setPos(double x, double y, double z) {
    super.setPos(x, y, z);
    // Spawn/teleport packets rebuild Entity's default one-block box. The visible tree can be
    // many blocks from its root, so keep the complete layout for culling and spatial queries.
    if (sources != null) refreshBounds();
  }

  @Override
  public AABB getBoundingBoxForCulling() {
    return timberBounds == null ? super.getBoundingBoxForCulling() : timberBounds;
  }

  public float visualProgress(float partial) {
    if (!level().isClientSide) return progress();
    return net.minecraft.util.Mth.lerp(partial, previousVisual, currentVisual);
  }

  public Timber(EntityType<? extends Timber> type, Level l) {
    super(type, l);
    noPhysics = true;
  }

  @Override
  protected void defineSynchedData() {
    entityData.define(LAYOUT, new CompoundTag());
    entityData.define(LAYOUT_READY, true);
    entityData.define(PLACED, false);
    entityData.define(WORK_INDEX, -1);
    entityData.define(CRACK, -1);
    entityData.define(PROGRESS, 0);
    entityData.define(CONSUMED, new CompoundTag());
  }

  public static boolean reserves(ServerLevel level, BlockPos pos, Entity except) {
    for (Timber t : nearby(level, new AABB(pos))) {
      if (t == except || t.isRemoved()) continue;
      for (int i = 0; i < t.targets.size(); i++)
        if (!t.consumed(i) && t.targets.get(i).equals(pos)) return true;
    }
    return false;
  }

  public boolean layoutReady() {
    return entityData.get(LAYOUT_READY);
  }

  public boolean hasPlacedBlocks() {
    return entityData.get(PLACED);
  }

  CompoundTag auditState() {
    CompoundTag n = new CompoundTag();
    n.putBoolean("Placed", hasPlacedBlocks());
    n.putInt("Progress", progress());
    n.put("Consumed", entityData.get(CONSUMED).copy());
    n.put("Layout", entityData.get(LAYOUT).copy());
    n.putBoolean("Ready", layoutReady());
    return n;
  }

  public void applyAuditState(CompoundTag n) {
    entityData.set(PLACED, n.getBoolean("Placed"));
    entityData.set(PROGRESS, n.getInt("Progress"));
    entityData.set(CONSUMED, n.getCompound("Consumed").copy());
    entityData.set(LAYOUT, n.getCompound("Layout").copy());
    entityData.set(LAYOUT_READY, n.getBoolean("Ready"));
    refreshBounds();
  }

  private boolean ownsLog(int index) {
    BlockPos at = targets.get(index);
    return level().getBlockState(at).equals(TreeFinder.fallenState(wood.get(index), direction))
        && !WorldData.get((ServerLevel) level()).placedLogs.contains(at.asLong());
  }

  /** Reconcile only this job's original blocks. Player replacements never become job cargo. */
  void sweepPlacedLogs() {
    if (!hasPlacedBlocks() || !(level() instanceof ServerLevel l)) return;
    for (int i = 0; i < targets.size(); i++) {
      if (consumed(i) || pendingLoot != null && pendingIndex == i) continue;
      if (l.hasChunkAt(targets.get(i)) && !ownsLog(i)) mark(i);
    }
    if (finished()) {
      for (ItemStack stack : crownLoot) Block.popResource(l, blockPosition(), stack.copy());
      crownLoot.clear();
      discard();
    }
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
    rigidFall = true;
    entityData.set(PROGRESS, 0);
    entityData.set(LAYOUT_READY, true);
    showProcessing(-1, 0, 1);
    CompoundTag layout = new CompoundTag();
    layout.putLongArray("Targets", targets.stream().mapToLong(BlockPos::asLong).toArray());
    layout.putBoolean("Segmented", segmented);
    layout.putBoolean("RigidFall", rigidFall);
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
        rigidFall = layout.getBoolean("RigidFall");
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
    // Once landed, Minecraft's ordinary block collision handles the logs.
    if (hasPlacedBlocks()) return false;
    if (progress() < duration)
      return sweep.stream().anyMatch(p -> box.intersects(new AABB(p).inflate(.08)));
    for (int i = 0; i < targets.size(); i++)
      if (!consumed(i) && box.intersects(new AABB(targets.get(i)).inflate(.03))) return true;
    return false;
  }

  /** Section lookup is anchored at the root, which can be far from a fallen tip. */
  public static List<Timber> nearby(Level level, AABB box) {
    return level.getEntitiesOfClass(Timber.class, box.inflate(64, 80, 64), t -> !t.finished());
  }

  public static boolean blocksWorker(Level level, AABB box) {
    return nearby(level, box).stream().anyMatch(t -> t.dangerAt(box));
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    // Entity.load restores position after reading our data, resetting the custom bounds.
    refreshBounds();
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
    List<BlockPos> crown = TreeFinder.fallFoliage(l, tree, landing);
    if (crown == null) return null;
    if (worker != null
        && java.util.stream.Stream.concat(tree.logs().stream(), crown.stream())
            .anyMatch(p -> ForestryHelp.needsPickaxe(l.getBlockState(p)))) return null;
    if (worker != null) {
      List<BlockPos> owned = TreeFinder.exclusiveCrown(l, tree);
      if (owned == null) return null;
      Set<BlockPos> canopy = new HashSet<>(owned);
      crown =
          TreeFinder.loose(tree)
              ? List.of()
              : crown.stream()
                  .filter(
                      p ->
                          !l.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES)
                              || canopy.contains(p))
                  .toList();
    }
    for (BlockPos p : crown)
      if (!Permissions.allowed(l, p, owner, Permissions.Action.CUT)) return null;
    Timber t = Automatons.TIMBER.get().create(l);
    if (t == null) return null;
    t.owner = owner;
    t.rigidFall = true;
    t.placeOnLanding = worker == null && actor instanceof net.minecraft.server.level.ServerPlayer;
    t.worker = worker;
    t.direction = landing.direction();
    t.segmented = landing.segmented();
    t.duration = Balance.FALL.get();
    t.sources.addAll(tree.logs());
    t.targets.addAll(landing.targets());
    t.sweep.addAll(landing.sweep());
    Map<BlockPos, List<ItemStack>> immediateLeaves = new LinkedHashMap<>();
    for (BlockPos p : crown) {
      BlockState state = l.getBlockState(p);
      if (state.getBlock() instanceof DoublePlantBlock
          && state.getValue(DoublePlantBlock.HALF)
              == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
          && crown.contains(p.below())) continue;
      if (worker != null && state.is(net.minecraft.tags.BlockTags.LEAVES))
        immediateLeaves.put(p, Block.getDrops(state, l, p, null, actor, ItemStack.EMPTY));
      else t.crownLoot.addAll(Block.getDrops(state, l, p, null, actor, tool));
    }
    for (BlockPos p : tree.logs()) t.wood.add(l.getBlockState(p));
    t.setPos(Vec3.atBottomCenterOf(tree.root()));
    t.refreshBounds();
    if (!l.addFreshEntity(t)) return null;
    // Suppress intermediate neighbor destruction: attached plants must not drop once through
    // vanilla physics and a second time through the owned crown ledger.
    for (BlockPos p : tree.logs()) l.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
    for (BlockPos p : crown) l.setBlock(p, l.getFluidState(p).createLegacyBlock(), 18);
    immediateLeaves.forEach((p, drops) -> drops.forEach(drop -> Block.popResource(l, p, drop)));
    Set<BlockPos> changed = new HashSet<>(tree.logs());
    changed.addAll(crown);
    for (BlockPos p : changed) {
      l.getBlockState(p).updateNeighbourShapes(l, p, 3);
      l.updateNeighborsAt(p, Blocks.AIR);
    }
    // Shared canopy outside the swept corridor stays intact. Worker-owned leaves drop now;
    // non-leaf attachments retain the existing exact-once timber payout.
    return t;
  }

  public boolean safe(Station s) {
    if (!(level() instanceof ServerLevel l)
        || !layoutReady()
        || owner == null
        || (s != null && !TreeFinder.within(s, blockPosition()))) return false;
    if (hasPlacedBlocks()) {
      for (int i = 0; i < targets.size(); i++)
        if (!consumed(i)
            && (!l.hasChunkAt(targets.get(i))
                || WorldData.get(l).protectedAt(targets.get(i))
                || !(pendingLoot != null && pendingIndex == i) && !ownsLog(i))) return false;
      return true;
    }
    if (progress() < duration) {
      for (BlockPos p : sweep)
        if (!l.hasChunkAt(p)
            || WorldData.get(l).protectedAt(p)
            || (!TreeFinder.clearTimberSpace(l.getBlockState(p))
                && !(l.getBlockState(p).is(net.minecraft.tags.BlockTags.LEAVES)
                    && !l.getBlockState(p).getValue(LeavesBlock.PERSISTENT)))) return false;
    }
    boolean supported = false;
    for (int i = 0; i < targets.size(); i++)
      if (!consumed(i)) {
        BlockPos p = targets.get(i);
        if (!l.hasChunkAt(p)
            || WorldData.get(l).protectedAt(p)
            || !TreeFinder.clearTimberSpace(l.getBlockState(p))) return false;
        supported |= l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP);
      }
    return (progress() >= duration && !placeOnLanding) || supported;
  }

  public int landingLift() {
    return targets.isEmpty()
        ? 0
        : targets.get(0).getY()
            - TreeFinder.fallenPosition(sources.get(0), blockPosition(), direction, 0).getY();
  }

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide) {
      previousVisual = currentVisual;
      currentVisual += Math.max(0, Math.min(1.5f, (progress() - currentVisual) * .5f));
      if (progress() >= duration && duration - currentVisual < .05f) currentVisual = duration;
    }
    setDeltaMovement(Vec3.ZERO);
    if (!level().isClientSide) {
      sweepPlacedLogs();
      if (isRemoved()) return;
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
      if (!paused && progress() < duration) entityData.set(PROGRESS, progress() + 1);
      if (!paused && !hasPlacedBlocks() && progress() >= duration) placeLandedLogs();
      if (finished()) discard();
    }
  }

  /** Finish the animation with actual sideways logs; retain only the worker's job ledger. */
  private void placeLandedLogs() {
    ServerLevel l = (ServerLevel) level();
    for (int i = 0; i < targets.size(); i++) {
      if (consumed(i) || pendingLoot != null && pendingIndex == i) continue;
      BlockPos at = targets.get(i);
      if (reserves(l, at, this) || !Permissions.allowed(l, at, owner, Permissions.Action.FALL))
        return;
    }
    Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
    for (int i = 0; i < targets.size(); i++) {
      if (consumed(i) || pendingLoot != null && pendingIndex == i) continue;
      BlockPos at = targets.get(i);
      previous.put(at, l.getBlockState(at));
      if (!l.setBlock(at, TreeFinder.fallenState(wood.get(i), direction), 18)) {
        previous.forEach((pos, state) -> l.setBlock(pos, state, 18));
        return;
      }
    }
    WorldData data = WorldData.get(l);
    for (BlockPos from : sources) data.naturalLogs.remove(from.asLong());
    for (int i = 0; i < targets.size(); i++) {
      if (consumed(i) || pendingLoot != null && pendingIndex == i) continue;
      BlockPos at = targets.get(i);
      if (placeOnLanding) mark(i);
      data.placedLogs.remove(at.asLong());
      data.placedPlants.remove(at.asLong());
      data.naturalLogs.add(at.asLong());
      l.getBlockState(at).updateNeighbourShapes(l, at, 3);
      l.updateNeighborsAt(at, l.getBlockState(at).getBlock());
      WorldEvents.changedNear(l, at);
    }
    data.setDirty();
    entityData.set(PLACED, true);
    if (placeOnLanding) {
      for (ItemStack loot : crownLoot) Block.popResource(l, blockPosition(), loot.copy());
      crownLoot.clear();
    }
  }

  public boolean claim(Lumberton w) {
    if (placeOnLanding || owner == null || !owner.equals(w.owner) || finished()) return false;
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
    sweepPlacedLogs();
    if (placeOnLanding
        || !hasPlacedBlocks()
        || index < 0
        || index >= wood.size()
        || consumed(index)
        || progress() < duration
        || !safe(s)
        || !w.getUUID().equals(worker)
        || !Inventories.axe(w.activeTool())
        || ForestryHelp.needsPickaxe(wood.get(index))
        || !w.inReach(targets.get(index))) return false;
    if (!Permissions.allowed(
        (ServerLevel) level(), targets.get(index), w.owner, Permissions.Action.PROCESS))
      return false;
    if (pendingLoot == null || pendingIndex != index) {
      if (!ownsLog(index)) return false;
      ServerLevel l = (ServerLevel) level();
      List<ItemStack> harvested = new ArrayList<>(drops(index, w.activeTool(), w));
      if (!l.destroyBlock(targets.get(index), false, w)) return false;
      pendingLoot = harvested;
      pendingLoot.addAll(crownLoot);
      crownLoot.clear();
      pendingIndex = index;
      WorldData.get(l).naturalLogs.remove(targets.get(index).asLong());
      WorldData.get(l).setDirty();
      WorldEvents.changedNear(l, targets.get(index));
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
    // Players target ordinary world logs after landing, never the animation's enclosing box.
    return false;
  }

  private void refreshBounds() {
    AABB bounds = new AABB(blockPosition());
    for (BlockPos p : sources) bounds = bounds.minmax(new AABB(p));
    for (BlockPos p : targets) bounds = bounds.minmax(new AABB(p));
    for (BlockPos p : sweep) bounds = bounds.minmax(new AABB(p));
    timberBounds = bounds.inflate(1);
    setBoundingBox(timberBounds);
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
    n.putBoolean("RigidFall", rigidFall);
    n.putBoolean("PlaceOnLanding", placeOnLanding);
    n.putBoolean("PlacedBlocks", hasPlacedBlocks());
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
    rigidFall = n.getBoolean("RigidFall");
    placeOnLanding = n.getBoolean("PlaceOnLanding");
    entityData.set(PLACED, n.getBoolean("PlacedBlocks"));
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
    entityData.set(LAYOUT_READY, distinctTargets() && (rigidFall || progress() >= duration));
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
    previousVisual = currentVisual = progress();
  }
}
