package org.omnirom.music.api.common;

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
    public static JSONObject getObject(String url, String query, boolean cached) throws IOException, JSONException {
        return new JSONObject(HttpGet.get(url, query, cached));
    }
}
