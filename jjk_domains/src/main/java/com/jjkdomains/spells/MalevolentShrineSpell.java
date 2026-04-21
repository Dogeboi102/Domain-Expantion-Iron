package com.jjkdomains.spells;

import com.jjkdomains.registry.EffectRegistry;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class MalevolentShrineSpell extends AbstractSpell {

    private static final int DOMAIN_RADIUS = 18;
    private static final int BASE_DURATION_TICKS = 160;
    private static final float BASE_DAMAGE_PER_SECOND = 3.0f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchool(SchoolType.BLOOD)
            .setMaxLevel(5)
            .setCooldownSeconds(30)
            .build();

    public MalevolentShrineSpell() {
        this.baseSpellPower = 12;
        this.spellPowerPerLevel = 6;
        this.baseManaCost = 50;
        this.manaCostPerLevel = 15;
        this.castTime = 30;
    }

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public CastType getCastType() { return CastType.LONG; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("jjkdomains", "malevolent_shrine");
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.RAVAGER_ROAR);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.RAVAGER_ROAR);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (DomainTickScheduler.cancelExistingDomain(entity)) {
            serverLevel.players().forEach(p ->
                    p.sendSystemMessage(Component.literal("§4§lMalevolent Shrine§r§7 has been dispelled.")));
            return;
        }

        Vec3 casterPos = entity.position();
        int durationTicks = BASE_DURATION_TICKS + (spellLevel * 30);
        float damagePerSecond = BASE_DAMAGE_PER_SECOND + (spellLevel * 1.5f);

        serverLevel.players().forEach(p ->
                p.sendSystemMessage(Component.literal("§4§lDomain Expansion: §cMalevolent Shrine§r")));

        List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(p ->
                p != entity && p.distanceTo(entity) <= DOMAIN_RADIUS);

        com.jjkdomains.dimension.DomainSession session =
                com.jjkdomains.dimension.DomainTeleportManager.openDomain(
                        serverLevel.getServer(), entity, nearbyPlayers,
                        com.jjkdomains.dimension.DomainInteriorType.MALEVOLENT_SHRINE);

        ServerLevel domainLevel = serverLevel.getServer().getLevel(DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel != null) {
            domainLevel.getPlayers(p -> p != entity).forEach(target -> {
                target.addEffect(new MobEffectInstance(EffectRegistry.MALEVOLENT_SHRINE_EFFECT,
                        durationTicks, spellLevel - 1, false, true, true));
                domainLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        target.getX(), target.getY() + 1, target.getZ(),
                        20, 0.5, 0.5, 0.5, 0.3);
            });
        }

        DomainTickScheduler.scheduleMalevolentShrine(serverLevel, entity, casterPos, DOMAIN_RADIUS,
                durationTicks, EffectRegistry.MALEVOLENT_SHRINE_EFFECT, spellLevel - 1,
                damagePerSecond, session);

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
