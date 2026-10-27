package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public final class Ranching {
  private final Lumberton worker;
  private UUID animal;
  private BlockPos sitePosition;
  private BlockPos drops;
  private long collectUntil;
  private long nextAction;

  public Ranching(Lumberton worker) {
    this.worker = worker;
  }

  public CompoundTag save() {
    var n = new CompoundTag();
    if (animal != null) n.putUUID("Animal", animal);
    if (sitePosition != null) n.putLong("Site", sitePosition.asLong());
    if (drops != null) {
      n.putLong("Drops", drops.asLong());
      n.putLong("CollectUntil", collectUntil);
    }
    return n;
  }

  public void load(CompoundTag n) {
    animal = n.hasUUID("Animal") ? n.getUUID("Animal") : null;
    sitePosition = n.contains("Site") ? BlockPos.of(n.getLong("Site")) : null;
    drops = n.contains("Drops") ? BlockPos.of(n.getLong("Drops")) : null;
    collectUntil = n.getLong("CollectUntil");
  }

  public void release() {
    if (animal != null
        && sitePosition != null
        && worker.level() instanceof ServerLevel level
        && level.hasChunkAt(sitePosition)
        && level.getBlockEntity(sitePosition) instanceof Station site
        && Objects.equals(site.owner, worker.owner)
        && animal.equals(site.ranch.handling)) {
      site.ranch.handling = null;
      site.changed();
    }
    if (worker.level() instanceof ServerLevel level
        && animal != null
        && level.getEntity(animal) instanceof Animal target) {
      if (target.getLeashHolder() == worker) target.dropLeash(true, true);
      AnimalTasks.release(target, worker.getUUID());
    }
    animal = null;
    sitePosition = null;
    drops = null;
  }

  /**
   * Tracking can stop while Minecraft iterates its entity list. Keep the real leash and saved
   * route.
   */
  public void unload() {
    if (worker.level() instanceof ServerLevel level
        && animal != null
        && level.getEntity(animal) instanceof Animal target)
      AnimalTasks.release(target, worker.getUUID());
  }

  public static List<Animal> stock(Station site, SiteLayout layout) {
    ServerLevel level = (ServerLevel) site.getLevel();
    List<Animal> result =
        new ArrayList<>(
            level.getEntitiesOfClass(
                Animal.class,
                layout.pen(),
                a ->
                    a.isAlive()
                        && RanchPlan.species(a) >= 0
                        && layout.pen().contains(a.position())));
    for (Animal a : result) AnimalTasks.register(a, site);
    result.removeIf(a -> !AnimalTasks.belongs(a, site));
    if (site.ranch.handling != null
        && level.getEntity(site.ranch.handling) instanceof Animal handled
        && handled.isAlive()
        && AnimalTasks.belongs(handled, site)
        && !result.contains(handled)) result.add(handled);
    return result;
  }

  public static boolean breedPair(
      Lumberton worker, Station site, SiteLayout layout, Animal a, Animal b) {
    int species = RanchPlan.species(a);
    ServerLevel level = (ServerLevel) worker.level();
    if (a == b
        || species < 0
        || RanchPlan.species(b) != species
        || a.getType() != b.getType()
        || !site.ranch.enabled[species]
        || a.getAge() != 0
        || b.getAge() != 0
        || a.isInLove()
        || b.isInLove()
        || !AnimalTasks.belongs(a, site)
        || !AnimalTasks.belongs(b, site)
        || !layout.pen().contains(a.position())
        || !layout.pen().contains(b.position())
        || worker.distanceToSqr(a) > 9
        || worker.distanceToSqr(b) > 9) return false;
    long count = stock(site, layout).stream().filter(v -> RanchPlan.species(v) == species).count();
    if (count + site.ranch.pending(level, species) >= site.ranch.targets[species]
        || worker.mining.carried(RanchPlan.food(species)) < 2
        || !AnimalTasks.available(a, worker)
        || !AnimalTasks.available(b, worker)) return false;
    AnimalTasks.claim(a, worker, "Breed");
    AnimalTasks.claim(b, worker, "Breed");
    BuildingProject.consume(worker, RanchPlan.food(species), 2);
    a.setInLove(null);
    b.setInLove(null);
    site.ranch.expect(a, b);
    site.changed();
    AnimalTasks.release(a, worker.getUUID());
    AnimalTasks.release(b, worker.getUUID());
    return true;
  }

  private int slot(Item item) {
    for (int i = 0; i < worker.cargo.getSlots(); i++)
      if (worker.cargo.getStackInSlot(i).is(item)) return i;
    return -1;
  }

  private void closeGate(SiteLayout layout) {
    if (animal != null
        && worker.level() instanceof ServerLevel level
        && level.getEntity(animal) instanceof Animal target
        && target.getLeashHolder() == worker
        && (layout.pen().contains(target.position())
            || target
                    .position()
                    .distanceToSqr(Vec3.atBottomCenterOf(layout.logical(new BlockPos(3, 0, 9))))
                > 1.6)) return;
    BlockPos gate = layout.feature("pen_gate");
    var state = worker.level().getBlockState(gate);
    if (state.getBlock() instanceof FenceGateBlock
        && state.getValue(FenceGateBlock.OPEN)
        && worker.distanceToSqr(Vec3.atCenterOf(gate)) > 4
        && worker
            .level()
            .getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, new AABB(gate).inflate(.7))
            .isEmpty())
      worker.level().setBlock(gate, state.setValue(FenceGateBlock.OPEN, false), 3);
  }

  private boolean go(SiteLayout layout, BlockPos destination, String status) {
    return go(layout, destination, status, false);
  }

  private boolean go(SiteLayout layout, BlockPos destination, String status, boolean exact) {
    boolean inside = layout.pen().contains(worker.position()),
        destinationInside = layout.pen().contains(Vec3.atBottomCenterOf(destination));
    BlockPos gate = layout.feature("pen_gate");
    var state = worker.level().getBlockState(gate);
    if (inside != destinationInside
        && state.getBlock() instanceof FenceGateBlock
        && !state.getValue(FenceGateBlock.OPEN)) {
      BlockPos approach = layout.logical(new BlockPos(inside ? 7 : 5, 0, 8));
      if (!WorkerMovement.walk(worker, approach, status)) return false;
      if (!worker
          .level()
          .getEntitiesOfClass(
              Animal.class, new AABB(gate).inflate(1.5), a -> !a.getUUID().equals(animal))
          .isEmpty()) {
        worker.state("Animals crowding ranch gate");
        return false;
      }
      worker.level().setBlock(gate, state.setValue(FenceGateBlock.OPEN, true), 3);
    }
    return exact
        ? WorkerMovement.walk(worker, destination, status)
        : worker.travelTo(destination, status, status);
  }

  private boolean outside(SiteLayout layout) {
    if (!layout.pen().contains(worker.position())) return true;
    go(layout, layout.logical(new BlockPos(4, 0, 8)), "Leaving pasture with products", true);
    return false;
  }

  public static boolean milk(Lumberton worker, Station site, Cow cow, Reservoir destination) {
    if (cow.isBaby()
        || !cow.isAlive()
        || !AnimalTasks.belongs(cow, site)
        || !AnimalTasks.canMilk(cow)
        || !ForgeMod.MILK.isPresent()
        || destination.tank.fill(new FluidStack(ForgeMod.MILK.get(), 1000), FluidAction.SIMULATE)
            != 1000
        || !AnimalTasks.available(cow, worker)) return false;
    int bucket = -1;
    for (int i = 0; i < worker.cargo.getSlots(); i++)
      if (worker.cargo.getStackInSlot(i).is(Items.BUCKET)) {
        bucket = i;
        break;
      }
    if (bucket < 0) return false;
    var after = LuminaService.exchanged(worker.cargo, bucket, new ItemStack(Items.MILK_BUCKET));
    if (after == null) return false;
    AnimalTasks.claim(cow, worker, "Milk");
    worker.cargo.deserializeNBT(after.serializeNBT());
    AnimalTasks.milked(cow);
    AnimalTasks.release(cow, worker.getUUID());
    worker.playSound(SoundEvents.COW_MILK, 1, 1);
    return true;
  }

  public void tick(Station site) {
    sitePosition = site.getBlockPos();
    ServerLevel level = (ServerLevel) worker.level();
    if (site.project() == null) return;
    SiteLayout layout = new SiteLayout(site.project(), level);
    closeGate(layout);
    if (level.getGameTime() < nextAction) return;
    List<Animal> stock = stock(site, layout);
    for (var cell : site.project().template(level).cells) {
      if (!(cell.state().getBlock() instanceof FenceBlock)
          && !(cell.state().getBlock() instanceof FenceGateBlock)) continue;
      BlockPos p =
          site.project()
              .template(level)
              .world(site.project().anchor, site.project().front, cell.local());
      if (!level.hasChunkAt(p)) {
        worker.state("Ranch enclosure unloaded");
        return;
      }
      var block = level.getBlockState(p).getBlock();
      if (!(block instanceof FenceBlock) && !(block instanceof FenceGateBlock)) {
        release();
        worker.state("Repair pasture enclosure");
        return;
      }
    }
    if (drops != null) {
      // Real lead and loot entities can bounce beyond passive pickup range.
      // Retain this short collection trip through a chunk unload/reload.
      var items =
          level.getEntitiesOfClass(
              ItemEntity.class, new AABB(drops).inflate(4), worker::canCollectDrop);
      for (ItemEntity item :
          items.stream().sorted(Comparator.comparingDouble(worker::distanceToSqr)).toList()) {
        if (!Storage.accepts(worker.cargo, item.getItem())) break;
        if (worker.distanceToSqr(item) < 4 && worker.hasLineOfSight(item)) worker.pickupDrop(item);
        else go(layout, item.blockPosition(), "Collecting ranch products and lead", true);
        return;
      }
      if (level.getGameTime() < collectUntil && !WorkSupplies.full(worker)) {
        go(layout, drops, "Collecting ranch products and lead", true);
        return;
      }
      drops = null;
    }
    if (site.ranch.handling != null) {
      if (level.getEntity(site.ranch.handling) instanceof Animal target) {
        if (!target.isAlive() || !AnimalTasks.belongs(target, site)) {
          site.ranch.handling = null;
          site.changed();
        } else if (AnimalTasks.claim(target, worker, "Cull")) {
          animal = target.getUUID();
          handle(site, layout, stock, target);
          return;
        }
      } else {
        worker.state("Waiting for animal being handled to load");
        return;
      }
    }
    if (slot(Items.MILK_BUCKET) >= 0) {
      if (!outside(layout)) return;
      BlockPos tankPos = layout.feature("milk_tank");
      if (!(level.getBlockEntity(tankPos) instanceof Reservoir tank)
          || tank.tank.fill(new FluidStack(ForgeMod.MILK.get(), 1000), FluidAction.SIMULATE)
              != 1000) {
        worker.state("Milk tank full or holding another fluid");
        return;
      }
      if (worker.travelTo(tankPos, "Delivering milk", "Delivering milk")) {
        var after =
            LuminaService.exchanged(
                worker.cargo, slot(Items.MILK_BUCKET), new ItemStack(Items.BUCKET));
        if (after != null) {
          tank.tank.fill(new FluidStack(ForgeMod.MILK.get(), 1000), FluidAction.EXECUTE);
          worker.cargo.deserializeNBT(after.serializeNBT());
          tank.synchronize();
        }
      }
      return;
    }
    Map<Item, Integer> keep =
        Map.of(
            Items.BUCKET,
            1,
            Items.LEAD,
            1,
            Items.WHEAT,
            16,
            Items.WHEAT_SEEDS,
            16,
            Items.CARROT,
            16,
            Luminall.BOTTLE.get(),
            1);
    boolean products = false;
    for (int i = 0; i < worker.cargo.getSlots(); i++) {
      var stack = worker.cargo.getStackInSlot(i);
      if (!stack.isEmpty()
          && worker.mining.carried(stack.getItem()) > keep.getOrDefault(stack.getItem(), 0))
        products = true;
    }
    if (products) {
      if (!outside(layout)) return;
      if (WorkSupplies.deposit(worker, site, keep)) return;
      if (WorkSupplies.full(worker)) {
        worker.state("Ranch product storage full");
        return;
      }
    }
    worker.selectTool();
    if (!Inventories.tool(worker.job(), worker.activeTool())) {
      if (!outside(layout)) return;
      if (WorkSupplies.equipment(worker, site, s -> s.is(Items.SHEARS))) return;
    }
    for (Item item :
        List.of(Items.BUCKET, Items.LEAD, Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT)) {
      if (worker.mining.carried(item) >= (item == Items.BUCKET || item == Items.LEAD ? 1 : 2))
        continue;
      if (available(site, item)) {
        if (!outside(layout)) return;
        if (WorkSupplies.take(worker, site, item, keep.get(item))) return;
      }
    }
    for (ItemEntity egg :
        level.getEntitiesOfClass(
            ItemEntity.class,
            layout.pen(),
            e -> e.getItem().is(Items.EGG) && worker.canCollectDrop(e))) {
      if (!Storage.accepts(worker.cargo, egg.getItem())) break;
      if (go(layout, egg.blockPosition(), "Collecting eggs", true) && worker.distanceToSqr(egg) < 4)
        worker.pickupDrop(egg);
      return;
    }
    for (Animal target :
        stock.stream()
            .filter(a -> layout.pen().contains(a.position()) && AnimalTasks.available(a, worker))
            .sorted(Comparator.comparingDouble(worker::distanceToSqr))
            .toList()) {
      if (target instanceof Sheep sheep
          && sheep.readyForShearing()
          && Inventories.tool(worker.job(), worker.activeTool())) {
        Item wool =
            BuiltInRegistries.ITEM.get(
                new net.minecraft.resources.ResourceLocation(sheep.getColor().getName() + "_wool"));
        if (!Inventories.fits(worker.cargo, List.of(new ItemStack(wool, 3)))) continue;
        AnimalTasks.claim(target, worker, "Shear");
        animal = target.getUUID();
        if (go(layout, target.blockPosition(), "Shearing sheep", true)
            && worker.distanceToSqr(target) <= 9) {
          var drops = sheep.onSheared(null, worker.activeTool(), level, sheep.blockPosition(), 0);
          for (ItemStack drop : drops) {
            var rest = Inventories.insert(worker.cargo, drop, false);
            if (!rest.isEmpty()) sheep.spawnAtLocation(rest);
          }
          worker.wear(1);
          worker.swing(InteractionHand.MAIN_HAND);
          release();
          nextAction = level.getGameTime() + 20;
        }
        return;
      }
      if (target instanceof Cow cow
          && !cow.isBaby()
          && AnimalTasks.canMilk(cow)
          && slot(Items.BUCKET) >= 0
          && level.getBlockEntity(layout.feature("milk_tank")) instanceof Reservoir tank
          && tank.tank.fill(new FluidStack(ForgeMod.MILK.get(), 1000), FluidAction.SIMULATE)
              == 1000) {
        AnimalTasks.claim(target, worker, "Milk");
        animal = target.getUUID();
        if (go(layout, target.blockPosition(), "Milking cow", true)
            && worker.distanceToSqr(target) <= 9) {
          milk(worker, site, cow, tank);
          release();
          nextAction = level.getGameTime() + 20;
        }
        return;
      }
    }
    for (int species = 0; species < 4; species++) {
      if (!site.ranch.enabled[species]) continue;
      final int kind = species;
      List<Animal> members = stock.stream().filter(a -> RanchPlan.species(a) == kind).toList();
      long adults = members.stream().filter(a -> !a.isBaby()).count();
      if (site.ranch.handling == null
          && members.size() > site.ranch.targets[species]
          && adults > 2
          && slot(Items.LEAD) >= 0) {
        for (Animal candidate : members) {
          if (candidate.isBaby()
              || candidate.isLeashed()
              || !layout.pen().contains(candidate.position())
              || candidate.getPersistentData().getLong("AutomatonsDispatchBlockedUntil")
                  > level.getGameTime()
              || !AnimalTasks.claim(candidate, worker, "Cull")) continue;
          site.ranch.handling = candidate.getUUID();
          animal = candidate.getUUID();
          site.changed();
          return;
        }
      }
      if (members.size() + site.ranch.pending(level, species) >= site.ranch.targets[species]
          || worker.mining.carried(RanchPlan.food(species)) < 2) continue;
      var parents =
          members.stream()
              .filter(a -> a.getAge() == 0 && !a.isInLove() && AnimalTasks.available(a, worker))
              .toList();
      for (Animal a : parents)
        for (Animal b : parents) {
          if (a == b || a.getType() != b.getType() || a.distanceToSqr(b) > 16) continue;
          BlockPos midpoint = BlockPos.containing(a.position().add(b.position()).scale(.5));
          if (go(layout, midpoint, "Feeding breeding pair", true))
            breedPair(worker, site, layout, a, b);
          return;
        }
    }
    nextAction = level.getGameTime() + 20;
    worker.state(
        stock.isEmpty()
            ? "Bring sheep, cows, chickens or pigs into pasture"
            : "Tending ranch — needs supplies or next production cycle");
  }

  private boolean available(Station site, Item item) {
    for (BlockPos p : site.chests) {
      var stock = Storage.handler((ServerLevel) worker.level(), p, site);
      if (stock == null) continue;
      for (int i = 0; i < stock.getSlots(); i++) if (stock.getStackInSlot(i).is(item)) return true;
    }
    return false;
  }

  private void handle(Station site, SiteLayout layout, List<Animal> stock, Animal target) {
    ServerLevel level = (ServerLevel) worker.level();
    int species = RanchPlan.species(target);
    long count = stock.stream().filter(a -> RanchPlan.species(a) == species).count();
    long adults =
        stock.stream().filter(a -> RanchPlan.species(a) == species && !a.isBaby()).count();
    if (species < 0
        || !site.ranch.enabled[species]
        || target.isBaby()
        || adults <= 2
        || count <= site.ranch.targets[species]) {
      release();
      site.ranch.handling = null;
      site.changed();
      return;
    }
    if (target.getLeashHolder() != worker) {
      if (target.isLeashed()) {
        worker.state("Ranch animal already on another lead");
        return;
      }
      int lead = slot(Items.LEAD);
      if (lead < 0) {
        release();
        site.ranch.handling = null;
        site.changed();
        worker.state("Needs reusable lead");
        return;
      }
      if (!go(layout, target.blockPosition(), "Leading surplus adult to shed", true)
          || worker.distanceToSqr(target) > 9) return;
      worker.cargo.extractItem(lead, 1, false);
      target.setLeashedTo(worker, true);
    }
    // Pull straight through the one-block gate before turning into the handling area.
    // A diagonal pull from the final position can pin a cow against the adjacent fence.
    BlockPos throughGate = layout.logical(new BlockPos(2, 0, 8));
    Vec3 gateAxis =
        Vec3.atBottomCenterOf(layout.feature("pen_gate"))
            .subtract(Vec3.atBottomCenterOf(throughGate))
            .normalize();
    if (layout.pen().contains(target.position())
        || target.position().subtract(Vec3.atBottomCenterOf(throughGate)).dot(gateAxis) > 2.5) {
      if (go(layout, throughGate, "Leading surplus adult through gate", true))
        target
            .getNavigation()
            .moveTo(throughGate.getX() + .5, throughGate.getY(), throughGate.getZ() + .5, 1);
      return;
    }
    BlockPos inside = layout.logical(new BlockPos(3, 0, 9));
    if (!go(layout, inside, "Leading surplus adult to shed", true)) return;
    // The lead and normal leash-follow goal move the animal; no teleport or remote dispatch.
    if (layout.pen().contains(target.position())
        || target.position().distanceToSqr(Vec3.atBottomCenterOf(inside)) > 1.6) {
      if (level.getGameTime() % 20 == 0)
        target.getNavigation().moveTo(inside.getX() + .5, inside.getY(), inside.getZ() + .5, 1);
      return;
    }
    closeGate(layout);
    var gate = level.getBlockState(layout.feature("pen_gate"));
    if (gate.getBlock() instanceof FenceGateBlock && gate.getValue(FenceGateBlock.OPEN)) return;
    var doorPos = layout.feature("door");
    var door = level.getBlockState(doorPos);
    if (door.getBlock() instanceof DoorBlock block
        && door.getValue(DoorBlock.OPEN)
        && level
            .getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, new AABB(doorPos).inflate(.3))
            .isEmpty()) block.setOpen(null, level, door, doorPos, false);
    if (worker.cargo.getSlots()
            - java.util.stream.IntStream.range(0, worker.cargo.getSlots())
                .filter(i -> !worker.cargo.getStackInSlot(i).isEmpty())
                .count()
        < 3) {
      worker.state("Make room for ranch products and reusable lead");
      return;
    }
    target.dropLeash(true, true);
    target.hurt(worker.damageSources().mobAttack(worker), Float.MAX_VALUE);
    if (target.isAlive())
      target
          .getPersistentData()
          .putLong("AutomatonsDispatchBlockedUntil", level.getGameTime() + 200);
    release();
    drops = target.blockPosition();
    collectUntil = level.getGameTime() + 100;
    site.ranch.handling = null;
    site.changed();
    nextAction = level.getGameTime() + 40;
    worker.state(target.isAlive() ? "Ranch dispatch blocked" : "Collecting ranch products");
  }
}
