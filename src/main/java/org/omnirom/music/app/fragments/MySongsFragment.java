package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.MySongsAdapter;

/**
 * Created by h4o on 19/06/2014.
 */
public class MySongsFragment extends Fragment {
    private ViewPager mViewPager;
    private PagerTabStrip mTabStrip;
    private MySongsAdapter mSongsAdapter;
    private Handler mHandler;

    public static MySongsFragment newInstance() {
        MySongsFragment fragment = new MySongsFragment();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_my_songs, container, false);
        assert root != null;

        mSongsAdapter = new MySongsAdapter(getResources(), getChildFragmentManager());
        mViewPager = (ViewPager) root.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setAdapter(mSongsAdapter);

        mTabStrip = (PagerTabStrip) root.findViewById(R.id.pager_title_strip);
        mTabStrip.setTabIndicatorColorResource(R.color.primary_light);

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mHandler = new Handler();

        final MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_MY_SONGS);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mainActivity.setContentShadowTop(mTabStrip.getMeasuredHeight());
            }
        });
    }
}
