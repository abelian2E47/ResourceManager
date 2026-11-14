package com.abelian.client.catalog;

import com.abelian.client.FilteredPackResources;
import com.abelian.client.ResourceIndex;
import com.abelian.client.ResourceNode;
import com.google.gson.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Registry subjects and their resource graph, independent of the screen and its current filters.
 */
public final class SubjectCatalog {
    public enum Kind {
        ITEM,
        BLOCK,
        ENTITY
    }

    public enum Group {
        ITEM_TEXTURE,
        BLOCK_TEXTURE,
        ENTITY_TEXTURE,
        MODEL,
        NAME,
        SOUND,
        OTHER
    }

    public record Subject(
            ResourceLocation id, String translationKey, ItemStack icon, Kind kind, Block block) {
        public String name() {
            return Component.translatable(translationKey).getString();
        }

        public String key() {
            return kind + ":" + id;
        }
    }

    public record Asset(Group group, ResourceLocation location, String textKey, String eventKey) {
        public String key() {
            return location + "|" + textKey + "|" + eventKey;
        }

        public String label() {
            return location.getPath().endsWith(".ogg")
                    ? location.getPath()
                    : !eventKey.isEmpty()
                            ? eventKey
                            : !textKey.isEmpty() ? textKey : location.getPath();
        }
    }

    private final ResourceIndex.Snapshot snapshot;
    private final Map<ResourceLocation, ResourceNode> files = new TreeMap<>();
    private final Map<ResourceLocation, List<JsonElement>> jsonCache = new HashMap<>();
    private final Map<String, Map<String, JsonObject>> sounds = new LinkedHashMap<>();
    private final Map<String, List<Asset>> related = new HashMap<>();
    private final List<Subject> subjects = new ArrayList<>();
    private boolean soundsLoaded;

