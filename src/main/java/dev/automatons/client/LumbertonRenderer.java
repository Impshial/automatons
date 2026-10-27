package dev.automatons.client;

import dev.automatons.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class LumbertonRenderer extends MobRenderer<Lumberton, LumbertonModel> {
  public LumbertonRenderer(EntityRendererProvider.Context c) {
    super(c, new LumbertonModel(c.bakeLayer(LumbertonModel.LAYER)), .4f);
    addLayer(new ItemInHandLayer<>(this, c.getItemInHandRenderer()));
  }

  @Override
  protected boolean shouldShowName(Lumberton worker) {
    return worker.showNames() && super.shouldShowName(worker);
  }

  @Override
  public ResourceLocation getTextureLocation(Lumberton w) {
    if (Math.floorMod(w.tickCount + w.getId() * 17, 100) < 3)
      return Automatons.id("textures/entity/lumberton_blink.png");
    return Automatons.id(
        "textures/entity/lumberton_"
            + new String[] {"idle", "smile", "brow", "focused", "frown", "anger"}
                [Math.min(5, w.face())]
            + ".png");
  }
}
