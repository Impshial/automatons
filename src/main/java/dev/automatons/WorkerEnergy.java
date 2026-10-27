package dev.automatons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Milli-Lumina accounting, driven only by ticks of the loaded entity. */
public final class WorkerEnergy {
  public static final int CAPACITY = 200_000, SERVICE_CAPACITY = 400_000, LOW = 2_000;
  private final Lumberton worker;
  private int elapsed, drinkTicks, retry;
  public WorkerEnergy(Lumberton worker) { this.worker = worker; }

  public CompoundTag save() {
    var n = new CompoundTag();
    n.putInt("Operating", worker.operating());
    n.putInt("Service", worker.serviceReserve());
    n.putInt("Elapsed", elapsed);
    return n;
  }

  public void load(CompoundTag n) {
    worker.operating(n.contains("Operating") ? n.getInt("Operating") : CAPACITY);
    worker.serviceReserve(n.getInt("Service"));
    elapsed = Math.floorMod(n.getInt("Elapsed"), 1200);
    drinkTicks = retry = 0;
  }

  public static boolean fullBottle(ItemStack stack) {
    return stack.is(Luminall.BOTTLE.get()) && LuminallBottle.amount(stack) == 250
        && LuminallBottle.volume(stack) == 250;
  }

  public boolean feed(Player player, InteractionHand hand) {
    if (!worker.owns(player) || worker.operating() >= CAPACITY || !fullBottle(player.getItemInHand(hand)))
      return false;
    if (!worker.level().isClientSide) {
      player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
      worker.operating(worker.operating() + 125_000);
      worker.state("Recharged");
    }
    return true;
  }

  public boolean drink(boolean service) {
    if (service && worker.job() != WorkerJob.LUMINARITON) return false;
    for (int i = 0; i < worker.cargo.getSlots(); i++) {
      if (!fullBottle(worker.cargo.getStackInSlot(i))) continue;
      if (++drinkTicks < 32) {
        worker.showActionItem(Luminall.BOTTLE.get());
        worker.state(service ? "Filling service reserve" : "Drinking Luminall");
        return true;
      }
      worker.cargo.setStackInSlot(i, new ItemStack(Items.GLASS_BOTTLE));
      if (service) worker.serviceReserve(worker.serviceReserve() + 125_000);
      else worker.operating(worker.operating() + 125_000);
      drinkTicks = 0;
      worker.showActionItem(Items.AIR);
      return true;
    }
    drinkTicks = 0;
    return false;
  }

  /** Returns true while work must remain suspended. */
  public boolean tick() {
    if (++elapsed >= 1200) {
      elapsed = 0;
      worker.operating(worker.operating() - 1000);
    }
    if (worker.operating() > LOW) return false;
    worker.clearChop();
    worker.building.release();
    worker.farming.release();
    worker.mining.releaseWork();
    if (worker.getVehicle() instanceof Machine machine) {
      machine.halt("Operator needs Luminall");
      Vec3 exit = machine.getDismountLocationForPassenger(worker);
      worker.stopRiding();
      worker.setPos(exit);
    }
    if (worker.operating() == 0) {
      worker.getNavigation().stop();
      worker.setDeltaMovement(0, worker.getDeltaMovement().y, 0);
      worker.state("Powered down — needs a full Luminall bottle");
      return true;
    }
    if (drink(false)) return true;
    if (worker.mining.returnToSurface()) return true;
    Station site = worker.station();
    if (site == null && worker.home != null && worker.level().hasChunkAt(worker.home)
        && worker.level().getBlockEntity(worker.home) instanceof Station home
        && java.util.Objects.equals(home.owner, worker.owner)) site = home;
    if (site == null) {
      worker.getNavigation().stop();
      worker.state("Low charge — supply point unavailable");
      return true;
    }
    if (retry > worker.tickCount) {
      worker.state("Low charge — waiting for Luminall");
      return true;
    }
    for (var pos : site.chests) {
      var stock = Storage.handler((ServerLevel) worker.level(), pos, site);
      if (stock == null || (site.flags(pos) & 2) == 0) continue;
      for (int slot = 0; slot < stock.getSlots(); slot++) {
        if (!fullBottle(stock.getStackInSlot(slot)) || !Storage.accepts(worker.cargo, stock.getStackInSlot(slot))) continue;
        if (!worker.canReach(pos)) continue;
        if (worker.travelTo(pos, "Returning for Luminall", "Collecting Luminall"))
          BuildingProject.move(stock, slot, worker.cargo, 1);
        return true;
      }
    }
    if (worker.travelTo(site.getBlockPos(), "Returning for recharge", "Waiting for Luminall")) retry = worker.tickCount + 40;
    return true;
  }
}
