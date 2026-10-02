package com.abelian.client.ui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Sidebar list of every disabled resource, newest state read straight from the config. */
public class DisabledList extends ScrollList {
    public interface Host {
        List<String> disabledKeys();

        void activateDisabledKey(String key);
    }

    private final Host host;

    public DisabledList(Font font, int x, int y, int width, int height, Host host) {
        super(font, x, y, width, height, Ui.ROW_HEIGHT);
        this.host = host;
    }

    @Override
    protected int rowCount() {
        return this.host.disabledKeys().size();
    }

    private String keyAt(int index) {
        List<String> keys = this.host.disabledKeys();
        return index >= 0 && index < keys.size() ? keys.get(index) : null;
    }

    @Override
    protected boolean isSelected(int index) {
        return false;
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
        if (hovered) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom() - 1, Ui.ROW_HOVER);
        }
        int textY = rect.y() + (rect.h() - 8) / 2;
        String text = Ui.trim(font(), key.replace('|', ' '), rect.w() - 6);
        graphics.drawString(font(), text, rect.x() + 3, textY, hovered ? Ui.WARN : Ui.MUTED, false);
    }

    @Override
    protected void clickRow(int index, double mouseX, double mouseY) {
        String key = keyAt(index);
        if (key != null) {
            this.host.activateDisabledKey(key);
        }
    }
}
