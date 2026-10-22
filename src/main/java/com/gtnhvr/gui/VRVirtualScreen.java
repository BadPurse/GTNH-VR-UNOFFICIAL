package com.gtnhvr.gui;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnhvr.settings.VROverlayConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * 3D Virtual Screen for rendering 2D Minecraft GUIs, Menus, Inventories, and Settings
 * as a comfortable floating display in VR space, eliminating flat 2D full-FOV stretching.
 * Matches Visor's VROverlayGameScreen design and behavior, including 1:1 Drag Bar & Resize Square handles.
 */
@Lwjgl3Aware
public final class VRVirtualScreen {
    private static final VRVirtualScreen INSTANCE = new VRVirtualScreen();

    public enum ScreenHandle {
        NONE, SCREEN, DRAG_BAR, RESIZE_HANDLE
    }

    // Virtual screen pose in room/world space
    private final Vector3f screenPos = new Vector3f(0.0F, 1.3F, -2.2F);
    private final Quaternionf screenRot = new Quaternionf();
    private float screenYaw = 0.0F;
    private float screenPitch = 0.0F;
    private float screenWidth = 2.2F;  // meters
    private float screenHeight = 1.25F; // meters

    private boolean inMenuRoom = true;
    private boolean orientationInitialized = false;
    private boolean customPositioned = false;

    // Laser cursor hit coordinates in normalized [0, 1] space
    private float cursorU = -1.0F;
    private float cursorV = -1.0F;
    private boolean cursorActive = false;
    private final Vector3f lastHitPoint = new Vector3f();

    // 1:1 Visor Drag Bar & Resize Handle state
    private ScreenHandle hoveredHandle = ScreenHandle.NONE;
    private boolean isDragging = false;
    private boolean isResizing = false;

    private final Vector3f dragLocalOffset = new Vector3f();
    private final Quaternionf dragLocalRot = new Quaternionf();
    private float initialHandDist = 1.0F;
    private float initialScreenWidth = 2.2F;

    private static int lastInteractedBlockX = 0;
    private static int lastInteractedBlockY = 0;
    private static int lastInteractedBlockZ = 0;
    private static long lastInteractedBlockTime = 0L;
    private static boolean hasInteractedBlock = false;
    private static java.lang.ref.WeakReference<net.minecraft.entity.Entity> lastInteractedEntity = null;
    private static long lastInteractedEntityTime = 0L;

    private boolean isBlockContainer = false;
    private double containerWorldX = 0.0D;
    private double containerWorldY = 0.0D;
    private double containerWorldZ = 0.0D;
    private java.lang.ref.WeakReference<net.minecraft.entity.Entity> containerEntity = null;

    private VRVirtualScreen() {}

    public static VRVirtualScreen getInstance() {
        return INSTANCE;
    }

    public static void setLastInteractedBlock(int x, int y, int z) {
        lastInteractedBlockX = x;
        lastInteractedBlockY = y;
        lastInteractedBlockZ = z;
        lastInteractedBlockTime = System.currentTimeMillis();
        hasInteractedBlock = true;
    }

    public static void setLastInteractedEntity(net.minecraft.entity.Entity entity) {
        if (entity != null) {
            lastInteractedEntity = new java.lang.ref.WeakReference<>(entity);
            lastInteractedEntityTime = System.currentTimeMillis();
        }
    }

