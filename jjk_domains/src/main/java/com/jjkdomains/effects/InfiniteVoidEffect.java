package com.jjkdomains.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public class InfiniteVoidEffect extends MobEffect {

    public InfiniteVoidEffect() {
        super(MobEffectCategory.HARMFUL, 0x1A0A2E);
    }

    @Override
    public boolean shouldApplyEffectTickThisGame(int pDuration, int pAmplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.setDeltaMovement(0, Math.min(0, entity.getDeltaMovement().y), 0);
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    3, 0.4, 0.8, 0.4, 0.05);
        }
        return true;
    }
}
