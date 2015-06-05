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

package com.fastbootmobile.encore.framework;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import org.json.JSONException;
import org.json.JSONObject;

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
    private static final String PREF_DISLIKED_ENTRIES = "disliked_entries";

    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_SONG_REF = "song_ref";
    private static final String KEY_PROVIDER = "provider";

    private SharedPreferences mPrefs;

    public ListenLogger(Context ctx) {
        mPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Adds an entry to the song history. The time used will be the current time.
     * @param song The song to add
     */
    public void addEntry(Song song) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Set<String> entries = new TreeSet<>(mPrefs.getStringSet(PREF_HISTORY_ENTRIES,
                new TreeSet<String>()));

        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(KEY_TIMESTAMP, new Date().getTime());
            jsonRoot.put(KEY_SONG_REF, song.getRef());
            jsonRoot.put(KEY_PROVIDER, song.getProvider().serialize());
        } catch (JSONException ignore) {}


        entries.add(jsonRoot.toString());

        // Remove old entries
        final long now = new Date().getTime();
        final long oneSec = 1000L;
        final long oneMin = oneSec * 60;
        final long oneHour = oneMin * 60;
        final long oneDay = oneHour * 24;
        final long oneMonth = oneDay * 31;

        Set<String> removal = new TreeSet<>();
        for (String entry : entries) {
            try {
                JSONObject obj = new JSONObject(entry);
                long timestamp = obj.getLong(KEY_TIMESTAMP);
                if (now - timestamp > oneMonth) {
                    removal.add(entry);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Cannot parse JSON", e);
            }
        }

        for (String entry : removal) {
            entries.remove(entry);
        }

        editor.putStringSet(PREF_HISTORY_ENTRIES, entries);
        editor.apply();
    }

    /**
     * Fetches and builds a list of all the history entries
     * @return A list of entries
     */
    public List<LogEntry> getEntries(int limit) {
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
                } catch (JSONException e) {
                    Log.e(TAG, "JSON Exception while trying to get log entry", e);
                }

                if (limit > 0 && output.size() == limit) {
                    break;
                }
            }
        }

        return output;
    }

    /**
     * Adds, if not already, a song to the list of liked songs.
     * @param song The song to add
     */
    public void addLike(Song song) {
        addLikingImpl(song, PREF_LIKED_ENTRIES);
    }

    /**
     * Adds, if not already, a song to the list of disliked songs.
     * @param song The song to add
     */
    public void addDislike(Song song) {
        addLikingImpl(song, PREF_DISLIKED_ENTRIES);
    }

    private void addLikingImpl(Song song, String entrySet) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Set<String> entries = new TreeSet<>(mPrefs.getStringSet(entrySet, new TreeSet<String>()));

        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(KEY_SONG_REF, song.getRef());
            jsonRoot.put(KEY_PROVIDER, song.getProvider().serialize());
        } catch (JSONException ignore) {}

        entries.add(jsonRoot.toString());

        editor.putStringSet(entrySet, entries);
        editor.apply();
    }

    /**
     * Removes a song from the list of liked songs
     * @param song The song to remove
     */
    public void removeLike(Song song) {
        removeLikingImpl(song, PREF_LIKED_ENTRIES);
    }

    /**
     * Removes a song from the list of disliked songs
     * @param song The song to remove
     */
    public void removeDislike(Song song) {
        removeLikingImpl(song, PREF_DISLIKED_ENTRIES);
    }

    private void removeLikingImpl(Song song, String entrySet) {
        SharedPreferences.Editor editor = mPrefs.edit();
        Set<String> entries = new TreeSet<>(mPrefs.getStringSet(entrySet, new TreeSet<String>()));

        JSONObject jsonRoot = new JSONObject();
        try {
            jsonRoot.put(KEY_SONG_REF, song.getRef());
            jsonRoot.put(KEY_PROVIDER, song.getProvider().serialize());
        } catch (JSONException ignore) {}

        entries.remove(jsonRoot.toString());
        editor.putStringSet(entrySet, entries);
        editor.apply();
    }

    /**
     * @return a list of all the liked entries
     */
    public List<LogEntry> getLikedEntries() {
        return getLikingEntriesImpl(PREF_LIKED_ENTRIES);
    }
    /**
     * @return a list of all the disliked entries
     */
    public List<LogEntry> getDislikedEntries() {
        return getLikingEntriesImpl(PREF_DISLIKED_ENTRIES);
    }


    public List<LogEntry> getLikingEntriesImpl(String entrySet) {
        Set<String> entries = mPrefs.getStringSet(entrySet, null);
        List<LogEntry> output = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                try {
                    JSONObject jsonObj = new JSONObject(entry);
                    String songRef = jsonObj.getString(KEY_SONG_REF);
                    String providerSerialized = jsonObj.getString(KEY_PROVIDER);

                    output.add(new LogEntry(songRef, providerSerialized, 0));
                } catch (JSONException e) {
                    Log.e(TAG, "JSON Exception while trying to get liked entries", e);
                }
            }
        }

        return output;
    }


    /**
     * Returns whether or not the song reference provided is in the list of liked songs or not
     * @param ref The reference of the song
     * @return true if the song is liked
     */
    public boolean isLiked(String ref) {
        return getLikingImpl(ref, PREF_LIKED_ENTRIES);
    }

    /**
     * Returns whether or not the song reference provided is in the list of disliked songs or not
     * @param ref The reference of the song
     * @return true if the song is disliked
     */
    public boolean isDisliked(String ref) {
        return getLikingImpl(ref, PREF_DISLIKED_ENTRIES);
    }

    private boolean getLikingImpl(String ref, String entrySet) {
        Set<String> entries = mPrefs.getStringSet(entrySet, null);
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

    /**
     * Class representing an entry in either the log or the list of liked songs
     */
    public static class LogEntry {
        private Date mTimestamp;
        private String mSongRef;
        private ProviderIdentifier mIdentifier;

        private LogEntry(String songRef, String serializedProviderIdentifier, long timestamp) {
            mSongRef = songRef;
            mIdentifier = ProviderIdentifier.fromSerialized(serializedProviderIdentifier);
            mTimestamp = new Date(timestamp);
        }

        /**
         * @return The reference of the song
         */
        public String getReference() {
            return mSongRef;
        }

        /**
         * @return The provider identifier of the song
         */
        public ProviderIdentifier getIdentifier() {
            return mIdentifier;
        }

        /**
         * @return The timestamp at which this song was added (valid only for history, not likes)
         */
        public Date getTimestamp() {
            return mTimestamp;
        }
    }
}
