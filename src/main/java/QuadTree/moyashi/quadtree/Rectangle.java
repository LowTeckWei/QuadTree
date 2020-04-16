/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package QuadTree.moyashi.quadtree;

/**
 *
 * @author Low Teck Wei
 */
public class Rectangle {

    public float minX, minY, width, height;

    public Rectangle() {

    }

    public Rectangle(float minX, float minY, float width, float height) {
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
    }

    public void set(float minX, float minY, float width, float height) {
        this.minX = minX;
        this.minY = minY;
        this.width = width;
        this.height = height;
    }

    public boolean contains(Rectangle other) {
        return other.minX > minX
                && other.minX + other.width < minX + width
                && other.minY > minY
                && other.minY + other.height < minY + height;
    }

    public boolean overlaps(Rectangle other) {
        return other.minX < minX + width
                && other.minX + other.width > minX
                && other.minY < minY + height
                && other.minY + other.height > minY;
    }
}
