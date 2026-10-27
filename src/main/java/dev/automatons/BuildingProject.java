package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.items.*;

/** One build journal per anchor totem. The existing anchor is never placed or deserialized. */
public final class BuildingProject {
  public UUID home, owner, totem;
  public UUID id = UUID.randomUUID();
  public WorksitePurpose purpose = WorksitePurpose.WAREHOUSE;
  public long created, generation;
  public final Set<Integer> completedFrames = new HashSet<>();
  public final Set<Integer> completedStands = new HashSet<>();
  public BlockPos anchor;
  public Direction front = Direction.NORTH;
  public boolean approved, ready, paused;
  public String reason = "Choose warehouse orientation";
  public final Map<Long, BlockState> original = new LinkedHashMap<>();
  public final Set<Long> completed = new HashSet<>();
  public int cursor;
  public int blueprintVersion = 3;
  public final Set<Long> stagingChests = new HashSet<>();
  public UUID builder;
  public final ConstructionSupply supplies = new ConstructionSupply(this);
  private BuildingTemplate template;
  private List<BuildingTemplate.Cell> order;
  private int verifyCursor;
  private BlockPos buildingStance;
  private BlockPos previousStance;
  private int stanceWork;
  private long nextBuildAction;
  private long nextStanceSearch;
  private Set<Long> structuralCells;
  private BlockPos structureAnchor;
  private Direction structureFront;

  public BuildingTemplate template(ServerLevel l) {
    if (template == null) template = BuildingTemplate.load(l, purpose.blueprint);
    return template;
  }

  public BlockPos at(ServerLevel l, int x, int y, int z) {
    return template(l).world(anchor, front, new BlockPos(x, y, z));
  }

  public AABB bounds(ServerLevel l) {
    BuildingTemplate t = template(l);
    BlockPos a = t.world(anchor, front, BlockPos.ZERO);
    BlockPos b = t.world(anchor, front, t.size.offset(-1, -1, -1));
    return new AABB(
        Math.min(a.getX(), b.getX()),
        Math.min(a.getY(), b.getY()),
        Math.min(a.getZ(), b.getZ()),
        Math.max(a.getX(), b.getX()) + 1,
        Math.max(a.getY(), b.getY()) + 1,
        Math.max(a.getZ(), b.getZ()) + 1);
  }

  public BlockPos entrance(ServerLevel l) {
    return template(l).feature(anchor, front, "door",
        template(l).feature(anchor, front, "healing_position", at(l, template(l).anchor.getX(), 1, 0)));
  }

  public boolean contains(ServerLevel l, BlockPos p) {
    return bounds(l).intersects(new AABB(p))
        || p.equals(entrance(l).relative(front))
        || p.equals(entrance(l).relative(front).above());
  }

  public boolean structure(ServerLevel l, BlockPos p) {
    if (!bounds(l).intersects(new AABB(p))) return false;
    if (structuralCells == null
        || !Objects.equals(anchor, structureAnchor)
        || front != structureFront) {
      structuralCells = new HashSet<>();
      structureAnchor = anchor;
      structureFront = front;
      for (var cell : template(l).cells)
        if (!cell.state().isAir())
          structuralCells.add(template.world(anchor, front, cell.local()).asLong());
    }
    return structuralCells.contains(p.asLong());
  }

  public boolean reserve(ServerLevel l) {
    if (!new SiteLayout(this, l).waterValid()) return fail("Fishing dock needs a 7 by 10 area of source water");
    for (BuildingProject other : WorldData.get(l).buildings.values()) {
      if (other == this) continue;
      if (bounds(l).intersects(other.bounds(l))
          || contains(l, other.entrance(l).relative(other.front))
          || other.contains(l, entrance(l).relative(front)))
        return fail("Overlaps another worksite");
    }
    for (var cell : template(l).cells) {
      BlockPos p = template.world(anchor, front, cell.local());
      if (!l.hasChunkAt(p)) return fail("Worksite area unloaded");
      if (WorldData.get(l).farms.values().stream().anyMatch(f -> f.contains(p)))
        return fail("Overlaps a registered field");
      if (WorldData.get(l).protectedAt(p) || WorldData.get(l).mineProtected(p, null))
        return fail("Worksite protected or reserved");
    }
    approved = true;
    reason = "Awaiting Buildington";
    return true;
  }

  public List<BlockPos> chests(ServerLevel l) {
    return template(l).cells.stream()
        .filter(c -> c.state().is(Blocks.CHEST))
        .map(c -> template.world(anchor, front, c.local()))
        .toList();
  }

