package net.turtleboi.bytebuddies.mixin;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.turtleboi.bytebuddies.api.SeedItemProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CropBlock.class)
public abstract class CropBlockAccessor implements SeedItemProvider {
    @Invoker("getBaseSeedId")
    public abstract ItemLike bytebuddies$invokeGetBaseSeedId();

    @Override
    public ItemLike bytebuddies$getSeedItem() {
        return bytebuddies$invokeGetBaseSeedId();
    }
}
