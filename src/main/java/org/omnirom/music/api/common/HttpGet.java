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
     * @param url The URL to get from
     * @param query The query field. '?' + query will be appended automatically, and the query data
     *              MUST be encoded properly.
     * @return A string with the data grabbed from the URL
     */
    public static String get(String url, String query) throws IOException, JSONException {
        final String formattedUrl = url + "?" + query;

        DefaultHttpClient httpClient = new DefaultHttpClient();
        org.apache.http.client.methods.HttpGet httpGet = new org.apache.http.client.methods.HttpGet(formattedUrl);

        HttpResponse httpResponse = httpClient.execute(httpGet);
        HttpEntity httpEntity = httpResponse.getEntity();

        return EntityUtils.toString(httpEntity);
    }

    /**
     * Downloads the data from the provided URL.
     * @param url The URL to get from
     * @param query The query field. '?' + query will be appended automatically, and the query data
     *              will be encoded properly.
     * @return A byte array of the data
     */
    public static byte[] getBytes(String url, String query) throws IOException {
        final String formattedUrl = url + "?" + URLEncoder.encode(query, "UTF-8");
        DefaultHttpClient httpClient = new DefaultHttpClient();
        org.apache.http.client.methods.HttpGet httpGet = new org.apache.http.client.methods.HttpGet(formattedUrl);

        HttpResponse httpResponse = httpClient.execute(httpGet);
        HttpEntity httpEntity = httpResponse.getEntity();

        ByteArrayBuffer bab = new ByteArrayBuffer((int) httpEntity.getContentLength());
        BufferedInputStream bis = new BufferedInputStream(httpEntity.getContent());
        int character;

        while ((character = bis.read()) != -1) {
            bab.append(character);
        }

        return bab.toByteArray();
    }
}
