package com.abelian.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Raw provider index. Only roots needed by item appearance, names and sounds are scanned. */
public final class ResourceIndex {
    private static final List<String> ROOTS =
            List.of("textures", "models", "blockstates", "items", "equipment", "lang", "sounds");
    private static volatile Snapshot cached;
    private static volatile ResourceManager cachedManager;

    private ResourceIndex() {}

    public static boolean isStale() {
        return cached == null || cachedManager != Minecraft.getInstance().getResourceManager();
    }

    public static Snapshot cachedSnapshot() {
        return cached;
    }

    public static CompletableFuture<Snapshot> scanAsync() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        return CompletableFuture.supplyAsync(() -> scan(manager));
    }

    private static Snapshot scan(ResourceManager manager) {
        long start = System.nanoTime();
        List<PackResources> packs = manager.listPacks().toList();
        List<PackInfo> infos = new ArrayList<>();
        Map<String, PackResources> sources = new HashMap<>();
        Map<ResourceLocation, List<ResourceNode>> providers = new HashMap<>();
        for (int i = 0; i < packs.size(); i++) {
            PackResources pack = FilteredPackResources.raw(packs.get(i));
            String id = ResourceManagerConfig.normalizePackId(pack.packId());
            if (sources.putIfAbsent(id, pack) != null) continue;
            infos.add(new PackInfo(id, packs.size() - i));
            for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                for (String root : ROOTS)
                    try {
                        pack.listResources(
                                PackType.CLIENT_RESOURCES,
                                namespace,
                                root,
                                (location, supplier) ->
                                        providers
                                                .computeIfAbsent(location, key -> new ArrayList<>())
                                                .add(new ResourceNode(id, location)));
                    } catch (Exception error) {
                        com.abelian.ResourceManager.LOGGER.warn(
                                "Could not index {} of {}", root, id, error);
                    }
                ResourceLocation sound =
                        ResourceLocation.fromNamespaceAndPath(namespace, "sounds.json");
                if (pack.getResource(PackType.CLIENT_RESOURCES, sound) != null)
                    providers
                            .computeIfAbsent(sound, key -> new ArrayList<>())
                            .add(new ResourceNode(id, sound));
            }
        }
        Snapshot built = new Snapshot(infos, sources, providers);
        cached = built;
        cachedManager = manager;
        com.abelian.ResourceManager.LOGGER.info(
                "Indexed {} packs and {} relevant files in {} ms",
                infos.size(),
                providers.size(),
                (System.nanoTime() - start) / 1_000_000);
        return built;
    }

    public record PackInfo(String id, int priority) {}

    public static final class Snapshot {
        private final List<PackInfo> packs;
        private final Map<String, PackResources> sources;
        private final Map<ResourceLocation, List<ResourceNode>> providers;
        private final List<ResourceNode> files;

        Snapshot(
                List<PackInfo> packs,
                Map<String, PackResources> sources,
                Map<ResourceLocation, List<ResourceNode>> providers) {
            this.packs = List.copyOf(packs);
            this.sources = Map.copyOf(sources);
            Map<ResourceLocation, List<ResourceNode>> frozen = new HashMap<>();
            providers.forEach((file, nodes) -> frozen.put(file, List.copyOf(nodes)));
            this.providers = Map.copyOf(frozen);
            this.files = this.providers.values().stream().flatMap(List::stream).toList();
        }

        public List<PackInfo> packs() {
            return packs;
        }

        public PackResources packResources(String id) {
            return sources.get(id);
        }

        public List<ResourceNode> providersOf(ResourceLocation file) {
            return providers.getOrDefault(file, List.of());
        }

        public List<ResourceNode> allFiles() {
            return files;
        }

        public int fileCount() {
            return providers.size();
        }
    }
}
