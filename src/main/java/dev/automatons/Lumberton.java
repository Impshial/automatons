package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

public class Lumberton extends PathfinderMob implements MenuProvider {
  public static final int TOOLS = 1, WOOD = 2, STORAGE = 4, CARGO = 8;
  private static final EntityDataAccessor<Integer>
      FACE = SynchedEntityData.defineId(Lumberton.class, EntityDataSerializers.INT),
      ACTIVE = SynchedEntityData.defineId(Lumberton.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<String> STATUS =
      SynchedEntityData.defineId(Lumberton.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Optional<BlockPos>> CHOP_TARGET =
      SynchedEntityData.defineId(Lumberton.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
  public UUID owner, homeId, totemId, timberId;
  private Timber escapeTimber;
  private UUID looseLogItem;
  private long nextLogSearch, nextLogPath;
  private long nextEscapePath;
  private UUID navigationEscape;
  private int navigationTimberTick = Integer.MIN_VALUE;
  private List<Timber> navigationTimber = List.of();
  private Vec3 routeProgressPosition;
  private int routeProgressTick;
  private long timberBlockedSince = -1;
  private final Map<Long, Long> avoidedNodes = new HashMap<>();
  private final Map<UUID, Long> failedTimber = new HashMap<>();
  private BlockPos crackTarget;

  public BlockPos home, totem;
  public String homeDimension = "", totemDimension = "";
  public boolean authorized = true, packing, deploying;
  private static final EntityDataAccessor<Integer> JOB =
      SynchedEntityData.defineId(Lumberton.class, EntityDataSerializers.INT);
  public int alerts;
  private int smileUntil, angryUntil, workTicks, returnRetry;
  private long nextPath;
  private int nextDebugLog;
  public boolean wantsJob, pathDeferred;
  private boolean servicing, serviceArrived;
  private int serviceRetry, storageCheckEpoch = -1;
  private long storageRouteUntil;
  private boolean storageRouteAvailable;
  private String serviceReason = "Waiting for available work";
  private long blockedSince = -1, landingBlockedSince = -1;
  private final Map<Long, Route> routes = new HashMap<>();

  private record Route(Path path, long until) {}

  private BlockPos pathTarget;
  private BlockPos resumeRoot;
  private TreeFinder.Tree pending;
  private TreeFinder.Landing pendingLanding;
  private BlockPos pendingAction;
  private int nextLanding;
  private final Map<Long, Long> failedTrees = new HashMap<>();
  public final Inventories.Tools tools =
      new Inventories.Tools(
          () -> {
            if (level() != null && !level().isClientSide) {
              selectTool();
              smile();
            }
          },
          () -> job() == 1);
  public final ItemStackHandler cargo =
      new ItemStackHandler(Balance.CARGO.get()) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
          return !Inventories.axe(stack);
        }
      };

  public Lumberton(EntityType<? extends Lumberton> type, Level l) {
    super(type, l);
    setPersistenceRequired();
    setCanPickUpLoot(false);
  }

  @Override
  protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
    return new LumbertonNavigation(this, level);
  }

