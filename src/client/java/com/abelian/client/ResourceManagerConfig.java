package com.abelian.client;

import com.abelian.client.sound.SoundTuning;
import com.google.gson.*;

import net.minecraft.client.Minecraft;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Persisted subject appearance, independent sound choices, and display-name edits. */
public final class ResourceManagerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ResourceManagerConfig instance;

    public record Appearance(String pack, Map<String, Set<String>> sources) {}

    private final Map<String, Appearance> appearances = new LinkedHashMap<>();
    private volatile Map<String, Set<String>> visualSources = Map.of();
    private final Map<String, String> soundLayers = new ConcurrentHashMap<>();
    private final Map<String, String> soundDefinitions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> soundSources = new ConcurrentHashMap<>();
    private final Map<String, SoundTuning> sounds = new ConcurrentHashMap<>();
    private final Map<String, String> texts = new ConcurrentHashMap<>();
    private final Set<String> legacyDisabled = ConcurrentHashMap.newKeySet();
    private volatile int textVersion;

    private ResourceManagerConfig() {}

    public static ResourceManagerConfig instance() {
        if (instance == null)
            synchronized (ResourceManagerConfig.class) {
                if (instance == null) instance = load();
            }
        return instance;
    }

    public static void replaceInstance(ResourceManagerConfig config) {
        instance = config;
    }

    public static String normalizePackId(String id) {
        return id == null ? "" : id.startsWith("file/") ? id.substring(5) : id;
    }

    public synchronized Appearance appearance(String subject) {
        return appearances.get(subject);
    }

    public synchronized Map<String, Appearance> appearances() {
        return Map.copyOf(appearances);
    }

    public synchronized void setAppearance(
            String subject, String pack, Map<String, Set<String>> sources) {
        appearances.remove(subject);
        appearances.put(subject, new Appearance(normalizePackId(pack), Map.copyOf(sources)));
        legacyDisabled.removeIf(
                key -> sources.keySet().stream().anyMatch(file -> key.endsWith("|" + file)));
        Map<String, Set<String>> built = new HashMap<>();
        appearances.values().forEach(choice -> built.putAll(choice.sources()));
        visualSources = Map.copyOf(built);
    }

    public String soundLayer(String event) {
        return soundLayers.getOrDefault(event, "");
    }

    public Map<String, String> soundLayers() {
        return Map.copyOf(soundLayers);
    }

    public Map<String, String> soundDefinitions() {
        return Map.copyOf(soundDefinitions);
    }

    public void setSoundLayer(
            String event, String pack, String definition, Map<String, Set<String>> files) {
        String id = normalizePackId(pack);
        soundLayers.put(event, id);
        soundDefinitions.put(event, normalizePackId(definition));
        files.forEach(
                (file, base) -> {
                    soundSources.put(file, Set.copyOf(base));
                    legacyDisabled.removeIf(key -> key.endsWith("|" + file));
                });
    }

    public boolean isDisabled(String pack, String location) {
        String id = normalizePackId(pack);
        var visual = visualSources.get(location);
        var audio = soundSources.get(location);
        return visual != null
                ? !visual.contains(id)
                : audio != null
                        ? !audio.contains(id)
                        : legacyDisabled.contains(id + "|" + location);
    }

    public SoundTuning soundTuning(String event) {
        return sounds.getOrDefault(event, SoundTuning.DEFAULT);
    }

    public void setSoundTuning(String event, SoundTuning tuning) {
        if (event == null || event.isEmpty()) return;
        if (tuning.isDefault()) sounds.remove(event);
        else sounds.put(event, tuning);
    }

    public String text(String key) {
        return texts.get(key);
    }

    public int textVersion() {
        return textVersion;
    }

    public void setText(String key, String value) {
        String old = value == null || value.isEmpty() ? texts.remove(key) : texts.put(key, value);
        if (!Objects.equals(old, value == null || value.isEmpty() ? null : value)) textVersion++;
    }

    private static Path path() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config/resourcemanager.json");
    }

    public static ResourceManagerConfig load() {
        ResourceManagerConfig config = new ResourceManagerConfig();
        if (!Files.exists(path())) return config;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
            if (root.has("disabled"))
                root.getAsJsonArray("disabled")
                        .forEach(e -> config.legacyDisabled.add(e.getAsString()));
            if (root.has("texts"))
                root.getAsJsonObject("texts")
                        .entrySet()
                        .forEach(e -> config.texts.put(e.getKey(), e.getValue().getAsString()));
            if (root.has("sounds"))
                root.getAsJsonObject("sounds")
                        .entrySet()
                        .forEach(
                                e -> {
                                    var value = e.getValue().getAsJsonObject();
                                    String key = e.getKey();
                                    int split = key.indexOf('|');
                                    config.setSoundTuning(
                                            split < 0 ? key : key.substring(split + 1),
                                            new SoundTuning(
                                                    number(value, "volume"),
                                                    number(value, "pitch")));
                                });
            if (root.has("appearances"))
                root.getAsJsonObject("appearances")
                        .entrySet()
                        .forEach(
                                e -> {
                                    var v = e.getValue().getAsJsonObject();
                                    config.setAppearance(
                                            e.getKey(),
                                            v.get("pack").getAsString(),
                                            sources(v.getAsJsonObject("sources")));
                                });
            if (root.has("soundLayers"))
                root.getAsJsonObject("soundLayers")
                        .entrySet()
                        .forEach(
                                e ->
                                        config.soundLayers.put(
                                                e.getKey(), e.getValue().getAsString()));
            config.soundDefinitions.putAll(config.soundLayers);
            if (root.has("soundDefinitions"))
                root.getAsJsonObject("soundDefinitions")
                        .entrySet()
                        .forEach(
                                e ->
                                        config.soundDefinitions.put(
                                                e.getKey(), e.getValue().getAsString()));
            if (root.has("soundSources"))
                config.soundSources.putAll(sources(root.getAsJsonObject("soundSources")));
        } catch (Exception error) {
            com.abelian.ResourceManager.LOGGER.warn("Could not read ResourceManager config", error);
        }
        return config;
    }

    private static float number(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsFloat() : 1;
    }

    private static Map<String, Set<String>> sources(JsonObject json) {
        Map<String, Set<String>> result = new HashMap<>();
        json.entrySet()
                .forEach(
                        e -> {
                            Set<String> ids = new HashSet<>();
                            e.getValue().getAsJsonArray().forEach(id -> ids.add(id.getAsString()));
                            result.put(e.getKey(), Set.copyOf(ids));
                        });
        return result;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(path().getParent());
            JsonObject root = new JsonObject();
            root.add("disabled", GSON.toJsonTree(legacyDisabled));
            root.add("texts", GSON.toJsonTree(texts));
            root.add("sounds", GSON.toJsonTree(sounds));
            root.add("appearances", GSON.toJsonTree(appearances));
            root.add("soundLayers", GSON.toJsonTree(soundLayers));
            root.add("soundDefinitions", GSON.toJsonTree(soundDefinitions));
            root.add("soundSources", GSON.toJsonTree(soundSources));
            Files.writeString(path(), GSON.toJson(root));
        } catch (Exception error) {
            com.abelian.ResourceManager.LOGGER.warn("Could not save ResourceManager config", error);
        }
    }
}
