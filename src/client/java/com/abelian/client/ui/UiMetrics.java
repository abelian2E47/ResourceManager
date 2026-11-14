package com.abelian.client.ui;

/**
 * Shared spacing and sizing scale. Pages lay themselves out with these numbers so that every screen
 * keeps the same rhythm, and a single change here re-tunes the whole interface.
 */
public final class UiMetrics {
    /** Outer window padding. */
    public static final int PAD = 8;
    /** Gap between sibling panels. */
    public static final int GAP = 6;
    /** Padding inside a panel. */
    public static final int INSET = 6;
    /** Height of a page header strip. */
    public static final int HEADER = 18;
    /** Height of the bottom status strip. */
    public static final int STATUS = 14;
    /** Height of the tab strip that switches pages. */
    public static final int TAB_STRIP = 22;
    /** Height of a standard button. */
    public static final int BUTTON_H = 18;
    /** Height of a small button. */
    public static final int BUTTON_SMALL_H = 14;
    /** Height of one tab in a tab strip. */
    public static final int TAB_H = 20;
    /** Height of a section header (a collapsible group inside a panel). */
    public static final int SECTION_H = 14;
    /** Height of a two-line list row. */
    public static final int ROW_H_TALL = 22;
    /** Height of a slider row. */
    public static final int SLIDER_H = 18;
    /** Height of a list row (dense). */
    public static final int ROW_H = 12;
    /** Height of a list row (comfortable, used by the layer stack and the browser). */
    public static final int ROW_H_COMFY = 16;
    /** Height of a single line text field. */
    public static final int FIELD_H = 16;
    /** Width of the scrollbar inside a scroll list. */
    public static final int SCROLLBAR_W = 6;
    /** Width of the left navigation rail. */
    public static final int RAIL_W = 96;
    /** Width of a two-panel page's left list when the window is wide. */
    public static final int SPLIT_W = 240;
    /** Minimum window width at which the side panel of a page is shown. */
    public static final int SPLIT_MIN_WIDTH = 520;
    /** Smallest usable page width; below this panels start folding. */
    public static final int COMPACT_WIDTH = 420;

    private UiMetrics() {
    }
}