  public boolean navigationBlocked(BlockPos node) {
    if (avoidedNodes != null
        && avoidedNodes.getOrDefault(node.asLong(), 0L) > level().getGameTime()) return true;
    AABB box = getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(node));
    if (navigationTimberTick != tickCount) {
      navigationTimber = Timber.nearby(level(), getBoundingBox().inflate(64));
      navigationTimberTick = tickCount;
    }
    return navigationTimber.stream()
        .anyMatch(
            t ->
                !t.isRemoved()
                    && !t.finished()
                    && !t.getUUID().equals(navigationEscape)
                    && t.dangerAt(box));
  }

  private void recoverStalledNavigation() {
    avoidedNodes.values().removeIf(until -> until <= level().getGameTime());
    failedTimber.values().removeIf(until -> until <= level().getGameTime());
    if (!navigation.isInProgress()
        || routeProgressPosition == null
        || position().distanceToSqr(routeProgressPosition) >= .0625) {
      if (routeProgressPosition != null && position().distanceToSqr(routeProgressPosition) >= .0625)
        blockedSince = -1;
      routeProgressPosition = position();
      routeProgressTick = tickCount;
      return;
    }
    if (tickCount - routeProgressTick < 40) return;
    Path path = navigation.getPath();
    if (path != null && !path.isDone()) {
      BlockPos blocked = path.getNextNodePos();
      if (!blocked.equals(blockPosition()))
        avoidedNodes.put(blocked.asLong(), level().getGameTime() + 80);
    }
    navigation.stop();
    routes.clear();
    pathTarget = null;
    nextPath = nextLogPath = nextEscapePath = 0;
    if (blockedSince < 0) blockedSince = level().getGameTime();
    routeProgressTick = tickCount;
  }

  private void deferBlockedTimber() {
    if (timberId != null) failedTimber.put(timberId, level().getGameTime() + 200);
    clearChop();
    suspendTimber();
    navigation.stop();
    routes.clear();
    pathTarget = null;
    nextPath = 0;
    blockedSince = timberBlockedSince = -1;
    wantsJob = true;
    state("Seeking other reachable work");
  }

  public static AttributeSupplier.Builder attributes() {
    return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 30)
        .add(Attributes.MOVEMENT_SPEED, .2)
        .add(Attributes.FOLLOW_RANGE, 64)
        .add(Attributes.KNOCKBACK_RESISTANCE, .5);
  }

  @Override
  protected void registerGoals() {
    goalSelector.addGoal(0, new FloatGoal(this));
    goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6));
    goalSelector.addGoal(9, new RandomLookAroundGoal(this));
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    entityData.define(CHOP_TARGET, Optional.empty());
    entityData.define(JOB, 1);
    entityData.define(FACE, 0);
    entityData.define(ACTIVE, 0);
    entityData.define(STATUS, "Unassigned");
  }

  @Override
  protected float getStandingEyeHeight(Pose pose, EntityDimensions dim) {
    return 1.29f;
  }

  public boolean owns(Player p) {
    return owner != null && (owner.equals(p.getUUID()) || p.hasPermissions(2));
  }

  public int job() {
    return entityData.get(JOB);
  }

  public void setJob(int job) {
    entityData.set(JOB, job == 1 ? 1 : 0);
    suspendWork();
    alerts = 0;
  }

  public void suspendWork() {
    clearChop();
    wantsJob = false;
    servicing = false;
    clearPending();
    navigation.stop();
    Timber timber = timber();
    if (timber != null && getUUID().equals(timber.worker)) timber.worker = null;
    timberId = null;
    timberAnchor = null;
  }

  public String status() {
    return entityData.get(STATUS);
  }

  public int face() {
    return entityData.get(FACE);
  }

  public int activeSlot() {
    return entityData.get(ACTIVE);
  }

  public ItemStack activeTool() {
    return tools == null || job() != 1
        ? ItemStack.EMPTY
        : tools.getStackInSlot(Math.min(2, Math.max(0, activeSlot())));
  }

  @Override
  public ItemStack getItemBySlot(EquipmentSlot slot) {
    return slot == EquipmentSlot.MAINHAND ? activeTool() : super.getItemBySlot(slot);
  }

  @Override
  public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    if (slot == EquipmentSlot.MAINHAND && tools != null) {
      if (!stack.isEmpty() && !Inventories.axe(stack)) return;
      tools.setStackInSlot(activeSlot(), stack);
      return;
    }
    super.setItemSlot(slot, stack);
  }

  public void selectTool() {
    if (tools == null) return;
    if (Inventories.axe(activeTool())) return;
    for (int i = 0; i < 3; i++)
      if (Inventories.axe(tools.getStackInSlot(i))) {
        entityData.set(ACTIVE, i);
        return;
      }
  }

  public void smile() {
    smileUntil = tickCount + 50;
  }

  public ItemStack expectedOutput() {
    Timber t = timber();
    if (t != null && t.pendingLoot != null && !t.pendingLoot.isEmpty())
      return t.pendingLoot.get(0).copyWithCount(1);
    if (t != null && t.next() >= 0) return new ItemStack(t.wood.get(t.next()).getBlock());
    if (pending != null) return new ItemStack(level().getBlockState(pending.root()).getBlock());
    Station s = station();
    if (s != null && !s.trees.isEmpty())
      return new ItemStack(level().getBlockState(s.orderedTrees().get(0).root()).getBlock());
    return new ItemStack(Items.OAK_LOG);
  }

  public boolean cargoEmpty() {
    for (int i = 0; i < cargo.getSlots(); i++) if (!cargo.getStackInSlot(i).isEmpty()) return false;
    return true;
  }

  public void wear(int amount) {
    ItemStack tool = activeTool();
    tool.hurtAndBreak(amount, this, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    if (tool.isEmpty()) tools.setStackInSlot(activeSlot(), ItemStack.EMPTY);
    selectTool();
  }

  public Station station() {
    return totem != null
            && (totemDimension.isEmpty()
                || totemDimension.equals(level().dimension().location().toString()))
            && level().hasChunkAt(totem)
            && level().getBlockEntity(totem) instanceof Station s
            && s.isTotem()
            && s.identity.equals(totemId)
            && (s.workers.contains(getUUID()) || getUUID().equals(s.worker))
        ? s
        : null;
  }

  public Timber timber() {
    return timberId != null
            && level() instanceof ServerLevel l
            && l.getEntity(timberId) instanceof Timber t
        ? t
        : null;
  }

  public void unassign() {
    Station old = station();
    if (old != null) {
      old.workers.remove(getUUID());
      if (getUUID().equals(old.worker)) {
        old.worker = null;
        old.workerHome = null;
      }
      old.changed();
    }
    totem = null;
    totemId = null;
    totemDimension = "";
    suspendWork();
    servicing = false;
    wantsJob = false;
    alerts = 0;
    if (level() instanceof ServerLevel l) {
      WorldData.get(l).release(getUUID());
      WorkerIdentity.update(this, "Deployed");
    }
  }

  public String assign(Station target) {
    if (!target.isTotem() || owner == null || !owner.equals(target.owner))
      return "Choose your own totem";
    unassign();
    totem = target.getBlockPos();
    totemId = target.identity;
    totemDimension = level().dimension().location().toString();
    target.workers.add(getUUID());
    authorized = true;
    WorkerIdentity.update(this, "Deployed");
    target.invalidateTrees();
    target.refreshStorage();
    target.changed();
    smile();
    return "Lumberton assigned";
  }

  public String rebind(Station s) {
    if (s.isTotem() || owner == null || !owner.equals(s.owner)) return "Choose your own pedestal";
    WorldData.Home existing =
        homeId == null || !homeDimension.equals(level().dimension().location().toString())
            ? null
            : WorldData.get((ServerLevel) level()).homes.get(homeId);
    if (existing != null
        && existing.exists()
        && level().hasChunkAt(existing.pos())
        && (!(level().getBlockEntity(existing.pos()) instanceof Station old)
            || !old.identity.equals(homeId))) existing = null;
    if (existing != null && existing.exists()) return "Existing home is still present";
    home = s.getBlockPos();
    homeId = s.identity;
    homeDimension = level().dimension().location().toString();
    s.workers.add(getUUID());
    s.registerHome();
    s.changed();
    Station job = station();
    if (job != null) job.changed();
    WorkerIdentity.update(this, "Deployed");
    return "Home rebound";
  }

  @Override
  public SpawnGroupData finalizeSpawn(
      ServerLevelAccessor l,
      net.minecraft.world.DifficultyInstance d,
      MobSpawnType reason,
      SpawnGroupData data,
      CompoundTag n) {
    return super.finalizeSpawn(l, d, reason, data, n);
  }

  private void debugWork(String message) {
    if (!Balance.DEBUG_WORK.get() || tickCount < nextDebugLog) return;
    nextDebugLog = tickCount + 20;
    org.slf4j.LoggerFactory.getLogger("automatons.work")
        .info("{} at {}: {} (flags {})", getUUID(), blockPosition(), message, alerts);
  }

  private void state(String text) {
    if (!text.equals("Chopping")
        && !text.equals("Clearing leaves")
        && !text.equals("Processing timber")) clearChop();
    if (!text.equals(status())) debugWork(status() + " -> " + text);
    entityData.set(STATUS, text);
    int face = 0;
    if (tickCount < angryUntil) face = 5;
    else if (alerts != 0 || text.contains("missing") || text.contains("blocked")) face = 4;
    else if (tickCount < smileUntil) face = 1;
    else if (text.equals("Chopping")
        || text.equals("Clearing leaves")
        || text.equals("Processing timber")) face = 3;
    else if (text.equals("Seeking tree")) face = 2;
    entityData.set(FACE, face);
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    boolean hit = super.hurt(source, amount);
    if (hit && !level().isClientSide) angryUntil = tickCount + 35;
    return hit;
  }

  @Override
  public void tick() {
    super.tick();
    if (!level().isClientSide && chopTarget() != null) aimAt(chopTarget());
    if (!level().isClientSide && isAlive() && !packing && !isNoAi() && tickCount % 5 == 0) {
      getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Balance.WALK.get());
      if (tickCount % 20 == 0) recoverStalledNavigation();
      brain();
      if (tickCount % 20 == 0) {
        if (job() == 1 && authorized) pickup();
        WorkerIdentity.update(this, "Deployed");
      }
    }
  }

  private void pickup() {
    for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1))) {
      if (e.hasPickUpDelay() || e.getOwner() != null && !e.getOwner().getUUID().equals(owner))
        continue;
      CompoundTag pickup = e.saveWithoutId(new CompoundTag());
      if (pickup.hasUUID("Owner") && !pickup.getUUID("Owner").equals(owner)) continue;
      ItemStack s = e.getItem(), rest = s.copy();
      if (Inventories.axe(s)) {
        for (int i = 0; i < 3 && !rest.isEmpty(); i++) rest = tools.insertItem(i, rest, false);
      } else if (s.is(net.minecraft.tags.ItemTags.LOGS) && looseLogAllowed(e, station())
          || s.is(net.minecraft.tags.ItemTags.SAPLINGS)
          || s.is(Items.STICK)
          || s.is(Items.APPLE)) {
        rest = Inventories.insert(cargo, rest, false);
      } else continue;
      if (rest.isEmpty()) e.discard();
      else e.setItem(rest);
      selectTool();
    }
  }

  private boolean looseLogAllowed(ItemEntity item, Station station) {
    if (station == null
        || item.isRemoved()
        || item.hasPickUpDelay()
        || !item.onGround()
        || !item.getItem().is(net.minecraft.tags.ItemTags.LOGS)
        || !TreeFinder.within(station, item.blockPosition())
        || Math.abs(item.getY() - station.getBlockPos().getY()) > Balance.ROOT_HEIGHT.get())
      return false;
    CompoundTag saved = item.saveWithoutId(new CompoundTag());
    return !saved.hasUUID("Owner") || saved.getUUID("Owner").equals(owner);
  }

  private boolean collectLooseLogs(Station station) {
    ServerLevel l = (ServerLevel) level();
    ItemEntity target =
        looseLogItem != null && l.getEntity(looseLogItem) instanceof ItemEntity item ? item : null;
    if (target != null && !looseLogAllowed(target, station)) {
      looseLogItem = null;
      target = null;
    }
    if (target == null && tickCount >= nextLogSearch) {
      nextLogSearch = tickCount + 40;
      var candidates =
          l.getEntitiesOfClass(
              ItemEntity.class,
              new AABB(station.getBlockPos())
                  .inflate(station.lumberRange(), Balance.ROOT_HEIGHT.get(), station.lumberRange()),
              e ->
                  looseLogAllowed(e, station)
                      && hasLineOfSight(e)
                      && Inventories.insert(cargo, e.getItem(), true).getCount()
                          < e.getItem().getCount());
      candidates.sort(Comparator.comparingDouble(this::distanceToSqr));
      for (ItemEntity candidate : candidates.stream().limit(24).toList()) {
        if (!WorldEvents.pathBudget()) break;
        Path path = navigation.createPath(candidate.blockPosition(), 0);
        if (path != null && path.canReach()) {
          target = candidate;
          looseLogItem = candidate.getUUID();
          navigation.moveTo(path, 1);
          nextLogPath = tickCount + 20;
          break;
        }
      }
    }
    if (target == null) return false;
    if (distanceToSqr(target) < 2) {
      pickup();
      if (target.isRemoved()
          || Inventories.insert(cargo, target.getItem(), true).getCount()
              == target.getItem().getCount()) {
        looseLogItem = null;
        navigation.stop();
        return false;
      }
    }
    if (tickCount >= nextLogPath || navigation.isDone()) {
      if (WorldEvents.pathBudget()) {
        Path path = navigation.createPath(target.blockPosition(), 0);
        if (path == null || !path.canReach()) {
          looseLogItem = null;
          nextLogSearch = tickCount + 40;
          return false;
        }
        navigation.moveTo(path, 1);
        nextLogPath = tickCount + 20;
      }
    }
    state("Collecting logs");
    return true;
  }

  public boolean inReach(BlockPos p) {
    if (distanceToSqr(Vec3.atCenterOf(p)) > 7) return false;
    var hit =
        level()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    getEyePosition(),
                    Vec3.atCenterOf(p),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p);
  }

  private boolean reachesFrom(Vec3 feet, BlockPos p) {
    if (feet.distanceToSqr(Vec3.atCenterOf(p)) > 4) return false;
    var hit =
        level()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    feet.add(0, getEyeHeight(), 0),
                    Vec3.atCenterOf(p),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    this));
    return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p);
  }

  private Path pathTo(BlockPos p) {
    pathDeferred = false;
    if (!onGround() && !isInWaterOrBubble() && !isPassenger()) {
      pathDeferred = true;
      return null;
    }
    Route cached = routes.get(p.asLong());
    if (cached != null && cached.until() > level().getGameTime()) return cached.path();
    List<BlockPos> candidates = new ArrayList<>();
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = -2; dz <= 2; dz++)
        for (int dy = -1; dy <= 1; dy++) {
          if (dx == 0 && dz == 0) continue;
          BlockPos q = p.offset(dx, dy, dz);
          Vec3 feet = Vec3.atBottomCenterOf(q);
          AABB box = getDimensions(Pose.STANDING).makeBoundingBox(feet);
          if (!level().hasChunkAt(q) || !level().hasChunkAt(q.above()) || !reachesFrom(feet, p))
            continue;
          if (level().getBlockState(q.below()).isFaceSturdy(level(), q.below(), Direction.UP)
              && level().noCollision(this, box)
              && !Timber.blocksWorker(level(), box)) candidates.add(q);
        }
    candidates.sort(
        Comparator.comparingDouble((BlockPos q) -> distanceToSqr(Vec3.atBottomCenterOf(q)))
            .thenComparingLong(BlockPos::asLong));
    if (!candidates.isEmpty()) {
      if (!WorldEvents.pathBudget()) {
        pathDeferred = true;
        return null;
      }
      // Search all usable sides together; retrying the first side alone can starve alternatives.
      Path path = navigation.createPath(new LinkedHashSet<>(candidates), 0);
      if (path != null && path.canReach()) {
        routes.put(p.asLong(), new Route(path, level().getGameTime() + 20));
        return path;
      }
    }
    routes.put(p.asLong(), new Route(null, level().getGameTime() + 40));
    return null;
  }

  public boolean canReach(BlockPos p) {
    pathDeferred = false;
    return inReach(p) || pathTo(p) != null;
  }

  private boolean travel(BlockPos p, String moving, String arrived) {
    if (inReach(p)) {
      navigation.stop();
      blockedSince = -1;
      state(arrived);
      return true;
    }
    if (tickCount >= nextPath
        || pathTarget == null
        || !pathTarget.equals(p)
        || navigation.isDone()) {
      if (tickCount < nextPath && p.equals(pathTarget) && navigation.isDone()) {
        state("Waiting for path");
        return false;
      }
      Path path = pathTo(p);
      pathTarget = p;
      nextPath = tickCount + (pathDeferred ? 5 : 40);
      if (path == null) {
        navigation.stop();
        if (!pathDeferred && blockedSince < 0) blockedSince = level().getGameTime();
        state(pathDeferred ? "Replanning" : "Waiting for path");
        return false;
      }
      navigation.moveTo(path, 1);
    }
    state(moving);
    return false;
  }

  private void goHome(String arrival) {
    if (home != null && level().hasChunkAt(home)) travel(home, "Returning home", arrival);
    else {
      navigation.stop();
      state(home == null ? "Home missing" : "Dependency unloaded");
    }
  }

  private void clearPending() {
    clearChop();
    landingBlockedSince = -1;
    resumeRoot = null;
    pendingLanding = null;
    pendingAction = null;
    nextLanding = 0;
    pending = null;
    workTicks = 0;
    if (level() instanceof ServerLevel l) WorldData.get(l).release(getUUID());
  }

  private void beginService(String reason) {
    wantsJob = false;
    serviceReason = reason;
    if (!servicing) {
      servicing = true;
      serviceArrived = false;
      serviceRetry = 0;
    }
  }

  private boolean spareTool() {
    for (int i = 0; i < 3; i++) if (tools.getStackInSlot(i).isEmpty()) return true;
    return false;
  }

  /** A service visit empties every accepted stack before returning to the existing job. */
  private void service(Station s) {
    if (!serviceArrived) {
      if (!travel(totem, "Returning totem", serviceReason)) return;
      serviceArrived = true;
    }
    if (tickCount < serviceRetry) {
      navigation.stop();
      state(serviceReason);
      return;
    }
    BlockPos target = !cargoEmpty() ? Storage.choose(this, s, false) : null;
    if (target == null && pathDeferred) {
      state("Replanning");
      return;
    }
    if (target == null && spareTool()) target = Storage.choose(this, s, true);
    if (target == null && pathDeferred) {
      state("Replanning");
      return;
    }
    if (target != null) {
      String activity = cargoEmpty() ? "Restocking" : "Unloading";
      if (travel(target, activity, activity)) Storage.transfer(this, s, target);
      return;
    }
    // No accepted cargo remains at a reachable chest. Wait in place, not a repeated chest/totem
    // loop.
    servicing = false;
    serviceArrived = false;
    serviceRetry = tickCount + 40;
    state(serviceReason);
  }

  private List<ItemStack> nextOutput() {
    Timber t = timber();
    return t != null && t.pendingLoot != null && !t.pendingLoot.isEmpty()
        ? List.of(t.pendingLoot.get(0).copyWithCount(1))
        : List.of(expectedOutput());
  }

  private void resourceAlerts(Station s) {
    alerts = Inventories.axe(activeTool()) ? alerts & ~TOOLS : alerts | TOOLS;
    alerts = Inventories.fits(cargo, nextOutput()) ? alerts & ~CARGO : alerts | CARGO;
    boolean work =
        pending != null || timber() != null || !s.trees.isEmpty() || !s.timberJobs().isEmpty();
    if (work) alerts &= ~WOOD;
    else if (s.scanComplete && !s.scanUnloaded) alerts |= WOOD;
    else alerts &= ~WOOD;
  }

  private String storageCondition(Station s) {
    ServerLevel l = (ServerLevel) level();
    boolean exists = Storage.anyOutput(s, l),
        open = Storage.anyOpenOutput(s, l),
        space = Storage.anySpace(this, s);
    if (exists && open && space) {
      alerts &= ~STORAGE;
      storageCheckEpoch = -1;
      if (level().getGameTime() >= storageRouteUntil) {
        storageRouteAvailable = false;
        for (BlockPos p : s.chests) {
          if ((s.flags(p) & 1) == 0) continue;
          var handler = Storage.handler(l, p, s);
          if (handler != null && Storage.accepts(handler, expectedOutput()) && canReach(p)) {
            storageRouteAvailable = true;
            break;
          }
          if (pathDeferred) return "Searching storage";
        }
        storageRouteUntil = level().getGameTime() + 40;
      }
      return storageRouteAvailable ? "Ready" : "Storage blocked";
    }
    if (storageCheckEpoch < 0) {
      storageCheckEpoch = s.storageEpoch;
      s.refreshStorage();
      routes.clear();
    }
    if (s.storageEpoch <= storageCheckEpoch) {
      alerts &= ~STORAGE;
      return "Searching storage";
    }
    if (s.storageUnloaded) {
      alerts &= ~STORAGE;
      return "Dependency unloaded";
    }
    if (!exists) {
      alerts &= ~STORAGE;
      return "No storage";
    }
    if (open && !space) {
      alerts |= STORAGE;
      return "Storage full";
    }
    alerts &= ~STORAGE;
    return "Storage blocked";
  }

  private boolean storageReady(Station s) {
    String condition = storageCondition(s);
    if (condition.equals("Ready")) return true;
    if (condition.equals("No storage")) {
      clearPending();
      suspendTimber();
      servicing = false;
      goHome(condition);
      return false;
    }
    if (condition.equals("Storage full") || condition.equals("Storage blocked")) {
      if (tickCount >= serviceRetry) beginService(condition);
      if (servicing) service(s);
      else {
        navigation.stop();
        state(condition);
      }
    } else {
      navigation.stop();
      state(condition);
    }
    return false;
  }

  private void suspendTimber() {
    Timber t = timber();
    if (t != null && getUUID().equals(t.worker)) t.worker = null;
    timberId = null;
    timberAnchor = null;
  }

  private BlockPos approach(TreeFinder.Tree tree) {
    if (canReach(tree.root())) return tree.root();
    if (pathDeferred) return null;
    // Mangroves carry their trunk above a natural root arch. A grounded root is an
    // actionable base cut; the tree's ordering/range anchor stays at the trunk footprint.
    List<BlockPos> roots =
        tree.leaves().stream()
            .filter(
                p ->
                    level()
                            .getBlockState(p)
                            .is(net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS)
                        || level()
                            .getBlockState(p)
                            .is(net.minecraft.world.level.block.Blocks.MUDDY_MANGROVE_ROOTS))
            .sorted(
                Comparator.comparingDouble((BlockPos p) -> distanceToSqr(Vec3.atCenterOf(p)))
                    .thenComparingLong(BlockPos::asLong))
            .limit(16)
            .toList();
    for (BlockPos p : roots) {
      if (canReach(p)) return p;
      if (pathDeferred) return null;
    }
    for (BlockPos leaf :
        BlockPos.betweenClosedStream(tree.root().offset(-3, -1, -3), tree.root().offset(3, 2, 3))
            .map(BlockPos::immutable)
            .filter(p -> accessLeaf(tree, p))
            .sorted(Comparator.comparingDouble(p -> distanceToSqr(Vec3.atCenterOf(p))))
            .limit(48)
            .toList()) {
      if (!Permissions.allowed((ServerLevel) level(), leaf, owner, Permissions.Action.CUT))
        continue;
      if (canReach(leaf)) return leaf;
      if (pathDeferred) return null;
    }
    return null;
  }

  private boolean accessLeaf(TreeFinder.Tree tree, BlockPos p) {
    var state = level().getBlockState(p);
    return p.getY() >= tree.root().getY() - 1
        && p.getY() <= tree.root().getY() + 2
        && Math.abs(p.getX() - tree.root().getX()) <= 3
        && Math.abs(p.getZ() - tree.root().getZ()) <= 3
        && state.is(net.minecraft.tags.BlockTags.LEAVES);
  }

  public void acceptNext(Station s) {
    if (!wantsJob || station() != s || pending != null || timber() != null) return;
    ServerLevel l = (ServerLevel) level();
    WorldData data = WorldData.get(l);
    boolean claimed = false, blocked = false;
    for (Timber t : s.timberJobs()) {
      if (failedTimber.getOrDefault(t.getUUID(), 0L) > l.getGameTime()) {
        blocked = true;
        continue;
      }
      if (t.worker != null && !getUUID().equals(t.worker)) {
        claimed = true;
        continue;
      }
      if (!t.safe(s)) {
        blocked = true;
        continue;
      }
      BlockPos at = t.targets.get(t.next());
      if (!canReach(at)) {
        if (pathDeferred) return;
        blocked = true;
        continue;
      }
      if (t.claim(this)) {
        timberId = t.getUUID();
        timberAnchor = t.blockPosition();
        wantsJob = false;
        alerts &= ~WOOD;
        return;
      }
    }
    for (TreeFinder.Tree tree : s.orderedTrees()) {
      if (!s.discoveredBefore(tree)) {
        state("Seeking tree");
        if (!cargoEmpty() && tickCount >= serviceRetry) beginService("Unloading while searching");
        return;
      }
      if (tree.logs().stream()
          .anyMatch(
              p ->
                  data.reservations.containsKey(p.asLong())
                      && !getUUID().equals(data.reservations.get(p.asLong())))) {
        claimed = true;
        continue;
      }
      if (failedTrees.getOrDefault(tree.root().asLong(), 0L) > l.getGameTime()) {
        blocked = true;
        continue;
      }
      if (!WorldEvents.budget(64)) return;
      if (!TreeFinder.revalidate(l, tree, s, owner)) {
        s.trees.remove(tree.root().asLong());
        continue;
      }
      BlockPos action = approach(tree);
      if (action == null) {
        if (pathDeferred) return;
        debugWork("No reachable cut for " + tree.root() + "; retry in 60 ticks");
        failedTrees.put(tree.root().asLong(), l.getGameTime() + 60);
        blocked = true;
        continue;
      }
      if (data.reserve(tree.logs(), getUUID())) {
        pending = tree;
        pendingAction = action;
        pendingLanding = null;
        workTicks = 0;
        wantsJob = false;
        alerts &= ~WOOD;
        return;
      }
      claimed = true;
    }
    if (claimed) {
      alerts &= ~WOOD;
      state("Waiting for available work");
    } else if (blocked) {
      alerts &= ~WOOD;
      state("Waiting for reachable work");
    } else if (s.scanComplete && !s.scanUnloaded) {
      alerts |= WOOD;
      state("No eligible wood");
    } else {
      alerts &= ~WOOD;
      state("Seeking tree");
      if (!cargoEmpty() && tickCount >= serviceRetry) beginService("Unloading while searching");
      return;
    }
    if (tickCount >= serviceRetry) beginService(status());
  }

  public BlockPos chopTarget() {
    return entityData.get(CHOP_TARGET).orElse(null);
  }

  public void clearChop() {
    if (level().isClientSide) return;
    if (crackTarget != null) ((ServerLevel) level()).destroyBlockProgress(getId(), crackTarget, -1);
    crackTarget = null;
    entityData.set(CHOP_TARGET, Optional.empty());
    Timber t = timber();
    if (t != null && getUUID().equals(t.worker)) t.showProcessing(-1, 0, 1);
  }

  private void aimAt(BlockPos pos) {
    Vec3 delta = Vec3.atCenterOf(pos).subtract(getEyePosition());
    float yaw = (float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
    float pitch =
        (float)
            (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))
                * 180
                / Math.PI);
    setYRot(yaw);
    setYHeadRot(yaw);
    yBodyRot = yaw;
    setXRot(pitch);
    lookControl.setLookAt(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 360, 360);
  }

  private void workVisual(BlockPos pos, int progress, int duration) {
    if (!pos.equals(chopTarget())) clearChop();
    entityData.set(CHOP_TARGET, Optional.of(pos.immutable()));
    aimAt(pos);
    if (!level().getBlockState(pos).isAir()) {
      crackTarget = pos.immutable();
      ((ServerLevel) level())
          .destroyBlockProgress(getId(), pos, Math.min(9, progress * 10 / Math.max(1, duration)));
    }
    Timber t = timber();
    if (t != null) t.showProcessing(t.next(), progress, duration);
  }

  public void moveClearOf(Timber timber) {
    escapeTimber = timber;
  }

  private boolean escapeFallingTimber() {
    if (escapeTimber == null) return false;
    if (escapeTimber.isRemoved() || !escapeTimber.dangerAt(getBoundingBox())) {
      escapeTimber = null;
      navigation.stop();
      return false;
    }
    clearChop();
    state("Moving clear of timber");
    if (tickCount < nextEscapePath && !navigation.isDone()) return true;
    nextEscapePath = tickCount + 20;
    List<BlockPos> exits = new ArrayList<>();
    for (int dx = -4; dx <= 4; dx++)
      for (int dz = -4; dz <= 4; dz++)
        for (int dy = -2; dy <= 2; dy++) {
          BlockPos q = blockPosition().offset(dx, dy, dz);
          Vec3 feet = Vec3.atBottomCenterOf(q);
          AABB box = getDimensions(Pose.STANDING).makeBoundingBox(feet);
          if (level().hasChunkAt(q)
              && level().getBlockState(q.below()).isFaceSturdy(level(), q.below(), Direction.UP)
              && level().noCollision(this, box)
              && !escapeTimber.dangerAt(box.inflate(.6, 0, .6))
              && !Timber.blocksWorker(level(), box)) exits.add(q);
        }
    exits.sort(Comparator.comparingDouble(q -> distanceToSqr(Vec3.atBottomCenterOf(q))));
    navigationEscape = escapeTimber.getUUID();
    try {
      if (!exits.isEmpty() && WorldEvents.pathBudget()) {
        Path path = navigation.createPath(new LinkedHashSet<>(exits), 0);
        if (path != null && path.canReach()) navigation.moveTo(path, 1);
      }
    } finally {
      navigationEscape = null;
    }
    return true;
  }

  private void impact(BlockPos pos, int progress, int duration) {
    workVisual(pos, progress, duration);
    int cadence = Math.max(5, Math.min(20, duration / 4));
    if (progress / cadence == (progress - 5) / cadence && progress != 5) return;
    swing(InteractionHand.MAIN_HAND);
    var sound = level().getBlockState(pos).getSoundType(level(), pos, this);
    if (level().getBlockState(pos).isAir()) sound = net.minecraft.world.level.block.SoundType.WOOD;
    level()
        .playSound(
            null,
            pos,
            sound.getHitSound(),
            net.minecraft.sounds.SoundSource.NEUTRAL,
            .55f,
            .95f + random.nextFloat() * .1f);
  }

  private void brain() {
    ServerLevel l = (ServerLevel) level();
    wantsJob = false;
    if (escapeFallingTimber()) return;
    selectTool();
    if (owner == null) {
      alerts = 0;
      state("Unassigned test worker — use a wand to claim");
      return;
    }
    if (!homeDimension.isEmpty() && !homeDimension.equals(l.dimension().location().toString())) {
      suspendWork();
      state("Home in another dimension — rebind locally");
      return;
    }
    WorldData data = WorldData.get(l);
    WorldData.Home h = homeId == null ? null : data.homes.get(homeId);
    if (homeId == null || home == null || h != null && !h.exists()) {
      suspendWork();
      state("Home missing");
      return;
    }
    if (authorized && job() == 1 && station() != null) {
      resourceAlerts(station());
      storageCondition(station());
    }
    if (h != null && h.recalled()) {
      clearPending();
      suspendTimber();
      servicing = false;
      goHome("Recalled");
      return;
    }
    if (!l.hasChunkAt(home)) {
      navigation.stop();
      state("Home unverified — chunk unloaded");
      return;
    }
    if (!(l.getBlockEntity(home) instanceof Station hs) || !hs.identity.equals(homeId)) {
      data.setHome(homeId, owner, home, false, false);
      suspendWork();
      state("Home missing");
      return;
    }
    hs.registerHome();
    if (!authorized) {
      alerts = 0;
      navigation.stop();
      state("Paused at placement — Start Work");
      return;
    }
    if (job() != 1) {
      alerts = 0;
      navigation.stop();
      state("Unassigned — choose a job");
      return;
    }
    if (!totemDimension.isEmpty() && !totemDimension.equals(l.dimension().location().toString())) {
      suspendWork();
      state("Totem in another dimension — reassign");
      return;
    }
    if (totem == null) {
      goHome("Idle at home");
      return;
    }
    if (!l.hasChunkAt(totem)) {
      navigation.stop();
      state("Totem unverified — chunk unloaded");
      return;
    }
    Station s = station();
    if (s == null) {
      unassign();
      goHome("Totem missing");
      return;
    }
    resourceAlerts(s);
    if (servicing) {
      service(s);
      return;
    }
    if (!storageReady(s)) return;
    if ((alerts & (TOOLS | CARGO)) != 0) {
      clearPending();
      if (tickCount >= serviceRetry)
        beginService((alerts & TOOLS) != 0 ? "Needs tools" : "Cargo full");
      if (servicing) service(s);
      else {
        navigation.stop();
        state((alerts & TOOLS) != 0 ? "Needs tools" : "Cargo full");
      }
      return;
    }
    if (chopTarget() == null && collectLooseLogs(s)) return;
    Timber timber = timber();
    if (timberId != null && timber == null) {
      if (timberAnchor != null && !l.hasChunkAt(timberAnchor)) {
        state("Timber unverified — chunk unloaded");
        return;
      }
      timberId = null;
      timberAnchor = null;
    }
    if (timber != null) {
      if (timber.finished() || !timber.claim(this)) {
        timberId = null;
        timberAnchor = null;
        return;
      }
      if (!timber.safe(s)) {
        clearChop();
        if (timberBlockedSince < 0) timberBlockedSince = l.getGameTime();
        if (l.getGameTime() - timberBlockedSince >= 60) deferBlockedTimber();
        else state("Waiting for timber clearance");
        return;
      }
      timberBlockedSince = -1;
      if (timber.progress() < timber.duration) {
        navigation.stop();
        state("Felling");
        return;
      }
      int next = timber.next();
      BlockPos target = timber.targets.get(next);
      if (!travel(target, "Travelling", "Processing timber")) {
        if (blockedSince >= 0 && l.getGameTime() - blockedSince > 100) deferBlockedTimber();
        return;
      }
      lookControl.setLookAt(Vec3.atCenterOf(target));
      int duration =
          Inventories.duration(
              activeTool(), Balance.PROCESS.get(), s.tier("speed"), s.tier("efficiency"));
      if (timber.processingTicks < duration) {
        timber.processingTicks += 5;
        impact(target, timber.processingTicks, duration);
      }
      if (timber.processingTicks >= duration) timber.process(next, this, s);
      return;
    }
    if (pending == null && resumeRoot != null) {
      if (!l.hasChunkAt(resumeRoot)) {
        state("Dependency unloaded");
        return;
      }
      if (!WorldEvents.budget(128)) return;
      var restored = TreeFinder.work(l, resumeRoot, s).tree();
      resumeRoot = null;
      if (restored != null && data.reserve(restored.logs(), getUUID())) pending = restored;
      else workTicks = 0;
    }
    if (pending == null) {
      wantsJob = true;
      state((alerts & WOOD) != 0 ? "No eligible wood" : "Seeking tree");
      return;
    }
    if (!WorldEvents.budget(64)) return;
    if (!TreeFinder.revalidate(l, pending, s, owner)) {
      s.trees.remove(pending.root().asLong());
      clearPending();
      return;
    }
    if (pendingAction == null) pendingAction = approach(pending);
    if (pendingAction == null) {
      state(pathDeferred ? "Replanning" : "Waiting for reachable work");
      if (!pathDeferred) {
        failedTrees.put(pending.root().asLong(), l.getGameTime() + 80);
        clearPending();
        beginService("Waiting for reachable work");
      }
      return;
    }
    if (!pending.logs().contains(pendingAction)
        && !level()
            .getBlockState(pendingAction)
            .is(net.minecraft.world.level.block.Blocks.MANGROVE_ROOTS)
        && !level()
            .getBlockState(pendingAction)
            .is(net.minecraft.world.level.block.Blocks.MUDDY_MANGROVE_ROOTS)) {
      if (!accessLeaf(pending, pendingAction)) {
        pendingAction = null;
        workTicks = 0;
        clearChop();
        return;
      }
      if (!travel(pendingAction, "Travelling", "Clearing leaves")) return;
      if (!Permissions.allowed(l, pendingAction, owner, Permissions.Action.CUT)) {
        clearPending();
        return;
      }
      workTicks += 5;
      impact(pendingAction, workTicks, 20);
      if (workTicks >= 20) {
        var leaf = l.getBlockState(pendingAction);
        for (ItemStack drop :
            net.minecraft.world.level.block.Block.getDrops(
                leaf, l, pendingAction, null, this, activeTool())) {
          ItemStack rest = Inventories.insert(cargo, drop, false);
          if (!rest.isEmpty())
            net.minecraft.world.level.block.Block.popResource(l, pendingAction, rest);
        }
        l.setBlock(pendingAction, l.getFluidState(pendingAction).createLegacyBlock(), 3);
        wear(1);
        clearChop();
        pendingAction = null;
        workTicks = 0;
        routes.clear();
      }
      return;
    }
    if (!travel(pendingAction, "Travelling", "Chopping")) {
      if (blockedSince >= 0 && l.getGameTime() - blockedSince > 160) {
        failedTrees.put(pending.root().asLong(), l.getGameTime() + 80);
        clearPending();
        beginService("Waiting for reachable work");
      }
      return;
    }
    if (TreeFinder.loose(pending)) {
      int duration =
          Inventories.duration(
              activeTool(), Balance.PROCESS.get(), s.tier("speed"), s.tier("efficiency"));
      workTicks += 5;
      impact(pendingAction, workTicks, duration);
      if (workTicks >= duration) {
        var state = l.getBlockState(pendingAction);
        var drops =
            net.minecraft.world.level.block.Block.getDrops(
                state, l, pendingAction, l.getBlockEntity(pendingAction), this, activeTool());
        if (!Inventories.fits(cargo, drops)) {
          beginService("Cargo full");
          return;
        }
        if (l.destroyBlock(pendingAction, false, this)) {
          for (ItemStack drop : drops) Inventories.insert(cargo, drop, false);
          data.placedLogs.remove(pendingAction.asLong());
          data.naturalLogs.remove(pendingAction.asLong());
          data.setDirty();
          wear(1);
          s.trees.remove(pending.root().asLong());
          clearPending();
        }
      }
      return;
    }
    if (pendingLanding == null) {
      if (tickCount < nextLanding) {
        state("Waiting for safe landing");
        return;
      }
      if (!WorldEvents.budget(256) || !WorldEvents.planBudget()) return;
      nextLanding = tickCount + 20;
      pendingLanding = TreeFinder.landing(l, pending, s, owner, this);
    }
    if (pendingLanding == null) {
      if (landingBlockedSince < 0) landingBlockedSince = l.getGameTime();
      state("Waiting for safe landing");
      if (l.getGameTime() - landingBlockedSince > 100) {
        failedTrees.put(pending.root().asLong(), l.getGameTime() + 100);
        clearPending();
        beginService("No safe landing space");
      }
      return;
    }
    if (!TreeFinder.landingStillSafe(l, pending, pendingLanding, s, owner, this)) {
      pendingLanding = null;
      state("Replanning fall");
      return;
    }
    lookControl.setLookAt(Vec3.atCenterOf(pendingAction));
    int duration =
        Inventories.duration(
                activeTool(), Balance.PREP.get(), s.tier("speed"), s.tier("efficiency"))
            * pending.baseCount();
    if (workTicks < duration) {
      workTicks += 5;
      impact(pendingAction, workTicks, duration);
    }
    if (workTicks >= duration && WorldEvents.conversionBudget()) {
      Timber t = Timber.commit(l, pending, pendingLanding, this, s);
      if (t != null) {
        timberId = t.getUUID();
        timberAnchor = t.blockPosition();
        wear(pending.baseCount());
        s.trees.remove(pending.root().asLong());
        level()
            .playSound(
                null,
                pending.root(),
                net.minecraft.sounds.SoundEvents.WOOD_BREAK,
                net.minecraft.sounds.SoundSource.NEUTRAL,
                .7f,
                1);
        clearPending();
        state("Felling");
      } else {
        pendingLanding = null;
        state("Replanning fall");
      }
    }
  }

  public String diagnostic() {
    Timber t = timber();
    return status()
        + " pending="
        + (pending == null ? "none" : pending.root())
        + " position="
        + position()
        + " pathTarget="
        + pathTarget
        + " nextPath="
        + nextPath
        + " ticks="
        + tickCount
        + " done="
        + navigation.isDone()
        + " path="
        + (navigation.getPath() == null
            ? "none"
            : navigation.getPath().getNextNodeIndex() + "/" + navigation.getPath().getNodeCount())
        + " timber="
        + (t == null ? "none" : t.next() + ":" + t.targets + " progress=" + t.processingTicks)
        + " servicing="
        + servicing;
  }

  public BlockPos timberAnchor;

  @Override
  public InteractionResult mobInteract(Player p, InteractionHand hand) {
    if (p.getItemInHand(hand).getItem() instanceof WandItem) return InteractionResult.PASS;
    if (!level().isClientSide)
      NetworkHooks.openScreen(
          (ServerPlayer) p,
          this,
          b -> {
            b.writeBoolean(true);
            b.writeInt(getId());
            b.writeVarInt(cargo.getSlots());
          });
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new WorkMenu(id, inv, null, this);
  }

  @Override
  public Component getDisplayName() {
    return hasCustomName() ? getCustomName() : Component.literal("Automaton");
  }

  @Override
  protected void dropEquipment() {
    /* Internal tools and cargo are dropped together below. */
  }

  @Override
  protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    clearChop();
    super.dropCustomDeathLoot(source, looting, recentlyHit);
    for (int i = 0; i < 3; i++) {
      spawnAtLocation(tools.getStackInSlot(i));
      tools.setStackInSlot(i, ItemStack.EMPTY);
    }
    for (int i = 0; i < cargo.getSlots(); i++) {
      spawnAtLocation(cargo.getStackInSlot(i));
      cargo.setStackInSlot(i, ItemStack.EMPTY);
    }
    unassign();
    WorkerIdentity.update(this, "Destroyed");
    if (home != null
        && level().hasChunkAt(home)
        && level().getBlockEntity(home) instanceof Station station
        && station.identity.equals(homeId)) {
      station.workers.remove(getUUID());
      station.changed();
    }
  }

  @Override
  public void addAdditionalSaveData(CompoundTag n) {
    super.addAdditionalSaveData(n);
    if (owner != null) n.putUUID("Owner", owner);
    if (homeId != null) n.putUUID("HomeId", homeId);
    if (totemId != null) n.putUUID("TotemId", totemId);
    if (home != null) n.putLong("Home", home.asLong());
    if (totem != null) n.putLong("Totem", totem.asLong());
    if (timberId != null) n.putUUID("Timber", timberId);
    if (timberAnchor != null) n.putLong("TimberAnchor", timberAnchor.asLong());
    n.putString("HomeDimension", homeDimension);
    n.putString("TotemDimension", totemDimension);
    n.putInt("Job", job());
    n.putBoolean("Authorized", authorized);
    n.putInt("DataVersion", 2);
    n.put("Tools", tools.serializeNBT());
    n.put("Cargo", cargo.serializeNBT());
    n.putInt("Active", activeSlot());
    n.putInt("Alerts", alerts);
    n.putInt("WorkTicks", workTicks);
    if (pending != null) n.putLong("PendingRoot", pending.root().asLong());
    else if (resumeRoot != null) n.putLong("PendingRoot", resumeRoot.asLong());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag n) {
    super.readAdditionalSaveData(n);
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    homeId = n.hasUUID("HomeId") ? n.getUUID("HomeId") : null;
    totemId = n.hasUUID("TotemId") ? n.getUUID("TotemId") : null;
    home = n.contains("Home") ? BlockPos.of(n.getLong("Home")) : null;
    totem = n.contains("Totem") ? BlockPos.of(n.getLong("Totem")) : null;
    timberId = n.hasUUID("Timber") ? n.getUUID("Timber") : null;
    timberAnchor = n.contains("TimberAnchor") ? BlockPos.of(n.getLong("TimberAnchor")) : null;
    homeDimension = n.getString("HomeDimension");
    totemDimension =
        n.contains("TotemDimension")
            ? n.getString("TotemDimension")
            : level().dimension().location().toString();
    entityData.set(JOB, n.contains("Job") ? n.getInt("Job") : 1);
    authorized = !n.contains("Authorized") || n.getBoolean("Authorized");
    tools.deserializeNBT(n.getCompound("Tools"));
    CompoundTag savedCargo = n.getCompound("Cargo").copy();
    // Never truncate a legacy inventory when the configured capacity is reduced.
    savedCargo.putInt("Size", Math.max(Balance.CARGO.get(), savedCargo.getInt("Size")));
    cargo.deserializeNBT(savedCargo);
    entityData.set(ACTIVE, Math.min(2, Math.max(0, n.getInt("Active"))));
    alerts = n.getInt("Alerts");
    workTicks = Math.max(0, n.getInt("WorkTicks"));
    resumeRoot = n.contains("PendingRoot") ? BlockPos.of(n.getLong("PendingRoot")) : null;
    if (level() instanceof ServerLevel l) WorldData.get(l).release(getUUID());
  }
}
