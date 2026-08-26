package com.github.wcqtech.jakit.utils.tree;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

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
                () -> TreeUtils.sort(null, Comparator.comparing(Node::name)));
        assertThrows(NullPointerException.class,
                () -> TreeUtils.sort(roots, null));
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
