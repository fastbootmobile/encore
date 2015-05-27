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

package com.fastbootmobile.encore.service;

import android.animation.Animator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.fastbootmobile.encore.app.DriveModeActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.ui.AlbumArtImageView;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Song;

public class NavHeadService extends Service {
    private static final String TAG = "NavHeadService";
    private static final String PREFS = "NAVHEAD";
    private static final String PREF_HEAD_X = "head_x_";
    private static final String PREF_HEAD_Y = "head_y_";

    private WindowManager mWindowManager;
    private AlbumArtImageView mHeadView;
    private WindowManager.LayoutParams mHeadLayoutParams;
    private Handler mHandler;
    private BasePlaybackCallback mPlaybackCallback = new BasePlaybackCallback() {
        @Override
        public void onSongStarted(boolean buffering, final Song s) throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mHeadView != null) {
                        mHeadView.loadArtForSong(s);
                    }
                }
            });
        }
    };

    public NavHeadService() {
    }


    @Override
    public IBinder onBind(Intent intent) {
        // Not used
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mHandler = new Handler();
        createHead();
        loadHeadPosition();
        PlaybackProxy.addCallback(mPlaybackCallback);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadHeadPosition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PlaybackProxy.removeCallback(mPlaybackCallback);
        if (mHeadView != null) {
            mHeadView.animate().scaleX(0.5f).scaleY(0.5f).alpha(0.0f).setDuration(500)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mWindowManager.removeView(mHeadView);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            mWindowManager.removeView(mHeadView);
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    }).start();
        }
    }

    private void createHead() {
        mHeadView = new AlbumArtImageView(this);
        mHeadView.setCrossfade(true);
        mHeadView.setScaleX(0.5f);
        mHeadView.setScaleY(0.5f);
        mHeadView.setAlpha(0.0f);
        mHeadView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                .setDuration(500).setInterpolator(new AccelerateDecelerateInterpolator()).start();

        mHeadLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mWindowManager.addView(mHeadView, mHeadLayoutParams);

        mHeadView.setOnTouchListener(new View.OnTouchListener() {
            private int mDownX;
            private int mDownY;
            private float mRawDownX;
            private float mRawDownY;
            private int mTotalMovedX;
            private int mTotalMovedY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownX = mHeadLayoutParams.x;
                        mDownY = mHeadLayoutParams.y;
                        mRawDownX = event.getRawX();
                        mRawDownY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (mTotalMovedX > 10 || mTotalMovedY > 10) {
                            saveHeadPosition();
                        } else {
                            v.callOnClick();
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - mRawDownX);
                        int deltaY = (int) (event.getRawY() - mRawDownY);

                        setHeadPosition(mDownX + deltaX, mDownY + deltaY);

                        mTotalMovedX += Math.abs(deltaX);
                        mTotalMovedY += Math.abs(deltaY);
                        return true;
                }
                return false;
            }
        });

        mHeadView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), DriveModeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        // Assign initial album art
        Song currentSong = PlaybackProxy.getCurrentTrack();
        if (currentSong != null) {
            mHeadView.loadArtForSong(currentSong);
        }
    }

    private void loadHeadPosition() {
        final int rotation = mWindowManager.getDefaultDisplay().getRotation();

        // Load position from shared preferences
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        int headX = prefs.getInt(PREF_HEAD_X + rotation, 0);
        int headY = prefs.getInt(PREF_HEAD_Y + rotation, 100);

        setHeadPosition(headX, headY);
    }

    private void saveHeadPosition() {
        final int rotation = mWindowManager.getDefaultDisplay().getRotation();

        // Save position into shared preferences
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt(PREF_HEAD_X + rotation, mHeadLayoutParams.x);
        editor.putInt(PREF_HEAD_Y + rotation, mHeadLayoutParams.y);

        editor.apply();
    }

    public void setHeadPosition(int x, int y) {
        final int sizePx = getResources().getDimensionPixelSize(R.dimen.nav_head_size);

        mHeadLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        mHeadLayoutParams.x = x;
        mHeadLayoutParams.y = y;
        mHeadLayoutParams.width = sizePx;
        mHeadLayoutParams.height = sizePx;

        mWindowManager.updateViewLayout(mHeadView, mHeadLayoutParams);
    }
}
