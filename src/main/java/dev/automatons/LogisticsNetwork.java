package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraftforge.items.*;

/** Dimension-local telemetry and shared reservations. Items exist only in real handlers/cargo. */
public final class LogisticsNetwork {
  public enum Phase {
    QUEUED,
    RESERVED,
    TO_SOURCE,
    CARRYING,
    TO_DESTINATION,
    DELIVERING,
    BLOCKED,
    COMPLETED
  }

  public record Endpoint(UUID totem, BlockPos pos, UUID worker) {
    CompoundTag save() {
      CompoundTag n = new CompoundTag();
      if (totem != null) n.putUUID("Totem", totem);
      if (pos != null) n.putLong("Pos", pos.asLong());
      if (worker != null) n.putUUID("Worker", worker);
      return n;
    }

    static Endpoint load(CompoundTag n) {
      return new Endpoint(
          n.hasUUID("Totem") ? n.getUUID("Totem") : null,
          n.contains("Pos") ? BlockPos.of(n.getLong("Pos")) : null,
          n.hasUUID("Worker") ? n.getUUID("Worker") : null);
    }
  }

  public static final class Demand {
    public String id, kind;
    public Endpoint source;
    public UUID worker, totem;
    public Item item;
    public int quantity, priority;
    public long born;
  }

  public static final class Report {
    public Endpoint endpoint;
    public boolean loaded, warehouse, output, supply;
    public int revision, usableSlots;
    public List<ItemStack> contents = List.of();
    public Map<Item, Integer> reserves = Map.of();
    public Set<UUID> sites = Set.of(), supplySites = Set.of();
  }

  public static final class Parcel {
    public UUID runner;
    public String request;
    public Endpoint source, destination;
    public ItemStack item = ItemStack.EMPTY;
    public int quantity, picked, delivered;
    public long touched;
    public Phase phase = Phase.RESERVED;
    public boolean returning;

    public int outstanding() {
      return Math.max(0, picked - delivered);
    }

    CompoundTag save() {
      CompoundTag n = new CompoundTag();
      n.putUUID("Runner", runner);
      n.putString("Request", request);
      n.put("Source", source.save());
      n.put("Destination", destination.save());
      n.put("Item", item.save(new CompoundTag()));
      n.putInt("Quantity", quantity);
      n.putInt("Picked", picked);
      n.putInt("Delivered", delivered);
      n.putLong("Touched", touched);
      n.putString("Phase", phase.name());
      n.putBoolean("Returning", returning);
      return n;
    }

    static Parcel load(CompoundTag n) {
      Parcel p = new Parcel();
      p.runner = n.getUUID("Runner");
      p.request = n.getString("Request");
      p.source = Endpoint.load(n.getCompound("Source"));
      p.destination = Endpoint.load(n.getCompound("Destination"));
      p.item = ItemStack.of(n.getCompound("Item"));
      p.quantity = n.getInt("Quantity");
      p.picked = n.getInt("Picked");
      p.delivered = n.getInt("Delivered");
      p.touched = n.getLong("Touched");
      try {
        p.phase = Phase.valueOf(n.getString("Phase"));
      } catch (Exception e) {
        p.phase = Phase.BLOCKED;
      }
      p.returning = n.getBoolean("Returning");
      return p;
    }
  }

  public UUID home, owner;
  public final Map<String, Demand> demands = new LinkedHashMap<>();
  public final Map<UUID, Parcel> parcels = new HashMap<>();
  public final Map<String, Report> reports = new LinkedHashMap<>();
  public final Map<Long, ForestryHelp.Request> obstacles = new LinkedHashMap<>();
  private final Map<UUID, Station> stations = new HashMap<>();
  private long nextRefresh;
  private int dispatches, collectionTurn;
  private int reportCursor, scanSlots;

  private record ReportTarget(
      Station station, BlockPos pos, boolean warehouse, IItemHandler handler) {}

  /** These supplies belong to the workers; couriers never withdraw them from storage. */
  public static boolean leaveInChest(ItemStack stack) {
    return stack.is(Automatons.HAMMER.get())
        || stack.is(net.minecraft.tags.ItemTags.TOOLS)
        || stack.getItem() instanceof TieredItem
        || stack.getItem() instanceof ShearsItem
        || stack.getItem() instanceof FishingRodItem
        || stack.getItem() instanceof FlintAndSteelItem
        || stack.getItem() instanceof BrushItem
        || stack.is(Items.TORCH)
        || stack.is(Items.SOUL_TORCH)
        || stack.is(Items.REDSTONE_TORCH)
        || stack.is(net.minecraft.tags.ItemTags.SAPLINGS);
  }