    public SubjectCatalog(ResourceIndex.Snapshot snapshot) {
        this.snapshot = snapshot;
        for (ResourceNode file : snapshot.allFiles()) files.put(file.location(), file);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Block block = item instanceof BlockItem bi ? bi.getBlock() : null;
            subjects.add(
                    new Subject(
                            BuiltInRegistries.ITEM.getKey(item),
                            item.getDescriptionId(),
                            new ItemStack(item),
                            block == null ? Kind.ITEM : Kind.BLOCK,
                            block));
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Item egg = SpawnEggItem.byId(type);
            subjects.add(
                    new Subject(
                            BuiltInRegistries.ENTITY_TYPE.getKey(type),
                            type.getDescriptionId(),
                            new ItemStack(egg == null ? Items.ARMOR_STAND : egg),
                            Kind.ENTITY,
                            null));
        }
        subjects.sort(Comparator.comparing(Subject::id).thenComparing(Subject::kind));
    }

    private void loadSounds() {
        if (soundsLoaded) return;
        soundsLoaded = true;
        for (ResourceIndex.PackInfo pack : snapshot.packs()) {
            var source = FilteredPackResources.raw(snapshot.packResources(pack.id()));
            if (source == null) continue;
            for (String ns : source.getNamespaces(PackType.CLIENT_RESOURCES)) {
                Map<String, JsonObject> declarations =
                        FilteredPackResources.readSoundEvents(source, ns);
                declarations.forEach(
                        (event, body) ->
                                sounds.computeIfAbsent(
                                                ns + ":" + event, key -> new LinkedHashMap<>())
                                        .put(pack.id(), body));
            }
        }
    }

    public List<Subject> subjects() {
        return subjects;
    }

    public boolean declares(String event, String pack) {
        return sounds.getOrDefault(event, Map.of()).containsKey(pack);
    }

    public ResourceLocation soundFile(String event, String pack) {
        return soundFile(event, pack, new HashSet<>());
    }

    private ResourceLocation soundFile(String event, String pack, Set<String> visited) {
        if (!visited.add(event)) return null;
        var declarations = sounds.getOrDefault(event, Map.of());
        JsonObject body = declarations.get(pack);
        if (body == null) body = declarations.get(soundDefinition(event, pack));
        if (body == null || !body.has("sounds") || !body.get("sounds").isJsonArray()) return null;
        for (JsonElement value : body.getAsJsonArray("sounds")) {
            String ref = value.isJsonPrimitive() ? value.getAsString() : string(value, "name");
            ResourceLocation id = parse(ref, ResourceLocation.parse(event).getNamespace());
            if (id == null) continue;
            if ("event".equals(string(value, "type"))) {
                ResourceLocation file = soundFile(id.toString(), pack, visited);
                if (file != null) return file;
            } else {
                ResourceLocation file = loc(id.getNamespace(), "sounds/" + id.getPath() + ".ogg");
                if (!snapshot.providersOf(file).isEmpty()) return file;
            }
        }
        return null;
    }

    public List<String> soundPacks(String event) {
        Set<String> providers = new HashSet<>(sounds.getOrDefault(event, Map.of()).keySet());
        Map<String, Asset> graph = new LinkedHashMap<>();
        sound(event, graph, new HashSet<>());
        for (Asset file : graph.values())
            if (file.location().getPath().endsWith(".ogg"))
                snapshot.providersOf(file.location()).forEach(n -> providers.add(n.packId()));
        return snapshot.packs().stream()
                .sorted(Comparator.comparingInt(ResourceIndex.PackInfo::priority))
                .map(ResourceIndex.PackInfo::id)
                .filter(providers::contains)
                .toList();
    }

    public String soundDefinition(String event, String chosen) {
        if (declares(event, chosen)) return chosen;
        return snapshot.packs().stream()
                .sorted(Comparator.comparingInt(ResourceIndex.PackInfo::priority))
                .map(ResourceIndex.PackInfo::id)
                .filter(
                        id ->
                                declares(event, id)
                                        && !snapshot.packResources(id).packId().startsWith("file/"))
                .findFirst()
                .orElse("");
    }

    public Map<String, Set<String>> audioFiles(String event, String chosen) {
        Map<String, Asset> found = new LinkedHashMap<>();
        sound(event, found, new HashSet<>());
        Map<String, Set<String>> files = new HashMap<>();
        for (Asset asset : found.values())
            if (asset.location().getPath().endsWith(".ogg")) {
                var providers = snapshot.providersOf(asset.location());
                String provider =
                        providers.stream()
                                .filter(n -> n.packId().equals(chosen))
                                .map(ResourceNode::packId)
                                .findFirst()
                                .orElseGet(
                                        () ->
                                                providers.stream()
                                                        .filter(
                                                                n ->
                                                                        !snapshot.packResources(
                                                                                        n.packId())
                                                                                .packId()
                                                                                .startsWith(
                                                                                        "file/"))
                                                        .reduce((a, b) -> b)
                                                        .map(ResourceNode::packId)
                                                        .orElse(""));
                files.put(
                        asset.location().toString(),
                        provider.isEmpty() ? Set.of() : Set.of(provider));
            }
        return files;
    }

    public List<Asset> assets(Subject subject) {
        return related.computeIfAbsent(subject.key(), key -> discover(subject));
    }

    private List<Asset> discover(Subject subject) {
        Map<String, Asset> result = new LinkedHashMap<>();
        Set<ResourceLocation> visited = new HashSet<>();
        String ns = subject.id().getNamespace(), id = subject.id().getPath();
        if (subject.kind() != Kind.ENTITY) {
            visit(loc(ns, "items/" + id + ".json"), result, visited);
            visit(loc(ns, "models/item/" + id + ".json"), result, visited);
            add(result, loc(ns, "textures/item/" + id + ".png"), "", "");
            if (subject.block() != null) {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(subject.block());
                visit(
                        loc(blockId.getNamespace(), "blockstates/" + blockId.getPath() + ".json"),
                        result,
                        visited);
                visit(
                        loc(blockId.getNamespace(), "models/block/" + blockId.getPath() + ".json"),
                        result,
                        visited);
                add(
                        result,
                        loc(blockId.getNamespace(), "textures/block/" + blockId.getPath() + ".png"),
                        "",
                        "");
            }
        }
        var equippable = subject.icon().get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        if (equippable != null) {
            equippable
                    .cameraOverlay()
                    .ifPresent(
                            overlay ->
                                    add(
                                            result,
                                            loc(
                                                    overlay.getNamespace(),
                                                    "textures/" + overlay.getPath() + ".png"),
                                            "",
                                            ""));
            equippable
                    .assetId()
                    .ifPresent(
                            key -> {
                                ResourceLocation definition =
                                        loc(
                                                key.location().getNamespace(),
                                                "equipment/" + key.location().getPath() + ".json");
                                add(result, definition, "", "");
                                for (JsonElement json : jsons(definition)) {
                                    if (!json.isJsonObject()) continue;
                                    JsonElement layerMap = json.getAsJsonObject().get("layers");
                                    if (layerMap == null || !layerMap.isJsonObject()) continue;
                                    for (var layerType : layerMap.getAsJsonObject().entrySet()) {
                                        if (!layerType.getValue().isJsonArray()) continue;
                                        for (JsonElement layer :
                                                layerType.getValue().getAsJsonArray()) {
                                            ResourceLocation texture =
                                                    ResourceLocation.tryParse(
                                                            string(layer, "texture"));
                                            if (texture != null)
                                                add(
                                                        result,
                                                        loc(
                                                                texture.getNamespace(),
                                                                "textures/entity/equipment/"
                                                                        + layerType.getKey()
                                                                        + "/"
                                                                        + texture.getPath()
                                                                        + ".png"),
                                                        "",
                                                        "");
                                        }
                                    }
                                }
                            });
        }
        String entityRoot =
                switch (id) {
                    case "mooshroom" -> "cow";
                    case "skeleton_horse", "zombie_horse", "donkey", "mule" -> "horse";
                    case "husk", "drowned", "giant" -> "zombie";
                    case "evoker", "vindicator", "pillager", "illusioner" -> "illager";
                    case "cave_spider" -> "spider";
                    case "glow_squid" -> "squid";
                    case "wither_skeleton", "stray", "bogged" -> "skeleton";
                    case "trader_llama" -> "llama";
                    case "snow_golem" -> "snowman";
                    case "magma_cube" -> "slime";
                    case "zombified_piglin" -> "piglin";
                    case "ender_dragon" -> "enderdragon";
                    case "cod", "salmon", "tropical_fish", "pufferfish" -> "fish";
                    default -> id;
                };
        for (ResourceLocation file : files.keySet()) {
            if (!file.getNamespace().equals(ns)) continue;
            String path = file.getPath();
            if (subject.kind() == Kind.ENTITY
                    && path.startsWith("textures/entity/")
                    && (path.startsWith("textures/entity/" + entityRoot + "/")
                            || path.startsWith("textures/entity/" + id + ".")
                            || path.startsWith("textures/entity/" + id + "_")
                            || path.endsWith("/" + id + ".png"))) add(result, file, "", "");
            if (subject.kind() != Kind.ENTITY
                    && path.startsWith("textures/entity/")
                    && (path.startsWith("textures/entity/" + id + "/")
                            || path.equals("textures/entity/" + id + ".png")))
                add(result, file, "", "");
            if (subject.block() != null
                    && id.contains("chest")
                    && path.startsWith("textures/entity/chest/")) add(result, file, "", "");
            if (subject.block() != null
                    && id.endsWith("_bed")
                    && path.equals(
                            "textures/entity/bed/" + id.substring(0, id.length() - 4) + ".png"))
                add(result, file, "", "");
            if (subject.icon().getItem() instanceof ArmorItem
                    && path.startsWith("textures/models/armor/")
                    && path.substring("textures/models/armor/".length())
                            .startsWith(
                                    id.substring(
                                                    0,
                                                    id.indexOf('_') < 0
                                                            ? id.length()
                                                            : id.indexOf('_'))
                                            + "_")) add(result, file, "", "");
        }
        String language =
                net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        ResourceLocation nameFile = loc(ns, "lang/" + language + ".json");
        put(result, new Asset(Group.NAME, nameFile, subject.translationKey(), ""));
        loadSounds();
        Set<String> eventIds = new LinkedHashSet<>();
        String prefix =
                (subject.kind() == Kind.ENTITY
                                ? "entity."
                                : subject.block() != null ? "block." : "item.")
                        + id
                        + ".";
        for (String event : sounds.keySet())
            if (event.startsWith(ns + ":" + prefix)) eventIds.add(event);
        // SoundType is the authoritative shared material sound, e.g. oak planks use wood.*.
        if (subject.block() != null) {
            var type = subject.block().defaultBlockState().getSoundType();
            for (SoundEvent event :
                    List.of(
                            type.getBreakSound(),
                            type.getStepSound(),
                            type.getPlaceSound(),
                            type.getHitSound(),
                            type.getFallSound())) eventIds.add(event.location().toString());
        }
        if (subject.icon().getItem() instanceof SwordItem) {
            sounds.keySet().stream()
                    .filter(key -> key.startsWith("minecraft:entity.player.attack."))
                    .forEach(eventIds::add);
        }
        if (subject.icon().getItem() instanceof ArmorItem) {
            String material = id.substring(0, id.indexOf('_') < 0 ? id.length() : id.indexOf('_'));
            String equip = "minecraft:item.armor.equip_" + material;
            if (sounds.containsKey(equip)) eventIds.add(equip);
        }
        if (equippable != null) eventIds.add(equippable.equipSound().value().location().toString());
        Set<String> soundVisited = new HashSet<>();
        for (String event : eventIds) sound(event, result, soundVisited);
        // A plain item must not acquire a block section through a shared parent model.
        if (subject.kind() == Kind.ITEM)
            result.values().removeIf(a -> a.group() == Group.BLOCK_TEXTURE);
        return result.values().stream()
                .sorted(Comparator.comparing(Asset::group).thenComparing(Asset::label))
                .toList();
    }

    private void sound(String event, Map<String, Asset> result, Set<String> visited) {
        if (!visited.add(event)) return;
        ResourceLocation eventId = ResourceLocation.tryParse(event);
        if (eventId == null) return;
        ResourceLocation definition = loc(eventId.getNamespace(), "sounds.json");
        put(result, new Asset(Group.SOUND, definition, "", event));
        for (JsonObject body : sounds.getOrDefault(event, Map.of()).values()) {
            JsonElement list = body.get("sounds");
            if (list == null || !list.isJsonArray()) continue;
            for (JsonElement value : list.getAsJsonArray()) {
                String name = value.isJsonPrimitive() ? value.getAsString() : string(value, "name");
                if (name.isEmpty()) continue;
                ResourceLocation target = parse(name, eventId.getNamespace());
                if (target == null) continue;
                if ("event".equals(string(value, "type")))
                    sound(target.toString(), result, visited);
                else
                    add(
                            result,
                            loc(target.getNamespace(), "sounds/" + target.getPath() + ".ogg"),
                            "",
                            event);
            }
        }
    }

    private void visit(
            ResourceLocation file, Map<String, Asset> result, Set<ResourceLocation> visited) {
        if (!files.containsKey(file) || !visited.add(file)) return;
        add(result, file, "", "");
        if (file.getPath().endsWith(".json"))
            for (JsonElement json : jsons(file)) walk(json, file.getNamespace(), result, visited);
    }

    private void walk(
            JsonElement json, String ns, Map<String, Asset> result, Set<ResourceLocation> visited) {
        if (json.isJsonArray()) {
            for (JsonElement child : json.getAsJsonArray()) walk(child, ns, result, visited);
        } else if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    String ref = value.getAsString();
                    if ((key.equals("parent") || key.equals("model")) && !ref.startsWith("#")) {
                        ResourceLocation model = parse(ref, ns);
                        if (model != null)
                            visit(
                                    loc(
                                            model.getNamespace(),
                                            "models/" + model.getPath() + ".json"),
                                    result,
                                    visited);
                    }
                }
                if (key.equals("texture")
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()) {
                    ResourceLocation texture = parse(value.getAsString(), ns);
                    if (texture != null)
                        visit(
                                loc(
                                        texture.getNamespace(),
                                        "textures/" + texture.getPath() + ".png"),
                                result,
                                visited);
                }
                if (key.equals("textures") && value.isJsonObject()) {
                    for (JsonElement texture : value.getAsJsonObject().asMap().values()) {
                        if (!texture.isJsonPrimitive()) continue;
                        String ref = texture.getAsString();
                        if (ref.startsWith("#")) continue;
                        ResourceLocation image = parse(ref, ns);
                        if (image != null)
                            visit(
                                    loc(
                                            image.getNamespace(),
                                            "textures/" + image.getPath() + ".png"),
                                    result,
                                    visited);
                    }
                } else walk(value, ns, result, visited);
            }
        }
    }

    public List<JsonElement> jsons(ResourceLocation file) {
        return jsonCache.computeIfAbsent(
                file,
                location -> {
                    List<JsonElement> values = new ArrayList<>();
                    for (ResourceNode provider : snapshot.providersOf(location)) {
                        try {
                            var supplier =
                                    FilteredPackResources.raw(
                                                    snapshot.packResources(provider.packId()))
                                            .getResource(PackType.CLIENT_RESOURCES, location);
                            if (supplier == null) continue;
                            try (var reader =
                                    new InputStreamReader(supplier.get(), StandardCharsets.UTF_8)) {
                                values.add(JsonParser.parseReader(reader));
                            }
                        } catch (Exception ignored) {
                            /* An invalid pack must not hide the other layers. */
                        }
                    }
                    return values;
                });
    }

    private void add(Map<String, Asset> result, ResourceLocation file, String text, String event) {
        if (!snapshot.providersOf(file).isEmpty())
            put(result, new Asset(group(file), file, text, event));
    }

    private static void put(Map<String, Asset> result, Asset asset) {
        result.putIfAbsent(asset.key(), asset);
    }

    private static Group group(ResourceLocation file) {
        String p = file.getPath();
        if (p.startsWith("textures/block/")) return Group.BLOCK_TEXTURE;
        if (p.startsWith("textures/item/")) return Group.ITEM_TEXTURE;
        if (p.startsWith("textures/entity/")) return Group.ENTITY_TEXTURE;
        if (p.startsWith("equipment/")
                || p.startsWith("models/")
                || p.startsWith("items/")
                || p.startsWith("blockstates/")) return Group.MODEL;
        if (p.startsWith("sounds/")) return Group.SOUND;
        return Group.OTHER;
    }

    private static String string(JsonElement e, String key) {
        if (!e.isJsonObject()) return "";
        JsonElement value = e.getAsJsonObject().get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static ResourceLocation loc(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    private static ResourceLocation parse(String value, String ns) {
        // Minecraft defaults unqualified references to minecraft, including inside mod namespaces.
        return ResourceLocation.tryParse(value);
    }
}
