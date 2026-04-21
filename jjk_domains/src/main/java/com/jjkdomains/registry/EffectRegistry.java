package com.jjkdomains.registry;

import com.jjkdomains.JJKDomainsMod;
import com.jjkdomains.effects.InfiniteVoidEffect;
import com.jjkdomains.effects.MalevolentShrineEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;

public class EffectRegistry {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, JJKDomainsMod.MOD_ID);

    public static final Holder<MobEffect> INFINITE_VOID_EFFECT =
            EFFECTS.register("infinite_void_effect", InfiniteVoidEffect::new);

    public static final Holder<MobEffect> MALEVOLENT_SHRINE_EFFECT =
            EFFECTS.register("malevolent_shrine_effect", MalevolentShrineEffect::new);
}
