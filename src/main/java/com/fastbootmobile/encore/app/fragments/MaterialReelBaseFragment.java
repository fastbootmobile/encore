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

package com.fastbootmobile.encore.app.fragments;

import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.fastbootmobile.encore.app.ArtistActivity;
import com.fastbootmobile.encore.service.PlaybackService;
import com.getbase.floatingactionbutton.FloatingActionButton;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.ui.CircularPathAnimation;
import com.fastbootmobile.encore.app.ui.PlayPauseDrawable;
import com.fastbootmobile.encore.app.ui.ReverseAnimator;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.utils.Utils;

public class MaterialReelBaseFragment extends Fragment {
    private Handler mHandler;
    private RelativeLayout mBarLayout;
    private ImageView mBarPlay;
    private ImageView mBarNext;
    private ImageView mBarPrevious;
    private ImageView mBarRepeat;
    private ImageView mBarShuffle;
    private TextView mBarTitle;
    private float mSourceDeltaX;
    protected boolean mMaterialBarVisible = false;
    protected PlayPauseDrawable mBarDrawable;

    private static final float DISABLED_OPACITY = 0.5f;

    public MaterialReelBaseFragment() {
        mHandler = new Handler();
    }

    protected void setupMaterialReelBar(View rootView, View.OnClickListener fabClickListener) {
        // Material reel
        mBarLayout = (RelativeLayout) rootView.findViewById(R.id.layoutBar);
        mBarPlay = (ImageView) rootView.findViewById(R.id.btnBarPlay);
        mBarNext = (ImageView) rootView.findViewById(R.id.btnBarNext);
        mBarPrevious = (ImageView) rootView.findViewById(R.id.btnBarPrevious);
        mBarRepeat = (ImageView) rootView.findViewById(R.id.btnBarRepeat);
        mBarShuffle = (ImageView) rootView.findViewById(R.id.btnBarShuffle);
        mBarTitle = (TextView) rootView.findViewById(R.id.tvBarTitle);

        mBarDrawable = new PlayPauseDrawable(getResources(), 1);
        mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
        mBarDrawable.setYOffset(-3);
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
        mBarRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isRepeat = !PlaybackProxy.isRepeatMode();
                PlaybackProxy.setRepeatMode(isRepeat);
                v.setAlpha(isRepeat ? 1 : DISABLED_OPACITY);
            }
        });
        mBarShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isShuffle = !PlaybackProxy.isShuffleMode();
                PlaybackProxy.setShuffleMode(isShuffle);
                v.setAlpha(isShuffle ? 1 : DISABLED_OPACITY);
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
                mSourceDeltaX = ((mBarLayout.getMeasuredWidth() / 2) - playFab.getLeft())
                        - playFab.getMeasuredWidth() / 2;

                CircularPathAnimation anim = new CircularPathAnimation(0, mSourceDeltaX, 0, 125 * DP);
                anim.setDuration(400);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.setFillAfter(true);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isDetached() || getActivity() == null) {
                            return;
                        }
                        try {
                            mBarLayout.setAlpha(1.0f);
                            mBarLayout.setBackgroundColor(playFab.getNormalColor());

                            Utils.animateHeadingReveal(mBarLayout, mBarLayout.getMeasuredWidth() / 2,
                                    (int) (mBarLayout.getMeasuredHeight() / 1.25f), ArtistActivity.BACK_DELAY);

                            playFab.animate().setStartDelay(80)
                                    .alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(150).start();

                            mBarPlay.setAlpha(0f);

                            mBarNext.setAlpha(0f);
                            mBarNext.setTranslationX(-16 * DP);
                            mBarNext.setScaleX(0);
                            mBarNext.setScaleY(0);
                            mBarPrevious.setAlpha(0f);
                            mBarPrevious.setTranslationX(16 * DP);
                            mBarPrevious.setScaleX(0);
                            mBarPrevious.setScaleY(0);
                            mBarRepeat.setAlpha(0f);
                            mBarRepeat.setTranslationX(-16 * DP);
                            mBarRepeat.setScaleX(0);
                            mBarRepeat.setScaleY(0);
                            mBarShuffle.setAlpha(0f);
                            mBarShuffle.setTranslationX(16 * DP);
                            mBarShuffle.setScaleX(0);
                            mBarShuffle.setScaleY(0);

                            mBarPlay.animate().alpha(1).setDuration(100).start();
                            mBarNext.animate().alpha(1).scaleX(1).scaleY(1)
                                    .translationX(0).setDuration(250).start();
                            mBarRepeat.animate().alpha(PlaybackProxy.isRepeatMode() ? 1 : DISABLED_OPACITY)
                                    .scaleX(1).scaleY(1)
                                    .translationX(0).setDuration(250).start();
                            mBarPrevious.animate().alpha(1).translationX(0).scaleX(1).scaleY(1)
                                    .setDuration(250).start();
                            mBarShuffle.animate().alpha(PlaybackProxy.isShuffleMode() ? 1 : DISABLED_OPACITY)
                                    .translationX(0).scaleX(1).scaleY(1)
                                    .setDuration(250).start();
                        } catch (IllegalStateException ignore) { }
                    }
                }, 300);

                playFab.startAnimation(anim);
            }
        });
    }

    protected void hideMaterialReelBar(final FloatingActionButton playFab) {
        if (!mMaterialBarVisible) {
            return;
        }

        mMaterialBarVisible = false;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final int DP = getResources().getDimensionPixelSize(R.dimen.one_dp);

                Utils.animateHeadingHiding(mBarLayout, mBarLayout.getMeasuredWidth() / 2,
                        (int) (mBarLayout.getMeasuredHeight() / 1.25f), ArtistActivity.BACK_DELAY);
                mBarLayout.setAlpha(1.0f);

                mBarPlay.setAlpha(1f);

                mBarNext.setAlpha(1f);
                mBarNext.setTranslationX(0);
                mBarNext.setScaleX(1);
                mBarNext.setScaleY(1);
                mBarShuffle.setTranslationX(0);
                mBarShuffle.setScaleX(1);
                mBarShuffle.setScaleY(1);
                mBarPrevious.setAlpha(1f);
                mBarPrevious.setTranslationX(0);
                mBarPrevious.setScaleX(1);
                mBarPrevious.setScaleY(1);
                mBarRepeat.setTranslationX(0);
                mBarRepeat.setScaleX(1);
                mBarRepeat.setScaleY(1);

                mBarPlay.animate().alpha(0).setDuration(100).start();
                mBarNext.animate().alpha(0).scaleX(0).scaleY(0)
                        .translationX(16 * DP).setDuration(250).start();
                mBarRepeat.animate().alpha(0).scaleX(0).scaleY(0)
                        .translationX(16 * DP).setDuration(250).start();
                mBarPrevious.animate().alpha(0).translationX(-16 * DP).scaleX(0).scaleY(0)
                        .setDuration(250).start();
                mBarShuffle.animate().alpha(0).scaleX(0).scaleY(0)
                        .translationX(16 * DP).setDuration(250).start();


                playFab.animate().setStartDelay(150)
                        .alpha(1).scaleX(1.0f).scaleY(1.0f).setDuration(150).start();

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        CircularPathAnimation anim = new CircularPathAnimation(0, mSourceDeltaX, 0, 125 * DP);
                        anim.setDuration(400);
                        anim.setInterpolator(new ReverseAnimator(new AccelerateDecelerateInterpolator()));
                        anim.setFillAfter(true);


                        playFab.startAnimation(anim);
                    }
                }, 150);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBarLayout.setVisibility(View.GONE);
                    }
                }, 500);

            }
        });
    }

    protected void setReelBarTitle(String title) {
        mBarTitle.setText(title);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Update playback status, if needed
        if (mMaterialBarVisible) {
            if (PlaybackProxy.getState() == PlaybackService.STATE_PAUSED) {
                mBarDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
            }
        }
    }
}
