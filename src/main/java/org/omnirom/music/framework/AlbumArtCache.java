package org.omnirom.music.framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.omnirom.music.api.common.HttpGet;
import org.omnirom.music.api.common.RateLimitException;
import org.omnirom.music.api.freebase.FreeBaseClient;
import org.omnirom.music.api.musicbrainz.AlbumInfo;
import org.omnirom.music.api.musicbrainz.MusicBrainzClient;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.io.IOException;

/**
 * Album art key store
 */
public class AlbumArtCache {

    private static final String TAG = "AlbumArtCache";
    private static final AlbumArtCache INSTANCE = new AlbumArtCache();
    public static final String DEFAULT_ART = "__DEFAULT_ALBUM_ART__";

    private Context mContext;
    private SharedPreferences mPrefs;
    private SharedPreferences.Editor mEditor;

    public static AlbumArtCache getDefault() { return INSTANCE; }

    /**
     * Setups the album art cache. This is called during application initialization.
     * @param ctx The application context
     */
    public void initialize(Context ctx) {
        mContext = ctx;
        mPrefs = ctx.getSharedPreferences(TAG, 0);
        mEditor = mPrefs.edit();
    }

    /**
     * Returns the album art url for the provided artist and album name from the cache.
     * @param artist Artist name
     * @param album Album name
     * @return An URL to an album art image, or null if none is cached
     */
    public String getAlbumArtUrl(String artist, String album) {
        return mPrefs.getString(artist + album, null);
    }

    /**
     * Returns the artist cover image for the provided artist from the cache
     * @param artist Artist name
     * @return An URL to an artist cover image, or null if none is cached
     */
    public String getArtistCoverUrl(String artist) {
        return mPrefs.getString("___OM__ARTIST__COVER____" + artist, null);
    }

    /**
     * Stores an album art url in the cache.
     * @param artist Artist name
     * @param album Album name
     * @param key The album art cache key
     */
    public void putAlbumArtUrl(String artist, String album, String key) {
        mEditor.putString(artist + album, key);
        mEditor.apply();
    }

    public void putArtistArtUrl(String artist, String url) {
        mEditor.putString("___OM__ARTIST__COVER____" + artist, url);
        mEditor.apply();
    }

    public static String getArtKey(final Album album,StringBuffer artUrl) {
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        if (album == null  || album.songs() == null || !album.songs().hasNext()) {
            return DEFAULT_ART;
        }

        String songRef = album.songs().next();
        Song song = cache.getSong(songRef);
        String key = DEFAULT_ART;
        if(song != null) {
            key = getArtKey(song, artUrl);
            if(!key.equals(DEFAULT_ART))
                cache.putAlbumArtKey(album, key);
        }
        return key;
    }

    public static String getArtKey(final Artist artist, StringBuffer artUrl) {
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        String artKey = DEFAULT_ART;
        if (artist != null) {
            // Any art from that artist will be fine.
            // Check if we have it in cache
            String tmpUrl = AlbumArtCache.getDefault().getArtistCoverUrl(artist.getName());
            if (tmpUrl == null) {
                // We don't, fetch from Freebase
                try {
                    tmpUrl = FreeBaseClient.getArtistImageUrl(artist.getName());

                    if (tmpUrl == null) {
                        // FreeBase has nothing, we get an album art instead from MusicBrainz
                        tmpUrl = AlbumArtCache.getDefault().getAlbumArtUrl(artist.getName(), null);

                        if (tmpUrl == null) {
                            AlbumInfo[] infos = MusicBrainzClient.getAlbum(artist.getName(), null);

                            for (AlbumInfo info : infos) {
                                tmpUrl = MusicBrainzClient.getAlbumArtUrl(info.id);
                                if (tmpUrl != null) {
                                    // Cache it for the album
                                    AlbumArtCache.getDefault().putAlbumArtUrl(artist.getName(), null, tmpUrl);
                                    break;
                                }
                            }
                        }
                    }

                    if (tmpUrl != null) {
                        AlbumArtCache.getDefault().putArtistArtUrl(artist.getName(), tmpUrl);
                        artKey = tmpUrl.replaceAll("\\W+", "");
                        cache.putArtistArtKey(artist, artKey);

                    }
                    if (tmpUrl == null) {
                        cache.putArtistArtKey(artist, DEFAULT_ART);
                        artKey = DEFAULT_ART;
                        Log.e("XPLOD", "No FB match for this query!");
                    } else {
                        artUrl.append(tmpUrl);
                        Log.e("XPLOD", "FB found a match: " + tmpUrl + " (" + artUrl.toString() + ")");
                    }
                } catch (Exception e) {
                    // Retry later, rate limited or network error / early exit (don't cache that)
                    artKey = DEFAULT_ART;
                    return artKey;
                }
            } else {
                artKey = tmpUrl.replaceAll("\\W+", "");
                cache.putArtistArtKey(artist, artKey);
            }

            if (artKey.equals(DEFAULT_ART)) {
                // Really, we don't know these guys.
                cache.putArtistArtKey(artist, DEFAULT_ART);
                tmpUrl = DEFAULT_ART;
                Log.e(TAG, "No match found for " + artist.getName() + ", at all");
            }
        }

        return artKey;
    }

