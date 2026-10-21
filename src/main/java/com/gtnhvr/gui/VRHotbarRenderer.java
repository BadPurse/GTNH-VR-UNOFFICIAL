package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Draws one radial hotbar (Visor VROverlayHotBar.renderRadialImage) into its own texture.
 * The 98x98 Visor image sits in the middle of a 160x160 canvas that also holds the item name
 * below it. One instance exists per hand, so both selectors can be open at the same time.
 */
@Lwjgl3Aware
public final class VRHotbarRenderer {
    public static final int IMAGE_SIZE = 98;
    public static final int CANVAS_SIZE = 160;

    private static final int FBO_SIZE = 480; // 3 texture pixels per canvas pixel
    private static final int SLOT_NUMBERS_DIFF = (112 - IMAGE_SIZE) / 2;
    private static final int ITEM_NAME_GAP = 4;
    private static final int ITEM_NAME_PADDING_X = 3;
    private static final int ITEM_NAME_PADDING_Y = 2;
    private static final float ITEM_NAME_BACKGROUND_ALPHA = 0.5F; // 0x80000000
    private static final int RING_SIZE = 26;
    private static final int RING_INSET = 5;

    private static final ResourceLocation SLOT_NUMBERS_TEX =
            new ResourceLocation("gtnhvr", "textures/gui/overlays/hotbar/slot_numbers.png");
    private static final ResourceLocation MAIN_SEL0 =
            new ResourceLocation("gtnhvr", "textures/gui/overlays/hotbar/hotbar_main_selected0.png");
    private static final ResourceLocation MAIN_SEL1 =
            new ResourceLocation("gtnhvr", "textures/gui/overlays/hotbar/hotbar_main_selected1.png");
    private static final ResourceLocation OFF_SEL0 =
            new ResourceLocation("gtnhvr", "textures/gui/overlays/hotbar/hotbar_offhand_selected0.png");
    private static final ResourceLocation OFF_SEL1 =
            new ResourceLocation("gtnhvr", "textures/gui/overlays/hotbar/hotbar_offhand_selected1.png");

    private static final RenderItem ITEM_RENDER = new RenderItem();
    private static boolean rendering = false;

    private int fbo = 0;
    private int texture = 0;
    private int depthRb = 0;

    /** True while a hotbar texture is being drawn (used to keep destination alpha opaque). */
    public static boolean isRendering() {
        return rendering;
    }

    /** Lets other offscreen overlay renderers use the same alpha-safe blending. */
    public static void markRendering(boolean value) {
        rendering = value;
    }

    public int getTextureId() {
        return texture;
    }

