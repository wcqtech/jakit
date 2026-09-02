package com.github.wcqtech.jakit.utils.tree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeUtilsTest {

    private static final class Node {
        private final Integer id;
        private final Integer parentId;
        private final String name;

        private Node(Integer id, Integer parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        Integer id() {
            return id;
        }

        Integer parentId() {
            return parentId;
        }

        String name() {
            return name;
        }
    }

    private static Node node(Integer id, Integer parentId) {
        return new Node(id, parentId, "node-" + id);
    }

    private static List<Integer> ids(List<TreeNode<Node>> nodes) {
        return nodes.stream().map(node -> node.getData().id()).toList();
    }

    private static List<Integer> dataIds(List<Node> data) {
        return data.stream().map(Node::id).toList();
    }

    private static List<String> nodeNames(List<TreeNode<Node>> nodes) {
        return nodes.stream().map(node -> node.getData().name()).toList();
    }

    private static List<String> dataNames(List<Node> data) {
        return data.stream().map(Node::name).toList();
    }

    private static List<TreeNode<Node>> sampleForest() {
        return TreeUtils.buildTree(List.of(
                node(1, null),
                node(2, 1),
                node(3, 1),
                node(4, 2),
                node(5, 2),
                node(6, null),
                node(7, 6)
        ), Node::id, Node::parentId);
    }

    @Test
    void buildsForestWithOrphansAsRoots() {
        List<Node> nodes = List.of(
                node(1, null),
                node(2, 1),
                node(3, 1),
                node(4, 2),
                node(5, null),
                node(6, 99)
        );

        List<TreeNode<Node>> roots = TreeUtils.buildTree(nodes, Node::id, Node::parentId);

        assertEquals(List.of(1, 5, 6), ids(roots));
        TreeNode<Node> root = roots.get(0);
        assertEquals(0, root.getDepth());
        assertEquals(List.of(2, 3), ids(root.getChildren()));
        assertEquals(1, root.getChildren().get(0).getDepth());
        assertEquals(List.of(4), ids(root.getChildren().get(0).getChildren()));
        assertEquals(2, root.getChildren().get(0).getChildren().get(0).getDepth());
        assertTrue(roots.get(1).isLeaf());
    }

    @Test
    void preservesInputOrderWithoutComparator() {
        List<Node> nodes = List.of(
                node(1, null),
                node(4, 1),
                node(2, 1),
                node(3, 1)
        );

        List<TreeNode<Node>> roots = TreeUtils.buildTree(nodes, Node::id, Node::parentId);

        assertEquals(List.of(4, 2, 3), ids(roots.get(0).getChildren()));
    }

    @Test
    void emptyCollectionReturnsEmptyList() {
        assertTrue(TreeUtils.buildTree(List.of(), Node::id, Node::parentId).isEmpty());
    }

    @Test
    void rejectsNullId() {
        List<Node> nodes = List.of(node(null, null));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.buildTree(nodes, Node::id, Node::parentId));

        assertTrue(exception.getMessage().contains("id"));
    }

    @Test
    void rejectsDuplicateId() {
        List<Node> nodes = List.of(node(1, null), node(1, 1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.buildTree(nodes, Node::id, Node::parentId));

        assertTrue(exception.getMessage().contains("1"));
    }

    @Test
    void rejectsCycle() {
        List<Node> nodes = List.of(node(1, 2), node(2, 1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.buildTree(nodes, Node::id, Node::parentId));

        assertTrue(exception.getMessage().contains("1"));
        assertTrue(exception.getMessage().contains("2"));
    }

    @Test
    void sortsRootsAndChildrenWithComparator() {
        Node rootA = new Node(1, null, "A");
        Node rootB = new Node(2, null, "B");
        Node childC = new Node(3, 1, "C");
        Node childD = new Node(4, 1, "D");
        List<Node> nodes = List.of(rootA, childC, rootB, childD);

        List<TreeNode<Node>> roots = TreeUtils.buildTree(
                nodes, Node::id, Node::parentId, Comparator.comparing(Node::name).reversed());

        assertEquals(List.of(2, 1), ids(roots));
        assertEquals(List.of(4, 3), ids(roots.get(1).getChildren()));
    }

    @Test
    void stableSortPreservesInputOrderForEqualKeys() {
        Node rootA = new Node(1, null, "same");
        Node rootB = new Node(2, null, "same");
        Node childZ = new Node(3, 1, "same");
        Node childY = new Node(4, 1, "same");
        Node childX = new Node(5, 1, "same");
        List<Node> nodes = List.of(rootA, childZ, rootB, childY, childX);

        List<TreeNode<Node>> roots = TreeUtils.buildTree(
                nodes, Node::id, Node::parentId, Comparator.comparing(Node::name));

        assertEquals(List.of(1, 2), ids(roots));
        assertEquals(List.of(3, 4, 5), ids(roots.get(0).getChildren()));
    }

    @Test
    void sortReordersRootsAndEveryChildrenLevel() {
        List<TreeNode<Node>> roots = TreeUtils.buildTree(List.of(
                new Node(1, null, "A"),
                new Node(2, null, "B"),
                new Node(3, 1, "D"),
                new Node(4, 1, "C")
        ), Node::id, Node::parentId);

        TreeUtils.sort(roots, Comparator.comparing(Node::name).reversed());

        assertEquals(List.of(2, 1), ids(roots));
        assertEquals(List.of(3, 4), ids(roots.get(1).getChildren()));
    }

    @Test
    void sortSingleRootSortsEveryChildrenLevel() {
        TreeNode<Node> root = TreeUtils.buildTree(List.of(
                new Node(1, null, "A"),
                new Node(2, 1, "D"),
                new Node(3, 1, "C"),
                new Node(4, 2, "F"),
                new Node(5, 2, "E")
        ), Node::id, Node::parentId).get(0);

        TreeUtils.sort(root, Comparator.comparing(Node::name).reversed());

        assertEquals(List.of(2, 3), ids(root.getChildren()));
        assertEquals(List.of(4, 5), ids(root.getChildren().get(0).getChildren()));
    }

    @Test
    void publicSortIsStableForEqualKeys() {
        Node rootA = new Node(1, null, "same");
        Node rootB = new Node(2, null, "same");
        Node childZ = new Node(3, 1, "same");
        Node childY = new Node(4, 1, "same");
        Node childX = new Node(5, 1, "same");
        List<TreeNode<Node>> roots = TreeUtils.buildTree(List.of(
                rootA, childZ, rootB, childY, childX
        ), Node::id, Node::parentId);

        TreeUtils.sort(roots, Comparator.comparing(Node::name));

        assertEquals(List.of(1, 2), ids(roots));
        assertEquals(List.of(3, 4, 5), ids(roots.get(0).getChildren()));
    }

    @Test
    void preorderVisitsNodeThenChildren() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(1, 2, 4, 5, 3, 6, 7), ids(TreeUtils.preorder(roots)));
        assertEquals(List.of(1, 2, 4, 5, 3, 6, 7), dataIds(TreeUtils.preorderData(roots)));
        assertEquals(List.of(1, 2, 4, 5, 3), ids(TreeUtils.preorder(roots.get(0))));
        assertEquals(List.of(1, 2, 4, 5, 3), dataIds(TreeUtils.preorderData(roots.get(0))));
    }

    @Test
    void postorderVisitsChildrenThenNode() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(4, 5, 2, 3, 1, 7, 6), ids(TreeUtils.postorder(roots)));
        assertEquals(List.of(4, 5, 2, 3, 1, 7, 6), dataIds(TreeUtils.postorderData(roots)));
        assertEquals(List.of(4, 5, 2, 3, 1), ids(TreeUtils.postorder(roots.get(0))));
        assertEquals(List.of(4, 5, 2, 3, 1), dataIds(TreeUtils.postorderData(roots.get(0))));
    }

    @Test
    void bfsVisitsLevelByLevel() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(1, 6, 2, 3, 7, 4, 5), ids(TreeUtils.bfs(roots)));
        assertEquals(List.of(1, 6, 2, 3, 7, 4, 5), dataIds(TreeUtils.bfsData(roots)));
        assertEquals(List.of(1, 2, 3, 4, 5), ids(TreeUtils.bfs(roots.get(0))));
        assertEquals(List.of(1, 2, 3, 4, 5), dataIds(TreeUtils.bfsData(roots.get(0))));
    }

    @Test
    void depthLimitedTraversalUsesDepthRelativeToPassedRoots() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(1, 6), ids(TreeUtils.preorder(roots, 0)));
        assertEquals(List.of(1, 2, 3, 6, 7), ids(TreeUtils.preorder(roots, 1)));
        assertEquals(List.of(1, 2, 4, 5, 3, 6, 7), ids(TreeUtils.preorder(roots, 2)));
        assertEquals(List.of(1, 6), ids(TreeUtils.postorder(roots, 0)));
        assertEquals(List.of(2, 3, 1, 7, 6), ids(TreeUtils.postorder(roots, 1)));
        assertEquals(List.of(1, 6), ids(TreeUtils.bfs(roots, 0)));
        assertEquals(List.of(1, 6, 2, 3, 7), ids(TreeUtils.bfs(roots, 1)));
    }

    @Test
    void depthLimitedSingleRootTraversal() {
        TreeNode<Node> root = sampleForest().get(0);

        assertEquals(List.of(1), ids(TreeUtils.preorder(root, 0)));
        assertEquals(List.of(1), ids(TreeUtils.postorder(root, 0)));
        assertEquals(List.of(1), ids(TreeUtils.bfs(root, 0)));
        assertEquals(List.of(1, 2, 3), ids(TreeUtils.preorder(root, 1)));
        assertEquals(List.of(2, 3, 1), ids(TreeUtils.postorder(root, 1)));
        assertEquals(List.of(1, 2, 3), ids(TreeUtils.bfs(root, 1)));
        assertEquals(List.of(1, 2, 4, 5, 3), ids(TreeUtils.preorder(root, 2)));
        assertEquals(List.of(4, 5, 2, 3, 1), ids(TreeUtils.postorder(root, 2)));
        assertEquals(List.of(1, 2, 3, 4, 5), ids(TreeUtils.bfs(root, 2)));
    }

    @Test
    void depthLimitBeyondTreeHeightReturnsFullTraversal() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(ids(TreeUtils.preorder(roots)), ids(TreeUtils.preorder(roots, 100)));
        assertEquals(ids(TreeUtils.postorder(roots)), ids(TreeUtils.postorder(roots, 100)));
        assertEquals(ids(TreeUtils.bfs(roots)), ids(TreeUtils.bfs(roots, 100)));
        assertEquals(ids(TreeUtils.preorder(roots)), ids(TreeUtils.preorder(roots, Integer.MAX_VALUE)));
    }

    @Test
    void depthLimitIsRelativeToPassedRoots() {
        List<TreeNode<Node>> roots = sampleForest();
        // node 2 sits at absolute depth 1, but becomes relative depth 0 here
        TreeNode<Node> subtree = roots.get(0).getChildren().get(0);

        assertEquals(List.of(2), ids(TreeUtils.preorder(subtree, 0)));
        assertEquals(List.of(2, 4, 5), ids(TreeUtils.preorder(subtree, 1)));
        assertEquals(List.of(4, 5, 2), ids(TreeUtils.postorder(subtree, 1)));
        assertEquals(List.of(2, 4, 5), ids(TreeUtils.bfs(subtree, 1)));
    }

    @Test
    void findAtDepthReturnsNodesExactlyAtDepth() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(1, 6), ids(TreeUtils.findAtDepth(roots, 0)));
        assertEquals(List.of(2, 3, 7), ids(TreeUtils.findAtDepth(roots, 1)));
        assertEquals(List.of(4, 5), ids(TreeUtils.findAtDepth(roots, 2)));
        assertTrue(TreeUtils.findAtDepth(roots, 3).isEmpty());

        assertEquals(List.of(1), ids(TreeUtils.findAtDepth(roots.get(0), 0)));
        assertEquals(List.of(2, 3), ids(TreeUtils.findAtDepth(roots.get(0), 1)));
        assertEquals(List.of(4, 5), ids(TreeUtils.findAtDepth(roots.get(0), 2)));
        assertTrue(TreeUtils.findAtDepth(roots.get(0), 3).isEmpty());
    }

    @Test
    void findAtDepthKeepsInputAndChildrenOrder() {
        List<TreeNode<Node>> roots = sampleForest();
        // roots are [1, 6] in input order; children of 1 are [2, 3]
        assertEquals(List.of(1, 6), ids(TreeUtils.findAtDepth(roots, 0)));
        assertEquals(List.of(2, 3, 7), ids(TreeUtils.findAtDepth(roots, 1)));
    }

    @Test
    void findAtDepthReturnsEmptyForShallowInput() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> leaf = roots.get(0).getChildren().get(1);

        assertTrue(TreeUtils.findAtDepth(leaf, 1).isEmpty());
        assertTrue(TreeUtils.findAtDepth(List.of(), 0).isEmpty());
        assertTrue(TreeUtils.findAtDepth(List.of(), 5).isEmpty());
    }

    @Test
    void findAtDepthRejectsNegativeDepth() {
        List<TreeNode<Node>> roots = sampleForest();

        assertThrows(IllegalArgumentException.class, () -> TreeUtils.findAtDepth(roots, -1));
        assertThrows(IllegalArgumentException.class, () -> TreeUtils.findAtDepth(roots.get(0), -1));
    }

    @Test
    void findByIdFindsNodeInForest() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(5, TreeUtils.findById(roots, Node::id, 5).orElseThrow().getData().id());
        assertEquals(2, TreeUtils.findById(roots, Node::id, 5).orElseThrow().getDepth());
        assertEquals(0, TreeUtils.findById(roots, Node::id, 1).orElseThrow().getDepth());
        assertEquals(2, TreeUtils.findById(roots.get(0), Node::id, 2).orElseThrow().getData().id());
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        List<TreeNode<Node>> roots = sampleForest();

        assertTrue(TreeUtils.findById(roots, Node::id, 99).isEmpty());
        assertTrue(TreeUtils.findById(List.of(), Node::id, 1).isEmpty());
        // node 6 lives in the second tree, not under root 1
        assertTrue(TreeUtils.findById(roots.get(0), Node::id, 6).isEmpty());
    }

    @Test
    void findByIdRejectsNullArguments() {
        List<TreeNode<Node>> roots = sampleForest();

        assertThrows(NullPointerException.class,
                () -> TreeUtils.findById((Collection<TreeNode<Node>>) null, Node::id, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findById(roots, null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findById((TreeNode<Node>) null, Node::id, 1));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.findById(roots, Node::id, null));
    }

    @Test
    void uniqueIndexBuildsKeyMapInPreorderOrder() {
        List<TreeNode<Node>> roots = sampleForest();

        Map<Integer, TreeNode<Node>> index = TreeUtils.uniqueIndex(roots, Node::id);

        assertEquals(List.of(1, 2, 4, 5, 3, 6, 7), new ArrayList<>(index.keySet()));
        assertEquals(3, index.get(3).getData().id());
        assertEquals(1, index.get(3).getDepth());
        assertEquals(7, TreeUtils.uniqueIndexData(roots, Node::id).get(7).id());
        assertEquals(0, TreeUtils.uniqueIndex(roots.get(0), Node::id).get(1).getDepth());
    }

    @Test
    void uniqueIndexRejectsDuplicateAndNullKeys() {
        List<TreeNode<Node>> dupRoots = TreeUtils.buildTree(List.of(
                new Node(1, null, "dup"),
                new Node(2, null, "dup")
        ), Node::id, Node::parentId);

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.uniqueIndex(dupRoots, Node::name));
        assertTrue(duplicate.getMessage().contains("dup"));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.uniqueIndexData(dupRoots, Node::name));

        List<TreeNode<Node>> roots = sampleForest();
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.uniqueIndex(roots, node -> null));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.uniqueIndexData(roots, node -> null));
    }

    @Test
    void indexGroupsByKeyInTraversalOrder() {
        // parent ids are never null here: 0 is not a real id, so node 1 is a root
        List<TreeNode<Node>> roots = TreeUtils.buildTree(List.of(
                node(1, 0), node(2, 1), node(3, 1), node(4, 2), node(5, 2)
        ), Node::id, Node::parentId);

        Map<Integer, List<TreeNode<Node>>> index = TreeUtils.index(roots, Node::parentId);

        assertEquals(List.of(0, 1, 2), new ArrayList<>(index.keySet()));
        assertEquals(List.of(1), ids(index.get(0)));
        assertEquals(List.of(2, 3), ids(index.get(1)));
        assertEquals(List.of(4, 5), ids(index.get(2)));

        Map<Integer, List<Node>> dataIndex = TreeUtils.indexData(roots, Node::parentId);
        assertEquals(List.of(2, 3), dataIds(dataIndex.get(1)));

        // single-root overload
        Map<Integer, List<TreeNode<Node>>> single = TreeUtils.index(roots.get(0), Node::parentId);
        assertEquals(List.of(0, 1, 2), new ArrayList<>(single.keySet()));
    }

    @Test
    void indexSortsEachGroupWithComparator() {
        List<TreeNode<Node>> roots = TreeUtils.buildTree(List.of(
                new Node(1, 0, "root1"),
                new Node(2, 1, "B"),
                new Node(3, 1, "A"),
                new Node(4, 2, "d"),
                new Node(5, 2, "c"),
                new Node(6, 1, "C")
        ), Node::id, Node::parentId);

        Map<Integer, List<TreeNode<Node>>> index = TreeUtils.index(roots, Node::parentId,
                Comparator.comparing(Node::name).reversed());

        assertEquals(List.of(0, 1, 2), new ArrayList<>(index.keySet()));
        assertEquals(List.of("root1"), nodeNames(index.get(0)));
        assertEquals(List.of("C", "B", "A"), nodeNames(index.get(1)));
        assertEquals(List.of("d", "c"), nodeNames(index.get(2)));

        Map<Integer, List<Node>> dataIndex = TreeUtils.indexData(roots, Node::parentId,
                Comparator.comparing(Node::name).reversed());
        assertEquals(List.of("C", "B", "A"), dataNames(dataIndex.get(1)));
    }

    @Test
    void indexReturnsEmptyForEmptyInput() {
        assertTrue(TreeUtils.uniqueIndex(List.of(), Node::id).isEmpty());
        assertTrue(TreeUtils.uniqueIndexData(List.of(), Node::id).isEmpty());
        assertTrue(TreeUtils.index(List.of(), Node::parentId).isEmpty());
        assertTrue(TreeUtils.indexData(List.of(), Node::parentId).isEmpty());
    }

    @Test
    void indexRejectsNullArgumentsAndKeys() {
        List<TreeNode<Node>> roots = sampleForest();

        assertThrows(NullPointerException.class,
                () -> TreeUtils.uniqueIndex((Collection<TreeNode<Node>>) null, Node::id));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.uniqueIndex(roots, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.uniqueIndex((TreeNode<Node>) null, Node::id));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.index((Collection<TreeNode<Node>>) null, Node::parentId));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.index(roots, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.index(roots, Node::parentId, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.indexData(roots, Node::parentId, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.indexData((TreeNode<Node>) null, Node::parentId));

        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.index(roots, node -> null));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.indexData(roots, node -> null));
    }

    @Test
    void descendantsExcludesNodeItselfInPreorder() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(2, 4, 5, 3), ids(TreeUtils.descendants(roots.get(0))));
        assertEquals(List.of(4, 5), ids(TreeUtils.descendants(roots.get(0).getChildren().get(0))));
        assertEquals(List.of(7), ids(TreeUtils.descendants(roots.get(1))));
        assertTrue(TreeUtils.descendants(roots.get(0).getChildren().get(1)).isEmpty()); // leaf 3

        assertEquals(List.of(2, 4, 5, 3), dataIds(TreeUtils.descendantsData(roots.get(0))));
        assertTrue(TreeUtils.descendantsData(roots.get(0).getChildren().get(1)).isEmpty());
    }

    @Test
    void findPathReturnsRootToNodePath() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(1, 2, 4), ids(TreeUtils.findPath(roots, Node::id, 4).orElseThrow()));
        assertEquals(List.of(1), ids(TreeUtils.findPath(roots, Node::id, 1).orElseThrow()));
        assertEquals(List.of(6, 7), ids(TreeUtils.findPath(roots, Node::id, 7).orElseThrow()));
        assertTrue(TreeUtils.findPath(roots, Node::id, 99).isEmpty());
        assertTrue(TreeUtils.findPath(List.of(), Node::id, 1).isEmpty());
        // a subtree root passed as the only root starts a fresh path
        TreeNode<Node> subtree = roots.get(0).getChildren().get(0);
        assertEquals(List.of(2, 5), ids(TreeUtils.findPath(List.of(subtree), Node::id, 5).orElseThrow()));
    }

    @Test
    void findPathWithSingleRootOverload() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> root1 = roots.get(0);

        assertEquals(List.of(1, 2, 4), ids(TreeUtils.findPath(root1, Node::id, 4).orElseThrow()));
        // node 7 lives in the other tree, not under root 1
        assertTrue(TreeUtils.findPath(root1, Node::id, 7).isEmpty());
        assertTrue(TreeUtils.findPath(root1, Node::id, 99).isEmpty());

        assertEquals(List.of(1, 2, 4), dataIds(TreeUtils.findPathData(root1, Node::id, 4).orElseThrow()));
        assertTrue(TreeUtils.findPathData(root1, Node::id, 7).isEmpty());
    }

    @Test
    void findPathDataReturnsBusinessDataPath() {
        List<TreeNode<Node>> roots = sampleForest();

        assertEquals(List.of(1, 2, 4), dataIds(TreeUtils.findPathData(roots, Node::id, 4).orElseThrow()));
        assertTrue(TreeUtils.findPathData(roots, Node::id, 99).isEmpty());
    }

    @Test
    void pathToRootWalksFromNodeUpToRoot() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> node4 = TreeUtils.findById(roots, Node::id, 4).orElseThrow();
        TreeNode<Node> node7 = TreeUtils.findById(roots, Node::id, 7).orElseThrow();

        assertEquals(List.of(4, 2, 1), ids(TreeUtils.pathToRoot(roots, node4).orElseThrow()));
        assertEquals(List.of(7, 6), ids(TreeUtils.pathToRoot(roots, node7).orElseThrow()));
        // a root is its own path
        assertEquals(List.of(1), ids(TreeUtils.pathToRoot(roots, roots.get(0)).orElseThrow()));
    }

    @Test
    void pathToRootWithReverseSupportsBothDirections() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> node4 = TreeUtils.findById(roots, Node::id, 4).orElseThrow();

        assertEquals(List.of(4, 2, 1), ids(TreeUtils.pathToRoot(roots, node4, false).orElseThrow()));
        assertEquals(List.of(1, 2, 4), ids(TreeUtils.pathToRoot(roots, node4, true).orElseThrow()));
        // a root is its own path in both directions
        assertEquals(List.of(1), ids(TreeUtils.pathToRoot(roots, roots.get(0), true).orElseThrow()));
        // both directions contain the same nodes
        assertEquals(
                ids(TreeUtils.pathToRoot(roots, node4, false).orElseThrow()).stream().sorted().toList(),
                ids(TreeUtils.pathToRoot(roots, node4, true).orElseThrow()).stream().sorted().toList());
    }

    @Test
    void pathToRootWithSingleRootOverloads() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> root1 = roots.get(0);
        TreeNode<Node> node4 = TreeUtils.findById(roots, Node::id, 4).orElseThrow();
        TreeNode<Node> node7 = TreeUtils.findById(roots, Node::id, 7).orElseThrow();

        assertEquals(List.of(4, 2, 1), ids(TreeUtils.pathToRoot(root1, node4).orElseThrow()));
        assertEquals(List.of(1, 2, 4), ids(TreeUtils.pathToRoot(root1, node4, true).orElseThrow()));
        List<Integer> nodeFirst = new ArrayList<>(ids(TreeUtils.pathToRoot(root1, node4, false).orElseThrow()));
        Collections.reverse(nodeFirst);
        assertEquals(nodeFirst, ids(TreeUtils.pathToRoot(root1, node4, true).orElseThrow()));
        // a node from another tree is not under root 1
        assertTrue(TreeUtils.pathToRoot(root1, node7).isEmpty());
        // the root itself is its own path
        assertEquals(List.of(1), ids(TreeUtils.pathToRoot(root1, root1).orElseThrow()));
        assertEquals(List.of(1), ids(TreeUtils.pathToRoot(root1, root1, true).orElseThrow()));
    }

    @Test
    void pathToRootDataReturnsBusinessDataPath() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> root1 = roots.get(0);
        TreeNode<Node> node4 = TreeUtils.findById(roots, Node::id, 4).orElseThrow();
        TreeNode<Node> foreign = TreeUtils.buildTree(List.of(node(100, null)), Node::id, Node::parentId).get(0);

        assertEquals(List.of(4, 2, 1), dataIds(TreeUtils.pathToRootData(roots, node4).orElseThrow()));
        assertEquals(List.of(1, 2, 4), dataIds(TreeUtils.pathToRootData(roots, node4, true).orElseThrow()));
        assertEquals(List.of(4, 2, 1), dataIds(TreeUtils.pathToRootData(root1, node4).orElseThrow()));
        assertEquals(List.of(1, 2, 4), dataIds(TreeUtils.pathToRootData(root1, node4, true).orElseThrow()));
        assertTrue(TreeUtils.pathToRootData(roots, foreign).isEmpty());
        assertTrue(TreeUtils.pathToRootData(root1, foreign).isEmpty());
    }

    @Test
    void pathToRootIsReverseOfFindPath() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> node5 = TreeUtils.findById(roots, Node::id, 5).orElseThrow();

        List<Integer> forward = new ArrayList<>(ids(TreeUtils.findPath(roots, Node::id, 5).orElseThrow()));
        Collections.reverse(forward);
        assertEquals(forward, ids(TreeUtils.pathToRoot(roots, node5).orElseThrow()));
    }

    @Test
    void pathToRootReturnsEmptyForForeignOrMissingNode() {
        List<TreeNode<Node>> roots = sampleForest();
        List<TreeNode<Node>> other = TreeUtils.buildTree(List.of(node(100, null)), Node::id, Node::parentId);

        assertTrue(TreeUtils.pathToRoot(List.of(), roots.get(0)).isEmpty());
        assertTrue(TreeUtils.pathToRoot(roots, other.get(0)).isEmpty());
    }

    @Test
    void pathToRootRejectsNullArguments() {
        List<TreeNode<Node>> roots = sampleForest();

        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRoot((Collection<TreeNode<Node>>) null, roots.get(0)));
        assertThrows(NullPointerException.class, () -> TreeUtils.pathToRoot(roots, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRoot((TreeNode<Node>) null, roots.get(0)));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRoot(roots.get(0), null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRoot((TreeNode<Node>) null, roots.get(0), true));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRootData((Collection<TreeNode<Node>>) null, roots.get(0)));
        assertThrows(NullPointerException.class, () -> TreeUtils.pathToRootData(roots, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRootData((TreeNode<Node>) null, roots.get(0)));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRootData(roots.get(0), null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.pathToRootData((TreeNode<Node>) null, roots.get(0), true));
    }

    @Test
    void descendantsAndFindPathRejectNullArguments() {
        List<TreeNode<Node>> roots = sampleForest();

        assertThrows(NullPointerException.class, () -> TreeUtils.descendants(null));
        assertThrows(NullPointerException.class, () -> TreeUtils.descendantsData(null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPath((Collection<TreeNode<Node>>) null, Node::id, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPath(roots, null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPathData((Collection<TreeNode<Node>>) null, Node::id, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPath((TreeNode<Node>) null, Node::id, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPath(roots.get(0), null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPathData((TreeNode<Node>) null, Node::id, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findPathData(roots.get(0), null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.findPath(roots, Node::id, null));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.findPathData(roots, Node::id, null));
        assertThrows(IllegalArgumentException.class,
                () -> TreeUtils.findPath(roots.get(0), Node::id, null));
    }

    @Test
    void traversalReturnsEmptyForEmptyCollection() {
        assertTrue(TreeUtils.preorder(List.of()).isEmpty());
        assertTrue(TreeUtils.postorder(List.of()).isEmpty());
        assertTrue(TreeUtils.bfs(List.of()).isEmpty());
        assertTrue(TreeUtils.preorderData(List.of()).isEmpty());
        assertTrue(TreeUtils.postorderData(List.of()).isEmpty());
        assertTrue(TreeUtils.bfsData(List.of()).isEmpty());
        assertTrue(TreeUtils.preorder(List.of(), 3).isEmpty());
        assertTrue(TreeUtils.postorder(List.of(), 3).isEmpty());
        assertTrue(TreeUtils.bfs(List.of(), 3).isEmpty());
    }

    @Test
    void traversalDoesNotModifyTree() {
        List<TreeNode<Node>> roots = sampleForest();
        List<TreeNode<Node>> rootChildren = roots.get(0).getChildren();

        TreeUtils.preorder(roots);
        TreeUtils.postorder(roots);
        TreeUtils.bfs(roots);
        TreeUtils.preorder(roots, 1);
        TreeUtils.postorder(roots, 1);
        TreeUtils.bfs(roots, 1);

        assertEquals(List.of(1, 6), ids(roots));
        assertEquals(List.of(2, 3), ids(rootChildren));
    }

    @Test
    void traversalRejectsNegativeMaxDepth() {
        List<TreeNode<Node>> roots = sampleForest();
        TreeNode<Node> root = roots.get(0);

        assertThrows(IllegalArgumentException.class, () -> TreeUtils.preorder(roots, -1));
        assertThrows(IllegalArgumentException.class, () -> TreeUtils.postorder(roots, -1));
        assertThrows(IllegalArgumentException.class, () -> TreeUtils.bfs(roots, -1));
        assertThrows(IllegalArgumentException.class, () -> TreeUtils.preorder(root, -1));
        assertThrows(IllegalArgumentException.class, () -> TreeUtils.postorder(root, -1));
        assertThrows(IllegalArgumentException.class, () -> TreeUtils.bfs(root, -1));
    }

    @Test
    void sortAndTraversalRejectNullArguments() {
        List<TreeNode<Node>> roots = sampleForest();

        assertThrows(NullPointerException.class,
                () -> TreeUtils.sort((List<TreeNode<Node>>) null, Comparator.comparing(Node::name)));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.sort(roots, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.sort((TreeNode<Node>) null, Comparator.comparing(Node::name)));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.sort(roots.get(0), null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.preorder((TreeNode<Node>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.preorder((List<TreeNode<Node>>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.postorder((TreeNode<Node>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.postorder((List<TreeNode<Node>>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.bfs((TreeNode<Node>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.bfs((List<TreeNode<Node>>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.preorderData((TreeNode<Node>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.preorderData((List<TreeNode<Node>>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.postorderData((TreeNode<Node>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.postorderData((List<TreeNode<Node>>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.bfsData((TreeNode<Node>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.bfsData((List<TreeNode<Node>>) null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.preorder((TreeNode<Node>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.preorder((List<TreeNode<Node>>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.postorder((TreeNode<Node>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.postorder((List<TreeNode<Node>>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.bfs((TreeNode<Node>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.bfs((List<TreeNode<Node>>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findAtDepth((TreeNode<Node>) null, 1));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.findAtDepth((List<TreeNode<Node>>) null, 1));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> TreeUtils.buildTree(null, Node::id, Node::parentId));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.buildTree(List.of(), null, Node::parentId));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.buildTree(List.of(), Node::id, null));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.buildTree(List.of(), Node::id, Node::parentId, null));
    }
}
