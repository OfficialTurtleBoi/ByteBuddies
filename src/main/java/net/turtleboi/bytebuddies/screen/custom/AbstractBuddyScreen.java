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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.network.ModNetworking;
import net.turtleboi.bytebuddies.network.packets.RoleDataC2SPacket;
import net.turtleboi.bytebuddies.screen.custom.widget.TinyIconButton;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;

public abstract class AbstractBuddyScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    protected static final ResourceLocation GUI_ADDONS_TEXTURE = new ResourceLocation(ByteBuddies.MOD_ID, "textures/gui/gui_addons.png");

    private static final int BAR_Y = 64;
    private static final int BAR_HEIGHT = 52;

    private static final int ENERGY_X = 6;
    private static final int ENERGY_WIDTH = 16;
    private static final int ENERGY_EMPTY_U = 16, ENERGY_EMPTY_V = 0;
    private static final int ENERGY_FULL_U = 0, ENERGY_FULL_V  = 0;

    private static final int HEALTH_WIDTH = 16;
    private static final int HEALTH_EMPTY_U = 16, HEALTH_EMPTY_V = 52;
    private static final int HEALTH_FULL_U = 0, HEALTH_FULL_V  = 52;

    private static final int PREVIEW_Y = 6;
    private static final int PREVIEW_WIDTH = 38;
    private static final int PREVIEW_HEIGHT = 38;
    private static final int[] ROLE_OFFSETS = { 0, 15, 31, 46 };

    public record LayoutConfig(ResourceLocation guiTexture, int imageWidth, int mainAtlasW, int buddyPreviewX, int healthX, int roleButtonBaseX) {}
    private final LayoutConfig config;

    @Nullable
    protected abstract ByteBuddyEntity getBuddy();

    protected abstract int getEnergyStored();
    protected abstract int getMaxEnergyStored();
    protected abstract double getHealth();
    protected abstract double getMaxHealth();

    protected AbstractBuddyScreen(M menu, Inventory inventory, Component title, LayoutConfig config) {
        super(menu, inventory, title);
        this.config = config;
        this.imageWidth  = config.imageWidth();
        this.imageHeight = 227;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width  - this.imageWidth)  / 2;
        int y = (height - this.imageHeight) / 2;
        int baseX   = x + config.roleButtonBaseX();
        int buttonY = y + 124;
        addRoleButton(baseX + ROLE_OFFSETS[0], buttonY, 1, "Farmer", 8, 112, 8, 104);
        addRoleButton(baseX + ROLE_OFFSETS[1], buttonY, 2, "Miner", 0, 112, 0, 104);
        addRoleButton(baseX + ROLE_OFFSETS[2], buttonY, 5, "Hauler", 16, 112, 16, 104);
        addRoleButton(baseX + ROLE_OFFSETS[3], buttonY, 3, "Fighter", 24, 112, 24, 104);
    }

    private void addRoleButton(int bx, int by, int roleId, String label, int uNormal, int vNormal, int uPressed, int vPressed) {
        addRenderableWidget(
                Button.builder(Component.empty(), b -> {
                            ByteBuddyEntity buddy = getBuddy();
                            if (buddy != null) ModNetworking.sendToServer(
                                    new RoleDataC2SPacket(buddy.getId(), roleId));
                })
                        .bounds(bx, by, 12, 12)
                        .tooltip(Tooltip.create(Component.literal(label)))
                        .build(TinyIconButton.buttonFactoryWithIcon(
                                GUI_ADDONS_TEXTURE, 128, 128,
                                32, 0, null,
                                new TinyIconButton.MiniIcon(GUI_ADDONS_TEXTURE, 128, 128,
                                        uNormal, vNormal, uPressed, vPressed, 2, 2)
                        ))
        );
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.blit(config.guiTexture(), x, y, 0, 0, this.imageWidth, this.imageHeight, config.mainAtlasW(), 256);
        drawBuddyPreview(guiGraphics,
                x + config.buddyPreviewX(),
                y + PREVIEW_Y,
                (x + config.buddyPreviewX() + (PREVIEW_WIDTH / 2f)) - mouseX,
                (y + PREVIEW_Y + (PREVIEW_HEIGHT / 2f)) - mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (width  - this.imageWidth)  / 2;
        int y = (height - this.imageHeight) / 2;

        drawEnergyBar(guiGraphics, x + ENERGY_X,         y + BAR_Y);
        drawHealthBar(guiGraphics, x + config.healthX(), y + BAR_Y);

        this.renderTooltip(guiGraphics, mouseX, mouseY);
        drawEnergyTooltip(guiGraphics, mouseX, mouseY);
        drawHealthTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawEnergyBar(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, y, ENERGY_EMPTY_U, ENERGY_EMPTY_V,
                ENERGY_WIDTH, BAR_HEIGHT, 128, 128);

        int filled = computeFill(getEnergyStoredSafe(), getMaxEnergyStoredSafe(), BAR_HEIGHT);
        if (filled <= 0) return;

        int dy = BAR_HEIGHT - filled;
        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, y + dy, ENERGY_FULL_U, ENERGY_FULL_V + dy,
                ENERGY_WIDTH, filled, 128, 128);
    }

    private void drawHealthBar(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, y, HEALTH_EMPTY_U, HEALTH_EMPTY_V,
                HEALTH_WIDTH, BAR_HEIGHT, 128, 128);

        int filled = computeFill(getHealthSafe(), getMaxHealthSafe(), BAR_HEIGHT);
        if (filled <= 0) return;

        int dy = BAR_HEIGHT - filled;
        guiGraphics.blit(GUI_ADDONS_TEXTURE, x, y + dy, HEALTH_FULL_U, HEALTH_FULL_V + dy,
                HEALTH_WIDTH, filled, 128, 128);
    }

    private static int computeFill(double value, double max, int barHeight) {
        return Mth.clamp((int) Math.round(value * barHeight / Math.max(1.0, max)), 0, barHeight);
    }

    private void drawEnergyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int ax = this.leftPos + ENERGY_X;
        int ay = this.topPos  + BAR_Y;
        if (!isMouseInArea(mouseX, mouseY, ax, ay, ENERGY_WIDTH, BAR_HEIGHT)) return;

        NumberFormat integerInstance = NumberFormat.getIntegerInstance();
        long energy = getEnergyStoredSafe();
        long energyMax = getMaxEnergyStoredSafe();
        guiGraphics.renderTooltip(this.font, List.of(Component.literal("Energy: "),
                Component.literal(integerInstance.format(energy) + " / " + integerInstance.format(energyMax) + " FE")
        ), Optional.empty(), mouseX, mouseY);
    }

    private void drawHealthTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int ax = this.leftPos + config.healthX();
        int ay = this.topPos + BAR_Y;
        if (!isMouseInArea(mouseX, mouseY, ax, ay, HEALTH_WIDTH, BAR_HEIGHT)) return;

        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        double health = getHealthSafe();
        double healthMax = getMaxHealthSafe();
        guiGraphics.renderTooltip(this.font, List.of(Component.literal("Health: "),
                Component.literal(decimalFormat.format(health) + " / " + decimalFormat.format(healthMax))
        ), Optional.empty(), mouseX, mouseY);
    }

    private void drawBuddyPreview(GuiGraphics guiGraphics, int previewX, int previewY, float mouseOffX, float mouseOffY) {
        guiGraphics.blit(config.guiTexture(), previewX, previewY, config.buddyPreviewX(), PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT, 512, 256);

        LivingEntity entity = getPreviewEntitySafe();
        if (entity == null) return;

        int scale = Math.max(8, Mth.floor((PREVIEW_HEIGHT * 0.65f) / Math.max(0.6f, entity.getBbHeight())));
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics, previewX + PREVIEW_WIDTH / 2, previewY + PREVIEW_HEIGHT - 4,
                scale, mouseOffX, mouseOffY, entity);
    }

    @Nullable
    private LivingEntity getPreviewEntitySafe() {
        try {
            return getBuddy();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private long getEnergyStoredSafe() {
        try {
            return Math.max(0, getEnergyStored());
        } catch (Throwable throwable) {
            return 0; }
    }

    private long getMaxEnergyStoredSafe() {
        try {
            return Math.max(1, getMaxEnergyStored());
        } catch (Throwable throwable) {
            return 1;
        }
    }

    private double getHealthSafe() {
        try {
            return Math.max(0, getHealth());
        } catch (Throwable throwable) {
            return 0;
        }
    }

    private double getMaxHealthSafe() {
        try {
            return Math.max(1, getMaxHealth());
        } catch (Throwable throwable) {
            return 1;
        }
    }

    private boolean isMouseInArea(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
