package org.omnirom.music.framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.omnirom.music.api.common.HttpGet;
import org.omnirom.music.api.common.RateLimitException;
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
     * Stores an album art url in the cache.
     * @param artist Artist name
     * @param album Album name
     * @param key The album art cache key
     */
    public void putAlbumArtUrl(String artist, String album, String key) {
        mEditor.putString(artist + album, key);
        mEditor.apply();
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
        final Artist artist = cache.getArtist(song.getArtist());
        if (artist != null) {
            // If we have album information about this song, use the album name to fetch the cover.
            // Otherwise, use the title.
            String albumStr;
            final Album album = cache.getAlbum(song.getAlbum());
            if (album != null && album.getName() != null && !album.getName().isEmpty()) {
                albumStr = album.getName();
                Log.i(TAG, "Using album name: " + albumStr);
            } else {
                albumStr = song.getTitle();
                Log.i(TAG, "Using song title: " + albumStr);
            }

            // Check if we have it in cache
            String tmpUrl = AlbumArtCache.getDefault().getAlbumArtUrl(artist.getName(), albumStr);
            if (tmpUrl == null) {
                // We don't, fetch from MusicBrainz
                try {
                    AlbumInfo albumInfo = MusicBrainzClient.getAlbum(artist.getName(), albumStr);
                    if (albumInfo != null) {
                        tmpUrl = MusicBrainzClient.getAlbumArtUrl(albumInfo.id);

                        if (tmpUrl != null) {
                            artUrl.append(tmpUrl);
                            AlbumArtCache.getDefault().putAlbumArtUrl(artist.getName(), albumStr, tmpUrl);
                            artKey = tmpUrl.replaceAll("\\W+", "");
                            cache.putSongArtKey(song, artKey);
                        } else {
                            cache.putSongArtKey(song, DEFAULT_ART);
                            artKey = DEFAULT_ART;
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
                if (artUrl != null) {
                    try {
                        byte[] imageData = HttpGet.getBytes(artUrl, "", true);
                        bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                        ImageCache.getDefault().put(artKey, bmp);
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
