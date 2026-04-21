package com.jjkdomains.spells;

import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.*;
import java.util.UUID;

/**
 * Handles per-tick domain logic using NeoForge's LevelTickEvent.
 *
 * Every domain is registered through DomainClashManager before being added
 * here. The clash manager may suppress a domain (freeze its ticks) if a
 * higher-mana caster opened a conflicting domain at the same time or within
 * the same space. Suppressed domains resume when the winning domain expires.
 */
@EventBusSubscriber(modid = "jjkdomains")
public class DomainTickScheduler {

    private static final List<ActiveDomain> activeDomains = Collections.synchronizedList(new ArrayList<>());

    /**
     * Tracks which caster UUID owns an active primary domain.
     * Used for recast cancellation — if a caster casts again while a domain
     * is already active, we cancel the old one first.
     */
    private static final Map<UUID, ActiveDomain> casterDomains = Collections.synchronizedMap(new HashMap<>());

    // ─────────────────────────────────────────────────────────────────────────
    //  Recast / manual cancel
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call from each spell's onCast BEFORE scheduling a new domain.
     * If the caster already has an active domain, it is immediately cancelled
     * (players returned, blocks cleared) and this method returns true.
     */
    public static boolean cancelExistingDomain(LivingEntity caster) {
        ActiveDomain existing = casterDomains.get(caster.getUUID());
        if (existing != null && !existing.isExpired()) {
            existing.forceCancel();
            return true;
        }
        return false;
    }

