package org.omnirom.music.app.adapters;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v13.app.FragmentStatePagerAdapter;


import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.SongsFragment;

/**
 * Created by h4o on 19/06/2014.
 */
public class MySongsAdapter extends FragmentStatePagerAdapter {
    private SongsFragment mSongsFragment;
    private PlaylistListFragment mPlaylistListFragment;
    public MySongsAdapter(FragmentManager fm) {
       super(fm);
        mSongsFragment = new SongsFragment();
        mPlaylistListFragment = new PlaylistListFragment();

    }
    @Override
    public Fragment getItem(int i){
        Fragment fragment;
        switch (i){
            case 0:
                fragment =  SongsFragment.newInstance();
                break;
            case 1:
                fragment = PlaylistListFragment.newInstance();
                break;
            default:
                fragment = PlaylistListFragment.newInstance();
                break;
        }
        return fragment;
    }
    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        CharSequence title;
        switch (position) {
            case 0:
                title = "ALL SONGS";
                break;
            case 1:
                title = "PLAYLISTS";
                break;
            default:
                title = "OBJECT "+position;
                break;
        }
        return title;


    }

}
