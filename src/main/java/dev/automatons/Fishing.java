package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Six exclusive far-to-near dock slots; casts use an actual vanilla-derived fishing hook. */
public final class Fishing {
  private final Lumberton worker;
  private UUID siteId, hook;
  private BlockPos sitePos;
  private int slot = -1;
  private long nextCast;
  public Fishing(Lumberton worker) { this.worker = worker; }
  public CompoundTag save() { var n = new CompoundTag(); n.putInt("Slot", slot); return n; }
  public void load(CompoundTag n) { slot = n.contains("Slot") ? n.getInt("Slot") : -1; }
  public void release() {
    if (worker.level() instanceof ServerLevel level) {
      if (hook != null && level.getEntity(hook) instanceof WorkerFishingHook bobber) bobber.discard();
      if (sitePos != null && level.hasChunkAt(sitePos) && level.getBlockEntity(sitePos) instanceof Station site && site.identity.equals(siteId))
        site.dockSlots.values().removeIf(id -> id.equals(worker.getUUID()));
    }
    hook = null; sitePos = null; siteId = null; slot = -1; worker.fishingPose(0);
  }
  public int reserve(Station site) {
    ServerLevel level = (ServerLevel)worker.level();
    site.dockSlots.entrySet().removeIf(e -> !(level.getEntity(e.getValue()) instanceof Lumberton w)
        || !w.isAlive() || w.job() != WorkerJob.FISHERTON || !site.identity.equals(w.totemId));
    for (var entry : site.dockSlots.entrySet()) if (entry.getValue().equals(worker.getUUID())) { slot = entry.getKey(); return slot; }
    for (int i = 0; i < 6; i++) if (!site.dockSlots.containsKey(i)) {
      site.dockSlots.put(i, worker.getUUID()); siteId = site.identity; sitePos = site.getBlockPos(); slot = i; return i;
    }
    return -1;
  }
  public void tick(Station site) {
    ServerLevel level = (ServerLevel)worker.level();
    if (site.project() == null) return;
    SiteLayout layout = new SiteLayout(site.project(), level);
    if (!layout.waterValid()) { release(); worker.state("Dock needs the full 7×10 source-water area"); return; }
    if (worker.fishingPose() > 0 && slot >= 0 && slot < layout.dockSlots().size()
        && worker.position().distanceToSqr(Vec3.atBottomCenterOf(layout.dockSlots().get(slot))) > .64) release();
    worker.selectTool();
    if (!Inventories.tool(worker.job(), worker.activeTool())) {
      release(); if (!WorkSupplies.tool(worker, site)) worker.state("Needs fishing rod"); return;
    }
    if (hook != null && level.getEntity(hook) instanceof WorkerFishingHook bobber) {
      if (bobber.bitingNow()) { bobber.reel(); hook = null; nextCast = level.getGameTime() + 40; worker.state("Reeling in catch"); }
      else worker.state("Fishing");
      return;
    }
    hook = null;
    if (level.getGameTime() < nextCast) return;
    boolean cargo = false;
    for (int i = 0; i < worker.cargo.getSlots(); i++) if (!worker.cargo.getStackInSlot(i).isEmpty() && !worker.cargo.getStackInSlot(i).is(Luminall.BOTTLE.get())) cargo = true;
    if (cargo) { release(); if (WorkSupplies.deposit(worker, site, Map.of(Luminall.BOTTLE.get(), 1))) return; }
    if (WorkSupplies.full(worker)) { worker.state("Fishing storage full"); return; }
    for (ItemEntity catchItem : level.getEntitiesOfClass(ItemEntity.class, site.project().bounds(level).inflate(4), worker::canCollectDrop)) {
      if (!Storage.accepts(worker.cargo, catchItem.getItem())) continue;
      if (worker.distanceToSqr(catchItem) < 4) worker.pickupDrop(catchItem);
      // Do not leave the dock or swim after a cast: nearby vanilla catch motion brings it ashore.
    }
    if (reserve(site) < 0) { worker.state("All six fishing places are occupied"); return; }
    List<BlockPos> positions = layout.dockSlots();
    if (positions.size() != 6) { release(); worker.state("Dock layout unavailable"); return; }
    if (!WorkerMovement.walk(worker, positions.get(slot), "Taking fishing place")) { worker.fishingPose(0); return; }
    int logicalZ = 14 - (slot / 2) * 2;
    BlockPos water = layout.logical(new BlockPos(slot % 2 == 0 ? 2 : 8, -2, logicalZ));
    if (!level.hasChunkAt(water) || !level.getFluidState(water).isSource() || !level.isEmptyBlock(water.above())) {
      release(); worker.state("Fishing cast is obstructed"); return;
    }
    worker.fishingPose((worker.getUUID().hashCode() & 1) == 0 ? 1 : 2);
    Vec3 cast = Vec3.atCenterOf(water).add(0, .25, 0); worker.getLookControl().setLookAt(cast.x, cast.y, cast.z, 30, 30);
    WorkerFishingHook bobber = WorkerFishingHook.cast(worker, cast);
    if (level.addFreshEntity(bobber)) { hook = bobber.getUUID(); nextCast = level.getGameTime() + 20; worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    else bobber.discard();
  }
}
