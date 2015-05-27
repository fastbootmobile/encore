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

package com.fastbootmobile.encore.api.chartlyrics;

import com.fastbootmobile.encore.api.common.HttpGet;
import com.fastbootmobile.encore.api.common.RateLimitException;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChartLyricsClient {
    private static final String TAG = "ChartLyricsClient";
    private static final String BASE_URL = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect";

    private static final Pattern PATTERN_LYRICS = Pattern.compile("<Lyric>(.*)</Lyric>", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern PATTERN_SONG = Pattern.compile("<LyricSong>(.*)</LyricSong>", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern PATTERN_ARTIST = Pattern.compile("<LyricArtist>(.*)</LyricArtist>", Pattern.MULTILINE | Pattern.DOTALL);

    public static LyricsResponse getSongLyrics(String artist, String title) throws IOException, RateLimitException {
        String lyricsXml = HttpGet.get(BASE_URL, "artist=" + URLEncoder.encode(artist, "UTF-8") + "&song=" + URLEncoder.encode(title, "UTF-8"), true);

        Matcher matcher_song = PATTERN_SONG.matcher(lyricsXml);
        Matcher matcher_artist = PATTERN_ARTIST.matcher(lyricsXml);
        Matcher matcher_lyrics = PATTERN_LYRICS.matcher(lyricsXml);

        LyricsResponse response = new LyricsResponse();

        if (matcher_lyrics.find()) {
            response.lyrics = matcher_lyrics.group(1);
        }

        if (matcher_artist.find()) {
            response.artist = matcher_artist.group(1);
        }

        if (matcher_song.find()) {
            response.title = matcher_song.group(1);
        }

        return response;
    }

    public static class LyricsResponse {
        public String lyrics;
        public String artist;
        public String title;
    }
}
