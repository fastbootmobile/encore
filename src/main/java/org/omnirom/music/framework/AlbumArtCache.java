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

package org.omnirom.music.framework;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONException;
import org.omnirom.music.api.common.HttpGet;
import org.omnirom.music.api.common.RateLimitException;
import org.omnirom.music.api.freebase.FreeBaseClient;
import org.omnirom.music.api.gimages.GoogleImagesClient;
import org.omnirom.music.api.musicbrainz.AlbumInfo;
import org.omnirom.music.api.musicbrainz.MusicBrainzClient;
import org.omnirom.music.app.Utils;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IArtCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cache downloading and handling album art
 */
public class AlbumArtCache {
    private static final String TAG = "AlbumArtCachev2";
    private static final AlbumArtCache INSTANCE = new AlbumArtCache();

    private final List<BoundEntity> mRunningQueries = new ArrayList<BoundEntity>();

    /**
     * The art is not in the cache
     */
    public static final int CACHE_STATUS_UNAVAILABLE = 0;

    /**
     * The art is available on the disk
     */
    public static final int CACHE_STATUS_DISK = 1;

    /**
     * The art is available in memory
     */
    public static final int CACHE_STATUS_MEMORY = 2;

    /**
     * @return Default instance of this class
     */
    public static AlbumArtCache getDefault() {
        return INSTANCE;
    }

    /**
     * Default constructor
     */
    private AlbumArtCache() {
    }

    /**
     * Empties the image cache
     */
    public void clear() {
        ImageCache.getDefault().clear();
    }

    /**
     * Returns whether or not the entity passed in parameter has an album art in the cache
     * @param ent The entity
     * @return One of {@link #CACHE_STATUS_DISK}, {@link #CACHE_STATUS_MEMORY}, or
     * {@link #CACHE_STATUS_UNAVAILABLE}
     */
    public int getCacheStatus(BoundEntity ent) {
        final String key = getEntityArtKey(ent);
        final ImageCache cache = ImageCache.getDefault();

        if (cache.hasInMemory(key)) {
            return CACHE_STATUS_MEMORY;
        } else if (cache.hasOnDisk(key)) {
            return CACHE_STATUS_DISK;
        } else {
            return CACHE_STATUS_UNAVAILABLE;
        }
    }

    /**
     * Returns the art key that is associated with the provided entity
     * @param ent The entity from which get the art key
     * @return The art key of the entity
     */
    public String getEntityArtKey(BoundEntity ent) {
        return ent.getRef();
    }

