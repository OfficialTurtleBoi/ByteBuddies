package net.turtleboi.bytebuddies.entity.renderers;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.models.ByteBuddyModel;

public class ByteBuddyRenderer extends MobRenderer<ByteBuddyEntity, ByteBuddyModel<ByteBuddyEntity>> {
    private static ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddy.png");
    public ByteBuddyRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new ByteBuddyModel<>(pContext.bakeLayer(ByteBuddyModel.BYTEBUDDY_LAYER)),0.5f);
        this.addLayer(new ItemInHandLayer<>(this, pContext.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(ByteBuddyEntity entity) {
        return TEXTURE;
    }
}
