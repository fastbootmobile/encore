package org.omnirom.music.model;

import org.omnirom.music.providers.intf.IMusicProvider;

public class Song {
    private IMusicProvider mProvider;
    private String mTitle;
    private String mArtist;
    private Artist mMatchedArtist;
    private String mAlbum;
    private Album mMatchedAlbum;
    private int mYear;

    public Song(IMusicProvider provider) {
        mProvider = provider;
    }

    public IMusicProvider getProvider() {
        return mProvider;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(final String title) {
        mTitle = title;
    }

    public String getArtist() {
        return mArtist;
    }

    public void setArtist(final String artist) {
        mArtist = artist;
    }

    public Artist getMatchedArtist() {
        return mMatchedArtist;
    }

    public void setMatchedArtist(final Artist artist) {
        mMatchedArtist = artist;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public void setAlbum(final String album) {
        mAlbum = album;
    }

    public Album getMatchedAlbum() {
        return mMatchedAlbum;
    }

    public void setMatchedAlbum(final Album album) {
        mMatchedAlbum = album;
    }

    public int getYear() {
        return mYear;
    }

    public void setYear(final int year) {
        mYear = year;
    }
}
