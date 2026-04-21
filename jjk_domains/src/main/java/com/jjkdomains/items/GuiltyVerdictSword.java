package com.jjkdomains.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
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
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + 1, target.getZ(),
                        30, 0.3, 0.5, 0.3, 0.3);
                serverLevel.players().forEach(p ->
                        p.sendSystemMessage(Component.literal(
                                "§c⚖ " + target.getName().getString() + " §7has been sentenced to death. §c⚖")));
            }
            target.hurt(target.level().damageSources().magic(), damage);
            stack.hurtAndBreak(stack.getMaxDamage(), attacker,
                    LivingEntity.getSlotForHand(attacker.getUsedItemHand()));
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§c⚖ Verdict: GUILTY§r"));
        tooltip.add(Component.literal("§7One strike. One death.§r"));
        tooltip.add(Component.literal("§8Breaks after use.§r"));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public Rarity getRarity(ItemStack stack) { return Rarity.EPIC; }
}
