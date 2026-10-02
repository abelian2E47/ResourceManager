package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Colours, geometry and drawing helpers shared by the Resource Manager panels. */
public final class Ui {
    public static final int PANEL = 0xFF16181D;
    public static final int PANEL_HEAD = 0xFF1F232B;
    public static final int BODY = 0xFF101216;
    public static final int BORDER = 0xFF2E3542;
    public static final int TEXT = 0xFFE8EBF1;
    public static final int DIM = 0xFFBBC6D6;
    public static final int MUTED = 0xFF97A1B2;
    public static final int ACCENT = 0xFF66B2FF;
    public static final int OK = 0xFF6FD39B;
    public static final int WARN = 0xFFFFB86B;
    public static final int OFF = 0xFF6C7381;
    public static final int ROW_SELECTED = 0xFF2B3D57;
    public static final int ROW_HOVER = 0xFF232A36;
    public static final int BUTTON = 0xFF272D37;
    public static final int BUTTON_HOVER = 0xFF333B48;
    public static final int BUTTON_ON = 0xFF2F5C96;
    public static final int BUTTON_OFF = 0xFF1B1F27;
    public static final int SCROLL_TRACK = 0xFF14161B;
    public static final int SCROLL_THUMB = 0xFF39424F;
    public static final int SCROLL_THUMB_HOVER = 0xFF4C5768;

    public static final int ROW_HEIGHT = 12;
    public static final int BUTTON_HEIGHT = 16;

    private Ui() {
    }

    /** Simple integer rectangle with exclusive right/bottom edges. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < right() && my >= y && my < bottom();
        }

        public Rect inset(int amount) {
            return new Rect(x + amount, y + amount, Math.max(0, w - amount * 2), Math.max(0, h - amount * 2));
        }
    }

    public static void panel(GuiGraphics graphics, Rect rect, int fill, int border) {
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        border(graphics, rect, border);
    }

    public static void border(GuiGraphics graphics, Rect rect, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, color);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color);
        graphics.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.bottom() - 1, color);
        graphics.fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, color);
    }

    /** Panel title strip: filled header with the label vertically centred. */
    public static void header(GuiGraphics graphics, Font font, Rect rect, Component title, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_HEAD);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), BORDER);
        graphics.drawString(font, title, rect.x() + 5, rect.y() + (rect.h() - 8) / 2, color, true);
    }

    /** Truncates a string with an ellipsis so that it fits into {@code maxWidth} pixels. */
    public static String trim(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 1 && font.width(text.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
    }

    /** Truncates from the left, which keeps the file name visible for long paths. */
    public static String trimLeft(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String prefix = "...";
        int start = 0;
        while (start < text.length() - 1 && font.width(prefix + text.substring(start)) > maxWidth) {
            start++;
        }
        return prefix + text.substring(start);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Positions a widget from an (x, y, width, height) rectangle.
     *
     * <p>{@code AbstractWidget#setRectangle} is {@code (width, height, x, y)} in the 1.21.4 mappings
     * — the opposite of what its parameter names suggest — which silently transposes every control
     * placed through it. All panel code goes through this helper instead.
     */
    public static <T extends AbstractWidget> T place(T widget, int x, int y, int width, int height) {
        widget.setPosition(x, y);
        widget.setSize(Math.max(1, width), Math.max(1, height));
        return widget;
    }
}
