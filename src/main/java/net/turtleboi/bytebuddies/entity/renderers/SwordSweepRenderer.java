package net.turtleboi.bytebuddies.entity.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;

public class SwordSweepRenderer extends EntityRenderer<SwordSweepEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/sword_sweep.png");
    public SwordSweepRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SwordSweepEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(45));


        float scale = 1.0F;
        poseStack.scale(scale, scale, scale);
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        drawQuad(builder, poseStack, packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void drawQuad(VertexConsumer builder, PoseStack poseStack, int light) {
        float size = 0.5F;
        var pose = poseStack.last().pose();

        builder.addVertex(pose, -size, -size, 0)
                .setColor(255, 255, 255, 255)
                .setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setUv2(15,15)
                .setNormal(0, 1, 0);

        builder.addVertex(pose, size, -size, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setUv2(15,15)
                .setNormal(0, 1, 0);

        builder.addVertex(pose, size, size, 0)
                .setColor(255, 255, 255, 255)
                .setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setUv2(15,15)
                .setNormal(0, 1, 0);

        builder.addVertex(pose, -size, size, 0)
                .setColor(255, 255, 255, 255)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setUv2(15,15)
                .setNormal(0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(SwordSweepEntity entity) {
        return TEXTURE;
    }
}