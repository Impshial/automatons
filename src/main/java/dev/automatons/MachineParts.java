package dev.automatons;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

public final class MachineParts {
  public static final Map<String, RegistryObject<Block>> BLOCKS = new LinkedHashMap<>();
  public static final Map<String, RegistryObject<Item>> ITEMS = new LinkedHashMap<>();

  static {
    for (String name :
        List.of(
            "machine_track",
            "machine_frame",
            "attachment_mount",
            "operator_station",
            "cargo_compartment",
            "lumina_engine")) {
      var block =
          Automatons.BLOCKS.register(
              name,
              () -> new Block(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
      BLOCKS.put(name, block);
      ITEMS.put(
          name,
          Automatons.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }
    for (String name : List.of("blade_attachment", "bore_attachment", "program"))
      ITEMS.put(
          name,
          Automatons.ITEMS.register(
              name,
              () ->
                  new Item(new Item.Properties().stacksTo(1)) {
                    @Override
                    public void appendHoverText(
                        ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
                      lines.add(
                          Component.literal(
                              switch (name) {
                                case "blade_attachment" -> "Clears soft terrain and harvests"
                                    + " Lumina.";
                                case "bore_attachment" -> "Nine diamond drills cut a 3 × 3 tunnel.";
                                default -> "Reusable Machine instructions.";
                              }));
                      if (name.equals("program")
                          && stack.hasTag()
                          && !stack.getTag().getString("ProgramName").isEmpty())
                        lines.add(Component.literal(stack.getTag().getString("ProgramName")));
                    }
                  }));
  }

  public static final RegistryObject<EntityType<Machine>> ENTITY =
      Automatons.ENTITIES.register(
          "machine",
          () ->
              EntityType.Builder.<Machine>of(Machine::new, MobCategory.MISC)
                  .sized(2.98f, 2f)
                  .clientTrackingRange(12)
                  .updateInterval(1)
                  .build("machine"));
  public static final RegistryObject<Item> PACKED =
      Automatons.ITEMS.register("packed_machine", MachineItem::new);
  public static final RegistryObject<MenuType<MachineMenu>> MENU =
      Automatons.MENUS.register("machine", () -> IForgeMenuType.create(MachineMenu::new));

  public static Item item(String name) {
    return ITEMS.get(name).get();
  }

  public static Block block(String name) {
    return BLOCKS.get(name).get();
  }

  public static boolean component(Block b) {
    return BLOCKS.values().stream().anyMatch(v -> v.get() == b) || b == Luminall.TANK.get();
  }

  public static void register() {
    BoreRecipe.register();
  }
}
