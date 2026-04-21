package com.jjkdomains;

import com.jjkdomains.registry.SpellRegistry;
import com.jjkdomains.registry.ItemRegistry;
import com.jjkdomains.registry.EffectRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(JJKDomainsMod.MOD_ID)
public class JJKDomainsMod {

    public static final String MOD_ID = "jjkdomains";

    public JJKDomainsMod(IEventBus modEventBus) {
        SpellRegistry.SPELLS.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);
        EffectRegistry.EFFECTS.register(modEventBus);
    }
}
