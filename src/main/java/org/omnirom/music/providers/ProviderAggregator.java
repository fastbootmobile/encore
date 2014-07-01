package org.omnirom.music.providers;

import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class ProviderAggregator extends IProviderCallback.Stub {

    private static final String TAG = "ProviderAggregator";

    private List<ILocalCallback> mUpdateCallbacks;
    final private List<ProviderConnection> mProviders;
    private ProviderCache mCache;
    private Handler mHandler;
    private ThreadPoolExecutor mExecutor = new ScheduledThreadPoolExecutor(4);


    private Runnable mUpdatePlaylistsRunnable = new Runnable() {
        @Override
        public void run() {
            // We make a copy to avoid synchronization issues and needless locks
            ArrayList<ProviderConnection> providers;
            synchronized (mProviders) {
                providers = new ArrayList<ProviderConnection>(mProviders);
            }

            // Then we query the providers
            for (ProviderConnection conn : providers) {
                try {
                    if (conn.getBinder() != null &&
                            conn.getBinder().isSetup() && conn.getBinder().isAuthenticated()) {
                        List<Playlist> playlist = conn.getBinder().getPlaylists();
                        ensurePlaylistsSongsCached(conn, playlist);
                        cacheSongs(conn,conn.getBinder().getSongs());
                        cacheAlbums(conn,conn.getBinder().getAlbums());
                        cacheArtists(conn,conn.getBinder().getArtists());
                    } else if (conn.getBinder() != null) {
                        Log.i(TAG, "Skipping a providers because it is not setup or authenticated" +
                                " ==> binder=" + conn.getBinder() + " ; isSetup=" +
                                conn.getBinder().isSetup() + " ; isAuthenticated=" +
                                conn.getBinder().isAuthenticated());
                    } else {
                        unregisterProvider(conn);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to get playlists from a providers", e);
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
        Log.e(TAG, " ___ CONSTRUCTOR ___");
        mUpdateCallbacks = new ArrayList<ILocalCallback>();
        mProviders = new ArrayList<ProviderConnection>();
        mCache = new ProviderCache();
        mHandler = new Handler();
    }

    /**
     * Posts the provided runnable to this class' Handler, and make sure
     * @param r The runnable
     */
    private void postOnce(Runnable r) {
        mHandler.removeCallbacks(r);
        mHandler.post(r);
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
     * @param cb The callback to add
     */
    public void addUpdateCallback(ILocalCallback cb) {
        mUpdateCallbacks.add(cb);
    }

    /**
     * Unregisters a local update callback
     * @param cb The callback to remove
     */
    public void removeUpdateCallback(ILocalCallback cb) {
        mUpdateCallbacks.remove(cb);
    }
    public void cacheSongs(final ProviderConnection provider,final List<Song> songs){
        if(provider == null)
            return;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for(Song song : songs){
                    mCache.putSong(provider.getIdentifier(),song);
                }
            }
        });
    }
    public void cacheAlbums(final ProviderConnection provider,final List<Album> albums){
        if(provider == null)
            return;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for(Album album : albums){
                    mCache.putAlbum(provider.getIdentifier(),album);
                }
            }
        });
    }
    public void cacheArtists(final ProviderConnection provider,final List<Artist> artists){
        if(provider == null)
            return;
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for(Artist artist : artists){
                    mCache.putArtist(provider.getIdentifier(),artist);
                }
            }
        });
    }
    /**
     * Queries in a thread the songs of the list of playlist passed in parameter, if needed
     * Note that this method is only valid for playlists that have been provided by a provider.
     * @param provider The provider that provided these playlists
     * @param playlist The list of playlists to fetch
     */
    private void ensurePlaylistsSongsCached(final ProviderConnection provider,
                                            final List<Playlist> playlist) {
        if (provider == null || playlist == null ) {
            // playlist may be null if there are no playlists
            return;
        }

        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                for (Playlist p : playlist) {
                    if (p == null || p.getName() == null) {
                        continue;
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
                            song = provider.getBinder().getSong(songRef);
                        } catch (RemoteException e) {
                            // ignore, provider likely died, we just skip its song
                        }

                        if (song != null) {
                            mCache.putSong(provider.getIdentifier(), song);

                            // We call the songUpdate callback only if the track has been loaded.
                            // If it's not, we assume that the provider will call songUpdated
                            // here when it has the data for the track.
                            if (song.isLoaded()) {
                                for (ILocalCallback cb : mUpdateCallbacks) {
                                    cb.onSongUpdate(song);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Registers a new providers service that has been bound, and add the aggregator as a callback
     * @param provider The providers that connected
     */
    public void registerProvider(ProviderConnection provider) {
        boolean added = false;
        synchronized (mProviders) {
            if (!mProviders.contains(provider)) {
                mProviders.add(provider);
                added = true;
            }
        }

        if (added) {
            try {
                provider.getBinder().registerCallback(this);

                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onProviderConnected(provider.getBinder());
                }
            } catch (RemoteException e) {
                // Maybe the service died already?
                Log.e(TAG, "Unable to register as a callback");
            }
        }
    }

    /**
     * Removes the connection to a providers. This may be called either if the connection to a
     * service has been lost (e.g. in case of a DeadObjectException if the service crashed), or
     * simply if the app closes and a service is not needed anymore.
     * @param provider The providers to remove
     */
    public void unregisterProvider(final ProviderConnection provider) {
        mHandler.post(new Runnable() {
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
                    provider.unbindService();
                }
            }
        });
    }

    /**
     * Perform a search query for the provided terms on all providers. The results are pushed
     * progressively as they are given by the providers to the callback.
     * @param query The search terms
     * @param callback The callback to call with results
     */
    public void search(final String query, final ISearchCallback callback) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Returns the list of all cached playlists. At the same time, providers will be called for
     * updates and/or fetching playlists, and LocalCallbacks will be called when providers notify
     * this class of eventual new entries.
     *
     * @return A list of playlists
     */
    public List<Playlist> getAllPlaylists() {
        new Thread(mUpdatePlaylistsRunnable).start();
        return mCache.getAllPlaylists();
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
            postOnce(mUpdatePlaylistsRunnable);
        } else {
            // TODO: Show a message
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
        // We compare the provided copy with the one we have in cache. We only notify the callbacks
        // if it indeed changed.
        Playlist cached = mCache.getPlaylist(p.getRef());

        boolean notify;
        if (cached == null) {
            mCache.putPlaylist(provider, p);
            notify = true;
        } else {
            notify = !cached.isIdentical(p);
            if (notify) {
                // The playlist changed, update the cache
                mCache.putPlaylist(provider, p);
            }
        }

        // If something has actually changed
        if (notify) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    final IMusicProvider binder = PluginsLookup.getDefault().getProvider(provider).getBinder();

                    // First, we try to check if we need information for some of the songs
                    Iterator<String> it = p.songs();
                    while (it.hasNext()) {
                        String ref = it.next();
                        Song cachedSong = mCache.getSong(ref);
                        if (cachedSong == null || !cachedSong.isLoaded()) {
                            try {
                                Song providerSong = binder.getSong(ref);
                                if (providerSong != null) {
                                    mCache.putSong(provider, providerSong);
                                }
                            } catch (RemoteException e) {
                                // ignored
                            }
                        }
                    }

                    // Then we notify the callbacks
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onPlaylistUpdate(p);
                    }
                }
            });
        }
    }

    /**
     * Called by the providers when the details of a song have been updated.
     */
    @Override
    public void onSongUpdate(ProviderIdentifier provider, final Song s) throws RemoteException {
        Song cached = mCache.getSong(s.getRef());

        if (cached == null || !cached.isLoaded()) {
            // Log.i(TAG, "Song update: Title: " + s.getTitle());
            mCache.putSong(provider, s);

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onSongUpdate(s);
                    }
                }
            });
        }
    }

    /**
     * Called by the providers when the details of an album have been updated.
     */
    @Override
    public void onAlbumUpdate(ProviderIdentifier provider, final Album a) throws RemoteException {
        Album cached = mCache.getAlbum(a.getRef());

        // See IProviderCallback.aidl in providerlib for more info about the logic of updating
        // the Album objects
        if (cached == null || !cached.isLoaded() || cached.getSongsCount() < a.getSongsCount()) {
            mCache.putAlbum(provider, a);

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    for (ILocalCallback cb : mUpdateCallbacks) {
                        cb.onAlbumUpdate(a);
                    }
                }
            });
        }
    }

    /**
     * Called by the providers when the details of an artist have been updated.
     */
    @Override
    public void onArtistUpdate(ProviderIdentifier provider, Artist a) throws RemoteException {
        mCache.putArtist(provider, a);
    }

    @Override
    public void onSongPlaying(ProviderIdentifier provider, Song s) throws RemoteException {

    }

    @Override
    public void onSongPaused(ProviderIdentifier provider) throws RemoteException {

    }

    @Override
    public void onSongStopped(ProviderIdentifier provider) throws RemoteException {

    }

}
