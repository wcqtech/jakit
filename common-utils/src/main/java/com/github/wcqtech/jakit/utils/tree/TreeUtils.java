package com.github.wcqtech.jakit.utils.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Utility methods for building and navigating trees of {@link TreeNode}.
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * Builds a forest from flat business data.
     *
     * <p>Nodes whose parent id is {@code null} or not present in the input
     * become roots. The input order is preserved for roots and children.
     *
     * @param nodes the flat business data; must not be null; an empty
     *              collection yields an empty list
     * @param idExtractor extracts the unique id of a node; must not be null;
     *                    a null result is rejected
     * @param parentIdExtractor extracts the parent id of a node; must not be
     *                          null
     * @param <T> the business data type
     * @return the root nodes of the built forest
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if a node has a null or duplicate id,
     *                                  or if the data contains a cycle
     */
    public static <T> List<TreeNode<T>> buildTree(Collection<T> nodes,
                                                  Function<T, ?> idExtractor,
                                                  Function<T, ?> parentIdExtractor) {
        Objects.requireNonNull(nodes, "nodes must not be null");
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        Objects.requireNonNull(parentIdExtractor, "parentIdExtractor must not be null");
        return buildTreeInternal(nodes, idExtractor, parentIdExtractor, null);
    }

    /**
     * Builds a forest from flat business data and sorts every level.
     *
     * <p>Roots and each sibling group are sorted with the supplied comparator
     * after construction. {@code List.sort} is stable, so elements with equal
     * keys keep their input order.
     *
     * @param nodes the flat business data; must not be null; an empty
     *              collection yields an empty list
     * @param idExtractor extracts the unique id of a node; must not be null;
     *                    a null result is rejected
     * @param parentIdExtractor extracts the parent id of a node; must not be
     *                          null
     * @param comparator orders roots and sibling children; must not be null
     * @param <T> the business data type
     * @return the root nodes of the built forest
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if a node has a null or duplicate id,
     *                                  or if the data contains a cycle
     */
    public static <T> List<TreeNode<T>> buildTree(Collection<T> nodes,
                                                  Function<T, ?> idExtractor,
                                                  Function<T, ?> parentIdExtractor,
                                                  Comparator<T> comparator) {
        Objects.requireNonNull(nodes, "nodes must not be null");
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        Objects.requireNonNull(parentIdExtractor, "parentIdExtractor must not be null");
        Objects.requireNonNull(comparator, "comparator must not be null");
        return buildTreeInternal(nodes, idExtractor, parentIdExtractor, comparator);
    }

    private static <T> List<TreeNode<T>> buildTreeInternal(Collection<T> nodes,
                                                           Function<T, ?> idExtractor,
                                                           Function<T, ?> parentIdExtractor,
                                                           Comparator<T> comparator) {
        if (nodes.isEmpty()) {
            return List.of();
        }

        Map<Object, T> dataById = new LinkedHashMap<>();
        for (T node : nodes) {
            Object id = idExtractor.apply(node);
            if (id == null) {
                throw new IllegalArgumentException("id must not be null for node: " + node);
            }
            if (dataById.containsKey(id)) {
                throw new IllegalArgumentException("duplicate id: " + id);
            }
            dataById.put(id, node);
        }

        Map<Object, List<Object>> childIdsByParent = new LinkedHashMap<>();
        List<Object> rootIds = new ArrayList<>();
        for (Map.Entry<Object, T> entry : dataById.entrySet()) {
            Object id = entry.getKey();
            Object parentId = parentIdExtractor.apply(entry.getValue());
            if (parentId == null || !dataById.containsKey(parentId)) {
                rootIds.add(id);
            } else {
                childIdsByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(id);
            }
        }

        Map<Object, Integer> depthById = new HashMap<>();
        Deque<Object> queue = new ArrayDeque<>();
        for (Object rootId : rootIds) {
            depthById.put(rootId, 0);
            queue.add(rootId);
        }
        while (!queue.isEmpty()) {
            Object id = queue.remove();
            int childDepth = depthById.get(id) + 1;
            for (Object childId : childIdsByParent.getOrDefault(id, List.of())) {
                if (depthById.putIfAbsent(childId, childDepth) == null) {
                    queue.add(childId);
                }
            }
        }

        if (depthById.size() != dataById.size()) {
            List<Object> cyclicIds = new ArrayList<>();
            for (Object id : dataById.keySet()) {
                if (!depthById.containsKey(id)) {
                    cyclicIds.add(id);
                }
            }
            throw new IllegalArgumentException("cycle detected in nodes: " + cyclicIds);
        }

        List<Object> idsByDescendingDepth = new ArrayList<>(depthById.keySet());
        idsByDescendingDepth.sort(Comparator.comparingInt(depthById::get).reversed());
        Map<Object, TreeNode<T>> nodeById = new LinkedHashMap<>();
        for (Object id : idsByDescendingDepth) {
            List<Object> childIds = childIdsByParent.getOrDefault(id, List.of());
            List<TreeNode<T>> children = new ArrayList<>(childIds.size());
            for (Object childId : childIds) {
                children.add(nodeById.get(childId));
            }
            nodeById.put(id, new TreeNode<>(dataById.get(id), children, depthById.get(id)));
        }

        List<TreeNode<T>> roots = new ArrayList<>(rootIds.size());
        for (Object rootId : rootIds) {
            roots.add(nodeById.get(rootId));
        }
        if (comparator != null) {
            Comparator<TreeNode<T>> nodeComparator = comparingData(comparator);
            roots.sort(nodeComparator);
            for (TreeNode<T> root : roots) {
                sortTree(root, nodeComparator);
            }
        }
        return roots;
    }

    private static <T> Comparator<TreeNode<T>> comparingData(Comparator<T> comparator) {
        return (left, right) -> comparator.compare(left.getData(), right.getData());
    }

    private static <T> void sortTree(TreeNode<T> root, Comparator<TreeNode<T>> comparator) {
        Deque<TreeNode<T>> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            TreeNode<T> node = stack.pop();
            node.sortChildren(comparator);
            for (TreeNode<T> child : node.getChildren()) {
                stack.push(child);
            }
        }
    }
}
