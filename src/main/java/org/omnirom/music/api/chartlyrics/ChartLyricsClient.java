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

package org.omnirom.music.api.chartlyrics;

import android.util.Log;

import org.omnirom.music.api.common.HttpGet;
import org.omnirom.music.api.common.RateLimitException;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChartLyricsClient {
    private static final String TAG = "ChartLyricsClient";
    private static final String BASE_URL = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect";

    public static String getSongLyrics(String artist, String title) throws IOException, RateLimitException {
        String lyricsXml = HttpGet.get(BASE_URL, "artist=" + URLEncoder.encode(artist, "UTF-8") + "&song=" + URLEncoder.encode(title, "UTF-8"), true);
        Pattern pattern = Pattern.compile("<Lyric>(.*)</Lyric>", Pattern.MULTILINE | Pattern.DOTALL);
        Matcher m = pattern.matcher(lyricsXml);

        if (m.find()) {
            return m.group(1);
        } else {
            Log.e(TAG, "No matches");
        }

        return null;
    }
}
