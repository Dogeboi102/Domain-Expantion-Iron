package com.jjkdomains.dimension;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Represents one active domain expansion session.
 *
 * Stores:
 * - The domain_space centre block where the interior was built
 * - The overworld position of the caster (for return teleport)
 * - The dimension each player came from (so they return to the right world)
 * - The list of placed blocks (for cleanup on expiry)
 * - The players currently inside this domain
 */
public class DomainSession {

    /** Unique slot index — used to space multiple simultaneous domains apart. */
    public final int slotIndex;

    /** Centre block of this domain's interior in domain_space. */
    public final BlockPos domainCentre;

    /** Floor Y inside the domain (players spawn here). */
    public final int floorY;

    /** The caster entity UUID. */
    public final UUID casterUUID;

    /** Type of domain — determines interior appearance. */
    public final DomainInteriorType interiorType;

    /** Blocks placed for this domain — cleared on session end. */
    public final List<BlockPos> placedBlocks;

    /**
     * Per-player return data.
     * Key = player UUID
     * Value = [origin dimension key, origin Vec3 position]
     */
    private final Map<UUID, PlayerReturnData> returnData = new HashMap<>();

    /** Players currently inside this domain. */
    private final Set<UUID> playersInside = new HashSet<>();

    public DomainSession(int slotIndex, BlockPos domainCentre,
                          UUID casterUUID, DomainInteriorType interiorType,
                          List<BlockPos> placedBlocks) {
        this.slotIndex    = slotIndex;
        this.domainCentre = domainCentre;
        this.floorY       = domainCentre.getY() - 8 + 1; // one block above floor
        this.casterUUID   = casterUUID;
        this.interiorType = interiorType;
        this.placedBlocks = placedBlocks;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Player tracking
    // ─────────────────────────────────────────────────────────────────────────

    public void addPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!returnData.containsKey(id)) {
            returnData.put(id, new PlayerReturnData(
                    player.level().dimension(),
                    player.position()
            ));
        }
        playersInside.add(id);
    }

    public void removePlayer(UUID playerId) {
        playersInside.remove(playerId);
    }

    public boolean hasPlayer(UUID playerId) {
        return playersInside.contains(playerId);
    }

    public Set<UUID> getPlayersInside() {
        return Collections.unmodifiableSet(playersInside);
    }

    public PlayerReturnData getReturnData(UUID playerId) {
        return returnData.get(playerId);
    }

    public Collection<UUID> getAllTrackedPlayers() {
        return returnData.keySet();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Player spawn point inside the domain (slightly offset per-player so
    //  they don't all stack on the same block)
    // ─────────────────────────────────────────────────────────────────────────

    public Vec3 getSpawnPos(int playerIndex) {
        double angle = (2 * Math.PI / Math.max(1, playerIndex)) * playerIndex;
        double dx = playerIndex == 0 ? 0 : 3 * Math.cos(angle);
        double dz = playerIndex == 0 ? 0 : 3 * Math.sin(angle);
        return new Vec3(
                domainCentre.getX() + dx,
                floorY + 0.5,
                domainCentre.getZ() + dz
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Inner record
    // ─────────────────────────────────────────────────────────────────────────

    public record PlayerReturnData(ResourceKey<Level> dimension, Vec3 position) {}
}
