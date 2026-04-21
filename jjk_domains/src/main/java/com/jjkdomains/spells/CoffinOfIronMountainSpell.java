package com.jjkdomains.spells;

import com.jjkdomains.dimension.DimensionRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class CoffinOfIronMountainSpell extends AbstractSpell {

    private static final int DOMAIN_RADIUS = 20;
    private static final int BASE_DURATION_TICKS = 180;
    private static final float BASE_DAMAGE_PER_SECOND = 6.0f;
    private static final int WEAKNESS_AMPLIFIER = 2;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchool(SchoolType.FIRE)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    public CoffinOfIronMountainSpell() {
        this.baseSpellPower = 15;
        this.spellPowerPerLevel = 7;
        this.baseManaCost = 50;
        this.manaCostPerLevel = 15;
        this.castTime = 35;
    }

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public CastType getCastType() { return CastType.LONG; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("jjkdomains", "coffin_of_iron_mountain");
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.STONE_BREAK);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.GENERIC_EXPLODE.value());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (DomainTickScheduler.cancelExistingDomain(entity)) {
            serverLevel.players().forEach(p ->
                    p.sendSystemMessage(Component.literal("§6§lCoffin of the Iron Mountain§r§7 has been dispelled.")));
            return;
        }

        Vec3 casterPos = entity.position();
        int durationTicks = BASE_DURATION_TICKS + (spellLevel * 30);
        float damagePerSecond = BASE_DAMAGE_PER_SECOND + (spellLevel * 2.0f);

        serverLevel.players().forEach(p -> {
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§6§lDomain Expansion: Coffin of the Iron Mountain§r"));
            p.sendSystemMessage(Component.literal("§7§o\"Scorched and crushed beneath the mountain...\"§r"));
            p.sendSystemMessage(Component.literal(""));
        });

        List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(p ->
                p != entity && p.distanceTo(entity) <= DOMAIN_RADIUS);

        com.jjkdomains.dimension.DomainSession session =
                com.jjkdomains.dimension.DomainTeleportManager.openDomain(
                        serverLevel.getServer(), entity, nearbyPlayers,
                        com.jjkdomains.dimension.DomainInteriorType.COFFIN_OF_IRON_MOUNTAIN);

        ServerLevel domainLevel = serverLevel.getServer().getLevel(DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel != null) {
            domainLevel.getPlayers(p -> p != entity)
                    .forEach(target -> applyDomainEffects(domainLevel, target, durationTicks, WEAKNESS_AMPLIFIER));
        }

        DomainTickScheduler.scheduleCoffinOfIronMountain(serverLevel, entity, casterPos,
                DOMAIN_RADIUS, durationTicks, damagePerSecond, WEAKNESS_AMPLIFIER, session);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    public static void applyDomainEffects(ServerLevel level, ServerPlayer target,
                                           int durationTicks, int weaknessAmplifier) {
        boolean hasFireResistance = target.hasEffect(MobEffects.FIRE_RESISTANCE);
        if (!hasFireResistance) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks,
                    weaknessAmplifier, false, true, true));
            target.setRemainingFireTicks(60);
            level.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + 1, target.getZ(),
                    25, 0.5, 0.8, 0.5, 0.15);
        } else {
            target.sendSystemMessage(Component.literal(
                    "§a§l[Fire Resistance] §7You resist the Coffin of the Iron Mountain!§r"));
        }
    }
}
