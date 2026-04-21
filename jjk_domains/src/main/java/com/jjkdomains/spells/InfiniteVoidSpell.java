package com.jjkdomains.spells;

import com.jjkdomains.registry.EffectRegistry;
import com.jjkdomains.dimension.DimensionRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.MagicSchoolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

@AutoSpellConfig
public class InfiniteVoidSpell extends AbstractSpell {

    // Domain radius in blocks
    private static final int DOMAIN_RADIUS = 20;
    // Duration in ticks (20 ticks = 1 second). 200 = 10 seconds base.
    private static final int BASE_DURATION_TICKS = 200;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchool(MagicSchoolRegistry.ENDER)
            .setMaxLevel(5)
            .setCooldownSeconds(120)
            .build();

    public InfiniteVoidSpell() {
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 5;
        this.baseManaCost = 200;
        this.manaCostPerLevel = 50;
        this.castTime = 40; // 2-second cast
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
        return ResourceLocation.fromNamespaceAndPath("jjkdomains", "infinite_void");
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.ENDERMAN_AMBIENT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.END_PORTAL_SPAWN);
    }

    @Override
    public Component getSpellName() {
        return Component.translatable("spell.jjkdomains.infinite_void");
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        // ── Recast: cancel existing domain instead of opening a new one ──
        if (DomainTickScheduler.cancelExistingDomain(entity)) {
            serverLevel.players().forEach(p ->
                    p.sendSystemMessage(Component.literal("§5§lInfinite Void§r§7 has been dispelled.")));
            return;
        }

        Vec3 casterPos = entity.position();
        int durationTicks = BASE_DURATION_TICKS + (spellLevel * 40);

        // Broadcast domain open message
        serverLevel.players().forEach(p ->
                p.sendSystemMessage(Component.literal("§5§lDomain Expansion: §dInfinite Void§r"))
        );

        // Gather players to pull in
        List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(p ->
                p != entity && p.distanceTo(entity) <= DOMAIN_RADIUS
        );

        // Open the domain space — builds interior and teleports everyone in
        com.jjkdomains.dimension.DomainSession session =
                com.jjkdomains.dimension.DomainTeleportManager.openDomain(
                        serverLevel.getServer(), entity, nearbyPlayers,
                        com.jjkdomains.dimension.DomainInteriorType.INFINITE_VOID
                );

        // Apply freeze effect to targets already inside the domain
        ServerLevel domainLevel = serverLevel.getServer().getLevel(
                com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel != null) {
            domainLevel.getPlayers(p -> p != entity).forEach(target ->
                    target.addEffect(new MobEffectInstance(
                            EffectRegistry.INFINITE_VOID_EFFECT,
                            durationTicks, spellLevel - 1, false, true, true))
            );
        }

        DomainTickScheduler.scheduleDomain(
                serverLevel, entity, casterPos, DOMAIN_RADIUS, durationTicks,
                EffectRegistry.INFINITE_VOID_EFFECT, spellLevel - 1, session
        );

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
