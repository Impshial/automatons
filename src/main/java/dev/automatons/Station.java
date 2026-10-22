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
  public int cursor, storageCursor;
  public boolean scanComplete, scanUnloaded, storageComplete, storageUnloaded;
  private boolean roundUnloaded, storageRoundUnloaded;
  private int scanRadius = -1, storageRadius = -1;
  public final ItemStackHandler inventory =
      new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack s) {
          if (!isTotem() && level != null) {
            var recipes = level.getRecipeManager().getAllRecipesFor(Automatons.CREATION.get());
            if (!recipes.isEmpty())
              return recipes.stream()
                  .anyMatch(
                      r ->
                          switch (slot) {
                            case 0 -> r.wood().test(s);
                            case 1 -> r.metal().test(s);
                            case 2 -> r.core().test(s);
                            default -> r.tool().test(s) && Inventories.axe(s);
                          });
          }
          if (!isTotem())
            return switch (slot) {
              case 0 -> s.is(net.minecraft.tags.ItemTags.OAK_LOGS);
              case 1 -> s.is(Items.IRON_INGOT);
              case 2 -> s.is(Items.AMETHYST_SHARD);
              default -> Inventories.axe(s);
            };
          if (!(s.getItem() instanceof UpgradeItem u)) return false;
          for (int i = 0; i < 4; i++)
            if (i != slot
                && getStackInSlot(i).getItem() instanceof UpgradeItem other
                && other.family.equals(u.family)) return false;
          return true;
        }

        @Override
        public int getSlotLimit(int slot) {
          return isTotem() || slot == 3 ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
          if (level != null && !level.isClientSide) {
            trees.clear();
            scanComplete = false;
            cursor = 0;
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
    return owner != null && owner.equals(p.getUUID());
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
  }

  public Lumberton loadedWorker() {
    return worker != null
            && level instanceof ServerLevel l
            && l.getEntity(worker) instanceof Lumberton w
        ? w
        : null;
  }

  public void recall(boolean value) {
    if (isTotem()) return;
    recalled = value;
    registerHome();
    changed();
  }

  public boolean create(ServerPlayer player) {
    if (!owns(player) || isTotem() || !(level instanceof ServerLevel l)) return false;
    var c =
        new SimpleContainer(
            inventory.getStackInSlot(0),
            inventory.getStackInSlot(1),
            inventory.getStackInSlot(2),
            inventory.getStackInSlot(3));
    var recipe = l.getRecipeManager().getRecipeFor(Automatons.CREATION.get(), c, l);
    if (recipe.isEmpty()) {
      player.displayClientMessage(
          Component.literal("Recipe incomplete: check the four ingredient slots"), true);
      return false;
    }
    Lumberton w = Automatons.LUMBERTON.get().create(l);
    if (w == null) return false;
    BlockPos spawn = null;
    search:
    for (int radius = 1; radius <= 3; radius++)
      for (int dx = -radius; dx <= radius; dx++)
        for (int dz = -radius; dz <= radius; dz++)
          for (int dy = 0; dy >= -1; dy--) {
            BlockPos p = worldPosition.offset(dx, dy, dz);
            if (!l.hasChunkAt(p)) continue;
            w.moveTo(p.getX() + .5, p.getY(), p.getZ() + .5, player.getYRot(), 0);
            if (l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)
                && l.getFluidState(p).isEmpty()
                && l.noCollision(w)
                && l.getEntities(w, w.getBoundingBox()).isEmpty()) {
              spawn = p;
              break search;
            }
          }
    if (spawn == null) {
      player.displayClientMessage(
          Component.literal("Clear a safe space beside the pedestal"), true);
      return false;
    }
    CreationRecipe r = recipe.get();
    w.owner = owner;
    w.home = worldPosition;
    w.homeId = identity;
    w.homeDimension = l.dimension().location().toString();
    w.tools.setStackInSlot(0, inventory.getStackInSlot(3).copyWithCount(1));
    w.selectTool();
    w.smile();
    if (!l.addFreshEntity(w)) return false;
    inventory.extractItem(0, r.woodCount(), false);
    inventory.extractItem(1, r.metalCount(), false);
    inventory.extractItem(2, 1, false);
    inventory.extractItem(3, 1, false);
    workers.add(w.getUUID());
    registerHome();
    changed();
    return true;
  }

  public void destroyed() {
    if (!(level instanceof ServerLevel l)) return;
    if (!isTotem() && owner != null)
      WorldData.get(l).setHome(identity, owner, worldPosition, recalled, false);
    if (isTotem()) {
      Lumberton w = loadedWorker();
      if (w != null) w.unassign();
      alerts = 0;
    }
  }

  public static void tick(Level level, BlockPos pos, BlockState state, Station s) {
    if (!s.isTotem() || s.worker == null) return;
    WorldEvents.requestScan(s);
    if (level.getGameTime() % 20 == 0) {
      Lumberton w = s.loadedWorker();
      if (w != null && s.identity.equals(w.totemId)) {
        if (s.alerts != w.alerts) {
          s.alerts = w.alerts;
          s.changed();
        }
      }
    }
  }

  private static int vertical(int layer) {
    return layer == 0 ? 0 : (layer % 2 == 1 ? (layer + 1) / 2 : -layer / 2);
  }

  public void scanWork(ServerLevel l) {
    int r = lumberRange(), side = 2 * r + 1, total = side * side * 33;
    if (scanRadius != r) {
      scanRadius = r;
      cursor = 0;
      trees.clear();
      scanComplete = false;
    }
    for (int i = 0; i < Balance.TOTEM_BUDGET.get() && WorldEvents.budget(1); i++) {
      int v = cursor++;
      BlockPos p =
          worldPosition.offset(v % side - r, vertical(v / (side * side)), (v / side) % side - r);
      if (!l.hasChunkAt(p)) roundUnloaded = true;
      else if (TreeFinder.isRoot(l, p) && trees.size() < 32 && WorldEvents.budget(64)) {
        TreeFinder.Result result = TreeFinder.inspect(l, p, this);
        if (result.tree() != null) trees.put(p.asLong(), result.tree());
        else scanReason = result.reason();
      }
      if (cursor >= total) {
        cursor = 0;
        scanComplete = true;
        scanUnloaded = roundUnloaded;
        roundUnloaded = false;
        trees
            .entrySet()
            .removeIf(
                e ->
                    l.hasChunkAt(e.getValue().root())
                        && !TreeFinder.isRoot(l, e.getValue().root()));
        break;
      }
    }
    int sr = storageRange(), ss = sr * 2 + 1, sh = Balance.HEIGHT.get() * 2 + 1, st = ss * ss * sh;
    if (storageRadius != sr) {
      storageRadius = sr;
      storageCursor = 0;
      chests.clear();
      storageComplete = false;
    }
    for (int i = 0; i < 16 && WorldEvents.budget(1); i++) {
      int v = storageCursor++;
      BlockPos p =
          worldPosition.offset(
              v % ss - sr, v / (ss * ss) - Balance.HEIGHT.get(), (v / ss) % ss - sr);
      if (!l.hasChunkAt(p)) storageRoundUnloaded = true;
      else if (Storage.isChest(l, p) && Storage.canonical(l, p).equals(p) && !chests.contains(p))
        chests.add(p);
      if (storageCursor >= st) {
        storageCursor = 0;
        storageComplete = true;
        storageUnloaded = storageRoundUnloaded;
        storageRoundUnloaded = false;
        chests.removeIf(
            p2 -> !Storage.inRange(this, p2) || (l.hasChunkAt(p2) && !Storage.isChest(l, p2)));
        chests.sort(
            Comparator.comparingDouble((BlockPos p2) -> p2.distSqr(worldPosition))
                .thenComparingLong(BlockPos::asLong));
        break;
      }
    }
  }

  public int flags(BlockPos p) {
    return chestFlags.getOrDefault(p.asLong(), 3);
  }

  public void toggleChest(int index, int bit) {
    if (index >= 0 && index < chests.size()) {
      BlockPos p = chests.get(index);
      chestFlags.put(p.asLong(), flags(p) ^ bit);
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
    inventory.deserializeNBT(n.getCompound("Inventory"));
    workers.clear();
    for (Tag t : n.getList("Workers", 10)) workers.add(((CompoundTag) t).getUUID("Id"));
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
    return new AABB(worldPosition)
        .expandTowards(
            0, level == null ? 320 : level.getMaxBuildHeight() - worldPosition.getY(), 0);
  }
}
