package com.jjkdomains.spells;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class DomainClashManager {

    private static final List<DomainEntry> registry = Collections.synchronizedList(new ArrayList<>());

    public static DomainEntry register(DomainTickScheduler.ActiveDomain domain,
                                        LivingEntity caster, Vec3 origin, int radius) {
        DomainEntry newEntry = new DomainEntry(domain, caster, origin, radius);

        List<DomainEntry> clashing = new ArrayList<>();
        for (DomainEntry existing : registry) {
            if (!existing.isAlive()) continue;
            if (existing.caster == caster) continue;
            if (domainsOverlap(existing, newEntry)) {
                clashing.add(existing);
            }
        }

        if (!clashing.isEmpty()) {
            DomainEntry strongest = clashing.stream()
                    .max(Comparator.comparingInt(e -> getCurrentMana(e.caster)))
                    .orElseThrow();

            int newMana      = getCurrentMana(caster);
            int existingMana = getCurrentMana(strongest.caster);

            if (newMana > existingMana) {
                broadcastClash(domain.level, caster, strongest.caster, true);
                for (DomainEntry loser : clashing) loser.suppress(newEntry);
            } else if (existingMana > newMana) {
                broadcastClash(domain.level, strongest.caster, caster, false);
                newEntry.suppress(strongest);
            } else {
                broadcastStalemate(domain.level, caster, strongest.caster);
                newEntry.suppress(null);
                for (DomainEntry tied : clashing) tied.suppress(null);
            }
        }

        registry.add(newEntry);
        return newEntry;
    }

    public static void tick() {
        registry.removeIf(e -> !e.isAlive());
        for (DomainEntry entry : registry) {
            if (entry.suppressedBy != null && !entry.suppressedBy.isAlive()) {
                entry.restore();
            }
        }
    }

    public static void reset() { registry.clear(); }

    private static boolean domainsOverlap(DomainEntry a, DomainEntry b) {
        return a.origin.distanceTo(b.origin) <= (a.radius + b.radius);
    }

    public static int getCurrentMana(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return 0;
        try {
            MagicData data = MagicData.getPlayerMagicData(player);
            return data != null ? (int) data.getMana() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void broadcastClash(ServerLevel level, LivingEntity winner, LivingEntity loser, boolean newWon) {
        String wn = winner.getName().getString();
        String ln = loser.getName().getString();
        level.players().forEach(p -> {
            p.sendSystemMessage(Component.literal("§e§l⚔ Domain Clash! ⚔§r"));
            p.sendSystemMessage(Component.literal("§6" + wn + "§7's domain overwhelms §c" + ln + "§7's!"));
        });
    }

    private static void broadcastStalemate(ServerLevel level, LivingEntity a, LivingEntity b) {
        level.players().forEach(p -> {
            p.sendSystemMessage(Component.literal("§b§l⚔ Domain Stalemate! ⚔§r"));
            p.sendSystemMessage(Component.literal("§7" + a.getName().getString() + " and " + b.getName().getString() + "'s domains cancel out!"));
        });
    }

    public static class DomainEntry {
        public final DomainTickScheduler.ActiveDomain domain;
        public final LivingEntity caster;
        public final Vec3 origin;
        public final int radius;
        public DomainEntry suppressedBy = null;

        DomainEntry(DomainTickScheduler.ActiveDomain domain, LivingEntity caster, Vec3 origin, int radius) {
            this.domain = domain; this.caster = caster; this.origin = origin; this.radius = radius;
        }

        void suppress(DomainEntry winner) {
            this.suppressedBy = winner;
            domain.setSuppressed(true);
            if (caster instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.literal("§c§lYour domain has been suppressed!§r"));
        }

        void restore() {
            suppressedBy = null;
            domain.setSuppressed(false);
            if (caster instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.literal("§a§lThe opposing domain ended — yours resumes!§r"));
        }

        boolean isAlive() { return !domain.isExpired(); }
    }
}
