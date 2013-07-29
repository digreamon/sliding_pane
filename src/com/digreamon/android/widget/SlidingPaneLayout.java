/*
 * Copyright (C) 2013 Dima Kolomiyets Android Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digreamon.android.widget;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Scroller;

/**
 * SlidingPaneLayout provides a horizontal, multi-pane layout for use at the top level
 * of a UI. A left (or first) pane is treated as a content list or browser, subordinate to a
 * primary detail view for displaying content.
 *
 * <p>Child views may overlap if their combined width exceeds the available width
 * in the SlidingPaneLayout. When this occurs the user may slide the topmost view out of the way
 * by dragging it, or by navigating in the direction of the overlapped view using a keyboard.
 * If the content of the dragged child view is itself horizontally scrollable, the user may
 * grab it by the very edge.</p>
 *
 * <p>Thanks to this sliding behavior, SlidingPaneLayout may be suitable for creating layouts
 * that can smoothly adapt across many different screen sizes, expanding out fully on larger
 * screens and collapsing on smaller screens.</p>
 *
 * <p>SlidingPaneLayout is distinct from a navigation drawer as described in the design
 * guide and should not be used in the same scenarios. SlidingPaneLayout should be thought
 * of only as a way to allow a two-pane layout normally used on larger screens to adapt to smaller
 * screens in a natural way. The interaction patterns expressed by SlidingPaneLayout imply
 * a physicality and direct information hierarchy between panes that does not necessarily exist
 * in a scenario where a navigation drawer should be used instead.</p>
 *
 * <p>Appropriate uses of SlidingPaneLayout include pairings of panes such as a contact list and
 * subordinate interactions with those contacts, or an email thread list with the content pane
 * displaying the contents of the selected thread. Inappropriate uses of SlidingPaneLayout include
 * switching between disparate functions of your app, such as jumping from a social stream view
 * to a view of your personal profile - cases such as this should use the navigation drawer
 * pattern instead. (TODO: insert doc link to nav drawer widget.)</p>
 *
 * <p>Like {@link android.widget.LinearLayout LinearLayout}, SlidingPaneLayout supports
 * the use of the layout parameter <code>layout_weight</code> on child views to determine
 * how to divide leftover space after measurement is complete. It is only relevant for width.
 * When views do not overlap weight behaves as it does in a LinearLayout.</p>
 *
 * <p>When views do overlap, weight on a slideable pane indicates that the pane should be
 * sized to fill all available space in the closed state. Weight on a pane that becomes covered
 * indicates that the pane should be sized to fill all available space except a small minimum strip
 * that the user may use to grab the slideable view and pull it back over into a closed state.</p>
 *
 * <p>Experimental. This class may be removed.</p>
 */
public class SlidingPaneLayout extends ViewGroup {
	
    private static final String TAG = "SlidingPaneLayout";

    /**
     * Default size of the touch gutter along the edge where the user
     * may grab and drag a sliding pane, even if its internal content
     * may horizontally scroll.
     */
    private static final int DEFAULT_GUTTER_SIZE = 16; // dp

    /**
     * Default size of the overhang for a pane in the open state.
     * At least this much of a sliding pane will remain visible.
     * This indicates that there is more content available and provides
     * a "physical" edge to grab to pull it closed.
     */
    private static final int DEFAULT_OVERHANG_SIZE = 32; // dp;

    private static final int MAX_SETTLE_DURATION = 600; // ms;
    
    /**
     * Default width of the touch area to detect the start of gesture for sliding the front view.
     */
    private static final int DEFAULT_SENSE_AREA_WIDTH = 30; // dp;
    
    private static final int DEFAULT_FADE_COLOR = 0x99999999;

    /**
     * Base duration for programmatic scrolling of the sliding pane.
     * This will be increased relative to the distance to be covered.
     */
    private static final int BASE_SCROLL_DURATION = 200; // ms
    
    private static final int INVALID_POINTER = -1;

    /**
     * Indicates that the panels are in an idle, settled state. The current panel
     * is fully in view and no animation is in progress.
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * Indicates that a panel is currently being dragged by the user.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * Indicates that a panel is in the process of settling to a final position.
     */
    public static final int SCROLL_STATE_SETTLING = 2;

//    /**
//     * Indicates if nothing is to reveal.
//     */
//    private static final int REVEAL_MODE_IDLE = 0;
    
