package org.omnirom.music.framework;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Album art key store
 */
public class AlbumArtCache {

    private static final String TAG = "AlbumArtCache";
    private static final AlbumArtCache INSTANCE = new AlbumArtCache();
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

}
