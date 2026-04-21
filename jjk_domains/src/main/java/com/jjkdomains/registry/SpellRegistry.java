package com.jjkdomains.registry;

import com.jjkdomains.JJKDomainsMod;
import com.jjkdomains.spells.InfiniteVoidSpell;
import com.jjkdomains.spells.MalevolentShrineSpell;
import com.jjkdomains.spells.DeadlySentencingSpell;
import com.jjkdomains.spells.CoffinOfIronMountainSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

public class SpellRegistry {

    // Use IronSpells spell registry key
    private static final ResourceKey<Registry<AbstractSpell>> SPELL_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spells"));

    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SPELL_REGISTRY_KEY, JJKDomainsMod.MOD_ID);

    public static final DeferredHolder<AbstractSpell, InfiniteVoidSpell> INFINITE_VOID_SPELL =
            SPELLS.register("infinite_void", InfiniteVoidSpell::new);

    public static final DeferredHolder<AbstractSpell, MalevolentShrineSpell> MALEVOLENT_SHRINE_SPELL =
            SPELLS.register("malevolent_shrine", MalevolentShrineSpell::new);

    public static final DeferredHolder<AbstractSpell, DeadlySentencingSpell> DEADLY_SENTENCING_SPELL =
            SPELLS.register("deadly_sentencing", DeadlySentencingSpell::new);

    public static final DeferredHolder<AbstractSpell, CoffinOfIronMountainSpell> COFFIN_OF_IRON_MOUNTAIN_SPELL =
            SPELLS.register("coffin_of_iron_mountain", CoffinOfIronMountainSpell::new);
}
