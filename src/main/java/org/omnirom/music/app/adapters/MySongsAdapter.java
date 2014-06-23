package org.omnirom.music.app.adapters;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v13.app.FragmentStatePagerAdapter;


import org.omnirom.music.app.fragments.AlbumsFragment;
import org.omnirom.music.app.fragments.ArtistsFragment;
import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.SongsFragment;

/**
 * Created by h4o on 19/06/2014.
 */
public class MySongsAdapter extends FragmentStatePagerAdapter {
    public MySongsAdapter(FragmentManager fm) {
       super(fm);


    }
    @Override
    public Fragment getItem(int i){
        Fragment fragment;
        switch (i){
            case 0:
                fragment =  SongsFragment.newInstance();
                break;
            case 1:
                fragment = ArtistsFragment.newInstance();

                break;
            case 2:
                fragment = AlbumsFragment.newInstance();
                break;
            default:
                fragment = PlaylistListFragment.newInstance();
                break;
        }
        return fragment;
    }
    @Override
    public int getCount() {
        return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        CharSequence title;
        switch (position) {
            case 0:
                title = "ALL SONGS";
                break;
            case 1:
                title = "ARTISTS";
                break;
            case 2:
                title = "ALBUMS";
                break;
            default:
                title = "PLAYLISTS";
                break;
        }
        return title;


    }

}
