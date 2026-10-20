package com.gtnhvr;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mod logger. Writes through log4j so every line lands in latest.log together with
 * the rest of the client output (System.out lines were missing from the launcher log).
 */
@Lwjgl3Aware
public final class GTNHVRLog {
    private static final Logger LOGGER = LogManager.getLogger("GTNHVR");
    private static long lastThrottledNanos = 0;

    private GTNHVRLog() {}

    private static String fmt(String format, Object[] args) {
        if (args == null || args.length == 0) {
            return format;
        }
        try {
            return String.format(format, args);
        } catch (RuntimeException ex) {
            return format + " " + java.util.Arrays.toString(args);
        }
    }

    public static void info(String format, Object... args) {
        LOGGER.info("[GTNHVR] " + fmt(format, args));
    }

    public static void warn(String format, Object... args) {
        LOGGER.warn("[GTNHVR] " + fmt(format, args));
    }

    public static void throttled(String format, Object... args) {
        long now = System.nanoTime();
        if (now - lastThrottledNanos >= 1_000_000_000L) { // 1 sec
            lastThrottledNanos = now;
            info(format, args);
        }
    }

    public static void chat(String message) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("\u00a7b[GTNHVR]\u00a7r " + message));
            }
        } catch (Throwable ignored) {
            // Client may not have a player yet.
        }
    }
}
