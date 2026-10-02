package com.abelian.client;

import java.util.Locale;

/** Quick filters shown in the sidebar / toolbar. */
public enum ResourceCategory {
    ALL,
    TEXTURES,
    SOUNDS,
    UI,
    MODELS,
    TEXT,
    OTHER;

    public String translationKey() {
        return "resourcemanager.ui.filter." + name().toLowerCase(Locale.ROOT);
    }

    public ResourceCategory next() {
        ResourceCategory[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean matches(ResourceNode node) {
        if (this == ALL) {
            return true;
        }
        if (node.isSoundEvent()) {
            return this == SOUNDS;
        }
        String path = node.path() == null ? "" : node.path();
        if (node.isSoundDefinition()) {
            return this == SOUNDS;
        }
        return switch (this) {
            case TEXTURES -> path.startsWith("textures/");
            case UI -> path.startsWith("textures/gui/") || path.contains("/gui/") || path.startsWith("gui/");
            case SOUNDS -> path.startsWith("sounds/");
            case MODELS -> path.startsWith("models/") || path.startsWith("blockstates/") || path.startsWith("atlases/");
            case TEXT -> path.startsWith("lang/") || path.startsWith("font/") || path.startsWith("texts/");
            case OTHER -> !path.startsWith("textures/") && !path.startsWith("sounds/") && !path.startsWith("models/")
                    && !path.startsWith("blockstates/") && !path.startsWith("atlases/") && !path.startsWith("lang/")
                    && !path.startsWith("font/") && !path.startsWith("texts/");
            default -> true;
        };
    }

    /** True when a leaf matches the query. Pack and namespace rows are matched through their children. */
    public static boolean matchesQuery(ResourceNode node, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (node.kind() == ResourceNode.Kind.PACK) {
            return node.packId().toLowerCase(Locale.ROOT).contains(query);
        }
        String path = node.path() == null ? "" : node.path();
        return node.name().toLowerCase(Locale.ROOT).contains(query)
                || path.toLowerCase(Locale.ROOT).contains(query)
                || (node.namespace() != null && node.namespace().toLowerCase(Locale.ROOT).contains(query))
                || node.displayPath().toLowerCase(Locale.ROOT).contains(query);
    }

}
