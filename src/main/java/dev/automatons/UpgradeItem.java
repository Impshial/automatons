package dev.automatons;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public class UpgradeItem extends Item {
  public final String family;
  public final int tier;

  public UpgradeItem(String family, int tier) {
    super(new Properties().stacksTo(1));
    this.family = family;
    this.tier = tier;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    String effect =
        switch (family) {
          case "storage" -> "+" + (tier * 4) + " storage radius";
          case "lumber" -> "+" + (tier * 8) + " lumber radius";
          case "speed" -> (tier == 1 ? "20%" : "36%") + " shorter chopping time";
          default -> "Effective Efficiency " + tier + " (higher level wins)";
        };
    lines.add(Component.literal(effect));
    lines.add(Component.literal("One upgrade per family. Install in a totem."));
  }
}
