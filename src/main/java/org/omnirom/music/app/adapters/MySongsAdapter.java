package org.omnirom.music.app.adapters;

import android.content.res.Resources;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import org.omnirom.music.app.R;
import org.omnirom.music.app.fragments.AlbumsFragment;
import org.omnirom.music.app.fragments.ArtistsListFragment;
import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.SongsFragment;

/**
 * Created by h4o on 19/06/2014.
 */
public class MySongsAdapter extends FragmentStatePagerAdapter {
    private Resources mResources;

    public MySongsAdapter(Resources res, FragmentManager fm) {
        super(fm);
        mResources = res;
    }

    @Override
    public Fragment getItem(int i) {
        Fragment fragment;
        switch (i) {
            case 0:
                fragment = SongsFragment.newInstance();
                break;

            case 1:
                fragment = ArtistsListFragment.newInstance();
                break;

            case 2:
                fragment = AlbumsFragment.newInstance();
                break;

            default:
                fragment = PlaylistListFragment.newInstance(false);
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
        String title;
        switch (position) {
            case 0:
                title = mResources.getString(R.string.tab_all_songs);
                break;
            case 1:
                title = mResources.getString(R.string.tab_artists);
                break;
            case 2:
                title = mResources.getString(R.string.tab_albums);
                break;
            default:
                title = mResources.getString(R.string.tab_playlists);
                break;
        }

        return title.toUpperCase();


    }

}
