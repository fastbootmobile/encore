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

package org.omnirom.music.framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderIdentifier;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class handling logging of played and liked songs
 */
public class ListenLogger {
    private static final String TAG = "ListenLogger";
    private static final boolean DEBUG = true;

    private static final String PREFS = "ListenLogger";
    private static final String PREF_HISTORY_ENTRIES = "history_entries";
    private static final String PREF_LIKED_ENTRIES = "liked_entries";

    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_SONG_REF = "song_ref";
    private static final String KEY_PROVIDER = "provider";

    private SharedPreferences mPrefs;

    public ListenLogger(Context ctx) {
        mPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void addEntry(Song song) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Set<String> entries = mPrefs.getStringSet(PREF_HISTORY_ENTRIES, new TreeSet<String>());

        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(KEY_TIMESTAMP, new Date().getTime());
            jsonRoot.put(KEY_SONG_REF, song.getRef());
            jsonRoot.put(KEY_PROVIDER, song.getProvider().serialize());
        } catch (JSONException ignore) {}

        entries.add(jsonRoot.toString());
        editor.putStringSet(PREF_HISTORY_ENTRIES, entries);
        editor.apply();
    }

    public List<LogEntry> getEntries() {
        Set<String> entries = mPrefs.getStringSet(PREF_HISTORY_ENTRIES, null);
        List<LogEntry> output = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                try {
                    JSONObject jsonObj = new JSONObject(entry);
                    String songRef = jsonObj.getString(KEY_SONG_REF);
                    String providerSerialized = jsonObj.getString(KEY_PROVIDER);
                    long timestamp = jsonObj.getLong(KEY_TIMESTAMP);

                    output.add(new LogEntry(songRef, providerSerialized, timestamp));
                } catch (JSONException ignore) {
                }
            }
        }

        return output;
    }

    public void addLike(Song song) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Set<String> entries = mPrefs.getStringSet(PREF_LIKED_ENTRIES, new TreeSet<String>());

        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(KEY_SONG_REF, song.getRef());
            jsonRoot.put(KEY_PROVIDER, song.getProvider().serialize());
        } catch (JSONException ignore) {}

        entries.add(jsonRoot.toString());
        editor.apply();
    }

    public void removeLike(Song song) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Set<String> entries = mPrefs.getStringSet(PREF_LIKED_ENTRIES, new TreeSet<String>());

        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(KEY_SONG_REF, song.getRef());
            jsonRoot.put(KEY_PROVIDER, song.getProvider().serialize());
        } catch (JSONException ignore) {}

        entries.remove(jsonRoot.toString());
        editor.apply();
    }

    public List<LogEntry> getLikedEntries() {
        Set<String> entries = mPrefs.getStringSet(PREF_HISTORY_ENTRIES, null);
        List<LogEntry> output = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                try {
                    JSONObject jsonObj = new JSONObject(entry);
                    String songRef = jsonObj.getString(KEY_SONG_REF);
                    String providerSerialized = jsonObj.getString(KEY_PROVIDER);

                    output.add(new LogEntry(songRef, providerSerialized, 0));
                } catch (JSONException ignore) {
                }
            }
        }

        return output;
    }

    public boolean isLiked(String ref) {
        Set<String> entries = mPrefs.getStringSet(PREF_HISTORY_ENTRIES, null);
        if (entries != null) {
            for (String entry : entries) {
                try {
                    JSONObject jsonObj = new JSONObject(entry);
                    String songRef = jsonObj.getString(KEY_SONG_REF);
                    if (songRef.equals(ref)) {
                        return true;
                    }
                } catch (JSONException ignore) {
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static class LogEntry {
        private Date mTimestamp;
        private String mSongRef;
        private ProviderIdentifier mIdentifier;

        private LogEntry(String songRef, String serializedProviderIdentifier, long timestamp) {
            mSongRef = songRef;
            mIdentifier = ProviderIdentifier.fromSerialized(serializedProviderIdentifier);
            mTimestamp = new Date(timestamp);
        }

        public String getReference() {
            return mSongRef;
        }

        public ProviderIdentifier getIdentifier() {
            return mIdentifier;
        }

        public Date getTimestamp() {
            return mTimestamp;
        }
    }
}
