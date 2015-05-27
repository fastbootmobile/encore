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

package com.fastbootmobile.encore.api.freebase;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.fastbootmobile.encore.api.common.JsonGet;
import com.fastbootmobile.encore.api.common.RateLimitException;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * Client for the Google FreeBase API
 */
public class FreeBaseClient {
    private static final String TAG = "FreeBaseClient";

    private static final String API_ENDPOINT = "https://www.googleapis.com/freebase/v1/search";
    private static final String TOPIC_ENDPOINT = "https://www.googleapis.com/freebase/v1/topic";
    private static final String IMAGE_ENDPOINT = "https://usercontent.googleapis.com/freebase/v1/image";

    /**
     * Fetches an Artist Image URL
     * @param artist The name of the artist
     * @return An URL to an image representing this artist
     * @throws JSONException
     * @throws RateLimitException
     * @throws IOException
     */
    public static String getArtistImageUrl(String artist)
            throws JSONException, RateLimitException, IOException {
        if (artist == null) {
            Log.e(TAG, "getArtistImageUrl: Null artist requested");
            return null;
        }

        final String ecFilter = URLEncoder.encode("(all type:/music/artist)", "UTF-8");
        final String ecArtist = URLEncoder.encode(artist, "UTF-8");

        JSONObject object = JsonGet.getObject(API_ENDPOINT,
                "query=" + ecArtist + "&filter=" + ecFilter + "&limit=1",
                true);

        JSONArray result = object.getJSONArray("result");
        if (result.length() > 0) {
            JSONObject firstItem = result.getJSONObject(0);
            String metaId = firstItem.getString("mid");

            // While we could get an image immediately, we're not sure there's actually an image
            // for that topic. We do one more query to not end up with an ugly "NO IMAGE" result.
            // We do take the risk however by allowing the image anyway if we go above the rate limit
            // of Google's API, as the topic endpoint is rate-limited and we're not using any API key.
            object = JsonGet.getObject(TOPIC_ENDPOINT + metaId, "filter=/common/topic/image&limit=1", true);

            if (object.has("property") || object.has("error")) {
                return IMAGE_ENDPOINT + metaId + "?maxwidth=800&maxheight=800";
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
