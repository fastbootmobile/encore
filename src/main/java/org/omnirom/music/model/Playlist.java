package org.omnirom.music.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Playlist {
    private List<Song> mSongs;

    public Playlist() {
        mSongs = new ArrayList<Song>();
    }

    public void addSong(Song s) {
        mSongs.add(s);
    }

    public void removeSong(Song s) {
        mSongs.remove(s);
    }

    public void removeSong(int i) {
        mSongs.remove(i);
    }

    public Iterator<Song> iterator() {
        return mSongs.iterator();
    }
}
