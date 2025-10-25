package net.turtleboi.bytebuddies.entity.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;

import static net.turtleboi.bytebuddies.client.renderer.util.VertexBuilder.vertex;

public class SwordSweepRenderer extends EntityRenderer<SwordSweepEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/terrablade_sweep.png");
    public SwordSweepRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SwordSweepEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight) {
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.entityTranslucentCull(TEXTURE));

        int baseAlpha = 196;
        float lifespanPercent = (entity.tickCount + partialTicks) / (float) entity.getLifespan();

        float fadeMultiplier;
        if (lifespanPercent <= 0.9f) {
            fadeMultiplier = 1.0f;
        } else {
            float fade = (lifespanPercent - 0.9f) / 0.1f;
            fadeMultiplier = 1.0f - Mth.clamp(fade, 0.0f, 1.0f);
        }
        int vertexAlpha = Mth.clamp(Math.round(baseAlpha * fadeMultiplier), 0, 255);

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() / 2, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        createBladeSweep(entity, poseStack, vertexConsumer, ((float) 1 / 32) * entity.getBbHeight(), vertexAlpha);
        poseStack.popPose();
    }

    private void createBladeSweep(SwordSweepEntity entity, PoseStack poseStack, VertexConsumer vertexConsumer, float scale, int vertexAlpha) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));
        float xTilt = Mth.randomBetween(RandomSource.create(entity.getUUID().getLeastSignificantBits()), -30f, 30f);
        poseStack.mulPose(Axis.XP.rotationDegrees(xTilt));
        drawQuad(poseStack, vertexConsumer, vertexAlpha);
        poseStack.popPose();
    }

    private void drawQuad(PoseStack poseStack, VertexConsumer vertexConsumer, int vertexAlpha) {
        var pose = poseStack.last();
        int width = 10;
        int height = 32;
        float halfWidth = (width / 2f);
        float halfHeight = (height / 2f);

        vertex(pose, vertexConsumer, -halfWidth, -halfHeight, 0, 0, 0, 1, 255, 255, 255, vertexAlpha);
        vertex(pose, vertexConsumer, halfWidth, -halfHeight, 0, 1, 0, 1, 255, 255, 255, vertexAlpha);
        vertex(pose, vertexConsumer, halfWidth, halfHeight, 0, 1, 1, 1, 255, 255, 255, vertexAlpha);
        vertex(pose, vertexConsumer, -halfWidth, halfHeight, 0, 0, 1, 1, 255, 255, 255, vertexAlpha);

        vertex(pose, vertexConsumer, -halfWidth, halfHeight, 0, 0, 1, -1, 255, 255, 255, vertexAlpha);
        vertex(pose, vertexConsumer, halfWidth, halfHeight, 0, 1, 1, -1, 255, 255, 255, vertexAlpha);
        vertex(pose, vertexConsumer, halfWidth, -halfHeight, 0, 1, 0, -1, 255, 255, 255, vertexAlpha);
        vertex(pose, vertexConsumer, -halfWidth, -halfHeight, 0, 0, 0, -1, 255, 255, 255, vertexAlpha);
    }

    @Override
    public ResourceLocation getTextureLocation(SwordSweepEntity entity) {
        return TEXTURE;
    }
}