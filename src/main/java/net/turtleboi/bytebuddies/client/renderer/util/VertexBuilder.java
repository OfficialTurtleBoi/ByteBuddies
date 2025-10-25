package net.turtleboi.bytebuddies.client.renderer.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class VertexBuilder {
    public static void vertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, float x, float y, float z, float u, float v, int normalZ, int red, int green, int blue, int vertexAlpha) {
        vertexConsumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, vertexAlpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 0, normalZ);
    }
}
