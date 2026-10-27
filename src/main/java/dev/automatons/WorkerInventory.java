package dev.automatons;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Dynamic slot offsets: a cached capability still sees a committed job-capacity change. */
public final class WorkerInventory implements IItemHandler {
  private final Lumberton worker;

  public WorkerInventory(Lumberton worker) {
    this.worker = worker;
  }

  private IItemHandler handler(int slot) {
    if (slot < 0 || slot >= getSlots()) throw new IndexOutOfBoundsException(slot);
    return slot < 3 ? worker.tools : worker.cargo;
  }

  private int index(int slot) {
    return slot < 3 ? slot : slot - 3;
  }

  public int getSlots() {
    return 3 + worker.cargo.getSlots();
  }

  public ItemStack getStackInSlot(int slot) {
    return handler(slot).getStackInSlot(index(slot));
  }

  public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
    return handler(slot).insertItem(index(slot), stack, simulate);
  }

  public ItemStack extractItem(int slot, int amount, boolean simulate) {
    return handler(slot).extractItem(index(slot), amount, simulate);
  }

  public int getSlotLimit(int slot) {
    return handler(slot).getSlotLimit(index(slot));
  }

  public boolean isItemValid(int slot, ItemStack stack) {
    return handler(slot).isItemValid(index(slot), stack);
  }
}
