package com.abelian.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads the enabled packs from the live resource manager and turns them into the tree the GUI shows.
 *
 * <p>Using the runtime manager (instead of walking {@code resourcepacks/} on disk) keeps the pack ids,
 * the loading order and the set of enabled packs exact, and it yields the real provider chain of every
 * resource, which is what "disable this file so a lower pack can win" is built on.
 *
 * <p>One 1.21.4 quirk shapes this class: {@code listResources(path, ...)} rejects an empty path
 * ({@code FileUtil.decomposePath} only accepts real path segments), so a namespace cannot be listed in
 * one call. The scan therefore lists a set of known asset roots plus every root it finds inside the
 * packs on disk, and probes namespace level files such as {@code sounds.json} separately.
 */
public final class ResourceIndex {
    /** Asset directories that resource packs actually ship; the disk walk adds anything else. */
    private static final List<String> ASSET_ROOTS = List.of(
            "textures", "models", "blockstates", "items", "atlases", "lang", "font", "sounds",
            "particles", "equipment", "post_effect", "shaders", "waypoint_style", "gui", "optifine",
            "misc", "realms");

    private static final Comparator<String> PATH_ORDER =
            Comparator.comparing((String value) -> value.toLowerCase(Locale.ROOT)).thenComparing(value -> value);

    private ResourceIndex() {
    }

    public static CompletableFuture<Snapshot> scanAsync() {
        return CompletableFuture.supplyAsync(ResourceIndex::scan);
    }

