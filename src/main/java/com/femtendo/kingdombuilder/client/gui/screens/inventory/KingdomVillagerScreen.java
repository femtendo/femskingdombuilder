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
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(KingdomBuilder.MODID, "textures/gui/desiredgui.png");

    public KingdomVillagerScreen(KingdomVillagerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        // Standard inventory sizes
        this.imageWidth = 174;
        this.imageHeight = 174;
    }

    @Override
    protected void init() {
        super.init();
        // --- 1. THE MAIN TITLE ("Kingdom Villager") ---
        // X = Horizontal position. Right now it calculates the exact center of the GUI.
        this.titleLabelX = 6;
        // Y = Vertical position. Increase this to move the text DOWN.
        this.titleLabelY = 6; 
        
        // --- 2. THE INVENTORY TITLE ("Inventory") ---
        // X = Horizontal position. Distance from the left edge of the GUI box.
        this.inventoryLabelX = 8;
        // Y = Vertical position. Increase this to move the text DOWN.
        this.inventoryLabelY = 82; 
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

   @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // 1. Save the standard rendering state
        pGuiGraphics.pose().pushPose(); 
        
        // 2. Define your global text scale (0.8F = 80%)
        float textScale = 0.8F; 
        pGuiGraphics.pose().scale(textScale, textScale, 1.0F); 
        
        // 3. Convert coordinates for the Main Title
        int scaledTitleX = (int) (this.titleLabelX / textScale);
        int scaledTitleY = (int) (this.titleLabelY / textScale);
        
        // 4. Convert coordinates for the Inventory Title
        int scaledInvX = (int) (this.inventoryLabelX / textScale);
        int scaledInvY = (int) (this.inventoryLabelY / textScale);
        
        // 5. Draw BOTH strings using the scaled graphics engine
        pGuiGraphics.drawString(this.font, Component.literal("Kingdom Villager"), scaledTitleX, scaledTitleY, 4210752, false);
        pGuiGraphics.drawString(this.font, Component.literal("Inventory"), scaledInvX, scaledInvY, 4210752, false);
        
        // 6. Restore the engine back to standard 1.0 scale for drawing items
        pGuiGraphics.pose().popPose(); 
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // This longer overload forces the engine to read all 357x337 source pixels 
        // and squish them into the 176x166 screen box.
        pGuiGraphics.blit(TEXTURE, x, y, this.imageWidth, this.imageHeight, 0.0F, 0.0F, 357, 337, 357, 337);
    }
}
