package com.abelian.client.ui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Translation keys of one {@code lang} file, filtered by the inspector's key box. */
public class TextKeyList extends ScrollList {
    public interface Host {
        /** Keys to show, already filtered and sorted. */
        List<String> textKeys();

        String selectedTextKey();

        void selectTextKey(String key);

        /** True when the key carries a value edited in the GUI. */
        boolean textOverridden(String key);
    }

    private final Host host;

    public TextKeyList(Font font, int x, int y, int width, int height, Host host) {
        super(font, x, y, width, height, Ui.ROW_HEIGHT);
        this.host = host;
    }

    @Override
    protected int rowCount() {
        return this.host.textKeys().size();
    }

    private String keyAt(int index) {
        List<String> keys = this.host.textKeys();
        return index >= 0 && index < keys.size() ? keys.get(index) : null;
    }

    @Override
    protected boolean isSelected(int index) {
        String key = keyAt(index);
        return key != null && key.equals(this.host.selectedTextKey());
    }

    @Override
    public String tooltipAt(int index) {
        return keyAt(index);
    }

    @Override
    protected void renderRow(GuiGraphics graphics, int index, Ui.Rect rect, boolean hovered, boolean selected) {
        String key = keyAt(index);
        if (key == null) {
            return;
        }
        if (selected) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom() - 1, Ui.ROW_SELECTED);
        } else if (hovered) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom() - 1, Ui.ROW_HOVER);
        }
        int textY = rect.y() + (rect.h() - 8) / 2;
        boolean overridden = this.host.textOverridden(key);
        String badge = overridden ? "\u270e" : null;
        int badgeWidth = badge == null ? 0 : font().width(badge) + 4;
        String name = Ui.trim(font(), key, rect.w() - badgeWidth - 7);
        graphics.drawString(font(), name, rect.x() + 3, textY, overridden ? Ui.ACCENT : Ui.MUTED, false);
        if (badge != null) {
            graphics.drawString(font(), badge, rect.right() - badgeWidth - 2, textY, Ui.ACCENT, false);
        }
    }

    @Override
    protected void clickRow(int index, double mouseX, double mouseY) {
        String key = keyAt(index);
        if (key != null) {
            this.host.selectTextKey(key);
        }
    }
}
