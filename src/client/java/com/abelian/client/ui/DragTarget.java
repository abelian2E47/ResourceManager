package com.abelian.client.ui;

/** A widget that is being dragged with the left mouse button; the screen forwards drag events to it. */
public interface DragTarget {
    boolean isDragging();

    void dragTo(double mouseX, double mouseY);

    void endDrag();
}
