package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.level.BlockEvent;

/** Shared preconditions for every real mining commit; planning never awards loot. */
public final class MiningRules {
  public static final TagKey<Block> TERRAIN =
      TagKey.create(Registries.BLOCK, Automatons.id("excavation_terrain"));

  public static boolean capable(ItemStack tool, BlockState state) {
    return Inventories.tool(2, tool)
        && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state));
  }

  public static int select(Lumberton w, BlockState state) {
    int best = -1;
    float speed = -1;
    for (int i = 0; i < 3; i++) {
      ItemStack tool = w.tools.getStackInSlot(i);
      if (capable(tool, state) && tool.getDestroySpeed(state) > speed) {
        best = i;
        speed = tool.getDestroySpeed(state);
      }
    }
    if (best >= 0) w.activateTool(best);
    return best;
  }

  public static int duration(
      ItemStack tool, BlockState block, ServerLevel l, BlockPos p, Station station) {
    double speed = Math.max(1, tool.getDestroySpeed(block));
    int efficiency =
        Math.max(
            EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, tool),
            station.tier("efficiency"));
    if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
    double hardness = Math.max(0, block.getDestroySpeed(l, p));
    return Math.max(1, (int) Math.ceil(hardness * 30 / speed * (1 - .2 * station.tier("speed"))));
  }

  public static boolean supported(ServerLevel l, BlockPos feet) {
    return l.hasChunkAt(feet)
        && l.getFluidState(feet).isEmpty()
        && l.getBlockState(feet.below()).isFaceSturdy(l, feet.below(), Direction.UP);
  }

  public static boolean fallingRisk(ServerLevel l, BlockPos p) {
    return l.getBlockState(p).getBlock() instanceof FallingBlock
        || l.getBlockState(p.above()).getBlock() instanceof FallingBlock;
  }

  public static boolean fluidAhead(ServerLevel l, BlockPos p) {
    if (!l.getFluidState(p).isEmpty()) return true;
    for (Direction direction : Direction.values()) {
      BlockPos next = p.relative(direction);
      if (l.hasChunkAt(next) && !l.getFluidState(next).isEmpty()) return true;
    }
    return false;
  }

  public static boolean fallingNow(ServerLevel l, BlockPos p) {
    return !l.getEntitiesOfClass(
            FallingBlockEntity.class,
            new AABB(p).expandTowards(0, l.getMaxBuildHeight() - p.getY(), 0).inflate(.2, 0, .2))
        .isEmpty();
  }

  public static boolean safeFallingStance(Lumberton w, BlockPos p, Vec3 feet) {
    double dx = feet.x - p.getX() - .5, dz = feet.z - p.getZ() - .5;
    if (dx * dx + dz * dz < 4
        || feet.add(0, w.getEyeHeight(), 0).distanceToSqr(Vec3.atCenterOf(p))
            > Math.pow(Balance.MINING_REACH.get(), 2)) return false;
    var l = (ServerLevel) w.level();
    BlockPos footing = BlockPos.containing(feet);
    if (!supported(l, footing)
        || !l.noCollision(w, w.getDimensions(Pose.STANDING).makeBoundingBox(feet))) return false;
    var hit =
        l.clip(
            new net.minecraft.world.level.ClipContext(
                feet.add(0, w.getEyeHeight(), 0),
                Vec3.atCenterOf(p),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                w));
    return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p);
  }

  public static String hazard(Lumberton w, Station s, MinePlan.Task task) {
    ServerLevel l = (ServerLevel) w.level();
    BlockPos p = task.pos();
    if (!l.hasChunkAt(p) || !l.hasChunkAt(p.above()) || !l.hasChunkAt(p.below()))
      return "Area unloaded";
    if (l.isOutsideBuildHeight(p) || !MinePlan.inBounds(s, p)) return "Outside mining range";
    if (WorldData.get(l).protectedAt(p)) return "Protected area";
    if (WorldData.get(l).buildingProtected(l, p)) return "Worksite structure excluded";
    if (WorldData.get(l).farmProtected(p)) return "Registered farmland excluded";
    if (WorldData.get(l).mineProtected(p, s.mine.id)) return "Another mine reserves this block";
    UUID claimant = WorldData.get(l).reservations.get(p.asLong());
    if (claimant != null && !claimant.equals(w.getUUID())) return "Waiting for colleague";
    BlockState state = l.getBlockState(p);
    boolean plugging =
        s.mine.quarry != null
            && task.placement()
            && task.activity().equals("Plugging quarry fluid")
            && QuarryPlan.FILLERS.contains(task.place().getBlock().asItem())
            && (s.mine.quarry.contains(p) || s.mine.quarry.accessCell(s.mine, p));
    if (state.hasBlockEntity()) return "Container or machine excluded";
    if (!plugging && !l.getFluidState(p).isEmpty()) return "Fluid ahead";
    if (!task.placement() && !state.isAir() && !state.canBeReplaced()) {
      boolean ownPlug =
          s.mine.quarry != null
              && s.mine.quarry.plugs.contains(p.asLong())
              && QuarryPlan.FILLERS.contains(state.getBlock().asItem());
      if (!state.is(TERRAIN) && !ownPlug) return "Structure excluded";
      if (state.getDestroySpeed(l, p) < 0) return "Unbreakable terrain";
      boolean extendingShaft =
          (s.mine.strip != null || s.mine.quarry != null)
              && s.mine.stage == MinePlan.Stage.SHAFT
              && task.activity().equals("Excavating shaft")
              && p.getX() == s.mine.entrance.getX()
              && p.getZ() == s.mine.entrance.getZ()
              && p.getY() >= s.mine.entrance.getY() - s.mine.shaftDepth;
      if (s.mine.supports.contains(p.asLong()) && !extendingShaft
          || s.mine.torchSupports.contains(p.asLong())) return "Reserved access support";
    }
    for (Direction d : Direction.values()) {
      BlockPos q = p.relative(d);
      if (!l.hasChunkAt(q)) return "Area unloaded";
      if (!plugging && !l.getFluidState(q).isEmpty()) return "Fluid ahead";
    }
    if (fallingNow(l, p)) return "Waiting for falling material to settle";
    if (!task.placement() && fallingRisk(l, p)) {
      if (!safeFallingStance(w, p, w.position()))
        return "Needs safe distance from falling material";
      AABB column = new AABB(p).expandTowards(0, l.getMaxBuildHeight() - p.getY(), 0);
      if (!l.getEntitiesOfClass(LivingEntity.class, column).isEmpty())
        return "Waiting for colleague below falling material";
    }
    AABB cell = new AABB(p);
    for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, cell.inflate(0, 2, 0))) {
      // Torches have no collision and can be placed beside a worker's feet, as in vanilla.
      if (task.placement() && (task.place().is(Blocks.TORCH) || task.place().is(Blocks.WALL_TORCH)))
        continue;
      boolean cling =
          e == w
              && w.onClimbable()
              && s.mine.stage == MinePlan.Stage.SHAFT
              && task.activity().equals("Excavating shaft")
              && p.getX() == s.mine.entrance.getX()
              && p.getZ() == s.mine.entrance.getZ();
      if (e.getBoundingBox().intersects(cell)
          || !task.placement() && !cling && e.getBoundingBox().move(0, -.1, 0).intersects(cell))
        return "Occupied work position";
    }
    if (task.placement() && !state.isAir() && !state.canBeReplaced()) return "Placement obstructed";
    return null;
  }

  public static FakePlayer actor(Lumberton w) {
    FakePlayer player =
        FakePlayerFactory.get((ServerLevel) w.level(), new GameProfile(w.owner, "[Automatons]"));
    player.moveTo(w.getX(), w.getY(), w.getZ(), w.getYRot(), w.getXRot());
    return player;
  }

  public static int breakPermission(Lumberton w, BlockPos p, BlockState state) {
    if (w.job() == WorkerJob.LUMBERTON && ForestryHelp.needsPickaxe(state)) return -1;
    FakePlayer actor = actor(w);
    ItemStack previous = actor.getMainHandItem();
    actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, w.activeTool().copy());
    try {
      BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(w.level(), p, state, actor);
      if (MinecraftForge.EVENT_BUS.post(event)) return -1;
      return event.getExpToDrop();
    } finally {
      actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previous);
    }
  }

  public static boolean place(Lumberton w, BlockPos p, BlockState placed) {
    ServerLevel l = (ServerLevel) w.level();
    if (!placed.canSurvive(l, p)
        || !l.isUnobstructed(placed, p, net.minecraft.world.phys.shapes.CollisionContext.empty()))
      return false;
    BlockSnapshot snapshot = BlockSnapshot.create(l.dimension(), l, p);
    BlockState previous = l.getBlockState(p);
    // Publish only after the cancellable Forge placement event has inspected the snapshot.
    if (!l.setBlock(p, placed, 2)) return false;
    if (MinecraftForge.EVENT_BUS.post(
        new BlockEvent.EntityPlaceEvent(snapshot, l.getBlockState(p.below()), actor(w)))) {
      snapshot.restore(true, false);
      return false;
    }
    l.blockUpdated(p, placed.getBlock());
    l.sendBlockUpdated(p, previous, placed, 3);
    Construction.placed(w, p, placed);
    return true;
  }
}
