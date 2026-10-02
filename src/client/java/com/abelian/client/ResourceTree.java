package com.abelian.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Flattens the pack tree into the list of rows the tree panel draws, applying the search query and the
 * quick filter. Branches stay visible while any descendant matches, and a branch that matches by its own
 * name opens up completely — the behaviour people expect from a file manager.
 */
public final class ResourceTree {
    public record Row(ResourceNode node, int depth) {
    }

    private final List<ResourceNode> packs;
    private final List<Row> rows = new ArrayList<>();
    private final Predicate<ResourceNode> disabled;
    private String query = "";
    private ResourceCategory category = ResourceCategory.ALL;
    private int matchedFiles;

    public ResourceTree(List<ResourceNode> packs, Predicate<ResourceNode> disabled) {
        this.packs = packs;
        this.disabled = disabled;
        refresh();
    }

    public List<Row> rows() {
        return rows;
    }

    public List<ResourceNode> packs() {
        return packs;
    }

    public String query() {
        return query;
    }

    public ResourceCategory category() {
        return category;
    }

    /** Number of files (ignoring folders) currently visible. */
    public int matchedFiles() {
        return matchedFiles;
    }

    public void setFilter(String query, ResourceCategory category) {
        String normalised = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalised.equals(this.query) && category == this.category) {
            return;
        }
        this.query = normalised;
        this.category = category;
        refresh();
    }

    public void refresh() {
        rows.clear();
        matchedFiles = 0;
        boolean searching = !query.isEmpty();
        for (ResourceNode pack : packs) {
            if (filter(pack, searching, false)) {
                rows.add(new Row(pack, 0));
                collect(pack, 1, searching);
            }
        }
    }

    /** Returns true when the node or one of its descendants is visible. */
    private boolean filter(ResourceNode node, boolean searching, boolean forced) {
        boolean forcedHere = forced || (searching && ResourceCategory.matchesQuery(node, query));
        boolean anyChild = false;
        int disabledHere = 0;
        for (ResourceNode child : node.children()) {
            anyChild |= filter(child, searching, forcedHere);
            disabledHere += child.disabledCount();
        }
        boolean selfDisabled = node.isFile() && this.disabled.test(node);
        node.setDisabledCount((selfDisabled ? 1 : 0) + disabledHere);
        boolean leafLike = node.isFile() || node.isSoundEvent();
        boolean selfVisible = leafLike && forcedHere
                || leafLike && category.matches(node) && ResourceCategory.matchesQuery(node, query);
        node.setMatches(selfVisible || anyChild);
        if (node.isFile() && node.matches()) {
            matchedFiles++;
        }
        return node.matches();
    }

    private void collect(ResourceNode node, int depth, boolean searching) {
        for (ResourceNode child : node.children()) {
            if (!child.matches()) {
                continue;
            }
            rows.add(new Row(child, depth));
            if (child.expanded() || (searching && child.matches())) {
                collect(child, depth + 1, searching);
            }
        }
    }

    public void setExpanded(ResourceNode node, boolean expanded) {
        node.setExpanded(expanded);
        refresh();
    }

    public void toggle(ResourceNode node) {
        if (node.hasChildren()) {
            setExpanded(node, !node.expanded());
        }
    }

    /** Expands every ancestor and refreshes so that {@code node} becomes a visible row. */
    public void reveal(ResourceNode node) {
        ResourceNode current = node.parent();
        while (current != null) {
            current.setExpanded(true);
            current = current.parent();
        }
        refresh();
    }

    public void expandAll(ResourceNode node) {
        node.setExpanded(true);
        for (ResourceNode child : node.children()) {
            expandAll(child);
        }
    }

    public void collapseAll(ResourceNode node) {
        node.setExpanded(false);
        for (ResourceNode child : node.children()) {
            collapseAll(child);
        }
    }

    public int rowIndexOf(ResourceNode node) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).node() == node) {
                return index;
            }
        }
        return -1;
    }
}
