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

  public static int remainder(Player player) {
    return player.getServer() == null
        ? 0
        : Ledger.get(player.getServer()).magicaThirds.getOrDefault(player.getUUID(), 0);
  }

  public static long thirds(Player player) {
    return balance(player) * 3L + remainder(player);
  }

  public static long addThirds(ServerPlayer player, long amount) {
    long old = thirds(player), maximum = Integer.MAX_VALUE * 3L + 2;
    long delta = amount >= 0 ? Math.min(amount, maximum - old) : Math.max(amount, -old);
    long next = old + delta;
    Ledger data = Ledger.get(player.server);
    data.magica.put(player.getUUID(), (int) (next / 3));
    data.magicaThirds.put(player.getUUID(), (int) (next % 3));
    data.setDirty();
    sync(player);
    return delta;
  }

  public static void clear(ServerPlayer player) {
    addThirds(player, -thirds(player));
  }

  public static String format(int points, int remainder) {
    return "" + points + (remainder == 1 ? "⅓" : remainder == 2 ? "⅔" : "");
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
    n.putInt("Thirds", remainder(p));
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
