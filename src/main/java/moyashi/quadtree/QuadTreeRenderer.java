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
public interface QuadTreeRenderer<T extends Leaf> {
    
    public void render(Rectangle bounds, List<T> unmodifiableItems);
}
