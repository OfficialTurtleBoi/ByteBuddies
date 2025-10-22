package net.turtleboi.bytebuddies.screen.custom.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Renders a 12x12 icon button pulled from a 24x24 "color block" on a texture atlas.
 *
 * Atlas layout per color block (24x24):
 *  - (0,0)   12x12: NORMAL
 *  - (12,0)  12x12: PRESSED
 *  - (0,12)  12x12: HOVERED
 *  - (12,12) 12x12: HOVERED+PRESSED
 *
 * All color blocks are arranged in a vertical strip (same X), each 24px tall.
 * Example given: first block starts at baseX=32, baseY=0; next color is baseY=24, then 48, etc.
 */
public class TinyIconButton extends Button {
    public static final int ICON_SIZE = 12;

    private final ResourceLocation atlas;
    private final int atlasW, atlasH;
    private final int tileU, tileV;
    private final BooleanSupplier lockedCond;

    private boolean pressedVisual = false;
    private Component lockedTooltip;

    @Nullable
    private MiniIcon miniIcon;

    public TinyIconButton(Button.Builder buttonBuilder, ResourceLocation atlas, int atlasW, int atlasH,
                          int tileU, int tileV, @Nullable BooleanSupplier lockedCond, @Nullable MiniIcon miniIcon) {
        super(buttonBuilder);
        this.atlas = Objects.requireNonNull(atlas);
        this.atlasW = atlasW;
        this.atlasH = atlasH;
        this.tileU = tileU;
        this.tileV = tileV;
        this.lockedCond = lockedCond == null ? () -> false : lockedCond;
        this.active = true;
        this.miniIcon = miniIcon;
    }

    public static Function<Builder, Button> buttonFactory(ResourceLocation atlas, int atlasW, int atlasH,
                                                          int tileU, int tileV, @Nullable BooleanSupplier lockedCond) {
        return builder -> new TinyIconButton(builder, atlas, atlasW, atlasH, tileU, tileV, lockedCond, null);
    }

    public static Function<Builder, Button> buttonFactoryWithIcon(ResourceLocation atlas, int atlasW, int atlasH,
                                                                  int tileU, int tileV, @Nullable BooleanSupplier lockedCond, @Nullable MiniIcon miniIcon) {
        return builder -> new TinyIconButton(builder, atlas, atlasW, atlasH, tileU, tileV, lockedCond, miniIcon);
    }

    public static final class MiniIcon {
        public final ResourceLocation atlas;
        public final int atlasW, atlasH;
        public final int uNormal, vNormal;
        public final int uPressed, vPressed;
        public final int xOff, yOff;

        public MiniIcon(ResourceLocation atlas, int atlasW, int atlasH, int uNormal, int vNormal, int uPressed, int vPressed, int xOff, int yOff) {
            this.atlas = atlas;
            this.atlasW = atlasW;
            this.atlasH = atlasH;
            this.uNormal = uNormal;
            this.vNormal = vNormal;
            this.uPressed = uPressed;
            this.vPressed = vPressed;
            this.xOff = xOff;
            this.yOff = yOff;
        }
    }

    public TinyIconButton withLockedTooltip(Component reason) {
        this.lockedTooltip = reason;
        return this;
    }

    public boolean isLocked() {
        return lockedCond.getAsBoolean();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        final boolean hovered = isMouseOver(mouseX, mouseY);
        final boolean enabled = this.active;
        final boolean locked  = isLocked();
        final boolean showPressed = pressedVisual && hovered && enabled && !locked;

        int quadX = showPressed ? 12 : 0;
        int quadY = hovered ? 12 : 0;


        guiGraphics.blit(atlas, getX(), getY(), tileU + quadX, tileV + quadY, ICON_SIZE, ICON_SIZE, atlasW, atlasH);

        if (miniIcon != null) {
            int iu = showPressed ? miniIcon.uPressed : miniIcon.uNormal;
            int iv = showPressed ? miniIcon.vPressed : miniIcon.vNormal;
            guiGraphics.blit(miniIcon.atlas, getX() + miniIcon.xOff, getY() + miniIcon.yOff,
                    iu, iv, 8, 8, miniIcon.atlasW, miniIcon.atlasH);
        }

        if (hovered && locked && lockedTooltip != null) {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, lockedTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!this.active || isLocked()) return;
        this.pressedVisual = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        boolean wasPressed = this.pressedVisual;
        this.pressedVisual = false;
        if (!this.active || isLocked()) return;
        if (wasPressed && isMouseOver(mouseX, mouseY)) {
            super.onPress();
        }
    }

    @Override
    public void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        super.updateWidgetNarration(narrationElementOutput);
    }
}
