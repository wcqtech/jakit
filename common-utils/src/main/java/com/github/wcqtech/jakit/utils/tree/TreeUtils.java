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

    private static void requireNonNegative(int maxDepth) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must not be negative: " + maxDepth);
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
