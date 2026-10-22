package com.gtnhvr.gui.settings;

import com.gtnhvr.gui.VRSettingsScreen;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public abstract class VROptionsSet {
    protected final VRSettingsScreen screen;
    protected final VROptionsSet previousSet;
    protected final List<OptionWidget> widgets = new ArrayList<>();
    protected OptionWidget activeDraggingWidget = null;
    protected int scrollOffset = 0;

    public VROptionsSet(VRSettingsScreen screen, VROptionsSet previousSet) {
        this.screen = screen;
        this.previousSet = previousSet;
    }

    public VRSettingsScreen getScreen() {
        return screen;
    }

    public VROptionsSet getPreviousSet() {
        return previousSet;
    }

    public boolean canGoBack() {
        return previousSet != null;
    }

    public void init() {
        widgets.clear();
        buildWidgets();
        int max = getMaxScroll();
        if (scrollOffset > max) scrollOffset = max;
    }

    protected abstract void buildWidgets();

    /**
     * Gives specialized settings pages a chance to consume a physical keyboard key,
     * for example while waiting for a new binding. Return true when handled.
     */
    public boolean handleKeyTyped(char typedChar, int keyCode) {
        return false;
    }

    public int getMaxScroll() {
        int maxRow = 0;
        for (OptionWidget w : widgets) {
            if (w.getRow() > maxRow) {
                maxRow = w.getRow();
            }
        }
        int totalH = (maxRow + 1) * screen.getOptionRowStep();
        int areaH = screen.getOptionsAreaHeight();
        return Math.max(0, totalH - areaH);
    }

    public void handleMouseWheel(int dWheel) {
        int max = getMaxScroll();
        if (max > 0) {
            int step = screen.getOptionRowStep();
            int delta = (dWheel < 0) ? step : -step;
            scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
        }
    }

    private float stickScrollAccum = 0.0F;

    public void handleStickScroll(float stickY) {
        if (Math.abs(stickY) < 0.15F) {
            stickScrollAccum = 0.0F;
            return;
        }

        int max = getMaxScroll();
        if (max <= 0) {
            scrollOffset = 0;
            return;
        }

        float speed = screen.getScaleHelper().scaledSize(4) * stickY;
        stickScrollAccum -= speed;
        if (Math.abs(stickScrollAccum) >= 1.0F) {
            int delta = (int) stickScrollAccum;
            scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
            stickScrollAccum -= delta;
        }
    }

    public void draw(VRSettingsScreen screen, int mouseX, int mouseY, float partialTicks) {
        int leftColX = screen.getOptionLeftColumnX();
        int rightColX = screen.getOptionRightColumnX();
        int centerColX = screen.getOptionCenterColumnX();
        int colW = screen.getOptionColumnWidth();
        int centerW = screen.getOptionCenterWidth();
        int widgetH = screen.getOptionWidgetHeight();
        int startY = screen.getOptionsStartY();
        int rowStep = screen.getOptionRowStep();
        int areaH = screen.getOptionsAreaHeight();
        int areaW = screen.getOptionsAreaWidth();

        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Clip options strictly within the center options pane
        VRSettingsScreen.enableScissor(screen.mc, centerColX - 2, startY - 2, areaW + 4, areaH + 4, screen.width, screen.height);
        try {
            for (OptionWidget widget : widgets) {
                int x;
                int w;
                if (widget.getPosition() == OptionWidgetPosition.LEFT) {
                    x = leftColX;
                    w = colW;
                } else if (widget.getPosition() == OptionWidgetPosition.RIGHT) {
                    x = rightColX;
                    w = colW;
                } else {
                    x = centerColX;
                    w = centerW;
                }

                int y = startY + widget.getRow() * rowStep - scrollOffset;
                if (y + widgetH >= startY - 5 && y <= startY + areaH + 5) {
                    widget.draw(screen, x, y, w, widgetH, mouseX, mouseY);
                }
            }
        } finally {
            VRSettingsScreen.disableScissor();
        }

        // Draw scrollbar if content exceeds available height
        if (maxScroll > 0) {
            drawScrollbar(screen, centerColX + areaW + 1, startY, areaH, maxScroll);
        }
    }

    private void drawScrollbar(VRSettingsScreen screen, int barX, int barY, int barH, int maxScroll) {
        int barW = screen.getScaleHelper().scaledSize(2);
        screen.drawSolidRect(barX, barY, barX + barW, barY + barH, 0xFF111111);
        int thumbH = Math.max(screen.getScaleHelper().scaledSize(10), barH * barH / (barH + maxScroll));
        int thumbY = barY + (barH - thumbH) * scrollOffset / maxScroll;
        screen.drawSolidRect(barX, thumbY, barX + barW, thumbY + thumbH, 0xFF7BB3F0);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false; // Left mouse click

        int leftColX = screen.getOptionLeftColumnX();
        int rightColX = screen.getOptionRightColumnX();
        int centerColX = screen.getOptionCenterColumnX();
        int colW = screen.getOptionColumnWidth();
        int centerW = screen.getOptionCenterWidth();
        int widgetH = screen.getOptionWidgetHeight();
        int startY = screen.getOptionsStartY();
        int areaH = screen.getOptionsAreaHeight();
        int rowStep = screen.getOptionRowStep();

        if (mouseY < startY - 2 || mouseY > startY + areaH + 2) {
            return false;
        }

        for (OptionWidget widget : widgets) {
            int x = (widget.getPosition() == OptionWidgetPosition.LEFT) ? leftColX :
                    (widget.getPosition() == OptionWidgetPosition.RIGHT) ? rightColX : centerColX;
            int w = (widget.getPosition() == OptionWidgetPosition.CENTER) ? centerW : colW;
            int y = startY + widget.getRow() * rowStep - scrollOffset;

            if (screen.isHovered(mouseX, mouseY, x, y, w, widgetH)) {
                widget.onClick(screen, mouseX, mouseY, x, y, w, widgetH);
                if (widget instanceof OptionWidget.SliderWidget) {
                    activeDraggingWidget = widget;
                }
                return true;
            }
        }
        return false;
    }

    public void mouseClickMove(int mouseX, int mouseY, int button, long time) {
        if (activeDraggingWidget != null) {
            int leftColX = screen.getOptionLeftColumnX();
            int rightColX = screen.getOptionRightColumnX();
            int centerColX = screen.getOptionCenterColumnX();
            int colW = screen.getOptionColumnWidth();
            int centerW = screen.getOptionCenterWidth();
            int widgetH = screen.getOptionWidgetHeight();
            int startY = screen.getOptionsStartY();
            int rowStep = screen.getOptionRowStep();

            int x = (activeDraggingWidget.getPosition() == OptionWidgetPosition.LEFT) ? leftColX :
                    (activeDraggingWidget.getPosition() == OptionWidgetPosition.RIGHT) ? rightColX : centerColX;
            int w = (activeDraggingWidget.getPosition() == OptionWidgetPosition.CENTER) ? centerW : colW;
            int y = startY + activeDraggingWidget.getRow() * rowStep - scrollOffset;

            activeDraggingWidget.onDrag(screen, mouseX, mouseY, x, y, w, widgetH);
        }
    }

    public void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which >= 0) {
            if (activeDraggingWidget != null) {
                activeDraggingWidget.onRelease(screen, mouseX, mouseY);
                activeDraggingWidget = null;
            }
        }
    }

    public void handleMouseInput() {}

    public OptionWidget getHoveredWidget(int mouseX, int mouseY) {
        int leftColX = screen.getOptionLeftColumnX();
        int rightColX = screen.getOptionRightColumnX();
        int centerColX = screen.getOptionCenterColumnX();
        int colW = screen.getOptionColumnWidth();
        int centerW = screen.getOptionCenterWidth();
        int widgetH = screen.getOptionWidgetHeight();
        int startY = screen.getOptionsStartY();
        int areaH = screen.getOptionsAreaHeight();
        int rowStep = screen.getOptionRowStep();

        if (mouseY < startY || mouseY > startY + areaH) {
            return null;
        }

        for (OptionWidget widget : widgets) {
            int x = (widget.getPosition() == OptionWidgetPosition.LEFT) ? leftColX :
                    (widget.getPosition() == OptionWidgetPosition.RIGHT) ? rightColX : centerColX;
            int w = (widget.getPosition() == OptionWidgetPosition.CENTER) ? centerW : colW;
            int y = startY + widget.getRow() * rowStep - scrollOffset;

            if (screen.isHovered(mouseX, mouseY, x, y, w, widgetH)) {
                return widget;
            }
        }
        return null;
    }

    public void loadDefaults() {
        init();
    }
}
