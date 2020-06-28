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
public interface Leaf {

    public void getMinimum(float[] minimum);

    public void getMaximum(float[] maximum);

    public boolean isStatic();
}
