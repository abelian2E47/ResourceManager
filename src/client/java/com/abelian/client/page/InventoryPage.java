package com.abelian.client.page;

import com.abelian.client.*;
import com.abelian.client.catalog.*;
import com.abelian.client.catalog.SubjectCatalog.*;
import com.abelian.client.search.BilingualSearch;
import com.abelian.client.ui.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Object inventory and three direct tasks: appearance, display name and sound. */
public final class InventoryPage {
    private static final int SLOT = 22;
    private final ResourceManagerScreen host;
    private SubjectCatalog catalog;
    private int generation = -1, textVersion = -1;
    private String query = "",
            returnQuery = "",
            pack = "",
            appearancePack = "",
            previewSource = "",
            rawText = "";
    private int kind, mode, scrollRow, cursor, columns = 9, rows = 5;
    private Subject subject;
    private Asset asset;
    private AppearanceLayers appearance;
    private List<Subject> visible = List.of();
    private List<Asset> assets = List.of(), assetRows = List.of();
    private List<String> packRows = List.of();
    private final List<UiButton> kinds = new ArrayList<>(), modes = new ArrayList<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> controls =
            new ArrayList<>();
    private UiButton back, apply, play, stop, saveName, resetName, editFile, layerMode;
    private UiField name;
    private UiSlider volume, pitch;
    private LayerList layers;
    private AssetList list;
    private boolean compactLayers;
    private Ui.Rect frame = new Ui.Rect(0, 0, 0, 0),
            grid = frame,
            inspector = frame,
            preview = frame;
    private long previewLoads;

    public InventoryPage(ResourceManagerScreen host) {
        this.host = host;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("resourcemanager.inventory." + key, args).getString();
    }

    private UiButton button(String key, Runnable action) {
        var b = host.addButton(key, action);
        controls.add(b);
        return b;
    }

    public void createWidgets() {
        back = button("resourcemanager.inventory.back", this::back);
        layerMode =
                button("resourcemanager.inventory.layerMode", () -> compactLayers = !compactLayers);
        for (int i = 0; i < 4; i++) {
            final int value = i;
            kinds.add(
                    button(
                            "resourcemanager.inventory.kind." + i,
                            () -> {
                                kind = value;
                                refreshSearch();
                                scrollRow = cursor = 0;
                            }));
        }
        for (int i = 0; i < 3; i++) {
            final int value = i;
            modes.add(button("resourcemanager.inventory.mode." + i, () -> setMode(value)));
        }
        layers = host.add(new LayerList(host.font(), this::selectLayer));
        controls.add(layers);
        list = host.add(new AssetList());
        controls.add(list);
        apply = button("resourcemanager.inventory.apply", this::applyLayer);
        apply.setTooltip(tr("appearance.hint"));
        play = button("resourcemanager.ui.action.play", this::play);
        stop = button("resourcemanager.inventory.stop", PackSoundPreview::stop);
        editFile =
                button(
                        "resourcemanager.inventory.editFile",
                        () -> {
                            if (asset != null && !previewSource.isEmpty())
                                ResourceWorkspace.openAppearance(
                                        host.snapshot(),
                                        appearance,
                                        appearancePack,
                                        subject.key(),
                                        asset.location(),
                                        host::setStatus);
                        });
        name = host.addField(tr("name.hint"), 256);
        controls.add(name.widget());
        saveName =
                button(
                        "resourcemanager.inventory.name.save",
                        () -> host.setTextOverride(subject.translationKey(), name.value()));
        resetName =
                button(
                        "resourcemanager.inventory.name.reset",
                        () -> {
                            host.setTextOverride(subject.translationKey(), "");
                            name.setValue(subject.name());
                        });
        volume = slider("volume", 0, 8, false);
        pitch = slider("pitch", 0.25, 4, true);
    }

