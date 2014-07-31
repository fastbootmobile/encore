package org.omnirom.music.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * Created by h4o on 30/07/2014.
 */
public class AlbumListView extends ListView {
    private ImageView ivHero;
    private int mImageViewHeight;
    public AlbumListView(Context context) {
        super(context);
    }
    public AlbumListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public void setHero(ImageView hero){
        ivHero = hero;
        mImageViewHeight = ivHero.getLayoutParams().height;
    }
    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        View firstView = (View) ivHero.getParent();
        Log.d("AlbumListView","scroll changed "+l+":"+t + " "+oldl+":"+oldt );

        // firstView.getTop < getPaddingTop means mImageView will be covered by top padding,
        // so we can layout it to make it shorter
        Log.d("AlbumListView",firstView.getTop() +":"+ getPaddingTop() + "  " +  ivHero.getHeight() + ":"+ mImageViewHeight );
        if (firstView.getTop() < getPaddingTop() && ivHero.getHeight() > mImageViewHeight) {
            Log.d("AlbumListView","changed something !");
            ivHero.getLayoutParams().height = Math.max(ivHero.getHeight() - (getPaddingTop() - firstView.getTop()), mImageViewHeight);
            // to set the firstView.mTop to 0,
            // maybe use View.setTop() is more easy, but it just support from Android 3.0 (API 11)
            firstView.layout(firstView.getLeft(), 0, firstView.getRight(), firstView.getHeight());
            ivHero.requestLayout();
        }
    }
}
