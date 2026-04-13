package com.femtendo.kingdombuilder.client;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages external skins loaded from the config directory.
 * This class is strictly client-side.
 */
@OnlyIn(Dist.CLIENT)
public class ClientSkinManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ResourceLocation> EXTERNAL_SKINS = new HashMap<>();
    private static final List<String> EXTERNAL_SKIN_IDS = new ArrayList<>();

    // POINTER: This directory is shared between server and client in singleplayer,
    // but textures are only loaded on the client.
    private static final Path SKINS_DIR = FMLPaths.CONFIGDIR.get().resolve("kingdomconfig/skins");

    /**
     * Loads and caches all .png skins from the external skins directory.
     * This should be called during client initialization.
     */
    public static void loadExternalSkins() {
        EXTERNAL_SKINS.clear();
        EXTERNAL_SKIN_IDS.clear();

        if (!Files.exists(SKINS_DIR)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(SKINS_DIR, "*.png")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String skinId = fileName.substring(0, fileName.lastIndexOf('.'));
                
                // POINTER: Registering as DynamicTexture and mapping to a ResourceLocation
                // avoids frame drops during rendering.
                ResourceLocation location = loadExternalTexture(skinId, path);
                if (location != null) {
                    EXTERNAL_SKINS.put(skinId, location);
                    EXTERNAL_SKIN_IDS.add(skinId);
                    LOGGER.info("Loaded external skin: {} as {}", skinId, location);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read external skins directory", e);
        }
    }

    private static ResourceLocation loadExternalTexture(String skinId, Path path) {
        try (InputStream is = new FileInputStream(path.toFile())) {
            NativeImage nativeImage = NativeImage.read(is);
            DynamicTexture texture = new DynamicTexture(nativeImage);
            
            // Generate a unique resource location for this dynamic texture
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(KingdomBuilder.MODID, "external_skin_" + skinId.toLowerCase());
            
            Minecraft.getInstance().getTextureManager().register(location, texture);
            return location;
        } catch (IOException e) {
            LOGGER.error("Failed to load external skin texture: {}", path, e);
            return null;
        }
    }

    public static ResourceLocation getExternalSkinLocation(String skinId) {
        return EXTERNAL_SKINS.get(skinId);
    }

    public static List<String> getExternalSkinIds() {
        return new ArrayList<>(EXTERNAL_SKIN_IDS);
    }
    
    public static boolean isExternalSkin(String skinId) {
        return EXTERNAL_SKINS.containsKey(skinId);
    }
}