    private UiSlider slider(String label, double min, double max, boolean isPitch) {
        var slider =
                host.add(
                        new UiSlider(
                                        host.font(),
                                        0,
                                        0,
                                        1,
                                        16,
                                        tr(label),
                                        min,
                                        max,
                                        "%.2f",
                                        new UiSlider.Value() {
                                            public double get() {
                                                var t = host.soundTuning(event());
                                                return isPitch ? t.pitch() : t.volume();
                                            }

                                            public void set(double value) {
                                                if (event().isEmpty()) return;
                                                var t = host.soundTuning(event());
                                                host.applySoundTuning(
                                                        event(),
                                                        isPitch
                                                                ? t.withPitch((float) value)
                                                                : t.withVolume((float) value));
                                            }
                                        })
                                .setCenter(1));
        controls.add(slider);
        return slider;
    }

    private void refresh() {
        if (host.snapshot() == null) return;
        if (generation != host.indexGeneration()) {
            generation = host.indexGeneration();
            catalog = new SubjectCatalog(host.snapshot());
            query = "\0";
            if (subject != null) {
                String key = subject.key();
                subject =
                        catalog.subjects().stream()
                                .filter(s -> s.key().equals(key))
                                .findFirst()
                                .orElse(null);
                if (subject != null) loadSubject();
            }
        }
        if (!query.equals(host.searchQuery()) || textVersion != host.config().textVersion()) {
            query = host.searchQuery();
            textVersion = host.config().textVersion();
            if (subject == null) {
                var parsed = BilingualSearch.parse(query);
                visible =
                        catalog.subjects().stream()
                                .filter(s -> kind == 0 || s.kind().ordinal() == kind - 1)
                                .filter(s -> matches(s, parsed))
                                .toList();
                cursor = Math.min(cursor, Math.max(0, visible.size() - 1));
                scrollRow = Ui.clamp(scrollRow, 0, maxScroll());
            } else updateRows();
        }
    }

    private boolean matches(Subject s, BilingualSearch.Query parsed) {
        if (parsed.isEmpty()) return true;
        String text = (s.id() + " " + s.name() + " " + s.translationKey()).toLowerCase(Locale.ROOT);
        for (var token : parsed.tokens())
            if (!text.contains(token.raw().toLowerCase(Locale.ROOT))
                    && token.ids().stream().noneMatch(id -> s.id().getPath().contains(id)))
                return false;
        return true;
    }

    private void loadSubject() {
        String selectedKey = asset == null ? "" : asset.key();
        assets = catalog.assets(subject);

        appearance = new AppearanceLayers(host.snapshot(), subject, assets);
        var choice = host.config().appearance(subject.key());
        appearancePack =
                choice != null && appearance.packs().contains(choice.pack())
                        ? choice.pack()
                        : appearance.packs().stream().findFirst().orElse("");
        updateRows();
        if (!assetRows.isEmpty())
            selectAsset(
                    assetRows.stream()
                            .filter(a -> a.key().equals(selectedKey))
                            .findFirst()
                            .orElse(assetRows.get(0)));
        else clearSelection();
        name.setValue(subject.name());
    }

    public void open(Subject value) {
        refresh();
        subject = value;
        mode = 0;
        compactLayers = false;
        returnQuery = host.searchQuery();
        host.setSearchQuery("");
        query = "";
        loadSubject();
        list.resetScroll();
        host.focus(null);
    }

    public void back() {
        PackSoundPreview.stop();
        subject = null;
        asset = null;
        host.setSearchQuery(returnQuery);
        refreshSearch();
        host.focusSearch();
    }

    public void setMode(int value) {
        mode = value;
        compactLayers = false;
        PackSoundPreview.stop();
        host.setSearchQuery("");
        updateRows();
        list.resetScroll();
        if (!assetRows.isEmpty()) selectAsset(assetRows.get(0));
        else {
            asset = null;
            packRows = List.of();
            host.preview().clear();
        }
        host.focus(null);
    }

