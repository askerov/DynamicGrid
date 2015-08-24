package org.askerov.dynamicgrid;

import android.animation.*;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;

import java.util.*;

/**
 * Author: alex askerov
 * Date: 9/6/13
 * Time: 12:31 PM
 */
public class DynamicGridView extends GridView {
    private static final int INVALID_ID = -1;

    private static final int MOVE_DURATION = 300;
    private static final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 8;

    private BitmapDrawable mHoverCell;
    private Rect mHoverCellCurrentBounds;
    private Rect mHoverCellOriginalBounds;

    private int mTotalOffsetY = 0;
    private int mTotalOffsetX = 0;

    private int mDownX = -1;
    private int mDownY = -1;
    private int mLastEventY = -1;
    private int mLastEventX = -1;

    //used to distinguish straight line and diagonal switching
    private int mOverlapIfSwitchStraightLine;

    private List<Long> idList = new ArrayList<Long>();

    private long mMobileItemId = INVALID_ID;

    private boolean mCellIsMobile = false;
    private int mActivePointerId = INVALID_ID;

    private boolean mIsMobileScrolling;
    private int mSmoothScrollAmountAtEdge = 0;
    private boolean mIsWaitingForScrollFinish = false;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    private boolean mIsEditMode = false;
    private List<ObjectAnimator> mWobbleAnimators = new LinkedList<ObjectAnimator>();
    private boolean mHoverAnimation;
    private boolean mReorderAnimation;
    private boolean mWobbleInEditMode = true;
    private boolean mIsEditModeEnabled = true;

    private OnDropListener mDropListener;
    private OnDragListener mDragListener;
    private OnEditModeChangeListener mEditModeChangeListener;

