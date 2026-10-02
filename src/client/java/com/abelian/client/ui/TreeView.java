package com.abelian.client.ui;

import com.abelian.client.ResourceNode;
import com.abelian.client.ResourceTree;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** File-manager style tree: indented rows, expand arrows, click to select, double click to open. */
public class TreeView extends ScrollList {
    public interface Host {
        List<ResourceTree.Row> rows();

        ResourceNode selectedNode();

        void selectNode(ResourceNode node);

        void toggleExpand(ResourceNode node);

        String badgeOf(ResourceNode node);

        int badgeColorOf(ResourceNode node);

        int nameColorOf(ResourceNode node);

        boolean strikeThroughOf(ResourceNode node);
    }

    private static final int INDENT = 10;
    private static final int ARROW_WIDTH = 9;
    private static final long DOUBLE_CLICK_MILLIS = 350L;

    private final Host host;
    private int lastClickRow = -1;
    private long lastClickTime;

    public TreeView(Font font, int x, int y, int width, int height, Host host) {
        super(font, x, y, width, height, Ui.ROW_HEIGHT);
        this.host = host;
    }

    @Override
    protected int rowCount() {
        return this.host.rows().size();
    }

    public ResourceTree.Row rowAt(int index) {
        List<ResourceTree.Row> rows = this.host.rows();
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    public ResourceNode nodeAt(int index) {
        ResourceTree.Row row = rowAt(index);
        return row == null ? null : row.node();
    }

    public int indexOf(ResourceNode node) {
        List<ResourceTree.Row> rows = this.host.rows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).node() == node) {
                return index;
            }
        }
        return -1;
    }

    /** Selects the given node, expands its parents and scrolls it into view. */
    public void reveal(ResourceNode node) {
        int index = indexOf(node);
        if (index >= 0) {
            scrollToRow(index);
        }
    }

    @Override
    protected boolean isSelected(int index) {
        ResourceNode node = nodeAt(index);
        return node != null && node == this.host.selectedNode();
    }

    @Override
    public String tooltipAt(int index) {
        ResourceNode node = nodeAt(index);
        return node == null ? null : node.displayPath();
    }

    @Override
    protected void renderRow(GuiGraphics graphics, int index, Ui.Rect rect, boolean hovered, boolean selected) {
        ResourceTree.Row row = rowAt(index);
        if (row == null) {
            return;
        }
        ResourceNode node = row.node();
        if (selected) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom() - 1, Ui.ROW_SELECTED);
        } else if (hovered) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom() - 1, Ui.ROW_HOVER);
        }

        int textY = rect.y() + (rect.h() - 8) / 2;
        int arrowX = rect.x() + 3 + row.depth() * INDENT;
        if (node.hasChildren()) {
            graphics.drawString(font(), node.expanded() ? "\u25be" : "\u25b8", arrowX, textY,
                    node.expanded() ? Ui.ACCENT : Ui.MUTED, false);
        } else {
            graphics.drawString(font(), node.isSoundEvent() ? "\u266a" : "\u2022", arrowX, textY, Ui.OFF, false);
        }

        int textX = arrowX + ARROW_WIDTH;
        String badge = this.host.badgeOf(node);
        int badgeWidth = badge == null ? 0 : font().width(badge) + 6;
        String name = Ui.trim(font(), node.name(), rect.right() - textX - badgeWidth - 2);
        int nameColor = this.host.nameColorOf(node);
        if (this.host.strikeThroughOf(node)) {
            graphics.drawString(font(), Component.literal(name).withStyle(ChatFormatting.STRIKETHROUGH), textX, textY,
                    nameColor, false);
        } else {
            graphics.drawString(font(), name, textX, textY, nameColor, node.hasChildren() || !node.isFile());
        }
        if (badge != null) {
            graphics.drawString(font(), badge, rect.right() - badgeWidth + 1, textY, this.host.badgeColorOf(node), false);
        }
    }

    @Override
    protected void clickRow(int index, double mouseX, double mouseY) {
        ResourceTree.Row row = rowAt(index);
        if (row == null) {
            return;
        }
        ResourceNode node = row.node();
        long now = Util.getMillis();
        boolean doubleClick = index == this.lastClickRow && now - this.lastClickTime < DOUBLE_CLICK_MILLIS;
        this.lastClickRow = index;
        this.lastClickTime = now;

        int arrowX = getX() + 3 + row.depth() * INDENT;
        boolean onArrow = mouseX >= arrowX - 1 && mouseX < arrowX + ARROW_WIDTH;
        if (node.hasChildren() && (doubleClick || onArrow)) {
            this.host.toggleExpand(node);
        }
        this.host.selectNode(node);
    }
}
