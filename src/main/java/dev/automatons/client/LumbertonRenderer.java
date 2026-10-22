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
  public ResourceLocation getTextureLocation(Lumberton w) {
    return Automatons.id(
        "textures/entity/lumberton_"
            + new String[] {"idle", "smile", "brow", "focused", "frown", "anger"}
                [Math.min(5, w.face())]
            + ".png");
  }
}
