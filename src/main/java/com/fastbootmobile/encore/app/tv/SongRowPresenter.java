/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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

package com.fastbootmobile.encore.app.tv;

import android.support.v17.leanback.widget.RowPresenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.utils.Utils;

public class SongRowPresenter extends RowPresenter {

    private View.OnClickListener mSongClickListener;
    private Song mCurrentSong;

    SongRowPresenter(View.OnClickListener songClickListener) {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);
        mSongClickListener = songClickListener;
    }

    public void setCurrentSong(Song song) {
        mCurrentSong = song;
    }

    @Override
    protected ViewHolder createRowViewHolder(ViewGroup parent) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_song_row, parent, false));
    }

    @Override
    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
        final SongRow songRow = (SongRow) item;
        final Song song = songRow.getSong();
        final String trackTitle = song.getTitle();
        final long trackDuration = song.getDuration();
        final String artistName = getArtistName(song);

        // Don't display the divider on first row
        vh.view.findViewById(R.id.song_row_separator)
                .setVisibility(songRow.getPosition() == 0 ? View.GONE : View.VISIBLE);

        if (mCurrentSong != null && mCurrentSong.equals(song)) {
            vh.view.findViewById(R.id.track_playing_icon).setVisibility(View.VISIBLE);
            vh.view.findViewById(R.id.track_number).setVisibility(View.GONE);
        } else {
            vh.view.findViewById(R.id.track_playing_icon).setVisibility(View.GONE);
            vh.view.findViewById(R.id.track_number).setVisibility(View.VISIBLE);
        }

        View background = vh.view.findViewById(R.id.background);
        if (Utils.hasLollipop()) {
            background.setClipToOutline(true);
        }
        background.setOnClickListener(mSongClickListener);
        background.setTag(songRow);

        final String trackDurationStr = Utils.formatTrackLength(trackDuration);

        ((TextView) vh.view.findViewById(R.id.track_title)).setText(trackTitle);
        ((TextView) vh.view.findViewById(R.id.track_artist)).setText(String.format(" / %s", artistName));
        ((TextView) vh.view.findViewById(R.id.track_number)).setText(Integer.toString(songRow.getPosition() + 1));
        ((TextView) vh.view.findViewById(R.id.track_duration)).setText(trackDurationStr);

    }

    private String getArtistName(Song song) {
        String artistName;

        if (song.getArtist() != null) {
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(song.getArtist(), song.getProvider());
            if (artist != null) {
                artistName = artist.getName();
            } else {
                artistName = null;
            }
        } else {
            artistName = null;
        }

        return artistName;
    }
}
