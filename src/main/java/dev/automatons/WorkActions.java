package dev.automatons;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.*;

/** Menu-scoped actions with ownership, range and current-state checks on every commit. */
public final class WorkActions {
  public record Action(int menu, String kind, String value) {}

  public static void register() {
    Network.CHANNEL.registerMessage(
        5,
        Action.class,
        (a, b) -> {
          b.writeVarInt(a.menu);
          b.writeUtf(a.kind, 24);
          b.writeUtf(a.value, 80);
        },
        b -> new Action(b.readVarInt(), b.readUtf(24), b.readUtf(80)),
        (a, supplier) -> {
          var c = supplier.get();
          c.enqueueWork(() -> handle(c.getSender(), a));
          c.setPacketHandled(true);
        },
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
  }

  public static void handle(ServerPlayer player, Action action) {
    if (player == null
        || !(player.containerMenu instanceof WorkMenu menu)
        || menu.containerId != action.menu
        || !menu.canEdit()
        || !menu.stillValid(player)) return;
    menu.feedback = "";
    try {
      switch (action.kind) {
        case "name" -> {
          String name = action.value.strip().replaceAll("[\\p{Cntrl}§]", "");
          if (name.isBlank() || name.length() > 40) return;
          if (menu.worker != null) {
            Ledger ledger = Ledger.get(player.getServer());
            if (!ledger.names.rename(menu.worker.getUUID(), name)) {
              menu.feedback = "That name is already in use";
              break;
            }
            menu.worker.setCustomName(Component.literal(name));
            WorkerIdentity.update(menu.worker, "Deployed");
          } else {
            menu.station.customName = name;
            menu.station.changed();
          }
        }
        case "guard" -> {
          if (menu.worker == null || !menu.worker.isGuard()) return;
          UUID id = UUID.fromString(action.value);
          var project = WorldData.get(player.serverLevel()).buildings.get(id);
          if (project != null
              && player.serverLevel().hasChunkAt(project.anchor)
              && player.serverLevel().getBlockEntity(project.anchor) instanceof Station site)
            menu.feedback = menu.worker.guarding.protect(site);
        }
        case "assign", "open", "unassign" -> {
          if (!menu.isTotem) return;
          UUID id = UUID.fromString(action.value);
          var entity = player.serverLevel().getEntity(id);
          if (!(entity instanceof Lumberton) && menu.station.guards.contains(id)) {
            menu.feedback = "Bring this guard into a loaded area to change protection";
            break;
          }
          if (action.kind.equals("unassign") && menu.station.workers.contains(id)) {
            menu.station.removeMember(id);
            break;
          }
          if (!(entity instanceof Lumberton worker)
              || !worker.owns(player)
              || !worker.isAlive()
              || worker.packing) return;
          if (action.kind.equals("assign")) {
            if (worker.distanceToSqr(Vec3.atCenterOf(menu.station.getBlockPos())) <= 32 * 32)
              menu.feedback = worker.assign(menu.station);
          } else if (action.kind.equals("open")) {
            if (worker.distanceToSqr(player) > 64) {
              menu.feedback = "Move within eight blocks of this worker";
              break;
            }
            NetworkHooks.openScreen(
                player,
                worker,
                b -> {
                  b.writeBoolean(true);
                  b.writeInt(worker.getId());
                  b.writeVarInt(worker.cargo.getSlots());
                });
            return;
          } else if (menu.station.guards.contains(id)) worker.guarding.clear();
        }
        case "change" -> {
          if (!menu.isTotem) return;
          int value = Integer.parseInt(action.value);
          WorksitePurpose purpose = WorksitePurpose.of(value);
          menu.feedback = menu.station.professionChangeIssue();
          if (purpose == WorksitePurpose.UNCONFIGURED
              || purpose == menu.station.purpose
              || !menu.feedback.isEmpty()) return;
          menu.pendingProfession = value;
          menu.professionConfirmationUntil = player.level().getGameTime() + 1200;
        }
        case "confirm" -> {
          if (!menu.isTotem
              || menu.pendingProfession == 0
              || player.level().getGameTime() > menu.professionConfirmationUntil) return;
          var value = WorksitePurpose.of(menu.pendingProfession);
          menu.pendingProfession = 0;
          if (!menu.station.changeProfession(value))
            menu.feedback =
                menu.station.professionChangeIssue().isEmpty()
                    ? menu.station.setupIssue
                    : menu.station.professionChangeIssue();
        }
        case "cancel" -> menu.pendingProfession = 0;
        default -> {
          return;
        }
      }
    } catch (IllegalArgumentException invalid) {
      return;
    }
    menu.broadcastChanges();
    Network.snapshot(player, menu.containerId, menu.describe());
  }
}
