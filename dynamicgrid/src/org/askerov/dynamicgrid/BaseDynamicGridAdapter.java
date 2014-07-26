package org.askerov.dynamicgrid;

import android.content.Context;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: alex askerov
 * Date: 9/7/13
 * Time: 10:49 PM
 */
public abstract class BaseDynamicGridAdapter extends AbstractDynamicGridAdapter {
    private Context mContext;

    private ArrayList<Object> mItems = new ArrayList<Object>();
	private HashMap<Integer, Boolean> mVisMap = new HashMap<Integer, Boolean>();
    private int mColumnCount;

    protected BaseDynamicGridAdapter(Context context, int columnCount) {
        this.mContext = context;
        this.mColumnCount = columnCount;
    }

    public BaseDynamicGridAdapter(Context context, List<?> items, int columnCount) {
        mContext = context;
        mColumnCount = columnCount;
        init(items);
    }

    private void init(List<?> items) {
        addAllStableId(items);
        this.mItems.addAll(items);
    }


    public void set(List<?> items) {
        clear();
        init(items);
        notifyDataSetChanged();
    }

    public void clear() {
        clearStableIdMap();
        mItems.clear();
        notifyDataSetChanged();
    }

    public void add(Object item) {
        addStableId(item);
        mItems.add(item);
        notifyDataSetChanged();
    }

    public void add(int position, Object item) {
        addStableId(item);
        mItems.add(position, item);
        notifyDataSetChanged();
    }

    public void add(List<?> items) {
        addAllStableId(items);
        this.mItems.addAll(items);
        notifyDataSetChanged();
    }


    public void remove(Object item) {
        mItems.remove(item);
        removeStableID(item);
        notifyDataSetChanged();
    }

	/**
	 * This method should be called for every view about to be returned by 
	 * overridden {@link android.widget.Adapter#getView(int, android.view.View, android.view.ViewGroup)}
	 * 
	 * Check if there is an entry in visibility map for given position.
	 * If the entry exists - change view's visibility accordingly. 
	 * Otherwise view's visibility remains unchanged
	 * 
	 * @param position Position to search visibility map for
	 * @param view View to change the visibility of
	 */
	protected void setViewVisibilityOnPosition(int position, View view){
		Boolean visible = getViewVisibilityMap().get((position));
		if (visible != null) {
			view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
		}
	}

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public int getColumnCount() {
        return mColumnCount;
    }

    public void setColumnCount(int columnCount) {
        this.mColumnCount = columnCount;
        notifyDataSetChanged();
    }

    @Override
    public void reorderItems(int originalPosition, int newPosition) {
        if (newPosition < getCount()) {
            DynamicGridUtils.reorder(mItems, originalPosition, newPosition);
            notifyDataSetChanged();
        }
    }

	@Override
	public Map<Integer, Boolean> getViewVisibilityMap() {
		return mVisMap;
	}

    public List<Object> getItems() {
        return mItems;
    }

    protected Context getContext() {
        return mContext;
    }
}
