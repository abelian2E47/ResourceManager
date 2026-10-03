package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

/** Flat, dark button that matches the panel styling (vanilla buttons are too tall and too bright here). */
public class UiButton extends AbstractWidget {
    private final Font font;
    private final Runnable action;
    private boolean toggled;

    public UiButton(Font font, int x, int y, int width, int height, Component message, Runnable action) {
        super(x, y, width, height, message);
        this.font = font;
        this.action = action;
    }

    public UiButton setToggled(boolean toggled) {
        this.toggled = toggled;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int background;
        if (!this.active) {
            background = Ui.BUTTON_OFF;
        } else if (this.toggled) {
            background = Ui.BUTTON_ON;
        } else if (this.isHoveredOrFocused()) {
            background = Ui.BUTTON_HOVER;
        } else {
            background = Ui.BUTTON;
        }
        Ui.panel(graphics, new Ui.Rect(getX(), getY(), getWidth(), getHeight()), background, Ui.BORDER);
        int color = this.active ? (this.toggled ? Ui.TEXT : Ui.TEXT) : Ui.OFF;
        String label = Ui.trim(this.font, getMessage().getString(), getWidth() - 6);
        graphics.drawString(this.font, label, getX() + (getWidth() - this.font.width(label)) / 2,
                getY() + (getHeight() - 8) / 2, color, false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
