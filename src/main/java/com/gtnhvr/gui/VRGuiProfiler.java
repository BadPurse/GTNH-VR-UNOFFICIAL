package com.gtnhvr.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Opt-in diagnostics for expensive modded GUI paths.
 *
 * Enable with the JVM argument -Dgtnhvr.guiProfiler=true. GPU timer queries are polled
 * asynchronously several frames later, so profiling never waits for the GPU to finish.
 */
public final class VRGuiProfiler {
    public enum Section {
        SCREEN_CAPTURE,
        HUD_CAPTURE,
        EYE_WORLD_LEFT,
        EYE_WORLD_RIGHT,
        SCREEN_COMPOSITE_LEFT,
        SCREEN_COMPOSITE_RIGHT,
        HUD_COMPOSITE_LEFT,
        HUD_COMPOSITE_RIGHT
    }

    private static final Logger LOGGER = LogManager.getLogger("GTNH-VR GUI");
    private static final boolean ENABLED = Boolean.getBoolean("gtnhvr.guiProfiler");
    private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static final int QUERY_RING_SIZE = 4;

    private static final GpuSection[] GPU = new GpuSection[Section.values().length];
    private static final long[] cpuWindowNanos = new long[Section.values().length];
    private static final long[] cpuWindowSamples = new long[Section.values().length];
    private static final long[] cpuLastNanos = new long[Section.values().length];

    private static boolean timerSupportChecked;
    private static boolean timerQueriesSupported;
    private static long lastLogNanos = System.nanoTime();

    private static Section activeSection;
    private static long activeCpuStart;
    private static int activeGpuSlot = -1;
    private static GpuSection activeGpuState;

    private static long scaledResolutionCount;
    private static long screenInitializationCount;
    private static long tooltipRequestCount;
    private static long tooltipHitCount;
    private static long tooltipMissCount;
    private static long tooltipGenerationNanos;
    private static long attribPushCount;
    private static long guiCaptureSkippedCount;
    private static long hudCaptureSkippedCount;

    static {
        for (int i = 0; i < GPU.length; i++) GPU[i] = new GpuSection();
    }

    private VRGuiProfiler() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void beginSection(Section section) {
        if (!ENABLED || section == null || activeSection != null) return;
        activeSection = section;
        activeCpuStart = System.nanoTime();
        startGpuTimer(section);
    }

    public static void endSection(Section section) {
        if (!ENABLED || section == null || activeSection != section) return;
        long elapsed = System.nanoTime() - activeCpuStart;
        int index = section.ordinal();
        cpuLastNanos[index] = elapsed;
        cpuWindowNanos[index] += elapsed;
        cpuWindowSamples[index]++;

        if (activeGpuState != null && activeGpuSlot >= 0) {
            try {
                GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
                activeGpuState.pending[activeGpuSlot] = true;
            } catch (Throwable ignored) {
                timerQueriesSupported = false;
            }
        }
        activeGpuState = null;
        activeGpuSlot = -1;
        activeSection = null;
        logIfDue();
    }

