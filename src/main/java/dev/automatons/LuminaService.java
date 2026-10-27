package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.ItemStackHandler;

/** Real workshop stock, physical collector visits and an independent, finite service reserve. */
public final class LuminaService {
  private final Lumberton worker;
  private long lastHeal = Long.MIN_VALUE, nextTask;
  private UUID lastRecipient;
  private boolean refilling;
  private int plantCursor;
  public LuminaService(Lumberton worker) { this.worker = worker; }
  public CompoundTag save() {
    var n = new CompoundTag(); n.putBoolean("Refilling", refilling); n.putInt("PlantCursor", plantCursor);
    if (lastRecipient != null) n.putUUID("LastRecipient", lastRecipient);
    return n;
  }
  public void load(CompoundTag n) {
    refilling = n.getBoolean("Refilling"); plantCursor = n.getInt("PlantCursor");
    lastRecipient = n.hasUUID("LastRecipient") ? n.getUUID("LastRecipient") : null;
  }
  public static boolean context(Lumberton a, Lumberton b) {
    return a.owner != null && a.homeId != null && Objects.equals(a.owner, b.owner) && Objects.equals(a.homeId, b.homeId);
  }
  public boolean available() {
    return worker.isAlive() && worker.authorized && worker.job() == WorkerJob.LUMINARITON
        && worker.operating() > WorkerEnergy.LOW && !worker.threats.escaping;
  }

  public boolean healNearby() {
    if (!available() || worker.serviceReserve() <= 0) return false;
    ServerLevel level = (ServerLevel)worker.level();
    if (lastHeal != Long.MIN_VALUE && level.getGameTime() - lastHeal < 20) return false;
    lastHeal = level.getGameTime();
    List<Lumberton> recipients = new ArrayList<>(level.getEntitiesOfClass(Lumberton.class, worker.getBoundingBox().inflate(4),
        w -> w.isAlive() && context(worker, w) && worker.distanceToSqr(w) <= 16 && w.getHealth() < w.getMaxHealth()));
    if (worker.getHealth() < worker.getMaxHealth() && !recipients.contains(worker)) recipients.add(worker);
    recipients.sort(Comparator.comparingDouble((Lumberton w) -> w.getHealth() / w.getMaxHealth())
        .thenComparingInt(w -> lastRecipient == null || w.getUUID().compareTo(lastRecipient) > 0 ? 0 : 1)
        .thenComparing(Lumberton::getUUID));
    boolean healed = false;
    for (Lumberton recipient : recipients) {
      int units = Math.min(2000, Math.min(worker.serviceReserve(), Math.round((recipient.getMaxHealth() - recipient.getHealth()) * 2000)));
      if (units <= 0) break;
      float before = recipient.getHealth(), limit = before + units / 2000f;
      recipient.heal(units / 2000f);
      // A mod's heal event can cancel/reduce the request; charge only the actual accepted healing.
      if (recipient.getHealth() > limit) recipient.setHealth(limit);
      int cost = Math.min(units, Math.max(0, Math.round((recipient.getHealth() - before) * 2000)));
      if (cost == 0) continue;
      worker.serviceReserve(worker.serviceReserve() - cost); lastRecipient = recipient.getUUID(); healed = true;
      transfer(level, worker.getEyePosition().add(0, -.35, 0), recipient.position().add(0, recipient.getBbHeight() * .6, 0));
    }
    return healed;
  }
  private static void transfer(ServerLevel level, Vec3 from, Vec3 to) {
    var dust = new DustParticleOptions(new org.joml.Vector3f(.77f, .63f, 1), .7f);
    for (int i = 0; i < 8; i++) {
      Vec3 p = from.lerp(to, i / 8.0);
      level.sendParticles(dust, p.x, p.y, p.z, 1, .03, .03, .03, 0);
    }
  }

  public static boolean plantingStock(ItemStack stack) {
    if (!(stack.getItem() instanceof BlockItem item) || stack.is(ItemTags.SAPLINGS)) return false;
    Block block = item.getBlock(); BlockState state = block.defaultBlockState();
    if (block instanceof SaplingBlock || block instanceof LeavesBlock || DeadVegetation.isDead(state)) return false;
    return List.of("flowers", "grass", "crops", "berries", "stems", "fruit").stream().anyMatch(tag -> LuminaHarvest.tagged(state, tag));
  }

