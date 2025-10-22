package net.turtleboi.bytebuddies.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.turtleboi.bytebuddies.ByteBuddies;

public class AttributeModifierUtil {
    public static void applyPermanentModifier(LivingEntity livingEntity, Holder<Attribute> attributeHolder, String name, double value, AttributeModifier.Operation operation) {
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name);
        AttributeInstance attributeInstance = livingEntity.getAttribute(attributeHolder);
        if (attributeInstance != null) {
            if (attributeInstance.getModifier(modifierId) != null) {
                attributeInstance.removeModifier(modifierId);
            }
            attributeInstance.addPermanentModifier(new AttributeModifier(modifierId, value, operation));
        }
    }

    public static void applyTransientModifier(LivingEntity livingEntity, Holder<Attribute> attributeHolder, String name, double value, AttributeModifier.Operation operation) {
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name);
        AttributeInstance attributeInstance = livingEntity.getAttribute(attributeHolder);
        if (attributeInstance != null) {
            if (attributeInstance.getModifier(modifierId) != null) {
                attributeInstance.removeModifier(modifierId);
            }
            attributeInstance.addTransientModifier(new AttributeModifier(modifierId, value, operation));
        }
    }

    public static void removeModifier(LivingEntity livingEntity, Holder<Attribute> attributeHolder, String name) {
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, name);
        AttributeInstance attributeInstance = livingEntity.getAttribute(attributeHolder);
        if (attributeInstance != null) {
            attributeInstance.removeModifier(modifierId);
        }
    }

    public static void removeModifiersByPrefix(LivingEntity livingEntity, Holder<Attribute> attributeHolder, String prefix) {
        AttributeInstance attributeInstance = livingEntity.getAttribute(attributeHolder);
        if (attributeInstance != null) {
            attributeInstance.getModifiers().stream()
                    .map(AttributeModifier::id)
                    .filter(id -> id.getPath().startsWith(prefix))
                    .forEach(attributeInstance::removeModifier);
        }
    }

}
