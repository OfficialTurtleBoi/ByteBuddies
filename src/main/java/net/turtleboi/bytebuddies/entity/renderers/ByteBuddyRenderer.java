package net.turtleboi.bytebuddies.entity.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.client.HueShiftTextureCache;
import net.turtleboi.bytebuddies.client.renderer.util.TintingBuffer;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.HologramBuddyEntity;
import net.turtleboi.bytebuddies.entity.models.ByteBuddyModel;
import net.turtleboi.bytebuddies.init.ModTags;
import net.turtleboi.bytebuddies.item.ModItems;
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
        this.addLayer(new BuddyItemInHandLayer(this, pContext.getItemInHandRenderer()));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ByteBuddyEntity byteBuddy) {
        ByteBuddyEntity.ChassisMaterial chassisMaterial = byteBuddy.getChassisMaterial();
        return switch (chassisMaterial) {
            case CHARGED_STEEL -> CHARGED_STEEL_TEXTURE;
            case NETHERITE -> NETHERITE_TEXTURE;
            case STEEL -> STEEL_TEXTURE;
            case IRON -> IRON_TEXTURE;
            default -> DEFAULT_TEXTURE;
        };
    }


    @Override
    public void render(ByteBuddyEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int packedLight) {
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
            if (entity instanceof HologramBuddyEntity) {
                VertexConsumer holoBuf = buffers.getBuffer(RenderType.entityTranslucentCull(DISPLAY));
                model.renderDisplayBg(pose, holoBuf, packedLight, OverlayTexture.NO_OVERLAY, 0x73FFFFFF);
            } else {
                VertexConsumer baseBuf = buffers.getBuffer(RenderType.entityCutoutNoCull(tintedSheet));
                model.renderDisplayBg(pose, baseBuf, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }

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

    private static final class BuddyItemInHandLayer extends RenderLayer<ByteBuddyEntity, ByteBuddyModel<ByteBuddyEntity>> {
        private final ItemInHandRenderer itemInHandRenderer;
        BuddyItemInHandLayer(RenderLayerParent<ByteBuddyEntity, ByteBuddyModel<ByteBuddyEntity>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, ByteBuddyEntity byteBuddy,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            boolean rightHanded = byteBuddy.getMainArm() == HumanoidArm.RIGHT;
            ItemStack leftStack  = rightHanded ? byteBuddy.getOffhandItem() : byteBuddy.getMainHandItem();
            ItemStack rightStack = rightHanded ? byteBuddy.getMainHandItem() : byteBuddy.getOffhandItem();

            if (!leftStack.isEmpty() || !rightStack.isEmpty()) {
                poseStack.pushPose();
                if (this.getParentModel().young) {
                    poseStack.translate(0.0F, 0.75F, 0.0F);
                    poseStack.scale(0.5F, 0.5F, 0.5F);
                }

                this.renderArmWithItem(byteBuddy, rightStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        HumanoidArm.RIGHT, poseStack, multiBufferSource, packedLight);

                this.renderArmWithItem(byteBuddy, leftStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        HumanoidArm.LEFT, poseStack, multiBufferSource, packedLight);

                poseStack.popPose();
            }
        }

        private void renderArmWithItem(ByteBuddyEntity byteBuddy, ItemStack itemStack, ItemDisplayContext displayContext,
                                       HumanoidArm humanoidArm, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight) {
            if (!itemStack.isEmpty()) {
                poseStack.pushPose();
                this.getParentModel().translateToHand(humanoidArm, poseStack);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

                boolean isLeft = (humanoidArm == HumanoidArm.LEFT);
                poseStack.translate((float)(isLeft ? -1 : 1) / 16.0F, 0.125F, -0.625F);

                boolean hologram = (byteBuddy instanceof HologramBuddyEntity);
                MultiBufferSource actualBuffer = multiBufferSource;

                if (hologram) {
                    float red = 81 / 255f;
                    float green = 240 / 255f;
                    float blue = 255 / 255f;
                    float alpha = 0.45f;

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    actualBuffer = new TintingBuffer(multiBufferSource, red, green, blue, alpha);
                }

                this.itemInHandRenderer.renderItem(byteBuddy, itemStack, displayContext, isLeft, poseStack, actualBuffer, packedLight);

                if (hologram) {
                    RenderSystem.disableBlend();
                }

                poseStack.popPose();
            }
        }
    }
}
