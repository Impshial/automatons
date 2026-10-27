package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** A real inventory-funded jump pillar. Its journal is also the recovery work queue. */
public final class ForestryAccess {
  private final Lumberton worker;
  private BlockPos base, target;
  private UUID constructionId;
  private final LinkedHashMap<BlockPos, BlockState> placed = new LinkedHashMap<>();
  private boolean cleaning;
  private int stuck;

  public ForestryAccess(Lumberton worker) {
    this.worker = worker;
  }

  private ServerLevel level() {
    return (ServerLevel) worker.level();
  }

  public boolean cleaning() {
    return cleaning && !placed.isEmpty();
  }

  public boolean active() {
    return !placed.isEmpty();
  }

  public void finish() {
    cleaning = true;
    target = null;
  }

  public static boolean buildingBlock(ItemStack stack) {
    return stack.is(Items.DIRT) || stack.is(net.minecraft.tags.ItemTags.PLANKS);
  }

  private int material() {
    for (int i = 0; i < worker.cargo.getSlots(); i++)
      if (buildingBlock(worker.cargo.getStackInSlot(i))) return i;
    return -1;
  }

  private boolean safe(BlockPos p) {
    Station s = worker.station();
    WorldData data = WorldData.get(level());
    BuildingProject construction =
        worker.job() == WorkerJob.BUILDINGTON ? worker.building.active() : null;
    if (construction == null && constructionId != null)
      construction =
          data.buildings.values().stream()
              .filter(pj -> pj.id.equals(constructionId))
              .findFirst()
              .orElse(null);
    boolean buildArea =
        construction != null
            && construction.clearingArea(level()).inflate(2, 0, 2).contains(Vec3.atCenterOf(p));
    boolean structure = data.buildingProtected(level(), p);
    if (buildArea) {
      BuildingProject clearingProject = construction;
      structure =
          data.buildings.values().stream()
              .anyMatch(
                  project ->
                      project != clearingProject && project.contains(level(), p)
                          || project == clearingProject
                              && !placed.containsKey(p)
                              && clearingProject.preserveDuringClearing(level(), p));
    }
    return (buildArea || s != null && TreeFinder.within(s, p) || placed.containsKey(p))
        && level().hasChunkAt(p)
        && !level().isOutsideBuildHeight(p)
        && level().getFluidState(p).isEmpty()
        && !data.protectedAt(p)
        && !data.mineProtected(p, null)
        && !structure
        && !data.farmProtected(p)
        && !SiteLayout.protectedBed(level(), p)
        && (buildArea
            ? MiningRules.breakPermission(worker, p, level().getBlockState(p)) >= 0
            : Permissions.allowed(level(), p, worker.owner, Permissions.Action.CUT));
  }

  /** Planning is read-only; no blocks are placed until the claimed job approaches its target. */
  public boolean plan(BlockPos log) {
    if (target != null && target.equals(log)) return true;
    if (active() || material() < 0 || log.getY() < worker.getY() + 2) return false;
    List<BlockPos> choices = new ArrayList<>();
    for (Direction d : Direction.Plane.HORIZONTAL) {
      BlockPos column = log.relative(d);
      for (int down = 2;
          down <= (worker.job() == WorkerJob.BUILDINGTON ? 18 : Balance.ROOT_HEIGHT.get() + 2);
          down++) {
        BlockPos feet = column.below(down);
        if (!safe(feet)) break;
        if (!MiningRules.supported(level(), feet)) continue;
        boolean clear = true;
        for (int y = feet.getY();
            y <= log.getY() + (worker.job() == WorkerJob.BUILDINGTON ? -1 : 1);
            y++) {
          BlockPos p = new BlockPos(feet.getX(), y, feet.getZ());
          if (!safe(p)
              || !level().getBlockState(p).isAir()
                  && !(worker.job() == WorkerJob.BUILDINGTON
                      && Construction.naturalObstacle(level().getBlockState(p)))) {
            clear = false;
            break;
          }
        }
        int blocks = 0;
        for (int i = 0; i < worker.cargo.getSlots(); i++)
          if (buildingBlock(worker.cargo.getStackInSlot(i)))
            blocks += worker.cargo.getStackInSlot(i).getCount();
        if (clear
            && level().getBlockState(feet).isAir()
            && level().getBlockState(feet.above()).isAir()
            && blocks >= log.getY() - feet.getY() - 1) choices.add(feet);
        break;
      }
    }
    choices.sort(Comparator.comparingDouble(p -> worker.distanceToSqr(Vec3.atBottomCenterOf(p))));
    for (BlockPos feet : choices) {
      if (worker.canReach(feet)) {
        var project = worker.job() == WorkerJob.BUILDINGTON ? worker.building.active() : null;
        constructionId = project == null ? null : project.id;
        base = feet;
        target = log.immutable();
        cleaning = false;
        stuck = 0;
        return true;
      }
      if (worker.pathDeferred) return false;
    }
    return false;
  }

