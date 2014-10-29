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

package org.omnirom.music.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.AlbumArtHelper;
import org.omnirom.music.framework.RefCountedBitmap;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

/**
 * Class handling the playback service notification
 */
public class ServiceNotification implements AlbumArtHelper.AlbumArtListener {
    private Context mContext;
    private Resources mResources;
    private final RefCountedBitmap mDefaultArt;
    private RefCountedBitmap mCurrentArt;
    private NotificationChangedListener mListener;
    private Song mCurrentSong;

    private Notification mNotification;
    private NotificationCompat.Action.Builder mStopActionBuilder;
    private NotificationCompat.Action.Builder mPlayActionBuilder;
    private NotificationCompat.Action.Builder mNextActionBuilder;

    private static final int NOTIFICATION_ID = 1;

    public ServiceNotification(Context ctx) {
        mContext = ctx;
        mResources = ctx.getResources();

        // Get the default album art
        BitmapDrawable def = (BitmapDrawable) mResources.getDrawable(R.drawable.album_placeholder);
        mDefaultArt = new RefCountedBitmap(def.getBitmap());
        mDefaultArt.acquire();

        mCurrentArt = mDefaultArt;

        // Build the static notification actions
        mStopActionBuilder = new NotificationCompat.Action.Builder(R.drawable.ic_notif_stop,
                getString(R.string.stop),
                PendingIntent.getService(mContext, 0, NotifActionService.getIntentStop(mContext), 0));
        mNextActionBuilder = new NotificationCompat.Action.Builder(R.drawable.ic_notif_next,
                getString(R.string.next), null);
        setPlayPauseActionImpl(false, false);

        // Build the initial notification
        buildNotification();
    }

    public String getString(int resId) {
        return mResources.getString(resId);
    }

    private void buildNotification() {
        Intent notificationIntent = new Intent(mContext, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, notificationIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);

        // Build the core notification
        builder.setSmallIcon(R.drawable.ic_launcher_white)
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(mCurrentArt.get())
                        .bigLargeIcon(mCurrentArt.get()))
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Set the notification text
        if (mCurrentSong != null) {
            final Artist artist = ProviderAggregator.getDefault()
                    .retrieveArtist(mCurrentSong.getArtist(), mCurrentSong.getProvider());
            builder.setContentTitle(mCurrentSong.getTitle());
            if (artist != null) {
                builder.setContentText(artist.getName());
            }
        }

        // Add the notification actions
        builder.addAction(mStopActionBuilder.build());
        builder.addAction(mPlayActionBuilder.build());
        builder.addAction(mNextActionBuilder.build());

        // And get the notification object
        mNotification = builder.build();

        // Post update
        if (mListener != null) {
            mListener.onNotificationChanged(this);
        }
    }

    /**
     * Posts or updates the notification to the system's notification manager
     *
     * @param managerCompat The notification manager
     */
    public void notify(NotificationManagerCompat managerCompat) {
        managerCompat.notify(NOTIFICATION_ID, mNotification);
    }

    /**
     * Posts or updates the notification as a foreground notification
     *
     * @param service The service instance
     */
    public void notify(Service service) {
        service.startForeground(NOTIFICATION_ID, mNotification);
    }

    /**
     * Sets the status of the Play/Pause action button within the notification.
     *
     * @param play If true, the "Play" action will be shown. If false, the "Pause" action will be
     *             shown.
     */
    public void setPlayPauseAction(boolean play) {
        setPlayPauseActionImpl(play, true);
    }

    private void setPlayPauseActionImpl(boolean play, boolean callBuild) {
        int iconDrawable;
        CharSequence text;
        PendingIntent pi = PendingIntent.getService(mContext, 0,
                NotifActionService.getIntentTogglePause(mContext), 0);

        if (play) {
            iconDrawable = R.drawable.ic_notif_play;
            text = getString(R.string.play);
        } else {
            iconDrawable = R.drawable.ic_notif_pause;
            text = getString(R.string.pause);
        }

        mPlayActionBuilder = new NotificationCompat.Action.Builder(iconDrawable, text, pi);

        if (callBuild) {
            buildNotification();
        }
    }

    /**
     * Sets the current song to be displayed. This will update the notification text and icon
     *
     * @param s The song to be displayed
     */
    public void setCurrentSong(Song s) {
        mCurrentSong = s;
        updateAlbumArt();
        buildNotification();
    }

    /**
     * Sets whether or not the "Next" action should be enabled or not
     */
    public void setHasNext(boolean hasNext) {
        mNextActionBuilder = new NotificationCompat.Action.Builder(R.drawable.ic_notif_next,
                getString(R.string.next),
                hasNext ?
                        PendingIntent.getService(mContext, 0, NotifActionService.getIntentNext(mContext), 0)
                        : null);
        buildNotification();
    }

    /**
     * Sets the listener to be called when the notification contents changes and notify should be
     * called on the NotificationManager.
     *
     * @param listener The listener
     */
    public void setOnNotificationChangedListener(NotificationChangedListener listener) {
        mListener = listener;
    }

    /**
     * @return The current active album art
     */
    public RefCountedBitmap getAlbumArt() {
        return mCurrentArt;
    }

    private void updateAlbumArt() {
        if (mCurrentSong != null) {
            if (mCurrentArt != null && mCurrentArt != mDefaultArt) {
                mCurrentArt.release();
            }
            mCurrentArt = mDefaultArt;
            AlbumArtHelper.retrieveAlbumArt(this, mCurrentSong, false);
        }
    }

    @Override
    public void onArtLoaded(RefCountedBitmap output, BoundEntity request) {
        if (request.equals(mCurrentSong)) {
            if (mCurrentArt != null && mCurrentArt != mDefaultArt) {
                mCurrentArt.release();
            }

            if (output == null) {
                mCurrentArt = mDefaultArt;
            } else {
                mCurrentArt = output;
            }

            if (mCurrentArt != mDefaultArt) {
                mCurrentArt.acquire();
            }

            buildNotification();
        }
    }

    public interface NotificationChangedListener {
        public void onNotificationChanged(ServiceNotification notification);
    }
}
