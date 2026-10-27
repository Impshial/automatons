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
    lines.add(Component.literal("Retired item — no active effects"));
  }
}
