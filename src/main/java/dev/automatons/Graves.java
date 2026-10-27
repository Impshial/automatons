package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.RegistryObject;

/** A durable recovery journal owns the inventory before any grave blocks are placed. */
public final class Graves extends SavedData {
  public static final RegistryObject<Block> STONE = Automatons.BLOCKS.register("gravestone", () -> new Block(
      Block.Properties.copy(Blocks.STONE).strength(1.5f).noLootTable()) {
    @Override public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
        BlockPos pos, CollisionContext context) { return Block.box(3, 0, 5, 13, 13, 11); }
  });
  public static void register() { Automatons.ITEMS.register("gravestone", () -> new BlockItem(STONE.get(), new Item.Properties())); }
  private final Map<UUID, CompoundTag> pending = new LinkedHashMap<>();
  private final Set<UUID> completed = new HashSet<>();
  private int retryCursor;
  public static Graves get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(Graves::load, Graves::new, "automatons_graves");
  }

  public static int recoveredCount(int percentile) {
    return percentile < 30 ? 3 : percentile < 65 ? 4 : percentile < 95 ? 5 : 6;
  }

  public void capture(Lumberton worker) {
    UUID id = worker.getUUID();
    if (completed.contains(id) || pending.containsKey(id)) return;
    CompoundTag record = new CompoundTag();
    record.putUUID("Worker", id);
    if (worker.owner != null) record.putUUID("Owner", worker.owner);
    record.putString("Name", worker.getDisplayName().getString());
    record.putLong("Death", worker.blockPosition().asLong());
    ListTag stacks = new ListTag();
    for (int i = 0; i < worker.tools.getSlots(); i++) saveStack(stacks, worker.tools.getStackInSlot(i));
    for (int i = 0; i < worker.cargo.getSlots(); i++) saveStack(stacks, worker.cargo.getStackInSlot(i));
    for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
      if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) saveStack(stacks, worker.getItemBySlot(slot));
    boolean armored = worker.isGuard();
    saveStack(stacks, new ItemStack((armored ? Automatons.ARMORED_BODY : Automatons.BODY).get()));
    List<Item> parts = new ArrayList<>(List.of(
        (armored ? Automatons.ARMORED_HEAD : Automatons.HEAD).get(),
        (armored ? Automatons.ARMORED_ARM : Automatons.ARM).get(),
        (armored ? Automatons.ARMORED_ARM : Automatons.ARM).get(),
        (armored ? Automatons.ARMORED_LEG : Automatons.LEG).get(),
        (armored ? Automatons.ARMORED_LEG : Automatons.LEG).get()));
    Collections.shuffle(parts, new Random(worker.getRandom().nextLong()));
    int count = recoveredCount(worker.getRandom().nextInt(100));
    for (int i = 0; i < count - 1; i++) saveStack(stacks, new ItemStack(parts.get(i)));
    record.put("Items", stacks);
    pending.put(id, record);
    setDirty();
    // The journal now owns these exact stacks, including NBT, even if placement is deferred.
    for (int i = 0; i < worker.tools.getSlots(); i++) worker.tools.setStackInSlot(i, ItemStack.EMPTY);
    for (int i = 0; i < worker.cargo.getSlots(); i++) worker.cargo.setStackInSlot(i, ItemStack.EMPTY);
    for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
      if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) worker.setItemSlot(slot, ItemStack.EMPTY);
    place((ServerLevel)worker.level(), id, record);
  }

  private static void saveStack(ListTag stacks, ItemStack stack) {
    if (!stack.isEmpty()) stacks.add(stack.copy().save(new CompoundTag()));
  }

  public int pendingCount() { return pending.size(); }

  private static boolean replaceable(ServerLevel level, BlockPos pos) {
    return level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos)
        && !level.getBlockState(pos).is(Blocks.BEDROCK)
        && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0
        && !level.getBlockState(pos).hasBlockEntity();
  }

  private static boolean valid(ServerLevel level, BlockPos chest) {
    if (!replaceable(level, chest) || !replaceable(level, chest.above())) return false;
    for (Direction side : Direction.Plane.HORIZONTAL)
      if (level.hasChunkAt(chest.relative(side)) && level.getBlockState(chest.relative(side)).is(Blocks.CHEST)) return false;
    return true;
  }

  public void retry(ServerLevel level) {
    var entries = new ArrayList<>(pending.entrySet());
    for (int i = 0; i < Math.min(8, entries.size()); i++) {
      var entry = entries.get(Math.floorMod(retryCursor++, entries.size()));
      place(level, entry.getKey(), entry.getValue());
    }
  }

  private void place(ServerLevel level, UUID id, CompoundTag record) {
    ItemStackHandler inventory = new ItemStackHandler(27);
    for (Tag value : record.getList("Items", 10))
      if (!Inventories.insert(inventory, ItemStack.of((CompoundTag)value), false).isEmpty()) {
        record.putString("Deferred", "Recovery inventory exceeds one chest; all items retained in journal"); setDirty(); return;
      }
    BlockPos death = BlockPos.of(record.getLong("Death"));
    if (!level.hasChunkAt(death)) return;
    BlockPos target = null;
    if (record.contains("Target")) {
      BlockPos previous = BlockPos.of(record.getLong("Target"));
      if (level.getBlockEntity(previous) instanceof ChestBlockEntity existing
          && existing.getPersistentData().hasUUID("AutomatonsGrave")
          && id.equals(existing.getPersistentData().getUUID("AutomatonsGrave"))) {
        // The chest commit won an interrupted save; never replenish a recovered chest.
        completed.add(id); pending.remove(id); setDirty(); return;
      }
    }
    List<BlockPos> candidates = new ArrayList<>(List.of(death, death.below()));
    for (int radius = 1; radius <= 3; radius++)
      for (int y = -1; y <= 1; y++)
        for (int x = -radius; x <= radius; x++)
          for (int z = -radius; z <= radius; z++)
            if (Math.max(Math.abs(x), Math.abs(z)) == radius) candidates.add(death.offset(x, y, z));
    for (BlockPos candidate : candidates) if (valid(level, candidate)) { target = candidate; break; }
    if (target == null) { record.putString("Deferred", "No safe grave position nearby"); setDirty(); return; }
    record.putLong("Target", target.asLong()); setDirty();
    BlockState lower = level.getBlockState(target), upper = level.getBlockState(target.above());
    if (!level.setBlock(target, Blocks.CHEST.defaultBlockState(), 2)) return;
    if (!(level.getBlockEntity(target) instanceof ChestBlockEntity chest)
        || !level.setBlock(target.above(), STONE.get().defaultBlockState(), 2)) {
      level.setBlock(target, lower, 2); level.setBlock(target.above(), upper, 2); return;
    }
    for (int i = 0; i < 27; i++) chest.setItem(i, inventory.getStackInSlot(i).copy());
    chest.setCustomName(Component.literal(record.getString("Name") + "'s belongings"));
    chest.getPersistentData().putUUID("AutomatonsGrave", id);
    chest.setChanged();
    completed.add(id); pending.remove(id); setDirty();
    level.sendBlockUpdated(target, lower, level.getBlockState(target), 3);
    level.sendBlockUpdated(target.above(), upper, STONE.get().defaultBlockState(), 3);
  }

  @Override public CompoundTag save(CompoundTag tag) {
    ListTag records = new ListTag(); pending.values().forEach(p -> records.add(p.copy())); tag.put("Pending", records);
    ListTag done = new ListTag(); completed.forEach(id -> { CompoundTag n = new CompoundTag(); n.putUUID("Worker", id); done.add(n); });
    tag.put("Completed", done); return tag;
  }
  public static Graves load(CompoundTag tag) {
    Graves graves = new Graves();
    for (Tag value : tag.getList("Pending", 10)) { CompoundTag record = (CompoundTag)value; graves.pending.put(record.getUUID("Worker"), record.copy()); }
    for (Tag value : tag.getList("Completed", 10)) graves.completed.add(((CompoundTag)value).getUUID("Worker"));
    return graves;
  }
}
