package com.github.wcqtech.jakit.utils.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable node of a tree built by {@link TreeUtils}.
 *
 * <p>A node does not hold a reference to its parent, so trees are safe to
 * share, cache, and serialize. The business {@code data} object is stored as
 * is; whether it is mutable is decided by the caller.
 *
 * @param <T> the type of the business data carried by the node
 */
public final class TreeNode<T> {

    private final T data;
    private final List<TreeNode<T>> children;
    private final List<TreeNode<T>> childrenView;
    private final int depth;

    TreeNode(T data, List<TreeNode<T>> children, int depth) {
        this.data = data;
        this.children = new ArrayList<>(Objects.requireNonNull(children));
        this.childrenView = Collections.unmodifiableList(this.children);
        this.depth = depth;
    }

    /**
     * Returns the business data carried by this node.
     *
     * @return the business data
     */
    public T getData() {
        return data;
    }

    /**
     * Returns a read-only view of the children of this node.
     *
     * @return the children, ordered as configured during construction
     */
    public List<TreeNode<T>> getChildren() {
        return childrenView;
    }

    /**
     * Returns the absolute depth of this node; the root of a tree is at depth
     * {@code 0}.
     *
     * @return the absolute depth
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Returns whether this node has no children.
     *
     * @return {@code true} if this node is a leaf
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    void sortChildren(Comparator<? super TreeNode<T>> comparator) {
        children.sort(comparator);
    }
}
