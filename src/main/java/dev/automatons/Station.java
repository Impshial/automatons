package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemStackHandler;

public class Station extends BlockEntity implements MenuProvider {
  public UUID identity = UUID.randomUUID(), owner, worker;
  public UUID workerHome;
  public UUID logisticsHome;
  public boolean recalled;
  public boolean showNames = true;
  public MinePlan mine;
  public FarmPlan farm;
  public WorksitePurpose purpose = WorksitePurpose.UNCONFIGURED;
  public Direction orientation = Direction.NORTH;
  public String setupIssue = "";

  /** The sole surface range: both nearby inventories and profession work use this boundary. */
  public int workRange() {
    return purpose.radius(this);
  }

  public BuildingProject project() {
    return level instanceof ServerLevel l ? WorldData.get(l).buildings.get(identity) : null;
  }

  public boolean worksiteReady() {
    return project() != null && project().ready;
  }

  public boolean configure(WorksitePurpose value) {
    if (!(level instanceof ServerLevel l)
        || !isTotem()
        || owner == null
        || value == WorksitePurpose.UNCONFIGURED
        || project() != null) return false;
    BuildingProject plan = new BuildingProject();
    plan.owner = owner;
    if (logisticsHome == null) {
      var homes =
          WorldData.get(l).homes.entrySet().stream()
              .filter(e -> e.getValue().exists() && owner.equals(e.getValue().owner()))
              .toList();
      if (homes.size() == 1) logisticsHome = homes.get(0).getKey();
    }
    plan.home = logisticsHome;
    plan.totem = identity;
    plan.anchor = worldPosition;
    plan.front = orientation;
    plan.purpose = value;
    plan.created = l.getGameTime();
    if (!plan.reserve(l)) {
      setupIssue = plan.reason;
      changed();
      return false;
    }
    setupIssue = "";
    purpose = value;
    WorldData.get(l).buildings.put(identity, plan);
    WorldData.get(l).setDirty();
    invalidateTrees();
    refreshStorage();
    changed();
    return true;
  }

  public BlockPos buildOutlineMin, buildOutlineMax;
  public int alerts;
  public final Set<UUID> workers = new HashSet<>();
  public final Map<Long, Integer> chestFlags = new HashMap<>();
  public final List<BlockPos> chests = new ArrayList<>();
  public final LinkedHashMap<Long, TreeFinder.Tree> trees = new LinkedHashMap<>();
  public final Map<Long, Long> deferredFalls = new HashMap<>();
  public String scanReason = "Searching";
  public int cursor, storageCursor, storageEpoch;
  private final LinkedHashSet<BlockPos> pendingRoots = new LinkedHashSet<>();
  private List<BlockPos> scanColumns = List.of();
  private boolean swept;
  private double discoveredDistance;
  private int dispatchTurn;
  private long timberCacheAt = -1;
  private List<Timber> timberCache = List.of();
  public boolean scanComplete, scanUnloaded, storageComplete, storageUnloaded;
  private boolean roundUnloaded, storageRoundUnloaded;
  private int scanRadius = -1, scanHeight = -1, chestScanRange = -1;
  private List<ChunkPos> chestScanChunks = List.of();
  private Iterator<BlockEntity> chestScanEntities;
  public final ItemStackHandler inventory =
      new ItemStackHandler(isTotem() ? 4 : 6) {
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
          if (!isTotem()) {
            if (level != null) {
              var recipes = level.getRecipeManager().getAllRecipesFor(Automatons.CREATION.get());
              if (!recipes.isEmpty())
                return recipes.stream().anyMatch(r -> r.components().get(slot).test(s));
            }
            return s.is(
                switch (slot) {
                  case 0 -> Automatons.HEAD.get();
                  case 1, 3 -> Automatons.ARM.get();
                  case 2 -> Automatons.BODY.get();
                  default -> Automatons.LEG.get();
                });
          }
          return false; // Retired slots reject all automation and menu insertion.
        }

