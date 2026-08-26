package com.github.wcqtech.jakit.utils.tree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeNodeTest {

    @Test
    void exposesDataDepthAndLeafState() {
        TreeNode<String> leaf = new TreeNode<>("leaf", List.of(), 2);

        assertEquals("leaf", leaf.getData());
        assertEquals(2, leaf.getDepth());
        assertTrue(leaf.isLeaf());
    }

    @Test
    void nodeWithChildrenIsNotLeaf() {
        TreeNode<String> child = new TreeNode<>("child", List.of(), 1);
        TreeNode<String> root = new TreeNode<>("root", List.of(child), 0);

        assertFalse(root.isLeaf());
        assertEquals(List.of(child), root.getChildren());
    }

    @Test
    void childrenAreReadOnly() {
        TreeNode<String> root = new TreeNode<>("root", List.of(), 0);

        assertThrows(UnsupportedOperationException.class,
                () -> root.getChildren().add(new TreeNode<>("child", List.of(), 1)));
    }

    @Test
    void childrenAreDefensivelyCopied() {
        List<TreeNode<String>> children = new ArrayList<>();
        TreeNode<String> root = new TreeNode<>("root", children, 0);
        children.add(new TreeNode<>("late", List.of(), 1));

        assertEquals(List.of(), root.getChildren());
    }

    @Test
    void rejectsNullChildren() {
        assertThrows(NullPointerException.class, () -> new TreeNode<>("root", null, 0));
    }
}
