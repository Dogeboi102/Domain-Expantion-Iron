package com.jjkdomains.dimension;

import com.jjkdomains.JJKDomainsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central manager for all domain space operations.
 *
 * Flow:
 *  1. Spell calls openDomain() with caster + nearby players
 *  2. We pick a unique slot (XZ offset in domain_space) for this session
 *  3. DomainSphereBuilder constructs the interior
 *  4. All players are teleported into the domain
 *  5. When the domain expires, closeDomain() returns everyone home
 *     and clears the built blocks
 *
 * Slots are spaced 512 blocks apart on the X axis so simultaneous domains
 * never overlap in domain_space.
 */
@EventBusSubscriber(modid = JJKDomainsMod.MOD_ID)
public class DomainTeleportManager {

    private static final int SLOT_SPACING = 512;
    // Domains are centred at Y=128 in domain_space
    private static final int DOMAIN_Y = 128;

    private static final AtomicInteger nextSlot = new AtomicInteger(0);
    private static final Map<UUID, DomainSession> activeSessions = new HashMap<>();
    // Map caster UUID → session for quick lookup by spell tick code
    private static final Map<UUID, DomainSession> casterToSession = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Open a domain
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens a domain session:
     *  - Builds the interior in domain_space
     *  - Teleports the caster and all nearby players inside
     *
     * @return the created DomainSession (or null if domain_space isn't loaded)
     */
    public static DomainSession openDomain(MinecraftServer server,
                                            LivingEntity caster,
                                            List<ServerPlayer> nearbyPlayers,
                                            DomainInteriorType type) {
        ServerLevel domainLevel = server.getLevel(DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel == null) {
            // Dimension not loaded — log and bail gracefully
            caster.sendSystemMessage(Component.literal(
                    "§c[JJK] domain_space dimension not found. Check mod installation."));
            return null;
        }

        int slot = nextSlot.getAndIncrement();
        BlockPos centre = new BlockPos(slot * SLOT_SPACING, DOMAIN_Y, 0);

        // Build the themed interior
        List<BlockPos> placed = DomainSphereBuilder.buildDomain(domainLevel, centre, type);

        DomainSession session = new DomainSession(slot, centre, caster.getUUID(), type, placed);

        activeSessions.put(session.casterUUID, session);
        casterToSession.put(caster.getUUID(), session);

        // Teleport all participants — caster first (index 0 = centre spawn)
        List<ServerPlayer> all = new ArrayList<>();
        if (caster instanceof ServerPlayer sp) all.add(sp);
        all.addAll(nearbyPlayers);

        for (int i = 0; i < all.size(); i++) {
            ServerPlayer player = all.get(i);
            session.addPlayer(player);
            teleportInto(player, domainLevel, session.getSpawnPos(i));
        }

        return session;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Close a domain
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ends a domain session — returns all players home and clears the interior.
     */
    public static void closeDomain(MinecraftServer server, DomainSession session) {
        ServerLevel domainLevel = server.getLevel(DimensionRegistry.DOMAIN_SPACE);

        // Return all tracked players to their origin
        for (UUID id : session.getAllTrackedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;

            DomainSession.PlayerReturnData ret = session.getReturnData(id);
            if (ret == null) continue;

            ServerLevel returnLevel = server.getLevel(ret.dimension());
            if (returnLevel == null) returnLevel = server.overworld();

            teleportTo(player, returnLevel, ret.position());
            player.sendSystemMessage(Component.literal("§7The domain has collapsed. You return."));
        }

        // Clear built blocks
        if (domainLevel != null) {
            DomainSphereBuilder.clearDomain(domainLevel, session.placedBlocks);
        }

        activeSessions.remove(session.casterUUID);
        casterToSession.remove(session.casterUUID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lookup helpers (used by DomainTickScheduler)
    // ─────────────────────────────────────────────────────────────────────────

    public static DomainSession getSessionForCaster(UUID casterUUID) {
        return casterToSession.get(casterUUID);
    }

    public static boolean isCasterInActiveDomain(UUID casterUUID) {
        return casterToSession.containsKey(casterUUID);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Teleport utilities
    // ─────────────────────────────────────────────────────────────────────────

    private static void teleportInto(ServerPlayer player, ServerLevel target, Vec3 pos) {
        player.teleportTo(target,
                pos.x, pos.y, pos.z,
                Set.of(),
                player.getYRot(), player.getXRot(),
                false);
    }

    private static void teleportTo(ServerPlayer player, ServerLevel target, Vec3 pos) {
        player.teleportTo(target,
                pos.x, pos.y, pos.z,
                Set.of(),
                player.getYRot(), player.getXRot(),
                false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Cleanup on server stop
    // ─────────────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        activeSessions.clear();
        casterToSession.clear();
        nextSlot.set(0);
    }
}
