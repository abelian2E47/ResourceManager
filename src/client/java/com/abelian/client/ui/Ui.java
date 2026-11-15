package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** Shared pixel geometry, vanilla bevels and the few primitives every panel draws. */
public final class Ui {
    public static final int SCROLL_THUMB = UiColors.HANDLE, SCROLL_THUMB_HOVER = UiColors.ACCENT;

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

    /** Raised or sunken 1px bevel over a fill, the way vanilla draws slots and buttons. */
    public static void bevel(GuiGraphics g, Rect r, int fill, boolean inset) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        int top = inset ? UiColors.BORDER : UiColors.BORDER_STRONG;
        int bottom = inset ? UiColors.BORDER_STRONG : 0xFF555555;
        g.fill(r.x(), r.y(), r.right() - 1, r.y() + 1, top);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom() - 1, top);
        g.fill(r.x() + 1, r.bottom() - 1, r.right(), r.bottom(), bottom);
        g.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom(), bottom);
    }

    /** Raised or sunken bevel with explicit edge colours, for controls off the vanilla palette. */
    public static void bevelStyled(GuiGraphics g, Rect r, int fill, int edgeLight, int edgeDark) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), fill);
        g.fill(r.x(), r.y(), r.right() - 1, r.y() + 1, edgeLight);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom() - 1, edgeLight);
        g.fill(r.x() + 1, r.bottom() - 1, r.right(), r.bottom(), edgeDark);
        g.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom(), edgeDark);
    }

    /** Window body: a 2px drop shadow, a dark outline, a light inner bevel and the container fill. */
    public static void frame(GuiGraphics g, Rect r) {
        shadow(g, r);
        bevel(g, r, UiColors.CONTAINER, false);
        border(g, r, UiColors.BORDER);
    }

    /** Two pixel offset shadow, so a panel reads as a surface above the world instead of a texture in it. */
    public static void shadow(GuiGraphics g, Rect r) {
        g.fill(r.x() + 2, r.bottom(), r.right() + 2, r.bottom() + 2, UiColors.SHADOW);
        g.fill(r.right(), r.y() + 2, r.right() + 2, r.bottom() + 2, UiColors.SHADOW);
    }

    /** Dark app bar: chrome fill with a light top edge and a hard dark base. */
    public static void chrome(GuiGraphics g, Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), UiColors.CHROME);
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, UiColors.CHROME_EDGE_LIGHT);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), UiColors.CHROME_EDGE_DARK);
    }

    /** Section heading: a 3px accent tick and a bold label. Returns the width it used. */
    public static int section(GuiGraphics g, Font font, String text, int x, int y) {
        g.fill(x, y + 2, x + 2, y + 9, UiColors.ACCENT);
        g.drawString(font, net.minecraft.network.chat.Component.literal(text)
                .withStyle(net.minecraft.ChatFormatting.BOLD), x + 5, y, UiColors.INK, false);
        return 5 + font.width(text);
    }

    /** One pixel line in {@code color}. */
    public static void border(GuiGraphics g, Rect r, int color) {
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, color);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), color);
        g.fill(r.x(), r.y(), r.x() + 1, r.bottom(), color);
        g.fill(r.right() - 1, r.y(), r.right(), r.bottom(), color);
    }

    /** Horizontal rule: a dark line with a white line under it, like vanilla separators. */
    public static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, UiColors.SLOT);
        g.fill(x, y + 1, x + width, y + 2, UiColors.BORDER_STRONG);
    }

    /** Vertical rule, used between the list column and the inspector. */
    public static void ruleDown(GuiGraphics g, int x, int y, int height) {
        g.fill(x, y, x + 1, y + height, UiColors.SLOT);
        g.fill(x + 1, y, x + 2, y + height, UiColors.BORDER_STRONG);
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
