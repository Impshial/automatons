package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;

/** Convert only after vanilla confirms the root was actually removed. */
public final class PlayerFelling {
  private record Pending(
      ServerLevel level,
      ServerPlayer player,
      TreeFinder.Tree tree,
      Map<BlockPos, BlockState> logs,
      ItemStack tool) {}

  private static final Map<String, Pending> pending = new LinkedHashMap<>();

  public static boolean eligible(ItemStack tool) {
    return Inventories.axe(tool)
        && tool.getItem() instanceof TieredItem t
        && t.getTier().getLevel() >= Tiers.STONE.getLevel();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void broken(BlockEvent.BreakEvent event) {
    if (event.isCanceled()
        || !(event.getLevel() instanceof ServerLevel level)
        || !(event.getPlayer() instanceof ServerPlayer player)
        || player instanceof FakePlayer
        || player.isCreative()
        || !eligible(player.getMainHandItem())) return;
    var tree = TreeFinder.inspect(level, event.getPos(), null).tree();
    if (tree == null || !tree.root().equals(event.getPos())) return;
    WorldData data = WorldData.get(level);
    if (tree.logs().stream().anyMatch(p -> data.reservations.containsKey(p.asLong()))) return;
    Map<BlockPos, BlockState> states = new LinkedHashMap<>();
    for (BlockPos p : tree.logs()) states.put(p, level.getBlockState(p));
    pending.putIfAbsent(
        level.dimension().location() + ":" + tree.root().asLong(),
        new Pending(level, player, tree, states, player.getMainHandItem().copy()));
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    for (Pending job : pending.values()) finish(job);
    pending.clear();
  }

  private static void finish(Pending job) {
    ServerLevel l = job.level;
    BlockPos root = job.tree.root();
    if (!l.hasChunkAt(root)
        || !TreeFinder.clearTimberSpace(l.getBlockState(root))
        || job.player.isRemoved()
        || job.player.level() != l) return;
    WorldData data = WorldData.get(l);
    for (var entry : job.logs.entrySet()) {
      BlockPos p = entry.getKey();
      if (p.equals(root)) continue;
      if (!l.hasChunkAt(p)
          || !l.getBlockState(p).equals(entry.getValue())
          || data.placedLogs.contains(p.asLong())
          || data.reservations.containsKey(p.asLong())) return;
    }
    // Vanilla already paid for the broken base log and damaged the axe once.
    var tree =
        new TreeFinder.Tree(
            root,
            job.tree.logs().stream().filter(p -> !p.equals(root)).toList(),
            job.tree.leaves(),
            job.tree.baseCount());
    var landing = TreeFinder.landing(l, tree, null, job.player.getUUID(), job.player);
    if (landing != null) Timber.commitPlayer(l, tree, landing, job.player, job.tool);
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
    pending.clear();
  }
}
