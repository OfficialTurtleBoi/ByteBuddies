package net.turtleboi.bytebuddies.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.screen.custom.menu.DockingStationMenu;
import net.turtleboi.bytebuddies.screen.custom.menu.SolarPanelMenu;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;

public class SolarPanelScreen extends AbstractContainerScreen<SolarPanelMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/gui/solar_panel_gui.png");
    private static final ResourceLocation GUI_ADDONS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/gui/gui_addons.png");

    private static final int energyX = 80;
    private static final int energyY = 23;
    private static final int energyWidth = 16;
    private static final int energyHeight = 52;
    private static final int energyEmptyU = 16;
    private static final int energyEmptyV = 0;
    private static final int energyFullU = 0;
    private static final int energyFullV = 0;

    private boolean debugEnergyOverride = false;
    private double debugFillPct = 0.50;

    public SolarPanelScreen(SolarPanelMenu solarPanelMenu, Inventory inventory, Component title) {
        super(solarPanelMenu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 205;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, GUI_TEXTURE);

        int x = (width - this.imageWidth) / 2;
        int y = (height - this.imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {

    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int x = (width - this.imageWidth) / 2;
        int y = (height - this.imageHeight) / 2;
        drawEnergyBar(guiGraphics, x + energyX, y + energyY);
        drawGenerationBolt(guiGraphics, x + 82, y + 5);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        drawEnergyToolTip(guiGraphics, mouseX, mouseY);
    }

    private void drawEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, y, energyEmptyU, energyEmptyV, energyWidth, energyHeight, 128, 128);

        int filled;
        if (debugEnergyOverride) {
            filled = Mth.clamp((int)Math.round(debugFillPct * energyHeight), 0, energyHeight);
        } else {
            long energy = getEnergyStoredSafe();
            long energyMax = Math.max(1, getMaxEnergyStoredSafe());
            filled = Mth.clamp((int)Math.round((double)energy * energyHeight / (double)energyMax), 0, energyHeight);
        }
        if (filled <= 0) return;

        int dy = energyHeight - filled;
        int drawY = y + dy;
        int vFill = energyFullV + dy;

        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, drawY, energyFullU, vFill, energyWidth, filled, 128, 128);
    }

    private void drawEnergyToolTip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int absoluteX = this.leftPos + energyX;
        int absoluteY = this.topPos + energyY;
        if (isMouseInArea(mouseX, mouseY, absoluteX, absoluteY, energyWidth, energyHeight)) {
            long energy = getEnergyStoredSafe();
            long energyMax = Math.max(1, getMaxEnergyStoredSafe());
            NumberFormat numberFormat = NumberFormat.getIntegerInstance();
            Component line1 = Component.literal(
                    numberFormat.format(energy) + " / " + numberFormat.format(energyMax) + " FE");

            guiGraphics.renderTooltip(this.font, List.of(line1), Optional.empty(), mouseX, mouseY);
        }
    }

    private void drawGenerationBolt(GuiGraphics guiGraphics, int x, int y) {
        if (getGenerating()) {
            guiGraphics.blit(GUI_TEXTURE, x, y, 176, 0, 12, 15, 256, 256);
        } else {
            guiGraphics.blit(GUI_TEXTURE, x, y, 188, 0, 12, 15, 256, 256);
        }
    }

    private long getEnergyStoredSafe() {
        try {
            return Math.max(0, this.menu.getEnergyStored());
        } catch (Throwable t) {
            return 0;
        }
    }

    private long getMaxEnergyStoredSafe() {
        try {
            return Math.max(1, this.menu.getMaxEnergyStored());
        } catch (Throwable t) {
            return 1;
        }
    }

    private boolean getGenerating() {
        try {
            return this.menu.getGeneratingBinary();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isMouseInArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
