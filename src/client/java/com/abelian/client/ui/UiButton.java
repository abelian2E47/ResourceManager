package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Raised inventory button with one shared palette. */
public final class UiButton extends AbstractWidget {
    private final Font font;
    private final Runnable action;
    private boolean toggled;
    private String tooltip;

    public UiButton(
            Font font, int x, int y, int width, int height, Component label, Runnable action) {
        super(x, y, width, height, label);
        this.font = font;
        this.action = action;
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
        int fill =
                !active
                        ? UiColors.CONTROL_OFF
                        : toggled
                                ? UiColors.CONTROL_ON
                                : isHoveredOrFocused() ? UiColors.CONTROL_HOVER : UiColors.CONTROL;
        var r = new Ui.Rect(getX(), getY(), getWidth(), getHeight());
        Ui.bevel(g, r, fill, false);
        if (active && isHoveredOrFocused()) Ui.border(g, r, UiColors.TEXT);
        String label = Ui.trim(font, getMessage().getString(), getWidth() - 6);
        g.drawString(
                font,
                label,
                getX() + (getWidth() - font.width(label)) / 2,
                getY() + (getHeight() - 8) / 2,
                active ? UiColors.TEXT : UiColors.TEXT_OFF,
                false);
    }

    public void onClick(double x, double y) {
        action.run();
    }

    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
