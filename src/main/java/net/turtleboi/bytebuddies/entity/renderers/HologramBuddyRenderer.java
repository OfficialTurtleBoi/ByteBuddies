package net.turtleboi.bytebuddies.entity.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.HologramBuddyEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HologramBuddyRenderer extends ByteBuddyRenderer{
    public HologramBuddyRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    protected @Nullable RenderType getRenderType(@NotNull ByteBuddyEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(this.getTextureLocation(livingEntity));
    }

    @Override
    public void render(ByteBuddyEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float red = 81 / 255f;
        float green = 240 / 255f;
        float blue = 255 / 255f;
        float alpha = 0.45f;

        RenderSystem.setShaderColor(red, green, blue, alpha);
        super.render(entity, yaw, partialTicks, pose, buffers, packedLight);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }
}
