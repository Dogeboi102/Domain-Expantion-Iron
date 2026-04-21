package com.jjkdomains.spells;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Domain Clash System
 *
 * When a new domain is opened, this manager checks whether it overlaps with
 * any already-active domain. If it does, the two casters' maximum mana totals
 * are compared. The higher-mana caster's domain "wins":
 *
 *  - The losing domain is immediately suppressed (paused, not destroyed).
 *  - All players in the overlap receive a clash notification.
 *  - When the winning domain expires, the suppressed domain resumes if it
 *    still has ticks remaining.
 *
 * Ties (equal max mana) result in both domains being suppressed — a stalemate
 * — with a distinct message broadcast to all nearby players.
 *
 * This class is intentionally separate from DomainTickScheduler so the clash
 * logic can evolve independently.
 */
public class DomainClashManager {

    // ─────────────────────────────────────────────────────────────────────────
    //  Singleton-style state (server-side only, reset on world unload)
    // ─────────────────────────────────────────────────────────────────────────

    /** All domains currently registered with the clash manager. */
    private static final List<DomainEntry> registry = Collections.synchronizedList(new ArrayList<>());

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API  (called by DomainTickScheduler.schedule* methods)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Register a new domain and immediately resolve any clashes.
     * Returns the DomainEntry so the scheduler can flip its suppressed flag
     * when ticking.
     */
    public static DomainEntry register(DomainTickScheduler.ActiveDomain domain,
                                        LivingEntity caster, Vec3 origin, int radius) {
        DomainEntry newEntry = new DomainEntry(domain, caster, origin, radius);

        // Find all active (non-expired, non-suppressed-by-clash) domains
        // whose radii overlap with the new one.
        List<DomainEntry> clashing = new ArrayList<>();
        for (DomainEntry existing : registry) {
            if (!existing.isAlive()) continue;
            if (existing.caster == caster) continue; // same caster — stack normally
            if (domainsOverlap(existing, newEntry)) {
                clashing.add(existing);
            }
        }

        if (!clashing.isEmpty()) {
            // Pick the existing domain with the highest-mana caster to clash against
            DomainEntry strongest = clashing.stream()
                    .max(Comparator.comparingInt(e -> getMaxMana(e.caster)))
                    .orElseThrow();

            int newMana      = getMaxMana(caster);
            int existingMana = getMaxMana(strongest.caster);

            if (newMana > existingMana) {
                // New domain wins — suppress all clashing domains
                broadcastClash(domain.level, caster, strongest.caster, true);
                for (DomainEntry loser : clashing) {
                    loser.suppress(newEntry);
                }
                // New domain runs freely
            } else if (existingMana > newMana) {
                // Existing domain wins — suppress the new one
                broadcastClash(domain.level, strongest.caster, caster, false);
                newEntry.suppress(strongest);
            } else {
                // Tie — stalemate, both suppressed
                broadcastStalemate(domain.level, caster, strongest.caster);
                newEntry.suppress(null);
                for (DomainEntry tied : clashing) {
                    tied.suppress(null);
                }
            }
        }

        registry.add(newEntry);
        return newEntry;
    }

    /**
     * Called every server tick by DomainTickScheduler to clean expired entries
     * and restore any domains that were suppressed by a now-expired winner.
     */
    public static void tick() {
        registry.removeIf(e -> !e.isAlive());

        // Check if any suppressed domain's winner has now expired — if so, restore it
        for (DomainEntry entry : registry) {
            if (entry.suppressedBy != null && !entry.suppressedBy.isAlive()) {
                entry.restore();
            }
        }
    }

    /** Clear all state (call on server stop / world unload). */
    public static void reset() {
        registry.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean domainsOverlap(DomainEntry a, DomainEntry b) {
        double dist = a.origin.distanceTo(b.origin);
        return dist <= (a.radius + b.radius);
    }

    /**
     * Returns the caster's maximum mana pool.
     * Falls back to 0 if they are not a player or mana data is unavailable.
     */
    public static int getMaxMana(LivingEntity entity) {
        if (!(entity instanceof Player player)) return 0;
        MagicData data = MagicData.getPlayerMagicData((ServerPlayer) player);
        return data != null ? (int) data.getMaxMana() : 0;
    }

    private static void broadcastClash(ServerLevel level, LivingEntity winner, LivingEntity loser,
                                        boolean newWon) {
        String winnerName = winner.getName().getString();
        String loserName  = loser.getName().getString();
        level.players().forEach(p -> {
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§e§l⚔ Domain Clash! ⚔§r"));
            p.sendSystemMessage(Component.literal(
                    "§6" + winnerName + "§7's domain overwhelms §c" + loserName + "§7's domain!"
            ));
            p.sendSystemMessage(Component.literal(
                    "§7(Higher max mana wins: §6" + winnerName + "§7)§r"
            ));
            p.sendSystemMessage(Component.literal(""));
        });
    }

    private static void broadcastStalemate(ServerLevel level, LivingEntity a, LivingEntity b) {
        String nameA = a.getName().getString();
        String nameB = b.getName().getString();
        level.players().forEach(p -> {
            p.sendSystemMessage(Component.literal(""));
            p.sendSystemMessage(Component.literal("§b§l⚔ Domain Stalemate! ⚔§r"));
            p.sendSystemMessage(Component.literal(
                    "§7" + nameA + " and " + nameB + "'s domains are perfectly matched!"
            ));
            p.sendSystemMessage(Component.literal("§7Both domains cancel each other out.§r"));
            p.sendSystemMessage(Component.literal(""));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DomainEntry — wraps an ActiveDomain with clash metadata
    // ─────────────────────────────────────────────────────────────────────────

    public static class DomainEntry {
        public final DomainTickScheduler.ActiveDomain domain;
        public final LivingEntity caster;
        public final Vec3 origin;
        public final int radius;

        /** Non-null when this domain has been suppressed by a clashing winner. */
        public DomainEntry suppressedBy = null;

        DomainEntry(DomainTickScheduler.ActiveDomain domain,
                    LivingEntity caster, Vec3 origin, int radius) {
            this.domain = domain;
            this.caster = caster;
            this.origin = origin;
            this.radius = radius;
        }

        /** Suppress this domain — its ticks will be frozen. */
        void suppress(DomainEntry winner) {
            this.suppressedBy = winner; // null means stalemate
            domain.setSuppressed(true);

            // Notify the losing caster
            if (caster instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(
                        "§c§lYour domain has been suppressed by a stronger domain!§r"
                ));
            }
        }

        /** Restore this domain after its suppressor expires. */
        void restore() {
            suppressedBy = null;
            domain.setSuppressed(false);

            if (caster instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal(
                        "§a§lThe opposing domain has ended — your domain resumes!§r"
                ));
            }

            // Also broadcast to nearby players
            if (domain.level != null) {
                domain.level.players().forEach(p -> {
                    if (p.position().distanceTo(origin) <= radius + 10) {
                        p.sendSystemMessage(Component.literal(
                                "§a" + caster.getName().getString() + "§7's domain has resumed!"
                        ));
                    }
                });
            }
        }

        boolean isAlive() {
            return !domain.isExpired();
        }
    }
}
