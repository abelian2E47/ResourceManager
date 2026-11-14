package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** Shared pixel geometry and vanilla bevel drawing. */
public final class Ui {
    public static final int LINE_HEIGHT = 9;
    public static final int SCROLL_TRACK = UiColors.SLOT,
            SCROLL_THUMB = UiColors.CONTROL,
            SCROLL_THUMB_HOVER = UiColors.HOVER;

    private Ui() {}

    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double x, double y) {
            return x >= this.x && x < right() && y >= this.y && y < bottom();
        }
    }

    public static void bevel(GuiGraphics g, Rect r, int fill, boolean inset) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        int top = inset ? 0xFF373737 : 0xFFFFFFFF, bottom = inset ? 0xFFFFFFFF : 0xFF555555;
        g.fill(r.x(), r.y(), r.right() - 1, r.y() + 1, top);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom() - 1, top);
        g.fill(r.x() + 1, r.bottom() - 1, r.right(), r.bottom(), bottom);
        g.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom(), bottom);
    }

    public static void panel(GuiGraphics g, Rect r, int fill, int border) {
        bevel(g, r, fill, true);
    }

    public static void border(GuiGraphics g, Rect r, int color) {
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }

    public static String trim(Font font, String text, int width) {
        if (font == null || text == null || width <= 0) return "";
        if (font.width(text) <= width) return text;
        if (font.width("...") > width) return "";
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static <T extends AbstractWidget> T place(
            T widget, int x, int y, int width, int height) {
        widget.setPosition(x, y);
        widget.setSize(Math.max(1, width), Math.max(1, height));
        return widget;
    }
}
