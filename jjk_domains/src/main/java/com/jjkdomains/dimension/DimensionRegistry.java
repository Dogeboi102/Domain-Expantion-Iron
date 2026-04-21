package com.jjkdomains.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class DimensionRegistry {

    public static final String MOD_ID = "jjkdomains";

    public static final ResourceKey<Level> DOMAIN_SPACE =
            ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "domain_space")
            );
}
