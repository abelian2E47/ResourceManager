package com.abelian.client.verify;

import com.abelian.client.*;
import com.abelian.client.catalog.*;
import com.abelian.client.catalog.SubjectCatalog.*;
import com.abelian.client.sound.SoundTuning;
import com.abelian.client.ui.*;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;

import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.util.*;

/** Opt-in integration checks exercise real widgets, loaded packs and the live resource manager. */
public final class InventoryVerify {
    private int step, delay = 40, ticks;
    private String shot, oldName, selectedEvent;
    private byte[] configBytes;
    private boolean configExisted;
    private final List<String> report = new ArrayList<>();
    private static final String PACK_A = "RM-Verify-Model", PACK_B = "RM-Verify-Texture";

    public static void install() {
        if ("1".equals(System.getenv("RESOURCEMANAGER_INVENTORY_VERIFY")))
            ClientTickEvents.END_CLIENT_TICK.register(new InventoryVerify()::tick);
    }

    private void tick(Minecraft client) {
        if (step < 0) return;
        if (shot != null) {
            Screenshot.grab(client.gameDirectory, shot, client.getMainRenderTarget(), m -> {});
            if (step == 2) search((ResourceManagerScreen) client.screen, "钻石剑");
            if (step == 7) ((ResourceManagerScreen) client.screen).inventoryPage().setMode(1);
            if (step == 9) {
                var gui = (ResourceManagerScreen) client.screen;
                gui.inventoryPage().back();
                search(gui, "minecraft:cow");
            }
            if (step == 15) {
                client.getWindow().setWindowed(854, 480);
                client.resizeDisplay();
            }
            if (step == 16)
                click(
                        (ResourceManagerScreen) client.screen,
                        button(
                                (ResourceManagerScreen) client.screen,
                                "resourcemanager.inventory.layerMode"));
            if (step == 17) {
                var gui = (ResourceManagerScreen) client.screen;
                gui.inventoryPage().back();
                search(gui, "minecraft:diamond_chestplate");
            }
            shot = null;
        }
        if (client.getOverlay() != null || delay-- > 0) return;
        if (++ticks > 3600) {
            check(false, "Timed out at " + step);
            finish(client);
            return;
        }
        try {
            if (step == 0) {
                if (!(client.screen instanceof TitleScreen)) return;
                Path config = client.gameDirectory.toPath().resolve("config/resourcemanager.json");
                configExisted = Files.exists(config);
                configBytes = configExisted ? Files.readAllBytes(config) : new byte[0];
                fixtures(client);
                var repo = client.getResourcePackRepository();
                repo.reload();
                repo.addPack("file/" + PACK_A);
                repo.addPack("file/" + PACK_B);
                client.options.updateResourcePacks(repo);
                client.reloadResourcePacks()
                        .thenRun(
                                () ->
                                        client.execute(
                                                () -> {
                                                    client.options.guiScale().set(2);
                                                    client.getWindow().setWindowed(1280, 720);
                                                    client.resizeDisplay();
                                                    client.setScreen(
                                                            new ResourceManagerScreen(
                                                                    client.screen));
                                                }));
                next();
                delay = 60;
                return;
            }
            if (!(client.screen instanceof ResourceManagerScreen gui)
                    || gui.scanning()
                    || gui.snapshot() == null) return;
            var page = gui.inventoryPage();
            page.layout();
            switch (step) {
                case 1 -> {
                    if (page.selectedSubject() != null) page.back();
                    gui.setSearchQuery("");
                    page.layout();
                    check(
                            page.visibleSubjects().size() > 1000,
                            "Single inventory loads real registry entries");
                    shot = "readme-inventory.png";
                    next();
                }
                case 2 -> {
                    check(
                            page.visibleSubjects().stream()
                                    .anyMatch(s -> s.id().getPath().equals("diamond_sword")),
                            "Chinese search finds item");
                    search(gui, "minecraft:diamond_sword");
                    shot = "readme-search.png";
                    next();
                }
                case 3 -> {
                    check(
                            page.visibleSubjects().size() == 1,
                            "ID search isolates item: query="
                                    + gui.searchQuery()
                                    + " subject="
                                    + page.selectedSubject());
                    int before = ((EditBox) gui.focusedWidget()).getCursorPosition();
                    gui.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
                    check(
                            ((EditBox) gui.focusedWidget()).getCursorPosition() == before - 1,
                            "Focused search retains caret navigation");
                    var r = page.gridRect();
                    click(gui, r.x() + 8, r.y() + 8);
                    next();
                }
                case 4 -> {
                    check(page.selectedSubject() != null, "Slot click opens detail");
                    check(
                            page.visibleAssets().stream()
                                    .allMatch(a -> a.location().getPath().endsWith(".png")),
                            "Appearance hides JSON and metadata rows");
                    check(
                            page.relatedAssets().stream()
                                    .anyMatch(
                                            a ->
                                                    a.location()
                                                            .getPath()
                                                            .equals(
                                                                    "models/item/diamond_sword.json")),
                            "Hidden model remains in visual dependency graph");
                    check(
                            page.visibleAssets().stream()
                                    .noneMatch(a -> a.group() == Group.BLOCK_TEXTURE),
                            "Plain item does not show block textures");
                    check(gui.preview().hasImage(), "Texture preview decodes real bytes");
                    check(
                            page.layerPacks().containsAll(List.of(PACK_A, PACK_B)),
                            "Appearance layers include model-only and texture-only packs");
                    page.selectLayer(PACK_B);
                    page.applyLayer();
                    next();
                    delay = 60;
                }
                case 5 -> {
                    check(
                            serving("textures/item/diamond_sword.png").equals("file/" + PACK_B),
                            "Texture-only layer provides selected texture after reload");
                    check(
                            serving("models/item/diamond_sword.json").equals("vanilla"),
                            "Model falls back to bundled asset instead of another user pack");
                    check(
                            gui.config()
                                    .appearance(page.selectedSubject().key())
                                    .pack()
                                    .equals(PACK_B),
                            "Appearance choice persists after reload");
                    long loads = page.previewLoads();
                    long start = System.nanoTime();
                    for (int i = 0; i < 100; i++) page.layout();
                    Files.writeString(
                            client.gameDirectory
                                    .toPath()
                                    .resolve("inventory-performance-after.txt"),
                            "100_detail_layout_ms=" + (System.nanoTime() - start) / 1e6 + "\n");
                    check(
                            loads == page.previewLoads(),
                            "100 unchanged layouts do not decode another preview");
                    page.selectLayer(PACK_A);
                    page.applyLayer();
                    next();
                    delay = 60;
                }
                case 6 -> {
                    check(
                            serving("models/item/diamond_sword.json").equals("file/" + PACK_A),
                            "Model-only pack supplies model after group switch");
                    check(
                            serving("textures/item/diamond_sword.png").equals("vanilla"),
                            "Group switch excludes texture from previous user pack");
                    shot = "readme-visuals.png";
                    next();
                }
                case 7 -> {
                    check(
                            page.visibleAssets().size() == 1,
                            "Name is one entry instead of every language file");
                    oldName = gui.config().text(page.selectedSubject().translationKey());
                    var field =
                            gui.children().stream()
                                    .filter(w -> w instanceof EditBox e && e.visible)
                                    .map(w -> (EditBox) w)
                                    .max(Comparator.comparingInt(EditBox::getY))
                                    .orElseThrow();
                    field.setValue("Custom Sword");
                    click(gui, button(gui, "resourcemanager.inventory.name.save"));
                    next();
                }
                case 8 -> {
                    check(
                            new ItemStack(Items.DIAMOND_SWORD)
                                    .getHoverName()
                                    .getString()
                                    .equals("Custom Sword"),
                            "Name editor updates actual item display text");
                    check(
                            gui.config()
                                    .appearance(page.selectedSubject().key())
                                    .pack()
                                    .equals(PACK_A),
                            "Name edit leaves appearance layer independent");
                    gui.setTextOverride(
                            page.selectedSubject().translationKey(),
                            oldName == null ? "" : oldName);
                    shot = "readme-name.png";

                    next();
                }
                case 9 -> {
                    click(gui, button(gui, "resourcemanager.inventory.kind.3"));
                    next();
                }
                case 10 -> {
                    var r = page.gridRect();
                    click(gui, r.x() + 8, r.y() + 8);
                    next();
                }
                case 11 -> {
                    click(gui, button(gui, "resourcemanager.inventory.mode.2"));
                    next();
                }
                case 12 -> {
                    check(
                            !page.visibleAssets().isEmpty()
                                    && page.visibleAssets().stream()
                                            .allMatch(
                                                    a ->
                                                            a.location()
                                                                    .getPath()
                                                                    .equals("sounds.json")),
                            "Sound list shows events without JSON contents or duplicate OGG rows");
                    var sliders =
                            gui.children().stream()
                                    .filter(w -> w instanceof UiSlider s && s.visible)
                                    .map(w -> (UiSlider) w)
                                    .sorted(Comparator.comparingInt(UiSlider::getY))
                                    .toList();
                    String tuned = page.selectedAsset().eventKey();
                    check(
                            sliders.size() == 2,
                            "Sound detail offers volume and pitch sliders: " + sliders.size());
                    click(
                            gui,
                            sliders.get(0).getX() + sliders.get(0).getWidth() * 2 / 3,
                            sliders.get(0).getY() + 6);
                    check(
                            gui.soundTuning(tuned).volume() != 1,
                            "Sound volume changes through real input");
                    click(
                            gui,
                            sliders.get(1).getX() + sliders.get(1).getWidth() * 2 / 3,
                            sliders.get(1).getY() + 6);
                    check(
                            gui.soundTuning(tuned).pitch() != 1,
                            "Sound pitch changes through real input");
                    checkTuningReachesPlayback(client, gui, tuned);
                    checkTuningRoundTrip(gui, tuned);
                    page.selectAsset(
                            page.visibleAssets().stream()
                                    .filter(a -> a.eventKey().endsWith(".hurt"))
                                    .findFirst()
                                    .orElseThrow());
                    selectedEvent = page.selectedAsset().eventKey();
                    check(
                            page.layerPacks().contains(PACK_B),
                            "Audio-only pack is available without a sound definition");
                    page.selectLayer(PACK_B);
                    page.applyLayer();
                    next();
                    delay = 60;
                }
                case 13 -> {
                    check(
                            page.selectedAsset().eventKey().equals(selectedEvent),
                            "Reload preserves the selected non-first sound event");
                    System.out.println(
                            "[InventoryState] after sound reload subject="
                                    + page.selectedSubject()
                                    + " asset="
                                    + page.selectedAsset()
                                    + " visible="
                                    + page.visibleAssets().size()
                                    + " query="
                                    + gui.searchQuery()
                                    + " selected="
                                    + page.selectedPack()
                                    + " stored="
                                    + gui.config().soundLayers());
                    check(
                            gui.config().soundLayer(page.selectedAsset().eventKey()).equals(PACK_B),
                            "Sound provider is independently selected");
                    check(
                            serving("sounds/mob/cow/hurt1.ogg").equals("file/" + PACK_B),
                            "Audio-only layer serves live audio after reload");
                    check(
                            client.getSoundManager()
                                            .getSoundEvent(ResourceLocation.parse(selectedEvent))
                                    != null,
                            "Audio-only layer keeps its bundled event definition");
                    check(
                            gui.config()
                                    .appearance("ITEM:minecraft:diamond_sword")
                                    .pack()
                                    .equals(PACK_A),
                            "Sound switch preserves item appearance");
                    click(gui, button(gui, "resourcemanager.ui.action.play"));
                    System.out.println(
                            "[InventoryAudio] start event="
                                    + page.selectedAsset().eventKey()
                                    + " pack="
                                    + page.selectedPack()
                                    + " status="
                                    + gui.displayedStatus());
                    next();
                    delay = 20;
                }
                case 14 -> {
                    check(
                            PackSoundPreview.isPlaying()
                                    || gui.displayedStatus().startsWith("Playing"),
                            "Selected layer audio decodes and plays");
                    click(gui, button(gui, "resourcemanager.inventory.stop"));
                    check(!PackSoundPreview.isPlaying(), "Stop releases audio player");
                    shot = "readme-sound.png";

                    next();
                }
                case 15 -> {
                    checkBounds(gui);
                    shot = "readme-compact.png";

                    next();
                }
                case 16 -> {
                    check(page.layerList().visible, "Compact mode reaches pack layers");
                    checkBounds(gui);
                    shot = "readme-compact-layers.png";

                    next();
                }
                case 17 -> {
                    click(gui, button(gui, "resourcemanager.inventory.kind.0"));
                    next();
                }
                case 18 -> {
                    client.getWindow().setWindowed(1280, 720);
                    client.resizeDisplay();
                    gui.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    next();
                }
                case 19 -> {
                    check(
                            page.relatedAssets().stream()
                                    .anyMatch(
                                            a ->
                                                    a.location()
                                                            .getPath()
                                                            .equals(
                                                                    "textures/entity/equipment/humanoid/diamond.png")),
                            "Equipment texture still resolves");
                    check(
                            page.visibleAssets().stream().noneMatch(a -> a.group() == Group.MODEL),
                            "Equipment JSON remains hidden");
                    gui.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    search(gui, "minecraft:grass_block");
                    next();
                }
                case 20 -> {
                    gui.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    next();
                }
                case 21 -> {
                    check(
                            page.visibleAssets().stream()
                                    .anyMatch(a -> a.group() == Group.BLOCK_TEXTURE),
                            "Block appearance includes its actual textures");
                    shot = "readme-block.png";
                    long start = System.nanoTime();
                    var catalog = new SubjectCatalog(gui.snapshot());
                    long built = System.nanoTime();
                    for (String id :
                            List.of("diamond_sword", "grass_block", "cow", "diamond_chestplate"))
                        catalog.assets(
                                catalog.subjects().stream()
                                        .filter(s -> s.id().getPath().equals(id))
                                        .findFirst()
                                        .orElseThrow());
                    Files.writeString(
                            client.gameDirectory
                                    .toPath()
                                    .resolve("inventory-performance-after.txt"),
                            "catalog_ms="
                                    + (built - start) / 1e6
                                    + "\ndiscover_four_ms="
                                    + (System.nanoTime() - built) / 1e6
                                    + "\n",
                            StandardOpenOption.APPEND);
                    check(
                            ResourceManagerConfig.load()
                                    .appearance("ITEM:minecraft:diamond_sword")
                                    .pack()
                                    .equals(PACK_A),
                            "Grouped layer survives config serialization");
                    next();
                }
                case 22 -> {
                    search(gui, "this-resource-does-not-exist");
                    next();
                }
                case 23 -> {
                    check(
                            page.visibleAssets().isEmpty() && page.selectedAsset() == null,
                            "Empty detail search clears hidden selection and controls");
                    search(gui, "");
                    next();
                }
                case 24 -> {
                    check(
                            page.selectedAsset() != null && !page.visibleAssets().isEmpty(),
                            "Clearing detail filter restores a visible selection");
                    var subject = page.selectedSubject();
                    var appearance =
                            new AppearanceLayers(gui.snapshot(), subject, page.relatedAssets());
                    var chosen =
                            appearance.packs().contains("vanilla")
                                    ? "vanilla"
                                    : appearance.packs().get(0);
                    var location = page.selectedAsset().location();
                    var copy =
                            ResourceWorkspace.prepare(
                                    gui.snapshot(), appearance.source(chosen, location), location);
                    byte[] bytes = Files.readAllBytes(copy);
                    ResourceWorkspace.prepareAppearance(
                            gui.snapshot(), appearance, chosen, subject.key());
                    check(
                            Arrays.equals(bytes, Files.readAllBytes(copy)),
                            "Preparing grouped local edits preserves an existing file");
                    Path assetsRoot =
                            client.gameDirectory
                                    .toPath()
                                    .resolve("resourcepacks/ResourceManager-Edits/assets");
                    check(
                            appearance.files().stream()
                                    .filter(f -> !appearance.source(chosen, f).isEmpty())
                                    .allMatch(
                                            f ->
                                                    Files.exists(
                                                            assetsRoot
                                                                    .resolve(f.getNamespace())
                                                                    .resolve(f.getPath()))),
                            "Local edit pack contains the complete model and texture group");
                    check(
                            gui.config()
                                    .appearance(subject.key())
                                    .pack()
                                    .equals("ResourceManager-Edits"),
                            "Editing binds the whole appearance to its local pack");
                    check(
                            appearance.files().stream()
                                    .filter(f -> f.getPath().endsWith(".png.mcmeta"))
                                    .allMatch(
                                            f ->
                                                    appearance.source(chosen, f).isEmpty()
                                                            || appearance
                                                                    .source(chosen, f)
                                                                    .equals(
                                                                            appearance.source(
                                                                                    chosen,
                                                                                    f.withPath(
                                                                                            p ->
                                                                                                    p
                                                                                                            .substring(
                                                                                                                    0,
                                                                                                                    p
                                                                                                                                    .length()
                                                                                                                            - 7))))),
                            "Animation metadata follows its image provider");
                    var repo = client.getResourcePackRepository();
                    repo.reload();
                    repo.addPack("file/ResourceManager-Edits");
                    client.options.updateResourcePacks(repo);
                    next();
                    delay = 60;
                }
                case 25 -> {
                    check(
                            serving(page.selectedAsset().location().getPath())
                                    .equals("file/ResourceManager-Edits"),
                            "Grouped local edits become the live provider after reload");
                    next();
                }
                default -> finish(client);
            }
        } catch (Throwable error) {
            check(false, "Step " + step + ": " + error);
            error.printStackTrace();
            finish(client);
        }
    }

