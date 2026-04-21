package com.jjkdomains.registry;

import com.jjkdomains.JJKDomainsMod;
import com.jjkdomains.items.GuiltyVerdictSword;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;

public class ItemRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, JJKDomainsMod.MOD_ID);

    public static final DeferredHolder<Item, GuiltyVerdictSword> GUILTY_VERDICT_SWORD =
            ITEMS.register("guilty_verdict_sword", () -> new GuiltyVerdictSword(
                    new Item.Properties().stacksTo(1).durability(1)
            ));
}
