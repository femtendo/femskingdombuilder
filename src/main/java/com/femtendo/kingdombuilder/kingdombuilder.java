package com.femtendo.kingdombuilder;

import com.femtendo.kingdombuilder.client.renderer.KingdomVillagerRenderer;
import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import com.femtendo.kingdombuilder.entities.ModEntities;
import com.femtendo.kingdombuilder.items.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(KingdomBuilder.MODID)
public class KingdomBuilder {
    public static final String MODID = "kingdombuilder";
    private static final Logger LOGGER = LogUtils.getLogger();

    public KingdomBuilder() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(this::entityAttributeEvent);

        createConfigFolders();
    }

    private void createConfigFolders() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("kingdomconfig/skins");
        try {
            Files.createDirectories(configPath);
            LOGGER.info("Created Kingdom Builder skins directory at: {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create Kingdom Builder skins directory", e);
        }
    }


    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.KINGDOM_VILLAGER_SPAWN_EGG);
        }
    }

    public void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(ModEntities.KINGDOM_VILLAGER.get(), KingdomVillagerEntity.createAttributes().build());
    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // POINTER: Load external skins from the config directory on the client.
            com.femtendo.kingdombuilder.client.ClientSkinManager.loadExternalSkins();
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            // POINTER: Hooking the renderer to our entity. This ensures it uses the Player-like model.
            event.registerEntityRenderer(ModEntities.KINGDOM_VILLAGER.get(), KingdomVillagerRenderer::new);
        }
    }
}