  /** Replace one container only after simulating every output; never spill a remainder. */
  public static ItemStackHandler exchanged(ItemStackHandler cargo, int slot, ItemStack output) {
    ItemStackHandler after = new ItemStackHandler(cargo.getSlots()); after.deserializeNBT(cargo.serializeNBT());
    if (after.extractItem(slot, 1, false).isEmpty() || !Inventories.insert(after, output, false).isEmpty()) return null;
    return after;
  }
  public static boolean bottle(Lumberton worker, Reservoir source, int slot) {
    if (!worker.cargo.getStackInSlot(slot).is(Items.GLASS_BOTTLE) || !source.tank.getFluid().getFluid().isSame(Luminall.SOURCE.get())
        || source.tank.getFluidAmount() < 250) return false;
    ItemStackHandler after = exchanged(worker.cargo, slot, new ItemStack(Luminall.BOTTLE.get()));
    if (after == null) return false;
    source.tank.drain(250, FluidAction.EXECUTE); worker.cargo.deserializeNBT(after.serializeNBT()); source.synchronize();
    return true;
  }
  private int slot(Item item) {
    for (int i = 0; i < worker.cargo.getSlots(); i++) if (worker.cargo.getStackInSlot(i).is(item)) return i;
    return -1;
  }
  private boolean fullBottle() {
    for (int i = 0; i < worker.cargo.getSlots(); i++) if (WorkerEnergy.fullBottle(worker.cargo.getStackInSlot(i))) return true;
    return false;
  }
  private Map<Item, Integer> reserves() {
    Map<Item, Integer> keep = new HashMap<>();
    keep.put(Items.GLASS_BOTTLE, 16); keep.put(Luminall.BOTTLE.get(), refilling ? 3 : 1);
    keep.put(Items.BUCKET, 1); keep.put(Luminall.BUCKET.get(), 1); keep.put(Automatons.LUMITE_SHARD.get(), 8);
    for (int i = 0; i < worker.cargo.getSlots(); i++) {
      var stack = worker.cargo.getStackInSlot(i); if (plantingStock(stack)) keep.put(stack.getItem(), 16);
    }
    return keep;
  }
  public void tick(Station site) {
    ServerLevel level = (ServerLevel)worker.level(); BuildingProject project = site.project();
    if (project == null) { worker.state("Collector layout unavailable"); return; }
    SiteLayout layout = new SiteLayout(project, level);
    boolean healed = healNearby();
    if (worker.serviceReserve() <= 125000) refilling = true;
    if (worker.serviceReserve() >= WorkerEnergy.SERVICE_CAPACITY) refilling = false;
    if (refilling && fullBottle()) {
      if (worker.travelTo(site.getBlockPos(), "Returning to service point", "Filling service reserve")) worker.energy.drink(true);
      return;
    }
    if (worker.serviceReserve() > 0) {
      var visitors = level.getEntitiesOfClass(Lumberton.class, new net.minecraft.world.phys.AABB(layout.feature("healing_position")).inflate(5),
          w -> w.isAlive() && context(worker, w) && w.getHealth() < w.getMaxHealth());
      if (healed || !visitors.isEmpty()) { WorkerMovement.walk(worker, layout.feature("healing_position"), "Tending injured automatons"); return; }
    }
    if (WorkSupplies.deposit(worker, site, reserves())) return;
    if (refilling && takeFullBottle(site)) return;
    if (level.getGameTime() < nextTask) return;
    if (bottling(site, layout)) return;
    if (maintainBeds(site, layout)) return;
    if (transferTanks(site, project, layout)) return;
    if (infuse(site, layout)) return;
    nextTask = level.getGameTime() + 20;
    WorkerMovement.walk(worker, layout.feature("healing_position"), refilling ? "Needs bottles or collector Luminall" : "Watching collector garden");
  }
  private boolean takeFullBottle(Station site) {
    for (BlockPos pos : site.chests) {
      if (ConstructionSupply.reserved((ServerLevel)worker.level(), pos)) continue;
      var stock = Storage.handler((ServerLevel)worker.level(), pos, site); if (stock == null) continue;
      for (int i = 0; i < stock.getSlots(); i++) {
        if (!WorkerEnergy.fullBottle(stock.getStackInSlot(i)) || !Storage.accepts(worker.cargo, stock.getStackInSlot(i)) || !worker.canReach(pos)) continue;
        if (worker.travelTo(pos, "Fetching service Luminall", "Fetching service Luminall")) BuildingProject.move(stock, i, worker.cargo, 1);
        return true;
      }
    }
    return false;
  }
  private boolean bottling(Station site, SiteLayout layout) {
    int bottle = slot(Items.GLASS_BOTTLE);
    if (bottle < 0) return WorkSupplies.take(worker, site, Items.GLASS_BOTTLE, 16);
    if (exchanged(worker.cargo, bottle, new ItemStack(Luminall.BOTTLE.get())) == null) return false;
    for (BlockPos pos : layout.collectors()) {
      if (!worker.level().hasChunkAt(pos) || !(worker.level().getBlockEntity(pos) instanceof Reservoir source)
          || source.tank.getFluidAmount() < 250 || !worker.canReach(pos)) continue;
      if (worker.travelTo(pos, "Bottling collector Luminall", "Bottling collector Luminall")) {
        bottle(worker, source, bottle); nextTask = worker.level().getGameTime() + 20;
      }
      return true;
    }
    return false;
  }
  public static boolean plant(Lumberton worker, SiteLayout layout, BlockPos pos, int slot) {
    var level = (ServerLevel)worker.level(); var stack = worker.cargo.getStackInSlot(slot);
    if (!plantingStock(stack) || !layout.plantingCells().contains(pos) || !level.isEmptyBlock(pos)
        || WorldData.get(level).protectedAt(pos)) return false;
    BlockState plant = ((BlockItem)stack.getItem()).getBlock().defaultBlockState();
    if (!plant.canSurvive(level, pos)) return false;
    boolean tall = plant.getBlock() instanceof DoublePlantBlock;
    if (tall && (!level.isEmptyBlock(pos.above()) || !level.isUnobstructed(plant, pos.above(), net.minecraft.world.phys.shapes.CollisionContext.empty()))) return false;
    if (tall) plant = plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
    if (!MiningRules.place(worker, pos, plant)) return false;
    if (tall && !MiningRules.place(worker, pos.above(), plant.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER))) {
      level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); return false;
    }
    worker.cargo.extractItem(slot, 1, false); SourceReserves.get(level).forget(pos); return true;
  }
  private boolean maintainBeds(Station site, SiteLayout layout) {
    var level = (ServerLevel)worker.level(); List<BlockPos> cells = layout.plantingCells();
    if (cells.isEmpty()) return false;
    for (int i = 0; i < cells.size(); i++) {
      int index = Math.floorMod(plantCursor + i, cells.size()); BlockPos pos = cells.get(index);
      if (!level.hasChunkAt(pos) || WorldData.get(level).protectedAt(pos)) continue;
      var state = level.getBlockState(pos);
      if (!state.isAir() && !DeadVegetation.isDead(state)) continue;
      if (DeadVegetation.isDead(state)) {
        if (!worker.canReach(pos)) continue;
        if (worker.travelTo(pos, "Clearing exhausted plant", "Clearing exhausted plant")) { DeadVegetation.clear(worker, pos); plantCursor = index; }
        return true;
      }
      for (int slot = 0; slot < worker.cargo.getSlots(); slot++) {
        var stack = worker.cargo.getStackInSlot(slot);
        if (!plantingStock(stack) || !((BlockItem)stack.getItem()).getBlock().defaultBlockState().canSurvive(level, pos) || !worker.canReach(pos)) continue;
        if (worker.travelTo(pos, "Replanting collector bed", "Replanting collector bed")) { plant(worker, layout, pos, slot); plantCursor = (index + 1) % cells.size(); nextTask = level.getGameTime() + 10; }
        return true;
      }
      for (BlockPos chest : site.chests) {
        var stock = Storage.handler(level, chest, site); if (stock == null || ConstructionSupply.reserved(level, chest)) continue;
        for (int slot = 0; slot < stock.getSlots(); slot++) {
          var stack = stock.getStackInSlot(slot);
          if (!plantingStock(stack) || !((BlockItem)stack.getItem()).getBlock().defaultBlockState().canSurvive(level, pos)
              || !Storage.accepts(worker.cargo, stack) || !worker.canReach(chest)) continue;
          if (worker.travelTo(chest, "Fetching living plants", "Fetching living plants")) BuildingProject.move(stock, slot, worker.cargo, 16);
          return true;
        }
      }
    }
    return false;
  }
  private boolean transferTanks(Station site, BuildingProject project, SiteLayout layout) {
    ServerLevel level = (ServerLevel)worker.level();
    List<BlockPos> tanks = project.template(level).cells.stream().filter(c -> c.state().is(Luminall.TANK.get()))
        .map(c -> project.template(level).world(project.anchor, project.front, c.local())).toList();
    BlockPos destination = tanks.stream().filter(p -> level.hasChunkAt(p) && level.getBlockEntity(p) instanceof Reservoir tank
        && tank.tank.fill(new FluidStack(Luminall.SOURCE.get(), 1000), FluidAction.SIMULATE) == 1000).findFirst().orElse(null);
    if (destination == null) return false;
    int filled = slot(Luminall.BUCKET.get());
    if (filled >= 0) {
      if (worker.travelTo(destination, "Filling workshop tank", "Filling workshop tank")) {
        var after = exchanged(worker.cargo, filled, new ItemStack(Items.BUCKET)); if (after == null) return false;
        Reservoir tank = (Reservoir)level.getBlockEntity(destination);
        tank.tank.fill(new FluidStack(Luminall.SOURCE.get(), 1000), FluidAction.EXECUTE); worker.cargo.deserializeNBT(after.serializeNBT()); tank.synchronize();
      }
      return true;
    }
    int empty = slot(Items.BUCKET);
    if (empty < 0) return WorkSupplies.take(worker, site, Items.BUCKET, 1);
    for (BlockPos pos : layout.collectors()) {
      if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Reservoir source) || source.tank.getFluidAmount() < 1000) continue;
      var after = exchanged(worker.cargo, empty, new ItemStack(Luminall.BUCKET.get())); if (after == null) return false;
      if (worker.travelTo(pos, "Collecting Luminall for tank", "Collecting Luminall for tank")) {
        source.tank.drain(1000, FluidAction.EXECUTE); worker.cargo.deserializeNBT(after.serializeNBT()); source.synchronize();
      }
      return true;
    }
    return false;
  }
  private boolean infuse(Station site, SiteLayout layout) {
    ServerLevel level = (ServerLevel)worker.level(); BlockPos pos = layout.feature("infuser");
    if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof Infuser infuser)) return false;
    for (int i = 0; i < 4; i++) {
      boolean own = infuser.donors[i] != null && (infuser.donors[i].equals(worker.getUUID()) || sameDonor(infuser.donors[i]));
      if (own && infuser.shards[i].is(Automatons.INFUSED_LUMITE.get())) {
        if (!Inventories.fits(worker.cargo, List.of(infuser.shards[i]))) return false;
        if (worker.travelTo(pos, "Collecting infused shard", "Collecting infused shard")) {
          Inventories.insert(worker.cargo, infuser.shards[i], false); infuser.shards[i] = ItemStack.EMPTY; infuser.donors[i] = null; infuser.changed();
        }
        return true;
      }
      if (own && infuser.shards[i].is(Automatons.LUMITE_SHARD.get()) && worker.serviceReserve() >= 1000) {
        if (worker.travelTo(pos, "Infusing Lumite", "Infusing Lumite") && level.getGameTime() - infuser.lastTransfer[i] >= Balance.INFUSE_INTERVAL.get()) {
          int thirds = Math.min(3, LumiteShardItem.CAPACITY - LumiteShardItem.paid(infuser.shards[i]));
          worker.serviceReserve(worker.serviceReserve() - (thirds * 1000 + 2) / 3);
          infuser.shards[i] = LumiteShardItem.charge(infuser.shards[i], thirds); infuser.lastTransfer[i] = level.getGameTime();
          infuser.donors[i] = worker.getUUID(); infuser.transferring[i] = true; infuser.changed();
          transfer(level, worker.getEyePosition(), infuser.shardPosition(i));
        }
        return true;
      }
    }
    if (worker.serviceReserve() < 25000) return false;
    int raw = slot(Automatons.LUMITE_SHARD.get());
    if (raw < 0) return WorkSupplies.take(worker, site, Automatons.LUMITE_SHARD.get(), 8);
    for (int i = 0; i < 4; i++) if (infuser.shards[i].isEmpty()) {
      if (worker.travelTo(pos, "Placing raw Lumite", "Placing raw Lumite")) {
        infuser.shards[i] = worker.cargo.extractItem(raw, 1, false); infuser.donors[i] = worker.getUUID(); infuser.changed();
      }
      return true;
    }
    return false;
  }
  private boolean sameDonor(UUID id) {
    var record = Ledger.get(worker.getServer()).workers.get(id);
    return record != null && record.hasUUID("Owner") && record.getUUID("Owner").equals(worker.owner)
        && record.hasUUID("HomeId") && record.getUUID("HomeId").equals(worker.homeId);
  }
}
