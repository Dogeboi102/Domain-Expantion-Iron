package com.jjkdomains.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public class MalevolentShrineEffect extends MobEffect {

    public MalevolentShrineEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000); // Dark crimson color
    }

    @Override
    public boolean shouldApplyEffectTickThisGame(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Visual cutting effect: spawn damage indicator particles
        if (entity.level() instanceof ServerLevel serverLevel) {
            // Blood slash particles
            serverLevel.sendParticles(
                    ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX() + (Math.random() - 0.5) * 1.2,
                    entity.getY() + Math.random() * 2,
                    entity.getZ() + (Math.random() - 0.5) * 1.2,
                    2, 0.2, 0.2, 0.2, 0.1
            );

            // Sweep attack effect every few ticks
            if (entity.tickCount % 10 == 0) {
                serverLevel.sendParticles(
                        ParticleTypes.SWEEP_ATTACK,
                        entity.getX(), entity.getY() + 1, entity.getZ(),
                        3, 0.5, 0.3, 0.5, 0.1
                );
            }
        }
        return true;
    }
}
