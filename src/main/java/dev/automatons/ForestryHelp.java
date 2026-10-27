package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Pickaxe work requested by forestry, separate from courier item deliveries and mine plans. */
public final class ForestryHelp {
  public static final class Request {
    public BlockPos pos, root;
    public UUID requester, site, miner;
    public CompoundTag original;
    public long expires, lease, retry;
    public String status = "Waiting for Minington";

    CompoundTag save() {
      CompoundTag n = new CompoundTag();
      n.putLong("Pos", pos.asLong());
      if (root != null) n.putLong("Root", root.asLong());
      n.putUUID("Requester", requester);
      n.putUUID("Site", site);
      n.put("Original", original);
      n.putLong("Expires", expires);
      return n;
    }

    static Request load(CompoundTag n) {
      Request r = new Request();
      r.pos = BlockPos.of(n.getLong("Pos"));
      r.root = n.contains("Root") ? BlockPos.of(n.getLong("Root")) : null;
      r.requester = n.getUUID("Requester");
      r.site = n.getUUID("Site");
      r.original = n.getCompound("Original");
      r.expires = n.getLong("Expires");
      return r;
    }
  }

  public static boolean needsPickaxe(BlockState state) {
    return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
  }

  public static void request(Lumberton lumberton, BlockPos pos, BlockPos root) {
    if (lumberton.homeId == null || lumberton.owner == null || lumberton.station() == null) return;
    ServerLevel l = (ServerLevel) lumberton.level();
    if (!needsPickaxe(l.getBlockState(pos))
        || !TreeFinder.canPrepareFallBlock(l, pos, lumberton.owner)) return;
    LogisticsNetwork network = LogisticsNetwork.get(lumberton);
    maintain(network, l);
    Request r = network.obstacles.get(pos.asLong());
    if (r == null) {
      if (network.obstacles.size() >= 256) return;
      r = new Request();
      r.pos = pos.immutable();
      r.root = root == null ? null : root.immutable();
      r.requester = lumberton.getUUID();
      r.site = lumberton.station().identity;
      r.original = NbtUtils.writeBlockState(l.getBlockState(pos));
      network.obstacles.put(pos.asLong(), r);
    }
    r.expires = l.getGameTime() + 6000;
    WorldData.get(l).setDirty();
  }

  private static boolean member(LogisticsNetwork n, Lumberton w) {
    return Objects.equals(n.home, w.homeId)
        && Objects.equals(n.owner, w.owner)
        && (w.homeDimension.isEmpty()
            || w.homeDimension.equals(w.level().dimension().location().toString()));
  }

  public static void maintain(LogisticsNetwork n, ServerLevel l) {
    boolean removed =
        n.obstacles
            .values()
            .removeIf(
                r -> {
                  if (l.getGameTime() > r.expires) return true;
                  if (l.hasChunkAt(r.pos)
                      && !r.original.equals(NbtUtils.writeBlockState(l.getBlockState(r.pos))))
                    return true;
                  if (r.root != null
                      && l.hasChunkAt(r.root)
                      && !l.getBlockState(r.root).is(BlockTags.LOGS)) return true;
                  if (l.getEntity(r.requester) instanceof Lumberton w) {
                    if (!member(n, w)
                        || w.job() != WorkerJob.LUMBERTON
                        || w.station() == null
                        || !w.station().identity.equals(r.site)) return true;
                  } else {
                    CompoundTag record = Ledger.get(l.getServer()).workers.get(r.requester);
                    if (record == null) return true;
                  }
                  if (r.miner != null
                      && (l.getGameTime() > r.lease
                          || !(l.getEntity(r.miner) instanceof Lumberton w)
                          || !eligible(n, w))) {
                    r.miner = null;
                    r.status = "Waiting for Minington";
                  }
                  return false;
                });
    if (removed) WorldData.get(l).setDirty();
  }

  private static boolean eligible(LogisticsNetwork n, Lumberton w) {
    WorldData.Home home = WorldData.get((ServerLevel) w.level()).homes.get(w.homeId);
    return member(n, w)
        && w.isAlive()
        && w.authorized
        && w.job() == WorkerJob.MININGTON
        && w.station() != null
        && w.station().purpose == WorksitePurpose.MINE
        && !w.station().recalled
        && home != null
        && home.exists()
        && !home.recalled();
  }

  private static int tool(Lumberton w, BlockState state) {
    int best = -1;
    float speed = -1;
    for (int i = 0; i < 3; i++) {
      ItemStack t = w.tools.getStackInSlot(i);
      if (t.canPerformAction(net.minecraftforge.common.ToolActions.PICKAXE_DIG)
          && MiningRules.capable(t, state)
          && t.getDestroySpeed(state) > speed) {
        best = i;
        speed = t.getDestroySpeed(state);
      }
    }
    return best;
  }

  private final Lumberton worker;
  private Request active;
  private int progress;
  private long started, blocked = -1, nextScan;

  public ForestryHelp(Lumberton worker) {
    this.worker = worker;
  }

  public void release() {
    if (active != null) {
      if (worker.getUUID().equals(active.miner)) active.miner = null;
      worker.clearChop();
    }
    active = null;
    progress = 0;
    blocked = -1;
  }

