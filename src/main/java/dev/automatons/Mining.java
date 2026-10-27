package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.*;

/** Job-specific work; lifecycle, real inventory and surface navigation remain on the worker. */
public final class Mining {
  private final Lumberton w;
  private MinePlan access;
  private BlockPos target;
  private Vec3 travelAnchor;
  private BlockState targetState;
  private List<ItemStack> staged;
  private int progress, revision = -1;
  private boolean servicing;
  private boolean serviceArrived;
  private long retry, blockedAt = -1, nextPath;
  private String serviceReason = "Waiting for task";
  private int climb; // -1 down, +1 up, 0 ground navigation
  private double climbY;
  private boolean exiting;
  private boolean holdingLadder;
  private BlockPos ladderExit;
  private int serviceFlags;
  private List<BlockPos> sites;
  private BlockPos fallingStance, fallingTarget;
  private long yieldUntil;

  public Mining(Lumberton worker) {
    w = worker;
  }

  private ServerLevel level() {
    return (ServerLevel) w.level();
  }

  public ItemStack expectedOutput() {
    return staged != null && !staged.isEmpty()
        ? staged.get(0).copyWithCount(1)
        : new ItemStack(Items.COBBLESTONE);
  }

  public void releaseWork() {
    if (access != null) {
      if (w.getUUID().equals(access.builder)) access.builder = null;
      if (climb == 0 && !exiting && !holdingLadder && w.getUUID().equals(access.traveller))
        access.traveller = null;
    }
    if (w.level() instanceof ServerLevel l) WorldData.get(l).release(w.getUUID());
    w.clearChop();
    progress = 0;
  }

  public void release() {
    releaseWork();
    servicing = false;
  }

  public String diagnostic() {
    return w.status()
        + " mine="
        + (access == null ? "none" : access.id)
        + " target="
        + target
        + " progress="
        + progress
        + " path="
        + w.getNavigation().getPath()
        + " done="
        + w.getNavigation().isDone()
        + " exiting="
        + exiting
        + " reason="
        + serviceReason
        + " climb="
        + climb
        + " climbY="
        + climbY
        + " feet="
        + w.position()
        + " ground="
        + w.onGround()
        + " gravity="
        + !w.isNoGravity()
        + " cargo="
        + w.cargo.serializeNBT();
  }

  private void changed(Station s) {
    s.setChanged();
    WorldData.get(level()).setDirty();
  }

  private void reason(Station s, String reason, int flags) {
    w.alerts = flags;
    w.state(reason);
    if (!reason.equals(s.mine.reason)) {
      s.mine.reason = reason;
      s.changed();
    }
  }

  private void service(Station s, String reason, int flags) {
    if (reason.startsWith("Needs") && (flags & 16) != 0) flags = 0;
    if (!reason.startsWith("Needs")
        && !reason.equals("Cargo full")
        && !reason.equals("Storage full")) {
      releaseWork();
      w.getNavigation().stop();
      reason(s, reason, flags);
      retry = level().getGameTime() + 20;
      serviceReason = reason;
      return;
    }
    boolean building =
        access != null
            && (access.stage != MinePlan.Stage.READY
                || access.entrance != null && w.getY() < access.chamber().getY() + .7)
            && w.getUUID().equals(access.builder)
            && flags != 1;
    releaseWork();
    if (building) {
      access.builder = w.getUUID();
      access.builderUntil = level().getGameTime() + 60;
    }
    if (!servicing) serviceArrived = false;
    servicing = true;
    serviceReason = reason;
    serviceFlags = flags;
    w.alerts = 0;
    serviceTick(s);
  }

  public Map<Item, Integer> reserve() {
    Map<Item, Integer> result = new LinkedHashMap<>();
    Station s = w.station();
    MinePlan mine = s == null ? null : s.mine;
    if (w.job() != 2 || mine == null || mine.paused) return result;
    if (mine.stage == MinePlan.Stage.SITE) {
      // The complete kit is validated across real staging chests before site reservation.
      // Carry one material at a time so a nine-slot miner still has room for excavation loot.
      result.put(Items.COBBLESTONE, 59);
      return result;
    }
    if (mine.stage == MinePlan.Stage.ARCH && mine.rustic) {
      var tasks = mine.constructionTasks(level());
      for (int i = mine.construction; i < tasks.size(); i++)
        if (tasks.get(i).placement()) {
          ItemStack cost = BuildingTemplate.cost(tasks.get(i).place());
          int remaining = 0;
          for (int j = i; j < tasks.size(); j++)
            if (tasks.get(j).placement()
                && tasks.get(j).place().getBlock().asItem() == cost.getItem()) remaining++;
          result.put(cost.getItem(), Math.min(64, remaining));
          break;
        }
      return result;
    }
    if (mine.stage == MinePlan.Stage.SITE || mine.stage == MinePlan.Stage.ARCH) {
      result.put(Items.COBBLESTONE, 38);
      result.put(Items.OAK_PLANKS, 4);
      result.put(Items.LADDER, mine.shaftDepth);
      if (mine.stage == MinePlan.Stage.ARCH) {
        result.clear();
        var tasks = mine.constructionTasks(level());
        for (int i = mine.construction; i < tasks.size(); i++) {
          var t = tasks.get(i);
          if (t.placement()
              && (!level().hasChunkAt(t.pos())
                  || !level().getBlockState(t.pos()).equals(t.place())))
            result.merge(t.place().getBlock().asItem(), 1, Integer::sum);
        }
        result.put(Items.LADDER, mine.shaftDepth);
      }
    } else if (mine.stage == MinePlan.Stage.SHAFT) {
      result.put(Items.LADDER, Math.max(0, mine.ladderCount() - mine.construction / 3));
      int backing = 0;
      for (int d = mine.firstLadder(); d <= mine.shaftDepth; d++) {
        BlockPos p = mine.ladder(d).relative(mine.forward.getOpposite());
        if (!level().hasChunkAt(p)
            || !level().getBlockState(p).isFaceSturdy(level(), p, mine.forward)) backing++;
      }
      if (backing > 0) result.put(Items.COBBLESTONE, backing);
    } else {
      int ladders = 0;
      if (mine.entrance != null)
        for (int d = mine.firstLadder(); d <= mine.shaftDepth; d++)
          if (level().hasChunkAt(mine.ladder(d))
              && !level().getBlockState(mine.ladder(d)).is(Blocks.LADDER)) ladders++;
      if (ladders > 0) result.put(Items.LADDER, ladders);
      int backing = 0;
      if (mine.entrance != null)
        for (int d = mine.firstLadder(); d <= mine.shaftDepth; d++) {
          BlockPos p = mine.ladder(d).relative(mine.forward.getOpposite());
          if (level().hasChunkAt(p)
              && !level().getBlockState(p).isFaceSturdy(level(), p, mine.forward)) backing++;
        }
      if (backing > 0) result.put(Items.COBBLESTONE, backing);
      // Only the next staircase tread may need a support, never unlimited building stock.
      if (mine.mode == MinePlan.Mode.QUARRY
          && mine.quarrySide != 0
          && !MiningRules.supported(level(), mine.step(mine.quarryLayer)))
        result.merge(Items.COBBLESTONE, 1, Integer::sum);
    }
    if (mine.depotRequired && !mine.depotBuilt) {
      long remaining =
          mine.entrance == null
              ? 4
              : mine.depotPositions().stream()
                  .filter(
                      p ->
                          !mine.placedChests.contains(p.asLong())
                              || !level().getBlockState(p).is(Blocks.CHEST))
                  .count();
      result.put(Items.CHEST, (int) remaining);
      if (mine.stage == MinePlan.Stage.STORAGE)
        result.put(
            Items.COBBLESTONE,
            (int)
                mine.constructionTasks(level()).stream()
                    .filter(
                        t ->
                            t.activity().equals("Supporting storage")
                                && !level()
                                    .getBlockState(t.pos())
                                    .isFaceSturdy(level(), t.pos(), Direction.UP))
                    .count());
    }
    result.put(Items.TORCH, Balance.TORCH_STOCK.get());
    return result;
  }

  public int carried(Item item) {
    int count = 0;
    for (int i = 0; i < w.cargo.getSlots(); i++)
      if (w.cargo.getStackInSlot(i).is(item)) count += w.cargo.getStackInSlot(i).getCount();
    return count;
  }

  public Map<Item, Integer> requiredStock() {
    Station station = w.station();
    MinePlan m = station == null ? null : station.mine;
    if (m == null) return Map.of();
    if (m.stage == MinePlan.Stage.SITE) {
      Map<Item, Integer> kit = new LinkedHashMap<>(m.minehead(level()).materials);
      kit.put(Items.CHEST, 4);
      kit.put(Items.LADDER, m.ladderCount());
      kit.put(Items.TORCH, Balance.TORCH_STOCK.get());
      return kit;
    }
    if (m.stage == MinePlan.Stage.ARCH && m.rustic) {
      Map<Item, Integer> required = new LinkedHashMap<>();
      var tasks = m.constructionTasks(level());
      for (int i = m.construction; i < tasks.size(); i++)
        if (tasks.get(i).placement()
            && !level().getBlockState(tasks.get(i).pos()).equals(tasks.get(i).place())) {
          ItemStack cost = BuildingTemplate.cost(tasks.get(i).place());
          if (!cost.isEmpty()) required.merge(cost.getItem(), cost.getCount(), Integer::sum);
        }
      return required;
    }
    return reserve();
  }

