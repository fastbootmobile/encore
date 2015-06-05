package com.fastbootmobile.encore.app.ui;

import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class SpaceItemDecorator extends RecyclerView.ItemDecoration {
    private int mSpace;

    public SpaceItemDecorator(int space) {
        mSpace = space;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view,
                               RecyclerView parent, RecyclerView.State state) {
        outRect.left = mSpace;
        outRect.right = mSpace;
        outRect.bottom = mSpace;

        // Add top margin only for the first item to avoid double space between items
        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top = mSpace;
        }
    }
}