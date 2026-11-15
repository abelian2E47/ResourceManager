package com.abelian.client.ui;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Horizontal value slider: a label, a sunken groove with a raised handle and the numeric value.
 * Dragging works through {@link DragTarget} so the owning screen keeps the mouse capture.
 *
 * <p>The track can be bent with {@link #setCurve(double)}, which is what a 0..16 volume multiplier
 * needs: most of the travel then covers the range where a volume is actually judged. {@link #setCenter}
 * only draws a tick so a neutral point such as pitch 1.0 stays visible.
 */
public class UiSlider extends AbstractWidget implements DragTarget {
    public interface Value {
        double get();

        void set(double value);
    }

    private static final int TRACK_HEIGHT = 6;
    private static final int HANDLE_WIDTH = 6;
    private static final int HANDLE_HEIGHT = 14;
    private static final int VALUE_WIDTH = 30;
    private static final int LABEL_GAP = 6;
    private static final int MIN_WIDTH_FOR_LABEL = 92;
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
    private double curve = 1.0;
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

    /** Bends the track so most of its travel covers the low end of a wide range. */
    public UiSlider setCurve(double exponent) {
        this.curve = exponent >= 1.0 && !Double.isNaN(exponent) ? exponent : 1.0;
        return this;
    }

    /** Draws a tick at {@code value}, so a neutral point such as pitch 1.0 is visible. */
    public UiSlider setCenter(double value) {
        this.center = value;
        return this;
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

    private static double clean(double value) {
        return Math.round(value * 1.0E6) / 1.0E6;
    }

    /** Pushes {@code value} into the model and the local mirror. */
    private void apply(double value) {
        this.current = clean(clampValue(value));
        if (this.value != null) {
            this.value.set(this.current);
        }
    }

    /** Re-reads the model so a slider follows a value changed elsewhere. */
    private void pull() {
        if (this.value != null) {
            this.current = clean(clampValue(this.value.get()));
        }
    }

    /** Label as drawn; a narrow control drops it and keeps the value. */
    private String shownLabel() {
        return getWidth() >= MIN_WIDTH_FOR_LABEL ? this.label : "";
    }

    private int labelWidth() {
        String text = shownLabel();
        return text.isEmpty() ? 0 : this.font.width(text) + LABEL_GAP;
    }

    private int trackLeft() {
        return getX() + 2 + labelWidth();
    }

    private int trackWidth() {
        return Math.max(1, getWidth() - 4 - labelWidth() - VALUE_WIDTH - 4);
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
        if (!label.isEmpty()) {
            graphics.drawString(
                    this.font,
                    Ui.trim(this.font, label, Math.max(0, trackLeft() - getX() - LABEL_GAP)),
                    getX() + 2,
                    textY,
                    UiColors.TEXT_MUTED,
                    false);
        }
        int trackX = trackLeft();
        int trackW = trackWidth();
        int trackY = getY() + (height - TRACK_HEIGHT) / 2;
        var groove = new Ui.Rect(trackX, trackY, trackW, TRACK_HEIGHT);
        Ui.bevel(graphics, groove, UiColors.GROOVE, true);
        int handleX = Ui.clamp(handleCenter(trackX, trackW) - HANDLE_WIDTH / 2, trackX - 2, trackX + trackW - HANDLE_WIDTH + 2);
        graphics.fill(trackX + 1, trackY + 1, handleX + HANDLE_WIDTH / 2, trackY + TRACK_HEIGHT - 1, UiColors.GROOVE_FILL);
        for (int i = 1; i < 4; i++) {
            int tick = trackX + Math.round(trackW * i / 4.0F);
            graphics.fill(tick, trackY + 1, tick + 1, trackY + TRACK_HEIGHT - 1, UiColors.GROOVE);
        }
        if (this.center > this.min && this.center < this.max) {
            int tick = trackX + (int) Math.round(ratio(this.center) * (trackW - 1));
            graphics.fill(tick, trackY - 1, tick + 1, trackY + TRACK_HEIGHT + 1, UiColors.TEXT_MUTED);
        }
        var handle = new Ui.Rect(handleX, getY() + (height - HANDLE_HEIGHT) / 2, HANDLE_WIDTH, HANDLE_HEIGHT);
        Ui.bevel(graphics, handle, this.active ? (this.dragging || isHoveredOrFocused() ? UiColors.TEXT : UiColors.HANDLE)
                : UiColors.CONTROL_OFF, false);
        String text = formatted();
        graphics.drawString(
                this.font,
                text,
                getX() + width - 2 - this.font.width(text),
                textY,
                this.active ? UiColors.INK : UiColors.TEXT_OFF,
                false);
    }

    private int handleCenter(int trackX, int trackW) {
        return trackX + (int) Math.round(ratio(this.current) * (trackW - 1));
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
        int trackW = trackWidth();
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