    private void updateRows() {
        String filter = host.searchQuery().trim().toLowerCase(Locale.ROOT);
        assetRows =
                assets.stream()
                        .filter(
                                a ->
                                        switch (mode) {
                                            case 1 -> a.group() == Group.NAME;
                                            case 2 ->
                                                    a.group() == Group.SOUND
                                                            && a.location()
                                                                    .getPath()
                                                                    .equals("sounds.json");
                                            default -> a.location().getPath().endsWith(".png");
                                        })
                        .filter(
                                a ->
                                        filter.isEmpty()
                                                || (a.location() + " " + a.label())
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(filter))
                        .toList();
        if (asset == null || !assetRows.contains(asset)) {
            if (assetRows.isEmpty()) clearSelection();
            else selectAsset(assetRows.get(0));
        }
    }

    private void clearSelection() {
        PackSoundPreview.stop();
        asset = null;
        packRows = List.of();
        pack = previewSource = rawText = "";
        host.preview().clear();
        layers.setLayers(packRows, "", "");
    }

    public void selectAsset(Asset value) {
        PackSoundPreview.stop();
        asset = value;
        rawText = "";
        host.preview().clear();
        if (mode == 0) {
            packRows = appearance.packs();
            selectLayer(appearancePack);
        } else {
            packRows =
                    mode == 2
                            ? catalog.soundPacks(event())
                            : host.snapshot().packs().stream()
                                    .sorted(
                                            Comparator.comparingInt(
                                                    ResourceIndex.PackInfo::priority))
                                    .map(ResourceIndex.PackInfo::id)
                                    .filter(
                                            id ->
                                                    mode == 2
                                                            ? catalog.declares(event(), id)
                                                            : nameValue(id) != null)
                                    .toList();
            String selected = mode == 2 ? host.config().soundLayer(event()) : "";
            selectLayer(
                    packRows.contains(selected)
                            ? selected
                            : packRows.stream().findFirst().orElse(""));
        }
    }

    private String nameValue(String id) {
        var source = FilteredPackResources.raw(host.snapshot().packResources(id));
        return source == null
                ? null
                : LangText.values(
                                id,
                                asset.location(),
                                source.getResource(PackType.CLIENT_RESOURCES, asset.location()))
                        .get(subject.translationKey());
    }

    public void selectLayer(String id) {
        PackSoundPreview.stop();
        pack = id;
        previewSource = "";
        if (mode == 0) {
            appearancePack = id;
            previewSource = appearance.source(id, asset.location());
            var source = FilteredPackResources.raw(host.snapshot().packResources(previewSource));
            if (source == null) host.preview().clear();
            else {
                host.preview()
                        .show(source.getResource(PackType.CLIENT_RESOURCES, asset.location()));
                previewLoads++;
            }
            rawText = previewSource.equals(id) ? "" : tr("appearance.fallback");
        } else if (mode == 1) rawText = Optional.ofNullable(nameValue(id)).orElse(subject.name());
        else rawText = event() + "\n" + tr("audio.selected");
        layers.setLayers(packRows, pack, appliedPack());
    }

    public void refreshAppearanceRules() {
        if (subject != null && appearance != null) {
            var chosen = host.config().appearance(subject.key());
            if (chosen != null && appearance.packs().contains(chosen.pack()))
                appearance.apply(host.config(), chosen.pack());
        }
    }

    private String appliedPack() {
        if (mode == 0) {
            var choice = host.config().appearance(subject.key());
            return choice == null ? "" : choice.pack();
        }
        return mode == 2 ? host.config().soundLayer(event()) : "";
    }

    public void applyLayer() {
        if (asset == null || pack.isEmpty()) return;
        if (mode == 0) {
            appearance.apply(host.config(), pack);
            host.reloadPacks();
        } else if (mode == 1) {
            host.setTextOverride(subject.translationKey(), rawText);
            name.setValue(subject.name());
        } else {
            host.config()
                    .setSoundLayer(
                            event(),
                            pack,
                            catalog.soundDefinition(event(), pack),
                            catalog.audioFiles(event(), pack));
            host.reloadPacks();
        }
    }

    private String event() {
        return asset == null ? "" : asset.eventKey();
    }

