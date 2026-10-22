package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.VRControllerHandler;
import com.gtnhvr.settings.VRClientSettings;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import org.joml.Vector3f;

/**
 * Physical two-handed bow. With a bow in the main hand, the off hand grabs the string by pulling
 * the off hand trigger close to the bow hand. The distance between the hands sets the draw
 * strength, the line from the off hand to the bow hand sets the shot direction and releasing
 * the trigger shoots. Draw strength is applied to the arrow through Forge's ArrowLooseEvent
 * (integrated server), the direction through the player look direction at release time.
 */
@Lwjgl3Aware
public class VRTaskBow {
    private static final VRTaskBow INSTANCE = new VRTaskBow();

    /** Maximum hand distance (metres) at which the string can be grabbed. */
    private static final float GRAB_DISTANCE = 0.28F;
    /** Hand distance of an undrawn string and of a fully drawn bow. */
    private static final float MIN_PULL = 0.10F;
    private static final float FULL_PULL = 0.55F;
    private static final float TRIGGER_GRAB = 0.60F;
    private static final float TRIGGER_RELEASE = 0.30F;
    /** Vanilla charge in ticks that equals a fully drawn bow. */
    private static final float FULL_CHARGE_TICKS = 20.0F;
    /** Time after the release during which the shot still uses the VR draw data. */
    private static final long RELEASE_WINDOW_MS = 1500L;
    private static final long AIM_HOLD_MS = 250L;

    private volatile boolean drawing = false;
    /** true when the bow is held by the main/right controller, false for the offhand/left controller. */
    private volatile boolean bowInMainHand = true;
    private volatile float tension = 0.0F;
    private volatile long releasedAtMs = 0L;
    private final Vector3f aimRoomDir = new Vector3f(0.0F, 0.0F, -1.0F);
    private long lastHapticMs = 0L;

    private final java.util.List<Vector3f> trajectoryPoints = new java.util.ArrayList<>();
    private Vector3f hitPointRoom = null;

    private VRTaskBow() {}

    public static VRTaskBow getInstance() {
        return INSTANCE;
    }

    /** True when a physical two-handed bow can be controlled by the tracked controllers. */
    public boolean isBowHeld(Minecraft mc) {
        if (!VRClientSettings.worldTwoHanded || mc == null || mc.thePlayer == null || mc.currentScreen != null) {
            return false;
        }
        VRControllerHandler handler = VRControllerHandler.getInstance();
        if (!handler.isRightHandTracked() || !handler.isLeftHandTracked()) {
            return false;
        }

        ItemStack main = mc.thePlayer.getHeldItem();
        if (main != null && main.getItem() instanceof ItemBow) {
            bowInMainHand = true;
            return true;
        }

        ItemStack offhand = com.gtnhvr.gui.VRHotbarSelector.getInstance().getOffhandItemStack(mc);
        if (offhand != null && offhand.getItem() instanceof ItemBow) {
            bowInMainHand = false;
            return true;
        }
        return false;
    }

    public boolean isBowInMainHand() {
        return bowInMainHand;
    }

    public boolean isDrawing() {
        return drawing;
    }

    public float getTension() {
        return tension;
    }

    /** True while the hands decide where the player aims (drawing and a moment after the release). */
    public boolean hasAim() {
        return drawing || (System.currentTimeMillis() - releasedAtMs) < AIM_HOLD_MS;
    }

    /** Shot direction in Minecraft world space. */
    public Vector3f getAimDirectionWorld(VRControllerHandler handler) {
        return handler.roomDirectionToWorld(new Vector3f(aimRoomDir));
    }

