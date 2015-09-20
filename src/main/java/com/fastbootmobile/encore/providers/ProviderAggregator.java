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

package com.fastbootmobile.encore.providers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;
import android.util.Log;
import android.widget.Toast;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Genre;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Class federating all the information from providers
 */
public class ProviderAggregator extends IProviderCallback.Stub {
    private static final String TAG = "ProviderAggregator";
    private static final int PROPAGATION_DELAY = 200;
    private static final boolean DEBUG = false;

    private final Map<String, List<SearchResult>> mCachedSearches;
    private final List<ILocalCallback> mUpdateCallbacks;
    private final List<ProviderConnection> mProviders;
    private ProviderCache mCache;
    private Handler mMainHandler;
    private HandlerThread mBackHandlerThread;
    private Handler mBackHandler;
    private final List<Song> mPostedUpdateSongs = new ArrayList<>();
    private final List<Album> mPostedUpdateAlbums = new ArrayList<>();
    private final List<Artist> mPostedUpdateArtists = new ArrayList<>();
    private final List<Playlist> mPostedUpdatePlaylists = new ArrayList<>();
    private List<String> mRosettaStonePrefix = new ArrayList<>();
    private Map<String, ProviderIdentifier> mRosettaStoneMap = new HashMap<>();
    private ThreadPoolExecutor mExecutor = new ScheduledThreadPoolExecutor(4);
    private Context mContext;
    private boolean mIsOfflineMode = false;
    private List<OfflineModeListener> mOfflineModeListeners = new ArrayList<>();

