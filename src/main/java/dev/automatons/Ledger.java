package dev.automatons;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-wide identities and balances, independent of player life and dimension. */
public final class Ledger extends SavedData {
  public final Map<UUID, Integer> magica = new HashMap<>();
  public final Map<UUID, Integer> magicaThirds = new HashMap<>();
  public final Map<UUID, CompoundTag> workers = new HashMap<>();

  public static Ledger get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(Ledger::load, Ledger::new, "automatons_ledger");
  }

  public static Ledger load(CompoundTag n) {
    Ledger data = new Ledger();
    for (Tag t : n.getList("Balances", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Player")) {
        data.magica.put(c.getUUID("Player"), Math.max(0, c.getInt("Points")));
        data.magicaThirds.put(c.getUUID("Player"), Math.max(0, Math.min(2, c.getInt("Thirds"))));
      }
    }
    for (Tag t : n.getList("Workers", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Id")) data.workers.put(c.getUUID("Id"), c.copy());
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag n) {
    n.putInt("Version", 3);
    ListTag balances = new ListTag();
    magica.forEach(
        (id, points) -> {
          CompoundTag c = new CompoundTag();
          c.putUUID("Player", id);
          c.putInt("Points", points);
          c.putInt("Thirds", magicaThirds.getOrDefault(id, 0));
          balances.add(c);
        });
    n.put("Balances", balances);
    ListTag entries = new ListTag();
    workers.values().forEach(c -> entries.add(c.copy()));
    n.put("Workers", entries);
    return n;
  }
}
