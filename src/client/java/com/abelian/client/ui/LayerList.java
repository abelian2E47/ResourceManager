package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Consumer;

/** A pack picker: selection previews; the explicit Apply button changes the game. */
public final class LayerList extends ScrollList {
    private static final int ROW_GAP = 2;

    private List<String> packs = List.of();
    private String selected = "", applied = "";
    private final Consumer<String> select;

    public LayerList(Font font, Consumer<String> select) {
        super(font, 0, 0, 1, 1, 20);
        this.select = select;
    }

    public void setLayers(List<String> packs, String selected, String applied) {
        this.packs = packs;
        this.selected = selected;
        this.applied = applied;
    }

    public String layerAt(int index) {
        return index >= 0 && index < packs.size() ? packs.get(index) : null;
    }

    protected int rowCount() {
        return packs.size();
    }

    protected boolean isSelected(int index) {
        return packs.get(index).equals(selected);
    }

    protected void clickRow(int index, double x, double y) {
        select.accept(packs.get(index));
    }

    public String tooltipAt(int index) {
        return layerAt(index);
    }

    protected void renderRow(GuiGraphics g, int i, Ui.Rect r, boolean hover, boolean selected) {
        var row = new Ui.Rect(r.x() + 1, r.y(), r.w() - ROW_GAP - 1, r.h() - ROW_GAP);
        Ui.bevel(g, row, selected ? UiColors.SELECTED : hover ? UiColors.HOVER : UiColors.SLOT, true);
        if (selected) Ui.border(g, row, UiColors.BORDER_STRONG);
        String id = packs.get(i);
        boolean applied = id.equals(this.applied);
        int textX = row.x() + 4;
        if (applied) {
            g.fill(textX, row.y() + 3, textX + 2, row.bottom() - 3,
                    selected ? UiColors.ACCENT_LIGHT : UiColors.ACCENT);
            textX += 5;
        }
        g.drawString(
                font(),
                Ui.trim(font(), id, row.right() - 4 - textX),
                textX,
                row.y() + (row.h() - 8) / 2,
                selected ? UiColors.TEXT : UiColors.INK,
                false);
    }
}
