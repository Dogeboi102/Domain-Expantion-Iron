package com.jjkdomains.spells;

import com.jjkdomains.registry.EffectRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.registries.MagicSchoolRegistry;
import com.jjkdomains.dimension.DimensionRegistry;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class CoffinOfIronMountainSpell extends AbstractSpell {

    private static final int DOMAIN_RADIUS = 20;
    private static final int BASE_DURATION_TICKS = 180; // 9 seconds base
    private static final float BASE_DAMAGE_PER_SECOND = 6.0f; // Heavy damage
    private static final int WEAKNESS_AMPLIFIER = 2; // Weakness III (0-indexed)

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchool(MagicSchoolRegistry.FIRE)
            .setMaxLevel(5)
            .setCooldownSeconds(110)
            .build();

    public CoffinOfIronMountainSpell() {
        this.baseSpellPower = 15;
        this.spellPowerPerLevel = 7;
        this.baseManaCost = 220;
        this.manaCostPerLevel = 50;
        this.castTime = 35;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

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
        return Optional.of(SoundEvents.GENERIC_EXPLODE);
    }

    @Override
    public Component getSpellName() {
        return Component.translatable("spell.jjkdomains.coffin_of_iron_mountain");
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // ── Recast: cancel existing domain ──
        if (DomainTickScheduler.cancelExistingDomain(entity)) {
            serverLevel.players().forEach(p ->
                    p.sendSystemMessage(Component.literal("§6§l🔥 Coffin of the Iron Mountain§r§7 has been dispelled.")));
            return;
        }

        Vec3 casterPos = entity.position();
        int durationTicks = BASE_DURATION_TICKS + (spellLevel * 30);
        float damagePerSecond = BASE_DAMAGE_PER_SECOND + (spellLevel * 2.0f);

        // Announce domain
        serverLevel.players().forEach(p -> {
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§6§l🔥 Domain Expansion: Coffin of the Iron Mountain 🔥§r"));
            p.sendSystemMessage(Component.literal("§7§o\"Scorched and crushed beneath the mountain...\"§r"));
            p.sendSystemMessage(Component.literal(""));
        });

        // Gather nearby players to pull into the domain
        List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(p ->
                p != entity && p.distanceTo(entity) <= DOMAIN_RADIUS
        );

        // Open domain space — builds blackstone cavern interior and teleports everyone in
        com.jjkdomains.dimension.DomainSession session =
                com.jjkdomains.dimension.DomainTeleportManager.openDomain(
                        serverLevel.getServer(), entity, nearbyPlayers,
                        com.jjkdomains.dimension.DomainInteriorType.COFFIN_OF_IRON_MOUNTAIN
                );

        // Apply initial effects to players now inside the domain
        ServerLevel domainLevel = serverLevel.getServer().getLevel(
                com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel != null) {
            domainLevel.getPlayers(p -> p != entity)
                    .forEach(target -> applyDomainEffects(domainLevel, target, durationTicks, WEAKNESS_AMPLIFIER));
        }

        // Schedule domain ticker
        DomainTickScheduler.scheduleCoffinOfIronMountain(
                serverLevel, entity, casterPos, DOMAIN_RADIUS,
                durationTicks, damagePerSecond, WEAKNESS_AMPLIFIER, session
        );

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * Applies weakness and sets target on fire — unless they have fire resistance.
     * Fire resistance fully negates all domain effects.
     */
    public static void applyDomainEffects(ServerLevel level, ServerPlayer target,
                                           int durationTicks, int weaknessAmplifier) {
        boolean hasFireResistance = target.hasEffect(MobEffects.FIRE_RESISTANCE);

        if (!hasFireResistance) {
            // Apply Weakness
            target.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    durationTicks,
                    weaknessAmplifier,
                    false,
                    true,
                    true
            ));

            // Set on fire (3 seconds of fire, refreshed each domain tick)
            target.setRemainingFireTicks(60);

            // Particle burst on initial application
            level.sendParticles(
                    ParticleTypes.FLAME,
                    target.getX(), target.getY() + 1, target.getZ(),
                    25, 0.5, 0.8, 0.5, 0.15
            );
            level.sendParticles(
                    ParticleTypes.LAVA,
                    target.getX(), target.getY() + 1, target.getZ(),
                    10, 0.4, 0.6, 0.4, 0.05
            );
        } else {
            // Notify the fire-resistant player they are immune
            target.sendSystemMessage(Component.literal(
                    "§a§l[Fire Resistance] §7You resist the Coffin of the Iron Mountain!§r"
            ));
        }
    }
}
