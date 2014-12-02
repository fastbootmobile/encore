package org.omnirom.music.service;

import android.graphics.Bitmap;

import org.omnirom.music.model.Song;

/**
 * Created by Guigui on 02/12/2014.
 */
interface IRemoteMetadataManager {
    void setup();
    void release();
    void setActive(final boolean active);
    void setAlbumArt(final Bitmap bmp);
    void setCurrentSong(final Song song, final boolean hasNext);
    void notifyPlaying(final long timeElapsed);
    void notifyBuffering();
    void notifyPaused(final long timeElapsed);
    void notifyStopped();
}
