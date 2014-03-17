package org.omnirom.music.model;

import java.util.Iterator;
import java.util.List;

public class Album {
    private List<Song> mSongs;
    private String mName;

    public Album(final String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void addSong(Song s) {
        mSongs.add(s);
    }

    public Iterator<Song> songs() {
        return mSongs.iterator();
    }
}