    /**
     * Updates screen orientation based on whether the player is in the main menu
     * or opening an in-game GUI (inventory, container, pause menu, options, etc.).
     */
    public void updateScreenPlacement(boolean inMainMenu, Vector3f hmdPos, Quaternionf hmdRot) {
        inMenuRoom = inMainMenu;

        Minecraft mc = Minecraft.getMinecraft();

        if (customPositioned || isDragging || isResizing) {
            return;
        }

        if (inMainMenu) {
            float floorY = com.gtnhvr.OpenXRRuntime.isStageSpace() ? 0.0F : -1.62F;
            screenPos.set(0.0F, floorY + 1.40F, -2.2F);
            screenRot.identity();
            screenYaw = 0.0F;
            screenPitch = 0.0F;
            screenWidth = 2.4F;
            screenHeight = 1.35F;
            orientationInitialized = true;
            return;
        }

        VROverlayConfig.Entry cfg = VROverlayConfig.get(VROverlayConfig.GAME_SCREEN);
        if (!cfg.anchor.equals("WORLD")) {
            placeFollowing(mc, cfg, hmdPos, hmdRot);
            return;
        }

        // 3D World Container Overlay (Visor VROverlayContainer parity):
        // Automatically anchors containers (chests, furnaces, machines, etc.) above the interacted block/entity in 3D world space.
        if (mc != null && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer
                && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory)) {
            if (!orientationInitialized) {
                long now = System.currentTimeMillis();
                if (hasInteractedBlock && (now - lastInteractedBlockTime < 4000L)) {
                    isBlockContainer = true;
                    containerEntity = null;
                    containerWorldX = lastInteractedBlockX + 0.5D;
                    containerWorldY = lastInteractedBlockY + 1.15D;
                    containerWorldZ = lastInteractedBlockZ + 0.5D;
                    screenWidth = 1.75F * cfg.scale;
                    screenHeight = 1.25F * cfg.scale;
                    orientationInitialized = true;
                } else if (lastInteractedEntity != null && lastInteractedEntity.get() != null && (now - lastInteractedEntityTime < 4000L)) {
                    net.minecraft.entity.Entity ent = lastInteractedEntity.get();
                    isBlockContainer = true;
                    containerEntity = lastInteractedEntity;
                    containerWorldX = ent.posX;
                    containerWorldY = ent.posY + ent.getEyeHeight() + 0.35D;
                    containerWorldZ = ent.posZ;
                    screenWidth = 1.75F * cfg.scale;
                    screenHeight = 1.25F * cfg.scale;
                    orientationInitialized = true;
                }
            }

            if (isBlockContainer) {
                if (containerEntity != null && containerEntity.get() != null) {
                    net.minecraft.entity.Entity ent = containerEntity.get();
                    if (!ent.isDead) {
                        containerWorldX = ent.posX;
                        containerWorldY = ent.posY + ent.getEyeHeight() + 0.35D;
                        containerWorldZ = ent.posZ;
                    }
                }

                if (mc.thePlayer != null) {
                    double dx = mc.thePlayer.posX - containerWorldX;
                    double dy = mc.thePlayer.posY - containerWorldY;
                    double dz = mc.thePlayer.posZ - containerWorldZ;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > 25.0D) { // Auto-close container if player walks > 5 blocks away (Visor isOverlayNearPlayer)
                        mc.thePlayer.closeScreen();
                        resetOrientation();
                        return;
                    }
                }

                Vector3f roomPos = com.gtnhvr.VRControllerHandler.getInstance().worldToRoom(containerWorldX, containerWorldY, containerWorldZ, 1.0F);
                screenPos.set(roomPos);

                Vector3f dir = new Vector3f(screenPos).sub(hmdPos);
                float dist = dir.length();
                float yawRad = (float) Math.PI + (float) Math.atan2(dir.x, dir.z);
                float pitch = (float) Math.toDegrees(Math.asin(Math.max(-0.999f, Math.min(0.999f, dir.y / Math.max(0.1F, dist)))));
                screenYaw = (float) Math.toDegrees(yawRad);
                screenPitch = pitch;
                screenRot.set(VRCustomOverlayRenderer.getConfiguredRotation(cfg, hmdRot));
                return;
            }
        }

        if (!orientationInitialized) {
            boolean isChat = (mc != null && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat);

            // Floating screen directly in front of headset (Visor 1:1 orient())
            Vector3f forward = hmdRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            forward.y = 0.0F;
            if (forward.lengthSquared() < 1e-4F) {
                forward.set(0.0F, 0.0F, -1.0F);
            } else {
                forward.normalize();
            }

            float dist = isChat ? Math.max(0.3F, cfg.distance - 0.10F) : cfg.distance;
            float yOffset = (isChat ? 0.22F : -0.06F) + cfg.offsetY;
            float pitch = isChat ? -4.0F : -8.0F;

            screenPos.set(hmdPos.x + forward.x * dist - forward.z * cfg.offsetX, hmdPos.y + yOffset,
                    hmdPos.z + forward.z * dist + forward.x * cfg.offsetX);
            float yawRad = (float) Math.PI + (float) Math.atan2(forward.x, forward.z);
            screenYaw = (float) Math.toDegrees(yawRad);
            screenPitch = pitch;
            screenRot.set(VRCustomOverlayRenderer.getConfiguredRotation(cfg, hmdRot));

            screenWidth = 1.75F * cfg.scale;
            screenHeight = 1.25F * cfg.scale;
            orientationInitialized = true;
        }
        // Position and orientation anchors are independent in Visor. Re-evaluate rotation
        // each frame so orientation sliders remain live even for a world-positioned screen.
        screenRot.set(VRCustomOverlayRenderer.getConfiguredRotation(cfg, hmdRot));
    }

    /** Anchor HMD / MAIN_HAND / OFFHAND: the screen follows that reference every frame. */
    private void placeFollowing(Minecraft mc, VROverlayConfig.Entry cfg, Vector3f hmdPos, Quaternionf hmdRot) {
        boolean isChat = (mc != null && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat);
        float dist = isChat ? Math.max(0.3F, cfg.distance - 0.10F) : cfg.distance;
        Vector3f pos = new Vector3f();

        if (cfg.anchor.equals("HMD")) {
            Vector3f forward = hmdRot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
            forward.y = 0.0F;
            if (forward.lengthSquared() < 1e-4F) {
                forward.set(0.0F, 0.0F, -1.0F);
            } else {
                forward.normalize();
            }
            pos.set(hmdPos.x + forward.x * dist - forward.z * cfg.offsetX,
                    hmdPos.y + (isChat ? 0.22F : -0.06F) + cfg.offsetY,
                    hmdPos.z + forward.z * dist + forward.x * cfg.offsetX);
        } else {
            com.gtnhvr.VRControllerHandler handler = com.gtnhvr.VRControllerHandler.getInstance();
            boolean main = cfg.anchor.equals("MAIN_HAND");
            Vector3f handPos = main ? handler.getRightHandPos() : handler.getLeftHandPos();
            Quaternionf handRot = main ? handler.getRightHandRot() : handler.getLeftHandRot();
            pos.set(handPos).add(handRot.transform(new Vector3f(cfg.offsetX, cfg.offsetY, -dist), new Vector3f()));
        }

        Vector3f dir = new Vector3f(pos).sub(hmdPos);
        dir.y = 0.0F;
        if (dir.lengthSquared() < 1e-4F) {
            dir.set(0.0F, 0.0F, -1.0F);
        } else {
            dir.normalize();
        }
        float yawRad = (float) Math.PI + (float) Math.atan2(dir.x, dir.z);
        screenPos.set(pos);
        screenYaw = (float) Math.toDegrees(yawRad);
        screenPitch = isChat ? -4.0F : -8.0F;
        screenRot.set(VRCustomOverlayRenderer.getConfiguredRotation(cfg, hmdRot));
        screenWidth = 1.75F * cfg.scale;
        screenHeight = 1.25F * cfg.scale;
        orientationInitialized = true;
    }

    public void resetOrientation() {
        orientationInitialized = false;
        customPositioned = false;
        isDragging = false;
        isResizing = false;
        hoveredHandle = ScreenHandle.NONE;
        hasInteractedBlock = false;
        lastInteractedEntity = null;
        isBlockContainer = false;
        containerEntity = null;
        VRHudRenderer.resetSmoothing();
    }

    /**
     * Starts 6DoF dragging with the given hand pose.
     */
    public void startDragging(Vector3f handPos, Quaternionf handRot) {
        if (handPos == null || handRot == null) return;
        dragLocalOffset.set(screenPos).sub(handPos);
        new Quaternionf(handRot).conjugate().transform(dragLocalOffset);
        dragLocalRot.set(new Quaternionf(handRot).conjugate().mul(screenRot));
        isDragging = true;
        customPositioned = true;
        isBlockContainer = false;
        containerEntity = null;
    }

    public void updateDragging(Vector3f handPos, Quaternionf handRot) {
        if (!isDragging || handPos == null || handRot == null) return;
        Vector3f newPos = new Vector3f(dragLocalOffset);
        handRot.transform(newPos);
        newPos.add(handPos);
        screenPos.set(newPos);

        Quaternionf newRot = new Quaternionf(handRot).mul(dragLocalRot);
        screenRot.set(newRot);
    }

    public void stopDragging() {
        isDragging = false;
    }

    /**
     * Starts resizing with the given hand position.
     */
    public void startResizing(Vector3f handPos) {
        if (handPos == null) return;
        initialHandDist = Math.max(0.1F, new Vector3f(handPos).sub(screenPos).length());
        initialScreenWidth = screenWidth;
        isResizing = true;
        customPositioned = true;
    }

    public void updateResizing(Vector3f handPos) {
        if (!isResizing || handPos == null) return;
        float currentDist = new Vector3f(handPos).sub(screenPos).length();
        float ratio = currentDist / initialHandDist;
        screenWidth = Math.max(0.8F, Math.min(4.5F, initialScreenWidth * ratio));
    }

    public void stopResizing() {
        isResizing = false;
    }

    public float getScreenAspect() {
        int captureWidth = VRGuiRenderer.getCaptureWidth();
        int captureHeight = VRGuiRenderer.getCaptureHeight();
        return captureWidth > 0 && captureHeight > 0
                ? (float) captureHeight / (float) captureWidth
                : (9.0F / 16.0F);
    }

    public Vector3f getDragBarWorldPos() {
        float aspect = getScreenAspect();
        float halfW = screenWidth * 0.5F;
        float halfH = screenWidth * aspect * 0.5F;
        float barGap = halfH * 0.04F;
        float barHalfH = halfH * 0.025F;
        float barCenterY = -halfH - barGap - barHalfH;
        Vector3f local = new Vector3f(0.0F, barCenterY, 0.001F);
        return new Vector3f(screenPos).add(new Quaternionf(screenRot).transform(local));
    }

    public Vector3f getResizeHandleWorldPos() {
        float aspect = getScreenAspect();
        float halfW = screenWidth * 0.5F;
        float halfH = screenWidth * aspect * 0.5F;
        float barHalfW = halfW * 0.18F;
        float barHalfH = halfH * 0.025F;
        float barGap = halfH * 0.04F;
        float barCenterY = -halfH - barGap - barHalfH;
        float handleGap = barHalfW * 0.20F;
        float handleSide = barHalfH * 1.3F;
        float localX = barHalfW + handleGap + handleSide;
        Vector3f local = new Vector3f(localX, barCenterY, 0.001F);
        return new Vector3f(screenPos).add(new Quaternionf(screenRot).transform(local));
    }

    /**
     * Renders the 3D virtual screen quad and Visor 1:1 Drag Bar + Resize Square handles.
     */
    public void render(int eyeIndex, int fboTextureId, int texW, int texH) {
        if (fboTextureId <= 0 || texW <= 0 || texH <= 0) return;

        VROverlayConfig.Entry overlayConfig = VROverlayConfig.get(VROverlayConfig.GAME_SCREEN);
        float aspect = (float) texH / (float) texW;
        screenHeight = screenWidth * aspect;
        float halfW = screenWidth * 0.5F;
        float halfH = screenHeight * 0.5F;

        // Visor exact handle dimensions
        float barHalfW = halfW * 0.18F;
        float barHalfH = halfH * 0.025F;
        float barGap   = halfH * 0.04F;
        float barCenterY = -halfH - barGap - barHalfH;
        float barLeft  = -barHalfW;
        float barRight =  barHalfW;
        float barBottom = barCenterY - barHalfH;
        float barTop    = barCenterY + barHalfH;

        float handleGap = barHalfW * 0.20F;
        float handleSide = barHalfH * 1.3F;
        float resizeLeft = barRight + handleGap;
        float resizeRight = resizeLeft + handleSide * 2.0F;
        float resizeBottom = barCenterY - handleSide;
        float resizeTop = barCenterY + handleSide;

        GLStateManager.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GLStateManager.glPushMatrix();

        try {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
            // The captured GUI is a flat overlay: no depth test or depth writes, otherwise NEI
            // tooltips and item overlays z-fight with the quad and flicker between the eyes.
            GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
            GLStateManager.glDepthMask(false);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Transform into virtual screen local space via full 6DoF rotation
            GLStateManager.glTranslatef(screenPos.x, screenPos.y, screenPos.z);
            org.joml.AxisAngle4f aa = new org.joml.AxisAngle4f(screenRot);
            if (aa.angle != 0.0F && (aa.x != 0.0F || aa.y != 0.0F || aa.z != 0.0F)) {
                GLStateManager.glRotatef((float) Math.toDegrees(aa.angle), aa.x, aa.y, aa.z);
            }

            // 1. Textured virtual screen surface
            GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, fboTextureId);
            GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, overlayConfig.visible ? overlayConfig.opacity : 0.0F);

            GLStateManager.glBegin(GL11.GL_QUADS);
            GLStateManager.glTexCoord2f(0.0F, 0.0F); GLStateManager.glVertex3f(-halfW, -halfH, 0.0F);
            GLStateManager.glTexCoord2f(1.0F, 0.0F); GLStateManager.glVertex3f( halfW, -halfH, 0.0F);
            GLStateManager.glTexCoord2f(1.0F, 1.0F); GLStateManager.glVertex3f( halfW,  halfH, 0.0F);
            GLStateManager.glTexCoord2f(0.0F, 1.0F); GLStateManager.glVertex3f(-halfW,  halfH, 0.0F);
            GLStateManager.glEnd();

            // 2. Visor 1:1 Drag Bar & Resize Handle (disabled in Main Menu)
            Minecraft mc = Minecraft.getMinecraft();
            boolean allowHandles = (overlayConfig.visible && mc != null && mc.theWorld != null && !inMenuRoom);
            if (allowHandles && (isDragging || isResizing || hoveredHandle != ScreenHandle.NONE)) {
                GLStateManager.glDisable(GL11.GL_TEXTURE_2D);

                // Visor 1:1 Handle Colors
                // Drag Bar (horizontal line / bar)
                if (isDragging) {
                    GLStateManager.glColor4f(0.863F, 0.863F, 0.863F, 0.431F); // Active drag
                } else if (hoveredHandle == ScreenHandle.DRAG_BAR) {
                    GLStateManager.glColor4f(0.863F, 0.863F, 0.863F, 0.470F); // Hovered
                } else {
                    GLStateManager.glColor4f(0.745F, 0.745F, 0.745F, 0.333F); // Normal
                }
                GLStateManager.glBegin(GL11.GL_QUADS);
                GLStateManager.glVertex3f(barLeft,  barBottom, 0.001F);
                GLStateManager.glVertex3f(barRight, barBottom, 0.001F);
                GLStateManager.glVertex3f(barRight, barTop,    0.001F);
                GLStateManager.glVertex3f(barLeft,  barTop,    0.001F);
                GLStateManager.glEnd();

                // Resize Handle (square to the right of the bar)
                if (isResizing) {
                    GLStateManager.glColor4f(0.470F, 0.863F, 1.000F, 0.627F); // Active resize
                } else if (hoveredHandle == ScreenHandle.RESIZE_HANDLE) {
                    GLStateManager.glColor4f(0.863F, 0.863F, 0.863F, 0.470F); // Hovered
                } else {
                    GLStateManager.glColor4f(0.745F, 0.745F, 0.745F, 0.333F); // Normal
                }
                GLStateManager.glBegin(GL11.GL_QUADS);
                GLStateManager.glVertex3f(resizeLeft,  resizeBottom, 0.001F);
                GLStateManager.glVertex3f(resizeRight, resizeBottom, 0.001F);
                GLStateManager.glVertex3f(resizeRight, resizeTop,    0.001F);
                GLStateManager.glVertex3f(resizeLeft,  resizeTop,    0.001F);
                GLStateManager.glEnd();
            }

            // 3. Glowing border outline during resizing (Visor 1:1)
            if (isResizing) {
                GLStateManager.glColor4f(0.470F, 0.863F, 1.000F, 0.627F);
                float thick = Math.max(0.005F, halfW * 0.012F);
                GLStateManager.glBegin(GL11.GL_QUADS);
                // Top edge
                GLStateManager.glVertex3f(-halfW, halfH - thick, 0.002F);
                GLStateManager.glVertex3f( halfW, halfH - thick, 0.002F);
                GLStateManager.glVertex3f( halfW, halfH,         0.002F);
                GLStateManager.glVertex3f(-halfW, halfH,         0.002F);
                // Bottom edge
                GLStateManager.glVertex3f(-halfW, -halfH,         0.002F);
                GLStateManager.glVertex3f( halfW, -halfH,         0.002F);
                GLStateManager.glVertex3f( halfW, -halfH + thick, 0.002F);
                GLStateManager.glVertex3f(-halfW, -halfH + thick, 0.002F);
                // Left edge
                GLStateManager.glVertex3f(-halfW,         -halfH + thick, 0.002F);
                GLStateManager.glVertex3f(-halfW + thick, -halfH + thick, 0.002F);
                GLStateManager.glVertex3f(-halfW + thick,  halfH - thick, 0.002F);
                GLStateManager.glVertex3f(-halfW,          halfH - thick, 0.002F);
                // Right edge
                GLStateManager.glVertex3f(halfW - thick, -halfH + thick, 0.002F);
                GLStateManager.glVertex3f(halfW,         -halfH + thick, 0.002F);
                GLStateManager.glVertex3f(halfW,          halfH - thick, 0.002F);
                GLStateManager.glVertex3f(halfW - thick,  halfH - thick, 0.002F);
                GLStateManager.glEnd();
            }

        } finally {
            GLStateManager.glPopMatrix();
            GLStateManager.glPopAttrib();
        }

        // A custom game-screen overlay uses the same live GUI texture but its own saved pose/scale.
        VRCustomOverlayRenderer.renderFromCurrentHead(VROverlayConfig.GAME_SCREEN, eyeIndex,
                fboTextureId, texW, texH, 1.75F, 1.0F, 1.0F, false);
    }

    public boolean raycastCheck(Vector3f rayOrigin, Vector3f rayDir, org.joml.Vector2f outUV, Vector3f outHitPoint) {
        if (!VROverlayConfig.get(VROverlayConfig.GAME_SCREEN).visible) {
            hoveredHandle = ScreenHandle.NONE;
            return false;
        }
        Vector3f planeNorm = screenRot.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        float denom = planeNorm.dot(rayDir);
        if (Math.abs(denom) < 1e-5F) {
            hoveredHandle = ScreenHandle.NONE;
            return false;
        }

        Vector3f diff = new Vector3f(screenPos).sub(rayOrigin);
        float t = diff.dot(planeNorm) / denom;
        if (t <= 0.0F || t > 10.0F) {
            hoveredHandle = ScreenHandle.NONE;
            return false;
        }

        Vector3f hitPoint = new Vector3f(rayOrigin).add(new Vector3f(rayDir).mul(t));
        Vector3f localHit = new Vector3f(hitPoint).sub(screenPos);
        new Quaternionf(screenRot).conjugate().transform(localHit);

        float halfW = screenWidth * 0.5F;
        float aspect = getScreenAspect();
        float halfH = screenWidth * aspect * 0.5F;

        // Visor handle bounds
        float barHalfW = halfW * 0.18F;
        float barHalfH = halfH * 0.025F;
        float barGap   = halfH * 0.04F;
        float barCenterY = -halfH - barGap - barHalfH;
        float barLeft  = -barHalfW;
        float barRight =  barHalfW;
        float barBottom = barCenterY - barHalfH;
        float barTop    = barCenterY + barHalfH;

        float handleGap = barHalfW * 0.20F;
        float handleSide = barHalfH * 1.3F;
        float resizeLeft = barRight + handleGap;
        float resizeRight = resizeLeft + handleSide * 2.0F;
        float resizeBottom = barCenterY - handleSide;
        float resizeTop = barCenterY + handleSide;

        if (outHitPoint != null) {
            outHitPoint.set(hitPoint);
        }

        Minecraft mc = Minecraft.getMinecraft();
        boolean allowHandles = (mc != null && mc.theWorld != null && !inMenuRoom && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory));

        if (allowHandles) {
            // 1. Drag bar hit
            if (localHit.x >= barLeft - 0.015F && localHit.x <= barRight + 0.015F &&
                localHit.y >= barBottom - 0.015F && localHit.y <= barTop + 0.015F) {
                hoveredHandle = ScreenHandle.DRAG_BAR;
                return true;
            }

            // 2. Resize handle hit
            if (localHit.x >= resizeLeft - 0.015F && localHit.x <= resizeRight + 0.015F &&
                localHit.y >= resizeBottom - 0.015F && localHit.y <= resizeTop + 0.015F) {
                hoveredHandle = ScreenHandle.RESIZE_HANDLE;
                return true;
            }
        }

        // 3. Screen quad hit
        if (localHit.x >= -halfW && localHit.x <= halfW && localHit.y >= -halfH && localHit.y <= halfH) {
            hoveredHandle = ScreenHandle.SCREEN;
            if (outUV != null) {
                outUV.x = (localHit.x + halfW) / screenWidth;
                outUV.y = (localHit.y + halfH) / (screenWidth * aspect);
            }
            return true;
        }

        hoveredHandle = ScreenHandle.NONE;
        return false;
    }

    public boolean raycastCheck(Vector3f rayOrigin, Vector3f rayDir, org.joml.Vector2f outUV) {
        Vector3f hitPoint = new Vector3f();
        boolean hit = raycastCheck(rayOrigin, rayDir, outUV, hitPoint);
        if (hit) {
            lastHitPoint.set(hitPoint);
        }
        return hit;
    }

    public boolean raycast(Vector3f rayOrigin, Vector3f rayDir) {
        org.joml.Vector2f uv = new org.joml.Vector2f();
        boolean hit = raycastCheck(rayOrigin, rayDir, uv);
        if (hit && hoveredHandle == ScreenHandle.SCREEN) {
            setCursorUV(uv.x, uv.y, true);
            return true;
        } else {
            cursorActive = false;
            return hit;
        }
    }

    public void setCursorUV(float u, float v, boolean active) {
        this.cursorU = u;
        this.cursorV = v;
        this.cursorActive = active;
    }

    public ScreenHandle getHoveredHandle() { return hoveredHandle; }
    public boolean isDragging() { return isDragging; }
    public boolean isResizing() { return isResizing; }

    public float getCursorU() { return cursorU; }
    public float getCursorV() { return cursorV; }
    public boolean isCursorActive() { return cursorActive; }
    public Vector3f getLastHitPoint() { return lastHitPoint; }
    public void setLastHitPoint(Vector3f p) { if (p != null) this.lastHitPoint.set(p); }

    public Vector3f getScreenPos() { return screenPos; }
    public Quaternionf getScreenRot() { return screenRot; }
    public float getScreenWidth() { return screenWidth; }
    public float getScreenHeight() { return screenHeight; }
}
