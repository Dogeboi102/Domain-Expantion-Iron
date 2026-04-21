package com.jjkdomains.items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class GuiltyVerdictSword extends SwordItem {

    public GuiltyVerdictSword(Properties properties) {
        super(Tiers.NETHERITE, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // One-shot: set target health to 0 (instant kill)
        if (!target.level().isClientSide()) {
            // Deal maximum possible damage for an instant kill
            float damage = target.getMaxHealth() + 100f;

            if (target.level() instanceof ServerLevel serverLevel) {
                // Dramatic execution particles
                serverLevel.sendParticles(
                        ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + 1, target.getZ(),
                        40, 0.5, 0.8, 0.5, 0.5
                );
                serverLevel.sendParticles(
                        ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + 1, target.getZ(),
                        30, 0.3, 0.5, 0.3, 0.3
                );

                // Broadcast verdict execution
                serverLevel.players().forEach(p ->
                        p.sendSystemMessage(Component.literal(
                                "§c⚖ " + target.getName().getString() + " §7has been sentenced to death. §c⚖"
                        ))
                );
            }

            target.hurt(target.level().damageSources().magic(), damage);

            // Break (consume) the sword after one hit — durability 1, so damage it fully
            stack.hurtAndBreak(stack.getMaxDamage(), attacker, LivingEntity.getSlotForHand(attacker.getUsedItemHand()));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§c⚖ Verdict: GUILTY§r"));
        tooltip.add(Component.literal("§7One strike. One death.§r"));
        tooltip.add(Component.literal("§8Breaks after use.§r"));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.EPIC;
    }
}
