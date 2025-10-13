package net.turtleboi.bytebuddies.block;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.block.custom.BluestoneOreBlock;
import net.turtleboi.bytebuddies.block.custom.DockingStationBlock;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.ByteBuddies;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ByteBuddies.MOD_ID);

    public static final DeferredBlock<Block> ALUMINUM_ORE = registerBlock("aluminum_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> DEEPSLATE_ALUMINUM_ORE = registerBlock("deepslate_aluminum_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> ALUMINUM_BLOCK = registerBlock("aluminum_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    public static final DeferredBlock<Block> BLUESTONE_ORE = registerBlock("bluestone_ore",
            () -> new BluestoneOreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .requiresCorrectToolForDrops()
                            .randomTicks()
                            .lightLevel(litBlockEmission(9))
                            .strength(3.0F, 3.0F)
            ));

    public static final DeferredBlock<Block> DEEPSLATE_BLUESTONE_ORE = registerBlock("deepslate_bluestone_ore",
            () -> new BluestoneOreBlock(
                    BlockBehaviour.Properties.ofFullCopy(BLUESTONE_ORE.get())
                            .mapColor(MapColor.DEEPSLATE)
                            .strength(4.5F, 3.0F)
                            .sound(SoundType.DEEPSLATE)));

    public static final DeferredBlock<Block> BLUESTONE_BLOCK = registerBlock("bluestone_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.REDSTONE_BLOCK)));

    public static final DeferredBlock<Block> DOCKING_STATION = registerBlock("docking_station",
            () -> new DockingStationBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block){
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block){
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus){
        BLOCKS.register(eventBus);
    }

    private static ToIntFunction<BlockState> litBlockEmission(int lightValue) {
        return blockState -> blockState.getValue(BlockStateProperties.LIT) ? lightValue : 0;
    }
}
