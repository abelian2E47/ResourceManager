package com.abelian.client.ui;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Horizontal value slider with a label on the left and the numeric value on the right. Dragging works
 * through {@link DragTarget} so the owning screen stays in control of the mouse capture.
 *
 * <p>Sliders cover ranges as different as a 0..16 volume multiplier and a 0.5..2 pitch, so the step
 * is derived from the range unless the caller sets one, and the label gives way to the value when the
 * control is too narrow to hold both. {@link #setSnap(double)} quantises the value itself, which is
 * what semitone snapping needs, {@link #setCurve(double)} spends most of the travel on the low end of
 * a wide range, and {@link #setCenter(double)} only draws a tick so a neutral point such as pitch 1.0
 * stays visible.
 *
 * <pre>{@code
 * UiSlider pitch = new UiSlider(font, 0, 0, 120, UiMetrics.SLIDER_H, "Pitch", 0.5, 2.0, "%.2f",
 *         this.holder::getPitch, this.holder::setPitch).setSnap(0.05).setCenter(1.0);
 * }</pre>
 */
public class UiSlider extends AbstractWidget implements DragTarget {
    public interface Value {
        double get();

        void set(double value);
    }

    private static final int TRACK_HEIGHT = 4;
    private static final int HANDLE_WIDTH = 4;
    private static final int VALUE_PAD = 3;
    /** Gap between the label and the track, on top of the label width {@link #trackLeft()} reserves. */
    private static final int LABEL_GAP = 4;
    private static final int MIN_WIDTH_FOR_LABEL = 90;
    private static final double MAX_DERIVED_STEP_FRACTION = 0.05;
    private static final double[] NICE_STEPS = { 0.001, 0.002, 0.005, 0.01, 0.02, 0.025, 0.05, 0.1, 0.2, 0.25, 0.5, 1.0,
            2.0, 5.0, 10.0, 25.0, 50.0, 100.0 };

    private final Font font;
    private final String label;
    private final Value value;
    private final double min;
    private final double max;
    private final String format;
    private boolean dragging;
    private double current;
    private double step;
    private double snap;
    private double curve = 1.0;
    private boolean hasCenter;
    private double center;

    public UiSlider(Font font, int x, int y, int width, int height, String label, double min, double max, String format,
            Value value) {
        super(x, y, width, height, Component.literal(label));
        this.font = font;
        this.label = label == null ? "" : label;
        this.value = value;
        this.min = min;
        this.max = max > min ? max : min + 1.0;
        this.format = format;
        this.current = clampValue(value == null ? min : value.get());
        this.step = deriveStep(this.max - this.min);
    }

    /** Step used by the arrow keys and the mouse wheel; defaults to a value derived from the range. */
    public UiSlider step(double step) {
        if (step > 0.0 && !Double.isNaN(step)) {
            this.step = step;
        }
        return this;
    }

    /** When positive, every change snaps to a multiple of {@code snap} (for example 1.0 for semitones). */
    public UiSlider setSnap(double snap) {
        this.snap = snap > 0.0 && !Double.isNaN(snap) ? snap : 0.0;
        return this;
    }

    /**
     * Bends the track so most of its travel covers the low end of a wide range, which is where a
     * volume multiplier is judged: 3 puts 1.0 about 40% of the way along a 0..16 slider. Values below
     * 1 keep the linear mapping.
     */
    public UiSlider setCurve(double exponent) {
        this.curve = exponent >= 1.0 && !Double.isNaN(exponent) ? exponent : 1.0;
        return this;
    }

    /** Draws a tick at {@code value}, so a neutral point such as pitch 1.0 is visible. */
    public UiSlider setCenter(double value) {
        this.center = value;
        this.hasCenter = true;
        return this;
    }

    /** The current value as shown by the slider. */
    public double value() {
        return this.current;
    }

    /** Sets the value, applying the snap and the range clamp. */
    public void setValue(double value) {
        apply(value);
    }

    private static double deriveStep(double range) {
        double wanted = range / 100.0;
        double cap = range * MAX_DERIVED_STEP_FRACTION;
        double best = NICE_STEPS[0];
        for (double candidate : NICE_STEPS) {
            best = candidate;
            if (candidate >= wanted) {
                break;
            }
        }
        return Math.min(best, cap);
    }

    private double clampValue(double value) {
        return Ui.clamp(value, this.min, this.max);
    }

    private double quantise(double value) {
        double limited = clampValue(value);
        if (this.snap > 0.0) {
            limited = clampValue(Math.round(limited / this.snap) * this.snap);
        }
        return clean(limited);
    }

    private static double clean(double value) {
        return Math.round(value * 1.0E6) / 1.0E6;
    }

    /** Pushes {@code value} into the model and the local mirror. */
    private void apply(double value) {
        this.current = quantise(value);
        if (this.value != null) {
            this.value.set(this.current);
        }
    }

    /** Re-reads the model so a slider follows a value changed elsewhere. */
    private void pull() {
        if (this.value != null) {
            this.current = quantise(this.value.get());
        }
    }

    private int contentWidth() {
        return Math.max(0, getWidth() - 4);
    }

    /** Label and value as they will be drawn, with the label dropped on a narrow control. */
    private String shownLabel() {
        return getWidth() >= MIN_WIDTH_FOR_LABEL ? this.label : "";
    }

    private String shownValue() {
        String text = formatted();
        int room = getWidth() - (shownLabel().isEmpty() ? 6 : 0) - 6;
        return this.font.width(text) <= room ? text : Ui.trim(this.font, text, Math.max(0, room));
    }

    private int trackLeft() {
        String label = shownLabel();
        return getX() + 2 + (label.isEmpty() ? 0 : this.font.width(label) + 6);
    }

    private int trackWidth() {
        String label = shownLabel();
        String value = shownValue();
        int labelWidth = label.isEmpty() ? 0 : this.font.width(label) + 6;
        int valueWidth = value.isEmpty() ? 0 : this.font.width(value) + VALUE_PAD;
        int room = contentWidth() - labelWidth - valueWidth;
        int minimum = Math.min(20, Math.max(1, contentWidth() - labelWidth - 1));
        return Math.max(Math.min(20, minimum), Math.min(room, contentWidth() - labelWidth));
    }

    private String formatted() {
        try {
            return String.format(Locale.ROOT, this.format == null ? "%.2f" : this.format, this.current);
        } catch (RuntimeException error) {
            return Double.toString(this.current);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        pull();
        int textY = getY() + (height - 8) / 2;
        String label = shownLabel();
        String text = shownValue();
        if (!label.isEmpty()) {
            int room = Math.max(0, trackLeft() - getX() - LABEL_GAP);
            graphics.drawString(this.font, Ui.trim(this.font, label, room), getX() + 2, textY, UiColors.TEXT_MUTED,
                    false);
        }
        int trackX = trackLeft();
        int trackW = trackWidth();
        int trackY = getY() + (height - TRACK_HEIGHT) / 2;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + TRACK_HEIGHT, UiColors.SUNKEN);
        if (this.hasCenter) {
            int tickX = tickX(trackX, trackW);
            graphics.fill(tickX, trackY - 1, tickX + 1, trackY + TRACK_HEIGHT + 1, UiColors.TEXT_MUTED);
        }
        int handleCenter = Ui.clamp(handleCenter(trackX, trackW), trackX, trackX + trackW - 1);
        graphics.fill(trackX, trackY + 1, Math.max(trackX, handleCenter), trackY + TRACK_HEIGHT - 1,
                this.active ? UiColors.ACCENT_DARK : UiColors.CONTROL_OFF);
        int handleX = Ui.clamp(handleCenter - HANDLE_WIDTH / 2, trackX - 1, trackX + trackW - HANDLE_WIDTH + 1);
        int handleColor = !this.active ? UiColors.TEXT_OFF
                : this.dragging || this.isHoveredOrFocused() ? UiColors.TEXT : UiColors.ACCENT;
        graphics.fill(handleX, getY() + 1, handleX + HANDLE_WIDTH, getY() + height - 1, handleColor);
        graphics.drawString(this.font, text, getX() + width - VALUE_PAD - this.font.width(text), textY,
                this.active ? UiColors.TEXT : UiColors.TEXT_OFF, false);
    }

    private int handleCenter(int trackX, int trackW) {
        return trackX + (int) Math.round(ratio(this.current) * (trackW - 1));
    }

    private int tickX(int trackX, int trackW) {
        return trackX + (int) Math.round(ratio(this.center) * (trackW - 1));
    }

    /** Where {@code value} sits on the track, from 0 to 1, following {@link #setCurve(double)}. */
    private double ratio(double value) {
        double normal = (clampValue(value) - this.min) / (this.max - this.min);
        return Ui.clamp(this.curve == 1.0 ? normal : Math.pow(normal, 1.0 / this.curve), 0.0, 1.0);
    }

    /** The value at track position {@code ratio}, the inverse of {@link #ratio(double)}. */
    private double valueAt(double ratio) {
        double normal = this.curve == 1.0 ? ratio : Math.pow(Ui.clamp(ratio, 0.0, 1.0), this.curve);
        return this.min + normal * (this.max - this.min);
    }

    private void setFromMouse(double mouseX) {
        int trackX = trackLeft();
        int trackW = Math.max(1, trackWidth());
        apply(valueAt((mouseX - trackX) / (double) (trackW - 1)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && this.active) {
            this.dragging = true;
        }
        return handled;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.dragging = true;
        setFromMouse(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        setFromMouse(mouseX);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        this.dragging = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.active || !isMouseOver(mouseX, mouseY) || verticalAmount == 0.0) {
            return false;
        }
        apply(this.current + Math.signum(verticalAmount) * this.step);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.active && isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN) {
                apply(this.current - this.step);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP) {
                apply(this.current + this.step);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
