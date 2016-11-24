package demo.xuqingqi.leftdrawerlayout.drawer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.ColorInt;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.KeyEventCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewGroupCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class LeftDrawerLayout extends ViewGroup {

    private static final String TAG = "LeftDrawerLayout";

    @IntDef({STATE_IDLE, STATE_DRAGGING, STATE_SETTLING})
    @Retention(RetentionPolicy.SOURCE)
    private @interface State {}

    public static final int STATE_IDLE = DrawerDragHelper.STATE_IDLE;

    public static final int STATE_DRAGGING = DrawerDragHelper.STATE_DRAGGING;

    public static final int STATE_SETTLING = DrawerDragHelper.STATE_SETTLING;

    private static final int MIN_DRAWER_MARGIN = 64; // dp

    private static final int DEFAULT_SCRIM_COLOR = 0x99000000;

    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    private static final boolean CHILDREN_DISALLOW_INTERCEPT = true;

    private static final float TOUCH_SLOP_SENSITIVITY = 1.f;

    public static final float DRAWER_ON_SCREEN = 0.5f;

    public static final int CONTENT_OFFSET_FACTOR = 2; // 1/DRAWER_ON_SCREEN

    private static final int[] LAYOUT_ATTRS = new int[] {
            android.R.attr.layout_gravity
    };

    /** Whether we can use NO_HIDE_DESCENDANTS accessibility importance. */
    private static final boolean CAN_HIDE_DESCENDANTS = Build.VERSION.SDK_INT >= 19;

    private final ChildAccessibilityDelegate mChildAccessibilityDelegate =
            new ChildAccessibilityDelegate();

    private int mMinDrawerMargin;

    private int mScrimColor = DEFAULT_SCRIM_COLOR;
    private float mScrimOpacity;
    private Paint mScrimPaint = new Paint();

    private final DrawerDragHelper mLeftDragger;
    private final ViewDragCallback mLeftCallback;
    private int mDrawerState;
    private boolean mInLayout;
    private boolean mFirstLayout = true;

    private boolean mDisallowInterceptRequested;
    private boolean mChildrenCanceledTouch;

    private @Nullable
    DrawerListener mListener;
    private List<DrawerListener> mListeners;

    private float mInitialMotionX;
    private float mInitialMotionY;

    private final ArrayList<View> mNonDrawerViews;

    /**
     * Listener for monitoring events about drawers.
     */
    public interface DrawerListener {

        void onDrawerSlide(View drawerView, float slideOffset);

        void onDrawerOpened(View drawerView);

        void onDrawerClosed(View drawerView);

        void onDrawerStateChanged(@State int newState);
    }


    public static abstract class SimpleDrawerListener implements DrawerListener {

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
        }

        @Override
        public void onDrawerOpened(View drawerView) {
        }

        @Override
        public void onDrawerClosed(View drawerView) {
        }

        @Override
        public void onDrawerStateChanged(int newState) {
        }
    }

    public LeftDrawerLayout(Context context) {
        this(context, null);
    }

    public LeftDrawerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LeftDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        final float density = getResources().getDisplayMetrics().density;
        mMinDrawerMargin = (int) (MIN_DRAWER_MARGIN * density + 0.5f);
        final float minVel = MIN_FLING_VELOCITY * density;

        mLeftCallback = new ViewDragCallback();

        mLeftDragger = DrawerDragHelper.create(this, TOUCH_SLOP_SENSITIVITY, mLeftCallback);
        mLeftDragger.setEdgeTrackingEnabled(DrawerDragHelper.EDGE_LEFT);
        mLeftDragger.setMinVelocity(minVel);
        mLeftCallback.setDragger(mLeftDragger);

        // So that we can catch the back button
        setFocusableInTouchMode(true);

        ViewCompat.setImportantForAccessibility(this,
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);

        ViewCompat.setAccessibilityDelegate(this, new AccessibilityDelegate());
        ViewGroupCompat.setMotionEventSplittingEnabled(this, false);

        mNonDrawerViews = new ArrayList<>();
    }

    public void setScrimColor(@ColorInt int color) {
        mScrimColor = color;
        invalidate();
    }

    @Deprecated
    public void setDrawerListener(DrawerListener listener) {
        // The logic in this method emulates what we had before support for multiple
        // registered listeners.
        if (mListener != null) {
            removeDrawerListener(mListener);
        }
        if (listener != null) {
            addDrawerListener(listener);
        }
        // Update the deprecated field so that we can remove the passed listener the next
        // time we're called
        mListener = listener;
    }

    public void addDrawerListener(@NonNull DrawerListener listener) {
        if (listener == null) {
            return;
        }
        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }
        mListeners.add(listener);
    }

    public void removeDrawerListener(@NonNull DrawerListener listener) {
        if (listener == null) {
            return;
        }
        if (mListeners == null) {
            // This can happen if this method is called before the first call to addDrawerListener
            return;
        }
        mListeners.remove(listener);
    }

    void updateDrawerState(@State int activeState, View activeDrawer) {
        final int leftState = mLeftDragger.getViewDragState();

        final int state;
        if (leftState == STATE_DRAGGING ) {
            state = STATE_DRAGGING;
        } else if (leftState == STATE_SETTLING) {
            state = STATE_SETTLING;
        } else {
            state = STATE_IDLE;
        }

        if (activeDrawer != null && activeState == STATE_IDLE) {
            final LayoutParams lp = (LayoutParams) activeDrawer.getLayoutParams();
            if (lp.onScreen <= DRAWER_ON_SCREEN) {
                dispatchOnDrawerClosed(activeDrawer);
            } else if (lp.onScreen >= 1) {
                dispatchOnDrawerOpened(activeDrawer);
            }
        }

        if (state != mDrawerState) {
            mDrawerState = state;

            if (mListeners != null) {
                // Notify the listeners. Do that from the end of the list so that if a listener
                // removes itself as the result of being called, it won't mess up with our iteration
                int listenerCount = mListeners.size();
                for (int i = listenerCount - 1; i >= 0; i--) {
                    mListeners.get(i).onDrawerStateChanged(state);
                }
            }
        }
    }

    void dispatchOnDrawerClosed(View drawerView) {
        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if ((lp.openState & LayoutParams.FLAG_IS_OPENED) == 1) {
            lp.openState = 0;

            if (mListeners != null) {
                // Notify the listeners. Do that from the end of the list so that if a listener
                // removes itself as the result of being called, it won't mess up with our iteration
                int listenerCount = mListeners.size();
                for (int i = listenerCount - 1; i >= 0; i--) {
                    mListeners.get(i).onDrawerClosed(drawerView);
                }
            }

            updateChildrenImportantForAccessibility(drawerView, false);

            // Only send WINDOW_STATE_CHANGE if the host has window focus. This
            // may change if support for multiple foreground windows (e.g. IME)
            // improves.
            if (hasWindowFocus()) {
                final View rootView = getRootView();
                if (rootView != null) {
                    rootView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                }
            }
        }
    }

    void dispatchOnDrawerOpened(View drawerView) {
        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if ((lp.openState & LayoutParams.FLAG_IS_OPENED) == 0) {
            lp.openState = LayoutParams.FLAG_IS_OPENED;
            if (mListeners != null) {
                // Notify the listeners. Do that from the end of the list so that if a listener
                // removes itself as the result of being called, it won't mess up with our iteration
                int listenerCount = mListeners.size();
                for (int i = listenerCount - 1; i >= 0; i--) {
                    mListeners.get(i).onDrawerOpened(drawerView);
                }
            }

            updateChildrenImportantForAccessibility(drawerView, true);

            // Only send WINDOW_STATE_CHANGE if the host has window focus.
            if (hasWindowFocus()) {
                sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            }

            drawerView.requestFocus();
        }
    }

    private void updateChildrenImportantForAccessibility(View drawerView, boolean isDrawerOpen) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (!isDrawerOpen && !isDrawerView(child)
                    || isDrawerOpen && child == drawerView) {
                // Drawer is closed and this is a content view or this is an
                // open drawer view, so it should be visible.
                ViewCompat.setImportantForAccessibility(child,
                        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
            } else {
                ViewCompat.setImportantForAccessibility(child,
                        ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
    }

    void dispatchOnDrawerSlide(View drawerView, float slideOffset) {
        if (mListeners != null) {
            // Notify the listeners. Do that from the end of the list so that if a listener
            // removes itself as the result of being called, it won't mess up with our iteration
            int listenerCount = mListeners.size();
            for (int i = listenerCount - 1; i >= 0; i--) {
                mListeners.get(i).onDrawerSlide(drawerView, slideOffset);
            }
        }
    }

    void setDrawerViewOffset(View drawerView, float slideOffset) {
        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (slideOffset == lp.onScreen) {
            return;
        }

        if (slideOffset < DRAWER_ON_SCREEN) {
            slideOffset = DRAWER_ON_SCREEN;
        }
        if (slideOffset > 1.0f) {
            slideOffset = 1.0f;
        }
        lp.onScreen = slideOffset;
        dispatchOnDrawerSlide(drawerView, slideOffset);
    }

    float getDrawerViewOffset(View drawerView) {
        if (drawerView == null) {
            return 0;
        }
        return ((LayoutParams) drawerView.getLayoutParams()).onScreen;
    }

    View findOpenDrawer() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams childLp = (LayoutParams) child.getLayoutParams();
            if ((childLp.openState & LayoutParams.FLAG_IS_OPENED) == 1) {
                return child;
            }
        }
        return null;
    }

    void moveDrawerToOffset(boolean start) {
        View drawerView = findDrawer();
        View contentView = findContent();
        if (drawerView == null || contentView == null) {
            return;
        }

        int drawerLeft = drawerView.getLeft();
        int contentLeft = contentView.getLeft();
        int drawerTarget;
        int contentTarget;

        if (start) {
            drawerTarget = (int) (drawerView.getWidth() * DRAWER_ON_SCREEN) - drawerView.getWidth();
            contentTarget = 0;
        } else {
            drawerTarget = 0;
            contentTarget = drawerView.getWidth();
        }

        drawerView.offsetLeftAndRight(drawerTarget - drawerLeft);
        contentView.offsetLeftAndRight(contentTarget - contentLeft);

        if (start) {
            setDrawerViewOffset(drawerView, DRAWER_ON_SCREEN);
        } else {
            setDrawerViewOffset(drawerView, 1.0f);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY || heightMode != MeasureSpec.EXACTLY) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Consume all of the space if specified
                // or pick a magic number from thin air otherwise.
                // TODO Better communication with tools of this bogus state.
                // It will crash on a real device.
                if (widthMode == MeasureSpec.AT_MOST) {
                    widthMode = MeasureSpec.EXACTLY;
                } else if (widthMode == MeasureSpec.UNSPECIFIED) {
                    widthMode = MeasureSpec.EXACTLY;
                    widthSize = 300;
                }
                if (heightMode == MeasureSpec.AT_MOST) {
                    heightMode = MeasureSpec.EXACTLY;
                }
                else if (heightMode == MeasureSpec.UNSPECIFIED) {
                    heightMode = MeasureSpec.EXACTLY;
                    heightSize = 300;
                }
            } else {
                throw new IllegalArgumentException(
                        "DrawerLayout must be measured with MeasureSpec.EXACTLY.");
            }
        }

        setMeasuredDimension(widthSize, heightSize);

        final int layoutDirection = ViewCompat.getLayoutDirection(this);

        // Only one drawer is permitted along each vertical edge (left / right). These two booleans
        // are tracking the presence of the edge drawers.
        boolean hasDrawerOnLeftEdge = false;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (isContentView(child)) {
                // Content views get measured at exactly the layout's size.
                final int contentWidthSpec = MeasureSpec.makeMeasureSpec(
                        widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                final int contentHeightSpec = MeasureSpec.makeMeasureSpec(
                        heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
            } else if (isDrawerView(child)) {
                if (hasDrawerOnLeftEdge) {
                    throw new IllegalStateException("Child drawer has absolute gravity " +
                           "LEFT" + " but this " + TAG + " already has a " +
                            "drawer view along that edge");
                }
                hasDrawerOnLeftEdge = true;
                final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec,
                        mMinDrawerMargin + lp.leftMargin + lp.rightMargin,
                        lp.width);
                final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                        lp.topMargin + lp.bottomMargin,
                        lp.height);
                child.measure(drawerWidthSpec, drawerHeightSpec);
            } else {
                throw new IllegalStateException("Child " + child + " at index " + i +
                        " does not have a valid layout_gravity - must be Gravity.LEFT, " +
                        "Gravity.RIGHT or Gravity.NO_GRAVITY");
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout = true;

        int drawerOffset = 0;

        View drawer = findDrawer();
        if (drawer != null) {
            final LayoutParams lp = (LayoutParams) drawer.getLayoutParams();
            final int childWidth = drawer.getMeasuredWidth();
            final int childHeight = drawer.getMeasuredHeight();
            int childLeft;
            if (lp.onScreen > 1) {
                childLeft = 0;
            } else {
                childLeft = -childWidth + (int) (childWidth * lp.onScreen);
            }
            final float newOffset = (float) (childWidth + childLeft) / childWidth;
            final boolean changeOffset = newOffset != lp.onScreen;

            drawer.layout(childLeft, lp.topMargin, childLeft + childWidth,
                    lp.topMargin + childHeight);

            if (changeOffset) {
                setDrawerViewOffset(drawer, newOffset);
            }
            drawerOffset = (int) (childWidth * lp.onScreen) - (int) (childWidth * DRAWER_ON_SCREEN);
        }

        View content = findContent();
        if (content != null) {
            final LayoutParams lp = (LayoutParams) content.getLayoutParams();

            int contentOffset = drawerOffset * CONTENT_OFFSET_FACTOR;
            content.layout(contentOffset + lp.leftMargin, lp.topMargin,
                    contentOffset + lp.leftMargin + content.getMeasuredWidth(),
                    lp.topMargin + content.getMeasuredHeight());
        }

        mInLayout = false;
        mFirstLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    @Override
    public void computeScroll() {
        final int childCount = getChildCount();
        float scrimOpacity = 0;
        for (int i = 0; i < childCount; i++) {
            final float onscreen = ((LayoutParams) getChildAt(i).getLayoutParams()).onScreen;
            scrimOpacity = Math.max(scrimOpacity, onscreen);
        }
        mScrimOpacity = scrimOpacity;

        if (mLeftDragger.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final boolean drawingContent = isContentView(child);
        int clipLeft = child.getLeft();
        int clipRight = getWidth();
        if (!drawingContent) {
            View contentView = findContent();
            if (contentView != null) {
                clipRight = contentView.getLeft();
            }
        }

        final int restoreCount = canvas.save();
        canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (mScrimOpacity > DRAWER_ON_SCREEN && drawingContent) {
            final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
            final int imag = (int) (baseAlpha * (mScrimOpacity - DRAWER_ON_SCREEN));
            final int color = imag << 24 | (mScrimColor & 0xffffff);
            mScrimPaint.setColor(color);

            canvas.drawRect(clipLeft, 0, clipRight, getHeight(), mScrimPaint);
        }

        return result;
    }

    boolean isContentView(View child) {
        return child instanceof IContentView;
    }

    boolean isDrawerView(View child) {
        return child instanceof IDrawerView;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        final boolean interceptForDrag = mLeftDragger.shouldInterceptTouchEvent(ev);

        boolean interceptForTap = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;
                if (mScrimOpacity > 0) {
                    final View child = findTopChildUnder((int) x, (int) y);
                    if (child != null && isContentView(child)) {
                        //interceptForTap = true;
                    }
                }
                mDisallowInterceptRequested = false;
                mChildrenCanceledTouch = false;
                break;
            }

            case MotionEvent.ACTION_MOVE: {

                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                mDisallowInterceptRequested = false;
                mChildrenCanceledTouch = false;
            }
        }

        //return true;
        return interceptForDrag || interceptForTap || mChildrenCanceledTouch;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mLeftDragger.processTouchEvent(ev);

        final int action = ev.getAction();
        boolean wantTouchEvents = true;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mInitialMotionX = x;
                mInitialMotionY = y;
                mDisallowInterceptRequested = false;
                mChildrenCanceledTouch = false;
                break;
            }

            case MotionEvent.ACTION_UP: {
                //closeDrawer(true);
                mDisallowInterceptRequested = false;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mDisallowInterceptRequested = false;
                mChildrenCanceledTouch = false;
                break;
            }
        }

        return wantTouchEvents;
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (CHILDREN_DISALLOW_INTERCEPT ||
                (!mLeftDragger.isEdgeTouched(DrawerDragHelper.EDGE_LEFT))) {
            // If we have an edge touch we want to skip this and track it for later instead.
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
        mDisallowInterceptRequested = disallowIntercept;
        if (disallowIntercept) {
            closeDrawer(true);
        }
    }

    /**
     * Open the specified drawer view by animating it into view.
     *
     * @param drawerView Drawer view to open
     */
    public void openDrawer(View drawerView) {
        openDrawer(drawerView, true);
    }

    /**
     * Open the specified drawer view.
     *
     * @param drawerView Drawer view to open
     * @param animate Whether opening of the drawer should be animated.
     */
    public void openDrawer(View drawerView, boolean animate) {
        if (!isDrawerView(drawerView)) {
            throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
        }

        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (mFirstLayout) {
            lp.onScreen = 1.f;
            lp.openState = LayoutParams.FLAG_IS_OPENED;

            updateChildrenImportantForAccessibility(drawerView, true);
        } else if (animate) {
            lp.openState |= LayoutParams.FLAG_IS_OPENING;

            int offset = drawerView.getWidth();
            mLeftDragger.smoothSlideViewTo(offset, drawerView.getTop());
        } else {
            moveDrawerToOffset(false);
            updateDrawerState(STATE_IDLE, drawerView);
        }
        invalidate();
    }


    public void openDrawer() {
        openDrawer(true);
    }

    public void openDrawer(boolean animate) {
        final View drawerView = findDrawer();
        if (drawerView == null) {
            throw new IllegalArgumentException("No drawer view found with ");
        }
        openDrawer(drawerView, animate);
    }

    public void closeDrawer(View drawerView) {
        closeDrawer(drawerView, true);
    }

    public void closeDrawer(View drawerView, boolean animate) {
        if (!isDrawerView(drawerView)) {
            throw new IllegalArgumentException("View " + drawerView + " is not a sliding drawer");
        }

        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (mFirstLayout) {
            lp.onScreen = DRAWER_ON_SCREEN;
            lp.openState = 0;
        } else if (animate) {
            lp.openState |= LayoutParams.FLAG_IS_CLOSING;

            mLeftDragger.smoothSlideViewTo(0, drawerView.getTop());
        } else {
            moveDrawerToOffset(true);
            updateDrawerState(STATE_IDLE, drawerView);
        }
        invalidate();
    }

    public void closeDrawer() {
        closeDrawer(true);
    }

    public void closeDrawer(boolean animate) {
        final View drawerView = findDrawer();
        if (drawerView == null) {
            throw new IllegalArgumentException("No drawer view found with ");
        }
        closeDrawer(drawerView, animate);
    }

    public boolean isDrawerOpen(View drawer) {
        if (!isDrawerView(drawer)) {
            throw new IllegalArgumentException("View " + drawer + " is not a drawer");
        }
        LayoutParams drawerLp = (LayoutParams) drawer.getLayoutParams();
        return (drawerLp.openState & LayoutParams.FLAG_IS_OPENED) == 1;
    }

    public boolean isDrawerOpen() {
        final View drawerView = findDrawer();
        if (drawerView != null) {
            return isDrawerOpen(drawerView);
        }
        return false;
    }

    public boolean isDrawerVisible(View drawer) {
        if (!isDrawerView(drawer)) {
            throw new IllegalArgumentException("View " + drawer + " is not a drawer");
        }
        return ((LayoutParams) drawer.getLayoutParams()).onScreen > DRAWER_ON_SCREEN;
    }

    public boolean isDrawerVisible() {
        final View drawerView = findDrawer();
        if (drawerView != null) {
            return isDrawerVisible(drawerView);
        }
        return false;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams
                ? new LayoutParams((LayoutParams) p)
                : p instanceof ViewGroup.MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS) {
            return;
        }

        // Only the views in the open drawers are focusables. Add normal child views when
        // no drawers are opened.
        final int childCount = getChildCount();
        boolean isDrawerOpen = false;
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (isDrawerView(child)) {
                if (isDrawerOpen(child)) {
                    isDrawerOpen = true;
                    child.addFocusables(views, direction, focusableMode);
                }
            } else {
                mNonDrawerViews.add(child);
            }
        }

        if (!isDrawerOpen) {
            final int nonDrawerViewsCount = mNonDrawerViews.size();
            for (int i = 0; i < nonDrawerViewsCount; ++i) {
                final View child = mNonDrawerViews.get(i);
                if (child.getVisibility() == View.VISIBLE) {
                    child.addFocusables(views, direction, focusableMode);
                }
            }
        }

        mNonDrawerViews.clear();
    }

    private boolean hasVisibleDrawer() {
        return findVisibleDrawer() != null;
    }

    private View findVisibleDrawer() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (isDrawerView(child) && isDrawerVisible(child)) {
                return child;
            }
        }
        return null;
    }

    public View findDrawer() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (isDrawerView(child)) {
                return child;
            }
        }
        return null;
    }

    public View findContent() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (isContentView(child)) {
                return child;
            }
        }
        return null;
    }

    void cancelChildViewTouch() {
        // Cancel child touches
        if (!mChildrenCanceledTouch) {
            final long now = SystemClock.uptimeMillis();
            final MotionEvent cancelEvent = MotionEvent.obtain(now, now,
                    MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                getChildAt(i).dispatchTouchEvent(cancelEvent);
            }
            cancelEvent.recycle();
            mChildrenCanceledTouch = true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && hasVisibleDrawer()) {
            KeyEventCompat.startTracking(event);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            final View visibleDrawer = findVisibleDrawer();
            if (visibleDrawer != null) {
                closeDrawer();
            }
            return visibleDrawer != null;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        final SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        final View toOpen = findDrawer();
        if (toOpen != null) {
            openDrawer(toOpen);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState ss = new SavedState(superState);

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            // Is the current child fully opened (that is, not closing)?
            boolean isOpenedAndNotClosing = (lp.openState == LayoutParams.FLAG_IS_OPENED);
            // Is the current child opening?
            boolean isClosedAndOpening = (lp.openState == LayoutParams.FLAG_IS_OPENING);
            if (isOpenedAndNotClosing || isClosedAndOpening) {
                // If one of the conditions above holds, save the child's gravity
                // so that we open that child during state restore.
                ss.openDrawerGravity = lp.gravity;
                break;
            }
        }

        return ss;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof IContentView || child instanceof IDrawerView) {
            //left empty
        } else {
            return;
        }

        super.addView(child, index, params);

        final View openDrawer = findOpenDrawer();
        if (openDrawer != null || isDrawerView(child)) {
            // A drawer is already open or the new view is a drawer, so the
            // new view should start out hidden.
            ViewCompat.setImportantForAccessibility(child,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        } else {
            // Otherwise this is a content view and no drawer is open, so the
            // new view should start out visible.
            ViewCompat.setImportantForAccessibility(child,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        // We only need a delegate here if the framework doesn't understand
        // NO_HIDE_DESCENDANTS importance.
        if (!CAN_HIDE_DESCENDANTS) {
            ViewCompat.setAccessibilityDelegate(child, mChildAccessibilityDelegate);
        }
    }

    private static boolean includeChildForAccessibility(View child) {
        // If the child is not important for accessibility we make
        // sure this hides the entire subtree rooted at it as the
        // IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDATS is not
        // supported on older platforms but we want to hide the entire
        // content and not opened drawers if a drawer is opened.
        return ViewCompat.getImportantForAccessibility(child)
                != ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                && ViewCompat.getImportantForAccessibility(child)
                != ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO;
    }

    /**
     * State persisted across instances
     */
    protected static class SavedState extends AbsSavedState {
        int openDrawerGravity = Gravity.NO_GRAVITY;
        int lockModeLeft;
        int lockModeRight;
        int lockModeStart;
        int lockModeEnd;

        public SavedState(Parcel in, ClassLoader loader) {
            super(in, loader);
            openDrawerGravity = in.readInt();
            lockModeLeft = in.readInt();
            lockModeRight = in.readInt();
            lockModeStart = in.readInt();
            lockModeEnd = in.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(openDrawerGravity);
            dest.writeInt(lockModeLeft);
            dest.writeInt(lockModeRight);
            dest.writeInt(lockModeStart);
            dest.writeInt(lockModeEnd);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = ParcelableCompat.newCreator(
                new ParcelableCompatCreatorCallbacks<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                });
    }

    private class ViewDragCallback extends DrawerDragHelper.Callback {

        private DrawerDragHelper mDragger;

        public ViewDragCallback() {

        }

        public void setDragger(DrawerDragHelper dragger) {
            mDragger = dragger;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            View drawerView = findDrawer();
            updateDrawerState(state, drawerView);
        }

        @Override
        public void onViewPositionChanged(View contentView, int left, int top, int dx, int dy) {
            View drawerView = findDrawer();
            int maxDrag = drawerView.getWidth();
            float offset = 1.0f * contentView.getLeft() / CONTENT_OFFSET_FACTOR / maxDrag;

            setDrawerViewOffset(drawerView, DRAWER_ON_SCREEN + offset);

            invalidate();
        }

        @Override
        public void onViewReleased(View contentView, float xvel, float yvel) {
            // Offset is how open the drawer is, therefore left/right values
            // are reversed from one another.
            View drawerView = findDrawer();
            final float offset = getDrawerViewOffset(drawerView);
            int maxDrag = drawerView.getWidth();
            int left = xvel > 0 || xvel == 0 && offset > 0.75f ? maxDrag : 0;

            mDragger.settleCapturedViewAt(left, drawerView.getTop());
            invalidate();
        }

        @Override
        public void onEdgeTouched(int edgeFlags, int pointerId) {

        }

        @Override
        public boolean onEdgeLock(int edgeFlags) {

            return false;
        }

        @Override
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {

        }

        @Override
        public boolean tryCaptureView(View contentView, int pointerId) {
            return isContentView(contentView);
        }

        @Override
        public int getViewHorizontalDragRange(View contentView) {
            View drawerView = findDrawer();
            return drawerView.getWidth();
        }

        @Override
        public int clampViewPositionHorizontal(View contentView, int left, int dx) {
            View drawerView = findDrawer();
            int maxDrag = drawerView.getWidth();
            int offset = Math.min(maxDrag, Math.max(left, 0));

            return offset;
        }

        @Override
        public int clampViewPositionVertical(View contentView, int top, int dy) {
            return contentView.getTop();
        }

    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int FLAG_IS_OPENED = 0x1;
        private static final int FLAG_IS_OPENING = 0x2;
        private static final int FLAG_IS_CLOSING = 0x4;

        public int gravity = Gravity.NO_GRAVITY;
        private float onScreen = DRAWER_ON_SCREEN;
        private int openState;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            this.gravity = a.getInt(0, Gravity.NO_GRAVITY);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            this(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.gravity = source.gravity;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }
    }

    class AccessibilityDelegate extends AccessibilityDelegateCompat {
        private final Rect mTmpRect = new Rect();

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            if (CAN_HIDE_DESCENDANTS) {
                super.onInitializeAccessibilityNodeInfo(host, info);
            } else {
                // Obtain a node for the host, then manually generate the list
                // of children to only include non-obscured views.
                final AccessibilityNodeInfoCompat superNode =
                        AccessibilityNodeInfoCompat.obtain(info);
                super.onInitializeAccessibilityNodeInfo(host, superNode);

                info.setSource(host);
                final ViewParent parent = ViewCompat.getParentForAccessibility(host);
                if (parent instanceof View) {
                    info.setParent((View) parent);
                }
                copyNodeInfoNoChildren(info, superNode);
                superNode.recycle();

                addChildrenForAccessibility(info, (ViewGroup) host);
            }

            info.setClassName(LeftDrawerLayout.class.getName());

            // This view reports itself as focusable so that it can intercept
            // the back button, but we should prevent this view from reporting
            // itself as focusable to accessibility services.
            info.setFocusable(false);
            info.setFocused(false);
            info.removeAction(AccessibilityActionCompat.ACTION_FOCUS);
            info.removeAction(AccessibilityActionCompat.ACTION_CLEAR_FOCUS);
        }

        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);

            event.setClassName(LeftDrawerLayout.class.getName());
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            // Special case to handle window state change events. As far as
            // accessibility services are concerned, state changes from
            // DrawerLayout invalidate the entire contents of the screen (like
            // an Activity or Dialog) and they should announce the title of the
            // new content.
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                return true;
            }

            return super.dispatchPopulateAccessibilityEvent(host, event);
        }

        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            if (CAN_HIDE_DESCENDANTS || includeChildForAccessibility(child)) {
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
            return false;
        }

        private void addChildrenForAccessibility(AccessibilityNodeInfoCompat info, ViewGroup v) {
            final int childCount = v.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = v.getChildAt(i);
                if (includeChildForAccessibility(child)) {
                    info.addChild(child);
                }
            }
        }

        /**
         * This should really be in AccessibilityNodeInfoCompat, but there unfortunately
         * seem to be a few elements that are not easily cloneable using the underlying API.
         * Leave it private here as it's not general-purpose useful.
         */
        private void copyNodeInfoNoChildren(AccessibilityNodeInfoCompat dest,
                                            AccessibilityNodeInfoCompat src) {
            final Rect rect = mTmpRect;

            src.getBoundsInParent(rect);
            dest.setBoundsInParent(rect);

            src.getBoundsInScreen(rect);
            dest.setBoundsInScreen(rect);

            dest.setVisibleToUser(src.isVisibleToUser());
            dest.setPackageName(src.getPackageName());
            dest.setClassName(src.getClassName());
            dest.setContentDescription(src.getContentDescription());

            dest.setEnabled(src.isEnabled());
            dest.setClickable(src.isClickable());
            dest.setFocusable(src.isFocusable());
            dest.setFocused(src.isFocused());
            dest.setAccessibilityFocused(src.isAccessibilityFocused());
            dest.setSelected(src.isSelected());
            dest.setLongClickable(src.isLongClickable());

            dest.addAction(src.getActions());
        }
    }

    final class ChildAccessibilityDelegate extends AccessibilityDelegateCompat {
        @Override
        public void onInitializeAccessibilityNodeInfo(View child,
                                                      AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(child, info);

            if (!includeChildForAccessibility(child)) {
                // If we are ignoring the sub-tree rooted at the child,
                // break the connection to the rest of the node tree.
                // For details refer to includeChildForAccessibility.
                info.setParent(null);
            }
        }
    }

    public View findTopChildUnder(int x, int y) {
        final int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (x >= child.getLeft() && x < child.getRight() &&
                    y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

}
