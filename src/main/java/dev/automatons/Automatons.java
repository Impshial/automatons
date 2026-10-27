package dev.automatons;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(Automatons.ID)
public final class Automatons {
  public static final String ID = "automatons";

  public static ResourceLocation id(String name) {
    return new ResourceLocation(ID, name);
  }

  public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>>
      FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, ID);
  public static final RegistryObject<LumiteFeature> LUMITE_FEATURE =
      FEATURES.register("lumite", LumiteFeature::new);
  public static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
  public static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(ForgeRegistries.ITEMS, ID);
  public static final DeferredRegister<EntityType<?>> ENTITIES =
      DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
  public static final DeferredRegister<BlockEntityType<?>> BES =
      DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(ForgeRegistries.MENU_TYPES, ID);
  public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
      DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ID);
  public static final DeferredRegister<RecipeType<?>> RECIPES =
      DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ID);
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
  public static final RegistryObject<Block> PEDESTAL =
      BLOCKS.register("pedestal", () -> new StationBlock(false));
  public static final RegistryObject<Block> TOTEM =
      BLOCKS.register("totem", () -> new StationBlock(true));
  public static final RegistryObject<Block> INFUSER =
      BLOCKS.register("infusing_pedestal", InfuserBlock::new);
  public static final RegistryObject<BlockEntityType<Infuser>> INFUSER_BE =
      BES.register(
          "infuser", () -> BlockEntityType.Builder.of(Infuser::new, INFUSER.get()).build(null));
  public static final RegistryObject<Item> AUTOMATON_ITEM =
      ITEMS.register("automaton", AutomatonItem::new);
  public static final RegistryObject<Item> EMPTY_CORE =
      ITEMS.register(LegacyMagic.EMPTY_CORE_ID, CoreItem::new);
  public static final RegistryObject<Item> LUMINA_CORE =
      ITEMS.register(LegacyMagic.CORE_ID, () -> new Item(new Item.Properties()));
  public static final RegistryObject<Block> LUMITE_ORE =
      BLOCKS.register(
          "lumite_ore",
          () ->
              new net.minecraft.world.level.block.DropExperienceBlock(
                  net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                          net.minecraft.world.level.block.Blocks.IRON_ORE)
                      .lightLevel(s -> 0)));
  public static final RegistryObject<Block> DEEPSLATE_LUMITE_ORE =
      BLOCKS.register(
          "deepslate_lumite_ore",
          () ->
              new net.minecraft.world.level.block.DropExperienceBlock(
                  net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(
                          net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE)
                      .lightLevel(s -> 0)));
  public static final RegistryObject<Item> LUMITE_SHARD =
      ITEMS.register("lumite_shard", LumiteShardItem::new);
  public static final RegistryObject<Item> INFUSED_LUMITE =
      ITEMS.register("infused_lumite_shard", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> HEAD =
      ITEMS.register("automaton_head", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> BODY =
      ITEMS.register("automaton_core", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> ARM =
      ITEMS.register("automaton_arm", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> LEG =
      ITEMS.register("automaton_leg", () -> new Item(new Item.Properties()));
  public static final RegistryObject<Item> PLATE =
      ITEMS.register("upgrade_plate", () -> new Item(new Item.Properties()));
  public static final RegistryObject<EntityType<LuminaOrb>> LUMINA_ORB =
      ENTITIES.register(
          LegacyMagic.ORB_ID,
          () ->
              EntityType.Builder.<LuminaOrb>of(LuminaOrb::new, MobCategory.MISC)
                  .sized(.5f, .5f)
                  .clientTrackingRange(6)
                  .updateInterval(20)
                  .build(LegacyMagic.ORB_ID));
  public static final RegistryObject<Item> WAND = ITEMS.register("wand", WandItem::new);
  public static final RegistryObject<Item> HAMMER = ITEMS.register("hammer", HammerItem::new);
  public static final RegistryObject<Item> HANDBOOK =
      ITEMS.register("automaton_handbook", HandbookItem::new);
  public static final RegistryObject<Item> COAL_PIECE =
      ITEMS.register("coal_piece", () -> new FuelPieceItem(Items.COAL));
  public static final RegistryObject<Item> CHARCOAL_PIECE =
      ITEMS.register("charcoal_piece", () -> new FuelPieceItem(Items.CHARCOAL));
  public static final Map<String, RegistryObject<Item>> UPGRADES = new LinkedHashMap<>();
  public static final RegistryObject<EntityType<Lumberton>> LUMBERTON =
      ENTITIES.register(
          "lumberton",
          () ->
              EntityType.Builder.of(Lumberton::new, MobCategory.CREATURE)
                  .sized(.7f, 1.625f)
                  .clientTrackingRange(10)
                  .build("lumberton"));
  public static final RegistryObject<EntityType<Timber>> TIMBER =
      ENTITIES.register(
          "fallen_timber",
          () ->
              EntityType.Builder.<Timber>of(Timber::new, MobCategory.MISC)
                  .sized(1f, 1f)
                  .clientTrackingRange(12)
                  .updateInterval(5)
                  .build("fallen_timber"));
  public static final RegistryObject<BlockEntityType<Station>> STATION =
      BES.register(
          "station",
          () -> BlockEntityType.Builder.of(Station::new, PEDESTAL.get(), TOTEM.get()).build(null));
  public static final RegistryObject<MenuType<WorkMenu>> MENU =
      MENUS.register("work", () -> IForgeMenuType.create(WorkMenu::new));
  public static final RegistryObject<RecipeType<CreationRecipe>> CREATION =
      RECIPES.register(
          "creation",
          () ->
              new RecipeType<>() {
                public String toString() {
                  return "automatons:creation";
                }
              });
  public static final RegistryObject<RecipeSerializer<CreationRecipe>> CREATION_SERIALIZER =
      SERIALIZERS.register("creation", CreationRecipe.Serializer::new);

  public static boolean retired(Item item) {
    return item instanceof UpgradeItem || item == PLATE.get();
  }

  static {
    ITEMS.register("lumite_ore", () -> new BlockItem(LUMITE_ORE.get(), new Item.Properties()));
    ITEMS.register(
        "deepslate_lumite_ore",
        () -> new BlockItem(DEEPSLATE_LUMITE_ORE.get(), new Item.Properties()));
    ITEMS.register("infusing_pedestal", () -> new BlockItem(INFUSER.get(), new Item.Properties()));
    ITEMS.register("pedestal", () -> new BlockItem(PEDESTAL.get(), new Item.Properties()));
    ITEMS.register("totem", () -> new BlockItem(TOTEM.get(), new Item.Properties()));
    for (String family : List.of("storage", "lumber", "speed", "efficiency", "mining"))
      for (int level = 1; level <= 2; level++) {
        int tier = level;
        UPGRADES.put(
            family + "_" + tier,
            ITEMS.register(family + "_" + tier, () -> new UpgradeItem(family, tier)));
      }
    ITEMS.register(
        "lumberton_test_egg",
        () ->
            new net.minecraftforge.common.ForgeSpawnEggItem(
                LUMBERTON, 0x886137, 0xe8aa26, new Item.Properties()));
    TABS.register(
        "automatons",
        () ->
            CreativeModeTab.builder()
                .title(Component.literal("Automatons"))
                .icon(() -> new ItemStack(WAND.get()))
                .displayItems(
                    (p, o) ->
                        ITEMS.getEntries().stream()
                            .filter(i -> !retired(i.get()))
                            .forEach(i -> o.accept(i.get())))
                .build());
  }

  public Automatons() {
    IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
    Luminall.register(bus);
    MachineParts.register();
    FEATURES.register(bus);
    BLOCKS.register(bus);
    ITEMS.register(bus);
    ENTITIES.register(bus);
    BES.register(bus);
    MENUS.register(bus);
    SERIALIZERS.register(bus);
    RECIPES.register(bus);
    TABS.register(bus);
    bus.addListener(this::attributes);
    ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Balance.SPEC);
    MinecraftForge.EVENT_BUS.register(WorldEvents.class);
    MinecraftForge.EVENT_BUS.register(WorkerNames.class);
    MinecraftForge.EVENT_BUS.register(LuminaHarvest.class);
    MinecraftForge.EVENT_BUS.register(PlayerFelling.class);
    Network.init();
  }

  private void attributes(EntityAttributeCreationEvent e) {
    e.put(LUMBERTON.get(), Lumberton.attributes().build());
  }
}
