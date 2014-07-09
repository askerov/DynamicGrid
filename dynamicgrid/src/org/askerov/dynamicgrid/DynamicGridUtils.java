package org.askerov.dynamicgrid;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: alex askerov
 * Date: 9/7/13
 * Time: 10:14 PM
 */
public class DynamicGridUtils {
    /**
     * Delete item in <code>list</code> from position <code>indexFrom</code> and insert it to <code>indexTwo</code>
     * @param <T>
     *
     * @param list
     * @param indexFrom
     * @param indexTwo
     */
    public static <T> void reorder(List<T> list, int indexFrom, int indexTwo) {
        T obj = list.remove(indexFrom);
        list.add(indexTwo, obj);
    }

    /**
     * Swap item in <code>list</code> at position <code>firstIndex</code> with item at position <code>secondIndex</code>
     * @param <T>
     *
     * @param list The list in which to swap the items.
     * @param firstIndex The position of the first item in the list.
     * @param secondIndex The position of the second item in the list.
     */
    public static <T> void swap(ArrayList<T> list, int firstIndex, int secondIndex) {
        T firstObject = list.get(firstIndex);
        T secondObject = list.get(secondIndex);
        list.set(firstIndex, secondObject);
        list.set(secondIndex, firstObject);
    }

    public static float getViewX(View view) {
        return Math.abs((view.getRight() - view.getLeft()) / 2);
    }

    public static float getViewY(View view) {
        return Math.abs((view.getBottom() - view.getTop()) / 2);
    }
}