    /** Force-expire a domain by its caster UUID (called from mana-drain check). */
    public static void cancelDomainForCaster(UUID casterUUID) {
        ActiveDomain d = casterDomains.get(casterUUID);
        if (d != null) d.forceCancel();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Registration helpers
    //  Each helper creates the domain, registers it with the clash manager
    //  (which may immediately suppress it), then adds it to the tick list.
    // ─────────────────────────────────────────────────────────────────────────

    public static void scheduleDomain(ServerLevel level, LivingEntity caster, Vec3 origin,
                                       int radius, int durationTicks,
                                       Holder<MobEffect> effect, int amplifier,
                                       com.jjkdomains.dimension.DomainSession session) {
        ActiveDomain d = new InfiniteVoidDomain(level, caster, origin, radius, durationTicks, effect, amplifier, session);
        DomainClashManager.register(d, caster, origin, radius);
        activeDomains.add(d);
        casterDomains.put(caster.getUUID(), d);
    }

    public static void scheduleMalevolentShrine(ServerLevel level, LivingEntity caster, Vec3 origin,
                                                  int radius, int durationTicks,
                                                  Holder<MobEffect> effect, int amplifier, float dps,
                                                  com.jjkdomains.dimension.DomainSession session) {
        ActiveDomain d = new MalevolentShrineDomain(level, caster, origin, radius, durationTicks, effect, amplifier, dps, session);
        DomainClashManager.register(d, caster, origin, radius);
        activeDomains.add(d);
        casterDomains.put(caster.getUUID(), d);
    }

    public static void scheduleDeadlySentencing(ServerLevel level, Player caster,
                                                  List<ServerPlayer> targets, float guiltyChance,
                                                  int durationTicks, int radius,
                                                  com.jjkdomains.dimension.DomainSession session) {
        ActiveDomain d = new DeadlySentencingDomain(level, caster, targets, guiltyChance, durationTicks, radius, session);
        DomainClashManager.register(d, caster, caster.position(), radius);
        activeDomains.add(d);
        casterDomains.put(caster.getUUID(), d);
    }

    public static void scheduleCoffinOfIronMountain(ServerLevel level, LivingEntity caster, Vec3 origin,
                                                      int radius, int durationTicks,
                                                      float dps, int weaknessAmplifier,
                                                      com.jjkdomains.dimension.DomainSession session) {
        ActiveDomain d = new CoffinOfIronMountainDomain(level, caster, origin, radius, durationTicks, dps, weaknessAmplifier, session);
        DomainClashManager.register(d, caster, origin, radius);
        activeDomains.add(d);
        casterDomains.put(caster.getUUID(), d);
    }

    public static void scheduleManaZeroDrain(ServerLevel level, Player caster,
                                              List<ServerPlayer> targets, int durationTicks, int radius) {
        // Secondary effect of Deadly Sentencing — bypasses clash system
        activeDomains.add(new ManaDrainDomain(level, caster, targets, durationTicks, radius));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tick handler
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        DomainClashManager.tick();
        activeDomains.removeIf(domain -> {
            domain.tick();
            return domain.isExpired();
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        activeDomains.clear();
        casterDomains.clear();
        DomainClashManager.reset();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Base domain class — now carries suppressed flag
    // ─────────────────────────────────────────────────────────────────────────

    public abstract static class ActiveDomain {
        protected int ticksRemaining;
        public final ServerLevel level;
        protected final LivingEntity caster;
        protected final Vec3 origin;
        protected final int radius;
        private boolean suppressed = false;
        private boolean forceCancelled = false;
        /** May be null for secondary domains (mana drain) that don't own a session. */
        protected final com.jjkdomains.dimension.DomainSession session;

        /**
         * Mana drained from the caster every second (20 ticks) while the domain is active.
         * Subclasses set this in their constructor. Secondary domains (mana drain) use 0.
         */
        protected float manaPerSecond = 0f;

        ActiveDomain(ServerLevel level, LivingEntity caster, Vec3 origin, int radius, int duration,
                     com.jjkdomains.dimension.DomainSession session) {
            this.level = level;
            this.caster = caster;
            this.origin = origin;
            this.radius = radius;
            this.ticksRemaining = duration;
            this.session = session;
        }

        abstract void doTick();

        final void tick() {
            if (forceCancelled) return;

            ticksRemaining--;

            // ── Mana drain & out-of-mana cancellation ──────────────────────
            if (manaPerSecond > 0 && ticksRemaining % 20 == 0 && caster instanceof ServerPlayer sp) {
                var magicData = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(sp);
                if (magicData != null) {
                    float current = magicData.getMana();
                    if (current <= 0) {
                        // Caster ran out of mana — cancel the domain
                        sp.sendSystemMessage(Component.literal(
                                "§c§lYour domain collapsed — you ran out of mana!§r"));
                        forceCancel();
                        return;
                    }
                    // Drain mana
                    magicData.setMana(Math.max(0, current - manaPerSecond));
                }
            }

            if (!suppressed && ticksRemaining > 0) {
                doTick();
            }

            // Natural expiry — close the domain space session
            if (ticksRemaining <= 0 && session != null) {
                closeSession();
            }
        }

        /**
         * Immediately cancels this domain — used for recast and out-of-mana.
         * Sets ticksRemaining to 0 so the scheduler removes it next tick.
         */
        public void forceCancel() {
            if (forceCancelled) return;
            forceCancelled = true;
            ticksRemaining = 0;
            if (session != null) closeSession();
            // Notify caster if they're a player
            if (caster instanceof ServerPlayer sp && !sp.level().isClientSide()) {
                sp.sendSystemMessage(Component.literal("§7Your domain has collapsed.§r"));
            }
            // Remove from casterDomains map
            casterDomains.remove(caster.getUUID());
        }

        private void closeSession() {
            com.jjkdomains.dimension.DomainTeleportManager.closeDomain(
                    level.getServer(), session);
        }

        public void setSuppressed(boolean suppressed) { this.suppressed = suppressed; }
        public boolean isSuppressed() { return suppressed; }
        public boolean isExpired() { return ticksRemaining <= 0 || forceCancelled; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Infinite Void Domain
    // ─────────────────────────────────────────────────────────────────────────

    private static class InfiniteVoidDomain extends ActiveDomain {
        private final Holder<MobEffect> effect;
        private final int amplifier;

        InfiniteVoidDomain(ServerLevel level, LivingEntity caster, Vec3 origin,
                            int radius, int duration, Holder<MobEffect> effect, int amplifier,
                            com.jjkdomains.dimension.DomainSession session) {
            super(level, caster, origin, radius, duration, session);
            this.effect = effect;
            this.amplifier = amplifier;
            this.manaPerSecond = 20f + (amplifier * 5f); // 20 base + 5 per spell level
        }

        @Override
        void doTick() {
            if (ticksRemaining % 10 == 0) {
                // Effects run on players inside the domain space dimension
                ServerLevel domainLevel = level.getServer().getLevel(
                        com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
                if (domainLevel == null) return;
                domainLevel.getPlayers(p -> p != caster)
                        .forEach(p -> {
                            if (!p.hasEffect(effect))
                                p.addEffect(new MobEffectInstance(effect, ticksRemaining, amplifier, false, true, true));
                        });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Malevolent Shrine Domain
    // ─────────────────────────────────────────────────────────────────────────

    private static class MalevolentShrineDomain extends ActiveDomain {
        private final Holder<MobEffect> effect;
        private final int amplifier;
        private final float dps;

        MalevolentShrineDomain(ServerLevel level, LivingEntity caster, Vec3 origin,
                                int radius, int duration, Holder<MobEffect> effect,
                                int amplifier, float dps,
                                com.jjkdomains.dimension.DomainSession session) {
            super(level, caster, origin, radius, duration, session);
            this.effect = effect;
            this.amplifier = amplifier;
            this.dps = dps;
            this.manaPerSecond = 20f + (amplifier * 5f);
        }

        @Override
        void doTick() {
            ServerLevel domainLevel = level.getServer().getLevel(
                    com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
            if (domainLevel == null) return;
            List<ServerPlayer> inRange = domainLevel.getPlayers(p -> p != caster);

            if (ticksRemaining % 10 == 0) {
                inRange.forEach(p -> {
                    if (!p.hasEffect(effect))
                        p.addEffect(new MobEffectInstance(effect, ticksRemaining, amplifier, false, true, true));
                });
            }
            if (ticksRemaining % 20 == 0) {
                inRange.forEach(p -> p.hurt(domainLevel.damageSources().magic(), dps));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deadly Sentencing Domain
    // ─────────────────────────────────────────────────────────────────────────

    private static class DeadlySentencingDomain extends ActiveDomain {
        private final Player casterPlayer;
        private final List<ServerPlayer> targets;
        private final float guiltyChance;
        private final int totalDuration;
        private boolean verdictDelivered = false;
        private static final int VERDICT_DELAY = 60;

        DeadlySentencingDomain(ServerLevel level, Player caster, List<ServerPlayer> targets,
                                float guiltyChance, int duration, int radius,
                                com.jjkdomains.dimension.DomainSession session) {
            super(level, caster, caster.position(), radius, duration, session);
            this.casterPlayer = caster;
            this.targets = new ArrayList<>(targets);
            this.guiltyChance = guiltyChance;
            this.totalDuration = duration;
            this.manaPerSecond = 25f; // Deadly sentencing drains a bit more
        }

        @Override
        void doTick() {
            ServerLevel domainLevel = level.getServer().getLevel(
                    com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
            if (domainLevel == null) return;

            if (ticksRemaining % 20 == 0) {
                domainLevel.getPlayers(p -> p != caster)
                        .forEach(p -> {
                            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
                            if (md != null) md.setMana(0);
                            if (!targets.contains(p)) targets.add(p);
                        });
            }
            if (!verdictDelivered && ticksRemaining == totalDuration - VERDICT_DELAY) {
                verdictDelivered = true;
                if (casterPlayer instanceof ServerPlayer sp)
                    DeadlySentencingSpell.deliverVerdict(domainLevel, sp, targets, guiltyChance, ticksRemaining);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mana Drain (secondary, no clash check)
    // ─────────────────────────────────────────────────────────────────────────

    private static class ManaDrainDomain extends ActiveDomain {
        private final List<ServerPlayer> targets;

        ManaDrainDomain(ServerLevel level, Player caster, List<ServerPlayer> targets,
                         int duration, int radius) {
            super(level, caster, caster.position(), radius, duration, null);
            this.targets = new ArrayList<>(targets);
        }

        @Override
        void doTick() {
            ServerLevel domainLevel = level.getServer().getLevel(
                    com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
            if (domainLevel == null) return;
            if (ticksRemaining % 20 == 0) {
                domainLevel.getPlayers(p -> p != caster)
                        .forEach(p -> {
                            var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
                            if (md != null) md.setMana(0);
                        });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Coffin of the Iron Mountain Domain
    // ─────────────────────────────────────────────────────────────────────────

    private static class CoffinOfIronMountainDomain extends ActiveDomain {
        private final float dps;
        private final int weaknessAmplifier;

        CoffinOfIronMountainDomain(ServerLevel level, LivingEntity caster, Vec3 origin,
                                    int radius, int duration, float dps, int weaknessAmplifier,
                                    com.jjkdomains.dimension.DomainSession session) {
            super(level, caster, origin, radius, duration, session);
            this.dps = dps;
            this.weaknessAmplifier = weaknessAmplifier;
            this.manaPerSecond = 20f + (weaknessAmplifier * 5f);
        }

        @Override
        void doTick() {
            ServerLevel domainLevel = level.getServer().getLevel(
                    com.jjkdomains.dimension.DimensionRegistry.DOMAIN_SPACE);
            if (domainLevel == null) return;
            List<ServerPlayer> inRange = domainLevel.getPlayers(p -> p != caster);

            if (ticksRemaining % 10 == 0) {
                for (ServerPlayer p : inRange) {
                    if (p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                        p.clearFire();
                        p.removeEffect(MobEffects.WEAKNESS);
                    } else {
                        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                                ticksRemaining + 10, weaknessAmplifier, false, true, true));
                        p.setRemainingFireTicks(30);
                        domainLevel.sendParticles(ParticleTypes.FLAME,
                                p.getX() + (Math.random() - 0.5), p.getY() + Math.random() * 2,
                                p.getZ() + (Math.random() - 0.5), 3, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            }

            if (ticksRemaining % 20 == 0) {
                for (ServerPlayer p : inRange) {
                    if (!p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                        p.hurt(domainLevel.damageSources().inFire(), dps);
                        domainLevel.sendParticles(ParticleTypes.LAVA,
                                p.getX(), p.getY() + 1, p.getZ(),
                                8, 0.4, 0.5, 0.4, 0.1);
                    }
                }
            }
        }
    }
}