  public boolean approach(BlockPos log) {
    if (worker.inReach(log) && worker.onGround()) return false;
    if (!plan(log)) return false;
    if (++stuck > 400) {
      finish();
      return true;
    }
    BlockPos next = base.above(placed.size());
    Vec3 feet = Vec3.atBottomCenterOf(next);
    double horizontal = worker.position().subtract(feet).horizontalDistanceSqr();
    if (horizontal > .025 || worker.getY() < next.getY() - .1) {
      if (horizontal < 2 && Math.abs(worker.getY() - next.getY()) < .3) {
        worker.getNavigation().stop();
        worker.getMoveControl().setWantedPosition(feet.x, feet.y, feet.z, 1);
      } else if (worker.job() == WorkerJob.BUILDINGTON)
        WorkerMovement.walkGround(worker, next, "Approaching construction climbing position");
      else
        worker.travelTo(
            next,
            "Approaching climbing position",
            worker.job() == WorkerJob.BUILDINGTON
                ? "Climbing to clear construction"
                : "Climbing for wood");
      return true;
    }
    worker.getNavigation().stop();
    worker.state("Building temporary climbing blocks");
    if (worker.job() == WorkerJob.BUILDINGTON && worker.building.active() != null) {
      for (int up = 1; up <= 3; up++) {
        BlockPos obstruction = next.above(up);
        var state = level().getBlockState(obstruction);
        if (safe(obstruction)
            && Construction.naturalObstacle(state)
            && worker.inReach(obstruction)) {
          worker.building.active().clearObstacle(worker, obstruction, state);
          return true;
        }
      }
    }
    int slot = material();
    if (slot < 0 || !safe(next) || !level().getBlockState(next).isAir()) {
      finish();
      return true;
    }
    if (worker.onGround()) worker.getJumpControl().jump();
    if (worker.getY() >= next.getY() + 1.01) {
      BlockState state =
          ((BlockItem) worker.cargo.getStackInSlot(slot).getItem()).getBlock().defaultBlockState();
      if (MiningRules.place(worker, next, state)) {
        worker.cargo.extractItem(slot, 1, false);
        placed.put(next, state);
        stuck = 0;
      }
    }
    return true;
  }

  public boolean cleanup() {
    if (placed.isEmpty()) {
      base = target = null;
      constructionId = null;
      cleaning = false;
      return false;
    }
    BlockPos p = new ArrayList<>(placed.keySet()).get(placed.size() - 1);
    if (!level().hasChunkAt(p)) {
      worker.state("Climbing blocks unloaded");
      return true;
    }
    BlockState state = placed.get(p);
    if (!level().getBlockState(p).equals(state)) {
      placed.remove(p);
      return true;
    }
    if (!safe(p)) {
      worker.state("Climbing block recovery protected");
      return true;
    }
    if (ForestryHelp.needsPickaxe(state)) {
      ForestryHelp.request(worker, p, null);
      worker.state("Waiting for Minington to recover stone support");
      return true;
    }
    // Descend one block at a time under gravity; never remove a support under another entity.
    if (!level()
        .getEntitiesOfClass(
            net.minecraft.world.entity.LivingEntity.class,
            new AABB(p).expandTowards(0, 2, 0),
            e -> e != worker)
        .isEmpty()) {
      worker.state("Waiting to recover climbing blocks");
      return true;
    }
    if (!worker.inReach(p)) {
      worker.travelTo(p, "Returning for climbing blocks", "Recovering climbing blocks");
      return true;
    }
    worker.getNavigation().stop();
    if (!worker.onGround()) return true;
    if (MiningRules.breakPermission(worker, p, state) < 0) {
      worker.state("Climbing block recovery protected");
      return true;
    }
    var drops = Block.getDrops(state, level(), p, null, worker, worker.activeTool());
    if (level().setBlock(p, Blocks.AIR.defaultBlockState(), 3)) {
      for (ItemStack drop : drops) {
        ItemStack rest = Inventories.insert(worker.cargo, drop, false);
        if (!rest.isEmpty()) Block.popResource(level(), p, rest);
      }
      level().levelEvent(2001, p, Block.getId(state));
      placed.remove(p);
      worker.state("Recovering climbing blocks");
    }
    return true;
  }

  public CompoundTag save() {
    CompoundTag n = new CompoundTag();
    if (constructionId != null) n.putUUID("Construction", constructionId);
    if (base != null) n.putLong("Base", base.asLong());
    if (target != null) n.putLong("Target", target.asLong());
    n.putBoolean("Cleaning", cleaning);
    ListTag blocks = new ListTag();
    placed.forEach(
        (p, s) -> {
          CompoundTag b = new CompoundTag();
          b.putLong("Pos", p.asLong());
          b.put("State", NbtUtils.writeBlockState(s));
          blocks.add(b);
        });
    n.put("Blocks", blocks);
    return n;
  }

  public void load(CompoundTag n) {
    constructionId = n.hasUUID("Construction") ? n.getUUID("Construction") : null;
    base = n.contains("Base") ? BlockPos.of(n.getLong("Base")) : null;
    target = n.contains("Target") ? BlockPos.of(n.getLong("Target")) : null;
    cleaning = n.getBoolean("Cleaning");
    placed.clear();
    for (Tag tag : n.getList("Blocks", Tag.TAG_COMPOUND)) {
      CompoundTag b = (CompoundTag) tag;
      placed.put(
          BlockPos.of(b.getLong("Pos")),
          NbtUtils.readBlockState(
              worker.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
              b.getCompound("State")));
    }
    if (target == null) cleaning = true;
  }
}
