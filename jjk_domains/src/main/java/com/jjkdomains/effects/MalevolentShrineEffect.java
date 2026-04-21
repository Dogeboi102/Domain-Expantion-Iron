package com.jjkdomains.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public class MalevolentShrineEffect extends MobEffect {

    public MalevolentShrineEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    entity.getX() + (Math.random() - 0.5) * 1.2,
                    entity.getY() + Math.random() * 2,
                    entity.getZ() + (Math.random() - 0.5) * 1.2,
                    2, 0.2, 0.2, 0.2, 0.1);
        }
        return true;
    }

    @Override
    public int getDurationSeconds() { return 1; }
}
