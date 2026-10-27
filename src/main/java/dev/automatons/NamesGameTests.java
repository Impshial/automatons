package dev.automatons;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(Automatons.ID)
@PrefixGameTestTemplate(false)
public final class NamesGameTests {
  private static Station home(GameTestHelper h, ServerPlayer owner, int x) {
    for (int a = 0; a < 24; a++)
      for (int z = 0; z < 12; z++) h.setBlock(new BlockPos(a, 0, z), Blocks.STONE);
    BlockPos at = h.absolutePos(new BlockPos(x, 1, 3));
    h.getLevel().setBlock(at, Automatons.PEDESTAL.get().defaultBlockState(), 3);
    Station s = (Station) h.getLevel().getBlockEntity(at);
    s.owner = owner.getUUID();
    s.registerHome();
    return s;
  }

  private static ServerPlayer owner(GameTestHelper h) {
    return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "NamesTest"));
  }

  private static void parts(Station s) {
    Item[] parts = {
      Automatons.HEAD.get(),
      Automatons.ARM.get(),
      Automatons.BODY.get(),
      Automatons.ARM.get(),
      Automatons.LEG.get(),
      Automatons.LEG.get()
    };
    for (int i = 0; i < 6; i++) s.inventory.setStackInSlot(i, new ItemStack(parts[i], 3));
  }

  private static Lumberton worker(ServerLevel level, Station home, BlockPos at) {
    Lumberton w = Automatons.LUMBERTON.get().create(level);
    w.setNoAi(true);
    w.owner = home.owner;
    w.home = home.getBlockPos();
    w.homeId = home.identity;
    w.homeDimension = home.getLevel().dimension().location().toString();
    w.moveTo(Vec3.atBottomCenterOf(at));
    level.addFreshEntity(w);
    WorkerIdentity.update(w, "Deployed");
    return w;
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void names_validate_catalog_canonicalization_and_exhaustion(GameTestHelper h) {
    List<String> pool = NameRegistry.catalog();
    h.assertTrue(pool.size() == 640, "Catalog incomplete");
    for (String[] pair :
        new String[][] {
          {"Seán", "Sean"},
          {"Se\u0061\u0301n", "SEÁN"},
          {"I", "İ"},
          {"I", "ı"},
          {"Łukasz", "Lukasz"},
          {"Seo-jun", "Seo jun"},
          {"D’Arcy", "darcy"},
          {"ØĐÐÆŒÞß", "oddaeoethss"}
        })
      h.assertTrue(
          NameRegistry.key(pair[0]).equals(NameRegistry.key(pair[1])),
          "Canonical mismatch " + Arrays.toString(pair));
    h.assertTrue(
        !NameRegistry.key("Giovanni").equals(NameRegistry.key("John")),
        "Translations incorrectly merged");
    NameRegistry names = new NameRegistry();
    RandomSource random = RandomSource.create(71);
    Set<String> assigned = new HashSet<>();
    for (int i = 0; i < 640; i++) {
      UUID id = UUID.randomUUID();
      String name = names.allocate(id, random, pool);
      h.assertTrue(
          pool.contains(name) && assigned.add(NameRegistry.key(name)),
          "Base name reused or early compound at " + i);
      h.assertTrue(names.allocate(id, random, pool).equals(name), "Identity allocated twice");
    }
    for (int i = 0; i < 25; i++) {
      String name = names.allocate(UUID.randomUUID(), random, pool);
      h.assertTrue(assigned.add(NameRegistry.key(name)) && !pool.contains(name), "Compound reused");
    }
    NameRegistry tiny = new NameRegistry();
    Set<String> small = new HashSet<>();
    for (int i = 0; i < 12; i++) {
      String name = tiny.allocate(UUID.randomUUID(), random, List.of("Amina", "Lorcan"));
      h.assertTrue(small.add(NameRegistry.key(name)), "Suffix reused");
      if (i >= 4) h.assertTrue(name.matches(".*-[0-9]+"), "Suffix fallback missing");
      tiny = NameRegistry.load(tiny.save());
    }
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void names_migrate_before_allocating_and_retire_renames(GameTestHelper h) {
    NameRegistry names = new NameRegistry();
    Map<UUID, CompoundTag> records = new HashMap<>();
    UUID first = new UUID(0, 1), second = new UUID(0, 2), unnamed = new UUID(0, 3);
    for (UUID id : List.of(first, second, unnamed)) {
      CompoundTag n = new CompoundTag();
      n.putUUID("Id", id);
      n.putInt("Job", 2);
      n.putInt("Torches", 13);
      records.put(id, n);
    }
    records.get(first).putString("Name", Component.Serializer.toJson(Component.literal("Seán")));
    records.get(second).putString("Name", Component.Serializer.toJson(Component.literal("Sean")));
    int[] counts = names.migrate(records, RandomSource.create(7));
    h.assertTrue(
        counts[1] == 1 && names.name(first).equals("Seán"), "Legacy tie was not deterministic");
    h.assertTrue(
        !NameRegistry.key(names.name(second)).equals("sean"), "Duplicate legacy name kept");
    h.assertTrue(
        records.get(unnamed).getInt("Torches") == 13 && records.get(unnamed).getInt("Job") == 2,
        "Migration changed resources/job");
    h.assertTrue(!names.rename(second, "SEÁN"), "Conflicting rename accepted");
    h.assertTrue(names.rename(first, "Sean"), "No-op canonical rename rejected");
    h.assertTrue(names.rename(first, "Unique Artisan"), "Unique rename rejected");
    h.assertTrue(!names.rename(second, "Seán"), "Retired name recycled");
    String stable = names.name(unnamed);
    names = NameRegistry.load(names.save());
    names.migrate(records, RandomSource.create(80));
    h.assertTrue(
        names.name(unnamed).equals(stable) && names.name(first).equals("Unique Artisan"),
        "Reload renamed worker");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 40)
  public static void names_assembly_only_commits_successful_outputs(GameTestHelper h) {
    ServerPlayer p = owner(h);
    Station a = home(h, p, 3), b = home(h, p, 10);
    parts(a);
    parts(b);
    p.moveTo(Vec3.atBottomCenterOf(a.getBlockPos().south()));
    Ledger ledger = Ledger.get(h.getLevel().getServer());
    int before = ledger.names.issuedCount();
    WorkMenu menu = new WorkMenu(1, p.getInventory(), a, null);
    menu.describe();
    menu.describe();
    h.assertTrue(ledger.names.issuedCount() == before, "Preview reserved a name");
    for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
    menu.quickMoveStack(p, 6);
    h.assertTrue(
        ledger.names.issuedCount() == before && a.inventory.getStackInSlot(0).getCount() == 3,
        "Failed shift craft reserved/consumed");
    p.getInventory().setItem(0, ItemStack.EMPTY);
    ItemStack first = menu.quickMoveStack(p, 6);
    h.assertTrue(
        !first.isEmpty() && ledger.names.issuedCount() == before + 1,
        "Craft did not allocate once");
    CompoundTag one = first.getTag().getCompound("Automaton");
    String name = WorkerNames.recordName(one);
    h.assertTrue(
        !name.isBlank() && name.equals(ledger.names.name(one.getUUID("Id"))),
        "Named output missing identity");
    menu.setCarried(new ItemStack(Items.DIRT));
    menu.clicked(6, 0, ClickType.PICKUP, p);
    h.assertTrue(ledger.names.issuedCount() == before + 1, "Occupied cursor reserved name");
    p.moveTo(Vec3.atBottomCenterOf(b.getBlockPos().south()));
    WorkMenu other = new WorkMenu(2, p.getInventory(), b, null);
    other.clicked(6, 0, ClickType.PICKUP, p);
    CompoundTag two = other.getCarried().getTag().getCompound("Automaton");
    h.assertTrue(
        !NameRegistry.key(name).equals(NameRegistry.key(WorkerNames.recordName(two)))
            && !one.getUUID("Id").equals(two.getUUID("Id")),
        "Two homes allocated same worker/name");
    h.assertTrue(first.getMaxStackSize() == 1, "Named items stack");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void names_home_toggles_cross_dimension_and_replacement_identity(GameTestHelper h) {
    ServerPlayer p = owner(h);
    Station a = home(h, p, 3), b = home(h, p, 10);
    Lumberton wa = worker(h.getLevel(), a, a.getBlockPos().south(2)),
        wb = worker(h.getLevel(), b, b.getBlockPos().south(2));
    wa.setJob(1);
    wb.setJob(2);
    BlockPos shared = a.getBlockPos().offset(4, 0, 5);
    h.getLevel().setBlock(shared, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station totem = (Station) h.getLevel().getBlockEntity(shared);
    totem.owner = p.getUUID();
    wa.assign(totem);
    wb.assign(totem);
    ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
    Lumberton remote = worker(nether, a, new BlockPos(0, 80, 0));
    String name = wa.getDisplayName().getString();
    p.moveTo(Vec3.atBottomCenterOf(a.getBlockPos().south()));
    WorkMenu menu = new WorkMenu(1, p.getInventory(), a, null);
    boolean accepted = menu.clickMenuButton(p, 15);
    h.assertTrue(accepted && !a.showNames, "Home toggle rejected");
    h.runAtTickTime(
        25,
        () -> {
          // The Nether fixture is in an unloaded chunk; its next load resolves the saved home
          // cache.
          WorkerNames.sync(remote);
          h.assertTrue(
              !wa.showNames() && !remote.showNames() && wb.showNames(),
              "Home toggle: local="
                  + wa.showNames()
                  + " remote="
                  + remote.showNames()
                  + " other="
                  + wb.showNames());
          var stranger = owner(h);
          stranger.moveTo(p.position());
          WorkMenu foreign = new WorkMenu(2, stranger.getInventory(), a, null);
          h.assertTrue(
              !foreign.clickMenuButton(stranger, 15) && !a.showNames,
              "Foreign home toggle accepted");
          CompoundTag saved = wa.saveWithoutId(new CompoundTag());
          Lumberton loaded = Automatons.LUMBERTON.get().create(h.getLevel());
          loaded.load(saved);
          WorkerNames.sync(loaded);
          h.assertTrue(
              !loaded.showNames() && loaded.getDisplayName().getString().equals(name),
              "Reload lost visibility/name");
          Station copy = new Station(a.getBlockPos(), a.getBlockState());
          copy.load(a.saveWithoutMetadata());
          h.assertTrue(!copy.showNames, "Home setting not saved");
          a.destroyed();
          Station replacement = new Station(a.getBlockPos(), a.getBlockState());
          replacement.setLevel(h.getLevel());
          replacement.owner = p.getUUID();
          replacement.registerHome();
          WorkerNames.sync(wa);
          h.assertTrue(
              !wa.showNames() && !replacement.identity.equals(a.identity),
              "Replacement seized old visibility");
          h.assertTrue(
              wa.rebind(replacement).equals("Home rebound") && wa.showNames(),
              "Rebind did not adopt new home visibility");
          remote.discard();
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void names_tag_conflicts_keep_item_and_roster_hides_ids(GameTestHelper h) {
    ServerPlayer p = owner(h);
    Station a = home(h, p, 3);
    Lumberton w = worker(h.getLevel(), a, a.getBlockPos().south(2)),
        other = worker(h.getLevel(), a, a.getBlockPos().east(2));
    String old = w.getDisplayName().getString();
    ItemStack tag = new ItemStack(Items.NAME_TAG);
    tag.setHoverName(other.getDisplayName());
    p.setItemInHand(InteractionHand.MAIN_HAND, tag);
    WorkerNames.nameTag(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, w));
    h.assertTrue(
        tag.getCount() == 1 && w.getDisplayName().getString().equals(old),
        "Conflicting tag consumed or renamed");
    tag.setHoverName(Component.literal("Élodie of the Northern Mine " + h.getTick()));
    WorkerNames.nameTag(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, w));
    h.assertTrue(
        tag.isEmpty() && !w.getDisplayName().getString().equals(old),
        "Unique name tag not applied");
    BlockPos at = a.getBlockPos().east(5);
    h.getLevel().setBlock(at, Automatons.TOTEM.get().defaultBlockState(), 3);
    Station totem = (Station) h.getLevel().getBlockEntity(at);
    totem.owner = p.getUUID();
    w.assign(totem);
    other.assign(totem);
    p.moveTo(Vec3.atBottomCenterOf(at.south()));
    WorkMenu menu = new WorkMenu(1, p.getInventory(), totem, null);
    menu.selectedWorker = w.getUUID();
    CompoundTag view = menu.describe();
    h.assertTrue(
        view.getList("Details", 8).getString(0).equals(w.getDisplayName().getString()),
        "Detail identity still raw ID");
    for (Tag t : view.getList("Members", 10))
      h.assertTrue(!WorkerNames.recordName((CompoundTag) t).isBlank(), "Unnamed roster row");
    a.showNames(false);
    h.assertTrue(
        menu.describe().getList("Details", 8).getString(0).equals(w.getDisplayName().getString()),
        "Toggle hid menu name");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 50)
  public static void names_packing_deployment_and_legacy_item_keep_identity(GameTestHelper h) {
    ServerPlayer p = owner(h);
    Station a = home(h, p, 3);
    Lumberton w = worker(h.getLevel(), a, a.getBlockPos().south(2));
    w.setJob(2);
    w.cargo.setStackInSlot(0, new ItemStack(Items.TORCH, 13));
    a.showNames(false);
    UUID id = w.getUUID();
    String name = w.getDisplayName().getString();
    int issued = Ledger.get(h.getLevel().getServer()).names.issuedCount();
    h.assertTrue(WorkerIdentity.pack(w, p), "Pack failed");
    ItemEntity drop =
        h.getLevel().getEntitiesOfClass(ItemEntity.class, w.getBoundingBox().inflate(2)).stream()
            .filter(e -> e.getItem().is(Automatons.AUTOMATON_ITEM.get()))
            .findFirst()
            .orElseThrow();
    ItemStack packed = drop.getItem().copy();
    drop.discard();
    p.setItemInHand(InteractionHand.MAIN_HAND, packed);
    BlockPos ground = a.getBlockPos().south(4).below();
    UseOnContext use =
        new UseOnContext(
            p,
            InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false));
    h.assertTrue(packed.useOn(use).consumesAction(), "Redeployment failed");
    Lumberton deployed = (Lumberton) h.getLevel().getEntity(id);
    h.assertTrue(
        deployed != null
            && deployed.getDisplayName().getString().equals(name)
            && !deployed.showNames()
            && deployed.job() == 2,
        "Redeploy changed name/home visibility/job");
    h.assertTrue(
        Ledger.get(h.getLevel().getServer()).names.issuedCount() == issued,
        "Redeploy consumed another name");
    CompoundTag legacy = new CompoundTag();
    legacy.putInt("Version", 2);
    legacy.putUUID("Id", UUID.randomUUID());
    legacy.putUUID("Owner", p.getUUID());
    legacy.putString("Name", Component.Serializer.toJson(Component.literal(name)));
    legacy.putFloat("Health", 20);
    ItemStack old = WorkerIdentity.item(legacy);
    old.getItem().inventoryTick(old, h.getLevel(), p, 0, false);
    h.assertTrue(
        !NameRegistry.key(name)
            .equals(
                NameRegistry.key(WorkerNames.recordName(old.getTag().getCompound("Automaton")))),
        "Legacy item bypassed uniqueness");
    h.succeed();
  }
}
