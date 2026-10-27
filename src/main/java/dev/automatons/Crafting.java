package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;

public final class Crafting {
  private final Lumberton worker;
  public final Map<Item, Integer> requested = new LinkedHashMap<>();
  public final Map<Item, Integer> held = new LinkedHashMap<>();
  private Item activeGoal = Items.AIR;
  private long nextTick;

  private record Stock(
      Station site,
      BlockPos position,
      IItemHandler items,
      boolean local,
      LogisticsNetwork.Report report) {}

  private record Order(Item item, int amount, int priority, EquipmentSlot display) {}

  public Crafting(Lumberton worker) {
    this.worker = worker;
  }

  public CompoundTag save() {
    var n = new CompoundTag();
    ListTag needs = new ListTag();
    requested.forEach(
        (item, count) -> needs.add(new ItemStack(item, count).save(new CompoundTag())));
    n.put("Needs", needs);
    ListTag reserves = new ListTag();
    held.forEach((item, count) -> reserves.add(new ItemStack(item, count).save(new CompoundTag())));
    n.put("Held", reserves);
    n.put("Goal", new ItemStack(activeGoal).save(new CompoundTag()));
    return n;
  }

  public void load(CompoundTag n) {
    requested.clear();
    for (Tag value : n.getList("Needs", 10)) {
      ItemStack stack = ItemStack.of((CompoundTag) value);
      if (!stack.isEmpty()) requested.put(stack.getItem(), Math.min(64, stack.getCount()));
    }
    held.clear();
    for (Tag value : n.getList("Held", 10)) {
      ItemStack stack = ItemStack.of((CompoundTag) value);
      if (!stack.isEmpty()) held.put(stack.getItem(), Math.min(64, stack.getCount()));
    }
    activeGoal = ItemStack.of(n.getCompound("Goal")).getItem();
  }

  public boolean courierExists() {
    return Ledger.get(worker.getServer()).workers.values().stream()
        .anyMatch(
            n ->
                n.getInt("Job") == WorkerJob.RUNNERTON
                    && n.hasUUID("Owner")
                    && n.getUUID("Owner").equals(worker.owner)
                    && n.hasUUID("HomeId")
                    && n.getUUID("HomeId").equals(worker.homeId)
                    && n.getString("State").equals("Deployed"));
  }

  private List<Stock> stock(Station site, LogisticsNetwork network) {
    ServerLevel level = (ServerLevel) worker.level();
    List<Stock> result = new ArrayList<>();
    Set<BlockPos> seen = new HashSet<>();
    for (BlockPos pos : site.chests) {
      var h = Storage.handler(level, pos, site);
      if (h != null
          && !ConstructionSupply.reserved(level, pos)
          && seen.add(Storage.canonical(level, pos)))
        result.add(new Stock(site, pos, h, true, null));
    }
    for (var report : network.reports.values()) {
      var endpoint = report.endpoint;
      if (!report.loaded
          || !report.supply
          || endpoint.worker() != null
          || endpoint.pos() == null
          || !seen.add(endpoint.pos())) continue;
      Station remote = network.station(endpoint.totem());
      var h = network.handler(level, endpoint, ItemStack.EMPTY);
      if (remote != null && h != null && !ConstructionSupply.reserved(level, endpoint.pos()))
        result.add(new Stock(remote, endpoint.pos(), h, false, report));
    }
    return result;
  }

  private List<ItemStack> inventory(List<Stock> stock) {
    List<ItemStack> result = new ArrayList<>(CraftingPlan.contents(worker.cargo));
    for (Stock source : stock) {
      Map<String, Integer> used = new HashMap<>();
      for (int i = 0; i < source.items.getSlots(); i++) {
        ItemStack item = source.items.getStackInSlot(i);
        if (!item.isEmpty()) {
          String key = item.copyWithCount(1).save(new CompoundTag()).toString();
          int allowed =
              source.local
                  ? item.getCount()
                  : Math.min(
                      item.getCount(),
                      Math.max(
                          0,
                          LogisticsNetwork.get(worker)
                                  .availableForCrafting(
                                      (ServerLevel) worker.level(), source.report, item)
                              - used.getOrDefault(key, 0)));
          if (allowed > 0) {
            result.add(item.copyWithCount(allowed));
            used.merge(key, allowed, Integer::sum);
          }
        }
      }
    }
    return result;
  }

