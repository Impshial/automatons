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
  public UUID owner, homeId, totemId, timberId;
  public BlockPos home, totem;
  public String homeDimension = "";
  public int alerts;
  private int smileUntil, angryUntil, workTicks, returnRetry;
  private long nextPath;
  private BlockPos pathTarget;
  private BlockPos resumeRoot;
  private TreeFinder.Tree pending;
  private TreeFinder.Landing pendingLanding;
  private final Map<Long, Long> failedTrees = new HashMap<>();
  public final Inventories.Tools tools =
      new Inventories.Tools(
          () -> {
            if (level() != null && !level().isClientSide) {
              selectTool();
              smile();
            }
          });
  public final ItemStackHandler cargo =
      new ItemStackHandler(9) {
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
    entityData.define(FACE, 0);
    entityData.define(ACTIVE, 0);
    entityData.define(STATUS, "Unassigned");
  }

  @Override
  protected float getStandingEyeHeight(Pose pose, EntityDimensions dim) {
    return 1.29f;
  }

  public boolean owns(Player p) {
    return owner != null && owner.equals(p.getUUID());
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
    return tools == null
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
    if (t != null && t.next() >= 0) return new ItemStack(t.wood.get(t.next()).getBlock());
    if (pending != null) return new ItemStack(level().getBlockState(pending.root()).getBlock());
    Station s = station();
    if (s != null && !s.trees.isEmpty())
      return new ItemStack(
          level().getBlockState(s.trees.values().iterator().next().root()).getBlock());
    return new ItemStack(Items.OAK_LOG);
  }

  public boolean cargoEmpty() {
    for (int i = 0; i < 9; i++) if (!cargo.getStackInSlot(i).isEmpty()) return false;
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
            && level().hasChunkAt(totem)
            && level().getBlockEntity(totem) instanceof Station s
            && s.isTotem()
            && s.identity.equals(totemId)
            && getUUID().equals(s.worker)
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
      old.worker = null;
      old.workerHome = null;
      old.alerts = 0;
      old.changed();
    }
    totem = null;
    totemId = null;
    pending = null;
    workTicks = 0;
    alerts = 0;
    if (level() instanceof ServerLevel l) WorldData.get(l).release(getUUID());
  }

  public String assign(Station target) {
    if (!target.isTotem() || owner == null || !owner.equals(target.owner))
      return "Choose your own totem";
    if (target.worker != null && !target.worker.equals(getUUID()))
      return "This totem already has a worker";
    unassign();
    totem = target.getBlockPos();
    totemId = target.identity;
    target.worker = getUUID();
    target.workerHome = homeId;
    target.changed();
    smile();
    return "Lumberton assigned";
  }

  public String rebind(Station s) {
    if (s.isTotem() || owner == null || !owner.equals(s.owner)) return "Choose your own pedestal";
    WorldData.Home existing =
        homeId == null ? null : WorldData.get((ServerLevel) level()).homes.get(homeId);
    if (existing != null && existing.exists()) return "Existing home is still present";
    home = s.getBlockPos();
    homeId = s.identity;
    homeDimension = level().dimension().location().toString();
    s.workers.add(getUUID());
    s.registerHome();
    s.changed();
    Station job = station();
    if (job != null) {
      job.workerHome = homeId;
      job.changed();
    }
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

  private void state(String text) {
    entityData.set(STATUS, text);
    int face = 0;
    if (tickCount < angryUntil) face = 5;
    else if (alerts != 0 || text.contains("missing") || text.contains("blocked")) face = 4;
    else if (tickCount < smileUntil) face = 1;
    else if (text.equals("Chopping") || text.equals("Processing timber")) face = 3;
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
    if (!level().isClientSide && isAlive() && !isNoAi() && tickCount % 5 == 0) {
      getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Balance.WALK.get());
      brain();
      if (tickCount % 20 == 0) pickup();
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
      } else if (s.is(net.minecraft.tags.ItemTags.SAPLINGS)
          || s.is(Items.STICK)
          || s.is(Items.APPLE)) {
        rest = Inventories.insert(cargo, rest, false);
      } else continue;
      if (rest.isEmpty()) e.discard();
      else e.setItem(rest);
      selectTool();
    }
  }

  private boolean inReach(BlockPos p) {
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

  private Path pathTo(BlockPos p) {
    List<BlockPos> candidates = new ArrayList<>();
    for (Direction d : Direction.Plane.HORIZONTAL)
      for (int dy = 1; dy >= -1; dy--) {
        BlockPos q = p.relative(d).offset(0, dy, 0);
        if (level().hasChunkAt(q)
            && level().getBlockState(q).getCollisionShape(level(), q).isEmpty()
            && level().getBlockState(q.above()).getCollisionShape(level(), q.above()).isEmpty()
            && level().getBlockState(q.below()).isFaceSturdy(level(), q.below(), Direction.UP))
          candidates.add(q);
      }
    candidates.sort(Comparator.comparingDouble(q -> distanceToSqr(Vec3.atCenterOf(q))));
    for (BlockPos q : candidates) {
      Path path = navigation.createPath(q, 0);
      if (path != null && path.canReach()) return path;
    }
    return null;
  }

  public boolean canReach(BlockPos p) {
    return inReach(p) || pathTo(p) != null;
  }

  private boolean travel(BlockPos p, String moving, String arrived) {
    if (inReach(p)) {
      navigation.stop();
      state(arrived);
      return true;
    }
    if (tickCount >= nextPath || pathTarget == null || !pathTarget.equals(p)) {
      pathTarget = p;
      nextPath = tickCount + 60;
      Path path = pathTo(p);
      if (path == null || !path.canReach()) {
        navigation.stop();
        state("Path blocked");
        returnRetry = tickCount + 60;
        return false;
      }
      navigation.moveTo(path, 1);
    }
    if (tickCount < returnRetry) {
      state("Path blocked");
      return false;
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
    resumeRoot = null;
    pendingLanding = null;
    pending = null;
    workTicks = 0;
    if (level() instanceof ServerLevel l) WorldData.get(l).release(getUUID());
  }

  private void brain() {
    ServerLevel l = (ServerLevel) level();
    selectTool();
    Station s = station();
    Timber timber = timber();
    alerts = Inventories.axe(activeTool()) ? alerts & ~TOOLS : alerts | TOOLS;
    if (Inventories.fits(
        cargo,
        timber != null && timber.pendingLoot != null
            ? timber.pendingLoot
            : List.of(expectedOutput()))) alerts &= ~CARGO;
    else alerts |= CARGO;
    if (owner == null) {
      state("Unassigned test worker — use a wand to claim");
      return;
    }
    var data = WorldData.get(l);
    WorldData.Home h = homeId == null ? null : data.homes.get(homeId);
    if (homeId == null || h == null || !h.exists()) {
      navigation.stop();
      clearPending();
      state("Home missing");
      return;
    }
    if (!homeDimension.equals(l.dimension().location().toString())) {
      navigation.stop();
      state("Home in another dimension");
      return;
    }
    if (totem != null && l.hasChunkAt(totem) && s == null) {
      unassign();
      s = null;
    }
    if (s != null) {
      boolean work = timber != null && !timber.finished() || !s.trees.isEmpty();
      if (work) alerts &= ~WOOD;
      else if (s.scanComplete && !s.scanUnloaded) alerts |= WOOD;
      if (s.storageComplete && !s.storageUnloaded) {
        if (Storage.anyOutput(s, l) && Storage.anyOpenOutput(s, l)) {
          if (Storage.anySpace(this, s)) alerts &= ~STORAGE;
          else alerts |= STORAGE;
        } else alerts &= ~STORAGE;
      }
    }
    if (h.recalled()) {
      clearPending();
      goHome("Recalled");
      return;
    }
    if (!l.hasChunkAt(home)) {
      navigation.stop();
      state("Dependency unloaded");
      return;
    }
    if (!(l.getBlockEntity(home) instanceof Station hs) || !hs.identity.equals(homeId)) {
      data.setHome(homeId, owner, home, false, false);
      state("Home missing");
      return;
    }
    if (totem == null) {
      goHome("Idle at home");
      return;
    }
    if (!l.hasChunkAt(totem)) {
      navigation.stop();
      state("Dependency unloaded");
      return;
    }
    if (s == null) {
      goHome("Unassigned");
      return;
    }
    if (!s.storageComplete) {
      state("Discovering storage");
      return;
    }
    if (!Storage.anyOutput(s, l)) {
      clearPending();
      if (s.storageUnloaded) {
        state("Dependency unloaded");
        navigation.stop();
      } else goHome("No storage");
      return;
    }
    if (!Storage.anyOpenOutput(s, l)) {
      clearPending();
      travel(
          totem, "Returning totem", s.storageUnloaded ? "Dependency unloaded" : "Storage blocked");
      return;
    }
    boolean needTools = (alerts & TOOLS) != 0;
    boolean spare = false;
    for (int i = 0; i < 3; i++) spare |= tools.getStackInSlot(i).isEmpty();
    boolean unloadDue =
        !cargoEmpty() && (timber == null || timber.finished() || (alerts & (CARGO | STORAGE)) != 0);
    if (unloadDue || needTools || (spare && cargoEmpty())) {
      if (tickCount % 20 == 0 || pathTarget == null) {
        BlockPos target = Storage.choose(this, s, needTools || !unloadDue);
        if (target == null && unloadDue) target = Storage.choose(this, s, false);
        if (target != null) {
          if (travel(
              target,
              needTools ? "Restocking" : "Unloading",
              needTools ? "Restocking" : "Unloading")) Storage.transfer(this, s, target);
          return;
        }
      } else if (status().equals("Unloading") || status().equals("Restocking")) {
        if (pathTarget != null && travel(pathTarget, status(), status()))
          Storage.transfer(this, s, pathTarget);
        return;
      }
    }
    if ((alerts & (TOOLS | STORAGE | CARGO)) != 0) {
      clearPending();
      travel(
          totem,
          "Returning totem",
          (alerts & TOOLS) != 0
              ? "Needs tools"
              : (alerts & CARGO) != 0 ? "Cargo full" : "Storage full");
      return;
    }
    // A chest may have room but be inaccessible; pause rather than start additional harvesting.
    if (tickCount % 60 == 0 && Storage.choose(this, s, false) == null) {
      clearPending();
      travel(totem, "Returning totem", "Storage blocked");
      returnRetry = tickCount + 60;
      return;
    }
    if (status().equals("Storage blocked") && tickCount < returnRetry) return;
    if (timberId != null && timber == null) {
      // Do not abandon an unloaded persisted job. Its anchor is saved alongside the id.
      if (timberAnchor != null && !l.hasChunkAt(timberAnchor)) {
        state("Dependency unloaded");
        return;
      }
      timberId = null;
      timberAnchor = null;
    }
    if (timber != null) {
      if (timber.finished()) {
        timberId = null;
        timberAnchor = null;
        clearPending();
        return;
      }
      if (!timber.safe(s)) {
        state("Timber blocked: protection, range, or obstruction");
        return;
      }
      if (timber.progress() < timber.duration) {
        state("Felling");
        navigation.stop();
        return;
      }
      int next = timber.next();
      if (next < 0) return;
      if (!Inventories.fits(
          cargo,
          timber.pendingLoot != null
              ? timber.pendingLoot
              : List.of(new ItemStack(timber.wood.get(next).getBlock())))) {
        alerts |= CARGO;
        travel(totem, "Returning totem", "Cargo full");
        return;
      }
      if (!travel(timber.targets.get(next), "Travelling", "Processing timber")) return;
      lookControl.setLookAt(Vec3.atCenterOf(timber.targets.get(next)));
      swing(InteractionHand.MAIN_HAND);
      workTicks += 5;
      if (workTicks
          >= Inventories.duration(
              activeTool(), Balance.PROCESS.get(), s.tier("speed"), s.tier("efficiency"))) {
        timber.process(next, this, s);
        workTicks = 0;
      }
      return;
    }
    if (pending == null && resumeRoot != null) {
      if (!l.hasChunkAt(resumeRoot)) {
        state("Dependency unloaded");
        return;
      }
      var restored = TreeFinder.inspect(l, resumeRoot, s).tree();
      resumeRoot = null;
      if (restored != null && data.reserve(restored.logs(), getUUID())) pending = restored;
      else workTicks = 0;
    }
    if (pending == null) {
      boolean reservedElsewhere = false;
      for (TreeFinder.Tree t : new ArrayList<>(s.trees.values())) {
        if (failedTrees.getOrDefault(t.root().asLong(), 0L) > l.getGameTime()) continue;
        if (!TreeFinder.revalidate(l, t, s, owner)) {
          s.trees.remove(t.root().asLong());
          continue;
        }
        if (!canReach(t.root())) {
          failedTrees.put(t.root().asLong(), l.getGameTime() + 200);
          s.scanReason = "No reachable trees";
          continue;
        }
        if (data.reserve(t.logs(), getUUID())) {
          pending = t;
          pendingLanding = null;
          workTicks = 0;
          break;
        } else reservedElsewhere = true;
      }
      if (pending == null) {
        if (reservedElsewhere) {
          travel(totem, "Returning totem", "Waiting for reserved tree");
          return;
        }
        if (s.scanComplete && !s.scanUnloaded && !s.trees.isEmpty()) alerts |= WOOD;
        if ((alerts & WOOD) != 0)
          travel(
              totem,
              "Returning totem",
              s.scanReason.contains("Protected")
                  ? "Protected trees"
                  : s.scanReason.equals("No reachable trees")
                      ? "No reachable trees"
                      : s.scanReason.contains("landing")
                          ? "No safe landing space"
                          : "No eligible wood");
        else {
          travel(
              totem, "Returning totem", s.trees.isEmpty() ? "Seeking tree" : "No reachable trees");
        }
        return;
      }
    }
    if (!TreeFinder.revalidate(l, pending, s, owner)) {
      s.trees.remove(pending.root().asLong());
      clearPending();
      return;
    }
    if (!travel(pending.root(), "Travelling", "Chopping")) return;
    if (pendingLanding == null) {
      if (!WorldEvents.budget(256)) {
        state("Seeking tree");
        return;
      }
      pendingLanding = TreeFinder.landing(l, pending, s, owner, this);
    }
    TreeFinder.Landing landing = pendingLanding;
    if (landing == null) {
      failedTrees.put(pending.root().asLong(), l.getGameTime() + 400);
      s.scanReason = "No safe landing space";
      clearPending();
      state("Tree landing blocked");
      return;
    }
    if (!WorldEvents.budget(64)) return;
    if (!TreeFinder.landingStillSafe(l, pending, landing, s, owner, this)) {
      failedTrees.put(pending.root().asLong(), l.getGameTime() + 100);
      clearPending();
      state("Tree landing blocked");
      return;
    }
    lookControl.setLookAt(Vec3.atCenterOf(pending.root()));
    swing(InteractionHand.MAIN_HAND);
    workTicks += 5;
    if (workTicks
        >= Inventories.duration(
            activeTool(),
            Balance.PREP.get() * pending.baseCount(),
            s.tier("speed"),
            s.tier("efficiency"))) {
      Timber t = Timber.commit(l, pending, landing, this, s);
      if (t != null) {
        timberId = t.getUUID();
        timberAnchor = t.blockPosition();
        wear(pending.baseCount());
        s.trees.remove(pending.root().asLong());
      } else {
        failedTrees.put(pending.root().asLong(), l.getGameTime() + 200);
        s.scanReason = "Crown cannot be safely separated";
      }
      clearPending();
      state(t != null ? "Felling" : "Tree landing blocked");
    }
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
          });
    return InteractionResult.sidedSuccess(level().isClientSide);
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
    return new WorkMenu(id, inv, null, this);
  }

  @Override
  public Component getDisplayName() {
    return hasCustomName() ? getCustomName() : Component.literal("Lumberton");
  }

  @Override
  protected void dropEquipment() {
    /* Internal tools and cargo are dropped together below. */
  }

  @Override
  protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    super.dropCustomDeathLoot(source, looting, recentlyHit);
    for (int i = 0; i < 3; i++) {
      spawnAtLocation(tools.getStackInSlot(i));
      tools.setStackInSlot(i, ItemStack.EMPTY);
    }
    for (int i = 0; i < 9; i++) {
      spawnAtLocation(cargo.getStackInSlot(i));
      cargo.setStackInSlot(i, ItemStack.EMPTY);
    }
    unassign();
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
    tools.deserializeNBT(n.getCompound("Tools"));
    cargo.deserializeNBT(n.getCompound("Cargo"));
    entityData.set(ACTIVE, Math.min(2, Math.max(0, n.getInt("Active"))));
    alerts = n.getInt("Alerts");
    workTicks = Math.max(0, n.getInt("WorkTicks"));
    resumeRoot = n.contains("PendingRoot") ? BlockPos.of(n.getLong("PendingRoot")) : null;
    if (level() instanceof ServerLevel l) WorldData.get(l).release(getUUID());
  }
}
