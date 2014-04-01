package org.omnirom.music.providers;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches information gotten by providers
 */
public class ProviderCache {
    private Map<String, Playlist> mPlaylists;
    private Map<String, Song> mSongs;
    private Map<String, ProviderIdentifier> mRefProvider;
    private Map<String, Album> mAlbums;
    private Map<String, Artist> mArtists;

    /**
     * Default constructor
     */
    public ProviderCache() {
        mPlaylists = new HashMap<String, Playlist>();
        mSongs = new HashMap<String, Song>();
        mRefProvider = new HashMap<String, ProviderIdentifier>();
        mAlbums = new HashMap<String, Album>();
        mArtists = new HashMap<String, Artist>();
    }

    /**
     * Purges the songs cache in case the provider may change
     */
    public void purgeSongCache() {
        mSongs.clear();
    }

    public ProviderIdentifier getRefProvider(final String ref) {
        return mRefProvider.get(ref);
    }

    public void putPlaylist(final ProviderIdentifier provider, final Playlist pl) {
        mPlaylists.put(pl.getRef(), pl);
        mRefProvider.put(pl.getRef(), provider);
    }

    public Playlist getPlaylist(final String ref) {
        return mPlaylists.get(ref);
    }

    public List<Playlist> getAllPlaylists() {
        return new ArrayList<Playlist>(mPlaylists.values());
    }

    public void putSong(final ProviderIdentifier provider, final Song song) {
        mSongs.put(song.getRef(), song);
        mRefProvider.put(song.getRef(), provider);
    }

    public Song getSong(final String ref) {
        return mSongs.get(ref);
    }

    public void putAlbum(final ProviderIdentifier provider, final Album album) {
        mAlbums.put(album.getRef(), album);
        mRefProvider.put(album.getRef(), provider);
    }

    public Album getAlbum(final String ref) {
        return mAlbums.get(ref);
    }

    public void putArtist(final ProviderIdentifier provider, final Artist artist) {
        mArtists.put(artist.getRef(), artist);
        mRefProvider.put(artist.getRef(), provider);
    }

    public Artist getArtist(final String ref) {
        return mArtists.get(ref);
    }
}
