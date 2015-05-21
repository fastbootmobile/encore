package org.omnirom.music.app.tv;

import android.support.v17.leanback.widget.Row;

import org.omnirom.music.model.Song;

public class SongRow extends Row {
    private final Song mSong;
    private final int mPosition;

    public SongRow(Song song, int position) {
        mSong = song;
        mPosition = position;
    }

    public Song getSong() {
        return mSong;
    }

    public int getPosition() {
        return mPosition;
    }
}
