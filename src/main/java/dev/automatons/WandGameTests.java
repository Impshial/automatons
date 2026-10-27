package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class WandGameTests {
  private record Fixture(ServerPlayer player, Lumberton worker, Station home, Station totem) {}

  private static Fixture setup(GameTestHelper h, GameType mode) {
    ServerPlayer player =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "WandTest"));
    player.setGameMode(mode);
    BlockPos homePos = new BlockPos(3, 1, 3), totemPos = new BlockPos(5, 1, 3);
    h.setBlock(homePos, Automatons.PEDESTAL.get());
    h.setBlock(totemPos, Automatons.TOTEM.get());
    Station home = (Station) h.getLevel().getBlockEntity(h.absolutePos(homePos));
    Station totem = (Station) h.getLevel().getBlockEntity(h.absolutePos(totemPos));
    home.owner = player.getUUID();
    home.registerHome();
    totem.owner = player.getUUID();
    WorksiteGameTests.readyFixture(totem, WorksitePurpose.LUMBER);
    Lumberton worker = Automatons.LUMBERTON.get().create(h.getLevel());
    worker.owner = player.getUUID();
    worker.home = home.getBlockPos();
    worker.homeId = home.identity;
    worker.homeDimension = h.getLevel().dimension().location().toString();
    worker.setNoAi(true);
    worker.moveTo(Vec3.atBottomCenterOf(home.getBlockPos().south()));
    h.getLevel().addFreshEntity(worker);
    player.setPos(worker.getX(), worker.getY(), worker.getZ() + 1);
    for (InteractionHand hand : InteractionHand.values())
      player.setItemInHand(hand, new ItemStack(Automatons.WAND.get()));
    return new Fixture(player, worker, home, totem);
  }

  private static boolean selected(ItemStack wand) {
    return wand.hasTag() && wand.getTag().hasUUID("Worker");
  }

  private static void select(GameTestHelper h, Fixture f, InteractionHand hand) {
    // Exercise Player.interactOn: creative mode passes a copy into the item callback.
    h.assertTrue(
        f.player.interactOn(f.worker, hand).consumesAction(), "Entity click was not handled");
    ItemStack wand = f.player.getItemInHand(hand);
    h.assertTrue(selected(wand), "Held wand lost selection after entity click");
    h.assertTrue(
        wand.getTag().getUUID("Worker").equals(f.worker.getUUID()), "Wrong worker selected");
    InteractionHand other =
        hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    h.assertTrue(
        !selected(f.player.getItemInHand(other)), "Selection changed the other hand's wand");
  }

  private static void click(GameTestHelper h, Fixture f, InteractionHand hand, Station target) {
    BlockPos pos = target.getBlockPos();
    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    h.assertTrue(
        f.player
            .gameMode
            .useItemOn(f.player, h.getLevel(), f.player.getItemInHand(hand), hand, hit)
            .consumesAction(),
        "Station click was not handled");
  }

  private static void assignment(GameTestHelper h, GameType mode, InteractionHand hand) {
    Fixture f = setup(h, mode);
    try {
      select(h, f, hand);
      click(h, f, hand, f.totem);
      h.assertTrue(
          f.totem.getBlockPos().equals(f.worker.totem) && f.totem.identity.equals(f.worker.totemId),
          "Worker did not bind to the totem");
      h.assertTrue(
          f.totem.workers.contains(f.worker.getUUID()) && f.home.identity.equals(f.worker.homeId),
          "Totem did not retain worker/home identity");
      h.assertTrue(f.home.identity.equals(f.worker.homeId), "Assignment changed the home");
      h.assertTrue(
          !selected(f.player.getItemInHand(hand)), "Successful assignment did not clear selection");
    } finally {
      // Completed tests must not keep consuming the shared totem scan budget.
      f.worker.unassign();
      f.worker.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void creative_main_hand_assignment(GameTestHelper h) {
    assignment(h, GameType.CREATIVE, InteractionHand.MAIN_HAND);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void creative_off_hand_assignment(GameTestHelper h) {
    assignment(h, GameType.CREATIVE, InteractionHand.OFF_HAND);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void survival_main_hand_assignment(GameTestHelper h) {
    assignment(h, GameType.SURVIVAL, InteractionHand.MAIN_HAND);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void survival_off_hand_assignment(GameTestHelper h) {
    assignment(h, GameType.SURVIVAL, InteractionHand.OFF_HAND);
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void creative_spawn_egg_worker_gets_home_then_assignment(GameTestHelper h) {
    Fixture f = setup(h, GameType.CREATIVE);
    try {
      f.worker.owner = null;
      f.worker.home = null;
      f.worker.homeId = null;
      select(h, f, InteractionHand.MAIN_HAND);
      h.assertTrue(f.player.getUUID().equals(f.worker.owner), "Spawned worker was not claimed");
      h.assertTrue(
          f.player.getMainHandItem().getTag().getBoolean("Rebind"),
          "Unhomed worker did not request a pedestal");
      click(h, f, InteractionHand.MAIN_HAND, f.home);
      h.assertTrue(
          f.home.identity.equals(f.worker.homeId) && f.home.workers.contains(f.worker.getUUID()),
          "Spawned worker did not acquire a home");
      h.assertTrue(
          !selected(f.player.getMainHandItem()), "Home assignment did not clear selection");
      select(h, f, InteractionHand.MAIN_HAND);
      h.assertTrue(
          !f.player.getMainHandItem().getTag().getBoolean("Rebind"),
          "Homed worker still requested a pedestal");
      click(h, f, InteractionHand.MAIN_HAND, f.totem);
      h.assertTrue(
          f.totem.workers.contains(f.worker.getUUID()), "Claimed worker did not bind to the totem");
    } finally {
      // Completed tests must not keep consuming the shared totem scan budget.
      f.worker.unassign();
      f.worker.discard();
    }
    h.succeed();
  }
}