  private static int count(List<ItemStack> stock, Item item) {
    return stock.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  private List<Order> orders(Station site, LogisticsNetwork network, List<ItemStack> inventory) {
    ServerLevel level = (ServerLevel) worker.level();
    List<Order> orders = new ArrayList<>();
    for (var demand : network.demands.values()) {
      if (demand.source != null || demand.worker == null || demand.worker.equals(worker.getUUID()))
        continue;
      int amount = network.neededNow(level, demand);
      if (amount <= 0) continue;
      Item wanted = demand.item;
      if (wanted == null
          && demand.kind.equals("tool")
          && level.getEntity(demand.worker) instanceof Lumberton recipient) {
        if (inventory.stream().anyMatch(s -> Inventories.tool(recipient.job(), s))) continue;
        wanted =
            switch (recipient.job()) {
              case WorkerJob.LUMBERTON -> Items.IRON_AXE;
              case WorkerJob.MININGTON, WorkerJob.QUARRYTON -> Items.IRON_PICKAXE;
              case WorkerJob.FARMINGTON -> Items.IRON_HOE;
              case WorkerJob.BUILDINGTON -> Automatons.HAMMER.get();
              case WorkerJob.HUNTERTON, WorkerJob.GUARDINGTON -> Items.IRON_SWORD;
              case WorkerJob.FISHERTON -> Items.FISHING_ROD;
              case WorkerJob.RANCHERTON -> Items.SHEARS;
              default -> null;
            };
      }
      if (wanted == null
          || wanted == Luminall.BOTTLE.get()
          || wanted == Automatons.INFUSED_LUMITE.get()) continue;
      if (count(inventory, wanted) >= amount) continue;
      orders.add(
          new Order(
              wanted,
              amount,
              demand.kind.equals("tool")
                  ? 0
                  : wanted == Items.TORCH ? 1 : demand.kind.equals("material") ? 2 : 3,
              null));
    }
    site.craftOrders.forEach(
        (item, target) -> {
          if (count(inventory, item) < target) orders.add(new Order(item, target, 3, null));
        });
    orders.sort(
        Comparator.comparingInt(Order::priority)
            .thenComparing(
                o ->
                    net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(o.item)
                        .toString()));
    if (orders.isEmpty()) {
      ArmorStand stand = display(site);
      if (stand != null)
        for (EquipmentSlot slot :
            List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
          if (!stand.getItemBySlot(slot).isEmpty()) continue;
          Item armor =
              switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                default -> Items.IRON_BOOTS;
              };
          orders.add(new Order(armor, 1, 4, slot));
        }
    }
    return orders;
  }

