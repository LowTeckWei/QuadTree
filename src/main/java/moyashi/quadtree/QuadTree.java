/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moyashi.quadtree;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Low Teck Wei
 * @param <T>
 */
//This class is not thread-safe, no snapshots done
public class QuadTree<T extends Leaf> {

    private static final int SELF = -1, NE = 0, NW = 1, SE = 2, SW = 3;
    private static final int LAYER_SIZE = 4, REINSERT_THRESHOLD = 3, ROOT_DEPTH = 0;

    private final BitSet treeIndex = new BitSet();
    private final List<TreeNode<T>> treePool = new ArrayList<>();

    private final BitSet leafIndex = new BitSet();
    private final List<LeafNode<T>> leafPool = new ArrayList<>();
    private final Map<T, LeafNode<T>> leafMap = new HashMap<>();

    private final int nodeCapacity;
    private TreeNode<T> root;
    private final Rectangle bufferRectangle = new Rectangle();
    private final List<T> bufferItems = new ArrayList<>();

    public QuadTree(int nodeCapacity, float minX, float minY, float width, float height) {
        this.nodeCapacity = nodeCapacity;
        root = obtainTreeNodeIndex(minX, minY, width, height, ROOT_DEPTH);
    }

    //Also functions as an update
    public void insert(T leaf) {
        LeafNode<T> leafNode = getLeafNode(leaf);

        //If leaf already exists, check if need to reinsert.
        if (leafNode.treeNode != null) {

            //If within, don't need to insert from root
            TreeNode<T> treeNode = leafNode.treeNode;
            if (treeNode.depth > REINSERT_THRESHOLD && treeNode.bounds.contains(leafNode.bounds)) {
                if (treeNode.childs != null) {
                    int index = treeNode.indexOf(leafNode.bounds);
                    if (index != SELF) {
                        treeNode.leafs.remove(leafNode);
                        treeNode.childs[index].insert(leafNode);
                    }
                }
                return;
            }

            //Remove and update size
            treeNode.leafs.remove(leafNode);
            do {
                treeNode.size--;
                treeNode = treeNode.parent;
            } while (treeNode != null);
        }

        root.insert(leafNode);
    }

    public void remove(T leaf) {
        LeafNode<T> leafNode = leafMap.remove(leaf);
        if (leafNode != null) {
            TreeNode<T> treeNode = leafNode.treeNode;
            treeNode.leafs.remove(leafNode);
            do {
                treeNode.size--;
                treeNode = treeNode.parent;
            } while (treeNode != null);
            leafNode.item = null;
            leafNode.treeNode = null;
            leafIndex.clear(leafNode.index);
        }
    }

    //APPENDS to result
    public List<T> search(List<T> result, float minX, float minY, float width, float height) {
        bufferRectangle.set(minX, minY, width, height);
        return root.search(result, bufferRectangle);
    }

    //Visits all nodes and items using depth first search.
    public void traverse(QuadTreeVisitor<T> renderer) {
        root.traverse(renderer);
    }

    //Resets everything, leaving only an empty root node
    public void clear() {
        float minX = root.bounds.minX;
        float minY = root.bounds.minY;
        float width = root.bounds.width;
        float height = root.bounds.height;

        treeIndex.clear();
        leafIndex.clear();
        leafMap.clear();
        root = obtainTreeNodeIndex(minX, minY, width, height, ROOT_DEPTH);
    }

    //Creates new root node, reinserts all items.
    public void resize(float minX, float minY, float width, float height) {
        treeIndex.clear();
        leafIndex.clear();
        root = obtainTreeNodeIndex(minX, minY, width, height, ROOT_DEPTH);
        leafMap.replaceAll((key, value) -> obtainLeafNode(key));
        leafMap.keySet().forEach(this::insert);
    }

    //Updates all item
    public void refresh() {
        leafMap.keySet().forEach(this::insert);
    }

    private LeafNode<T> getLeafNode(T leaf) {
        LeafNode<T> leafNode = leafMap.get(leaf);
        if (leafNode == null) {
            leafMap.put(leaf, leafNode = obtainLeafNode(leaf));
        } else {
            leafNode.bounds.set(leaf.getMinX(), leaf.getMinY(), leaf.getWidth(), leaf.getHeight());
        }
        return leafNode;
    }

    private LeafNode<T> obtainLeafNode(T item) {
        int index = leafIndex.nextClearBit(0);
        while (index >= leafPool.size()) {
            leafPool.add(new LeafNode<>(leafPool.size()));
        }
        leafIndex.set(index);

        LeafNode<T> leafNode = leafPool.get(index);
        leafNode.item = item;
        leafNode.treeNode = null;
        leafNode.bounds.set(item.getMinX(), item.getMinY(), item.getWidth(), item.getHeight());
        return leafNode;
    }

    private TreeNode<T> obtainTreeNodeIndex(float minX, float minY, float width, float height, int depth) {
        return obtainTreeNode(null, minX, minY, width, height, depth);
    }

