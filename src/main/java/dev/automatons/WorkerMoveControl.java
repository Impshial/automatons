package dev.automatons;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.DoorBlock;

/** Center each worker's body in the usable gap beside an open door panel. */
public final class WorkerMoveControl extends MoveControl {
  private final Lumberton worker;

  public WorkerMoveControl(Lumberton worker) {
    super(worker);
    this.worker = worker;
  }

  /** Navigation.stop() leaves a pending final step in MoveControl. */
  public void stop() {
    operation = Operation.WAIT;
    worker.setSpeed(0);
    worker.setXxa(0);
    worker.setZza(0);
  }

  @Override
  public void tick() {
    var path = worker.getNavigation().getPath();
    if (operation == Operation.MOVE_TO && path != null && !path.isDone()) {
      for (int i = Math.max(0, path.getNextNodeIndex() - 1);
          i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2);
          i++) {
        var pos = path.getNode(i).asBlockPos();
        var state = worker.level().getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) continue;
        var shape = state.getCollisionShape(worker.level(), pos);
        if (shape.isEmpty()) continue;
        var box = shape.bounds();
        if (state.getValue(DoorBlock.FACING).getAxis() == Direction.Axis.Z) {
          if (Math.abs(worker.getZ() - (pos.getZ() + .5)) > 1.3) continue;
          double center = pos.getX() + (box.maxX < .5 ? (box.maxX + 1) / 2 : box.minX / 2);
          wantedX = center;
          double clearance = (1 - box.getXsize() - worker.getBbWidth()) / 2 - .01;
          if (Math.abs(worker.getX() - center) > Math.max(.005, clearance)) wantedZ = worker.getZ();
        } else {
          if (Math.abs(worker.getX() - (pos.getX() + .5)) > 1.3) continue;
          double center = pos.getZ() + (box.maxZ < .5 ? (box.maxZ + 1) / 2 : box.minZ / 2);
          wantedZ = center;
          double clearance = (1 - box.getZsize() - worker.getBbWidth()) / 2 - .01;
          if (Math.abs(worker.getZ() - center) > Math.max(.005, clearance)) wantedX = worker.getX();
        }
        break;
      }
    }
    super.tick();
  }
}