    /**
     * Returns the art associated with the entity
     * @param ent The entity
     */
    public boolean getArt(BoundEntity ent, IAlbumArtCacheListener listener) {
        final String key = getEntityArtKey(ent);
        final ImageCache cache = ImageCache.getDefault();
        boolean result = false;

        if (cache.hasInMemory(key) || cache.hasOnDisk(key)) {
            listener.onArtLoaded(ent, cache.get(key));
            result = true;
        } else {
            try {
                if (ent instanceof Song) {
                    result = getSongArt((Song) ent, listener);
                } else if (ent instanceof Artist) {
                    result = getArtistArt((Artist) ent, listener);
                } else if (ent instanceof Album) {
                    result = getAlbumArt((Album) ent, listener);
                } else if (ent instanceof Playlist) {
                    result = getPlaylistArt((Playlist) ent, listener);
                } else {
                    throw new IllegalArgumentException("Entity is of an unknown class!");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Remote Exception while trying to get album art", e);
            }
        }

        return result;
    }

    private boolean getSongArt(final Song song, final IAlbumArtCacheListener listener) throws RemoteException {
        // Try to get the art from the provider first
        final ProviderIdentifier id = song.getProvider();
        final IMusicProvider binder = safeGetBinder(id);

        boolean providerprovides = false;
        boolean result = false;

        if (binder != null) {
            providerprovides = result = binder.getSongArt(song, new IArtCallback.Stub() {
                @Override
                public void onArtLoaded(final Bitmap bitmap) throws RemoteException {
                    new Thread() {
                        public void run() {
                            RefCountedBitmap rfb = ImageCache.getDefault().put(getEntityArtKey(song), bitmap);
                            listener.onArtLoaded(song, rfb);
                        }
                    }.start();
                }
            });
        }

        if (!providerprovides) {
            // Provider can't provide an art for this song, get from MusicBrainz
            final ProviderAggregator aggregator = ProviderAggregator.getDefault();

            final Artist artist = aggregator.retrieveArtist(song.getArtist(), id);
            final Album album = aggregator.retrieveAlbum(song.getAlbum(), id);

            // If we have both the artist and the album name, use that for album art
            boolean hasAlbum = (album != null && album.getName() != null && !album.getName().isEmpty());
            boolean hasArtist = (artist != null && artist.getName() != null && !artist.getName().isEmpty());

            if (hasArtist && hasAlbum) {
                result = getAlbumArtImpl(album, artist, listener, song);
            } else if (hasAlbum && album.getSongsCount() > 0) {
                result = getAlbumArtImpl(album, null, listener, song);
            } else if (hasArtist) {
                // TODO: Get any album art from the artist
            } else {
                result = false;
            }
        }

        // TODO: Don't forget to push to imagecache!
        return result;
    }

    private boolean getAlbumArt(final Album album, final IAlbumArtCacheListener listener) throws RemoteException {
        return getAlbumArtImpl(album, null, listener, album);
    }

    private boolean getAlbumArtImpl(final Album album, final Artist hintArtist,
                                    final IAlbumArtCacheListener listener,
                                    final BoundEntity listenerRef) throws RemoteException {
        // Try to get the art from the provider first
        final ProviderIdentifier id = album.getProvider();
        final IMusicProvider binder = safeGetBinder(id);

        boolean providerprovides = false;
        boolean result = false;

        if (binder != null) {
            providerprovides = result = binder.getAlbumArt(album, new IArtCallback.Stub() {
                @Override
                public void onArtLoaded(final Bitmap bitmap) throws RemoteException {
                    new Thread() {
                        public void run() {
                            RefCountedBitmap rcb = ImageCache.getDefault().put(getEntityArtKey(album), bitmap);
                            listener.onArtLoaded(album, rcb);
                        }
                    }.start();
                }
            });
        }

        if (!providerprovides) {
            String url = null;
            final String artistRef = (album.getSongsCount() > 0 ? Utils.getMainArtist(album) : null);
            final String albumName = album.getName();
            String artistName = (hintArtist != null ? hintArtist.getName() : null);

            // If we have the artist, bias the search with it
            if (artistName == null && artistRef != null) {
                Artist artist = ProviderAggregator.getDefault().retrieveArtist(artistRef, id);
                if (artist != null && artist.getName() != null && !artist.getName().isEmpty()) {
                    artistName = artist.getName();
                }
            }

            // Try to get from Google Images
            try {
                if (artistName != null) {
                    url = GoogleImagesClient.getImageUrl("album " + artistName + " " + albumName);
                } else {
                    url = GoogleImagesClient.getImageUrl("album " + albumName);
                }
            } catch (RateLimitException e) {
                Log.w(TAG, "Rate limit hit while getting image from Google Images");
            } catch (JSONException e) {
                Log.e(TAG, "JSON Error while getting image from Google Images", e);
            } catch (IOException e) {
                Log.e(TAG, "IO error while getting image from Google Images", e);
            }

            if (url == null) {
                // Get from MusicBrainz as both the provider and Google Images can't provide
                AlbumInfo[] albums = null;

                // Query MusicBrainz
                try {
                    albums = MusicBrainzClient.getAlbum(artistName, albumName);
                } catch (RateLimitException e) {
                    Log.w(TAG, "Can't get from MusicBrainz, rate limited");
                }

                // If we have results, go and fetch one
                if (albums != null && albums.length > 0) {
                    AlbumInfo selection = albums[0];

                    if (albums.length > 1) {
                        // Try to find if we have an album that has the same track count, otherwise use
                        // the first one
                        for (AlbumInfo albumInfo : albums) {
                            if (albumInfo.track_count == album.getSongsCount()) {
                                selection = albumInfo;
                                break;
                            }
                        }
                    }

                    // Get the URL
                    try {
                        url = MusicBrainzClient.getAlbumArtUrl(selection.id);
                    } catch (RateLimitException e) {
                        Log.w(TAG, "Can't get URL from MusicBrainz, rate limited");
                    }
                }
            }

            // If we have an URL from either image source, download it and pass it back
            if (url != null) {
                // Download it
                try {
                    byte[] imageData = HttpGet.getBytes(url, "", true);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                    if (bitmap != null) {
                        result = true;
                        RefCountedBitmap rcb = ImageCache.getDefault().put(getEntityArtKey(listenerRef), bitmap);
                        listener.onArtLoaded(listenerRef, rcb);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error while downloading album art", e);
                } catch (RateLimitException e) {
                    Log.e(TAG, "Rate limited while downloading album art", e);
                }
            } else {
                ImageCache.getDefault().put(getEntityArtKey(listenerRef), (RefCountedBitmap) null);
                listener.onArtLoaded(listenerRef, null);
                result = true;
            }
        }

        return result;
    }

    private boolean getArtistArt(final Artist artist, final IAlbumArtCacheListener listener) throws RemoteException {
        // Try to get the art from the provider first
        final ProviderIdentifier id = artist.getProvider();
        final IMusicProvider binder = safeGetBinder(id);

        boolean providerprovides = false;
        boolean result = false;

        if (binder != null) {
            providerprovides = result = binder.getArtistArt(artist, new IArtCallback.Stub() {
                @Override
                public void onArtLoaded(final Bitmap bitmap) throws RemoteException {
                    new Thread() {
                        public void run() {
                            RefCountedBitmap rcb = ImageCache.getDefault().put(getEntityArtKey(artist), bitmap);
                            listener.onArtLoaded(artist, rcb);
                        }
                    }.start();
                }
            });
        }

        if (!providerprovides) {
            // Hardcode warning: Image providers tend to return... funny images for empty
            // names and '<unknown>'. We just don't want images for them then.
            if (artist.getName().isEmpty() || "<unknown>".equals(artist.getName())) {
                return false;
            }

            // Try to get it first from FreeBase, then from Google Image if none found or error
            // (Google Images might return some random/unwanted images, so prefer FreeBase first)
            String url = null;

            try {
                url = FreeBaseClient.getArtistImageUrl(artist.getName());
            } catch (JSONException e) {
                Log.e(TAG, "JSON error while getting image from FreeBase", e);
            } catch (RateLimitException e) {
                Log.w(TAG, "Rate limit hit while getting image from FreeBase");
            } catch (IOException e) {
                Log.e(TAG, "IO error while getting image from FreeBase", e);
            }

            if (url == null) {
                try {
                    url = GoogleImagesClient.getImageUrl("Music Band " + artist.getName());
                } catch (RateLimitException e) {
                    Log.w(TAG, "Rate limit hit while getting image from Google Images");
                } catch (JSONException e) {
                    Log.e(TAG, "JSON Error while getting image from Google Images", e);
                } catch (IOException e) {
                    Log.e(TAG, "IO error while getting image from Google Images", e);
                }

            }

            if (url != null) {
                try {
                    byte[] imageData = HttpGet.getBytes(url, "", true);
                    Bitmap image = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                    if (image != null) {
                        result = true;
                        RefCountedBitmap rcb = ImageCache.getDefault().put(getEntityArtKey(artist), image);
                        listener.onArtLoaded(artist, rcb);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IO exception while downloading image", e);
                } catch (RateLimitException e) {
                    Log.w(TAG, "Rate limited while getting image");
                }
            } else {
                ImageCache.getDefault().put(getEntityArtKey(artist), (RefCountedBitmap) null);
                listener.onArtLoaded(artist, null);
                result = true;
            }
        }

        return result;
    }

    private boolean getPlaylistArt(final Playlist playlist, final IAlbumArtCacheListener listener) throws RemoteException {
        // Try to get the art from the provider first
        final ProviderIdentifier id = playlist.getProvider();
        final IMusicProvider binder = safeGetBinder(id);

        boolean providerprovides = false;
        boolean result = false;

        if (binder != null) {
            providerprovides = result = binder.getPlaylistArt(playlist, new IArtCallback.Stub() {
                @Override
                public void onArtLoaded(final Bitmap bitmap) throws RemoteException {
                    new Thread() {
                        public void run() {
                            RefCountedBitmap rcb = ImageCache.getDefault().put(getEntityArtKey(playlist), bitmap);
                            listener.onArtLoaded(playlist, rcb);
                        }
                    }.start();
                }
            });
        }

        if (!providerprovides) {
            // Build our own art
            final PlaylistArtBuilder builder = new PlaylistArtBuilder();
            builder.start(playlist, new IArtCallback.Stub() {
                @Override
                public void onArtLoaded(final Bitmap bitmap) throws RemoteException {
                    new Thread() {
                        public void run() {
                            RefCountedBitmap rcb = ImageCache.getDefault().put(getEntityArtKey(playlist), bitmap);
                            listener.onArtLoaded(playlist, rcb);
                            builder.freeMemory();
                        }
                    }.start();
                }
            });
        }

        return result;
    }

    private IMusicProvider safeGetBinder(final ProviderIdentifier id) {
        final ProviderConnection conn = PluginsLookup.getDefault().getProvider(id);
        if (conn != null) {
            return conn.getBinder();
        } else {
            return null;
        }
    }

    /**
     * Returns whether or not a query is currently running for the provided song
     * @param song The song for which we need the album art
     * @return true if a request is currently running, false if nothing is currently looking for
     * that song's art
     */
    public boolean isQueryRunning(final BoundEntity song) {
        synchronized (mRunningQueries) {
            return mRunningQueries.contains(song);
        }
    }

    /**
     * Notifies an async task has started processing the art for the provided entity
     * @param song The song for which we need the album art
     */
    public void notifyQueryRunning(final BoundEntity song) {
        synchronized (mRunningQueries) {
            mRunningQueries.add(song);
        }
    }

    /**
     * Notifies that the async task that started processing the art for the entity has done
     * @param song The song for which we need the album art
     */
    public void notifyQueryStopped(final BoundEntity song) {
        synchronized (mRunningQueries) {
            mRunningQueries.remove(song);
        }
    }

    public interface IAlbumArtCacheListener {
        public void onArtLoaded(BoundEntity ent, RefCountedBitmap result);
    }
}