  public IItemHandler handler(ServerLevel l, BlockPos p) {
    if (!ready || !l.hasChunkAt(p) || !completed.contains(p.asLong()) || !chests(l).contains(p))
      return null;
    BlockState s = l.getBlockState(p);
    if (!s.is(Blocks.CHEST) || s.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
    BlockPos other = p.relative(ChestBlock.getConnectedDirection(s));
    if (!l.hasChunkAt(other) || !chests(l).contains(other) || !completed.contains(other.asLong()))
      return null;
    var container = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, s, l, p, false);
    return container == null || container.getContainerSize() != 54
        ? null
        : new net.minecraftforge.items.wrapper.InvWrapper(container);
  }

  public Map<Item, Integer> remaining(ServerLevel l) {
    Map<Item, Integer> result = new LinkedHashMap<>();
    for (var c : template(l).cells) {
      BlockPos p = template.world(anchor, front, c.local());
      if (l.hasChunkAt(p)
          && Construction.matches(
              l.getBlockState(p), c.state().rotate(template.rotationFor(front)))) continue;
      ItemStack cost = BuildingTemplate.cost(c.state());
      if (!cost.isEmpty()) result.merge(cost.getItem(), cost.getCount(), Integer::sum);
    }
    for (int index = 0; index < template(l).frames.size(); index++)
      if (!completedFrames.contains(index)) {
        result.merge(Items.ITEM_FRAME, 1, Integer::sum);
        result.merge(template(l).frames.get(index).item(), 1, Integer::sum);
      }
    if (completedStands.size() < template(l).stands.size())
      result.merge(Items.ARMOR_STAND, template(l).stands.size() - completedStands.size(), Integer::sum);
    return result;
  }

  /** Smallest payable placement still needing this item (double slabs require two). */
  public int minimumWorkingAmount(ServerLevel level, Item item) {
    if (item == Items.ARMOR_STAND && completedStands.size() < template(level).stands.size()) return 1;
    int minimum = Integer.MAX_VALUE;
    for (var cell : template(level).cells) {
      ItemStack cost = BuildingTemplate.cost(cell.state());
      if (!cost.is(item)) continue;
      BlockPos pos = template.world(anchor, front, cell.local());
      if (level.hasChunkAt(pos)
          && Construction.matches(
              level.getBlockState(pos), cell.state().rotate(template.rotationFor(front))))
        continue;
      minimum = Math.min(minimum, cost.getCount());
    }
    for (int i = 0; i < template(level).frames.size(); i++)
      if (!completedFrames.contains(i)
          && (item == Items.ITEM_FRAME || template.frames.get(i).item() == item)) minimum = 1;
    return minimum;
  }

  private boolean finishFrames(Lumberton w, Station station) {
    ServerLevel l = (ServerLevel) w.level();
    for (int index = 0; index < template(l).frames.size(); index++) {
      if (completedFrames.contains(index)) continue;
      var display = template.frames.get(index);
      BlockPos p = template.world(anchor, front, display.local());
      if (!l.hasChunkAt(p)) {
        w.state("Display area unloaded");
        return false;
      }
      final int frameIndex = index;
      var existing =
          l.getEntitiesOfClass(
              net.minecraft.world.entity.decoration.ItemFrame.class,
              new AABB(p).inflate(.5),
              e ->
                  e.getPersistentData().hasUUID("AutomatonsProject")
                      && id.equals(e.getPersistentData().getUUID("AutomatonsProject"))
                      && frameIndex == e.getPersistentData().getInt("AutomatonsDisplay"));
      if (!existing.isEmpty()) {
        completedFrames.add(index);
        dirty(l);
        continue;
      }
      for (Item item : List.of(Items.ITEM_FRAME, display.item())) {
        if (w.mining.carried(item) == 0) {
          if (!restock(w, station, item, 1))
            w.state("Needs display " + new ItemStack(item).getHoverName().getString());
          return false;
        }
      }
      if (!outsideStance(w, p)) return false;
      if (l.getGameTime() < nextBuildAction) return false;
      if (!Objects.equals(builder, w.getUUID())
          || !Inventories.tool(WorkerJob.BUILDINGTON, w.activeTool())) return false;
        Direction displayFacing = template.rotationFor(front).rotate(display.facing());
        if (WorldData.get(l).protectedAt(p)
            || MiningRules.breakPermission(
                    w,
                    p.relative(displayFacing.getOpposite()),
                    l.getBlockState(p.relative(displayFacing.getOpposite())))
              < 0) {
        w.state("Display placement protected");
        return false;
      }
        var frame = new net.minecraft.world.entity.decoration.ItemFrame(l, p, displayFacing);
      frame.getPersistentData().putUUID("AutomatonsProject", id);
      frame.getPersistentData().putInt("AutomatonsDisplay", index);
      frame.setItem(new ItemStack(display.item()), false);
      if (!frame.survives() || !l.addFreshEntity(frame)) {
        w.state("Display needs clear support");
        return false;
      }
      consume(w, Items.ITEM_FRAME, 1);
      consume(w, display.item(), 1);
      w.wear(1);
      worked(l);
      w.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
      completedFrames.add(index);
      dirty(l);
      return false;
    }
    return true;
  }

