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

package org.omnirom.music.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;

/**
 * Adapter for the side-bar navigation drawer
 */
public class NavDrawerAdapter extends BaseAdapter {
    public static class ViewHolder {
        public TextView tvText;
        public ImageView ivLogo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return 6;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getItem(int i) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int i) {
        return i;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            view = inflater.inflate(R.layout.nav_drawer_list_item_activated, viewGroup, false);

            tag = new ViewHolder();
            tag.tvText = (TextView) view.findViewById(android.R.id.text1);
            tag.ivLogo = (ImageView) view.findViewById(android.R.id.icon);
            view.setTag(tag);
        } else {
            tag = (ViewHolder) view.getTag();
        }

        switch (i+1) {
            case MainActivity.SECTION_LISTEN_NOW:
                tag.tvText.setText(R.string.title_section_listen_now);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_listen_now);
                break;

            case MainActivity.SECTION_MY_SONGS:
                tag.tvText.setText(R.string.title_section_my_songs);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_library);
                break;

            case MainActivity.SECTION_PLAYLISTS:
                tag.tvText.setText(R.string.title_section_playlists);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_playlist);
                break;

            case MainActivity.SECTION_AUTOMIX:
                tag.tvText.setText(R.string.title_section_automix);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_automix);
                break;

            case MainActivity.SECTION_RECOGNITION:
                tag.tvText.setText(R.string.title_section_recognition);
                tag.ivLogo.setImageResource(R.drawable.ic_mic);
                break;

            case MainActivity.SECTION_NOW_PLAYING:
                tag.tvText.setText(R.string.title_activity_playback_queue);
                tag.ivLogo.setImageResource(R.drawable.ic_nav_nowplaying);
        }

        return view;
    }
}
