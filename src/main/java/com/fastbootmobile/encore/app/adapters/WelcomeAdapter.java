package com.fastbootmobile.encore.app.adapters;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.fastbootmobile.encore.app.fragments.WelcomeFragment;

public class WelcomeAdapter extends FragmentStatePagerAdapter {
    public WelcomeAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        return WelcomeFragment.newInstance(position + 1);
    }

    @Override
    public int getCount() {
        return 4;
    }
}
