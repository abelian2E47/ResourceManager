package com.abelian.client.search;

import com.google.gson.*;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small translated-name index for registry searches, warmed off the render thread. */
public final class BilingualSearch {
    public record Token(String raw, Set<String> ids) {}

    public record Query(List<Token> tokens) {
        public boolean isEmpty() {
            return tokens.isEmpty();
        }
    }

    private record Alias(String text, String id) {}

    private static volatile List<Alias> aliases = List.of();
    private static volatile Runnable listener;
    private static volatile boolean warmed;
    private static int generation;

    private BilingualSearch() {}

    public static Query parse(String text) {
        if (text == null || text.isBlank()) return new Query(List.of());
        List<Token> tokens = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            Set<String> ids = new HashSet<>();
            for (Alias alias : aliases) if (alias.text().contains(word)) ids.add(alias.id());
            tokens.add(new Token(word, Set.copyOf(ids)));
        }
        return new Query(List.copyOf(tokens));
    }

    public static void onWarm(Runnable callback) {
        listener = callback;
    }

    public static synchronized void invalidate() {
        generation++;
        warmed = false;
        aliases = List.of();
    }

    public static void warm() {
        int token;
        synchronized (BilingualSearch.class) {
            if (warmed) return;
            token = generation;
        }
        Minecraft client = Minecraft.getInstance();
        var manager = client.getResourceManager();
        Set<String> languages =
                new LinkedHashSet<>(
                        List.of("en_us", "zh_cn", client.getLanguageManager().getSelected()));
        List<Alias> built = new ArrayList<>();
        for (String namespace : manager.getNamespaces())
            for (String language : languages) {
                var file =
                        ResourceLocation.fromNamespaceAndPath(
                                namespace, "lang/" + language + ".json");
                for (var resource : manager.getResourceStack(file))
                    try (var reader =
                            new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        for (var entry : json.entrySet()) {
                            String key = entry.getKey();
                            if (!(key.startsWith("item.")
                                            || key.startsWith("block.")
                                            || key.startsWith("entity."))
                                    || !entry.getValue().isJsonPrimitive()) continue;
                            String text = entry.getValue().getAsString();
                            if (text.length() < 65)
                                built.add(
                                        new Alias(
                                                text.toLowerCase(Locale.ROOT),
                                                key.substring(key.lastIndexOf('.') + 1)));
                        }
                    } catch (Exception ignored) {
                        /* A reload may close a pack while a warm-up is running. */
                    }
            }
        synchronized (BilingualSearch.class) {
            if (token != generation) return;
            aliases = List.copyOf(built);
            warmed = true;
        }
        client.execute(
                () -> {
                    var callback = listener;
                    if (callback != null) callback.run();
                });
    }
}
