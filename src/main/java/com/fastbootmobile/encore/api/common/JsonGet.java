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

package com.fastbootmobile.encore.api.common;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Helper class for JSON GET requests
 */
public class JsonGet {

    private static final String TAG = "JsonGet";

    /**
     * Downloads a JSON object from the provided URL.
     * @param url The URL to get from
     * @param query The query field. '?' + query will be appended automatically, and the query data
     *              will be encoded properly.
     * @return A json object
     */
    public static JSONObject getObject(String url, String query, boolean cached)
            throws IOException, JSONException, RateLimitException {
        return new JSONObject(HttpGet.get(url, query, cached));
    }
}
