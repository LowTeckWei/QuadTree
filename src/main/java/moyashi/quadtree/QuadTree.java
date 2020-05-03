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
import java.util.stream.Collectors;

/**
 *
 * @author Low Teck Wei
 * @param <T>
 */
//This class is not thread-safe, no snapshots done
public class QuadTree<T extends Leaf> {

    private static final int INVALID = -1, SELF = -1, NE = 0, NW = 1, SE = 2, SW = 3;
    private static final int LAYER_SIZE = 4, REINSERT_THRESHOLD = 3, ROOT_DEPTH = 0;

    private final BitSet treeIndex = new BitSet();
    private final List<TreeNode> treePool = new ArrayList<>();

    private final BitSet leafIndex = new BitSet();
    private final List<LeafNode> leafPool = new ArrayList<>();
    private final Map<T, LeafNode> leafMap = new HashMap<>();

    private final int nodeCapacity;
    private TreeNode root;
    private final Rectangle targetAABB = new Rectangle();

    public QuadTree(int nodeCapacity, float minX, float minY, float width, float height) {
        this.nodeCapacity = nodeCapacity;
        root = obtainTreeNodeIndex(INVALID, minX, minY, width, height, ROOT_DEPTH);
    }

    //Also functions as an update
    public void insert(T leaf) {

        //If leaf node exists update bounds else create
        LeafNode leafNode = leafMap.get(leaf);
        if (leafNode == null) {
            leafMap.put(leaf, leafNode = obtainLeafNodeIndex(leaf));
        } else {
            leafNode.bounds.set(leaf.getMinX(), leaf.getMinY(), leaf.getWidth(), leaf.getHeight());
        }

        //If leaf already exists, check if need to reinsert.
        if (leafNode.treeNode != INVALID) {

            //If within, don't need to insert from root
            TreeNode treeNode = treePool.get(leafNode.treeNode);
            if (treeNode.depth > REINSERT_THRESHOLD && treeNode.bounds.contains(leafNode.bounds)) {
                int index = treeNode.indexOf(leafNode.bounds);
                if (index != SELF) {
                    treeNode.leafs.remove(leafNode.index);
                    insert(treePool.get(treeNode.childs[index]), leafNode);
                }
                return;
            }

            //Remove and update size
            treeNode.leafs.remove(leafNode.index);
            do {
                treeNode.size--;
                treeNode = treeNode.parent == INVALID ? null : treePool.get(treeNode.parent);
            } while (treeNode != null);
        }

        insert(root, leafNode);
    }

    public void remove(T leaf) {
        LeafNode leafNode = leafMap.remove(leaf);
        if (leafNode != null) {
            TreeNode treeNode = treePool.get(leafNode.treeNode);
            treeNode.leafs.remove(leafNode.index);
            do {
                treeNode.size--;
                treeNode = treeNode.parent == INVALID ? null : treePool.get(treeNode.parent);
            } while (treeNode != null);
            leafNode.item = null;
            leafNode.treeNode = INVALID;
            leafIndex.clear(leafNode.index);
        }
    }

    //APPENDS to result
    public List<T> search(List<T> result, float minX, float minY, float width, float height) {
        targetAABB.set(minX, minY, width, height);

        TreeNode treeNode = root;
        while (treeNode != null) {
            int index = treeNode.indexOf(targetAABB);
            if (index == SELF) {
                break;
            }
            treeNode.collectSelf(result, targetAABB);
            treeNode = treeNode.childs == null ? null : treePool.get(treeNode.childs[index]);
        }

        if (treeNode != null) {
            treeNode.collectAll(result, targetAABB);
        }

        return result;
    }

    public void render(QuadTreeRenderer<T> renderer) {
        root.render(renderer);
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
        root = obtainTreeNodeIndex(INVALID, minX, minY, width, height, ROOT_DEPTH);
    }

    //Creates new root node, reinserts all items.
    public void resize(float minX, float minY, float width, float height) {
        treeIndex.clear();
        leafIndex.clear();
        root = obtainTreeNodeIndex(INVALID, minX, minY, width, height, ROOT_DEPTH);
        leafMap.replaceAll((key, value) -> obtainLeafNodeIndex(key));
        leafMap.keySet().forEach(this::insert);
    }

