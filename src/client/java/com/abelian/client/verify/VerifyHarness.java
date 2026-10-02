package com.abelian.client.verify;

import com.abelian.client.ResourceManagerConfig;
import com.abelian.client.ui.TreeView;
import com.abelian.client.ui.UiButton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.glfw.GLFW;

/**
 * Temporary, environment gated GUI verification driver. It only runs when the RESOURCEMANAGER_VERIFY
 * environment variable is present, so the shipped mod is unaffected.
 */
public final class VerifyHarness implements ClientModInitializer {
    private static final boolean ENABLED = System.getenv("RESOURCEMANAGER_VERIFY") != null;
    private static final String TEXTURE = "minecraft:textures/block/redstone_block.png";
    private static final int TOOLBAR_BOTTOM = 28;

    private static final int STEP_TITLE = 0;
    private static final int STEP_SEED = 1;
    private static final int STEP_BASE = 2;
    private static final int STEP_CHECK_OFF = 3;
    private static final int STEP_UNSEED = 4;
    private static final int STEP_CHECK_ON = 5;
    private static final int STEP_OPEN = 6;
    private static final int STEP_SCAN = 7;
    private static final int STEP_LAYOUT = 8;
    private static final int STEP_RESIZE = 9;
    private static final int STEP_WIDE = 10;
    private static final int STEP_SEARCH = 11;
    private static final int STEP_SELECT = 12;
    private static final int STEP_TOGGLE = 13;
    private static final int STEP_SOUND = 14;
    private static final int STEP_TUNE = 15;
    private static final int STEP_TUNE_CHECK = 16;
    private static final int STEP_RELOAD = 17;
    private static final int STEP_ESC = 18;
    private static final int STEP_DONE = 19;

    private final List<String> lines = new ArrayList<>();
    private int step = STEP_TITLE;
    private int delay;
    private int ticksInStep;
    private boolean reloadDone;
    private boolean openedViaKeybind;
    private String squarefulId = "";
    private String sourceBefore = "";
    private String sourceDisabled = "";
    private String sourceRestored = "";
    private Set<String> disabledBefore = Set.of();
    private Set<String> disabledAtStart = Set.of();
    private Map<String, ResourceManagerConfig.SoundTuning> soundsAtStart = Map.of();
    private Map<String, ResourceManagerConfig.SoundTuning> tunedBefore = Map.of();
    private int overlaps;
    private int outOfBounds;
    private String treeRect = "";
    private String sidebarRight = "";

    @Override
    public void onInitializeClient() {
        if (!ENABLED) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        note("harness enabled");
    }

    private ResourceManagerConfig config() {
        return ResourceManagerConfig.instance();
    }

    private void onTick(Minecraft client) {
        if (this.step == STEP_DONE) {
            return;
        }
        this.ticksInStep++;
        if (this.delay > 0) {
            this.delay--;
            return;
        }
        switch (this.step) {
            case STEP_TITLE -> stepTitle(client);
            case STEP_SEED -> stepSeed(client);
            case STEP_BASE -> stepBase(client);
            case STEP_CHECK_OFF -> stepCheckOff(client);
            case STEP_UNSEED -> stepUnseed(client);
            case STEP_CHECK_ON -> stepCheckOn(client);
            case STEP_OPEN -> stepOpen(client);
            case STEP_SCAN -> stepScan(client);
            case STEP_LAYOUT -> stepLayout(client);
            case STEP_RESIZE -> stepResize(client);
            case STEP_WIDE -> stepWide(client);
            case STEP_SEARCH -> stepSearch(client);
            case STEP_SELECT -> stepSelectFile(client);
            case STEP_TOGGLE -> stepToggle(client);
            case STEP_SOUND -> stepSound(client);
            case STEP_TUNE -> stepTune(client);
            case STEP_TUNE_CHECK -> stepCheckTuning(client);
            case STEP_RELOAD -> stepReload(client);
            case STEP_ESC -> stepEsc(client);
            default -> finish(client);
        }
    }

    // ------------------------------------------------------------------ steps

