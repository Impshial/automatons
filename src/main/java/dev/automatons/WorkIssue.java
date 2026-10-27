package dev.automatons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** A concrete intervention, separate from ordinary travel, scheduling and supply waits. */
public final class WorkIssue {
  public static CompoundTag at(ServerLevel level, BlockPos pos, String reason, String remedy) {
    CompoundTag n = new CompoundTag();
    n.putString("Reason", reason);
    n.putString("Action", remedy);
    n.putLong("Position", pos.asLong());
    n.putString("Coordinates", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    n.putString("Dimension", level.dimension().location().toString());
    if (level.hasChunkAt(pos)) {
      var state = level.getBlockState(pos);
      n.putString("Block", state.getBlock().getName().getString());
      n.putString("BlockId", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
      n.putString("BlockState", state.toString());
    } else n.putString("Block", "Unloaded — block not inspected");
    return n;
  }
}
