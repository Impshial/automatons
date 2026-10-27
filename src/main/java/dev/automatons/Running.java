package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Physical courier: each commit is made at a loaded, reachable real inventory. */
public final class Running {
  private final Lumberton w;
  private long blockedSince;
  private Vec3 progressPosition;
  private long nextDoorPath;

  public Running(Lumberton w) {
    this.w = w;
  }

  public void doorTick() {
    if (!(w.level() instanceof ServerLevel l) || w.homeId == null) return;
    for (BuildingProject p : WorldData.get(l).buildings.values()) {
      if (!p.ready || !w.homeId.equals(p.home) || !w.owner.equals(p.owner)) continue;
      BlockPos door =
          p.template(l).cells.stream()
              .filter(
                  c ->
                      c.state().getBlock() instanceof DoorBlock
                          && c.state().getValue(DoorBlock.HALF)
                              == net.minecraft.world.level.block.state.properties.DoubleBlockHalf
                                  .LOWER)
              .map(c -> p.template(l).world(p.anchor, p.front, c.local()))
              .findFirst()
              .orElse(null);
      if (door == null) continue;
      if (!l.hasChunkAt(door) || w.distanceToSqr(Vec3.atCenterOf(door)) > 100) continue;
      var state = l.getBlockState(door);
      var path = w.getNavigation().getPath();
      if (state.getBlock() instanceof DoorBlock block
          && DoorBlock.isWoodenDoor(l, door)
          && !state.getValue(DoorBlock.OPEN)
          && w.distanceToSqr(Vec3.atCenterOf(door)) < 6.25
          && path != null
          && !path.isDone()) {
        for (int i = Math.max(0, path.getNextNodeIndex() - 1);
            i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2);
            i++) {
          if (path.getNode(i).asBlockPos().equals(door)) {
            block.setOpen(null, l, state, door, true);
            break;
          }
        }
      }
      if (state.getBlock() instanceof DoorBlock block
          && state.getValue(DoorBlock.OPEN)
          && l.getEntitiesOfClass(
                  net.minecraft.world.entity.LivingEntity.class, new AABB(door).inflate(2, 1, 2))
              .isEmpty()) block.setOpen(null, l, state, door, false);
    }
  }

  public BuildingProject warehouse() {
    if (!(w.level() instanceof ServerLevel l) || w.totemId == null) return null;
    return WorldData.get(l).buildings.get(w.totemId);
  }

  public void release() {
    if (!(w.level() instanceof ServerLevel l)) return;
    WorldData data = WorldData.get(l);
    data.buildings.values().forEach(p -> p.release(w.getUUID()));
    data.logistics.values().forEach(n -> n.release(w));
  }

  public void tick(Station s) {
    ServerLevel l = (ServerLevel) w.level();
    WorldData data = WorldData.get(l);
    LogisticsNetwork network = LogisticsNetwork.get(w);
    network.refresh(l);
    LogisticsNetwork.Parcel parcel = network.parcels.get(w.getUUID());
    if (parcel == null && looseCargo()) {
      depositUnreserved(network);
      return;
    }
    if (parcel == null) parcel = network.claim(w);
    if (parcel == null) {
      idle(s.worksiteReady() ? "Waiting for deliveries" : "Waiting for worksite construction");
      return;
    }
    parcel.touched = l.getGameTime();
    if (parcel.outstanding() == 0 && parcel.picked > 0) {
      network.release(w);
      return;
    }
    LogisticsNetwork.Demand demand = network.demands.get(parcel.request);
    if (!parcel.request.startsWith("collect:")
        && (demand == null || !network.matches(l, demand, parcel.item))) {
      if (parcel.outstanding() == 0) {
        network.release(w);
        return;
      }
      parcel.returning = true;
    }
    if (parcel.outstanding() == 0) pickup(network, parcel, demand);
    else deliver(network, parcel, demand);
    data.setDirty();
  }

  private void pickup(
      LogisticsNetwork network, LogisticsNetwork.Parcel p, LogisticsNetwork.Demand demand) {
    // Recheck persisted reservations too, so updating the mod cannot revive an old tool pickup.
    if (p.request.startsWith("collect:")
        && p.source.worker() == null
        && LogisticsNetwork.leaveInChest(p.item)) {
      network.release(w);
      return;
    }
    ServerLevel l = (ServerLevel) w.level();
    p.phase = LogisticsNetwork.Phase.TO_SOURCE;
    if (!travel(network, p.source, p.item, "Collecting " + p.item.getHoverName().getString())) {
      blocked(network, p);
      return;
    }
    var handler = network.handler(l, p.source, p.item);
    if (handler == null) {
      w.state("Waiting for source storage");
      blocked(network, p);
      return;
    }
    int limit =
        p.request.startsWith("collect:") || demand == null
            ? p.quantity
            : Math.min(p.quantity, network.neededNow(l, demand));
    limit = Math.min(limit, network.capacity(l, p.destination, p.item, w.getUUID()));
    limit = Math.min(limit, network.pickupAllowance(l, p));
    if (limit == 0) {
      p.phase = LogisticsNetwork.Phase.BLOCKED;
      w.state("Waiting for destination space");
      blocked(network, p);
      return;
    }
    for (int slot = 0; slot < handler.getSlots() && p.picked < limit; slot++)
      if (ItemStack.isSameItemSameTags(handler.getStackInSlot(slot), p.item))
        p.picked += BuildingProject.move(handler, slot, w.cargo, limit - p.picked);
    p.quantity = p.picked;
    if (p.picked == 0) network.release(w);
    else {
      p.phase = LogisticsNetwork.Phase.CARRYING;
      blockedSince = 0;
      progressPosition = null;
      w.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    network.changed(l);
  }

  private void deliver(
      LogisticsNetwork network, LogisticsNetwork.Parcel p, LogisticsNetwork.Demand demand) {
    ServerLevel l = (ServerLevel) w.level();
    int actual = 0;
    for (int i = 0; i < w.cargo.getSlots(); i++)
      if (ItemStack.isSameItemSameTags(w.cargo.getStackInSlot(i), p.item))
        actual += w.cargo.getStackInSlot(i).getCount();
    p.picked = Math.min(p.picked, p.delivered + actual);
    if (p.outstanding() == 0) {
      network.release(w);
      return;
    }
    if (!p.returning && !p.request.startsWith("collect:") && network.neededNow(l, demand) == 0)
      p.returning = true;
    if (p.returning) {
      var to = network.warehouseDestination(l, p.item, w.getUUID());
      if (to == null) {
        p.phase = LogisticsNetwork.Phase.BLOCKED;
        w.state("Warehouse full");
        return;
      }
      p.destination = to;
    }
    var handler = network.handler(l, p.destination, p.item);
    if (handler == null || network.capacity(l, p.destination, p.item, w.getUUID()) == 0) {
      if (p.destination.worker() != null) {
        var fallback = fallback(network, p);
        if (fallback != null) p.destination = fallback;
        else p.returning = true;
      } else {
        var alternate = network.warehouseDestination(l, p.item, w.getUUID());
        if (alternate != null) {
          p.destination = alternate;
          p.returning = true;
        } else {
          p.phase = LogisticsNetwork.Phase.BLOCKED;
          w.state("Warehouse full");
        }
      }
      return;
    }
    p.phase = LogisticsNetwork.Phase.TO_DESTINATION;
    if (!travel(
        network, p.destination, p.item, "Delivering " + p.item.getHoverName().getString())) {
      if (stalledFor() > 200 && p.destination.worker() != null) {
        var fallback = fallback(network, p);
        if (fallback != null) {
          p.destination = fallback;
          blockedSince = 0;
        }
      }
      return;
    }
    blockedSince = 0;
    p.phase = LogisticsNetwork.Phase.DELIVERING;
    int limit = p.outstanding();
    if (p.destination.worker() != null && demand != null)
      limit = Math.min(limit, network.neededNow(l, demand));
    int delivered = 0;
    for (int slot = 0; slot < w.cargo.getSlots() && delivered < limit; slot++)
      if (ItemStack.isSameItemSameTags(w.cargo.getStackInSlot(slot), p.item))
        delivered += BuildingProject.move(w.cargo, slot, handler, limit - delivered);
    p.delivered += delivered;
    if (p.destination.worker() != null
        && l.getEntity(p.destination.worker()) instanceof Lumberton recipient) {
      recipient.selectTool();
      recipient.smile();
    }
    w.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    if (p.outstanding() == 0) {
      p.phase = LogisticsNetwork.Phase.COMPLETED;
      network.release(w);
    } else if (delivered == 0) {
      p.phase = LogisticsNetwork.Phase.BLOCKED;
      p.returning = true;
      w.state("Destination full");
    }
    network.changed(l);
  }

  private LogisticsNetwork.Endpoint fallback(LogisticsNetwork network, LogisticsNetwork.Parcel p) {
    ServerLevel l = (ServerLevel) w.level();
    Station s = network.station(p.destination.totem());
    if (s == null) return null;
    for (BlockPos chest : s.chests)
      if ((s.flags(chest) & 2) != 0) {
        var e = new LogisticsNetwork.Endpoint(s.identity, chest, null);
        if (network.capacity(l, e, p.item, w.getUUID()) > 0) return e;
      }
    return null;
  }

  private void blocked(LogisticsNetwork network, LogisticsNetwork.Parcel p) {
    if (stalledFor() > 200 && p.outstanding() == 0) {
      network.release(w);
      blockedSince = 0;
      progressPosition = null;
    }
  }

  private long stalledFor() {
    if (progressPosition == null
        || progressPosition.distanceToSqr(w.position()) > .0625
        || blockedSince == 0) {
      progressPosition = w.position();
      blockedSince = w.level().getGameTime();
      return 0;
    }
    return w.level().getGameTime() - blockedSince;
  }

  private boolean travel(
      LogisticsNetwork network, LogisticsNetwork.Endpoint endpoint, ItemStack item, String moving) {
    ServerLevel l = (ServerLevel) w.level();
    BlockPos target = network.position(l, endpoint);
    if (target == null || !l.hasChunkAt(target)) {
      w.state("Waiting for route");
      return false;
    }
    Station s = network.station(endpoint.totem());
    if (s != null
        && s.mine != null
        && s.mine.entrance != null
        && target.getY() < s.mine.entrance.getY() - 2)
      return w.mining.courierTravel(s.mine, target, moving);
    if (w.mining.returnToSurface()) return false;
    // Plan the known warehouse exit first. A distant destination behind a long
    // detour can exhaust the bounded path search while the runner is indoors.
    for (BuildingProject building : WorldData.get(l).buildings.values()) {
      if (!building.ready
          || !Objects.equals(building.home, w.homeId)
          || !Objects.equals(building.owner, w.owner)
          // The outer template row is the eave/porch, not the indoor walking area.
          || !w.getBoundingBox().intersects(building.bounds(l).deflate(1, 0, 1))
          || building.contains(l, target)) continue;
      // Navigation may finish before its final node. Leave the whole body outside
      // the building, then allow ordinary paths across the reserved approach.
      BlockPos outside = building.entrance(l).relative(building.front, 3);
      if (!l.hasChunkAt(outside)) {
        w.state("Waiting for route");
        return false;
      }
      if (l.getGameTime() >= nextDoorPath && WorldEvents.pathBudget()) {
        nextDoorPath = l.getGameTime() + 20;
        var path = w.getNavigation().createPath(outside, 0);
        if (path != null && path.canReach()) w.getNavigation().moveTo(path, 1);
      }
      w.state(moving);
      return false;
    }
    return w.travelTo(target, moving, moving);
  }

  public void depositUnreserved(LogisticsNetwork network) {
    ServerLevel l = (ServerLevel) w.level();
    for (int slot = 0; slot < w.cargo.getSlots(); slot++) {
      ItemStack stack = w.cargo.getStackInSlot(slot);
      int excess = w.mining.carried(stack.getItem()) - w.cargoKeep(stack);
      if (stack.isEmpty() || excess <= 0) continue;
      var target = network.warehouseDestination(l, stack, w.getUUID());
      if (target == null) {
        w.state("Warehouse full");
        return;
      }
      if (travel(network, target, stack, "Unloading backpack")) {
        var h = network.handler(l, target, stack);
        if (h != null) BuildingProject.move(w.cargo, slot, h, Math.min(stack.getCount(), excess));
        network.changed(l);
      }
      return;
    }
  }

  private boolean looseCargo() {
    for (int slot = 0; slot < w.cargo.getSlots(); slot++) {
      ItemStack stack = w.cargo.getStackInSlot(slot);
      if (!stack.isEmpty() && w.mining.carried(stack.getItem()) > w.cargoKeep(stack)) return true;
    }
    return false;
  }

  public void idle(String message) {
    if (w.mining.returnToSurface()) return;
    if (message.equals("Recalled") && looseCargo() && w.homeId != null) {
      var network = LogisticsNetwork.get(w);
      network.refresh((ServerLevel) w.level());
      // Only loose cargo may be deposited during recall; suspended parcels keep ownership.
      if (!network.parcels.containsKey(w.getUUID())) {
        for (int i = 0; i < w.cargo.getSlots(); i++)
          if (!w.cargo.getStackInSlot(i).isEmpty()
              && network.warehouseDestination(
                      (ServerLevel) w.level(), w.cargo.getStackInSlot(i), w.getUUID())
                  != null) {
            depositUnreserved(network);
            return;
          }
      }
    }
    if (w.home == null || !w.level().hasChunkAt(w.home)) {
      w.state("Home unavailable");
      return;
    }
    int offset = Math.floorMod(w.getUUID().hashCode(), 8);
    int[][] ring = {{3, 0}, {3, 3}, {0, 3}, {-3, 3}, {-3, 0}, {-3, -3}, {0, -3}, {3, -3}};
    for (int i = 0; i < 8; i++) {
      int[] d = ring[(offset + i) % 8];
      BlockPos feet = w.home.offset(d[0], 0, d[1]);
      Vec3 center = Vec3.atBottomCenterOf(feet);
      if (!MiningRules.supported((ServerLevel) w.level(), feet)
          || !w.level()
              .noCollision(
                  w,
                  w.getDimensions(net.minecraft.world.entity.Pose.STANDING)
                      .makeBoundingBox(center))) continue;
      if (w.position().distanceToSqr(center) < .3) {
        w.getNavigation().stop();
        ((WorkerMoveControl) w.getMoveControl()).stop();
        w.state(message);
        return;
      }
      if (WorldEvents.pathBudget()) {
        var path = w.getNavigation().createPath(feet, 0);
        if (path != null && path.canReach()) {
          w.getNavigation().moveTo(path, 1);
          w.state("Returning home");
          return;
        }
      }
    }
    w.state("Waiting for safe home position");
  }
}
