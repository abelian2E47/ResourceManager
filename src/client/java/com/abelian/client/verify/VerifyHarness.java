package com.abelian.client.verify;

import com.abelian.client.ResourceIndex;
import com.abelian.client.ResourceManagerConfig;
import com.abelian.client.ResourceManagerScreen;
import com.abelian.client.ResourceNode;
import com.abelian.client.ui.DisabledList;
import com.abelian.client.ui.TextKeyList;
import com.abelian.client.ui.TreeView;
import com.abelian.client.ui.UiButton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Temporary, environment gated GUI verification driver. It only runs when the RESOURCEMANAGER_VERIFY
 * environment variable is present, so the shipped mod is unaffected.
 */
public final class VerifyHarness implements ClientModInitializer {
    private static final boolean ENABLED = System.getenv("RESOURCEMANAGER_VERIFY") != null;
    private static final String TEXTURE = "minecraft:textures/block/redstone_block.png";
    private static final String MARKER = "RM_VERIFY_LANG_VALUE";
    /** The key behind a diamond sword's name, and the name the self check gives it. */
    private static final String ITEM_KEY = "item.minecraft.diamond_sword";
    private static final String ITEM_MARKER = "RM_VERIFY_ITEM_NAME";
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
    private static final int STEP_PLAY = 17;
    private static final int STEP_PLAY_CHECK = 18;
    private static final int STEP_FULLSCREEN = 19;
    private static final int STEP_FULLSCREEN_CHECK = 20;
    private static final int STEP_FULLSCREEN_BACK = 21;
    private static final int STEP_LANG_SEARCH = 22;
    private static final int STEP_LANG_EDIT = 23;
    private static final int STEP_LANG_CHECK = 24;
    private static final int STEP_LANG_REVERT = 25;
    private static final int STEP_TEXTURE = 26;
    private static final int STEP_TEXTURE_SHOT = 27;
    private static final int STEP_TEXTURE_CHECK = 28;
    private static final int STEP_SERVING_PICK = 29;
    private static final int STEP_SERVING_CHECK = 30;
    private static final int STEP_RELOAD = 31;
    private static final int STEP_SERVING_AFTER = 32;
    private static final int STEP_ITEM_TEXT = 33;
    private static final int STEP_ITEM_SEARCH = 34;
    private static final int STEP_ITEM_EDIT = 35;
    private static final int STEP_ITEM_CHECK = 36;
    private static final int STEP_ITEM_REVERT = 37;
    private static final int STEP_ESC = 38;
    private static final int STEP_DONE = 39;

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
    private Map<String, String> textsAtStart = Map.of();
    private String langKey = "";
    private String langFile = "";
    private int overlaps;
    private int outOfBounds;
    private String treeRect = "";
    private String sidebarRight = "";
    private ResourceLocation servingLocation;
    private String servingPack = "";
    private String servingNext = "";
    private String itemLangFile = "";
    private String itemText = "";
    private String itemOriginal = "";

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
            case STEP_PLAY -> stepPlay(client);
            case STEP_PLAY_CHECK -> stepCheckPlay(client);
            case STEP_FULLSCREEN -> stepFullscreen(client);
            case STEP_FULLSCREEN_CHECK -> stepCheckFullscreen(client);
            case STEP_FULLSCREEN_BACK -> stepLeaveFullscreen(client);
            case STEP_LANG_SEARCH -> stepLangSearch(client);
            case STEP_LANG_EDIT -> stepLangEdit(client);
            case STEP_LANG_CHECK -> stepCheckLang(client);
            case STEP_LANG_REVERT -> stepRevertLang(client);
            case STEP_TEXTURE -> stepTexture(client);
            case STEP_TEXTURE_SHOT -> stepCategoryShot(client);
            case STEP_TEXTURE_CHECK -> stepCheckTexture(client);
            case STEP_SERVING_PICK -> stepServingPick(client);
            case STEP_SERVING_CHECK -> stepCheckServing(client);
            case STEP_RELOAD -> stepReload(client);
            case STEP_SERVING_AFTER -> stepServingAfterReload(client);
            case STEP_ITEM_TEXT -> stepItemText(client);
            case STEP_ITEM_SEARCH -> stepItemSearch(client);
            case STEP_ITEM_EDIT -> stepItemEdit(client);
            case STEP_ITEM_CHECK -> stepCheckItemName(client);
            case STEP_ITEM_REVERT -> stepRevertItemName(client);
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
            this.textsAtStart = new TreeMap<>(config().texts());
            note("config at start: " + this.disabledAtStart.size() + " disabled, "
                    + this.soundsAtStart.size() + " tuned sounds, " + this.textsAtStart.size() + " text edits");
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
        advance(STEP_PLAY, 5);
    }

    // ------------------------------------------------------------------ sound preview

    private void stepPlay(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        UiButton play = button(client, "resourcemanager.ui.action.play");
        note("play button visible = " + (play != null));
        if (gui == null || play == null) {
            note("SKIP: no sound event selected, so the preview cannot be checked");
            advance(STEP_FULLSCREEN, 5);
            return;
        }
        click(client, play);
        note("clicked Play at [" + play.getX() + "," + play.getY() + "], tuning key = " + gui.soundTuningKey());
        advance(STEP_PLAY_CHECK, 20);
    }

    private void stepCheckPlay(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        String status = gui == null ? "<no screen>" : gui.statusMessage();
        note("status after Play: " + status);
        note("previewed event = " + (gui == null ? "" : gui.previewKey()));
        note((gui != null && gui.previewActive() ? "PASS" : "FAIL")
                + ": the preview sound is really audible (the sound engine reports the instance as active)");
        screenshot(client, "verify-07-play.png");
        advance(STEP_FULLSCREEN, 5);
    }

    // ------------------------------------------------------------------ expanded disabled list

    private void stepFullscreen(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        // The expanded view lists what the search box does not filter out, so clear it first.
        setSearch(client, "");
        int seeded = 0;
        for (String packId : new TreeSet<>(livePackIds())) {
            if (packId.startsWith("file/") && seeded < 3) {
                config().setDisabled(packId, TEXTURE, true);
                seeded++;
            }
        }
        config().save();
        note("seeded " + seeded + " disabled entries so the expanded list has rows to show");
        UiButton expand = button(client, "resourcemanager.ui.disabled.expand");
        note("expand button visible = " + (expand != null) + ", fullscreen already = "
                + (gui != null && gui.disabledFullscreenActive()));
        if (gui == null || expand == null) {
            note("SKIP: the sidebar with its expand button is not shown at this window size");
            advance(STEP_LANG_SEARCH, 5);
            return;
        }
        click(client, expand);
        advance(STEP_FULLSCREEN_CHECK, 10);
    }

    private void stepCheckFullscreen(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        boolean active = gui != null && gui.disabledFullscreenActive();
        note((active ? "PASS" : "FAIL") + ": the disabled column expanded to the whole window");
        DisabledList list = widget(client, DisabledList.class);
        if (list != null) {
            note("expanded disabled list [" + list.getX() + "," + list.getY() + " " + list.getWidth() + "x"
                    + list.getHeight() + "] window " + client.screen.width + "x" + client.screen.height);
            note((list.getWidth() > client.screen.width / 2 ? "PASS" : "FAIL")
                    + ": the expanded list spans the window width");
        }
        note("back button visible = " + (button(client, "resourcemanager.ui.disabled.back") != null));
        checkLayout(client, "fullscreen-disabled");
        screenshot(client, "verify-08-disabled-full.png");
        advance(STEP_FULLSCREEN_BACK, 5);
    }

    private void stepLeaveFullscreen(Minecraft client) {
        client.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        ResourceManagerScreen gui = screen(client);
        note((gui != null && !gui.disabledFullscreenActive() ? "PASS" : "FAIL")
                + ": ESC returns from the expanded view and keeps the GUI open (screen=" + name(client.screen) + ")");
        advance(STEP_LANG_SEARCH, 10);
    }

    // ------------------------------------------------------------------ original text editing

    private void stepLangSearch(Minecraft client) {
        setSearch(client, "en_us.json");
        for (int i = 0; i < 40; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        dumpTree(client, "en_us.json");
        ResourceManagerScreen gui = screen(client);
        note("text editor visible = " + (gui != null && gui.textEditorVisible()) + ", keys = "
                + (gui == null ? -1 : gui.translationKeys().size()));
        if (gui == null || gui.translationKeys().isEmpty()) {
            note("SKIP: no lang file selected, the tree walk needs adjusting");
            advance(STEP_TEXTURE, 5);
            return;
        }
        this.langFile = gui.selectedNode() == null ? "" : gui.selectedNode().displayPath();
        this.langKey = gui.translationKeys().get(0);
        note("lang file " + this.langFile + " with " + gui.translationKeys().size()
                + " keys, editing \"" + this.langKey + "\"");
        note((this.langFile.contains("lang/") ? "PASS" : "FAIL")
                + ": the lang file is recognised as a text resource (path " + this.langFile + ")");
        advance(STEP_LANG_EDIT, 10);
    }

    private void stepLangEdit(Minecraft client) {
        AbstractWidget filter = widgetByKey(client, "resourcemanager.ui.text.filter");
        AbstractWidget value = widgetByKey(client, "resourcemanager.ui.text.value");
        UiButton apply = button(client, "resourcemanager.ui.text.apply");
        if (filter == null || value == null || apply == null) {
            note("SKIP: the text editor widgets are not visible");
            advance(STEP_TEXTURE, 5);
            return;
        }
        note("the inspector switched to the text editor for this lang file");
        filter.setFocused(true);
        client.screen.setFocused(filter);
        for (int i = 0; i < this.langKey.length(); i++) {
            client.screen.charTyped(this.langKey.charAt(i), 0);
        }
        TextKeyList list = widget(client, TextKeyList.class);
        ResourceManagerScreen gui = screen(client);
        if (list != null) {
            client.screen.mouseClicked(list.getX() + 4, list.getY() + 6, 0);
            client.screen.mouseReleased(list.getX() + 4, list.getY() + 6, 0);
        }
        note("key row clicked, selected key = " + (gui == null ? "?" : gui.selectedTranslationKey()));
        value.setFocused(true);
        client.screen.setFocused(value);
        for (int i = 0; i < MARKER.length(); i++) {
            client.screen.charTyped(MARKER.charAt(i), 0);
        }
        click(client, apply);
        note("typed the new text into the value box and clicked Apply");
        advance(STEP_LANG_CHECK, 10);
    }

    private void stepCheckLang(Minecraft client) {
        String override = config().texts().get(this.langKey);
        String effective = Language.getInstance().getOrDefault(this.langKey, "");
        note("config override for \"" + this.langKey + "\" = " + override);
        note("the running game resolves \"" + this.langKey + "\" = \"" + effective + "\"");
        note((MARKER.equals(override) ? "PASS" : "FAIL") + ": the edited text is stored in the config");
        note((MARKER.equals(effective) ? "PASS" : "FAIL")
                + ": the edited text takes effect immediately, including on cached components");
        screenshot(client, "verify-09-text-edit.png");
        advance(STEP_LANG_REVERT, 5);
    }

    private void stepRevertLang(Minecraft client) {
        UiButton revert = button(client, "resourcemanager.ui.text.revert");
        if (revert == null) {
            note("SKIP: no revert button");
            advance(STEP_TEXTURE, 5);
            return;
        }
        click(client, revert);
        String override = config().texts().get(this.langKey);
        String effective = Language.getInstance().getOrDefault(this.langKey, "");
        note("after revert: override = " + override + ", the game resolves \"" + effective + "\"");
        note((override == null && !MARKER.equals(effective) ? "PASS" : "FAIL")
                + ": reverting removes the override and the pack text is used again");
        advance(STEP_TEXTURE, 5);
    }

    // ------------------------------------------------------------------ texture preview

    private void stepTexture(Minecraft client) {
        // The category filter matches on the full path, so it also proves the tree keeps directories.
        UiButton textures = button(client, "resourcemanager.ui.filter.textures");
        note("category filter button visible = " + (textures != null));
        setSearch(client, "");
        if (textures != null) {
            click(client, textures);
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            client.screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            client.screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            note("switched the category filter to textures and expanded two levels");
        }
        advance(STEP_TEXTURE_SHOT, 10);
    }

    private void stepCategoryShot(Minecraft client) {
        screenshot(client, "verify-11-category.png");
        TreeView tree = widget(client, TreeView.class);
        int rows = 0;
        String sample = "";
        while (tree != null && rows < 4000 && tree.tooltipAt(rows) != null) {
            String row = tree.tooltipAt(rows);
            if (row.indexOf(':') > 0 && row.indexOf('.') > 0 && sample.isEmpty()) {
                sample = row;
            }
            rows++;
        }
        note("textures-only tree shows " + rows + " expanded rows, first file row: " + sample);
        note((sample.contains("textures/") ? "PASS" : "FAIL")
                + ": the texture category only keeps textures (" + sample + ")");
        UiButton all = button(client, "resourcemanager.ui.filter.all");
        if (all != null) {
            click(client, all);
        }
        setSearch(client, "redstone_block");
        for (int i = 0; i < 40; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        ResourceManagerScreen gui = screen(client);
        String display = gui == null || gui.selectedNode() == null ? "?" : gui.selectedNode().displayPath();
        note("selected node = " + display);
        note((display.contains("textures/") ? "PASS" : "FAIL")
                + ": file rows keep their full path, which the category filter matches on");
        advance(STEP_TEXTURE_CHECK, 10);
    }

    private void stepCheckTexture(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        boolean ready = gui != null && gui.previewReady();
        note((ready ? "PASS" : "FAIL") + ": the inspector renders a thumbnail of the selected texture ("
                + (gui == null ? "" : gui.previewSize()) + ")");
        screenshot(client, "verify-10-texture-preview.png");
        advance(STEP_SERVING_PICK, 5);
    }

    private ResourceManagerScreen screen(Minecraft client) {
        return client.screen instanceof ResourceManagerScreen gui ? gui : null;
    }

    /** Types a query into the toolbar search box through the normal key handling. */
    private void setSearch(Minecraft client, String query) {
        focusSearch(client);
        for (int i = 0; i < 60; i++) {
            client.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
        }
        for (int i = 0; i < query.length(); i++) {
            client.screen.charTyped(query.charAt(i), 0);
        }
        AbstractWidget search = widgetByKey(client, "resourcemanager.ui.search");
        note("search box now reads \"" + (search instanceof net.minecraft.client.gui.components.EditBox box
                ? box.getValue() : "?") + "\"");
        // Hand keyboard navigation back to the tree, the way a real user's arrow keys would.
        if (search != null) {
            search.setFocused(false);
        }
        client.screen.setFocused(null);
    }

    private void stepReload(Minecraft client) {
        UiButton reload = button(client, "resourcemanager.ui.reload");
        if (reload == null) {
            note("FAIL: reload button missing");
            advance(STEP_ITEM_TEXT, 5);
            return;
        }
        note("clicking \"reload packs\"");
        click(client, reload);
        advance(STEP_SERVING_AFTER, 200);
    }

    /**
     * Picks a file whose current winner is its own pack, so disabling it really moves which pack serves
     * it, and disables it through the config. Nothing is reloaded yet: the GUI has to say so.
     */
    private void stepServingPick(Minecraft client) {
        ResourceIndex.Snapshot snapshot = ResourceIndex.cachedSnapshot();
        if (snapshot == null) {
            note("SKIP: no scan to pick a serving target from");
            advance(STEP_SERVING_CHECK, 5);
            return;
        }
        ResourceNode target = null;
        String next = "";
        for (ResourceNode packNode : snapshot.packNodesHighestFirst()) {
            for (ResourceNode node : filesUnder(packNode)) {
                if (!node.isFile() || node.location() == null || !node.path().endsWith(".png")) {
                    continue;
                }
                if (config().isDisabled(node.packId(), node.resourceId())) {
                    continue;
                }
                List<ResourceNode> providers = snapshot.providersOf(node.location());
                if (providers.size() < 2) {
                    continue;
                }
                // Priority 1 is the highest priority pack, so the current winner is the smallest number.
                int top = providers.stream().mapToInt(provider -> priorityOf(snapshot, provider))
                        .min().orElse(Integer.MAX_VALUE);
                if (priorityOf(snapshot, node) != top) {
                    continue;
                }
                int topPriority = top;
                ResourceNode second = providers.stream()
                        .filter(provider -> priorityOf(snapshot, provider) > topPriority)
                        .min(Comparator.comparingInt(provider -> priorityOf(snapshot, provider))).orElse(null);
                if (second == null) {
                    continue;
                }
                target = node;
                next = second.packId();
                break;
            }
            if (target != null) {
                break;
            }
        }
        if (target == null) {
            note("SKIP: no two provider texture is currently served by its own pack");
            advance(STEP_SERVING_CHECK, 5);
            return;
        }
        this.servingLocation = target.location();
        this.servingPack = target.packId();
        this.servingNext = next;
        note("serving target: " + this.servingPack + "|" + this.servingLocation
                + " should fall back to " + this.servingNext + " after a reload");
        config().setDisabledKey(ResourceManagerConfig.key(this.servingPack, this.servingLocation.toString()), true);
        config().save();
        advance(STEP_SERVING_CHECK, 10);
    }

    /**
     * The live manager answers with the pack the config points at, straight away: the filter sits in the
     * pack wrapper, so resource lookups follow the config while the loaded textures wait for a reload.
     */
    private void stepCheckServing(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        if (this.servingLocation == null) {
            note("SKIP: no serving target was picked");
            advance(STEP_RELOAD, 5);
            return;
        }
        if (gui == null || !selectServingTarget(client, gui)) {
            note("FAIL: could not select the serving target in the tree");
            advance(STEP_RELOAD, 5);
            return;
        }
        String serving = gui.servingPackId();
        String winner = gui.winnerPackId();
        String text = gui.servingText();
        note("effective source line before the reload: \"" + text + "\"");
        note((this.servingNext.equals(serving) ? "PASS" : "FAIL")
                + ": the live manager answers with the lower pack (" + serving + ")");
        note((this.servingNext.equals(winner) ? "PASS" : "FAIL")
                + ": the config points at the same pack (" + winner + ")");
        note((!this.servingPack.equals(serving) ? "PASS" : "FAIL")
                + ": the file is no longer answered by its own pack (" + this.servingPack + ")");
        note((text.equals(serving) ? "PASS" : "FAIL")
                + ": the inspector agrees with the live manager, with no divergence marker");
        screenshot(client, "verify-12-effective-source.png");
        advance(STEP_RELOAD, 5);
    }

    /** After the reload the answer must hold, and the disabled file has to still be listed. */
    private void stepServingAfterReload(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        if (this.servingLocation == null) {
            note("SKIP: no serving target was picked");
            advance(STEP_ITEM_TEXT, 5);
            return;
        }
        if (gui == null || !selectServingTarget(client, gui)) {
            note("FAIL: could not select the serving target after the reload");
            advance(STEP_ITEM_TEXT, 5);
            return;
        }
        String serving = gui.servingPackId();
        String text = gui.servingText();
        note("effective source line after the reload: \"" + text + "\"");
        note("PASS: the disabled file is still listed in the tree after the reload ("
                + this.servingPack + "|" + this.servingLocation + "), so it can be inspected and enabled");
        note((this.servingNext.equals(serving) ? "PASS" : "FAIL")
                + ": the live manager serves the file from the lower pack now (" + serving + ")");
        note((text.equals(serving) ? "PASS" : "FAIL")
                + ": the inspector reports it as in effect, with no divergence marker");
        note((gui.previewFromEffective() ? "PASS" : "FAIL")
                + ": the thumbnail shows the file the game serves");
        screenshot(client, "verify-13-serving-after.png");
        advance(STEP_ITEM_TEXT, 5);
    }

    /** Walks the filtered tree until the file row of the target pack is selected. */
    private boolean selectServingTarget(Minecraft client, ResourceManagerScreen gui) {
        if (this.servingLocation == null) {
            return false;
        }
        setSearch(client, this.servingLocation.getPath());
        for (int i = 0; i < 400; i++) {
            ResourceNode node = gui.selectedNode();
            if (node != null && this.servingLocation.equals(node.location())
                    && this.servingPack.equals(node.packId())) {
                return true;
            }
            client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        }
        return false;
    }

    private List<ResourceNode> filesUnder(ResourceNode node) {
        List<ResourceNode> found = new ArrayList<>();
        for (ResourceNode child : node.children()) {
            if (child.isFile()) {
                found.add(child);
            }
            found.addAll(filesUnder(child));
        }
        return found;
    }

    private int priorityOf(ResourceIndex.Snapshot snapshot, ResourceNode node) {
        ResourceNode pack = snapshot.packNode(node.packId());
        return pack == null ? -1 : pack.priority();
    }

    // ------------------------------------------------------------------ in game text (item names)

    /** Selects a {@code lang} file that declares item names, preferring the active language. */
    private void stepItemText(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        if (gui == null) {
            note("SKIP: the GUI is not open, the item name check needs it");
            advance(STEP_ESC, 5);
            return;
        }
        this.itemOriginal = Language.getInstance().getOrDefault(ITEM_KEY, "");
        this.itemText = new ItemStack(Items.DIAMOND_SWORD).getHoverName().getString();
        note("a diamond sword shows \"" + this.itemText + "\" in game (key " + ITEM_KEY + ")");
        String active = client.getLanguageManager().getSelected();
        for (String language : new String[] { active, "en_us", "zh_cn" }) {
            if (language == null || language.isEmpty()) {
                continue;
            }
            setSearch(client, "lang/" + language + ".json");
            // The search lists the lang file of every pack that ships one, so walk back from the last
            // row until a file that declares the item key with exactly the text the game shows turns up.
            for (int i = 0; i < 40; i++) {
                client.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
            }
            for (int i = 0; i < 140; i++) {
                if (gui.translationKeys().contains(ITEM_KEY) && this.itemText.equals(gui.fileTranslationValue(ITEM_KEY))) {
                    this.itemLangFile = gui.selectedNode() == null ? "" : gui.selectedNode().displayPath();
                    note("picked " + this.itemLangFile + " with " + gui.translationKeys().size()
                            + " keys; it declares " + ITEM_KEY + " = \"" + this.itemText + "\"");
                    advance(STEP_ITEM_SEARCH, 5);
                    return;
                }
                client.screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
            }
        }
        note("SKIP: no loaded lang file declares " + ITEM_KEY + " as \"" + this.itemText + "\" (language " + active + ")");
        advance(STEP_ESC, 5);
    }

    /** Filters the key list by the name the item shows in game, not by its key. */
    private void stepItemSearch(Minecraft client) {
        ResourceManagerScreen gui = screen(client);
        if (gui == null) {
            note("SKIP: the GUI is not open");
            advance(STEP_ESC, 5);
            return;
        }
        gui.setTextFilter(this.itemText);
        List<String> visible = gui.visibleTranslationKeys();
        note("filtering the key list by that text leaves " + visible.size() + " keys, first: "
                + (visible.isEmpty() ? "-" : visible.get(0)));
        note((visible.contains(ITEM_KEY) ? "PASS" : "FAIL")
                + ": searching by the text you see in game finds the key behind it");
        if (!visible.contains(ITEM_KEY)) {
            advance(STEP_ESC, 5);
            return;
        }
        TextKeyList list = widget(client, TextKeyList.class);
        if (list != null) {
            client.screen.mouseClicked(list.getX() + 4, list.getY() + 6, 0);
            client.screen.mouseReleased(list.getX() + 4, list.getY() + 6, 0);
        }
        note("clicked the key row, the editor now edits " + gui.selectedTranslationKey());
        // Clean shot for the docs: filtered by the name an item shows, with the value column visible.
        screenshot(client, "verify-15-text-editor.png");
        advance(STEP_ITEM_EDIT, 5);
    }

    private void stepItemEdit(Minecraft client) {
        AbstractWidget value = widgetByKey(client, "resourcemanager.ui.text.value");
        UiButton apply = button(client, "resourcemanager.ui.text.apply");
        ResourceManagerScreen gui = screen(client);
        if (value == null || apply == null || gui == null) {
            note("SKIP: the text editor widgets are not visible");
            advance(STEP_ESC, 5);
            return;
        }
        value.setFocused(true);
        client.screen.setFocused(value);
        for (int i = 0; i < ITEM_MARKER.length(); i++) {
            client.screen.charTyped(ITEM_MARKER.charAt(i), 0);
        }
        click(client, apply);
        note("typed the new item name and clicked Apply, the editor now edits "
                + gui.selectedTranslationKey());
        advance(STEP_ITEM_CHECK, 10);
    }

    private void stepCheckItemName(Minecraft client) {
        String override = config().texts().get(ITEM_KEY);
        String resolved = Language.getInstance().getOrDefault(ITEM_KEY, "");
        String shown = new ItemStack(Items.DIAMOND_SWORD).getHoverName().getString();
        note("config override for " + ITEM_KEY + " = " + override);
        note("the language lookup resolves it to \"" + resolved + "\"");
        note("a diamond sword now shows \"" + shown + "\"");
        note((ITEM_MARKER.equals(override) ? "PASS" : "FAIL") + ": the item name override is stored in the config");
        note((ITEM_MARKER.equals(shown) ? "PASS" : "FAIL")
                + ": the item really displays the new name in game (was \"" + this.itemOriginal + "\")");
        screenshot(client, "verify-14-item-name.png");
        advance(STEP_ITEM_REVERT, 5);
    }

    private void stepRevertItemName(Minecraft client) {
        UiButton revert = button(client, "resourcemanager.ui.text.revert");
        if (revert == null) {
            note("SKIP: no revert button");
            advance(STEP_ESC, 5);
            return;
        }
        click(client, revert);
        String override = config().texts().get(ITEM_KEY);
        String shown = new ItemStack(Items.DIAMOND_SWORD).getHoverName().getString();
        note("after revert: override = " + override + ", the sword shows \"" + shown + "\"");
        note((override == null && shown.equals(this.itemOriginal) ? "PASS" : "FAIL")
                + ": reverting brings the original item name back");
        advance(STEP_ESC, 5);
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
        for (String key : new TreeSet<>(config().texts().keySet())) {
            config().setText(key, null);
        }
        for (Map.Entry<String, String> entry : this.textsAtStart.entrySet()) {
            config().setText(entry.getKey(), entry.getValue());
        }
        config().save();
        note("restored the config to its original state (" + config().disabledCount() + " disabled, "
                + config().tunedSoundCount() + " tuned sounds, " + config().textCount() + " text edits)");
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
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), 1, message -> {
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