    public void play() {
        ResourceLocation file = catalog.soundFile(event(), pack);
        if (file == null) {
            host.setStatus(Component.literal(tr("audio.empty")));
            return;
        }
        String source =
                host.snapshot().providersOf(file).stream().anyMatch(n -> n.packId().equals(pack))
                        ? pack
                        : host.snapshot().providersOf(file).stream()
                                .filter(
                                        n ->
                                                !host.snapshot()
                                                        .packResources(n.packId())
                                                        .packId()
                                                        .startsWith("file/"))
                                .reduce((a, b) -> b)
                                .map(ResourceNode::packId)
                                .orElse("");
        if (source.isEmpty()) {
            host.setStatus(Component.literal(tr("audio.empty")));
            return;
        }
        PackSoundPreview.play(
                host.snapshot(), source, file, host.soundTuning(event()), host::setStatus);
    }

    public void layout() {
        refresh();
        var area = host.contentRect();
        int w = Math.min(subject == null ? 440 : 610, area.w()), h = Math.min(340, area.h());
        frame = new Ui.Rect(area.x() + (area.w() - w) / 2, area.y() + (area.h() - h) / 2, w, h);
        controls.forEach(host::hideWidget);
        int x = frame.x() + 8, y = frame.y() + 8;
        if (subject == null) {
            int bw = Math.min(76, (w - 20) / 4);
            for (int i = 0; i < 4; i++) {
                host.showWidget(kinds.get(i), x + i * (bw + 2), y, bw, 18);
                kinds.get(i).setToggled(kind == i);
            }
            columns = Math.max(1, Math.min(18, (w - 34) / SLOT));
            rows = Math.max(1, (h - 75) / SLOT);
            grid =
                    new Ui.Rect(
                            frame.x() + (w - columns * SLOT - 12) / 2,
                            y + 36,
                            columns * SLOT,
                            rows * SLOT);
            return;
        }
        host.showWidget(back, x, y, 58, 18);
        int left = Math.max(100, (w - 26) * 42 / 100), right = w - 26 - left;
        for (int i = 0; i < 3; i++) {
            host.showWidget(modes.get(i), x + i * (left / 3), y + 24, left / 3 - 2, 18);
            modes.get(i).setToggled(mode == i);
        }
        host.showWidget(list, x, y + 46, left, h - 62);
        inspector = new Ui.Rect(x + left + 10, y + 46, right, h - 62);
        int ix = inspector.x(), iy = inspector.y(), iw = inspector.w();
        boolean compact = inspector.h() < 190;
        if (compact) host.showWidget(layerMode, frame.right() - 100, y + 24, 92, 18);
        int ph = compact ? 32 : Math.max(48, Math.min(100, inspector.h() / 3));
        preview = new Ui.Rect(ix, iy, iw, ph);
        if (asset == null) return;
        int cy = preview.bottom() + 5;
        if (!compact || !compactLayers) {
            if (mode == 1) {
                host.showWidget(name.widget(), ix, cy, iw, 18);
                host.showWidget(saveName, ix, cy + 21, (iw - 3) / 2, 18);
                host.showWidget(resetName, ix + (iw + 3) / 2, cy + 21, (iw - 3) / 2, 18);
                cy += 43;
            } else if (mode == 2) {
                host.showWidget(play, ix, cy, (iw - 3) / 2, 18);
                host.showWidget(stop, ix + (iw + 3) / 2, cy, (iw - 3) / 2, 18);
                host.showWidget(volume, ix, cy + 22, iw, 16);
                host.showWidget(pitch, ix, cy + 41, iw, 16);
                cy += 61;
            } else {
                host.showWidget(editFile, ix, cy, iw, 18);
                cy += 22;
            }
        }
        if (!compact || compactLayers) {
            if (compact) cy = preview.bottom() + 5;
            host.showWidget(layers, ix, cy + 12, iw, Math.max(18, inspector.bottom() - cy - 35));
            host.showWidget(apply, ix, inspector.bottom() - 18, iw, 18);
            apply.active = !host.scanning() && !pack.isEmpty();
        }
    }

