package org.askerov.dynamicgrid;

/**
 * Author: alex askerov
 * Date: 18/07/14
 * Time: 23:44
 */

import java.util.Map;

/**
 * Any adapter used with DynamicGridView must implement DynamicGridAdapterInterface.
 * Adapter implementation also must has stable items id.
 * See {@link org.askerov.dynamicgrid.AbstractDynamicGridAdapter} for stable id implementation example.
 */

public interface DynamicGridAdapterInterface {

    /**
     * Determines how to reorder items dragged from <code>originalPosition</code> to <code>newPosition</code>
     */
    void reorderItems(int originalPosition, int newPosition);

    /**
     * @return return columns number for GridView. Need for compatibility
     * (@link android.widget.GridView#getNumColumns() requires api 11)
     */
    int getColumnCount();

    /**
     * @return map of <position, visible> entries which describe if the view on certain position is visible.
     * If there is no entry for certain position, it is assumed that the view is visible
     */
    Map<Integer, Boolean> getViewVisibilityMap();
}
