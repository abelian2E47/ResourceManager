package com.abelian.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Base class for the scrollable panels: clipped row rendering, mouse wheel, draggable scrollbar and
 * row hit testing. Subclasses only describe their rows.
 */
public abstract class ScrollList extends AbstractWidget implements DragTarget {
    protected static final int SCROLLBAR_WIDTH = 6;

    private final Font font;
    private final int rowHeight;
    protected double scroll;
    private boolean draggingThumb;
    private int hoveredRow = -1;

    protected ScrollList(Font font, int x, int y, int width, int height, int rowHeight) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.rowHeight = rowHeight;
    }

    protected Font font() {
        return this.font;
    }

    protected abstract int rowCount();

    protected abstract void renderRow(GuiGraphics graphics, int index, Ui.Rect rowRect, boolean hovered,
            boolean selected);

    protected abstract boolean isSelected(int index);

    protected abstract void clickRow(int index, double mouseX, double mouseY);

    /** Tooltip for a row, or null. */
    public String tooltipAt(int index) {
        return null;
    }

    public int hoveredRow() {
        return this.hoveredRow;
    }

    public int rowHeightValue() {
        return this.rowHeight;
    }

    protected int contentHeight() {
        return rowCount() * this.rowHeight;
    }

    protected double maxScroll() {
        return Math.max(0, contentHeight() - getHeight());
    }

    public boolean scrollbarVisible() {
        return contentHeight() > getHeight() && rowCount() > 0;
    }

    protected int listRight() {
        return getX() + getWidth() - (scrollbarVisible() ? SCROLLBAR_WIDTH : 0);
    }

    protected int rowTop(int index) {
        return getY() + index * this.rowHeight - (int) Math.round(this.scroll);
    }

    protected int rowIndexAt(double mouseY) {
        int index = (int) Math.floor((mouseY - getY() + this.scroll) / this.rowHeight);
        return index >= 0 && index < rowCount() ? index : -1;
    }

    public void clampScroll() {
        this.scroll = Ui.clamp(this.scroll, 0, maxScroll());
    }

    /** Scrolls so that the given row is fully visible. */
    public void scrollToRow(int index) {
        if (index < 0) {
            return;
        }
        int top = rowTop(index);
        if (top < getY()) {
            this.scroll = Ui.clamp(this.scroll + (top - getY()), 0, maxScroll());
        } else if (top + this.rowHeight > getBottom()) {
            this.scroll = Ui.clamp(this.scroll + (top + this.rowHeight - getBottom()), 0, maxScroll());
        }
        clampScroll();
    }

    public void resetScroll() {
        this.scroll = 0;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        clampScroll();
        this.hoveredRow = isMouseOver(mouseX, mouseY) ? rowIndexAt(mouseY) : -1;
        int right = listRight();
        if (right > getX() && getBottom() > getY()) {
            graphics.enableScissor(getX(), getY(), right, getBottom());
            int first = Math.max(0, (int) Math.floor(this.scroll / this.rowHeight));
            for (int index = first; index < rowCount(); index++) {
                int top = rowTop(index);
                if (top >= getBottom()) {
                    break;
                }
                renderRow(graphics, index, new Ui.Rect(getX(), top, right - getX(), this.rowHeight),
                        index == this.hoveredRow, isSelected(index));
            }
            graphics.disableScissor();
        }
        if (scrollbarVisible()) {
            drawScrollbar(graphics, mouseX, mouseY);
        }
    }

    private int thumbHeight() {
        int height = getHeight();
        return Math.max(10, (int) (height * (double) height / contentHeight()));
    }

    private int thumbTop(int thumbHeight) {
        double max = maxScroll();
        double ratio = max <= 0 ? 0 : this.scroll / max;
        return getY() + (int) Math.round((getHeight() - thumbHeight) * ratio);
    }

    private int scrollbarLeft() {
        return getX() + getWidth() - SCROLLBAR_WIDTH;
    }

    private void drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = scrollbarLeft();
        graphics.fill(left, getY(), left + SCROLLBAR_WIDTH - 1, getBottom(), Ui.SCROLL_TRACK);
        int thumbHeight = thumbHeight();
        int thumbTop = thumbTop(thumbHeight);
        boolean hovered = this.draggingThumb
                || (mouseX >= left && mouseY >= thumbTop && mouseY < thumbTop + thumbHeight);
        graphics.fill(left, thumbTop, left + SCROLLBAR_WIDTH - 1, thumbTop + thumbHeight,
                hovered ? Ui.SCROLL_THUMB_HOVER : Ui.SCROLL_THUMB);
    }

    private void setScrollFromMouse(double mouseY) {
        int thumbHeight = thumbHeight();
        double track = Math.max(1, getHeight() - thumbHeight);
        double ratio = Ui.clamp((mouseY - getY() - thumbHeight / 2.0) / track, 0.0, 1.0);
        this.scroll = ratio * maxScroll();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.active || !this.visible || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        if (scrollbarVisible() && mouseX >= scrollbarLeft()) {
            this.draggingThumb = true;
            setScrollFromMouse(mouseY);
            return true;
        }
        int index = rowIndexAt(mouseY);
        if (index >= 0) {
            clickRow(index, mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() <= 0) {
            return false;
        }
        this.scroll = Ui.clamp(this.scroll - Math.signum(verticalAmount) * this.rowHeight * 3, 0, maxScroll());
        return true;
    }

    @Override
    public boolean isDragging() {
        return this.draggingThumb;
    }

    @Override
    public void dragTo(double mouseX, double mouseY) {
        setScrollFromMouse(mouseY);
    }

    @Override
    public void endDrag() {
        this.draggingThumb = false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