  public static LogisticsNetwork get(Lumberton w) {
    WorldData data = WorldData.get((ServerLevel) w.level());
    LogisticsNetwork n =
        data.logistics.computeIfAbsent(
            w.homeId,
            id -> {
              LogisticsNetwork created = new LogisticsNetwork();
              created.home = id;
              created.owner = w.owner;
              return created;
            });
    return n;
  }

  private void dirty(ServerLevel l) {
    WorldData.get(l).setDirty();
  }

  public void removeSite(UUID identity) {
    obstacles.values().removeIf(r -> identity.equals(r.site));
    stations.remove(identity);
    demands.values().removeIf(d -> identity.equals(d.totem));
    reports.values().removeIf(r -> identity.equals(r.endpoint.totem));
    parcels
        .values()
        .removeIf(
            p -> {
              if (!identity.equals(p.source.totem) && !identity.equals(p.destination.totem))
                return false;
              if (p.outstanding() == 0) return true;
              p.returning = true;
              return false;
            });
    nextRefresh = 0;
  }

  private static String key(Endpoint e) {
    if (e.worker != null) return "entity:" + e.worker;
    return "container:" + e.pos;
  }

  private static boolean sameInventory(Endpoint a, Endpoint b) {
    return a.worker != null || b.worker != null
        ? Objects.equals(a.worker, b.worker)
        : Objects.equals(a.pos, b.pos);
  }

  public boolean member(Lumberton w) {
    return Objects.equals(owner, w.owner)
        && Objects.equals(home, w.homeId)
        && (w.homeDimension.isEmpty()
            || w.homeDimension.equals(w.level().dimension().location().toString()));
  }

