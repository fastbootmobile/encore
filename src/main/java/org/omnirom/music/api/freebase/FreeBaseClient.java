package org.omnirom.music.api.freebase;

import org.json.JSONObject;
import org.omnirom.music.api.common.JsonGet;

/**
 * Created by xplodwild on 7/7/14.
 */
public class FreeBaseClient {

    private static final String API_ENDPOINT = "https://www.googleapis.com/freebase/v1/search";
    private static final String IMAGE_ENDPOINT = "https://usercontent.googleapis.com/freebase/v1/image";

    public static String getArtistImage(String artist) {
        JSONObject object = JsonGet.getObject(API_ENDPOINT + "/?query=");
    }
}
