package com.femtendo.kingdombuilder.entities;

import com.femtendo.kingdombuilder.KingdomBuilder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomBuilder.MODID);

    // POINTER: We've changed the base type to Villager in the registration.
    // This is crucial for the game to recognize it as a villager-type entity and apply the correct AI mechanics.
    public static final RegistryObject<EntityType<KingdomVillagerEntity>> KINGDOM_VILLAGER =
            ENTITY_TYPES.register("kingdom_villager",
                    () -> EntityType.Builder.of(KingdomVillagerEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f) // Corrected size to match Villager
                            .build("kingdom_villager"));


    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
