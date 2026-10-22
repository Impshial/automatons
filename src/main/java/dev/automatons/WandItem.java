package dev.automatons;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class WandItem extends Item {
  public WandItem() {
    super(new Properties().stacksTo(1));
  }

  public static int mode(ItemStack s) {
    return Math.floorMod(s.getOrCreateTag().getInt("Mode"), 3);
  }

  public static String name(int mode) {
    return new String[] {"Assign", "Protect", "Revoke"}[Math.floorMod(mode, 3)];
  }

  public static ItemStack held(Player p) {
    return p.getMainHandItem().getItem() instanceof WandItem
        ? p.getMainHandItem()
        : p.getOffhandItem().getItem() instanceof WandItem ? p.getOffhandItem() : ItemStack.EMPTY;
  }

  public static void select(ItemStack s, Lumberton w, boolean rebind) {
    CompoundTag n = s.getOrCreateTag();
    n.putUUID("Worker", w.getUUID());
    n.putString("Dimension", w.level().dimension().location().toString());
    n.putBoolean("Rebind", rebind);
    n.putLong("SelectedAt", w.level().getGameTime());
    n.putInt("Mode", 0);
  }

  public static void cancel(ItemStack s) {
    s.getOrCreateTag().remove("Worker");
    s.getOrCreateTag().remove("Rebind");
  }

  @Override
  public InteractionResult interactLivingEntity(
      ItemStack s, Player p, LivingEntity target, InteractionHand hand) {
    if (!(target instanceof Lumberton w) || mode(s) != 0) return InteractionResult.PASS;
    if (!p.level().isClientSide) {
      if (w.owner == null) {
        w.owner = p.getUUID();
        select(s, w, true);
        p.displayClientMessage(
            Component.literal("Test worker claimed. Choose your pedestal to give it a home."),
            true);
      } else if (w.owns(p)) {
        select(s, w, false);
        p.displayClientMessage(
            Component.literal("Lumberton selected. Right-click your totem; sneak-click to cancel."),
            true);
      } else
        p.displayClientMessage(Component.literal("This Lumberton belongs to someone else"), true);
    }
    return InteractionResult.sidedSuccess(p.level().isClientSide);
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
    ItemStack s = p.getItemInHand(hand);
    if (p.isShiftKeyDown() && !l.isClientSide) {
      cancel(s);
      p.displayClientMessage(Component.literal("Selection cancelled"), true);
    }
    return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
  }

  @Override
  public InteractionResult useOn(UseOnContext c) {
    Player p = c.getPlayer();
    if (p == null) return InteractionResult.PASS;
    if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
    ItemStack wand = c.getItemInHand();
    int mode = mode(wand);
    if (mode != 0) {
      Network.requestProtection((ServerPlayer) p, c.getClickedPos(), mode == 1);
      return InteractionResult.CONSUME;
    }
    if (p.isShiftKeyDown()) {
      cancel(wand);
      p.displayClientMessage(Component.literal("Selection cancelled"), true);
      return InteractionResult.CONSUME;
    }
    CompoundTag n = wand.getOrCreateTag();
    if (!n.hasUUID("Worker")) {
      p.displayClientMessage(Component.literal("Select your Lumberton first"), true);
      return InteractionResult.CONSUME;
    }
    ServerLevel l = (ServerLevel) c.getLevel();
    if (!n.getString("Dimension").equals(l.dimension().location().toString())
        || l.getGameTime() - n.getLong("SelectedAt") > 1200) {
      cancel(wand);
      p.displayClientMessage(
          Component.literal("Selection expired; select your worker again"), true);
      return InteractionResult.CONSUME;
    }
    if (!(l.getEntity(n.getUUID("Worker")) instanceof Lumberton w)) {
      p.displayClientMessage(Component.literal("Selected worker is unloaded"), true);
      return InteractionResult.CONSUME;
    }
    if (!w.owns(p)) {
      cancel(wand);
      return InteractionResult.CONSUME;
    }
    if (l.getBlockEntity(c.getClickedPos()) instanceof Station s) {
      String result = n.getBoolean("Rebind") ? w.rebind(s) : w.assign(s);
      p.displayClientMessage(Component.literal(result), true);
      if (result.equals("Home rebound") || result.equals("Lumberton assigned")) cancel(wand);
    } else
      p.displayClientMessage(
          Component.literal(
              n.getBoolean("Rebind")
                  ? "Choose a replacement pedestal"
                  : "Choose an Automaton Totem"),
          true);
    return InteractionResult.CONSUME;
  }

  @Override
  public void appendHoverText(ItemStack s, Level l, List<Component> lines, TooltipFlag f) {
    lines.add(Component.literal("Mode: " + name(mode(s))));
    lines.add(Component.literal("Shift + scroll changes mode"));
    lines.add(Component.literal("Sneak + right-click cancels selection"));
    if (s.hasTag() && s.getTag().hasUUID("Worker"))
      lines.add(
          Component.literal(
              s.getTag().getBoolean("Rebind") ? "Select a replacement home" : "Worker selected"));
  }
}
