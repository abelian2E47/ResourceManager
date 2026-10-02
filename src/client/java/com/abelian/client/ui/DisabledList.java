package com.abelian.client.ui;

import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * List of every disabled resource.
 *
 * <p>In the sidebar it is a compact hint list where a single click jumps to the file. Expanded to the
 * whole window it becomes the working view of the disabled entries: rows show the pack and the file in
 * two columns and a double click (or Enter) restores the entry.
 */
public class DisabledList extends ScrollList {
    public interface Host {
        List<String> disabledKeys();

        /** Jumps to the entry in the tree. */
        void activateDisabledKey(String key);

        /** Removes one disabled entry. */
        void restoreDisabledKey(String key);

        /** True while this list fills the whole window. */
        boolean disabledExpanded();
    }

    private static final long DOUBLE_CLICK_MILLIS = 350L;

    private final Host host;
    private int selectedIndex = -1;
    private int lastClickRow = -1;
    private long lastClickTime;

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

    public String selectedKey() {
        return keyAt(this.selectedIndex);
    }

    public int selectedIndex() {
        return this.selectedIndex;
    }

    public void select(int index) {
        this.selectedIndex = index;
    }

    /** Moves the highlight with the arrow keys, clamping at both ends. */
    public void moveSelection(int delta) {
        int count = rowCount();
        if (count == 0) {
            this.selectedIndex = -1;
            return;
        }
        int index = this.selectedIndex < 0 ? (delta > 0 ? 0 : count - 1) : this.selectedIndex + delta;
        this.selectedIndex = Ui.clamp(index, 0, count - 1);
        scrollToRow(this.selectedIndex);
    }

    /** Restores the highlighted entry; used by the Enter key. */
    public boolean restoreSelected() {
        String key = selectedKey();
        if (key == null) {
            return false;
        }
        this.host.restoreDisabledKey(key);
        return true;
    }

    @Override
    protected boolean isSelected(int index) {
        return index == this.selectedIndex;
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
        int separator = key.indexOf('|');
        String pack = separator < 0 ? "" : key.substring(0, separator);
        String file = separator < 0 ? key : key.substring(separator + 1);
        if (this.host.disabledExpanded() && rect.w() > 240) {
            int packWidth = Math.max(60, Math.min(rect.w() / 3, 180));
            String packText = Ui.trim(font(), pack, packWidth - 8);
            String fileText = Ui.trim(font(), file, rect.w() - packWidth - 8);
            graphics.drawString(font(), packText, rect.x() + 4, textY, selected ? Ui.WARN : Ui.ACCENT, false);
            graphics.drawString(font(), fileText, rect.x() + packWidth + 4, textY,
                    selected ? Ui.TEXT : Ui.MUTED, false);
            return;
        }
        String text = Ui.trim(font(), key.replace('|', ' '), rect.w() - 6);
        graphics.drawString(font(), text, rect.x() + 3, textY, hovered || selected ? Ui.WARN : Ui.MUTED, false);
    }

    @Override
    protected void clickRow(int index, double mouseX, double mouseY) {
        String key = keyAt(index);
        if (key == null) {
            return;
        }
        long now = Util.getMillis();
        boolean doubleClick = index == this.lastClickRow && now - this.lastClickTime < DOUBLE_CLICK_MILLIS;
        this.lastClickRow = index;
        this.lastClickTime = now;
        this.selectedIndex = index;
        if (doubleClick) {
            this.host.restoreDisabledKey(key);
        } else {
            this.host.activateDisabledKey(key);
        }
    }
}
