package org.omnirom.music.api.freebase;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.music.api.common.JsonGet;
import org.omnirom.music.api.common.RateLimitException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by xplodwild on 7/7/14.
 */
public class FreeBaseClient {
    private static final String TAG = "FreeBaseClient";

    private static final String API_ENDPOINT = "https://www.googleapis.com/freebase/v1/search";
    private static final String TOPIC_ENDPOINT = "https://www.googleapis.com/freebase/v1/topic";
    private static final String IMAGE_ENDPOINT = "https://usercontent.googleapis.com/freebase/v1/image";

    public static String getArtistImageUrl(String artist)
            throws JSONException, RateLimitException, IOException {
        final String ecFilter = URLEncoder.encode("(all type:/music/artist)", "UTF-8");
        final String ecArtist = URLEncoder.encode(artist, "UTF-8");

        JSONObject object = JsonGet.getObject(API_ENDPOINT,
                "query=" + ecArtist + "&filter=" + ecFilter + "&limit=1",
                true);

        JSONArray result = object.getJSONArray("result");
        JSONObject firstItem = result.getJSONObject(0);
        String metaId = firstItem.getString("mid");

        // While we could get an image immediately, we're not sure there's actually an image
        // for that topic. We do one more query to not end up with an ugly "NO IMAGE" result.
        // We do take the risk however by allowing the image anyway if we go above the rate limit
        // of Google's API, as the topic endpoint is rate-limited.
        object = JsonGet.getObject(TOPIC_ENDPOINT + metaId, "filter=/common/topic/image&limit=1", true);

        if (object.has("property") || object.has("error")) {
            return IMAGE_ENDPOINT + metaId + "?maxwidth=1080&maxheight=1080";
        } else {
            return null;
        }
    }
}
