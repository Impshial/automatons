package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;

public final class LuminaHarvest {
  private record Pending(
      ServerLevel level, BlockPos pos, BlockState before, UUID player, String category) {}

  private static final Map<String, Pending> pending = new LinkedHashMap<>();

  public static boolean tagged(BlockState s, String category) {
    return s.is(TagKey.create(Registries.BLOCK, Automatons.id("magica/" + category)));
  }

  public static String category(ServerLevel l, BlockPos p, BlockState s) {
    if (DeadVegetation.isDead(s)) return "";
    WorldData d = WorldData.get(l);
    if (d.placedLogs.contains(p.asLong()) || d.placedPlants.contains(p.asLong())) return "";
    if (tagged(s, "logs")) {
      if (!d.naturalLogs.contains(p.asLong()) && !TreeFinder.recognizeNatural(l, p)) return "";
      return "logs";
    }
    if (tagged(s, "crops")) {
      if (s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) return "crops";
      if (s.is(Blocks.NETHER_WART) && s.getValue(NetherWartBlock.AGE) == 3) return "crops";
      return "";
    }
    if (tagged(s, "fruit")) return "fruit";
    if (tagged(s, "berries"))
      return s.hasProperty(SweetBerryBushBlock.AGE) && s.getValue(SweetBerryBushBlock.AGE) == 3
          ? "berries"
          : "";
    if (tagged(s, "flowers")) return "flowers";
    if (tagged(s, "stems")) return "stems";
    if (tagged(s, "leaves"))
      return s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)
          ? "leaves"
          : "";
    if (tagged(s, "grass")) return "grass";
    return "";
  }

  public record Drop(int largeValue, boolean small) {}

  public static List<Drop> drops(String category, int largeValue, int percentile) {
    if (largeValue <= 0) return List.of();
    return switch (category) {
      case "flowers", "grass" -> List.of(new Drop(largeValue, true));
      case "crops" -> List.of(new Drop(largeValue, percentile >= 10));
      case "logs" -> percentile < 40
          ? List.of(
              new Drop(largeValue, true), new Drop(largeValue, true), new Drop(largeValue, false))
          : List.of(new Drop(largeValue, true), new Drop(largeValue, true));
      default -> List.of(new Drop(largeValue, false));
    };
  }

  public static void award(ServerLevel level, Vec3 position, String category) {
    for (Drop drop :
        drops(category, Balance.roll(category, level.random), level.random.nextInt(100)))
      LuminaOrb.drop(level, position, drop.largeValue(), drop.small());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void broken(BlockEvent.BreakEvent event) {
    if (event.isCanceled()
        || !(event.getLevel() instanceof ServerLevel l)
        || event.getPlayer() instanceof FakePlayer) return;
    BlockPos p = event.getPos().immutable();
    BlockState before = event.getState();
    BlockPos logical =
        before.hasProperty(DoublePlantBlock.HALF)
                && before.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
            ? p.below()
            : p;
    String key = l.dimension().location() + ":" + logical.asLong();
    pending.putIfAbsent(
        key, new Pending(l, p, before, event.getPlayer().getUUID(), category(l, p, before)));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void placed(BlockEvent.EntityPlaceEvent event) {
    if (event.isCanceled()
        || !(event.getLevel() instanceof ServerLevel l)
        || !(event.getEntity() instanceof Player)) return;
    if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
      for (var snapshot : multi.getReplacedBlockSnapshots())
        markPlaced(l, snapshot.getPos(), l.getBlockState(snapshot.getPos()));
    } else markPlaced(l, event.getPos(), event.getPlacedBlock());
  }

  public static void markPlaced(ServerLevel l, BlockPos p, BlockState state) {
    SourceReserves.get(l).forget(p);
    WorldEvents.changedNear(l, p);
    WorldData d = WorldData.get(l);
    d.naturalLogs.remove(p.asLong());
    if (state.is(net.minecraft.tags.BlockTags.LOGS)) d.placedLogs.add(p.asLong());
    else d.placedLogs.remove(p.asLong());
    boolean eligible =
        state.is(Blocks.MOSS_CARPET)
            || state.is(Blocks.MANGROVE_PROPAGULE)
            || state.is(Blocks.VINE)
            || state.is(Blocks.MANGROVE_ROOTS)
            || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    for (String category : List.of("logs", "fruit", "flowers", "stems", "leaves", "grass"))
      eligible |= tagged(state, category);
    if (eligible) d.placedPlants.add(p.asLong());
    else d.placedPlants.remove(p.asLong());
    d.setDirty();
  }

  @SubscribeEvent
  public static void growth(BlockEvent.CropGrowEvent.Post e) {
    if (e.getLevel() instanceof ServerLevel l) {
      WorldData d = WorldData.get(l);
      if (!e.getOriginalState().equals(e.getState())) {
        d.placedPlants.remove(e.getPos().asLong());
        d.setDirty();
      }
    }
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    for (Pending p : pending.values()) {
      if (!p.level.hasChunkAt(p.pos) || p.level.getBlockState(p.pos).equals(p.before)) continue;
      // Only a completed removal pays. The event's pre-break state decided eligibility.
      SourceReserves.get(p.level).forget(p.pos);
      award(p.level, Vec3.atCenterOf(p.pos), p.category);
      WorldData d = WorldData.get(p.level);
      d.placedLogs.remove(p.pos.asLong());
      d.placedPlants.remove(p.pos.asLong());
      d.naturalLogs.remove(p.pos.asLong());
      d.setDirty();
      WorldEvents.changedNear(p.level, p.pos);
    }
    pending.clear();
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent e) {
    if (e.getEntity() instanceof ServerPlayer p && !Balance.KEEP_LUMINA.get()) Lumina.clear(p);
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
    pending.clear();
  }
}
