package com.femtendo.kingdombuilder.items;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.femtendo.kingdombuilder.entities.ModEntities;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Item registry for Kingdom Builder.
 *
 * POINTER (System 11): The Zoning Tool and Wrench are both registered with
 * {@code stacksTo(1)} to enforce the "single-item tools" requirement from the
 * architecture plan. Stack-size > 1 would let a player stash multiple
 * first-corner buffers simultaneously, defeating the two-click flow.
 *
 * POINTER: Creative-tab population is wired inside {@code KingdomBuilder#addCreative}
 * — when a new item is added here, remember to also add it to the relevant
 * creative tab branch there or it won't show up for testing.
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, KingdomBuilder.MODID);

    public static final RegistryObject<Item> KINGDOM_VILLAGER_SPAWN_EGG = ITEMS.register("kingdom_villager_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KINGDOM_VILLAGER, 0x4B2B00, 0xD4C5A1, new Item.Properties()));

    // POINTER (System 11): ZONING_TOOL — two-click blueprint zone placer.
    // stacksTo(1) is mandatory: the first-corner buffer lives in the stack's
    // CustomData component, and a stack > 1 would share the buffer across
    // "copies" creating confusing duplicate-placement bugs.
    public static final RegistryObject<Item> ZONING_TOOL = ITEMS.register("zoning_tool",
            () -> new ZoningToolItem(new Item.Properties().stacksTo(1)));

    // POINTER (System 11): WRENCH — face-toggle tool for Iron Tubes. stacksTo(1)
    // mirrors tech-mod wrench conventions (Create, Mekanism) and matches the
    // architecture plan's spec.
    public static final RegistryObject<Item> WRENCH = ITEMS.register("wrench",
            () -> new WrenchItem(new Item.Properties().stacksTo(1)));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