  private boolean finishStands(Lumberton w, Station station) {
    ServerLevel level = (ServerLevel) w.level();
    for (int i = 0; i < template(level).stands.size(); i++) {
      if (completedStands.contains(i)) continue;
      BlockPos pos = template.world(anchor, front, template.stands.get(i));
      final int index = i;
      if (!level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
          new AABB(pos).inflate(.5), e -> id.equals(e.getPersistentData().hasUUID("AutomatonsProject")
              ? e.getPersistentData().getUUID("AutomatonsProject") : null)
              && e.getPersistentData().getInt("AutomatonsDisplay") == index).isEmpty()) {
        completedStands.add(i); dirty(level); continue;
      }
      if (w.mining.carried(Items.ARMOR_STAND) == 0) {
        if (!restock(w, station, Items.ARMOR_STAND, 1)) w.state("Needs armor stand");
        return false;
      }
      if (!outsideStance(w, pos) || level.getGameTime() < nextBuildAction) return false;
      if (!level.hasChunkAt(pos) || WorldData.get(level).protectedAt(pos)) return false;
      var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5);
      stand.setYRot(front.toYRot());
      stand.getPersistentData().putUUID("AutomatonsProject", id);
      stand.getPersistentData().putInt("AutomatonsDisplay", i);
      stand.getPersistentData().putBoolean("AutomatonsDisplayArmor", true);
      if (!level.noCollision(stand) || !level.addFreshEntity(stand)) { w.state("Clear armor stand position"); return false; }
      consume(w, Items.ARMOR_STAND, 1);
      completedStands.add(i); worked(level); dirty(level); return false;
    }
    return true;
  }

  public static void consume(Lumberton w, Item item, int amount) {
    for (int slot = 0; slot < w.cargo.getSlots() && amount > 0; slot++)
      if (w.cargo.getStackInSlot(slot).is(item))
        amount -= w.cargo.extractItem(slot, amount, false).getCount();
    if (amount != 0) throw new IllegalStateException("Construction stock changed during commit");
  }

  public boolean validate(Lumberton w, Station station) {
    ServerLevel l = (ServerLevel) w.level();
    if (l.getBlockEntity(anchor) != station || !station.identity.equals(totem))
      return fail("Worksite anchor changed");
    return approved || reserve(l);
  }

  private boolean fail(String text) {
    reason = text;
    return false;
  }

  private void dirty(ServerLevel l) {
    WorldData.get(l).setDirty();
  }

  public boolean claim(Lumberton w) {
    if (w.job() != WorkerJob.BUILDINGTON
        || !Objects.equals(owner, w.owner)
        || home != null && !Objects.equals(home, w.homeId)) return false;
    if (builder != null && !builder.equals(w.getUUID())) return false;
    if (builder == null) {
      builder = w.getUUID();
      generation++;
      dirty((ServerLevel) w.level());
    }
    return true;
  }

  public void release(UUID worker) {
    if (worker.equals(builder)) {
      builder = null;
      generation++;
    }
  }

  public void repair(ServerLevel l) {
    if (!approved) return;
    for (var c : template(l).cells) {
      BlockPos p = template.world(anchor, front, c.local());
      if (p.equals(anchor) || c.state().isAir()) continue;
      if (!l.hasChunkAt(p)) {
        reason = "Worksite area unloaded";
        return;
      }
      BlockState actual = l.getBlockState(p);
      if (actual.isAir()) {
        completed.remove(p.asLong());
        original.put(p.asLong(), actual);
      } else if (actual.getBlock() != c.state().getBlock()) {
        reason = "Worksite block changed — review site";
        paused = true;
        return;
      }
    }
    ready = false;
    paused = false;
    cursor = 0;
    reason = "Repairing warehouse";
    dirty(l);
  }

  public void tick(Lumberton w, Station s) {
    ServerLevel l = (ServerLevel) w.level();
    if (paused
        && (reason.equals("Worksite block changed — review site")
            || reason.startsWith("Site changed at "))) paused = false;
    if (paused) {
      w.state("Construction suspended");
      return;
    }
    if (ready) return;
    if (approved) {
      var cells = template(l).cells;
      var cell = cells.get(Math.floorMod(verifyCursor++, cells.size()));
      BlockPos pos = template.world(anchor, front, cell.local());
      if (!pos.equals(anchor)
          && completed.contains(pos.asLong())
          && l.hasChunkAt(pos)
          && !Construction.matches(
              l.getBlockState(pos), cell.state().rotate(template.rotationFor(front)))) {
        completed.remove(pos.asLong());
        cursor = 0;
      }
    }
    if (!claim(w)) {
      w.running.idle("Waiting for Buildington");
      return;
    }
    if (!approved) {
      if (w.tickCount % 20 != 0) {
        w.state(reason);
        return;
      }
      if (!validate(w, s)) {
        w.state(reason);
        return;
      }
    }
    if (order == null) {
      order = new ArrayList<>();
      // Only essential interior headroom is cleared. Eave air and surrounding terrain are
      // untouched.
      for (var cell : template(l).cells)
        if (cell.state().isAir() && (template(l).explicitAir || (
            cell.local().getY() > 0
            && cell.local().getY() <= 2
            && cell.local().getX() > 0
            && cell.local().getX() < template.size.getX() - 1
            && cell.local().getZ() > 0
            && cell.local().getZ() < template.size.getZ() - 1))) order.add(cell);
      // No temporary access hole is needed when the worker stays outside. Such a hole
      // also incorrectly demanded demolition of an already-correct stone foundation.
      order.addAll(template(l).placements());
    }
    if (cursor >= order.size()) cursor = 0;
    int visited = 0;
    boolean missing = false;
    Item nextMaterial = null;
    while (visited++ < order.size()) {
      cursor = Math.floorMod(cursor, order.size());
      var c = order.get(cursor);
      BlockPos p = template.world(anchor, front, c.local());
      if (!l.hasChunkAt(p)) {
        w.state("Waiting for worksite chunks");
        return;
      }
      BlockState target = c.state().rotate(template.rotationFor(front));
      BlockState now = l.getBlockState(p);
      if (target.is(Blocks.GRASS_BLOCK) && !FarmPlan.soil(now)) target = Blocks.DIRT.defaultBlockState();
      if (WorldData.get(l).protectedAt(p)) {
        w.state("Construction protected");
        return;
      }
      if (completed.contains(p.asLong())) {
        cursor++;
        continue;
      }
      if (now.hasBlockEntity() && !Construction.matches(now, target)) {
        missing = true;
        reason = "Move the existing container or workstation before construction";
        cursor++;
        continue;
      }
      // Air is not a required building block. Clear loose terrain for headroom, but
      // leave extra player workstations/containers alone instead of demanding tools
      // for demolition and preventing the remaining structure from being finished.
      if (target.isAir() && !Construction.soft(now) && !template(l).explicitAir) {
        cursor++;
        continue;
      }
      if (Construction.matches(now, target)) {
        if (!target.isAir()) completed.add(p.asLong());
        cursor++;
        dirty(l);
        continue;
      }
      ItemStack cost = BuildingTemplate.cost(target);
      if (!cost.isEmpty() && w.mining.carried(cost.getItem()) < cost.getCount()) {
        if (nextMaterial == null) {
          nextMaterial = cost.getItem();
          reason = "Waiting for materials: " + cost.getHoverName().getString();
        }
        missing = true;
        cursor++;
        continue;
      }
      if (!now.isAir() && now.getDestroySpeed(l, p) < 0) {
        w.getNavigation().stop();
        ((WorkerMoveControl) w.getMoveControl()).stop();
        w.state(
            "Unbreakable "
                + now.getBlock().getName().getString()
                + " at "
                + p.getX()
                + ", "
                + p.getY()
                + ", "
                + p.getZ());
        return;
      }
      if (target.getBlock() instanceof DoorBlock
          && target.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
        // The lower half pays for the complete door. A partial kit must wait for it;
        // an existing paid lower half can have its missing upper half repaired.
        BlockState lower = target.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        if (!Construction.matches(l.getBlockState(p.below()), lower)) {
          cursor++;
          continue;
        }
      }
      if (!target.canSurvive(l, p)) {
        cursor++;
        continue;
      }
      // Construction targets need not be walkable. In particular an empty foundation cell
      // must be filled from the perimeter, without finding a route into the hole or roof.
      if (!outsideStance(w, p)) return;
      if (l.getGameTime() < nextBuildAction) return;
      if (!Objects.equals(builder, w.getUUID()) || !validate(w, s)) return;
      if (WorldData.get(l).protectedAt(p)) {
        w.state("Worksite protected");
        return;
      }
      if (!now.isAir() && now.getFluidState().isEmpty()) {
        clearObstacle(w, p, now);
        return;
      }
      if (target.isAir()) continue;
      if (!Inventories.tool(WorkerJob.BUILDINGTON, w.activeTool())) {
        w.state("Needs Hammer");
        return;
      }
      if (target.getBlock() instanceof DoorBlock
          && target.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
        BlockPos upperPos = p.above();
        if (WorldData.get(l).protectedAt(upperPos)) {
          w.state("Worksite door placement protected");
          return;
        }
        BlockState above = l.getBlockState(upperPos);
        BlockState upper = target.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        if (!above.isAir()
            && above.getFluidState().isEmpty()
            && !Construction.matches(above, upper)) {
          clearObstacle(w, upperPos, above);
          return;
        }
      }
      if (!MiningRules.place(w, p, target)) {
        w.state("Waiting for safe placement");
        return;
      }
      if (target.getBlock() instanceof DoorBlock
          && target.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
        BlockState upper = target.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        if (!MiningRules.place(w, p.above(), upper)) {
          l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
          w.state("Worksite door placement protected");
          return;
        }
        completed.add(p.above().asLong());
      }
      int left = cost.getCount();
      for (int i = 0; i < w.cargo.getSlots() && left > 0; i++)
        if (w.cargo.getStackInSlot(i).is(cost.getItem()))
          left -= w.cargo.extractItem(i, left, false).getCount();
      completed.add(p.asLong());
      w.wear(1);
      worked(l);
      cursor++;
      w.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
      w.building.lookAt(p);
      dirty(l);
      return;
    }
    if (missing) {
      // Use the materials already carried before interrupting construction for stock.
      if (nextMaterial != null && restock(w, s, nextMaterial, Math.min(64, remaining(l).getOrDefault(nextMaterial, 1))))
        return;
      w.getNavigation().stop();
      ((WorkerMoveControl) w.getMoveControl()).stop();
      w.state(reason);
      w.alerts = 16;
      return;
    }
    boolean incomplete =
        template(l).cells.stream()
            .anyMatch(
                c -> {
                  BlockPos p = template.world(anchor, front, c.local());
                  if (p.equals(anchor) || c.state().isAir()) return false;
                  if (!l.hasChunkAt(p)) return true;
                  if (Construction.matches(
                      l.getBlockState(p), c.state().rotate(template.rotationFor(front))))
                    return false;
                  completed.remove(p.asLong());
                  return true;
                });
    if (incomplete) {
      cursor = 0;
      dirty(l);
      return;
    }
    for (BlockPos chest : chests(l)) {
      BlockState state = l.getBlockState(chest);
      if (!state.is(Blocks.CHEST)
          || ChestBlock.getContainer((ChestBlock) Blocks.CHEST, state, l, chest, false) == null) {
        w.state("Free worksite chest lids before handover");
        return;
      }
    }
    if (!finishFrames(w, s)) return;
    if (!finishStands(w, s)) return;
    if (!new SiteLayout(this, l).waterValid()) { w.state("Fishing water has changed — restore source water"); return; }
    ready = true;
    reason = purpose.label + " ready";
    if (supplies.chest == null) release(w.getUUID());
    if (purpose == WorksitePurpose.MINE && (s.mine == null || s.mine.entrance == null)) {
      if (s.mine == null) s.mine = new MinePlan();
      s.mine.entrance = at(l, 3, 1, 3);
      s.mine.forward = front;
      s.mine.rustic = true;
      s.mine.openAccess = true;
      s.mine.entranceComplete = true;
      s.mine.stage = MinePlan.Stage.SHAFT;
      s.mine.reason = "Preparing shaft";
      WorldData.get(l).mines.put(s.mine.id, s.mine);
    }
    dirty(l);
    s.refreshStorage();
    s.changed();
  }

  private void clearObstacle(Lumberton w, BlockPos p, BlockState now) {
    ServerLevel l = (ServerLevel) w.level();
    if (WorldData.get(l).protectedAt(p)) {
      w.state("Worksite protected");
      return;
    }
    if (now.getDestroySpeed(l, p) < 0) {
      w.state(
          "Unbreakable "
              + now.getBlock().getName().getString()
              + " at "
              + p.getX()
              + ", "
              + p.getY()
              + ", "
              + p.getZ());
      return;
    }
    var drops =
        !now.requiresCorrectToolForDrops() || w.activeTool().isCorrectToolForDrops(now)
            ? Block.getDrops(now, l, p, l.getBlockEntity(p), w, w.activeTool())
            : List.<ItemStack>of();
    if (MiningRules.breakPermission(w, p, now) < 0) {
      w.state("Worksite protected");
      return;
    }
    var entity = l.getBlockEntity(p);
    if (entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
        || entity instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) {
      var inventory =
          new net.minecraftforge.items.wrapper.InvWrapper((net.minecraft.world.Container) entity);
      for (int slot = 0; slot < inventory.getSlots(); slot++) move(inventory, slot, w.cargo, 64);
    }
    if (!l.setBlock(p, Blocks.AIR.defaultBlockState(), 3)) {
      w.state("Worksite protected");
      return;
    }
    // setBlock invokes the old block's removal hook, preserving loose container
    // contents. Keep block loot when it fits, otherwise leave it as real world drops.
    for (var drop : drops) {
      ItemStack rest = Inventories.insert(w.cargo, drop, false);
      if (!rest.isEmpty()) Block.popResource(l, p, rest);
    }
    if (!Construction.soft(now)) w.wear(1);
    w.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    worked(l);
    l.levelEvent(2001, p, Block.getId(now));
    original.put(p.asLong(), Blocks.AIR.defaultBlockState());
    dirty(l);
  }

  private void worked(ServerLevel level) {
    stanceWork++;
    nextBuildAction = level.getGameTime() + 2;
  }

  private boolean safeStance(Lumberton w, BlockPos feet) {
    ServerLevel l = (ServerLevel) w.level();
    AABB box =
        w.getDimensions(net.minecraft.world.entity.Pose.STANDING)
            .makeBoundingBox(Vec3.atBottomCenterOf(feet));
    AABB site = bounds(l);
    boolean outside =
        box.maxX <= site.minX
            || box.minX >= site.maxX
            || box.maxZ <= site.minZ
            || box.minZ >= site.maxZ;
    return outside
        && site.inflate(3, 5, 3).intersects(box)
        && l.hasChunkAt(feet)
        && l.hasChunkAt(feet.above())
        && MiningRules.supported(l, feet)
        && l.noCollision(w, box);
  }

  /**
   * Work in small batches from stable footing, then walk clockwise around the footprint. Upper
   * floors remain reachable from outside; stock transfers still require ordinary reach.
   */
  private boolean outsideStance(Lumberton w, BlockPos target) {
    ServerLevel l = (ServerLevel) w.level();
    AABB site = bounds(l);
    if (!w.getBoundingBox().intersects(site)) w.building.perimeterRoute(site);
    if (buildingStance != null && !safeStance(w, buildingStance)) {
      buildingStance = null;
      nextStanceSearch = 0;
      w.getNavigation().stop();
    }
    if (buildingStance != null) {
      Vec3 center = Vec3.atBottomCenterOf(buildingStance);
      if (w.position().distanceToSqr(center) < .16
          && w.onGround()
          && !w.getBoundingBox().intersects(bounds(l))) {
        w.getNavigation().stop();
        ((WorkerMoveControl) w.getMoveControl()).stop();
        if (stanceWork < 12) {
          w.building.lookAt(target);
          w.state("Building from perimeter");
          return true;
        }
        previousStance = buildingStance;
        buildingStance = null;
        stanceWork = 0;
        nextStanceSearch = 0;
      }
      // Finish the final fraction of a path node, including stepping down from furniture.
      if (buildingStance != null
          && w.getNavigation().isDone()
          && w.position().distanceToSqr(center) < 2.25) {
        w.getMoveControl().setWantedPosition(center.x, center.y, center.z, 1);
        w.state("Moving outside building");
        return false;
      }
      if (!w.getNavigation().isDone()) {
        w.state("Moving outside building");
        return false;
      }
    }
    if (l.getGameTime() < nextStanceSearch || !WorldEvents.pathBudget()) {
      w.state("Finding building perimeter");
      return false;
    }
    nextStanceSearch = l.getGameTime() + 40;
    Set<BlockPos> options = new HashSet<>();
    for (int x = (int) site.minX - 2; x <= (int) site.maxX + 1; x++)
      for (int z = (int) site.minZ - 2; z <= (int) site.maxZ + 1; z++) {
        if (x >= site.minX && x < site.maxX && z >= site.minZ && z < site.maxZ) continue;
        for (int y = anchor.getY() - 3; y <= anchor.getY() + 4; y++) {
          BlockPos feet = new BlockPos(x, y, z);
          if (safeStance(w, feet)) options.add(feet);
        }
      }
    if (options.isEmpty()) {
      buildingStance = null;
      w.state("Clear safe footing outside building");
      return false;
    }
    Set<BlockPos> destinations = options;
    if (buildingStance != null) destinations = Set.of(buildingStance);
    else if (previousStance != null) {
      // A short, consistently directed walk avoids oscillating between whichever
      // side happens to be closest to consecutive blueprint cells.
      double angle =
          Math.atan2(
              previousStance.getZ() + .5 - site.getCenter().z,
              previousStance.getX() + .5 - site.getCenter().x);
      var next =
          options.stream()
              .filter(
                  p ->
                      Math.pow(p.getX() - previousStance.getX(), 2)
                              + Math.pow(p.getZ() - previousStance.getZ(), 2)
                          >= 9)
              .sorted(
                  Comparator.comparingDouble(
                          (BlockPos p) ->
                              (Math.atan2(
                                          p.getZ() + .5 - site.getCenter().z,
                                          p.getX() + .5 - site.getCenter().x)
                                      - angle
                                      + Math.PI * 2)
                                  % (Math.PI * 2))
                      .thenComparingDouble(p -> p.distSqr(previousStance)))
              .limit(4)
              .toList();
      if (!next.isEmpty()) destinations = new HashSet<>(next);
    }
    var path = w.getNavigation().createPath(destinations, 0);
    if (path != null && path.canReach()) {
      buildingStance = path.getTarget();
      previousStance = null;
      w.getNavigation().moveTo(path, 1);
      w.state("Moving outside building");
      return false;
    }
    // A blocked side must not stop construction from otherwise usable footing.
    if (previousStance != null && safeStance(w, previousStance)) {
      buildingStance = previousStance;
      previousStance = null;
      return false;
    }
    buildingStance = null;
    // Use existing segmented travel for a project beyond the local pathfinder's horizon.
    BlockPos nearest =
        options.stream()
            .min(Comparator.comparingDouble(p -> w.distanceToSqr(Vec3.atBottomCenterOf(p))))
            .orElseThrow();
    if (w.distanceToSqr(Vec3.atBottomCenterOf(nearest)) > 32 * 32)
      w.travelTo(nearest, "Travelling to construction site", "Finding building perimeter");
    else w.state("Clear a route to building perimeter");
    return false;
  }

  public static void count(IItemHandler h, Map<Item, Integer> counts) {
    for (int i = 0; i < h.getSlots(); i++) {
      ItemStack stack = h.getStackInSlot(i);
      if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }
  }

  public static void countConstructionSupply(IItemHandler h, Map<Item, Integer> counts) {
    for (int i = 0; i < h.getSlots(); i++) {
      ItemStack stack = h.getStackInSlot(i);
      if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }
  }

  private boolean restock(Lumberton w, Station s, Item item, int target) {
    if (supplies.chest != null) return supplies.refill(w, item);
    for (Station supply : w.station() == s ? List.of(s) : List.of(w.station(), s))
      for (BlockPos p : supply.chests) {
        var h = Storage.handler((ServerLevel) w.level(), p, supply);
        if (h == null) continue;
        for (int i = 0; i < h.getSlots(); i++)
          if (h.getStackInSlot(i).is(item)) {
            if (!Storage.accepts(w.cargo, h.getStackInSlot(i)) || !w.canReach(p)) continue;
            if (w.travelTo(
                p, "Collecting construction materials", "Collecting construction materials")) {
              move(h, i, w.cargo, Math.max(0, target - w.mining.carried(item)));
              collectBuildKit(w, h);
            }
            return true;
          }
      }
    return false;
  }

  void collectBuildKit(Lumberton w, IItemHandler source) {
    var remaining = remaining((ServerLevel) w.level());
    Set<Item> upcoming = new LinkedHashSet<>();
    if (order != null)
      for (int offset = 0; offset < order.size(); offset++) {
        ItemStack cost =
            BuildingTemplate.cost(order.get(Math.floorMod(cursor + offset, order.size())).state());
        if (!cost.isEmpty()) upcoming.add(cost.getItem());
      }
    upcoming.addAll(remaining.keySet());
    for (Item material : upcoming) {
      int carried = w.mining.carried(material);
      int needed =
          Math.min(material.getMaxStackSize(), remaining.getOrDefault(material, 0)) - carried;
      if (needed <= 0) continue;
      int empty = 0;
      for (int slot = 0; slot < w.cargo.getSlots(); slot++)
        if (w.cargo.getStackInSlot(slot).isEmpty()) empty++;
      // Leave room for terrain drops while topping up existing material stacks.
      if (carried == 0 && empty <= 1) continue;
      for (int slot = 0; slot < source.getSlots() && needed > 0; slot++)
        if (source.getStackInSlot(slot).is(material)) needed -= move(source, slot, w.cargo, needed);
    }
  }

  public static int move(IItemHandler from, int slot, IItemHandler to, int maximum) {
    if (maximum <= 0) return 0;
    ItemStack preview = from.extractItem(slot, maximum, true);
    int amount = preview.getCount() - ItemHandlerHelper.insertItem(to, preview, true).getCount();
    if (amount <= 0) return 0;
    ItemStack actual = from.extractItem(slot, amount, false);
    int extracted = actual.getCount();
    ItemStack rest = ItemHandlerHelper.insertItem(to, actual, false);
    if (!rest.isEmpty()) {
      ItemStack refused = from.insertItem(slot, rest, false);
      if (!refused.isEmpty())
        throw new IllegalStateException("Inventory violated simulated transfer");
    }
    return extracted - rest.getCount();
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    if (home != null) n.putUUID("Home", home);
    n.putUUID("Project", id);
    n.put("ConstructionSupply", supplies.save());
    n.putInt("Purpose", purpose.ordinal());
    n.putLong("Created", created);
    n.putLong("Generation", generation);
    if (builder != null) n.putUUID("Builder", builder);
    n.putIntArray("Frames", completedFrames.stream().mapToInt(Integer::intValue).toArray());
    n.putIntArray("Stands", completedStands.stream().mapToInt(Integer::intValue).toArray());
    n.putUUID("Owner", owner);
    n.putUUID("Totem", totem);
    n.putLong("Anchor", anchor.asLong());
    n.putInt("Front", front.get2DDataValue());
    n.putBoolean("Approved", approved);
    n.putBoolean("Ready", ready);
    n.putBoolean("Paused", paused);
    n.putInt("Cursor", cursor);
    n.putInt("BlueprintVersion", blueprintVersion);
    n.putLongArray("StagingChests", stagingChests.stream().mapToLong(Long::longValue).toArray());
    n.putString("Reason", reason);
    n.putLongArray("Completed", completed.stream().mapToLong(Long::longValue).toArray());
    ListTag before = new ListTag();
    original.forEach(
        (pos, state) -> {
          CompoundTag b = new CompoundTag();
          b.putLong("Pos", pos);
          b.put("State", NbtUtils.writeBlockState(state));
          before.add(b);
        });
    n.put("Original", before);
    return n;
  }

  public static BuildingProject load(CompoundTag n) {
    BuildingProject p = new BuildingProject();
    for (int index : n.getIntArray("Stands")) p.completedStands.add(index);
    p.supplies.load(n.getCompound("ConstructionSupply"));
    p.home = n.hasUUID("Home") ? n.getUUID("Home") : null;
    p.id = n.hasUUID("Project") ? n.getUUID("Project") : UUID.randomUUID();
    p.purpose =
        n.contains("Purpose") ? WorksitePurpose.of(n.getInt("Purpose")) : WorksitePurpose.WAREHOUSE;
    p.created = n.getLong("Created");
    p.generation = n.getLong("Generation");
    p.builder = n.hasUUID("Builder") ? n.getUUID("Builder") : null;
    for (int frame : n.getIntArray("Frames")) p.completedFrames.add(frame);
    p.owner = n.getUUID("Owner");
    p.totem = n.getUUID("Totem");
    p.anchor = BlockPos.of(n.getLong("Anchor"));
    p.front = Direction.from2DDataValue(n.getInt("Front"));
    p.approved = n.getBoolean("Approved");
    p.ready = n.getBoolean("Ready");
    p.paused = n.getBoolean("Paused");
    p.cursor = n.getInt("Cursor");
    p.blueprintVersion = n.contains("BlueprintVersion") ? n.getInt("BlueprintVersion") : 1;
    if (!p.approved && n.getLongArray("Completed").length == 0) p.blueprintVersion = 3;
    for (long at : n.getLongArray("StagingChests")) p.stagingChests.add(at);
    p.reason = n.getString("Reason");
    for (long at : n.getLongArray("Completed")) p.completed.add(at);
    for (Tag t : n.getList("Original", 10)) {
      CompoundTag b = (CompoundTag) t;
      p.original.put(
          b.getLong("Pos"),
          NbtUtils.readBlockState(
              net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
              b.getCompound("State")));
    }
    return p;
  }
}