    private void ensureTarget() {
        if (fbo != 0) {
            return;
        }
        fbo = GLStateManager.glGenFramebuffers();
        texture = GLStateManager.glGenTextures();
        depthRb = GLStateManager.glGenRenderbuffers();

        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, FBO_SIZE, FBO_SIZE, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRb);
        GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, FBO_SIZE, FBO_SIZE);

        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRb);
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /**
     * @param selectedSlice slice under the pointer (-1 when none)
     * @param slotMain      slot held in the main hand (-1 when unknown)
     * @param slotOffhand   slot held in the off hand (-1 when none)
     */
    public void render(Minecraft mc, int selectedSlice, int slotMain, int slotOffhand) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return;
        }
        ensureTarget();

        int previousFbo = GLStateManager.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean scissor = GLStateManager.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) {
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GLStateManager.glPushAttrib(GL11.GL_VIEWPORT_BIT);
        rendering = true;
        try {
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLStateManager.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
            GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glOrtho(0.0D, CANVAS_SIZE, CANVAS_SIZE, 0.0D, 1000.0D, 3000.0D);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            GLStateManager.glTranslatef(0.0F, 0.0F, -2000.0F);
            GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                drawHotbar(mc, selectedSlice, slotMain, slotOffhand);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GLStateManager.glPopAttrib();
                GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
                GLStateManager.glPopMatrix();
                GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                GLStateManager.glPopMatrix();
            }
        } finally {
            rendering = false;
            GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GLStateManager.glPopAttrib();
            if (scissor) {
                GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void drawHotbar(Minecraft mc, int selectedSlice, int slotMain, int slotOffhand) {
        GLStateManager.glDisable(GL11.GL_LIGHTING);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        final int imageX = (CANVAS_SIZE - IMAGE_SIZE) / 2;
        final int imageY = (CANVAS_SIZE - IMAGE_SIZE) / 2;

        // ---- Main image: background of the slice under the pointer
        mc.getTextureManager().bindTexture(HotBarSlice.fromSlot(selectedSlice).getBackground());
        drawTexturedRect(imageX, imageY, IMAGE_SIZE, IMAGE_SIZE);

        // ---- Slot numbers
        if (VRClientSettings.hotbarSlotNumbers) {
            mc.getTextureManager().bindTexture(SLOT_NUMBERS_TEX);
            drawTexturedRect(imageX - SLOT_NUMBERS_DIFF, imageY - SLOT_NUMBERS_DIFF,
                    IMAGE_SIZE + 2 * SLOT_NUMBERS_DIFF, IMAGE_SIZE + 2 * SLOT_NUMBERS_DIFF);
        }

        // ---- Items
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack == null) {
                continue;
            }
            HotBarSlice slice = HotBarSlice.fromSlot(slot);
            int x = imageX + slice.getItemX();
            int y = imageY + slice.getItemY();

            GLStateManager.glPushMatrix();
            GLStateManager.glEnable(GL11.GL_DEPTH_TEST);
            GLStateManager.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            ITEM_RENDER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            RenderHelper.disableStandardItemLighting();
            GLStateManager.glDisable(GL12.GL_RESCALE_NORMAL);
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glPopMatrix();
        }

        // ---- Name of the hovered item
        drawSelectedItemName(mc, selectedSlice, imageY);

        // ---- Rings around the held items
        GLStateManager.glEnable(GL11.GL_BLEND);
        GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);

        // Main hand: use the same pre-coloured main-hand texture as Visor.
        // Do not apply an extra tint or draw the old square placeholder.
        drawRing(mc, slotMain, imageX, imageY, MAIN_SEL0, MAIN_SEL1);

        // Offhand: use the same pre-coloured offhand texture as Visor.
        if (slotOffhand >= 0 && slotOffhand < 9) {
            drawRing(mc, slotOffhand, imageX, imageY, OFF_SEL0, OFF_SEL1);
        }
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawRing(Minecraft mc, int slot, int imageX, int imageY,
                          ResourceLocation ring, ResourceLocation centerRing) {
        HotBarSlice slice = HotBarSlice.fromSlot(slot);
        if (slice == HotBarSlice.NOT_SELECTED) {
            return;
        }

        int previousProgram = GLStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GLStateManager.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // The original Visor renderer draws these after item rendering through GuiTexture.blit.
            // Use Angelica's GUI/fixed-function path and a Tessellator quad rather than the custom
            // GLStateManager immediate-mode quad, which can inherit item-render state.
            GLStateManager.glUseProgram(0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE);
            GLStateManager.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(slice.getSlot() != 0 ? ring : centerRing);
            drawSelectionTexture(imageX + slice.getItemX() - RING_INSET,
                    imageY + slice.getItemY() - RING_INSET, RING_SIZE, RING_SIZE);
        } finally {
            GLStateManager.glPopAttrib();
            GLStateManager.glActiveTexture(previousActiveTexture);
            GLStateManager.glUseProgram(previousProgram);
        }
    }

    private void drawSelectedItemName(Minecraft mc, int selectedSlice, int imageY) {
        if (selectedSlice < 0 || selectedSlice >= 9) {
            return;
        }
        ItemStack stack = mc.thePlayer.inventory.mainInventory[selectedSlice];
        FontRenderer font = mc.fontRenderer;
        if (stack == null || font == null) {
            return;
        }

        String name = stack.getRarity().rarityColor.toString()
                + (stack.hasDisplayName() ? EnumChatFormatting.ITALIC.toString() : "")
                + stack.getDisplayName();
        int nameWidth = font.getStringWidth(name);
        int nameHeight = font.FONT_HEIGHT - 1;
        int nameX = (CANVAS_SIZE - nameWidth) / 2;
        int nameY = imageY + IMAGE_SIZE + ITEM_NAME_GAP;
        if (VRClientSettings.hotbarSlotNumbers) {
            nameY += SLOT_NUMBERS_DIFF;
        }

        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glColor4f(0.0F, 0.0F, 0.0F, ITEM_NAME_BACKGROUND_ALPHA);
        drawRect(nameX - ITEM_NAME_PADDING_X, nameY - ITEM_NAME_PADDING_Y,
                nameX + nameWidth + ITEM_NAME_PADDING_X, nameY + nameHeight + ITEM_NAME_PADDING_Y);

        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        font.drawStringWithShadow(name, nameX, nameY, 0xFFFFFF);
    }

    /**
     * Minecraft 1.7.10 equivalent of Visor GuiTexture.blit: submit a textured quad through
     * Tessellator and store white tint per vertex, so previously rendered item state cannot
     * recolour or reorder the selection texture.
     */
    private static void drawSelectionTexture(float x, float y, float w, float h) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);
        tessellator.addVertexWithUV(x, y + h, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + w, y + h, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + w, y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
    }

    private static void drawTexturedRect(float x, float y, float w, float h) {
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glTexCoord2f(0.0F, 0.0F); GLStateManager.glVertex2f(x, y);
        GLStateManager.glTexCoord2f(1.0F, 0.0F); GLStateManager.glVertex2f(x + w, y);
        GLStateManager.glTexCoord2f(1.0F, 1.0F); GLStateManager.glVertex2f(x + w, y + h);
        GLStateManager.glTexCoord2f(0.0F, 1.0F); GLStateManager.glVertex2f(x, y + h);
        GLStateManager.glEnd();
    }

    private static void drawRect(float x0, float y0, float x1, float y1) {
        GLStateManager.glBegin(GL11.GL_QUADS);
        GLStateManager.glVertex2f(x0, y0);
        GLStateManager.glVertex2f(x1, y0);
        GLStateManager.glVertex2f(x1, y1);
        GLStateManager.glVertex2f(x0, y1);
        GLStateManager.glEnd();
    }

    public void destroy() {
        if (fbo != 0) {
            GLStateManager.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (texture != 0) {
            GLStateManager.glDeleteTextures(texture);
            texture = 0;
        }
        if (depthRb != 0) {
            GLStateManager.glDeleteRenderbuffers(depthRb);
            depthRb = 0;
        }
    }
}
