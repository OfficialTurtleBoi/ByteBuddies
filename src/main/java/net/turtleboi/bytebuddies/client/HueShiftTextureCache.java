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

        if (baseTexture.getWidth() != 64 || baseTexture.getHeight() != 64) {

        }

        NativeImage newTexture = new NativeImage(
                baseTexture.format(), baseTexture.getWidth(), baseTexture.getHeight(), false);
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
        ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(
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

        boolean targetIsGray = (Math.abs(targetRed - targetGreen) <= 1) && (Math.abs(targetGreen - targetBlue) <= 1);
        float[] sourceHsv = rgbToHsv(red, green, blue);
        float sourceSaturation = sourceHsv[1];
        float sourceValue = sourceHsv[2];

        if (targetIsGray) {
            float targetValue = Math.max(targetRed, Math.max(targetGreen, targetBlue)) / 255f;
            float outputValue = clamp(sourceValue * targetValue);
            int outputRgb = hsvToRgb(0f, 0f, outputValue);
            return (alpha << 24) | outputRgb;
        }

        float[] targetHsv = rgbToHsv(targetRed, targetGreen, targetBlue);
        float outputHue = targetHsv[0];
        int outputRgb = hsvToRgb(outputHue, sourceSaturation, sourceValue);
        return (alpha << 24) | outputRgb;
    }

    private static float clamp(float x) {
        return x < 0f ? 0f : (Math.min(x, 1f));
    }

    private static float[] rgbToHsv(int red, int green, int blue) {
        float redFloat = red / 255f;
        float greenFloat = green / 255f;
        float blueFloat = blue / 255f;
        float maximum = Math.max(redFloat, Math.max(greenFloat, blueFloat));
        float minimum = Math.min(redFloat, Math.min(greenFloat, blueFloat));
        float delta = maximum - minimum;
        float hue;
        if (delta == 0f) hue = 0f;
        else if (maximum == redFloat) hue = ((greenFloat - blueFloat) / delta) % 6f;
        else if (maximum == greenFloat) hue = ((blueFloat - redFloat) / delta) + 2f;
        else hue = ((redFloat - greenFloat) / delta) + 4f;
        hue /= 6f;
        if (hue < 0f) hue += 1f;
        float saturation = (maximum == 0f) ? 0f : (delta / maximum);
        return new float[]{hue, saturation, maximum};
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float chroma = value * saturation;
        float xComponent = chroma * (1f - Math.abs(((hue * 6f) % 2f) - 1f));
        float match = value - chroma;
        float redPrime = 0f;
        float greenPrime = 0f;
        float bluePrime = 0f;
        float hueSector = hue * 6f;
        if (hueSector < 1f) {
            redPrime = chroma; greenPrime = xComponent;
        }
        else if (hueSector < 2f) {
            redPrime = xComponent; greenPrime = chroma;
        }
        else if (hueSector < 3f) {
            greenPrime = chroma; bluePrime = xComponent;
        }
        else if (hueSector < 4f) {
            greenPrime = xComponent; bluePrime = chroma;
        }
        else if (hueSector < 5f) {
            redPrime = xComponent; bluePrime = chroma;
        }
        else { redPrime = chroma; bluePrime = xComponent;
        }
        int redOut = Math.round((redPrime + match) * 255f);
        int greenOut = Math.round((greenPrime + match) * 255f);
        int blueOut = Math.round((bluePrime + match) * 255f);
        return (redOut << 16) | (greenOut << 8) | blueOut;
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