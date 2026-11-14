package com.abelian.client.catalog;

import com.abelian.client.*;
import com.abelian.client.catalog.SubjectCatalog.*;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * One selected pack for a subject's whole visual graph, with only bundled resources as fallback.
 */
public final class AppearanceLayers {
    private final ResourceIndex.Snapshot snapshot;
    private final Subject subject;
    private final List<ResourceLocation> files;
    private final List<String> packs;

    public AppearanceLayers(ResourceIndex.Snapshot snapshot, Subject subject, List<Asset> assets) {
        this.snapshot = snapshot;
        this.subject = subject;
        Set<ResourceLocation> graph = new LinkedHashSet<>();
        for (Asset asset : assets)
            if (asset.group() != Group.NAME && asset.group() != Group.SOUND) {
                graph.add(asset.location());
                if (asset.location().getPath().endsWith(".png"))
                    graph.add(asset.location().withPath(p -> p + ".mcmeta"));
            }
        files = List.copyOf(graph);
        Set<String> ids = new HashSet<>();
        files.forEach(file -> snapshot.providersOf(file).forEach(node -> ids.add(node.packId())));
        packs =
                snapshot.packs().stream()
                        .sorted(Comparator.comparingInt(ResourceIndex.PackInfo::priority))
                        .map(ResourceIndex.PackInfo::id)
                        .filter(ids::contains)
                        .toList();
    }

    public List<String> packs() {
        return packs;
    }

    public List<ResourceLocation> files() {
        return files;
    }

    private boolean bundled(String pack) {
        var source = snapshot.packResources(pack);
        return source != null && !source.packId().startsWith("file/");
    }

    public String source(String chosen, ResourceLocation file) {
        if (file.getPath().endsWith(".png.mcmeta")) {
            ResourceLocation image = file.withPath(p -> p.substring(0, p.length() - 7));
            String imagePack = source(chosen, image);
            return snapshot.providersOf(file).stream().anyMatch(n -> n.packId().equals(imagePack))
                    ? imagePack
                    : "";
        }
        var providers = snapshot.providersOf(file);
        for (int i = providers.size() - 1; i >= 0; i--)
            if (providers.get(i).packId().equals(chosen)) return chosen;
        for (int i = providers.size() - 1; i >= 0; i--)
            if (bundled(providers.get(i).packId())) return providers.get(i).packId();
        return "";
    }

    private Set<String> sourceSet(String chosen, ResourceLocation file) {
        String actual = source(chosen, file);
        return actual.isEmpty() ? Set.of() : Set.of(actual);
    }

    public void apply(ResourceManagerConfig config, String chosen) {
        if (!packs.contains(chosen)) throw new IllegalArgumentException("Unknown appearance layer");
        // Shared textures/models are global Minecraft resources. Keep existing overlapping groups
        // coherent instead of letting a later choice silently split an earlier object's appearance.
        Set<String> graph = new LinkedHashSet<>();
        files.forEach(file -> graph.add(file.toString()));
        Set<String> affected = new HashSet<>();
        var previous = config.appearances();
        boolean expanded;
        do {
            expanded = false;
            for (var entry : previous.entrySet()) {
                if (!affected.contains(entry.getKey())
                        && entry.getValue().sources().entrySet().stream()
                                .anyMatch(
                                        e ->
                                                graph.contains(e.getKey())
                                                        && !e.getValue()
                                                                .equals(
                                                                        sourceSet(
                                                                                chosen,
                                                                                ResourceLocation
                                                                                        .parse(
                                                                                                e
                                                                                                        .getKey()))))) {
                    affected.add(entry.getKey());
                    graph.addAll(entry.getValue().sources().keySet());
                    expanded = true;
                }
            }
        } while (expanded);
        Map<String, Set<String>> sources = new HashMap<>();
        for (String path : graph) {
            String actual = source(chosen, ResourceLocation.parse(path));
            sources.put(path, actual.isEmpty() ? Set.of() : Set.of(actual));
        }
        for (String key : affected) config.setAppearance(key, chosen, sources);
        config.setAppearance(subject.key(), chosen, sources);
    }
}
