package com.gtnhvr;

import com.gtnhvr.settings.HeightUnits;
import com.gtnhvr.settings.VRClientSettings;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

/**
 * Player height handling: the standing eye level used by the Height Mode setting and the
 * automatic measurement that Auto Height performs once per game session.
 */
@Lwjgl3Aware
public final class VRHeightCalibrator {
    private static final float EYE_TO_TOP = 0.12F;
    private static final int COUNTDOWN_TICKS = 60;
    private static final int SAMPLE_TICKS = 20;

    private static boolean autoDone = false;
    private static int phase = 0; // 0 idle, 1 countdown, 2 sampling
    private static int ticks = 0;
    private static float sum = 0.0F;
    private static int samples = 0;

    private VRHeightCalibrator() {}

    /** Standing eye level in metres derived from the configured player height. */
    public static float getStandingEyeHeight() {
        return Math.max(0.8F, Math.min(2.4F, VRClientSettings.playerHeight - EYE_TO_TOP));
    }

    /** Converts a measured eye level into a body height. */
    public static float eyeToHeight(float eyeLevel) {
        return eyeLevel + EYE_TO_TOP;
    }

    /** Schedules a new automatic measurement, e.g. after Auto Height was switched on. */
    public static void requestAutoMeasure() {
        autoDone = false;
    }

    public static String formatHeight(float meters) {
        if (VRClientSettings.heightUnits == HeightUnits.IMPERIAL) {
            int totalInches = Math.round(meters * 39.3701F);
            return I18n.format("visor.height.imperial", totalInches / 12, totalInches % 12);
        }
        return I18n.format("visor.height.metric", Math.round(meters * 100.0F));
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) {
            return;
        }
        if (phase == 0) {
            boolean auto = VRClientSettings.autoHeight || VRClientSettings.heightAuto;
            if (autoDone || !auto || VRClientSettings.seated
                    || !OpenXRRuntime.isStageSpace() || OpenXRRuntime.getHeadPose() == null) {
                return;
            }
            phase = 1;
            ticks = COUNTDOWN_TICKS;
            GTNHVRLog.chat(I18n.format("visor.messages.height_auto_hint"));
            return;
        }
        if (phase == 1) {
            ticks--;
            if (ticks <= 0) {
                phase = 2;
                ticks = SAMPLE_TICKS;
                sum = 0.0F;
                samples = 0;
            }
            return;
        }

        float headY = HeadPoseController.getY();
        if (headY > 0.90F && headY < 2.5F) {
            sum += headY;
            samples++;
        }
        ticks--;
        if (ticks <= 0) {
            phase = 0;
            autoDone = true;
            if (samples > 0) {
                float height = Math.max(1.2F, Math.min(2.4F, eyeToHeight(sum / samples)));
                VRClientSettings.playerHeight = height;
                com.gtnhvr.gameplay.tasks.VRTaskRoomCrawl.getInstance().resetHeightCalibration();
                VRClientSettings.save();
                GTNHVRLog.chat(I18n.format("visor.messages.height_auto_set", formatHeight(height)));
            }
        }
    }
}