    /**
     * Indicates whether we reveal left pane.
     */
    private static final int REVEAL_MODE_LEFT2RIGHT = 1;
    
    /**
     * Indicates whether we reveal right pane.
     */
    private static final int REVEAL_MODE_RIGHT2LEFT = 2;

    /**
     * The fade color used for the sliding panel. 0 = no fading.
     */
    private int mSliderFadeColor = DEFAULT_FADE_COLOR;

    /**
     * The fade color used for the panel covered by the slider. 0 = no fading.
     */
    private int mCoveredFadeColor;

    /**
     * Drawable used to draw the shadow between panes on the left.
     */
    private Drawable mLeftShadowDrawable;
    
    /**
     * Drawable used to draw the shadow between panes on the right.
     */
    private Drawable mRightShadowDrawable;

    /**
     * The size of the touch gutter in pixels
     */
    private final int mGutterSize;

    /**
     * The size of the overhang in pixels.
     * This is the minimum section of the sliding panel that will
     * be visible in the open state to allow for a closing drag.
     */
    private final int mOverhangSize;

    /**
     * True if a panel can slide with the current measurements
     */
    private boolean mIsSlidable;

    /**
     * The child view that is in the front and is slidable.
     */
    private View mFrontView;
    
    /**
     * The child view that is represents menu on the left.
     */
    private View mLeftView;
    
    /**
     * The child view that is represents menu on the left.
     */
    private View mRightView;

    /**
     * How far the panel is offset from its closed position.
     * range [0, 1] where 0 = closed, 1 = open.
     */
    private float mSlideOffset;

//    /**
//     * How far the non-sliding panel is parallaxed from its usual position when open.
//     * range [0, 1]
//     */
//    private float mParallaxOffset;
    
    /**
     * How far in pixels the slidable panel may move from Left to Right.
     */
    private int mL2rSlideRange;

    /**
     * How far in pixels the slidable panel may move from Right to Left.
     */
    private int mR2lSlideRange;
    
    /**
     * A panel view is locked into internal scrolling or another condition that
     * is preventing a drag.
     */
    private boolean mIsUnableToDrag;

    private int mTouchSlop;
    private float mInitialMotionX;
    private float mInitialMotionY;
    private float mLastMotionX;
    private float mLastMotionY;
    private int mActivePointerId = INVALID_POINTER;

    private VelocityTracker mVelocityTracker;
    private float mMaxVelocity;

    private PanelSlideListener mPanelSlideListener;
    
    private int mScrollState = SCROLL_STATE_IDLE;
    
    private int mRevealMode = REVEAL_MODE_LEFT2RIGHT;

    /**
     * Interpolator defining the animation curve for mScroller
     */
    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    /**
     * Used to animate flinging panes.
     */
    private final Scroller mScroller;

    static final SlidingPanelLayoutImpl IMPL;

    static {
        final int deviceVersion = Build.VERSION.SDK_INT;
        if (deviceVersion >= 17) {
            IMPL = new SlidingPanelLayoutImplJBMR1();
        } else if (deviceVersion >= 16) {
            IMPL = new SlidingPanelLayoutImplJB();
        } else {
            IMPL = new SlidingPanelLayoutImplBase();
        }
    }

    /**
     * Listener for monitoring events about sliding panes.
     */
    public interface PanelSlideListener {
        /**
         * Called when a sliding pane's position changes.
         * @param panel The child view that was moved
         * @param slideOffset The new offset of this sliding pane within its range, from 0-1
         */
        public void onPanelSlide(View panel, float slideOffset);
        /**
         * Called when a sliding pane becomes slid completely open. The pane may or may not
         * be interactive at this point depending on how much of the pane is visible.
         * @param panel The child view that was slid to an open position, revealing other panes
         */
        public void onPanelOpened(View panel);

        /**
         * Called when a sliding pane becomes slid completely closed. The pane is now guaranteed
         * to be interactive. It may now obscure other views in the layout.
         * @param panel The child view that was slid to a closed position
         */
        public void onPanelClosed(View panel);
    }

    /**
     * No-op stubs for {@link PanelSlideListener}. If you only want to implement a subset
     * of the listener methods you can extend this instead of implement the full interface.
     */
    public static class SimplePanelSlideListener implements PanelSlideListener {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {
        }
        @Override
        public void onPanelOpened(View panel) {
        }
        @Override
        public void onPanelClosed(View panel) {
        }
    }

