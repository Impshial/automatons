package dev.automatons;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-wide identities and balances, independent of player life and dimension. */
public final class Ledger extends SavedData {
  public final Map<UUID, Integer> lumina = new HashMap<>();
  public final Map<UUID, Integer> luminaThirds = new HashMap<>();
  public final Map<UUID, Double> bottleFractions = new HashMap<>();
  public final Map<UUID, CompoundTag> workers = new HashMap<>();
  public NameRegistry names = new NameRegistry();
  public final Map<UUID, Boolean> homeNames = new HashMap<>();
  private boolean namesReconciled;

  public static Ledger get(MinecraftServer server) {
    Ledger ledger =
        server
            .overworld()
            .getDataStorage()
            .computeIfAbsent(Ledger::load, Ledger::new, "automatons_ledger");
    if (!ledger.namesReconciled) {
      ledger.namesReconciled = true;
      int[] counts = ledger.names.migrate(ledger.workers, server.overworld().random);
      if (!ledger.workers.isEmpty()) ledger.setDirty();
      if (counts[0] > 0 || counts[1] > 0)
        org.slf4j.LoggerFactory.getLogger("automatons.names")
            .info("Name migration: {} assigned, {} legacy conflicts", counts[0], counts[1]);
    }
    return ledger;
  }

  public static Ledger load(CompoundTag n) {
    Ledger data = new Ledger();
    for (Tag t : n.getList("Balances", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Player")) {
        data.lumina.put(c.getUUID("Player"), Math.max(0, c.getInt("Points")));
        data.luminaThirds.put(c.getUUID("Player"), Math.max(0, Math.min(2, c.getInt("Thirds"))));
        double fraction = c.getDouble("BottleFraction");
        data.bottleFractions.put(
            c.getUUID("Player"),
            Double.isFinite(fraction) ? Math.max(0, Math.min(.999999999, fraction)) : 0);
      }
    }
    for (Tag t : n.getList("Workers", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Id")) data.workers.put(c.getUUID("Id"), c.copy());
    }
    data.names = NameRegistry.load(n.getCompound("Names"));
    for (Tag t : n.getList("HomeNames", 10)) {
      CompoundTag c = (CompoundTag) t;
      if (c.hasUUID("Id")) data.homeNames.put(c.getUUID("Id"), c.getBoolean("Show"));
    }
    return data;
  }

  @Override
  public CompoundTag save(CompoundTag n) {
    n.putInt("Version", 4);
    n.put("Names", names.save());
    ListTag homes = new ListTag();
    homeNames.forEach(
        (id, show) -> {
          CompoundTag c = new CompoundTag();
          c.putUUID("Id", id);
          c.putBoolean("Show", show);
          homes.add(c);
        });
    n.put("HomeNames", homes);
    ListTag balances = new ListTag();
    lumina.forEach(
        (id, points) -> {
          CompoundTag c = new CompoundTag();
          c.putUUID("Player", id);
          c.putInt("Points", points);
          c.putInt("Thirds", luminaThirds.getOrDefault(id, 0));
          c.putDouble("BottleFraction", bottleFractions.getOrDefault(id, 0d));
          balances.add(c);
        });
    n.put("Balances", balances);
    ListTag entries = new ListTag();
    workers.values().forEach(c -> entries.add(c.copy()));
    n.put("Workers", entries);
    return n;
  }
}