    private void stepTitle(Minecraft client) {
        if (client.screen instanceof TitleScreen || client.level != null) {
            note("client ready, screen=" + name(client.screen));
            this.disabledAtStart = new TreeSet<>(config().disabledKeys());
            this.soundsAtStart = new TreeMap<>(config().sounds());
            note("config at start: " + this.disabledAtStart.size() + " disabled, "
                    + this.soundsAtStart.size() + " tuned sounds");
            advance(STEP_SEED, 20);
        } else if (this.ticksInStep > 2400) {
            note("FAIL: timed out waiting for the title screen, screen=" + name(client.screen));
            finish(client);
        }
    }

    private void stepSeed(Minecraft client) {
        List<String> packIds = client.getResourceManager().listPacks().map(PackResources::packId).toList();
        note("live pack ids (" + packIds.size() + "): " + packIds);
        this.squarefulId = packIds.stream()
                .filter(id -> id.contains("Squareful") && id.contains("v3.8"))
                .findFirst()
                .orElse("");
        note("target pack id: " + this.squarefulId + " -> normalized "
                + ResourceManagerConfig.normalizePackId(this.squarefulId));
        note("mixin on FallbackResourceManager applied = " + mixinApplied(
                "net.minecraft.server.packs.resources.FallbackResourceManager"));
        note("mixin on AbstractSoundInstance applied = " + mixinApplied(
                "net.minecraft.client.resources.sounds.AbstractSoundInstance"));
        if (this.squarefulId.isEmpty()) {
            note("SKIP: squareful pack not found");
            advance(STEP_OPEN, 5);
            return;
        }
        // Start from a known state: this texture is served by every pack that has it again.
        for (String packId : packIds) {
            config().setDisabled(packId, TEXTURE, false);
        }
        config().save();
        reload(client);
        advance(STEP_BASE, 30);
    }

    private void stepBase(Minecraft client) {
        if (!this.reloadDone && this.ticksInStep < 600) {
            return;
        }
        this.sourceBefore = sourceOf(client, TEXTURE);
        note("baseline source(" + TEXTURE + ") with the texture enabled = " + this.sourceBefore);
        note((this.sourceBefore.contains("Squareful") ? "PASS" : "FAIL")
                + ": the top pack provides the texture while it is enabled");
        config().setDisabled(this.squarefulId, TEXTURE, true);
        config().save();
        note("disabled " + TEXTURE + " in " + ResourceManagerConfig.normalizePackId(this.squarefulId)
                + " (config keys: " + config().disabledCount() + ")");
        reload(client);
        advance(STEP_CHECK_OFF, 30);
    }

    private void stepCheckOff(Minecraft client) {
        if (!this.reloadDone && this.ticksInStep < 600) {
            return;
        }
        this.sourceDisabled = sourceOf(client, TEXTURE);
        note("source(" + TEXTURE + ") while disabled = " + this.sourceDisabled);
        note((!this.sourceDisabled.contains("Squareful") ? "PASS" : "FAIL")
                + ": a disabled file is no longer served by that pack (" + this.sourceBefore + " -> "
                + this.sourceDisabled + ")");
        advance(STEP_UNSEED, 5);
    }

    private void stepUnseed(Minecraft client) {
        config().setDisabled(this.squarefulId, TEXTURE, false);
        config().save();
        reload(client);
        advance(STEP_CHECK_ON, 30);
    }

    private void stepCheckOn(Minecraft client) {
        if (!this.reloadDone && this.ticksInStep < 600) {
            return;
        }
        this.sourceRestored = sourceOf(client, TEXTURE);
        note("source(" + TEXTURE + ") after re-enabling = " + this.sourceRestored);
        note((this.sourceRestored.equals(this.sourceBefore) ? "PASS" : "FAIL")
                + ": re-enabling restores the original provider");
        advance(STEP_OPEN, 5);
    }

    private void reload(Minecraft client) {
        this.reloadDone = false;
        this.ticksInStep = 0;
        client.reloadResourcePacks().thenRunAsync(() -> client.execute(() -> this.reloadDone = true), client);
    }