    private static Snapshot scan() {
        Minecraft client = Minecraft.getInstance();
        ResourceManager manager = client.getResourceManager();
        List<PackResources> packs = manager.listPacks().toList();

        Map<String, ResourceNode> packNodes = new LinkedHashMap<>();
        Map<String, PackResources> packSources = new LinkedHashMap<>();
        Map<String, Integer> priorities = new HashMap<>();
        List<PackInfo> infos = new ArrayList<>();
        for (int index = 0; index < packs.size(); index++) {
            // listPacks() is ordered from lowest to highest priority: later entries win.
            PackResources pack = packs.get(index);
            String packId = ResourceManagerConfig.normalizePackId(pack.packId());
            if (priorities.containsKey(packId)) {
                continue;
            }
            int priority = packs.size() - index;
            priorities.put(packId, priority);
            packNodes.put(packId, ResourceNode.pack(packId, priority));
            packSources.put(packId, pack);
            infos.add(new PackInfo(packId, priority));
        }

        Map<ResourceLocation, List<String>> locations = new LinkedHashMap<>();
        Catalogue catalogue = readCatalogue(client);
        Set<String> roots = new LinkedHashSet<>(ASSET_ROOTS);
        roots.addAll(catalogue.roots());
        for (String root : roots) {
            try {
                Map<ResourceLocation, List<Resource>> stacks = manager.listResourceStacks(root, location -> true);
                stacks.forEach((location, stack) -> {
                    List<String> ids = locations.computeIfAbsent(location, key -> new ArrayList<>());
                    for (Resource resource : stack) {
                        String packId = ResourceManagerConfig.normalizePackId(resource.source().packId());
                        if (packNodes.containsKey(packId) && !ids.contains(packId)) {
                            ids.add(packId);
                        }
                    }
                });
            } catch (Exception error) {
                com.abelian.ResourceManager.LOGGER.warn("Could not list resources under '{}'", root, error);
            }
        }
        for (ResourceLocation location : catalogue.rootFiles()) {
            try {
                List<Resource> stack = manager.getResourceStack(location);
                if (stack.isEmpty()) {
                    continue;
                }
                List<String> ids = locations.computeIfAbsent(location, key -> new ArrayList<>());
                for (Resource resource : stack) {
                    String packId = ResourceManagerConfig.normalizePackId(resource.source().packId());
                    if (packNodes.containsKey(packId) && !ids.contains(packId)) {
                        ids.add(packId);
                    }
                }
            } catch (Exception error) {
                com.abelian.ResourceManager.LOGGER.warn("Could not resolve {}", location, error);
            }
        }
        for (Map.Entry<String, PackResources> entry : packSources.entrySet()) {
            for (String namespace : safeNamespaces(entry.getValue())) {
                ResourceLocation sounds = ResourceLocation.fromNamespaceAndPath(namespace, "sounds.json");
                if (hasResource(entry.getValue(), sounds)) {
                    List<String> ids = locations.computeIfAbsent(sounds, key -> new ArrayList<>());
                    if (!ids.contains(entry.getKey())) {
                        ids.add(entry.getKey());
                    }
                }
            }
        }

        Map<String, List<ResourceLocation>> byPack = new HashMap<>();
        locations.forEach((location, ids) -> {
            for (String packId : ids) {
                byPack.computeIfAbsent(packId, key -> new ArrayList<>()).add(location);
            }
        });

        Map<ResourceLocation, List<ResourceNode>> providers = new LinkedHashMap<>();
        int fileCount = 0;
        int soundEventCount = 0;
        for (Map.Entry<String, ResourceNode> entry : packNodes.entrySet()) {
            String packId = entry.getKey();
            ResourceNode packNode = entry.getValue();
            PackResources pack = packSources.get(packId);
            List<ResourceLocation> packLocations = byPack.get(packId);
            if (pack == null || packLocations == null) {
                continue;
            }
            Map<String, List<ResourceLocation>> byNamespace = new TreeMap<>();
            for (ResourceLocation location : packLocations) {
                byNamespace.computeIfAbsent(location.getNamespace(), key -> new ArrayList<>()).add(location);
            }
            for (Map.Entry<String, List<ResourceLocation>> namespaceEntry : byNamespace.entrySet()) {
                String namespace = namespaceEntry.getKey();
                Map<String, Leaf> leaves = new TreeMap<>(PATH_ORDER);
                for (ResourceLocation location : namespaceEntry.getValue()) {
                    if (!location.getPath().isEmpty()) {
                        leaves.put(location.getPath(), new Leaf(location));
                    }
                }
                if (leaves.isEmpty()) {
                    continue;
                }
                Map<String, List<ResourceNode.SoundEvent>> events = readSoundEvents(pack, namespace, leaves);
                ResourceNode namespaceNode = ResourceNode.namespace(packId, namespace);
                new DirBuilder().build(leaves, namespaceNode, packId, namespace, providers, events);
                packNode.addChild(namespaceNode);
                fileCount += namespaceNode.fileCount();
            }
        }
        soundEventCount = countSoundEvents(packNodes);
        com.abelian.ResourceManager.LOGGER.info(
                "Indexed {} packs, {} resources ({}+{} roots) and {} sound events from the live resource manager",
                packNodes.size(), providers.size(), roots.size(), catalogue.rootFiles().size(), soundEventCount);
        return new Snapshot(infos, packNodes, packSources, providers, fileCount, soundEventCount);
    }

    private static boolean hasResource(PackResources pack, ResourceLocation location) {
        try {
            return pack.getResource(PackType.CLIENT_RESOURCES, location) != null;
        } catch (Exception error) {
            return false;
        }
    }

    private static Set<String> safeNamespaces(PackResources pack) {
        try {
            return pack.getNamespaces(PackType.CLIENT_RESOURCES);
        } catch (Exception error) {
            com.abelian.ResourceManager.LOGGER.warn("Could not read namespaces of pack {}", pack.packId(), error);
            return Set.of();
        }
    }

    private static int countSoundEvents(Map<String, ResourceNode> packNodes) {
        int count = 0;
        for (ResourceNode node : packNodes.values()) {
            count += countSoundEvents(node);
        }
        return count;
    }

    private static int countSoundEvents(ResourceNode root) {
        int count = 0;
        for (ResourceNode child : root.children()) {
            if (child.isSoundEvent()) {
                count++;
            } else {
                count += countSoundEvents(child);
            }
        }
        return count;
    }