    /**
     * Fetches an URL (and/or cache key) for the album art for the provided song. The art URL
     * will be put in the StringBuffer passed in artUrl.
     * This method does synchronous network calls, so it must NOT be called from the UI thread.
     * @param song The song to fetch the album from
     * @param artUrl The stringbuffer in which the album art will be put
     * @return The cache key for the album art
     */
    public static String getArtKey(final Song song, StringBuffer artUrl) {
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        String artKey = DEFAULT_ART;
        if (song == null) {
            Log.e(TAG, "Song is null");
            return artKey;
        }

        final Artist artist = cache.getArtist(song.getArtist());
        if (artist != null) {
            // If we have album information about this song, use the album name to fetch the cover.
            // Otherwise, use the title. However, if the upstream API doesn't have the cover for
            // the exact album, try the song title. And if still no luck, any art from that
            // artist will be fine.
            String queryStr;
            String initialQueryStr;
            boolean retry = true;
            boolean triedTitle = false;
            boolean triedArtist = false;

            final Album album = cache.getAlbum(song.getAlbum());
            if (album != null && album.getName() != null && !album.getName().isEmpty()) {
                queryStr = album.getName();
            } else {
                queryStr = song.getTitle();
                triedTitle = true;
            }

            initialQueryStr = new String(queryStr);

            // We try first the exact album, then the title, then just the artist
            while (retry) {
                // Escape the query
                if (queryStr != null) {
                    queryStr = queryStr.replace('"', ' ');
                }

                // Check if we have it in cache
                String tmpUrl = AlbumArtCache.getDefault().getAlbumArtUrl(artist.getName(), queryStr);
                if (tmpUrl == null) {
                    // We don't, fetch from MusicBrainz
                    try {
                        AlbumInfo[] albumInfoArray = MusicBrainzClient.getAlbum(artist.getName(), queryStr);

                        if (albumInfoArray != null) {
                            Log.e("XPLOD", "MBC found " + albumInfoArray.length + " albums");

                            for (AlbumInfo albumInfo : albumInfoArray) {
                                tmpUrl = MusicBrainzClient.getAlbumArtUrl(albumInfo.id);

                                if (tmpUrl != null) {
                                    AlbumArtCache.getDefault().putAlbumArtUrl(artist.getName(), initialQueryStr, tmpUrl);
                                    artKey = tmpUrl.replaceAll("\\W+", "");
                                    cache.putSongArtKey(song, artKey);
                                    break;
                                }
                            }

                            if (tmpUrl == null) {
                                cache.putSongArtKey(song, DEFAULT_ART);
                                artKey = DEFAULT_ART;
                                Log.e("XPLOD", "No MBC match for this query!");
                            } else {
                                artUrl.append(tmpUrl);
                                Log.e("XPLOD", "MBC found a match: " + tmpUrl + " (" + artUrl.toString() + ")");
                            }
                        } else {
                            // No album found on musicbrainz
                            cache.putSongArtKey(song, DEFAULT_ART);
                            artKey = DEFAULT_ART;
                        }
                    } catch (RateLimitException e) {
                        // Retry later, rate limited
                        artKey = DEFAULT_ART;
                    }
                } else {
                    artKey = tmpUrl.replaceAll("\\W+", "");
                    cache.putSongArtKey(song, artKey);
                }

                if (artKey.equals(DEFAULT_ART)) {
                    if (!triedTitle) {
                        // Couldn't find the exact album, try the song title
                        triedTitle = true;
                        queryStr = song.getTitle();
                        Log.e(TAG, "No exact match found for " + artist.getName() + ", trying title");
                    } else if (!triedArtist) {
                        // Coudln't find neither the exact album, nor the title, try the artist
                        triedArtist = true;
                        queryStr = null;
                        Log.e(TAG, "No title match found for " + artist.getName() + ", trying artist");
                    } else {
                        // Really, we don't know these guys.
                        retry = false;
                        cache.putSongArtKey(song, DEFAULT_ART);
                        tmpUrl = DEFAULT_ART;
                        Log.e(TAG, "No match found for " + artist.getName() + ", at all");
                    }
                } else {
                    retry = false;
                }

                if (!retry) {
                    // Ensure we have this entry, even if it's not the exact original query (e.g.
                    // used song title instead of album name) - we'll cache it for that.
                    AlbumArtCache.getDefault().putAlbumArtUrl(artist.getName(), initialQueryStr, tmpUrl);
                }
            }

        }

        return artKey;
    }

    /**
     * Fetches the album art from the provided URL (or returns the cached image). This method
     * does synchronous network calls, so it must NOT be called from the UI thread.
     * @param artKey The cache key for the album art
     * @param artUrl The URL to the album art image
     * @param def The default bitmap to return in case of error
     * @return A bitmap containing the album art
     */
    public static Bitmap getOrDownloadArt(final String artKey, final String artUrl, final Bitmap def) {
        // Check if we have it in our albumart local cache
        Bitmap bmp = ImageCache.getDefault().get(artKey);

        try {
            if (bmp == null) {
                if (artUrl != null && !artUrl.isEmpty()) {
                    try {
                        byte[] imageData = HttpGet.getBytes(artUrl, "", true);
                        bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                        if (bmp != null) {
                            ImageCache.getDefault().put(artKey, bmp);
                        } else {
                            // This may happen if the image is corrupted
                            bmp = def;
                        }
                    } catch (RateLimitException e) {
                        Log.w(TAG, "Cannot get album art image data (rate limit)");
                        bmp = def;
                    }
                } else {
                    bmp = def;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to get album art", e);
        }

        return bmp;
    }
}
