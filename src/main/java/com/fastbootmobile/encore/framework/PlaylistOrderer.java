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

import org.json.JSONArray;
import org.json.JSONException;
import com.fastbootmobile.encore.model.Playlist;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class handling playlist reordering
 */
public class PlaylistOrderer {
    private static final String TAG = "PlaylistOrderer";

    private static final String PREFS_NAME = "PlaylistOrder";
    private static final String KEY_ORDER = "order";

    private Context mContext;

    public PlaylistOrderer(Context ctx) {
        mContext = ctx;
    }

    /**
     * Returns the order in which the playlists should be sorted. Playlists not ordered (not in
     * the list) should be put at the end in the order they're received.
     * @return A mutable list of playlist references in the order they should be displayed, or null
     * if no order has been set at all.
     * @throws JSONException
     */
    public Map<String, Integer> getOrder() throws JSONException {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String orderStringJson = prefs.getString(KEY_ORDER, null);

        if (orderStringJson == null) {
            return null;
        }

        JSONArray root = new JSONArray(orderStringJson);
        if (root.length() == 0) {
            return null;
        } else {
            Map<String, Integer> output = new HashMap<>();
            for (int i = 0; i < root.length(); ++i) {
                output.put(root.getString(i), i);
            }

            return output;
        }
    }

    /**
     * Sets the playlists order
     * @param order Order to save
     */
    public void setOrder(List<Playlist> order) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        JSONArray json = new JSONArray();
        for (Playlist p : order) {
            json.put(p.getRef());
        }

        editor.putString(KEY_ORDER, json.toString());
        editor.apply();

        Log.e(TAG, "Set order");
    }
}
