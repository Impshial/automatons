package dev.automatons;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.*;
import net.minecraftforge.registries.*;

/** Common registrations; client rendering is initialized only by Forge on the client. */
public final class Luminall {
  public static final int COLOR = 0xffd9bbf5;
  public static final DeferredRegister<FluidType> TYPES =
      DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Automatons.ID);
  public static final DeferredRegister<Fluid> FLUIDS =
      DeferredRegister.create(Registries.FLUID, Automatons.ID);
  public static final RegistryObject<FluidType> TYPE =
      TYPES.register(
          "luminall",
          () ->
              new FluidType(
                  FluidType.Properties.create()
                      .density(1100)
                      .viscosity(1500)
                      .canConvertToSource(false)
                      .canHydrate(false)
                      .lightLevel(3)
                      .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                      .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)) {
                @Override
                public void initializeClient(
                    java.util.function.Consumer<
                            net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions>
                        consumer) {
                  dev.automatons.client.LuminallClient.fluid(consumer);
                }
              });
  public static final RegistryObject<ForgeFlowingFluid.Source> SOURCE =
      FLUIDS.register("luminall", () -> new ForgeFlowingFluid.Source(properties()));
  public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING =
      FLUIDS.register("flowing_luminall", () -> new ForgeFlowingFluid.Flowing(properties()));
  public static final RegistryObject<LiquidBlock> LIQUID =
      Automatons.BLOCKS.register(
          "luminall",
          () ->
              new LiquidBlock(
                  SOURCE, BlockBehaviour.Properties.copy(Blocks.WATER).lightLevel(s -> 3)));
  public static final RegistryObject<Item> BUCKET =
      Automatons.ITEMS.register(
          "luminall_bucket",
          () ->
              new BucketItem(
                  SOURCE, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
  public static final RegistryObject<Item> BOTTLE =
      Automatons.ITEMS.register("luminall_bottle", LuminallBottle::new);
  public static final RegistryObject<ReservoirBlock> TANK =
      Automatons.BLOCKS.register("tank", () -> new ReservoirBlock(false));
  public static final RegistryObject<ReservoirBlock> COLLECTOR =
      Automatons.BLOCKS.register("lumina_collector", () -> new ReservoirBlock(true));
  public static final RegistryObject<BlockEntityType<Reservoir>> TANK_BE =
      Automatons.BES.register(
          "tank", () -> BlockEntityType.Builder.of(Reservoir::new, TANK.get()).build(null));
  public static final RegistryObject<BlockEntityType<Reservoir>> COLLECTOR_BE =
      Automatons.BES.register(
          "lumina_collector",
          () -> BlockEntityType.Builder.of(Reservoir::new, COLLECTOR.get()).build(null));
  public static final RegistryObject<Item> TANK_ITEM =
      Automatons.ITEMS.register("tank", () -> new ReservoirItem(TANK.get()));
  public static final RegistryObject<Item> COLLECTOR_ITEM =
      Automatons.ITEMS.register("lumina_collector", () -> new ReservoirItem(COLLECTOR.get()));
  public static final RegistryObject<LuminaDrain> DRAIN =
      Automatons.BLOCKS.register("lumina_drain", LuminaDrain::new);
  public static final RegistryObject<Item> DRAIN_ITEM =
      Automatons.ITEMS.register(
          "lumina_drain", () -> new BlockItem(DRAIN.get(), new Item.Properties()));
  public static final RegistryObject<FluidSpill> SPILL =
      Automatons.BLOCKS.register("fluid_spill", FluidSpill::new);

  private static ForgeFlowingFluid.Properties properties() {
    return new ForgeFlowingFluid.Properties(TYPE, SOURCE, FLOWING)
        .bucket(BUCKET)
        .block(LIQUID)
        .tickRate(10)
        .slopeFindDistance(3)
        .levelDecreasePerBlock(1);
  }

  public static void register(IEventBus bus) {
    CollectorRecipe.register();
    TYPES.register(bus);
    FLUIDS.register(bus);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(LuminaCollection.class);
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(FluidSpill.class);
  }
}
