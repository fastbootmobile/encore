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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ogaclejapan.smarttablayout.SmartTabLayout;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.MySongsAdapter;

/**
 * Created by h4o on 19/06/2014.
 */
public class MySongsFragment extends Fragment {
    private ViewPager mViewPager;
    private SmartTabLayout mTabStrip;
    private MySongsAdapter mSongsAdapter;
    private Handler mHandler;

    public static MySongsFragment newInstance() {
        return new MySongsFragment();
    }

    public MySongsFragment() {
        mHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_my_songs, container, false);
        assert root != null;

        mSongsAdapter = new MySongsAdapter(getResources(), getChildFragmentManager());
        mViewPager = (ViewPager) root.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(4);
        mViewPager.setAdapter(mSongsAdapter);

        mTabStrip = (SmartTabLayout) root.findViewById(R.id.pager_title_strip);
        mTabStrip.setViewPager(mViewPager);

        return root;
    }

    private void updateShadowTop() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final MainActivity act = (MainActivity) getActivity();
                if (act != null && mTabStrip != null) {
                    act.setContentShadowTop(mTabStrip.getMeasuredHeight());
                }
            }
        });
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        final MainActivity mainActivity = (MainActivity) activity;
        if (mainActivity != null) {
            mainActivity.onSectionAttached(MainActivity.SECTION_MY_SONGS);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateShadowTop();
    }
}
