package com.femtendo.kingdombuilder.client.renderer;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.femtendo.kingdombuilder.entities.KingdomVillagerEntity;
import com.femtendo.kingdombuilder.client.ClientSkinManager;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;

public class KingdomVillagerRenderer extends HumanoidMobRenderer<KingdomVillagerEntity, PlayerModel<KingdomVillagerEntity>> {

    public KingdomVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        
        // POINTER: Reverting to getModelManager(). In Forge 1.21.1, this remains the correct 
        // getter for the HumanoidArmorLayer constructor.
        this.addLayer(new HumanoidArmorLayer<>(this, 
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), 
            new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager())); 
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

    @Override
    public void render(KingdomVillagerEntity entity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        
        // DEBUG: Ask the client directly if it sees the armor. 
        // We limit it to every 20 ticks so it doesn't flood your console 60 times a second.
        if (entity.tickCount % 20 == 0) {
            ItemStack chestItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (!chestItem.isEmpty()) {
                System.out.println("CLIENT RENDERER SEES ARMOR: " + chestItem.getHoverName().getString());
            }
        }

        this.setModelProperties(entity);
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    private void setModelProperties(KingdomVillagerEntity entity) {
        PlayerModel<KingdomVillagerEntity> model = this.getModel();
        
        ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHandItem = entity.getItemInHand(InteractionHand.OFF_HAND);
        
        HumanoidModel.ArmPose mainPose = mainHandItem.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        HumanoidModel.ArmPose offPose = offHandItem.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }
    }
}
