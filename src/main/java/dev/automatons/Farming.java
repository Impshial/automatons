package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Physical actions for a Totem-owned field. A claim never authorizes a distant mutation. */
public final class Farming {
  private final Lumberton w;
  private BlockPos target;
  private long nextAction, targetSince;
  private Item supplyItem;
  private int supplyCount;

  public Farming(Lumberton worker) {
    w = worker;
  }

  public void release() {
    if (w.level() instanceof ServerLevel l)
      WorldData.get(l).farms.values().forEach(f -> f.release(w.getUUID()));
    target = null;
  }

  public Map<Item, Integer> reserve() {
    Station s = w.station();
    if (s == null || s.farm == null || !(w.level() instanceof ServerLevel l)) return Map.of();
    Map<Item, Integer> result = new LinkedHashMap<>();
    s.farm.reserves(l).forEach((item, count) -> result.put(item, Math.min(64, count)));
    if (s.farm.water != null
        && l.hasChunkAt(s.farm.water)
        && !l.getFluidState(s.farm.water).isSource()) result.put(Items.WATER_BUCKET, 1);
    if (s.farm.boneMeal) result.put(Items.BONE_MEAL, 16);
    return result;
  }

  private void state(FarmPlan f, String text, int flags) {
    w.state(text);
    w.alerts = flags;
    f.reason = text;
  }