    public SlidingPaneLayout(Context context) {
        this(context, null);
    }

    public SlidingPaneLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mScroller = new Scroller(context, sInterpolator);
        mGutterSize = (int) dp2px(DEFAULT_GUTTER_SIZE);
        mOverhangSize = (int) dp2px(DEFAULT_OVERHANG_SIZE);

        final ViewConfiguration viewConfig = ViewConfiguration.get(context);
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(viewConfig);
        mMaxVelocity = viewConfig.getScaledMaximumFlingVelocity();

        setWillNotDraw(false);
    }

    /**
     * Set the color used to fade the sliding pane out when it is slid most of the way offscreen.
     *
     * @param color An ARGB-packed color value
     */
    public void setSliderFadeColor(int color) {
        mSliderFadeColor = color;
    }

    /**
     * @return The ARGB-packed color value used to fade the sliding pane
     */
    public int getSliderFadeColor() {
        return mSliderFadeColor;
    }

    /**
     * Set the color used to fade the pane covered by the sliding pane out when the pane
     * will become fully covered in the closed state.
     *
     * @param color An ARGB-packed color value
     */
    public void setCoveredFadeColor(int color) {
        mCoveredFadeColor = color;
    }

    /**
     * @return The ARGB-packed color value used to fade the fixed pane
     */
    public int getCoveredFadeColor() {
        return mCoveredFadeColor;
    }

    void setScrollState(int state) {
        if (mScrollState != state) {
            mScrollState = state;
        }
    }
    
    private void setRevealMode(float actionDownX){
    	if(!isOpen()){
    		mLeftView.setVisibility(GONE);
    		mRightView.setVisibility(GONE);
    		if(actionDownX < mFrontView.getWidth()/2){
    			mRevealMode =  REVEAL_MODE_LEFT2RIGHT;
    			mLeftView.setVisibility(VISIBLE);
    			bringChildToFront(mLeftView);
    		} else {
    			mRevealMode = REVEAL_MODE_RIGHT2LEFT;
    			mRightView.setVisibility(VISIBLE);
    			bringChildToFront(mRightView);
    		}
    		bringChildToFront(mFrontView);
			requestLayout();
			invalidate();
    	}
    }

    public void setPanelSlideListener(PanelSlideListener listener) {
        mPanelSlideListener = listener;
    }

    void dispatchOnPanelSlide(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelSlide(panel, mSlideOffset);
        }
    }

    void dispatchOnPanelOpened(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelOpened(panel);
        }
    }

    void dispatchOnPanelClosed(View panel) {
        if (mPanelSlideListener != null) {
            mPanelSlideListener.onPanelClosed(panel);
        }
    }

    private void setChildMeasure(View child, int maxHeight, int maxWidth){
    	if(child==null) return;
    	final LayoutParams lp = (LayoutParams) child.getLayoutParams();
    	
    	int childWidthSpec;
        final int horizontalMargin = lp.leftMargin + lp.rightMargin;
        final int verticalMargin = lp.topMargin + lp.bottomMargin;
        if (lp.width == LayoutParams.WRAP_CONTENT) {
        	childWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth-horizontalMargin, MeasureSpec.AT_MOST);
        } else if (lp.width == LayoutParams.MATCH_PARENT) {
        	childWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth-horizontalMargin, MeasureSpec.EXACTLY);
        } else {
            childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
        }

        int childHeightSpec;
        if (lp.height == LayoutParams.WRAP_CONTENT) {
        	childHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight-verticalMargin, MeasureSpec.AT_MOST);
        } else if (lp.height == LayoutParams.MATCH_PARENT) {
        	childHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight-verticalMargin, MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
        }

        child.measure(childWidthSpec, childHeightSpec);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    	
    	final int childCount = getChildCount();
    	
    	if (childCount != 3) {
            throw new IllegalStateException("onMeasure: only exactly 3 child views are supported!");
        }
    	
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            throw new IllegalStateException("Height must not be UNSPECIFIED");
        }
        
        int layoutHeight = 0;
        int maxLayoutHeight = -1;
        
        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                layoutHeight = maxLayoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
                break;
            case MeasureSpec.AT_MOST:
                maxLayoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
                break;
        }

        boolean canSlide = false;
        int widthRemaining = widthSize - getPaddingLeft() - getPaddingRight();
        
        for(int i=0; i<getChildCount(); i++){
        	View child = getChildAt(i);
        	LayoutParams lp = (LayoutParams)child.getLayoutParams();
        	switch (lp.spec) {
			case LayoutParams.SPEC_FRONT:
				mFrontView = child;
				setChildMeasure(mFrontView, maxLayoutHeight, widthRemaining);
				break;
			case LayoutParams.SPEC_LEFT:
				canSlide = true;
				mLeftView = child;
				setChildMeasure(mLeftView, maxLayoutHeight, widthRemaining - mOverhangSize);
				break;
			case LayoutParams.SPEC_RIGHT:
				canSlide = true;
				mRightView = child;
				setChildMeasure(mRightView, maxLayoutHeight, widthRemaining - mOverhangSize);
				break;
			default:
				continue;
			}
        	
        	final int childHeight = child.getMeasuredHeight();
        	if (heightMode == MeasureSpec.AT_MOST && childHeight > layoutHeight) {
        		layoutHeight = Math.min(childHeight, maxLayoutHeight);
        	}
        }
        
        setMeasuredDimension(widthSize, layoutHeight);
        mIsSlidable = canSlide;
        if (mScrollState != SCROLL_STATE_IDLE && !canSlide) {
            // Cancel scrolling in progress, it's no longer relevant.
            setScrollState(SCROLL_STATE_IDLE);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        
        mL2rSlideRange = calculateSlideRange(mLeftView, width);
        mR2lSlideRange = calculateSlideRange(mRightView, width);
        
        setViewLayout(mLeftView, paddingLeft, paddingTop);
        setViewLayout(mRightView, width - paddingRight - mRightView.getMeasuredWidth(), paddingTop);
        setViewLayout(mFrontView, paddingLeft, paddingTop);
    }

    private int calculateSlideRange(View backView, final int width){
    	if(backView==null) return 0;
    	final LayoutParams lp = (LayoutParams) backView.getLayoutParams();
    	final int margin = lp.leftMargin + lp.rightMargin;
    	final int range = Math.min(lp.width, width - mOverhangSize) - margin;
    	return range;
    }
    
    private void setViewLayout(final View view, final int leftOffset, final int topOffset){
    	view.layout(leftOffset, topOffset, 
    			leftOffset + view.getMeasuredWidth(), topOffset+view.getMeasuredHeight());
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        if (!mIsSlidable || (mIsUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mActivePointerId = INVALID_POINTER;
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            return false;
        }

        boolean interceptTap = false;

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // No valid pointer = no valid drag. Ignore.
                    break;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float dx = x - mLastMotionX;
                final float xDiff = Math.abs(dx);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = Math.abs(y - mLastMotionY);

                if (dx != 0 && !isGutterDrag(mLastMotionX, dx) &&
                        canScroll(this, false, (int) dx, (int) x, (int) y)) {
                    mInitialMotionX = mLastMotionX = x;
                    mLastMotionY = y;
                    mIsUnableToDrag = true;
                    return false;
                }
                if (xDiff > mTouchSlop && xDiff > yDiff && isSlideablePaneUnder(x, y)) {
                    mLastMotionX = dx > 0 ? mInitialMotionX + mTouchSlop :
                            mInitialMotionX - mTouchSlop;
                    setScrollState(SCROLL_STATE_DRAGGING);
                } else if (yDiff > mTouchSlop) {
                    mIsUnableToDrag = true;
                    return false;
                }
                if (mScrollState == SCROLL_STATE_DRAGGING && performDrag(x)) {
                    invalidate();
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mIsUnableToDrag = false;
                mInitialMotionX = x;
                mInitialMotionY = y;
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                if (isSlideablePaneUnder(x, y)) {
                    mLastMotionX = x;
                    mLastMotionY = y;
                    if (mScrollState == SCROLL_STATE_SETTLING || mScrollState == SCROLL_STATE_IDLE) {
                        // Start dragging immediately. "Catch"
                        setScrollState(SCROLL_STATE_DRAGGING);
                    } else if (isDimmed(mFrontView)) {
                        interceptTap = true;
                    }
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
        return mScrollState == SCROLL_STATE_DRAGGING || interceptTap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mIsSlidable) {
            return super.onTouchEvent(ev);
        }

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        boolean needsInvalidate = false;
        boolean wantTouchEvents = true;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                final float x = ev.getX();
                final float y = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mInitialMotionX = x;
                mInitialMotionY = y;

                if (isSlideablePaneUnder(x, y)) {
                    mScroller.abortAnimation();
                    wantTouchEvents = true;
                    mLastMotionX = x;
                    setScrollState(SCROLL_STATE_DRAGGING);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mScrollState != SCROLL_STATE_DRAGGING) {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float dx = Math.abs(x - mLastMotionX);
                    final float dy = Math.abs(y - mLastMotionY);
                    if (dx > mTouchSlop && dx > dy && isSlideablePaneUnder(x, y)) {
                        mLastMotionX = x - mInitialMotionX > 0 ? mInitialMotionX + mTouchSlop :
                                mInitialMotionX - mTouchSlop;
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    final int activePointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
                    needsInvalidate |= performDrag(x);
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (isDimmed(mFrontView)) {
                    final int pi = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, pi);
                    final float y = MotionEventCompat.getY(ev, pi);
                    final float dx = x - mInitialMotionX;
                    final float dy = y - mInitialMotionY;
                    if (dx * dx + dy * dy < mTouchSlop * mTouchSlop && isSlideablePaneUnder(x, y)) {
                        // Taps close a dimmed open pane.
                        closePane(mFrontView, 0);
                        mActivePointerId = INVALID_POINTER;
                        break;
                    }
                }
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    final VelocityTracker vt = mVelocityTracker;
                    vt.computeCurrentVelocity(1000, mMaxVelocity);
                    int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(vt,
                            mActivePointerId);
                    
                    boolean isPerformClose = mRevealMode == REVEAL_MODE_LEFT2RIGHT ? 
                    		initialVelocity < 0 || (initialVelocity == 0 && mSlideOffset < 0.5f) :
                    			initialVelocity > 0 || (initialVelocity == 0 && mSlideOffset < 0.5f);
                    
                    if (isPerformClose) {
                    	closePane(mFrontView, initialVelocity);
                    } else {
                    	openPane(mFrontView, initialVelocity);
                    }
                    
                    mActivePointerId = INVALID_POINTER;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    mActivePointerId = INVALID_POINTER;
                    if (mSlideOffset < 0.5f) {
                        closePane(mFrontView, 0);
                    } else {
                        openPane(mFrontView, 0);
                    }
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);
                mLastMotionX = MotionEventCompat.getX(ev, index);
                mLastMotionY = MotionEventCompat.getY(ev, index);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP: {
                onSecondaryPointerUp(ev);
                break;
            }
        }

        if (needsInvalidate) {
            invalidate();
        }
        return wantTouchEvents;
    }

    private void closePane(View pane, int initialVelocity) {
        if (mIsSlidable) {
        	smoothSlideTo(0.f, initialVelocity);
        }
    }

    private void openPane(View pane, int initialVelocity) {
        if (mIsSlidable) {
            smoothSlideTo(mRevealMode == REVEAL_MODE_LEFT2RIGHT ? 1.f : -1.f, initialVelocity);
        }
    }

    /**
     * Animate the sliding panel to its open state.
     */
    public void smoothSlideOpen() {
        if (mIsSlidable) {
            openPane(mFrontView, 0);
        }
    }

    /**
     * Animate the sliding panel to its closed state.
     */
    public void smoothSlideClosed() {
        if (mIsSlidable) {
            closePane(mFrontView, 0);
        }
    }

    /**
     * @return true if sliding panels are completely open
     */
    public boolean isOpen() {
        return !mIsSlidable || mSlideOffset != 0;
    }

    /**
     * @return true if content in this layout can be slid open and closed
     */
    public boolean isCanSlide() {
        return mIsSlidable;
    }

    private int getSlideRange(){
    	return mRevealMode == REVEAL_MODE_LEFT2RIGHT ? mL2rSlideRange : mR2lSlideRange;
    }
    
    private boolean performDrag(float x) {
        final float dxMotion = x - mLastMotionX;
        mLastMotionX = x;

        final LayoutParams lp = (LayoutParams) mFrontView.getLayoutParams();
        final int leftBound = getLeft()+getPaddingLeft() + lp.leftMargin;
        final int rightBound = getRight()-getPaddingRight()-lp.rightMargin;
        
        float oldX = 0;
        float newX = 0;
        int dxPane = 0;
        if(mRevealMode == REVEAL_MODE_LEFT2RIGHT){
          oldX = mFrontView.getLeft();
          newX = Math.min(Math.max(oldX + dxMotion, leftBound), rightBound);
          
          if(newX>mLeftView.getRight()){
        	  newX = mLeftView.getRight();
          }
          
          dxPane = (int) (newX - oldX);       
          mSlideOffset = (newX - leftBound) / getSlideRange();
        } else {
        	oldX = mFrontView.getRight();
            newX = Math.max(Math.min(oldX + dxMotion, rightBound), leftBound);
            
            if(newX<mRightView.getLeft()){
            	newX = mRightView.getLeft();
            }
            
            dxPane = (int) (newX - oldX);
            mSlideOffset = (rightBound - newX) / getSlideRange();
        }

        if (dxPane == 0) {
            return false;
        }

        mFrontView.offsetLeftAndRight(dxPane);

        mLastMotionX += newX - (int) newX;
        if (lp.dimWhenOffset) {
            dimChildView(mFrontView, mSlideOffset, mSliderFadeColor);
        }
        dispatchOnPanelSlide(mFrontView);

        return true;
    }

    private void dimChildView(View v, float mag, int fadeColor) {
        final LayoutParams lp = (LayoutParams) v.getLayoutParams();

        if (mag > 0 && fadeColor != 0) {
            final int baseAlpha = (fadeColor & 0xff000000) >>> 24;
            int imag = (int) (baseAlpha * mag);
            int color = imag << 24 | (fadeColor & 0xffffff);
            if (lp.dimPaint == null) {
                lp.dimPaint = new Paint();
            }
            lp.dimPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_OVER));
            if (ViewCompat.getLayerType(v) != ViewCompat.LAYER_TYPE_HARDWARE) {
                ViewCompat.setLayerType(v, ViewCompat.LAYER_TYPE_HARDWARE, lp.dimPaint);
            }
            invalidateChildRegion(v);
        } else if (ViewCompat.getLayerType(v) != ViewCompat.LAYER_TYPE_NONE) {
            ViewCompat.setLayerType(v, ViewCompat.LAYER_TYPE_NONE, null);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (Build.VERSION.SDK_INT >= 11) { // HC
            return super.drawChild(canvas, child, drawingTime);
        }

        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (lp.dimWhenOffset && mSlideOffset > 0) {
            if (!child.isDrawingCacheEnabled()) {
                child.setDrawingCacheEnabled(true);
            }
            final Bitmap cache = child.getDrawingCache();
            canvas.drawBitmap(cache, child.getLeft(), child.getTop(), lp.dimPaint);
            return false;
        } else {
            if (child.isDrawingCacheEnabled()) {
                child.setDrawingCacheEnabled(false);
            }
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    private void invalidateChildRegion(View v) {
        IMPL.invalidateChildRegion(this, v);
    }

    private boolean isGutterDrag(float x, float dx) {
        return (x < mGutterSize && dx > 0) || (x > getWidth() - mGutterSize && dx < 0);
    }

    /**
     * Smoothly animate mDraggingPane to the target X position within its range.
     *
     * @param slideOffset position to animate to
     * @param velocity initial velocity in case of fling, or 0.
     */
    void smoothSlideTo(float slideOffset, int velocity) {
        if (!mIsSlidable) {
            // Nothing to do.
            return;
        }

        final LayoutParams lp = (LayoutParams) mFrontView.getLayoutParams();

        final int leftBound = getPaddingLeft() + lp.leftMargin;
        int sx = mFrontView.getLeft();
        int x = (int) (leftBound + slideOffset * getSlideRange());
        int dx = x - sx;
        if (dx == 0) {
            setScrollState(SCROLL_STATE_IDLE);
            if (mSlideOffset == 0) {
                dispatchOnPanelClosed(mFrontView);
            } else {
                dispatchOnPanelOpened(mFrontView);
            }
            return;
        }

        setScrollState(SCROLL_STATE_SETTLING);

        final int width = getWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
        final float distance = halfWidth + halfWidth *
                distanceInfluenceForSnapDuration(distanceRatio);

        int duration = 0;
        velocity = Math.abs(velocity);
        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float range = (float) Math.abs(dx) / getSlideRange();
            duration = (int) ((range + 1) * BASE_SCROLL_DURATION);
        }
        duration = Math.min(duration, MAX_SETTLE_DURATION);

        mScroller.startScroll(sx, 0, dx, 0, duration);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    @Override
    public void computeScroll() {
        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            if (!mIsSlidable) {
                mScroller.abortAnimation();
                return;
            }

            final int oldLeft = mFrontView.getLeft();
            final int newLeft = mScroller.getCurrX();
            final int dx = newLeft - oldLeft;
            mFrontView.offsetLeftAndRight(dx);

            final LayoutParams lp = (LayoutParams) mFrontView.getLayoutParams();
            final int leftBound = getPaddingLeft() + lp.leftMargin;
            
            mSlideOffset = (float) (newLeft - leftBound) / getSlideRange();
            if (lp.dimWhenOffset) {
                dimChildView(mFrontView, Math.abs(mSlideOffset), mSliderFadeColor);
            }
            dispatchOnPanelSlide(mFrontView);

            if (mScroller.isFinished()) {
                setScrollState(SCROLL_STATE_IDLE);
                post(new Runnable() {
                    public void run() {
                        if (mSlideOffset == 0) {
                            dispatchOnPanelClosed(mFrontView);
                        } else {
                            dispatchOnPanelOpened(mFrontView);
                        }
                    }
                });
            }
            ViewCompat.postInvalidateOnAnimation(this);
        }

    }
    
    /**
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param d drawable to use as a shadow
     */
    public void setLeftShadowDrawable(Drawable d) {
        mLeftShadowDrawable = d;
    }

    /**
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param d drawable to use as a shadow
     */
    public void setRightShadowDrawable(Drawable d) {
        mRightShadowDrawable = d;
    }

    /**
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param resId Resource ID of a drawable to use
     */
    public void setLeftShadowResource(int resId) {
        setLeftShadowDrawable(getResources().getDrawable(resId));
    }
    
    /**
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param resId Resource ID of a drawable to use
     */
    public void setRightShadowResource(int resId) {
        setRightShadowDrawable(getResources().getDrawable(resId));
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        Drawable mShadowDrawable = mRevealMode == REVEAL_MODE_LEFT2RIGHT ? mLeftShadowDrawable : mRightShadowDrawable;
        
        if (mFrontView == null || mFrontView.getVisibility() == GONE ||
                mShadowDrawable == null) {
            // No need to draw a shadow if we don't have one.
            return;
        }

        final int shadowWidth = mShadowDrawable.getIntrinsicWidth();
        if(mRevealMode == REVEAL_MODE_LEFT2RIGHT){
            doDraw(c, mShadowDrawable, 
            		mFrontView.getLeft() - shadowWidth, mFrontView.getLeft(), 
            		mFrontView.getTop(), mFrontView.getBottom());
        } else {
            doDraw(c, mShadowDrawable, 
            		mFrontView.getRight(), mFrontView.getRight()+shadowWidth, 
            		mFrontView.getTop(), mFrontView.getBottom());
        }
    }

    private void doDraw(Canvas c, Drawable mShadowDrawable, 
    		final int left, final int right, final int top, final int bottom){
    	mShadowDrawable.setBounds(left, top, right, bottom);
        mShadowDrawable.draw(c);
    }
    
//    private void parallaxOtherViews(float slideOffset) {
//        final LayoutParams slideLp = (LayoutParams) mFrontView.getLayoutParams();
//        final boolean dimViews = slideLp.dimWhenOffset && slideLp.leftMargin <= 0;
//        final int childCount = getChildCount();
//        for (int i = 0; i < childCount; i++) {
//            final View v = getChildAt(i);
//            if (v == mFrontView) continue;
//
//            final int oldOffset = (int) ((1 - mParallaxOffset) * mParallaxBy);
//            mParallaxOffset = slideOffset;
//            final int newOffset = (int) ((1 - slideOffset) * mParallaxBy);
//            final int dx = oldOffset - newOffset;
//
//            v.offsetLeftAndRight(dx);
//
//            if (dimViews) {
//                dimChildView(v, 1 - mParallaxOffset, mCoveredFadeColor);
//            }
//        }
//    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx Delta scrolled in pixels
     * @param x X coordinate of the active touch point
     * @param y Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
            mLastMotionY = MotionEventCompat.getY(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    boolean isSlideablePaneUnder(float x, float y) {
        final View child = mFrontView;
        setRevealMode(x);
        
        boolean isByX = false;
        if(mRevealMode == REVEAL_MODE_LEFT2RIGHT){
        	isByX = x >= child.getLeft() - mGutterSize && x < child.getLeft() + dp2px(DEFAULT_SENSE_AREA_WIDTH) + mGutterSize;
        } else {
        	isByX = x >= child.getRight() - dp2px(DEFAULT_SENSE_AREA_WIDTH) - mGutterSize && x < child.getRight() + mGutterSize;
        }
        
        return child != null && isByX && y >= child.getTop() && y < child.getBottom();
    }

    boolean isDimmed(View child) {
        if (child == null) {
            return false;
        }
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return mIsSlidable && lp.dimWhenOffset && mSlideOffset > 0;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
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

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
    	public final static int SPEC_FRONT = 0;
    	public final static int SPEC_LEFT = 1;
    	public final static int SPEC_RIGHT = 2;
    	
        private static final int[] ATTRS = new int[] {
            android.R.attr.layout_weight,
            R.attr.spec,
            R.attr.dimWhenOffset
        };

        /**
         * The weighted proportion of how much of the leftover space
         * this child should consume after measurement.
         */
        public float weight = 0;

        public int spec = SPEC_FRONT;

        /**
         * True if this view should be drawn dimmed
         * when it's been offset from its default position.
         */
        boolean dimWhenOffset;

        Paint dimPaint;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(android.view.ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.weight = source.weight;
            this.spec = source.spec;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            this.weight = a.getFloat(0, 0);
            this.spec = a.getInteger(1, SPEC_FRONT);
            this.dimWhenOffset = a.getBoolean(2, false);
            a.recycle();
        }

    }

    static class SavedState extends BaseSavedState {
        boolean canSlide;
        boolean isOpen;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            canSlide = in.readInt() != 0;
            isOpen = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(canSlide ? 1 : 0);
            out.writeInt(isOpen ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    interface SlidingPanelLayoutImpl {
        void invalidateChildRegion(SlidingPaneLayout parent, View child);
    }

    static class SlidingPanelLayoutImplBase implements SlidingPanelLayoutImpl {
        public void invalidateChildRegion(SlidingPaneLayout parent, View child) {
            ViewCompat.postInvalidateOnAnimation(parent, child.getLeft(), child.getTop(),
                    child.getRight(), child.getBottom());
        }
    }

    static class SlidingPanelLayoutImplJB extends SlidingPanelLayoutImplBase {
        /*
         * Private API hacks! Nasty! Bad!
         *
         * In Jellybean, some optimizations in the hardware UI renderer
         * prevent a changed Paint on a View using a hardware layer from having
         * the intended effect. This twiddles some internal bits on the view to force
         * it to recreate the display list.
         */
        private Method mGetDisplayList;
        private Field mRecreateDisplayList;

        SlidingPanelLayoutImplJB() {
            try {
                mGetDisplayList = View.class.getDeclaredMethod("getDisplayList", (Class[]) null);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Couldn't fetch getDisplayList method; dimming won't work right.", e);
            }
            try {
                mRecreateDisplayList = View.class.getDeclaredField("mRecreateDisplayList");
                mRecreateDisplayList.setAccessible(true);
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "Couldn't fetch mRecreateDisplayList field; dimming will be slow.", e);
            }
        }

        @Override
        public void invalidateChildRegion(SlidingPaneLayout parent, View child) {
            if (mGetDisplayList != null && mRecreateDisplayList != null) {
                try {
                    mRecreateDisplayList.setBoolean(child, true);
                    mGetDisplayList.invoke(child, (Object[]) null);
                } catch (Exception e) {
                    Log.e(TAG, "Error refreshing display list state", e);
                }
            } else {
                // Slow path. REALLY slow path. Let's hope we don't get here.
                child.invalidate();
                return;
            }
            super.invalidateChildRegion(parent, child);
        }
    }

    static class SlidingPanelLayoutImplJBMR1 extends SlidingPanelLayoutImplBase {
        @Override
        public void invalidateChildRegion(SlidingPaneLayout parent, View child) {
            ViewCompat.setLayerPaint(child, ((LayoutParams) child.getLayoutParams()).dimPaint);
        }
    }
    
    private float dp2px(float dp){
    	// Get the screen's density scale
    	final float scale = getResources().getDisplayMetrics().density;
    	// Convert the dps to pixels, based on density scale
    	return dp * scale + 0.5f;
    }
}
