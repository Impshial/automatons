package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;

public final class Permissions {
  public enum Action {
    CUT,
    FALL,
    PROCESS,
    MOVE,
    INVENTORY
  }

  public static boolean allowed(ServerLevel level, BlockPos pos, UUID owner, Action action) {
    if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return false;
    if (action == Action.MOVE || action == Action.INVENTORY) return true;
    if (WorldData.get(level).protectedAt(pos)) return false;
    if (WorldData.get(level).mineProtected(pos, null)) return false;
    if (WorldData.get(level).buildingProtected(level, pos)) return false;
    if (WorldData.get(level).farmProtected(pos)) return false;
    if (SiteLayout.protectedBed(level, pos)) return false;
    // Claim mods that honor Forge's standard break event can veto destructive work.
    var actor = FakePlayerFactory.get(level, new GameProfile(owner, "[Automatons]"));
    return !MinecraftForge.EVENT_BUS.post(
        new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), actor));
  }
}
