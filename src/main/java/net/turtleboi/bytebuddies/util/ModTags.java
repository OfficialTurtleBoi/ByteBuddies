package net.turtleboi.bytebuddies.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.turtleboi.bytebuddies.ByteBuddies;

public final class ModTags {
    public static final class Blocks {
        public static final TagKey<Block> GENERATORS = createTag("generators");

        private static TagKey<Block> createTag(String name){
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name));
        }
    }

    public static final class Items {
        public static final TagKey<Item> AUGMENT = createTag("augment");
        public static final TagKey<Item> BATTERY = createTag("battery");

        private static TagKey<Item> createTag(String name){
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name));
        }
    }
}
