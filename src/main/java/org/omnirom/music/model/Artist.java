package org.omnirom.music.model;

import java.util.Iterator;
import java.util.List;

public class Artist {
    private String mName;
    private List<Album> mAlbums;

    private Artist(final String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void addAlbum(Album a) {
        mAlbums.add(a);
    }

    public Iterator<Album> getAlbums() {
        return mAlbums.iterator();
    }
}
