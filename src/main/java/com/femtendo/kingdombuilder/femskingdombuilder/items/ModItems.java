package com.femtendo.femskingdombuilder.items;

import com.femtendo.femskingdombuilder.FemsKingdomBuilder;
import com.femtendo.femskingdombuilder.entities.ModEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FemsKingdomBuilder.MODID);

    public static final RegistryObject<Item> KINGDOM_VILLAGER_SPAWN_EGG = ITEMS.register("kingdom_villager_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KINGDOM_VILLAGER, 0x4B2B00, 0xD4C5A1, new Item.Properties()));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
