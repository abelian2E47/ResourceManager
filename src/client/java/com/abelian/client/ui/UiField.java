package com.abelian.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The sunken single line text input used by the toolbar and the name editor.
 *
 * <p>It wraps a vanilla {@link EditBox} instead of extending it: the screen registers the inner widget
 * so it gets real focus, caret and drag behaviour, while this class paints the well and the hint. An
 * unbordered EditBox draws its text at the top of its own box, so {@link #render} shifts the pose down
 * by half the leftover height; that keeps the text vertically centred without moving the hit area.
 */
public final class UiField {
    /** Standard field height; the well is one pixel larger than the text line. */
    public static final int HEIGHT = 18;

    private static final int TEXT_HEIGHT = 8;

    private final Font font;
    private final EditBox widget;
    private final String hint;
    private boolean chrome;

    /** Creates the field; it starts hidden so the screen decides where it goes on the first layout. */
    public UiField(Font font, int maxLength, Consumer<String> responder, String hint) {
        this.font = font;
        this.widget = new EditBox(font, 0, 0, 1, HEIGHT, Component.empty());
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

    /** True for the toolbar field: dark well with light ink instead of the vanilla grey. */
    public UiField setChrome(boolean value) {
        chrome = value;
        this.widget.setTextColor(value ? UiColors.CHROME_TEXT : UiColors.INK);
        this.widget.setTextColorUneditable(value ? UiColors.CHROME_MUTED : UiColors.TEXT_OFF);
        return this;
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

    /** Paints the well, the hint and the widget's own text and caret. */
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
        var box = new Ui.Rect(x, y, width, height);
        if (this.chrome) {
            Ui.bevelStyled(graphics, box, UiColors.CHROME_WELL,
                    UiColors.CHROME_EDGE_DARK, UiColors.CHROME_EDGE_LIGHT);
        } else {
            Ui.bevel(graphics, box, UiColors.SLOT, true);
        }
        if (this.widget.isFocused()) {
            Ui.border(graphics, box, UiColors.ACCENT_LIGHT);
        }
        if (this.widget.getValue().isEmpty() && this.hint != null && !this.hint.isEmpty()) {
            int room = Math.max(0, width - 8);
            graphics.drawString(
                    this.font,
                    Ui.trim(this.font, this.hint, room),
                    x + 4,
                    y + (height - TEXT_HEIGHT) / 2,
                    this.chrome ? UiColors.CHROME_MUTED : UiColors.TEXT_MUTED,
                    false);
        }
        // An unbordered EditBox draws its text at the top of its own box, so the pose is shifted
        // down by the leftover half height; that centres the text without moving the hit area.
        int shift = Math.max(0, (height - TEXT_HEIGHT) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, shift, 0.0F);
        this.widget.render(graphics, 0, 0, 0.0F);
        graphics.pose().popPose();
    }
}
