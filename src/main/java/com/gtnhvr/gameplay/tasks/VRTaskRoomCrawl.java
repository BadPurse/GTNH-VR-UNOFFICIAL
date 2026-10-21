package com.gtnhvr.gameplay.tasks;

import com.gtnhvr.OpenXRRuntime;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import org.lwjgl.openxr.XrPosef;

/**
 * 1:1 Visor TaskRoomCrawl & TaskRoomSneak ported to Minecraft 1.7.10:
 * Detects real-life crouching and crawling from the VR headset height.
 * - Sneaking: Triggered when head height drops below roomSneakThreshold (default 0.85).
 * - Crawling: Triggered when head height drops below roomCrawlThreshold (default 0.70),
 *   reducing the player bounding box height to 0.65m to enable crawling through 1-block gaps.
 * Connected directly to VRClientSettings (roomSneakEnabled, roomCrawlEnabled, thresholds).
 */
@Lwjgl3Aware
public class VRTaskRoomCrawl {
    private static final VRTaskRoomCrawl INSTANCE = new VRTaskRoomCrawl();

    private boolean isCrawling = false;
    private boolean isSneaking = false;

    // LOCAL reference space has no floor origin. Track the highest recent standing-head
    // position and measure crouching as a downward delta instead of treating raw Y as height.
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

        XrPosef headPose = OpenXRRuntime.getHeadPose();
        if (headPose == null) return;

        float headY = headPose.position$().y();
        float baseHeight = Math.max(1.0F, VRClientSettings.playerHeight);
        final boolean stageSpace = OpenXRRuntime.isStageSpace();
        final float effectiveHeadHeight;

        if (stageSpace) {
            // STAGE is floor-relative, so the headset's Y coordinate is its real room height.
            localHeightCalibrated = false;
            effectiveHeadHeight = headY;
        } else {
            // LOCAL spaces can be centered around the user's head rather than the floor.
            // Raw headY is therefore not a valid height-from-floor measurement.
            if (!localHeightCalibrated) {
                localStandingHeadY = headY;
                localHeightCalibrated = true;
            } else if (!isSneaking && !isCrawling && headY > localStandingHeadY + 0.04F) {
                // Update upward only while standing, allowing a new upright pose after recentering.
                localStandingHeadY = headY;
            }

            // A discontinuous pose jump is usually recentering or tracking re-acquisition.
            // Rebase instead of interpreting it as an instant crouch or crawl.
            if (Math.abs(headY - localStandingHeadY) > baseHeight * 0.75F) {
                localStandingHeadY = headY;
                isSneaking = false;
                isCrawling = false;
                mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 1.80D;
                mc.thePlayer.yOffset = 1.62F;
            }
            float dropFromStanding = Math.max(0.0F, localStandingHeadY - headY);
            effectiveHeadHeight = Math.max(0.0F, baseHeight - dropFromStanding);
        }

        float ratio = effectiveHeadHeight / baseHeight;

        // 1. Room Sneak (with hysteresis to prevent rapid boundary flicker)
        boolean allowSneak = (VRClientSettings.roomSneakEnabled || VRClientSettings.roomSneak);
        float sneakEnter = VRClientSettings.roomSneakThreshold - 0.02F;
        float sneakExit = VRClientSettings.roomSneakThreshold + 0.02F;
        boolean validHeight = stageSpace ? headY > 0.30F : localHeightCalibrated;
        if (allowSneak && validHeight) {
            isSneaking = isSneaking ? (ratio <= sneakExit) : (ratio <= sneakEnter);
        } else {
            isSneaking = false;
        }

        // 2. Room Crawl (with hysteresis)
        boolean allowCrawl = (VRClientSettings.roomCrawlEnabled || VRClientSettings.roomCrawl);
        float crawlEnter = VRClientSettings.roomCrawlThreshold - 0.02F;
        float crawlExit = VRClientSettings.roomCrawlThreshold + 0.02F;
        boolean shouldCrawl = false;
        if (allowCrawl && validHeight) {
            shouldCrawl = isCrawling ? (ratio <= crawlExit) : (ratio <= crawlEnter);
        }

        if (shouldCrawl) {
            if (!isCrawling) {
                isCrawling = true;
            }
            // Shrink bounding box for 1-block crawling
            mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 0.65D;
            mc.thePlayer.yOffset = 0.35F;
        } else if (isCrawling) {
            isCrawling = false;
            // Restore normal height if clear above
            mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 1.80D;
            mc.thePlayer.yOffset = 1.62F;
        }
    }

    public boolean isSneaking() {
        return isSneaking;
    }

    public boolean isCrawling() {
        return isCrawling;
    }

    public void clear(Minecraft mc) {
        if (isCrawling && mc != null && mc.thePlayer != null) {
            mc.thePlayer.boundingBox.maxY = mc.thePlayer.boundingBox.minY + 1.80D;
            mc.thePlayer.yOffset = 1.62F;
        }
        isCrawling = false;
        isSneaking = false;
        localHeightCalibrated = false;
        localStandingHeadY = 0.0F;
    }
}
