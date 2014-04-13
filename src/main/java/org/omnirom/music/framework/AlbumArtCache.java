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

    public void initialize(Context ctx) {
        mContext = ctx;
        mPrefs = ctx.getSharedPreferences(TAG, 0);
        mEditor = mPrefs.edit();
    }

    public String getAlbumArtUrl(String artist, String album) {
        return mPrefs.getString(artist + album, null);
    }

    public void putAlbumArtUrl(String artist, String album, String key) {
        mEditor.putString(artist + album, key);
        mEditor.apply();
    }

    public static String getArtKey(final Song song, StringBuffer artUrl) {
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        String artKey = DEFAULT_ART;
        final Artist artist = cache.getArtist(song.getArtist());
        if (artist != null) {
            // Check if we have it in cache
            String tmpUrl = AlbumArtCache.getDefault().getAlbumArtUrl(artist.getName(), song.getTitle());
            if (tmpUrl == null) {
                try {
                    AlbumInfo albumInfo = MusicBrainzClient.getAlbum(artist.getName(), song.getTitle());
                    if (albumInfo != null) {
                        tmpUrl = MusicBrainzClient.getAlbumArtUrl(albumInfo.id);

                        if (tmpUrl != null) {
                            artUrl.append(tmpUrl);
                            AlbumArtCache.getDefault().putAlbumArtUrl(artist.getName(), song.getTitle(), tmpUrl);
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
