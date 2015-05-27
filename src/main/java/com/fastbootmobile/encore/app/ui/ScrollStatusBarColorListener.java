package com.fastbootmobile.encore.app.ui;

import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.AbsListView;

import com.fastbootmobile.encore.utils.AlphaForegroundColorSpan;

import java.util.Dictionary;
import java.util.Hashtable;

public abstract class ScrollStatusBarColorListener implements AbsListView.OnScrollListener {
    protected Dictionary<Integer, Integer> mListViewItemHeights = new Hashtable<>();
    protected ColorDrawable mColorDrawable;
    protected AlphaForegroundColorSpan mAlphaSpan;

    protected ScrollStatusBarColorListener() {
        mColorDrawable = new ColorDrawable();
        mAlphaSpan = new AlphaForegroundColorSpan(0xFFFFFFFF);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    protected int getScroll(AbsListView listView) {
        View c = listView.getChildAt(0); //this is the first visible row
        int scrollY = -c.getTop();
        mListViewItemHeights.put(listView.getFirstVisiblePosition(), c.getHeight());
        for (int i = 0; i < listView.getFirstVisiblePosition(); ++i) {
            if (mListViewItemHeights.get(i) != null) // (this is a sanity check)
                scrollY += mListViewItemHeights.get(i); //add all heights of the views that are gone
        }
        return scrollY;
    }
}
