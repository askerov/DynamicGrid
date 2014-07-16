package org.askerov.dynamicgrid.interfaces;

/**
 * @author dbachelder
 */
public interface ColumnarAdapter {
  /**
   * @return return columns number for GridView. Need for compatibility
   * (@link android.widget.GridView#getNumColumns() requires api 11)
   */
  int getColumnCount();
}
