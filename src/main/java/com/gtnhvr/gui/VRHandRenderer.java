package com.gtnhvr.gui;

import com.gtnhvr.settings.VROverlayConfig;
import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import java.nio.FloatBuffer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockTorch;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * 3D VR Hand & Pointer Renderer:
 * - In-Game: Authentic player skin arms for BOTH Left and Right hands matching player skin texture
 *   (1:1 Visor VRHandRenderer / CenteredArmsPlayerMesh design).
 * - Menus / Settings: Sleek controller pointer tool with laser beam originating
 *   from its forward tip along -Z (1:1 Visor renderGuiHand design).
 * - Aim Crosshair: Visor 1:1 inverting 3D '+' crosshair rendered at block face or entity hit point.
 * - Solid skin rendering with glDisable(GL_BLEND) so black skin pixels NEVER become transparent.
 * - Pure OpenGL calls for 100% compatibility with Angelica & Celeritas.
 */
@Lwjgl3Aware
public final class VRHandRenderer {

    private static final ResourceLocation DEFAULT_STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ICONS_TEX = new ResourceLocation("textures/gui/icons.png");

    private VRHandRenderer() {}

    public static void render(int eyeIndex, Vector3f hmdPos, Quaternionf hmdRot) {
        VRControllerHandler handler = VRControllerHandler.getInstance();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;

        boolean inMenu = (mc.theWorld == null || mc.currentScreen != null);
        boolean isItemOffsetsScreen = false;
        if (mc.currentScreen instanceof VRSettingsScreen) {
            VRSettingsScreen ss = (VRSettingsScreen) mc.currentScreen;
            if (ss.getCurrentOptionsSet() instanceof com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets) {
                isItemOffsetsScreen = true;
            }
        }

        String body = (VRClientSettings.vrBodyModel != null) ? VRClientSettings.vrBodyModel : VRClientSettings.defaultVrBody;
        boolean hidden = (body != null && (body.equalsIgnoreCase("Hidden") || body.equalsIgnoreCase("hidden")));
        boolean isSlim = (body != null && (body.toLowerCase().contains("alex") || body.toLowerCase().contains("slim")));

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            if (inMenu && !isItemOffsetsScreen) {
                // ---- MENU STAGE: Render Sleek Pointer Tool from Visor ----
                if (handler.isRightHandTracked()) {
                    Vector3f rightPos = handler.getRightHandPos();
                    Quaternionf rightRot = handler.getRightHandRot();
                    renderPointerTool(rightPos, rightRot, true);
                }
                if (handler.isLeftHandTracked()) {
                    Vector3f leftPos = handler.getLeftHandPos();
                    Quaternionf leftRot = handler.getLeftHandRot();
                    renderPointerTool(leftPos, leftRot, false);
                }
            } else if (!hidden || isItemOffsetsScreen) {
                // ---- WORLD STAGE / ITEM OFFSETS SCREEN: Render Solid Player Skin Arms from Visor ----
                ResourceLocation skin = getPlayerSkin(mc);

                // 1. Left Arm (Player Skin + Offhand Item / Preview Item)
                if (handler.isLeftHandTracked()) {
                    Vector3f leftPos = handler.getLeftHandPos();
                    Quaternionf leftRot = handler.getLeftHandRot();
                    renderSkinArm(leftPos, leftRot, skin, false, mc, isSlim);
                }

                // 2. Right Arm (Player Skin + Main Hand Item / Preview Item)
                if (handler.isRightHandTracked()) {
                    Vector3f rightPos = handler.getRightHandPos();
                    Quaternionf rightRot = handler.getRightHandRot();
                    renderSkinArm(rightPos, rightRot, skin, true, mc, isSlim);
                }
            }

        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /**
     * Renders authentic Visor 3D '+' Crosshair (HandEffectCrosshair) at the targeted block or entity.
     * Uses vanilla textures/gui/icons.png with color-inverting blend mode, aligned with block face normal.
     * Renders in WORLD CAMERA SPACE so crosshair matches targeted block position 1:1 with zero offset!
     */
    /**
     * Renders authentic Visor 3D '+' Crosshair (HandEffectCrosshair) for BOTH Hands.
     * Uses vanilla textures/gui/icons.png with color-inverting blend mode, aligned with block face normal.
     * Active hand is rendered with full brightness (1.0F / 0.5F on miss).
     * Inactive hand is dimmed to 0.4F (1:1 Visor INACTIVE_BRIGHTNESS = 0.4F).
     * Renders in WORLD CAMERA SPACE so crosshair matches targeted block position 1:1 with zero offset!
     */
    public static void renderCrosshair(Minecraft mc, int eyeIndex, float partialTicks) {
        if (mc == null || mc.theWorld == null || mc.currentScreen != null) return;

        VRControllerHandler handler = VRControllerHandler.getInstance();
        if (!handler.isRightHandTracked() && !handler.isLeftHandTracked()) return;

        net.minecraft.entity.Entity renderEntity = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (renderEntity == null) return;
        double camX = renderEntity.prevPosX + (renderEntity.posX - renderEntity.prevPosX) * (double) partialTicks;
        double camY = renderEntity.prevPosY + (renderEntity.posY - renderEntity.prevPosY) * (double) partialTicks;
        double camZ = renderEntity.prevPosZ + (renderEntity.posZ - renderEntity.prevPosZ) * (double) partialTicks;

        // Switch to World Camera Space for crosshair and laser rendering
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        FloatBuffer proj = OpenXRRuntime.getEyeProjectionBuffer(eyeIndex, 0.05F, 100.0F);
        if (proj != null) GL11.glLoadMatrixf(proj);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        FloatBuffer mv = OpenXRRuntime.getEyeModelViewBuffer(eyeIndex, partialTicks);
        if (mv != null) GL11.glLoadMatrixf(mv);

        try {
            // Render Right Hand Crosshair & Laser
            String crossAnchor = VROverlayConfig.get(VROverlayConfig.CROSSHAIR).anchor;
            boolean showRight = !crossAnchor.equals("OFFHAND");
            boolean showLeft = !crossAnchor.equals("MAIN_HAND");
            if (showRight && handler.isRightHandTracked()) {
                renderSingleHandCrosshair(mc, handler, eyeIndex, false, partialTicks, camX, camY, camZ);
            }
            // Render Left Hand Crosshair & Laser
            if (showLeft && handler.isLeftHandTracked()) {
                renderSingleHandCrosshair(mc, handler, eyeIndex, true, partialTicks, camX, camY, camZ);
            }
        } finally {
            GL11.glPopMatrix(); // Pop world MODELVIEW
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix(); // Pop world PROJECTION
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void renderSingleHandCrosshair(Minecraft mc, VRControllerHandler handler, int eyeIndex, boolean isLeft, float partialTicks, double camX, double camY, double camZ) {
        Vector3f handPos = isLeft ? handler.getLeftHandWorldPos(partialTicks) : handler.getRightHandWorldPos(partialTicks);
        Vector3f handDir = isLeft ? handler.getLeftHandWorldDir() : handler.getRightHandWorldDir();

        MovingObjectPosition mop = com.gtnhvr.VRHandAimPicker.getHitResult(isLeft);
        net.minecraft.util.Vec3 aimHit = com.gtnhvr.VRHandAimPicker.getAimHitPos(isLeft);

        Vector3f crossPos = new Vector3f();
        int sideHit = -1;
        boolean isBlock = false;
        float brightness = 1.0F;

        if (mop != null && mop.typeOfHit != MovingObjectPosition.MovingObjectType.MISS && aimHit != null) {
            crossPos.set((float) aimHit.xCoord, (float) aimHit.yCoord, (float) aimHit.zCoord);
            if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                isBlock = true;
                sideHit = mop.sideHit;
            }
        } else {
            // In air / Miss: 4.0 meters along hand aim ray, half brightness (0.5F)
            crossPos.set(handPos).add(new Vector3f(handDir).mul(4.0F));
            brightness = 0.5F;
        }

        // 1:1 Visor INACTIVE_BRIGHTNESS (0.4F): Inactive hand crosshair is dimmed so user distinguishes active vs offhand
        boolean isActive = isLeft ? handler.isLeftHandActive() : !handler.isLeftHandActive();
        if (!isActive) {
            brightness *= 0.4F;
        }

        // Visor 1:1 LIGHT_OFFSET (-0.01m): Nudge crosshair 1cm towards hand along aim ray to eliminate z-fighting
        Vector3f aimRay = new Vector3f(crossPos).sub(handPos);
        if (aimRay.lengthSquared() > 1e-4F) {
            aimRay.normalize();
            crossPos.sub(new Vector3f(aimRay).mul(0.01F));
        }

        Vector3f relCross = new Vector3f((float) (crossPos.x - camX), (float) (crossPos.y - camY), (float) (crossPos.z - camZ));

        if (!VRClientSettings.vrCrosshair) {
            return;
        }

        // Seamless transition: when pointer aims at the wrist inventory, suppress the world block crosshair
        com.gtnhvr.gui.VRWristInventory wristInv = com.gtnhvr.gui.VRWristInventory.getInstance();
        boolean isPointerForWrist = (wristInv.isWristLeft() && !isLeft) || (!wristInv.isWristLeft() && isLeft);
        if (wristInv.isActive() && wristInv.isHoverValid() && isPointerForWrist) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        try {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(
                    GL11.GL_ONE_MINUS_DST_COLOR,
                    GL11.GL_ONE_MINUS_SRC_COLOR,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
            );
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDepthFunc(GL11.GL_ALWAYS);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            mc.getTextureManager().bindTexture(ICONS_TEX);
            GL11.glColor4f(brightness, brightness, brightness, 1.0F);

            GL11.glTranslatef(relCross.x, relCross.y, relCross.z);

            float handYaw = isLeft ? handler.getLeftHandWorldYaw() : handler.getRightHandWorldYaw();
            float handPitch = isLeft ? handler.getLeftHandWorldPitch() : handler.getRightHandWorldPitch();

            if (isBlock) {
                // 1:1 Visor HandEffectCrosshair block face orientation in world space
                switch (sideHit) {
                    case 0: // DOWN (bottom face)
                        GL11.glRotatef(handYaw, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
                        break;
                    case 1: // UP (top face)
                        GL11.glRotatef(-handYaw, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
                        break;
                    case 2: // NORTH
                        break;
                    case 3: // SOUTH
                        GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
                        break;
                    case 4: // WEST
                        GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
                        break;
                    case 5: // EAST
                        GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
                        break;
                }
            } else {
                // 1:1 Visor HandEffectCrosshair: Perpendicular to hand aim in world space
                GL11.glRotatef(-handYaw, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(-handPitch, 1.0F, 0.0F, 0.0F);
            }

            // Crosshair Quad size (0.125m base scale)
            float s = 0.125F * VROverlayConfig.get(VROverlayConfig.CROSSHAIR).scale;
            float uv = 16.0F / 256.0F; // 16x16 pixels crosshair at top-left of icons.png

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex3f(-s,  s, 0.0F);
            GL11.glTexCoord2f(uv,   0.0F); GL11.glVertex3f( s,  s, 0.0F);
            GL11.glTexCoord2f(uv,   uv);   GL11.glVertex3f( s, -s, 0.0F);
            GL11.glTexCoord2f(0.0F, uv);   GL11.glVertex3f(-s, -s, 0.0F);
            GL11.glEnd();

        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static void renderLaserBeam(Vector3f start, Vector3f end, float r, float g, float b) {
        renderLaserBeam(start, end, r, g, b, 1.0F);
    }

    public static void renderLaserBeam(Vector3f start, Vector3f end, float r, float g, float b, float alpha) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);

        GL11.glLineWidth(4.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, 0.25F * alpha);
        GL11.glVertex3f(start.x, start.y, start.z);
        GL11.glColor4f(r, g, b, 0.65F * alpha);
        GL11.glVertex3f(end.x, end.y, end.z);
        GL11.glEnd();

        GL11.glLineWidth(2.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.70F * alpha);
        GL11.glVertex3f(start.x, start.y, start.z);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.95F * alpha);
        GL11.glVertex3f(end.x, end.y, end.z);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    private static ResourceLocation getPlayerSkin(Minecraft mc) {
        if (mc != null && mc.thePlayer != null) {
            ResourceLocation loc = mc.thePlayer.getLocationSkin();
            if (loc != null) return loc;
        }
        return DEFAULT_STEVE;
    }

    /**
     * Renders the player's textured arm (skin) at the controller location,
     * scaled to realistic proportions (0.025F, matching Visor 1:1), with 64x64 skin
     * layout and 3D outer sleeve layers.
     */
    private static void renderSkinArm(Vector3f pos, Quaternionf rot, ResourceLocation skin, boolean isRight, Minecraft mc, boolean isSlim) {
        if (mc == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(pos.x, pos.y, pos.z);

        // Apply controller orientation
        applyQuaternionRotation(rot);

        // Arm model transform: forearm extends along +Z (backward along arm)
        GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);

        // Scale arm cuboid: 4x12x4 units scaled to VR dimensions (~0.025F per unit -> 0.30m arm length, 1:1 Visor)
        float s = 0.025F;
        GL11.glScalef(s, s, s);

        // Lateral shift for natural wrist angle (inward towards torso)
        float handFactor = isRight ? 1.0F : -1.0F;
        GL11.glTranslatef(-0.20F * handFactor, 0.0F, 0.0F);

        // SOLID ARMS WITHOUT CULLING: Backface culling is disabled so wrist inside and back of arm are never hollow
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        mc.getTextureManager().bindTexture(skin);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Check if hand holds Thaumometer - if so, suppress VR arm model for clear line of sight
        ItemStack preview = com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.getActivePreviewItem();
        ItemStack heldItem = (preview != null) ? preview : (isRight
                ? (mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null)
                : VRHotbarSelector.getInstance().getOffhandItemStack(mc));
        boolean skipArm = isThaumometer(heldItem);

        if (!skipArm) {
            int texH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            boolean is64x64 = (texH >= 64);

            // Draw Base 3D textured arm cuboid
            drawTexturedArmBox(isRight, isSlim, is64x64, false);

            // Draw Outer 3D Sleeve Layer (expanded by 0.25F with alpha transparency) if 64x64
            if (is64x64) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
                drawTexturedArmBox(isRight, isSlim, true, true);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_BLEND);
            }
        }

        GL11.glPopMatrix(); // Pop arm matrix

        // Render Held Item / Block in Hand in Controller Root Space
        renderHeldItem(pos, rot, isRight, mc);
    }

    public static boolean isThaumometer(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Item item = stack.getItem();
        String name = item.getClass().getName().toLowerCase();
        String unloc = (item.getUnlocalizedName() != null) ? item.getUnlocalizedName().toLowerCase() : "";
        return name.contains("thaumometer") || unloc.contains("thaumometer");
    }

    public static boolean isTinkersTool(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        Item item = stack.getItem();
        Class<?> cls = item.getClass();
        while (cls != null && cls != Object.class) {
            String cname = cls.getName().toLowerCase();
            if (cname.contains("toolcore")
                    || cname.startsWith("tconstruct.items.tools.")
                    || cname.startsWith("tconstruct.library.tools.")
                    || cname.startsWith("tconstruct.weaponry.")
                    || cname.startsWith("tconstruct.library.weaponry.")) {
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    public enum VRItemCategory {
        BLOCK_3D,
        SWORD,
        TOOL,
        TINKER,
        BOW,
        TORCH,
        CONSUMABLE,
        GENERIC
    }

    public static VRItemCategory getItemCategory(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return VRItemCategory.GENERIC;
        Item item = stack.getItem();
        String name = item.getClass().getName().toLowerCase();
        String unloc = (item.getUnlocalizedName() != null) ? item.getUnlocalizedName().toLowerCase() : "";

        // Torch (upright flame) - check before 3D block
        if ((item instanceof ItemBlock && Block.getBlockFromItem(item) instanceof BlockTorch)
                || unloc.contains("torch") || name.contains("torch")) {
            return VRItemCategory.TORCH;
        }

        // Bow - check before tool
        if (item instanceof ItemBow || name.contains("bow") || unloc.contains("bow")) {
            return VRItemCategory.BOW;
        }

        // Tinkers' Construct Tools & Weapons (ToolCore, broadswords, cleavers, hammers, pickaxes, etc.)
        if (isTinkersTool(stack)) {
            return VRItemCategory.TINKER;
        }

        // Sword (weapon: vanilla, modded swords)
        if (item instanceof ItemSword
                || name.contains("sword") || name.contains("weapon") || name.contains("rapier")
                || name.contains("cleaver") || name.contains("dagger") || name.contains("cutlass")
                || unloc.contains("sword") || unloc.contains("rapier") || unloc.contains("cleaver")) {
            return VRItemCategory.SWORD;
        }

        // Tools (pickaxes, axes, shovels, hoes, shears, full3d tools, hammers, lumber axes)
        if (item instanceof ItemTool || item instanceof ItemHoe || item instanceof ItemShears
                || item.isFull3D()
                || name.contains("tool") || name.contains("harvest") || name.contains("hammer")
                || name.contains("axe") || name.contains("pick") || name.contains("shovel")
                || name.contains("mattock") || name.contains("scythe") || name.contains("drill")
                || name.contains("chainsaw") || name.contains("wrench") || name.contains("crowbar")
                || unloc.contains("tool") || unloc.contains("pick") || unloc.contains("axe")
                || unloc.contains("shovel") || unloc.contains("hammer")) {
            return VRItemCategory.TOOL;
        }

        // 3D Block (solid cube or IItemRenderer equipped block)
        net.minecraftforge.client.IItemRenderer custom =
                net.minecraftforge.client.MinecraftForgeClient.getItemRenderer(stack, net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED);
        if (custom != null && custom.shouldUseRenderHelper(net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED, stack, net.minecraftforge.client.IItemRenderer.ItemRendererHelper.EQUIPPED_BLOCK)) {
            return VRItemCategory.BLOCK_3D;
        }
        if (stack.getItemSpriteNumber() == 0 && item instanceof ItemBlock) {
            Block block = Block.getBlockFromItem(item);
            if (block != null && RenderBlocks.renderItemIn3d(block.getRenderType())) {
                return VRItemCategory.BLOCK_3D;
            }
        }

        // Consumables (food, potions)
        if (item instanceof ItemFood || item instanceof ItemPotion
                || stack.getItemUseAction() == EnumAction.eat
                || stack.getItemUseAction() == EnumAction.drink) {
            return VRItemCategory.CONSUMABLE;
        }

        return VRItemCategory.GENERIC;
    }

    /**
     * Renders held items and blocks in controller root space with Visor 1:1 transforms and scale:
     * - 3D Blocks: ~0.16m mini-block resting neatly in palm, tilted towards eyes
     * - Swords: handle gripped in fist, blade extending forward along -Z aim ray (~0.42m)
     * - Tools (pickaxes, axes, shovels, hoes): handle anchored in fist, head forward (~0.39m)
     * - Bows: held vertically upright in fist (~0.38m)
     * - Torches: held upright in palm, flame on top (~0.24m)
     * - Generic items / consumables (food, potions, ingots, redstone, sticks): resting flat in palm (~0.20m)
     * - Full support for Tinkers' Construct (ToolCore) and modded Forge IItemRenderer items
     * - Multi-pass color tinting for GTNH / TConstruct tools and potions
     */
    private static void renderHeldItem(Vector3f pos, Quaternionf rot, boolean isRight, Minecraft mc) {
        if (mc == null) return;
        ItemStack preview = com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.getActivePreviewItem();
        ItemStack heldItem;
        if (preview != null) {
            heldItem = preview;
        } else {
            heldItem = isRight
                    ? (mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null)
                    : VRHotbarSelector.getInstance().getOffhandItemStack(mc);
        }

        if (heldItem == null || heldItem.getItem() == null) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(pos.x, pos.y, pos.z);
        applyQuaternionRotation(rot);

        float handX = isRight ? 0.015F : -0.015F;
        float handCant = isRight ? -5.0F : 5.0F; // natural inward wrist tilt

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableStandardItemLighting();

        int brightness = (mc.thePlayer != null) ? mc.thePlayer.getBrightnessForRender(1.0F) : 0xF000F0;
        int lightX = brightness % 65536;
        int lightY = brightness / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lightX, (float) lightY);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        VRItemCategory category = getItemCategory(heldItem);
        com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory offsetCat =
                switch (category) {
                    case BLOCK_3D -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.BLOCKS;
                    case SWORD -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.SWORDS;
                    case TOOL -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.TOOLS;
                    case TINKER -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.TINKER;
                    case TORCH -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.TORCHES;
                    case BOW -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.BOWS;
                    case CONSUMABLE, GENERIC -> com.gtnhvr.gui.settings.categories.controls.VRSettingsItemOffsets.OffsetCategory.ITEMS;
                };
        com.gtnhvr.settings.ItemHandOffset offset = VRClientSettings.getHandOffset(offsetCat, isRight);

        boolean isThaumo = isThaumometer(heldItem);
        try {
            if (isThaumo) {
                // Dedicated Thaumcraft Thaumometer handheld scanner transform:
                // Compact scale, centered in hand, tilted towards eyes for comfortable scanning
                GL11.glTranslatef(handX + offset.posX, 0.020F + offset.posY, -0.060F + offset.posZ);
                GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                float thaumoScale = 0.08F * (offset.scale > 0.01F ? (offset.scale / 0.20F) : 1.0F);
                GL11.glScalef(thaumoScale, thaumoScale, thaumoScale);
                GL11.glTranslatef(-0.5F, -0.5F, -0.5F); // center scanner.obj (Thaumcraft translates 0.5, 0.5, 0.5)
                GL11.glRotatef(25.0F, 1.0F, 0.0F, 0.0F); // tilt screen up towards face
            } else {
                switch (category) {
                    case BLOCK_3D: {
                        GL11.glTranslatef(handX + offset.posX, 0.025F + offset.posY, -0.090F + offset.posZ);
                        GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                        if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                        if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                        if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                        GL11.glScalef(offset.scale, offset.scale, offset.scale);
                        GL11.glTranslatef(-0.5F, -0.5F, -0.5F); // center 1x1x1 unit cube
                        break;
                    }
                    case SWORD: {
                        GL11.glTranslatef(handX + offset.posX, 0.015F + offset.posY, -0.015F + offset.posZ);
                        GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                        if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                        if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                        if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                        GL11.glScalef(offset.scale, offset.scale, offset.scale);
                        GL11.glTranslatef(0.177F, -0.621F, -1.164F);
                        GL11.glRotatef(37.2F, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(-25.4F, 1.0F, 0.0F, 0.0F);
                        break;
                    }
                    case TOOL:
                    case TINKER: {
                        GL11.glTranslatef(handX + offset.posX, 0.015F + offset.posY, -0.015F + offset.posZ);
                        GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                        if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                        if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                        if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                        GL11.glScalef(offset.scale, offset.scale, offset.scale);
                        GL11.glTranslatef(0.250F, -0.754F, -1.068F);
                        GL11.glRotatef(37.2F, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(-35.4F, 1.0F, 0.0F, 0.0F);
                        break;
                    }
                    case BOW: {
                        GL11.glTranslatef(handX + offset.posX, 0.015F + offset.posY, -0.055F + offset.posZ);
                        GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                        if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                        if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                        if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                        GL11.glScalef(offset.scale, offset.scale, offset.scale);
                        GL11.glTranslatef(-0.103F, -0.646F, 0.0F);
                        GL11.glRotatef(-160.0F, 0.0F, 0.0F, 1.0F);
                        GL11.glRotatef(50.0F, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(-180.0F, 1.0F, 0.0F, 0.0F);
                        break;
                    }
                    case TORCH: {
                        GL11.glTranslatef(handX + offset.posX, 0.020F + offset.posY, -0.055F + offset.posZ);
                        GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                        if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                        if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                        if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                        GL11.glScalef(offset.scale, offset.scale, offset.scale);
                        GL11.glTranslatef(-0.646F, 1.163F, 0.0F);
                        GL11.glRotatef(110.0F, 0.0F, 0.0F, 1.0F);
                        GL11.glRotatef(50.0F, 0.0F, 1.0F, 0.0F);
                        GL11.glRotatef(-180.0F, 1.0F, 0.0F, 0.0F);
                        break;
                    }
                    case CONSUMABLE:
                    case GENERIC:
                    default: {
                        GL11.glTranslatef(handX + offset.posX, 0.025F + offset.posY, -0.055F + offset.posZ);
                        GL11.glRotatef(handCant, 0.0F, 0.0F, 1.0F);
                        if (offset.yaw != 0.0F) GL11.glRotatef(offset.yaw, 0.0F, 1.0F, 0.0F);
                        if (offset.pitch != 0.0F) GL11.glRotatef(offset.pitch, 1.0F, 0.0F, 0.0F);
                        if (offset.roll != 0.0F) GL11.glRotatef(offset.roll, 0.0F, 0.0F, 1.0F);
                        GL11.glScalef(offset.scale, offset.scale, offset.scale);
                        GL11.glTranslatef(0.485F, -0.141F, 0.416F);
                        GL11.glRotatef(50.0F, 0.0F, 0.0F, 1.0F);
                        GL11.glRotatef(-65.0F, 1.0F, 0.0F, 0.0F);
                        break;
                    }
                }
            }

            // 1. Forge IItemRenderer (TConstruct tools, modded 3D items, GregTech tools)
            net.minecraftforge.client.IItemRenderer customRenderer =
                    net.minecraftforge.client.MinecraftForgeClient.getItemRenderer(heldItem, net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON);
            if (customRenderer == null) {
                customRenderer = net.minecraftforge.client.MinecraftForgeClient.getItemRenderer(heldItem, net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED);
            }

            if (customRenderer != null) {
                mc.getTextureManager().bindTexture(mc.getTextureManager().getResourceLocation(heldItem.getItemSpriteNumber()));
                RenderBlocks rb = RenderBlocks.getInstance();
                if (rb == null && mc.theWorld != null) {
                    rb = new RenderBlocks(mc.theWorld);
                }
                // For Thaumometer, force EQUIPPED so Thaumcraft does NOT draw vanilla first-person arm
                net.minecraftforge.client.IItemRenderer.ItemRenderType renderType =
                        isThaumo ? net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED
                                 : (customRenderer.handleRenderType(heldItem, net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON)
                                        ? net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED_FIRST_PERSON
                                        : net.minecraftforge.client.IItemRenderer.ItemRenderType.EQUIPPED);

                if (customRenderer.shouldUseRenderHelper(renderType, heldItem, net.minecraftforge.client.IItemRenderer.ItemRendererHelper.EQUIPPED_BLOCK)) {
                    GL11.glPushMatrix();
                    GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                    customRenderer.renderItem(renderType, heldItem, rb, mc.thePlayer);
                    GL11.glPopMatrix();
                } else {
                    customRenderer.renderItem(renderType, heldItem, rb, mc.thePlayer);
                }
            } else {
                // 2. Multi-pass rendering with color tinting (Vanilla items, GTNH tools, potions, colored items)
                if (RenderManager.instance != null && RenderManager.instance.itemRenderer != null) {
                    int passes = heldItem.getItem().getRenderPasses(heldItem.getItemDamage());
                    for (int pass = 0; pass < passes; ++pass) {
                        int color = heldItem.getItem().getColorFromItemStack(heldItem, pass);
                        float cr = (float) (color >> 16 & 255) / 255.0F;
                        float cg = (float) (color >> 8 & 255) / 255.0F;
                        float cb = (float) (color & 255) / 255.0F;
                        GL11.glColor4f(cr, cg, cb, 1.0F);
                        RenderManager.instance.itemRenderer.renderItem(mc.thePlayer, heldItem, pass);
                    }
                }
            }
        } catch (Throwable ignored) {}

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glPopMatrix();
    }

    /**
     * Draws a 4x12x4 (or 3x12x4 for slim Alex) arm cuboid with standard Minecraft skin texture coordinates:
     * - Right Arm: (40, 16), Right Sleeve: (40, 32)
     * - Left Arm: (32, 48), Left Sleeve: (48, 48) for 64x64 skins
     * - Left Arm mirrored (40, 16) for legacy 64x32 Steve skins
     * - Hand cap is mapped to the bottom face UVs, elbow cap to top face UVs.
     * - Supports sleeve expansion (+0.25F or +0.20F slim).
     */
    private static void drawTexturedArmBox(boolean isRight, boolean isSlim, boolean is64x64, boolean isSleeve) {
        float tw = 64.0F;
        float th = is64x64 ? 64.0F : 32.0F;

        float baseU;
        float baseV;

        if (isRight) {
            baseU = 40.0F;
            baseV = isSleeve ? 32.0F : 16.0F;
        } else {
            if (is64x64) {
                baseU = isSleeve ? 48.0F : 32.0F;
                baseV = 48.0F;
            } else {
                if (isSleeve) return; // Legacy 64x32 skins do not have a sleeve layer
                baseU = 40.0F;
                baseV = 16.0F;
            }
        }

        float w = isSlim ? 3.0F : 4.0F;
        float d = 4.0F;
        float h = 12.0F;

        float u0 = baseU / tw;
        float u1 = (baseU + d) / tw;
        float u2 = (baseU + d + w) / tw;
        float u3 = (baseU + d + w + d) / tw;
        float u4 = (baseU + d + w + d + w) / tw;

        float v0 = baseV / th;
        float v1 = (baseV + d) / th;
        float v2 = (baseV + d + h) / th;

        // Legacy 64x32 Steve skin: mirror U for the left arm
        if (!isRight && !is64x64) {
            float temp = u0; u0 = u4; u4 = temp;
            temp = u1; u1 = u3; u3 = temp;
        }

        // Determine UV ranges for Left Face (X = x0) and Right Face (X = x1):
        // In player space looking at hand:
        // Right Arm: Inner face (with thumb) is towards body (-X = x0), Outer face is away (+X = x1).
        // Left Arm (64x64): Outer face is away (-X = x0), Inner face (with thumb) is towards body (+X = x1).
        float uLeft0, uLeft1;
        float uRight0, uRight1;
        if (isRight || is64x64) {
            uLeft0 = u2;  uLeft1 = u3; // Inner face (thumb) for right arm; Outer face for left arm 64x64
            uRight0 = u0; uRight1 = u1; // Outer face for right arm; Inner face (thumb) for left arm 64x64
        } else {
            // Legacy 64x32 Steve mirrored left arm
            uLeft0 = u0;  uLeft1 = u1;
            uRight0 = u2; uRight1 = u3;
        }

        float x0 = isSlim ? -1.5F : -2.0F;
        float x1 = isSlim ?  1.5F :  2.0F;
        float y0 = -10.0F; // forearm towards elbow
        float y1 =   2.0F; // wrist / hand
        float z0 = -2.0F;
        float z1 =  2.0F;

        if (isSleeve) {
            float expand = isSlim ? 0.20F : 0.25F;
            x0 -= expand; x1 += expand;
            y0 -= expand; y1 += expand;
            z0 -= expand; z1 += expand;
        }

        GL11.glBegin(GL11.GL_QUADS);

        // Front Face: u1..u2, v1..v2 (y0 = elbow / v1, y1 = wrist / v2)
        GL11.glNormal3f(0.0F, 0.0F, 1.0F);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex3f(x0, y0, z1);
        GL11.glTexCoord2f(u2, v1); GL11.glVertex3f(x1, y0, z1);
        GL11.glTexCoord2f(u2, v2); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(u1, v2); GL11.glVertex3f(x0, y1, z1);

        // Back Face: u3..u4, v1..v2 (y0 = elbow / v1, y1 = wrist / v2)
        GL11.glNormal3f(0.0F, 0.0F, -1.0F);
        GL11.glTexCoord2f(u3, v1); GL11.glVertex3f(x1, y0, z0);
        GL11.glTexCoord2f(u4, v1); GL11.glVertex3f(x0, y0, z0);
        GL11.glTexCoord2f(u4, v2); GL11.glVertex3f(x0, y1, z0);
        GL11.glTexCoord2f(u3, v2); GL11.glVertex3f(x1, y1, z0);

        // Hand End Cap (at Y = y1, pointing towards fingers): Bottom Face UVs u2..u3, v0..v1
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glTexCoord2f(u2, v0); GL11.glVertex3f(x0, y1, z1);
        GL11.glTexCoord2f(u3, v0); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(u3, v1); GL11.glVertex3f(x1, y1, z0);
        GL11.glTexCoord2f(u2, v1); GL11.glVertex3f(x0, y1, z0);

        // Elbow End Cap (at Y = y0, pointing backward to elbow): Top Face UVs u1..u2, v0..v1
        GL11.glNormal3f(0.0F, -1.0F, 0.0F);
        GL11.glTexCoord2f(u1, v0); GL11.glVertex3f(x0, y0, z0);
        GL11.glTexCoord2f(u2, v0); GL11.glVertex3f(x1, y0, z0);
        GL11.glTexCoord2f(u2, v1); GL11.glVertex3f(x1, y0, z1);
        GL11.glTexCoord2f(u1, v1); GL11.glVertex3f(x0, y0, z1);

        // Left Side Face (X = x0, normal -1, 0, 0):
        GL11.glNormal3f(-1.0F, 0.0F, 0.0F);
        GL11.glTexCoord2f(uLeft0, v1); GL11.glVertex3f(x0, y0, z0);
        GL11.glTexCoord2f(uLeft1, v1); GL11.glVertex3f(x0, y0, z1);
        GL11.glTexCoord2f(uLeft1, v2); GL11.glVertex3f(x0, y1, z1);
        GL11.glTexCoord2f(uLeft0, v2); GL11.glVertex3f(x0, y1, z0);

        // Right Side Face (X = x1, normal +1, 0, 0):
        GL11.glNormal3f(1.0F, 0.0F, 0.0F);
        GL11.glTexCoord2f(uRight0, v1); GL11.glVertex3f(x1, y0, z1);
        GL11.glTexCoord2f(uRight1, v1); GL11.glVertex3f(x1, y0, z0);
        GL11.glTexCoord2f(uRight1, v2); GL11.glVertex3f(x1, y1, z0);
        GL11.glTexCoord2f(uRight0, v2); GL11.glVertex3f(x1, y1, z1);

        GL11.glEnd();
    }

    /**
     * Renders the sleek menu pointer tool matching Visor's renderGuiHand:
     * Dark titanium cuboid extending FORWARD along -Z from handle (+0.02m) to tip (-0.160m), width 4cm, height 2.5cm,
     * from whose forward tip the laser beam shoots directly towards the virtual screen.
     */
    private static void renderPointerTool(Vector3f pos, Quaternionf rot, boolean isRight) {
        GL11.glPushMatrix();
        GL11.glTranslatef(pos.x, pos.y, pos.z);

        // Rotate into controller aim orientation
        applyQuaternionRotation(rot);

        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Pointer cuboid extends FORWARD along -Z:
        // Tip is at z0 = -0.160F (forward towards screen), Handle is at z1 = +0.020F (at controller grip)
        float x0 = -0.020F, x1 = 0.020F;
        float y0 = -0.0125F, y1 = 0.0125F;
        float z0 = -0.160F, z1 = 0.020F;

        // 1. Dark charcoal/titanium pointer chassis
        GL11.glColor4f(0.12F, 0.14F, 0.18F, 1.0F);
        drawBox(x0, y0, z0, x1, y1, z1);

        // 2. Sleek beveled edge wireframe
        GL11.glLineWidth(1.5F);
        GL11.glColor4f(0.25F, 0.28F, 0.35F, 0.85F);
        drawBoxWireframe(x0, y0, z0, x1, y1, z1);

        // 3. Glowing neon accent stripe (Cyan for right pointer, Amber for left pointer)
        if (isRight) {
            GL11.glColor4f(0.0F, 0.85F, 1.0F, 0.95F); // Electric Cyan
        } else {
            GL11.glColor4f(1.0F, 0.65F, 0.10F, 0.95F); // Warm Amber
        }
        float stripeH = 0.003F;
        drawBox(x0 - 0.0005F, -stripeH, z0 + 0.015F, x1 + 0.0005F, stripeH, z1 - 0.015F);

        // 4. Luminous emitter ring around forward tip (Z = -0.160F)
        GL11.glLineWidth(2.5F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(x0 * 0.7F, y0 * 0.7F, z0 - 0.001F);
        GL11.glVertex3f(x1 * 0.7F, y0 * 0.7F, z0 - 0.001F);
        GL11.glVertex3f(x1 * 0.7F, y1 * 0.7F, z0 - 0.001F);
        GL11.glVertex3f(x0 * 0.7F, y1 * 0.7F, z0 - 0.001F);
        GL11.glEnd();

        GL11.glPopMatrix();
    }

    private static void applyQuaternionRotation(Quaternionf rot) {
        float qw = rot.w;
        float qx = rot.x;
        float qy = rot.y;
        float qz = rot.z;
        if (qw < 0.0F) {
            qw = -qw; qx = -qx; qy = -qy; qz = -qz;
        }
        float sinHalf = (float) Math.sqrt(Math.max(0.0F, 1.0F - qw * qw));
        if (sinHalf > 1e-4F) {
            float angleDeg = (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0F, qw)));
            GL11.glRotatef(angleDeg, qx / sinHalf, qy / sinHalf, qz / sinHalf);
        }
    }

    private static void drawBox(float x0, float y0, float z0, float x1, float y1, float z1) {
        GL11.glBegin(GL11.GL_QUADS);
        // Front (Z = z0, tip)
        GL11.glNormal3f(0.0F, 0.0F, -1.0F);
        GL11.glVertex3f(x0, y0, z0); GL11.glVertex3f(x1, y0, z0);
        GL11.glVertex3f(x1, y1, z0); GL11.glVertex3f(x0, y1, z0);
        // Back (Z = z1, handle)
        GL11.glNormal3f(0.0F, 0.0F, 1.0F);
        GL11.glVertex3f(x1, y0, z1); GL11.glVertex3f(x0, y0, z1);
        GL11.glVertex3f(x0, y1, z1); GL11.glVertex3f(x1, y1, z1);
        // Top (Y = y1)
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glVertex3f(x0, y1, z0); GL11.glVertex3f(x1, y1, z0);
        GL11.glVertex3f(x1, y1, z1); GL11.glVertex3f(x0, y1, z1);
        // Bottom (Y = y0)
        GL11.glNormal3f(0.0F, -1.0F, 0.0F);
        GL11.glVertex3f(x0, y0, z1); GL11.glVertex3f(x1, y0, z1);
        GL11.glVertex3f(x1, y0, z0); GL11.glVertex3f(x0, y0, z0);
        // Left (X = x0)
        GL11.glNormal3f(-1.0F, 0.0F, 0.0F);
        GL11.glVertex3f(x0, y0, z1); GL11.glVertex3f(x0, y0, z0);
        GL11.glVertex3f(x0, y1, z0); GL11.glVertex3f(x0, y1, z1);
        // Right (X = x1)
        GL11.glNormal3f(1.0F, 0.0F, 0.0F);
        GL11.glVertex3f(x1, y0, z0); GL11.glVertex3f(x1, y0, z1);
        GL11.glVertex3f(x1, y1, z1); GL11.glVertex3f(x1, y1, z0);
        GL11.glEnd();
    }

    private static void drawBoxWireframe(float x0, float y0, float z0, float x1, float y1, float z1) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3f(x0, y0, z0); GL11.glVertex3f(x1, y0, z0);
        GL11.glVertex3f(x1, y1, z0); GL11.glVertex3f(x0, y1, z0);
        GL11.glVertex3f(x0, y0, z0);
        GL11.glVertex3f(x0, y0, z1); GL11.glVertex3f(x1, y0, z1);
        GL11.glVertex3f(x1, y1, z1); GL11.glVertex3f(x0, y1, z1);
        GL11.glVertex3f(x0, y0, z1);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(x1, y0, z0); GL11.glVertex3f(x1, y0, z1);
        GL11.glVertex3f(x1, y1, z0); GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x0, y1, z0); GL11.glVertex3f(x0, y1, z1);
        GL11.glEnd();
    }
}
