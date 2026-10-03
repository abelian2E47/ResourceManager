package com.abelian.client;

import com.abelian.client.ui.DisabledList;
import com.abelian.client.ui.DragTarget;
import com.abelian.client.ui.PreviewTexture;
import com.abelian.client.ui.ScrollList;
import com.abelian.client.ui.TextKeyList;
import com.abelian.client.ui.TreeView;
import com.abelian.client.ui.Ui;
import com.abelian.client.ui.UiButton;
import com.abelian.client.ui.UiSlider;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * Resource Manager GUI.
 *
 * <p>Layout: a toolbar on top, a status bar at the bottom and three sibling panels in between —
 * filters/disabled-list, the pack tree and the inspector. Every widget is positioned from the rects
 * computed by {@link #layout()}, so panels and controls can never drift apart or overlap the text.
 *
 * <p>The disabled list has two sizes: the sidebar list and, through its expand button, a full window
 * view where entries can be filtered and restored. Selecting a {@code lang} file turns the inspector
 * into a text editor for that file's translation keys.
 */
public final class ResourceManagerScreen extends Screen implements TreeView.Host, DisabledList.Host,
        TextKeyList.Host {
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
    /** How long a fresh status message keeps its slot in the status bar. */
    private static final long STATUS_HOLD_MILLIS = 4000L;
    /**
     * Grace period after the runtime swaps its resource manager (F3+T, options screen, world change)
     * before the scan runs again, so the reload is not scanned while it is still in flight.
     */
    private static final long RESCAN_DELAY_MILLIS = 250L;

    private final Screen parent;
    private final ResourceManagerConfig config = ResourceManagerConfig.instance();

    private Ui.Rect toolbar = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect statusBar = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect sidebar;
    private Ui.Rect treePanel = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect inspector = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect filterHeader = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect disabledHeader = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect disabledPanel = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect disabledPanelHeader = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect disabledPanelBody = new Ui.Rect(0, 0, 0, 0);
    private Ui.Rect previewBox;
    private Ui.Rect inspectorInfo = new Ui.Rect(0, 0, 0, 0);
    private boolean sidebarOpen;
    private boolean disabledFullscreen;

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
    private UiButton expandDisabledButton;
    private UiButton collapseDisabledButton;
    private UiSlider volumeSlider;
    private UiSlider pitchSlider;
    private TreeView treeView;
    private DisabledList disabledList;
    private EditBox textFilter;
    private EditBox textValue;
    private TextKeyList textKeyList;
    private UiButton textApplyButton;
    private UiButton textRevertButton;
    private UiButton textAddKeyButton;
    private final List<UiButton> filterButtons = new ArrayList<>();
    private final PreviewTexture preview = new PreviewTexture();

    private ResourceIndex.Snapshot snapshot;
    private ResourceTree tree;
    private boolean scanning;
    private int scanGeneration;
    private String statusMessage = "";
    private String lastStatusMessage = "";
    private long statusMessageTime;
    /** Non-zero while a rescan is scheduled because the runtime replaced its resource manager. */
    private long rescanAt;
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

    /** Identity of what the thumbnail currently shows, so the pixels are read only when it changes. */
    private String previewIdentity = "";
    /** True when the previewed pixels come from the live manager (the file the game serves). */
    private boolean previewFromEffective;
    /** Keys of the {@code lang} file selected in the inspector, and the filtered subset that is drawn. */
    private List<String> langKeys = List.of();
    private List<String> langKeysFiltered = List.of();
    private String langKey;
    private String textEditStatus = "";
    private SoundInstance previewSound;
    private boolean lastPreviewActive;
    private String lastPreviewKey = "";
    private long previewSoundStart;

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

        this.expandDisabledButton = addRenderableWidget(new UiButton(this.font, 0, 0, 20, BUTTON,
                Component.translatable("resourcemanager.ui.disabled.expand"), this::toggleDisabledFullscreen));
        this.collapseDisabledButton = addRenderableWidget(new UiButton(this.font, 0, 0, 40, BUTTON,
                Component.translatable("resourcemanager.ui.disabled.back"), this::toggleDisabledFullscreen));

        this.textFilter = addRenderableWidget(new EditBox(this.font, 0, 0, 120, 12,
                Component.translatable("resourcemanager.ui.text.filter")));
        this.textFilter.setHint(Component.translatable("resourcemanager.ui.text.filter.hint"));
        this.textFilter.setMaxLength(120);
        this.textFilter.setResponder(value -> refreshLangKeys());
        this.textKeyList = addRenderableWidget(new TextKeyList(this.font, 0, 0, 100, 100, this));
        this.textValue = addRenderableWidget(new EditBox(this.font, 0, 0, 120, 12,
                Component.translatable("resourcemanager.ui.text.value")));
        this.textValue.setHint(Component.translatable("resourcemanager.ui.text.value.hint"));
        this.textValue.setMaxLength(400);
        this.textApplyButton = addRenderableWidget(new UiButton(this.font, 0, 0, 40, BUTTON,
                Component.translatable("resourcemanager.ui.text.apply"), this::applyTextEdit));
        this.textRevertButton = addRenderableWidget(new UiButton(this.font, 0, 0, 40, BUTTON,
                Component.translatable("resourcemanager.ui.text.revert"), this::revertTextEdit));
        this.textAddKeyButton = addRenderableWidget(new UiButton(this.font, 0, 0, 40, BUTTON,
                Component.translatable("resourcemanager.ui.text.addKey"), this::addTextKey));

        layout();
        // Show the previous scan at once when there is one, then refresh from the live manager: the
        // window is never empty waiting for the scan, and never keeps answering from a stale index.
        if (this.snapshot == null) {
            ResourceIndex.Snapshot cached = ResourceIndex.cachedSnapshot();
            if (cached != null) {
                applySnapshot(cached);
            }
        }
        if (!this.scanning) {
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
        LangText.clearCache();
        this.previewIdentity = "";
        this.snapshot = result;
        this.tree = new ResourceTree(result.packNodesHighestFirst(),
                node -> this.config.isDisabled(node.packId(), node.resourceId()));
        this.tree.setFilter(this.query, this.category);
        applyExpandedState();
        this.selected = null;
        this.tuningKey = null;
        onSelectionChanged(null);
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

        boolean showSidebar = !this.disabledFullscreen && this.sidebarOpen && available >= SIDEBAR_MIN_WIDTH;
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
        this.sidebar = null;
        this.treePanel = new Ui.Rect(0, 0, 0, 0);
        this.inspector = new Ui.Rect(0, 0, 0, 0);
        if (!this.disabledFullscreen) {
            if (showSidebar) {
                this.sidebar = new Ui.Rect(x, contentTop, sidebarWidth, contentHeight);
                x += sidebarWidth + PANEL_GAP;
            }
            this.treePanel = new Ui.Rect(x, contentTop, treeWidth, contentHeight);
            x += treeWidth + PANEL_GAP;
            this.inspector = new Ui.Rect(x, contentTop, inspectorWidth, contentHeight);
        }
        // The expanded disabled list takes the whole content area instead of the three panels.
        this.disabledPanel = new Ui.Rect(PAD, contentTop, available, contentHeight);

        layoutToolbar(compact);
        layoutSidebar();
        layoutTree();
        layoutInspector();
        layoutDisabledView();
    }

    private void layoutToolbar(boolean compact) {
        int y = this.toolbar.y() + (TOOLBAR_HEIGHT - BUTTON) / 2;
        int gap = 4;

        this.sidebarButton.setMessage(Component.translatable("resourcemanager.ui.sidebar.show"));
        this.sidebarButton.setToggled(this.sidebarOpen);
        this.categoryButton.setMessage(Component.translatable("resourcemanager.ui.filter.cycle",
                Component.translatable(this.category.translationKey())));
        this.categoryButton.visible = this.sidebar == null && !this.disabledFullscreen;

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
            this.expandDisabledButton.visible = false;
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
        this.expandDisabledButton.visible = showDisabledList;
        if (showDisabledList) {
            Ui.place(this.clearButton, innerX + 4, footerTop, innerWidth - 8, BUTTON);
            Ui.place(this.disabledList, innerX + 3, this.disabledHeader.bottom() + 1, innerWidth - 6,
                    Math.max(12, footerTop - 4 - (this.disabledHeader.bottom() + 1)));
            int expandWidth = Ui.clamp(this.font.width(this.expandDisabledButton.getMessage()) + 8, 18, 46);
            Ui.place(this.expandDisabledButton, this.disabledHeader.right() - expandWidth - 1,
                    this.disabledHeader.y() + 1, expandWidth, HEADER_HEIGHT - 2);
        }
    }

    private ResourceCategory filterCategory(UiButton button) {
        int index = this.filterButtons.indexOf(button);
        return index < 0 ? ResourceCategory.ALL : ResourceCategory.values()[index];
    }

    private void layoutTree() {
        Ui.Rect body = treeBody();
        this.treeView.visible = body.w() > 0;
        Ui.place(this.treeView, body.x() + 1, body.y() + 1, Math.max(20, body.w() - 2), Math.max(12, body.h() - 2));
    }

    /** Places the expanded disabled list and its header buttons. */
    private void layoutDisabledView() {
        this.collapseDisabledButton.visible = this.disabledFullscreen;
        if (!this.disabledFullscreen) {
            return;
        }
        int headerHeight = BUTTON + 4;
        this.disabledPanelHeader = new Ui.Rect(this.disabledPanel.x() + 1, this.disabledPanel.y() + 1,
                this.disabledPanel.w() - 2, headerHeight);
        int bodyTop = this.disabledPanelHeader.bottom() + 1;
        this.disabledPanelBody = new Ui.Rect(this.disabledPanel.x() + 1, bodyTop, this.disabledPanel.w() - 2,
                Math.max(12, this.disabledPanel.bottom() - 1 - bodyTop));
        int buttonY = this.disabledPanelHeader.y() + 2;
        int backWidth = Ui.clamp(this.font.width(this.collapseDisabledButton.getMessage()) + 10, 40, 90);
        int clearWidth = Ui.clamp(this.font.width(this.clearButton.getMessage()) + 10, 40, 130);
        Ui.place(this.collapseDisabledButton, this.disabledPanelHeader.right() - 3 - backWidth, buttonY, backWidth,
                BUTTON);
        Ui.place(this.clearButton, this.disabledPanelHeader.right() - 7 - backWidth - clearWidth, buttonY, clearWidth,
                BUTTON);
        this.clearButton.visible = true;
        Ui.place(this.disabledList, this.disabledPanelBody.x() + 1, this.disabledPanelBody.y() + 1,
                this.disabledPanelBody.w() - 2, Math.max(12, this.disabledPanelBody.h() - 2));
        this.disabledList.visible = true;
    }

    private Ui.Rect treeHeader() {
        return new Ui.Rect(this.treePanel.x() + 1, this.treePanel.y() + 1, this.treePanel.w() - 2, HEADER_HEIGHT);
    }

    private Ui.Rect treeBody() {
        if (this.treePanel.w() <= 0 || this.treePanel.h() <= 0) {
            return new Ui.Rect(0, 0, 0, 0);
        }
        Ui.Rect header = treeHeader();
        return new Ui.Rect(header.x(), header.bottom(), header.w(),
                Math.max(12, this.treePanel.bottom() - 1 - header.bottom()));
    }

    private void layoutInspector() {
        if (this.inspector.w() <= 0) {
            this.hideInspectorWidgets();
            return;
        }
        int innerX = this.inspector.x() + 1;
        int innerWidth = this.inspector.w() - 2;
        int infoTop = this.inspector.y() + 1 + HEADER_HEIGHT + 4;
        boolean textMode = this.selected != null && LangText.isLangFile(this.selected.path());
        boolean hasSound = this.selected != null && this.selected.isSoundEvent();
        this.toggleButton.visible = canToggle(this.selected) && !textMode;
        this.providerButton.visible = this.selected != null && this.selected.isFile() && this.snapshot != null
                && this.snapshot.providersOf(this.selected.location()).size() > 1 && !textMode;
        layoutTextEditor(innerX, innerWidth, infoTop, textMode);

        if (textMode) {
            this.volumeSlider.visible = false;
            this.pitchSlider.visible = false;
            this.playButton.visible = false;
            this.resetButton.visible = false;
            return;
        }

        int soundHeight = hasSound ? HEADER_HEIGHT + SLIDER_HEIGHT * 2 + 6 + BUTTON : 0;
        int soundTop = hasSound ? this.inspector.bottom() - 4 - soundHeight : this.inspector.bottom() - 4;

        int buttonCount = (this.toggleButton.visible ? 1 : 0) + (this.providerButton.visible ? 1 : 0);
        int actionHeight = buttonCount > 0 ? BUTTON : 0;
        int actionTop = soundTop - 6 - actionHeight;
        this.toggleButton.setMessage(toggleLabel());
        if (buttonCount > 0) {
            int budget = innerWidth - 8;
            int first;
            if (buttonCount == 1) {
                first = budget;
            } else {
                int wanted = this.font.width(toggleLabel()) + 10;
                int wantedSecond = this.font.width(this.providerButton.getMessage()) + 10;
                int gap = 4;
                first = Math.max(40, Math.min(wanted, budget - gap - Math.min(wantedSecond, budget / 2)));
            }
            Ui.place(this.toggleButton, innerX + 4, actionTop, first, BUTTON);
            if (this.providerButton.visible) {
                Ui.place(this.providerButton, innerX + 4 + first + 4, actionTop,
                        Math.max(40, innerWidth - 8 - first - 4), BUTTON);
            }
        }

        this.inspectorInfo = new Ui.Rect(innerX + 4, infoTop, innerWidth - 8,
                Math.max(10, actionTop - 4 - infoTop));

        this.volumeSlider.visible = hasSound;
        this.pitchSlider.visible = hasSound;
        this.playButton.visible = hasSound;
        this.resetButton.visible = hasSound;
        if (hasSound) {
            int width = (innerWidth - 8 - 4) / 2;
            Ui.place(this.volumeSlider, innerX + 4, soundTop + HEADER_HEIGHT, innerWidth - 8, SLIDER_HEIGHT);
            Ui.place(this.pitchSlider, innerX + 4, soundTop + HEADER_HEIGHT + SLIDER_HEIGHT + 3, innerWidth - 8,
                    SLIDER_HEIGHT);
            Ui.place(this.resetButton, innerX + 4, soundTop + HEADER_HEIGHT + (SLIDER_HEIGHT + 3) * 2, width, BUTTON);
            Ui.place(this.playButton, innerX + 8 + width, soundTop + HEADER_HEIGHT + (SLIDER_HEIGHT + 3) * 2, width,
                    BUTTON);
        }

        // Thumbnail of the inspected texture, above the information lines.
        boolean image = this.selected != null && this.selected.isFile() && isImageFile(this.selected.path());
        int previewHeight = image ? Ui.clamp(Math.min(innerWidth - 8, 104), 24, 104) : 0;
        this.previewBox = image
                ? new Ui.Rect(this.inspectorInfo.x() + (this.inspectorInfo.w() - previewHeight) / 2,
                        this.inspectorInfo.y(), previewHeight, previewHeight)
                : null;
        if (this.previewBox != null) {
            this.inspectorInfo = new Ui.Rect(this.inspectorInfo.x(), this.previewBox.bottom() + 4,
                    this.inspectorInfo.w(), Math.max(10, this.inspectorInfo.bottom() - this.previewBox.bottom() - 4));
        }
    }

    private void hideInspectorWidgets() {
        this.toggleButton.visible = false;
        this.providerButton.visible = false;
        this.volumeSlider.visible = false;
        this.pitchSlider.visible = false;
        this.playButton.visible = false;
        this.resetButton.visible = false;
        this.textFilter.visible = false;
        this.textKeyList.visible = false;
        this.textValue.visible = false;
        this.textApplyButton.visible = false;
        this.textRevertButton.visible = false;
        this.textAddKeyButton.visible = false;
        this.inspectorInfo = new Ui.Rect(0, 0, 0, 0);
        this.previewBox = null;
    }

    /** The translation key editor fills the inspector from top to bottom. */
    private void layoutTextEditor(int innerX, int innerWidth, int infoTop, boolean textMode) {
        this.textFilter.visible = textMode;
        this.textKeyList.visible = textMode;
        this.textValue.visible = textMode;
        this.textApplyButton.visible = textMode;
        this.textRevertButton.visible = textMode;
        this.textAddKeyButton.visible = textMode;
        if (!textMode) {
            return;
        }
        this.previewBox = null;
        int x = innerX + 4;
        int width = Math.max(40, innerWidth - 8);
        int bottom = this.inspector.bottom() - 4;
        // The information block always shows name + pack + keys + key + file value + game value + status,
        // so it gets whatever is left after the filter, the key list, the value box and the two buttons.
        int reserved = 12 + 24 + 12 + BUTTON + 12;
        int infoHeight = Ui.clamp(Math.min(80, bottom - infoTop - reserved), 32, 80);
        this.inspectorInfo = new Ui.Rect(x, infoTop, width, infoHeight);
        // "Add key" sits next to the filter box: it starts editing the key typed there, which covers the
        // keys a pack does not declare (a mod string that has no translation, for instance).
        int addWidth = Ui.clamp(this.font.width(this.textAddKeyButton.getMessage()) + 10, 40, 72);
        Ui.place(this.textAddKeyButton, x + width - addWidth, this.inspectorInfo.bottom() + 3, addWidth, 12);
        Ui.place(this.textFilter, x, this.inspectorInfo.bottom() + 3, width - addWidth - 4, 12);
        int buttonsTop = bottom - BUTTON;
        int valueTop = buttonsTop - 3 - 12;
        int listTop = this.inspectorInfo.bottom() + 3 + 12 + 3;
        Ui.place(this.textKeyList, x, listTop, width, Math.max(12, valueTop - 3 - listTop));
        Ui.place(this.textValue, x, valueTop, width, 12);
        int half = (width - 4) / 2;
        Ui.place(this.textApplyButton, x, buttonsTop, half, BUTTON);
        Ui.place(this.textRevertButton, x + half + 4, buttonsTop, width - half - 4, BUTTON);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        syncConfig();
        syncRuntime();
        layout();
        saveTuningIfIdle();
        checkPreviewSound();

        renderBackground(graphics, mouseX, mouseY, delta);
        renderToolbar(graphics);
        if (this.disabledFullscreen) {
            renderDisabledView(graphics);
        } else {
            if (this.sidebar != null) {
                renderSidebar(graphics);
            }
            renderTreePanel(graphics);
            renderInspector(graphics);
        }
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
            // The title is trimmed so that it never runs under the expand button of the panel.
            boolean expand = this.expandDisabledButton.visible;
            int reserved = expand ? this.expandDisabledButton.getWidth() + 8 : 4;
            String title = Ui.trim(this.font,
                    Component.translatable("resourcemanager.ui.panel.disabled", this.config.disabledCount())
                            .getString(),
                    this.disabledHeader.w() - reserved);
            Ui.header(graphics, this.font, this.disabledHeader, Component.literal(title),
                    this.config.disabledCount() > 0 ? Ui.WARN : Ui.MUTED);
            if (this.config.disabledCount() == 0) {
                graphics.drawString(this.font, Component.translatable("resourcemanager.ui.empty.disabled"),
                        this.disabledList.getX() + 3, this.disabledList.getY() + 2, Ui.OFF, false);
            }
        }
    }

    /** Full window view of the disabled entries. */
    private void renderDisabledView(GuiGraphics graphics) {
        Ui.panel(graphics, this.disabledPanel, Ui.PANEL, Ui.BORDER);
        boolean expand = this.expandDisabledButton.visible;
        int reserved = (expand ? this.expandDisabledButton.getWidth() : 0)
                + this.collapseDisabledButton.getWidth() + this.clearButton.getWidth() + 24;
        // While the search box filters the entries the header shows both numbers, so a short list is
        // never mistaken for a stale count.
        boolean filtered = this.disabledKeys.size() < this.config.disabledCount();
        String titleText = filtered
                ? Component.translatable("resourcemanager.ui.panel.disabledFiltered", this.disabledKeys.size(),
                        this.config.disabledCount()).getString()
                : Component.translatable("resourcemanager.ui.panel.disabled", this.config.disabledCount()).getString();
        String title = Ui.trim(this.font, titleText, this.disabledPanelHeader.w() - reserved);
        Ui.header(graphics, this.font, this.disabledPanelHeader, Component.literal(title),
                this.config.disabledCount() > 0 ? Ui.WARN : Ui.MUTED);
        graphics.fill(this.disabledPanelBody.x(), this.disabledPanelBody.y(), this.disabledPanelBody.right(),
                this.disabledPanelBody.bottom(), Ui.BODY);
        if (this.disabledKeys.isEmpty()) {
            String message = this.config.disabledCount() == 0
                    ? Component.translatable("resourcemanager.ui.empty.disabled").getString()
                    : Component.translatable("resourcemanager.ui.empty.disabledFiltered").getString();
            graphics.drawString(this.font, message, this.disabledPanelBody.x() + 6, this.disabledPanelBody.y() + 5,
                    Ui.MUTED, false);
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
        boolean textMode = this.selected != null && LangText.isLangFile(this.selected.path());
        Ui.header(graphics, this.font,
                new Ui.Rect(this.inspector.x() + 1, this.inspector.y() + 1, this.inspector.w() - 2, HEADER_HEIGHT),
                Component.translatable(textMode ? "resourcemanager.ui.panel.inspector.text"
                        : "resourcemanager.ui.panel.inspector"),
                Ui.TEXT);

        int x = this.inspectorInfo.x();
        int width = this.inspectorInfo.w();
        int y = this.inspectorInfo.y();
        int limit = this.inspectorInfo.bottom();

        if (this.selected == null) {
            drawWrapped(graphics, Component.translatable("resourcemanager.ui.selectHint"), x, y, width, Ui.MUTED, 4,
                    limit);
            return;
        }
        if (textMode) {
            renderTextInfo(graphics, x, y, width, limit);
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
            // Authoritative line: the live manager is asked which pack serves the file at this moment.
            String serving = servingPack(node);
            y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.serving"),
                    servingLabel(node), serving == null || !serving.equals(winner) ? Ui.WARN : Ui.OK);
        }
        if (isServerPack(node.packId())) {
            y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.source"),
                    Component.translatable("resourcemanager.ui.info.sourceServer").getString(), Ui.ACCENT);
        }
        y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.state"),
                stateText(node), stateColor(node));
        if (this.previewBox != null) {
            ensurePreview(node);
            this.preview.render(graphics, this.previewBox);
            if (this.preview.hasImage()) {
                // Caption strip inside the frame: the strip below it carries the inspector text, and a
                // long file name would otherwise run into the size label.
                String size = this.preview.sourceWidth() + "x" + this.preview.sourceHeight();
                String source = Component.translatable(this.previewFromEffective
                        ? "resourcemanager.ui.preview.effective"
                        : "resourcemanager.ui.preview.ownCopy").getString();
                int captionY = this.previewBox.bottom() - 11;
                graphics.fill(this.previewBox.x() + 1, captionY - 1, this.previewBox.right() - 1, captionY + 9,
                        Ui.PANEL);
                int room = this.previewBox.w() - this.font.width(size) - 10;
                graphics.drawString(this.font, Ui.trim(this.font, source, room), this.previewBox.x() + 3, captionY,
                        this.previewFromEffective ? Ui.OK : Ui.WARN, false);
                graphics.drawString(this.font, size, this.previewBox.right() - 3 - this.font.width(size), captionY,
                        Ui.OFF, false);
            }
        }
    }

    /** Inspector body for a {@code lang} file: what the file declares and what the game shows now. */
    private void renderTextInfo(GuiGraphics graphics, int x, int y, int width, int limit) {
        ResourceNode node = this.selected;
        String language = LangText.languageOf(node.path());
        y = drawWrapped(graphics, Component.literal(node.name() + "  (" + language + ")"), x, y, width, Ui.TEXT, 1,
                limit) + 2;
        y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.pack"),
                node.packId(), Ui.MUTED);
        y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.keys"),
                Component.translatable("resourcemanager.ui.info.keysValue", this.langKeys.size(),
                        this.config.textCount()).getString(), Ui.MUTED);
        y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.selectedKey"),
                this.langKey == null ? "-" : this.langKey, Ui.ACCENT);
        Map<String, String> values = currentLangValues();
        String fileValue = this.langKey == null ? null : values.get(this.langKey);
        String effective = this.langKey == null ? null : LangText.effective(this.langKey);
        if (this.langKey != null) {
            y = drawInfoLine(graphics, x, y, width, limit, Component.translatable("resourcemanager.ui.info.fileValue"),
                    valueOrDash(fileValue), Ui.MUTED);
            y = drawInfoLine(graphics, x, y, width, limit,
                    Component.translatable("resourcemanager.ui.info.effectiveValue"), valueOrDash(effective),
                    Ui.OK);
        }
        // The status is the one line that may be dropped, but never one that may overlap the lines above.
        if (!this.textEditStatus.isEmpty() && y + 9 <= limit) {
            graphics.drawString(this.font, Ui.trim(this.font, this.textEditStatus, width), x, y, Ui.ACCENT, false);
        }
    }

    private static String valueOrDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
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
        refreshStatusTime();
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
        if (this.config.textCount() > 0) {
            left = left + "  \u00b7  " + Component.translatable("resourcemanager.ui.status.textCount",
                    this.config.textCount()).getString();
        }
        graphics.drawString(this.font, Ui.trim(this.font, left, this.statusBar.w() / 2), this.statusBar.x(),
                this.statusBar.y() + 2, Ui.MUTED, false);

        // A fresh message (preview result, restored entry, applied text) wins over the generic hints for a while.
        boolean fresh = !this.statusMessage.isEmpty() && Util.getMillis() - this.statusMessageTime < STATUS_HOLD_MILLIS;
        String right;
        if (fresh) {
            right = this.statusMessage;
        } else if (this.pendingChanges > 0) {
            right = Component.translatable("resourcemanager.ui.status.pending", this.pendingChanges).getString();
        } else {
            right = Component.translatable(this.disabledFullscreen ? "resourcemanager.ui.hints.disabled"
                    : "resourcemanager.ui.hints").getString();
        }
        int color = fresh ? Ui.ACCENT : this.pendingChanges > 0 ? Ui.WARN : Ui.OFF;
        // The hint is the first thing to drop when the window is too narrow for both halves.
        int leftWidth = this.font.width(Ui.trim(this.font, left, this.statusBar.w() / 2));
        if (fresh || this.pendingChanges > 0 || leftWidth + 8 + this.font.width(right) <= this.statusBar.w()) {
            graphics.drawString(this.font, right, this.statusBar.right() - this.font.width(right),
                    this.statusBar.y() + 2, color, false);
        }
    }

    /** Remembers when the status message last changed, so a fresh message can outrank the standing hints. */
    private void refreshStatusTime() {
        if (!this.statusMessage.equals(this.lastStatusMessage)) {
            this.lastStatusMessage = this.statusMessage;
            this.statusMessageTime = Util.getMillis();
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
        refreshDisabledKeys();
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
        refreshDisabledKeys();
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

    /**
     * Keeps what is displayed tied to the live resource manager. The game replaces its manager whenever
     * resources are reloaded (F3+T, the options screen, joining or leaving a world), and the scan is only
     * a snapshot of one manager, so a swap means a refresh — otherwise the GUI would keep reporting an
     * index that no longer describes the game.
     */
    private void syncRuntime() {
        if (this.scanning) {
            return;
        }
        if (ResourceIndex.cachedSnapshot() == null || !ResourceIndex.isStale()) {
            this.rescanAt = 0L;
            return;
        }
        if (this.rescanAt == 0L) {
            this.rescanAt = Util.getMillis() + RESCAN_DELAY_MILLIS;
            return;
        }
        if (Util.getMillis() >= this.rescanAt) {
            this.rescanAt = 0L;
            startScan();
        }
    }

    /**
     * The pack the running resource manager serves this file from <em>right now</em>. This is the
     * authoritative answer — the config only describes what the next reload will do — which is why the
     * inspector shows it next to the pending state.
     */
    private String servingPack(ResourceNode node) {
        if (node == null || node.location() == null) {
            return null;
        }
        try {
            return Minecraft.getInstance().getResourceManager().getResource(node.location())
                    .map(resource -> ResourceManagerConfig.normalizePackId(resource.sourcePackId()))
                    .orElse(null);
        } catch (Exception error) {
            return null;
        }
    }

    /** Packs the server pushed to this client (their runtime ids start with {@code server/}). */
    private static boolean isServerPack(String packId) {
        return packId != null && packId.startsWith("server/");
    }

    /**
     * The "Serving now" text: the pack the live manager uses, plus the pack the config will use once the
     * packs are reloaded when the two differ. That difference is the whole delay the GUI can otherwise
     * misreport, so it is spelled out instead of being hidden.
     */
    private String servingLabel(ResourceNode node) {
        String serving = servingPack(node);
        if (serving == null) {
            return Component.translatable("resourcemanager.ui.info.servingNone").getString();
        }
        String winner = node == null ? null : winnerName(node);
        if (winner != null && !winner.equals(serving)) {
            return Component.translatable("resourcemanager.ui.info.servingPending", serving, winner).getString();
        }
        return serving;
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
            this.statusMessage = Component.translatable("resourcemanager.ui.preview.noSelection").getString();
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(this.tuningKey);
        if (id == null) {
            return;
        }
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        SoundPreview.Availability availability = SoundPreview.availability(manager, id);
        if (!availability.isPlayable()) {
            // The engine drops unresolvable events without a word, so say why nothing was heard.
            this.lastPreviewActive = false;
            this.lastPreviewKey = this.tuningKey;
            this.statusMessage = Component.translatable(availability.translationKey(), this.tuningKey).getString();
            return;
        }
        SoundInstance instance = SoundPreview.create(id);
        manager.play(instance);
        this.previewSound = instance;
        this.previewSoundStart = Util.getMillis();
        this.lastPreviewKey = this.tuningKey;
        this.statusMessage = Component.translatable("resourcemanager.ui.preview.starting", this.tuningKey).getString();
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
                    this.disabledList, this.textKeyList)) {
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
        // The search box only grabs typing when no other text field is active.
        if (isEditingText()) {
            return super.charTyped(character, modifiers);
        }
        if (this.search != null && !this.search.isFocused() && character >= ' '
                && Character.isLetterOrDigit(character)) {
            this.search.setFocused(true);
            this.search.insertText(String.valueOf(character));
            setFocused(this.search);
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    private boolean isEditingText() {
        return this.textValue != null && (this.textValue.isFocused() || this.textFilter.isFocused());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.disabledFullscreen) {
                toggleDisabledFullscreen();
                return true;
            }
            onClose();
            return true;
        }
        if (this.textValue != null && this.textValue.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyTextEdit();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.textFilter != null && this.textFilter.isFocused()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_DOWN -> {
                    focusTextList(false);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    focusTextList(true);
                    return true;
                }
                default -> {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
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
        if (this.disabledFullscreen) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    this.disabledList.moveSelection(1);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    this.disabledList.moveSelection(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    this.disabledList.restoreSelected();
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
        onSelectionChanged(node);
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

    @Override
    public void restoreDisabledKey(String key) {
        int separator = key.indexOf('|');
        if (separator < 0) {
            return;
        }
        this.config.setDisabled(key.substring(0, separator), key.substring(separator + 1), false);
        this.config.save();
        this.pendingChanges++;
        this.statusMessage = Component.translatable("resourcemanager.ui.status.restored", key.replace('|', ' '))
                .getString();
    }

    @Override
    public boolean disabledExpanded() {
        return this.disabledFullscreen;
    }

    /** Swaps between the sidebar list and the full window view of the disabled entries. */
    private void toggleDisabledFullscreen() {
        this.disabledFullscreen = !this.disabledFullscreen;
        this.disabledList.select(-1);
        refreshDisabledKeys();
        layout();
    }

    /**
     * The list of disabled keys, restricted to the search query while the expanded view is open so that
     * the toolbar search box filters the entries the same way it filters the tree.
     */
    private void refreshDisabledKeys() {
        TreeSet<String> keys = new TreeSet<>(this.config.disabledKeys());
        if (this.disabledFullscreen) {
            String needle = this.query == null ? "" : this.query.trim().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty()) {
                keys.removeIf(key -> !key.toLowerCase(Locale.ROOT).replace('|', ' ').contains(needle));
            }
        }
        this.disabledKeys = List.copyOf(keys);
        // init() restores the search value before the list exists, so this must stay null safe.
        if (this.disabledList != null) {
            this.disabledList.clampScroll();
        }
    }

    // ------------------------------------------------------------------ TextKeyList.Host

    @Override
    public List<String> textKeys() {
        return this.langKeysFiltered;
    }

    @Override
    public String selectedTextKey() {
        return this.langKey;
    }

    @Override
    public void selectTextKey(String key) {
        this.langKey = key;
        this.textEditStatus = "";
        String override = this.config.text(key);
        this.textValue.setValue(override == null ? "" : override);
    }

    @Override
    public boolean textOverridden(String key) {
        return this.config.text(key) != null;
    }

    @Override
    public String textValue(String key) {
        return LangText.effective(key);
    }

    /** Refreshes the key list whenever the selection or the filter changes. */
    private void refreshLangKeys() {
        String filter = this.textFilter == null ? "" : this.textFilter.getValue().trim().toLowerCase(Locale.ROOT);
        if (filter.isEmpty() || this.langKeys.isEmpty()) {
            this.langKeysFiltered = this.langKeys;
        } else {
            Map<String, String> values = currentLangValues();
            List<String> filtered = new ArrayList<>();
            for (String key : this.langKeys) {
                String value = values.get(key);
                if (key.toLowerCase(Locale.ROOT).contains(filter)
                        || (value != null && value.toLowerCase(Locale.ROOT).contains(filter))) {
                    filtered.add(key);
                }
            }
            this.langKeysFiltered = List.copyOf(filtered);
        }
        this.textKeyList.resetScroll();
    }

    /** Values declared by the selected {@code lang} file, parsed once and cached. */
    private Map<String, String> currentLangValues() {
        ResourceNode node = this.selected;
        if (node == null || this.snapshot == null || !LangText.isLangFile(node.path())) {
            return Map.of();
        }
        return LangText.values(node.packId(), node.location(),
                resource(this.snapshot.packResources(node.packId()), node.location()));
    }

    private void applyTextEdit() {
        if (this.langKey == null) {
            this.textEditStatus = Component.translatable("resourcemanager.ui.text.noKey").getString();
            return;
        }
        if (this.textValue.isFocused()) {
            this.textValue.setFocused(false);
            setFocused(null);
        }
        this.config.setText(this.langKey, this.textValue.getValue());
        this.config.save();
        this.pendingChanges++;
        this.textEditStatus = Component.translatable("resourcemanager.ui.text.applied", this.langKey).getString();
    }

    private void revertTextEdit() {
        if (this.langKey == null) {
            this.textEditStatus = Component.translatable("resourcemanager.ui.text.noKey").getString();
            return;
        }
        this.config.setText(this.langKey, null);
        this.config.save();
        this.pendingChanges++;
        this.textValue.setValue("");
        this.textEditStatus = Component.translatable("resourcemanager.ui.text.reverted", this.langKey).getString();
    }

    /**
     * Starts editing the key typed into the filter box, even when no loaded {@code lang} file declares it:
     * that is how a string that simply has no translation yet (a mod item name, say) gets one.
     */
    private void addTextKey() {
        String key = this.textFilter == null ? "" : this.textFilter.getValue().trim();
        if (key.isEmpty() || key.indexOf('.') < 0) {
            this.textEditStatus = Component.translatable("resourcemanager.ui.text.addKeyHint").getString();
            return;
        }
        selectTextKey(key);
        this.textEditStatus = this.config.text(key) != null
                ? Component.translatable("resourcemanager.ui.text.applied", key).getString()
                : Component.translatable("resourcemanager.ui.text.addedKey", key).getString();
    }

    /** Leaves the key filter and hands keyboard navigation to the key list. */
    private void focusTextList(boolean fromBottom) {
        if (this.textFilter != null) {
            this.textFilter.setFocused(false);
        }
        setFocused(null);
        if (this.langKeysFiltered.isEmpty()) {
            return;
        }
        selectTextKey(this.langKeysFiltered.get(fromBottom ? this.langKeysFiltered.size() - 1 : 0));
        this.textKeyList.scrollToRow(fromBottom ? this.langKeysFiltered.size() - 1 : 0);
    }

    // ------------------------------------------------------------------ preview

    /** Reads the pixels of the inspected file once per selection. */
    private void ensurePreview(ResourceNode node) {
        String identity = node.packId() + "|" + node.location() + "|" + this.config.version() + "|"
                + System.identityHashCode(Minecraft.getInstance().getResourceManager());
        if (identity.equals(this.previewIdentity)) {
            return;
        }
        this.previewIdentity = identity;
        if (this.snapshot == null || !isImageFile(node.path())) {
            this.preview.clear();
            return;
        }
        // The live manager answers with the file the game is really using right now, which is what a
        // preview is for: once pack A's copy is disabled, this shows pack B's copy.
        IoSupplier<InputStream> supplier = liveResource(node.location());
        this.previewFromEffective = supplier != null;
        if (supplier == null) {
            supplier = resource(this.snapshot.packResources(node.packId()), node.location());
        }
        this.preview.show(supplier);
    }

    /** The resource the running manager serves for this location, or {@code null} when nobody serves it. */
    private static IoSupplier<InputStream> liveResource(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        try {
            return Minecraft.getInstance().getResourceManager().getResource(location)
                    .map(resource -> (IoSupplier<InputStream>) resource::open)
                    .orElse(null);
        } catch (Exception error) {
            return null;
        }
    }

    private static boolean isImageFile(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static IoSupplier<InputStream> resource(PackResources pack, ResourceLocation location) {
        if (pack == null || location == null) {
            return null;
        }
        try {
            return pack.getResource(PackType.CLIENT_RESOURCES, location);
        } catch (Exception error) {
            return null;
        }
    }

    /** Loads the translation keys of the selected {@code lang} file. */
    private void onSelectionChanged(ResourceNode node) {
        this.previewIdentity = "";
        this.langKey = null;
        this.textEditStatus = "";
        boolean langFile = node != null && this.snapshot != null && LangText.isLangFile(node.path());
        if (!langFile) {
            this.langKeys = List.of();
            this.langKeysFiltered = List.of();
            if (this.textFilter != null) {
                this.textFilter.setValue("");
            }
            if (this.textValue != null) {
                this.textValue.setValue("");
            }
            return;
        }
        this.langKeys = LangText.keys(node.packId(), node.location(),
                resource(this.snapshot.packResources(node.packId()), node.location()));
        if (this.textFilter != null && !this.textFilter.getValue().isEmpty()) {
            this.textFilter.setValue("");
        } else {
            refreshLangKeys();
        }
        if (this.textValue != null) {
            this.textValue.setValue("");
        }
        this.statusMessage = Component.translatable("resourcemanager.ui.status.langFile", this.langKeys.size())
                .getString();
    }

    // ------------------------------------------------------------------ sound preview

    /** Tells the user whether the preview really started playing instead of failing silently. */
    private void checkPreviewSound() {
        if (this.previewSound == null || Util.getMillis() - this.previewSoundStart < 120L) {
            return;
        }
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        this.lastPreviewActive = manager.isActive(this.previewSound);
        this.lastPreviewKey = this.previewSound.getLocation().toString();
        this.previewSound = null;
        this.statusMessage = Component.translatable(this.lastPreviewActive
                ? "resourcemanager.ui.preview.playing"
                : "resourcemanager.ui.preview.silent", this.lastPreviewKey).getString();
    }

    /** True when the last preview was still being played one tick after it started. */
    public boolean previewActive() {
        return this.lastPreviewActive;
    }

    public String previewKey() {
        return this.lastPreviewKey;
    }

    public boolean disabledFullscreenActive() {
        return this.disabledFullscreen;
    }

    /** Pack the live resource manager serves the selected file from; empty when nothing serves it. */
    public String servingPackId() {
        String serving = this.selected == null ? null : servingPack(this.selected);
        return serving == null ? "" : serving;
    }

    /** Pack the config would let win the selected file once the packs are reloaded. */
    public String winnerPackId() {
        String winner = this.selected == null ? null : winnerName(this.selected);
        return winner == null ? "" : winner;
    }

    /** The inspector's "Serving now" line for the current selection. */
    public String servingText() {
        return this.selected == null || !this.selected.isFile() ? "" : servingLabel(this.selected);
    }

    /** True when the thumbnail shows the pixels the live manager serves (not a shadowed copy). */
    public boolean previewFromEffective() {
        return this.previewFromEffective;
    }

    public String statusMessage() {
        return this.statusMessage;
    }

    /** True when the thumbnail of the inspected texture has pixels. */
    public boolean previewReady() {
        return this.preview.hasImage();
    }

    public String previewSize() {
        return this.preview.sourceWidth() + "x" + this.preview.sourceHeight();
    }

    /** Translation keys of the selected {@code lang} file; empty when the inspector is not in text mode. */
    public List<String> translationKeys() {
        return this.langKeys;
    }

    public String selectedTranslationKey() {
        return this.langKey;
    }

    public String soundTuningKey() {
        return this.tuningKey;
    }

    public boolean textEditorVisible() {
        return this.textFilter != null && this.textFilter.visible && this.textApplyButton.visible;
    }

    /** Keys the key list currently shows: the file's keys, narrowed by the key-or-text filter. */
    public List<String> visibleTranslationKeys() {
        return this.langKeysFiltered;
    }

    /** The value the selected {@code lang} file declares for a key, without the GUI override. */
    public String fileTranslationValue(String key) {
        return currentLangValues().get(key);
    }

    public String textEditStatus() {
        return this.textEditStatus;
    }

    /** Fills the key filter box the way typing into it does, for the self check. */
    public void setTextFilter(String value) {
        if (this.textFilter != null) {
            this.textFilter.setValue(value);
        }
    }

    public String textFilterValue() {
        return this.textFilter == null ? "" : this.textFilter.getValue();
    }
}
