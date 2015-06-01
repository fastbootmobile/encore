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

package com.fastbootmobile.encore.api.gimages;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.fastbootmobile.encore.api.common.JsonGet;
import com.fastbootmobile.encore.api.common.RateLimitException;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Client class to get images from Google Images
 */
public class GoogleImagesClient {
    private static final String TAG = "GoogleImagesClient";

    public static String getImageUrl(String query) throws IOException, JSONException, RateLimitException {
        if (query == null) {
            Log.e(TAG, "Null query!");
            return null;
        }

        String queryUrl = "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q="
                + URLEncoder.encode(query, "UTF-8") + "&imgsz=large";

        JSONObject obj = JsonGet.getObject(queryUrl, "", true);
        JSONArray results = obj.getJSONObject("responseData").getJSONArray("results");

        if (results.length() > 0) {
            return URLDecoder.decode(results.getJSONObject(0).getString("url"), "UTF-8");
        } else {
            return null;
        }
    }
}
