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

package com.fastbootmobile.encore.service;

import android.content.SharedPreferences;
import android.util.Log;

import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the playback of a list of songs
 */
public class PlaybackQueue extends ArrayList<Song> {
    private static final String TAG = "PlaybackQueue";
    private static final String KEY_SONGS = "songlist";

    /**
     * Adds a song to the queue
     * @param s The song to add
     * @param top If true, the song will be added at the top
     */
    public void addSong(Song s, boolean top) {
        if (top) {
            this.add(0, s);
        } else {
            this.add(s);
        }
    }

    /**
     * Saves the playback queue into the specified SharedPreferences editor
     *
     * @param editor The editor in which write the data
     */
    public void save(SharedPreferences.Editor editor) {
        // Avoid concurrent modification errors
        List<Song> copy = new ArrayList<>(this);

        // Save it!
        JSONArray array = new JSONArray();
        for (Song song : copy) {
            if (song == null) continue;

            JSONObject object = new JSONObject();
            try {
                object.put("r", song.getRef());
                object.put("p", song.getProvider().serialize());
            } catch (NullPointerException | JSONException e) {
                Log.e(TAG, "Cannot save playback queue entry", e);
            }

            array.put(object);
        }

        editor.putString(KEY_SONGS, array.toString());
        editor.apply();
    }

    /**
     * Reads and restores the playback queue from the specified SharedPreferences.
     *
     * @param prefs The preferences to read from
     */
    public void restore(SharedPreferences prefs) {
        String entries = prefs.getString(KEY_SONGS, null);

        if (entries != null) {
            try {
                JSONArray array = new JSONArray(entries);
                final int len = array.length();
                for (int i = 0; i < len; ++i) {
                    JSONObject obj = array.getJSONObject(i);
                    String ref = obj.getString("r");
                    String provider = obj.getString("p");

                    Song song = ProviderAggregator.getDefault().retrieveSong(ref,
                            ProviderIdentifier.fromSerialized(provider));
                    if (song != null) {
                        addSong(song, false);
                    } else {
                        Log.e(TAG, "Cannot retrieve song " + ref + " from " + provider);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Cannot restore playback queue entry", e);
            }
        }
    }
}