  public boolean wants(ItemStack stack) {
    if (Inventories.tool(2, stack)) {
      if (replacementSlot(stack) >= 0) return true;
      for (int i = 0; i < 3; i++) if (w.tools.getStackInSlot(i).isEmpty()) return true;
    }
    return reserve().getOrDefault(stack.getItem(), 0) > carried(stack.getItem())
        && Inventories.insert(w.cargo, stack.copyWithCount(1), true).isEmpty();
  }

  public int keep(ItemStack stack) {
    return reserve().getOrDefault(stack.getItem(), 0);
  }

  public int replacementSlot(ItemStack candidate) {
    if (targetState == null
        || !MiningRules.capable(candidate, targetState)
        || MiningRules.select(w, targetState) >= 0) return -1;
    for (int i = 0; i < 3; i++) if (Inventories.tool(2, w.tools.getStackInSlot(i))) return i;
    return -1;
  }

  public boolean needsTool() {
    if (targetState != null) return MiningRules.select(w, targetState) < 0;
    for (int i = 0; i < 3; i++) if (Inventories.pick(w.tools.getStackInSlot(i))) return false;
    return true;
  }

  public boolean acceptsNeededTool(ItemStack candidate) {
    return targetState == null
        ? Inventories.pick(candidate)
        : MiningRules.capable(candidate, targetState);
  }

  public boolean courierTravel(MinePlan mine, BlockPos destination, String message) {
    if (access != null && !access.id.equals(mine.id) && returnToSurface()) return false;
    access = mine;
    if (goUnderground(mine.chamber()) || routeToWork(destination)) return false;
    return w.travelTo(destination, message, message);
  }

  public boolean hasOutput() {
    Map<Item, Integer> kept = new HashMap<>();
    for (int i = 0; i < w.cargo.getSlots(); i++) {
      ItemStack stack = w.cargo.getStackInSlot(i);
      int before = kept.getOrDefault(stack.getItem(), 0), limit = keep(stack);
      if (before + stack.getCount() > limit) return true;
      kept.merge(stack.getItem(), stack.getCount(), Integer::sum);
    }
    return false;
  }

  private boolean suppliesReady() {
    Station s = w.station();
    if (s != null && s.mine != null && s.mine.stage == MinePlan.Stage.SITE) {
      Map<Item, Integer> available = new HashMap<>();
      BuildingProject.count(w.cargo, available);
      for (BlockPos p : s.chests)
        if ((s.flags(p) & 2) != 0) {
          var h = Storage.handler(level(), p, s);
          if (h != null) BuildingProject.count(h, available);
        }
      Map<Item, Integer> kit = new HashMap<>(s.mine.minehead(level()).materials);
      kit.put(Items.LADDER, s.mine.ladderCount());
      kit.put(Items.CHEST, 4);
      return kit.entrySet().stream()
          .allMatch(e -> available.getOrDefault(e.getKey(), 0) >= e.getValue());
    }
    for (var need : reserve().entrySet())
      if (need.getKey() != Items.TORCH && carried(need.getKey()) < need.getValue()) return false;
    return true;
  }

  private void serviceTick(Station s) {
    if (access != null && w.getUUID().equals(access.builder))
      access.builderUntil = level().getGameTime() + 60;
    // End a completed supply trip before selecting another chest or returning to the totem.
    if (!hasOutput()
        && (serviceReason.startsWith("Needs")
            || serviceReason.equals("Cargo full")
            || serviceReason.equals("Storage full"))
        && !needsTool()
        && suppliesReady()
        && (serviceFlags != 64 || carried(Items.TORCH) > 0)) {
      servicing = false;
      serviceArrived = false;
      retry = 0;
      blockedAt = -1;
      w.alerts = 0;
      return;
    }
    if (access != null && access.depotBuilt) {
      if (w.getY() < access.entrance.getY() - 2) {
        for (BlockPos chest : access.depotPositions()) {
          var handler = Storage.depotHandler(level(), chest, access);
          if (handler == null) continue;
          boolean useful = false;
          for (int i = 0; i < handler.getSlots(); i++)
            if (wants(handler.getStackInSlot(i))) {
              useful = true;
              break;
            }
          if (!useful) continue;
          if (returnToPlatform()) return;
          if (w.travelTo(chest, "Returning to mine storage", "Restocking underground"))
            Storage.transfer(w, s, chest, handler, false, true);
          return;
        }
      }
      if (hasOutput()) {
        if (returnToPlatform()) return;
        for (BlockPos chest : access.depotPositions()) {
          var handler = Storage.depotHandler(level(), chest, access);
          if (handler == null) continue;
          boolean accepts = false;
          for (int i = 0; i < w.cargo.getSlots(); i++) {
            ItemStack stack = w.cargo.getStackInSlot(i);
            if (!stack.isEmpty()
                && carried(stack.getItem()) > keep(stack)
                && Storage.accepts(handler, stack)) accepts = true;
          }
          if (!accepts) continue;
          if (w.travelTo(chest, "Returning to mine storage", "Unloading underground"))
            Storage.transfer(w, s, chest, handler, true, false);
          return;
        }
        reason(s, "Underground storage full or blocked", Lumberton.STORAGE);
        return;
      }
      if (serviceReason.equals("Cargo full") || serviceReason.equals("Storage full")) {
        servicing = false;
        retry = 0;
        w.alerts = 0;
        return;
      }
      if (serviceReason.startsWith("Needs")
          && !needsTool()
          && suppliesReady()
          && carried(Items.TORCH) > 0) {
        servicing = false;
        retry = 0;
        w.alerts = 0;
        return;
      }
    }
    // Do not climb out just to discover that there are no useful supplies.
    if (!hasOutput() && !suppliesReady()
        || !hasOutput() && needsTool()
        || serviceFlags == 64 && carried(Items.TORCH) == 0) {
      boolean available = false;
      for (BlockPos p : s.chests) {
        if ((s.flags(p) & 2) == 0) continue;
        var handler = Storage.handler(level(), p, s);
        if (handler != null)
          for (int i = 0; i < handler.getSlots(); i++)
            if (wants(handler.extractItem(i, 64, true))) available = true;
      }
      if (!available) {
        w.getNavigation().stop();
        w.alerts = serviceFlags;
        w.state(serviceReason + " — waiting for supplies");
        return;
      }
    }
    if (returnToSurface()) return;
    if (level().getGameTime() < retry) {
      w.state(serviceReason);
      return;
    }
    w.pathDeferred = false;
    BlockPos chest = hasOutput() ? Storage.choose(w, s, false) : null;
    if (chest == null && w.pathDeferred) {
      w.state("Planning storage route");
      return;
    }
    if (chest == null) chest = Storage.choose(w, s, true);
    if (chest == null && w.pathDeferred) {
      w.state("Planning storage route");
      return;
    }
    if (chest != null) {
      w.alerts = 0;
      if (w.travelTo(
          chest,
          hasOutput() ? "Unloading" : "Restocking",
          hasOutput() ? "Unloading" : "Restocking")) Storage.transfer(w, s, chest);
      return;
    }
    servicing = false;
    blockedAt = -1;
    retry = level().getGameTime() + 40;
    w.alerts = serviceFlags;
    w.state(serviceReason);
  }