    public void update(Minecraft mc, VRControllerHandler handler) {
        if (!isBowHeld(mc)) {
            if (drawing) finishDraw();
            return;
        }

        // Match Visor's bow task: either controller may hold the bow, and the opposite
        // hand controls the string trigger and sets the aim direction.
        Vector3f bowPos = bowInMainHand ? handler.getRightHandPos() : handler.getLeftHandPos();
        Vector3f stringPos = bowInMainHand ? handler.getLeftHandPos() : handler.getRightHandPos();
        float distance = bowPos.distance(stringPos);
        float stringTrigger = bowInMainHand ? handler.getLeftTriggerValue() : handler.getRightTriggerValue();

        if (!drawing) {
            if (stringTrigger > TRIGGER_GRAB && distance < GRAB_DISTANCE) {
                drawing = true;
                tension = 0.0F;
                handler.triggerHaptic(bowInMainHand, 10_000_000L, 0.60F, 0);
            }
            return;
        }

        // Match Visor's BowAimCrosshairRenderer: the bow controller's aim pose defines
        // the arrow direction. Hand spacing controls draw strength, not the shot's bearing.
        org.joml.Quaternionf bowAimRotation = bowInMainHand
                ? handler.getRightHandRot() : handler.getLeftHandRot();
        Vector3f aim = bowAimRotation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        if (aim.lengthSquared() > 1.0E-6F) {
            aimRoomDir.set(aim).normalize();
        }
        tension = Math.max(0.0F, Math.min(1.0F, (distance - MIN_PULL) / (FULL_PULL - MIN_PULL)));

        // Haptic feedback in the hand that holds the bow, matching Visor's two-hand pose.
        long now = System.currentTimeMillis();
        if (tension > 0.05F && now - lastHapticMs > 60L) {
            lastHapticMs = now;
            handler.triggerHaptic(!bowInMainHand, 20_000_000L, 0.15F + 0.45F * tension, 0);
        }

        // Calculate arrow trajectory arc in Minecraft world (1:1 with Visor bow aiming).
        trajectoryPoints.clear();
        hitPointRoom = null;
        if (tension > 0.05F && mc.theWorld != null) {
            float charge = tension * FULL_CHARGE_TICKS;
            float f = (charge * charge + charge * 2.0F) / 3.0F / 20.0F;
            if (f > 1.0F) f = 1.0F;
            float speed = f * 3.0F;

            Vector3f startRoom = new Vector3f(bowPos);
            trajectoryPoints.add(new Vector3f(startRoom));

            net.minecraft.util.Vec3 curWorld = handler.roomToWorld(startRoom, 1.0F);
            // Vanilla 1.7.10 creates the arrow from the player's look vector, not the
            // controller's aim pose. Predict the same direction the actual projectile uses.
            net.minecraft.util.Vec3 playerLook = mc.thePlayer.getLook(1.0F);
            Vector3f worldAim = new Vector3f((float) playerLook.xCoord,
                    (float) playerLook.yCoord, (float) playerLook.zCoord).normalize();

            double vx = worldAim.x * speed;
            double vy = worldAim.y * speed;
            double vz = worldAim.z * speed;

            float baseYawRad = (float) Math.toRadians(com.gtnhvr.HeadPoseController.getBaseYaw());
            float cos = (float) Math.cos(-baseYawRad);
            float sin = (float) Math.sin(-baseYawRad);
            double px = mc.thePlayer.posX;
            double py = mc.thePlayer.posY;
            double pz = mc.thePlayer.posZ;

            for (int step = 0; step < 30; step++) {
                net.minecraft.util.Vec3 nextWorld = net.minecraft.util.Vec3.createVectorHelper(
                        curWorld.xCoord + vx,
                        curWorld.yCoord + vy,
                        curWorld.zCoord + vz
                );
                net.minecraft.util.MovingObjectPosition hit = mc.theWorld.func_147447_a(curWorld, nextWorld, false, true, false);
                net.minecraft.util.Vec3 pt = (hit != null) ? hit.hitVec : nextWorld;
                double dx = pt.xCoord - px;
                double dy = pt.yCoord - py;
                double dz = pt.zCoord - pz;
                float rx = (float) (dx * cos - dz * sin);
                float ry = (float) dy;
                float rz = (float) (dx * sin + dz * cos);
                Vector3f ptRoom = new Vector3f(rx, ry, rz);
                trajectoryPoints.add(ptRoom);

                if (hit != null) {
                    hitPointRoom = ptRoom;
                    break;
                }

                curWorld = nextWorld;
                vy -= 0.05D;
                vx *= 0.99D;
                vy *= 0.99D;
                vz *= 0.99D;
            }
        }

        if (stringTrigger < TRIGGER_RELEASE) {
            finishDraw();
            handler.triggerHaptic(bowInMainHand, 15_000_000L, 0.70F, 0);
        }
    }

    private void finishDraw() {
        drawing = false;
        releasedAtMs = System.currentTimeMillis();
        trajectoryPoints.clear();
        hitPointRoom = null;
    }

    public void clear() {
        drawing = false;
        tension = 0.0F;
        releasedAtMs = 0L;
        trajectoryPoints.clear();
        hitPointRoom = null;
    }

    public void renderTrajectoryArc() {
        if (!drawing || trajectoryPoints.size() < 2) return;

        com.gtnewhorizons.angelica.glsm.GLStateManager.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        com.gtnewhorizons.angelica.glsm.GLStateManager.glPushMatrix();

        try {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
            com.gtnewhorizons.angelica.glsm.GLStateManager.glLineWidth(3.5F);

            // Vibrant gradient: cyan transitioning to gold at full tension
            float r = 0.20F + 0.80F * tension;
            float g = 0.85F - 0.20F * tension;
            float b = 0.90F - 0.70F * tension;
            com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f(r, g, b, 0.85F);

            com.gtnewhorizons.angelica.glsm.GLStateManager.glBegin(org.lwjgl.opengl.GL11.GL_LINE_STRIP);
            for (Vector3f pt : trajectoryPoints) {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glVertex3f(pt.x, pt.y, pt.z);
            }
            com.gtnewhorizons.angelica.glsm.GLStateManager.glEnd();

            // Hit target marker ring
            if (hitPointRoom != null) {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glPushMatrix();
                com.gtnewhorizons.angelica.glsm.GLStateManager.glTranslatef(hitPointRoom.x, hitPointRoom.y + 0.01F, hitPointRoom.z);
                com.gtnewhorizons.angelica.glsm.GLStateManager.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

                com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f(r, g, b, 0.75F);
                com.gtnewhorizons.angelica.glsm.GLStateManager.glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
                int segments = 16;
                float radius = 0.20F;
                for (int s = 0; s < segments; s++) {
                    double theta = 2.0 * Math.PI * s / segments;
                    com.gtnewhorizons.angelica.glsm.GLStateManager.glVertex2d(Math.cos(theta) * radius, Math.sin(theta) * radius);
                }
                com.gtnewhorizons.angelica.glsm.GLStateManager.glEnd();
                com.gtnewhorizons.angelica.glsm.GLStateManager.glPopMatrix();
            }

        } finally {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glPopMatrix();
            com.gtnewhorizons.angelica.glsm.GLStateManager.glPopAttrib();
        }
    }

    /** Applies the physical draw strength to the arrow of the local player. */
    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || event.entityPlayer == null) {
            return;
        }
        if (!mc.thePlayer.getCommandSenderName().equals(event.entityPlayer.getCommandSenderName())) {
            return;
        }
        boolean recentlyReleased = (System.currentTimeMillis() - releasedAtMs) < RELEASE_WINDOW_MS;
        if (!drawing && !recentlyReleased) {
            return;
        }
        event.charge = Math.round(tension * FULL_CHARGE_TICKS);
    }
}
