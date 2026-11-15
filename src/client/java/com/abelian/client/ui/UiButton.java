package com.abelian.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * One button class, four surfaces: the chrome bar, the light panel, the tab row and the primary action.
 * The style only changes paint, never geometry, so a layout can restyle a control in place.
 */
public final class UiButton extends AbstractWidget {
    public enum Style {
        /** Grey raised control on a light panel. */
        PANEL,
        /** Dark raised control on the app chrome. */
        CHROME,
        /** Toggle group member; the active one is filled with the accent. */
        TAB,
        /** The decisive action of the page; accent filled. */
        PRIMARY
    }

    private final Font font;
    private final Runnable action;
    private Style style = Style.PANEL;
    private boolean toggled;
    private boolean pressed;
    private String tooltip;

    public UiButton(
            Font font, int x, int y, int width, int height, Component label, Runnable action) {
        super(x, y, width, height, label);
        this.font = font;
        this.action = action;
    }

    public UiButton setStyle(Style value) {
        style = value == null ? Style.PANEL : value;
        return this;
    }

    public Style style() {
        return style;
    }

    public UiButton setToggled(boolean value) {
        toggled = value;
        return this;
    }

    public UiButton setTooltip(String value) {
        tooltip = value;
        return this;
    }

    public String tooltip() {
        return tooltip;
    }

    protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        var r = new Ui.Rect(getX(), getY(), getWidth(), getHeight());
        boolean hot = isHoveredOrFocused();
        paint(g, r, hot);
        String label = Ui.trim(font, getMessage().getString(), getWidth() - 6);
        boolean bold = style == Style.TAB && toggled;
        Component drawn = bold
                ? Component.literal(label).withStyle(ChatFormatting.BOLD)
                : Component.literal(label);
        g.drawString(
                font,
                drawn,
                getX() + (getWidth() - font.width(label)) / 2,
                getY() + (getHeight() - 8) / 2 + (pressed && active ? 1 : 0),
                labelColor(),
                false);
    }

    private void paint(GuiGraphics g, Ui.Rect r, boolean hot) {
        switch (style) {
            case CHROME -> {
                int fill = !active
                        ? UiColors.CHROME_WELL
                        : pressed ? UiColors.CHROME_RAISED_PRESSED
                                : hot ? UiColors.CHROME_RAISED_HOVER : UiColors.CHROME_RAISED;
                Ui.bevelStyled(g, r, fill, UiColors.CHROME_EDGE_LIGHT, UiColors.CHROME_EDGE_DARK);
                if (active && hot) Ui.border(g, r, UiColors.CHROME_TEXT);
            }
            case PRIMARY -> {
                int fill = !active
                        ? UiColors.CONTROL_OFF
                        : pressed ? UiColors.ACCENT_PRESSED
                                : hot ? UiColors.ACCENT_HOVER : UiColors.ACCENT;
                Ui.bevelStyled(g, r, fill, UiColors.ACCENT_HOVER, UiColors.ACCENT_PRESSED);
                if (active && hot) Ui.border(g, r, UiColors.TEXT);
            }
            case TAB -> {
                int fill = toggled
                        ? UiColors.ACCENT
                        : pressed ? UiColors.CONTROL_PRESSED
                                : hot ? UiColors.CONTROL_HOVER : UiColors.CONTROL;
                Ui.bevel(g, r, fill, false);
                if (toggled) {
                    g.fill(r.x() + 1, r.bottom() - 1, r.right() - 1, r.bottom(), UiColors.ACCENT_PRESSED);
                    Ui.border(g, r, UiColors.ACCENT_PRESSED);
                } else if (hot) Ui.border(g, r, UiColors.TEXT);
            }
            default -> {
                int fill =
                        !active
                                ? UiColors.CONTROL_OFF
                                : pressed ? UiColors.CONTROL_PRESSED
                                        : hot ? UiColors.CONTROL_HOVER : UiColors.CONTROL;
                Ui.bevel(g, r, fill, false);
                if (active && hot) Ui.border(g, r, UiColors.TEXT);
            }
        }
    }

    private int labelColor() {
        if (!active) return style == Style.CHROME ? UiColors.CHROME_MUTED : UiColors.TEXT_OFF;
        if (style == Style.CHROME) return UiColors.CHROME_TEXT;
        return UiColors.TEXT;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (handled && active) pressed = true;
        return handled;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = super.mouseReleased(x, y, button);
        pressed = false;
        return handled;
    }

    public void onClick(double x, double y) {
        action.run();
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}