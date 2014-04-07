package org.omnirom.music.api.common;

import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.Buffer;

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
            throws IOException, JSONException, RateLimitException {
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

        URL url = new URL(formattedUrl);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestProperty("User-Agent","OmniMusic/1.0-dev (http://www.omnirom.org)");
        urlConnection.setUseCaches(cached);
        int maxStale = 60 * 60 * 24 * 28; // tolerate 4-weeks stale
        urlConnection.addRequestProperty("Cache-Control", "max-stale=" + maxStale);
        try {
            // MusicBrainz returns 503 Unavailable on rate limit errors. Parse the JSON anyway.
            if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
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
            } else if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                // 404
                return new byte[]{};
            } else if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                return new byte[]{};
            } else if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new RateLimitException();
            } else {
                Log.e(TAG, "Error when fetching: " + formattedUrl);
                return new byte[]{};
            }
        } finally {
            urlConnection.disconnect();
        }
    }
}
