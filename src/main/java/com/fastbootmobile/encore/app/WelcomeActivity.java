/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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

package com.fastbootmobile.encore.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.view.ViewPager;

import com.fastbootmobile.encore.app.adapters.WelcomeAdapter;


public class WelcomeActivity extends AppActivity {

    private static final String PREFS_WELCOME = "welcome";
    private static final String KEY_DONE = "done";

    private ViewPager mViewPager;

    public static boolean hasDoneWelcomeWizard(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_WELCOME, 0);
        return prefs.getBoolean(KEY_DONE, false);
    }

    public static void setDoneWelcomeWizard(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_WELCOME, 0);
        prefs.edit().putBoolean(KEY_DONE, true).apply();
    }

    public static void resetWelcomeWizard(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_WELCOME, 0);
        prefs.edit().putBoolean(KEY_DONE, false).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(new WelcomeAdapter(getSupportFragmentManager()));
    }

    public void showStep(int step) {
        mViewPager.setCurrentItem(step - 1, true);

        if (step == 2) {
            setDoneWelcomeWizard(this);
        }
    }
}
