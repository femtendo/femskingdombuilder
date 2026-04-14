package com.femtendo.kingdombuilder.client.gui.screens.inventory;

import com.femtendo.kingdombuilder.KingdomBuilder;
import com.femtendo.kingdombuilder.inventory.KingdomVillagerMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class KingdomVillagerScreen extends AbstractContainerScreen<KingdomVillagerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KingdomBuilder.MODID, "textures/gui/container/kingdom_villager.png");

    public KingdomVillagerScreen(KingdomVillagerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        // Standard inventory sizes
        this.imageWidth = 176;
        this.imageHeight = 166;
        // POINTER: The generic inventory has 1 row of 8 slots, plus 1 slot for the mainhand. 
        // We'll use a generic texture or a custom one. Since we don't have a custom one yet, 
        // we'll use standard generic_54 or similar visually, but ideally we load our custom one.
        // For now, let's just render the background without a texture or a default one if it's missing.
        // Actually, let's use the standard dispenser texture or something if we don't have it,
        // but we'll stick to a custom path that the user can fill in.
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // POINTER: If texture is missing, this will render the default missing texture (purple/black).
        // A real mod should have the texture at assets/kingdombuilder/textures/gui/container/kingdom_villager.png.
        // For now, let's draw a fallback generic 9-slot background if possible, or just the custom one.
        // We will just draw the custom one.
        pGuiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }
}