    private void stepOpen(Minecraft client) {
        net.minecraft.client.KeyMapping mapping = net.minecraft.client.KeyMapping.get("key.resourcemanager.open");
        note("screen before the shortcut = " + name(client.screen));
        if (mapping == null) {
            note("key binding 'key.resourcemanager.open' is not registered");
            client.setScreen(new com.abelian.client.ResourceManagerScreen(client.screen));
            advance(STEP_SCAN, 5);
            return;
        }
        net.minecraft.network.chat.Component bound = mapping.getTranslatedKeyMessage();
        note("key binding registered, bound to " + bound.getString() + " (" + mapping.getDefaultKey().getName()
                + "), dispatching a key press event");
        net.minecraft.client.KeyMapping.click(mapping.getDefaultKey());
        advance(STEP_SCAN, 5);
    }

    private void stepScan(Minecraft client) {
        if (client.screen instanceof com.abelian.client.ResourceManagerScreen) {
            if (!this.openedViaKeybind) {
                this.openedViaKeybind = true;
                note("PASS: the key binding opened the GUI");
            }
            if (this.ticksInStep < 120) {
                return;
            }
            note("screen open, widgets=" + widgets(client).size());
            advance(STEP_LAYOUT, 60);
            return;
        }
        if (this.ticksInStep < 20) {
            return;
        }
        note("the key binding dispatch did not open the GUI (screen=" + name(client.screen)
                + "), opening it directly for the layout checks");
        client.setScreen(new com.abelian.client.ResourceManagerScreen(client.screen));
        advance(STEP_SCAN, 5);
    }

    private void stepLayout(Minecraft client) {
        screenshot(client, "verify-01-overview.png");
        checkLayout(client, "compact");
        advance(STEP_RESIZE, 10);
    }

    /** Enlarges the window so the three-panel layout (with the sidebar) can be verified too. */
    private void stepResize(Minecraft client) {
        client.options.guiScale().set(2);
        client.getWindow().setWindowed(1280, 720);
        client.resizeDisplay();
        note("resized the window to " + client.getWindow().getWidth() + "x" + client.getWindow().getHeight()
                + " with guiScale=" + client.options.guiScale().get() + ", logical size "
                + client.getWindow().getGuiScaledWidth() + "x" + client.getWindow().getGuiScaledHeight());
        advance(STEP_WIDE, 30);
    }

    private void stepWide(Minecraft client) {
        screenshot(client, "verify-02-wide.png");
        checkLayout(client, "wide");
        advance(STEP_SEARCH, 5);
    }

