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

import android.util.Log;

import org.apache.http.util.ByteArrayBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * HTTP GET helper
 */
public class HttpGet {

    private static final String TAG = "HttpGet";

    /**
     * Downloads the data from the provided URL.
     * @param inUrl The URL to get from
     * @param query The query field. '?' + query will be appended automatically, and the query data
     *              MUST be encoded properly.
     * @return A string with the data grabbed from the URL
     */
    public static String get(String inUrl, String query, boolean cached)
            throws IOException, RateLimitException {
        return new String(getBytes(inUrl, query, cached));
    }

    /**
     * Downloads the data from the provided URL.
     * @param inUrl The URL to get from
     * @param query The query field. '?' + query will be appended automatically, and the query data
     *              MUST be encoded properly.
     * @return A byte array of the data
     */
    public static byte[] getBytes(String inUrl, String query, boolean cached)
            throws IOException, RateLimitException {
        final String formattedUrl = inUrl + (query.isEmpty() ? "" : ("?" + query));

        Log.d(TAG, "Formatted URL: " + formattedUrl);

        URL url = new URL(formattedUrl);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestProperty("User-Agent","OmniMusic/1.0-dev (http://www.omnirom.org)");
        urlConnection.setUseCaches(cached);
        urlConnection.setInstanceFollowRedirects(true);
        int maxStale = 60 * 60 * 24 * 28; // tolerate 4-weeks stale
        urlConnection.addRequestProperty("Cache-Control", "max-stale=" + maxStale);
        try {
            final int status = urlConnection.getResponseCode();
            // MusicBrainz returns 503 Unavailable on rate limit errors. Parse the JSON anyway.
            if (status == HttpURLConnection.HTTP_OK) {
                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                int contentLength = urlConnection.getContentLength();
                if (contentLength <= 0) {
                    // No length? Let's allocate 100KB.
                    contentLength = 100 * 1024;
                }
                ByteArrayBuffer bab = new ByteArrayBuffer(contentLength);
                BufferedInputStream bis = new BufferedInputStream(in);
                int character;

                while ((character = bis.read()) != -1) {
                    bab.append(character);
                }
                return bab.toByteArray();
            } else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                // 404
                return new byte[]{};
            } else if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                return new byte[]{};
            } else if (status == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new RateLimitException();
            } else if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == 307 /* HTTP/1.1 TEMPORARY REDIRECT */
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                // We've been redirected, follow the new URL
                final String followUrl = urlConnection.getHeaderField("Location");
                Log.e(TAG, "Redirected to: " +  followUrl);
                return getBytes(followUrl, "", cached);
            } else {
                Log.e(TAG, "Error when fetching: " + formattedUrl + " (" + urlConnection.getResponseCode() + ")");
                return new byte[]{};
            }
        } finally {
            urlConnection.disconnect();
        }
    }
}
