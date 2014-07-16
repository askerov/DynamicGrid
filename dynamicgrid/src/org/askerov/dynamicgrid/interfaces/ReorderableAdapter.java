package org.askerov.dynamicgrid.interfaces;

/**
 * @author dbachelder
 */
public interface ReorderableAdapter {
  /**
   * Determines how to reorder items dragged from <code>originalPosition</code> to <code>newPosition</code>
   */
  void reorderItems(int originalPosition, int newPosition);
}
