package com.abelian.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Reading of {@code lang/*.json} resources and lookup of the string the client currently resolves.
 *
 * <p>Parsed files are cached per pack, so opening a 7000 key vanilla language file costs a single pass.
 * The cache is dropped whenever the packs are reloaded.
 */
public final class LangText {
    private static final String PREFIX = "lang/";
    private static final String SUFFIX = ".json";

    private static final Map<String, Map<String, String>> FILES = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> KEYS = new ConcurrentHashMap<>();

    private LangText() {
    }

    public static boolean isLangFile(String path) {
        return path != null && path.startsWith(PREFIX) && path.endsWith(SUFFIX);
    }

    /** {@code lang/en_us.json} becomes {@code en_us}; anything else becomes an empty string. */
    public static String languageOf(String path) {
        return isLangFile(path) ? path.substring(PREFIX.length(), path.length() - SUFFIX.length()) : "";
    }

    /** Sorted translation keys declared by one file. */
    public static List<String> keys(String packId, Identifier file, IoSupplier<InputStream> supplier) {
        String cacheKey = cacheKey(packId, file);
        List<String> cached = KEYS.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> keys = new ArrayList<>(values(packId, file, supplier).keySet());
        keys.sort(Comparator.comparing(key -> key.toLowerCase(Locale.ROOT)));
        List<String> result = List.copyOf(keys);
        KEYS.put(cacheKey, result);
        return result;
    }

    /** Key to value map declared by one file; empty when the file is missing or broken. */
    public static Map<String, String> values(String packId, Identifier file, IoSupplier<InputStream> supplier) {
        String cacheKey = cacheKey(packId, file);
        Map<String, String> cached = FILES.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (supplier != null) {
            try (InputStream stream = supplier.get()) {
                JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                if (root != null && root.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            values.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            } catch (Exception ignored) {
                // A broken or filtered language file simply shows up empty.
            }
        }
        Map<String, String> result = Map.copyOf(values);
        FILES.put(cacheKey, result);
        return result;
    }

    /** The string the client resolves for a key right now, including GUI overrides. */
    public static String effective(String key) {
        return Language.getInstance().getOrDefault(key, "");
    }

    public static void clearCache() {
        FILES.clear();
        KEYS.clear();
    }

    private static String cacheKey(String packId, Identifier file) {
        return packId + "|" + file;
    }
}
