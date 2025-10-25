package net.turtleboi.bytebuddies.client.renderer.util;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class RepeatingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float uScale;
    private final float vScale;

    public RepeatingVertexConsumer(VertexConsumer delegate, float uScale, float vScale) {
        this.delegate = delegate;
        this.uScale = uScale;
        this.vScale = vScale;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u * uScale, v * vScale);
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int packedOverlay) {
        delegate.setOverlay(packedOverlay);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
