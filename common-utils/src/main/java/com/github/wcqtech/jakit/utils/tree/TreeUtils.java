package com.github.wcqtech.jakit.utils.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    /**
     * Sorts the given roots and every level of children with the supplied
     * comparator.
     *
     * <p>The sort is stable, so nodes with equal keys keep their current
     * relative order. Only child order changes; the tree structure itself is
     * not modified.
     *
     * @param roots the root nodes whose subtrees should be sorted; must not be
     *              null
     * @param comparator orders roots and sibling children by business data;
     *                   must not be null
     * @param <T> the business data type
     * @throws NullPointerException if any argument is null
     */
    public static <T> void sort(List<TreeNode<T>> roots, Comparator<T> comparator) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(comparator, "comparator must not be null");
        Comparator<TreeNode<T>> nodeComparator = comparingData(comparator);
        roots.sort(nodeComparator);
        for (TreeNode<T> root : roots) {
            sortTree(root, nodeComparator);
        }
    }

    /**
     * Sorts every level of children of the given root with the supplied
     * comparator.
     *
     * <p>The root itself has no siblings, so only its children levels are
     * reordered. The sort is stable, so nodes with equal keys keep their
     * current relative order.
     *
     * @param root the root node whose children levels should be sorted; must
     *             not be null
     * @param comparator orders sibling children by business data; must not be
     *                   null
     * @param <T> the business data type
     * @throws NullPointerException if any argument is null
     */
    public static <T> void sort(TreeNode<T> root, Comparator<T> comparator) {
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(comparator, "comparator must not be null");
        sortTree(root, comparingData(comparator));
    }

    /**
     * Traverses one tree in preorder (node before children).
     *
     * @param root the root node; must not be null
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code root} is null
     */
    public static <T> List<TreeNode<T>> preorder(TreeNode<T> root) {
        return preorder(List.of(Objects.requireNonNull(root, "root must not be null")), Integer.MAX_VALUE);
    }

    /**
     * Traverses a forest in preorder (node before children).
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code roots} is null
     */
    public static <T> List<TreeNode<T>> preorder(Collection<TreeNode<T>> roots) {
        return preorder(roots, Integer.MAX_VALUE);
    }

    /**
     * Traverses one tree in preorder up to the given relative depth.
     *
     * @param root the root node; must not be null
     * @param maxDepth the maximum relative depth to visit; {@code 0} visits
     *                 only the root; must not be negative
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code root} is null
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    public static <T> List<TreeNode<T>> preorder(TreeNode<T> root, int maxDepth) {
        return preorder(List.of(Objects.requireNonNull(root, "root must not be null")), maxDepth);
    }

    /**
     * Traverses a forest in preorder up to the given relative depth.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param maxDepth the maximum relative depth to visit; {@code 0} visits
     *                 only the roots; must not be negative
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code roots} is null
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    public static <T> List<TreeNode<T>> preorder(Collection<TreeNode<T>> roots, int maxDepth) {
        Objects.requireNonNull(roots, "roots must not be null");
        requireNonNegative(maxDepth);
        List<TreeNode<T>> result = new ArrayList<>();
        Deque<TreeNode<T>> stack = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        pushReversed(stack, depths, roots, 0);
        while (!stack.isEmpty()) {
            TreeNode<T> node = stack.pop();
            int depth = depths.pop();
            result.add(node);
            if (depth < maxDepth) {
                pushReversed(stack, depths, node.getChildren(), depth + 1);
            }
        }
        return result;
    }

    /**
     * Traverses one tree in postorder (children before node).
     *
     * @param root the root node; must not be null
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code root} is null
     */
    public static <T> List<TreeNode<T>> postorder(TreeNode<T> root) {
        return postorder(List.of(Objects.requireNonNull(root, "root must not be null")), Integer.MAX_VALUE);
    }

    /**
     * Traverses a forest in postorder (children before node).
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code roots} is null
     */
    public static <T> List<TreeNode<T>> postorder(Collection<TreeNode<T>> roots) {
        return postorder(roots, Integer.MAX_VALUE);
    }

    /**
     * Traverses one tree in postorder up to the given relative depth.
     *
     * @param root the root node; must not be null
     * @param maxDepth the maximum relative depth to visit; {@code 0} visits
     *                 only the root; must not be negative
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code root} is null
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    public static <T> List<TreeNode<T>> postorder(TreeNode<T> root, int maxDepth) {
        return postorder(List.of(Objects.requireNonNull(root, "root must not be null")), maxDepth);
    }

    /**
     * Traverses a forest in postorder up to the given relative depth.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param maxDepth the maximum relative depth to visit; {@code 0} visits
     *                 only the roots; must not be negative
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code roots} is null
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    public static <T> List<TreeNode<T>> postorder(Collection<TreeNode<T>> roots, int maxDepth) {
        Objects.requireNonNull(roots, "roots must not be null");
        requireNonNegative(maxDepth);
        List<TreeNode<T>> result = new ArrayList<>();
        Deque<TreeNode<T>> stack = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        Deque<Boolean> expanded = new ArrayDeque<>();
        pushReversed(stack, depths, roots, 0);
        for (int i = 0; i < roots.size(); i++) {
            expanded.push(Boolean.FALSE);
        }
        while (!stack.isEmpty()) {
            TreeNode<T> node = stack.pop();
            int depth = depths.pop();
            if (expanded.pop()) {
                result.add(node);
                continue;
            }
            if (depth >= maxDepth) {
                result.add(node);
                continue;
            }
            stack.push(node);
            depths.push(depth);
            expanded.push(Boolean.TRUE);
            List<TreeNode<T>> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
                depths.push(depth + 1);
                expanded.push(Boolean.FALSE);
            }
        }
        return result;
    }

    /**
     * Traverses one tree breadth-first.
     *
     * @param root the root node; must not be null
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code root} is null
     */
    public static <T> List<TreeNode<T>> bfs(TreeNode<T> root) {
        return bfs(List.of(Objects.requireNonNull(root, "root must not be null")), Integer.MAX_VALUE);
    }

    /**
     * Traverses a forest breadth-first.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code roots} is null
     */
    public static <T> List<TreeNode<T>> bfs(Collection<TreeNode<T>> roots) {
        return bfs(roots, Integer.MAX_VALUE);
    }

    /**
     * Traverses one tree breadth-first up to the given relative depth.
     *
     * @param root the root node; must not be null
     * @param maxDepth the maximum relative depth to visit; {@code 0} visits
     *                 only the root; must not be negative
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code root} is null
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    public static <T> List<TreeNode<T>> bfs(TreeNode<T> root, int maxDepth) {
        return bfs(List.of(Objects.requireNonNull(root, "root must not be null")), maxDepth);
    }

    /**
     * Traverses a forest breadth-first up to the given relative depth.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param maxDepth the maximum relative depth to visit; {@code 0} visits
     *                 only the roots; must not be negative
     * @param <T> the business data type
     * @return the visited nodes
     * @throws NullPointerException if {@code roots} is null
     * @throws IllegalArgumentException if {@code maxDepth} is negative
     */
    public static <T> List<TreeNode<T>> bfs(Collection<TreeNode<T>> roots, int maxDepth) {
        Objects.requireNonNull(roots, "roots must not be null");
        requireNonNegative(maxDepth);
        List<TreeNode<T>> result = new ArrayList<>();
        Deque<TreeNode<T>> queue = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        for (TreeNode<T> root : roots) {
            queue.add(root);
            depths.add(0);
        }
        while (!queue.isEmpty()) {
            TreeNode<T> node = queue.remove();
            int depth = depths.remove();
            result.add(node);
            if (depth < maxDepth) {
                for (TreeNode<T> child : node.getChildren()) {
                    queue.add(child);
                    depths.add(depth + 1);
                }
            }
        }
        return result;
    }

    /**
     * Returns the business data of one tree visited in preorder.
     *
     * @param root the root node; must not be null
     * @param <T> the business data type
     * @return the visited data
     * @throws NullPointerException if {@code root} is null
     */
    public static <T> List<T> preorderData(TreeNode<T> root) {
        return mapData(preorder(root));
    }

    /**
     * Returns the business data of a forest visited in preorder.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param <T> the business data type
     * @return the visited data
     * @throws NullPointerException if {@code roots} is null
     */
    public static <T> List<T> preorderData(Collection<TreeNode<T>> roots) {
        return mapData(preorder(roots));
    }

    /**
     * Returns the business data of one tree visited in postorder.
     *
     * @param root the root node; must not be null
     * @param <T> the business data type
     * @return the visited data
     * @throws NullPointerException if {@code root} is null
     */
    public static <T> List<T> postorderData(TreeNode<T> root) {
        return mapData(postorder(root));
    }

    /**
     * Returns the business data of a forest visited in postorder.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param <T> the business data type
     * @return the visited data
     * @throws NullPointerException if {@code roots} is null
     */
    public static <T> List<T> postorderData(Collection<TreeNode<T>> roots) {
        return mapData(postorder(roots));
    }

    /**
     * Returns the business data of one tree visited breadth-first.
     *
     * @param root the root node; must not be null
     * @param <T> the business data type
     * @return the visited data
     * @throws NullPointerException if {@code root} is null
     */
    public static <T> List<T> bfsData(TreeNode<T> root) {
        return mapData(bfs(root));
    }

    /**
     * Returns the business data of a forest visited breadth-first.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param <T> the business data type
     * @return the visited data
     * @throws NullPointerException if {@code roots} is null
     */
    public static <T> List<T> bfsData(Collection<TreeNode<T>> roots) {
        return mapData(bfs(roots));
    }

    /**
     * Returns the nodes located exactly at the given depth relative to the
     * passed root.
     *
     * <p>The returned nodes keep the order of the input and of the children
     * lists, level by level from left to right.
     *
     * @param root the root node; must not be null
     * @param depth the depth to look up; {@code 0} returns the root itself;
     *              must not be negative
     * @param <T> the business data type
     * @return the nodes at the given depth; empty if the tree is shallower
     * @throws NullPointerException if {@code root} is null
     * @throws IllegalArgumentException if {@code depth} is negative
     */
    public static <T> List<TreeNode<T>> findAtDepth(TreeNode<T> root, int depth) {
        return findAtDepth(List.of(Objects.requireNonNull(root, "root must not be null")), depth);
    }

    /**
     * Returns the nodes located exactly at the given depth relative to the
     * passed roots.
     *
     * <p>The returned nodes keep the order of the input and of the children
     * lists, level by level from left to right.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty list
     * @param depth the depth to look up; {@code 0} returns the roots
     *              themselves; must not be negative
     * @param <T> the business data type
     * @return the nodes at the given depth; empty if the trees are shallower
     * @throws NullPointerException if {@code roots} is null
     * @throws IllegalArgumentException if {@code depth} is negative
     */
    public static <T> List<TreeNode<T>> findAtDepth(Collection<TreeNode<T>> roots, int depth) {
        Objects.requireNonNull(roots, "roots must not be null");
        requireNonNegative(depth);
        List<TreeNode<T>> result = new ArrayList<>();
        Deque<TreeNode<T>> queue = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        for (TreeNode<T> root : roots) {
            queue.add(root);
            depths.add(0);
        }
        while (!queue.isEmpty()) {
            TreeNode<T> node = queue.remove();
            int nodeDepth = depths.remove();
            if (nodeDepth == depth) {
                result.add(node);
            } else if (nodeDepth < depth) {
                for (TreeNode<T> child : node.getChildren()) {
                    queue.add(child);
                    depths.add(nodeDepth + 1);
                }
            }
        }
        return result;
    }

    /**
     * Finds the first node whose extracted id equals the given id, searching
     * the tree in preorder.
     *
     * @param root the root node; must not be null
     * @param idExtractor extracts the id of a node's data; must not be null
     * @param id the id to look for; must not be null
     * @param <T> the business data type
     * @param <ID> the id type
     * @return the matching node, or {@code Optional.empty()} if no node
     *         matches
     * @throws NullPointerException if {@code root} or {@code idExtractor} is
     *                              null
     * @throws IllegalArgumentException if {@code id} is null
     */
    public static <T, ID> Optional<TreeNode<T>> findById(TreeNode<T> root,
                                                         Function<T, ID> idExtractor,
                                                         ID id) {
        return findById(List.of(Objects.requireNonNull(root, "root must not be null")), idExtractor, id);
    }

    /**
     * Finds the first node whose extracted id equals the given id, searching
     * the forest in preorder.
     *
     * <p>Nodes whose extracted id is null never match, since built trees
     * cannot contain null ids.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param idExtractor extracts the id of a node's data; must not be null
     * @param id the id to look for; must not be null
     * @param <T> the business data type
     * @param <ID> the id type
     * @return the matching node, or {@code Optional.empty()} if no node
     *         matches
     * @throws NullPointerException if {@code roots} or {@code idExtractor} is
     *                              null
     * @throws IllegalArgumentException if {@code id} is null
     */
    public static <T, ID> Optional<TreeNode<T>> findById(Collection<TreeNode<T>> roots,
                                                         Function<T, ID> idExtractor,
                                                         ID id) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        for (TreeNode<T> node : preorder(roots)) {
            ID nodeId = idExtractor.apply(node.getData());
            if (nodeId != null && nodeId.equals(id)) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    /**
     * Indexes one tree by an arbitrary business key, requiring unique keys.
     *
     * @param root the root node; must not be null
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the unique key index, keys in first-appearance order
     * @throws NullPointerException if {@code root} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null or duplicated
     */
    public static <T, K> Map<K, TreeNode<T>> uniqueIndex(TreeNode<T> root,
                                                         Function<T, K> keyExtractor) {
        return uniqueIndex(List.of(Objects.requireNonNull(root, "root must not be null")), keyExtractor);
    }

    /**
     * Indexes a forest by an arbitrary business key, requiring unique keys.
     *
     * <p>The map is a {@link LinkedHashMap}: keys keep their first-appearance
     * (preorder) order. Keys must be non-null and unique.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty map
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the unique key index
     * @throws NullPointerException if {@code roots} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null or duplicated
     */
    public static <T, K> Map<K, TreeNode<T>> uniqueIndex(Collection<TreeNode<T>> roots,
                                                         Function<T, K> keyExtractor) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        Map<K, TreeNode<T>> index = new LinkedHashMap<>();
        for (TreeNode<T> node : preorder(roots)) {
            K key = requireNonNullKey(keyExtractor.apply(node.getData()), node.getData());
            if (index.containsKey(key)) {
                throw new IllegalArgumentException("duplicate key: " + key);
            }
            index.put(key, node);
        }
        return index;
    }

    /**
     * Indexes one tree by an arbitrary business key, requiring unique keys;
     * values are the business data.
     *
     * @param root the root node; must not be null
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the unique key index, keys in first-appearance order
     * @throws NullPointerException if {@code root} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null or duplicated
     */
    public static <T, K> Map<K, T> uniqueIndexData(TreeNode<T> root,
                                                   Function<T, K> keyExtractor) {
        return uniqueIndexData(List.of(Objects.requireNonNull(root, "root must not be null")), keyExtractor);
    }

    /**
     * Indexes a forest by an arbitrary business key, requiring unique keys;
     * values are the business data.
     *
     * <p>The map is a {@link LinkedHashMap}: keys keep their first-appearance
     * (preorder) order. Keys must be non-null and unique.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty map
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the unique key index
     * @throws NullPointerException if {@code roots} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null or duplicated
     */
    public static <T, K> Map<K, T> uniqueIndexData(Collection<TreeNode<T>> roots,
                                                   Function<T, K> keyExtractor) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        Map<K, T> index = new LinkedHashMap<>();
        for (TreeNode<T> node : preorder(roots)) {
            K key = requireNonNullKey(keyExtractor.apply(node.getData()), node.getData());
            if (index.containsKey(key)) {
                throw new IllegalArgumentException("duplicate key: " + key);
            }
            index.put(key, node.getData());
        }
        return index;
    }

    /**
     * Indexes one tree by an arbitrary business key; each value list keeps
     * preorder order.
     *
     * @param root the root node; must not be null
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index
     * @throws NullPointerException if {@code root} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<TreeNode<T>>> index(TreeNode<T> root,
                                                         Function<T, K> keyExtractor) {
        return index(List.of(Objects.requireNonNull(root, "root must not be null")), keyExtractor);
    }

    /**
     * Indexes a forest by an arbitrary business key; each value list keeps
     * preorder order.
     *
     * <p>The map is a {@link LinkedHashMap}: keys keep their first-appearance
     * (preorder) order.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty map
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index
     * @throws NullPointerException if {@code roots} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<TreeNode<T>>> index(Collection<TreeNode<T>> roots,
                                                         Function<T, K> keyExtractor) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        Map<K, List<TreeNode<T>>> index = new LinkedHashMap<>();
        for (TreeNode<T> node : preorder(roots)) {
            K key = requireNonNullKey(keyExtractor.apply(node.getData()), node.getData());
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(node);
        }
        return index;
    }

    /**
     * Indexes one tree by an arbitrary business key, sorting each group by
     * the business data.
     *
     * @param root the root node; must not be null
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param comparator orders each group's nodes by business data; must not
     *                   be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index, each group sorted
     * @throws NullPointerException if {@code root}, {@code keyExtractor} or
     *                              {@code comparator} is null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<TreeNode<T>>> index(TreeNode<T> root,
                                                         Function<T, K> keyExtractor,
                                                         Comparator<T> comparator) {
        return index(List.of(Objects.requireNonNull(root, "root must not be null")), keyExtractor, comparator);
    }

    /**
     * Indexes a forest by an arbitrary business key, sorting each group by
     * the business data.
     *
     * <p>The map is a {@link LinkedHashMap}: keys keep their first-appearance
     * (preorder) order. Grouping happens first, then each group is sorted with
     * the comparator; the sort is stable.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty map
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param comparator orders each group's nodes by business data; must not
     *                   be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index, each group sorted
     * @throws NullPointerException if {@code roots}, {@code keyExtractor} or
     *                              {@code comparator} is null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<TreeNode<T>>> index(Collection<TreeNode<T>> roots,
                                                         Function<T, K> keyExtractor,
                                                         Comparator<T> comparator) {
        Objects.requireNonNull(comparator, "comparator must not be null");
        Map<K, List<TreeNode<T>>> index = index(roots, keyExtractor);
        Comparator<TreeNode<T>> nodeComparator = comparingData(comparator);
        for (List<TreeNode<T>> group : index.values()) {
            group.sort(nodeComparator);
        }
        return index;
    }

    /**
     * Indexes one tree by an arbitrary business key; values are the business
     * data, each list keeping preorder order.
     *
     * @param root the root node; must not be null
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index
     * @throws NullPointerException if {@code root} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<T>> indexData(TreeNode<T> root,
                                                   Function<T, K> keyExtractor) {
        return indexData(List.of(Objects.requireNonNull(root, "root must not be null")), keyExtractor);
    }

    /**
     * Indexes a forest by an arbitrary business key; values are the business
     * data, each list keeping preorder order.
     *
     * <p>The map is a {@link LinkedHashMap}: keys keep their first-appearance
     * (preorder) order.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty map
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index
     * @throws NullPointerException if {@code roots} or {@code keyExtractor} is
     *                              null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<T>> indexData(Collection<TreeNode<T>> roots,
                                                   Function<T, K> keyExtractor) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        Map<K, List<T>> index = new LinkedHashMap<>();
        for (TreeNode<T> node : preorder(roots)) {
            K key = requireNonNullKey(keyExtractor.apply(node.getData()), node.getData());
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(node.getData());
        }
        return index;
    }

    /**
     * Indexes one tree by an arbitrary business key, sorting each group by
     * the business data; values are the business data.
     *
     * @param root the root node; must not be null
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param comparator orders each group's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index, each group sorted
     * @throws NullPointerException if {@code root}, {@code keyExtractor} or
     *                              {@code comparator} is null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<T>> indexData(TreeNode<T> root,
                                                   Function<T, K> keyExtractor,
                                                   Comparator<T> comparator) {
        return indexData(List.of(Objects.requireNonNull(root, "root must not be null")), keyExtractor, comparator);
    }

    /**
     * Indexes a forest by an arbitrary business key, sorting each group by
     * the business data; values are the business data.
     *
     * <p>The map is a {@link LinkedHashMap}: keys keep their first-appearance
     * (preorder) order. Grouping happens first, then each group is sorted with
     * the comparator; the sort is stable.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields an empty map
     * @param keyExtractor extracts the key of a node's data; must not be null
     * @param comparator orders each group's data; must not be null
     * @param <T> the business data type
     * @param <K> the key type
     * @return the key index, each group sorted
     * @throws NullPointerException if {@code roots}, {@code keyExtractor} or
     *                              {@code comparator} is null
     * @throws IllegalArgumentException if a key is null
     */
    public static <T, K> Map<K, List<T>> indexData(Collection<TreeNode<T>> roots,
                                                   Function<T, K> keyExtractor,
                                                   Comparator<T> comparator) {
        Objects.requireNonNull(comparator, "comparator must not be null");
        Map<K, List<T>> index = indexData(roots, keyExtractor);
        for (List<T> group : index.values()) {
            group.sort(comparator);
        }
        return index;
    }

    /**
     * Returns all descendants of the given node in preorder, excluding the
     * node itself.
     *
     * @param node the node whose descendants should be returned; must not be
     *             null
     * @param <T> the business data type
     * @return the descendants in preorder; empty for a leaf
     * @throws NullPointerException if {@code node} is null
     */
    public static <T> List<TreeNode<T>> descendants(TreeNode<T> node) {
        Objects.requireNonNull(node, "node must not be null");
        List<TreeNode<T>> pre = preorder(node);
        return new ArrayList<>(pre.subList(1, pre.size()));
    }

    /**
     * Returns the business data of all descendants of the given node in
     * preorder, excluding the node itself.
     *
     * @param node the node whose descendants should be returned; must not be
     *             null
     * @param <T> the business data type
     * @return the descendants' data in preorder; empty for a leaf
     * @throws NullPointerException if {@code node} is null
     */
    public static <T> List<T> descendantsData(TreeNode<T> node) {
        return mapData(descendants(node));
    }

    /**
     * Finds the path from a root to the first node whose extracted id equals
     * the given id, searching in preorder.
     *
     * <p>The path is root-first and includes both the root and the target
     * node. Nodes do not hold parent references, so the search always starts
     * from the passed roots.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param idExtractor extracts the id of a node's data; must not be null
     * @param id the id to look for; must not be null
     * @param <T> the business data type
     * @param <ID> the id type
     * @return the path from root to target, or {@code Optional.empty()} if no
     *         node matches
     * @throws NullPointerException if {@code roots} or {@code idExtractor} is
     *                              null
     * @throws IllegalArgumentException if {@code id} is null
     */
    public static <T, ID> Optional<List<TreeNode<T>>> findPath(Collection<TreeNode<T>> roots,
                                                               Function<T, ID> idExtractor,
                                                               ID id) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(idExtractor, "idExtractor must not be null");
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        Map<TreeNode<T>, TreeNode<T>> parent = new IdentityHashMap<>();
        Deque<TreeNode<T>> stack = new ArrayDeque<>();
        for (TreeNode<T> root : roots) {
            parent.put(root, null);
            stack.push(root);
        }
        while (!stack.isEmpty()) {
            TreeNode<T> node = stack.pop();
            ID nodeId = idExtractor.apply(node.getData());
            if (nodeId != null && nodeId.equals(id)) {
                return Optional.of(reconstructPath(parent, node));
            }
            List<TreeNode<T>> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                TreeNode<T> child = children.get(i);
                parent.put(child, node);
                stack.push(child);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the path from the given root to the first node whose extracted
     * id equals the given id, searching the subtree of the root in preorder.
     *
     * <p>Equivalent to {@link #findPath(Collection, Function, Object)} with a
     * single-element forest.
     *
     * @param root the root node whose subtree should be searched; must not be
     *             null
     * @param idExtractor extracts the id of a node's data; must not be null
     * @param id the id to look for; must not be null
     * @param <T> the business data type
     * @param <ID> the id type
     * @return the path from root to target, or {@code Optional.empty()} if no
     *         node matches
     * @throws NullPointerException if {@code root} or {@code idExtractor} is
     *                              null
     * @throws IllegalArgumentException if {@code id} is null
     */
    public static <T, ID> Optional<List<TreeNode<T>>> findPath(TreeNode<T> root,
                                                               Function<T, ID> idExtractor,
                                                               ID id) {
        return findPath(List.of(Objects.requireNonNull(root, "root must not be null")), idExtractor, id);
    }

    /**
     * Finds the business data path from a root to the first node whose
     * extracted id equals the given id, searching in preorder.
     *
     * <p>The path is root-first and includes both the root and the target
     * node.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param idExtractor extracts the id of a node's data; must not be null
     * @param id the id to look for; must not be null
     * @param <T> the business data type
     * @param <ID> the id type
     * @return the path data from root to target, or {@code Optional.empty()}
     *         if no node matches
     * @throws NullPointerException if {@code roots} or {@code idExtractor} is
     *                              null
     * @throws IllegalArgumentException if {@code id} is null
     */
    public static <T, ID> Optional<List<T>> findPathData(Collection<TreeNode<T>> roots,
                                                         Function<T, ID> idExtractor,
                                                         ID id) {
        return findPath(roots, idExtractor, id).map(TreeUtils::mapData);
    }

    /**
     * Finds the business data path from the given root to the first node
     * whose extracted id equals the given id, searching the subtree of the
     * root in preorder.
     *
     * <p>Equivalent to {@link #findPathData(Collection, Function, Object)}
     * with a single-element forest.
     *
     * @param root the root node whose subtree should be searched; must not be
     *             null
     * @param idExtractor extracts the id of a node's data; must not be null
     * @param id the id to look for; must not be null
     * @param <T> the business data type
     * @param <ID> the id type
     * @return the path data from root to target, or {@code Optional.empty()}
     *         if no node matches
     * @throws NullPointerException if {@code root} or {@code idExtractor} is
     *                              null
     * @throws IllegalArgumentException if {@code id} is null
     */
    public static <T, ID> Optional<List<T>> findPathData(TreeNode<T> root,
                                                         Function<T, ID> idExtractor,
                                                         ID id) {
        return findPathData(List.of(Objects.requireNonNull(root, "root must not be null")), idExtractor, id);
    }

    /**
     * Finds the path from the given node up to its root, searching the forest
     * for the node instance.
     *
     * <p>Equivalent to {@link #pathToRoot(Collection, TreeNode, boolean)} with
     * {@code reverse = false}.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param node the node whose path to its root should be returned; must
     *             not be null
     * @param <T> the business data type
     * @return the path from {@code node} to its root, node first, root last,
     *         both included; {@code Optional.empty()} if {@code node} is not
     *         part of the forest
     * @throws NullPointerException if {@code roots} or {@code node} is null
     */
    public static <T> Optional<List<TreeNode<T>>> pathToRoot(Collection<TreeNode<T>> roots,
                                                             TreeNode<T> node) {
        return pathToRoot(roots, node, false);
    }

    /**
     * Finds the path between the given node and its root, searching the
     * forest for the node instance.
     *
     * <p>By default the path is node first and root last; with
     * {@code reverse = true} it is root first and node last. Both ends are
     * always included. Nodes do not hold parent references, so the search
     * always starts from the passed roots and matches the node by identity.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param node the node whose path to its root should be returned; must
     *             not be null
     * @param reverse {@code true} returns the path root first and node last;
     *                {@code false} (the default) returns it node first and
     *                root last
     * @param <T> the business data type
     * @return the path between {@code node} and its root in the requested
     *         order, or {@code Optional.empty()} if {@code node} is not part
     *         of the forest
     * @throws NullPointerException if {@code roots} or {@code node} is null
     */
    public static <T> Optional<List<TreeNode<T>>> pathToRoot(Collection<TreeNode<T>> roots,
                                                             TreeNode<T> node,
                                                             boolean reverse) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(node, "node must not be null");
        Map<TreeNode<T>, TreeNode<T>> parent = new IdentityHashMap<>();
        Deque<TreeNode<T>> stack = new ArrayDeque<>();
        for (TreeNode<T> root : roots) {
            parent.put(root, null);
            stack.push(root);
        }
        while (!stack.isEmpty()) {
            TreeNode<T> current = stack.pop();
            if (current == node) {
                List<TreeNode<T>> path = new ArrayList<>();
                TreeNode<T> cursor = current;
                while (cursor != null) {
                    path.add(cursor);
                    cursor = parent.get(cursor);
                }
                if (reverse) {
                    Collections.reverse(path);
                }
                return Optional.of(path);
            }
            List<TreeNode<T>> children = current.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                TreeNode<T> child = children.get(i);
                parent.put(child, current);
                stack.push(child);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the path from the given node up to the passed root, searching
     * the subtree of the root for the node instance.
     *
     * <p>Equivalent to {@link #pathToRoot(TreeNode, TreeNode, boolean)} with
     * {@code reverse = false}.
     *
     * @param root the root of the tree to search; must not be null
     * @param node the node whose path to the root should be returned; must
     *             not be null
     * @param <T> the business data type
     * @return the path from {@code node} up to {@code root}, node first, root
     *         last, both included; {@code Optional.empty()} if {@code node}
     *         is not part of the subtree of {@code root}
     * @throws NullPointerException if {@code root} or {@code node} is null
     */
    public static <T> Optional<List<TreeNode<T>>> pathToRoot(TreeNode<T> root,
                                                             TreeNode<T> node) {
        return pathToRoot(List.of(Objects.requireNonNull(root, "root must not be null")), node);
    }

    /**
     * Finds the path between the given node and the passed root, searching
     * the subtree of the root for the node instance.
     *
     * <p>By default the path is node first and root last; with
     * {@code reverse = true} it is root first and node last. Both ends are
     * always included.
     *
     * @param root the root of the tree to search; must not be null
     * @param node the node whose path to the root should be returned; must
     *             not be null
     * @param reverse {@code true} returns the path root first and node last;
     *                {@code false} (the default) returns it node first and
     *                root last
     * @param <T> the business data type
     * @return the path between {@code node} and {@code root} in the requested
     *         order, or {@code Optional.empty()} if {@code node} is not part
     *         of the subtree of {@code root}
     * @throws NullPointerException if {@code root} or {@code node} is null
     */
    public static <T> Optional<List<TreeNode<T>>> pathToRoot(TreeNode<T> root,
                                                             TreeNode<T> node,
                                                             boolean reverse) {
        return pathToRoot(List.of(Objects.requireNonNull(root, "root must not be null")), node, reverse);
    }

    /**
     * Returns the business data of the path from the given node up to its
     * root, searching the forest for the node instance.
     *
     * <p>Equivalent to {@link #pathToRootData(Collection, TreeNode, boolean)}
     * with {@code reverse = false}.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param node the node whose path to its root should be returned; must
     *             not be null
     * @param <T> the business data type
     * @return the path data from {@code node} to its root, node first, root
     *         last, both included; {@code Optional.empty()} if {@code node}
     *         is not part of the forest
     * @throws NullPointerException if {@code roots} or {@code node} is null
     */
    public static <T> Optional<List<T>> pathToRootData(Collection<TreeNode<T>> roots,
                                                       TreeNode<T> node) {
        return pathToRoot(roots, node).map(TreeUtils::mapData);
    }

    /**
     * Returns the business data of the path between the given node and its
     * root, searching the forest for the node instance.
     *
     * <p>By default the path data is node first and root last; with
     * {@code reverse = true} it is root first and node last. Both ends are
     * always included.
     *
     * @param roots the root nodes; must not be null; an empty collection
     *              yields {@code Optional.empty()}
     * @param node the node whose path to its root should be returned; must
     *             not be null
     * @param reverse {@code true} returns the path data root first and node
     *                last; {@code false} (the default) returns it node first
     *                and root last
     * @param <T> the business data type
     * @return the path data between {@code node} and its root in the
     *         requested order, or {@code Optional.empty()} if {@code node}
     *         is not part of the forest
     * @throws NullPointerException if {@code roots} or {@code node} is null
     */
    public static <T> Optional<List<T>> pathToRootData(Collection<TreeNode<T>> roots,
                                                       TreeNode<T> node,
                                                       boolean reverse) {
        return pathToRoot(roots, node, reverse).map(TreeUtils::mapData);
    }

    /**
     * Returns the business data of the path from the given node up to the
     * passed root, searching the subtree of the root for the node instance.
     *
     * <p>Equivalent to {@link #pathToRootData(TreeNode, TreeNode, boolean)}
     * with {@code reverse = false}.
     *
     * @param root the root of the tree to search; must not be null
     * @param node the node whose path to the root should be returned; must
     *             not be null
     * @param <T> the business data type
     * @return the path data from {@code node} up to {@code root}, node first,
     *         root last, both included; {@code Optional.empty()} if
     *         {@code node} is not part of the subtree of {@code root}
     * @throws NullPointerException if {@code root} or {@code node} is null
     */
    public static <T> Optional<List<T>> pathToRootData(TreeNode<T> root,
                                                       TreeNode<T> node) {
        return pathToRoot(root, node).map(TreeUtils::mapData);
    }

    /**
     * Returns the business data of the path between the given node and the
     * passed root, searching the subtree of the root for the node instance.
     *
     * <p>By default the path data is node first and root last; with
     * {@code reverse = true} it is root first and node last. Both ends are
     * always included.
     *
     * @param root the root of the tree to search; must not be null
     * @param node the node whose path to the root should be returned; must
     *             not be null
     * @param reverse {@code true} returns the path data root first and node
     *                last; {@code false} (the default) returns it node first
     *                and root last
     * @param <T> the business data type
     * @return the path data between {@code node} and {@code root} in the
     *         requested order, or {@code Optional.empty()} if {@code node}
     *         is not part of the subtree of {@code root}
     * @throws NullPointerException if {@code root} or {@code node} is null
     */
    public static <T> Optional<List<T>> pathToRootData(TreeNode<T> root,
                                                       TreeNode<T> node,
                                                       boolean reverse) {
        return pathToRoot(root, node, reverse).map(TreeUtils::mapData);
    }

    private static <T> List<TreeNode<T>> reconstructPath(Map<TreeNode<T>, TreeNode<T>> parent,
                                                         TreeNode<T> target) {
        List<TreeNode<T>> path = new ArrayList<>();
        TreeNode<T> node = target;
        while (node != null) {
            path.add(node);
            node = parent.get(node);
        }
        Collections.reverse(path);
        return path;
    }

    private static <T, K> K requireNonNullKey(K key, T data) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null for node: " + data);
        }
        return key;
    }

    private static void requireNonNegative(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative: " + depth);
        }
    }

    private static <T> void pushReversed(Deque<TreeNode<T>> stack,
                                         Deque<Integer> depths,
                                         Collection<TreeNode<T>> nodes,
                                         int depth) {
        List<TreeNode<T>> list = nodes instanceof List<TreeNode<T>> listNodes
                ? listNodes
                : new ArrayList<>(nodes);
        for (int i = list.size() - 1; i >= 0; i--) {
            stack.push(list.get(i));
            depths.push(depth);
        }
    }

    private static <T> List<T> mapData(List<TreeNode<T>> nodes) {
        List<T> data = new ArrayList<>(nodes.size());
        for (TreeNode<T> node : nodes) {
            data.add(node.getData());
        }
        return data;
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
