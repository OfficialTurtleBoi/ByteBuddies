package net.turtleboi.bytebuddies.client.renderer.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public final class TintingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float mulR, mulG, mulB, mulA;

    TintingVertexConsumer(VertexConsumer delegate, float mulR, float mulG, float mulB, float mulA) {
        this.delegate = delegate;
        this.mulR = mulR; this.mulG = mulG; this.mulB = mulB; this.mulA = mulA;
    }

    private static int mul255(int colorValue, float multiplier) {
        int outputColor = Math.round(colorValue * multiplier);
        if (outputColor < 0) outputColor = 0;
        if (outputColor > 255) outputColor = 255;
        return outputColor;
    }

    @Override public @NotNull VertexConsumer addVertex(float x, float y, float z) {
        return delegate.addVertex(x, y, z);
    }

    @Override public @NotNull VertexConsumer setColor(int r, int g, int b, int a) {
        return delegate.setColor(mul255(r, mulR), mul255(g, mulG), mul255(b, mulB), mul255(a, mulA));
    }

    @Override public @NotNull VertexConsumer setUv(float u, float v) {
        return delegate.setUv(u, v);
    }

    @Override public @NotNull VertexConsumer setOverlay(int overlay) {
        return delegate.setOverlay(overlay);
    }

    @Override
    public @NotNull VertexConsumer setUv1(int u, int v) {
        return delegate.setUv1(u, v);
    }

    @Override public @NotNull VertexConsumer setUv2(int u, int v) {
        return delegate.setUv2(u, v);
    }

    @Override public @NotNull VertexConsumer setLight(int light) {
        return delegate.setLight(light);
    }

    @Override public @NotNull VertexConsumer setNormal(float nx, float ny, float nz) {
        return delegate.setNormal(nx, ny, nz);
    }
}
