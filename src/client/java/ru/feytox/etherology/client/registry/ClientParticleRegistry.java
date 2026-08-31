package ru.feytox.etherology.client.registry;

import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.PendingParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import ru.feytox.etherology.client.particle.*;

import static ru.feytox.etherology.registry.particle.SharedParticleTypes.*;

@UtilityClass
public class ClientParticleRegistry {
    public static void registerAll() {
        register(LIGHT.get(), LightParticle::new);
        register(STEAM.get(), SteamParticle::new);
        register(SPARK.get(), SparkParticle::new);
        register(ELECTRICITY1.get(), ElectricityParticle::new);
        register(ELECTRICITY2.get(), ElectricityParticle::new);
        register(ITEM.get(), ItemParticle::new);
        register(RISING.get(), RisingParticle::new);
        register(VITAL.get(), VitalParticle::new);
        register(SHOCKWAVE.get(), ShockwaveParticle::new);
        register(GLINT.get(), GlintParticle::new);
        register(ENERGY_ABSORPTION.get(), EnergyAbsorptionParticle::new);
        register(ARMILLARY_SPHERE.get(), SphereParticle::new);
        register(HAZE.get(), HazeParticle::new);
        register(ALCHEMY.get(), AlchemyParticle::new);
        register(ETHER_STAR.get(), EtherParticle.EtherStarParticle::new);
        register(ETHER_DOT.get(), EtherParticle.EtherDotParticle::new);
        register(RESONATION.get(), ResonationParticle::new);
        register(LIGHTNING_BOLT.get(), LightningBoltParticle::new);
        register(SCALABLE_SWEEP.get(), ScalableSweepParticle::new);
        register(REDSTONE_FLASH.get(), RedstoneFlashParticle::new);
        register(REDSTONE_STREAM.get(), RedstoneStreamParticle::new);
        register(SEAL.get(), SealParticle::new);
    }

    private static <T extends ParticleEffect, P extends Particle> void register(ParticleType<T> particleType, ParticleConstructor<T, P> particleConstructor) {
        var factory = createFactory(particleConstructor);
        ParticleFactoryRegistry.getInstance().register(particleType, factory);
    }

    public static <T extends ParticleEffect, P extends Particle> PendingParticleFactory<T> createFactory(ParticleConstructor<T, P> particleConstructor) {
        return (spriteProvider) ->
                (ParticleFactory<T>) (parameters, world, x, y, z, velocityX, velocityY, velocityZ) ->
                        particleConstructor.create(world, x, y, z, parameters, spriteProvider);
    }

    @FunctionalInterface
    public interface ParticleConstructor<T extends ParticleEffect, P extends Particle> {
        P create(ClientWorld clientWorld, double x, double y, double z, T parameters, SpriteProvider spriteProvider);
    }
}