  public void tick(Station s) {
    if (s.mine == null) {
      s.mine = new MinePlan();
      s.changed();
    }
    MinePlan m = s.mine;
    if (access != null && !access.id.equals(m.id) && returnToSurface()) return;
    access = m;
    if (m.stage.ordinal() > MinePlan.Stage.ARCH.ordinal()) m.entranceComplete = true;
    if (m.entrance != null) WorldData.get(level()).mines.put(m.id, m);
    if (revision != m.revision) {
      releaseWork();
      revision = m.revision;
      target = null;
      staged = null;
    }
    if (m.paused) {
      releaseWork();
      w.getNavigation().stop();
      w.alerts = 0;
      w.state("Mine paused — holding position");
      return;
    }
    if (servicing) {
      serviceTick(s);
      return;
    }
    if (level().getGameTime() < retry) {
      w.state(serviceReason);
      return;
    }
    if (m.stage == MinePlan.Stage.READY
        && w.getY() >= m.entrance.getY() - .2
        && (needsTool() || carried(Items.TORCH) == 0)) {
      service(s, needsTool() ? "Needs capable tool" : "Needs torches", needsTool() ? 1 : 64);
      return;
    }
    if (m.stage.ordinal() > MinePlan.Stage.SHAFT.ordinal()
        && !level().getBlockState(m.ladder(1)).equals(m.ladderState())
        && !repairTopLadder(s)) return;
    if (m.stage == MinePlan.Stage.READY && goUnderground(m.chamber())) return;
    boolean independentTunnel = m.stage == MinePlan.Stage.READY && m.mode == MinePlan.Mode.TUNNEL;
    if (independentTunnel && m.claimTunnel(w, s) == null) {
      reason(s, "No unclaimed tunnel fits in mining range", 0);
      return;
    }
    if (level().getGameTime() < yieldUntil || !independentTunnel && !m.claim(w)) {
      reason(
          s,
          m.stage == MinePlan.Stage.READY
              ? "Sharing mine — waiting for work face"
              : "Waiting for builder",
          0);
      return;
    }
    if (m.redirecting) {
      redirect(s);
      return;
    }
    if (m.depotBuilt
        && m.depotPositions().stream()
            .anyMatch(p -> !level().hasChunkAt(p) || !level().getBlockState(p).is(Blocks.CHEST))) {
      m.depotBuilt = false;
      m.stage = MinePlan.Stage.STORAGE;
      m.construction = 0;
      changed(s);
    }
    if (m.stage == MinePlan.Stage.STORAGE) {
      buildDepot(s);
      return;
    }
    if (m.mode == MinePlan.Mode.QUARRY && m.quarrySide == 0) {
      m.quarrySide = m.lateral == 0 ? 1 : -m.lateral;
      changed(s);
    }
    MinePlan.Tunnel ownTunnel = independentTunnel ? m.tunnel(w.getUUID()) : null;
    if (ownTunnel != null && ownTunnel.wet) {
      tunnel(s);
      return;
    }
    if (m.stage != MinePlan.Stage.SITE && m.stage != MinePlan.Stage.ARCH && lighting(s)) return;
    if (m.stage == MinePlan.Stage.SITE) {
      if (!suppliesReady()) {
        service(s, "Needs entrance supplies", 16);
        return;
      }
      if (!Inventories.pick(w.activeTool())
          && w.tools.getStackInSlot(0).isEmpty()
          && w.tools.getStackInSlot(1).isEmpty()
          && w.tools.getStackInSlot(2).isEmpty()) {
        service(s, "Needs capable tool", 1);
        return;
      }
      findSite(s);
      return;
    }
    if (!suppliesReady() && m.stage != MinePlan.Stage.READY) {
      service(s, "Needs construction supplies", 16);
      return;
    }
    if (m.stage != MinePlan.Stage.READY) {
      var tasks = m.constructionTasks(level());
      // Skip already-correct cells in one bounded pass, including partial entrance reloads.
      for (int skipped = 0; skipped < 32 && m.construction < tasks.size(); skipped++) {
        var next = tasks.get(m.construction);
        var actual = level().getBlockState(next.pos());
        if (next.placement() && Construction.matches(actual, next.place())
            || !next.placement() && actual.isAir()) m.construction++;
        else if (!next.placement()
            && m.construction + 1 < tasks.size()
            && tasks.get(m.construction + 1).placement()
            && tasks.get(m.construction + 1).pos().equals(next.pos())
            && Construction.matches(actual, tasks.get(m.construction + 1).place()))
          m.construction += 2;
        else break;
      }
      if (m.construction >= tasks.size()) {
        if (m.stage == MinePlan.Stage.ARCH) {
          m.entranceComplete = true;
          m.stage = MinePlan.Stage.SHAFT;
          m.retain(m.entrance.relative(m.forward));
        } else if (m.stage == MinePlan.Stage.SHAFT) m.stage = MinePlan.Stage.CHAMBER;
        else {
          m.stage = m.depotRequired ? MinePlan.Stage.STORAGE : MinePlan.Stage.READY;
          for (int x = -1; x <= 1; x++)
            for (int z = 0; z <= 2; z++) m.retain(m.at(x, -m.shaftDepth, z));
        }
        m.construction = 0;
        changed(s);
        s.changed();
        return;
      }
      var task = tasks.get(m.construction);
      if (m.stage == MinePlan.Stage.SHAFT) {
        int depth = m.construction / 3 + m.firstLadder();
        if (depth > m.firstLadder() && goUnderground(m.entrance.below(depth - 1))) return;
      } else if (m.stage == MinePlan.Stage.CHAMBER && goUnderground(m.chamber())) return;
      // Existing full terrain is valid ladder backing; no free replacement is needed.
      if (task.activity().equals("Backing ladder")
          && level().getBlockState(task.pos()).isFaceSturdy(level(), task.pos(), m.forward)) {
        m.construction++;
        changed(s);
        return;
      }
      if (execute(s, task)) {
        m.construction++;
        changed(s);
      }
      return;
    }
    if (!accessValid(s)) return;
    if (m.mode == MinePlan.Mode.TUNNEL) tunnel(s);
    else quarry(s);
  }

  private void findSite(Station s) {
    MinePlan m = s.mine;
    if (level().getGameTime() < m.nextSearch) {
      reason(s, "Finding mine site", 0);
      return;
    }
    if (sites == null) {
      sites = new ArrayList<>();
      int r = s.miningRange();
      for (int x = -r; x <= r; x++)
        for (int z = -r; z <= r; z++) sites.add(s.getBlockPos().offset(x, 0, z));
      sites.sort(
          Comparator.comparingDouble((BlockPos p) -> p.distSqr(s.getBlockPos()))
              .thenComparingLong(BlockPos::asLong));
    }
    if (!WorldEvents.budget(400)) {
      w.state("Finding mine site");
      return;
    }
    if (m.search >= sites.size() * 4) {
      m.search = 0;
      m.nextSearch = level().getGameTime() + 200;
      service(s, "No safe mine site in range", 16);
      return;
    }
    BlockPos column = sites.get(m.search / 4);
    Direction facing = Direction.from2DDataValue(m.search++ % 4);
    if (!level().hasChunkAt(column)) {
      reason(s, "Area unloaded", 0);
      return;
    }
    int y =
        level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
    BlockPos p = new BlockPos(column.getX(), y, column.getZ());
    if (Math.abs(y - s.getBlockPos().getY()) > Balance.MINING_HEIGHT.get()
        || !WorldData.get(level()).siteAvailable(p)) return;
    MinePlan proposed = new MinePlan();
    proposed.rustic = true;
    proposed.entrance = p;
    proposed.forward = facing;
    if (!proposed.laneFits(s, 0, 1)
        || (!proposed.laneFits(s, 1, 1) && !proposed.laneFits(s, 1, -1))) return;
    for (int x = -3; x <= 3; x++)
      for (int z = -3; z <= 3; z++) {
        BlockPos ground = proposed.at(x, 0, z);
        if (!siteCell(s, ground) || !MiningRules.supported(level(), ground)) return;
        for (int dy = -1; dy < 6; dy++) if (!siteCell(s, ground.above(dy))) return;
      }
    for (int d = 1; d <= m.shaftDepth; d++) if (!siteCell(s, p.below(d))) return;
    for (int x = -1; x <= 1; x++)
      for (int z = 0; z <= 2; z++)
        for (int dy = -m.shaftDepth; dy <= 2 - m.shaftDepth; dy++)
          if (!siteCell(s, proposed.at(x, dy, z))) return;
    for (int z = 3; z <= 34; z++)
      if (!siteCell(s, proposed.at(0, -m.shaftDepth, z))
          || !siteCell(s, proposed.at(0, 1 - m.shaftDepth, z))) return;
    if (!w.canReach(p)) {
      if (w.pathDeferred) m.search--;
      return;
    }
    m.entrance = p;
    m.rustic = true;
    m.forward = facing;
    m.stage = MinePlan.Stage.ARCH;
    m.construction = 0;
    WorldData.get(level()).mines.put(m.id, m);
    changed(s);
    s.changed();
  }

  private boolean siteCell(Station s, BlockPos p) {
    if (!level().hasChunkAt(p)
        || level().isOutsideBuildHeight(p)
        || !MinePlan.inBounds(s, p)
        || WorldData.get(level()).protectedAt(p)
        || WorldData.get(level()).buildings.values().stream()
            .anyMatch(b -> b.approved && b.contains(level(), p))) return false;
    BlockState b = level().getBlockState(p);
    return !b.hasBlockEntity()
        && level().getFluidState(p).isEmpty()
        && (Construction.soft(b) || b.is(MiningRules.TERRAIN));
  }

  private boolean accessValid(Station s) {
    MinePlan m = s.mine;
    for (int d = m.firstLadder(); d <= m.shaftDepth; d++) {
      BlockPos p = m.ladder(d);
      if (!level().hasChunkAt(p)) {
        reason(s, "Area unloaded", 0);
        return false;
      }
      if (!level().getBlockState(p).equals(m.ladderState())) {
        if (level().getBlockState(p).isAir()) {
          if (!suppliesReady()) {
            service(s, "Needs access supplies", 16);
            return false;
          }
          BlockPos backing = p.relative(m.forward.getOpposite());
          if (!level().hasChunkAt(backing)) {
            reason(s, "Area unloaded", 0);
            return false;
          }
          if (!level().getBlockState(backing).isFaceSturdy(level(), backing, m.forward)) {
            if (w.inReach(backing))
              execute(
                  s,
                  new MinePlan.Task(
                      backing, Blocks.COBBLESTONE.defaultBlockState(), "Repairing access"));
            else if (w.getY() >= m.entrance.getY() - .2)
              goUnderground(m.entrance.below(Math.max(1, d - 1)));
            return false;
          }
          if (w.inReach(p)) execute(s, new MinePlan.Task(p, m.ladderState(), "Repairing ladder"));
          else if (w.getY() >= m.entrance.getY() - .2)
            goUnderground(m.entrance.below(Math.max(1, d - 1)));
          else reason(s, "Access blocked: ladder out of reach", 16);
        } else {
          reason(s, "Access blocked: ladder segment " + d, 16);
          if (w.getY() >= m.entrance.getY() - .5 && !suppliesReady())
            service(s, "Needs ladders", 16);
        }
        return false;
      }
    }
    return true;
  }

