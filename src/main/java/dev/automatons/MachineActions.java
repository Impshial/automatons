package dev.automatons;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

public final class MachineActions {
  public record Action(int entity, int action, String value, String name) {
    public static void encode(Action a, FriendlyByteBuf b) {
      b.writeInt(a.entity);
      b.writeInt(a.action);
      b.writeUtf(a.value, MachineRules.MAX_TEXT);
      b.writeUtf(a.name, 80);
    }

    public static Action decode(FriendlyByteBuf b) {
      return new Action(b.readInt(), b.readInt(), b.readUtf(MachineRules.MAX_TEXT), b.readUtf(80));
    }
  }

  public static void register() {
    Network.CHANNEL.registerMessage(
        4,
        Action.class,
        Action::encode,
        Action::decode,
        (a, supplier) -> {
          var context = supplier.get();
          context.enqueueWork(() -> handle(context.getSender(), a));
          context.setPacketHandled(true);
        },
        Optional.of(NetworkDirection.PLAY_TO_SERVER));
  }

  private static void handle(ServerPlayer p, Action a) {
    if (p == null || !(p.serverLevel().getEntity(a.entity) instanceof Machine m) || !m.owns(p))
      return;
    boolean driving = p.getVehicle() == m;
    MachineMenu menu =
        p.containerMenu instanceof MachineMenu mm && mm.machine == m && mm.stillValid(p)
            ? mm
            : null;
    if (!driving && menu == null) return;
    String result = "";
    try {
      switch (a.action) {
        case 0 -> {
          if (menu != null && !m.locked()) {
            m.mode = Machine.Mode.valueOf(a.value);
            m.job.cancel();
            m.status = "Mode selected";
          }
        }
        case 1 -> {
          if (m.engine == Machine.Engine.OFF || m.engine == Machine.Engine.STOPPING)
            m.startEngine();
          else m.halt("Engine stopped — job paused");
        }
        case 2 -> {
          if (menu != null) result = m.job.start();
        }
        case 3 -> m.halt("Job paused");
        case 4 -> {
          if (menu != null) result = m.job.resume();
        }
        case 5 -> {
          if (menu != null) {
            m.halt("Job cancelled");
            m.job.cancel();
          }
        }
        case 6 -> {
          if (menu != null
              && !m.locked()
              && m.equipment.getStackInSlot(1).is(MachineParts.item("program"))) {
            var tag = m.equipment.getStackInSlot(1).getOrCreateTag();
            if (!tag.getString("Text").equals(a.value)) m.job.programChanged();
            tag.putString("Text", a.value);
            tag.putString("ProgramName", a.name);
            result = "Program saved";
          }
        }
        case 7 -> {
          if (driving) {
            m.input(0);
            m.open(p);
          }
        }
        case 8 -> {
          if (driving) m.input(Integer.parseInt(a.value));
        }
        case 9 -> {
          if (menu != null
              && p.serverLevel().getEntity(UUID.fromString(a.value)) instanceof Lumberton w)
            result = MachineOperator.assign(m, w);
        }
        case 10 -> {
          if (menu != null) MachineOperator.release(m);
        }
        case 11 -> {
          if (menu != null)
            result =
                m.link(UUID.fromString(a.value)) ? "Pedestal linked" : "Unable to link pedestal";
        }
        case 12 -> {
          if (menu != null) menu.page = Math.max(0, Math.min(1, Integer.parseInt(a.value)));
        }
        case 13 -> {
          if (menu != null) menu.tab = Math.max(0, Math.min(2, Integer.parseInt(a.value)));
        }
        case 14 -> {
          if (menu != null && !m.locked()) {
            m.job.chunkX = m.grid.getX() >> 4;
            m.job.chunkZ = m.grid.getZ() >> 4;
            result = "Selected current chunk";
          }
        }
        case 15 -> {
          if (menu != null && m.operator == null && m.getPassengers().isEmpty()) {
            m.halt("Manual control");
            m.mode = Machine.Mode.MANUAL;
            p.closeContainer();
            p.startRiding(m);
          }
        }
      }
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (!result.isEmpty()) {
      m.status = result;
      p.displayClientMessage(Component.literal(result), true);
    }
    m.sync();
  }
}
