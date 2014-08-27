package org.omnirom.music.providers;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Genre;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

public class ProviderAggregator extends IProviderCallback.Stub {

    private static final String TAG = "ProviderAggregator";
    private static final int PROPAGATION_DELAY = 200;
    private SearchResult mCachedSearch;
    private List<ILocalCallback> mUpdateCallbacks;
    final private List<ProviderConnection> mProviders;
    private ProviderCache mCache;
    private Handler mHandler;
    private final List<Song> mPostedUpdateSongs = new ArrayList<Song>();
    private final List<Album> mPostedUpdateAlbums = new ArrayList<Album>();
    private final List<Artist> mPostedUpdateArtists = new ArrayList<Artist>();
    private final List<Playlist> mPostedUpdatePlaylists = new ArrayList<Playlist>();
    private List<String> mRosettaStonePrefix = new ArrayList<String>();
    private Map<String, ProviderIdentifier> mRosettaStoneMap = new HashMap<String, ProviderIdentifier>();
    private ThreadPoolExecutor mExecutor = new ScheduledThreadPoolExecutor(4);
    private Context mContext;

    private Runnable mPostSongsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdateSongs) {
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onSongUpdate(mPostedUpdateSongs);
                }
                mPostedUpdateSongs.clear();
            }
        }
    };

    private Runnable mPostAlbumsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdateAlbums) {
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onAlbumUpdate(mPostedUpdateAlbums);
                }
                mPostedUpdateAlbums.clear();
            }
        }
    };

    private Runnable mPostArtistsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdateArtists) {
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onArtistUpdate(mPostedUpdateArtists);
                }
                mPostedUpdateArtists.clear();
            }
        }
    };

    private Runnable mPostPlaylistsRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mPostedUpdatePlaylists) {
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onPlaylistUpdate(mPostedUpdatePlaylists);
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
                providers = new ArrayList<ProviderConnection>(mProviders);
            }

            // Then we query the providers
            for (ProviderConnection conn : providers) {
                try {
                    if (conn.getBinder() != null &&
                            conn.getBinder().isSetup() && conn.getBinder().isAuthenticated()) {
                        List<Playlist> playlist = conn.getBinder().getPlaylists();
                        ensurePlaylistsSongsCached(conn, playlist);
                        cacheSongs(conn, conn.getBinder().getSongs());
                        cacheAlbums(conn, conn.getBinder().getAlbums());
                        cacheArtists(conn, conn.getBinder().getArtists());
                    } else if (conn.getBinder() != null) {
                        Log.i(TAG, "Skipping a providers because it is not setup or authenticated" +
                                " ==> binder=" + conn.getBinder() + " ; isSetup=" +
                                conn.getBinder().isSetup() + " ; isAuthenticated=" +
                                conn.getBinder().isAuthenticated());
                    } else {
                        unregisterProvider(conn);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to get playlists from " + conn.getProviderName(), e);
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
        mUpdateCallbacks = new ArrayList<ILocalCallback>();
        mProviders = new ArrayList<ProviderConnection>();
        mCache = new ProviderCache();
        mHandler = new Handler();
    }

    public void setContext(Context ctx) {
        mContext = ctx;
    }

    /**
     * Posts the provided runnable to this class' Handler, and make sure
     *
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
     *
     * @param cb The callback to add
     */
    public void addUpdateCallback(ILocalCallback cb) {
        mUpdateCallbacks.add(cb);
    }

    /**
     * Unregisters a local update callback
     *
     * @param cb The callback to remove
     */
    public void removeUpdateCallback(ILocalCallback cb) {
        mUpdateCallbacks.remove(cb);
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
     * @param provider The provider from which retrieve the song
     * @return The song, or null if the provider says so
     */
    public Song retrieveSong(final String ref, final ProviderIdentifier provider) {
        ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
        if (pc != null) {
            IMusicProvider binder = pc.getBinder();

            if (binder != null) {
                try {
                    Song song = binder.getSong(ref);
                    onSongUpdate(provider, song);
                    return song;
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to retrieve the song", e);
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves an artist from the provider, and put it in the cache
     *
     * @param ref      The reference to the artist
     * @param provider The provider from which retrieve the artist
     * @return The artist, or null if the provider says so
     */
    public Artist retrieveArtist(final String ref, final ProviderIdentifier provider) {
        ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
        if (pc != null) {
            IMusicProvider binder = pc.getBinder();

            if (binder != null) {
                try {
                    Artist artist = binder.getArtist(ref);
                    onArtistUpdate(provider, artist);
                    return artist;
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to retrieve the artist", e);
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves an album from the provider, and put it in the cache
     *
     * @param ref      The reference to the album
     * @param provider The provider from which retrieve the album
     * @return The album, or null if the provider says so
     */
    public Album retrieveAlbum(final String ref, final ProviderIdentifier provider) {
        ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
        if (pc != null) {
            IMusicProvider binder = pc.getBinder();

            if (binder != null) {
                try {
                    Album album = binder.getAlbum(ref);
                    onAlbumUpdate(provider, album);
                    return album;
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to retrieve the album", e);
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves an album from the provider, and put it in the cache
     *
     * @param ref      The reference to the album
     * @param provider The provider from which retrieve the album
     * @return The album, or null if the provider says so
     */
    public Playlist retrievePlaylist(final String ref, final ProviderIdentifier provider) {
        ProviderConnection pc = PluginsLookup.getDefault().getProvider(provider);
        if (pc != null) {
            IMusicProvider binder = pc.getBinder();

            if (binder != null) {
                try {
                    Playlist playlist = binder.getPlaylist(ref);
                    onPlaylistAddedOrUpdated(provider, playlist);
                    return playlist;
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to retrieve the playlist", e);
                    return null;
                }
            }
        }

        return null;
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
    public void registerProvider(ProviderConnection provider) {
        boolean added = false;
        synchronized (mProviders) {
            //if (!mProviders.contains(provider)) {
            mProviders.add(provider);
            added = true;
            //}
        }

        if (added) {
            try {
                // Register this class as callback
                provider.getBinder().registerCallback(this);

                // Add all rosetta prefixes and map it to this provider
                List<String> rosettaPrefixes = provider.getBinder().getSupportedRosettaPrefix();

                if (rosettaPrefixes != null) {
                    for (String prefix : rosettaPrefixes) {
                        mRosettaStoneMap.put(prefix, provider.getIdentifier());
                        if (!mRosettaStonePrefix.contains(prefix)) {
                            mRosettaStonePrefix.add(prefix);
                        }
                    }
                }

                // Notify subclasses of the new provider
                for (ILocalCallback cb : mUpdateCallbacks) {
                    cb.onProviderConnected(provider.getBinder());
                }
            } catch (RemoteException e) {
                // Maybe the service died already?
                Log.e(TAG, "Unable to register as a callback", e);
            }
        }
    }

    /**
     * Removes the connection to a providers. This may be called either if the connection to a
     * service has been lost (e.g. in case of a DeadObjectException if the service crashed), or
     * simply if the app closes and a service is not needed anymore.
     *
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

    public List<Playlist> getAllMultiProviderPlaylists() {
        return mCache.getAllMultiProviderPlaylists();
    }

    public void postSongForUpdate(Song s) {
        mHandler.removeCallbacks(mPostSongsRunnable);
        synchronized (mPostedUpdateSongs) {
            mPostedUpdateSongs.add(s);
        }
        mHandler.postDelayed(mPostSongsRunnable, PROPAGATION_DELAY);
    }

    public void postAlbumForUpdate(Album a) {
        mHandler.removeCallbacks(mPostAlbumsRunnable);
        synchronized (mPostedUpdateAlbums) {
            mPostedUpdateAlbums.add(a);
        }
        mHandler.postDelayed(mPostAlbumsRunnable, PROPAGATION_DELAY);
    }

    public void postArtistForUpdate(Artist a) {
        mHandler.removeCallbacks(mPostArtistsRunnable);
        synchronized (mPostedUpdateArtists) {
            mPostedUpdateArtists.add(a);
        }
        mHandler.postDelayed(mPostArtistsRunnable, PROPAGATION_DELAY);
    }

    public void postPlaylistForUpdate(Playlist p) {
        mHandler.removeCallbacks(mPostPlaylistsRunnable);
        synchronized (mPostedUpdatePlaylists) {
            mPostedUpdatePlaylists.add(p);
        }
        mHandler.postDelayed(mPostPlaylistsRunnable, PROPAGATION_DELAY);
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
            mHandler.post(new Runnable() {
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
        // We compare the provided copy with the one we have in cache. We only notify the callbacks
        // if it indeed changed.
        Playlist cached = mCache.getPlaylist(p.getRef());

        boolean notify;
        if (cached == null) {
            mCache.putPlaylist(provider, p);
            notify = true;
        } else {
            notify = !cached.isIdentical(p);

            // If the playlist isn't identical, update it
            if (notify) {
                // Update the name
                cached.setName(p.getName());

                // Empty the playlist
                while (cached.getSongsCount() > 0) {
                    cached.removeSong(0);
                }

                // Re-add the songs to it
                Iterator<String> songIt = p.songs();
                while (songIt.hasNext()) {
                    cached.addSong(songIt.next());
                }
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
                    postPlaylistForUpdate(p);
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
                cached.setIsLoaded(s.isLoaded());
                changed = true;
            }
        }

        if (!wasLoaded && cached.isLoaded()) {
            // Match the album with the artist
            Artist artist = mCache.getArtist(s.getArtist());
            if (artist != null) {
                Album album = mCache.getAlbum(s.getAlbum());
                if (album != null) {
                    artist.addAlbum(album.getRef());
                }
            }
        }

        if (changed) {
            postSongForUpdate(cached);
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
                Song song = mCache.getSong(songRef);

                if (song != null) {
                    String artistRef = song.getArtist();
                    Artist artist = mCache.getArtist(artistRef);

                    if (artist != null) {
                        artist.addAlbum(a.getRef());
                    } else {
                        Log.e(TAG, "Artist is null!");
                    }
                } else {
                    Log.e(TAG, "Song is null!");
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
        Artist cached = mCache.getArtist(a.getRef());

        if (cached == null) {
            mCache.putArtist(provider, a);
            postArtistForUpdate(a);
        } else if (!cached.getName().equals(a.getName()) || cached.isLoaded() != a.isLoaded()) {
            cached.setName(a.getName());
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

        if (mCachedSearch == null || !mCachedSearch.getQuery().equals(searchResult.getQuery())) {
            Log.d(TAG, "new search cache");
            mCachedSearch = searchResult;
        } else {
            //if we don't have the same search cache we merge the new and the cache
            Log.d(TAG, "updating search result");
            mCachedSearch.setIdentifier(searchResult.getIdentifier());
            for (String song : searchResult.getSongsList()) {
                if (!mCachedSearch.getSongsList().contains(song))
                    mCachedSearch.getSongsList().add(song);
            }
            for (String artist : searchResult.getArtistList()) {
                if (!mCachedSearch.getArtistList().contains(artist))
                    mCachedSearch.getArtistList().add(artist);
            }
            for (String album : searchResult.getAlbumsList()) {
                if (!mCachedSearch.getAlbumsList().contains(album))
                    mCachedSearch.getAlbumsList().add(album);
            }
            for (String playlist : searchResult.getPlaylistList()) {
                if (!mCachedSearch.getPlaylistList().contains(playlist))
                    mCachedSearch.getPlaylistList().add(playlist);
            }
        }
        for (ILocalCallback cb : mUpdateCallbacks) {
            cb.onSearchResult(mCachedSearch);
        }
    }

}
