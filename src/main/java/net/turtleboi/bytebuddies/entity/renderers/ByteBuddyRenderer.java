package net.turtleboi.bytebuddies.entity.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.client.HueShiftTextureCache;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.models.ByteBuddyModel;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;

public class ByteBuddyRenderer extends MobRenderer<ByteBuddyEntity, ByteBuddyModel<ByteBuddyEntity>> {
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddy.png");
    private static final ResourceLocation IRON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddy_iron.png");
    private static final ResourceLocation STEEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddy_steel.png");
    private static final ResourceLocation NETHERITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddy_netherite.png");
    private static final ResourceLocation CHARGED_STEEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddy_charged_steel.png");
    private static final ResourceLocation DISPLAY =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/entity/bytebuddy/bytebuddycore.png");
    public ByteBuddyRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new ByteBuddyModel<>(pContext.bakeLayer(ByteBuddyModel.BYTEBUDDY_LAYER)),0.5f);
        this.addLayer(new DisplayLayer(this));
        this.addLayer(new ItemInHandLayer<>(this, pContext.getItemInHandRenderer()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ByteBuddyEntity byteBuddy) {
        ResourceLocation chassisMaterial = DEFAULT_TEXTURE;
        if (byteBuddy.getChassisMaterial() == ByteBuddyEntity.ChassisMaterial.CHARGED_STEEL){
            chassisMaterial = CHARGED_STEEL_TEXTURE;
        } else if (byteBuddy.getChassisMaterial() == ByteBuddyEntity.ChassisMaterial.NETHERITE){
            chassisMaterial = NETHERITE_TEXTURE;
        } else if (byteBuddy.getChassisMaterial() == ByteBuddyEntity.ChassisMaterial.STEEL){
            chassisMaterial = STEEL_TEXTURE;
        } else if (byteBuddy.getChassisMaterial() == ByteBuddyEntity.ChassisMaterial.IRON){
            chassisMaterial = IRON_TEXTURE;
        }
        return chassisMaterial;
    }

    @Override
    public void render(ByteBuddyEntity entity, float yaw, float partialTicks,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        model.setDisplayVisibility(false, false);
        super.render(entity, yaw, partialTicks, pose, buffers, packedLight);
        model.setDisplayVisibility(true, true);
    }

    private static class DisplayLayer extends RenderLayer<ByteBuddyEntity, ByteBuddyModel<ByteBuddyEntity>> {
        DisplayLayer(RenderLayerParent<ByteBuddyEntity, ByteBuddyModel<ByteBuddyEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, MultiBufferSource buffers, int packedLight,
                           ByteBuddyEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {

            ByteBuddyModel<ByteBuddyEntity> model = this.getParentModel();
            model.setDisplayVisibility(true, true);
            pose.pushPose();
            model.translateToDisplay(pose);
            int targetRGB = entity.getDisplayColorRGB();
            ResourceLocation tintedSheet = BG_CACHE.getOrCreate(targetRGB);
            VertexConsumer baseBuf = buffers.getBuffer(RenderType.entityCutoutNoCull(tintedSheet));
            model.renderDisplayBg(pose, baseBuf, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

            ResourceLocation faceTex = entity.getMoodTexture();
            VertexConsumer faceBuf = buffers.getBuffer(RenderType.entityTranslucentCull(faceTex));

            PoseStack.Pose cur = pose.last();
            Matrix4f mat = cur.pose();

            final float scaling = 1.0f / 16.0f;
            float x1 = -4f * scaling, y1 = -4f * scaling;
            float x2 =  4f * scaling, y2 =  4f * scaling;
            float z  = -2.051f * scaling;
            float u1 = 0f, v1 = 0f;
            float u2 = 1f, v2 = 1f;
            int r = 255, g = 255, b = 255, a = 255;

            faceBuf.addVertex(mat, x1, y1, z).setColor(r,g,b,a).setUv(u1, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
                    .setNormal(cur, 0f, 0f, -1f);

            faceBuf.addVertex(mat, x1, y2, z).setColor(r,g,b,a).setUv(u1, v2)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
                    .setNormal(cur, 0f, 0f, -1f);

            faceBuf.addVertex(mat, x2, y2, z).setColor(r,g,b,a).setUv(u2, v2)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
                    .setNormal(cur, 0f, 0f, -1f);

            faceBuf.addVertex(mat, x2, y1, z).setColor(r,g,b,a).setUv(u2, v1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
                    .setNormal(cur, 0f, 0f, -1f);

            pose.popPose();
            model.setDisplayVisibility(false, false);
        }
    }

    static List<HueShiftTextureCache.Rect> DISPLAY_BG_UVS = List.of(
            new HueShiftTextureCache.Rect( 8,  8, 8, 8),
            new HueShiftTextureCache.Rect(24,  8, 8, 8),
            new HueShiftTextureCache.Rect(0,  8, 8, 8),
            new HueShiftTextureCache.Rect(16,  8, 8, 8),
            new HueShiftTextureCache.Rect( 16, 0, 8, 8),
            new HueShiftTextureCache.Rect(16, 0, 8, 8)
    );

    private static final HueShiftTextureCache BG_CACHE =
            new HueShiftTextureCache(
                    DISPLAY,
                    "display_bg_",
                    DISPLAY_BG_UVS
            );

}
