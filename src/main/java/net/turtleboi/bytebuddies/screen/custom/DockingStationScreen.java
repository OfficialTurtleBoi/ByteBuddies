package net.turtleboi.bytebuddies.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
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
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.network.payloads.ReloadData;
import net.turtleboi.bytebuddies.network.payloads.SleepData;
import net.turtleboi.bytebuddies.screen.custom.menu.DockingStationMenu;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DockingStationScreen extends AbstractContainerScreen<DockingStationMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/gui/docking_station_gui.png");
    private static final ResourceLocation GUI_ADDONS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "textures/gui/gui_addons.png");

    private static final int energyX = 6;
    private static final int energyY = 53;
    private static final int energyWidth = 16;
    private static final int energyHeight = 52;
    private static final int energyEmptyU = 16;
    private static final int energyEmptyV = 0;
    private static final int energyFullU = 0;
    private static final int energyFullV = 0;

    private static final int buddyPreviewX = 71;
    private static final int buddyPreviewY = 6;
    private static final int buddyPreviewWidth = 38;
    private static final int buddyPreviewHeight = 38;
    private static final int buddyPreviewU = buddyPreviewX;
    private static final int buddyPreviewV = buddyPreviewY;

    private static final int infoX = 114;
    private static final int infoY = 7;
    private static final int infoW = 54;
    private static final int infoH = 22;

    private boolean debugEnergyOverride = false;
    private double debugFillPct = 0.50;

    private int selected = 0;
    private Button previousButton, nextButton, sleepButton, restartButton, teleportButton;

    public DockingStationScreen(DockingStationMenu dockingStationMenu, Inventory inventory, Component title) {
        super(dockingStationMenu, inventory, title);
        this.imageWidth = 240;
        this.imageHeight = 227;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - this.imageWidth) / 2;
        int y = (height - this.imageHeight) / 2;

        int bx = x + buddyPreviewX;
        int by = y + buddyPreviewY;

        previousButton = addRenderableWidget(
                Button.builder(Component.literal("<"), button -> cycleBuddy(-1))
                        .bounds(bx - 20, by + buddyPreviewHeight / 2 - 6, 12, 12)
                        .build()
        );

        nextButton = addRenderableWidget(
                Button.builder(Component.literal(">"), button -> cycleBuddy(+1))
                        .bounds(bx + buddyPreviewWidth + 68, by + buddyPreviewHeight / 2 - 6, 12, 12)
                        .build()
        );

        sleepButton = addRenderableWidget(
                Button.builder(Component.literal("S"), button -> asleepBuddy(Objects.requireNonNull(this.menu.getBuddyByIndexClient(selected))))
                        .bounds(bx+44,   by+26, 12, 12)
                        .build()
        );


        restartButton = addRenderableWidget(
                Button.builder(Component.literal("↺"), button -> restartBuddy(Objects.requireNonNull(this.menu.getBuddyByIndexClient(selected))))
                        .bounds(bx+64,   by+26, 12, 12)
                        .build()
        );
        teleportButton = addRenderableWidget(
                Button.builder(Component.literal("TP"), button -> asleepBuddy(Objects.requireNonNull(this.menu.getBuddyByIndexClient(selected))))
                        .bounds(bx+84,   by+26, 12, 12)
                        .build()
        );
        updateNavState();


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
        drawBuddyPreview(guiGraphics, x + buddyPreviewX, y + buddyPreviewY, mouseX, mouseY);
        drawBuddyInfo(guiGraphics, x + infoX, y + infoY);
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

    private boolean isMouseInArea(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawBuddyPreview(GuiGraphics guiGraphics, int previewX, int previewY, int mouseX, int mouseY) {
        guiGraphics.blit(GUI_TEXTURE, previewX, previewY, buddyPreviewU, buddyPreviewV, buddyPreviewWidth, buddyPreviewHeight, 256, 256);

        LivingEntity entity = this.menu.getBuddyByIndexClient(selected);
        if (entity == null) return;

        int x2 = previewX + buddyPreviewWidth;
        int y2 = previewY + buddyPreviewHeight;

        final float entityHeight = Math.max(0.6f, entity.getBbHeight());
        final float targetScale = (DockingStationScreen.buddyPreviewHeight * 0.65f);
        int scale = Math.max(8, Mth.floor(targetScale / entityHeight));
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics, previewX, previewY, x2, y2, scale, 0.1f, mouseX, mouseY, entity
        );
    }

    private void drawBuddyInfo(GuiGraphics guiGraphics, int boxX, int boxY) {
        var entity = this.menu.getBuddyByIndexClient(selected);
        if (entity == null) return;

        String name = entity.getName().getString();
        String role = (entity instanceof ByteBuddyEntity byteBuddy) ? byteBuddy.getBuddyRole().name() : "Unknown";
        var pos = entity.blockPosition();
        String coords = "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";

        String[] lines = { name, "Role: " + role, coords };

        int lineHeight = this.font.lineHeight;
        int rawHeight = lines.length * lineHeight;
        int maxWidth = 0;
        for (String string : lines) maxWidth = Math.max(maxWidth, this.font.width(string));
        if (maxWidth == 0 || rawHeight == 0) return;

        float scaleX = (float) infoW / (float) maxWidth;
        float scaleY = (float) infoH / (float) rawHeight;
        float scale = Math.min(scaleX, scaleY);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(boxX, boxY, 0);
        pose.scale(scale, scale, 1f);

        int drawAreaWScaled = Mth.floor(infoW / scale);
        int drawAreaHScaled = Mth.floor(infoH / scale);
        int y = Math.max(0, (drawAreaHScaled - rawHeight) / 2);

        for (String string : lines) {
            int fontWidth = this.font.width(string);
            int x = (drawAreaWScaled - fontWidth) / 2;
            guiGraphics.drawString(this.font, string, x, y, 0xFFFFFF, false);
            y += lineHeight;
        }
        pose.popPose();
    }

    private void cycleBuddy(int delta) {
        int count = this.menu.getBuddyCount();
        if (count <= 0) return;
        selected = (selected + delta) % count;
        if (selected < 0) selected += count;
        updateNavState();
    }
    private void asleepBuddy(ByteBuddyEntity byteBuddyEntity) {
        boolean asleep = byteBuddyEntity.isSleeping();
        SleepData.setAsleep(byteBuddyEntity,!asleep);
    }
    private void restartBuddy(ByteBuddyEntity byteBuddyEntity) {
        boolean asleep = byteBuddyEntity.isSleeping();
        ReloadData.setreload(byteBuddyEntity);
    }





    private void updateNavState() {
        int count = this.menu.getBuddyCount();
        boolean enable = count > 1;
        if (previousButton != null) previousButton.active = enable;
        if (nextButton != null) nextButton.active = enable;
        sleepButton.active = count > 0;
        restartButton.active = count > 0;
        teleportButton.active = count > 0;
        if (count == 0) selected = 0;
        else if (selected >= count) selected = count - 1;
    }
}
