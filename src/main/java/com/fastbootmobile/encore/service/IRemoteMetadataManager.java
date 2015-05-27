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

import android.graphics.drawable.BitmapDrawable;

import com.fastbootmobile.encore.model.Song;

/**
 * Interface for remote metadata (lockscreen / wear / ...)
 */
interface IRemoteMetadataManager {
    void setup();
    void release();
    void setActive(final boolean active);
    void setAlbumArt(final BitmapDrawable bmp);
    void setCurrentSong(final Song song, final boolean hasNext);
    void notifyPlaying(final long timeElapsed);
    void notifyBuffering();
    void notifyPaused(final long timeElapsed);
    void notifyStopped();
}