    private void fixtures(Minecraft client) throws Exception {
        Path packs = client.gameDirectory.toPath().resolve("resourcepacks");
        for (String id : List.of(PACK_A, PACK_B)) {
            Path root = packs.resolve(id);
            Files.createDirectories(root);
            Files.writeString(
                    root.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":46,\"description\":\"Inventory regression"
                            + " fixture\"}}");
        }
        for (int i = 1; i <= 3; i++) {
            String path = "sounds/mob/cow/hurt" + i + ".ogg";
            Path audio = packs.resolve(PACK_B + "/assets/minecraft/" + path);
            Files.createDirectories(audio.getParent());
            try (var stream =
                    client.getResourceManager()
                            .getResource(ResourceLocation.parse("minecraft:" + path))
                            .orElseThrow()
                            .open()) {
                Files.copy(stream, audio, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path model = packs.resolve(PACK_A + "/assets/minecraft/models/item/diamond_sword.json");
        Files.createDirectories(model.getParent());
        Files.writeString(
                model,
                "{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"minecraft:item/diamond_sword\"}}");
        Path texture = packs.resolve(PACK_B + "/assets/minecraft/textures/item/diamond_sword.png");
        Files.createDirectories(texture.getParent());
        try (var stream =
                client.getResourceManager()
                        .getResource(
                                ResourceLocation.parse("minecraft:textures/item/diamond_sword.png"))
                        .orElseThrow()
                        .open()) {
            Files.copy(stream, texture, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String serving(String path) {
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(ResourceLocation.parse("minecraft:" + path))
                .map(r -> r.sourcePackId())
                .orElse("");
    }

    /**
     * Proves the tuning reaches real playback. The engine multiplies what it reads from
     * {@code getVolume()}/{@code getPitch()} into the channel, so the mixin has to show up there. Two
     * instances of the same event sampled from the same seed cancel the event's own random volume and
     * pitch and leave only the multiplier.
     */
    private void checkTuningReachesPlayback(
            Minecraft client, ResourceManagerScreen gui, String event) {
        SoundTuning tuning = gui.soundTuning(event);
        float[] tuned = playbackVolumeAndPitch(client, event);
        gui.config().setSoundTuning(event, SoundTuning.DEFAULT);
        float[] plain = playbackVolumeAndPitch(client, event);
        gui.config().setSoundTuning(event, tuning);
        check(
                plain[0] > 0 && near(tuned[0] / plain[0], tuning.volume()),
                "Game playback multiplies volume by "
                        + tuning.volume()
                        + " ("
                        + plain[0]
                        + " -> "
                        + tuned[0]
                        + ")");
        check(
                near(tuned[1] / plain[1], tuning.pitch()),
                "Game playback multiplies pitch by "
                        + tuning.pitch()
                        + " ("
                        + plain[1]
                        + " -> "
                        + tuned[1]
                        + ")");
    }

    /** Volume and pitch a real sound instance reports for the tuned event. */
    private float[] playbackVolumeAndPitch(Minecraft client, String event) {
        var instance =
                new SimpleSoundInstance(
                        ResourceLocation.parse(event),
                        SoundSource.NEUTRAL,
                        1,
                        1,
                        RandomSource.create(7L),
                        false,
                        0,
                        SoundInstance.Attenuation.NONE,
                        0,
                        0,
                        0,
                        false);
        instance.resolve(client.getSoundManager());
        return new float[] {instance.getVolume(), instance.getPitch()};
    }

    private void checkTuningRoundTrip(ResourceManagerScreen gui, String event) {
        gui.config().save();
        SoundTuning stored = ResourceManagerConfig.load().soundTuning(event);
        check(stored.equals(gui.soundTuning(event)), "Sound tuning survives a config round trip");
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) <= 1.0E-3f + Math.abs(expected) * 1.0E-3f;
    }

    private void search(ResourceManagerScreen gui, String value) {
        gui.focusSearch();
        ((EditBox) gui.focusedWidget()).setValue("");
        for (char c : value.toCharArray()) gui.charTyped(c, 0);
    }

    private void next() {
        step++;
        ticks = 0;
        delay = 12;
    }

    private void check(boolean ok, String message) {
        String line = (ok ? "PASS: " : "FAIL: ") + message;
        report.add(line);
        System.out.println("[InventoryVerify] " + line);
    }

    private UiButton button(ResourceManagerScreen gui, String key) {
        return gui.children().stream()
                .filter(
                        w ->
                                w instanceof UiButton b
                                        && b.visible
                                        && b.getMessage().getContents()
                                                instanceof TranslatableContents t
                                        && t.getKey().equals(key))
                .map(w -> (UiButton) w)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing " + key));
    }

    private void click(ResourceManagerScreen gui, AbstractWidget w) {
        click(gui, w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2);
    }

    private void click(ResourceManagerScreen gui, int x, int y) {
        gui.mouseClicked(x, y, 0);
        gui.mouseReleased(x, y, 0);
    }

    private void checkBounds(ResourceManagerScreen gui) {
        check(
                gui.children().stream()
                        .filter(w -> w instanceof AbstractWidget a && a.visible)
                        .map(w -> (AbstractWidget) w)
                        .allMatch(
                                w ->
                                        w.getX() >= 0
                                                && w.getY() >= 0
                                                && w.getRight() <= gui.width
                                                && w.getBottom() <= gui.height),
                "Controls fit " + gui.width + "x" + gui.height);
    }

    private void finish(Minecraft client) {
        step = -1;
        PackSoundPreview.stop();
        try {
            Files.write(
                    client.gameDirectory.toPath().resolve("inventory-verify-report.txt"), report);
            client.setScreen(new TitleScreen());
            Path config = client.gameDirectory.toPath().resolve("config/resourcemanager.json");
            if (configExisted) Files.write(config, configBytes);
            else Files.deleteIfExists(config);
            ResourceManagerConfig.replaceInstance(ResourceManagerConfig.load());
            var repo = client.getResourcePackRepository();
            var ids = new ArrayList<>(repo.getSelectedIds());
            ids.remove("file/" + PACK_A);
            ids.remove("file/" + PACK_B);
            repo.setSelected(ids);
            client.options.resourcePacks.clear();
            client.options.incompatibleResourcePacks.clear();
            for (var selected : repo.getSelectedPacks())
                if (!selected.isFixedPosition()) {
                    client.options.resourcePacks.add(selected.getId());
                    if (!selected.getCompatibility().isCompatible())
                        client.options.incompatibleResourcePacks.add(selected.getId());
                }
            client.options.save();
        } catch (Exception error) {
            error.printStackTrace();
        }
        System.out.println(
                "[InventoryVerify] COMPLETE "
                        + report.stream().filter(s -> s.startsWith("PASS")).count()
                        + " PASS / "
                        + report.stream().filter(s -> s.startsWith("FAIL")).count()
                        + " FAIL");
        client.stop();
    }
}
