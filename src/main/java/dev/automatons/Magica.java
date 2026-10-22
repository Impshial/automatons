package dev.automatons;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class Magica {
  public static int balance(Player player) {
    return player.getServer() == null
        ? 0
        : Ledger.get(player.getServer()).magica.getOrDefault(player.getUUID(), 0);
  }

  public static int add(ServerPlayer player, int amount) {
    int old = balance(player);
    int next = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (long) old + amount));
    Ledger data = Ledger.get(player.server);
    data.magica.put(player.getUUID(), next);
    data.setDirty();
    sync(player);
    return next - old;
  }

  public static boolean spend(ServerPlayer player, int amount) {
    if (amount < 0 || balance(player) < amount) return false;
    add(player, -amount);
    return true;
  }

  public static void sync(ServerPlayer p) {
    CompoundTag n = new CompoundTag();
    n.putInt("Magica", balance(p));
    Network.balance(p, n);
  }

  public static int levelCost(int level) {
    return level >= 30
        ? 112 + (level - 30) * 9
        : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
  }

  public static int[] display(int points) {
    int level = 0;
    while (points >= levelCost(level)) {
      points -= levelCost(level++);
    }
    return new int[] {level, points, levelCost(level)};
  }
}