    private static Map<String, List<ResourceNode.SoundEvent>> readSoundEvents(PackResources pack, String namespace,
            Map<String, Leaf> leaves) {
        if (!leaves.containsKey("sounds.json")) {
            return Map.of();
        }
        Map<String, com.google.gson.JsonObject> raw = FilteredPackResources.readSoundEvents(pack, namespace);
        if (raw.isEmpty()) {
            return Map.of();
        }
        List<String> ids = new ArrayList<>(raw.keySet());
        ids.sort(String::compareTo);
        List<ResourceNode.SoundEvent> events = new ArrayList<>();
        for (String id : ids) {
            events.add(new ResourceNode.SoundEvent(namespace, id));
        }
        Map<String, List<ResourceNode.SoundEvent>> result = new HashMap<>();
        result.put("sounds.json", events);
        return result;
    }

    // ------------------------------------------------------------------ catalogue

    /** Asset roots and namespace level files found inside {@code resourcepacks/}. */
    private record Catalogue(Set<String> roots, Set<ResourceLocation> rootFiles) {
    }

    private static Catalogue readCatalogue(Minecraft client) {
        Set<String> roots = new LinkedHashSet<>();
        Set<ResourceLocation> rootFiles = new LinkedHashSet<>();
        Path directory = client.gameDirectory.toPath().resolve("resourcepacks");
        if (!Files.isDirectory(directory)) {
            return new Catalogue(roots, rootFiles);
        }
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path pack : stream.toList()) {
                try {
                    if (Files.isDirectory(pack)) {
                        scanDirectory(pack.resolve("assets"), roots, rootFiles);
                    } else if (pack.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        scanArchive(pack, roots, rootFiles);
                    }
                } catch (Exception error) {
                    com.abelian.ResourceManager.LOGGER.warn("Could not inspect pack {}", pack, error);
                }
            }
        } catch (IOException error) {
            com.abelian.ResourceManager.LOGGER.warn("Could not list resourcepacks directory", error);
        }
        return new Catalogue(roots, rootFiles);
    }

    private static void scanDirectory(Path assets, Set<String> roots, Set<ResourceLocation> rootFiles) {
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(assets)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                Path relative = assets.relativize(file);
                record(relative.toString().replace('\\', '/'), roots, rootFiles);
            });
        } catch (IOException error) {
            com.abelian.ResourceManager.LOGGER.warn("Could not walk {}", assets, error);
        }
    }

    private static void scanArchive(Path pack, Set<String> roots, Set<ResourceLocation> rootFiles) throws IOException {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("assets/")) {
                    record(name.substring("assets/".length()), roots, rootFiles);
                }
            }
        }
    }

    /** Splits {@code <namespace>/<rest>} into an asset root ({@code rest} has a folder) or a namespace file. */
    private static void record(String relative, Set<String> roots, Set<ResourceLocation> rootFiles) {
        int namespaceEnd = relative.indexOf('/');
        if (namespaceEnd <= 0 || namespaceEnd == relative.length() - 1) {
            return;
        }
        String namespace = relative.substring(0, namespaceEnd);
        String rest = relative.substring(namespaceEnd + 1);
        int directoryEnd = rest.indexOf('/');
        if (directoryEnd > 0) {
            roots.add(rest.substring(0, directoryEnd));
            return;
        }
        ResourceLocation location = ResourceLocation.tryBuild(namespace, rest);
        if (location != null) {
            rootFiles.add(location);
        }
    }

    private record Leaf(ResourceLocation location) {
    }

    /** Temporary tree used while a pack is being walked; turns into {@link ResourceNode}s afterwards. */
    private static final class DirBuilder {
        private final Map<String, DirBuilder> directories = new TreeMap<>(PATH_ORDER);
        private final Map<String, Leaf> files = new TreeMap<>(PATH_ORDER);

        void add(String[] segments, int index, Leaf leaf) {
            if (index == segments.length - 1) {
                files.put(segments[index], leaf);
                return;
            }
            directories.computeIfAbsent(segments[index], key -> new DirBuilder()).add(segments, index + 1, leaf);
        }

        void build(Map<String, Leaf> leaves, ResourceNode parent, String packId, String namespace,
                Map<ResourceLocation, List<ResourceNode>> providers,
                Map<String, List<ResourceNode.SoundEvent>> events) {
            for (Map.Entry<String, Leaf> entry : leaves.entrySet()) {
                add(entry.getKey().split("/"), 0, entry.getValue());
            }
            buildInto(parent, packId, namespace, providers, events, "");
        }

        /**
         * @param prefix full path of the enclosing directory, so a node keeps both its own name (row label)
         *               and the complete path (needed to tell a {@code lang} file from a texture)
         */
        void buildInto(ResourceNode parent, String packId, String namespace,
                Map<ResourceLocation, List<ResourceNode>> providers,
                Map<String, List<ResourceNode.SoundEvent>> events, String prefix) {
            for (Map.Entry<String, DirBuilder> entry : directories.entrySet()) {
                String directoryPath = prefix.isEmpty() ? entry.getKey() : prefix + "/" + entry.getKey();
                ResourceNode directory = ResourceNode.directory(packId, namespace, entry.getKey(), directoryPath);
                entry.getValue().buildInto(directory, packId, namespace, providers, events, directoryPath);
                parent.addChild(directory);
            }
            for (Map.Entry<String, Leaf> entry : files.entrySet()) {
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "/" + entry.getKey();
                if (!path.equals(path.trim())) {
                    continue;
                }
                ResourceNode file = ResourceNode.file(packId, namespace, entry.getKey(), path,
                        entry.getValue().location());
                List<ResourceNode.SoundEvent> soundEvents = events.get(path);
                if (soundEvents != null) {
                    for (ResourceNode.SoundEvent event : soundEvents) {
                        file.addSoundEvent(event.namespace(), event.id());
                        file.addChild(ResourceNode.sound(packId, namespace, entry.getValue().location(), event.id()));
                    }
                }
                parent.addChild(file);
                providers.computeIfAbsent(entry.getValue().location(), key -> new ArrayList<>()).add(file);
            }
        }
    }

    public record PackInfo(String id, int priority) {
    }

    /** Immutable result of one scan. */
    public static final class Snapshot {
        private final List<PackInfo> packs;
        private final Map<String, ResourceNode> packNodes;
        private final Map<String, PackResources> sources;
        private final Map<ResourceLocation, List<ResourceNode>> providers;
        private final int fileCount;
        private final int soundEventCount;

        Snapshot(List<PackInfo> packs, Map<String, ResourceNode> packNodes, Map<String, PackResources> sources,
                Map<ResourceLocation, List<ResourceNode>> providers, int fileCount, int soundEventCount) {
            this.packs = List.copyOf(packs);
            this.packNodes = Map.copyOf(packNodes);
            this.sources = Map.copyOf(sources);
            this.providers = Map.copyOf(providers);
            this.fileCount = fileCount;
            this.soundEventCount = soundEventCount;
        }

        /** Enabled packs, lowest priority first (the loading order). */
        public List<PackInfo> packs() {
            return packs;
        }

        public List<ResourceNode> packNodesHighestFirst() {
            List<ResourceNode> nodes = new ArrayList<>();
            for (int index = packs.size() - 1; index >= 0; index--) {
                ResourceNode node = packNodes.get(packs.get(index).id());
                if (node != null) {
                    nodes.add(node);
                }
            }
            return nodes;
        }

        public ResourceNode packNode(String packId) {
            return packNodes.get(packId);
        }

        /** The pack that owns a file, used to read its raw contents for the preview and the lang editor. */
        public PackResources packResources(String packId) {
            return sources.get(packId);
        }

        /** All nodes holding the same resource, lowest priority first. */
        public List<ResourceNode> providersOf(ResourceLocation location) {
            return providers.getOrDefault(location, List.of());
        }

        public int fileCount() {
            return fileCount;
        }

        public int soundEventCount() {
            return soundEventCount;
        }

        public List<ResourceNode> allFiles() {
            List<ResourceNode> files = new ArrayList<>();
            for (ResourceNode node : packNodes.values()) {
                collectFiles(node, files);
            }
            return files;
        }

        private static void collectFiles(ResourceNode node, List<ResourceNode> out) {
            if (node.isFile() && !node.isSoundDefinition()) {
                out.add(node);
            }
            for (ResourceNode child : node.children()) {
                collectFiles(child, out);
            }
        }
    }
}
