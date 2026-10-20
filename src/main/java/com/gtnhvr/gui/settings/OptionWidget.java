package com.gtnhvr.gui.settings;

import com.gtnhvr.gui.VRSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class OptionWidget {
    private static final ResourceLocation GENERAL_TEX = new ResourceLocation("gtnhvr", "textures/gui/settings/general.png");
    private static final int GENERAL_TEX_W = 274;
    private static final int GENERAL_TEX_H = 260;

    protected final OptionWidgetPosition position;
    protected final int row;
    protected final String tooltip;

    public OptionWidget(OptionWidgetPosition position, int row, String tooltip) {
        this.position = position;
        this.row = row;
        this.tooltip = (tooltip != null) ? tooltip.replace("\\n", "\n").replace("/n", "\n") : null;
    }

    public OptionWidgetPosition getPosition() { return position; }
    public int getRow() { return row; }
    public String getTooltip() { return tooltip; }

    public abstract void draw(VRSettingsScreen screen, int x, int y, int w, int h, int mouseX, int mouseY);
    public abstract void onClick(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h);
    public void onDrag(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h) {}
    public void onRelease(VRSettingsScreen screen, int mouseX, int mouseY) {}

    // --- Button / Cycle Widget ---
    public static class ButtonWidget extends OptionWidget {
        private final Supplier<String> labelSupplier;
        private final Runnable onClickAction;

        public ButtonWidget(OptionWidgetPosition pos, int row, Supplier<String> labelSupplier, Runnable onClick, String tooltip) {
            super(pos, row, tooltip);
            this.labelSupplier = labelSupplier;
            this.onClickAction = onClick;
        }

        @Override
        public void draw(VRSettingsScreen screen, int x, int y, int w, int h, int mouseX, int mouseY) {
            boolean hovered = screen.isHovered(mouseX, mouseY, x, y, w, h);
            int bgColor = hovered ? 0xFF3A6082 : 0xFF222222;
            int borderColor = hovered ? 0xFF5A9AE2 : 0xFF444444;

            screen.drawSolidRect(x, y, x + w, y + h, bgColor);
            screen.drawOutline(x, y, w, h, borderColor);

            String text = labelSupplier.get();
            screen.drawScaledStringCentered(text, x + w / 2, y + (h - 8) / 2, 0xFFFFFF, w - 4);
        }

        @Override
        public void onClick(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h) {
            if (onClickAction != null) {
                onClickAction.run();
                screen.playClickSound();
            }
        }
    }

    // --- Toggle Widget with Visor Switch Texture ---
    public static class ToggleWidget extends OptionWidget {
        private final String label;
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        public ToggleWidget(OptionWidgetPosition pos, int row, String label, Supplier<Boolean> getter, Consumer<Boolean> setter, String tooltip) {
            super(pos, row, tooltip);
            this.label = label;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void draw(VRSettingsScreen screen, int x, int y, int w, int h, int mouseX, int mouseY) {
            boolean hovered = screen.isHovered(mouseX, mouseY, x, y, w, h);
            int bgColor = hovered ? 0xFF3A6082 : 0xFF222222;
            int borderColor = hovered ? 0xFF5A9AE2 : 0xFF444444;

            screen.drawSolidRect(x, y, x + w, y + h, bgColor);
            screen.drawOutline(x, y, w, h, borderColor);

            boolean on = getter.get();

            // Draw label
            int textW = w - 28;
            screen.drawScaledStringCentered(label, x + textW / 2 + 2, y + (h - 8) / 2, 0xFFFFFF, textW - 2);

            // Draw Visor Switch Icon
            Minecraft.getMinecraft().getTextureManager().bindTexture(GENERAL_TEX);
            int u = 235;
            int v = on ? 52 : 65; // 52 is ON, 65 is OFF
            int swW = 25;
            int swH = 12;
            int swX = x + w - swW - 2;
            int swY = y + (h - swH) / 2;
            VRSettingsScreen.drawTexturedQuad(swX, swY, swW, swH, u, v, swW, swH, GENERAL_TEX_W, GENERAL_TEX_H);
        }

        @Override
        public void onClick(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h) {
            setter.accept(!getter.get());
            screen.playClickSound();
        }
    }

    // --- Slider Widget ---
    public static class SliderWidget extends OptionWidget {
        private final String label;
        private final float min;
        private final float max;
        private final float step;
        private final Supplier<Float> getter;
        private final Consumer<Float> setter;
        private final Function<Float, String> formatter;

        public SliderWidget(OptionWidgetPosition pos, int row, String label,
                            float min, float max, float step,
                            Supplier<Float> getter, Consumer<Float> setter,
                            Function<Float, String> formatter, String tooltip) {
            super(pos, row, tooltip);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.formatter = formatter;
        }

        @Override
        public void draw(VRSettingsScreen screen, int x, int y, int w, int h, int mouseX, int mouseY) {
            boolean hovered = screen.isHovered(mouseX, mouseY, x, y, w, h);
            int trackBg = 0xFF111111;
            int borderColor = hovered ? 0xFF5A9AE2 : 0xFF444444;

            screen.drawSolidRect(x, y, x + w, y + h, trackBg);
            screen.drawOutline(x, y, w, h, borderColor);

            float cur = Math.max(min, Math.min(max, getter.get()));
            float frac = (max > min) ? (cur - min) / (max - min) : 0f;
            int thumbW = 6;
            int thumbX = x + Math.round(frac * (w - thumbW));

            // Filled bar
            screen.drawSolidRect(x, y + 1, thumbX, y + h - 1, 0xFF2A5072);

            // Thumb handle
            int thumbColor = hovered ? 0xFF7BB3F0 : 0xFF5A9AE2;
            screen.drawSolidRect(thumbX, y + 1, thumbX + thumbW, y + h - 1, thumbColor);

            // Label text
            String valStr = formatter != null ? formatter.apply(cur) : String.format("%.2f", cur);
            String fullText = label.isEmpty() ? valStr : (label + ": " + valStr);
            screen.drawScaledStringCentered(fullText, x + w / 2, y + (h - 8) / 2, 0xFFFFFF, w - 4);
        }

        @Override
        public void onClick(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h) {
            updateFromMouse(mouseX, x, w);
            screen.playClickSound();
        }

        @Override
        public void onDrag(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h) {
            updateFromMouse(mouseX, x, w);
        }

        private void updateFromMouse(int mouseX, int x, int w) {
            float frac = Math.max(0.0F, Math.min(1.0F, (float) (mouseX - x) / (float) w));
            float raw = min + frac * (max - min);
            float stepped = Math.round(raw / step) * step;
            stepped = Math.max(min, Math.min(max, stepped));
            setter.accept(stepped);
        }
    }

    // --- Submenu Widget ---
    public static class SubmenuWidget extends OptionWidget {
        private final String label;
        private final Supplier<VROptionsSet> targetSupplier;

        public SubmenuWidget(OptionWidgetPosition pos, int row, String label, Supplier<VROptionsSet> targetSupplier, String tooltip) {
            super(pos, row, tooltip);
            this.label = label;
            this.targetSupplier = targetSupplier;
        }

        @Override
        public void draw(VRSettingsScreen screen, int x, int y, int w, int h, int mouseX, int mouseY) {
            boolean hovered = screen.isHovered(mouseX, mouseY, x, y, w, h);
            int bgColor = hovered ? 0xFF3A6082 : 0xFF222222;
            int borderColor = hovered ? 0xFF5A9AE2 : 0xFF444444;

            screen.drawSolidRect(x, y, x + w, y + h, bgColor);
            screen.drawOutline(x, y, w, h, borderColor);

            String text = label;
            screen.drawScaledStringCentered(text, x + w / 2, y + (h - 8) / 2, 0xFFFFFF, w - 4);
        }

        @Override
        public void onClick(VRSettingsScreen screen, int mouseX, int mouseY, int x, int y, int w, int h) {
            if (targetSupplier != null) {
                VROptionsSet nextSet = targetSupplier.get();
                if (nextSet != null) {
                    screen.pushOptionsSet(nextSet);
                    screen.playClickSound();
                }
            }
        }
    }
}