    public void render(GuiGraphics g, int mx, int my, float delta) {
        Ui.bevel(g, frame, UiColors.CONTAINER, false);
        if (subject == null) {
            text(g, tr("browse"), frame.x() + 8, frame.y() + 31, frame.w() - 16, UiColors.INK);
            for (int row = 0; row < rows; row++)
                for (int col = 0; col < columns; col++) {
                    int index = (row + scrollRow) * columns + col;
                    var cell =
                            new Ui.Rect(grid.x() + col * SLOT, grid.y() + row * SLOT, SLOT, SLOT);
                    Ui.bevel(g, cell, UiColors.SLOT, true);
                    if (index < visible.size()) {
                        if (cell.contains(mx, my)
                                || (index == cursor && host.focusedWidget() == null))
                            g.fill(
                                    cell.x() + 1,
                                    cell.y() + 1,
                                    cell.right() - 1,
                                    cell.bottom() - 1,
                                    0x60FFFFFF);
                        g.renderItem(visible.get(index).icon(), cell.x() + 3, cell.y() + 3);
                    }
                }
            int thumb = Math.max(12, grid.h() * rows / Math.max(rows, totalRows()));
            Ui.bevel(g, new Ui.Rect(grid.right() + 3, grid.y(), 9, grid.h()), UiColors.SLOT, true);
            int ty =
                    grid.y()
                            + (maxScroll() == 0 ? 0 : (grid.h() - thumb) * scrollRow / maxScroll());
            Ui.bevel(
                    g,
                    new Ui.Rect(grid.right() + 4, ty + 1, 7, thumb - 2),
                    UiColors.CONTAINER,
                    false);
            text(
                    g,
                    tr("count", visible.size(), catalog == null ? 0 : catalog.subjects().size()),
                    frame.x() + 8,
                    frame.bottom() - 25,
                    frame.w() - 16,
                    UiColors.INK);
            if (visible.isEmpty())
                text(
                        g,
                        host.scanning() ? tr("loading") : tr("empty"),
                        grid.x() + 5,
                        grid.y() + 8,
                        grid.w() - 10,
                        UiColors.INK);
        } else {
            g.renderItem(subject.icon(), frame.x() + 74, frame.y() + 8);
            text(g, subject.name(), frame.x() + 96, frame.y() + 8, frame.w() - 104, UiColors.INK);
            text(
                    g,
                    subject.id().toString(),
                    frame.x() + 96,
                    frame.y() + 20,
                    frame.w() - 104,
                    UiColors.INK);
            Ui.bevel(g, preview, UiColors.SLOT, true);
            if (asset == null) {
                text(
                        g,
                        tr("emptySection"),
                        preview.x() + 5,
                        preview.y() + 8,
                        preview.w() - 10,
                        UiColors.INK);
                return;
            }
            if (mode == 0 && host.preview().hasImage())
                host.preview().render(g, preview, host.font());
            else {
                int yy = preview.y() + 5;
                for (String line : rawText.split("\n")) {
                    if (yy + 9 > preview.bottom() - 2) break;
                    text(g, line, preview.x() + 5, yy, preview.w() - 10, UiColors.INK);
                    yy += 11;
                }
            }
            if (layers.visible)
                text(
                        g,
                        tr(mode == 0 ? "appearance.layers" : "layers", packRows.size()),
                        layers.getX(),
                        layers.getY() - 11,
                        layers.getWidth(),
                        UiColors.INK);
        }
    }

    private void text(GuiGraphics g, String s, int x, int y, int w, int color) {
        g.drawString(host.font(), Ui.trim(host.font(), s, w), x, y, color, false);
    }

