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
  public boolean recalled;
  public int alerts;
  public final Set<UUID> workers = new HashSet<>();
  public final Map<Long, Integer> chestFlags = new HashMap<>();
  public final List<BlockPos> chests = new ArrayList<>();
  public final LinkedHashMap<Long, TreeFinder.Tree> trees = new LinkedHashMap<>();
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
  private int scanRadius = -1, storageRadius = -1;
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
          if (!(s.getItem() instanceof UpgradeItem u)) return false;
          for (int i = 0; i < 4; i++)
            if (i != slot
                && getStackInSlot(i).getItem() instanceof UpgradeItem other
                && other.family.equals(u.family)) return false;
          return true;
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
    for (int i = 0; i < 4; i++)
      if (inventory.getStackInSlot(i).getItem() instanceof UpgradeItem u && u.family.equals(family))
        return u.tier;
    return 0;
  }

  public int storageRange() {
    return Balance.STORAGE.get() + 4 * tier("storage");
  }

  public int lumberRange() {
    return Balance.LUMBER.get() + 8 * tier("lumber");
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
    if (!isTotem() && owner != null && level instanceof ServerLevel l)
      WorldData.get(l).setHome(identity, owner, worldPosition, recalled, true);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    registerHome();
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
    if (!s.workers.isEmpty()) WorldEvents.requestScan(s);
    if (level.getGameTime() % 20 == 0) {
      int flags = 0;
      for (Lumberton w : s.loadedWorkers()) if (w.authorized && w.job() == 1) flags |= w.alerts;
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
    trees.clear();
    pendingRoots.clear();
    scanComplete = false;
    swept = false;
    cursor = 0;
    discoveredDistance = 0;
  }

  public void refreshStorage() {
    if (storageComplete) {
      storageCursor = 0;
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
              new AABB(worldPosition).inflate(lumberRange() + 32, 80, lumberRange() + 32),
              t ->
                  !t.finished()
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
    int range = lumberRange(), height = Balance.ROOT_HEIGHT.get() * 2 + 1;
    if (scanRadius != range || scanColumns.isEmpty()) {
      scanRadius = range;
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
    for (int i = 0; i < Balance.TOTEM_BUDGET.get() && WorldEvents.budget(1); i++) {
      int value = cursor++;
      BlockPos column = scanColumns.get(value / height),
          pos = worldPosition.offset(column.getX(), vertical(value % height), column.getZ());
      if (!l.hasChunkAt(pos)) roundUnloaded = true;
      else if (l.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
        if (pendingRoots.size() >= 256) {
          cursor--;
          break;
        }
        if (trees.values().stream().noneMatch(t -> t.logs().contains(pos)))
          pendingRoots.add(pos.immutable());
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
    int sr = storageRange(),
        side = sr * 2 + 1,
        layers = Balance.HEIGHT.get() * 2 + 1,
        storageTotal = side * side * layers;
    if (storageRadius != sr) {
      storageRadius = sr;
      storageCursor = 0;
      chests.clear();
      storageComplete = false;
    }
    for (int i = 0; i < 64 && WorldEvents.budget(1); i++) {
      int value = storageCursor++;
      BlockPos pos =
          worldPosition.offset(
              value % side - sr,
              value / (side * side) - Balance.HEIGHT.get(),
              (value / side) % side - sr);
      if (!l.hasChunkAt(pos)) storageRoundUnloaded = true;
      else if (Storage.isChest(l, pos)
          && Storage.canonical(l, pos).equals(pos)
          && !chests.contains(pos)) chests.add(pos);
      if (storageCursor >= storageTotal) {
        storageCursor = 0;
        storageComplete = true;
        storageEpoch++;
        storageUnloaded = storageRoundUnloaded;
        storageRoundUnloaded = false;
        chests.removeIf(
            p -> !Storage.inRange(this, p) || l.hasChunkAt(p) && !Storage.isChest(l, p));
        chests.sort(
            Comparator.comparingDouble((BlockPos p) -> p.distSqr(worldPosition))
                .thenComparingLong(BlockPos::asLong));
        break;
      }
    }
    dispatch();
  }

  public int flags(BlockPos p) {
    return chestFlags.getOrDefault(p.asLong(), 3);
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
    n.putUUID("Identity", identity);
    if (owner != null) n.putUUID("Owner", owner);
    if (worker != null) n.putUUID("Worker", worker);
    if (workerHome != null) n.putUUID("WorkerHome", workerHome);
    n.putBoolean("Recall", recalled);
    n.putInt("Alerts", alerts);
    n.put("Inventory", inventory.serializeNBT());
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
    if (n.hasUUID("Identity")) identity = n.getUUID("Identity");
    owner = n.hasUUID("Owner") ? n.getUUID("Owner") : null;
    worker = n.hasUUID("Worker") ? n.getUUID("Worker") : null;
    workerHome = n.hasUUID("WorkerHome") ? n.getUUID("WorkerHome") : null;
    recalled = n.getBoolean("Recall");
    alerts = n.getInt("Alerts");
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
    return saveWithoutMetadata();
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
