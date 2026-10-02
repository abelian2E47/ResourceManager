package com.abelian.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Persistent debug state: which individual resources are hidden for a pack and the tuned volume/pitch
 * of sound events. Everything is keyed by the *normalised* pack id so that the ids shown in the GUI
 * (folder names from {@code resourcepacks/}) match the ids reported by the pack at runtime.
 */
public final class ResourceManagerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "resourcemanager.json";
    private static volatile ResourceManagerConfig instance;

    private final Set<String> disabled = ConcurrentHashMap.newKeySet();
    private final Map<String, SoundTuning> sounds = new ConcurrentHashMap<>();
    private volatile int version;

    private ResourceManagerConfig() {
    }

    public static ResourceManagerConfig instance() {
        ResourceManagerConfig current = instance;
        if (current == null) {
            synchronized (ResourceManagerConfig.class) {
                if (instance == null) {
                    instance = load();
                }
                current = instance;
            }
        }
        return current;
    }

    /** Replaces the shared instance; used by the client initialiser after reading the config file. */
    public static void replaceInstance(ResourceManagerConfig config) {
        instance = config;
    }

    public static ResourceManagerConfig load() {
        ResourceManagerConfig config = new ResourceManagerConfig();
        Path file = config.path();
        if (!Files.exists(file)) {
            return config;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null) {
                return config;
            }
            JsonArray disabled = root.getAsJsonArray("disabled");
            if (disabled != null) {
                for (JsonElement value : disabled) {
                    if (value.isJsonPrimitive()) {
                        config.disabled.add(value.getAsString());
                    }
                }
            }
            JsonObject soundObject = root.getAsJsonObject("sounds");
            if (soundObject != null) {
                for (Map.Entry<String, JsonElement> entry : soundObject.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        JsonObject tuning = entry.getValue().getAsJsonObject();
                        config.sounds.put(entry.getKey(), new SoundTuning(
                                number(tuning, "volume", 1.0F),
                                number(tuning, "pitch", 1.0F)));
                    }
                }
            }
        } catch (Exception ignored) {
            // A broken debug file must never stop the client from starting.
        }
        return config;
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }

    /** {@code file/MyPack.zip} is the runtime id, {@code MyPack.zip} is what the GUI shows. */
    public static String normalizePackId(String packId) {
        if (packId == null) {
            return "";
        }
        return packId.startsWith("file/") ? packId.substring("file/".length()) : packId;
    }

    public static String key(String packId, String location) {
        return normalizePackId(packId) + "|" + location;
    }

    public boolean isDisabled(String packId, String location) {
        return disabled.contains(key(packId, location));
    }

    public boolean isDisabledKey(String key) {
        return disabled.contains(key);
    }

    public void setDisabled(String packId, String location, boolean value) {
        setDisabledKey(key(packId, location), value);
    }

    public void setDisabledKey(String key, boolean value) {
        if (value ? disabled.add(key) : disabled.remove(key)) {
            version++;
        }
    }

    /** Live view of the disabled keys, safe to iterate from the render thread. */
    public Set<String> disabledKeys() {
        return Collections.unmodifiableSet(disabled);
    }

    public int disabledCount() {
        return disabled.size();
    }

    public void clearDisabled() {
        if (!disabled.isEmpty()) {
            disabled.clear();
            version++;
        }
    }

    public int disabledCountForPack(String packId) {
        String prefix = normalizePackId(packId) + "|";
        int count = 0;
        for (String key : disabled) {
            if (key.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    public SoundTuning sound(String event) {
        SoundTuning direct = sounds.get(event);
        if (direct != null) {
            return direct;
        }
        // Older files stored "<pack>|<namespace>:<event>"; keep honouring those entries.
        for (Map.Entry<String, SoundTuning> entry : sounds.entrySet()) {
            int separator = entry.getKey().indexOf('|');
            if (separator >= 0 && entry.getKey().substring(separator + 1).equals(event)) {
                return entry.getValue();
            }
        }
        return SoundTuning.DEFAULT;
    }

    public void setSound(String event, float volume, float pitch) {
        float clampedVolume = clamp(volume);
        float clampedPitch = clamp(pitch);
        if (Math.abs(clampedVolume - 1.0F) < 0.001F && Math.abs(clampedPitch - 1.0F) < 0.001F) {
            if (sounds.remove(event) != null) {
                version++;
            }
            return;
        }
        sounds.put(event, new SoundTuning(clampedVolume, clampedPitch));
        version++;
    }

    public Map<String, SoundTuning> sounds() {
        return Map.copyOf(sounds);
    }

    public int tunedSoundCount() {
        return sounds.size();
    }

    /** Bumped on every mutation so the GUI can invalidate caches. */
    public int version() {
        return version;
    }

    public void save() {
        try {
            Path file = path();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            JsonArray disabled = new JsonArray();
            new LinkedHashSet<>(this.disabled).stream().sorted().forEach(disabled::add);
            root.add("disabled", disabled);
            JsonObject soundObject = new JsonObject();
            sounds.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                JsonObject tuning = new JsonObject();
                tuning.addProperty("volume", entry.getValue().volume());
                tuning.addProperty("pitch", entry.getValue().pitch());
                soundObject.add(entry.getKey(), tuning);
            });
            root.add("sounds", soundObject);
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException ignored) {
            // Configuration is best effort; runtime behaviour stays usable.
        }
    }

    private Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(4.0F, value));
    }

    public record SoundTuning(float volume, float pitch) {
        public static final SoundTuning DEFAULT = new SoundTuning(1.0F, 1.0F);

        public boolean isDefault() {
            return Math.abs(volume - 1.0F) < 0.001F && Math.abs(pitch - 1.0F) < 0.001F;
        }
    }
}
