package net.turtleboi.turtlecore.client.util;

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

    @Override
    public VertexConsumer vertex(double pX, double pY, double pZ) {
        return delegate.vertex(pX, pY, pZ);
    }

    @Override
    public VertexConsumer color(int pRed, int pGreen, int pBlue, int pAlpha) {
        return delegate.color(mul255(pRed, mulR), mul255(pGreen, mulG), mul255(pBlue, mulB), mul255(pAlpha, mulA));
    }

    @Override
    public VertexConsumer uv(float pU, float pV) {
        return delegate.uv(pU, pV);
    }

    @Override
    public VertexConsumer overlayCoords(int pU, int pV) {
        return overlayCoords(pU, pV);
    }

    @Override
    public VertexConsumer uv2(int pU, int pV) {
        return uv2(pU, pV);
    }

    @Override
    public VertexConsumer normal(float pX, float pY, float pZ) {
        return normal(pX, pY, pZ);
    }

    public void endVertex() {}

    public void defaultColor(int pDefaultR, int pDefaultG, int pDefaultB, int pDefaultA) {}

    public void unsetDefaultColor() {}
}
