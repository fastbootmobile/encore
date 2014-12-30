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

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;

/**
 * Adapter for the side-bar navigation drawer
 */
public class NavDrawerAdapter extends BaseAdapter {
    private static final int REGULAR_COUNT = 6;
    private static final int SPECIAL_COUNT = 2;

    private int mActiveEntry = 0;

    public static class ViewHolder {
        public TextView tvText;
        public View tvDivider;
    }

    public void setActive(int entry) {
        mActiveEntry = entry;
        notifyDataSetChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return REGULAR_COUNT + SPECIAL_COUNT;
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

    @Override
    public int getItemViewType(int position) {
        if (position >= 0 && position < REGULAR_COUNT) {
            return 1;
        } else {
            return 2;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());

            if (getItemViewType(i) == 1) {
                view = inflater.inflate(R.layout.nav_drawer_regular_item, viewGroup, false);

                tag = new ViewHolder();
                tag.tvText = (TextView) view.findViewById(android.R.id.text1);
                view.setTag(tag);
            } else {
                view = inflater.inflate(R.layout.nav_drawer_special_item, viewGroup, false);

                tag = new ViewHolder();
                tag.tvText = (TextView) view.findViewById(android.R.id.text1);
                tag.tvDivider = view.findViewById(R.id.navdrawer_divider);
                view.setTag(tag);
            }
        } else {
            tag = (ViewHolder) view.getTag();
        }

        if (i == mActiveEntry) {
            tag.tvText.setTextColor(tag.tvText.getResources().getColor(R.color.primary));
            view.setBackgroundColor(0xFFEAEAEA);
        } else {
            tag.tvText.setTextColor(tag.tvText.getResources().getColor(R.color.text_regular));
            int[] attrs = new int[] { android.R.attr.selectableItemBackground };
            TypedArray ta = view.getContext().obtainStyledAttributes(attrs);
            Drawable drawableFromTheme = ta.getDrawable(0);
            ta.recycle();

            Utils.setViewBackground(view, drawableFromTheme);
        }

        if (tag.tvDivider != null) {
            if (i == REGULAR_COUNT) {
                tag.tvDivider.setVisibility(View.VISIBLE);
            } else {
                tag.tvDivider.setVisibility(View.GONE);
            }
        }

        switch (i+1) {
            case MainActivity.SECTION_LISTEN_NOW:
                tag.tvText.setText(R.string.title_section_listen_now);
                if (i == mActiveEntry) {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_listen_now_active);
                } else {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_listen_now);
                }
                break;

            case MainActivity.SECTION_MY_SONGS:
                tag.tvText.setText(R.string.title_section_my_songs);
                if (i == mActiveEntry) {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_library_active);
                } else {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_library);
                }
                break;

            case MainActivity.SECTION_PLAYLISTS:
                tag.tvText.setText(R.string.title_section_playlists);
                if (i == mActiveEntry) {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_playlist_active);
                } else {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_playlist);
                }
                break;

            case MainActivity.SECTION_AUTOMIX:
                tag.tvText.setText(R.string.title_section_automix);
                if (i == mActiveEntry) {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_automix_active);
                } else {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_automix);
                }
                break;

            case MainActivity.SECTION_RECOGNITION:
                tag.tvText.setText(R.string.title_section_recognition);
                if (i == mActiveEntry) {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_recognition_active);
                } else {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_recognition);
                }
                break;

            case MainActivity.SECTION_HISTORY:
                tag.tvText.setText(R.string.section_history);
                if (i == mActiveEntry) {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_history_active);
                } else {
                    setCompoundCompat(tag.tvText, R.drawable.ic_nav_history);
                }
                break;

            // Special actions
            case MainActivity.SECTION_NOW_PLAYING:
                tag.tvText.setText(R.string.title_activity_playback_queue);
                break;

            case MainActivity.SECTION_DRIVE_MODE:
                tag.tvText.setText(R.string.drive_mode);
                break;

        }

        return view;
    }

    public static void setCompoundCompat(@NonNull TextView text, @DrawableRes int id) {
        Drawable drawable = text.getResources().getDrawable(id);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            text.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null);
        } else {
            text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
    }
}
