package net.turtleboi.bytebuddies.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ClipboardItem extends Item {
    private static final String firstPos = "FirstPos";
    private static final String secondPos = "SecondPos";
    private static final String xCoord = "X";
    private static final String yCoord = "Y";
    private static final String zCoord = "Z";

    public ClipboardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack itemStack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        var player = context.getPlayer();
        boolean isClient = level.isClientSide;
        boolean isSneaking = player != null && player.isShiftKeyDown();

        if (isSneaking) {
            if (!isClient) {
                clearPositions(itemStack);
                player.displayClientMessage(Component.literal("Clipboard cleared").withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.sidedSuccess(isClient);
        }

        if (!isClient) {
            boolean hasFirst = hasFirstPosition(itemStack);
            boolean hasSecond = hasSecondPosition(itemStack);

            if (!hasFirst) {
                setFirstPosition(itemStack, clickedPos);
                if (player != null)
                    player.displayClientMessage(Component.literal("Set first position: " + format(clickedPos)).withStyle(ChatFormatting.GREEN), true);
            } else if (!hasSecond) {
                setSecondPosition(itemStack, clickedPos);
                if (player != null)
                    player.displayClientMessage(Component.literal("Set second position: " + format(clickedPos)).withStyle(ChatFormatting.GREEN), true);
            } else {
                clearSecondPosition(itemStack);
                setFirstPosition(itemStack, clickedPos);
                if (player != null)
                    player.displayClientMessage(Component.literal("Set first position: " + format(clickedPos)).withStyle(ChatFormatting.GREEN), true);
            }
        }

        return InteractionResult.sidedSuccess(isClient);
    }

    public void handleClick(Player player, ItemStack itemStack, BlockPos clickedPos) {
        boolean isSneaking = player != null && player.isShiftKeyDown();

        if (isSneaking) {
            clearPositions(itemStack);
            player.displayClientMessage(
                    Component.literal("Clipboard cleared").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        boolean hasFirst = hasFirstPosition(itemStack);
        boolean hasSecond = hasSecondPosition(itemStack);

        if (!hasFirst) {
            setFirstPosition(itemStack, clickedPos);
            if (player != null) player.displayClientMessage(
                    Component.literal("Set first position: " + format(clickedPos)).withStyle(ChatFormatting.GREEN), true);
        } else if (!hasSecond) {
            setSecondPosition(itemStack, clickedPos);
            if (player != null) player.displayClientMessage(
                    Component.literal("Set second position: " + format(clickedPos)).withStyle(ChatFormatting.GREEN), true);
        } else {
            clearSecondPosition(itemStack);
            setFirstPosition(itemStack, clickedPos);
            if (player != null) player.displayClientMessage(
                    Component.literal("Set first position: " + format(clickedPos)).withStyle(ChatFormatting.GREEN), true);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            clearPositions(itemStack);
            player.displayClientMessage(Component.literal("Clipboard cleared").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.consume(itemStack);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        Optional<BlockPos> firstPos = getFirstPosition(pStack);
        Optional<BlockPos> secondPosition = getSecondPosition(pStack);
        if (firstPos.isPresent()) {
            pTooltipComponents.add(Component.literal("First Position: " + format(firstPos.get())).withStyle(ChatFormatting.AQUA));
        } else {
            pTooltipComponents.add(Component.literal("First Position: not set").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (secondPosition.isPresent()) {
            pTooltipComponents.add(Component.literal("Second Position: " + format(secondPosition.get())).withStyle(ChatFormatting.AQUA));
        } else {
            pTooltipComponents.add(Component.literal("Second Position: not set").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (firstPos.isPresent() && secondPosition.isPresent()) {
            BlockPos aPos = firstPos.get();
            BlockPos bPos = secondPosition.get();
            int width = Math.abs(aPos.getX() - bPos.getX()) + 1;
            int height = Math.abs(aPos.getY() - bPos.getY()) + 1;
            int depth = Math.abs(aPos.getZ() - bPos.getZ()) + 1;
            pTooltipComponents.add(Component.literal("Box: " + width + "×" + height + "×" + depth).withStyle(ChatFormatting.GRAY));
        }
    }

    public static Optional<BlockPos> getFirstPosition(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        if (root == null || !root.contains(firstPos)) {
            return Optional.empty();
        }

        CompoundTag pos = root.getCompound(firstPos);
        return Optional.of(new BlockPos(
                pos.getInt(xCoord),
                pos.getInt(yCoord),
                pos.getInt(zCoord)
        ));
    }

    public static Optional<BlockPos> getSecondPosition(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        if (root == null || !root.contains(secondPos)) {
            return Optional.empty();
        }

        CompoundTag pos = root.getCompound(secondPos);
        return Optional.of(new BlockPos(
                pos.getInt(xCoord),
                pos.getInt(yCoord),
                pos.getInt(zCoord)
        ));
    }

    public static boolean hasFirstPosition(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        return root != null && root.contains(firstPos);
    }

    public static boolean hasSecondPosition(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        return root != null && root.contains(secondPos);
    }

    public static void setFirstPosition(ItemStack clipboardStack, BlockPos blockPos) {
        CompoundTag root = getOrCreateRoot(clipboardStack);
        CompoundTag dataPos = new CompoundTag();
        dataPos.putInt(xCoord, blockPos.getX());
        dataPos.putInt(yCoord, blockPos.getY());
        dataPos.putInt(zCoord, blockPos.getZ());
        root.put(firstPos, dataPos);
        // no need to setTag, root is a live reference
    }

    public static void setSecondPosition(ItemStack itemStack, BlockPos blockPos) {
        CompoundTag root = getOrCreateRoot(itemStack);
        CompoundTag dataPos = new CompoundTag();
        dataPos.putInt(xCoord, blockPos.getX());
        dataPos.putInt(yCoord, blockPos.getY());
        dataPos.putInt(zCoord, blockPos.getZ());
        root.put(secondPos, dataPos);
        // no need to setTag, root is a live reference
    }

    public static void clearPositions(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        if (root == null) return;
        root.remove(firstPos);
        root.remove(secondPos);
    }

    private static void clearFirstPosition(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        if (root == null) return;
        root.remove(firstPos);
    }

    private static void clearSecondPosition(ItemStack itemStack) {
        CompoundTag root = itemStack.getTag();
        if (root == null) return;
        root.remove(secondPos);
    }

    private static CompoundTag getOrCreateRoot(ItemStack itemStack) {
        return itemStack.getOrCreateTag();
    }


    public static Optional<Region> getRegion(ItemStack itemStack) {
        Optional<BlockPos> first = getFirstPosition(itemStack);
        Optional<BlockPos> second = getSecondPosition(itemStack);
        if (first.isEmpty() || second.isEmpty()) return Optional.empty();
        BlockPos a = first.get();
        BlockPos b = second.get();
        int xMin = Math.min(a.getX(), b.getX());
        int xMax = Math.max(a.getX(), b.getX());
        int yMin = Math.min(a.getY(), b.getY());
        int yMax = Math.max(a.getY(), b.getY());
        int zMin = Math.min(a.getZ(), b.getZ());
        int zMax = Math.max(a.getZ(), b.getZ());
        return Optional.of(new Region(new BlockPos(xMin, yMin, zMin), new BlockPos(xMax, yMax, zMax)));
    }

    public record Region(BlockPos min, BlockPos max) {}
    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
