/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moyashi.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 *
 * @author Low Teck Wei
 * @param <T>
 */
public class SpatialTree<T extends Leaf> {

    private static final int SELF = -1;

    private final int dimensions;
    private final ArrayDeque<TreeNode<T>[]> treeNodes = new ArrayDeque<>();
    private final ArrayDeque<LeafNode<T>> leafNodes = new ArrayDeque<>();
    private final int regions;
    private final HashMap<T, LeafNode<T>> leafs = new HashMap<>();
    private final int splitThreshold;
    private final float[] splitMinimumBuffer, splitMaximumBuffer;
    private final TreeNode<T> treeNode;

    public SpatialTree(int dimensions, int splitThreshold) {
        this.dimensions = dimensions;
        this.splitThreshold = splitThreshold;

        regions = (int) Math.pow(2, dimensions);
        splitMinimumBuffer = new float[dimensions];
        splitMaximumBuffer = new float[dimensions];

        treeNode = new TreeNode<>(this);
    }

    public void resize(float[] minimum, float[] maximum) {
        for (LeafNode<T> leafNode : leafs.values()) {
            leafNode.parent = null;
        }

        if (treeNode.subTrees != null) {
            treeNodes.addLast(treeNode.subTrees);
        }
        treeNode.subTrees = null;
        treeNode.leafs.clear();
        treeNode.bounds.setBounds(minimum, maximum);

        leafs.values().forEach(treeNode::insert);
    }

    public void insert(T leaf) {
        LeafNode<T> leafNode = getLeafNode(leaf);
        leafNode.updateNode();
        insert(leafNode);
    }

    private void insert(LeafNode<T> leafNode) {
        TreeNode<T> insertionPoint = treeNode;

        if (leafNode.parent != null) {
            if (leafNode.parent.bounds.contains(leafNode.bounds)) {
                int index = leafNode.parent.indexOf(leafNode);
                if (index == SELF) {
                    return;
                } else {
                    insertionPoint = leafNode.parent.subTrees[index];
                }
            }
            leafNode.parent.leafs.remove(leafNode);
        }

        insertionPoint.insert(leafNode);
    }

    public void delete(T leaf) {
        LeafNode<T> leafNode = leafs.remove(leaf);
        if (leafNode != null) {
            if (leafNode.parent != null) {
                leafNode.parent.delete(leafNode);
            }
            leafNodes.addLast(leafNode);
        }
    }

    public void search(ArrayList<T> output, float[] minimum, float[] maximum) {
        treeNode.search(output, minimum, maximum);
    }

    public void visit(SpatialTreeVisitor<T> visitor) {
        treeNode.visit(visitor);
    }

    public void update() {
        for (LeafNode<T> leafNode : leafs.values()) {
            if (!leafNode.leaf.isStatic()) {
                leafNode.updateNode();
                insert(leafNode);
            }
        }
    }

    public int size() {
        assert treeNode.size == leafs.size();

        return leafs.size();
    }

    private TreeNode<T>[] obtainTreeNodes() {
        TreeNode<T>[] treeNodes;
        if (this.treeNodes.isEmpty()) {
            treeNodes = new TreeNode[regions];
            for (int i = 0; i < treeNodes.length; i++) {
                treeNodes[i] = new TreeNode<>(this);
            }
        } else {
            treeNodes = this.treeNodes.removeFirst();
            for (TreeNode<T> treeNode : treeNodes) {
                if (treeNode.subTrees != null) {
                    this.treeNodes.addLast(treeNode.subTrees);
                }
                treeNode.subTrees = null;
            }
        }
        return treeNodes;
    }

    private LeafNode<T> obtainLeafNode() {
        return leafNodes.isEmpty() ? new LeafNode<>(this) : leafNodes.removeFirst();
    }

    private LeafNode<T> getLeafNode(T leaf) {
        LeafNode<T> leafNode = leafs.get(leaf);
        if (leafNode == null) {
            leafs.put(leaf, leafNode = obtainLeafNode());
            leafNode.leaf = leaf;
        }
        return leafNode;
    }

    private static class LeafNode<T extends Leaf> {

        public final SpatialTree<T> root;
        public final Bounds bounds;
        public TreeNode<T> parent;
        public T leaf;

        public LeafNode(SpatialTree<T> root) {
            this.root = root;
            bounds = new Bounds(root.dimensions);
        }

        public void updateNode() {
            if (leaf != null) {
                leaf.getMinimum(bounds.minimum);
                leaf.getMaximum(bounds.maximum);
                for (int i = 0; i < root.dimensions; i++) {
                    bounds.middle[i] = (bounds.minimum[i] + bounds.maximum[i]) / 2;
                }
            }
        }
    }

    private static class Bounds {

        public final int dimensions;
        public final float[] minimum, maximum, middle;

        public Bounds(int dimensions) {
            this.dimensions = dimensions;
            minimum = new float[dimensions];
            maximum = new float[dimensions];
            middle = new float[dimensions];
        }

        public void setBounds(float[] minimum, float[] maximum) {
            System.arraycopy(minimum, 0, this.minimum, 0, dimensions);
            System.arraycopy(maximum, 0, this.maximum, 0, dimensions);
            for (int i = 0; i < dimensions; i++) {
                middle[i] = (minimum[i] + maximum[i]) / 2;
            }
        }

        public boolean contains(Bounds bounds) {
            return contains(bounds.minimum, bounds.maximum);
        }

