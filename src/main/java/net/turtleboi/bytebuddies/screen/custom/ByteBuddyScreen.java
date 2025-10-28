package net.turtleboi.bytebuddies.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.network.payloads.RoleData;
import net.turtleboi.bytebuddies.network.payloads.SleepData;
import net.turtleboi.bytebuddies.screen.custom.menu.ByteBuddyMenu;
import net.turtleboi.bytebuddies.screen.custom.widget.TinyIconButton;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ByteBuddyScreen extends AbstractContainerScreen<ByteBuddyMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/gui/bytebuddy_gui.png");
    private static final ResourceLocation GUI_ADDONS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/gui/gui_addons.png");

    private static final int energyX = 6;
    private static final int energyY = 64;
    private static final int energyWidth = 16;
    private static final int energyHeight = 52;
    private static final int energyEmptyU = 16;
    private static final int energyEmptyV = 0;
    private static final int energyFullU = 0;
    private static final int energyFullV = 0;

    private static final int buddyPreviewX = 82;
    private static final int buddyPreviewY = 6;
    private static final int buddyPreviewWidth = 38;
    private static final int buddyPreviewHeight = 38;
    private static final int buddyPreviewU = buddyPreviewX;
    private static final int buddyPreviewV = buddyPreviewY;

    private static final int healthX = 180;
    private static final int healthY = 64;
    private static final int healthWidth = 16;
    private static final int healthHeight = 52;
    private static final int healthEmptyU = 16;
    private static final int healthEmptyV = 52;
    private static final int healthFullU = 0;
    private static final int healthFullV = 52;

    private boolean debugEnergyOverride = false;
    private boolean debugHealthOverride = false;
    private double debugFillPct = 0.50;

    public ByteBuddyScreen(ByteBuddyMenu buddyMenu, Inventory inventory, Component title) {
        super(buddyMenu, inventory, title);
        this.imageWidth = 202;
        this.imageHeight = 227;
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - this.imageWidth) / 2;
        int y = (height - this.imageHeight) / 2;

        addRenderableWidget(
                Button.builder(Component.empty(),
                                button -> setFarmer(menu.byteBuddy))
                        .bounds(x + 72, y + 124, 12, 12)
                        .tooltip(Tooltip.create(
                                Component.literal("Farmer")))
                        .build(TinyIconButton.buttonFactoryWithIcon(
                                GUI_ADDONS_TEXTURE, 128, 128,
                                32, 0,
                                null,
                                new TinyIconButton.MiniIcon(
                                        GUI_ADDONS_TEXTURE, 128, 128,
                                        8, 112, 8, 104, 2, 2
                                )
                        ))
        );

        addRenderableWidget(
                Button.builder(Component.empty(),
                                button -> setMiner(menu.byteBuddy))
                        .bounds(x + 87, y + 124, 12, 12)
                        .tooltip(Tooltip.create(
                                Component.literal("Miner")))
                        .build(TinyIconButton.buttonFactoryWithIcon(
                                GUI_ADDONS_TEXTURE, 128, 128,
                                32, 0,
                                null,
                                new TinyIconButton.MiniIcon(
                                        GUI_ADDONS_TEXTURE, 128, 128,
                                        0, 112,0, 104, 2, 2
                                )
                        ))
        );

        addRenderableWidget(
                Button.builder(Component.empty(),
                                button -> setHauler(menu.byteBuddy))
                        .bounds(x + 103, y + 124, 12, 12)
                        .tooltip(Tooltip.create(
                                Component.literal("Hauler")))
                        .build(TinyIconButton.buttonFactoryWithIcon(
                                GUI_ADDONS_TEXTURE, 128, 128,
                                32, 0,
                                null,
                                new TinyIconButton.MiniIcon(
                                        GUI_ADDONS_TEXTURE, 128, 128,
                                        16, 112,16, 104, 2, 2
                                )
                        ))
        );

        addRenderableWidget(
                Button.builder(Component.empty(),
                                button -> setFighter(menu.byteBuddy))
                        .bounds(x + 118, y + 124, 12, 12)
                        .tooltip(Tooltip.create(
                                Component.literal("Fighter")))
                        .build(TinyIconButton.buttonFactoryWithIcon(
                                GUI_ADDONS_TEXTURE, 128, 128,
                                32, 0,
                                null,
                                new TinyIconButton.MiniIcon(
                                        GUI_ADDONS_TEXTURE, 128, 128,
                                        24, 112,24, 104, 2, 2
                                )
                        ))

        );
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
        drawHealthBar(guiGraphics, x + healthX, y + healthY);
        drawBuddyPreview(guiGraphics, x + buddyPreviewX, y + buddyPreviewY, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        drawEnergyToolTip(guiGraphics, mouseX, mouseY);
        drawHealthToolTip(guiGraphics, mouseX, mouseY);
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
            Component line1 = Component.literal("Energy: ");
            Component line2 = Component.literal(numberFormat.format(energy) + " / " + numberFormat.format(energyMax) + " FE");

            guiGraphics.renderTooltip(this.font, List.of(line1, line2), Optional.empty(), mouseX, mouseY);
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

    private void drawHealthBar(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, y, healthEmptyU, healthEmptyV, healthWidth, healthHeight, 128, 128);

        int filled;
        if (debugHealthOverride) {
            filled = Mth.clamp((int)Math.round(debugFillPct * healthHeight), 0, healthHeight);
        } else {
            double health = getHealthSafe();
            double healthMax = Math.max(1, getMaxHealthSafe());
            filled = Mth.clamp((int)Math.round(health * healthHeight / healthMax), 0, healthHeight);
        }
        if (filled <= 0) return;

        int dy = healthHeight - filled;
        int drawY = y + dy;
        int vFill = healthFullV + dy;

        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, drawY, healthFullU, vFill, healthWidth, filled, 128, 128);
    }

    private void drawHealthToolTip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int absoluteX = this.leftPos + healthX;
        int absoluteY = this.topPos + healthY;
        if (isMouseInArea(mouseX, mouseY, absoluteX, absoluteY, healthWidth, healthHeight)) {
            double health = getHealthSafe();
            double healthMax = Math.max(1, getMaxHealthSafe());
            DecimalFormat decimalFormat = new DecimalFormat("#.##");
            Component line1 = Component.literal("Health: ");
            Component line2 = Component.literal(decimalFormat.format(health) + " / " + decimalFormat.format(healthMax));

            guiGraphics.renderTooltip(this.font, List.of(line1, line2), Optional.empty(), mouseX, mouseY);
        }
    }

    private double getHealthSafe() {
        try {
            return Math.max(0, this.menu.getHealth());
        } catch (Throwable t) {
            return 0;
        }
    }

    private double getMaxHealthSafe() {
        try {
            return Math.max(1, this.menu.getMaxHealth());
        } catch (Throwable t) {
            return 1;
        }
    }

    private boolean isMouseInArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawBuddyPreview(GuiGraphics guiGraphics, int previewX, int previewY, int mouseX, int mouseY) {
        guiGraphics.blit(GUI_TEXTURE, previewX, previewY, buddyPreviewU, buddyPreviewV, buddyPreviewWidth, buddyPreviewHeight, 256, 256);

        LivingEntity entity = getPreviewEntity();
        if (entity == null) return;

        int x2 = previewX + buddyPreviewWidth;
        int y2 = previewY + buddyPreviewHeight;

        final float entityHeight = Math.max(0.6f, entity.getBbHeight());
        final float targetScale = (ByteBuddyScreen.buddyPreviewHeight * 0.65f);
        int scale = Math.max(8, Mth.floor(targetScale / entityHeight));
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics, previewX, previewY, x2, y2, scale, 0.1f, mouseX, mouseY, entity
        );
    }

    private LivingEntity getPreviewEntity() {
        try {
            return this.menu.getByteBuddy();
        } catch (Throwable ignored) {}
        return null;
    }

    private void setFarmer(ByteBuddyEntity byteBuddy) {
        RoleData.setBuddyRole(byteBuddy, 1);
    }

    private void setMiner(ByteBuddyEntity byteBuddy) {
        RoleData.setBuddyRole(byteBuddy, 2);
    }

    private void setHauler(ByteBuddyEntity byteBuddy) {
        RoleData.setBuddyRole(byteBuddy, 5);
    }

    private void setFighter(ByteBuddyEntity byteBuddy) {
        RoleData.setBuddyRole(byteBuddy, 3);
    }
}
