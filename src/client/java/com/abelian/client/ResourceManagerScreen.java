package com.abelian.client;

import com.abelian.client.ui.DisabledList;
import com.abelian.client.ui.DragTarget;
import com.abelian.client.ui.ScrollList;
import com.abelian.client.ui.TreeView;
import com.abelian.client.ui.Ui;
import com.abelian.client.ui.UiButton;
import com.abelian.client.ui.UiSlider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Resource Manager GUI.
 *
 * <p>Layout: a toolbar on top, a status bar at the bottom and three sibling panels in between —
 * filters/disabled-list, the pack tree and the inspector. Every widget is positioned from the rects
 * computed by {@link #layout()}, so panels and controls can never drift apart or overlap the text.
 */
public final class ResourceManagerScreen extends Screen implements TreeView.Host, DisabledList.Host {
    private static final int PAD = 8;
    private static final int TOOLBAR_HEIGHT = 20;
    private static final int STATUS_HEIGHT = 12;
    private static final int PANEL_GAP = 6;
    private static final int HEADER_HEIGHT = 13;
    private static final int BUTTON = Ui.BUTTON_HEIGHT;
    private static final int SLIDER_HEIGHT = 14;
    private static final int SIDEBAR_MIN_WIDTH = 490;
    private static final float TUNING_MIN = 0.0F;
    private static final float TUNING_MAX = 4.0F;
    private static final long BULK_CONFIRM_MILLIS = 4000L;

    private final Screen parent;
    private final ResourceManagerConfig config = ResourceManagerConfig.instance();

    private Ui.Rect toolbar = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect statusBar = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect sidebar;
    private Ui.Rect treePanel = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect inspector = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect filterHeader = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect disabledHeader = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect inspectorInfo = new Ui.Rect(0, 0, 0, 0);
    private boolean sidebarOpen;

    private EditBox search;
    private UiButton reloadButton;
    private UiButton closeButton;
    private UiButton sidebarButton;
    private UiButton categoryButton;
    private UiButton clearButton;
    private UiButton toggleButton;
    private UiButton providerButton;
    private UiButton playButton;
    private UiButton resetButton;
    private UiSlider volumeSlider;
    private UiSlider pitchSlider;
    private TreeView treeView;
    private DisabledList disabledList;
    private final List<UiButton> filterButtons = new ArrayList<>();

    private ResourceIndex.Snapshot snapshot;
    private ResourceTree tree;
    private boolean scanning;
    private int scanGeneration;
    private String statusMessage = "";
    private String statusDetail = "";

    private ResourceNode selected;
    private String tuningKey;
    private float pendingVolume = 1.0F;
    private float pendingPitch = 1.0F;
    private boolean tuningDirty;
    private long lastTuningSave;
    private ResourceNode bulkConfirmNode;
    private long bulkConfirmTime;

    private String query = "";
    private ResourceCategory category = ResourceCategory.ALL;
    private int lastConfigVersion = -1;
    private List<String> disabledKeys = List.of();
    private Set<String> tunedKeys = Set.of();
    private int pendingChanges;
    private final Set<String> expandedKeys = new LinkedHashSet<>();
    private DragTarget dragTarget;

    public ResourceManagerScreen(Screen parent) {
        super(Component.translatable("resourcemanager.ui.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        this.sidebarOpen = this.width - PAD * 2 >= SIDEBAR_MIN_WIDTH;

        this.search = addRenderableWidget(new EditBox(this.font, 0, 0, 120, TOOLBAR_HEIGHT - 6,
                Component.translatable("resourcemanager.ui.search")));
        this.search.setHint(Component.translatable("resourcemanager.ui.search.hint"));
        this.search.setMaxLength(160);
        this.search.setResponder(this::onSearchChanged);
        this.search.setValue(this.query);

        this.reloadButton = addRenderableWidget(new UiButton(this.font, 0, 0, 80, BUTTON,
                Component.translatable("resourcemanager.ui.reload"), this::reloadPacks));
        this.closeButton = addRenderableWidget(new UiButton(this.font, 0, 0, 50, BUTTON,
                Component.translatable("resourcemanager.ui.close"), this::onClose));
        this.sidebarButton = addRenderableWidget(new UiButton(this.font, 0, 0, 60, BUTTON,
                Component.translatable("resourcemanager.ui.sidebar.show"), () -> this.sidebarOpen = !this.sidebarOpen));
        this.categoryButton = addRenderableWidget(new UiButton(this.font, 0, 0, 96, BUTTON,
                Component.empty(), () -> setCategory(this.category.next())));
        this.clearButton = addRenderableWidget(new UiButton(this.font, 0, 0, 60, BUTTON,
                Component.translatable("resourcemanager.ui.clearAll"), this::clearAllDisabled));

        this.toggleButton = addRenderableWidget(new UiButton(this.font, 0, 0, 80, BUTTON,
                Component.translatable("resourcemanager.ui.action.disable"), this::toggleSelected));
        this.providerButton = addRenderableWidget(new UiButton(this.font, 0, 0, 80, BUTTON,
                Component.translatable("resourcemanager.ui.action.provider"), this::nextProvider));
        this.playButton = addRenderableWidget(new UiButton(this.font, 0, 0, 60, BUTTON,
                Component.translatable("resourcemanager.ui.action.play"), this::playSelectedSound));
        this.resetButton = addRenderableWidget(new UiButton(this.font, 0, 0, 60, BUTTON,
                Component.translatable("resourcemanager.ui.action.reset"), this::resetSelectedSound));

        this.volumeSlider = addRenderableWidget(new UiSlider(this.font, 0, 0, 120, SLIDER_HEIGHT,
                Component.translatable("resourcemanager.ui.sound.volume").getString(), TUNING_MIN, TUNING_MAX, "%.2f",
                new UiSlider.Value() {
                    @Override
                    public double get() {
                        return pendingVolume;
                    }

                    @Override
                    public void set(double value) {
                        applyTuning((float) value, pendingPitch);
                    }
                }));
        this.pitchSlider = addRenderableWidget(new UiSlider(this.font, 0, 0, 120, SLIDER_HEIGHT,
                Component.translatable("resourcemanager.ui.sound.pitch").getString(), TUNING_MIN, TUNING_MAX, "%.2f",
                new UiSlider.Value() {
                    @Override
                    public double get() {
                        return pendingPitch;
                    }

                    @Override
                    public void set(double value) {
                        applyTuning(pendingVolume, (float) value);
                    }
                }));

        this.filterButtons.clear();
        for (ResourceCategory value : ResourceCategory.values()) {
            UiButton button = addRenderableWidget(new UiButton(this.font, 0, 0, 100, BUTTON,
                    Component.translatable(value.translationKey()), () -> setCategory(value)));
            this.filterButtons.add(button);
        }

        this.treeView = addRenderableWidget(new TreeView(this.font, 0, 0, 100, 100, this));
        this.disabledList = addRenderableWidget(new DisabledList(this.font, 0, 0, 100, 100, this));

        layout();
        if (this.snapshot == null && !this.scanning) {
            startScan();
        }
    }

    @Override
    public void onClose() {
        saveTuning();
        this.config.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ scanning

    private void startScan() {
        this.scanning = true;
        this.statusMessage = Component.translatable("resourcemanager.ui.status.scanning").getString();
        this.statusDetail = "";
        int generation = ++this.scanGeneration;
        ResourceIndex.scanAsync().whenComplete((result, error) -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (generation != this.scanGeneration) {
                    return;
                }
                this.scanning = false;
                if (error != null) {
                    this.statusMessage = "Scan failed: " + error.getClass().getSimpleName();
                    return;
                }
                applySnapshot(result);
            });
        });
    }

    private void applySnapshot(ResourceIndex.Snapshot result) {
        ResourceNode previous = this.selected;
        this.snapshot = result;
        this.tree = new ResourceTree(result.packNodesHighestFirst(),
                node -> this.config.isDisabled(node.packId(), node.resourceId()));
        this.tree.setFilter(this.query, this.category);
        applyExpandedState();
        this.selected = null;
        this.tuningKey = null;
        if (previous != null) {
            ResourceNode restored = findNode(previous);
            if (restored != null) {
                selectNode(restored, false);
            }
        }
        this.statusDetail = Component.translatable("resourcemanager.ui.status.loaded", result.fileCount(),
                result.packs().size(), result.soundEventCount()).getString();
        if (!this.scanning) {
            this.statusMessage = this.statusDetail;
        }
    }

    private void applyExpandedState() {
        if (this.expandedKeys.isEmpty() || this.tree == null) {
            return;
        }
        for (ResourceNode pack : this.tree.packs()) {
            restoreExpanded(pack);
        }
    }

    private void restoreExpanded(ResourceNode node) {
        for (ResourceNode child : node.children()) {
            if (child.hasChildren() && this.expandedKeys.contains(expandedKey(child))) {
                child.setExpanded(true);
            }
            restoreExpanded(child);
        }
    }

    private String expandedKey(ResourceNode node) {
        return node.packId() + "|" + node.kind() + "|" + (node.path() == null ? "" : node.path());
    }

    private ResourceNode findNode(ResourceNode previous) {
        if (this.snapshot == null) {
            return null;
        }
        if (previous.isFile() && previous.location() != null) {
            for (ResourceNode candidate : this.snapshot.providersOf(previous.location())) {
                if (candidate.packId().equals(previous.packId())) {
                    return candidate;
                }
            }
            return null;
        }
        String packId = previous.packId();
        for (ResourceNode pack : this.tree.packs()) {
            if (!pack.packId().equals(packId)) {
                continue;
            }
            ResourceNode found = findWithin(pack, previous);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ResourceNode findWithin(ResourceNode node, ResourceNode wanted) {
        for (ResourceNode child : node.children()) {
            if (child.kind() == wanted.kind() && child.name().equals(wanted.name())) {
                return child;
            }
            ResourceNode found = findWithin(child, wanted);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void reloadPacks() {
        saveTuning();
        this.config.save();
        this.pendingChanges = 0;
        this.scanning = true;
        this.statusMessage = Component.translatable("resourcemanager.ui.status.reloading").getString();
        Minecraft client = Minecraft.getInstance();
        client.reloadResourcePacks().thenRunAsync(() -> client.execute(this::startScan), client);
    }
    

    private void layout() {
        this.toolbar = new Ui.Rect(PAD, PAD, Math.max(60, this.width - PAD * 2), TOOLBAR_HEIGHT);
        this.statusBar = new Ui.Rect(PAD, this.height - PAD - STATUS_HEIGHT, this.toolbar.w(), STATUS_HEIGHT);

        int contentTop = this.toolbar.bottom() + PANEL_GAP;
        int contentBottom = this.statusBar.y() - PANEL_GAP;
        int contentHeight = Math.max(48, contentBottom - contentTop);
        int available = this.toolbar.w();
        boolean compact = available < 560;

        boolean showSidebar = this.sidebarOpen && available >= SIDEBAR_MIN_WIDTH;
        int sidebarWidth = showSidebar ? Ui.clamp(available * 19 / 100, 148, 190) : 0;
        int inspectorWidth = Ui.clamp(available * 31 / 100, 168, 300);
        int gaps = (showSidebar ? 1 : 0) + 1;
        int treeWidth = available - sidebarWidth - inspectorWidth - gaps * PANEL_GAP;
        if (treeWidth < 150) {
            int give = Math.min(150 - treeWidth, inspectorWidth - 150);
            if (give > 0) {
                inspectorWidth -= give;
                treeWidth += give;
            }
        }
        treeWidth = Math.max(100, treeWidth);

        int x = PAD;
        if (showSidebar) {
            this.sidebar = new Ui.Rect(x, contentTop, sidebarWidth, contentHeight);
            x += sidebarWidth + PANEL_GAP;
        } else {
            this.sidebar = null;
        }
        this.treePanel = new Ui.Rect(x, contentTop, treeWidth, contentHeight);
        x += treeWidth + PANEL_GAP;
        this.inspector = new Ui.Rect(x, contentTop, inspectorWidth, contentHeight);

        layoutToolbar(compact);
        layoutSidebar();
        layoutTree();
        layoutInspector();
    }

    private void layoutToolbar(boolean compact) {
        int y = this.toolbar.y() + (TOOLBAR_HEIGHT - BUTTON) / 2;
        int gap = 4;

        this.sidebarButton.setMessage(Component.translatable("resourcemanager.ui.sidebar.show"));
        this.sidebarButton.setToggled(this.sidebarOpen);
        this.categoryButton.setMessage(Component.translatable("resourcemanager.ui.filter.cycle",
                Component.translatable(this.category.translationKey())));
        this.categoryButton.visible = this.sidebar == null;

        // Right-to-left strip: every button is wide enough for its label, the search box gets what is left
        // (and the buttons give up space first when the window is narrow, so no label is cut off).
        List<UiButton> buttons = new ArrayList<>();
        buttons.add(this.closeButton);
        buttons.add(this.reloadButton);
        buttons.add(this.sidebarButton);
        if (this.categoryButton.visible) {
            buttons.add(this.categoryButton);
        }
        int[] widths = new int[buttons.size()];
        int needed = 0;
        for (int index = 0; index < buttons.size(); index++) {
            widths[index] = Ui.clamp(this.font.width(buttons.get(index).getMessage()) + 10, 24, 160);
            needed += widths[index] + (index == 0 ? 0 : gap);
        }
        int titleWidth = compact ? 0 : Math.min(this.font.width(this.title) + 12, 150);
        int searchLeft = this.toolbar.x() + titleWidth;
        int minSearch = compact ? 64 : 110;
        int overflow = needed + minSearch + 6 - (this.toolbar.right() - searchLeft);
        for (int index = 0; index < widths.length && overflow > 0; index++) {
            int floor = buttons.get(index) == this.categoryButton ? 34 : 26;
            int reduce = Math.min(overflow, Math.max(0, widths[index] - floor));
            widths[index] -= reduce;
            overflow -= reduce;
        }

        int cursor = this.toolbar.right();
        int leftmost = cursor;
        for (int index = 0; index < buttons.size(); index++) {
            cursor -= widths[index];
            Ui.place(buttons.get(index), cursor, y, widths[index], BUTTON);
            leftmost = cursor;
            cursor -= gap;
        }
        int searchWidth = Math.max(40, leftmost - 6 - searchLeft);
        Ui.place(this.search, searchLeft, y, searchWidth, BUTTON);
    }

    private void layoutSidebar() {
        if (this.sidebar == null) {
            for (UiButton button : this.filterButtons) {
                button.visible = false;
            }
            this.clearButton.visible = false;
            this.disabledList.visible = false;
            return;
        }
        int innerX = this.sidebar.x() + 1;
        int innerWidth = this.sidebar.w() - 2;
        this.filterHeader = new Ui.Rect(innerX, this.sidebar.y() + 1, innerWidth, HEADER_HEIGHT);
        int y = this.filterHeader.bottom() + 3;
        for (UiButton button : this.filterButtons) {
            button.visible = true;
            Ui.place(button, innerX + 4, y, innerWidth - 8, BUTTON);
            button.setToggled(this.category == filterCategory(button));
            y += BUTTON + 2;
        }
        int footerTop = this.sidebar.bottom() - 4 - BUTTON;
        int available = footerTop - 4 - (y + 3) - HEADER_HEIGHT;
        boolean showDisabledList = available >= 16;
        this.disabledHeader = new Ui.Rect(innerX, y + 3, innerWidth, HEADER_HEIGHT);
        this.disabledHeader = showDisabledList ? this.disabledHeader : null;
        this.clearButton.visible = showDisabledList;
        this.disabledList.visible = showDisabledList;
        if (showDisabledList) {
            Ui.place(this.clearButton, innerX + 4, footerTop, innerWidth - 8, BUTTON);
            Ui.place(this.disabledList, innerX + 3, this.disabledHeader.bottom() + 1, innerWidth - 6,
                    Math.max(12, footerTop - 4 - (this.disabledHeader.bottom() + 1)));
        }
    }

    private ResourceCategory filterCategory(UiButton button) {
        int index = this.filterButtons.indexOf(button);
        return index < 0 ? ResourceCategory.ALL : ResourceCategory.values()[index];
    }

    private void layoutTree() {
        Ui.Rect body = treeBody();
        Ui.place(this.treeView, body.x() + 1, body.y() + 1, Math.max(20, body.w() - 2), Math.max(12, body.h() - 2));
        this.treeView.visible = true;
    }

    private Ui.Rect treeHeader() {
        return new Ui.Rect(this.treePanel.x() + 1, this.treePanel.y() + 1, this.treePanel.w() - 2, HEADER_HEIGHT);
    }

    private Ui.Rect treeBody() {
        Ui.Rect header = treeHeader();
        return new Ui.Rect(header.x(), header.bottom(), header.w(),
                Math.max(12, this.treePanel.bottom() - 1 - header.bottom()));
    }

    private void layoutInspector() {
        int innerX = this.inspector.x() + 1;
        int innerWidth = this.inspector.w() - 2;
        int infoTop = this.inspector.y() + 1 + HEADER_HEIGHT + 4;
        boolean hasSound = this.selected != null && this.selected.isSoundEvent();
        int soundHeight = hasSound ? HEADER_HEIGHT + SLIDER_HEIGHT * 2 + 6 + BUTTON : 0;
        int soundTop = hasSound ? this.inspector.bottom() - 4 - soundHeight : this.inspector.bottom() - 4;

        this.toggleButton.visible = canToggle(this.selected);
        this.providerButton.visible = this.selected != null && this.selected.isFile() && this.snapshot != null
                && this.snapshot.providersOf(this.selected.location()).size() > 1;

        int buttonCount = (this.toggleButton.visible ? 1 : 0) + (this.providerButton.visible ? 1 : 0);
        int actionHeight = buttonCount > 0 ? BUTTON : 0;
        int actionTop = soundTop - 6 - actionHeight;
        int columnWidth = (innerWidth - 8 - (buttonCount == 2 ? 4 : 0)) / Math.max(1, buttonCount);
        if (this.toggleButton.visible) {
            Ui.place(this.toggleButton, innerX + 4, actionTop, columnWidth, BUTTON);
        }
        if (this.providerButton.visible) {
            int secondX = this.toggleButton.visible ? innerX + 4 + columnWidth + 4 : innerX + 4;
            Ui.place(this.providerButton, secondX, actionTop, columnWidth, BUTTON);
        }
        this.toggleButton.setMessage(toggleLabel());

        this.inspectorInfo = new Ui.Rect(innerX + 4, infoTop, innerWidth - 8,
                Math.max(10, actionTop - 4 - infoTop));

        this.volumeSlider.visible = hasSound;
        this.pitchSlider.visible = hasSound;
        this.playButton.visible = hasSound;
        this.resetButton.visible = hasSound;
        if (hasSound) {
            Ui.place(this.volumeSlider, innerX + 4, soundTop + HEADER_HEIGHT, innerWidth - 8, SLIDER_HEIGHT);
            Ui.place(this.pitchSlider, innerX + 4, soundTop + HEADER_HEIGHT + SLIDER_HEIGHT + 3, innerWidth - 8,
                    SLIDER_HEIGHT);
            int width = (innerWidth - 8 - 4) / 2;
            Ui.place(this.resetButton, innerX + 4, soundTop + HEADER_HEIGHT + (SLIDER_HEIGHT + 3) * 2, width, BUTTON);
            Ui.place(this.playButton, innerX + 8 + width, soundTop + HEADER_HEIGHT + (SLIDER_HEIGHT + 3) * 2, width,
                    BUTTON);
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        syncConfig();
        layout();
        saveTuningIfIdle();

        renderBackground(graphics, mouseX, mouseY, delta);
        renderToolbar(graphics);
        if (this.sidebar != null) {
            renderSidebar(graphics);
        }
        renderTreePanel(graphics);
        renderInspector(graphics);
        renderStatusBar(graphics);

        // Screen.render() would call renderBackground() a second time and paint over everything drawn
        // above, so the widgets are rendered directly, on top of the panel scaffolding.
        for (GuiEventListener child : this.children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, delta);
            }
        }
        renderTooltips(graphics, mouseX, mouseY);
    }

    private void renderToolbar(GuiGraphics graphics) {
        Ui.panel(graphics, this.toolbar, Ui.PANEL, Ui.BORDER);
        if (this.toolbar.w() >= 560) {
            graphics.drawString(this.font, this.title, this.toolbar.x() + 6,
                    this.toolbar.y() + (TOOLBAR_HEIGHT - 8) / 2, Ui.TEXT, true);
        }
    }

    private void renderSidebar(GuiGraphics graphics) {
        Ui.panel(graphics, this.sidebar, Ui.PANEL, Ui.BORDER);
        Ui.header(graphics, this.font, this.filterHeader, Component.translatable("resourcemanager.ui.panel.filters"),
                Ui.MUTED);
        if (this.disabledHeader != null) {
            Ui.header(graphics, this.font, this.disabledHeader,
                    Component.translatable("resourcemanager.ui.panel.disabled", this.config.disabledCount()),
                    this.config.disabledCount() > 0 ? Ui.WARN : Ui.MUTED);
            if (this.config.disabledCount() == 0) {
                graphics.drawString(this.font, Component.translatable("resourcemanager.ui.empty.disabled"),
                        this.disabledList.getX() + 3, this.disabledList.getY() + 2, Ui.OFF, false);
            }
        }
    }

    private void renderTreePanel(GuiGraphics graphics) {
        Ui.panel(graphics, this.treePanel, Ui.PANEL, Ui.BORDER);
        Ui.Rect header = treeHeader();
        Ui.header(graphics, this.font, header, Component.translatable("resourcemanager.ui.panel.tree"), Ui.TEXT);
        if (this.snapshot != null) {
            String counter = Component.translatable("resourcemanager.ui.tree.count",
                    this.tree == null ? 0 : this.tree.matchedFiles(), this.snapshot.fileCount()).getString();
            graphics.drawString(this.font, counter, header.right() - 4 - this.font.width(counter), header.y() + 3,
                    Ui.MUTED, false);
        }
        Ui.Rect body = treeBody();
        graphics.fill(body.x(), body.y(), body.right(), body.bottom(), Ui.BODY);
        if (this.tree == null || this.tree.rows().isEmpty()) {
            String message = this.scanning
                    ? Component.translatable("resourcemanager.ui.scanning").getString()
                    : Component.translatable("resourcemanager.ui.empty.tree").getString();
            graphics.drawString(this.font, message, body.x() + 6, body.y() + 5, Ui.MUTED, false);
        }
    }

    private void renderInspector(GuiGraphics graphics) {
        Ui.panel(graphics, this.inspector, Ui.PANEL, Ui.BORDER);
        Ui.header(graphics, this.font,
                new Ui.Rect(this.inspector.x() + 1, this.inspector.y() + 1, this.inspector.w() - 2, HEADER_HEIGHT),
                Component.translatable("resourcemanager.ui.panel.inspector"), Ui.TEXT);

        int x = this.inspectorInfo.x();
        int width = this.inspectorInfo.w();
        int y = this.inspectorInfo.y();
        int limit = this.inspectorInfo.bottom();

        if (this.selected == null) {
            drawWrapped(graphics, Component.translatable("resourcemanager.ui.selectHint"), x, y, width, Ui.MUTED, 4,
                    limit);
            return;
        }
        ResourceNode node = this.selected;
        y = drawWrapped(graphics, Component.literal(node.name()), x, y, width,
                node.isSoundEvent() ? Ui.ACCENT : Ui.TEXT, 2, limit) + 2;
        if (node.isFile() || node.isSoundEvent()) {
            y = drawWrapped(graphics, Component.literal(node.isSoundEvent()
                    ? node.namespace() + ":" + node.name()
                    : node.displayPath()), x, y, width, Ui.MUTED, 2, limit) + 3;
        }

        y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.pack"),
                node.packId(), Ui.TEXT);
        y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.type"),
                Component.translatable(typeKey(node)).getString(), Ui.MUTED);
        if (node.kind() == ResourceNode.Kind.PACK) {
            y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.priority"),
                    "#" + node.priority(), Ui.MUTED);
        }
        if (!node.isSoundEvent()) {
            y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.files"),
                    String.valueOf(node.fileCount()), Ui.MUTED);
        }
        if (node.isFile() && this.snapshot != null) {
            List<ResourceNode> providers = this.snapshot.providersOf(node.location());
            String winner = winnerName(node);
            String value = providers.size() + " \u00b7 " + (winner == null ? "-" : winner);
            y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.providers"),
                    value, Ui.MUTED);
        }
        drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.state"),
                stateText(node), stateColor(node));
    }

    private String stateText(ResourceNode node) {
        if (node.isSoundEvent()) {
            ResourceManagerConfig.SoundTuning tuning = this.config.sound(soundKeyOf(node));
            return tuning.isDefault()
                    ? Component.translatable("resourcemanager.ui.state.default").getString()
                    : Component.translatable("resourcemanager.ui.state.tuned",
                            String.format(Locale.ROOT, "%.2f", tuning.volume()),
                            String.format(Locale.ROOT, "%.2f", tuning.pitch())).getString();
        }
        if (node.isFile()) {
            if (isDisabled(node)) {
                return Component.translatable("resourcemanager.ui.state.disabled").getString();
            }
            String winner = winnerName(node);
            if (winner == null) {
                return Component.translatable("resourcemanager.ui.state.missing").getString();
            }
            return winner.equals(node.packId())
                    ? Component.translatable("resourcemanager.ui.state.active").getString()
                    : Component.translatable("resourcemanager.ui.state.shadowed", winner).getString();
        }
        return Component.translatable("resourcemanager.ui.state.disabledCount", node.disabledCount(), node.fileCount())
                .getString();
    }

    private int stateColor(ResourceNode node) {
        if (node.isSoundEvent()) {
            return this.config.sound(soundKeyOf(node)).isDefault() ? Ui.MUTED : Ui.ACCENT;
        }
        if (node.isFile()) {
            if (isDisabled(node)) {
                return Ui.WARN;
            }
            String winner = winnerName(node);
            if (winner == null) {
                return Ui.WARN;
            }
            return winner.equals(node.packId()) ? Ui.OK : Ui.MUTED;
        }
        return node.disabledCount() > 0 ? Ui.WARN : Ui.MUTED;
    }

    private String typeKey(ResourceNode node) {
        return switch (node.kind()) {
            case PACK -> "resourcemanager.ui.type.pack";
            case NAMESPACE -> "resourcemanager.ui.type.namespace";
            case DIRECTORY -> "resourcemanager.ui.type.folder";
            case SOUND -> "resourcemanager.ui.type.sound";
            case FILE -> "resourcemanager.ui.type.file";
        };
    }

    private void renderStatusBar(GuiGraphics graphics) {
        graphics.fill(this.statusBar.x(), this.statusBar.y(), this.statusBar.right(), this.statusBar.bottom(),
                Ui.PANEL);
        String left = this.scanning ? this.statusMessage
                : Component.translatable("resourcemanager.ui.status.loaded",
                        this.snapshot == null ? 0 : this.snapshot.fileCount(),
                        this.snapshot == null ? 0 : this.snapshot.packs().size(),
                        this.snapshot == null ? 0 : this.snapshot.soundEventCount()).getString();
        if (this.config.disabledCount() > 0) {
            left = left + "  \u00b7  " + Component.translatable("resourcemanager.ui.status.disabledCount",
                    this.config.disabledCount()).getString();
        }
        graphics.drawString(this.font, Ui.trim(this.font, left, this.statusBar.w() / 2), this.statusBar.x(),
                this.statusBar.y() + 2, Ui.MUTED, false);

        String right = this.pendingChanges > 0
                ? Component.translatable("resourcemanager.ui.status.pending", this.pendingChanges).getString()
                : Component.translatable("resourcemanager.ui.hints").getString();
        int color = this.pendingChanges > 0 ? Ui.WARN : Ui.OFF;
        // The hint is the first thing to drop when the window is too narrow for both halves.
        int leftWidth = this.font.width(Ui.trim(this.font, left, this.statusBar.w() / 2));
        if (this.pendingChanges > 0 || leftWidth + 8 + this.font.width(right) <= this.statusBar.w()) {
            graphics.drawString(this.font, right, this.statusBar.right() - this.font.width(right),
                    this.statusBar.y() + 2, color, false);
        }
    }

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int index = this.treeView.hoveredRow();
        if (index >= 0 && isMouseOver(this.treeView, mouseX, mouseY)) {
            ResourceTree.Row row = this.treeView.rowAt(index);
            if (row != null) {
                graphics.renderTooltip(this.font, Component.literal(row.node().displayPath()), mouseX, mouseY);
            }
            return;
        }
        int disabledIndex = this.disabledList.hoveredRow();
        if (disabledIndex >= 0 && isMouseOver(this.disabledList, mouseX, mouseY)) {
            String key = this.disabledList.tooltipAt(disabledIndex);
            if (key != null) {
                graphics.renderTooltip(this.font, Component.literal(key), mouseX, mouseY);
            }
        }
    }

    private boolean isMouseOver(ScrollList list, int mouseX, int mouseY) {
        return list.visible && mouseX >= list.getX() && mouseX < list.getRight() && mouseY >= list.getY()
                && mouseY < list.getBottom();
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxLines,
            int limit) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        int drawn = 0;
        for (FormattedCharSequence line : lines) {
            if (drawn >= maxLines || y + 9 > limit) {
                break;
            }
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
            drawn++;
        }
        return y;
    }

    private int drawInfoLine(GuiGraphics graphics, int x, int y, int width, int limit, Component label,
            String value, int color) {
        if (y + 9 > limit) {
            return y;
        }
        graphics.drawString(this.font, label, x, y, Ui.MUTED, false);
        int valueX = x + Math.max(52, this.font.width(label) + 6);
        graphics.drawString(this.font, Ui.trim(this.font, value, width - (valueX - x)), valueX, y, color, false);
        return y + 10;
    }

    // ------------------------------------------------------------------ state

    private void onSearchChanged(String value) {
        this.query = value;
        if (this.tree != null) {
            this.tree.setFilter(this.query, this.category);
            this.treeView.clampScroll();
        }
    }

    private void setCategory(ResourceCategory value) {
        this.category = value;
        if (this.tree != null) {
            this.tree.setFilter(this.query, this.category);
            this.treeView.resetScroll();
        }
    }

    private void syncConfig() {
        int version = this.config.version();
        if (version == this.lastConfigVersion) {
            return;
        }
        this.lastConfigVersion = version;
        this.disabledKeys = List.copyOf(new TreeSet<>(this.config.disabledKeys()));
        Set<String> keys = new LinkedHashSet<>();
        this.config.sounds().keySet().forEach(key -> {
            keys.add(key);
            int separator = key.indexOf('|');
            if (separator >= 0) {
                keys.add(key.substring(separator + 1));
            }
        });
        this.tunedKeys = keys;
        if (this.tree != null) {
            this.tree.refresh();
        }
    }

    private void applyTuning(float volume, float pitch) {
        this.pendingVolume = (float) Ui.clamp(volume, TUNING_MIN, TUNING_MAX);
        this.pendingPitch = (float) Ui.clamp(pitch, TUNING_MIN, TUNING_MAX);
        if (this.tuningKey != null) {
            this.config.setSound(this.tuningKey, this.pendingVolume, this.pendingPitch);
            this.tuningDirty = true;
        }
    }

    private void saveTuningIfIdle() {
        if (this.tuningDirty && Util.getMillis() - this.lastTuningSave > 1000L) {
            saveTuning();
        }
    }

    private void saveTuning() {
        if (this.tuningDirty) {
            this.config.save();
            this.tuningDirty = false;
        }
        this.lastTuningSave = Util.getMillis();
    }

    private void resetSelectedSound() {
        if (this.tuningKey == null) {
            return;
        }
        this.config.setSound(this.tuningKey, 1.0F, 1.0F);
        ResourceManagerConfig.SoundTuning tuning = this.config.sound(this.tuningKey);
        this.pendingVolume = tuning.volume();
        this.pendingPitch = tuning.pitch();
        saveTuning();
        this.config.save();
    }

    private void playSelectedSound() {
        if (this.tuningKey == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(this.tuningKey);
        if (id == null) {
            return;
        }
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, 1.0F));
    }

    /** Second click confirms bulk operations so a stray click cannot disable thousands of files. */
    private void toggleSelected() {
        ResourceNode node = this.selected;
        if (node == null || node.isSoundEvent()) {
            return;
        }
        if (node.isFile()) {
            boolean disabled = isDisabled(node);
            this.config.setDisabled(node.packId(), node.resourceId(), !disabled);
            this.pendingChanges++;
            this.config.save();
            return;
        }
        if (node.fileCount() == 0) {
            return;
        }
        boolean allDisabled = node.disabledCount() >= node.fileCount();
        if (this.bulkConfirmNode != node || Util.getMillis() - this.bulkConfirmTime > BULK_CONFIRM_MILLIS) {
            this.bulkConfirmNode = node;
            this.bulkConfirmTime = Util.getMillis();
            return;
        }
        this.bulkConfirmNode = null;
        int changed = 0;
        for (ResourceNode file : collectFiles(node)) {
            boolean disabled = isDisabled(file);
            if (allDisabled ? disabled : !disabled) {
                this.config.setDisabled(file.packId(), file.resourceId(), !allDisabled);
                changed++;
            }
        }
        this.pendingChanges += changed;
        this.config.save();
    }

    private Component toggleLabel() {
        ResourceNode node = this.selected;
        if (node == null) {
            return Component.empty();
        }
        if (node.isFile()) {
            return Component.translatable(isDisabled(node)
                    ? "resourcemanager.ui.action.enable"
                    : "resourcemanager.ui.action.disable");
        }
        boolean allDisabled = node.disabledCount() >= node.fileCount();
        if (this.bulkConfirmNode == node) {
            return Component.translatable("resourcemanager.ui.action.confirm", node.fileCount());
        }
        return Component.translatable(allDisabled ? "resourcemanager.ui.action.enableAll"
                : "resourcemanager.ui.action.disableAll", node.fileCount());
    }

    private boolean canToggle(ResourceNode node) {
        if (node == null || node.isSoundEvent()) {
            return false;
        }
        return node.isFile() || node.fileCount() > 0;
    }

    private List<ResourceNode> collectFiles(ResourceNode node) {
        List<ResourceNode> files = new ArrayList<>();
        collectFilesInto(node, files);
        return files;
    }

    private void collectFilesInto(ResourceNode node, List<ResourceNode> out) {
        for (ResourceNode child : node.children()) {
            if (child.isFile()) {
                out.add(child);
            } else {
                collectFilesInto(child, out);
            }
        }
    }

    private void clearAllDisabled() {
        this.config.clearDisabled();
        this.pendingChanges = 0;
        this.config.save();
        this.bulkConfirmNode = null;
    }

    private void nextProvider() {
        ResourceNode node = this.selected;
        if (node == null || !node.isFile() || this.snapshot == null) {
            return;
        }
        List<ResourceNode> providers = this.snapshot.providersOf(node.location());
        if (providers.size() < 2) {
            return;
        }
        int index = providers.indexOf(node);
        selectNode(providers.get((index + 1) % providers.size()), true);
    }

    private boolean isDisabled(ResourceNode node) {
        return this.config.isDisabled(node.packId(), node.resourceId());
    }

    private String winnerName(ResourceNode node) {
        if (this.snapshot == null || node.location() == null) {
            return null;
        }
        List<ResourceNode> providers = this.snapshot.providersOf(node.location());
        for (int index = providers.size() - 1; index >= 0; index--) {
            ResourceNode candidate = providers.get(index);
            if (!isDisabled(candidate)) {
                return candidate.packId();
            }
        }
        return null;
    }

    private String soundKeyOf(ResourceNode node) {
        return node.namespace() + ":" + node.name();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && button == 0) {
            DragTarget target = null;
            for (DragTarget candidate : Arrays.asList(this.volumeSlider, this.pitchSlider, this.treeView,
                    this.disabledList)) {
                if (candidate.isDragging()) {
                    target = candidate;
                    break;
                }
            }
            this.dragTarget = target;
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragTarget != null && button == 0) {
            this.dragTarget.dragTo(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragTarget != null && button == 0) {
            this.dragTarget.endDrag();
            this.dragTarget = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (this.search != null && !this.search.isFocused() && character >= ' '
                && Character.isLetterOrDigit(character)) {
            this.search.setFocused(true);
            this.search.insertText(String.valueOf(character));
            setFocused(this.search);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.search != null && this.search.isFocused()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                    focusTree(false);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    focusTree(false);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    focusTree(true);
                    return true;
                }
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                expandSelected(true);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                expandSelected(false);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                activateSelected();
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                if (this.selected != null && this.selected.hasChildren()) {
                    toggleExpand(this.selected);
                } else {
                    activateSelected();
                }
                return true;
            }
            case GLFW.GLFW_KEY_F5 -> {
                reloadPacks();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    /** Leaves the search box and hands keyboard navigation to the tree, like a file manager does. */
    private void focusTree(boolean fromBottom) {
        if (this.search != null) {
            this.search.setFocused(false);
        }
        setFocused(null);
        if (this.tree == null || this.tree.rows().isEmpty()) {
            return;
        }
        int index = fromBottom ? this.tree.rows().size() - 1 : 0;
        selectNode(this.tree.rows().get(index).node(), true);
    }

    private void moveSelection(int direction) {
        if (this.tree == null || this.tree.rows().isEmpty()) {
            return;
        }
        int index = this.selected == null ? -1 : this.treeView.indexOf(this.selected);
        int next = Ui.clamp(index + direction, 0, this.tree.rows().size() - 1);
        if (index < 0) {
            next = 0;
        }
        ResourceNode node = this.tree.rows().get(next).node();
        selectNode(node, true);
    }

    private void expandSelected(boolean expand) {
        if (this.selected == null) {
            return;
        }
        if (!expand && this.selected.hasChildren() && this.selected.expanded()) {
            toggleExpand(this.selected);
            return;
        }
        if (expand && this.selected.hasChildren()) {
            toggleExpand(this.selected);
            return;
        }
        if (!expand && this.selected.parent() != null) {
            selectNode(this.selected.parent(), true);
        }
    }

    private void activateSelected() {
        if (this.selected == null) {
            return;
        }
        if (this.selected.hasChildren() && !this.selected.isFile()) {
            toggleExpand(this.selected);
        } else {
            toggleSelected();
        }
    }

    // ------------------------------------------------------------------ TreeView.Host

    @Override
    public List<ResourceTree.Row> rows() {
        return this.tree == null ? List.of() : this.tree.rows();
    }

    @Override
    public ResourceNode selectedNode() {
        return this.selected;
    }

    @Override
    public void selectNode(ResourceNode node) {
        selectNode(node, true);
    }

    private void selectNode(ResourceNode node, boolean reveal) {
        this.selected = node;
        this.bulkConfirmNode = null;
        if (node != null && node.isSoundEvent()) {
            this.tuningKey = soundKeyOf(node);
            ResourceManagerConfig.SoundTuning tuning = this.config.sound(this.tuningKey);
            this.pendingVolume = tuning.volume();
            this.pendingPitch = tuning.pitch();
        } else {
            this.tuningKey = null;
        }
        if (reveal && node != null && this.tree != null) {
            this.tree.reveal(node);
            this.treeView.reveal(node);
        }
    }

    @Override
    public void toggleExpand(ResourceNode node) {
        if (!node.hasChildren()) {
            return;
        }
        boolean expanded = !node.expanded();
        node.setExpanded(expanded);
        if (expanded) {
            this.expandedKeys.add(expandedKey(node));
        } else {
            this.expandedKeys.remove(expandedKey(node));
        }
        if (this.tree != null) {
            this.tree.refresh();
        }
    }

    @Override
    public String badgeOf(ResourceNode node) {
        return switch (node.kind()) {
            case PACK -> {
                String badge = "#" + node.priority();
                yield node.disabledCount() > 0 ? badge + " \u00b7 " + node.disabledCount() + " off" : badge;
            }
            case NAMESPACE, DIRECTORY -> node.disabledCount() > 0
                    ? node.fileCount() + " \u00b7 " + node.disabledCount() + " off"
                    : String.valueOf(node.fileCount());
            case FILE -> {
                if (isDisabled(node)) {
                    yield Component.translatable("resourcemanager.ui.badge.off").getString();
                }
                if (node.isSoundDefinition()) {
                    yield node.children().size() + " ev";
                }
                String winner = winnerName(node);
                if (winner == null) {
                    yield Component.translatable("resourcemanager.ui.badge.missing").getString();
                }
                yield winner.equals(node.packId())
                        ? Component.translatable("resourcemanager.ui.badge.active").getString()
                        : Component.translatable("resourcemanager.ui.badge.shadowed").getString();
            }
            case SOUND -> this.tunedKeys.contains(soundKeyOf(node))
                    ? Component.translatable("resourcemanager.ui.badge.tuned").getString()
                    : null;
        };
    }

    @Override
    public int badgeColorOf(ResourceNode node) {
        return switch (node.kind()) {
            case PACK, NAMESPACE, DIRECTORY -> node.disabledCount() > 0 ? Ui.WARN : Ui.OFF;
            case SOUND -> Ui.ACCENT;
            case FILE -> {
                if (isDisabled(node)) {
                    yield Ui.WARN;
                }
                if (node.isSoundDefinition()) {
                    yield Ui.MUTED;
                }
                String winner = winnerName(node);
                if (winner == null) {
                    yield Ui.WARN;
                }
                yield winner.equals(node.packId()) ? Ui.OK : Ui.OFF;
            }
        };
    }

    @Override
    public int nameColorOf(ResourceNode node) {
        if (node.kind() == ResourceNode.Kind.FILE && isDisabled(node)) {
            return Ui.OFF;
        }
        if (node.kind() == ResourceNode.Kind.SOUND) {
            return Ui.ACCENT;
        }
        if (node.kind() == ResourceNode.Kind.PACK) {
            return node.disabledCount() > 0 ? Ui.WARN : Ui.TEXT;
        }
        if (node.kind() == ResourceNode.Kind.NAMESPACE || node.kind() == ResourceNode.Kind.DIRECTORY) {
            return Ui.DIM;
        }
        if (node.isFile() && winnerName(node) != null && !node.packId().equals(winnerName(node))) {
            return Ui.MUTED;
        }
        return Ui.TEXT;
    }

    @Override
    public boolean strikeThroughOf(ResourceNode node) {
        return node.isFile() && isDisabled(node);
    }

    // ------------------------------------------------------------------ DisabledList.Host

    @Override
    public List<String> disabledKeys() {
        return this.disabledKeys;
    }

    @Override
    public void activateDisabledKey(String key) {
        int separator = key.indexOf('|');
        if (separator < 0 || this.snapshot == null) {
            return;
        }
        String packId = key.substring(0, separator);
        String location = key.substring(separator + 1);
        ResourceLocation id = ResourceLocation.tryParse(location);
        if (id == null) {
            return;
        }
        for (ResourceNode candidate : this.snapshot.providersOf(id)) {
            if (candidate.packId().equals(packId)) {
                selectNode(candidate, true);
                return;
            }
        }
        this.statusMessage = "Not loaded: " + key;
    }
}
