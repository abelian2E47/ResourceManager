package com.abelian.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import java.nio.file.*;
import java.util.function.Consumer;

/** Edits live in a separate resource pack; original directory and zip packs are never rewritten. */
public final class ResourceWorkspace {
    private ResourceWorkspace() {}

    public static void open(
            ResourceIndex.Snapshot snapshot,
            String pack,
            ResourceLocation location,
            Consumer<Component> status) {
        try {
            Minecraft client = Minecraft.getInstance();
            Path target = prepare(snapshot, pack, location);
            var repository = client.getResourcePackRepository();
            repository.reload();
            repository.addPack("file/ResourceManager-Edits");
            client.options.updateResourcePacks(repository);
            Util.getPlatform().openFile(target.toFile());
            status.accept(Component.translatable("resourcemanager.inventory.edit.ready"));
        } catch (Exception error) {
            status.accept(
                    Component.translatable(
                            "resourcemanager.inventory.edit.error", error.getMessage()));
        }
    }

    public static void openAppearance(
            ResourceIndex.Snapshot snapshot,
            com.abelian.client.catalog.AppearanceLayers appearance,
            String chosen,
            String subjectKey,
            ResourceLocation location,
            Consumer<Component> status) {
        try {
            prepareAppearance(snapshot, appearance, chosen, subjectKey);
            open(snapshot, appearance.source(chosen, location), location, status);
        } catch (Exception error) {
            status.accept(
                    Component.translatable(
                            "resourcemanager.inventory.edit.error", error.getMessage()));
        }
    }

    public static void prepareAppearance(
            ResourceIndex.Snapshot snapshot,
            com.abelian.client.catalog.AppearanceLayers appearance,
            String chosen,
            String subjectKey)
            throws java.io.IOException {
        for (ResourceLocation file : appearance.files()) {
            String source = appearance.source(chosen, file);
            if (!source.isEmpty()) prepare(snapshot, source, file);
        }
        var config = ResourceManagerConfig.instance();
        var sources = new java.util.HashMap<String, java.util.Set<String>>();
        for (ResourceLocation file : appearance.files())
            sources.put(
                    file.toString(),
                    appearance.source(chosen, file).isEmpty()
                            ? java.util.Set.of()
                            : java.util.Set.of("ResourceManager-Edits"));
        config.setAppearance(subjectKey, "ResourceManager-Edits", sources);
        config.save();
    }

    /** Materialize an editable copy without replacing an existing edit. */
    public static Path prepare(
            ResourceIndex.Snapshot snapshot, String pack, ResourceLocation location)
            throws java.io.IOException {
        Minecraft client = Minecraft.getInstance();
        Path root =
                client.gameDirectory
                        .toPath()
                        .resolve("resourcepacks/ResourceManager-Edits")
                        .toAbsolutePath()
                        .normalize();
        Path target =
                root.resolve("assets")
                        .resolve(location.getNamespace())
                        .resolve(location.getPath())
                        .normalize();
        if (!target.startsWith(root.resolve("assets")))
            throw new java.io.IOException("Invalid resource path");
        Files.createDirectories(target.getParent());
        Path metadata = root.resolve("pack.mcmeta");
        if (!Files.exists(metadata))
            Files.writeString(
                    metadata,
                    "{\"pack\":{\"pack_format\":46,\"description\":\"Resource Manager local"
                            + " edits\"}}");
        if (!Files.exists(target)) {
            var supplier =
                    FilteredPackResources.raw(snapshot.packResources(pack))
                            .getResource(PackType.CLIENT_RESOURCES, location);
            if (supplier == null)
                throw new java.io.IOException("Resource unavailable: " + location);
            try (var stream = supplier.get()) {
                Files.copy(stream, target);
            }
        }
        if (location.getPath().endsWith(".png")) {
            ResourceLocation sidecar =
                    ResourceLocation.fromNamespaceAndPath(
                            location.getNamespace(), location.getPath() + ".mcmeta");
            Path sidecarTarget = target.resolveSibling(target.getFileName() + ".mcmeta");
            if (!Files.exists(sidecarTarget)) {
                var metadataSource =
                        FilteredPackResources.raw(snapshot.packResources(pack))
                                .getResource(PackType.CLIENT_RESOURCES, sidecar);
                if (metadataSource != null)
                    try (var stream = metadataSource.get()) {
                        Files.copy(stream, sidecarTarget);
                    }
            }
        }
        return target;
    }
}