  public void refresh(ServerLevel l) {
    if (l.getGameTime() < nextRefresh) return;
    nextRefresh = l.getGameTime() + Balance.LOGISTICS_INTERVAL.get();
    stations.clear();
    List<Lumberton> workers = new ArrayList<>();
    Ledger ledger = Ledger.get(l.getServer());
    for (var entry : ledger.workers.entrySet()) {
      var n = entry.getValue();
      if (!n.hasUUID("HomeId")
          || !home.equals(n.getUUID("HomeId"))
          || !n.hasUUID("Owner")
          || !owner.equals(n.getUUID("Owner"))
          || !n.getString("Dimension").equals(l.dimension().location().toString())
          || !n.getString("State").equals("Deployed")) continue;
      if (l.getEntity(entry.getKey()) instanceof Lumberton w
          && member(w)
          && w.isAlive()
          && !w.packing) workers.add(w);
      if (n.contains("Totem") && n.hasUUID("TotemId")) {
        BlockPos p = BlockPos.of(n.getLong("Totem"));
        if (l.hasChunkAt(p)
            && l.getBlockEntity(p) instanceof Station s
            && s.identity.equals(n.getUUID("TotemId"))
            && owner.equals(s.owner)
            && (s.logisticsHome == null || home.equals(s.logisticsHome)))
          stations.put(s.identity, s);
      }
    }
    for (BuildingProject project : WorldData.get(l).buildings.values())
      if (home.equals(project.home)
          && owner.equals(project.owner)
          && l.hasChunkAt(project.anchor)
          && l.getBlockEntity(project.anchor) instanceof Station site
          && site.identity.equals(project.totem)) stations.put(site.identity, site);
    Map<String, Demand> previous = new HashMap<>(demands);
    Map<String, Demand> next = new LinkedHashMap<>();
    for (Lumberton w : workers) {
      if (!w.authorized || w.job() == 0 || w.station() == null) continue;
      if (w.job() == WorkerJob.FARMINGTON) {
        for (var need : w.farming.reserve().entrySet()) {
          int missing = need.getValue() - w.mining.carried(need.getKey());
          if (missing > 0)
            demand(
                next,
                w,
                need.getKey() == Items.BONE_MEAL ? "optional" : "planting",
                need.getKey(),
                missing,
                need.getKey() == Items.BONE_MEAL ? 3 : 1,
                l);
        }
        continue;
      }
      if (w.job() == WorkerJob.BUILDINGTON) {
        BuildingProject building = w.building.active();
        if (building != null && !building.ready && w.getUUID().equals(building.builder)) {
          Map<Item, Integer> nearby = new HashMap<>();
          BuildingProject.count(w.cargo, nearby);
          var staged = building.supplies.handler(l);
          if (staged != null) BuildingProject.count(staged, nearby);
          for (var material : building.supplies.needed(l).entrySet()) {
            int missing = material.getValue() - nearby.getOrDefault(material.getKey(), 0);
            if (missing > 0) demand(next, w, "material", material.getKey(), missing, 0, l);
          }
        }
        continue;
      }
      if (w.job() == 1 && !Inventories.axe(w.activeTool()) || w.job() == 2 && w.mining.needsTool())
        demand(next, w, "tool", null, 1, 0, l);
      if (w.job() == 2)
        for (var e : w.mining.requiredStock().entrySet()) {
          int missing = e.getValue() - w.mining.carried(e.getKey());
          if (missing > 0)
            demand(
                next,
                w,
                e.getKey() == Items.TORCH ? "torch" : "material",
                e.getKey(),
                missing,
                (w.alerts & ~Lumberton.WOOD) != 0 ? 0 : 1,
                l);
        }
    }
    demands.clear();
    demands.putAll(next);
    // Reports remain explicitly unavailable across unloads; an unload never means an empty chest.
    reports
        .values()
        .forEach(
            r ->
                r.loaded =
                    r.loaded
                        && handler(l, r.endpoint, ItemStack.EMPTY) != null
                        && (r.endpoint.worker != null
                            || Storage.canonical(l, r.endpoint.pos).equals(r.endpoint.pos)));
    List<ReportTarget> targets = new ArrayList<>();
    for (Station s :
        stations.values().stream().sorted(Comparator.comparing(x -> x.identity)).toList()) {
      BuildingProject warehouse = WorldData.get(l).buildings.get(s.identity);
      if (warehouse != null
          && warehouse.purpose == WorksitePurpose.WAREHOUSE
          && home.equals(warehouse.home)) {
        if (warehouse.ready)
          for (BlockPos p : warehouse.chests(l))
            targets.add(new ReportTarget(s, p, true, Storage.handler(l, p, s)));
      } else {
        for (BlockPos p : s.chests)
          targets.add(new ReportTarget(s, p, false, Storage.handler(l, p, s)));
      }
      if (warehouse != null && warehouse.supplies.chest != null)
        targets.add(
            new ReportTarget(s, warehouse.supplies.chest, false, warehouse.supplies.handler(l)));
      if (s.mine != null && s.mine.depotBuilt)
        for (BlockPos p : s.mine.depotPositions())
          targets.add(new ReportTarget(s, p, false, Storage.depotHandler(l, p, s.mine)));
    }
    Map<BlockPos, List<ReportTarget>> groups = new LinkedHashMap<>();
    for (var target : targets)
      if (l.hasChunkAt(target.pos) && Storage.canonical(l, target.pos).equals(target.pos))
        groups.computeIfAbsent(target.pos, p -> new ArrayList<>()).add(target);
    targets = new ArrayList<>();
    for (var group : groups.values())
      targets.add(group.stream().filter(t -> t.warehouse).findFirst().orElse(group.get(0)));
    scanSlots = Balance.LOGISTICS_SLOTS.get();
    int start = targets.isEmpty() ? 0 : Math.floorMod(reportCursor, targets.size());
    for (int i = 0; i < targets.size(); i++) {
      int index = (start + i) % targets.size();
      var target = targets.get(index);
      if (report(
          l,
          target.station,
          target.pos,
          target.warehouse,
          target.handler,
          workers,
          groups.get(target.pos))) reportCursor = (index + 1) % targets.size();
    }
    var machineHome = WorldData.get(l).homes.get(home);
    if (machineHome != null)
      for (var machine :
          l.getEntitiesOfClass(
              Machine.class,
              new net.minecraft.world.phys.AABB(machineHome.pos()).inflate(MachineRules.RANGE + 4)))
        if (Objects.equals(machine.homeId, home) && Objects.equals(machine.owner, owner))
          MachineHauling.publish(this, machine);
    reserveSiteStocks(l, workers);
    // Output requests use the same stable queue and aging as shortages, but never
    // masquerade as a worker equipment request or as a second item inventory.
    for (Report r : reports.values())
      if (r.loaded && r.output && !r.warehouse) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : r.contents)
          if (!stack.isEmpty() && !leaveInChest(stack))
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        for (var entry : counts.entrySet()) {
          int quantity = entry.getValue() - r.reserves.getOrDefault(entry.getKey(), 0);
          if (quantity <= 0) continue;
          String id =
              "collect:"
                  + key(r.endpoint)
                  + ":"
                  + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey());
          Demand d = previous.getOrDefault(id, new Demand());
          d.id = id;
          d.kind = r.usableSlots == 0 ? "output_full" : "collectible";
          d.source = r.endpoint;
          d.totem = r.endpoint.totem;
          d.item = entry.getKey();
          d.quantity = quantity;
          d.priority = r.usableSlots == 0 ? 1 : 2;
          if (d.born == 0) d.born = l.getGameTime();
          demands.put(id, d);
        }
      }
    parcels
        .entrySet()
        .removeIf(
            e -> {
              Parcel p = e.getValue();
              CompoundTag record = ledger.workers.get(e.getKey());
              if (record == null) return false;
              // Packed/dead cargo follows worker inventory drops; never put its parcel back at the
              // source.
              if (!record.getString("State").equals("Deployed")
                  || record.getInt("Job") != 3
                  || !record.hasUUID("HomeId")
                  || !home.equals(record.getUUID("HomeId"))) return true;
              return p.outstanding() == 0 && l.getGameTime() - p.touched > 200;
            });
    dirty(l);
  }

  private void demand(
      Map<String, Demand> next,
      Lumberton w,
      String kind,
      Item item,
      int amount,
      int priority,
      ServerLevel l) {
    String id =
        w.getUUID()
            + ":"
            + kind
            + ":"
            + (item == null
                ? "tool"
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
    Demand d = demands.getOrDefault(id, new Demand());
    d.id = id;
    d.worker = w.getUUID();
    d.totem = w.totemId;
    d.kind = kind;
    d.item = item;
    d.quantity = amount;
    d.priority = priority;
    if (d.born == 0) d.born = l.getGameTime();
    next.put(id, d);
  }

  /**
   * Allocate one physical reserve across the site's containers; never reserve a full field per
   * chest.
   */
  private void reserveSiteStocks(ServerLevel l, List<Lumberton> workers) {
    for (Station station : stations.values()) {
      Map<Item, Integer> required = new LinkedHashMap<>();
      if (station.farm != null) required.putAll(station.farm.reserves(l));
      for (Lumberton worker : workers)
        if (station.identity.equals(worker.totemId)) {
          if (worker.job() == WorkerJob.FARMINGTON)
            required.replaceAll((item, count) -> Math.max(0, count - worker.mining.carried(item)));
          if (worker.job() == WorkerJob.BUILDINGTON && worker.building.active() != null)
            worker
                .building
                .active()
                .remaining(l)
                .forEach(
                    (item, count) ->
                        required.merge(
                            item, Math.max(0, count - worker.mining.carried(item)), Integer::sum));
        }
      var project = station.project();
      if (project != null && !project.ready && station.purpose != WorksitePurpose.BUILDER)
        project.remaining(l).forEach((item, count) -> required.merge(item, count, Math::max));
      for (Report report :
          reports.values().stream()
              .filter(r -> r.loaded && r.sites.contains(station.identity))
              .sorted(Comparator.comparing(r -> key(r.endpoint)))
              .toList()) {
        Map<Item, Integer> actual = new HashMap<>();
        var handler = handler(l, report.endpoint, ItemStack.EMPTY);
        if (handler == null) continue;
        BuildingProject.count(handler, actual);
        required.replaceAll(
            (item, count) -> {
              int reserved = Math.min(count, actual.getOrDefault(item, 0));
              report.reserves.merge(item, reserved, Math::max);
              return count - reserved;
            });
      }
    }
  }

  private boolean report(
      ServerLevel l,
      Station s,
      BlockPos p,
      boolean warehouse,
      IItemHandler handler,
      List<Lumberton> workers,
      List<ReportTarget> aliases) {
    if (!l.hasChunkAt(p)) return false;
    BlockPos canonical = Storage.canonical(l, p);
    if (!canonical.equals(p)) return false;
    Endpoint endpoint = new Endpoint(s.identity, canonical, null);
    Report report = reports.computeIfAbsent(key(endpoint), id -> new Report());
    report.endpoint = endpoint;
    report.warehouse = warehouse;
    Set<UUID> sites = new HashSet<>(), supplies = new HashSet<>();
    for (var alias : aliases) {
      sites.add(alias.station.identity);
      if (alias.warehouse || (alias.station.flags(p) & 2) != 0)
        supplies.add(alias.station.identity);
    }
    report.sites = sites;
    report.supplySites = supplies;
    report.output =
        !warehouse
            && aliases.stream()
                .anyMatch(
                    a ->
                        (a.station.mine != null && a.station.mine.depotCell(p))
                            || (a.station.flags(p) & 1) != 0);
    if (ConstructionSupply.reserved(l, p)) report.output = false;
    report.supply = !supplies.isEmpty() && !ConstructionSupply.reserved(l, p);
    if (handler == null) {
      report.loaded = false;
      return false;
    }
    if (handler.getSlots() > scanSlots) return false;
    scanSlots -= handler.getSlots();
    List<ItemStack> contents = new ArrayList<>();
    int fingerprint = 1, space = 0;
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack stack = handler.getStackInSlot(i).copy();
      contents.add(stack);
      fingerprint = 31 * fingerprint + stack.save(new CompoundTag()).hashCode();
      if (stack.isEmpty()
          || stack.getCount() < Math.min(stack.getMaxStackSize(), handler.getSlotLimit(i))) space++;
    }
    report.contents = contents;
    report.revision = fingerprint;
    report.usableSlots = space;
    report.loaded = true;
    Map<Item, Integer> reserves = new HashMap<>();
    for (Lumberton w : workers)
      if (sites.contains(w.totemId) && w.job() == 2)
        w.mining.requiredStock().forEach((item, count) -> reserves.merge(item, count, Math::max));
    report.reserves = reserves;
    return true;
  }

  public IItemHandler handler(ServerLevel l, Endpoint e, ItemStack item) {
    if (e.worker != null) {
      if (l.getEntity(e.worker) instanceof Machine m)
        return Objects.equals(home, m.homeId) && Objects.equals(owner, m.owner) ? m.cargo : null;
      if (!(l.getEntity(e.worker) instanceof Lumberton w)
          || !member(w)
          || !w.isAlive()
          || w.packing
          || !Objects.equals(e.totem, w.totemId)) return null;
      if (Inventories.tool(w.job(), item)) return w.tools;
      return w.job() == 2 && w.mining.wants(item)
              || w.job() == WorkerJob.FARMINGTON && w.farming.reserve().containsKey(item.getItem())
              || w.job() == WorkerJob.BUILDINGTON
                  && w.building.active() != null
                  && !w.building.active().ready
          ? w.cargo
          : null;
    }
    Station s = stations.get(e.totem);
    if (s == null
        || s.isRemoved()
        || !l.hasChunkAt(s.getBlockPos())
        || l.getBlockEntity(s.getBlockPos()) != s
        || !owner.equals(s.owner)) return null;
    BuildingProject warehouse = WorldData.get(l).buildings.get(e.totem);
    if (warehouse != null && e.pos.equals(warehouse.supplies.chest))
      return home.equals(warehouse.home) ? warehouse.supplies.handler(l) : null;
    if (s.mine != null && s.mine.depotCell(e.pos)) return Storage.depotHandler(l, e.pos, s.mine);
    if (warehouse != null && warehouse.ready && warehouse.chests(l).contains(e.pos))
      return home.equals(warehouse.home) ? Storage.handler(l, e.pos, s) : null;
    return Storage.handler(l, e.pos, s);
  }

  public Station station(UUID id) {
    return stations.get(id);
  }

  public BlockPos position(ServerLevel l, Endpoint e) {
    if (e.worker != null
        && l.getEntity(e.worker) instanceof Machine m
        && Objects.equals(home, m.homeId)
        && Objects.equals(owner, m.owner)) return m.grid.relative(m.heading.getClockWise(), 2);
    return e.worker == null
        ? e.pos
        : l.getEntity(e.worker) instanceof Lumberton w && member(w) ? w.blockPosition() : null;
  }

  public boolean matches(ServerLevel l, Demand d, ItemStack stack) {
    if (stack.isEmpty()) return false;
    if (d.item != null) return stack.is(d.item);
    return l.getEntity(d.worker) instanceof Lumberton w
        && member(w)
        && (w.job() == 2
            ? w.mining.acceptsNeededTool(stack)
            : w.job() == 3 ? Inventories.pick(stack) : Inventories.tool(w.job(), stack));
  }

  public int neededNow(ServerLevel l, Demand d) {
    if (d == null
        || !(l.getEntity(d.worker) instanceof Lumberton w)
        || !member(w)
        || !w.authorized
        || w.packing
        || !Objects.equals(w.totemId, d.totem)) return 0;
    if (d.item != null)
      return w.job() == WorkerJob.FARMINGTON
          ? Math.max(
              0,
              w.farming.reserve().getOrDefault(d.item, 0)
                  - w.mining.carried(d.item)
                  - localStock(l, d))
          : w.job() == 2
              ? Math.max(
                  0,
                  w.mining.requiredStock().getOrDefault(d.item, 0)
                      - w.mining.carried(d.item)
                      - localStock(l, d))
              : w.job() == WorkerJob.BUILDINGTON
                      && w.building.active() != null
                      && !w.building.active().ready
                  ? Math.max(
                      0,
                      w.building.active().supplies.needed(l).getOrDefault(d.item, 0)
                          - w.mining.carried(d.item)
                          - w.building.active().supplies.count(l, d.item))
                  : 0;
    if (localStock(l, d) > 0) return 0;
    return w.job() == 1 && !Inventories.axe(w.activeTool()) || w.job() >= 2 && w.mining.needsTool()
        ? 1
        : 0;
  }

  private int localStock(ServerLevel l, Demand d) {
    int available = 0;
    for (Report r : reports.values())
      if (r.loaded && r.supply && r.supplySites.contains(d.totem))
        for (ItemStack item : r.contents) if (matches(l, d, item)) available += item.getCount();
    return available;
  }

  private int reservedSource(Endpoint e, ItemStack item) {
    return reservedSource(e, item, null);
  }

  private int reservedSource(Endpoint e, ItemStack item, UUID except) {
    return parcels.values().stream()
        .filter(
            p ->
                !Objects.equals(p.runner, except)
                    && p.outstanding() == 0
                    && p.phase != Phase.COMPLETED
                    && sameInventory(p.source, e)
                    && ItemStack.isSameItemSameTags(p.item, item))
        .mapToInt(p -> p.quantity)
        .sum();
  }

  public int capacity(ServerLevel l, Endpoint e, ItemStack item, UUID except) {
    IItemHandler h = handler(l, e, item);
    if (h == null) return 0;
    ItemStackHandler copy = new ItemStackHandler(h.getSlots());
    for (int i = 0; i < h.getSlots(); i++) copy.setStackInSlot(i, h.getStackInSlot(i).copy());
    for (Parcel p : parcels.values())
      if (!p.runner.equals(except)
          && p.phase != Phase.COMPLETED
          && sameInventory(p.destination, e)
          && l.getGameTime() - p.touched <= 200)
        virtualInsert(copy, h, p.item.copyWithCount(Math.max(0, p.quantity - p.delivered)));
    int total = 0;
    for (int i = 0; i < copy.getSlots(); i++) {
      ItemStack stored = copy.getStackInSlot(i);
      if (!h.isItemValid(i, item)
          || !stored.isEmpty() && !ItemStack.isSameItemSameTags(stored, item)) continue;
      int limit = Math.min(item.getMaxStackSize(), h.getSlotLimit(i));
      total += Math.max(0, limit - stored.getCount());
    }
    return total;
  }

  private static void virtualInsert(ItemStackHandler copy, IItemHandler real, ItemStack stack) {
    int left = stack.getCount();
    for (int i = 0; i < copy.getSlots() && left > 0; i++) {
      var old = copy.getStackInSlot(i);
      if (!real.isItemValid(i, stack)
          || !old.isEmpty() && !ItemStack.isSameItemSameTags(old, stack)) continue;
      int n =
          Math.min(
              left,
              Math.max(
                  0, Math.min(stack.getMaxStackSize(), real.getSlotLimit(i)) - old.getCount()));
      if (n > 0) {
        copy.setStackInSlot(i, stack.copyWithCount(old.getCount() + n));
        left -= n;
      }
    }
  }

  public Endpoint warehouseDestination(ServerLevel l, ItemStack item, UUID except) {
    return reports.values().stream()
        .filter(r -> r.loaded && r.warehouse && capacity(l, r.endpoint, item, except) > 0)
        .sorted(Comparator.comparing(r -> key(r.endpoint)))
        .map(r -> r.endpoint)
        .findFirst()
        .orElse(null);
  }

  public Parcel claim(Lumberton runner) {
    ServerLevel l = (ServerLevel) runner.level();
    refresh(l);
    if (!member(runner) || parcels.containsKey(runner.getUUID()))
      return parcels.get(runner.getUUID());
    if (++dispatches % 4 == 0) {
      Parcel collection = collect(runner);
      if (collection != null) return collection;
    }
    List<Demand> ordered =
        demands.values().stream()
            .filter(d -> d.source == null && d.worker != null)
            .filter(
                d ->
                    parcels.values().stream()
                        .noneMatch(
                            p ->
                                p.request.equals(d.id)
                                    && p.phase != Phase.COMPLETED
                                    && l.getGameTime() - p.touched <= 200))
            .sorted(
                Comparator.comparingLong(
                        (Demand d) -> d.priority * 600L - (l.getGameTime() - d.born))
                    .thenComparing(d -> d.id))
            .toList();
    for (Demand d : ordered) {
      BuildingProject construction = construction(l, d);
      for (Report r : reports.values()) {
        if (!r.loaded || !r.supply || construction == null && r.supplySites.contains(d.totem))
          continue;
        for (ItemStack stack : r.contents)
          if (!leaveInChest(stack) && matches(l, d, stack)) {
            Endpoint to =
                construction != null && construction.supplies.handler(l) != null
                    ? new Endpoint(construction.totem, construction.supplies.chest, null)
                    : new Endpoint(d.totem, null, d.worker);
            int available = available(l, r, stack, null, true, construction);
            int space = capacity(l, to, stack, runner.getUUID());
            if (space == 0 && construction == null)
              for (Report local : reports.values())
                if (local.loaded && local.supply && local.supplySites.contains(d.totem)) {
                  int capacity = capacity(l, local.endpoint, stack, runner.getUUID());
                  if (capacity > 0) {
                    to = local.endpoint;
                    space = capacity;
                    break;
                  }
                }
            int carriedSpace =
                stack.getCount() - Inventories.insert(runner.cargo, stack, true).getCount();
            int quantity =
                Math.min(Math.min(neededNow(l, d), available), Math.min(space, carriedSpace));
            if (quantity > 0) return reserve(runner, d.id, r.endpoint, to, stack, quantity);
          }
      }
    }
    return collect(runner);
  }

  private Parcel collect(Lumberton runner) {
    ServerLevel l = (ServerLevel) runner.level();
    List<Report> output =
        reports.values().stream()
            .filter(r -> r.loaded && r.output && !r.warehouse)
            .sorted(Comparator.comparingInt(r -> r.usableSlots == 0 ? 0 : 1))
            .toList();
    for (int turn = 0; turn < output.size(); turn++) {
      Report r = output.get(Math.floorMod(collectionTurn + turn, output.size()));
      for (ItemStack stack : r.contents) {
        if (stack.isEmpty() || leaveInChest(stack)) continue;
        int available = available(l, r, stack, false);
        if (available <= 0) continue;
        Endpoint to = warehouseDestination(l, stack, runner.getUUID());
        if (to == null) continue;
        int max = Math.min(available, Balance.RUNNER_BATCH.get());
        ItemStack probe = stack.copyWithCount(max);
        int quantity =
            Math.min(
                max - Inventories.insert(runner.cargo, probe, true).getCount(),
                capacity(l, to, stack, runner.getUUID()));
        if (quantity > 0) {
          collectionTurn += turn + 1;
          return reserve(
              runner,
              "collect:"
                  + key(r.endpoint)
                  + ":"
                  + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
              r.endpoint,
              to,
              stack,
              quantity);
        }
      }
    }
    return null;
  }

  private int available(ServerLevel l, Report r, ItemStack item, boolean delivery) {
    return available(l, r, item, null, false);
  }

  /**
   * Final source check excludes this parcel's own reservation and rechecks changing field reserves.
   */
  public int pickupAllowance(ServerLevel l, Parcel parcel) {
    Report report = reports.get(key(parcel.source));
    return report == null || !report.loaded
        ? 0
        : available(
            l,
            report,
            parcel.item,
            parcel.runner,
            true,
            construction(l, demands.get(parcel.request)));
  }

  private BuildingProject construction(ServerLevel l, Demand demand) {
    if (demand == null
        || demand.worker == null
        || !(l.getEntity(demand.worker) instanceof Lumberton worker)
        || !member(worker)
        || worker.job() != WorkerJob.BUILDINGTON) return null;
    var project = worker.building.active();
    return project != null && !project.ready ? project : null;
  }

  private int available(ServerLevel l, Report r, ItemStack item, UUID except, boolean committing) {
    return available(l, r, item, except, committing, null);
  }

  private int available(
      ServerLevel l,
      Report r,
      ItemStack item,
      UUID except,
      boolean committing,
      BuildingProject construction) {
    if (leaveInChest(item) || ConstructionSupply.reserved(l, r.endpoint.pos)) return 0;
    IItemHandler h = handler(l, r.endpoint, item);
    if (h == null) return 0;
    int same = 0, total = 0;
    for (int i = 0; i < h.getSlots(); i++) {
      ItemStack s = h.getStackInSlot(i);
      if (s.is(item.getItem())) total += s.getCount();
      if (ItemStack.isSameItemSameTags(s, item)) same += s.getCount();
    }
    int keep = r.reserves.getOrDefault(item.getItem(), 0);
    if (construction != null) {
      // Moving a project's own reserved stock into its chest is permitted; all other
      // projects, farmers and miners retain their current physical reserves.
      keep = freshWorksiteReserve(l, r, item.getItem(), construction.id);
      for (UUID siteId : r.sites) {
        Station site = stations.get(siteId);
        if (site != null)
          for (Lumberton worker : site.loadedWorkers())
            if (member(worker) && worker.job() == WorkerJob.MININGTON)
              keep = Math.max(keep, worker.mining.requiredStock().getOrDefault(item.getItem(), 0));
      }
    } else if (committing) keep = Math.max(keep, freshWorksiteReserve(l, r, item.getItem(), null));
    return Math.max(0, Math.min(same, total - keep) - reservedSource(r.endpoint, item, except));
  }

  private int freshWorksiteReserve(ServerLevel l, Report source, Item item, UUID exceptProject) {
    int required = 0;
    Set<UUID> countedWorkers = new HashSet<>(), projects = new HashSet<>();
    for (UUID id : source.sites) {
      Station site = stations.get(id);
      if (site == null) continue;
      if (site.farm != null) required += site.farm.reserves(l).getOrDefault(item, 0);
      var local = site.project();
      if (local != null
          && !local.ready
          && !local.id.equals(exceptProject)
          && projects.add(local.id)) required += local.remaining(l).getOrDefault(item, 0);
      for (Lumberton worker : site.loadedWorkers()) {
        if (!member(worker)
            || worker.job() != WorkerJob.FARMINGTON && worker.job() != WorkerJob.BUILDINGTON
            || !countedWorkers.add(worker.getUUID())) continue;
        if (worker.job() == WorkerJob.BUILDINGTON) {
          var active = worker.building.active();
          if (active != null && active.id.equals(exceptProject)) continue;
          if (active != null && !active.ready && projects.add(active.id))
            required += active.remaining(l).getOrDefault(item, 0);
        }
        if (worker.job() == WorkerJob.FARMINGTON || worker.job() == WorkerJob.BUILDINGTON)
          required -= worker.mining.carried(item);
      }
    }
    for (Parcel parcel : parcels.values())
      if (!parcel.returning
          && parcel.item.is(item)
          && countedWorkers.contains(parcel.destination.worker)) required -= parcel.outstanding();
    // A bounded fresh scan can undercount distant stock, which conservatively keeps extra reserve.
    int budget = Balance.LOGISTICS_SLOTS.get();
    for (Report other : reports.values()) {
      if (required <= 0 || budget <= 0) break;
      if (!other.loaded
          || sameInventory(source.endpoint, other.endpoint)
          || Collections.disjoint(source.sites, other.sites)) continue;
      var stock = handler(l, other.endpoint, ItemStack.EMPTY);
      if (stock == null) continue;
      for (int slot = 0; slot < stock.getSlots() && budget-- > 0; slot++)
        if (stock.getStackInSlot(slot).is(item)) required -= stock.getStackInSlot(slot).getCount();
    }
    return Math.max(0, required);
  }

  private Parcel reserve(
      Lumberton w, String request, Endpoint source, Endpoint to, ItemStack stack, int count) {
    Parcel p = new Parcel();
    p.runner = w.getUUID();
    p.request = request;
    p.source = source;
    p.destination = to;
    p.item = stack.copyWithCount(1);
    p.quantity = count;
    p.touched = w.level().getGameTime();
    parcels.put(p.runner, p);
    dirty((ServerLevel) w.level());
    return p;
  }

  public void release(Lumberton w) {
    parcels.remove(w.getUUID());
    if (w.level() instanceof ServerLevel l) dirty(l);
  }

  public void changed(ServerLevel l) {
    nextRefresh = 0;
    dirty(l);
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    n.putUUID("Home", home);
    n.putUUID("Owner", owner);
    n.putInt("Dispatches", dispatches);
    n.putInt("CollectionTurn", collectionTurn);
    ListTag list = new ListTag();
    parcels.values().forEach(p -> list.add(p.save()));
    n.put("Parcels", list);
    ListTag aging = new ListTag();
    demands
        .values()
        .forEach(
            d -> {
              CompoundTag a = new CompoundTag();
              a.putString("Id", d.id);
              a.putLong("Born", d.born);
              aging.add(a);
            });
    n.put("Aging", aging);
    ListTag clearing = new ListTag();
    obstacles.values().forEach(r -> clearing.add(r.save()));
    n.put("ForestryObstacles", clearing);
    return n;
  }

  public static LogisticsNetwork load(CompoundTag n) {
    LogisticsNetwork network = new LogisticsNetwork();
    network.home = n.getUUID("Home");
    network.owner = n.getUUID("Owner");
    network.dispatches = n.getInt("Dispatches");
    network.collectionTurn = n.getInt("CollectionTurn");
    for (Tag tag : n.getList("ForestryObstacles", 10)) {
      ForestryHelp.Request r = ForestryHelp.Request.load((CompoundTag) tag);
      if (network.obstacles.size() < 256) network.obstacles.put(r.pos.asLong(), r);
    }
    for (Tag t : n.getList("Parcels", 10)) {
      Parcel p = Parcel.load((CompoundTag) t);
      network.parcels.put(p.runner, p);
    }
    for (Tag t : n.getList("Aging", 10)) {
      CompoundTag a = (CompoundTag) t;
      Demand d = new Demand();
      d.id = a.getString("Id");
      d.born = a.getLong("Born");
      network.demands.put(d.id, d);
    }
    return network;
  }
}
