package org.omnirom.music.api.musicbrainz;

import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.music.api.common.JsonGet;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * MusicBrainz API Client
 */
public class MusicBrainzClient {

    private static final String TAG = "MusicBrainzClient";
    private static final String MAIN_EP = "http://musicbrains.org/ws/2";
    private static final String COVER_EP = "http://coverartarchive.org/release/";

    private static final Map<Pair<String, String>, AlbumInfo> mAlbumInfoCache
            = new HashMap<Pair<String, String>, AlbumInfo>();
    private static final Map<String, String> mAlbumArtCache = new HashMap<String, String>();

    /**
     * Retrieves the album information from MusicBrainz. This method is synchronous and must be
     * called from a Thread!
     * @param artist The name of the artist. Must be filled.
     * @param album The name of the album. May be empty.
     * @return An {@link org.omnirom.music.api.musicbrainz.AlbumInfo} filled with the information
     * from musicbrainz, or null in case of error
     */
    public static AlbumInfo getAlbum(String artist, String album) {
        if (mAlbumInfoCache.containsKey(Pair.create(artist, album))) {
            return mAlbumInfoCache.get(Pair.create(artist, album));
        }

        try {
            String query = URLEncoder.encode("artist:\"" + artist + "\"", "UTF-8");
            if (!album.isEmpty()) {
                query += URLEncoder.encode(" AND release:\"" + album + "\"", "UTF-8");
            }
            JSONObject object = JsonGet.getObject(MAIN_EP + "/release/", "fmt=json&query=" + query);

            if (object.has("releases")) {
                JSONArray releases = object.getJSONArray("releases");
                if (releases.length() > 0) {
                    AlbumInfo info = new AlbumInfo();

                    JSONObject release = releases.getJSONObject(0);

                    info.id = release.getString("id");
                    info.track_count = release.getInt("track-count");

                    mAlbumInfoCache.put(Pair.create(artist, album), info);
                    return info;
                }
            }

            // We retry with just the artist instead, too bad for the album (if we didn't
            // already).
            mAlbumInfoCache.put(Pair.create(artist, album), null);
            if (!album.isEmpty()) {
                return getAlbum(artist, "");
            } else {
                // Log.e(TAG, "No match at all for the artist '" + artist + "' (" + MAIN_EP + "/release/?fmt=json&query=" + query + ")");
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to get album info", e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSON error while parsing album info", e);
            return null;
        }
    }

    /**
     * Returns the URL to an image representing the album art of the provided album ID. This album
     * id must be retrieved with {@link #getAlbum(String, String)}
     * @param albumId The album ID
     * @return An album art URL, or null if none found
     */
    public static String getAlbumArtUrl(String albumId) {
        if (mAlbumArtCache.containsKey(albumId)) {
            return mAlbumArtCache.get(albumId);
        }

        try {
            JSONObject object = JsonGet.getObject(COVER_EP + albumId, "");

            // We take the very first art here, no matter if it's front or back. Eventually some
            // day, we might filter only front art.
            JSONArray images = object.getJSONArray("images");
            JSONObject image = images.getJSONObject(0);

            String output = image.getJSONObject("thumbnails").getString("large");
            mAlbumArtCache.put(albumId, output);
            return output;
        } catch (IOException e) {
            mAlbumArtCache.put(albumId, null);
            return null;
        } catch (JSONException e) {
            mAlbumArtCache.put(albumId, null);
            return null;
        }
    }

}
