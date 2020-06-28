/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moyashi.util;

/**
 *
 * @author Low Teck Wei
 */
public interface SpatialTreeVisitor<T> {

    public void visitTree(float[] minimum, float[] maximum);

    public void visitLeaf(T leaf, float[] minimum, float[] maximum);
}
