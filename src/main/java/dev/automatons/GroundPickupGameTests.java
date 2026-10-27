package dev.automatons;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("automatons_worksites")
@PrefixGameTestTemplate(false)
public final class GroundPickupGameTests {
  private static void floor(GameTestHelper h) {
    for (int x = 0; x < 28; x++)
      for (int z = 0; z < 28; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
  }

  private static Lumberton worker(GameTestHelper h, int job, BlockPos at) {
    Lumberton w = Automatons.LUMBERTON.get().create(h.getLevel());
    w.owner = UUID.randomUUID();
    w.setJob(job);
    w.moveTo(Vec3.atBottomCenterOf(h.absolutePos(at)));
    h.getLevel().addFreshEntity(w);
    return w;
  }

  private static ItemEntity drop(GameTestHelper h, Vec3 at, ItemStack stack) {
    ItemEntity e = new ItemEntity(h.getLevel(), at.x, at.y + .15, at.z, stack);
    e.setNoPickUpDelay();
    e.setNoGravity(true);
    e.setDeltaMovement(Vec3.ZERO);
    h.getLevel().addFreshEntity(e);
    return e;
  }

  private static Item tool(int job) {
    return switch (job) {
      case WorkerJob.MININGTON -> Items.IRON_PICKAXE;
      case WorkerJob.BUILDINGTON -> Automatons.HAMMER.get();
      case WorkerJob.FARMINGTON -> Items.IRON_HOE;
      default -> Items.IRON_AXE;
    };
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void all_jobs_pick_up_nearby_items_while_idle_or_paused(GameTestHelper h) {
    floor(h);
    List<Lumberton> workers = new ArrayList<>();
    List<ItemEntity> drops = new ArrayList<>();
    for (int row = 0; row < 2; row++)
      for (int job = 0; job <= 5; job++) {
        Lumberton w = worker(h, job, new BlockPos(3 + job * 4, 1, 4 + row * 8));
        w.authorized = row == 0;
        workers.add(w);
        for (Item item :
            List.of(Items.OAK_LOG, Items.DIAMOND, Items.TORCH, Items.OAK_SAPLING, Items.COAL))
          drops.add(drop(h, w.position(), new ItemStack(item, 3)));
        ItemStack equipment = new ItemStack(tool(job));
        equipment.setDamageValue(17);
        equipment.setHoverName(Component.literal("Recovered equipment"));
        drops.add(drop(h, w.position(), equipment));
      }
    h.runAtTickTime(
        20,
        () -> {
          for (Lumberton w : workers) {
            for (Item item :
                List.of(Items.OAK_LOG, Items.DIAMOND, Items.TORCH, Items.OAK_SAPLING, Items.COAL))
              h.assertTrue(
                  w.mining.carried(item) == 3,
                  "Missing pickup for " + WorkerJob.name(w.job()) + ": " + item);
            ItemStack equipment =
                w.job() == 0 ? w.cargo.getStackInSlot(5) : w.tools.getStackInSlot(0);
            if (w.job() == 0)
              for (int i = 0; i < w.cargo.getSlots(); i++)
                if (w.cargo.getStackInSlot(i).is(tool(0))) equipment = w.cargo.getStackInSlot(i);
            h.assertTrue(
                equipment.is(tool(w.job()))
                    && equipment.getDamageValue() == 17
                    && equipment.getHoverName().getString().equals("Recovered equipment"),
                "Tool pickup lost metadata or wrong slot");
            h.assertTrue(w.getNavigation().isDone(), "Nearby cleanup started a travel job");
          }
          h.assertTrue(drops.stream().allMatch(ItemEntity::isRemoved), "Collected entity remains");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 70)
  public static void partial_pickup_and_competing_workers_conserve_items(GameTestHelper h) {
    floor(h);
    Lumberton a = worker(h, 0, new BlockPos(8, 1, 8));
    Lumberton b = worker(h, 0, new BlockPos(9, 1, 8));
    ItemStack named = new ItemStack(Items.COBBLESTONE, 10);
    named.setHoverName(Component.literal("Recovered stone"));
    for (Lumberton w : List.of(a, b)) {
      for (int slot = 0; slot < w.cargo.getSlots(); slot++)
        w.cargo.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
      w.cargo.setStackInSlot(0, named.copyWithCount(w == a ? 62 : 61));
    }
    ItemEntity item = drop(h, a.position().add(.5, 0, 0), named);
    h.runAtTickTime(
        20,
        () -> {
          h.assertTrue(
              a.mining.carried(Items.COBBLESTONE) + b.mining.carried(Items.COBBLESTONE) == 128,
              "Partial insertion duplicated/lost cargo");
          h.assertTrue(
              item.isAlive() && item.getItem().getCount() == 5,
              "Full inventories destroyed leftovers");
          h.assertTrue(
              item.getItem().getHoverName().getString().equals("Recovered stone"),
              "Remainder lost metadata");
          a.cargo.setStackInSlot(1, ItemStack.EMPTY);
        });
    h.runAtTickTime(
        45,
        () -> {
          h.assertTrue(item.isRemoved(), "Did not resume after space was made");
          h.assertTrue(
              a.mining.carried(Items.COBBLESTONE) + b.mining.carried(Items.COBBLESTONE) == 133,
              "Competing workers duplicated pickup");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 60)
  public static void spare_tools_go_into_cargo_without_replacing_equipment(GameTestHelper h) {
    floor(h);
    List<Lumberton> workers = new ArrayList<>();
    for (int job = 1; job <= 5; job++) {
      Lumberton w = worker(h, job, new BlockPos(3 + job * 4, 1, 8));
      workers.add(w);
      for (int slot = 0; slot < 3; slot++) {
        ItemStack equipped = new ItemStack(tool(job));
        equipped.setDamageValue(13 + slot);
        w.tools.setStackInSlot(slot, equipped);
      }
      ItemStack spare = new ItemStack(tool(job));
      spare.setDamageValue(42);
      spare.setHoverName(Component.literal("Spare"));
      drop(h, w.position(), spare);
    }
    h.runAtTickTime(
        20,
        () -> {
          for (Lumberton w : workers) {
            for (int slot = 0; slot < 3; slot++)
              h.assertTrue(
                  w.tools.getStackInSlot(slot).getDamageValue() == 13 + slot,
                  "Replaced an equipped tool");
            ItemStack spare = w.cargo.getStackInSlot(0);
            h.assertTrue(
                spare.is(tool(w.job()))
                    && spare.getDamageValue() == 42
                    && spare.getHoverName().getString().equals("Spare"),
                "Spare tool was not preserved as cargo");
          }
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 90)
  public static void pickup_respects_delay_ownership_walls_and_nearby_range(GameTestHelper h) {
    floor(h);
    Lumberton w = worker(h, WorkerJob.MININGTON, new BlockPos(8, 1, 8));
    w.authorized = false;
    ItemEntity own = drop(h, w.position(), new ItemStack(Items.EMERALD));
    own.setTarget(w.owner);
    ItemEntity foreign = drop(h, w.position(), new ItemStack(Items.DIAMOND));
    foreign.setTarget(UUID.randomUUID());
    ItemEntity delayed = drop(h, w.position(), new ItemStack(Items.COAL));
    delayed.setPickUpDelay(40);
    ItemEntity distant = drop(h, w.position().add(5, 0, 0), new ItemStack(Items.GOLD_INGOT));
    BlockPos wall = new BlockPos(9, 1, 8);
    h.setBlock(wall, Blocks.GLASS_PANE);
    h.setBlock(wall.above(), Blocks.GLASS_PANE);
    ItemEntity hidden = drop(h, w.position().add(1.3, 0, 0), new ItemStack(Items.IRON_INGOT));
    h.runAtTickTime(
        20,
        () -> {
          h.assertTrue(own.isRemoved(), "Worker rejected its owner's drop");
          h.assertTrue(foreign.isAlive(), "Stole an offline owner's protected drop");
          h.assertTrue(delayed.isAlive(), "Ignored pickup delay");
          h.assertTrue(hidden.isAlive(), "Picked up through a wall");
          h.assertTrue(distant.isAlive(), "Left its job to chase distant drops");
        });
    h.runAtTickTime(
        55,
        () -> {
          h.assertTrue(delayed.isRemoved(), "Did not collect when delay expired");
          h.assertTrue(hidden.isAlive(), "Picked up through a wall after waiting");
          h.setBlock(wall, Blocks.AIR);
          h.setBlock(wall.above(), Blocks.AIR);
        });
    h.runAtTickTime(
        75,
        () -> {
          h.assertTrue(hidden.isRemoved(), "Did not collect after obstruction cleared");
          h.assertTrue(
              foreign.isAlive() && distant.isAlive(), "Ownership or range protection changed");
          h.succeed();
        });
  }
}
