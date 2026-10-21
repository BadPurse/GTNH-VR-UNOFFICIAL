package com.gtnhvr.gui;

import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
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
        fbo = GL30.glGenFramebuffers();
        texture = GL11.glGenTextures();
        depthRb = GL30.glGenRenderbuffers();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, FBO_SIZE, FBO_SIZE, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRb);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, FBO_SIZE, FBO_SIZE);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRb);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
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

        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glPushAttrib(GL11.GL_VIEWPORT_BIT);
        rendering = true;
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, CANVAS_SIZE, CANVAS_SIZE, 0.0D, 1000.0D, 3000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                drawHotbar(mc, selectedSlice, slotMain, slotOffhand);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GL11.glPopAttrib();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
        } finally {
            rendering = false;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glPopAttrib();
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void drawHotbar(Minecraft mc, int selectedSlice, int slotMain, int slotOffhand) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

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

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            ITEM_RENDER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        }

        // ---- Name of the hovered item
        drawSelectedItemName(mc, selectedSlice, imageY);

        // ---- Rings around the held items
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Main Hand (Right Hand): tint the textured selection ring cyan/blue.
        // Keep the original ring texture; do not draw the old square placeholder.
        drawRing(mc, slotMain, imageX, imageY, OFF_SEL0, OFF_SEL1);

        // Off Hand (Left Hand): the source texture already contains the orange colour.
        if (slotOffhand >= 0 && slotOffhand < 9) {
            drawRing(mc, slotOffhand, imageX, imageY, MAIN_SEL0, MAIN_SEL1);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawRing(Minecraft mc, int slot, int imageX, int imageY,
                          ResourceLocation ring, ResourceLocation centerRing) {
        HotBarSlice slice = HotBarSlice.fromSlot(slot);
        if (slice == HotBarSlice.NOT_SELECTED) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            mc.getTextureManager().bindTexture(slice.getSlot() != 0 ? ring : centerRing);
            // The Visor PNG is already cyan/orange. Draw it without applying a second vertex tint.
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawTexturedRect(imageX + slice.getItemX() - RING_INSET, imageY + slice.getItemY() - RING_INSET,
                    RING_SIZE, RING_SIZE);
        } finally {
            GL11.glPopAttrib();
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

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, ITEM_NAME_BACKGROUND_ALPHA);
        drawRect(nameX - ITEM_NAME_PADDING_X, nameY - ITEM_NAME_PADDING_Y,
                nameX + nameWidth + ITEM_NAME_PADDING_X, nameY + nameHeight + ITEM_NAME_PADDING_Y);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        font.drawStringWithShadow(name, nameX, nameY, 0xFFFFFF);
    }

    private static void drawTexturedRect(float x, float y, float w, float h) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(x + w, y);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(x, y + h);
        GL11.glEnd();
    }

    private static void drawRect(float x0, float y0, float x1, float y1) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x0, y1);
        GL11.glEnd();
    }

    public void destroy() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        if (depthRb != 0) {
            GL30.glDeleteRenderbuffers(depthRb);
            depthRb = 0;
        }
    }
}
