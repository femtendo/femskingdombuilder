package com.femtendo.kingdombuilder.entities;

import com.femtendo.kingdombuilder.KingdomBuilder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, KingdomBuilder.MODID);

    public static final RegistryObject<EntityType<KingdomVillagerEntity>> KINGDOM_VILLAGER =
            ENTITY_TYPES.register("kingdom_villager",
                    () -> EntityType.Builder.of(KingdomVillagerEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.8f)
                            .build(KingdomBuilder.MODID + ":kingdom_villager"));


    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
