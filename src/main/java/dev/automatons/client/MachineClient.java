package dev.automatons.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.automatons.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Automatons.ID, value = Dist.CLIENT)
public final class MachineClient {
  public static final KeyMapping ENGINE =
      new KeyMapping(
          "key.automatons.machine_engine",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_R,
          "key.categories.automatons");
  public static final KeyMapping INVENTORY =
      new KeyMapping(
          "key.automatons.machine_inventory",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_I,
          "key.categories.automatons");
  private static final Map<Integer, EngineSound> sounds = new HashMap<>();
  private static int lastInput, ticks;
  private static int lastVehicle = -1;
  private static boolean engineHeld;

  @Mod.EventBusSubscriber(
      modid = Automatons.ID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Registration {
    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
      e.register(ENGINE);
      e.register(INVENTORY);
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent e) {
      e.registerReloadListener(
          (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
              resources -> MachineRenderer.clearModels());
    }
  }

  private static void send(Machine m, int action, int value) {
    Network.CHANNEL.sendToServer(new MachineActions.Action(m.getId(), action, "" + value, ""));
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    var mc = Minecraft.getInstance();
    ticks++;
    boolean engineDown = ENGINE.isDown();
    boolean enginePressed = engineDown && !engineHeld;
    engineHeld = engineDown;
    // Consume OS repeat events without treating them as additional presses.
    while (ENGINE.consumeClick()) {}
    if (mc.level == null) {
      sounds.values().forEach(EngineSound::end);
      sounds.clear();
      return;
    }
    if (ticks % 10 == 0) {
      sounds.values().removeIf(s -> s.isStopped());
      for (var entity : mc.level.entitiesForRendering())
        if (entity instanceof Machine m
            && m.view().getString("Engine").equals("RUNNING")
            && !sounds.containsKey(m.getId())) {
          EngineSound sound = new EngineSound(m);
          sounds.put(m.getId(), sound);
          mc.getSoundManager().play(sound);
        }
    }
    if (mc.player == null || !(mc.player.getVehicle() instanceof Machine m)) {
      lastInput = 0;
      lastVehicle = -1;
      return;
    }
    if (lastVehicle != m.getId()) {
      lastVehicle = m.getId();
      mc.player.setYRot(m.getYRot());
      mc.player.yRotO = m.getYRot();
      mc.player.setXRot(0);
      mc.player.xRotO = 0;
    }
    if (mc.screen == null) {
      if (enginePressed) send(m, 1, 0);
      while (INVENTORY.consumeClick()) send(m, 7, 0);
    }
    int input =
        mc.screen != null
            ? 0
            : mc.options.keyLeft.isDown()
                ? 3
                : mc.options.keyRight.isDown()
                    ? 4
                    : mc.options.keyUp.isDown() ? 1 : mc.options.keyDown.isDown() ? 2 : 0;
    // Turns are edge-triggered; held forward/reverse is refreshed with a bounded heartbeat.
    if (input != lastInput || ticks % 5 == 0 && input < 3) send(m, 8, input);
    lastInput = input;
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || !(mc.player.getVehicle() instanceof Machine m) || mc.screen != null)
      return;
    var g = e.getGuiGraphics();
    var n = m.view();
    int x = 8, y = 8;
    g.fill(x - 3, y - 3, x + 270, y + 62, 0xb8202028);
    g.drawString(
        mc.font,
        "The Machine · " + n.getString("Engine") + " · " + n.getInt("Used") + "/108 cargo",
        x,
        y,
        0xfff4e9ff);
    g.fill(x, y + 13, x + 180, y + 20, 0xff493d57);
    g.fill(
        x,
        y + 13,
        x
            + (int)
                (180L
                    * Math.min(MachineRules.FUEL_THIRDS, n.getInt("Fuel"))
                    / MachineRules.FUEL_THIRDS),
        y + 20,
        0xffc6a3ed);
    g.drawString(
        mc.font,
        new String[] {"None", "Blade", "Bore"}[Math.min(2, n.getInt("Attachment"))]
            + " · "
            + n.getString("Status"),
        x,
        y + 24,
        0xffeeeeee);
    String hints =
        mc.options.keyUp.getTranslatedKeyMessage().getString()
            + "/"
            + mc.options.keyDown.getTranslatedKeyMessage().getString()
            + " Drive  "
            + mc.options.keyLeft.getTranslatedKeyMessage().getString()
            + "/"
            + mc.options.keyRight.getTranslatedKeyMessage().getString()
            + " Turn";
    g.drawString(mc.font, hints, x, y + 37, 0xffcccccc);
    g.drawString(
        mc.font,
        ENGINE.getTranslatedKeyMessage().getString()
            + " Engine  "
            + INVENTORY.getTranslatedKeyMessage().getString()
            + " Controls  "
            + mc.options.keyShift.getTranslatedKeyMessage().getString()
            + " Dismount",
        x,
        y + 50,
        0xffcccccc);
  }

  @SubscribeEvent
  public static void boundary(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
    var mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) return;
    Machine m =
        mc.screen instanceof MachineScreen screen
            ? screen.getMenu().machine
            : mc.player.getVehicle() instanceof Machine driven
                ? driven
                : mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                        && hit.getEntity() instanceof Machine looked
                    ? looked
                    : null;
    if (m == null || !m.view().getString("Mode").equals("CHUNK")) return;
    int x = m.view().getInt("ChunkX") * 16, z = m.view().getInt("ChunkZ") * 16;
    var pose = e.getPoseStack();
    var camera = e.getCamera().getPosition();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    var buffers = mc.renderBuffers().bufferSource();
    var lines = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
    net.minecraft.client.renderer.LevelRenderer.renderLineBox(
        pose,
        lines,
        new net.minecraft.world.phys.AABB(x, m.getY() + .05, z, x + 16, m.getY() + 2, z + 16),
        1f,
        .85f,
        .15f,
        .9f);
    buffers.endBatch(net.minecraft.client.renderer.RenderType.lines());
    pose.popPose();
  }

  private static final class EngineSound extends AbstractTickableSoundInstance {
    private final Machine machine;

    EngineSound(Machine m) {
      super(
          SoundEvents.MINECART_INSIDE,
          SoundSource.BLOCKS,
          net.minecraft.util.RandomSource.create());
      machine = m;
      looping = true;
      delay = 0;
      volume = .24f;
      pitch = .55f;
      x = m.getX();
      y = m.getY();
      z = m.getZ();
    }

    public void tick() {
      if (machine.isRemoved() || !machine.view().getString("Engine").equals("RUNNING")) {
        stop();
        return;
      }
      x = machine.getX();
      y = machine.getY();
      z = machine.getZ();
      pitch =
          machine.view().getBoolean("Moving") || machine.view().getBoolean("Working") ? .75f : .55f;
    }

    void end() {
      stop();
    }
  }
}
