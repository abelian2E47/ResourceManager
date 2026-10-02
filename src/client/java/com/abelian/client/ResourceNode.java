package com.abelian.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** One entry of the resource tree: a pack, a namespace, a folder, a file or a sound event. */
public final class ResourceNode {
    public enum Kind {
        PACK,
        NAMESPACE,
        DIRECTORY,
        FILE,
        /** A single event declared in a {@code sounds.json}; tuning targets it by {@code namespace:event}. */
        SOUND
    }

    private final Kind kind;
    private final String name;
    private final String packId;
    private final String namespace;
    private final String path;
    private final ResourceLocation location;
    private final List<ResourceNode> children = new ArrayList<>();
    private final List<SoundEvent> soundEvents = new ArrayList<>();
    private final int priority;

    private ResourceNode parent;
    private int fileCount;
    private int disabledCount;
    private boolean expanded;
    private boolean matches = true;

    private ResourceNode(Kind kind, String name, String packId, String namespace, String path, ResourceLocation location) {
        this(kind, name, packId, namespace, path, location, 0);
    }

    private ResourceNode(Kind kind, String name, String packId, String namespace, String path, ResourceLocation location, int priority) {
        this.kind = kind;
        this.name = name;
        this.packId = packId;
        this.namespace = namespace;
        this.path = path;
        this.location = location;
        this.priority = priority;
    }

    public static ResourceNode pack(String packId, int priority) {
        return new ResourceNode(Kind.PACK, packId, packId, null, null, null, priority);
    }

    public static ResourceNode namespace(String packId, String namespace) {
        return new ResourceNode(Kind.NAMESPACE, namespace, packId, namespace, null, null);
    }

    public static ResourceNode directory(String packId, String namespace, String path) {
        return new ResourceNode(Kind.DIRECTORY, path, packId, namespace, path, null);
    }

    public static ResourceNode file(String packId, String namespace, String path, ResourceLocation location) {
        ResourceNode node = new ResourceNode(Kind.FILE, path, packId, namespace, path, location);
        node.fileCount = 1;
        return node;
    }

    public static ResourceNode sound(String packId, String namespace, ResourceLocation soundsJson, String eventId) {
        return new ResourceNode(Kind.SOUND, eventId, packId, namespace, soundsJson.getPath(), soundsJson);
    }

    void addChild(ResourceNode child) {
        child.parent = this;
        children.add(child);
        fileCount += child.fileCount;
    }

    void addSoundEvent(String namespace, String id) {
        soundEvents.add(new SoundEvent(namespace, id));
    }

    public Kind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public String packId() {
        return packId;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    public ResourceLocation location() {
        return location;
    }

    /** Loading order rank, 1 = highest priority. Only meaningful for {@link Kind#PACK}. */
    public int priority() {
        return priority;
    }

    public ResourceNode parent() {
        return parent;
    }

    public List<ResourceNode> children() {
        return children;
    }

    /** Recursive number of resources below this node (1 for a file, 0 for a sound event). */
    public int fileCount() {
        return fileCount;
    }

    public List<SoundEvent> soundEvents() {
        return soundEvents;
    }

    /** Number of disabled files in this subtree; refreshed whenever the tree filter runs. */
    public int disabledCount() {
        return disabledCount;
    }

    void setDisabledCount(int disabledCount) {
        this.disabledCount = disabledCount;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean expanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    boolean matches() {
        return matches;
    }

    void setMatches(boolean matches) {
        this.matches = matches;
    }

    /** Full id of a file node, e.g. {@code minecraft:textures/gui/widgets.png}. */
    public String resourceId() {
        return location == null ? name : location.toString();
    }

    public boolean isFile() {
        return kind == Kind.FILE;
    }

    public boolean isSoundEvent() {
        return kind == Kind.SOUND;
    }

    /** A {@code sounds.json} file node carries the events declared by that pack. */
    public boolean isSoundDefinition() {
        return kind == Kind.FILE && "sounds.json".equals(path);
    }

    public String displayPath() {
        return switch (kind) {
            case NAMESPACE -> namespace + ":";
            case FILE -> namespace + ":" + path;
            case SOUND -> namespace + ":" + name;
            default -> name;
        };
    }

    /** {@code namespace:event} key used to store sound tuning. */
    public record SoundEvent(String namespace, String id) {
        public String key() {
            return namespace + ":" + id;
        }
    }
}
