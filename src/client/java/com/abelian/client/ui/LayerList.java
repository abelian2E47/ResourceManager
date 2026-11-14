package com.abelian.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Consumer;

/** A pack picker: selection previews; the explicit Apply button changes the game. */
public final class LayerList extends ScrollList {
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

    public int layerCount() {
        return packs.size();
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
        Ui.bevel(g, r, selected ? UiColors.SELECTED : hover ? UiColors.HOVER : UiColors.SLOT, true);
        String id = packs.get(i);
        String label = (id.equals(applied) ? "* " : "") + id;
        g.drawString(
                font(),
                Ui.trim(font(), label, r.w() - 8),
                r.x() + 4,
                r.y() + 6,
                selected ? UiColors.TEXT : UiColors.INK,
                false);
    }
}
