/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moyashi.quadtree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 *
 * @author Low Teck Wei
 * @param <T>
 */
public class QuadTree<T extends Leaf> {

    private static final int SELF = -1, NE = 0, NW = 1, SE = 2, SW = 3;
    private static final int SUBTREE_SIZE = 4, REINSERT_THRESHOLD = 3;

    private final Queue<QuadNode<T>> nodePool = new ArrayDeque<>();
    private final Queue<Rectangle> rectanglePool = new ArrayDeque<>();
    private final int nodeCapacity;
    private final Map<T, Rectangle> aabbs = new HashMap<>();
    private final Map<T, QuadNode> nodes = new HashMap<>();

    private QuadNode root;

    public QuadTree(int nodeCapacity, float minX, float minY, float width, float height) {
        this.nodeCapacity = nodeCapacity;
        root = obtainQuadNode(minX, minY, width, height, 0);
    }

    public void resize(float minX, float minY, float width, float height) {
        root.empty();
        nodePool.offer(root);
        root = obtainQuadNode(minX, minY, width, height, 0);
        nodes.clear();
        aabbs.keySet().forEach(this::insert);
    }

    public void clear() {
        root.empty();
        if (root.subtrees != null) {
            for (QuadNode<T> node : root.subtrees) {
                nodePool.offer(node);
            }
        }
        aabbs.values().forEach(rectanglePool::offer);
        aabbs.clear();
        nodes.clear();
    }

    //Also functions as an update
    public void insert(T leaf) {
        Rectangle aabb = aabbs.get(leaf);
        if (aabb == null) {
            aabbs.put(leaf, aabb = obtainRectangle(leaf.getMinX(), leaf.getMinY(), leaf.getWidth(), leaf.getHeight()));
        } else {
            aabb.set(leaf.getMinX(), leaf.getMinY(), leaf.getWidth(), leaf.getHeight());
        }

        QuadNode<T> node = nodes.get(leaf);
        if (node != null) {
            if (node.depth > REINSERT_THRESHOLD && node.bounds.contains(aabb)) {
                int index = node.indexOf(aabb);
                if (index != SELF) {
                    node.leafs.remove(leaf);
                    insert(node.subtrees[index], aabb, leaf);
                }
                return;
            }
            node.leafs.remove(leaf);
        }

        insert(root, aabb, leaf);
    }

    public void remove(T leaf) {
        aabbs.remove(leaf);
        QuadNode<T> node = nodes.remove(leaf);
        if (node != null) {
            node.leafs.remove(leaf);
        }
    }

    public List<T> search(List<T> result, float minX, float minY, float width, float height) {
        Rectangle aabb = obtainRectangle(minX, minY, width, height);

        QuadNode<T> node = root;
        while (node != null) {
            int index = node.indexOf(aabb);
            if (index == SELF) {
                break;
            }
            node = node.subtrees == null ? null : node.subtrees[index];
        }

        if (node != null) {
            node.collect(result, aabb);
        }

        rectanglePool.offer(aabb);

        return result;
    }

    public void render(QuadTreeRenderer renderer) {
        root.render(renderer);
    }

    private void insert(QuadNode<T> node, Rectangle aabb, T leaf) {
        while (node.subtrees != null) {
            int index = node.indexOf(aabb);
            if (index == SELF) {
                break;
            }
            node = node.subtrees[index];
        }

        node.leafs.add(leaf);
        nodes.put(leaf, node);

        if (node.subtrees == null && node.leafs.size() > nodeCapacity) {
            node.split();
            for (Iterator<T> it = node.leafs.iterator(); it.hasNext();) {
                T t = it.next();
                Rectangle r = aabbs.get(t);
                int index = node.indexOf(r);
                if (index != SELF) {
                    it.remove();
                    insert(node.subtrees[index], r, t);
                }
            }
        }
    }

    private QuadNode<T> obtainQuadNode(float minX, float minY, float width, float height, int depth) {
        QuadNode<T> node = nodePool.poll();
        if (node == null) {
            node = new QuadNode<>(minX, minY, width, height, depth);
        } else {
            node.bounds.set(minX, minY, width, height);
            if (node.subtrees != null) {
                for (QuadNode<T> subtree : node.subtrees) {
                    nodePool.offer(subtree);
                }
                node.subtrees = null;
            }
        }
        return node;
    }

    private Rectangle obtainRectangle(float minX, float minY, float width, float height) {
        Rectangle rect = rectanglePool.poll();
        if (rect == null) {
            return new Rectangle(minX, minY, width, height);
        }
        rect.set(minX, minY, width, height);
        return rect;
    }

    private class QuadNode<T> {

        public int depth;
        public final Rectangle bounds;
        public QuadNode[] subtrees;
        public final List<T> leafs = new ArrayList<>(nodeCapacity);

        public QuadNode(float minX, float minY, float width, float height, int depth) {
            bounds = obtainRectangle(minX, minY, width, height);
            this.depth = depth;
        }

        public void render(QuadTreeRenderer renderer) {
            renderer.render(bounds);
            if (subtrees != null) {
                for (QuadNode node : subtrees) {
                    node.render(renderer);
                }
            }
        }

        @SuppressWarnings("element-type-mismatch") //This is impossible
        public void collect(List<T> result, Rectangle aabb) {
            for (T t : leafs) {
                if (aabbs.get(t).overlaps(aabb)) {
                    result.add(t);
                }
            }
            if (subtrees != null) {
                for (QuadNode node : subtrees) {
                    node.collect(result, aabb);
                }
            }
        }

        //Important note: Clear leafs without changing any meta.
        @SuppressWarnings("element-type-mismatch") //This is impossible
        public void empty() {
            for (T t : leafs) {
                aabbs.remove(t);
            }
            leafs.clear();
            if (subtrees != null) {
                for (QuadNode node : subtrees) {
                    node.empty();
                    nodePool.offer(node);
                }
            }
        }

        public void split() {
            float halfWidth = bounds.width / 2;
            float halfHeight = bounds.height / 2;

            subtrees = new QuadNode[SUBTREE_SIZE];
            subtrees[NE] = obtainQuadNode(bounds.minX + halfWidth, bounds.minY + halfHeight, halfWidth, halfHeight, depth + 1);
            subtrees[NW] = obtainQuadNode(bounds.minX, bounds.minY + halfHeight, halfWidth, halfHeight, depth + 1);
            subtrees[SE] = obtainQuadNode(bounds.minX + halfWidth, bounds.minY, halfWidth, halfHeight, depth + 1);
            subtrees[SW] = obtainQuadNode(bounds.minX, bounds.minY, halfWidth, halfHeight, depth + 1);
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
    }
}
