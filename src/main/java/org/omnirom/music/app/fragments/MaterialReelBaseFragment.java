/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package org.omnirom.music.app.fragments;

import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.getbase.floatingactionbutton.FloatingActionButton;

import org.omnirom.music.app.R;
import org.omnirom.music.app.ui.CircularPathAnimation;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.framework.PlaybackProxy;
import org.omnirom.music.utils.Utils;

public class MaterialReelBaseFragment extends Fragment {
    private Handler mHandler;
    private RelativeLayout mBarLayout;
    private ImageView mBarPlay;
    private ImageView mBarNext;
    private ImageView mBarPrevious;
    private TextView mBarTitle;
    protected boolean mMaterialBarVisible = false;
    protected PlayPauseDrawable mBarDrawable;

    public MaterialReelBaseFragment() {
        mHandler = new Handler();
    }

    protected void setupMaterialReelBar(View rootView, View.OnClickListener fabClickListener) {
        // Material reel
        mBarLayout = (RelativeLayout) rootView.findViewById(R.id.layoutBar);
        mBarPlay = (ImageView) rootView.findViewById(R.id.btnBarPlay);
        mBarNext = (ImageView) rootView.findViewById(R.id.btnBarNext);
        mBarPrevious = (ImageView) rootView.findViewById(R.id.btnBarPrevious);
        mBarTitle = (TextView) rootView.findViewById(R.id.tvBarTitle);

        mBarDrawable = new PlayPauseDrawable(getResources(), 1);
        mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mBarDrawable.setYOffset(-6);
        mBarDrawable.setColor(0xFFFFFFFF);

        mBarPlay.setImageDrawable(mBarDrawable);

        mBarPlay.setOnClickListener(fabClickListener);
        mBarNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaybackProxy.next();
            }
        });
        mBarPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaybackProxy.previous();
            }
        });
    }

    protected void showMaterialReelBar(final FloatingActionButton playFab) {
        if (mMaterialBarVisible) {
            return;
        }

        mMaterialBarVisible = true;
        mBarLayout.setVisibility(View.VISIBLE);
        mBarLayout.setAlpha(0);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final int DP = getResources().getDimensionPixelSize(R.dimen.one_dp);

                CircularPathAnimation anim = new CircularPathAnimation(0, -128 * DP, 0, 128 * DP);
                anim.setDuration(400);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.setFillAfter(true);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBarLayout.setAlpha(1.0f);
                        mBarLayout.setBackgroundColor(playFab.getNormalColor());
                        Utils.animateHeadingReveal(mBarLayout, mBarLayout.getMeasuredWidth() / 2,
                                (int) (mBarLayout.getMeasuredHeight() / 1.25f));

                        playFab.animate().setStartDelay(30)
                                .alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(150).start();

                        mBarPlay.setAlpha(0f);

                        mBarNext.setAlpha(0f);
                        mBarNext.setTranslationX(-8 * DP);
                        mBarPrevious.setAlpha(0f);
                        mBarPrevious.setTranslationX(8 * DP);

                        mBarPlay.animate().alpha(1).setDuration(100).start();
                        mBarNext.animate().alpha(1).translationX(0).setDuration(250).start();
                        mBarPrevious.animate().alpha(1).translationX(0).setDuration(250).start();
                    }
                }, 300);

                playFab.startAnimation(anim);
            }
        });
    }

    protected void setReelBarTitle(String title) {
        mBarTitle.setText(title);
    }

}
