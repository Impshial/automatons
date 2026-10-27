package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_expansion")
@PrefixGameTestTemplate(false)
public final class ExpansionGameTests {
  @GameTest(template = "empty", timeoutTicks = 40)
  public static void ranch_breeding_reserves_offspring_and_consumes_actual_food(GameTestHelper h) {
    var level = h.getLevel(); BlockPos anchor = h.absolutePos(new BlockPos(3, 3, 3));
    level.setBlock(anchor, Automatons.TOTEM.get().defaultBlockState(), 3); Station site = (Station)level.getBlockEntity(anchor);
    site.owner = UUID.randomUUID(); site.purpose = WorksitePurpose.RANCH; site.ranch.targets[1] = 5;
    BuildingProject project = new BuildingProject(); project.purpose = WorksitePurpose.RANCH; project.anchor = anchor; project.front = Direction.SOUTH;
    SiteLayout layout = new SiteLayout(project, level); Vec3 point = new Vec3(layout.pen().minX + 2, layout.pen().minY, layout.pen().minZ + 2);
    Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.owner = site.owner; worker.setJob(WorkerJob.RANCHERTON); worker.moveTo(point);
    worker.cargo.setStackInSlot(0, new ItemStack(Items.WHEAT, 4));
    List<net.minecraft.world.entity.animal.Cow> cows = new ArrayList<>();
    for (int i = 0; i < 4; i++) { var cow = net.minecraft.world.entity.EntityType.COW.create(level); cow.setNoAi(true); cow.moveTo(point.add(i * .2, 0, 0)); level.addFreshEntity(cow); cows.add(cow); }
    h.assertTrue(Ranching.stock(site, layout).size() == 4 && AnimalTasks.belongs(cows.get(0), site), "Pasture stock failed to register");
    h.assertTrue(Ranching.breedPair(worker, site, layout, cows.get(0), cows.get(1)) && worker.mining.carried(Items.WHEAT) == 2, "Breeding did not consume two real food items");
    h.assertTrue(!Ranching.breedPair(worker, site, layout, cows.get(2), cows.get(3)) && worker.mining.carried(Items.WHEAT) == 2, "Second rancher could exceed target using reserved offspring");
    RanchPlan restored = new RanchPlan(); restored.load(site.ranch.save());
    h.assertTrue(restored.pending(level, 1) == 1 && restored.targets[1] == 5, "Birth reservation or target lost on reload");
    cows.get(0).setPos(point.x + 40, point.y, point.z);
    h.assertTrue(AnimalTasks.managed(cows.get(0)) && !Hunting.eligible(cows.get(0), site), "Escaped ranch cow lost protection");
    for (var cow : cows) cow.discard(); h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void ranch_milk_uses_registered_fluid_bucket_and_persistent_elapsed_cooldown(GameTestHelper h) {
    var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(6, 2, 6));
    level.setBlock(pos, Automatons.TOTEM.get().defaultBlockState(), 3); Station site = (Station)level.getBlockEntity(pos); site.owner = UUID.randomUUID();
    level.setBlock(pos.east(2), Luminall.TANK.get().defaultBlockState(), 3); Reservoir tank = (Reservoir)level.getBlockEntity(pos.east(2));
    Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.owner = site.owner; worker.setJob(WorkerJob.RANCHERTON);
    worker.cargo.setStackInSlot(0, new ItemStack(Items.BUCKET));
    var cow = net.minecraft.world.entity.EntityType.COW.create(level); AnimalTasks.register(cow, site);
    h.assertTrue(net.minecraftforge.common.ForgeMod.MILK.isPresent(), "Compatible milk fluid not registered");
    h.assertTrue(Ranching.milk(worker, site, cow, tank) && worker.mining.carried(Items.MILK_BUCKET) == 1
        && worker.mining.carried(Items.BUCKET) == 0 && tank.tank.getFluidAmount() == 0, "Milk was created without a real carried bucket or teleported to tank");
    worker.cargo.setStackInSlot(1, new ItemStack(Items.BUCKET));
    h.assertTrue(!Ranching.milk(worker, site, cow, tank), "Cow milked twice within cooldown");
    var loaded = net.minecraft.world.entity.EntityType.COW.create(level); loaded.load(cow.saveWithoutId(new CompoundTag()));
    h.assertTrue(!AnimalTasks.canMilk(loaded), "Reload replenished cow milk");
    tank.tank.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    var fresh = net.minecraft.world.entity.EntityType.COW.create(level); AnimalTasks.register(fresh, site);
    h.assertTrue(!Ranching.milk(worker, site, fresh, tank) && AnimalTasks.canMilk(fresh), "Incompatible tank consumed cow production");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void healing_charges_actual_health_and_keeps_operating_energy_separate(GameTestHelper h) {
    var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
    Lumberton mage = Automatons.LUMBERTON.get().create(level), a = Automatons.LUMBERTON.get().create(level), b = Automatons.LUMBERTON.get().create(level), outsider = Automatons.LUMBERTON.get().create(level);
    UUID owner = UUID.randomUUID(), home = UUID.randomUUID();
    for (Lumberton w : List.of(mage, a, b, outsider)) {
      w.owner = owner; w.homeId = home; w.setNoAi(true); w.authorized = true;
      w.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5); level.addFreshEntity(w);
    }
    a.setPos(a.getX() + 1, a.getY(), a.getZ()); b.setPos(b.getX() + 2, b.getY(), b.getZ());
    mage.setJob(WorkerJob.LUMINARITON); mage.serviceReserve(2500); mage.operating(150000);
    a.setHealth(1); b.setHealth(b.getMaxHealth() - .25f); outsider.owner = UUID.randomUUID(); outsider.setHealth(1);
    h.assertTrue(mage.luminaService.healNearby(), "Funded mage failed to heal");
    h.assertTrue(a.getHealth() == 2 && b.getHealth() == b.getMaxHealth() && outsider.getHealth() == 1, "Healing priority, fraction, or ownership wrong");
    h.assertTrue(mage.serviceReserve() == 0 && mage.operating() == 150000, "Healing overcharged or used operating pool");
    mage.serviceReserve(5000); h.assertTrue(!mage.luminaService.healNearby() && a.getHealth() == 2, "Healing exceeded one pulse per second");
    mage.operating(0); h.assertTrue(!mage.luminaService.healNearby(), "Powered-down mage healed");
    for (Lumberton w : List.of(mage, a, b, outsider)) w.discard(); h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void service_bottling_commits_exact_fluid_and_requires_output_space(GameTestHelper h) {
    var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(7, 2, 7));
    level.setBlock(pos, Luminall.COLLECTOR.get().defaultBlockState(), 3);
    Reservoir source = (Reservoir)level.getBlockEntity(pos);
    source.tank.fill(new net.minecraftforge.fluids.FluidStack(Luminall.SOURCE.get(), 300), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.setJob(WorkerJob.LUMINARITON);
    for (int i = 0; i < worker.cargo.getSlots(); i++) worker.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
    worker.cargo.setStackInSlot(0, new ItemStack(Items.GLASS_BOTTLE, 64));
    h.assertTrue(!LuminaService.bottle(worker, source, 0) && source.tank.getFluidAmount() == 300
        && worker.mining.carried(Items.GLASS_BOTTLE) == 64, "Blocked bottling changed stock");
    worker.cargo.setStackInSlot(1, ItemStack.EMPTY);
    h.assertTrue(LuminaService.bottle(worker, source, 0) && source.tank.getFluidAmount() == 50
        && worker.mining.carried(Items.GLASS_BOTTLE) == 63 && worker.mining.carried(Luminall.BOTTLE.get()) == 1, "Bottling violated 250mB conversion");
    h.assertTrue(!LuminaService.bottle(worker, source, 0) && source.tank.getFluidAmount() == 50, "Bottling overdrew source");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void mage_replants_real_stock_only_in_suitable_registered_cells(GameTestHelper h) {
    var level = h.getLevel(); BuildingProject project = new BuildingProject(); project.purpose = WorksitePurpose.LUMINA;
    project.anchor = h.absolutePos(new BlockPos(12, 3, 8)); project.front = Direction.SOUTH;
    SiteLayout layout = new SiteLayout(project, level); BlockPos pos = layout.plantingCells().get(0);
    level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
    Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.owner = UUID.randomUUID(); worker.setJob(WorkerJob.LUMINARITON);
    worker.cargo.setStackInSlot(0, new ItemStack(Items.OAK_SAPLING));
    h.assertTrue(!LuminaService.plant(worker, layout, pos, 0), "Mage planted a tree");
    worker.cargo.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS));
    h.assertTrue(!LuminaService.plant(worker, layout, pos, 0), "Mage planted crop on unsuitable dirt");
    worker.cargo.setStackInSlot(0, new ItemStack(Items.SUNFLOWER, 2));
    level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
    h.assertTrue(!LuminaService.plant(worker, layout, pos, 0) && worker.mining.carried(Items.SUNFLOWER) == 2, "Tall placement consumed stock without clearance");
    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
    h.assertTrue(LuminaService.plant(worker, layout, pos, 0) && level.getBlockState(pos.above()).is(Blocks.SUNFLOWER)
        && worker.mining.carried(Items.SUNFLOWER) == 1, "Tall planting lost upper half or charged twice");
    h.assertTrue(!LuminaService.plant(worker, layout, project.anchor, 0), "Mage planted outside registered bed");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void hunting_excludes_protected_animals_and_claims_are_exclusive(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos pos = h.absolutePos(new BlockPos(8, 2, 8));
    level.setBlock(pos, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station site = (Station)level.getBlockEntity(pos); site.owner = UUID.randomUUID();
    var cow = net.minecraft.world.entity.EntityType.COW.create(level); cow.moveTo(pos.getX(), pos.getY(), pos.getZ());
    h.assertTrue(Hunting.eligible(cow, site), "Adult passive cow was excluded");
    cow.setAge(-24000); h.assertTrue(!Hunting.eligible(cow, site), "Baby accepted as prey"); cow.setAge(0);
    cow.setPos(pos.getX() + 97, pos.getY(), pos.getZ()); h.assertTrue(!Hunting.eligible(cow, site), "Hunt exceeded 96-block totem radius"); cow.setPos(pos.getX(), pos.getY(), pos.getZ());
    AnimalTasks.register(cow, site);
    var restored = net.minecraft.world.entity.EntityType.COW.create(level); restored.load(cow.saveWithoutId(new CompoundTag()));
    h.assertTrue(AnimalTasks.belongs(restored, site) && !Hunting.eligible(restored, site), "Livestock protection lost on reload");
    for (var type : List.of(net.minecraft.world.entity.EntityType.HORSE, net.minecraft.world.entity.EntityType.DONKEY,
        net.minecraft.world.entity.EntityType.CAT, net.minecraft.world.entity.EntityType.WOLF)) {
      var animal = (net.minecraft.world.entity.animal.Animal)type.create(level); animal.moveTo(pos.getX(), pos.getY(), pos.getZ());
      h.assertTrue(!Hunting.eligible(animal, site), "Excluded animal became prey: " + type);
    }
    Lumberton a = Automatons.LUMBERTON.get().create(level), b = Automatons.LUMBERTON.get().create(level);
    a.setNoAi(true); b.setNoAi(true); a.moveTo(pos.getX(), pos.getY(), pos.getZ()); b.moveTo(pos.getX(), pos.getY(), pos.getZ());
    level.addFreshEntity(a); level.addFreshEntity(b);
    h.assertTrue(AnimalTasks.claim(cow, a, "Milk") && !AnimalTasks.claim(cow, b, "Milk"), "Two workers acquired same animal");
    AnimalTasks.release(cow, a.getUUID()); h.assertTrue(AnimalTasks.claim(cow, b, "Milk"), "Released task remained unavailable");
    AnimalTasks.milked(cow); var saved = cow.saveWithoutId(new CompoundTag()); restored.load(saved);
    h.assertTrue(!AnimalTasks.canMilk(restored), "Reload replenished milk cooldown");
    a.discard(); b.discard(); h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void flowers_require_storage_and_preserve_registered_beds(GameTestHelper h) {
    var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(5, 2, 5));
    level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 3); level.setBlock(pos, Blocks.DANDELION.defaultBlockState(), 3);
    Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.owner = UUID.randomUUID();
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
    for (int i = 0; i < worker.cargo.getSlots(); i++) worker.cargo.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
    h.assertTrue(!OpportunisticFlowers.collectNearby(worker) && level.getBlockState(pos).is(Blocks.DANDELION), "Full inventory destroyed a flower");
    worker.cargo.setStackInSlot(0, ItemStack.EMPTY);
    h.assertTrue(OpportunisticFlowers.collectNearby(worker) && level.isEmptyBlock(pos)
        && worker.mining.carried(Items.DANDELION) == 1, "Reachable flower was not collected exactly once");
    BuildingProject project = new BuildingProject(); project.purpose = WorksitePurpose.LUMINA;
    project.anchor = h.absolutePos(new BlockPos(10, 3, 10)); project.front = Direction.SOUTH; project.approved = true;
    UUID id = UUID.randomUUID(); WorldData.get(level).buildings.put(id, project);
    BlockPos bed = new SiteLayout(project, level).plantingCells().get(0);
    level.setBlock(bed.below(), Blocks.DIRT.defaultBlockState(), 3); level.setBlock(bed, Blocks.POPPY.defaultBlockState(), 3);
    worker.moveTo(bed.getX() + .5, bed.getY(), bed.getZ() + 1.5);
    h.assertTrue(!OpportunisticFlowers.collectNearby(worker) && level.getBlockState(bed).is(Blocks.POPPY), "Opportunistic collection harvested protected bed");
    level.setBlock(bed, Blocks.AIR.defaultBlockState(), 3);
    h.assertTrue(SiteLayout.protectedBed(level, bed), "Empty bed lost persistent protection");
    WorldData.get(level).buildings.remove(id); h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void production_limit_excludes_guard_and_preserves_reassigned_worker(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos local = new BlockPos(8, 2, 8); h.setBlock(local, Automatons.TOTEM.get());
    Station site = (Station)level.getBlockEntity(h.absolutePos(local));
    site.owner = UUID.randomUUID(); site.logisticsHome = UUID.randomUUID();
    Lumberton first = Automatons.LUMBERTON.get().create(level);
    first.owner = site.owner; first.homeId = site.logisticsHome;
    first.assign(site);
    h.assertTrue(first.totemId == null, "Unconfigured totem accepted worker");
    site.purpose = WorksitePurpose.FARM;
    first.operating(87000); first.serviceReserve(72000); first.setHealth(13);
    ItemStack axe = new ItemStack(Items.DIAMOND_AXE); axe.setDamageValue(79); first.tools.setStackInSlot(0, axe.copy());
    first.assign(site);
    h.assertTrue(first.job() == WorkerJob.FARMINGTON && first.totemId.equals(site.identity), "Unbuilt configured site failed to assign profession");
    h.assertTrue(first.operating() == 87000 && first.serviceReserve() == 72000 && first.getHealth() == 13, "Reassignment replenished energy or health");
    h.assertTrue(first.tools.getStackInSlot(0).isEmpty() && ItemStack.isSameItemSameTags(first.cargo.getStackInSlot(0), axe), "Incompatible tool lost during assignment");
    for (int i = 1; i < 6; i++) {
      Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.owner = site.owner; worker.homeId = site.logisticsHome; worker.assign(site);
    }
    Lumberton seventh = Automatons.LUMBERTON.get().create(level); seventh.owner = site.owner; seventh.homeId = site.logisticsHome; seventh.assign(site);
    h.assertTrue(seventh.totemId == null && Assignments.productionCount(site) == 6, "Totem exceeded six production workers");
    Lumberton guard = Automatons.GUARDINGTON.get().create(level); guard.owner = site.owner; guard.homeId = site.logisticsHome;
    h.assertTrue(guard.assign(site).equals("Protection assigned") && site.guards.contains(guard.getUUID()) && !site.workers.contains(guard.getUUID()), "Protection consumed a production assignment");
    h.assertTrue(Assignments.productionCount(site) == 6 && guard.job() == WorkerJob.GUARDINGTON && guard.totemId == null, "Guard changed production profession");
    h.assertTrue(!guard.setJob(WorkerJob.LUMBERTON) && !first.setJob(WorkerJob.GUARDINGTON), "Assignment changed body type");
    CompoundTag saved = guard.guarding.save();
    guard.guarding.load(saved);
    h.assertTrue(guard.guarding.assigned() == site && !guard.guarding.circuit, "Protection assignment lost on reload");
    guard.unassign(); h.assertTrue(site.guards.isEmpty(), "Guard unassignment left stale roster entry");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 80)
  public static void zombie_targets_worker_but_creeper_and_drowned_do_not(GameTestHelper h) {
    var level = h.getLevel();
    for (int x = 2; x <= 18; x++) for (int z = 2; z <= 18; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    Lumberton worker = Automatons.LUMBERTON.get().create(level); worker.setNoAi(true);
    worker.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 2, 8)))); level.addFreshEntity(worker);
    var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
    zombie.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(14, 2, 8)))); level.addFreshEntity(zombie);
    var creeper = net.minecraft.world.entity.EntityType.CREEPER.create(level);
    creeper.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8, 2, 13)))); level.addFreshEntity(creeper);
    var drowned = net.minecraft.world.entity.EntityType.DROWNED.create(level);
    drowned.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 8)))); level.addFreshEntity(drowned);
    h.runAfterDelay(30, () -> {
      h.assertTrue(zombie.getTarget() == worker, "Zombie did not acquire automaton target");
      h.assertTrue(creeper.getTarget() != worker && drowned.getTarget() != worker, "Unspecified hostile acquired automaton target");
      zombie.discard(); creeper.discard(); drowned.discard(); worker.discard(); h.succeed();
    });
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exhaustion_clears_tall_sources_without_healthy_drops(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos base = h.absolutePos(new BlockPos(6, 3, 6));
    level.setBlock(base.below(), Blocks.SAND.defaultBlockState(), 2);
    for (int y = 0; y < 3; y++) level.setBlock(base.above(y), Blocks.CACTUS.defaultBlockState(), 2);
    var reserves = SourceReserves.get(level);
    var cactus = Blocks.CACTUS.defaultBlockState();
    reserves.available(base, cactus, "stems", 1);
    reserves.debit(level, base, cactus, "stems", Balance.PLANT_RESERVE.get() * 2);
    h.assertTrue(level.getBlockState(base).is(DeadVegetation.PLANT.get())
        && level.isEmptyBlock(base.above()) && level.isEmptyBlock(base.above(2)), "Exhausted cactus left unsupported living cells");
    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(2, 4, 2)).isEmpty(), "Exhaustion produced healthy cactus drops");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void supplied_collector_beds_rotate_with_world_aligned_coverage(GameTestHelper h) {
    for (Direction front : Direction.Plane.HORIZONTAL) {
      BuildingProject project = new BuildingProject();
      project.purpose = WorksitePurpose.LUMINA;
      project.anchor = h.absolutePos(new BlockPos(12, 3, 12));
      project.front = front;
      var layout = new SiteLayout(project, h.getLevel());
      h.assertTrue(layout.beds().size() == 12 && layout.plantingCells().size() == 180, "Mage beds lost planting cells");
      var template = project.template(h.getLevel());
      Set<BlockPos> actual = new HashSet<>();
      for (var cell : template.cells)
        if (cell.state().is(Luminall.COLLECTOR.get())) actual.add(template.world(project.anchor, front, cell.local()));
      h.assertTrue(actual.equals(new HashSet<>(layout.collectors())), "Collector rotation no longer covers its authored bed");
      for (BlockPos cell : layout.plantingCells())
        h.assertTrue(actual.stream().filter(c -> LuminaCollection.includes(c, cell, 4)).count() == 1, "Planted bed cell has missing or overlapping collector coverage");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void actual_death_keeps_exact_items_and_only_one_grave(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos pos = h.absolutePos(new BlockPos(8, 3, 8));
    Lumberton worker = Automatons.LUMBERTON.get().create(level);
    worker.owner = UUID.randomUUID(); worker.setNoAi(true);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    ItemStack cargo = new ItemStack(Items.DIAMOND, 13);
    cargo.setHoverName(net.minecraft.network.chat.Component.literal("Exact recovery"));
    worker.cargo.setStackInSlot(0, cargo.copy());
    ItemStack tool = new ItemStack(Items.DIAMOND_AXE); tool.setDamageValue(57);
    worker.tools.setStackInSlot(0, tool.copy());
    level.addFreshEntity(worker);
    worker.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
    h.assertTrue(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "Death did not create ordinary chest at death position");
    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
    h.assertTrue(chest.getContainerSize() == 27 && level.getBlockState(pos.above()).is(Graves.STONE.get()), "Grave shape or capacity incorrect");
    int diamonds = 0, axes = 0, torso = 0, head = 0, arms = 0, legs = 0;
    for (int i = 0; i < 27; i++) {
      ItemStack stack = chest.getItem(i);
      if (stack.is(Items.DIAMOND)) { diamonds += stack.getCount(); h.assertTrue(ItemStack.isSameItemSameTags(stack, cargo), "Cargo NBT lost"); }
      if (stack.is(Items.DIAMOND_AXE)) { axes++; h.assertTrue(ItemStack.isSameItemSameTags(stack, tool), "Tool durability lost"); }
      if (stack.is(Automatons.BODY.get())) torso += stack.getCount();
      if (stack.is(Automatons.HEAD.get())) head += stack.getCount();
      if (stack.is(Automatons.ARM.get())) arms += stack.getCount();
      if (stack.is(Automatons.LEG.get())) legs += stack.getCount();
    }
    h.assertTrue(diamonds == 13 && axes == 1 && torso == 1 && head <= 1 && arms <= 2 && legs <= 2
        && torso + head + arms + legs >= 3 && torso + head + arms + legs <= 6, "Recovery lost inventory or exceeded body part counts");
    CompoundTag before = chest.saveWithFullMetadata();
    worker.dropCustomDeathLoot(level.damageSources().genericKill(), 0, false);
    Graves.get(level).retry(level);
    h.assertTrue(before.equals(chest.saveWithFullMetadata()), "Repeated death callback duplicated recovery");
    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty(), "Death scattered duplicate loot");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void blocked_grave_retains_items_across_reload_and_lava_keeps_location(GameTestHelper h) {
    var level = h.getLevel(); BlockPos pos = h.absolutePos(new BlockPos(9, 4, 9));
    for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) level.setBlock(p, Blocks.BEDROCK.defaultBlockState(), 2);
    Lumberton worker = Automatons.GUARDINGTON.get().create(level);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    worker.cargo.setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
    var journal = new Graves(); journal.capture(worker);
    h.assertTrue(journal.pendingCount() == 1 && worker.cargoEmpty(), "Blocked recovery was lost instead of journaled");
    h.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK), "Recovery destroyed bedrock");
    var restored = Graves.load(journal.save(new CompoundTag()));
    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 2);
    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
    restored.retry(level);
    h.assertTrue(restored.pendingCount() == 0 && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "Lava recovery moved away from death position");
    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
    int emeralds = 0, torso = 0;
    for (int i = 0; i < 27; i++) {
      if (chest.getItem(i).is(Items.EMERALD)) emeralds += chest.getItem(i).getCount();
      if (chest.getItem(i).is(Automatons.ARMORED_BODY.get())) torso += chest.getItem(i).getCount();
      h.assertTrue(!chest.getItem(i).is(Automatons.BODY.get()), "Guard recovered ordinary torso");
    }
    h.assertTrue(emeralds == 7 && torso == 1, "Pending grave lost cargo or armored torso");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void loaded_energy_clock_and_whole_bottle_preserve_health(GameTestHelper h) {
    Lumberton worker = Automatons.LUMBERTON.get().create(h.getLevel());
    for (int i = 0; i < 1199; i++) worker.energy.tick();
    Lumberton restored = Automatons.LUMBERTON.get().create(h.getLevel());
    restored.energy.load(worker.energy.save()); restored.energy.tick();
    h.assertTrue(restored.operating() == 199000, "Partial energy interval reset on reload");
    restored.operating(1000); restored.setHealth(7);
    restored.cargo.setStackInSlot(0, LuminallBottle.create(249));
    h.assertTrue(!restored.energy.drink(false), "Worker consumed a partial bottle");
    restored.cargo.setStackInSlot(0, LuminallBottle.create(250));
    for (int i = 0; i < 32; i++) restored.energy.drink(false);
    h.assertTrue(restored.operating() == 126000 && restored.getHealth() == 7
        && restored.cargo.getStackInSlot(0).is(Items.GLASS_BOTTLE), "Bottle did not return glass with exactly 125 Lumina and no healing");
    restored.operating(190000); restored.cargo.setStackInSlot(0, LuminallBottle.create(250));
    for (int i = 0; i < 32; i++) restored.energy.drink(false);
    h.assertTrue(restored.operating() == 200000 && restored.cargo.getStackInSlot(0).is(Items.GLASS_BOTTLE), "Bottle overflow did not consume the whole vessel");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void dead_vegetation_breaks_without_any_loot(GameTestHelper h) {
    var level = h.getLevel();
    int x = 3;
    for (Block block : List.of(DeadVegetation.PLANT.get(), DeadVegetation.CROP.get())) {
      BlockPos local = new BlockPos(x++, 2, 3), pos = h.absolutePos(local);
      h.setBlock(local.below(), block == DeadVegetation.CROP.get() ? Blocks.FARMLAND : Blocks.DIRT);
      h.setBlock(local, block);
      ItemStack shears = new ItemStack(Items.SHEARS);
      ItemStack silk = new ItemStack(Items.DIAMOND_HOE); silk.enchant(Enchantments.SILK_TOUCH, 1);
      ItemStack fortune = new ItemStack(Items.DIAMOND_HOE); fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
      for (ItemStack tool : List.of(ItemStack.EMPTY, shears, silk, fortune))
        h.assertTrue(Block.getDrops(block.defaultBlockState(), level, pos, null, null, tool).isEmpty(), "Dead vegetation yielded loot with " + tool);
      h.assertTrue(LuminaCollection.category(level, pos, block.defaultBlockState()).isEmpty(), "Dead vegetation fed a collector");
      h.assertTrue(LuminaHarvest.category(level, pos, block.defaultBlockState()).isEmpty(), "Dead vegetation produced harvest Lumina");
      h.assertTrue(!(block instanceof BonemealableBlock), "Dead vegetation accepts bone meal");
      level.destroyBlock(pos, true);
      h.assertTrue(level.isEmptyBlock(pos), "Broken dead vegetation remained");
      h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(.4)).isEmpty(), "Breaking dead vegetation spawned items");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void worker_clears_dead_crop_without_inventory_or_loot(GameTestHelper h) {
    BlockPos local = new BlockPos(3, 2, 3), pos = h.absolutePos(local);
    h.setBlock(local.below(), Blocks.FARMLAND);
    h.setBlock(local, DeadVegetation.CROP.get());
    Lumberton worker = Automatons.LUMBERTON.get().create(h.getLevel());
    worker.owner = UUID.randomUUID();
    worker.setJob(WorkerJob.FARMINGTON);
    worker.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + 1.5);
    h.assertTrue(DeadVegetation.clear(worker, pos), "Worker could not clear dead crop");
    h.assertTrue(h.getLevel().isEmptyBlock(pos), "Worker left dead crop behind");
    for (int i = 0; i < worker.cargo.getSlots(); i++) h.assertTrue(worker.cargo.getStackInSlot(i).isEmpty(), "Clearing manufactured an item");
    h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Worker scattered dead crop loot");
    h.setBlock(local, Blocks.WHEAT);
    h.assertTrue(!DeadVegetation.clear(worker, pos), "Dead-clear operation destroyed a living crop");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exhaustion_survives_growth_and_never_exhausts_trees(GameTestHelper h) {
    var level = h.getLevel();
    BlockPos local = new BlockPos(4, 2, 4), pos = h.absolutePos(local);
    h.setBlock(local.below(), Blocks.FARMLAND);
    var mature = ((CropBlock)Blocks.WHEAT).getStateForAge(7);
    level.setBlock(pos, mature, 3);
    var reserves = SourceReserves.get(level);
    reserves.available(pos, mature, "crops", 4);
    reserves.debit(level, pos, mature, "crops", 1);
    int remaining = reserves.remaining(pos);
    var saved = SourceReserves.load(reserves.save(new CompoundTag()));
    var young = ((CropBlock)Blocks.WHEAT).getStateForAge(1);
    level.setBlock(pos, young, 3);
    saved.available(pos, young, "crops", 4);
    h.assertTrue(saved.remaining(pos) == remaining, "Age change or reload replenished crop reserve");
    saved.debit(level, pos, young, "crops", remaining);
    h.assertTrue(level.getBlockState(pos).is(DeadVegetation.CROP.get()), "Exhausted crop did not become dead crop");
    h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Exhaustion paid living crop loot");
    level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), 3);
    saved.available(pos, Blocks.OAK_LOG.defaultBlockState(), "logs", 4);
    saved.debit(level, pos, Blocks.OAK_LOG.defaultBlockState(), "logs", 100000);
    h.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG), "Collector exhausted a tree");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void supplied_field_has_three_persistent_ghost_selected_strips(GameTestHelper h) {
    for (Direction front : Direction.Plane.HORIZONTAL) {
      FarmPlan f = new FarmPlan();
      BlockPos center = h.absolutePos(new BlockPos(10, 1, 10));
      f.defineField(h.getLevel(), center, front);
      h.assertTrue(f.cells.size() == 80 && !f.cells.contains(center.asLong()), "Field included irrigation or lost a cell");
      for (int i = 0; i < 3; i++) {
        final int strip = i;
        long count = f.strips.values().stream().filter(s -> s == strip).count();
        h.assertTrue(count == (i == 1 ? 26 : 27), "Incorrect strip geometry");
      }
      ItemStack sample = new ItemStack(Items.BEETROOT, 7);
      h.assertTrue(f.select(0, sample) && sample.getCount() == 7, "Ghost selector consumed sample");
      f.select(1, new ItemStack(Items.CARROT));
      h.assertTrue(!f.select(2, new ItemStack(Items.POISONOUS_POTATO)), "Unsupported sample accepted");
      FarmPlan restored = FarmPlan.load(f.save());
      for (var cell : restored.strips.entrySet()) {
        Item expected = cell.getValue() == 0 ? Items.BEETROOT_SEEDS : cell.getValue() == 1 ? Items.CARROT : Items.AIR;
        h.assertTrue(restored.wanted(h.getLevel(), cell.getKey()) == expected, "Strip selection changed on reload");
      }
    }
    h.succeed();
  }
}
