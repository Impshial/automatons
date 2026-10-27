package dev.automatons;

import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Every assignment path uses the same server checks, including restored entity links. */
public final class Assignments {
  public static final int LIMIT = 6;

  public static String rejection(Lumberton worker, Station site) {
    if (!site.isTotem() || worker.owner == null || !worker.owner.equals(site.owner))
      return "Choose your own totem";
    if (worker.isGuard()) return "Guardingtons use protection assignments";
    if (site.purpose == WorksitePurpose.UNCONFIGURED) return "Choose the totem's profession first";
    if (site.logisticsHome != null && !site.logisticsHome.equals(worker.homeId))
      return "Totem belongs to another home";
    if (!site.workers.contains(worker.getUUID()) && productionCount(site) >= LIMIT)
      return "This totem already has six workers";
    return "";
  }

  public static int productionCount(Station site) {
    if (!(site.getLevel() instanceof ServerLevel level)) return site.workers.size();
    var ledger = Ledger.get(level.getServer());
    return (int) site.workers.stream().filter(id -> {
      if (level.getEntity(id) instanceof Lumberton worker) return !worker.isGuard();
      var record = ledger.workers.get(id);
      return record == null || record.getInt("Job") != WorkerJob.GUARDINGTON;
    }).count();
  }

  public static void validate(Lumberton worker) {
    if (worker.totem == null || !worker.level().hasChunkAt(worker.totem)) return;
    Station site = worker.station();
    if (site == null || !rejection(worker, site).isEmpty()
        || site.workers.stream().sorted().limit(LIMIT).noneMatch(worker.getUUID()::equals)) {
      worker.unassign();
      return;
    }
    if (worker.job() != site.purpose.job() && !worker.setJob(site.purpose.job())) worker.unassign();
  }
}
