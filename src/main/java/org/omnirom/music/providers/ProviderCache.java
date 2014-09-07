package org.omnirom.music.providers;

import android.util.Log;

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
    private final Map<String, Playlist> mPlaylists;
    private final Map<String, Song> mSongs;
    private final Map<String, ProviderIdentifier> mRefProvider;
    private final Map<String, Album> mAlbums;
    private final Map<String, Artist> mArtists;
    private final Map<Song, String> mSongArtKeys;
    private final Map<Album, String> mAlbumArtKeys;
    private final Map<Artist, String> mArtistArtKeys;
    private final List<Playlist> mMultiProviderPlaylists;

    /**
     * Default constructor
     */
    public ProviderCache() {
        mPlaylists = new HashMap<String, Playlist>();
        mSongs = new HashMap<String, Song>();
        mRefProvider = new HashMap<String, ProviderIdentifier>();
        mAlbums = new HashMap<String, Album>();
        mArtists = new HashMap<String, Artist>();
        mSongArtKeys = new HashMap<Song, String>();
        mAlbumArtKeys = new HashMap<Album, String>();
        mArtistArtKeys = new HashMap<Artist, String>();
        mMultiProviderPlaylists = new ArrayList<Playlist>();
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

    public void putAllProviderPlaylist(List<Playlist> playlists) {
        mMultiProviderPlaylists.addAll(playlists);
    }

    Playlist getPlaylist(final String ref) {
        return mPlaylists.get(ref);
    }

    public List<Playlist> getAllPlaylists() {
        return new ArrayList<Playlist>(mPlaylists.values());
    }

    public List<Playlist> getAllMultiProviderPlaylists() {
        return mMultiProviderPlaylists;
    }

    public List<Song> getAllSongs() {
        return new ArrayList<Song>(mSongs.values());
    }

    public List<Artist> getAllArtists() {
        synchronized (mArtists) {
            return new ArrayList<Artist>(mArtists.values());
        }
    }

    public List<Album> getAllAlbums() {
        synchronized (mAlbums) {
            return new ArrayList<Album>(mAlbums.values());
        }
    }

    public void putSong(final ProviderIdentifier provider, final Song song) {
        mSongs.put(song.getRef(), song);
        mRefProvider.put(song.getRef(), provider);
    }

    Song getSong(final String ref) {
        return mSongs.get(ref);
    }

    public void putAlbum(final ProviderIdentifier provider, final Album album) {
        synchronized (mAlbums) {
            mAlbums.put(album.getRef(), album);
            mRefProvider.put(album.getRef(), provider);
        }
    }

    Album getAlbum(final String ref) {
        synchronized (mAlbums) {
            return mAlbums.get(ref);
        }
    }

    public void putArtist(final ProviderIdentifier provider, final Artist artist) {
        synchronized (mArtists) {
            mArtists.put(artist.getRef(), artist);
            mRefProvider.put(artist.getRef(), provider);
        }
    }

    Artist getArtist(final String ref) {
        synchronized (mArtists) {
            return mArtists.get(ref);
        }
    }

    public void putSongArtKey(final Song song, final String key) {
        mSongArtKeys.put(song, key);
    }

    /**
     * Get the art cache key of the album art for the provided song, or null if none found
     *
     * @param song The song to lookup
     * @return The art key, or null if none found
     */
    public String getSongArtKey(final Song song) {
        return mSongArtKeys.get(song);
    }

    public String getAlbumArtKey(final Album album) {
        return mAlbumArtKeys.get(album);
    }

    public void putAlbumArtKey(final Album album, final String key) {
        mAlbumArtKeys.put(album, key);
    }

    public String getArtistArtKey(final Artist album) {
        return mArtistArtKeys.get(album);
    }

    public void putArtistArtKey(final Artist album, final String key) {
        mArtistArtKeys.put(album, key);
    }
}