  public void tick(Station s) {
    ServerLevel l = (ServerLevel) w.level();
    if (s.purpose != WorksitePurpose.FARM) {
      w.state("Assign to a Farm Totem");
      return;
    }
    if (s.farm == null) {
      s.farm = new FarmPlan();
      WorldData.get(l).farms.put(s.identity, s.farm);
      s.setChanged();
    }
    FarmPlan f = s.farm;
    f.discover(l, s, w);
    if (!f.discovered) {
      state(f, f.reason, 0);
      return;
    }
    if (supplyItem != null) {
      if (w.mining.carried(supplyItem) > 0) w.showActionItem(supplyItem);
      if (w.mining.carried(supplyItem) >= supplyCount) supplyItem = null;
      else if (!Storage.accepts(w.cargo, new ItemStack(supplyItem))
          && WorkSupplies.deposit(w, s, reserve())) return;
      else if (WorkSupplies.take(w, s, supplyItem, supplyCount)) return;
      else {
        boolean optional = supplyItem == Items.BONE_MEAL;
        supplyItem = null;
        release();
        if (!optional) {
          state(f, "Needs planting supplies", 16);
          return;
        }
      }
    }
    if (f.defaultPlant == null)
      for (Item plant : FarmPlan.PLANTS) {
        if (w.mining.carried(plant) > 0) {
          f.defaultPlant = plant;
          f.changed(l, s);
          break;
        }
        for (BlockPos p : s.chests) {
          var h = Storage.handler(l, p, s);
          if (h == null) continue;
          for (int i = 0; i < h.getSlots(); i++)
            if (h.getStackInSlot(i).is(plant)) {
              f.defaultPlant = plant;
              break;
            }
        }
        if (f.defaultPlant != null) {
          f.changed(l, s);
          break;
        }
      }
    if (!Inventories.tool(WorkerJob.FARMINGTON, w.activeTool())) {
      release();
      if (!WorkSupplies.tool(w, s))
        state(f, "Needs hoe in tool slots or worksite chest", Lumberton.TOOLS);
      return;
    }
    // Longer collection trips stay inside the field; ordinary nearby pickup runs for every job.
    for (ItemEntity drop : l.getEntitiesOfClass(ItemEntity.class, w.getBoundingBox().inflate(6))) {
      if (!w.canCollectDrop(drop)
          || !f.contains(drop.blockPosition())
          || !Storage.accepts(w.cargo, drop.getItem())) continue;
      release();
      if (w.travelTo(drop.blockPosition(), "Collecting farm drops", "Collecting farm drops")) {
        w.pickupDrop(drop);
      }
      return;
    }
    if (f.water != null && l.hasChunkAt(f.water) && !l.getBlockState(f.water).is(Blocks.WATER)) {
      irrigate(l, s, f);
      return;
    }
    if (target == null) {
      var options =
          f.cells.stream()
              .map(BlockPos::of)
              .filter(
                  p ->
                      l.hasChunkAt(p)
                          && f.inRange(s, p)
                          && !WorldData.get(l).protectedAt(p)
                          && !WorldData.get(l).protectedAt(p.above()))
              .sorted(
                  Comparator.comparingInt((BlockPos p) -> priority(l, f, p))
                      .thenComparingDouble(p -> w.distanceToSqr(Vec3.atCenterOf(p)))
                      .thenComparingLong(BlockPos::asLong))
              .toList();
      for (BlockPos p : options)
        if (priority(l, f, p) < 9 && f.claim(p, w.getUUID())) {
          target = p;
          targetSince = l.getGameTime();
          break;
        }
    }
    if (target == null) {
      if (f.boneMeal
          && w.mining.carried(Items.BONE_MEAL) == 0
          && WorkSupplies.take(w, s, Items.BONE_MEAL, 16)) {
        supplyItem = Items.BONE_MEAL;
        supplyCount = 16;
        return;
      }
      if (WorkSupplies.deposit(w, s, reserve())) return;
      state(f, "Waiting for crops to grow", 0);
      return;
    }
    BlockPos soil = target, p = soil.above();
    if (!l.hasChunkAt(p) || !f.inRange(s, soil) || !f.owned(soil, w.getUUID())) {
      release();
      return;
    }
    Item wanted = f.wanted(l, soil.asLong());
    if (l.getBlockState(p).isAir()
        && l.getBlockState(soil).is(Blocks.FARMLAND)
        && w.mining.carried(wanted) > 0) w.showActionItem(wanted);
    else if (priority(l, f, soil) == 3) w.showActionItem(Items.BONE_MEAL);
    if (!w.travelTo(p, "Tending field", "Tending field")) {
      if (l.getGameTime() - targetSince > 200) release();
      return;
    }
    if (l.getGameTime() < nextAction) return;
    nextAction = l.getGameTime() + Math.max(4, 10 - 2 * s.tier("speed"));
    if (WorldData.get(l).protectedAt(p) || WorldData.get(l).buildingProtected(l, p)) {
      state(f, "Field protected", 0);
      release();
      return;
    }
    BlockState actual = l.getBlockState(p), ground = l.getBlockState(soil);
    if (actual.getBlock() instanceof CropBlock crop
        && crop.isMaxAge(actual)
        && FarmPlan.planting(actual) != Items.AIR) {
      List<ItemStack> drops = Block.getDrops(actual, l, p, null, w, w.activeTool());
      if (!Inventories.fits(w.cargo, drops)) {
        release();
        if (!WorkSupplies.deposit(w, s, reserve()))
          state(f, "Needs room for harvest", Lumberton.CARGO);
        return;
      }
      if (MiningRules.breakPermission(w, p, actual) < 0
          || !l.setBlock(p, Blocks.AIR.defaultBlockState(), 3)) {
        release();
        return;
      }
      f.replant.put(soil.asLong(), FarmPlan.planting(actual));
      for (ItemStack drop : drops) {
        ItemStack rest = Inventories.insert(w.cargo, drop, false);
        if (!rest.isEmpty()) Block.popResource(l, p, rest);
      }
      l.levelEvent(2001, p, Block.getId(actual));
      w.swing(InteractionHand.MAIN_HAND);
      f.changed(l, s);
      return; // Retain this cell through replanting.
    }
    if (!actual.isAir() && actual.canBeReplaced() && FarmPlan.planting(actual) == Items.AIR) {
      if (MiningRules.breakPermission(w, p, actual) >= 0) l.destroyBlock(p, true, w);
      return;
    }
    if (actual.isAir() && !ground.is(Blocks.FARMLAND) && FarmPlan.soil(ground)) {
      var actor = MiningRules.actor(w);
      ItemStack before = actor.getMainHandItem();
      actor.setItemInHand(InteractionHand.MAIN_HAND, w.activeTool().copy());
      try {
        var context =
            new UseOnContext(
                actor,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(soil).add(0, .5, 0), Direction.UP, soil, false));
        BlockState tilled =
            ground.getToolModifiedState(
                context, net.minecraftforge.common.ToolActions.HOE_TILL, false);
        if (tilled != null
            && MiningRules.breakPermission(w, soil, ground) >= 0
            && MiningRules.place(w, soil, tilled)) {
          w.wear(1);
          l.playSound(
              null,
              soil,
              net.minecraft.sounds.SoundEvents.HOE_TILL,
              net.minecraft.sounds.SoundSource.BLOCKS,
              1,
              1);
          f.changed(l, s);
        } else {
          state(f, "Cannot till this soil", 0);
          release();
        }
      } finally {
        actor.setItemInHand(InteractionHand.MAIN_HAND, before);
      }
      return;
    }
    if (actual.isAir() && ground.is(Blocks.FARMLAND)) {
      Item item = f.wanted(l, soil.asLong());
      if (item == Items.AIR || w.mining.carried(item) == 0) {
        if (item != Items.AIR) {
          supplyItem = item;
          supplyCount = Math.min(64, f.reserves(l).getOrDefault(item, 1));
          release();
          return;
        }
        state(f, "Needs planting supplies", 16);
        release();
        return;
      }
      if (MiningRules.place(w, p, FarmPlan.crop(item))) {
        BuildingProject.consume(w, item, 1);
        f.replant.remove(soil.asLong());
        f.changed(l, s);
        release();
      } else {
        state(f, "Planting needs light and clear soil", 0);
        release();
      }
      return;
    }
    if (f.boneMeal
        && actual.getBlock() instanceof CropBlock crop
        && !crop.isMaxAge(actual)
        && FarmPlan.planting(actual) != Items.AIR
        && w.mining.carried(Items.BONE_MEAL) > 0) {
      if (MiningRules.breakPermission(w, p, actual) >= 0
          && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), l, p)) {
        BuildingProject.consume(w, Items.BONE_MEAL, 1);
        l.levelEvent(1505, p, 0);
        w.swing(InteractionHand.MAIN_HAND);
        f.changed(l, s);
      }
    }
    release();
  }

  private int priority(ServerLevel l, FarmPlan f, BlockPos p) {
    BlockState crop = l.getBlockState(p.above());
    if (f.replant.containsKey(p.asLong()) && crop.isAir()) return 0;
    if (crop.getBlock() instanceof CropBlock c
        && c.isMaxAge(crop)
        && FarmPlan.planting(crop) != Items.AIR) return 1;
    if ((crop.isAir() || crop.canBeReplaced() && FarmPlan.planting(crop) == Items.AIR)
        && FarmPlan.soil(l.getBlockState(p))) return 2;
    if (f.boneMeal
        && w.mining.carried(Items.BONE_MEAL) > 0
        && crop.getBlock() instanceof CropBlock c
        && !c.isMaxAge(crop)
        && FarmPlan.planting(crop) != Items.AIR) return 3;
    return 9;
  }

  private void irrigate(ServerLevel l, Station s, FarmPlan f) {
    BlockPos p = f.water;
    if (!f.inRange(s, p)) {
      state(f, "Irrigation outside Totem range", 0);
      release();
      return;
    }
    if (l.dimensionType().ultraWarm()) {
      state(f, "Water cannot remain in this dimension", 0);
      return;
    }
    if (!f.claim(p, w.getUUID())) {
      w.state("Colleague preparing irrigation");
      return;
    }
    if (w.mining.carried(Items.WATER_BUCKET) == 0) {
      if (!WorkSupplies.take(w, s, Items.WATER_BUCKET, 1)) {
        state(f, "Needs water bucket", 16);
        release();
      }
      return;
    }
    w.showActionItem(Items.WATER_BUCKET);
    if (!w.travelTo(p, "Preparing irrigation", "Preparing irrigation")) return;
    BlockState old = l.getBlockState(p);
    if (!f.owned(p, w.getUUID())
        || !(FarmPlan.soil(old) || old.isAir())
        || WorldData.get(l).protectedAt(p)
        || MiningRules.breakPermission(w, p, old) < 0) {
      release();
      return;
    }
    if (!MiningRules.place(w, p, Blocks.WATER.defaultBlockState())) {
      release();
      return;
    }
    for (ItemStack drop : Block.getDrops(old, l, p, null, w, ItemStack.EMPTY))
      Block.popResource(l, p.above(), drop);
    BuildingProject.consume(w, Items.WATER_BUCKET, 1);
    ItemStack rest = Inventories.insert(w.cargo, new ItemStack(Items.BUCKET), false);
    if (!rest.isEmpty())
      throw new IllegalStateException("Water bucket replacement must fit its freed slot");
    l.playSound(
        null,
        p,
        net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
        net.minecraft.sounds.SoundSource.BLOCKS,
        1,
        1);
    f.changed(l, s);
    release();
  }
}
