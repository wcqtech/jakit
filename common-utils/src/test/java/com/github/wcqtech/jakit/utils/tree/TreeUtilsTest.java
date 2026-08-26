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
