package com.gtnhvr.gui;

import com.gtnhvr.GTNHVRLog;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in GUI performance diagnostics. Enable with -Dgtnhvr.gui.profiler=true.
 * CPU timings never force GPU synchronization. GPU timings use asynchronous OpenGL timer
 * queries and are only read after GL_QUERY_RESULT_AVAILABLE reports completion.
 * Ten-second log summaries include count, average duration, and worst observed duration per stage.
 */
public final class VRGuiProfiler {
    private static final boolean ENABLED = Boolean.getBoolean("gtnhvr.gui.profiler");
    private static final long REPORT_INTERVAL_NANOS = 10_000_000_000L;
    private static final Map<String, Samples> SAMPLES = new LinkedHashMap<String, Samples>();
    private static final ArrayDeque<String> RESOLUTION_REASONS = new ArrayDeque<String>();
    private static final ArrayDeque<TimedCall> RESOLUTION_CALLS = new ArrayDeque<TimedCall>();
    private static final ArrayDeque<TimedCall> INIT_CALLS = new ArrayDeque<TimedCall>();
    private static final ArrayDeque<GpuSample> PENDING_GPU = new ArrayDeque<GpuSample>();
    private static long lastReportNanos = System.nanoTime();
    private static boolean gpuQueryActive;
    private static boolean reporting;

    static {
        if (ENABLED) {
            GTNHVRLog.info("GUI profiler enabled; per-screen CPU timings and asynchronous GPU queries enabled; no glFinish is used");
        }
    }

    private VRGuiProfiler() {}

    public static long startCpu() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void endCpu(String label, long startNanos) {
        if (!ENABLED || startNanos <= 0L) return;
        addSample("cpu/" + label, Math.max(0L, System.nanoTime() - startNanos));
        pollGpuQueries();
        maybeReport();
    }

    public static void pushResolutionReason(String reason) {
        if (ENABLED) RESOLUTION_REASONS.push(reason == null ? "other" : reason);
    }

    public static void popResolutionReason() {
        if (ENABLED && !RESOLUTION_REASONS.isEmpty()) RESOLUTION_REASONS.pop();
    }

    private static String currentResolutionReason() {
        return RESOLUTION_REASONS.isEmpty() ? "other" : RESOLUTION_REASONS.peek();
    }

    public static void beginScreenResolution(GuiScreen screen) {
        if (!ENABLED || screen == null) return;
        RESOLUTION_CALLS.push(new TimedCall(screen,
                "setWorldAndResolution/" + screen.getClass().getName() + "/reason=" + currentResolutionReason(),
                System.nanoTime()));
    }

    public static void endScreenResolution(GuiScreen screen) {
        if (!ENABLED || screen == null || RESOLUTION_CALLS.isEmpty()) return;
        TimedCall call = popForScreen(RESOLUTION_CALLS, screen);
        if (call != null) addSample("cpu/" + call.label, Math.max(0L, System.nanoTime() - call.startNanos));
        maybeReport();
    }

    public static void beginInitGui(GuiScreen screen) {
        if (!ENABLED || screen == null) return;
        INIT_CALLS.push(new TimedCall(screen, "initGui/" + screen.getClass().getName(), System.nanoTime()));
    }

    public static void endInitGui(GuiScreen screen) {
        if (!ENABLED || screen == null || INIT_CALLS.isEmpty()) return;
        TimedCall call = popForScreen(INIT_CALLS, screen);
        if (call != null) addSample("cpu/" + call.label, Math.max(0L, System.nanoTime() - call.startNanos));
        maybeReport();
    }

    private static TimedCall popForScreen(ArrayDeque<TimedCall> stack, GuiScreen screen) {
        if (stack.isEmpty()) return null;
        TimedCall top = stack.pop();
        if (top.screen == screen) return top;
        while (!stack.isEmpty()) {
            TimedCall current = stack.pop();
            if (current.screen == screen) return current;
        }
        return null;
    }

    public static int beginGpuTimer(String label) {
        if (!ENABLED || gpuQueryActive) return -1;
        int query = 0;
        try {
            if (!GL.getCapabilities().OpenGL33) return -1;
            query = GL15.glGenQueries();
            GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
            gpuQueryActive = true;
            return query;
        } catch (Throwable ignored) {
            if (query != 0) {
                try { GL15.glDeleteQueries(query); } catch (Throwable ignoredAgain) {}
            }
            return -1;
        }
    }

    public static void endGpuTimer(int query, String label) {
        if (!ENABLED || query < 0) return;
        try {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            gpuQueryActive = false;
            PENDING_GPU.add(new GpuSample(query, label));
        } catch (Throwable ignored) {
            gpuQueryActive = false;
            try { GL15.glDeleteQueries(query); } catch (Throwable ignoredAgain) {}
        }
        pollGpuQueries();
        maybeReport();
    }

    private static void pollGpuQueries() {
        if (!ENABLED || PENDING_GPU.isEmpty()) return;
        try {
            Iterator<GpuSample> it = PENDING_GPU.iterator();
            while (it.hasNext()) {
                GpuSample sample = it.next();
                if (GL15.glGetQueryObjecti(sample.query, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
                long elapsedNanos = GL33.glGetQueryObjecti64(sample.query, GL15.GL_QUERY_RESULT);
                it.remove();
                GL15.glDeleteQueries(sample.query);
                addSample("gpu/" + sample.label, Math.max(0L, elapsedNanos));
            }
        } catch (Throwable ignored) {
            // Keep CPU measurements available if timer queries are not supported by the driver.
        }
    }

    private static void addSample(String label, long elapsedNanos) {
        Samples s = SAMPLES.get(label);
        if (s == null) {
            s = new Samples();
            SAMPLES.put(label, s);
        }
        s.count++;
        s.totalNanos += elapsedNanos;
        if (elapsedNanos > s.maxNanos) s.maxNanos = elapsedNanos;
    }

    private static void maybeReport() {
        if (!ENABLED || reporting) return;
        long now = System.nanoTime();
        if (now - lastReportNanos < REPORT_INTERVAL_NANOS) return;
        reporting = true;
        try {
            pollGpuQueries();
            if (SAMPLES.isEmpty()) return;
            StringBuilder summary = new StringBuilder("GUI-PROFILER 10s");
            for (Map.Entry<String, Samples> entry : SAMPLES.entrySet()) {
                Samples s = entry.getValue();
                summary.append(" | ").append(entry.getKey())
                        .append(" n=").append(s.count)
                        .append(" avg=").append(String.format(java.util.Locale.ROOT, "%.2f",
                                s.totalNanos / (double) s.count / 1_000_000.0)).append("ms")
                        .append(" max=").append(String.format(java.util.Locale.ROOT, "%.2f",
                                s.maxNanos / 1_000_000.0)).append("ms");
            }
            GTNHVRLog.info("%s", summary.toString());
            SAMPLES.clear();
        } finally {
            lastReportNanos = now;
            reporting = false;
        }
    }

    private static final class Samples {
        private long count;
        private long totalNanos;
        private long maxNanos;
    }

    private static final class TimedCall {
        private final GuiScreen screen;
        private final String label;
        private final long startNanos;
        private TimedCall(GuiScreen screen, String label, long startNanos) {
            this.screen = screen;
            this.label = label;
            this.startNanos = startNanos;
        }
    }

    private static final class GpuSample {
        private final int query;
        private final String label;
        private GpuSample(int query, String label) {
            this.query = query;
            this.label = label;
        }
    }
}
