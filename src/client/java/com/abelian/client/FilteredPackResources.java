package com.abelian.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Decorates a resource pack so that individually disabled files are invisible to the game.
 *
 * <p>1.21.4 resolves resources by asking packs from the highest priority one downwards and stopping at
 * the first pack that answers ({@code FallbackResourceManager.getResource}). Hiding a file at this level
 * therefore makes the next pack in the loading order provide it, which is exactly the behaviour the GUI
 * promises. Vanilla's own pack filters cannot be used for this: they abort the lookup instead of
 * continuing with the lower priority packs.
 */
public final class FilteredPackResources implements PackResources {
    private final PackResources delegate;
    private final String configId;

    private FilteredPackResources(PackResources delegate, String configId) {
        this.delegate = delegate;
        this.configId = configId;
    }

    public static PackResources wrap(PackResources pack) {
        if (pack instanceof FilteredPackResources) {
            return pack;
        }
        return new FilteredPackResources(pack, ResourceManagerConfig.normalizePackId(pack.packId()));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return delegate.getRootResource(path);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (ResourceManagerConfig.instance().isDisabled(configId, location.toString())) {
            return null;
        }
        return delegate.getResource(type, location);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        delegate.listResources(type, namespace, path, (location, supplier) -> {
            if (!ResourceManagerConfig.instance().isDisabled(configId, location.toString())) {
                output.accept(location, supplier);
            }
        });
    }

    @Override
    public java.util.Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        return delegate.getMetadataSection(type);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    /** Kept identical to the delegate: other code reports this id as {@code Resource#sourcePackId}. */
    @Override
    public String packId() {
        return delegate.packId();
    }

    @Override
    public void close() {
        // The pack lifecycle is owned by the list handed to MultiPackResourceManager, which closes the
        // original instances; closing the delegate here would double-close it.
    }

    /** Reads the sound events declared by a pack's {@code sounds.json}. Used by the GUI. */
    public static Map<String, JsonObject> readSoundEvents(PackResources pack, String namespace) {
        Map<String, JsonObject> events = new LinkedHashMap<>();
        Identifier location = Identifier.fromNamespaceAndPath(namespace, "sounds.json");
        IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, location);
        if (supplier == null) {
            return events;
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(supplier.get()))) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root instanceof JsonObject object) {
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        events.put(entry.getKey(), entry.getValue().getAsJsonObject());
                    } else {
                        events.put(entry.getKey(), new JsonObject());
                    }
                }
            }
        } catch (Exception ignored) {
            // A broken sounds.json is already reported by Minecraft's own loader.
        }
        return events;
    }
}
