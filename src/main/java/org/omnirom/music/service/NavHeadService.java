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

package org.omnirom.music.service;

import android.animation.Animator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import org.omnirom.music.app.DriveModeActivity;
import org.omnirom.music.app.R;

public class NavHeadService extends Service {
    private static final String TAG = "NavHeadService";
    private static final String PREFS = "NAVHEAD";
    private static final String PREF_HEAD_X = "head_x";
    private static final String PREF_HEAD_Y = "head_y";

    private WindowManager mWindowManager;
    private ImageView mHeadView;
    private WindowManager.LayoutParams mHeadLayoutParams;


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
        createHead();
        loadHeadPosition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
        mHeadView = new ImageView(this);
        mHeadView.setImageResource(R.drawable.album_placeholder);
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
                Intent intent = new Intent(getApplicationContext(), DriveModeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
    }

    private void loadHeadPosition() {
        // Load position from shared preferences
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        int headX = prefs.getInt(PREF_HEAD_X, 0);
        int headY = prefs.getInt(PREF_HEAD_Y, 100);

        setHeadPosition(headX, headY);
    }

    private void saveHeadPosition() {
        // Save position into shared preferences
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_HEAD_X, mHeadLayoutParams.x);
        editor.putInt(PREF_HEAD_Y, mHeadLayoutParams.y);
        editor.apply();
    }

    public void setHeadPosition(int x, int y) {
        mHeadLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        mHeadLayoutParams.x = x;
        mHeadLayoutParams.y = y;
        mHeadLayoutParams.width = getResources().getDimensionPixelSize(R.dimen.nav_head_size);
        mHeadLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.nav_head_size);

        mWindowManager.updateViewLayout(mHeadView, mHeadLayoutParams);
    }
}
