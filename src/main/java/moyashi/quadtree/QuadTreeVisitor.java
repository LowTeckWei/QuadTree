/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moyashi.quadtree;

import java.util.List;

/**
 *
 * @author Low Teck Wei
 * @param <T>
 */
public interface QuadTreeVisitor<T extends Leaf> {

    //All parameters are temporary and modifiable, it has no effect on existing state of the tree.
    public void visit(Rectangle bounds, List<T> items);
}