        @Override
        public int getSlotLimit(int slot) {
          return isTotem() ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (level != null && !level.isClientSide) {
            invalidateTrees();
            refreshStorage();
          }
          changed();
        }
      };

  public Station(BlockPos p, BlockState s) {
    super(Automatons.STATION.get(), p, s);
  }

  public boolean isTotem() {
    return getBlockState().is(Automatons.TOTEM.get());
  }

  public int tier(String family) {
    return 0; // Retained compatibility API; installed legacy items are permanently inert.
  }

  public int miningRange() {
    return Balance.MINING_RANGE.get();
  }

  public void recoverRetired(Player p) {
    if (!isTotem() || !owns(p) || level == null || level.isClientSide) return;
    boolean recovered = false;
    for (int i = 0; i < inventory.getSlots(); i++) {
      ItemStack saved = inventory.getStackInSlot(i);
      if (saved.isEmpty()) continue;
      recovered = true;
      inventory.setStackInSlot(i, ItemStack.EMPTY);
      if (!p.getInventory().add(saved)) p.drop(saved, false);
    }
    if (!recovered) return;
    invalidateTrees();
    refreshStorage();
    changed();
  }

  public boolean owns(Player p) {
    return owner != null && (owner.equals(p.getUUID()) || p.hasPermissions(2));
  }

  public void changed() {
    setChanged();
    if (level != null && !level.isClientSide)
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
  }

  public void registerHome() {
    if (!isTotem() && owner != null && level instanceof ServerLevel l) {
      WorldData.get(l).setHome(identity, owner, worldPosition, recalled, true);
      Ledger ledger = Ledger.get(l.getServer());
      if (!Objects.equals(ledger.homeNames.put(identity, showNames), showNames)) ledger.setDirty();
    }
  }

  public void showNames(boolean value) {
    if (isTotem()) return;
    showNames = value;
    registerHome();
    if (level instanceof ServerLevel l && isTotem()) farm = WorldData.get(l).farms.get(identity);
    changed();
    if (level instanceof ServerLevel l)
      for (ServerLevel dimension : l.getServer().getAllLevels())
        for (var entity : dimension.getAllEntities())
          if (entity instanceof Lumberton w && identity.equals(w.homeId)) {
            w.showNames(value);
            WorkerIdentity.update(w, "Deployed");
          }
  }

  @Override
  public void onLoad() {
    super.onLoad();
    registerHome();
    if (level instanceof ServerLevel l && mine != null && mine.entrance != null)
      WorldData.get(l).mines.put(mine.id, mine);
    if (level instanceof ServerLevel && isTotem()) WorldEvents.stationLoaded(this);
  }

  public Lumberton loadedWorker() {
    return loadedWorkers().stream().findFirst().orElse(null);
  }

  public void recall(boolean value) {
    if (isTotem()) return;
    recalled = value;
    registerHome();
    changed();
  }

  public SimpleContainer components() {
    SimpleContainer c = new SimpleContainer(6);
    for (int i = 0; i < Math.min(6, inventory.getSlots()); i++)
      c.setItem(i, inventory.getStackInSlot(i));
    return c;
  }

  public boolean ready() {
    return !isTotem()
        && level != null
        && level
            .getRecipeManager()
            .getRecipeFor(Automatons.CREATION.get(), components(), level)
            .isPresent();
  }

  public ItemStack assemble(ServerPlayer player) {
    if (!owns(player) || !ready() || isRemoved()) return ItemStack.EMPTY;
    ItemStack output = WorkerIdentity.assemble(player, this);
    for (int i = 0; i < 6; i++) inventory.extractItem(i, 1, false);
    changed();
    return output;
  }

  /** Retained only for old development harnesses; normal creation is the result slot. */
  @Deprecated
  public boolean create(ServerPlayer player) {
    return false;
  }

  public java.util.List<Lumberton> loadedWorkers() {
    if (!(level instanceof ServerLevel l)) return java.util.List.of();
    return workers.stream()
        .map(l::getEntity)
        .filter(e -> e instanceof Lumberton)
        .map(e -> (Lumberton) e)
        .filter(w -> identity.equals(w.totemId) && !w.isRemoved())
        .sorted(java.util.Comparator.comparing(Lumberton::getUUID))
        .toList();
  }

  public void removeMember(UUID id) {
    workers.remove(id);
    if (id.equals(worker)) {
      worker = null;
      workerHome = null;
    }
    if (level instanceof ServerLevel l) WorkerIdentity.unbind(l, id, identity);
    changed();
  }

  public void destroyed() {
    if (!(level instanceof ServerLevel l)) return;
    if (!isTotem() && owner != null)
      WorldData.get(l).setHome(identity, owner, worldPosition, recalled, false);
    if (isTotem()) {
      WorldData data = WorldData.get(l);
      BuildingProject removed = data.buildings.remove(identity);
      if (removed != null) removed.supplies.abandon(l);
      if (removed != null
          && removed.builder != null
          && l.getEntity(removed.builder) instanceof Lumberton builder) {
        builder.building.release();
        builder.getNavigation().stop();
        ((WorkerMoveControl) builder.getMoveControl()).stop();
      }
      data.logistics.values().forEach(network -> network.removeSite(identity));
      data.setDirty();
      for (UUID id : new java.util.ArrayList<>(workers)) WorkerIdentity.unbind(l, id, identity);
      workers.clear();
      worker = null;
      workerHome = null;
      alerts = 0;
    }
  }

  public static void tick(Level level, BlockPos pos, BlockState state, Station s) {
    if (!s.isTotem()) return;
    if (s.worker != null) {
      s.workers.add(s.worker);
      s.worker = null;
      s.workerHome = null;
      s.changed();
    }
    if (!s.workers.isEmpty() || s.project() != null && !s.project().ready)
      WorldEvents.requestScan(s);
    if (level.getGameTime() % 20 == 0) {
      if (s.mine != null) s.changed();
      int flags = 0;
      for (Lumberton w : s.loadedWorkers()) if (w.authorized && w.job() != 0) flags |= w.alerts;
      if (flags != s.alerts) {
        s.alerts = flags;
        s.changed();
      }
    }
  }

  private static int vertical(int layer) {
    return layer == 0 ? 0 : (layer % 2 == 1 ? (layer + 1) / 2 : -layer / 2);
  }

  public void invalidateTrees() {
    deferredFalls.clear();
    trees.clear();
    pendingRoots.clear();
    scanComplete = false;
    swept = false;
    cursor = 0;
    discoveredDistance = 0;
  }

  public void terrainChanged(BlockPos pos) {
    // Keep unrelated discoveries and the outward scan cursor. Torches, building blocks
    // and saplings must not restart a 65-block-high search on every placement.
    deferredFalls.clear();
    trees
        .entrySet()
        .removeIf(
            e ->
                e.getValue().logs().contains(pos)
                    || e.getValue().leaves().contains(pos)
                    || e.getValue().root().below().equals(pos));
    if (TreeFinder.within(this, pos)
        && Math.abs(pos.getY() - worldPosition.getY()) <= Balance.ROOT_HEIGHT.get()) {
      if (pendingRoots.size() < 256) pendingRoots.add(pos.immutable());
      for (Direction direction : Direction.values()) {
        BlockPos adjacent = pos.relative(direction);
        if (pendingRoots.size() < 256
            && level.hasChunkAt(adjacent)
            && level.getBlockState(adjacent).is(net.minecraft.tags.BlockTags.LOGS))
          pendingRoots.add(adjacent);
      }
    }
  }

  public void refreshStorage() {
    if (storageComplete) {
      storageCursor = 0;
      chestScanEntities = null;
      storageComplete = false;
      storageRoundUnloaded = false;
    }
  }

  public List<TreeFinder.Tree> orderedTrees() {
    return trees.values().stream()
        .sorted(
            Comparator.comparingDouble((TreeFinder.Tree t) -> t.distanceFrom(this))
                .thenComparingInt(t -> t.root().getX())
                .thenComparingInt(t -> t.root().getZ())
                .thenComparingInt(t -> t.root().getY()))
        .toList();
  }

  public boolean discoveredBefore(TreeFinder.Tree tree) {
    return pendingRoots.isEmpty()
        && (scanComplete || tree.distanceFrom(this) <= discoveredDistance);
  }

  public List<Timber> timberJobs() {
    if (!(level instanceof ServerLevel l)) return List.of();
    if (timberCacheAt != l.getGameTime() / 20) {
      timberCacheAt = l.getGameTime() / 20;
      timberCache =
          l.getEntitiesOfClass(
              Timber.class,
              new AABB(worldPosition).inflate(workRange() + 32, 80, workRange() + 32),
              t ->
                  !t.placeOnLanding
                      && !t.finished()
                      && Objects.equals(owner, t.owner)
                      && TreeFinder.within(this, t.blockPosition()));
    }
    return timberCache.stream()
        .filter(t -> !t.isRemoved() && !t.finished())
        .sorted(
            Comparator.comparingDouble((Timber t) -> t.blockPosition().distSqr(worldPosition))
                .thenComparing(Timber::getUUID))
        .toList();
  }

  public void dispatch() {
    List<Lumberton> active = loadedWorkers();
    if (active.isEmpty()) return;
    int start = Math.floorMod(dispatchTurn++, active.size());
    for (int i = 0; i < active.size(); i++) {
      Lumberton w = active.get((start + i) % active.size());
      if (w.wantsJob) w.acceptNext(this);
    }
  }

  public void scanWork(ServerLevel l) {
    if (purpose == WorksitePurpose.LUMBER
        || loadedWorkers().stream().anyMatch(w -> w.job() == WorkerJob.LUMBERTON)) scanTrees(l);
    scanChests(l);
    dispatch();
  }

  private void scanTrees(ServerLevel l) {
    deferredFalls.values().removeIf(until -> until <= l.getGameTime());
    int range = workRange(), height = Balance.ROOT_HEIGHT.get() * 2 + 1;
    if (scanRadius != range || scanHeight != height || scanColumns.isEmpty()) {
      scanRadius = range;
      scanHeight = height;
      invalidateTrees();
      List<BlockPos> offsets = new ArrayList<>();
      for (int x = -range; x <= range; x++)
        for (int z = -range; z <= range; z++) offsets.add(new BlockPos(x, 0, z));
      offsets.sort(
          Comparator.comparingDouble((BlockPos p) -> p.getX() * p.getX() + p.getZ() * p.getZ())
              .thenComparingInt(BlockPos::getX)
              .thenComparingInt(BlockPos::getZ));
      scanColumns = List.copyOf(offsets);
    }
    int total = scanColumns.size() * height;
    int probes = 0;
    for (int i = 0; i < Balance.TOTEM_BUDGET.get() * 8; i++) {
      int value = cursor++;
      BlockPos column = scanColumns.get(value / height),
          pos = worldPosition.offset(column.getX(), vertical(value % height), column.getZ());
      if (!l.hasChunkAt(pos)) roundUnloaded = true;
      else if (pos.getY() >= l.getMinBuildHeight()
          && pos.getY()
              < l.getHeight(
                  net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                  pos.getX(),
                  pos.getZ())) {
        // Heightmap-guaranteed air and positions below the world need no block probe.
        // Charge the same bounded budget for every actual terrain inspection.
        if (probes >= Balance.TOTEM_BUDGET.get() || !WorldEvents.budget(1)) {
          cursor--;
          break;
        }
        probes++;
        if (l.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
          if (pendingRoots.size() >= 256) {
            cursor--;
            break;
          }
          if (trees.values().stream().noneMatch(t -> t.logs().contains(pos)))
            pendingRoots.add(pos.immutable());
        }
      }
      if (cursor % height == 0) {
        double radius =
            Math.max(
                0, Math.sqrt(column.getX() * column.getX() + column.getZ() * column.getZ()) - 1);
        discoveredDistance = Math.max(discoveredDistance, radius * radius);
      }
      if (cursor >= total) {
        cursor = 0;
        swept = true;
        scanUnloaded = roundUnloaded;
        roundUnloaded = false;
        trees
            .entrySet()
            .removeIf(
                e ->
                    l.hasChunkAt(e.getValue().root())
                        && !l.getBlockState(e.getValue().root())
                            .is(net.minecraft.tags.BlockTags.LOGS));
        break;
      }
    }
    for (int i = 0; i < 2 && !pendingRoots.isEmpty() && WorldEvents.budget(128); i++) {
      BlockPos root = pendingRoots.iterator().next();
      pendingRoots.remove(root);
      var result = TreeFinder.work(l, root, this);
      if (result.tree() != null) {
        TreeFinder.Tree tree = result.tree();
        trees.entrySet().removeIf(e -> tree.logs().contains(e.getValue().root()));
        trees.put(tree.root().asLong(), tree);
        pendingRoots.removeIf(tree.logs()::contains);
        if (trees.size() > 512) {
          var ordered = orderedTrees();
          trees.remove(ordered.get(ordered.size() - 1).root().asLong());
        }
      } else scanReason = result.reason();
    }
    if (swept && pendingRoots.isEmpty()) scanComplete = true;
  }

  /**
   * Bounded discovery of loaded block entities; a larger work range does not scan every air block.
   */
  private void scanChests(ServerLevel l) {
    int range = workRange();
    if (chestScanRange != range) {
      chestScanRange = range;
      storageCursor = 0;
      chestScanEntities = null;
      chests.removeIf(p -> !Storage.inRange(this, p));
      storageComplete = false;
      storageRoundUnloaded = false;
      List<ChunkPos> chunks = new ArrayList<>();
      for (int x = (worldPosition.getX() - range) >> 4;
          x <= (worldPosition.getX() + range) >> 4;
          x++)
        for (int z = (worldPosition.getZ() - range) >> 4;
            z <= (worldPosition.getZ() + range) >> 4;
            z++) chunks.add(new ChunkPos(x, z));
      chunks.sort(
          Comparator.comparingDouble(
                  (ChunkPos p) ->
                      p.getMiddleBlockPosition(worldPosition.getY()).distSqr(worldPosition))
              .thenComparingLong(ChunkPos::toLong));
      chestScanChunks = List.copyOf(chunks);
    }
    for (int probes = 0; probes < 64 && WorldEvents.budget(1); probes++) {
      if (storageCursor >= chestScanChunks.size()) {
        storageCursor = 0;
        chestScanEntities = null;
        storageComplete = true;
        storageEpoch++;
        storageUnloaded = storageRoundUnloaded;
        storageRoundUnloaded = false;
        chests.removeIf(
            p ->
                !Storage.inRange(this, p)
                    || l.hasChunkAt(p)
                        && (!Storage.isChest(l, p) || !Storage.canonical(l, p).equals(p)));
        chests.sort(
            Comparator.comparingDouble((BlockPos p) -> p.distSqr(worldPosition))
                .thenComparingLong(BlockPos::asLong));
        return;
      }
      ChunkPos pos = chestScanChunks.get(storageCursor);
      var chunk = l.getChunkSource().getChunkNow(pos.x, pos.z);
      if (chunk == null) {
        storageRoundUnloaded = true;
        storageCursor++;
        chestScanEntities = null;
        continue;
      }
      if (chestScanEntities == null)
        chestScanEntities = chunk.getBlockEntities().values().iterator();
      try {
        if (!chestScanEntities.hasNext()) {
          storageCursor++;
          chestScanEntities = null;
          continue;
        }
        BlockPos p = chestScanEntities.next().getBlockPos();
        if (Storage.inRange(this, p)
            && Storage.isChest(l, p)
            && Storage.canonical(l, p).equals(p)
            && !chests.contains(p)) chests.add(p);
      } catch (ConcurrentModificationException changed) {
        // A chest changed between visits. Revisit only this chunk, never restart the whole area.
        chestScanEntities = null;
      }
    }
  }

  public int flags(BlockPos p) {
    return 3;
  }

  public void toggleChest(int index, int bit) {
    if (index >= 0 && index < chests.size()) {
      BlockPos p = chests.get(index);
      chestFlags.put(p.asLong(), flags(p) ^ bit);
      refreshStorage();
      changed();
    }
  }

  @Override
  public Component getDisplayName() {
    return Component.literal(isTotem() ? "Automaton Totem" : "Automaton Pedestal");
  }

  @Override
  public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
    return new WorkMenu(id, inv, this, null);
  }

  @Override
  protected void saveAdditional(CompoundTag n) {
    super.saveAdditional(n);
    n.putInt("DataVersion", 2);
    n.putInt("Purpose", purpose.ordinal());
    n.putInt("Orientation", orientation.get2DDataValue());
    n.putUUID("Identity", identity);
    if (owner != null) n.putUUID("Owner", owner);
    if (worker != null) n.putUUID("Worker", worker);
    if (workerHome != null) n.putUUID("WorkerHome", workerHome);
    if (logisticsHome != null) n.putUUID("LogisticsHome", logisticsHome);
    n.putBoolean("Recall", recalled);
    n.putBoolean("ShowNames", showNames);
    n.putInt("Alerts", alerts);
    n.put("Inventory", inventory.serializeNBT());
    if (mine != null) n.put("Mine", mine.save());
    ListTag ids = new ListTag();
    for (UUID id : workers) {
      CompoundTag c = new CompoundTag();
      c.putUUID("Id", id);
      ids.add(c);
    }
    n.put("Workers", ids);
    ListTag flags = new ListTag();
    chestFlags.forEach(
        (p, f) -> {
          CompoundTag c = new CompoundTag();
          c.putLong("Pos", p);
          c.putInt("Flags", f);
          flags.add(c);
        });
    n.put("ChestFlags", flags);
  }

  @Override
  public void load(CompoundTag n) {
    super.load(n);
    purpose = WorksitePurpose.of(n.getInt("Purpose"));
    orientation = Direction.from2DDataValue(n.getInt("Orientation"));
    buildOutlineMin =
        n.contains("BuildOutlineMin") ? BlockPos.of(n.getLong("BuildOutlineMin")) : null;
    buildOutlineMax =
        n.contains("BuildOutlineMax") ? BlockPos.of(n.getLong("BuildOutlineMax")) : null;
    if (n.hasUUID("Identity")) identity = n.getUUID("Identity");
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    worker = n.hasUUID("Worker") ? n.getUUID("Worker") : null;
    workerHome = n.hasUUID("WorkerHome") ? n.getUUID("WorkerHome") : null;
    logisticsHome = n.hasUUID("LogisticsHome") ? n.getUUID("LogisticsHome") : null;
    recalled = n.getBoolean("Recall");
    showNames = !n.contains("ShowNames") || n.getBoolean("ShowNames");
    alerts = n.getInt("Alerts");
    mine = n.contains("Mine") ? MinePlan.load(n.getCompound("Mine")) : null;
    CompoundTag slots = n.getCompound("Inventory").copy();
    if (!isTotem() && slots.getInt("Size") < 6) slots.putInt("Size", 6);
    inventory.deserializeNBT(slots);
    workers.clear();
    for (Tag t : n.getList("Workers", 10)) workers.add(((CompoundTag) t).getUUID("Id"));
    if (isTotem() && worker != null) workers.add(worker);
    chestFlags.clear();
    for (Tag t : n.getList("ChestFlags", 10)) {
      CompoundTag c = (CompoundTag) t;
      chestFlags.put(c.getLong("Pos"), c.getInt("Flags"));
    }
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag n = saveWithoutMetadata();
    if (isTotem() && level instanceof ServerLevel l) {
      var plan = WorldData.get(l).buildings.get(identity);
      if (plan != null && !plan.ready) {
        int minX = Integer.MAX_VALUE,
            minZ = Integer.MAX_VALUE,
            maxX = Integer.MIN_VALUE,
            maxZ = Integer.MIN_VALUE;
        for (var cell : plan.template(l).cells) {
          BlockPos p = plan.template(l).world(plan.anchor, plan.front, cell.local());
          minX = Math.min(minX, p.getX());
          minZ = Math.min(minZ, p.getZ());
          maxX = Math.max(maxX, p.getX() + 1);
          maxZ = Math.max(maxZ, p.getZ() + 1);
        }
        n.putLong("BuildOutlineMin", new BlockPos(minX, plan.anchor.getY(), minZ).asLong());
        n.putLong("BuildOutlineMax", new BlockPos(maxX, plan.anchor.getY(), maxZ).asLong());
      }
    }
    if (n.contains("Mine")) {
      CompoundTag mineTag = n.getCompound("Mine");
      mineTag.remove("Supports");
      mineTag.remove("Passage");
      mineTag.remove("Exclusions");
      mineTag.remove("Torches");
      mineTag.remove("TorchSupports");
    }
    return n;
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public AABB getRenderBoundingBox() {
    if (!isTotem()) return new AABB(worldPosition).inflate(.5).expandTowards(0, 3, 0);
    return new AABB(worldPosition)
        .expandTowards(
            0, level == null ? 320 : level.getMaxBuildHeight() - worldPosition.getY(), 0);
  }
}
