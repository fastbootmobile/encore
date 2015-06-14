package com.fastbootmobile.encore.providers;

import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches information gotten by providers
 */
public class ProviderCache {
    private final Map<String, Playlist> mPlaylists;
    private final Map<String, Song> mSongs;
    private final Map<String, ProviderIdentifier> mRefProvider;
    private final Map<String, Album> mAlbums;
    private final Map<String, Artist> mArtists;
    private final List<Playlist> mMultiProviderPlaylists;

    /**
     * Default constructor
     */
    public ProviderCache() {
        mPlaylists = new HashMap<>();
        mSongs = new HashMap<>();
        mRefProvider = new HashMap<>();
        mAlbums = new HashMap<>();
        mArtists = new HashMap<>();
        mMultiProviderPlaylists = new ArrayList<>();
    }

    /**
     * Purges the cache in case the provider may change for the specified provider
     */
    public void purgeCacheForProvider(ProviderIdentifier id) {
        List<String> keysToRemove;

        // Playlists
        synchronized (mPlaylists) {
            Set<Map.Entry<String, Playlist>> playlists = mPlaylists.entrySet();
            keysToRemove = new ArrayList<>();

            for (Map.Entry<String, Playlist> item : playlists) {
                if (item.getValue().getProvider().equals(id)) {
                    keysToRemove.add(item.getKey());
                }
            }


            for (String key : keysToRemove) {
                mPlaylists.remove(key);
            }
        }

        // Songs
        synchronized (mSongs) {
            Set<Map.Entry<String, Song>> songs = mSongs.entrySet();
            keysToRemove.clear();

            for (Map.Entry<String, Song> item : songs) {
                if (item.getValue().getProvider() != null
                        && item.getValue().getProvider().equals(id)) {
                    keysToRemove.add(item.getKey());
                }
            }


            for (String key : keysToRemove) {
                mSongs.remove(key);
            }
        }

        // Albums
        synchronized (mAlbums) {
            Set<Map.Entry<String, Album>> albums = mAlbums.entrySet();
            keysToRemove.clear();

            for (Map.Entry<String, Album> item : albums) {
                if (item.getValue().getProvider().equals(id)) {
                    keysToRemove.add(item.getKey());
                }
            }
            for (String key : keysToRemove) {
                mAlbums.remove(key);
            }
        }

        // Artists
        synchronized (mArtists) {
            Set<Map.Entry<String, Artist>> artists = mArtists.entrySet();
            keysToRemove.clear();

            for (Map.Entry<String, Artist> item : artists) {
                if (item.getValue().getProvider().equals(id)) {
                    keysToRemove.add(item.getKey());
                }
            }

            for (String key : keysToRemove) {
                mArtists.remove(key);
            }
        }
    }

    public ProviderIdentifier getRefProvider(final String ref) {
        return mRefProvider.get(ref);
    }

    public void putPlaylist(final ProviderIdentifier provider, final Playlist pl) {
        synchronized (mPlaylists) {
            mPlaylists.put(pl.getRef(), pl);
        }
        mRefProvider.put(pl.getRef(), provider);
    }

    public void putAllProviderPlaylist(List<Playlist> playlists) {
        mMultiProviderPlaylists.addAll(playlists);
    }

    Playlist getPlaylist(final String ref) {
        synchronized(mPlaylists) {
            return mPlaylists.get(ref);
        }
    }

    public List<Playlist> getAllPlaylists() {
        synchronized (mPlaylists) {
            return new ArrayList<>(mPlaylists.values());
        }
    }

    public void removePlaylist(String ref) {
        synchronized (mPlaylists) {
            mPlaylists.remove(ref);
        }
    }

    public List<Playlist> getAllMultiProviderPlaylists() {
        return mMultiProviderPlaylists;
    }

    public List<Song> getAllSongs() {
        return new ArrayList<>(mSongs.values());
    }

    public List<Artist> getAllArtists() {
        synchronized (mArtists) {
            return new ArrayList<>(mArtists.values());
        }
    }

    public List<Album> getAllAlbums() {
        synchronized (mAlbums) {
            return new ArrayList<>(mAlbums.values());
        }
    }

    public void putSong(final ProviderIdentifier provider, final Song song) {
        synchronized (mSongs) {
            mSongs.put(song.getRef(), song);
        }
        mRefProvider.put(song.getRef(), provider);
    }

    Song getSong(final String ref) {
        synchronized (mSongs) {
            return mSongs.get(ref);
        }
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

}