    private Runnable mPostSongsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdateSongs) {
                synchronized (mUpdateCallbacks) {
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onSongUpdate(new ArrayList<>(mPostedUpdateSongs));
                    }
                }
                mPostedUpdateSongs.clear();
            }
        }
    };

    private Runnable mPostAlbumsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdateAlbums) {
                synchronized (mUpdateCallbacks) {
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onAlbumUpdate(new ArrayList<>(mPostedUpdateAlbums));
                    }
                }
                mPostedUpdateAlbums.clear();
            }
        }
    };

    private Runnable mPostArtistsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdateArtists) {
                synchronized (mUpdateCallbacks) {
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onArtistUpdate(new ArrayList<>(mPostedUpdateArtists));
                    }
                }
                mPostedUpdateArtists.clear();
            }
        }
    };

    private Runnable mPostPlaylistsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdatePlaylists) {
                synchronized (mUpdateCallbacks) {
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onPlaylistUpdate(new ArrayList<>(mPostedUpdatePlaylists));
                    }
                }
                mPostedUpdatePlaylists.clear();
            }
        }
    };


    private Runnable mUpdatePlaylistsRunnable = new Runnable() {
        @Override
        public void run() {
            // We make a copy to avoid synchronization issues and needless locks
            ArrayList<ProviderConnection> providers;
            synchronized (mProviders) {
                providers = new ArrayList<>(mProviders);
            }

            // Then we query the providers
            for (ProviderConnection conn : providers) {
                try {
                    IMusicProvider binder = conn.getBinder();
                    if (binder != null && binder.isSetup() && binder.isAuthenticated()) {
                        List<Playlist> playlist = binder.getPlaylists();
                        ensurePlaylistsSongsCached(conn, playlist);

                        // Cache all songs in batch
                        int offset = 0;
                        int limit = 100;
                        boolean goForIt = true;

                        while (goForIt) {
                            try {
                                List<Song> songs = binder.getSongs(offset, limit);

                                if (songs == null || songs.size() == 0) {
                                    goForIt = false;
                                } else {
                                    cacheSongs(conn, songs);

                                    if (songs.size() < limit) {
                                        goForIt = false;
                                    }

                                    offset += limit;
                                }
                            } catch (TransactionTooLargeException ignore) {
                                limit -= 10;
                                Log.w(TAG, "Got transaction size error, reducing limit to " + limit);
                            }
                        }

                        try {
                            cacheAlbums(conn, binder.getAlbums());
                        } catch (Exception e) {
                            Log.e(TAG, "Provider " + conn.getProviderName() + " threw an exception in getAlbums", e);
                        }

                        try {
                            cacheArtists(conn, binder.getArtists());
                        } catch (Exception e) {
                            Log.e(TAG, "Provider " + conn.getProviderName() + " threw an exception in getArtists", e);
                        }
                    } else if (conn.getBinder() != null) {
                        Log.i(TAG, "Skipping a providers because it is not setup or authenticated" +
                                " ==> binder=" + binder + " ; isSetup=" +
                                binder.isSetup() + " ; isAuthenticated=" +
                                binder.isAuthenticated());
                    } else {
                        unregisterProvider(conn);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to get data from " + conn.getProviderName(), e);
                    unregisterProvider(conn);
                }
            }
        }
    };

    // Singleton
    private final static ProviderAggregator INSTANCE = new ProviderAggregator();

    public static ProviderAggregator getDefault() {
        return INSTANCE;
    }

    /**
     * Default constructor
     */
    private ProviderAggregator() {
        mUpdateCallbacks = new ArrayList<>();
        mProviders = new ArrayList<>();
        mCache = new ProviderCache();
        mMainHandler = new Handler();
        mCachedSearches = new HashMap<>();
        mBackHandlerThread = new HandlerThread("ProviderAggregator");
        mBackHandlerThread.start();
        mBackHandler = new Handler(mBackHandlerThread.getLooper());
    }

    @Override
    protected void finalize() throws Throwable {
        mBackHandlerThread.interrupt();
        super.finalize();
    }

    public void setContext(Context ctx) {
        mContext = ctx;
    }

    /**
     * @return The data cache
     */
    public ProviderCache getCache() {
        return mCache;
    }

    /**
     * Registers a LocalCallback class, which will be called when various events happen from
     * any of the registered providers.
     *
     * @param cb The callback to add
     */
    public void addUpdateCallback(ILocalCallback cb) {
        synchronized (mUpdateCallbacks) {
            mUpdateCallbacks.add(cb);
        }
    }

    /**
     * Unregisters a local update callback
     *
     * @param cb The callback to remove
     */
    public void removeUpdateCallback(ILocalCallback cb) {
        synchronized (mUpdateCallbacks) {
            mUpdateCallbacks.remove(cb);
        }
    }

    public void cacheSongs(final ProviderConnection provider, final List<Song> songs) {
        if (provider == null)
            return;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (Song song : songs) {
                    mCache.putSong(provider.getIdentifier(), song);
                }
            }
        });
    }

    public void cacheAlbums(final ProviderConnection provider, final List<Album> albums) {
        if (provider == null)
            return;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (Album album : albums) {
                    if (album.getProvider() == null) {
                        Log.e(TAG, "Album " + album.getRef() + " is being cached with a null provider!");
                    }
                    mCache.putAlbum(provider.getIdentifier(), album);
                }
            }
        });
    }

    public void cacheArtists(final ProviderConnection provider, final List<Artist> artists) {
        if (provider == null) {
            return;
        }

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (Artist artist : artists) {
                    try {
                        onArtistUpdate(provider.getIdentifier(), artist);
                    } catch (RemoteException e) {
                        // ignore
                    }
                }
            }
        });
    }

    /**
     * Retrieves a song from the provider, and put it in the cache
     *
     * @param ref      The reference to the song
     * @param provider The provider from which retrieve the song (may be null to query cache only)
     * @return The song, or null if the provider says so
     */
    public Song retrieveSong(final String ref, final ProviderIdentifier provider) {
        if (ref == null) {
            // Force get stack trace
            try {
                throw new RuntimeException();
            } catch (RuntimeException e) {
                Log.e(TAG, "retrieveSong called with a null reference", e);
            }
            return null;
        }

        // Try from cache
        Song output = mCache.getSong(ref);

        if (output == null && provider != null) {
            // Get from provider then
            ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
            if (pc != null) {
                IMusicProvider binder = pc.getBinder();

                if (binder != null) {
                    try {
                        output = binder.getSong(ref);
                        if (output != null) {
                            onSongUpdate(provider, output);
                        }
                    } catch (DeadObjectException e) {
                        Log.e(TAG, "Provider died while retrieving song");
                        return null;
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to retrieve the song", e);
                        return null;
                    }
                } else {
                    if (DEBUG) Log.e(TAG, "Binder null: provider not yet connected?");
                }
            } else {
                Log.e(TAG, "Unknown provider identifier: " + provider);
            }
        }

        if (output == null && provider != null) {
            Log.d(TAG, "Unable to get song " + ref + " from " + provider.mName);
        }

        return output;
    }

    /**
     * Retrieves an artist from the provider, and put it in the cache
     *
     * @param ref      The reference to the artist
     * @param provider The provider from which retrieve the artist
     * @return The artist, or null if the provider says so
     */
    public Artist retrieveArtist(final String ref, final ProviderIdentifier provider) {
        if (ref == null) {
            // Force get stack trace
            try {
                throw new RuntimeException();
            } catch (RuntimeException e) {
                Log.e(TAG, "retrieveArtist called with a null reference", e);
            }
            return null;
        }

        // Try from cache
        Artist output = mCache.getArtist(ref);
        if (output == null && provider != null) {
            ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
            if (pc != null) {
                IMusicProvider binder = pc.getBinder();

                if (binder != null) {
                    try {
                        output = binder.getArtist(ref);
                        onArtistUpdate(provider, output);
                    } catch (DeadObjectException e) {
                        Log.e(TAG, "Provider died while retrieving artist");
                        return null;
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to retrieve the artist", e);
                        return null;
                    }
                }
            }
        }

        return output;
    }

    /**
     * Retrieves an album from the provider, and put it in the cache
     *
     * @param ref      The reference to the album
     * @param provider The provider from which retrieve the album
     * @return The album, or null if the provider says so
     */
    public Album retrieveAlbum(final String ref, final ProviderIdentifier provider) {
        if (ref == null) {
            // Force get stack trace
            try {
                throw new RuntimeException();
            } catch (RuntimeException e) {
                Log.e(TAG, "retrieveAlbum called with a null reference", e);
            }
            return null;
        }

        // Try from cache
        Album output = mCache.getAlbum(ref);

        if (output == null && provider != null) {
            ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
            if (pc != null) {
                IMusicProvider binder = pc.getBinder();

                if (binder != null) {
                    try {
                        output = binder.getAlbum(ref);
                        onAlbumUpdate(provider, output);
                    } catch (DeadObjectException e) {
                        Log.e(TAG, "Provider died while retrieving album");
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to retrieve the album", e);
                    }
                }
            }
        }

        return output;
    }

    /**
     * Retrieves an album from the provider, and put it in the cache
     *
     * @param ref      The reference to the album
     * @param provider The provider from which retrieve the album
     * @return The album, or null if the provider says so
     */
    public Playlist retrievePlaylist(final String ref, final ProviderIdentifier provider) {
        if (ref == null) {
            // Force get stack trace
            try {
                throw new RuntimeException();
            } catch (RuntimeException e) {
                Log.e(TAG, "retrievePlaylist called with a null reference", e);
            }
            return null;
        }

        // Try from cache
        Playlist output = mCache.getPlaylist(ref);

        if (output == null && provider != null) {
            ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
            if (pc != null) {
                IMusicProvider binder = pc.getBinder();

                if (binder != null) {
                    try {
                        output = binder.getPlaylist(ref);
                        onPlaylistAddedOrUpdated(provider, output);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to retrieve the playlist", e);
                    }
                }
            }
        }

        return output;
    }

    /**
     * Queries in a thread the songs of the list of playlist passed in parameter, if needed
     * Note that this method is only valid for playlists that have been provided by a provider.
     *
     * @param provider The provider that provided these playlists
     * @param playlist The list of playlists to fetch
     */
    private void ensurePlaylistsSongsCached(final ProviderConnection provider,
                                            final List<Playlist> playlist) {
        if (provider == null || playlist == null) {
            // playlist may be null if there are no playlists
            Log.w(TAG, "Bailing playlist song caching because provider or playlist is null");
            return;
        }

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                IMusicProvider binder = provider.getBinder();
                if (binder == null) {
                    return;
                }

                for (Playlist p : playlist) {
                    if (p == null || p.getName() == null) {
                        continue;
                    }

                    if (p.getProvider() == null) {
                        Log.w(TAG, "Playlist '" + p.getRef() + "' cached without identifier!");
                    }

                    mCache.putPlaylist(provider.getIdentifier(), p);

                    // Make sure we have references to all the songs in the playlist
                    Iterator<String> songs = p.songs();
                    while (songs.hasNext()) {
                        String songRef = songs.next();

                        // We first check that we don't already have the song in the cache
                        Song cachedSong = mCache.getSong(songRef);

                        if (cachedSong != null && cachedSong.isLoaded()) {
                            // We already have that song, continue to the next one
                            continue;
                        }

                        // Get the song from the provider
                        Song song = null;
                        try {
                            song = binder.getSong(songRef);
                        } catch (RemoteException e) {
                            // ignore, provider likely died, we just skip its song
                        }

                        if (song != null) {
                            mCache.putSong(provider.getIdentifier(), song);

                            // We call the songUpdate callback only if the track has been loaded.
                            // If it's not, we assume that the provider will call songUpdated
                            // here when it has the data for the track.
                            if (song.isLoaded()) {
                                postSongForUpdate(song);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Registers a new providers service that has been bound, and add the aggregator as a callback
     *
     * @param provider The providers that connected
     */
    public void registerProvider(final ProviderConnection provider) {
        mBackHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mProviders) {
                    mProviders.add(provider);
                }

                try {
                    IMusicProvider binder = provider.getBinder();

                    if (binder != null) {
                        // Register this class as callback
                        binder.registerCallback(ProviderAggregator.this);

                        // Add all rosetta prefixes and map it to this provider
                        List<String> rosettaPrefixes = binder.getSupportedRosettaPrefix();

                        if (rosettaPrefixes != null) {
                            for (String prefix : rosettaPrefixes) {
                                mRosettaStoneMap.put(prefix, provider.getIdentifier());
                                if (!mRosettaStonePrefix.contains(prefix)) {
                                    mRosettaStonePrefix.add(prefix);
                                }
                            }
                        }

                        // Notify subclasses of the new provider
                        synchronized (mUpdateCallbacks) {
                            for (ILocalCallback cb : mUpdateCallbacks) {
                                cb.onProviderConnected(binder);
                            }
                        }
                    }
                } catch (RemoteException e) {
                    // Maybe the service died already?
                    Log.e(TAG, "Unable to register as a callback", e);
                }
            }
        });
    }

    /**
     * Removes the connection to a providers. This may be called either if the connection to a
     * service has been lost (e.g. in case of a DeadObjectException if the service crashed), or
     * simply if the app closes and a service is not needed anymore.
     *
     * @param provider The providers to remove
     */
    public void unregisterProvider(final ProviderConnection provider) {
        mBackHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mProviders) {
                    mProviders.remove(provider);
                    try {
                        if (provider.getBinder() != null) {
                            provider.getBinder().unregisterCallback(ProviderAggregator.this);
                        }
                    } catch (RemoteException e) {
                        // This may perfectly happen if the provider died.
                    }
                }

                mCache.purgeCacheForProvider(provider.getIdentifier());
            }
        });
    }

    /**
     * Notify the providers that the offline mode has changed. Unlike isOfflineMode, this method
     * is only called when the user toggles Offline mode in the main activity overflow menu.
     *
     * @param isEnabled true if offline mode is enabled, false otherwise
     */
    public void notifyOfflineMode(final boolean isEnabled) {
        mIsOfflineMode = isEnabled;

        mBackHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mProviders) {
                    for (ProviderConnection provider : mProviders) {
                        IMusicProvider binder = provider.getBinder();
                        try {
                            if (binder != null) {
                                binder.setOfflineMode(isEnabled);
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG, "Cannot change offline mode on " + provider, e);
                        }
                    }
                }
            }
        });

        for (OfflineModeListener listener : mOfflineModeListeners) {
            listener.onOfflineModeChange(isEnabled);
        }
    }

    /**
     * Returns whether or not the device is offline. The value will be true if either the user
     * checked "Offline mode" in the main overflow menu, or no Internet connection has been
     * detected.
     * @return true if the device is offline (or user toggled offline mode), false otherwise
     */
    public boolean isOfflineMode() {
        return mIsOfflineMode || !hasNetworkConnectivity();
    }

    public void registerOfflineModeListener(OfflineModeListener listener) {
        mOfflineModeListeners.add(listener);
    }

    public void unregisterOfflineModeListener(OfflineModeListener listener) {
        mOfflineModeListeners.remove(listener);
    }

    /**
     * @return true if the device has an active internet connectivity, false otherwise
     */
    public boolean hasNetworkConnectivity() {
        // Check network connectivity
        final ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /**
     * Starts a search. Results will be given in onSearchResults
     * @param query The terms to look for
     */
    public void startSearch(final String query) {
        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        for (ProviderConnection providerConnection : providers) {
            try {
                final IMusicProvider binder = providerConnection.getBinder();
                if (binder != null) {
                    binder.startSearch(query);
                } else {
                    Log.e(TAG, "Null binder, cannot search on " + providerConnection.getIdentifier());
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot run search on a provider", e);
            }
        }
    }

    /**
     * Returns the list of all cached playlists. At the same time, providers will be called for
     * updates and/or fetching playlists, and LocalCallbacks will be called when providers notify
     * this class of eventual new entries.
     *
     * @return A list of playlists
     */
    public List<Playlist> getAllPlaylists() {
        mBackHandler.removeCallbacks(mUpdatePlaylistsRunnable);
        mBackHandler.post(mUpdatePlaylistsRunnable);
        return mCache.getAllPlaylists();
    }

    public List<Playlist> getAllMultiProviderPlaylists() {
        return mCache.getAllMultiProviderPlaylists();
    }

    public void postSongForUpdate(Song s) {
        mBackHandler.removeCallbacks(mPostSongsRunnable);
        synchronized (mPostedUpdateSongs) {
            mPostedUpdateSongs.add(s);
        }
        mBackHandler.postDelayed(mPostSongsRunnable, PROPAGATION_DELAY);
    }

    public void postAlbumForUpdate(Album a) {
        mBackHandler.removeCallbacks(mPostAlbumsRunnable);
        synchronized (mPostedUpdateAlbums) {
            mPostedUpdateAlbums.add(a);
        }
        mBackHandler.postDelayed(mPostAlbumsRunnable, PROPAGATION_DELAY);
    }

    public void postArtistForUpdate(Artist a) {
        mBackHandler.removeCallbacks(mPostArtistsRunnable);
        synchronized (mPostedUpdateArtists) {
            mPostedUpdateArtists.add(a);
        }
        mBackHandler.postDelayed(mPostArtistsRunnable, PROPAGATION_DELAY);
    }

    public void postPlaylistForUpdate(Playlist p) {
        mBackHandler.removeCallbacks(mPostPlaylistsRunnable);
        synchronized (mPostedUpdatePlaylists) {
            mPostedUpdatePlaylists.add(p);
        }
        mBackHandler.postDelayed(mPostPlaylistsRunnable, PROPAGATION_DELAY);
    }

    public List<String> getRosettaStonePrefix() {
        return mRosettaStonePrefix;
    }

    public String getPreferredRosettaStonePrefix() {
        if (mRosettaStonePrefix != null && mRosettaStonePrefix.size() > 0) {
            // TODO: Let user choose
            return mRosettaStonePrefix.get(0);
        } else {
            return null;
        }
    }

    public ProviderIdentifier getRosettaStoneIdentifier(final String identifier) {
        return mRosettaStoneMap.get(identifier);
    }


    @Override
    public int getIdentifier() throws RemoteException {
        return this.hashCode();
    }

    /**
     * Called by the providers when a feedback is available about a login request
     *
     * @param success Whether or not the login succeeded
     */
    @Override
    public void onLoggedIn(final ProviderIdentifier provider, boolean success) throws RemoteException {
        // Request playlists if we logged in
        Log.d(TAG, "onLoggedIn(" + success + ")");
        if (success) {
            // Cache data
            mBackHandler.removeCallbacks(mUpdatePlaylistsRunnable);
            mBackHandler.post(mUpdatePlaylistsRunnable);
        } else {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, mContext.getString(R.string.cannot_login, provider.mName),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Called by the providers when the user login has expired, or has been kicked.
     */
    @Override
    public void onLoggedOut(ProviderIdentifier provider) throws RemoteException {

    }

    /**
     * Called by the providers when a Playlist has been added or updated. The app's providers
     * syndicator will automatically update the local cache of playlists based on the playlist
     * name.
     */
    @Override
    public void onPlaylistAddedOrUpdated(final ProviderIdentifier provider, final Playlist p)
            throws RemoteException {
        if (p == null || p.getRef() == null) {
            Log.w(TAG, "Provider returned a null playlist or a null-ref playlist");
            return;
        }

        try {
            // We compare the provided copy with the one we have in cache. We only notify the callbacks
            // if it indeed changed.
            Playlist cached = mCache.getPlaylist(p.getRef());

            boolean notify;
            if (cached == null) {
                mCache.putPlaylist(provider, p);
                cached = p;
                notify = true;
            } else {
                notify = !cached.isIdentical(p);

                // If the playlist isn't identical, update it
                if (notify) {
                    // Update the name
                    cached.setName(p.getName());

                    if (p.getName() == null) {
                        Log.w(TAG, "Playlist " + p.getRef() + " updated, but name is null!");
                    }

                    cached.setIsLoaded(p.isLoaded());

                    // Empty the playlist
                    while (cached.getSongsCount() > 0) {
                        cached.removeSong(0);
                    }

                    // Re-add the songs to it
                    Iterator<String> songIt = p.songs();
                    while (songIt.hasNext()) {
                        cached.addSong(songIt.next());
                    }

                    // Set offline information
                    cached.setOfflineCapable(p.isOfflineCapable());
                    cached.setOfflineStatus(p.getOfflineStatus());
                }
            }

            final Playlist finalCachedPlaylist = cached;

            // If something has actually changed
            if (notify) {
                mExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        // First, we try to check if we need information for some of the songs
                        // TODO(xplodwild): Is this really needed in a properly designed provider?
                        Iterator<String> it = finalCachedPlaylist.songs();
                        while (it.hasNext()) {
                            String ref = it.next();
                            retrieveSong(ref, provider);
                        }

                        // Then we notify the callbacks
                        postPlaylistForUpdate(finalCachedPlaylist);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "FUUUU", e);
        }
    }

    /**
     * Called by the provider if a playlist has been removed from the user playlists container.
     * @param provider The provider
     * @param ref The reference of the playlist that has been removed
     * @throws RemoteException
     */
    @Override
    public void onPlaylistRemoved(ProviderIdentifier provider, String ref) throws RemoteException {
        if (ref != null) {
            mCache.removePlaylist(ref);
        }

        synchronized (mUpdateCallbacks) {
            for (ILocalCallback cb : mUpdateCallbacks) {
                cb.onPlaylistRemoved(ref);
            }
        }
    }

    /**
     * Called by the providers when the details of a song have been updated.
     */
    @Override
    public void onSongUpdate(ProviderIdentifier provider, final Song s) throws RemoteException {
        if (s == null) {
            Log.w(TAG, "Provider " + provider.mName + " sent in a null songUpdate");
            return;
        }

        try {
            Song cached = mCache.getSong(s.getRef());
            boolean wasLoaded = false;
            boolean changed = false;

            if (cached == null) {
                mCache.putSong(provider, s);
                changed = true;
                cached = s;
            } else {
                wasLoaded = cached.isLoaded();
                if (s.isLoaded() && !cached.isIdentical(s)) {
                    cached.setAlbum(s.getAlbum());
                    cached.setArtist(s.getArtist());
                    cached.setSourceLogo(s.getLogo());
                    cached.setDuration(s.getDuration());
                    cached.setTitle(s.getTitle());
                    cached.setYear(s.getYear());
                    cached.setOfflineStatus(s.getOfflineStatus());
                    cached.setAvailable(s.isAvailable());
                    cached.setIsLoaded(s.isLoaded());
                    changed = true;
                }
            }

            if (!wasLoaded && cached.isLoaded()) {
                // Match the album with the artist
                Artist artist = mCache.getArtist(s.getArtist());
                if (artist == null && s.getArtist() != null) {
                    artist = retrieveArtist(s.getArtist(), provider);
                }

                if (artist != null) {
                    Album album = mCache.getAlbum(s.getAlbum());
                    if (album == null && s.getAlbum() != null) {
                        album = retrieveAlbum(s.getAlbum(), provider);
                    }

                    if (album != null) {
                        artist.addAlbum(album.getRef());
                    }
                }
            }

            if (changed) {
                postSongForUpdate(cached);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while updating song data", e);
        }
    }

    @Override
    public void onGenreUpdate(ProviderIdentifier provider, final Genre g) throws RemoteException {

    }

    /**
     * Called by the providers when the details of an album have been updated.
     */
    @Override
    public void onAlbumUpdate(ProviderIdentifier provider, final Album a) throws RemoteException {
        if (a == null) {
            Log.w(TAG, "Provider returned a null album");
            return;
        }

        Album cached = mCache.getAlbum(a.getRef());
        boolean modified = false;

        // See IProviderCallback.aidl in providerlib for more info about the logic of updating
        // the Album objects
        if (cached == null) {
            mCache.putAlbum(provider, a);
            cached = a;
            modified = true;
        } else if (!cached.isLoaded() || !cached.isIdentical(a)) {
            cached.setName(a.getName());
            cached.setYear(a.getYear());
            cached.setIsLoaded(a.isLoaded());
            cached.setProvider(a.getProvider());

            if (cached.getSongsCount() != a.getSongsCount()) {
                Iterator<String> songsIt = a.songs();
                while (songsIt.hasNext()) {
                    String songRef = songsIt.next();
                    cached.addSong(songRef);
                }
            }

            modified = true;
        }

        if (cached.getProvider() == null) {
            Log.e(TAG, "Provider for " + cached.getRef() + " is null!");
        }

        if (modified) {
            // Add the album to each artist of the song (once)
            Iterator<String> songs = a.songs();

            while (songs.hasNext()) {
                String songRef = songs.next();
                Song song = retrieveSong(songRef, a.getProvider());

                if (song != null && song.isLoaded()) {
                    String artistRef = song.getArtist();
                    if (artistRef != null) {
                        Artist artist = retrieveArtist(artistRef, song.getProvider());

                        if (artist != null) {
                            artist.addAlbum(a.getRef());
                        } else {
                            if (DEBUG) Log.e(TAG, "Artist is null!");
                        }
                    }
                } else {
                    if (DEBUG) Log.e(TAG, "Song is null!");
                }
            }

            postAlbumForUpdate(cached);
        }
    }

    /**
     * Called by the providers when the details of an artist have been updated.
     */
    @Override
    public void onArtistUpdate(ProviderIdentifier provider, Artist a) throws RemoteException {
        if (a == null) {
            Log.w(TAG, "Provider returned a null artist");
            return;
        }

        Artist cached = mCache.getArtist(a.getRef());

        if (cached == null) {
            mCache.putArtist(provider, a);
            postArtistForUpdate(a);
        } else if (!cached.isIdentical(a)) {
            cached.setName(a.getName());
            Iterator<String> it = a.albums();
            while (it.hasNext()) {
                cached.addAlbum(it.next());
            }
            cached.setIsLoaded(a.isLoaded());
            postArtistForUpdate(a);
        }
    }

    @Override
    public void onSongPlaying(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onSongPaused(ProviderIdentifier provider) throws RemoteException {
    }

    @Override
    public void onTrackEnded(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onSearchResult(SearchResult searchResult) {
        if (searchResult == null) {
            return;
        }

        if (searchResult.getIdentifier() == null) {
            Log.e(TAG, "Search result came with no source identifier!");
            return;
        }

        Log.d(TAG, "Got new search results for '" + searchResult.getQuery()
                + "' from " + searchResult.getIdentifier().mName);

        final String query = searchResult.getQuery();

        if (!mCachedSearches.containsKey(query)) {
            // No cached results for this query, add this one
            Log.d(TAG, "New search cache for '" + query + "'");
            List<SearchResult> results = new ArrayList<>();
            results.add(searchResult);
            mCachedSearches.put(query, results);

            // Feed results to the callback
            synchronized (mUpdateCallbacks) {
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onSearchResult(results);
                }
            }
        } else {
            // We already have cached results for this query, add new results
            Log.d(TAG, "Updating search result for '" + query + "'");
            List<SearchResult> cachedResults = mCachedSearches.get(query);

            SearchResult cachedResult = null;
            for (SearchResult tmpResult : cachedResults) {
                if (tmpResult.getIdentifier().equals(searchResult.getIdentifier())) {
                    cachedResult = tmpResult;
                }
            }

            if (cachedResult == null) {
                cachedResults.add(searchResult);
            } else {
                cachedResult.setIdentifier(searchResult.getIdentifier());
                for (String song : searchResult.getSongsList()) {
                    if (!cachedResult.getSongsList().contains(song)) {
                        cachedResult.getSongsList().add(song);
                    }
                }
                for (String artist : searchResult.getArtistList()) {
                    if (!cachedResult.getArtistList().contains(artist)) {
                        cachedResult.getArtistList().add(artist);
                    }
                }
                for (String album : searchResult.getAlbumsList()) {
                    if (!cachedResult.getAlbumsList().contains(album)) {
                        cachedResult.getAlbumsList().add(album);
                    }
                }
                for (String playlist : searchResult.getPlaylistList()) {
                    if (!cachedResult.getPlaylistList().contains(playlist)) {
                        cachedResult.getPlaylistList().add(playlist);
                    }
                }
            }

            // Feed updated results to the callbacks
            synchronized (mUpdateCallbacks) {
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onSearchResult(cachedResults);
                }
            }
        }

    }

    /**
     * Interface for offline mode changes
     */
    public interface OfflineModeListener {
        void onOfflineModeChange(boolean enabled);
    }
}
