package com.abelian.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The dark single line text input used by every page.
 *
 * <p>It wraps a vanilla {@link EditBox} instead of extending it: the screen has to register the inner
 * widget to get real focus, cursor and drag behaviour, while the page still wants the panel look and a
 * place to park the hint. Drawing is split accordingly - this class paints the sunken well and the
 * border, then delegates the text and cursor to the widget, which keeps Minecraft's own editing
 * behaviour intact (a hand rolled field would have to reimplement it badly).
 *
 * <pre>{@code
 * this.filter = new UiField(font, 64, this::onFilterChanged, "Filter resources");
 * host.add(this.filter.widget());
 *
 * public void layout() {
 *     Ui.Rect body = this.box.body();
 *     this.filter.place(body.x(), body.y(), body.w(), UiMetrics.FIELD_H);
 * }
 *
 * public void render(GuiGraphics graphics, ...) {
 *     this.filter.render(graphics);
 * }
 * }</pre>
 */
public final class UiField {
    private final Font font;
    private final EditBox widget;
    private String hint;

    /** Creates the field; it starts hidden so the screen decides where it goes on the first layout. */
    public UiField(Font font, int maxLength, Consumer<String> responder, String hint) {
        this.font = font;
        this.widget = new EditBox(font, 0, 0, 1, UiMetrics.FIELD_H, Component.empty());
        this.widget.setMaxLength(Math.max(1, maxLength));
        this.widget.setBordered(false);
        this.widget.setTextColor(UiColors.INK);
        this.widget.setTextColorUneditable(UiColors.TEXT_OFF);
        this.widget.setCanLoseFocus(true);
        this.widget.setVisible(false);
        if (responder != null) {
            this.widget.setResponder(responder);
        }
        this.hint = hint;
        // No setSuggestion here: render() paints the hint itself, and the widget's own suggestion
        // would be drawn on top of it, leaving two overlapping copies of the same text.
    }

    /** The vanilla widget the screen has to register and forward focus to. */
    public EditBox widget() {
        return this.widget;
    }

    public String value() {
        return this.widget.getValue();
    }

    /** Sets the text without firing the responder. */
    public void setValue(String value) {
        this.widget.setValue(value == null ? "" : value);
    }

    /** Moves and shows the field in one call; a collapsed size hides the field entirely. */
    public void place(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            this.widget.setVisible(false);
            return;
        }
        this.widget.setPosition(x, y);
        this.widget.setSize(width, height);
        this.widget.setVisible(true);
    }

    /** Paints the sunken fill, the border and the hint, then the widget's own text and cursor. */
    public void render(GuiGraphics graphics) {
        if (!this.widget.isVisible()) {
            return;
        }
        int x = this.widget.getX();
        int y = this.widget.getY();
        int width = this.widget.getWidth();
        int height = this.widget.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean focused = this.widget.isFocused();
        int border = focused ? UiColors.BORDER_STRONG : UiColors.BORDER;
        Ui.panel(graphics, new Ui.Rect(x, y, width, height), UiColors.SUNKEN, border);
        if (this.widget.isActive() && !focused && this.widget.getValue().isEmpty() && this.hint != null
                && !this.hint.isEmpty()) {
            int room = Math.max(0, width - 8);
            graphics.drawString(this.font, Ui.trim(this.font, this.hint, room), x + 4, y + (height - 8) / 2,
                    UiColors.TEXT_MUTED, false);
        }
        this.widget.render(graphics, 0, 0, 0.0F);
    }

    /** Replaces the hint shown while the field is empty and unfocused. */
    public void setHint(String hint) {
        this.hint = hint;
    }

    /** Enables or disables the field; a disabled field draws muted and ignores clicks. */
    public void setActive(boolean active) {
        this.widget.active = active;
        this.widget.setEditable(active);
    }
}