  private boolean repairTopLadder(Station s) {
    MinePlan m = s.mine;
    BlockPos p = m.ladder(1);
    if (!level().hasChunkAt(p)) {
      reason(s, "Area unloaded", 0);
      return false;
    }
    // Approach from below when already in an older mine whose top rung was omitted.
    if (w.getY() < m.entrance.getY() - 3) {
      if (!atShaft()) {
        if (!routeBack()) walkTo(m.ladder(m.shaftDepth));
      } else if (climb == 0 && !exiting && passageClaim()) {
        climb = 1;
        holdingLadder = false;
        climbY = m.entrance.getY() - 2;
        w.setDeltaMovement(Vec3.ZERO);
      }
      w.state("Repairing top ladder");
      return false;
    }
    if (climb != 0 || exiting) return false;
    BlockPos backing = p.relative(m.forward.getOpposite());
    if (!level().getBlockState(backing).isFaceSturdy(level(), backing, m.forward)) {
      execute(
          s, new MinePlan.Task(backing, Blocks.COBBLESTONE.defaultBlockState(), "Backing ladder"));
      return false;
    }
    BlockState current = level().getBlockState(p);
    if (!current.isAir() && !current.is(Blocks.LADDER)) {
      execute(s, new MinePlan.Task(p, null, "Excavating shaft"));
      return false;
    }
    if (current.is(Blocks.LADDER)) {
      if (!w.inReach(p)) w.travelTo(p, "Repairing top ladder", "Repairing top ladder");
      else if (!WorldData.get(level()).protectedAt(p)) MiningRules.place(w, p, m.ladderState());
      return level().getBlockState(p).equals(m.ladderState());
    }
    return execute(s, new MinePlan.Task(p, m.ladderState(), "Installing top ladder"));
  }

  private void tunnel(Station s) {
    MinePlan m = s.mine;
    MinePlan.Tunnel t = m.claimTunnel(w, s);
    if (t == null) {
      reason(s, "No unclaimed tunnel fits in mining range", 0);
      return;
    }
    m.reportTunnel(w, t);
    if (t.wet) {
      if (returnToPlatform()) return;
      t.finished = true;
      m.claimTunnel(w, s);
      target = null;
      progress = 0;
      staged = null;
      changed(s);
      return;
    }
    // Empty cells and the shared crosscut do not add a pause between real block breaks.
    for (int skipped = 0; skipped < 16; skipped++) {
      BlockPos p = t.face(m);
      if (!MinePlan.inBounds(s, p) || !MinePlan.inBounds(s, t.at(m, t.lane * 3 * t.lateral, 34))) {
        t.finished = true;
        changed(s);
        return;
      }
      if (!MiningRules.supported(level(), p)) {
        service(s, "Tunnel floor missing", 16);
        return;
      }
      BlockPos cell = t.row == 0 ? p.above() : p;
      boolean alreadyAir = level().getBlockState(cell).isAir();
      if (!execute(s, new MinePlan.Task(cell, null, "Mining"))) return;
      if (++t.row >= 2) {
        t.row = 0;
        m.retain(p);
        if (t.connector < t.lane * 3) t.connector++;
        else if (++t.column >= 32) t.finished = true;
      }
      m.reportTunnel(w, t);
      changed(s);
      if (!alreadyAir || t.finished) return;
    }
  }

  private void quarry(Station s) {
    MinePlan m = s.mine;
    if (m.quarryRoute < 0) {
      m.quarryRoute = m.route;
      changed(s);
    }
    if (m.quarrySide == 0) {
      m.quarrySide = m.lateral == 0 ? 1 : -m.lateral;
      changed(s);
    }
    for (int x : new int[] {-2, 2})
      for (int z : new int[] {-2, 2})
        if (!MinePlan.inBounds(
            s,
            m.quarryOrigin()
                .relative(m.quarryFacing().getClockWise(), x)
                .relative(m.quarryFacing(), z))) {
          service(s, "Quarry conflicts with mining range", 16);
          return;
        }
    if (goUnderground(m.chamber())) return;
    // A level side connection keeps a quarry separate from the original tunnel floor.
    if (!m.edgeQuarry && m.quarryStep < 10) {
      int x = m.quarryStep / 2 + 1, up = m.quarryStep % 2 == 0 ? 1 : 0;
      BlockPos feet = m.quarryAt(x * m.quarrySide, -m.shaftDepth, 3);
      if (!MiningRules.supported(level(), feet)) {
        service(s, "Quarry connection needs support", 16);
        return;
      }
      if (execute(s, new MinePlan.Task(feet.above(up), null, "Mining"))) {
        m.quarryStep++;
        if (up == 0) m.retain(feet);
        changed(s);
      }
      return;
    }
    BlockPos tread = m.step(m.quarryLayer);
    if (tread.getY() <= level().getMinBuildHeight() + 1) {
      service(s, "Work complete: build limit", 32);
      return;
    }
    if (m.quarryCell < 4) {
      MinePlan.Task task =
          m.quarryCell < 3
              ? new MinePlan.Task(tread.above(2 - m.quarryCell), null, "Mining")
              : new MinePlan.Task(
                  tread.below(), Blocks.COBBLESTONE.defaultBlockState(), "Building quarry access");
      if (m.quarryCell == 3 && MiningRules.supported(level(), tread)) {
        m.retain(tread);
        m.quarryCell++;
        changed(s);
        return;
      }
      if (execute(s, task)) {
        m.quarryCell++;
        if (m.quarryCell == 4) m.retain(tread);
        changed(s);
      }
      return;
    }
    int index = m.quarryCell - 4;
    int height = m.quarryLayer == 0 ? 2 : 1;
    if (index >= 9 * height) {
      m.quarryLayer++;
      m.quarryCell = 0;
      changed(s);
      return;
    }
    int cell = index / height, x = new int[] {0, -1, 1}[cell % 3], z = cell / 3 - 1;
    BlockPos p =
        m.quarryOrigin()
            .relative(m.quarryFacing().getClockWise(), x)
            .relative(m.quarryFacing(), z)
            .below(m.quarryLayer)
            .above(height - 1 - index % height);
    if (execute(s, new MinePlan.Task(p, null, "Mining"))) {
      m.quarryCell++;
      // Light the permanent stairway. Lighting each temporary center floor causes
      // repeated torch relocation and trips back up levels that are about to be excavated.
      changed(s);
    }
  }

