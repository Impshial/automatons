package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;

/** Compact presentation state; inspecting a menu never claims work or changes its configuration. */
public final class WorkInterface {
  private static CompoundTag worker(ServerLevel level, UUID id) {
    CompoundTag saved = Ledger.get(level.getServer()).workers.get(id);
    CompoundTag n = saved == null ? new CompoundTag() : saved.copy();
    n.putUUID("Id", id);
    if (level.getEntity(id) instanceof Lumberton live) {
      n.putString("Label", live.getDisplayName().getString());
      n.putString("Activity", live.status());
      n.putFloat("Health", live.getHealth());
      n.putFloat("MaxHealth", live.getMaxHealth());
      n.putInt("Operating", live.operating());
      n.putBoolean("Loaded", true);
      n.putInt("Job", live.job());
    } else {
      n.putString("Label", saved == null ? "Unverified worker" : WorkerNames.recordName(saved));
      n.putString("Activity", "Unloaded");
      n.putInt("Operating", n.getCompound("Energy").getInt("Operating"));
      n.putFloat("MaxHealth", n.getInt("Job") == WorkerJob.GUARDINGTON ? 60 : 30);
    }
    return n;
  }

  public static void describe(WorkMenu menu, CompoundTag n) {
    if (!(menu.player.level() instanceof ServerLevel level)) return;
    n.putString("Feedback", menu.feedback);
    n.putInt("PendingProfession", menu.pendingProfession);
    if (menu.worker != null) {
      Lumberton w = menu.worker;
      n.putString("Name", w.getDisplayName().getString());
      Station site = w.isGuard() ? w.guarding.assigned() : w.station();
      n.putString(
          "Assignment",
          site == null
              ? "Unassigned"
              : site.getDisplayName().getString() + " — " + site.purpose.label);
      if (w.isGuard()) {
        ListTag sites = new ListTag();
        WorldData.get(level).buildings.values().stream()
            .filter(p -> Objects.equals(p.owner, w.owner) && Objects.equals(p.home, w.homeId))
            .sorted(Comparator.comparing(p -> p.totem))
            .limit(64)
            .forEach(
                p -> {
                  CompoundTag entry = new CompoundTag();
                  entry.putUUID("Id", p.totem);
                  boolean loaded =
                      level.hasChunkAt(p.anchor)
                          && level.getBlockEntity(p.anchor) instanceof Station;
                  entry.putString(
                      "Label",
                      (loaded
                              ? ((Station) level.getBlockEntity(p.anchor))
                                  .getDisplayName()
                                  .getString()
                              : p.purpose.label)
                          + " ("
                          + WorkMenu.xyz(p.anchor)
                          + ")");
                  entry.putBoolean("Loaded", loaded);
                  sites.add(entry);
                });
        n.put("GuardSites", sites);
      }
      return;
    }
    if (!menu.isTotem) return;
    Station s = menu.station;
    BuildingProject p = s.project();
    n.putString("Name", s.getDisplayName().getString());
    n.putString("ChangeIssue", s.professionChangeIssue());
    n.putBoolean("WorksiteReady", s.worksiteReady());
    n.putBoolean("WorkPaused", s.recalled);
    n.putInt("StorageCount", s.chests.size());
    n.putString(
        "SiteStatus", p == null ? s.setupIssue : p.paused ? "Construction paused" : p.reason);
    ListTag workers = new ListTag(), guards = new ListTag(), candidates = new ListTag();
    s.workers.stream().sorted().forEach(id -> workers.add(worker(level, id)));
    s.guards.stream().sorted().forEach(id -> guards.add(worker(level, id)));
    level
        .getEntitiesOfClass(
            Lumberton.class,
            new AABB(s.getBlockPos()).inflate(32),
            w ->
                w.isAlive()
                    && !w.packing
                    && Objects.equals(w.owner, s.owner)
                    && (s.logisticsHome == null || Objects.equals(w.homeId, s.logisticsHome))
                    && !s.workers.contains(w.getUUID())
                    && !s.guards.contains(w.getUUID()))
        .stream()
        .limit(24)
        .forEach(w -> candidates.add(worker(level, w.getUUID())));
    n.put("Production", workers);
    n.put("Protectors", guards);
    n.put("Candidates", candidates);
    Map<Item, Integer> stock = new HashMap<>();
    int budget = 512;
    for (BlockPos pos : s.chests) {
      var handler = Storage.handler(level, pos, s);
      if (handler == null) continue;
      for (int i = 0; i < handler.getSlots() && budget-- > 0; i++) {
        ItemStack stack = handler.getStackInSlot(i);
        if (!stack.isEmpty()) stock.merge(stack.getItem(), stack.getCount(), Integer::sum);
      }
    }
    ListTag supplies = new ListTag();
    var network = WorldData.get(level).logistics.get(s.logisticsHome);
    if (network != null)
      network.demands.values().stream()
          .filter(d -> d.source == null && s.identity.equals(d.totem) && d.item != null)
          .sorted(Comparator.comparingInt(d -> d.priority))
          .limit(24)
          .forEach(
              d -> {
                int needed = network.neededNow(level, d);
                if (needed <= 0) return;
                CompoundTag row = new ItemStack(d.item).save(new CompoundTag());
                row.putInt("Need", needed);
                supplies.add(row);
              });
    n.put("SupplyNeeds", supplies);
    n.putInt("Bottles", stock.getOrDefault(Luminall.BOTTLE.get(), 0));
    n.putInt("Arrows", stock.getOrDefault(Items.ARROW, 0));
    n.putInt("Buckets", stock.getOrDefault(Items.BUCKET, 0));
    n.putInt("Leads", stock.getOrDefault(Items.LEAD, 0));
    n.putInt("Rods", stock.getOrDefault(Items.FISHING_ROD, 0));
    n.putInt("RawShards", stock.getOrDefault(Automatons.LUMITE_SHARD.get(), 0));
    n.putInt("InfusedShards", stock.getOrDefault(Automatons.INFUSED_LUMITE.get(), 0));
    if (s.farm != null) n.putBoolean("BoneMeal", s.farm.boneMeal);
    n.putInt("DimensionBottom", level.getMinBuildHeight());
    n.putInt("QuarryBottom", s.quarryBottom == null ? level.getMinBuildHeight() : s.quarryBottom);
    if (s.mine != null && s.mine.quarry != null) {
      var quarry = s.mine.quarry;
      n.putInt("QuarryBottom", quarry.bottomY);
      n.putInt("QuarryY", quarry.y());
      n.putInt("QuarryDone", quarry.done.cardinality());
      n.putInt("QuarryUnbreakable", quarry.unbreakable.cardinality());
      n.putString("QuarryReason", s.mine.reason);
      n.putBoolean("QuarryActive", true);
    }
    if (s.mine != null && s.mine.strip != null) {
      MinePlan mine = s.mine;
      StripMine plan = mine.strip;
      ListTag lines = new ListTag();
      List<String> text =
          new ArrayList<>(
              List.of(
                  "Fixed strip: 20 passages",
                  "Level " + plan.floors.size() + " at Y " + mine.chamber().getY(),
                  "Center corridor: 3 wide",
                  "Passages: 1 wide, 2 high",
                  "Storage: four double chests",
                  mine.reason));
      int finished = 0, stopped = 0;
      for (int i = 0; i < 20; i++) {
        var branch = plan.current().branches.get(i);
        if (!branch.stopped.isEmpty()) stopped++;
        else if (branch.done(plan.shapes(level).get(i))) finished++;
      }
      text.add("Finished: " + finished + "/20; stopped: " + stopped);
      for (var branch : plan.current().branches)
        if (!branch.stopped.isEmpty()) text.add(branch.stopped);
      text.stream().limit(12).forEach(line -> lines.add(StringTag.valueOf(line)));
      n.put("MiningLines", lines);
    }
    if (p != null && s.purpose == WorksitePurpose.FISH) {
      n.putBoolean("WaterValid", new SiteLayout(p, level).waterValid());
      n.putInt("DockUsed", s.dockSlots.size());
    }
    if (p != null && s.purpose == WorksitePurpose.RANCH) {
      int[] adults = new int[4], babies = new int[4];
      for (Animal animal : level.getEntitiesOfClass(Animal.class, new SiteLayout(p, level).pen())) {
        int species = RanchPlan.species(animal);
        if (species >= 0) {
          if (animal.isBaby()) babies[species]++;
          else adults[species]++;
        }
      }
      n.putIntArray("Adults", adults);
      n.putIntArray("Babies", babies);
      int milk = 0, capacity = 0;
      for (var cell : p.template(level).cells)
        if (cell.state().is(Luminall.TANK.get())) {
          BlockPos pos = p.template(level).world(p.anchor, p.front, cell.local());
          if (level.hasChunkAt(pos)
              && level.getBlockEntity(pos) instanceof Reservoir tank
              && (tank.tank.isEmpty()
                  || net.minecraftforge.common.ForgeMod.MILK.isPresent()
                      && tank.tank.getFluid().getFluid()
                          == net.minecraftforge.common.ForgeMod.MILK.get())) {
            milk += tank.tank.getFluidAmount();
            capacity += tank.tank.getCapacity();
          }
        }
      n.putInt("Milk", milk);
      n.putInt("MilkCapacity", capacity);
    }
  }
}