    private TreeNode<T> obtainTreeNode(TreeNode parent, float minX, float minY, float width, float height, int depth) {
        int index = treeIndex.nextClearBit(0);
        while (index >= treePool.size()) {
            treePool.add(new TreeNode<>(this, treePool.size()));
        }
        treeIndex.set(index);

        TreeNode<T> treeNode = treePool.get(index);
        treeNode.parent = parent;
        treeNode.depth = depth;
        treeNode.bounds.set(minX, minY, width, height);
        treeNode.childs = null;
        treeNode.leafs.clear();
        treeNode.size = 0;
        return treeNode;
    }

    private static class LeafNode<T extends Leaf> {

        public final int index;
        public TreeNode<T> treeNode;
        public final Rectangle bounds = new Rectangle();
        public T item;

        public LeafNode(int index) {
            this.index = index;
        }
    }

    private static class TreeNode<T extends Leaf> {

        public final QuadTree<T> root;
        public final int index;
        public TreeNode<T> parent;
        public TreeNode<T>[] childs;
        public int depth, size;
        public final Rectangle bounds = new Rectangle();
        public final Set<LeafNode<T>> leafs = new HashSet<>();

        public TreeNode(QuadTree<T> root, int index) {
            this.root = root;
            this.index = index;
        }

        public void traverse(QuadTreeVisitor<T> visitor) {
            if (size > 0) {
                root.bufferRectangle.set(bounds);
                root.bufferItems.clear();
                for (LeafNode<T> leafNode : leafs) {
                    root.bufferItems.add(leafNode.item);
                }
                visitor.visit(root.bufferRectangle, root.bufferItems);
                root.bufferItems.clear();
                if (childs != null) {
                    for (TreeNode<T> child : childs) {
                        child.traverse(visitor);
                    }
                }
            }
        }

        public void insert(LeafNode<T> leafNode) {
            TreeNode<T> treeNode = this;
            while (treeNode.childs != null) {
                int childIndex = treeNode.indexOf(leafNode.bounds);
                if (childIndex == SELF) {
                    break;
                }
                treeNode.size++;
                treeNode = treeNode.childs[childIndex];
            }

            treeNode.size++;
            treeNode.leafs.add(leafNode);
            leafNode.treeNode = treeNode;

            if (treeNode.childs == null && treeNode.leafs.size() > root.nodeCapacity) {
                treeNode.split();
                for (Iterator<LeafNode<T>> it = treeNode.leafs.iterator(); it.hasNext();) {
                    LeafNode<T> t = it.next();
                    int childIndex = treeNode.indexOf(t.bounds);
                    if (childIndex != SELF) {
                        it.remove();
                        treeNode.childs[childIndex].insert(t);
                    }
                }
            }
        }

        public List<T> search(List<T> result, Rectangle targetAABB) {
            TreeNode<T> treeNode = this;
            while (treeNode != null) {
                int childIndex = treeNode.indexOf(targetAABB);
                if (childIndex == SELF) {
                    break;
                }
                treeNode.collectSelf(result, targetAABB);
                if (treeNode.childs == null || (treeNode = treeNode.childs[childIndex]).size <= 0) {
                    treeNode = null;
                }
            }

            if (treeNode != null) {
                treeNode.collectAll(result, targetAABB);
            }

            return result;
        }

        public void collectSelf(List<T> result, Rectangle aabb) {
            if (!leafs.isEmpty()) {
                for (LeafNode<T> leafNode : leafs) {
                    if (leafNode.bounds.overlaps(aabb)) {
                        result.add(leafNode.item);
                    }
                }
            }
        }

        public void collectAll(List<T> result, Rectangle aabb) {
            if (size > 0) {
                collectSelf(result, aabb);
                if (childs != null) {
                    for (TreeNode<T> child : childs) {
                        child.collectAll(result, aabb);
                    }
                }
            }
        }

        public int indexOf(Rectangle aabb) {
            if (bounds.contains(aabb)) {
                float midX = bounds.minX + bounds.width / 2;
                float midY = bounds.minY + bounds.height / 2;
                boolean east = aabb.minX > midX;
                boolean west = aabb.minX + aabb.width < midX;
                boolean north = aabb.minY > midY;
                boolean south = aabb.minY + aabb.height < midY;
                if (north) {
                    if (east) {
                        return NE;
                    } else if (west) {
                        return NW;
                    }
                } else if (south) {
                    if (east) {
                        return SE;
                    } else if (west) {
                        return SW;
                    }
                }
            }
            return SELF;
        }

        public void split() {
            float halfWidth = bounds.width / 2;
            float halfHeight = bounds.height / 2;
            childs = new TreeNode[LAYER_SIZE];
            childs[NE] = root.obtainTreeNode(this, bounds.minX + halfWidth, bounds.minY + halfHeight, halfWidth, halfHeight, depth + 1);
            childs[NW] = root.obtainTreeNode(this, bounds.minX, bounds.minY + halfHeight, halfWidth, halfHeight, depth + 1);
            childs[SE] = root.obtainTreeNode(this, bounds.minX + halfWidth, bounds.minY, halfWidth, halfHeight, depth + 1);
            childs[SW] = root.obtainTreeNode(this, bounds.minX, bounds.minY, halfWidth, halfHeight, depth + 1);
        }
    }
}