  private boolean execute(Station s, MinePlan.Task task) {
    BlockPos p = task.pos();
    if (!level().hasChunkAt(p)) {
      reason(s, "Area unloaded", 0);
      return false;
    }
    BlockState state = level().getBlockState(p);
    if (!task.placement()
        && s.mine.stage == MinePlan.Stage.READY
        && MiningRules.fluidAhead(level(), p)) {
      avoidFluid(s);
      return false;
    }
    if (MiningRules.fallingNow(level(), p)
        || !task.placement()
            && state.isAir()
            && level().getBlockState(p.above()).getBlock() instanceof FallingBlock) {
      w.getNavigation().stop();
      reason(s, "Waiting for falling material to settle", 0);
      return false;
    }
    if (!task.placement()
        && MiningRules.fallingRisk(level(), p)
        && !MiningRules.safeFallingStance(w, p, w.position())) {
      standBack(s, p);
      return false;
    }
    if (!task.placement() && relocateConflict(s, p)) return false;
    if (task.placement() ? Construction.matches(state, task.place()) : state.isAir()) {
      if (p.equals(target)) {
        progress = 0;
        staged = null;
        w.clearChop();
      }
      return true;
    }
    String hazard = MiningRules.hazard(w, s, task);
    if (hazard != null) {
      if (hazard.equals("Occupied work position")
          && (w.getBoundingBox().intersects(new AABB(p))
              || w.getBoundingBox().move(0, -.1, 0).intersects(new AABB(p)))
          && retreat(s, p)) return false;
      if (hazard.equals("Fluid ahead") && s.mine.stage == MinePlan.Stage.READY) {
        avoidFluid(s);
        return false;
      }
      if (hazard.equals("Unbreakable terrain")
          && s.mine.mode == MinePlan.Mode.QUARRY
          && s.mine.quarryCell >= 4) {
        s.mine.exclusions.add(p.asLong());
        return true;
      }
      if (blockedAt < 0) blockedAt = level().getGameTime();
      if (level().getGameTime() - blockedAt > 80)
        service(s, hazard, hazard.startsWith("Waiting") || hazard.equals("Area unloaded") ? 0 : 16);
      else reason(s, hazard, 0);
      return false;
    }
    if (!p.equals(target) || !state.equals(targetState)) {
      target = p;
      targetState = state;
      progress = 0;
      blockedAt = -1;
      travelAnchor = null;
      staged = null;
      w.clearChop();
    }
    if (!task.placement()
        && !(s.mine.stage == MinePlan.Stage.ARCH && Construction.soft(state))
        && MiningRules.select(w, state) < 0) {
      service(s, "Needs capable tool", 1);
      return false;
    }
    if (!task.placement()
        && s.mine.stage != MinePlan.Stage.ARCH
        && carried(Items.TORCH) == 0
        && Arrays.stream(Direction.values())
                    .mapToInt(
                        d ->
                            level()
                                .getBrightness(
                                    net.minecraft.world.level.LightLayer.BLOCK, p.relative(d)))
                    .max()
                    .orElse(0)
                - 1
            < Balance.MINE_LIGHT.get()) {
      service(s, "Needs torches", 64);
      return false;
    }
    if (!w.inReach(p)) {
      if (routeToWork(p)) return false;
      if (!w.travelTo(p, "Travelling", task.activity())) {
        // Long, healthy return trips are not blocked paths. Time only a lack of movement;
        // otherwise a distant face can permanently stop a miner halfway back after unloading.
        if (travelAnchor == null || w.position().distanceToSqr(travelAnchor) > .25) {
          travelAnchor = w.position();
          blockedAt = level().getGameTime();
        }
        if (blockedAt < 0) blockedAt = level().getGameTime();
        if (level().getGameTime() - blockedAt > 160) {
          travelAnchor = null;
          blockedAt = -1;
          service(s, "Access blocked", 16);
        }
        return false;
      }
    }
    blockedAt = -1;
    travelAnchor = null;
    if (!WorldData.get(level()).reserve(List.of(p), w.getUUID())) {
      reason(s, "Waiting for colleague", 0);
      return false;
    }
    if (task.placement()) {
      Item material = task.place().getBlock().asItem();
      if (material == Items.COBBLESTONE
          && carried(material) == 0
          && carried(Items.DIRT) > 0
          && task.activity().contains("quarry")) material = Items.DIRT;
      if (carried(material) == 0) {
        service(s, material == Items.LADDER ? "Needs ladders" : "Needs building blocks", 16);
        return false;
      }
      BlockState placed = material == Items.DIRT ? Blocks.DIRT.defaultBlockState() : task.place();
      if (!w.authorized
          || w.job() != 2
          || s.mine.paused
          || revision != s.mine.revision
          || MiningRules.hazard(w, s, task) != null) return false;
      if (MiningRules.place(w, p, placed)) {
        if (placed.is(Blocks.CHEST)) {
          s.mine.placedChests.add(p.asLong());
          s.mine.supports.add(p.below().asLong());
          s.mine.supports.add(
              p.relative(placed.getValue(ChestBlock.FACING).getOpposite()).asLong());
          changed(s);
        }
        for (int i = 0; i < w.cargo.getSlots(); i++)
          if (w.cargo.getStackInSlot(i).is(material)) {
            w.cargo.extractItem(i, 1, false);
            break;
          }
        w.state(task.activity());
        w.clearChop();
        WorldData.get(level()).release(w.getUUID());
        return true;
      }
      reason(s, "Placement denied or unsupported", 16);
      return false;
    }
    int duration = MiningRules.duration(w.activeTool(), state, level(), p, s);
    if (s.mine.stage == MinePlan.Stage.ARCH && Construction.soft(state))
      duration = Math.min(4, duration);
    reason(s, "Mining", 0);
    w.getNavigation().stop();
    progress++;
    w.workVisual(p, progress, duration);
    if (progress % 20 == 0 || progress == 1) {
      var sound = state.getSoundType(level(), p, w);
      level().playSound(null, p, sound.getHitSound(), SoundSource.NEUTRAL, .45f, sound.getPitch());
    }
    if (progress < duration) return false;
    if (!w.authorized
        || w.job() != 2
        || s.mine.paused
        || revision != s.mine.revision
        || !w.inReach(p)
        || MiningRules.hazard(w, s, task) != null
        || !level().getBlockState(p).equals(state)
        || !(s.mine.stage == MinePlan.Stage.ARCH && Construction.soft(state))
            && !MiningRules.capable(w.activeTool(), state)) return false;
    if (staged == null) staged = Block.getDrops(state, level(), p, null, w, w.activeTool());
    if (!Inventories.fits(w.cargo, staged)) {
      service(s, "Cargo full", Lumberton.CARGO);
      return false;
    }
    // Cargo capacity is the immediate limit. The miner creates its own depot below;
    // requiring an existing output chest here deadlocks the very first shaft block.
    int xp = MiningRules.breakPermission(w, p, state);
    if (xp < 0) {
      reason(s, "Mining denied", 16);
      progress = 0;
      staged = null;
      return false;
    }
    if (task.activity().equals("Excavating shaft") && w.onClimbable()) {
      holdingLadder = true;
      w.setNoGravity(true);
      w.setDeltaMovement(Vec3.ZERO);
    }
    boolean settling = MiningRules.fallingRisk(level(), p);
    if (!level().getBlockState(p).equals(state)
        || !level().setBlock(p, Blocks.AIR.defaultBlockState(), 3)) return false;
    for (ItemStack drop : staged) {
      ItemStack rest = Inventories.insert(w.cargo, drop, false);
      if (!rest.isEmpty()) drop(rest, p);
    }
    if (xp > 0) net.minecraft.world.entity.ExperienceOrb.award(level(), Vec3.atCenterOf(p), xp);
    w.wear(1);
    level().levelEvent(2001, p, Block.getId(state));
    staged = null;
    progress = 0;
    w.clearChop();
    WorldData.get(level()).release(w.getUUID());
    if (s.mine.stage == MinePlan.Stage.READY) {
      s.mine.release(w.getUUID());
      if (s.mine.mode == MinePlan.Mode.QUARRY
          && s.loadedWorkers().stream()
              .filter(peer -> peer.job() == 2 && peer != w)
              .findAny()
              .isPresent()) yieldUntil = level().getGameTime() + 2;
    }
    return !settling;
  }

  private void buildDepot(Station s) {
    MinePlan m = s.mine;
    if (!suppliesReady()) {
      service(s, "Needs four mine chests and supports", 16);
      return;
    }
    if (goUnderground(m.chamber())) return;
    for (MinePlan.Task task : m.constructionTasks(level())) {
      BlockState existing = level().getBlockState(task.pos());
      if (!task.placement() && existing.isAir()) continue;
      if (task.activity().equals("Supporting storage")
          && existing.isFaceSturdy(level(), task.pos(), Direction.UP)) continue;
      execute(s, task);
      return;
    }
    var positions = m.depotPositions();
    for (int i = 0; i < 4; i += 2) {
      BlockPos first = positions.get(i), second = positions.get(i + 1);
      BlockState state = level().getBlockState(first);
      Direction connection =
          Direction.fromDelta(second.getX() - first.getX(), 0, second.getZ() - first.getZ());
      var type =
          connection == state.getValue(ChestBlock.FACING).getClockWise()
              ? net.minecraft.world.level.block.state.properties.ChestType.LEFT
              : net.minecraft.world.level.block.state.properties.ChestType.RIGHT;
      level().setBlock(first, state.setValue(ChestBlock.TYPE, type), 3);
      level()
          .setBlock(
              second,
              level().getBlockState(second).setValue(ChestBlock.TYPE, type.getOpposite()),
              3);
    }
    m.depotBuilt = true;
    m.stage = MinePlan.Stage.READY;
    m.entranceComplete = true;
    m.release(w.getUUID());
    changed(s);
    s.changed();
  }

  private boolean returnToPlatform() {
    if (access == null || access.entrance == null) return false;
    if (w.getY() > access.chamber().getY() + .7 && goUnderground(access.chamber())) return true;
    if (routeBack()) return true;
    if (w.position().distanceToSqr(Vec3.atBottomCenterOf(access.chamber())) > 1) {
      walkTo(access.chamber());
      w.state("Returning to underground platform");
      return true;
    }
    return false;
  }

  private void redirect(Station s) {
    MinePlan m = s.mine;
    if (returnToPlatform()) return;
    for (int next = 0; next < 3; next++) {
      if (m.wetRoutes.contains(next)) continue;
      int previous = m.route;
      m.route = next;
      boolean fits = m.laneFits(s, 0, 1) && (m.laneFits(s, 1, 1) || m.laneFits(s, 1, -1));
      m.route = previous;
      if (!fits) continue;
      CompoundTag old = new CompoundTag();
      old.putInt("Route", m.route);
      old.putInt("Lane", m.lane);
      old.putInt("Column", m.column);
      old.putInt("QuarryLayer", m.quarryLayer);
      old.putInt("QuarrySide", m.quarrySide);
      m.abandonedRoutes.add(old);
      m.route = next;
      m.lane = m.column = m.turn = m.tunnelRow = m.lateral = 0;
      m.quarryLayer = m.quarryCell = m.quarryStep = m.quarrySide = 0;
      m.quarryRoute = -1;
      m.redirecting = false;
      m.revision++;
      target = null;
      staged = null;
      progress = 0;
      changed(s);
      reason(s, "Starting a dry direction", 0);
      return;
    }
    reason(s, "No dry direction remains", 16);
  }

  private void avoidFluid(Station s) {
    if (s.mine.mode == MinePlan.Mode.TUNNEL) {
      MinePlan.Tunnel t = s.mine.tunnel(w.getUUID());
      if (t != null) {
        if (!t.wet) s.mine.abandonedRoutes.add(t.save());
        t.wet = true;
        s.mine.wetRoutes.add(t.route);
        releaseWork();
        target = null;
        staged = null;
        progress = 0;
        changed(s);
        reason(s, "Fluid ahead: returning to platform", 0);
        return;
      }
    }
    releaseWork();
    target = null;
    staged = null;
    s.mine.wetRoutes.add(s.mine.route);
    s.mine.redirecting = true;
    changed(s);
    reason(s, "Fluid ahead: returning to platform", 0);
  }

