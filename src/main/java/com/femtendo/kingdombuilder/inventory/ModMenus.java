package com.femtendo.kingdombuilder.inventory;

import com.femtendo.kingdombuilder.KingdomBuilder;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, KingdomBuilder.MODID);

    public static final RegistryObject<MenuType<KingdomVillagerMenu>> KINGDOM_VILLAGER_MENU = MENUS.register("kingdom_villager_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                // The entity ID will be passed via network data.
                int entityId = data.readInt();
                net.minecraft.world.entity.Entity entity = inv.player.level().getEntity(entityId);
                if (entity instanceof com.femtendo.kingdombuilder.entities.KingdomVillagerEntity villager) {
                    return new KingdomVillagerMenu(windowId, inv, villager);
                }
                return null;
            }));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
