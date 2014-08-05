package org.omnirom.music.app.fragments;

import android.support.v4.app.Fragment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.ObservableScrollView;

/**
 * Root fragment for all usual fragments ; provides the sticky search bar
 */
public class AbstractRootFragment extends Fragment implements ObservableScrollView.ScrollViewListener {

    private static final int STATE_ONSCREEN = 0;
    private static final int STATE_OFFSCREEN = 1;
    private static final int STATE_RETURNING = 2;

    private EditText mQuickReturnView;
    private View mPlaceholderView;
    private ObservableScrollView mObservableScrollView;
    private ScrollSettleHandler mScrollSettleHandler = new ScrollSettleHandler();
    private int mMinRawY = 0;
    private int mState = STATE_ONSCREEN;
    private int mQuickReturnHeight;
    private int mMaxScrollY;

    private class ScrollSettleHandler extends Handler {
        private static final int SETTLE_DELAY_MILLIS = 100;

        private int mSettledScrollY = Integer.MIN_VALUE;
        private boolean mSettleEnabled;

        public void onScroll(int scrollY) {
            if (mSettledScrollY != scrollY) {
                // Clear any pending messages and post delayed
                removeMessages(0);
                sendEmptyMessageDelayed(0, SETTLE_DELAY_MILLIS);
                mSettledScrollY = scrollY;
            }
        }

        public void setSettleEnabled(boolean settleEnabled) {
            mSettleEnabled = settleEnabled;
        }

        @Override
        public void handleMessage(Message msg) {
            // Handle the scroll settling.
            if (STATE_RETURNING == mState && mSettleEnabled) {
                int mDestTranslationY;
                if (mSettledScrollY - mQuickReturnView.getTranslationY() > mQuickReturnHeight / 2) {
                    mState = STATE_OFFSCREEN;
                    mDestTranslationY = Math.max(
                            mSettledScrollY - mQuickReturnHeight,
                            mPlaceholderView.getTop());
                } else {
                    mDestTranslationY = mSettledScrollY;
                }

                mMinRawY = mPlaceholderView.getTop() - mQuickReturnHeight - mDestTranslationY;
                mQuickReturnView.animate().translationY(mDestTranslationY);
            }
            mSettledScrollY = Integer.MIN_VALUE; // reset
        }
    }

   public void setupSearchBox(View root) {
        // Setup sticky search box, if the layout is compatible
        if (root != null && (root instanceof ObservableScrollView)) {
            mObservableScrollView = (ObservableScrollView) root;
            mPlaceholderView = mObservableScrollView.findViewById(R.id
                    .sticky_search_box_placeholder);
            mQuickReturnView = (EditText) mObservableScrollView.findViewById(R.id.sticky_search_box);
            mObservableScrollView.setOnScrollListener(this);

            if (mObservableScrollView.getViewTreeObserver() != null) {
                mObservableScrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                onScroll(mObservableScrollView, 0, mObservableScrollView.getScrollY(), 0,
                                        0);
                                mMaxScrollY = mObservableScrollView.computeVerticalScrollRange()
                                        - mObservableScrollView.getHeight();
                                if (getActivity() != null) {
                                    mQuickReturnHeight = mQuickReturnView.getHeight()
                                            + Utils.getStatusBarHeight(getResources());
                                }
                            }
                        }
                );
            }
        }
    }


    @Override
    public void onScroll(ObservableScrollView scrollView, int x, int y, int oldx, int oldy) {
        y = Math.min(mMaxScrollY, y);

        mScrollSettleHandler.onScroll(y);

        int rawY = mPlaceholderView.getTop() - y;
        int translationY = 0;

        switch (mState) {
            case STATE_OFFSCREEN:
                if (rawY <= mMinRawY) {
                    mMinRawY = rawY;
                } else {
                    mState = STATE_RETURNING;
                }
                translationY = rawY;
                break;

            case STATE_ONSCREEN:
                if (rawY < -mQuickReturnHeight) {
                    mState = STATE_OFFSCREEN;
                    mMinRawY = rawY;
                }
                translationY = rawY;
                break;

            case STATE_RETURNING:
                translationY = (rawY - mMinRawY) - mQuickReturnHeight;
                if (translationY > 0) {
                    translationY = 0;
                    mMinRawY = rawY - mQuickReturnHeight;
                }

                if (rawY > 0) {
                    mState = STATE_ONSCREEN;
                    translationY = rawY;
                }

                if (translationY < -mQuickReturnHeight) {
                    mState = STATE_OFFSCREEN;
                    mMinRawY = rawY;
                }
                break;
        }
        mQuickReturnView.animate().cancel();
        mQuickReturnView.setTranslationY(translationY + y);
    }
}
