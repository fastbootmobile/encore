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

import android.annotation.TargetApi;
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

import com.fastbootmobile.encore.app.MainActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.utils.Utils;

/**
 * Adapter for the side-bar navigation drawer
 */
public class NavDrawerAdapter extends BaseAdapter {
    private static final int REGULAR_COUNT = 8;
    private static final int SPECIAL_COUNT = 2;

    private static final int TYPE_REGULAR = 1;
    private static final int TYPE_DIVIDER = 2;
    private static final int TYPE_SPECIAL = 3;

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
        return REGULAR_COUNT + 1 + SPECIAL_COUNT;
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
    public boolean isEnabled(int position) {
        return position != REGULAR_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areAllItemsEnabled() {
        return false;
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
        } else if (position == REGULAR_COUNT) {
            return 2;
        } else {
            return 3;
        }
    }

    private boolean isActiveEntry(int position) {
        return position == mActiveEntry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder tag;
        if (view == null) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            final int itemViewType = getItemViewType(i);

            if (itemViewType == TYPE_REGULAR) {
                view = inflater.inflate(R.layout.item_nav_drawer_regular, viewGroup, false);

                tag = new ViewHolder();
                tag.tvText = (TextView) view.findViewById(android.R.id.text1);
                view.setTag(tag);
            } else if (itemViewType == TYPE_DIVIDER) {
                view = inflater.inflate(R.layout.item_nav_drawer_divider, viewGroup, false);

                tag = new ViewHolder();
                tag.tvDivider = view;
                view.setTag(tag);
            } else if (itemViewType == TYPE_SPECIAL) {
                view = inflater.inflate(R.layout.item_nav_drawer_special, viewGroup, false);

                tag = new ViewHolder();
                tag.tvText = (TextView) view.findViewById(android.R.id.text1);
                view.setTag(tag);
            } else {
                throw new RuntimeException("Should not be here");
            }
        } else {
            tag = (ViewHolder) view.getTag();
        }

        if (isActiveEntry(i)) {
            tag.tvText.setTextColor(tag.tvText.getResources().getColor(R.color.primary));
            view.setBackgroundColor(0xFFEAEAEA);
        } else if (tag.tvText != null) {
            tag.tvText.setTextColor(tag.tvText.getResources().getColor(R.color.text_regular));
            int[] attrs = new int[] { android.R.attr.selectableItemBackground };
            TypedArray ta = view.getContext().obtainStyledAttributes(attrs);
            Drawable drawableFromTheme = ta.getDrawable(0);
            ta.recycle();

            Utils.setViewBackground(view, drawableFromTheme);
        }

        if (tag.tvText != null) {
            switch (i + 1) {
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

                case MainActivity.SECTION_LYRICS:
                    tag.tvText.setText(R.string.section_lyrics);
                    if (i == mActiveEntry) {
                        setCompoundCompat(tag.tvText, R.drawable.ic_nav_lyrics_active);
                    } else {
                        setCompoundCompat(tag.tvText, R.drawable.ic_nav_lyrics);
                    }
                    break;

                case MainActivity.SECTION_NOW_PLAYING:
                    tag.tvText.setText(R.string.title_activity_playback_queue);
                    if (i == mActiveEntry) {
                        setCompoundCompat(tag.tvText, R.drawable.ic_nav_nowplaying_active);
                    } else {
                        setCompoundCompat(tag.tvText, R.drawable.ic_nav_nowplaying);
                    }
                    break;

                // Special actions (offset with divider)
                case MainActivity.SECTION_DRIVE_MODE+1:
                    tag.tvText.setText(R.string.drive_mode);
                    setCompoundCompat(tag.tvText, 0);
                    break;

                case MainActivity.SECTION_SETTINGS+1:
                    tag.tvText.setText(R.string.settings);
                    setCompoundCompat(tag.tvText, 0);
                    break;

            }
        }

        return view;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void setCompoundCompat(@NonNull TextView text, @DrawableRes int id) {
        Drawable drawable = null;

        if (id > 0) {
            if (Utils.hasLollipop()) {
                drawable = text.getResources().getDrawable(id, null);
            } else {
                drawable = text.getResources().getDrawable(id);
            }
        }

        if (Utils.hasJellyBeanMR1()) {
            text.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null);
        } else {
            text.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }
    }
}
