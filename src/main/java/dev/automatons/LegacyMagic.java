package dev.automatons;

import net.minecraft.nbt.CompoundTag;

/** Isolated serialized aliases retained for pre-0.6 worlds and resource packs. */
public final class LegacyMagic {
  public static final String EMPTY_CORE_ID = "empty_magica_core",
      CORE_ID = "magica_core",
      ORB_ID = "magica_orb";

  public static int coreCredit(CompoundTag tag) {
    return Math.max(0, Math.min(49, tag.getInt("Magica")));
  }

  private LegacyMagic() {}
}