        public boolean contains(float[] minimum, float[] maximum) {
            assert minimum.length == dimensions && maximum.length == dimensions;

            for (int i = 0; i < dimensions; i++) {
                if (!(minimum[i] > this.minimum[i] && maximum[i] < this.maximum[i])) {
                    return false;
                }
            }
            return true;
        }

        public boolean overlaps(Bounds bounds) {
            return overlaps(bounds.minimum, bounds.maximum);
        }

        public boolean overlaps(float[] minimum, float[] maximum) {
            assert minimum.length == dimensions && maximum.length == dimensions;

            for (int i = 0; i < dimensions; i++) {
                if (!(minimum[i] < this.maximum[i] && maximum[i] > this.minimum[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class TreeNode<T extends Leaf> {

        public TreeNode<T> parent;
        public final SpatialTree<T> root;
        public final Bounds bounds;
        public TreeNode[] subTrees;
        public final HashSet<LeafNode<T>> leafs;
        public int size;

        public TreeNode(SpatialTree<T> root) {
            this.root = root;
            bounds = new Bounds(root.dimensions);
            leafs = new HashSet<>(root.splitThreshold);
        }

        public void search(ArrayList<T> output, float[] minimum, float[] maximum) {
            TreeNode<T> treeNode = this;
            while (treeNode.subTrees != null) {
                if (treeNode.size == 0) {
                    return;
                }
                int index = treeNode.indexOf(minimum, maximum);
                if (index == SELF) {
                    treeNode.collectAll(output, minimum, maximum);
                    return;
                } else {
                    treeNode.collectSelf(output, minimum, maximum);
                    treeNode = subTrees[index];
                }
            }
            collectSelf(output, minimum, maximum);
        }

        private void collectAll(ArrayList<T> output, float[] minimum, float[] maximum) {
            collectSelf(output, minimum, maximum);
            if (subTrees != null) {
                for (TreeNode<T> subTree : subTrees) {
                    if (subTree.size > 0) {
                        subTree.collectAll(output, minimum, maximum);
                    }
                }
            }
        }

        private void collectSelf(ArrayList<T> output, float[] minimum, float[] maximum) {
            for (LeafNode<T> leafNode : leafs) {
                if (leafNode.bounds.overlaps(minimum, maximum)) {
                    output.add(leafNode.leaf);
                }
            }
        }

        public void visit(SpatialTreeVisitor<T> visitor) {
            if (size > 0) {
                visitor.visitTree(bounds.minimum, bounds.maximum);
                for (LeafNode<T> leafNode : leafs) {
                    visitor.visitLeaf(leafNode.leaf, leafNode.bounds.minimum, leafNode.bounds.maximum);
                }
                if (subTrees != null) {
                    for (TreeNode<T> subTree : subTrees) {
                        subTree.visit(visitor);
                    }
                }
            }
        }

        public void insert(LeafNode<T> leafNode) {
            if (subTrees != null) {
                int index = indexOf(leafNode);
                if (index != SELF) {
                    subTrees[index].insert(leafNode);
                    return;
                }
            }

            leafs.add(leafNode);
            leafNode.parent = this;
            size++;

            if (subTrees == null && leafs.size() >= root.splitThreshold) {
                split();
                distribute();
            }
        }

        public void delete(LeafNode<T> leafNode) {
            if (leafs.remove(leafNode)) {
                TreeNode<T> treeNode = this;
                while (treeNode != null) {
                    treeNode.size--;
                    treeNode = treeNode.parent;
                }
            }
        }

        public int indexOf(LeafNode<T> leafNode) {
            return indexOf(leafNode.bounds.minimum, leafNode.bounds.maximum);
        }

        public int indexOf(float[] minimum, float[] maximum) {
            assert minimum.length == root.dimensions && maximum.length == root.dimensions;

            boolean[] leftSide = new boolean[root.dimensions];
            boolean[] rightSide = new boolean[root.dimensions];
            for (int i = 0; i < root.dimensions; i++) {
                leftSide[i] = maximum[i] < bounds.middle[i];
                rightSide[i] = minimum[i] > bounds.middle[i];
            }

            int index = 0;
            for (int i = 0; i < root.dimensions; i++) {
                if (leftSide[i]) {
                    index *= 2;
                } else if (rightSide[i]) {
                    index = index * 2 + 1;
                } else {
                    return SELF;
                }
            }
            return index;
        }

        private void distribute() {
            for (Iterator<LeafNode<T>> it = leafs.iterator(); it.hasNext();) {
                LeafNode<T> leafNode = it.next();
                int index = indexOf(leafNode);
                if (index != SELF) {
                    it.remove();
                    subTrees[index].insert(leafNode);
                }
            }
        }

        private void split() {
            subTrees = root.obtainTreeNodes();
            for (TreeNode<T> subTree : subTrees) {
                subTree.parent = this;
                subTree.size = 0;
            }
            setSubtreeBounds(root.splitMinimumBuffer, root.splitMaximumBuffer, 0, 0);
        }

        private void setSubtreeBounds(float[] minimum, float[] maximum, int dimension, int index) {
            if (dimension == root.dimensions) {
                subTrees[index].bounds.setBounds(minimum, maximum);
            } else {
                minimum[dimension] = bounds.minimum[dimension];
                maximum[dimension] = bounds.middle[dimension];
                setSubtreeBounds(minimum, maximum, dimension + 1, index * 2);

                minimum[dimension] = bounds.middle[dimension];
                maximum[dimension] = bounds.maximum[dimension];
                setSubtreeBounds(minimum, maximum, dimension + 1, index * 2 + 1);
            }
        }
    }
}