    private int totalRows() {
        return (visible.size() + columns - 1) / columns;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - rows);
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || subject != null) return false;
        if (grid.contains(x, y)) {
            int index =
                    ((int) (y - grid.y()) / SLOT + scrollRow) * columns
                            + (int) (x - grid.x()) / SLOT;
            if (index < visible.size()) {
                cursor = index;
                open(visible.get(index));
            }
            return true;
        }
        if (x >= grid.right() && x < grid.right() + 14 && y >= grid.y() && y < grid.bottom()) {
            scrollRow =
                    Ui.clamp((int) ((y - grid.y()) / grid.h() * (maxScroll() + 1)), 0, maxScroll());
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (subject != null || !frame.contains(x, y)) return false;
        scrollRow = Ui.clamp(scrollRow - (int) Math.signum(dy) * 2, 0, maxScroll());
        return true;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        if (subject != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                back();
                return true;
            }
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && !host.searchQuery().isEmpty()) {
            host.setSearchQuery("");
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && !visible.isEmpty()) {
            open(visible.get(cursor));
            return true;
        }
        if (host.focusedWidget() instanceof EditBox) return false;
        int move =
                switch (key) {
                    case GLFW.GLFW_KEY_LEFT -> -1;
                    case GLFW.GLFW_KEY_RIGHT -> 1;
                    case GLFW.GLFW_KEY_UP -> -columns;
                    case GLFW.GLFW_KEY_DOWN -> columns;
                    default -> 0;
                };
        if (move == 0 || visible.isEmpty()) return false;
        cursor = Ui.clamp(cursor + move, 0, visible.size() - 1);
        int row = cursor / columns;
        if (row < scrollRow) scrollRow = row;
        if (row >= scrollRow + rows) scrollRow = row - rows + 1;
        return true;
    }

    public String tooltip(int x, int y) {
        if (subject == null && grid.contains(x, y)) {
            int i = ((y - grid.y()) / SLOT + scrollRow) * columns + (x - grid.x()) / SLOT;
            if (i < visible.size()) return visible.get(i).name() + " · " + visible.get(i).id();
        }
        if (subject != null && layers.visible && layers.isMouseOver(x, y))
            return layers.tooltipAt(layers.hoveredRow());
        if (subject != null && list.isMouseOver(x, y)) return list.tooltipAt(list.hoveredRow());
        return null;
    }

    public void refreshSearch() {
        query = "\0";
    }

    public String statusText() {
        return tr(subject == null ? "hint" : mode == 0 ? "appearance.hint" : "detail.hint");
    }

    public List<Subject> visibleSubjects() {
        return visible;
    }

    public List<Asset> relatedAssets() {
        return assets;
    }

    public List<Asset> visibleAssets() {
        return assetRows;
    }

    public Subject selectedSubject() {
        return subject;
    }

    public Asset selectedAsset() {
        return asset;
    }

    public Ui.Rect gridRect() {
        return grid;
    }

    public LayerList layerList() {
        return layers;
    }

    public ScrollList assetList() {
        return list;
    }

    public List<String> layerPacks() {
        return packRows;
    }

    public String selectedPack() {
        return pack;
    }

    public String previewSource() {
        return previewSource;
    }

    public long previewLoads() {
        return previewLoads;
    }

    public AppearanceLayers appearanceLayers() {
        return appearance;
    }

    private final class AssetList extends ScrollList {
        AssetList() {
            super(host.font(), 0, 0, 1, 1, 24);
        }

        protected int rowCount() {
            return assetRows.size();
        }

        protected boolean isSelected(int i) {
            return assetRows.get(i).equals(asset);
        }

        protected void clickRow(int i, double x, double y) {
            selectAsset(assetRows.get(i));
        }

        protected void renderRow(
                GuiGraphics g, int i, Ui.Rect r, boolean hovered, boolean selected) {
            Ui.bevel(
                    g,
                    r,
                    selected ? UiColors.SELECTED : hovered ? UiColors.HOVER : UiColors.SLOT,
                    true);
            Asset a = assetRows.get(i);
            String label = a.group() == Group.NAME ? tr("mode.1") : a.label();
            text(
                    g,
                    label,
                    r.x() + 4,
                    r.y() + 8,
                    r.w() - 8,
                    selected ? UiColors.TEXT : UiColors.INK);
        }

        public String tooltipAt(int i) {
            return i >= 0 && i < assetRows.size() ? assetRows.get(i).label() : null;
        }
    }
}
