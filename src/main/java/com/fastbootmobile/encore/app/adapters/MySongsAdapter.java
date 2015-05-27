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

package com.fastbootmobile.encore.app.adapters;

import android.content.res.Resources;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.fragments.AlbumsFragment;
import com.fastbootmobile.encore.app.fragments.ArtistsListFragment;
import com.fastbootmobile.encore.app.fragments.PlaylistListFragment;
import com.fastbootmobile.encore.app.fragments.SongsFragment;

/**
 * Adapter to display the "My Songs" fragment ViewPager tabs
 */
public class MySongsAdapter extends FragmentStatePagerAdapter {
    private static final int TAB_ALL_SONGS  = 0;
    private static final int TAB_ARTISTS    = 1;
    private static final int TAB_ALBUMS     = 2;
    private static final int TAB_PLAYLISTS  = 3;
    private static final int TAB_COUNT = 4;

    private Resources mResources;

    /**
     * Default constructor
     * @param res Valid resources context
     * @param fm The host fragment manager
     */
    public MySongsAdapter(Resources res, FragmentManager fm) {
        super(fm);
        mResources = res;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Fragment getItem(int i) {
        Fragment fragment;

        switch (i) {
            case TAB_ALL_SONGS:
                fragment = SongsFragment.newInstance();
                break;

            case TAB_ARTISTS:
                fragment = ArtistsListFragment.newInstance();
                break;

            case TAB_ALBUMS:
                fragment = AlbumsFragment.newInstance();
                break;

            case TAB_PLAYLISTS:
                fragment = PlaylistListFragment.newInstance(false);
                break;

            default:
                throw new IllegalStateException("Invalid tab index: " + i);
        }

        return fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return TAB_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CharSequence getPageTitle(int position) {
        String title;

        switch (position) {
            case TAB_ALL_SONGS:
                title = mResources.getString(R.string.tab_all_songs);
                break;

            case TAB_ARTISTS:
                title = mResources.getString(R.string.tab_artists);
                break;

            case TAB_ALBUMS:
                title = mResources.getString(R.string.tab_albums);
                break;

            case TAB_PLAYLISTS:
                title = mResources.getString(R.string.tab_playlists);
                break;

            default:
                throw new IllegalStateException("Invalid tab index: " + position);
        }

        return title.toUpperCase();
    }

}
