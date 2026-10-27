package dev.automatons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Read-only legacy history. No commands, scheduler, tickets, or terrain writes remain. */
public final class LumiteRetrogen extends SavedData {
  private final CompoundTag history;

  private LumiteRetrogen(CompoundTag history) {
    this.history = history.copy();
  }

  public static LumiteRetrogen load(CompoundTag saved) {
    return new LumiteRetrogen(saved);
  }

  public static LumiteRetrogen get(ServerLevel level) {
    return level
        .getServer()
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            LumiteRetrogen::load,
            () -> new LumiteRetrogen(new CompoundTag()),
            "automatons_lumite_retrogen");
  }

  @Override
  public CompoundTag save(CompoundTag output) {
    output.merge(history.copy());
    return output;
  }
}
