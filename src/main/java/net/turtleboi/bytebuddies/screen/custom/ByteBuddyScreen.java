package net.turtleboi.bytebuddies.screen.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.screen.custom.menu.ByteBuddyMenu;

import javax.annotation.Nullable;

public class ByteBuddyScreen extends AbstractBuddyScreen<ByteBuddyMenu> {

    private static final LayoutConfig CONFIG = new LayoutConfig(
            new ResourceLocation(ByteBuddies.MOD_ID, "textures/gui/bytebuddy_gui.png"),
            202,
            256,
            82,
            180,
            72
    );

    public ByteBuddyScreen(ByteBuddyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CONFIG);
    }

    @Override @Nullable
    protected ByteBuddyEntity getBuddy() { return menu.byteBuddy; }

    @Override
    protected int getEnergyStored() { return menu.getEnergyStored(); }

    @Override
    protected int getMaxEnergyStored() { return menu.getMaxEnergyStored(); }

    @Override
    protected double getHealth() { return menu.getHealth(); }

    @Override
    protected double getMaxHealth() { return menu.getMaxHealth(); }
}