    private OnItemClickListener mUserItemClickListener;
    private OnItemClickListener mLocalItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (!isEditMode() && isEnabled() && mUserItemClickListener != null) {
                mUserItemClickListener.onItemClick(parent, view, position, id);
            }
        }
    };

    private boolean mUndoSupportEnabled;
    private Stack<DynamicGridModification> mModificationStack;
    private DynamicGridModification mCurrentModification;

    private OnSelectedItemBitmapCreationListener mSelectedItemBitmapCreationListener;
    private View mMobileView;


    public DynamicGridView(Context context) {
        super(context);
        init(context);
    }

    public DynamicGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public DynamicGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void setOnDropListener(OnDropListener dropListener) {
        this.mDropListener = dropListener;
    }

    public void setOnDragListener(OnDragListener dragListener) {
        this.mDragListener = dragListener;
    }

    /**
     * Start edit mode without starting drag;
     */
    public void startEditMode() {
        startEditMode(-1);
    }

    /**
     * Start edit mode with position. Useful for start edit mode in
     * {@link android.widget.AdapterView.OnItemClickListener}
     * or {@link android.widget.AdapterView.OnItemLongClickListener}
     */
    public void startEditMode(int position) {
        if (!mIsEditModeEnabled)
            return;
        requestDisallowInterceptTouchEvent(true);
        if (isPostHoneycomb() && mWobbleInEditMode)
            startWobbleAnimation();
        if (position != -1 && mDragListener != null) {
            startDragAtPosition(position);
        }
        mIsEditMode = true;
        if (mEditModeChangeListener != null)
            mEditModeChangeListener.onEditModeChanged(true);
    }

    public void stopEditMode() {
        mIsEditMode = false;
        requestDisallowInterceptTouchEvent(false);
        if (isPostHoneycomb() && mWobbleInEditMode)
            stopWobble(true);
        if (mEditModeChangeListener != null)
            mEditModeChangeListener.onEditModeChanged(false);
    }

    public boolean isEditModeEnabled() {
        return mIsEditModeEnabled;
    }

    public void setEditModeEnabled(boolean enabled) {
        this.mIsEditModeEnabled = enabled;
    }

    public void setOnEditModeChangeListener(OnEditModeChangeListener editModeChangeListener) {
        this.mEditModeChangeListener = editModeChangeListener;
    }

    public boolean isEditMode() {
        return mIsEditMode;
    }

    public boolean isWobbleInEditMode() {
        return mWobbleInEditMode;
    }

    public void setWobbleInEditMode(boolean wobbleInEditMode) {
        this.mWobbleInEditMode = wobbleInEditMode;
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.mUserItemClickListener = listener;
        super.setOnItemClickListener(mLocalItemClickListener);
    }

    public boolean isUndoSupportEnabled() {
        return mUndoSupportEnabled;
    }

    public void setUndoSupportEnabled(boolean undoSupportEnabled) {
        if (this.mUndoSupportEnabled != undoSupportEnabled) {
            if (undoSupportEnabled) {
                this.mModificationStack = new Stack<DynamicGridModification>();
            } else {
                this.mModificationStack = null;
            }
        }

        this.mUndoSupportEnabled = undoSupportEnabled;
    }

    public void undoLastModification() {
        if (mUndoSupportEnabled) {
            if (mModificationStack != null && !mModificationStack.isEmpty()) {
                DynamicGridModification modification = mModificationStack.pop();
                undoModification(modification);
            }
        }
    }

    public void undoAllModifications() {
        if (mUndoSupportEnabled) {
            if (mModificationStack != null && !mModificationStack.isEmpty()) {
                while (!mModificationStack.isEmpty()) {
                    DynamicGridModification modification = mModificationStack.pop();
                    undoModification(modification);
                }
            }
        }
    }

    public boolean hasModificationHistory() {
        if (mUndoSupportEnabled) {
            if (mModificationStack != null && !mModificationStack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void clearModificationHistory() {
        mModificationStack.clear();
    }

    public void setOnSelectedItemBitmapCreationListener(OnSelectedItemBitmapCreationListener selectedItemBitmapCreationListener) {
        this.mSelectedItemBitmapCreationListener = selectedItemBitmapCreationListener;
    }

    private void undoModification(DynamicGridModification modification) {
        for (Pair<Integer, Integer> transition : modification.getTransitions()) {
            reorderElements(transition.second, transition.first);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void startWobbleAnimation() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v != null && Boolean.TRUE != v.getTag(R.id.dgv_wobble_tag)) {
                if (i % 2 == 0)
                    animateWobble(v);
                else
                    animateWobbleInverse(v);
                v.setTag(R.id.dgv_wobble_tag, true);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void stopWobble(boolean resetRotation) {
        for (Animator wobbleAnimator : mWobbleAnimators) {
            wobbleAnimator.cancel();
        }
        mWobbleAnimators.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v != null) {
                if (resetRotation) v.setRotation(0);
                v.setTag(R.id.dgv_wobble_tag, false);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void restartWobble() {
        stopWobble(false);
        startWobbleAnimation();
    }

    public void init(Context context) {
        setOnScrollListener(mScrollListener);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        mSmoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE * metrics.density + 0.5f);
        mOverlapIfSwitchStraightLine = getResources().getDimensionPixelSize(R.dimen.dgv_overlap_if_switch_straight_line);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateWobble(View v) {
        ObjectAnimator animator = createBaseWobble(v);
        animator.setFloatValues(-2, 2);
        animator.start();
        mWobbleAnimators.add(animator);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateWobbleInverse(View v) {
        ObjectAnimator animator = createBaseWobble(v);
        animator.setFloatValues(2, -2);
        animator.start();
        mWobbleAnimators.add(animator);
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private ObjectAnimator createBaseWobble(final View v) {

        if (!isPreL())
            v.setLayerType(LAYER_TYPE_SOFTWARE, null);

        ObjectAnimator animator = new ObjectAnimator();
        animator.setDuration(180);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setPropertyName("rotation");
        animator.setTarget(v);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setLayerType(LAYER_TYPE_NONE, null);
            }
        });
        return animator;
    }


    private void reorderElements(int originalPosition, int targetPosition) {
        if (mDragListener != null)
            mDragListener.onDragPositionsChanged(originalPosition, targetPosition);
        getAdapterInterface().reorderItems(originalPosition, targetPosition);
    }

    private int getColumnCount() {
        return getAdapterInterface().getColumnCount();
    }

    private DynamicGridAdapterInterface getAdapterInterface() {
        return ((DynamicGridAdapterInterface) getAdapter());
    }

    /**
     * Creates the hover cell with the appropriate bitmap and of appropriate
     * size. The hover cell's BitmapDrawable is drawn on top of the bitmap every
     * single time an invalidate call is made.
     */
    private BitmapDrawable getAndAddHoverView(View v) {

        int w = v.getWidth();
        int h = v.getHeight();
        int top = v.getTop();
        int left = v.getLeft();

        Bitmap b = getBitmapFromView(v);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), b);

        mHoverCellOriginalBounds = new Rect(left, top, left + w, top + h);
        mHoverCellCurrentBounds = new Rect(mHoverCellOriginalBounds);

        drawable.setBounds(mHoverCellCurrentBounds);

        return drawable;
    }

    /**
     * Returns a bitmap showing a screenshot of the view passed in.
     */
    private Bitmap getBitmapFromView(View v) {
        Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);
        return bitmap;
    }


    private void updateNeighborViewsForId(long itemId) {
        idList.clear();
        int draggedPos = getPositionForID(itemId);
        for (int pos = getFirstVisiblePosition(); pos <= getLastVisiblePosition(); pos++) {
            if (draggedPos != pos) {
                idList.add(getId(pos));
            }
        }
    }

    /**
     * Retrieves the position in the grid corresponding to <code>itemId</code>
     */
    public int getPositionForID(long itemId) {
        View v = getViewForId(itemId);
        if (v == null) {
            return -1;
        } else {
            return getPositionForView(v);
        }
    }

    public View getViewForId(long itemId) {
        int firstVisiblePosition = getFirstVisiblePosition();
        ListAdapter adapter = getAdapter();
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int position = firstVisiblePosition + i;
            long id = adapter.getItemId(position);
            if (id == itemId) {
                return v;
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mDownX = (int) event.getX();
                mDownY = (int) event.getY();
                mActivePointerId = event.getPointerId(0);
                if (mIsEditMode && isEnabled()) {
                    layoutChildren();
                    int position = pointToPosition(mDownX, mDownY);
                    startDragAtPosition(position);
                } else if (!isEnabled()) {
                    return false;
                }

                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_ID) {
                    break;
                }

                int pointerIndex = event.findPointerIndex(mActivePointerId);

                mLastEventY = (int) event.getY(pointerIndex);
                mLastEventX = (int) event.getX(pointerIndex);
                int deltaY = mLastEventY - mDownY;
                int deltaX = mLastEventX - mDownX;

                if (mCellIsMobile) {
                    mHoverCellCurrentBounds.offsetTo(mHoverCellOriginalBounds.left + deltaX + mTotalOffsetX,
                            mHoverCellOriginalBounds.top + deltaY + mTotalOffsetY);
                    mHoverCell.setBounds(mHoverCellCurrentBounds);
                    invalidate();
                    handleCellSwitch();
                    mIsMobileScrolling = false;
                    handleMobileCellScroll();
                    return false;
                }
                break;

            case MotionEvent.ACTION_UP:
                touchEventsEnded();

                if (mUndoSupportEnabled) {
                    if (mCurrentModification != null && !mCurrentModification.getTransitions().isEmpty()) {
                        mModificationStack.push(mCurrentModification);
                        mCurrentModification = new DynamicGridModification();
                    }
                }

                if (mHoverCell != null) {
                    if (mDropListener != null) {
                        mDropListener.onActionDrop();
                    }
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                touchEventsCancelled();

                if (mHoverCell != null) {
                    if (mDropListener != null) {
                        mDropListener.onActionDrop();
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                /* If a multitouch event took place and the original touch dictating
                 * the movement of the hover cell has ended, then the dragging event
                 * ends and the hover cell is animated to its corresponding position
                 * in the listview. */
                pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                final int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    touchEventsEnded();
                }
                break;

            default:
                break;
        }

        return super.onTouchEvent(event);
    }

    private void startDragAtPosition(int position) {
        mTotalOffsetY = 0;
        mTotalOffsetX = 0;
        int itemNum = position - getFirstVisiblePosition();
        View selectedView = getChildAt(itemNum);
        if (selectedView != null) {
            mMobileItemId = getAdapter().getItemId(position);
            if (mSelectedItemBitmapCreationListener != null)
                mSelectedItemBitmapCreationListener.onPreSelectedItemBitmapCreation(selectedView, position, mMobileItemId);
            mHoverCell = getAndAddHoverView(selectedView);
            if (mSelectedItemBitmapCreationListener != null)
                mSelectedItemBitmapCreationListener.onPostSelectedItemBitmapCreation(selectedView, position, mMobileItemId);
            if (isPostHoneycomb())
                selectedView.setVisibility(View.INVISIBLE);
            mCellIsMobile = true;
            updateNeighborViewsForId(mMobileItemId);
            if (mDragListener != null) {
                mDragListener.onDragStarted(position);
            }
        }
    }

    private void handleMobileCellScroll() {
        mIsMobileScrolling = handleMobileCellScroll(mHoverCellCurrentBounds);
    }

    public boolean handleMobileCellScroll(Rect r) {
        int offset = computeVerticalScrollOffset();
        int height = getHeight();
        int extent = computeVerticalScrollExtent();
        int range = computeVerticalScrollRange();
        int hoverViewTop = r.top;
        int hoverHeight = r.height();

        if (hoverViewTop <= 0 && offset > 0) {
            smoothScrollBy(-mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        if (hoverViewTop + hoverHeight >= height && (offset + extent) < range) {
            smoothScrollBy(mSmoothScrollAmountAtEdge, 0);
            return true;
        }

        return false;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
    }

    private void touchEventsEnded() {
        final View mobileView = getViewForId(mMobileItemId);
        if (mobileView != null && (mCellIsMobile || mIsWaitingForScrollFinish)) {
            mCellIsMobile = false;
            mIsWaitingForScrollFinish = false;
            mIsMobileScrolling = false;
            mActivePointerId = INVALID_ID;

            // If the autoscroller has not completed scrolling, we need to wait for it to
            // finish in order to determine the final location of where the hover cell
            // should be animated to.
            if (mScrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mIsWaitingForScrollFinish = true;
                return;
            }

            mHoverCellCurrentBounds.offsetTo(mobileView.getLeft(), mobileView.getTop());

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
                animateBounds(mobileView);
            } else {
                mHoverCell.setBounds(mHoverCellCurrentBounds);
                invalidate();
                reset(mobileView);
            }
        } else {
            touchEventsCancelled();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateBounds(final View mobileView) {
        TypeEvaluator<Rect> sBoundEvaluator = new TypeEvaluator<Rect>() {
            public Rect evaluate(float fraction, Rect startValue, Rect endValue) {
                return new Rect(interpolate(startValue.left, endValue.left, fraction),
                        interpolate(startValue.top, endValue.top, fraction),
                        interpolate(startValue.right, endValue.right, fraction),
                        interpolate(startValue.bottom, endValue.bottom, fraction));
            }

            public int interpolate(int start, int end, float fraction) {
                return (int) (start + fraction * (end - start));
            }
        };


        ObjectAnimator hoverViewAnimator = ObjectAnimator.ofObject(mHoverCell, "bounds",
                sBoundEvaluator, mHoverCellCurrentBounds);
        hoverViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                invalidate();
            }
        });
        hoverViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mHoverAnimation = true;
                updateEnableState();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mHoverAnimation = false;
                updateEnableState();
                reset(mobileView);
            }
        });
        hoverViewAnimator.start();
    }

    private void reset(View mobileView) {
        idList.clear();
        mMobileItemId = INVALID_ID;
        mobileView.setVisibility(View.VISIBLE);
        mHoverCell = null;
        if (isPostHoneycomb() && mWobbleInEditMode) {
            if (mIsEditMode) {
                restartWobble();
            } else{
                stopWobble(true);
            }
        }
        //ugly fix for unclear disappearing items after reorder
        for (int i = 0; i < getLastVisiblePosition() - getFirstVisiblePosition(); i++) {
            View child = getChildAt(i);
            if (child != null) {
                child.setVisibility(View.VISIBLE);
            }
        }
        invalidate();
    }

    private void updateEnableState() {
        setEnabled(!mHoverAnimation && !mReorderAnimation);
    }

    /**
     * Seems that GridView before HONEYCOMB not support stable id in proper way.
     * That cause bugs on view recycle if we will animate or change visibility state for items.
     *
     * @return
     */
    private boolean isPostHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    /**
     * The GridView from Android L requires some different setVisibility() logic
     * when switching cells. Unfortunately, both 4.4W and the pre-release L
     * report 20 for the SDK_INT, but we want to return true for 4.4W and false
     * for Android L. So, we check the release name for "L" if we see SDK 20.
     * Hopefully, Android L will actually be SDK 21 or later when it ships.
     *
     * @return
     */
    public static boolean isPreL() {
        final int KITKAT_WATCH = 20;
        return (Build.VERSION.SDK_INT < KITKAT_WATCH) ||
                ((Build.VERSION.SDK_INT == KITKAT_WATCH) && !"L".equals(Build.VERSION.RELEASE));
    }

    private void touchEventsCancelled() {
        View mobileView = getViewForId(mMobileItemId);
        if (mCellIsMobile) {
            reset(mobileView);
        }
        mCellIsMobile = false;
        mIsMobileScrolling = false;
        mActivePointerId = INVALID_ID;

    }

    private void handleCellSwitch() {
        final int deltaY = mLastEventY - mDownY;
        final int deltaX = mLastEventX - mDownX;
        final int deltaYTotal = mHoverCellOriginalBounds.centerY() + mTotalOffsetY + deltaY;
        final int deltaXTotal = mHoverCellOriginalBounds.centerX() + mTotalOffsetX + deltaX;
        mMobileView = getViewForId(mMobileItemId);
        View targetView = null;
        float vX = 0;
        float vY = 0;
        Point mobileColumnRowPair = getColumnAndRowForView(mMobileView);
        for (Long id : idList) {
            View view = getViewForId(id);
            if (view != null) {
                Point targetColumnRowPair = getColumnAndRowForView(view);
                if ((aboveRight(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal < view.getBottom() && deltaXTotal > view.getLeft()
                        || aboveLeft(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal < view.getBottom() && deltaXTotal < view.getRight()
                        || belowRight(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal > view.getTop() && deltaXTotal > view.getLeft()
                        || belowLeft(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal > view.getTop() && deltaXTotal < view.getRight()
                        || above(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal < view.getBottom() - mOverlapIfSwitchStraightLine
                        || below(targetColumnRowPair, mobileColumnRowPair)
                        && deltaYTotal > view.getTop() + mOverlapIfSwitchStraightLine
                        || right(targetColumnRowPair, mobileColumnRowPair)
                        && deltaXTotal > view.getLeft() + mOverlapIfSwitchStraightLine
                        || left(targetColumnRowPair, mobileColumnRowPair)
                        && deltaXTotal < view.getRight() - mOverlapIfSwitchStraightLine)) {
                    float xDiff = Math.abs(DynamicGridUtils.getViewX(view) - DynamicGridUtils.getViewX(mMobileView));
                    float yDiff = Math.abs(DynamicGridUtils.getViewY(view) - DynamicGridUtils.getViewY(mMobileView));
                    if (xDiff >= vX && yDiff >= vY) {
                        vX = xDiff;
                        vY = yDiff;
                        targetView = view;
                    }
                }
            }
        }
        if (targetView != null) {
            final int originalPosition = getPositionForView(mMobileView);
            int targetPosition = getPositionForView(targetView);

            if (targetPosition == INVALID_POSITION) {
                updateNeighborViewsForId(mMobileItemId);
                return;
            }
            reorderElements(originalPosition, targetPosition);

            if (mUndoSupportEnabled) {
                mCurrentModification.addTransition(originalPosition, targetPosition);
            }

            mDownY = mLastEventY;
            mDownX = mLastEventX;

            SwitchCellAnimator switchCellAnimator;

            if (isPostHoneycomb() && isPreL())   //Between Android 3.0 and Android L
                switchCellAnimator = new KitKatSwitchCellAnimator(deltaX, deltaY);
            else if (isPreL())                   //Before Android 3.0
                switchCellAnimator = new PreHoneycombCellAnimator(deltaX, deltaY);
            else                                //Android L
                switchCellAnimator = new LSwitchCellAnimator(deltaX, deltaY);

            updateNeighborViewsForId(mMobileItemId);

            switchCellAnimator.animateSwitchCell(originalPosition, targetPosition);
        }
    }

    private interface SwitchCellAnimator {
        void animateSwitchCell(final int originalPosition, final int targetPosition);
    }

    private class PreHoneycombCellAnimator implements SwitchCellAnimator {
        private int mDeltaY;
        private int mDeltaX;

        public PreHoneycombCellAnimator(int deltaX, int deltaY) {
            mDeltaX = deltaX;
            mDeltaY = deltaY;
        }

        @Override
        public void animateSwitchCell(int originalPosition, int targetPosition) {
            mTotalOffsetY += mDeltaY;
            mTotalOffsetX += mDeltaX;
        }
    }

    /**
     * A {@link org.askerov.dynamicgrid.DynamicGridView.SwitchCellAnimator} for versions KitKat and below.
     */
    private class KitKatSwitchCellAnimator implements SwitchCellAnimator {

        private int mDeltaY;
        private int mDeltaX;

        public KitKatSwitchCellAnimator(int deltaX, int deltaY) {
            mDeltaX = deltaX;
            mDeltaY = deltaY;
        }

        @Override
        public void animateSwitchCell(final int originalPosition, final int targetPosition) {
            assert mMobileView != null;
            getViewTreeObserver().addOnPreDrawListener(new AnimateSwitchViewOnPreDrawListener(mMobileView, originalPosition, targetPosition));
            mMobileView = getViewForId(mMobileItemId);
        }

        private class AnimateSwitchViewOnPreDrawListener implements ViewTreeObserver.OnPreDrawListener {

            private final View mPreviousMobileView;
            private final int mOriginalPosition;
            private final int mTargetPosition;

            AnimateSwitchViewOnPreDrawListener(final View previousMobileView, final int originalPosition, final int targetPosition) {
                mPreviousMobileView = previousMobileView;
                mOriginalPosition = originalPosition;
                mTargetPosition = targetPosition;
            }

            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);

                mTotalOffsetY += mDeltaY;
                mTotalOffsetX += mDeltaX;

                animateReorder(mOriginalPosition, mTargetPosition);

                mPreviousMobileView.setVisibility(View.VISIBLE);

                if (mMobileView != null) {
                    mMobileView.setVisibility(View.INVISIBLE);
                }
                return true;
            }
        }
    }

    /**
     * A {@link org.askerov.dynamicgrid.DynamicGridView.SwitchCellAnimator} for versions L and above.
     */
    private class LSwitchCellAnimator implements SwitchCellAnimator {

        private int mDeltaY;
        private int mDeltaX;

        public LSwitchCellAnimator(int deltaX, int deltaY) {
            mDeltaX = deltaX;
            mDeltaY = deltaY;
        }

        @Override
        public void animateSwitchCell(final int originalPosition, final int targetPosition) {
            getViewTreeObserver().addOnPreDrawListener(new AnimateSwitchViewOnPreDrawListener(originalPosition, targetPosition));
        }

        private class AnimateSwitchViewOnPreDrawListener implements ViewTreeObserver.OnPreDrawListener {
            private final int mOriginalPosition;
            private final int mTargetPosition;

            AnimateSwitchViewOnPreDrawListener(final int originalPosition, final int targetPosition) {
                mOriginalPosition = originalPosition;
                mTargetPosition = targetPosition;
            }

            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);

                mTotalOffsetY += mDeltaY;
                mTotalOffsetX += mDeltaX;

                animateReorder(mOriginalPosition, mTargetPosition);

                assert mMobileView != null;
                mMobileView.setVisibility(View.VISIBLE);
                mMobileView = getViewForId(mMobileItemId);
                assert mMobileView != null;
                mMobileView.setVisibility(View.INVISIBLE);
                return true;
            }
        }
    }

    private boolean belowLeft(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y > mobileColumnRowPair.y && targetColumnRowPair.x < mobileColumnRowPair.x;
    }

    private boolean belowRight(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y > mobileColumnRowPair.y && targetColumnRowPair.x > mobileColumnRowPair.x;
    }

    private boolean aboveLeft(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y < mobileColumnRowPair.y && targetColumnRowPair.x < mobileColumnRowPair.x;
    }

    private boolean aboveRight(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y < mobileColumnRowPair.y && targetColumnRowPair.x > mobileColumnRowPair.x;
    }

    private boolean above(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y < mobileColumnRowPair.y && targetColumnRowPair.x == mobileColumnRowPair.x;
    }

    private boolean below(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y > mobileColumnRowPair.y && targetColumnRowPair.x == mobileColumnRowPair.x;
    }

    private boolean right(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y == mobileColumnRowPair.y && targetColumnRowPair.x > mobileColumnRowPair.x;
    }

    private boolean left(Point targetColumnRowPair, Point mobileColumnRowPair) {
        return targetColumnRowPair.y == mobileColumnRowPair.y && targetColumnRowPair.x < mobileColumnRowPair.x;
    }

    private Point getColumnAndRowForView(View view) {
        int pos = getPositionForView(view);
        int columns = getColumnCount();
        int column = pos % columns;
        int row = pos / columns;
        return new Point(column, row);
    }

    private long getId(int position) {
        return getAdapter().getItemId(position);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void animateReorder(final int oldPosition, final int newPosition) {
        boolean isForward = newPosition > oldPosition;
        List<Animator> resultList = new LinkedList<Animator>();
        if (isForward) {
            for (int pos = Math.min(oldPosition, newPosition); pos < Math.max(oldPosition, newPosition); pos++) {
                View view = getViewForId(getId(pos));
                if ((pos + 1) % getColumnCount() == 0) {
                    resultList.add(createTranslationAnimations(view, -view.getWidth() * (getColumnCount() - 1), 0,
                            view.getHeight(), 0));
                } else {
                    resultList.add(createTranslationAnimations(view, view.getWidth(), 0, 0, 0));
                }
            }
        } else {
            for (int pos = Math.max(oldPosition, newPosition); pos > Math.min(oldPosition, newPosition); pos--) {
                View view = getViewForId(getId(pos));
                if ((pos + getColumnCount()) % getColumnCount() == 0) {
                    resultList.add(createTranslationAnimations(view, view.getWidth() * (getColumnCount() - 1), 0,
                            -view.getHeight(), 0));
                } else {
                    resultList.add(createTranslationAnimations(view, -view.getWidth(), 0, 0, 0));
                }
            }
        }

        AnimatorSet resultSet = new AnimatorSet();
        resultSet.playTogether(resultList);
        resultSet.setDuration(MOVE_DURATION);
        resultSet.setInterpolator(new AccelerateDecelerateInterpolator());
        resultSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mReorderAnimation = true;
                updateEnableState();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mReorderAnimation = false;
                updateEnableState();
            }
        });
        resultSet.start();
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private AnimatorSet createTranslationAnimations(View view, float startX, float endX, float startY, float endY) {
        ObjectAnimator animX = ObjectAnimator.ofFloat(view, "translationX", startX, endX);
        ObjectAnimator animY = ObjectAnimator.ofFloat(view, "translationY", startY, endY);
        AnimatorSet animSetXY = new AnimatorSet();
        animSetXY.playTogether(animX, animY);
        return animSetXY;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mHoverCell != null) {
            mHoverCell.draw(canvas);
        }
    }


    public interface OnDropListener {
        void onActionDrop();
    }

    public interface OnDragListener {

        public void onDragStarted(int position);

        public void onDragPositionsChanged(int oldPosition, int newPosition);
    }

    public interface OnEditModeChangeListener {
        public void onEditModeChanged(boolean inEditMode);
    }

    public interface OnSelectedItemBitmapCreationListener {
        public void onPreSelectedItemBitmapCreation(View selectedView, int position, long itemId);

        public void onPostSelectedItemBitmapCreation(View selectedView, int position, long itemId);
    }


    /**
     * This scroll listener is added to the gridview in order to handle cell swapping
     * when the cell is either at the top or bottom edge of the gridview. If the hover
     * cell is at either edge of the gridview, the gridview will begin scrolling. As
     * scrolling takes place, the gridview continuously checks if new cells became visible
     * and determines whether they are potential candidates for a cell swap.
     */
    private OnScrollListener mScrollListener = new OnScrollListener() {

        private int mPreviousFirstVisibleItem = -1;
        private int mPreviousVisibleItemCount = -1;
        private int mCurrentFirstVisibleItem;
        private int mCurrentVisibleItemCount;
        private int mCurrentScrollState;

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                             int totalItemCount) {
            mCurrentFirstVisibleItem = firstVisibleItem;
            mCurrentVisibleItemCount = visibleItemCount;

            mPreviousFirstVisibleItem = (mPreviousFirstVisibleItem == -1) ? mCurrentFirstVisibleItem
                    : mPreviousFirstVisibleItem;
            mPreviousVisibleItemCount = (mPreviousVisibleItemCount == -1) ? mCurrentVisibleItemCount
                    : mPreviousVisibleItemCount;

            checkAndHandleFirstVisibleCellChange();
            checkAndHandleLastVisibleCellChange();

            mPreviousFirstVisibleItem = mCurrentFirstVisibleItem;
            mPreviousVisibleItemCount = mCurrentVisibleItemCount;
            if (isPostHoneycomb() && mWobbleInEditMode) {
                updateWobbleState(visibleItemCount);
            }
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        private void updateWobbleState(int visibleItemCount) {
            for (int i = 0; i < visibleItemCount; i++) {
                View child = getChildAt(i);

                if (child != null) {
                    if (mMobileItemId != INVALID_ID && Boolean.TRUE != child.getTag(R.id.dgv_wobble_tag)) {
                        if (i % 2 == 0)
                            animateWobble(child);
                        else
                            animateWobbleInverse(child);
                        child.setTag(R.id.dgv_wobble_tag, true);
                    } else if (mMobileItemId == INVALID_ID && child.getRotation() != 0) {
                        child.setRotation(0);
                        child.setTag(R.id.dgv_wobble_tag, false);
                    }
                }

            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mCurrentScrollState = scrollState;
            mScrollState = scrollState;
            isScrollCompleted();
        }

        /**
         * This method is in charge of invoking 1 of 2 actions. Firstly, if the gridview
         * is in a state of scrolling invoked by the hover cell being outside the bounds
         * of the gridview, then this scrolling event is continued. Secondly, if the hover
         * cell has already been released, this invokes the animation for the hover cell
         * to return to its correct position after the gridview has entered an idle scroll
         * state.
         */
        private void isScrollCompleted() {
            if (mCurrentVisibleItemCount > 0 && mCurrentScrollState == SCROLL_STATE_IDLE) {
                if (mCellIsMobile && mIsMobileScrolling) {
                    handleMobileCellScroll();
                } else if (mIsWaitingForScrollFinish) {
                    touchEventsEnded();
                }
            }
        }

        /**
         * Determines if the gridview scrolled up enough to reveal a new cell at the
         * top of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleFirstVisibleCellChange() {
            if (mCurrentFirstVisibleItem != mPreviousFirstVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForId(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }

        /**
         * Determines if the gridview scrolled down enough to reveal a new cell at the
         * bottom of the list. If so, then the appropriate parameters are updated.
         */
        public void checkAndHandleLastVisibleCellChange() {
            int currentLastVisibleItem = mCurrentFirstVisibleItem + mCurrentVisibleItemCount;
            int previousLastVisibleItem = mPreviousFirstVisibleItem + mPreviousVisibleItemCount;
            if (currentLastVisibleItem != previousLastVisibleItem) {
                if (mCellIsMobile && mMobileItemId != INVALID_ID) {
                    updateNeighborViewsForId(mMobileItemId);
                    handleCellSwitch();
                }
            }
        }
    };

    private static class DynamicGridModification {

        private List<Pair<Integer, Integer>> transitions;

        DynamicGridModification() {
            super();
            this.transitions = new Stack<Pair<Integer, Integer>>();
        }

        public boolean hasTransitions() {
            return !transitions.isEmpty();
        }

        public void addTransition(int oldPosition, int newPosition) {
            transitions.add(new Pair<Integer, Integer>(oldPosition, newPosition));
        }

        public List<Pair<Integer, Integer>> getTransitions() {
            Collections.reverse(transitions);
            return transitions;
        }
    }

    /**
     * onScrollが呼ばれた際の処理（コールバックに渡す用）
     *
     * @param firstVisibleItem
     * @param visibleItemCount
     * @param totalItemCount
     */
    private void onDynamicScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (onDynamicScrollListener != null) {
            onDynamicScrollListener.onScroll(firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }

    // onScrollが呼ばれた際のコールバック
    private OnDynamicScrollListener onDynamicScrollListener;

    // onScrollが呼ばれた際のコールバック
    public interface OnDynamicScrollListener {
        public void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount);
    }

    /**
     * onScrollが呼び出された際のコールバックをセットする
     *
     * @param onDynamicScrollListener
     */
    public void setOnDynamicScrollListener(OnDynamicScrollListener onDynamicScrollListener) {
        this.onDynamicScrollListener = onDynamicScrollListener;
    }

    /**
     * onScrollのコールバックリスナーを削除する
     */
    public void removeOnDynamicScrollListener() {
        onDynamicScrollListener = null;
    }

    // １番目のセルを動かせるかどうか設定する（未整理プリの場合はfalseとする）
    private boolean canFirstCellMovable = true;

    /**
     * 最初のオブジェクトは移動できないかどうか
     *
     * @param movable true:最初のオブジェクトは移動出来るかどうか
     */
    public void setFirstCellMovable(boolean movable) {
        canFirstCellMovable = movable;
    }

    /**
     * 最初のオブジェクトは移動出来るかどうか
     *
     * @return true:移動出来ます | false:移動出来ない。
     */
    public boolean canFirstCellMovable(){
        return canFirstCellMovable;
    }


}