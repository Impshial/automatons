package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Server-authoritative, read-only construction costs and physically stored supply counts. */
public final class BuildMaterials {
  /** Projects stay listed when unloaded, without loading chunks or inventing stock counts. */
  public static ListTag network(Station home) {
    ListTag projects = new ListTag();
    if (home == null || home.isTotem() || !(home.getLevel() instanceof ServerLevel level))
      return projects;
    var plans =
        WorldData.get(level).buildings.values().stream()
            .filter(p -> home.identity.equals(p.home) && Objects.equals(home.owner, p.owner))
            .sorted(
                Comparator.comparingLong((BuildingProject p) -> p.created).thenComparing(p -> p.id))
            .toList();
    for (var plan : plans) {
      boolean loaded = level.hasChunkAt(plan.anchor);
      Station site =
          loaded
                  && level.getBlockEntity(plan.anchor) instanceof Station found
                  && found.identity.equals(plan.totem)
              ? found
              : null;
      if (loaded && site == null) continue;
      if (!plan.ready) projects.add(networkEntry(plan, site, false));
      if (site != null && site.mine != null && site.mine.stage != MinePlan.Stage.READY)
        projects.add(networkEntry(plan, site, true));
    }
    return projects;
  }

  private static CompoundTag networkEntry(BuildingProject plan, Station site, boolean mine) {
    CompoundTag entry = new CompoundTag();
    entry.putString("Key", plan.id.toString() + (mine ? ":mine" : ""));
    entry.putString("Title", mine ? "Mine Infrastructure" : plan.purpose.label);
    entry.putString(
        "Location", plan.anchor.getX() + ", " + plan.anchor.getY() + ", " + plan.anchor.getZ());
    entry.putBoolean("Loaded", site != null);
    entry.putString(
        "Status",
        site == null
            ? "Worksite unloaded"
            : mine ? site.mine.reason : (plan.paused ? "Paused: " : "") + plan.reason);
    if (site != null) entry.put("Rows", describe(site, null, !mine));
    return entry;
  }

  public static ListTag describe(Station s, Lumberton worker, boolean warehouse) {
    if (s == null || !(s.getLevel() instanceof ServerLevel l)) return new ListTag();
    Map<Item, Integer> total = new LinkedHashMap<>(),
        left = new LinkedHashMap<>(),
        have = new HashMap<>();
    if (warehouse) {
      BuildingProject plan = WorldData.get(l).buildings.get(s.identity);
      total.putAll(
          (plan == null ? BuildingTemplate.load(l, "warehouse") : plan.template(l)).materials);
      total.merge(Items.CHEST, 1, Integer::sum);
      left.putAll(plan == null ? total : plan.supplies.needed(l));
    } else {
      MinePlan m = s.mine == null ? new MinePlan() : s.mine;
      total.putAll(m.minehead(l).materials);
      total.put(Items.LADDER, m.ladderCount());
      total.put(Items.CHEST, 4);
      total.put(Items.TORCH, Balance.TORCH_STOCK.get());
      left.putAll(total);
      if (m.entrance != null) {
        if (m.entranceComplete || m.stage.ordinal() > MinePlan.Stage.ARCH.ordinal())
          m.minehead(l).materials.keySet().forEach(item -> left.put(item, 0));
        else
          for (var c : m.minehead(l).cells) {
            ItemStack cost = BuildingTemplate.cost(c.state());
            BlockPos pos = m.minehead(l).world(m.entrance, m.forward, c.local());
            if (!cost.isEmpty()
                && l.hasChunkAt(pos)
                && Construction.matches(l.getBlockState(pos), c.state().rotate(m.rotation())))
              left.computeIfPresent(cost.getItem(), (item, n) -> Math.max(0, n - cost.getCount()));
          }
        int ladders = 0;
        for (int d = m.firstLadder(); d <= m.shaftDepth; d++)
          if (!l.hasChunkAt(m.ladder(d)) || !l.getBlockState(m.ladder(d)).equals(m.ladderState()))
            ladders++;
        total.put(Items.LADDER, m.ladderCount());
        left.put(Items.LADDER, ladders);
        left.put(
            Items.CHEST,
            (int)
                m.depotPositions().stream()
                    .filter(
                        p ->
                            !m.placedChests.contains(p.asLong())
                                || !l.getBlockState(p).is(Blocks.CHEST))
                    .count());
      }
    }
    BuildingProject project = warehouse ? s.project() : null;
    Set<Station> supplies = new LinkedHashSet<>();
    supplies.add(s);
    if (project != null
        && project.builder != null
        && l.getEntity(project.builder) instanceof Lumberton builder) {
      if (worker == null) worker = builder;
      if (builder.station() != null) supplies.add(builder.station());
    }
    if (worker != null && worker.job() == WorkerJob.BUILDINGTON && worker.station() != null)
      supplies.add(worker.station());
    Set<UUID> workers = new HashSet<>();
    for (Lumberton w : s.loadedWorkers())
      if (w.job() == (warehouse ? WorkerJob.BUILDINGTON : WorkerJob.MININGTON)
          && workers.add(w.getUUID())) BuildingProject.count(w.cargo, have);
    if (worker != null && workers.add(worker.getUUID())) BuildingProject.count(worker.cargo, have);
    Set<BlockPos> chests = new HashSet<>();
    for (Station supply : supplies)
      for (BlockPos p : supply.chests) {
        if ((supply.flags(p) & 2) == 0 || !l.hasChunkAt(p) || !chests.add(Storage.canonical(l, p)))
          continue;
        if (warehouse
            && ConstructionSupply.reserved(l, p)
            && (project == null || !p.equals(project.supplies.chest))) continue;
        var h = Storage.handler(l, p, supply);
        if (h != null) {
          if (warehouse) BuildingProject.countConstructionSupply(h, have);
          else BuildingProject.count(h, have);
        }
      }
    if (project != null && project.supplies.chest != null && chests.add(project.supplies.chest)) {
      var stock = project.supplies.handler(l);
      if (stock != null) BuildingProject.count(stock, have);
    }
    ListTag rows = new ListTag();
    total.forEach(
        (item, amount) -> {
          CompoundTag row = new CompoundTag();
          row.put("Item", new ItemStack(item).save(new CompoundTag()));
          row.putInt("Total", amount);
          row.putInt("Left", left.getOrDefault(item, 0));
          row.putInt("Have", have.getOrDefault(item, 0));
          row.putInt(
              "Missing", Math.max(0, left.getOrDefault(item, 0) - have.getOrDefault(item, 0)));
          rows.add(row);
        });
    return rows;
  }
}
