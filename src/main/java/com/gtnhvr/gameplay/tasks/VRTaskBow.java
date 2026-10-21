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

    /** True when the physical bow replaces the normal use-item input. */
    public boolean isBowHeld(Minecraft mc) {
        if (!VRClientSettings.worldTwoHanded || mc == null || mc.thePlayer == null || mc.currentScreen != null) {
            return false;
        }
        VRControllerHandler handler = VRControllerHandler.getInstance();
        if (!handler.isRightHandTracked() || !handler.isLeftHandTracked()) {
            return false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBow;
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
            if (drawing) {
                finishDraw();
            }
            return;
        }

        Vector3f mainPos = handler.getRightHandPos();
        Vector3f offPos = handler.getLeftHandPos();
        float distance = mainPos.distance(offPos);
        float offTrigger = handler.getLeftTriggerValue();

        if (!drawing) {
            if (offTrigger > TRIGGER_GRAB && distance < GRAB_DISTANCE) {
                drawing = true;
                tension = 0.0F;
                handler.triggerHaptic(true, 10_000_000L, 0.60F, 0);
            }
            return;
        }

        // Aim: from the string hand through the bow hand
        Vector3f aim = new Vector3f(mainPos).sub(offPos);
        if (aim.lengthSquared() > 1.0E-6F) {
            aimRoomDir.set(aim).normalize();
        }
        tension = Math.max(0.0F, Math.min(1.0F, (distance - MIN_PULL) / (FULL_PULL - MIN_PULL)));

        // String tension felt in the bow hand
        long now = System.currentTimeMillis();
        if (tension > 0.05F && now - lastHapticMs > 60L) {
            lastHapticMs = now;
            handler.triggerHaptic(false, 20_000_000L, 0.15F + 0.45F * tension, 0);
        }

        // Calculate arrow trajectory arc in Minecraft world (1:1 with Visor BowAimCrosshairRenderer)
        trajectoryPoints.clear();
        hitPointRoom = null;
        if (tension > 0.05F && mc.theWorld != null) {
            float charge = tension * FULL_CHARGE_TICKS;
            float f = (charge * charge + charge * 2.0F) / 3.0F / 20.0F;
            if (f > 1.0F) f = 1.0F;
            float speed = f * 3.0F;

            Vector3f startRoom = new Vector3f(mainPos);
            trajectoryPoints.add(new Vector3f(startRoom));

            net.minecraft.util.Vec3 curWorld = handler.roomToWorld(startRoom, 1.0F);
            Vector3f worldAim = handler.roomDirectionToWorld(new Vector3f(aimRoomDir));

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

        if (offTrigger < TRIGGER_RELEASE) {
            finishDraw();
            handler.triggerHaptic(true, 15_000_000L, 0.70F, 0);
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

        org.lwjgl.opengl.GLStateManager.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        org.lwjgl.opengl.GLStateManager.glPushMatrix();

        try {
            org.lwjgl.opengl.GLStateManager.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GLStateManager.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GLStateManager.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GLStateManager.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
            org.lwjgl.opengl.GLStateManager.glLineWidth(3.5F);

            // Vibrant gradient: cyan transitioning to gold at full tension
            float r = 0.20F + 0.80F * tension;
            float g = 0.85F - 0.20F * tension;
            float b = 0.90F - 0.70F * tension;
            org.lwjgl.opengl.GLStateManager.glColor4f(r, g, b, 0.85F);

            org.lwjgl.opengl.GLStateManager.glBegin(org.lwjgl.opengl.GL11.GL_LINE_STRIP);
            for (Vector3f pt : trajectoryPoints) {
                org.lwjgl.opengl.GLStateManager.glVertex3f(pt.x, pt.y, pt.z);
            }
            org.lwjgl.opengl.GLStateManager.glEnd();

            // Hit target marker ring
            if (hitPointRoom != null) {
                org.lwjgl.opengl.GLStateManager.glPushMatrix();
                org.lwjgl.opengl.GLStateManager.glTranslatef(hitPointRoom.x, hitPointRoom.y + 0.01F, hitPointRoom.z);
                org.lwjgl.opengl.GLStateManager.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

                org.lwjgl.opengl.GLStateManager.glColor4f(r, g, b, 0.75F);
                org.lwjgl.opengl.GLStateManager.glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
                int segments = 16;
                float radius = 0.20F;
                for (int s = 0; s < segments; s++) {
                    double theta = 2.0 * Math.PI * s / segments;
                    org.lwjgl.opengl.GLStateManager.glVertex2d(Math.cos(theta) * radius, Math.sin(theta) * radius);
                }
                org.lwjgl.opengl.GLStateManager.glEnd();
                org.lwjgl.opengl.GLStateManager.glPopMatrix();
            }

        } finally {
            org.lwjgl.opengl.GLStateManager.glPopMatrix();
            org.lwjgl.opengl.GLStateManager.glPopAttrib();
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
