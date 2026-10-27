package dev.automatons;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.items.*;
import net.minecraftforge.network.PacketDistributor;

public final class MachineMenu extends AbstractContainerMenu {
  public final Machine machine;
  public int tab, page;
  public CompoundTag snapshot = new CompoundTag();

  public MachineMenu(int id, Inventory inv, FriendlyByteBuf buf) {
    this(id, inv, (Machine) inv.player.level().getEntity(buf.readInt()));
  }

  public MachineMenu(int id, Inventory inv, Machine machine) {
    super(MachineParts.MENU.get(), id);
    this.machine = Objects.requireNonNull(machine);
    for (int i = 0; i < 108; i++) {
      final int slot = i;
      addSlot(
          new SlotItemHandler(machine.cargo, i, 8 + i % 9 * 18, 50 + (i / 9 % 6) * 18) {
            @Override
            public boolean isActive() {
              return tab == 2 && slot / 54 == page;
            }
          });
    }
    for (int i = 0; i < 3; i++) {
      final int slot = i;
      addSlot(
          new SlotItemHandler(machine.equipment, i, 8, i == 0 ? 72 : i == 1 ? 44 : 102) {
            @Override
            public int getMaxStackSize() {
              return 1;
            }

            @Override
            public boolean isActive() {
              return tab == 0 || slot == 1 && tab == 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
              if (slot == 2)
                return stack.is(Luminall.BOTTLE.get())
                    || stack
                        .getCapability(
                            net.minecraftforge.common.capabilities.ForgeCapabilities
                                .FLUID_HANDLER_ITEM)
                        .isPresent();
              if (machine.locked()) return false;
              return slot == 0 ? machine.canInstall(stack) : stack.is(MachineParts.item("program"));
            }

            @Override
            public boolean mayPickup(Player player) {
              return slot == 2 || !machine.locked();
            }

            @Override
            public void setChanged() {
              super.setChanged();
              if (!machine.level().isClientSide
                  && slot == 1
                  && !machine.job.text.equals(
                      machine.equipment.getStackInSlot(1).getOrCreateTag().getString("Text")))
                machine.job.programChanged();
              machine.sync();
            }
          });
    }
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 178 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 236));
  }

  @Override
  public boolean stillValid(Player p) {
    return !machine.isRemoved()
        && machine.owns(p)
        && (p.getVehicle() == machine || p.distanceToSqr(machine) < 100);
  }

  @Override
  public void clicked(int slot, int button, ClickType type, Player p) {
    if (!stillValid(p) || slot >= 0 && slot < slots.size() && !slots.get(slot).isActive()) return;
    super.clicked(slot, button, type, p);
  }

  @Override
  public ItemStack quickMoveStack(Player p, int index) {
    if (index < 0 || index >= slots.size() || !stillValid(p)) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.isActive() || !slot.hasItem() || !slot.mayPickup(p)) return ItemStack.EMPTY;
    ItemStack stack = slot.getItem(), original = stack.copy();
    if (index < 111) {
      if (!moveItemStackTo(stack, 111, slots.size(), true)) return ItemStack.EMPTY;
    } else {
      boolean moved = false;
      for (int i = 108; i < 111; i++)
        if (slots.get(i).isActive() && slots.get(i).mayPlace(stack))
          moved |= moveItemStackTo(stack, i, i + 1, false);
      if (!stack.isEmpty()) {
        // Cargo shift-click intentionally fills all 108 real slots, including the hidden page.
        ItemStack rest = Inventories.insert(machine.cargo, stack, false);
        moved |= rest.getCount() != stack.getCount();
        stack.setCount(rest.getCount());
      }
      if (!moved) return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
    else slot.setChanged();
    return original;
  }

  @Override
  public void broadcastChanges() {
    super.broadcastChanges();
    if (!(machine.level() instanceof net.minecraft.server.level.ServerLevel l)) return;
    snapshot = machine.view().copy();
    ListTag homes = new ListTag(), operators = new ListTag();
    for (var e : WorldData.get(l).homes.entrySet())
      if (e.getValue().exists()
          && Objects.equals(e.getValue().owner(), machine.owner)
          && e.getValue().pos().distSqr(machine.grid) < 16384) {
        CompoundTag n = new CompoundTag();
        n.putUUID("Id", e.getKey());
        n.putString("Name", e.getValue().pos().toShortString());
        homes.add(n);
      }
    for (var entity : l.getEntitiesOfClass(Lumberton.class, machine.getBoundingBox().inflate(96)))
      if (MachineOperator.eligible(machine, entity)
          && (entity.machineId == null || entity.machineId.equals(machine.getUUID()))) {
        CompoundTag n = new CompoundTag();
        n.putUUID("Id", entity.getUUID());
        n.putString("Name", entity.getDisplayName().getString());
        operators.add(n);
      }
    snapshot.put("Homes", homes);
    snapshot.put("Operators", operators);
    for (ServerPlayer p : l.players())
      if (p.containerMenu == this)
        Network.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> p),
            new Network.ClientData(6, containerId, snapshot));
  }
}
