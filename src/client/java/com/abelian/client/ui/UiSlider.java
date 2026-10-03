package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Horizontal value slider with a label on the left and the numeric value on the right. Dragging works
 * through {@link DragTarget} so the owning screen stays in control of the mouse capture.
 */
public class UiSlider extends AbstractWidget implements DragTarget {
    public interface Value {
        double get();

        void set(double value);
    }

    private static final int TRACK_HEIGHT = 4;
    private static final int HANDLE_WIDTH = 4;

    private final Font font;
    private final String label;
    private final Value value;
    private final double min;
    private final double max;
    private final String format;
    private boolean dragging;

    public UiSlider(Font font, int x, int y, int width, int height, String label, double min, double max, String format,
            Value value) {
        super(x, y, width, height, Component.literal(label));
        this.font = font;
        this.label = label;
        this.min = min;
        this.max = max;
        this.format = format;
        this.value = value;
    }

    private String formatted() {
        return String.format(java.util.Locale.ROOT, this.format, this.value.get());
    }

    private int trackLeft() {
        return getX() + this.font.width(this.label) + 6;
    }

    private int trackWidth() {
        int reserved = this.font.width(this.label) + 6 + this.font.width(formatted()) + 8;
        return Math.max(20, getWidth() - reserved);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(this.font, this.label, getX(), textY, Ui.MUTED, false);

        int trackX = trackLeft();
        int trackW = trackWidth();
        int trackY = getY() + (getHeight() - TRACK_HEIGHT) / 2;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + TRACK_HEIGHT, Ui.BODY);
        Ui.border(graphics, new Ui.Rect(trackX, trackY, trackW, TRACK_HEIGHT), Ui.BORDER);

        double ratio = (this.value.get() - this.min) / (this.max - this.min);
        int filled = trackX + (int) Math.round(Ui.clamp(ratio, 0.0, 1.0) * trackW);
        graphics.fill(trackX + 1, trackY + 1, Math.max(trackX + 1, filled), trackY + TRACK_HEIGHT - 1, Ui.ACCENT);

        int handleX = Ui.clamp(filled - HANDLE_WIDTH / 2, trackX - 1, trackX + trackW - HANDLE_WIDTH + 1);
        int handleColor = this.dragging || this.isHoveredOrFocused() ? Ui.TEXT : Ui.ACCENT;
        graphics.fill(handleX, getY() + 1, handleX + HANDLE_WIDTH, getY() + getHeight() - 1, handleColor);

        String text = formatted();
        graphics.drawString(this.font, text, getX() + getWidth() - this.font.width(text), textY, Ui.TEXT, false);
    }

    private void setFromMouse(double mouseX) {
        int trackX = trackLeft();
        int trackW = trackWidth();
        double ratio = Ui.clamp((mouseX - trackX) / (double) trackW, 0.0, 1.0);
        double raw = this.min + ratio * (this.max - this.min);
        double stepped = Math.round(raw * 100.0) / 100.0;
        this.value.set(Ui.clamp(stepped, this.min, this.max));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.dragging = true;
        setFromMouse(event.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        setFromMouse(event.x());
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        this.dragging = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.value.set(Ui.clamp(this.value.get() + Math.signum(verticalAmount) * 0.05, this.min, this.max));
        return true;
    }

    @Override
    public boolean isDragging() {
        return this.dragging;
    }

    @Override
    public void dragTo(double mouseX, double mouseY) {
        setFromMouse(mouseX);
    }

    @Override
    public void endDrag() {
        this.dragging = false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