  private boolean retreat(Station s, BlockPos target) {
    MinePlan m = s.mine;
    List<BlockPos> candidates = new ArrayList<>();
    if (m.stage == MinePlan.Stage.ARCH) {
      for (int x = -3; x <= 3; x++)
        for (int z = -3; z <= 3; z++) for (int y = 0; y <= 1; y++) candidates.add(m.at(x, y, z));
      candidates.sort(Comparator.comparingDouble(p -> w.distanceToSqr(Vec3.atBottomCenterOf(p))));
    }
    if (m.mode == MinePlan.Mode.QUARRY && m.quarrySide != 0)
      for (int d = m.quarryLayer; d >= Math.max(0, m.quarryLayer - 3); d--)
        candidates.add(m.step(d));
    if (m.entrance != null) candidates.add(m.chamber());
    for (BlockPos feet : candidates) {
      AABB box = w.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(feet));
      if (feet.below().equals(target)
          || box.intersects(new AABB(target))
          || !MiningRules.supported(level(), feet)
          || !level().noCollision(w, box)) continue;
      walkTo(feet);
      w.state("Moving to safe footing");
      return true;
    }
    return false;
  }

  void standBack(Station s, BlockPos target) {
    w.clearChop();
    progress = 0;
    reason(s, "Moving away from falling material", 0);
    if (!target.equals(fallingTarget)) {
      fallingStance = null;
      fallingTarget = target;
    }
    if (fallingStance != null
        && MiningRules.safeFallingStance(w, target, Vec3.atBottomCenterOf(fallingStance))) {
      walkTo(fallingStance);
      return;
    }
    if (level().getGameTime() < nextPath) return;
    if (!WorldEvents.pathBudget()) return;
    nextPath = level().getGameTime() + 20;
    List<BlockPos> candidates = new ArrayList<>();
    for (int x = -4; x <= 4; x++)
      for (int z = -4; z <= 4; z++)
        for (int y = -1; y <= 1; y++) {
          BlockPos feet =
              new BlockPos(target.getX() + x, w.blockPosition().getY() + y, target.getZ() + z);
          if (x * x + z * z < 6.25) continue;
          if (level().hasChunkAt(feet)
              && MiningRules.safeFallingStance(w, target, Vec3.atBottomCenterOf(feet)))
            candidates.add(feet);
        }
    candidates.sort(Comparator.comparingDouble(p -> w.distanceToSqr(Vec3.atBottomCenterOf(p))));
    for (BlockPos feet : candidates) {
      Path path = w.getNavigation().createPath(feet, 0);
      if (path != null && path.canReach()) {
        fallingStance = feet;
        nextPath = level().getGameTime() + 20;
        w.getNavigation().moveTo(path, 1);
        return;
      }
    }
    reason(s, "No safe footing for falling material", 16);
  }

  private BlockPos torchSupport(BlockPos p, BlockState state) {
    return state.is(Blocks.WALL_TORCH)
        ? p.relative(state.getValue(WallTorchBlock.FACING).getOpposite())
        : p.below();
  }

  private boolean relocateConflict(Station s, BlockPos excavation) {
    MinePlan m = s.mine;
    if (m.relocatingTorch != null) {
      lighting(s);
      return true;
    }
    List<BlockPos> neighbors = new ArrayList<>();
    neighbors.add(excavation);
    for (Direction direction : Direction.values()) neighbors.add(excavation.relative(direction));
    for (BlockPos torch : neighbors) {
      if (!m.torches.contains(torch.asLong()) || !level().hasChunkAt(torch)) continue;
      BlockState state = level().getBlockState(torch);
      if (!state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH)) {
        m.torches.remove(torch.asLong());
        continue;
      }
      if (!torch.equals(excavation) && !torchSupport(torch, state).equals(excavation)) continue;
      // Keep a replacement near the original light; never excavate a new cave to reach it.
      for (int d = 0; d <= 3; d++)
        for (int y = 0; y >= -2; y--)
          for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos floor = torch.relative(direction, d).above(y);
            if (m.passage.contains(floor.asLong())
                && MiningRules.supported(level(), floor)
                && level().getBlockState(floor).getCollisionShape(level(), floor).isEmpty()) {
              m.relocatingTorch = torch;
              m.relocationFloor = floor;
              m.excavationConflict = excavation;
              m.replacementPlaced = false;
              m.pendingLight = floor;
              changed(s);
              lighting(s);
              return true;
            }
          }
      service(s, "Lighting blocked", 16);
      return true;
    }
    // Reconcile a support whose light was removed outside the mine controller.
    if (m.torchSupports.remove(excavation.asLong())) changed(s);
    return false;
  }

  private boolean recoverTorch(Station s) {
    MinePlan m = s.mine;
    BlockPos p = m.relocatingTorch;
    if (!level().hasChunkAt(p)) {
      reason(s, "Area unloaded", 0);
      return true;
    }
    if (p.getY() < m.entrance.getY() - 1 && goUnderground(m.chamber())) return true;
    BlockState state = level().getBlockState(p);
    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
      if (!w.inReach(p)) {
        if (!routeToWork(p)) w.travelTo(p, "Travelling to light", "Recovering torch");
        return true;
      }
      if (!w.authorized
          || w.job() != 2
          || m.paused
          || revision != m.revision
          || !MinePlan.inBounds(s, p)
          || WorldData.get(level()).protectedAt(p)
          || !WorldData.get(level()).reserve(List.of(p), w.getUUID())) {
        reason(s, "Lighting blocked", 16);
        return true;
      }
      List<ItemStack> drops = Block.getDrops(state, level(), p, null, w, w.activeTool());
      if (!Inventories.fits(w.cargo, drops)) {
        service(s, "Cargo full", Lumberton.CARGO);
        return true;
      }
      if (MiningRules.breakPermission(w, p, state) < 0) {
        reason(s, "Lighting blocked", 16);
        return true;
      }
      BlockPos support = torchSupport(p, state);
      if (!level().getBlockState(p).equals(state)
          || !level().setBlock(p, Blocks.AIR.defaultBlockState(), 3)) return true;
      for (ItemStack drop : drops) Inventories.insert(w.cargo, drop, false);
      boolean another = false;
      for (Direction direction : Direction.values()) {
        BlockPos q = support.relative(direction);
        if (!level().hasChunkAt(q) || !m.torches.contains(q.asLong())) continue;
        BlockState other = level().getBlockState(q);
        if ((other.is(Blocks.TORCH) || other.is(Blocks.WALL_TORCH))
            && torchSupport(q, other).equals(support)) another = true;
      }
      if (!another) m.torchSupports.remove(support.asLong());
    }
    m.torches.remove(p.asLong());
    m.pendingLight = m.relocationFloor;
    m.relocatingTorch = null;
    m.relocationFloor = null;
    m.excavationConflict = null;
    m.replacementPlaced = false;
    m.lightWait = level().getGameTime() + 10;
    changed(s);
    WorldData.get(level()).release(w.getUUID());
    reason(s, "Updating lighting", 0);
    return true;
  }

  private boolean lighting(Station s) {
    MinePlan m = s.mine;
    if (level().getGameTime() < m.lightWait) {
      reason(s, "Updating lighting", 0);
      return true;
    }
    if (m.relocatingTorch != null && m.replacementPlaced) return recoverTorch(s);
    boolean relocating = m.relocatingTorch != null;
    if ((m.pendingLight == null || !relocating && !localLight(m.pendingLight))
        && !m.passage.isEmpty()) {
      var cells = m.lightCells;
      for (int i = 0; i < Math.min(8, cells.size()); i++) {
        BlockPos p = BlockPos.of(cells.get(Math.floorMod(m.lightCursor++, cells.size())));
        if (!level().hasChunkAt(p)) {
          reason(s, "Area unloaded", 0);
          return true;
        }
        if (w.distanceToSqr(Vec3.atCenterOf(p)) <= 64
            && localLight(p)
            && MiningRules.supported(level(), p)
            && level().getBlockState(p).getCollisionShape(level(), p).isEmpty()
            && level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p)
                < Balance.MINE_LIGHT.get()) {
          m.pendingLight = p;
          break;
        }
      }
    }
    BlockPos floor = m.pendingLight;
    if (floor == null) return false;
    // Another miner can service a distant light. Do not abandon the active face for it.
    if (!relocating && !localLight(floor)) return false;
    if (!level().hasChunkAt(floor)) {
      reason(s, "Area unloaded", 0);
      return true;
    }
    if (!MiningRules.supported(level(), floor)
        || !level().getBlockState(floor).getCollisionShape(level(), floor).isEmpty()) {
      m.pendingLight = null;
      changed(s);
      return false;
    }
    if (!relocating
        && level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, floor)
            >= Balance.MINE_LIGHT.get()) {
      m.pendingLight = null;
      changed(s);
      return false;
    }
    if (carried(Items.TORCH) == 0) {
      service(s, "Needs torches", 64);
      return true;
    }
    if (floor.getY() < m.entrance.getY() - 1 && goUnderground(m.chamber())) return true;
    if (routeToWork(floor)) return true;
    List<MinePlan.Task> options = new ArrayList<>();
    // Side walls first, above foot height, so a light does not occupy the next front face.
    for (int y : new int[] {1, 0})
      for (int along : new int[] {0, -1, 1}) {
        BlockPos pos = floor.relative(m.forward, along).above(y);
        if (!level().hasChunkAt(pos)
            || !level().getBlockState(pos).isAir()
            || pos.equals(m.excavationConflict)) continue;
        for (Direction d :
            new Direction[] {
              m.forward.getClockWise(),
              m.forward.getCounterClockWise(),
              m.forward,
              m.forward.getOpposite()
            }) {
          BlockState torch =
              Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, d);
          BlockPos support = pos.relative(d.getOpposite());
          if (level().hasChunkAt(support)
              && torch.canSurvive(level(), pos)
              && !m.passage.contains(support.asLong())
              && !support.equals(target)
              && !support.equals(m.excavationConflict)
              && !m.plannedExcavation(support))
            options.add(new MinePlan.Task(pos, torch, "Placing torch"));
        }
        if (y == 0
            && !pos.below().equals(m.excavationConflict)
            && !m.plannedExcavation(pos.below())
            && Blocks.TORCH.defaultBlockState().canSurvive(level(), pos))
          options.add(new MinePlan.Task(pos, Blocks.TORCH.defaultBlockState(), "Placing torch"));
      }
    for (var task : options) {
      if (MiningRules.hazard(w, s, task) != null) continue;
      if (!w.inReach(task.pos())) {
        if (w.canReach(task.pos())) {
          w.travelTo(task.pos(), "Travelling to light", "Placing torch");
          return true;
        }
        continue;
      }
      if (!WorldData.get(level()).reserve(List.of(task.pos()), w.getUUID())) continue;
      if (!w.authorized
          || w.job() != 2
          || m.paused
          || revision != m.revision
          || carried(Items.TORCH) == 0) break;
      if (MiningRules.place(w, task.pos(), task.place())) {
        for (int i = 0; i < w.cargo.getSlots(); i++)
          if (w.cargo.getStackInSlot(i).is(Items.TORCH)) {
            w.cargo.extractItem(i, 1, false);
            break;
          }
        m.torches.add(task.pos().asLong());
        BlockPos support =
            task.place().is(Blocks.WALL_TORCH)
                ? task.pos().relative(task.place().getValue(WallTorchBlock.FACING).getOpposite())
                : task.pos().below();
        m.torchSupports.add(support.asLong());
        m.lightWait = level().getGameTime() + 10;
        if (relocating) m.replacementPlaced = true;
        changed(s);
        reason(s, relocating ? "Relocating torch" : "Placing torch", 0);
        WorldData.get(level()).release(w.getUUID());
        return true;
      }
    }
    if (w.pathDeferred) {
      reason(s, "Planning lighting access", 0);
      return true;
    }
    service(s, "Lighting blocked", 16);
    return true;
  }

  private void drop(ItemStack stack, BlockPos p) {
    ItemEntity e = new ItemEntity(level(), p.getX() + .5, p.getY() + .5, p.getZ() + .5, stack);
    e.getPersistentData().putUUID("AutomatonMine", access.id);
    level().addFreshEntity(e);
  }

  private boolean passageClaim() {
    if (access.traveller != null
        && !access.traveller.equals(w.getUUID())
        && access.travelUntil > level().getGameTime()) {
      w.state("Waiting for passage");
      return false;
    }
    access.traveller = w.getUUID();
    access.travelUntil = level().getGameTime() + 40;
    return true;
  }

  private boolean goUnderground(BlockPos destination) {
    if (access == null || access.entrance == null) return false;
    if (climb != 0 || exiting) {
      w.state("Using ladder");
      return true;
    }
    if (w.getY() <= destination.getY() + .4) {
      BlockPos exit = access.ladder(access.shaftDepth).relative(access.forward);
      if (w.onClimbable()
          && Math.abs(w.getY() - exit.getY()) < .2
          && level()
              .noCollision(
                  w, w.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(exit)))) {
        ladderExit = exit;
        exiting = true;
        w.setNoGravity(true);
        w.getNavigation().stop();
        return true;
      }
      return false;
    }
    BlockPos top = access.entrance;
    // Elevation is not proof of being in the shaft: hillside workers may be below
    // the entrance while still many blocks away. Walk to the mouth before claiming it.
    if (!atShaft()
        && (w.getY() < top.getY() - .2
            || w.position()
                    .subtract(Vec3.atBottomCenterOf(top.relative(access.forward)))
                    .horizontalDistanceSqr()
                > .16)) {
      stopLadderHold();
      walkTo(top.relative(access.forward));
      if (!w.getNavigation().isDone()) w.state("Approaching mine ladder");
      return true;
    }
    if (!openTrapdoor()) {
      w.state("Mine access obstructed");
      return true;
    }
    int available = 0;
    for (int d = access.firstLadder(); d <= access.shaftDepth; d++) {
      if (!level().hasChunkAt(access.ladder(d))
          || !level().getBlockState(access.ladder(d)).equals(access.ladderState())) break;
      available = d;
    }
    if (available == 0) {
      w.state("Installing ladder");
      return true;
    }
    if (!passageClaim()) return true;
    climbY = Math.max(destination.getY(), top.getY() - available);
    climb = -1;
    holdingLadder = false;
    w.setDeltaMovement(Vec3.ZERO);
    w.getNavigation().stop();
    w.state("Climbing down");
    return true;
  }

  public boolean returnToSurface() {
    refreshAccess();
    if (access == null || access.entrance == null) return false;
    if (climb < 0) {
      climb = 0;
      exiting = false;
      w.setDeltaMovement(Vec3.ZERO);
    }
    if (climb > 0 || exiting) return true;
    if (w.getY() >= access.entrance.getY() - .2) {
      stopLadderHold();
      return false;
    }
    // A surface return on a slope must use ordinary walking, never remote climbing.
    if (!atShaft() && w.getY() > access.chamber().getY() + 3) {
      stopLadderHold();
      return false;
    }
    // An unfinished foundation is only a one-block step, not an installed mine shaft.
    if (access.stage == MinePlan.Stage.ARCH) return false;
    if (routeBack()) return true;
    int depth =
        Math.max(
            1, Math.min(access.shaftDepth, (int) Math.ceil(access.entrance.getY() - w.getY())));
    for (int d = access.firstLadder(); d <= depth; d++) {
      if (!level().hasChunkAt(access.ladder(d))) {
        w.state("Area unloaded");
        return true;
      }
      if (!level().getBlockState(access.ladder(d)).equals(access.ladderState())
          && !(d == 1 && openTopGap())) {
        w.state("Access blocked: ladder segment " + d);
        return true;
      }
    }
    BlockPos foot = access.ladder(depth);
    if (w.position().subtract(Vec3.atBottomCenterOf(foot)).horizontalDistanceSqr() > 1.05
        || Math.abs(w.getY() - foot.getY()) > .7) {
      stopLadderHold();
      walkTo(foot);
      return true;
    }
    if (!passageClaim()) return true;
    climb = 1;
    holdingLadder = false;
    climbY = access.entrance.getY();
    w.setDeltaMovement(Vec3.ZERO);
    w.getNavigation().stop();
    return true;
  }

  private MinePlan.Tunnel currentTunnel() {
    if (access == null
        || access.entrance == null
        || Math.abs(w.getY() - access.chamber().getY()) > .7) return null;
    return tunnelAt(w.position());
  }

  private MinePlan.Tunnel tunnelAt(Vec3 feet) {
    for (MinePlan.Tunnel t : access.tunnels) {
      BlockPos junction = t.at(access, t.lane * 3 * t.lateral, 3);
      Direction facing = access.facing(t.route), right = facing.getClockWise();
      double dx = feet.x - junction.getX() - .5, dz = feet.z - junction.getZ() - .5;
      double along = dx * facing.getStepX() + dz * facing.getStepZ();
      if (Math.abs(dx * right.getStepX() + dz * right.getStepZ()) < .8 && along > 1 && along <= 32)
        return t;
    }
    return null;
  }

  private boolean localLight(BlockPos floor) {
    if (w.distanceToSqr(Vec3.atCenterOf(floor)) > 100) return false;
    MinePlan.Tunnel current = currentTunnel();
    if (current == null) return true;
    // A light through the wall may be close in space but require a long crosscut detour.
    // Service it when walking its own corridor, without interrupting this branch's work.
    Direction right = access.facing(current.route).getClockWise();
    BlockPos junction = current.at(access, current.lane * 3 * current.lateral, 3);
    return (floor.getX() - junction.getX()) * right.getStepX()
            + (floor.getZ() - junction.getZ()) * right.getStepZ()
        == 0;
  }

  private boolean routeBack() {
    if (w.getY() < access.chamber().getY() - .7 && access.quarrySide != 0) {
      int depth =
          Math.max(
              0,
              Math.min(access.quarryLayer, (int) Math.ceil(access.chamber().getY() - w.getY()))
                  - 8);
      walkTo(access.step(depth));
      w.state("Returning up quarry");
      return true;
    }
    MinePlan.Tunnel branch = currentTunnel();
    if (branch == null) branch = access.tunnel(w.getUUID());
    if (branch != null && Math.abs(w.getY() - access.chamber().getY()) < .7) {
      BlockPos junction = branch.at(access, branch.lane * 3 * branch.lateral, 3);
      Direction facing = access.facing(branch.route);
      double along =
          (w.getX() - junction.getX() - .5) * facing.getStepX()
              + (w.getZ() - junction.getZ() - .5) * facing.getStepZ();
      if (along > 1) {
        walkTo(junction);
        w.state("Returning through tunnel");
        return true;
      }
    }
    return false;
  }

  private boolean openTrapdoor() {
    BlockPos p = access.hatch();
    if (!level().hasChunkAt(p)) return false;
    BlockState state = level().getBlockState(p);
    if (state.isAir() || state.equals(access.ladderState())) return true;
    if (!(state.getBlock() instanceof TrapDoorBlock)) return false;
    {
      if (!w.inReach(p)
          || WorldData.get(level()).protectedAt(p)
          || WorldData.get(level()).buildings.values().stream()
              .anyMatch(b -> b.approved && b.contains(level(), p))) return false;
      if (MiningRules.breakPermission(w, p, state) < 0) return false;
      if (!level().setBlock(p, Blocks.AIR.defaultBlockState(), 3)) return false;
      // Recover the old hatch without turning a courier's empty pickup trip into an unload trip.
      Block.popResource(level(), p, new ItemStack(state.getBlock().asItem()));
      level().levelEvent(2001, p, Block.getId(state));
    }
    return true;
  }

  private void walkTo(BlockPos feet) {
    if (!level().hasChunkAt(feet)) {
      w.state("Area unloaded");
      return;
    }
    Vec3 center = Vec3.atBottomCenterOf(feet);
    if (w.getNavigation().isDone()
        && w.position().distanceToSqr(center) < 2.25
        && level().noCollision(w, w.getDimensions(Pose.STANDING).makeBoundingBox(center))) {
      w.getMoveControl().setWantedPosition(center.x, center.y, center.z, 1);
      return;
    }
    if (level().getGameTime() < nextPath && !w.getNavigation().isDone()) return;
    nextPath = level().getGameTime() + 20;
    if (!WorldEvents.pathBudget()) return;
    Path path = w.getNavigation().createPath(feet, 0);
    if (path != null
        && (path.canReach()
            || path.getEndNode() != null
                && path.getNodeCount() > 1
                && Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceToSqr(center) + 4
                    < w.position().distanceToSqr(center))) w.getNavigation().moveTo(path, 1);
    else w.state("Waiting for access path");
  }

  private boolean atShaft() {
    return access != null
        && access.entrance != null
        && w.position().subtract(Vec3.atBottomCenterOf(access.entrance)).horizontalDistanceSqr()
            < 2.25;
  }

  private void refreshAccess() {
    if (access == null || !(w.level() instanceof ServerLevel l)) return;
    MinePlan shared = WorldData.get(l).mines.get(access.id);
    if (shared != null) access = shared;
  }

  private boolean openTopGap() {
    // The old minehead omitted precisely this rung. A worker's upper body is
    // still on the rung below while stepping out to fetch its replacement.
    return access.stage.ordinal() > MinePlan.Stage.SHAFT.ordinal()
        && level().hasChunkAt(access.ladder(1))
        && level().getBlockState(access.ladder(1)).isAir()
        && level().getBlockState(access.ladder(2)).equals(access.ladderState());
  }

  private void stopLadderHold() {
    if (holdingLadder) {
      holdingLadder = false;
      w.setDeltaMovement(Vec3.ZERO);
      if (access != null && w.getUUID().equals(access.traveller)) access.traveller = null;
    }
    if (climb == 0 && !exiting) w.setNoGravity(false);
  }

  /** Vanilla wall-contact ladder acceleration otherwise overrides the requested stop height. */
  public boolean travelControlled() {
    if (w.level().isClientSide || access == null || climb == 0 && !exiting && !holdingLadder)
      return false;
    w.move(MoverType.SELF, w.getDeltaMovement());
    w.setDeltaMovement(Vec3.ZERO);
    w.fallDistance = 0;
    return true;
  }

  private boolean routeToWork(BlockPos target) {
    if (access == null || access.entrance == null || w.getY() >= access.entrance.getY() - .2)
      return false;
    if (access.quarrySide != 0
        && w.getY() < access.chamber().getY() - 4
        && target.getY() > w.getY() + 4) {
      int depth = Math.max(0, (int) Math.ceil(access.chamber().getY() - w.getY()) - 8);
      walkTo(access.step(depth));
      w.state("Ascending quarry");
      return true;
    }
    if (access.quarrySide != 0
        && target.getY() < access.chamber().getY() - 4
        && w.getY() > target.getY() + 4) {
      int depth = Math.max(0, (int) Math.floor(access.chamber().getY() - w.getY()));
      int available = access.quarryCell >= 4 ? access.quarryLayer : access.quarryLayer - 1;
      walkTo(access.step(Math.max(0, Math.min(available, depth + 8))));
      w.state("Descending quarry");
      return true;
    }
    MinePlan.Tunnel branch = tunnelAt(Vec3.atBottomCenterOf(target));
    if (branch == null) branch = access.tunnel(w.getUUID());
    MinePlan.Tunnel current = currentTunnel();
    if (branch != null && current != null && branch != current && routeBack()) return true;
    if (branch != null
        && Math.abs(w.getY() - access.chamber().getY()) < .7
        && Math.abs(target.getY() - access.chamber().getY()) <= 1) {
      Direction right = access.facing(branch.route).getClockWise();
      double offset =
          (w.getX() - access.workOrigin().getX() - .5) * right.getStepX()
              + (w.getZ() - access.workOrigin().getZ() - .5) * right.getStepZ();
      int requested =
          (target.getX() - access.workOrigin().getX()) * right.getStepX()
              + (target.getZ() - access.workOrigin().getZ()) * right.getStepZ();
      if (Math.abs(requested - offset) > 1.4) {
        BlockPos junction = branch.at(access, requested, 3);
        // Only route through an already excavated junction, never past the current connector.
        if (level().getBlockState(junction).isAir() && !w.inReach(target)) {
          walkTo(junction);
          w.state("Travelling through tunnel");
          return true;
        }
      }
    }
    return false;
  }

  public void motionTick() {
    refreshAccess();
    if (access == null || access.entrance == null || w.packing || w.isRemoved()) return;
    if (climb == 0 && !exiting) {
      if (holdingLadder) {
        if (!atShaft() || !w.onClimbable()) stopLadderHold();
        else {
          passageClaim();
          w.setDeltaMovement(Vec3.ZERO);
        }
      }
      return;
    }
    if (!atShaft() && !exiting) {
      climb = 0;
      w.setNoGravity(false);
      w.setDeltaMovement(Vec3.ZERO);
      return;
    }
    if (!passageClaim()) {
      w.setDeltaMovement(Vec3.ZERO);
      return;
    }
    w.getNavigation().stop();
    ((WorkerMoveControl) w.getMoveControl()).stop();
    if (exiting) {
      BlockPos exit = ladderExit == null ? access.entrance.relative(access.forward) : ladderExit;
      Vec3 delta = Vec3.atBottomCenterOf(exit).subtract(w.position());
      if (delta.horizontalDistanceSqr() < .04) {
        exiting = false;
        access.traveller = null;
        w.setNoGravity(false);
        w.setDeltaMovement(0, -.08, 0);
        return;
      }
      if (!level()
          .noCollision(
              w, w.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(exit)))) {
        w.setDeltaMovement(Vec3.ZERO);
        w.state("Ladder exit blocked");
        return;
      }
      w.setDeltaMovement(delta.x * .2, Math.max(-.14, Math.min(.14, delta.y)), delta.z * .2);
      return;
    }
    BlockPos here =
        new BlockPos(
            access.entrance.getX(),
            (int) Math.floor(Math.min(w.getY(), access.entrance.getY() - access.firstLadder())),
            access.entrance.getZ());
    if (!level().hasChunkAt(here)
        || !level().getBlockState(here).equals(access.ladderState())
            && !(climb > 0 && here.equals(access.ladder(1)) && openTopGap())) {
      climb = 0;
      w.setNoGravity(false);
      w.state("Access blocked: ladder");
      return;
    }
    if (climb > 0 && w.getY() > access.entrance.getY() - 3 && !openTrapdoor()) {
      w.setNoGravity(true);
      w.setDeltaMovement(Vec3.ZERO);
      w.state("Mine access obstructed");
      return;
    }
    double dy = climbY - w.getY();
    w.setNoGravity(true);
    w.fallDistance = 0;
    if (Math.abs(dy) < .03) {
      boolean up = climb > 0;
      climb = 0;
      w.setDeltaMovement(Vec3.ZERO);
      ladderExit =
          (up
                  ? access.entrance
                  : new BlockPos(
                      access.entrance.getX(), (int) Math.round(climbY), access.entrance.getZ()))
              .relative(access.forward);
      if (level()
          .noCollision(
              w, w.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(ladderExit))))
        exiting = true;
      else {
        holdingLadder = true;
        w.setNoGravity(true);
      }
      return;
    }
    double dx = access.entrance.getX() + .5 + access.forward.getStepX() * .12 - w.getX();
    double dz = access.entrance.getZ() + .5 + access.forward.getStepZ() * .12 - w.getZ();
    w.setDeltaMovement(
        dx * .3, dx * dx + dz * dz < .025 ? Math.max(-.14, Math.min(.14, dy)) : 0, dz * .3);
    w.state(climb > 0 ? "Climbing up" : "Climbing down");
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    if (access != null) n.put("Access", access.save());
    n.putBoolean("Servicing", servicing);
    n.putString("Reason", serviceReason);
    return n;
  }

  public void load(CompoundTag n) {
    if (n.contains("Access")) access = MinePlan.load(n.getCompound("Access"));
    servicing = n.getBoolean("Servicing");
    serviceReason = n.getString("Reason");
    climb = 0;
    exiting = false;
    w.setNoGravity(false);
  }
}
