package com.jjkdomains.items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class GuiltyVerdictSword extends SwordItem {

    public GuiltyVerdictSword(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.level().isClientSide()) {
            float damage = target.getMaxHealth() + 100f;
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + 1, target.getZ(),
                        40, 0.5, 0.8, 0.5, 0.5);
                serverLevel.players().forEach(p ->
                        p.sendSystemMessage(Component.literal(
                                "§c⚖ " + target.getName().getString() + " has been sentenced to death.")));
            }
            target.hurt(target.level().damageSources().magic(), damage);
            stack.hurtAndBreak(stack.getMaxDamage(), attacker,
                    LivingEntity.getSlotForHand(attacker.getUsedItemHand()));
        }
        return true;
    }

    @Override
    public Rarity getRarity(ItemStack stack) { return Rarity.EPIC; }
}
