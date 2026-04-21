package com.jjkdomains.dimension;

import com.jjkdomains.JJKDomainsMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DimensionRegistry {

    /**
     * The shared void dimension all domain interiors are built inside.
     * Each active domain gets its own isolated platform at a unique XZ offset
     * so multiple simultaneous domains don't interfere with each other.
     */
    public static final ResourceKey<Level> DOMAIN_SPACE =
            ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(JJKDomainsMod.MOD_ID, "domain_space")
            );
}
