package com.jjkdomains.spells;

import com.jjkdomains.registry.ItemRegistry;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.registries.MagicSchoolRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Random;

@AutoSpellConfig
public class DeadlySentencingSpell extends AbstractSpell {

    private static final int DOMAIN_RADIUS = 20;
    private static final int BASE_DURATION_TICKS = 200; // 10 seconds
    // Chance (0.0 - 1.0) of GUILTY verdict per level
    // Level 1 = 50%, level 5 = 90%
    private static final float BASE_GUILTY_CHANCE = 0.5f;
    private static final float GUILTY_CHANCE_PER_LEVEL = 0.10f;

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchool(MagicSchoolRegistry.ENDER)
            .setMaxLevel(5)
            .setCooldownSeconds(180)
            .build();

    public DeadlySentencingSpell() {
        this.baseSpellPower = 20;
        this.spellPowerPerLevel = 8;
        this.baseManaCost = 300;
        this.manaCostPerLevel = 60;
        this.castTime = 60; // 3-second cast (dramatic)
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
        return ResourceLocation.fromNamespaceAndPath("jjkdomains", "deadly_sentencing");
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BELL_RESONATE);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ANVIL_LAND);
    }

    @Override
    public Component getSpellName() {
        return Component.translatable("spell.jjkdomains.deadly_sentencing");
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(entity instanceof Player caster)) return;

        // ── Recast: cancel existing domain ──
        if (DomainTickScheduler.cancelExistingDomain(entity)) {
            serverLevel.players().forEach(p ->
                    p.sendSystemMessage(Component.literal("§6§l⚖ Deadly Sentencing§r§7 has been dispelled.")));
            return;
        }

        int durationTicks = BASE_DURATION_TICKS + (spellLevel * 20);
        float guiltyChance = Math.min(0.95f, BASE_GUILTY_CHANCE + (spellLevel * GUILTY_CHANCE_PER_LEVEL));

        // Gather nearby players
        List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(p ->
                p != caster && p.distanceTo(caster) <= DOMAIN_RADIUS
        );

        // Announce domain opening with courtroom theme
        serverLevel.players().forEach(p -> {
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§6§l⚖ Domain Expansion: Deadly Sentencing ⚖§r"));
            p.sendSystemMessage(Component.literal("§7§o\"I hereby render my judgement...\"§r"));
            p.sendSystemMessage(Component.literal(""));
        });

        // Open domain space — builds courtroom interior and teleports everyone in
        com.jjkdomains.dimension.DomainSession session =
                com.jjkdomains.dimension.DomainTeleportManager.openDomain(
                        serverLevel.getServer(), caster, nearbyPlayers,
                        com.jjkdomains.dimension.DomainInteriorType.DEADLY_SENTENCING
                );

        // Drain mana of all players now inside the domain
        ServerLevel domainLevel = serverLevel.getServer().getLevel(
                com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel != null) {
            domainLevel.getPlayers(p -> p != caster).forEach(target -> {
                MagicData targetMagic = MagicData.getPlayerMagicData(target);
                if (targetMagic != null) targetMagic.setMana(0);
            });
        }

        // Schedule the verdict reveal after a dramatic delay (3 seconds = 60 ticks)
        DomainTickScheduler.scheduleDeadlySentencing(
                serverLevel, caster, nearbyPlayers, guiltyChance,
                durationTicks, DOMAIN_RADIUS, session
        );

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /**
     * Called by the scheduler after the verdict delay.
     * level here is domain_space — caster and targets are already inside it.
     */
    public static void deliverVerdict(ServerLevel level, ServerPlayer caster,
                                       List<ServerPlayer> targets, float guiltyChance, int durationTicks) {
        boolean guilty = new Random().nextFloat() < guiltyChance;

        if (guilty) {
            // Broadcast GUILTY to everyone in domain_space
            level.players().forEach(p -> {
                p.sendSystemMessage(Component.literal("§c§l⚖ VERDICT: GUILTY ⚖§r"));
                p.sendSystemMessage(Component.literal("§c§o\"The sentence is... death.\"§r"));
            });
            // Also broadcast to the wider server so spectators know
            if (level.getServer() != null) {
                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                    if (p.level().dimension() != com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE) {
                        p.sendSystemMessage(Component.literal("§c⚖ Deadly Sentencing: §lGUILTY§r"));
                    }
                });
            }

            // Give the one-shot sword to the caster
            ItemStack sword = new ItemStack(ItemRegistry.GUILTY_VERDICT_SWORD.get());
            sword.setHoverName(Component.literal("§c§lVerdict: Guilty§r"));
            if (!caster.getInventory().add(sword)) {
                caster.drop(sword, false);
            }

            // Keep draining mana for remaining duration
            DomainTickScheduler.scheduleManaZeroDrain(level, caster, targets, durationTicks, DOMAIN_RADIUS);

        } else {
            // INNOCENT — announce and domain will expire naturally
            level.players().forEach(p -> {
                p.sendSystemMessage(Component.literal("§a§l⚖ VERDICT: INNOCENT ⚖§r"));
                p.sendSystemMessage(Component.literal("§a§o\"You are free to go... this time.\"§r"));
            });
            if (level.getServer() != null) {
                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                    if (p.level().dimension() != com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE) {
                        p.sendSystemMessage(Component.literal("§a⚖ Deadly Sentencing: §lINNOCENT§r"));
                    }
                });
            }
        }
    }
}