    //Updates all item
    public void refresh() {
        leafMap.keySet().forEach(this::insert);
    }

    private LeafNode obtainLeafNodeIndex(T item) {
        int index = leafIndex.nextClearBit(0);
        while (index >= leafPool.size()) {
            leafPool.add(new LeafNode(leafPool.size()));
        }
        leafIndex.set(index);

        LeafNode leafNode = leafPool.get(index);
        leafNode.item = item;
        leafNode.treeNode = INVALID;
        leafNode.bounds.set(item.getMinX(), item.getMinY(), item.getWidth(), item.getHeight());
        return leafNode;
    }

    private TreeNode obtainTreeNodeIndex(int parent, float minX, float minY, float width, float height, int depth) {
        int index = treeIndex.nextClearBit(0);
        while (index >= treePool.size()) {
            treePool.add(new TreeNode(treePool.size()));
        }
        treeIndex.set(index);

        TreeNode treeNode = treePool.get(index);
        treeNode.parent = parent;
        treeNode.depth = depth;
        treeNode.bounds.set(minX, minY, width, height);
        treeNode.childs = null;
        treeNode.leafs.clear();
        treeNode.size = 0;
        return treeNode;
    }

    private void insert(TreeNode treeNode, LeafNode leafNode) {
        while (treeNode.childs != null) {
            int index = treeNode.indexOf(leafNode.bounds);
            if (index == SELF) {
                break;
            }
            treeNode.size++;
            treeNode = treePool.get(treeNode.childs[index]);
        }

        treeNode.size++;
        treeNode.leafs.add(leafNode.index);
        leafNode.treeNode = treeNode.index;

        if (treeNode.childs == null && treeNode.leafs.size() > nodeCapacity) {
            treeNode.split();
            for (Iterator<Integer> it = treeNode.leafs.iterator(); it.hasNext();) {
                LeafNode t = leafPool.get(it.next());
                int index = treeNode.indexOf(t.bounds);
                if (index != SELF) {
                    it.remove();
                    insert(treePool.get(treeNode.childs[index]), t);
                }
            }
        }
    }

    private class LeafNode {

        public final int index;
        public int treeNode = INVALID;
        public final Rectangle bounds = new Rectangle();
        public T item;

        public LeafNode(int index) {
            this.index = index;
        }
    }

    private class TreeNode {

        public final int index;
        public int parent = INVALID;
        public int[] childs;
        public int depth, size;
        private final Rectangle bounds = new Rectangle();
        private final Set<Integer> leafs = new HashSet<>();

        public TreeNode(int index) {
            this.index = index;
        }

        //To be refactored into a generic template
        public void render(QuadTreeRenderer<T> renderer) {
            renderer.render(bounds, leafs.stream()
                    .map(leafPool::get)
                    .map(node -> node.item)
                    .collect(Collectors.toList()));
            if (childs != null) {
                for (int child : childs) {
                    treePool.get(child).render(renderer);
                }
            }
        }

        public void collectSelf(List<T> result, Rectangle aabb) {
            leafs.stream()
                    .map(leafPool::get)
                    .filter(node -> node.bounds.overlaps(aabb))
                    .forEach(node -> result.add(node.item));
        }

        public void collectAll(List<T> result, Rectangle aabb) {
            collectSelf(result, aabb);
            if (childs != null) {
                for (int child : childs) {
                    treePool.get(child).collectAll(result, aabb);
                }
            }
        }

        public int indexOf(Rectangle aabb) {
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
            return SELF;
        }

        public void split() {
            float halfWidth = bounds.width / 2;
            float halfHeight = bounds.height / 2;
            childs = new int[LAYER_SIZE];
            childs[NE] = obtainTreeNodeIndex(index, bounds.minX + halfWidth, bounds.minY + halfHeight, halfWidth, halfHeight, depth + 1).index;
            childs[NW] = obtainTreeNodeIndex(index, bounds.minX, bounds.minY + halfHeight, halfWidth, halfHeight, depth + 1).index;
            childs[SE] = obtainTreeNodeIndex(index, bounds.minX + halfWidth, bounds.minY, halfWidth, halfHeight, depth + 1).index;
            childs[SW] = obtainTreeNodeIndex(index, bounds.minX, bounds.minY, halfWidth, halfHeight, depth + 1).index;
        }
    }
}
