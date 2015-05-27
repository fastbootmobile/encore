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

package com.fastbootmobile.encore.api.musicbrainz;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.fastbootmobile.encore.api.common.JsonGet;
import com.fastbootmobile.encore.api.common.Pair;
import com.fastbootmobile.encore.api.common.RateLimitException;

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

    private static final Map<Pair<String, String>, AlbumInfo[]> mAlbumInfoCache
            = new HashMap<>();
    private static final Map<String, String> mAlbumArtCache = new HashMap<>();

    /**
     * Retrieves the album information from MusicBrainz. This method is synchronous and must be
     * called from a Thread!
     * @param artist The name of the artist. Must be filled.
     * @param album The name of the album. May be empty.
     * @return An {@link com.fastbootmobile.encore.api.musicbrainz.AlbumInfo} filled with the information
     * from musicbrainz, or null in case of error
     */
    public static AlbumInfo[] getAlbum(String artist, String album) throws RateLimitException {
        if (mAlbumInfoCache.containsKey(Pair.create(artist, album))) {
            return mAlbumInfoCache.get(Pair.create(artist, album));
        }

        if (artist == null && album == null) {
            return null;
        }

        try {
            String query = "";
            if (artist != null) {
                query += URLEncoder.encode("artist:\"" + artist + "\"", "UTF-8");
            }
            if (album != null && !album.isEmpty()) {
                query += URLEncoder.encode(" AND release:\"" + album + "\"", "UTF-8");
            }
            JSONObject object = JsonGet.getObject(MAIN_EP + "/release/", "fmt=json&query=" + query, true);

            if (object.has("releases")) {
                JSONArray releases = object.getJSONArray("releases");
                final int releasesCount = releases.length();
                if (releasesCount > 0) {
                    AlbumInfo[] infoArray = new AlbumInfo[releasesCount];

                    for (int i = 0; i < releasesCount; i++) {
                        AlbumInfo info = new AlbumInfo();

                        JSONObject release = releases.getJSONObject(i);

                        info.id = release.getString("id");
                        try {
                            info.track_count = release.getInt("track-count");
                        } catch (JSONException e) {
                            // No track count info, too bad
                            info.track_count = 0;
                        }

                        infoArray[i] = info;
                    }

                    mAlbumInfoCache.put(Pair.create(artist, album), infoArray);
                    return infoArray;
                }
            } else if (object.has("error")) {
                Log.w(TAG, "Rate limited by the API, will retry later");
                throw new RateLimitException();
            }

            // AlbumArtCache will retry with something else if needed
            mAlbumInfoCache.put(Pair.create(artist, album), null);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Unable to get album info (rate limit?)", e);
            throw new RateLimitException();
        } catch (JSONException e) {
            // May happen due to an API error, e.g. error 502
            Log.e(TAG, "JSON error while parsing album info", e);
            throw new RateLimitException();
        }
    }

    /**
     * Returns the URL to an image representing the album art of the provided album ID. This album
     * id must be retrieved with {@link #getAlbum(String, String)}
     * @param albumId The album ID
     * @return An album art URL, or null if none found
     */
    public static String getAlbumArtUrl(String albumId) throws RateLimitException {
        if (mAlbumArtCache.containsKey(albumId)) {
            return mAlbumArtCache.get(albumId);
        }

        try {
            JSONObject object = JsonGet.getObject(COVER_EP + albumId, "", true);

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
