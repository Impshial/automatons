package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class LuminaGameTests {
  private static ServerPlayer player(GameTestHelper h, String name, BlockPos pos) {
    var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    p.moveTo(Vec3.atBottomCenterOf(pos));
    p.setOnGround(true);
    p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    return p;
  }

  private static Infuser infuser(GameTestHelper h, BlockPos local) {
    h.setBlock(local, Automatons.INFUSER.get());
    return (Infuser) h.getLevel().getBlockEntity(h.absolutePos(local));
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void four_corner_exact_thirds_and_shared_rate(GameTestHelper h) {
    for (int count = 1; count <= 4; count++) {
      Infuser inf = infuser(h, new BlockPos(5 + count * 2, 1, 5));
      var owner = player(h, "Corners" + count, inf.getBlockPos().south());
      Lumina.addThirds(owner, count * 75L + 2);
      for (int n = 0; n < count; n++) {
        inf.shards[n] = new ItemStack(Automatons.LUMITE_SHARD.get());
        inf.donors[n] = owner.getUUID();
      }
      for (int t = 0; t < 4; t++) inf.advance(id -> owner);
      int progress = Arrays.stream(inf.shards).mapToInt(LumiteShardItem::paid).sum();
      h.assertTrue(progress == 3, "More than one point per interval");
      if (count >= 3)
        h.assertTrue(
            LumiteShardItem.paid(inf.shards[0]) == 1 && LumiteShardItem.paid(inf.shards[1]) == 1,
            "Corner monopolized budget");
      for (int t = 4; t < count * 100; t++) inf.advance(id -> owner);
      for (int n = 0; n < count; n++)
        h.assertTrue(inf.shards[n].is(Automatons.INFUSED_LUMITE.get()), "Incomplete batch");
      for (int n = 0; n < count; n++)
        h.assertTrue(
            ItemStack.isSameItemSameTags(
                inf.shards[n], new ItemStack(Automatons.INFUSED_LUMITE.get())),
            "Completed infusion left empty progress metadata that prevents normal stacking");
      h.assertTrue(Lumina.thirds(owner) == 2, "Batch rounding changed cost or remainder");
      for (int t = 0; t < 40; t++) inf.advance(id -> owner);
      h.assertTrue(Lumina.thirds(owner) == 2, "Complete shards kept draining");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void shard_ownership_partial_retrieval_reload_and_near_zero(GameTestHelper h) {
    Infuser inf = infuser(h, new BlockPos(8, 1, 8));
    ServerPlayer a = player(h, "ShardA", inf.getBlockPos().south()),
        b = player(h, "ShardB", inf.getBlockPos().east()),
        stranger = player(h, "Bystander", inf.getBlockPos().west());
    Lumina.addThirds(a, 76);
    Lumina.addThirds(b, 1);
    Lumina.add(stranger, 200);
    for (int i = 0; i < 2; i++) {
      inf.shards[i] = new ItemStack(Automatons.LUMITE_SHARD.get());
      inf.donors[i] = i == 0 ? a.getUUID() : b.getUUID();
    }
    for (int t = 0; t < 4; t++) inf.advance(id -> id.equals(a.getUUID()) ? a : b);
    h.assertTrue(
        Lumina.thirds(b) == 0 && LumiteShardItem.paid(inf.shards[1]) == 1,
        "Fractional last energy lost");
    var hit =
        new BlockHitResult(
            Vec3.atLowerCornerOf(inf.getBlockPos()).add(.75, 1, .25),
            Direction.UP,
            inf.getBlockPos(),
            false);
    inf.getBlockState().use(h.getLevel(), stranger, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(!inf.shards[1].isEmpty(), "Bystander withdrew another owner's shard");
    inf.getBlockState().use(h.getLevel(), b, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        inf.shards[1].isEmpty()
            && LumiteShardItem.paid(b.getMainHandItem()) == 1
            && b.getMainHandItem().getMaxStackSize() == 1,
        "Partial retrieval spread or lost progress");
    inf.getBlockState().use(h.getLevel(), b, InteractionHand.MAIN_HAND, hit);
    h.assertTrue(
        b.getMainHandItem().isEmpty() && LumiteShardItem.paid(inf.shards[1]) == 1,
        "Partial reinsertion changed ownership");
    CompoundTag saved = inf.saveWithoutMetadata();
    Infuser copy = new Infuser(inf.getBlockPos(), inf.getBlockState());
    copy.setLevel(h.getLevel());
    copy.load(saved);
    h.assertTrue(
        copy.donors[1].equals(b.getUUID()) && LumiteShardItem.paid(copy.shards[1]) == 1,
        "Corner reload lost progress");
    a.moveTo(inf.shardPosition(0).add(10, 0, 0));
    long before = Lumina.thirds(a);
    for (int t = 0; t < 40; t++) copy.advance(id -> id.equals(a.getUUID()) ? a : null);
    h.assertTrue(
        Lumina.thirds(a) == before && Lumina.balance(stranger) == 200,
        "Absent owner or bystander drained");
    a.moveTo(inf.shardPosition(0));
    for (int t = 0; t < 100; t++) copy.advance(id -> id.equals(a.getUUID()) ? a : null);
    h.assertTrue(
        copy.shards[0].is(Automatons.INFUSED_LUMITE.get()) && Lumina.thirds(a) == 1,
        "Resumed partial was charged twice");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void all_prongs_targeted_full_pedestal_break_and_no_overwrite(GameTestHelper h) {
    Infuser inf = infuser(h, new BlockPos(8, 1, 8));
    var p = player(h, "Prongs", inf.getBlockPos().south());
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Automatons.LUMITE_SHARD.get(), 8));
    for (int i = 0; i < 4; i++) {
      var hit =
          new BlockHitResult(
              inf.shardPosition(i).subtract(0, .17, 0), Direction.UP, inf.getBlockPos(), false);
      h.assertTrue(InfuserBlock.corner(hit, p) == i, "Corner hit selected a different prong");
      inf.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
      inf.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    }
    h.assertTrue(
        p.getMainHandItem().getCount() == 4, "Occupied corner consumed or overwrote a shard");
    for (int i = 0; i < 4; i++) {
      inf.shards[i] = LumiteShardItem.charge(inf.shards[i], i + 1);
      h.assertTrue(inf.shards[i].getCount() == 1, "Stack accepted in a corner");
    }
    for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    var hit = new BlockHitResult(inf.shardPosition(0), Direction.UP, inf.getBlockPos(), false);
    inf.getBlockState().use(h.getLevel(), p, InteractionHand.OFF_HAND, hit);
    h.assertTrue(
        LumiteShardItem.paid(p.getOffhandItem()) == 1 && inf.shards[0].isEmpty(),
        "Full inventory prevented safe empty-offhand retrieval");
    inf.getBlockState().use(h.getLevel(), p, InteractionHand.OFF_HAND, hit);
    h.assertTrue(
        p.getOffhandItem().isEmpty() && LumiteShardItem.paid(inf.shards[0]) == 1,
        "Full inventory partial reinsertion duplicated or lost credit");
    for (int i = 0; i < 36; i++)
      h.assertTrue(
          p.getInventory().getItem(i).getCount() == 64,
          "Corner retrieval overwrote an occupied inventory slot");
    h.getLevel().destroyBlock(inf.getBlockPos(), false);
    int count = 0, paid = 0;
    for (var e :
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(inf.getBlockPos()).inflate(2))) {
      if (e.getItem().is(Automatons.LUMITE_SHARD.get())) {
        count += e.getItem().getCount();
        paid += LumiteShardItem.paid(e.getItem());
      }
    }
    h.assertTrue(
        count == 4 && paid == 10, "Pedestal removal duplicated/lost contents or paid energy");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void legacy_balances_cores_and_upgrades_recover_once(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(8, 1, 8));
    var p = player(h, "Legacy", pos);
    Lumina.addThirds(p, 31);
    Ledger restored = Ledger.load(Ledger.get(h.getLevel().getServer()).save(new CompoundTag()));
    h.assertTrue(
        restored.lumina.get(p.getUUID()) == 10 && restored.luminaThirds.get(p.getUUID()) == 1,
        "Fractional legacy balance lost");
    CompoundTag old = new CompoundTag();
    old.putInt("Version", 2);
    old.putUUID("Donor", p.getUUID());
    old.put("Core", CoreItem.vessel(37).save(new CompoundTag()));
    Infuser inf = infuser(h, new BlockPos(8, 1, 8));
    inf.load(old);
    inf.load(inf.saveWithoutMetadata());
    h.assertTrue(
        CoreItem.charge(inf.recovery) == 37
            && inf.recovery.getTag().getUUID("CreditOwner").equals(p.getUUID()),
        "Legacy paid core lost");
    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    inf.getBlockState().use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
    p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
    p.getMainHandItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
    h.assertTrue(
        Lumina.thirds(p) == 142
            && CoreItem.charge(p.getMainHandItem()) == 0
            && inf.recovery.isEmpty(),
        "Legacy credit duplicated or discarded");
    h.assertTrue(
        ItemStack.isSameItemSameTags(
            p.getMainHandItem(), new ItemStack(Automatons.EMPTY_CORE.get())),
        "Recovered Empty Core cannot stack or satisfy its uncharged crafting ingredient");
    old.remove("Donor");
    inf.load(old);
    h.assertTrue(
        CoreItem.charge(inf.recovery) == 37 && !inf.recovery.getTag().hasUUID("CreditOwner"),
        "Orphan credit invented an owner");
    h.setBlock(new BlockPos(10, 1, 8), Automatons.TOTEM.get());
    Station totem = (Station) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(10, 1, 8)));
    totem.owner = p.getUUID();
    int base = totem.workRange();
    for (var item : Automatons.UPGRADES.values()) {
      ItemStack stack = new ItemStack(item.get());
      totem.inventory.setStackInSlot(0, stack);
      h.assertTrue(
          totem.tier(((UpgradeItem) item.get()).family) == 0 && totem.workRange() == base,
          "Legacy upgrade remained active");
      h.assertTrue(
          !totem.inventory.insertItem(1, stack.copy(), false).isEmpty(),
          "Automation installed retired upgrade");
    }
    for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    totem.recoverRetired(p);
    totem.recoverRetired(p);
    h.assertTrue(totem.inventory.getStackInSlot(0).isEmpty(), "Retired upgrade stranded");
    long dropped =
        h
            .getLevel()
            .getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(3))
            .stream()
            .filter(e -> e.getItem().getItem() instanceof UpgradeItem)
            .count();
    h.assertTrue(dropped == 1, "Full-inventory recovery duplicated or lost old upgrade");
    WorkMenu menu = new WorkMenu(1, p.getInventory(), totem, null);
    h.assertTrue(menu.ownSlots == 0 && menu.slots.size() == 36, "Retired GUI slots remain");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void ore_loot_uniform_fortune_silk_and_zero_lumina(GameTestHelper h) {
    BlockPos pos = h.absolutePos(new BlockPos(8, 1, 8));
    var p = player(h, "OreLoot", pos.south());
    for (Block ore : List.of(Automatons.LUMITE_ORE.get(), Automatons.DEEPSLATE_LUMITE_ORE.get())) {
      var state = ore.defaultBlockState();
      h.getLevel().setBlock(pos, state, 3);
      h.assertTrue(state.getLightEmission(h.getLevel(), pos) == 0, "Ore emits world light");
      h.assertTrue(
          !MiningRules.capable(new ItemStack(Items.WOODEN_PICKAXE), state)
              && MiningRules.capable(new ItemStack(Items.STONE_PICKAXE), state),
          "Ore tier requirement incorrect");
      int[] counts = new int[4];
      int total = 0, fortuneTotal = 0;
      ItemStack pick = new ItemStack(Items.STONE_PICKAXE),
          fortune = new ItemStack(Items.DIAMOND_PICKAXE);
      fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
      h.getLevel().random.setSeed(60241);
      for (int roll = 0; roll < 600; roll++) {
        var drops = Block.getDrops(state, h.getLevel(), pos, null, p, pick);
        h.assertTrue(
            drops.size() == 1 && drops.get(0).is(Automatons.LUMITE_SHARD.get()),
            "Ordinary ore loot incorrect");
        int count = drops.get(0).getCount();
        h.assertTrue(count >= 1 && count <= 3, "Raw drop outside 1..3");
        counts[count]++;
        total += count;
        fortuneTotal +=
            Block.getDrops(state, h.getLevel(), pos, null, p, fortune).stream()
                .mapToInt(ItemStack::getCount)
                .sum();
      }
      for (int n = 1; n <= 3; n++)
        h.assertTrue(counts[n] > 140 && counts[n] < 260, "Raw drop distribution biased");
      h.assertTrue(fortuneTotal > total, "Fortune did not improve yield");
      fortune.enchant(Enchantments.SILK_TOUCH, 1);
      var silk = Block.getDrops(state, h.getLevel(), pos, null, p, fortune);
      h.assertTrue(
          silk.size() == 1 && silk.get(0).is(ore.asItem()) && silk.get(0).getCount() == 1,
          "Silk Touch did not take precedence");
      h.assertTrue(
          LuminaHarvest.category(h.getLevel(), pos, state).isEmpty(),
          "Ore introduced a resource-to-energy loop");
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void exact_recipes_bootstrap_and_retirement(GameTestHelper h) {
    var p = player(h, "Recipes", h.absolutePos(new BlockPos(8, 1, 8)));
    var menu = new ChestMenu(MenuType.GENERIC_9x1, 1, p.getInventory(), new SimpleContainer(9), 1);
    for (var recipe :
        h.getLevel()
            .getRecipeManager()
            .getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
      if (!recipe.getId().getNamespace().equals(Automatons.ID)) continue;
      h.assertTrue(
          !Automatons.retired(recipe.getResultItem(h.getLevel().registryAccess()).getItem()),
          "Retired item remains craftable");
      for (var ingredient : recipe.getIngredients())
        h.assertTrue(
            !ingredient.test(new ItemStack(Items.AMETHYST_SHARD)), "Amethyst still accepted");
    }
    var core =
        h.getLevel().getRecipeManager().byKey(Automatons.id(LegacyMagic.CORE_ID)).orElseThrow();
    var grid = new TransientCraftingContainer(menu, 3, 3);
    for (int i : new int[] {1, 3, 5, 7})
      grid.setItem(i, new ItemStack(Automatons.INFUSED_LUMITE.get()));
    grid.setItem(4, new ItemStack(Automatons.EMPTY_CORE.get()));
    var crafting = (net.minecraft.world.item.crafting.CraftingRecipe) core;
    h.assertTrue(
        crafting.matches(grid, h.getLevel())
            && crafting
                .assemble(grid, h.getLevel().registryAccess())
                .is(Automatons.LUMINA_CORE.get()),
        "Four-shard core recipe failed");
    grid.setItem(1, LumiteShardItem.charge(new ItemStack(Automatons.LUMITE_SHARD.get()), 74));
    h.assertTrue(
        !crafting.matches(grid, h.getLevel()), "Partial raw shard satisfied infused ingredient");
    var inf =
        h.getLevel().getRecipeManager().byKey(Automatons.id("infusing_pedestal")).orElseThrow();
    h.assertTrue(
        inf.getIngredients().stream().filter(i -> i.test(new ItemStack(Items.DIAMOND))).count()
            == 1,
        "First pedestal does not require exactly one diamond");
    h.assertTrue(
        inf.getIngredients().stream()
            .noneMatch(
                i ->
                    i.test(new ItemStack(Automatons.LUMINA_CORE.get()))
                        || i.test(new ItemStack(Automatons.INFUSED_LUMITE.get()))),
        "Bootstrap dependency cycle");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void hammer_survival_mining_progress_harvest_wear_and_modifiers(GameTestHelper h) {
    var pos = h.absolutePos(new BlockPos(8, 1, 8));
    var p = player(h, "HammerMiner", pos.south());
    var state = Blocks.STONE.defaultBlockState();
    ItemStack hammer = new ItemStack(Automatons.HAMMER.get()),
        wood = new ItemStack(Items.WOODEN_PICKAXE);
    h.assertTrue(
        hammer.getMaxDamage() == 2000 && !Inventories.tool(2, hammer) && Inventories.tool(4, hammer),
        "Hammer changed capacity or worker profession");
    p.setItemInHand(InteractionHand.MAIN_HAND, wood);
    int woodTicks = (int) Math.ceil(1 / state.getDestroyProgress(p, h.getLevel(), pos));
    p.setItemInHand(InteractionHand.MAIN_HAND, hammer);
    int hammerTicks = (int) Math.ceil(1 / state.getDestroyProgress(p, h.getLevel(), pos));
    float baseProgress = state.getDestroyProgress(p, h.getLevel(), pos);
    p.addEffect(
        new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DIG_SPEED, 200, 0));
    h.assertTrue(
        state.getDestroyProgress(p, h.getLevel(), pos) > baseProgress, "Hammer ignores Haste");
    p.removeEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED);
    p.addEffect(
        new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 200, 0));
    h.assertTrue(
        state.getDestroyProgress(p, h.getLevel(), pos) < baseProgress,
        "Hammer ignores Mining Fatigue");
    p.removeEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
    h.assertTrue(
        hammerTicks > woodTicks && hammerTicks < 1000,
        "Hammer not slower than wood or has zero progress");
    h.getLevel().setBlock(pos, state, 3);
    var start =
        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
            .START_DESTROY_BLOCK;
    var stop =
        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
    p.gameMode.handleBlockBreakAction(
        pos, start, Direction.UP, h.getLevel().getMaxBuildHeight(), 1);
    for (int i = 0; i < woodTicks; i++) p.gameMode.tick();
    h.assertTrue(
        !h.getLevel().getBlockState(pos).isAir(), "Hammer finished as quickly as wooden pick");
    p.gameMode.handleBlockBreakAction(pos, stop, Direction.UP, h.getLevel().getMaxBuildHeight(), 2);
    for (int i = woodTicks; i <= hammerTicks + 2; i++) p.gameMode.tick();
    h.assertTrue(
        h.getLevel().getBlockState(pos).isAir() && hammer.getDamageValue() == 1,
        "Normal Hammer mining failed or double-charged wear");
    for (var e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      e.discard();
    for (Block ore :
        List.of(
            Blocks.IRON_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Automatons.LUMITE_ORE.get(),
            Automatons.DEEPSLATE_LUMITE_ORE.get(),
            Blocks.DIAMOND_ORE)) {
      h.getLevel().setBlock(pos, ore.defaultBlockState(), 3);
      h.assertTrue(
          !hammer.isCorrectToolForDrops(ore.defaultBlockState()),
          "Hammer harvest tier exceeds wood");
      h.assertTrue(p.gameMode.destroyBlock(pos), "Hammer cannot break ordinary higher-tier ore");
      h.assertTrue(
          h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).isEmpty()
              && h.getLevel()
                  .getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(3))
                  .isEmpty(),
          "Higher-tier Hammer harvest awarded drops/XP");
    }
    h.getLevel().setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
    h.assertTrue(
        Blocks.BEDROCK.defaultBlockState().getDestroyProgress(p, h.getLevel(), pos) == 0,
        "Hammer breaks bedrock");
    System.out.println(
        "LUMINA_HAMMER stone woodenTicks="
            + woodTicks
            + " hammerTicks="
            + hammerTicks
            + " durability="
            + hammer.getMaxDamage());
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void deterministic_generation_height_boundary_and_chunk_receipts(GameTestHelper h) {
    var cfg = LumiteFeature.configured(h.getLevel().registryAccess());
    h.assertTrue(
        cfg.minimum() == -32
            && cfg.maximum() == 64
            && cfg.attempts() == 8
            && cfg.ore().size == 8
            && cfg.ore().discardChanceOnAirExposure == 0,
        "Initial generation settings differ");
    int[] bands = new int[7];
    long sampled = 0;
    for (long seed : new long[] {4217, 8675309, -60241024})
      for (int x = 0; x < 20; x++)
        for (int z = 0; z < 20; z++) {
          var chunk = new ChunkPos(x - 10, z - 10);
          List<BlockPos> a = LumiteFeature.positions(seed, chunk, cfg),
              b = LumiteFeature.positions(seed, chunk, cfg);
          h.assertTrue(a.equals(b), "Generation rerolls on resume");
          for (BlockPos p : a) {
            h.assertTrue(
                p.getY() >= -32 && p.getY() <= 64 && new ChunkPos(p).equals(chunk),
                "Ore extent escapes height/scope");
            bands[Math.min(6, (p.getY() + 32) / 16)]++;
            sampled++;
          }
        }
    h.assertTrue(
        bands[2] + bands[3] > bands[0] + bands[5], "Vertical distribution does not favor middle");
    LumiteChunkData data = new LumiteChunkData();
    data.normal = true;
    data.job = UUID.randomUUID();
    data.next = 12;
    data.completed = true;
    data.playerPlaced.add(123L);
    LumiteChunkData copy = new LumiteChunkData();
    copy.deserializeNBT(data.serializeNBT());
    h.assertTrue(
        copy.normal
            && copy.completed
            && copy.job.equals(data.job)
            && copy.next == 12
            && copy.playerPlaced.contains(123L),
        "Chunk provenance/progress not persistent");
    System.out.println(
        "LUMITE_CANDIDATES chunks=1200 candidates="
            + sampled
            + " heightBands="
            + Arrays.toString(bands));
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void retired_commands_absent_and_history_preserved(GameTestHelper h) {
    var history = new CompoundTag();
    history.putString("State", "RUNNING");
    history.putUUID("Id", UUID.randomUUID());
    history.putBoolean("Completed", false);
    history.putLongArray("UnknownFutureData", new long[] {42L, 117L});
    var read = LumiteRetrogen.load(history);
    h.assertTrue(read.save(new CompoundTag()).equals(history), "Pending history was altered");
    history.putBoolean("Completed", true);
    h.assertTrue(LumiteRetrogen.load(history).save(new CompoundTag()).equals(history), "Completed history was altered");
    var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot();
    h.assertTrue(root.getChild("automaton") == null && root.getChild("automatons") == null, "Retired command is still registered");
    h.assertTrue(root.getChild("give") != null && root.getChild("reload") != null, "Unrelated vanilla commands removed");
    h.succeed();
  }
}