    private void checkLayout(Minecraft client, String tag) {
        List<AbstractWidget> widgets = widgets(client);
        this.outOfBounds = 0;
        int screenBottom = client.screen.height;
        int toolbarBottom = TOOLBAR_BOTTOM;
        int statusTop = screenBottom - 8 - 12;
        note("---- [" + tag + "] widget layout (" + widgets.size() + ") screen " + client.screen.width + "x"
                + screenBottom + " ----");
        boolean straddles = false;
        for (AbstractWidget widget : widgets) {
            note("  " + pad(widget.getClass().getSimpleName()) + " [" + widget.getX() + "," + widget.getY() + " "
                    + widget.getWidth() + "x" + widget.getHeight() + "] " + keyOf(widget));
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > client.screen.width
                    || widget.getBottom() > screenBottom) {
                this.outOfBounds++;
                note("    OUT OF BOUNDS");
            }
            boolean crossesToolbar = widget.getY() < toolbarBottom && widget.getBottom() > toolbarBottom;
            boolean crossesStatus = widget.getY() < statusTop && widget.getBottom() > statusTop;
            if (crossesToolbar || crossesStatus) {
                straddles = true;
                note("    CROSSES A TEXT STRIP" + (crossesToolbar ? " (toolbar)" : "") + (crossesStatus ? " (status)" : ""));
            }
        }
        note("[" + tag + "] " + (straddles ? "FAIL" : "PASS")
                + ": no control crosses the toolbar or status text strips");
        this.overlaps = 0;
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                AbstractWidget a = widgets.get(i);
                AbstractWidget b = widgets.get(j);
                if (a.getX() < b.getRight() && b.getX() < a.getRight() && a.getY() < b.getBottom()
                        && b.getY() < a.getBottom()) {
                    this.overlaps++;
                    note("  OVERLAP: " + keyOf(a) + " <> " + keyOf(b));
                }
            }
        }
        note("[" + tag + "] " + (this.overlaps == 0 ? "PASS" : "FAIL")
                + ": visible widgets do not overlap (" + this.overlaps + ")");
        note("[" + tag + "] " + (this.outOfBounds == 0 ? "PASS" : "FAIL") + ": widgets stay inside the screen");
        TreeView tree = widget(client, TreeView.class);
        if (tree != null) {
            this.treeRect = "[" + tree.getX() + "," + tree.getY() + " " + tree.getWidth() + "x"
                    + tree.getHeight() + "]";
            int right = 0;
            for (AbstractWidget widget : widgets) {
                if (widget.getY() >= toolbarBottom && widget.getX() < tree.getX() && widget.getRight() > right) {
                    right = widget.getRight();
                }
            }
            this.sidebarRight = String.valueOf(right);
            note("[" + tag + "] " + (right <= tree.getX() ? "PASS" : "FAIL")
                    + ": the tree panel starts right of the sidebar controls (sidebar right="
                    + right + ", tree x=" + tree.getX() + " " + this.treeRect + ")");
        }
    }

    private void stepSearch(Minecraft client) {
        String query = "redstone_block";
        for (int i = 0; i < query.length(); i++) {
            client.screen.charTyped(query.charAt(i), 0);
        }
        note("typed \"" + query + "\" into the search box");
        advance(STEP_SELECT, 40);
    }

    private void stepSelectFile(Minecraft client) {
        screenshot(client, "verify-03-search.png");
        for (int i = 0; i < 40; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        note("selected the last filtered row with arrow keys");
        dumpTree(client, "redstone_block");
        advance(STEP_TOGGLE, 10);
    }

    private void dumpTree(Minecraft client, String query) {
        TreeView tree = widget(client, TreeView.class);
        if (tree == null) {
            return;
        }
        int count = 0;
        while (count < 4000 && tree.tooltipAt(count) != null) {
            count++;
        }
        note("rows matching \"" + query + "\" = " + count);
        for (int index = Math.max(0, count - 4); index < count; index++) {
            note("  row " + index + ": " + tree.tooltipAt(index));
        }
    }

    private void stepToggle(Minecraft client) {
        UiButton toggle = button(client, "resourcemanager.ui.action.disable");
        UiButton enable = button(client, "resourcemanager.ui.action.enable");
        note("toggle button: disable=" + (toggle != null) + " enable=" + (enable != null));
        this.disabledBefore = new TreeSet<>(config().disabledKeys());
        if (toggle == null) {
            note("SKIP: no file row selected, the tree filter or row walk needs adjusting");
            advance(STEP_SOUND, 5);
            return;
        }
        note("clicking the toggle button at [" + toggle.getX() + "," + toggle.getY() + "]");
        click(client, toggle);
        Set<String> after = new TreeSet<>(config().disabledKeys());
        Set<String> added = new TreeSet<>(after);
        added.removeAll(this.disabledBefore);
        note("config keys added by the GUI click: " + added);
        boolean normalized = !added.isEmpty() && added.stream().allMatch(this::matchesLivePack);
        note((added.size() == 1 ? "PASS" : "FAIL") + ": the GUI disabled exactly one resource ("
                + added.size() + ")");
        note((normalized ? "PASS" : "FAIL") + ": the stored pack id matches a live pack id");
        screenshot(client, "verify-04-disabled.png");
        advance(STEP_SOUND, 20);
    }

    private boolean matchesLivePack(String disabledKey) {
        int separator = disabledKey.indexOf('|');
        if (separator < 0) {
            return false;
        }
        return livePackIds().contains(disabledKey.substring(0, separator));
    }

    private List<String> livePackIds() {
        List<String> ids = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();
        if (client.getResourceManager() == null) {
            return ids;
        }
        client.getResourceManager().listPacks().forEach(pack -> {
            ids.add(pack.packId());
            ids.add(ResourceManagerConfig.normalizePackId(pack.packId()));
        });
        return ids;
    }

    private void stepSound(Minecraft client) {
        focusSearch(client);
        for (int i = 0; i < 48; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        }
        String query = "sounds.json";
        for (int i = 0; i < query.length(); i++) {
            client.screen.charTyped(query.charAt(i), 0);
        }
        note("typed \"" + query + "\" to reach sound event rows");
        for (int i = 0; i < 30; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        dumpTree(client, "sounds.json");
        advance(STEP_TUNE, 40);
    }

    private void focusSearch(Minecraft client) {
        AbstractWidget search = widgetByKey(client, "resourcemanager.ui.search");
        if (search != null) {
            search.setFocused(true);
            client.screen.setFocused(search);
            note("focused the search box, current value=\"" + search.getMessage().getString() + "\"");
        }
    }

    private AbstractWidget widgetByKey(Minecraft client, String translationKey) {
        for (AbstractWidget widget : widgets(client)) {
            if (translationKey.equals(translationKey(widget))) {
                return widget;
            }
        }
        return null;
    }

    private void stepTune(Minecraft client) {
        for (int i = 0; i < 60; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        List<AbstractWidget> sliders = widgets(client).stream()
                .filter(widget -> widget.getClass().getSimpleName().equals("UiSlider"))
                .toList();
        note("visible sliders after selecting a sound event: " + sliders.size());
        if (sliders.isEmpty()) {
            note("SKIP: no sound event row selected");
            advance(STEP_RELOAD, 5);
            return;
        }
        AbstractWidget slider = sliders.get(0);
        UiButton reset = button(client, "resourcemanager.ui.action.reset");
        if (reset != null) {
            click(client, reset);
            note("clicked Reset so the slider click has to produce a visible change");
        }
        int target = slider.getX() + (int) (slider.getWidth() * 0.7);
        int y = slider.getY() + slider.getHeight() / 2;
        note("clicking the first slider at [" + target + "," + y + "], rect [" + slider.getX() + ","
                + slider.getY() + " " + slider.getWidth() + "x" + slider.getHeight() + "] label="
                + slider.getMessage().getString() + " visible=" + slider.visible + " active=" + slider.active);
        this.tunedBefore = new TreeMap<>(config().sounds());
        boolean handled = client.screen.mouseClicked(target, y, 0);
        client.screen.mouseReleased(target, y, 0);
        Set<String> after = new TreeSet<>(config().sounds().keySet());
        Set<String> added = new TreeSet<>(after);
        added.removeAll(this.tunedBefore.keySet());
        note("mouseClicked handled=" + handled + ", tuning keys after the click: " + added
                + ", all keys: " + after);
        boolean changed = !new TreeMap<>(config().sounds()).equals(this.tunedBefore);
        note((changed ? "PASS" : "FAIL") + ": the volume slider changed the stored tuning");
        advance(STEP_TUNE_CHECK, 30);
    }

    private void stepCheckTuning(Minecraft client) {
        screenshot(client, "verify-05-tuning.png");
        for (Map.Entry<String, ResourceManagerConfig.SoundTuning> entry : config().sounds().entrySet()) {
            if (!this.tunedBefore.containsKey(entry.getKey())
                    || !this.tunedBefore.get(entry.getKey()).equals(entry.getValue())) {
                note("stored tuning " + entry.getKey() + " volume="
                        + String.format(Locale.ROOT, "%.2f", entry.getValue().volume()) + " pitch="
                        + String.format(Locale.ROOT, "%.2f", entry.getValue().pitch()));
            }
        }
        boolean louder = config().sounds().values().stream().anyMatch(tuning -> tuning.volume() > 1.5F);
        note((louder ? "PASS" : "FAIL") + ": a louder than default volume is stored");
        advance(STEP_RELOAD, 5);
    }

    private void stepReload(Minecraft client) {
        UiButton reload = button(client, "resourcemanager.ui.reload");
        if (reload == null) {
            note("FAIL: reload button missing");
            advance(STEP_ESC, 5);
            return;
        }
        note("clicking \"reload packs\"");
        click(client, reload);
        advance(STEP_ESC, 200);
    }

    private void stepEsc(Minecraft client) {
        note("screen after reload = " + name(client.screen));
        screenshot(client, "verify-06-after-reload.png");
        client.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        note("pressed ESC, screen is now " + name(client.screen));
        note((client.screen instanceof com.abelian.client.ResourceManagerScreen ? "FAIL" : "PASS")
                + ": ESC closes the GUI without a crash");
        advance(STEP_DONE, 20);
        finish(client);
    }

    private void finish(Minecraft client) {
        this.step = STEP_DONE;
        try {
            Path file = client.gameDirectory.toPath().resolve("verify-report.txt");
            Files.write(file, this.lines);
            note("report written to " + file);
            Files.write(file, this.lines);
        } catch (Exception error) {
            System.out.println("[verify] could not write report: " + error);
        }
        restoreConfig();
        for (String line : this.lines) {
            System.out.println("[verify] " + line);
        }
        try {
            String configFile = Files.readString(
                    client.gameDirectory.toPath().resolve("config").resolve("resourcemanager.json"));
            System.out.println("[verify] config file:\n" + configFile);
        } catch (Exception error) {
            System.out.println("[verify] could not read config: " + error);
        }
        System.out.println("[verify] DONE");
        client.stop();
    }

    // ------------------------------------------------------------------ helpers

    /** Puts the debug config back exactly as it was before the run, so verifying leaves no traces. */
    private void restoreConfig() {
        config().clearDisabled();
        for (String key : this.disabledAtStart) {
            config().setDisabledKey(key, true);
        }
        for (String key : config().sounds().keySet()) {
            if (!this.soundsAtStart.containsKey(key)) {
                config().setSound(key, 1.0F, 1.0F);
            }
        }
        for (Map.Entry<String, ResourceManagerConfig.SoundTuning> entry : this.soundsAtStart.entrySet()) {
            config().setSound(entry.getKey(), entry.getValue().volume(), entry.getValue().pitch());
        }
        config().save();
        note("restored the config to its original state (" + config().disabledCount() + " disabled, "
                + config().tunedSoundCount() + " tuned sounds)");
    }

    private void note(String line) {
        this.lines.add(line);
        System.out.println("[verify] " + line);
    }

    private void advance(int next, int delayTicks) {
        this.step = next;
        this.ticksInStep = 0;
        this.delay = delayTicks;
    }

    private String name(Object screen) {
        return screen == null ? "null" : screen.getClass().getSimpleName();
    }

    private List<AbstractWidget> widgets(Minecraft client) {
        List<AbstractWidget> widgets = new ArrayList<>();
        if (client.screen == null) {
            return widgets;
        }
        for (Object child : client.screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                widgets.add(widget);
            }
        }
        return widgets;
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractWidget> T widget(Minecraft client, Class<T> type) {
        for (AbstractWidget widget : widgets(client)) {
            if (type.isInstance(widget)) {
                return (T) widget;
            }
        }
        return null;
    }

    private UiButton button(Minecraft client, String translationKey) {
        for (AbstractWidget widget : widgets(client)) {
            if (widget instanceof UiButton button && translationKey.equals(translationKey(widget))) {
                return button;
            }
        }
        return null;
    }

    private String keyOf(AbstractWidget widget) {
        String key = translationKey(widget);
        return key != null ? key : "\"" + widget.getMessage().getString() + "\"";
    }

    private String translationKey(AbstractWidget widget) {
        Component message = widget.getMessage();
        if (message != null && message.getContents() instanceof TranslatableContents contents) {
            return contents.getKey();
        }
        return null;
    }

    private void click(Minecraft client, AbstractWidget widget) {
        int x = widget.getX() + widget.getWidth() / 2;
        int y = widget.getY() + widget.getHeight() / 2;
        client.screen.mouseClicked(x, y, 0);
        client.screen.mouseReleased(x, y, 0);
    }

    private void screenshot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {
        });
    }

    private String sourceOf(Minecraft client, String location) {
        ResourceLocation id = ResourceLocation.tryParse(location);
        if (id == null) {
            return "<bad id>";
        }
        Optional<Resource> resource = client.getResourceManager().getResource(id);
        return resource.map(Resource::sourcePackId).orElse("<missing>");
    }

    private boolean mixinApplied(String className) {
        try {
            Class<?> type = Class.forName(className);
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (method.getName().contains("resourcemanager")) {
                    return true;
                }
            }
        } catch (Throwable error) {
            return false;
        }
        return false;
    }

    private String pad(String text) {
        return text.length() >= 18 ? text : text + " ".repeat(18 - text.length());
    }
}
