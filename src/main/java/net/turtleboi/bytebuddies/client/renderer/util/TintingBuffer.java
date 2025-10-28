package net.turtleboi.bytebuddies.client.renderer.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class TintingBuffer implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float mulR, mulG, mulB, mulA;

    public TintingBuffer(MultiBufferSource delegate, float mulR, float mulG, float mulB, float mulA) {
        this.delegate = delegate;
        this.mulR = mulR; this.mulG = mulG; this.mulB = mulB; this.mulA = mulA;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return new TintingVertexConsumer(delegate.getBuffer(type), mulR, mulG, mulB, mulA);
    }
}
