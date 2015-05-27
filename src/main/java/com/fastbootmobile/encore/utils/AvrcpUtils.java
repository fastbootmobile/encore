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

package com.fastbootmobile.encore.utils;

import android.content.Context;
import android.content.Intent;

/**
 * AVRCP Utilities - Android Remote Metadata stuff can't handle Bluetooth, you're forced
 * to use some magic!
 */
public class AvrcpUtils {
    private static final String AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    private static final String AVRCP_META_CHANGED = "com.android.music.metachanged";

    private static final String KEY_ID = "id";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_ALBUM = "album";
    private static final String KEY_TRACK = "track";
    private static final String KEY_PLAYING = "playing";
    private static final String KEY_LIST_SIZE = "ListSize";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_POSITION = "position";

    public static void notifyMetaChanged(Context ctx, long id, String artist, String album,
                                         String track, int listSize, long duration, long position) {
        Intent i = new Intent(AVRCP_META_CHANGED);
        i.putExtra(KEY_ID, id);
        i.putExtra(KEY_ARTIST, artist);
        i.putExtra(KEY_ALBUM, album);
        i.putExtra(KEY_TRACK, track);
        i.putExtra(KEY_LIST_SIZE, listSize);
        i.putExtra(KEY_DURATION, duration);
        i.putExtra(KEY_POSITION, position);

        ctx.sendBroadcast(i);
    }

    public static void notifyPlayStateChanged(Context ctx, boolean playing, long position) {
        // The bluetooth stuff assumes STOP if playing == false && position = 0, PAUSE if
        // playing == false && position > 0, and PLAYING if playing == true
        Intent i = new Intent(AVRCP_PLAYSTATE_CHANGED);
        i.putExtra(KEY_PLAYING, playing);
        i.putExtra(KEY_POSITION, position);

        ctx.sendBroadcast(i);
    }
}
