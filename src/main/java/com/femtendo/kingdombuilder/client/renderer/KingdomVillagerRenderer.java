package com.femtendo.kingdombuilder.client.renderer;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import com.femtendo.kingdombuilder.client.ClientSkinManager;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class KingdomVillagerRenderer extends MobRenderer<KingdomVillagerEntity, PlayerModel<KingdomVillagerEntity>> {

    public KingdomVillagerRenderer(EntityRendererProvider.Context context) {
        // POINTER: We use ModelLayers.PLAYER for the standard bipedal player model.
        // The 'false' boolean indicates it's not the slim (Alex) model.
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(KingdomVillagerEntity entity) {
        String skinId = entity.getSkinId();

        // Check if it's an external skin first
        ResourceLocation external = ClientSkinManager.getExternalSkinLocation(skinId);
        if (external != null) {
            return external;
        }

        // POINTER: Skins are expected to be in assets/kingdombuilder/textures/entity/kingdom_villager/skins/
        return ResourceLocation.fromNamespaceAndPath(KingdomBuilder.MODID, "textures/entity/kingdom_villager/skins/" + skinId + ".png");
    }
}
