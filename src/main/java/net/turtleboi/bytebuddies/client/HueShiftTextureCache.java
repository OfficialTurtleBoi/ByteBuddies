package net.turtleboi.bytebuddies.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HueShiftTextureCache {
    private final ResourceLocation originalTexture;
    private final Map<Integer, ResourceLocation> cachedLocation = new HashMap<>();
    private final List<Rect> recolorRegions;
    private final String keyPrefix;

    public record Rect(int x, int y, int w, int h) {}

    public HueShiftTextureCache(ResourceLocation originalTexture, String keyPrefix, List<Rect> recolorRegions) {
        this.originalTexture = originalTexture;
        this.keyPrefix = keyPrefix;
        this.recolorRegions = recolorRegions;
    }

    public ResourceLocation getOrCreate(int targetRGB) {
        return cachedLocation.computeIfAbsent(targetRGB, this::hueShift);
    }

    private ResourceLocation hueShift(int targetRGB) {
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage baseTexture;
        try (var inputLocation = minecraft.getResourceManager().open(originalTexture)) {
            baseTexture = NativeImage.read(inputLocation);
        } catch (IOException ioException) {
            return originalTexture;
        }

        NativeImage newTexture = new NativeImage(baseTexture.format(), baseTexture.getWidth(), baseTexture.getHeight(), false);
        for (int y = 0; y < baseTexture.getHeight(); y++) {
            for (int x = 0; x < baseTexture.getWidth(); x++) {
                int abgr = baseTexture.getPixelRGBA(x, y);
                int argb = abgrToArgb(abgr);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    newTexture.setPixelRGBA(x, y, abgr);
                    continue;
                }
                if (recolorRegions.isEmpty() || inAnyRect(x, y, recolorRegions)) {
                    int recoloredARGB = hueReplaceARGB(argb, targetRGB);
                    newTexture.setPixelRGBA(x, y, argbToAbgr(recoloredARGB));
                } else {
                    newTexture.setPixelRGBA(x, y, abgr);
                }
            }
        }

        DynamicTexture dynamicTexture = new DynamicTexture(newTexture);
        String hexCode = String.format(Locale.ROOT, "%06x", (targetRGB & 0xFFFFFF));
        String imagePath = keyPrefix + hexCode;
        ResourceLocation resourceLocation = new ResourceLocation(
                originalTexture.getNamespace(), imagePath.toLowerCase(Locale.ROOT));
        minecraft.getTextureManager().register(resourceLocation, dynamicTexture);
        return resourceLocation;
    }

    private static boolean inAnyRect(int x, int y, List<Rect> rectList) {
        for (Rect rect : rectList) {
            if (x >= rect.x && x < rect.x + rect.w && y >= rect.y && y < rect.y + rect.h) return true;
        }
        return false;
    }

    private static int hueReplaceARGB(int argb, int targetRGB) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;

        int blackThreshold = 1;
        if (red <= blackThreshold && green <= blackThreshold && blue <= blackThreshold) {
            return (alpha << 24);
        }

        int targetRed = (targetRGB >>> 16) & 0xFF;
        int targetGreen = (targetRGB >>> 8) & 0xFF;
        int targetBlue = targetRGB & 0xFF;
        if (targetRed == 0 && targetGreen == 0 && targetBlue == 0) {
            return (alpha << 24);
        }

        float sourceValue = Math.max(red, Math.max(green, blue)) / 255f;
        int redOut = Math.round(targetRed * sourceValue);
        int greenOut = Math.round(targetGreen * sourceValue);
        int blueOut = Math.round(targetBlue * sourceValue);
        return (alpha << 24) | (redOut << 16) | (greenOut << 8) | blueOut;
    }

    static int abgrToArgb(int abgr) {
        int alpha = (abgr >>> 24) & 0xFF;
        int blue = (abgr >>> 16) & 0xFF;
        int green = (abgr >>> 8) & 0xFF;
        int red = abgr & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    static int argbToAbgr(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }
}