package com.abelian.client;

import com.abelian.client.page.InventoryPage;
import com.abelian.client.search.BilingualSearch;
import com.abelian.client.sound.SoundTuning;
import com.abelian.client.ui.*;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Inventory lifecycle, input, indexing and deferred persistence. */
public final class ResourceManagerScreen extends Screen {
    private final Screen parent;
    private final ResourceManagerConfig config = ResourceManagerConfig.instance();
    private final PreviewTexture preview = new PreviewTexture();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Map<AbstractWidget, UiField> fields = new IdentityHashMap<>();
    private InventoryPage inventory;
    private UiField search;
    private UiButton reload, close;
    private ResourceIndex.Snapshot snapshot;
    private int generation, scanToken;
    private boolean scanning, closed;
    private long saveAt, statusUntil;
    private String query = "", status = "";
    private DragTarget drag;

    public ResourceManagerScreen(Screen parent) {
        super(Component.translatable("resourcemanager.ui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        closed = false;
        if (inventory == null) {
            search =
                    addField(
                            Component.translatable("resourcemanager.inventory.search").getString(),
                            96,
                            this::setSearchQuery)
                            .setChrome(true);
            reload = addButton("resourcemanager.ui.reload", this::reloadPacks).setStyle(UiButton.Style.CHROME);
            close = addButton("resourcemanager.ui.close", this::onClose).setStyle(UiButton.Style.CHROME);
            inventory = new InventoryPage(this);
            inventory.createWidgets();
        }
        for (AbstractWidget widget : widgets) if (!children().contains(widget)) addWidget(widget);
        layout();
        focusSearch();
        if (snapshot == null && ResourceIndex.cachedSnapshot() != null)
            accept(ResourceIndex.cachedSnapshot());
        if (!scanning && ResourceIndex.isStale()) startScan();
    }

    private void startScan() {
        scanning = true;
        int token = ++scanToken;
        setStatus(Component.translatable("resourcemanager.ui.status.scanning"));
        ResourceIndex.scanAsync()
                .whenComplete(
                        (result, error) ->
                                Minecraft.getInstance()
                                        .execute(
                                                () -> {
                                                    if (closed || token != scanToken) return;
                                                    scanning = false;
                                                    if (error != null)
                                                        setStatus(
                                                                Component.literal(
                                                                        "Unable to read packs: "
                                                                                + error
                                                                                        .getMessage()));
                                                    else accept(result);
                                                }));
    }

    private void accept(ResourceIndex.Snapshot value) {
        snapshot = value;
        generation++;
        LangText.clearCache();
        preview.clear();
        inventory.refreshSearch();
        BilingualSearch.onWarm(
                () -> {
                    if (!closed) inventory.refreshSearch();
                });
        CompletableFuture.runAsync(BilingualSearch::warm);
    }

    public void reloadPacks() {
        if (scanning) return;
        inventory.refreshAppearanceRules();
        save();
        scanning = true;
        PackSoundPreview.stop();
        BilingualSearch.invalidate();
        setStatus(Component.translatable("resourcemanager.ui.status.reloading"));
        Minecraft client = Minecraft.getInstance();
        client.reloadResourcePacks()
                .whenComplete(
                        (unused, error) ->
                                client.execute(
                                        () -> {
                                            if (closed) return;
                                            if (error != null) {
                                                scanning = false;
                                                setStatus(
                                                        Component.literal(
                                                                "Reload failed: "
                                                                        + error.getMessage()));
                                            } else startScan();
                                        }));
    }

    public void changed() {
        saveAt = Util.getMillis() + 400;
    }

    private void save() {
        saveAt = 0;
        config.save();
    }

    public void setStatus(Component value) {
        status = value.getString();
        statusUntil = Util.getMillis() + 4000;
    }

    public String displayedStatus() {
        return Util.getMillis() < statusUntil ? status : inventory.statusText();
    }

    public ResourceManagerConfig config() {
        return config;
    }

    public ResourceIndex.Snapshot snapshot() {
        return snapshot;
    }

    public int indexGeneration() {
        return generation;
    }

    public boolean scanning() {
        return scanning;
    }

    public Font font() {
        return font;
    }

    public PreviewTexture preview() {
        return preview;
    }

    public InventoryPage inventoryPage() {
        return inventory;
    }

    public String searchQuery() {
        return query;
    }

    public Ui.Rect contentRect() {
        return new Ui.Rect(8, 34, width - 16, Math.max(80, height - 60));
    }

    public void setSearchQuery(String value) {
        query = value == null ? "" : value;
        if (search != null && !query.equals(search.value())) search.setValue(query);
    }

    public void focusSearch() {
        focus(search.widget());
    }

    public void focus(AbstractWidget widget) {
        setFocused(widget);
    }

    public AbstractWidget focusedWidget() {
        return getFocused() instanceof AbstractWidget w ? w : null;
    }

    public SoundTuning soundTuning(String event) {
        return config.soundTuning(event);
    }

    public void applySoundTuning(String event, SoundTuning tuning) {
        config.setSoundTuning(event, tuning);
        changed();
    }

    public void setTextOverride(String key, String value) {
        config.setText(key, value);
        changed();
        inventory.refreshSearch();
    }

    public <T extends AbstractWidget> T add(T widget) {
        widgets.add(widget);
        widget.visible = false;
        return widget;
    }

    public UiButton addButton(String label, Runnable action) {
        return add(new UiButton(font, 0, 0, 1, 18, Component.translatable(label), action));
    }

    public UiField addField(String hint, int maxLength) {
        return addField(hint, maxLength, null);
    }

    public UiField addField(String hint, int maxLength, Consumer<String> responder) {
        UiField field = new UiField(font, maxLength, responder, hint);
        add(field.widget());
        fields.put(field.widget(), field);
        return field;
    }

    public void showWidget(AbstractWidget widget, int x, int y, int w, int h) {
        Ui.place(widget, x, y, w, h);
        widget.visible = true;
    }

    public void hideWidget(AbstractWidget widget) {
        widget.visible = false;
    }

    private void layout() {
        showWidget(search.widget(), 8, 6, Math.max(40, width - 132), 18);
        showWidget(reload, width - 122, 6, 74, 18);
        showWidget(close, width - 44, 6, 36, 18);
        reload.active = !scanning;
        inventory.layout();
        if (getFocused() instanceof AbstractWidget widget && !widget.visible) setFocused(null);
    }

    @Override
    public void tick() {
        if (saveAt > 0 && Util.getMillis() >= saveAt) save();
        if (!scanning && ResourceIndex.isStale()) startScan();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        layout();
        renderBackground(g, mx, my, delta);
        Ui.chrome(g, new Ui.Rect(0, 0, width, 30));
        inventory.render(g, mx, my, delta);
        for (AbstractWidget widget : widgets) {
            if (!widget.visible) continue;
            UiField field = fields.get(widget);
            if (field != null) field.render(g);
            else widget.render(g, mx, my, delta);
        }
        Ui.chrome(g, new Ui.Rect(0, height - 24, width, 24));
        g.fill(13, height - 17, 15, height - 7, UiColors.ACCENT_LIGHT);
        g.drawString(
                font,
                Ui.trim(font, displayedStatus(), width - 30),
                21,
                height - 16,
                UiColors.CHROME_TEXT,
                false);
        String tooltip = inventory.tooltip(mx, my);
        if (tooltip == null)
            for (AbstractWidget widget : widgets)
                if (widget.visible && widget instanceof UiButton b && b.isMouseOver(mx, my)) {
                    tooltip = b.tooltip();
                    break;
                }
        if (tooltip != null && !tooltip.isBlank())
            g.renderTooltip(font, Component.literal(tooltip), mx, my);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) {
            drag =
                    widgets.stream()
                            .filter(w -> w.visible && w instanceof DragTarget d && d.isDragging())
                            .map(w -> (DragTarget) w)
                            .findFirst()
                            .orElse(null);
            return true;
        }
        return inventory.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (drag != null && button == 0) {
            drag.dragTo(x, y);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (drag != null && button == 0) {
            drag.endDrag();
            drag = null;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return super.mouseScrolled(x, y, dx, dy) || inventory.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_F5) {
            reloadPacks();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            focusSearch();
            return true;
        }
        return inventory.keyPressed(key, scan, mods) || super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        closed = true;
        save();
        PackSoundPreview.stop();
        preview.release();
        BilingualSearch.onWarm(null);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
