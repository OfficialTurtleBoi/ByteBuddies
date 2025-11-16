package net.turtleboi.bytebuddies.events;

import net.minecraft.client.particle.SpellParticle;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.particle.ModParticles;

@Mod.EventBusSubscriber(modid = ByteBuddies.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

}
