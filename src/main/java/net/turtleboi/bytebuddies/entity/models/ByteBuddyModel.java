package net.turtleboi.bytebuddies.entity.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.animations.ByteBuddyAnimations;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import org.jetbrains.annotations.NotNull;

public class ByteBuddyModel <T extends Entity> extends HierarchicalModel<T> implements ArmedModel {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation BYTEBUDDY_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "bytebuddy"), "main");

    private final ModelPart bytebuddy;
    private final ModelPart head;
    private final ModelPart display;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ByteBuddyModel(ModelPart root) {
        this.bytebuddy = root.getChild("bytebuddy");
        this.head = this.bytebuddy.getChild("head");
        this.display = this.head.getChild("display");
        this.rightArm = this.bytebuddy.getChild("rightArm");
        this.leftArm = this.bytebuddy.getChild("leftArm");
        this.rightLeg = this.bytebuddy.getChild("rightLeg");
        this.leftLeg = this.bytebuddy.getChild("leftLeg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bytebuddy = partdefinition.addOrReplaceChild("bytebuddy", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition head = bytebuddy.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, -6.0F, 0.0F));

        PartDefinition display = head.addOrReplaceChild("display", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -4.0F, -1.95F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(32, 24).addBox(-4.0F, -4.0F, -2.05F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -2.05F));

        PartDefinition rightArm = bytebuddy.addOrReplaceChild("rightArm", CubeListBuilder.create().texOffs(32, 0).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -4.5F, 0.0F));

        PartDefinition leftArm = bytebuddy.addOrReplaceChild("leftArm", CubeListBuilder.create().texOffs(32, 0).mirror().addBox(0.0F, -1.0F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, -4.5F, 0.0F));

        PartDefinition rightLeg = bytebuddy.addOrReplaceChild("rightLeg", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -1.25F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, -1.75F, 0.0F));

        PartDefinition leftLeg = bytebuddy.addOrReplaceChild("leftLeg", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-1.0F, -1.25F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.0F, -1.75F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(@NotNull Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        ByteBuddyEntity byteBuddy = (ByteBuddyEntity) entity;

        if (byteBuddy.isWaking()) {
            this.animate(byteBuddy.wakeUpState, ByteBuddyAnimations.WAKE_UP_ANIMATION, ageInTicks, 1f);
        }

        if (!byteBuddy.isSleeping() && !byteBuddy.isWaking()) {
            this.applyHeadRotation(netHeadYaw, headPitch);
            this.animateWalk(ByteBuddyAnimations.WALKING_ANIMATION, limbSwing, limbSwingAmount, 2f, 2.4f);
            this.animate(byteBuddy.idleAnimationState, ByteBuddyAnimations.IDLE_ANIMATION, ageInTicks, 1f);
        } else {
            this.animate(byteBuddy.sleepPoseState, ByteBuddyAnimations.INACTIVE_ANIMATION, ageInTicks, 1f);
        }

        if (byteBuddy.isWaving()) {
            this.animate(byteBuddy.waveState, ByteBuddyAnimations.WAVING_ANIMATION, ageInTicks, 1f);
        }
    }

    private void applyHeadRotation(float pNetHeadYaw, float pHeadPitch) {
        pNetHeadYaw = Mth.clamp(pNetHeadYaw, -30.0F, 30.0F);
        pHeadPitch = Mth.clamp(pHeadPitch, -25.0F, 45.0F);

        this.head.yRot = pNetHeadYaw * ((float)Math.PI / 180F);
        this.head.xRot = pHeadPitch * ((float)Math.PI / 180F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        bytebuddy.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    @Override
    public ModelPart root() {
        return bytebuddy;
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack poseStack) {

    }
}
