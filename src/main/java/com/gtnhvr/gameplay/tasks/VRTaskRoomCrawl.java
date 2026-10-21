package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.lwjgl.openxr.XrPosef;

/**
 * Physical room-scale sneak/crawl for Minecraft 1.7.10.
 *
 * STAGE positions are floor-relative, but they must be compared with a standing-head
 * baseline, not the configured avatar height: seated users and recentering can make those
 * values very different. LOCAL positions are measured relative to the most recent standing
 * pose because that reference space has no fixed floor origin.
 */
@Lwjgl3Aware
public class VRTaskRoomCrawl {
    private static final VRTaskRoomCrawl INSTANCE = new VRTaskRoomCrawl();

    private boolean isCrawling = false;
    private boolean isSneaking = false;

    private boolean stageHeightCalibrated = false;
    private float stageStandingHeadY = 0.0F;

    private boolean localHeightCalibrated = false;
    private float localStandingHeadY = 0.0F;

    private VRTaskRoomCrawl() {}

    public static VRTaskRoomCrawl getInstance() {
        return INSTANCE;
    }

    public void update(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) {
            clear(mc);
            return;
        }

        // A seated HMD commonly sits around 0.7 m above the floor. Treating that as a
        // standing-height ratio forced the player into a permanent sneak/crawl state.
        if (VRClientSettings.seated) {
            resetPhysicalCrouch(mc);
            return;
        }

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) {
            // Never leave a stale physical crouch latched when tracking is lost.
            resetPhysicalCrouch(mc);
            return;
        }

        float headY = headPose.position$().y();
        float baseHeight = Math.max(1.0F, VRClientSettings.playerHeight);
        boolean stageSpace = OpenXRRuntime.isStageSpace();
        float ratio;

        if (stageSpace) {
            // Capture current posture as the initial baseline. This also handles starting
            // the game while seated, then raises the baseline once the user stands up.
            if (!stageHeightCalibrated) {
                stageStandingHeadY = headY;
                stageHeightCalibrated = true;
            } else if (!isSneaking && !isCrawling && headY > stageStandingHeadY + 0.04F) {
                stageStandingHeadY = headY;
            }

            // Recenter/tracking jumps should reset the baseline rather than count as a crouch.
            float rebaseDistance = Math.max(0.90F, baseHeight * 0.75F);
            if (Math.abs(headY - stageStandingHeadY) > rebaseDistance) {
                stageStandingHeadY = headY;
                isSneaking = false;
                isCrawling = false;
                restoreStandingHitbox(mc);
            }

            float standingReference = Math.max(0.50F, stageStandingHeadY);
            ratio = Math.max(0.0F, headY) / standingReference;
        } else {
            // LOCAL reference spaces can be head-centered; use downward movement from a
            // standing baseline rather than interpreting raw Y as height from the floor.
            if (!localHeightCalibrated) {
                localStandingHeadY = headY;
                localHeightCalibrated = true;
            } else if (!isSneaking && !isCrawling && headY > localStandingHeadY + 0.04F) {
                localStandingHeadY = headY;
            }

            float rebaseDistance = Math.max(0.90F, baseHeight * 0.75F);
            if (Math.abs(headY - localStandingHeadY) > rebaseDistance) {
                localStandingHeadY = headY;
                isSneaking = false;
                isCrawling = false;
                restoreStandingHitbox(mc);
            }

            float dropFromStanding = Math.max(0.0F, localStandingHeadY - headY);
            ratio = Math.max(0.0F, baseHeight - dropFromStanding) / baseHeight;
        }

        // Hysteresis prevents flicker at the configured sneak/crawl thresholds.
        boolean allowSneak = VRClientSettings.roomSneakEnabled || VRClientSettings.roomSneak;
        float sneakEnter = VRClientSettings.roomSneakThreshold - 0.02F;
        float sneakExit = VRClientSettings.roomSneakThreshold + 0.02F;
        isSneaking = allowSneak && (isSneaking ? ratio <= sneakExit : ratio <= sneakEnter);

        boolean allowCrawl = VRClientSettings.roomCrawlEnabled || VRClientSettings.roomCrawl;
        float crawlEnter = VRClientSettings.roomCrawlThreshold - 0.02F;
        float crawlExit = VRClientSettings.roomCrawlThreshold + 0.02F;
        boolean shouldCrawl = allowCrawl && (isCrawling ? ratio <= crawlExit : ratio <= crawlEnter);

        if (shouldCrawl) {
            isCrawling = true;
            mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 0.65D;
            mc.thePlayer.yOffset = 0.35F;
        } else if (isCrawling) {
            isCrawling = false;
            restoreStandingHitbox(mc);
        }
    }

    private void resetPhysicalCrouch(Minecraft mc) {
        if (isCrawling) {
            restoreStandingHitbox(mc);
        }
        isCrawling = false;
        isSneaking = false;
        stageHeightCalibrated = false;
        stageStandingHeadY = 0.0F;
        localHeightCalibrated = false;
        localStandingHeadY = 0.0F;
    }

    private static void restoreStandingHitbox(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.thePlayer.boundingBox == null) return;
        mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 1.80D;
        mc.thePlayer.yOffset = 1.62F;
    }

    public boolean isSneaking() {
        return isSneaking;
    }

    public boolean isCrawling() {
        return isCrawling;
    }

    /** Reset posture calibration after GUI transitions, tracking loss, or height changes. */
    public void clear(Minecraft mc) {
        if (isCrawling) {
            restoreStandingHitbox(mc);
        }
        isCrawling = false;
        isSneaking = false;
        stageHeightCalibrated = false;
        stageStandingHeadY = 0.0F;
        localHeightCalibrated = false;
        localStandingHeadY = 0.0F;
    }

    /** Rebase the next room-scale sample after the avatar height has been changed. */
    public void resetHeightCalibration() {
        resetPhysicalCrouch(Minecraft.getMinecraft());
    }
}