  private void defer(ServerLevel l, String status) {
    Request r = active;
    release();
    r.status = status;
    r.retry = l.getGameTime() + 400;
    worker.clearChop();
    worker.getNavigation().stop();
  }

  /** Called before normal mine work; no MinePlan cursor, tunnel or staged drops are advanced. */
  public boolean tick() {
    ServerLevel l = (ServerLevel) worker.level();
    LogisticsNetwork n = LogisticsNetwork.get(worker);
    if (!eligible(n, worker)) {
      release();
      return false;
    }
    maintain(n, l);
    if (active != null
        && (n.obstacles.get(active.pos.asLong()) != active
            || !worker.getUUID().equals(active.miner))) {
      release();
      worker.clearChop();
      worker.getNavigation().stop();
    }
    if (active == null) {
      if (l.getGameTime() < nextScan) return false;
      nextScan = l.getGameTime() + 20;
      active =
          n.obstacles.values().stream()
              .filter(r -> r.miner == null && r.retry <= l.getGameTime() && l.hasChunkAt(r.pos))
              .filter(
                  r ->
                      l.getEntity(r.requester) instanceof Lumberton w
                          && w.authorized
                          && member(n, w))
              .filter(r -> tool(worker, l.getBlockState(r.pos)) >= 0)
              .min(Comparator.comparingDouble(r -> worker.distanceToSqr(Vec3.atCenterOf(r.pos))))
              .orElse(null);
      if (active == null) return false;
      active.miner = worker.getUUID();
      started = l.getGameTime();
      progress = 0;
      worker.mining.releaseWork();
      worker.getNavigation().stop();
    }
    Request r = active;
    r.lease = l.getGameTime() + 100;
    if (!(l.getEntity(r.requester) instanceof Lumberton requester) || !requester.authorized) {
      defer(l, "Waiting for Lumberton");
      return false;
    }
    r.status = "Minington clearing obstacle";
    if (l.getGameTime() - started > 2400) {
      defer(l, "Waiting for reachable Minington");
      return false;
    }
    if (!l.hasChunkAt(r.pos)) {
      defer(l, "Obstacle area unloaded");
      return false;
    }
    BlockState state = l.getBlockState(r.pos);
    int slot = tool(worker, state);
    if (slot < 0) {
      defer(l, "Needs Minington with capable pickaxe");
      return false;
    }
    worker.activateTool(slot);
    if (worker.mining.returnToSurface()) return true;
    if (!TreeFinder.canPrepareFallBlock(l, r.pos, worker.owner)
        || MiningRules.fluidAhead(l, r.pos)
        || MiningRules.fallingNow(l, r.pos)) {
      defer(l, "Obstacle protected or unsafe");
      return false;
    }
    if (MiningRules.fallingRisk(l, r.pos)
        && !MiningRules.safeFallingStance(worker, r.pos, worker.position())) {
      worker.mining.standBack(worker.station(), r.pos);
      return true;
    }
    if (!worker.travelTo(r.pos, "Helping Lumberton", "Clearing Lumberton obstacle")) {
      if (blocked < 0) blocked = l.getGameTime();
      if (l.getGameTime() - blocked > 400) {
        defer(l, "Waiting for reachable Minington");
        return false;
      }
      return true;
    }
    if (!l.getEntitiesOfClass(
            LivingEntity.class,
            new AABB(r.pos).expandTowards(0, 1, 0),
            e ->
                !(r.root == null
                    && e.getUUID().equals(r.requester)
                    && !e.getBoundingBox().intersects(new AABB(r.pos))))
        .isEmpty()) {
      defer(l, "Waiting for clear obstacle access");
      return false;
    }
    blocked = -1;
    int duration = MiningRules.duration(worker.activeTool(), state, l, r.pos, worker.station());
    progress++; // Minington brain runs every tick, matching ordinary mining speed.
    worker.workVisual(r.pos, progress, duration);
    if (progress == 1 || progress % 20 == 0) {
      worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
      var sound = state.getSoundType(l, r.pos, worker);
      l.playSound(
          null,
          r.pos,
          sound.getHitSound(),
          net.minecraft.sounds.SoundSource.NEUTRAL,
          .45f,
          sound.getPitch());
    }
    if (progress < duration) return true;
    var drops = Block.getDrops(state, l, r.pos, null, worker, worker.activeTool());
    if (!Inventories.fits(worker.cargo, drops)) {
      defer(l, "Minington needs cargo space");
      return false;
    }
    int xp = MiningRules.breakPermission(worker, r.pos, state);
    if (xp < 0) {
      defer(l, "Obstacle breaking denied");
      return false;
    }
    if (!r.original.equals(NbtUtils.writeBlockState(l.getBlockState(r.pos)))) return true;
    if (l.destroyBlock(r.pos, false, worker)) {
      for (ItemStack drop : drops) Inventories.insert(worker.cargo, drop, false);
      if (xp > 0) net.minecraft.world.entity.ExperienceOrb.award(l, Vec3.atCenterOf(r.pos), xp);
      worker.wear(1);
      n.obstacles.remove(r.pos.asLong());
      WorldData.get(l).setDirty();
      WorldEvents.changedNear(l, r.pos);
      release();
      nextScan = 0;
      worker.clearChop();
    }
    return true;
  }
}
