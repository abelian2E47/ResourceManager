package com.abelian.client.ui;

/**
 * Two surfaces and one accent: dark chrome for the app bars, vanilla-light panels for content, green for
 * every selected or decisive state. Kept in one place so no screen invents its own grey.
 */
public final class UiColors {
    /** Toolbar and status bar fill; the darkest surface in the window. */
    public static final int CHROME = 0xFF2B2F36;
    /** Sunken well on the chrome, used by the search field and disabled chrome buttons. */
    public static final int CHROME_WELL = 0xFF23272D;
    public static final int CHROME_RAISED = 0xFF3A4048, CHROME_RAISED_HOVER = 0xFF4A515B,
            CHROME_RAISED_PRESSED = 0xFF31373E;
    public static final int CHROME_EDGE_LIGHT = 0xFF4A515B, CHROME_EDGE_DARK = 0xFF1B1E23;
    public static final int CHROME_TEXT = 0xFFE8EAED, CHROME_MUTED = 0xFF98A0AA;

    /** Page and panel fill. */
    public static final int CONTAINER = 0xFFC6C6C6;
    /** Sunken slot and row fill. */
    public static final int SLOT = 0xFF8B8B8B;
    /** Body text on the light container. */
    public static final int INK = 0xFF1C1C1C;
    /** Quieter captions and hints; still 4.5:1 on both container and slot. */
    public static final int TEXT_MUTED = 0xFF4A4A4A;
    /** Labels on buttons and other filled controls. */
    public static final int TEXT = 0xFFFFFFFF;
    /** Disabled labels; the state is carried by the flat fill, not by legibility. */
    public static final int TEXT_OFF = 0xFF6B6B6B;

    /** Selection, pressed toggles, slider travel and the small section tick. */
    public static final int ACCENT = 0xFF2E7D4F;
    public static final int ACCENT_HOVER = 0xFF3A9660, ACCENT_PRESSED = 0xFF255F3D;
    /** Accent readable on the dark chrome. */
    public static final int ACCENT_LIGHT = 0xFF7FD3A0;

    /** Selected list row and the text that sits on it. */
    public static final int SELECTED = 0xFF2F5842;
    /** Hover fill for list rows, one step lighter than the slot. */
    public static final int HOVER = 0xFFB2B2B2;
    public static final int BORDER = 0xFF373737, BORDER_STRONG = 0xFFFFFFFF;
    public static final int CONTROL = 0xFF7A7A7A, CONTROL_HOVER = 0xFF8E8E8E, CONTROL_PRESSED = 0xFF6A6A6A;
    public static final int CONTROL_ON = ACCENT, CONTROL_OFF = 0xFF8B8B8B;
    /** Slider groove, the part left of the handle, and the handle itself. */
    public static final int GROOVE = 0xFF707070, GROOVE_FILL = ACCENT, HANDLE = 0xFFEFEFEF;
    /** Drop shadow behind the content panel. */
    public static final int SHADOW = 0x3A000000;

    private UiColors() {}
}