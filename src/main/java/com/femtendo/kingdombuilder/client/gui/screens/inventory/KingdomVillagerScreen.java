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
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // Clear standard titles to handle them manually, or adjust standard offsets
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 6;
        
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73; // Right above player inventory which starts at 84
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        // POINTER: Custom title rendering for cleaner UI instead of standard abstract container screen rendering
        pGuiGraphics.drawString(this.font, Component.literal("Kingdom Villager"), this.titleLabelX, this.titleLabelY, 4210752, false);
        pGuiGraphics.drawString(this.font, Component.literal("Inventory"), this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // POINTER: If texture is 357x337, but we want 176x166, we use the blit overload 
        // that specifies texture size to scale it correctly instead of assuming 256x256.
        pGuiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 357, 337);
    }
}
