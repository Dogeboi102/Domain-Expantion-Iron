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

@EventBusSubscriber(modid = JJKDomainsMod.MOD_ID)
public class DomainTeleportManager {

    private static final int SLOT_SPACING = 512;
    private static final int DOMAIN_Y = 128;

    private static final AtomicInteger nextSlot = new AtomicInteger(0);
    private static final Map<UUID, DomainSession> activeSessions = new HashMap<>();
    private static final Map<UUID, DomainSession> casterToSession = new HashMap<>();

    public static DomainSession openDomain(MinecraftServer server,
                                            LivingEntity caster,
                                            List<ServerPlayer> nearbyPlayers,
                                            DomainInteriorType type) {
        ServerLevel domainLevel = server.getLevel(DimensionRegistry.DOMAIN_SPACE);
        if (domainLevel == null) {
            caster.sendSystemMessage(Component.literal(
                    "§c[JJK] domain_space dimension not found. Check mod installation."));
            return null;
        }

        int slot = nextSlot.getAndIncrement();
        BlockPos centre = new BlockPos(slot * SLOT_SPACING, DOMAIN_Y, 0);

        List<BlockPos> placed = DomainSphereBuilder.buildDomain(domainLevel, centre, type);
        DomainSession session = new DomainSession(slot, centre, caster.getUUID(), type, placed);

        activeSessions.put(session.casterUUID, session);
        casterToSession.put(caster.getUUID(), session);

        List<ServerPlayer> all = new ArrayList<>();
        if (caster instanceof ServerPlayer sp) all.add(sp);
        all.addAll(nearbyPlayers);

        for (int i = 0; i < all.size(); i++) {
            ServerPlayer player = all.get(i);
            session.addPlayer(player);
            Vec3 spawnPos = session.getSpawnPos(i);
            // 1.21.1 teleportTo(ServerLevel, x, y, z, yRot, xRot)
            player.teleportTo(domainLevel,
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    player.getYRot(), player.getXRot());
        }

        return session;
    }

    public static void closeDomain(MinecraftServer server, DomainSession session) {
        ServerLevel domainLevel = server.getLevel(DimensionRegistry.DOMAIN_SPACE);

        for (UUID id : session.getAllTrackedPlayers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;

            DomainSession.PlayerReturnData ret = session.getReturnData(id);
            if (ret == null) continue;

            ServerLevel returnLevel = server.getLevel(ret.dimension());
            if (returnLevel == null) returnLevel = server.overworld();

            player.teleportTo(returnLevel,
                    ret.position().x, ret.position().y, ret.position().z,
                    player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("§7The domain has collapsed. You return."));
        }

        if (domainLevel != null) {
            DomainSphereBuilder.clearDomain(domainLevel, session.placedBlocks);
        }

        activeSessions.remove(session.casterUUID);
        casterToSession.remove(session.casterUUID);
    }

    public static DomainSession getSessionForCaster(UUID casterUUID) {
        return casterToSession.get(casterUUID);
    }

    public static boolean isCasterInActiveDomain(UUID casterUUID) {
        return casterToSession.containsKey(casterUUID);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        activeSessions.clear();
        casterToSession.clear();
        nextSlot.set(0);
    }
}