    private static void startGpuTimer(Section section) {
        if (!supportsTimerQueries()) return;
        pollGpuResults();

        GpuSection state = GPU[section.ordinal()];
        int slot = -1;
        for (int attempt = 0; attempt < QUERY_RING_SIZE; attempt++) {
            int candidate = (state.nextSlot + attempt) % QUERY_RING_SIZE;
            if (!state.pending[candidate]) {
                slot = candidate;
                state.nextSlot = (candidate + 1) % QUERY_RING_SIZE;
                break;
            }
        }
        if (slot < 0) return;

        try {
            if (state.queryIds[slot] == 0) state.queryIds[slot] = GL15.glGenQueries();
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, state.queryIds[slot]);
            activeGpuState = state;
            activeGpuSlot = slot;
        } catch (Throwable ignored) {
            timerQueriesSupported = false;
            activeGpuState = null;
            activeGpuSlot = -1;
        }
    }

    private static boolean supportsTimerQueries() {
        if (timerSupportChecked) return timerQueriesSupported;
        timerSupportChecked = true;
        try {
            String version = GL11.glGetString(GL11.GL_VERSION);
            String extensions = GL11.glGetString(GL11.GL_EXTENSIONS);
            boolean version33 = false;
            if (version != null) {
                String[] parts = version.split("[ .]");
                if (parts.length >= 2) {
                    int major = Integer.parseInt(parts[0].replaceAll("[^0-9].*$", ""));
                    int minor = Integer.parseInt(parts[1].replaceAll("[^0-9].*$", ""));
                    version33 = major > 3 || (major == 3 && minor >= 3);
                }
            }
            timerQueriesSupported = version33 || (extensions != null
                    && (extensions.contains("GL_ARB_timer_query") || extensions.contains("GL_EXT_timer_query")));
        } catch (Throwable ignored) {
            timerQueriesSupported = false;
        }
        if (!timerQueriesSupported) {
            LOGGER.info("GUI profiler: timer queries are unavailable; CPU timings and counters remain enabled.");
        }
        return timerQueriesSupported;
    }

    private static void pollGpuResults() {
        if (!timerQueriesSupported) return;
        for (int sectionIndex = 0; sectionIndex < GPU.length; sectionIndex++) {
            GpuSection state = GPU[sectionIndex];
            for (int slot = 0; slot < QUERY_RING_SIZE; slot++) {
                if (!state.pending[slot] || state.queryIds[slot] == 0) continue;
                try {
                    if (GL15.glGetQueryObjecti(state.queryIds[slot], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
                    state.latestNanos = GL33.glGetQueryObjectui64(state.queryIds[slot], GL15.GL_QUERY_RESULT);
                    state.windowNanos += Math.max(0L, state.latestNanos);
                    state.windowSamples++;
                    state.pending[slot] = false;
                } catch (Throwable ignored) {
                    timerQueriesSupported = false;
                    return;
                }
            }
        }
    }

    public static void scaledResolutionCreated() {
        if (ENABLED) scaledResolutionCount++;
    }

    public static void screenInitialization() {
        if (ENABLED) screenInitializationCount++;
    }

    public static void tooltipRequest() {
        if (ENABLED) tooltipRequestCount++;
    }

    public static void tooltipCacheHit() {
        if (ENABLED) tooltipHitCount++;
    }

    public static void tooltipCacheMiss(long generationNanos) {
        if (!ENABLED) return;
        tooltipMissCount++;
        tooltipGenerationNanos += Math.max(0L, generationNanos);
    }

    public static void attribPush() {
        if (ENABLED) attribPushCount++;
    }

    public static void guiCaptureSkipped() {
        if (ENABLED) guiCaptureSkippedCount++;
    }

    public static void hudCaptureSkipped() {
        if (ENABLED) hudCaptureSkippedCount++;
    }

    private static void logIfDue() {
        long now = System.nanoTime();
        if (now - lastLogNanos < LOG_INTERVAL_NANOS) return;
        pollGpuResults();
        LOGGER.info("GUI profiler (5s): screen GPU/CPU={} ms/{} ms, HUD={} ms/{} ms, " +
                        "eye-world L/R GPU/CPU={} / {} ms, {} / {} ms; " +
                        "composite L/R screen={} / {} ms HUD={} / {} ms; " +
                        "ScaledResolution={}, setWorldAndResolution={}, tooltips={} hit={} miss={} " +
                        "generation={} ms, glPushAttrib={}, skipped captures GUI/HUD={}/{} " +
                        "(GPU values are completed-sample averages)",
                gpuMs(Section.SCREEN_CAPTURE), cpuMs(Section.SCREEN_CAPTURE),
                gpuMs(Section.HUD_CAPTURE), cpuMs(Section.HUD_CAPTURE),
                gpuMs(Section.EYE_WORLD_LEFT), cpuMs(Section.EYE_WORLD_LEFT),
                gpuMs(Section.EYE_WORLD_RIGHT), cpuMs(Section.EYE_WORLD_RIGHT),
                gpuMs(Section.SCREEN_COMPOSITE_LEFT), gpuMs(Section.SCREEN_COMPOSITE_RIGHT),
                gpuMs(Section.HUD_COMPOSITE_LEFT), gpuMs(Section.HUD_COMPOSITE_RIGHT),
                scaledResolutionCount, screenInitializationCount, tooltipRequestCount,
                tooltipHitCount, tooltipMissCount, tooltipGenerationNanos / 1_000_000.0D,
                attribPushCount, guiCaptureSkippedCount, hudCaptureSkippedCount);
        lastLogNanos = now;
        scaledResolutionCount = 0L;
        screenInitializationCount = 0L;
        tooltipRequestCount = 0L;
        tooltipHitCount = 0L;
        tooltipMissCount = 0L;
        tooltipGenerationNanos = 0L;
        attribPushCount = 0L;
        guiCaptureSkippedCount = 0L;
        hudCaptureSkippedCount = 0L;
        java.util.Arrays.fill(cpuWindowNanos, 0L);
        java.util.Arrays.fill(cpuWindowSamples, 0L);
        for (GpuSection state : GPU) {
            state.windowNanos = 0L;
            state.windowSamples = 0L;
        }
    }

    private static double cpuMs(Section section) {
        int index = section.ordinal();
        long samples = cpuWindowSamples[index];
        return samples == 0 ? 0.0D : (cpuWindowNanos[index] / 1_000_000.0D) / samples;
    }

    private static String gpuMs(Section section) {
        if (!timerQueriesSupported || !timerSupportChecked) return "n/a";
        GpuSection state = GPU[section.ordinal()];
        if (state.windowSamples <= 0L) return "n/a";
        double averageNanos = state.windowNanos / (double) state.windowSamples;
        return String.format(java.util.Locale.ROOT, "%.3f", averageNanos / 1_000_000.0D);
    }

    /** Releases timer-query objects when the VR GUI targets are destroyed. */
    public static void destroyQueries() {
        if (!ENABLED || !timerSupportChecked || !timerQueriesSupported) return;
        for (GpuSection state : GPU) {
            for (int i = 0; i < state.queryIds.length; i++) {
                if (state.queryIds[i] == 0) continue;
                try {
                    GL15.glDeleteQueries(state.queryIds[i]);
                } catch (Throwable ignored) {}
                state.queryIds[i] = 0;
                state.pending[i] = false;
            }
        }
    }

    private static final class GpuSection {
        private final int[] queryIds = new int[QUERY_RING_SIZE];
        private final boolean[] pending = new boolean[QUERY_RING_SIZE];
        private int nextSlot;
        private long latestNanos = -1L;
        private long windowNanos;
        private long windowSamples;
    }
}