  private ArmorStand display(Station site) {
    if (site.project() == null) return null;
    BlockPos pos =
        new SiteLayout(site.project(), (ServerLevel) worker.level()).feature("armor_stand");
    return worker.level().getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(.7)).stream()
        .findFirst()
        .orElse(null);
  }

  private List<AbstractFurnaceBlockEntity> furnaces(Station site) {
    ServerLevel level = (ServerLevel) worker.level();
    List<AbstractFurnaceBlockEntity> result = new ArrayList<>();
    if (site.project() == null) return result;
    BuildingProject project = site.project();
    for (var cell : project.template(level).cells) {
      if (!(cell.state().getBlock() instanceof FurnaceBlock)
          && !(cell.state().getBlock() instanceof BlastFurnaceBlock)) continue;
      BlockPos pos = project.template(level).world(project.anchor, project.front, cell.local());
      if (level.hasChunkAt(pos)
          && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)
        result.add(furnace);
    }
    return result;
  }

  public void tick(Station site) {
    ServerLevel level = (ServerLevel) worker.level();
    if (level.getGameTime() < nextTick) return;
    nextTick = level.getGameTime() + 10;
    LogisticsNetwork network = LogisticsNetwork.get(worker);
    network.refresh(level);
    boolean courier = courierExists();
    if (network.parcels.containsKey(worker.getUUID())) {
      worker.running.tick(site);
      return;
    }
    if (activeGoal != Items.AIR && worker.mining.carried(activeGoal) > 0) {
      network.releaseCrafting(worker);
      activeGoal = Items.AIR;
      held.clear();
      requested.clear();
    }
    if (!courier && activeGoal == Items.AIR && network.offerCarried(worker)) {
      worker.running.tick(site);
      return;
    }
    if (site.project() == null) {
      worker.state("Workshop not built");
      return;
    }
    if (courier && !site.project().bounds(level).contains(worker.position())) {
      worker.travelTo(site.getBlockPos(), "Returning to workshop", "Returning to workshop");
      return;
    }
    List<AbstractFurnaceBlockEntity> furnaces = furnaces(site);
    for (var furnace : furnaces) {
      ItemStack result = furnace.getItem(2);
      if (!result.isEmpty() && Inventories.fits(worker.cargo, List.of(result))) {
        if (worker.travelTo(
            furnace.getBlockPos(), "Collecting furnace output", "Collecting furnace output")) {
          Inventories.insert(worker.cargo, furnace.removeItem(2, result.getCount()), false);
          furnace.setChanged();
        }
        return;
      }
    }
    List<Stock> stock = stock(site, network);
    List<ItemStack> inventory = inventory(stock);
    List<ItemStack> completedStock = new ArrayList<>(inventory);
    for (Lumberton peer : network.crafters(level))
      if (peer != worker) completedStock.addAll(CraftingPlan.contents(peer.cargo));
    List<Order> orders = orders(site, network, completedStock);
    requested.clear();
    CraftingPlan.Result pending = null;
    for (Order order : orders) {
      if (!network.claimCrafting(worker, order.item)) continue;
      if (order.item == Luminall.BOTTLE.get() || order.item == Automatons.INFUSED_LUMITE.get()) {
        if (pending == null)
          pending = new CraftingPlan.Result(null, Map.of(), "Luminariton service required");
        continue;
      }
      if (order.display != null && count(inventory, order.item) > 0) {
        requested.put(order.item, 1);
        if (worker.mining.carried(order.item) == 0) {
          fetch(stock, courier, order.item, 1);
          return;
        }
        ArmorStand stand = display(site);
        if (stand != null
            && stand.getItemBySlot(order.display).isEmpty()
            && worker.travelTo(
                stand.blockPosition(), "Restocking iron display", "Restocking iron display")) {
          for (int i = 0; i < worker.cargo.getSlots(); i++)
            if (worker.cargo.getStackInSlot(i).is(order.item)) {
              stand.setItemSlot(order.display, worker.cargo.extractItem(i, 1, false));
              break;
            }
        }
        return;
      }
      CraftingPlan.Result plan = CraftingPlan.next(level, order.item, inventory);
      if (plan.step() == null) {
        if (pending == null) pending = plan;
        continue;
      }
      CraftingPlan.Step step = plan.step();
      requested.putAll(step.ingredients());
      if (activeGoal != order.item) held.clear();
      activeGoal = order.item;
      plan.held().forEach((item, count) -> held.merge(item, count, Math::max));
      if (step.cooking()) {
        held.put(Items.COAL, 1);
        held.put(Items.CHARCOAL, 1);
      }
      if (step.cooking() && !hasFuel(furnaces)) requested.put(Items.COAL, 1);
      if (WorkSupplies.deposit(worker, site, keep())) return;
      for (var entry : step.ingredients().entrySet())
        if (worker.mining.carried(entry.getKey()) < entry.getValue()) {
          if (!fetch(stock, courier, entry.getKey(), entry.getValue()))
            worker.state(
                courier ? "Waiting for courier ingredients" : "Needs crafting ingredients");
          return;
        }
      if (step.cooking()) {
        cook(site, stock, courier, furnaces, step);
        return;
      }
      BlockPos table = new SiteLayout(site.project(), level).feature("crafting_table");
      if (!level.getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
        worker.state("Crafting table missing");
        return;
      }
      if (worker.travelTo(
          table,
          "Working at crafting table",
          "Crafting " + step.output().getHoverName().getString())) {
        if (!CraftingPlan.craft(level, worker.cargo, step))
          worker.state("Crafting inputs changed or output storage full");
        else {
          requested.clear();
          worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
          network.changed(level);
        }
      }
      return;
    }
    if (pending != null) {
      pending
          .missing()
          .forEach((item, count) -> requested.merge(item, Math.min(64, count), Math::max));
      if (WorkSupplies.deposit(worker, site, keep())) return;
      for (var need : requested.entrySet())
        if (worker.mining.carried(need.getKey()) < need.getValue()
            && fetch(stock, courier, need.getKey(), need.getValue())) return;
      worker.state(pending.reason());
    } else {
      network.releaseCrafting(worker);
      held.clear();
      activeGoal = Items.AIR;
      if (!courier && network.claim(worker) != null) worker.running.tick(site);
      else if (!WorkSupplies.deposit(worker, site, keep()))
        worker.travelTo(site.getBlockPos(), "Returning to workshop", "Waiting for crafting orders");
    }
  }

  public Map<Item, Integer> keep() {
    Map<Item, Integer> keep = new HashMap<>(held);
    requested.forEach((item, count) -> keep.merge(item, count, Math::max));
    keep.put(Luminall.BOTTLE.get(), 1);
    return keep;
  }

  private boolean fetch(List<Stock> stock, boolean courier, Item item, int target) {
    for (Stock source : stock) {
      if (courier && !source.local || !worker.canReach(source.position)) continue;
      for (int i = 0; i < source.items.getSlots(); i++) {
        ItemStack stack = source.items.getStackInSlot(i);
        if (!stack.is(item)
            || !CraftingPlan.ordinary(stack)
            || !Storage.accepts(worker.cargo, stack)) continue;
        int available =
            source.local
                ? stack.getCount()
                : LogisticsNetwork.get(worker)
                    .availableForCrafting((ServerLevel) worker.level(), source.report, stack);
        if (available <= 0) continue;
        if (worker.travelTo(
            source.position, "Collecting crafting stock", "Collecting crafting stock"))
          BuildingProject.move(
              source.items,
              i,
              worker.cargo,
              Math.min(available, Math.max(0, target - worker.mining.carried(item))));
        return true;
      }
    }
    return false;
  }

  private boolean hasFuel(List<AbstractFurnaceBlockEntity> furnaces) {
    return worker.mining.carried(Items.COAL) > 0
        || worker.mining.carried(Items.CHARCOAL) > 0
        || furnaces.stream().anyMatch(f -> lit(f) || !f.getItem(1).isEmpty());
  }

  private static boolean lit(AbstractFurnaceBlockEntity furnace) {
    return furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT);
  }

  private void cook(
      Station site,
      List<Stock> stock,
      boolean courier,
      List<AbstractFurnaceBlockEntity> furnaces,
      CraftingPlan.Step step) {
    ServerLevel level = (ServerLevel) worker.level();
    ItemStack ingredient = step.inputs().get(0);
    for (var furnace :
        furnaces.stream()
            .sorted(
                Comparator.comparingInt(
                    f -> ItemStack.isSameItemSameTags(f.getItem(0), ingredient) ? 0 : 1))
            .toList()) {
      if (step.recipe().getType()
          == RecipeType.BLASTING
          != (furnace instanceof BlastFurnaceBlockEntity)) continue;
      if (!furnace.getItem(0).isEmpty()) {
        if (!ItemStack.isSameItemSameTags(furnace.getItem(0), ingredient)) continue;
      }
      if (!furnace.getItem(2).isEmpty()
          && (!ItemStack.isSameItemSameTags(furnace.getItem(2), step.output())
              || furnace.getItem(2).getCount() + step.output().getCount()
                  > step.output().getMaxStackSize())) continue;
      if (!lit(furnace) && furnace.getItem(1).isEmpty()) {
        Item fuel =
            worker.mining.carried(Items.COAL) > 0
                ? Items.COAL
                : worker.mining.carried(Items.CHARCOAL) > 0 ? Items.CHARCOAL : null;
        if (fuel == null) {
          requested.put(Items.COAL, 1);
          if (!fetch(stock, courier, Items.COAL, 1) && !fetch(stock, courier, Items.CHARCOAL, 1))
            worker.state("Needs furnace fuel");
          return;
        }
        if (!worker.travelTo(furnace.getBlockPos(), "Fueling furnace", "Fueling furnace")) return;
        for (int i = 0; i < worker.cargo.getSlots(); i++)
          if (worker.cargo.getStackInSlot(i).is(fuel)) {
            furnace.setItem(1, worker.cargo.extractItem(i, 1, false));
            furnace.setChanged();
            break;
          }
      }
      if (!furnace.getItem(0).isEmpty()) {
        worker.state("Waiting for furnace processing");
        return;
      }
      if (!worker.travelTo(furnace.getBlockPos(), "Loading furnace", "Loading furnace")) return;
      for (int i = 0; i < worker.cargo.getSlots(); i++)
        if (ItemStack.isSameItemSameTags(worker.cargo.getStackInSlot(i), ingredient)) {
          furnace.setItem(0, worker.cargo.extractItem(i, 1, false));
          furnace.setChanged();
          return;
        }
    }
    worker.state("No compatible free furnace");
  }
}
