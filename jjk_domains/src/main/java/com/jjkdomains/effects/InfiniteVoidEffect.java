package com.jjkdomains.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public class InfiniteVoidEffect extends MobEffect {

    public InfiniteVoidEffect() {
        super(MobEffectCategory.HARMFUL, 0x1A0A2E); // Deep purple-black color
    }

    /**
     * Called every tick while the effect is active on an entity.
     * Returns true to indicate we want applyEffectTick to be called.
     */
    @Override
    public boolean shouldApplyEffectTickThisGame(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Zero out all movement velocity — entity is completely frozen
        entity.setDeltaMovement(0, entity.getDeltaMovement().y < 0 ? entity.getDeltaMovement().y : 0, 0);
        // Also prevent jumping/knockback
        entity.hurtMarked = false;

        // Spawn void particles around the frozen player
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 1, entity.getZ(),
                    3, 0.4, 0.8, 0.4, 0.05
            );
        }
        return true;
    }
}
