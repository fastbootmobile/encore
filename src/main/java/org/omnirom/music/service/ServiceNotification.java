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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.View;
import android.widget.RemoteViews;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.AlbumArtHelper;
import org.omnirom.music.framework.RefCountedBitmap;
import org.omnirom.music.model.Album;
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
    private final Bitmap mDefaultArt;
    private Bitmap mCurrentArt;
    private Bitmap mPendingArt;
    private NotificationChangedListener mListener;
    private Song mCurrentSong;
    private RemoteViews mBaseTemplate;
    private RemoteViews mExpandedTemplate;
    private boolean mHasNext;
    private boolean mShowPlayAction;

    private Notification mNotification;

    private static final int NOTIFICATION_ID = 1;

    public ServiceNotification(Context ctx) {
        mContext = ctx;
        mResources = ctx.getResources();

        // Get the default album art
        BitmapDrawable def = (BitmapDrawable) mResources.getDrawable(R.drawable.album_placeholder);
        mDefaultArt = def.getBitmap();
        mCurrentArt = mDefaultArt;

        mBaseTemplate = new RemoteViews(ctx.getPackageName(), R.layout.notification_base);
        mExpandedTemplate = new RemoteViews(ctx.getPackageName(), R.layout.notification_expanded);

        // Setup pending intents
        PendingIntent piNext = PendingIntent.getService(mContext, 0,
                NotifActionService.getIntentNext(mContext), 0);
        PendingIntent piPrevious = PendingIntent.getService(mContext, 0,
                NotifActionService.getIntentPrevious(mContext), 0);
        PendingIntent piPause = PendingIntent.getService(mContext,
                0, NotifActionService.getIntentTogglePause(mContext), 0);
        PendingIntent piStop = PendingIntent.getService(mContext,
                0, NotifActionService.getIntentStop(mContext), 0);

        mBaseTemplate.setOnClickPendingIntent(R.id.btnNotifNext, piNext);
        mExpandedTemplate.setOnClickPendingIntent(R.id.btnNotifNext, piNext);
        mBaseTemplate.setOnClickPendingIntent(R.id.btnNotifPlayPause, piPause);
        mExpandedTemplate.setOnClickPendingIntent(R.id.btnNotifPlayPause, piPause);
        mBaseTemplate.setOnClickPendingIntent(R.id.btnNotifPrevious, piPrevious);
        mExpandedTemplate.setOnClickPendingIntent(R.id.btnNotifPrevious, piPrevious);
        mBaseTemplate.setOnClickPendingIntent(R.id.btnNotifClose, piStop);
        mExpandedTemplate.setOnClickPendingIntent(R.id.btnNotifClose, piStop);

        // Build the static notification actions
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

        if (mCurrentArt == null || mCurrentArt.isRecycled()) {
            mCurrentArt = mDefaultArt;
        }
        // Build the core notification
        builder.setSmallIcon(R.drawable.ic_launcher_white)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContent(mBaseTemplate)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // And get the notification object
        mNotification = builder.build();

        // Add the expanded controls
        mNotification.bigContentView = mExpandedTemplate;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mNotification.category = Notification.CATEGORY_SERVICE;
        }

        // Update fields
        if (mCurrentSong != null) {
            final Artist artist = ProviderAggregator.getDefault()
                    .retrieveArtist(mCurrentSong.getArtist(), mCurrentSong.getProvider());

            final Album album = ProviderAggregator.getDefault()
                    .retrieveAlbum(mCurrentSong.getAlbum(), mCurrentSong.getProvider());

            if (mCurrentSong.isLoaded()) {
                // Song title
                mBaseTemplate.setTextViewText(R.id.tvNotifLineOne, mCurrentSong.getTitle());
                mExpandedTemplate.setTextViewText(R.id.tvNotifLineOne, mCurrentSong.getTitle());

                if (artist != null && artist.getName() != null && !artist.getName().isEmpty()) {
                    // Artist name
                    mBaseTemplate.setTextViewText(R.id.tvNotifLineTwo, artist.getName());
                    mExpandedTemplate.setTextViewText(R.id.tvNotifLineTwo, artist.getName());
                }

                if (album != null && album.getName() != null && !album.getName().isEmpty()) {
                    // Album name
                    mExpandedTemplate.setTextViewText(R.id.tvNotifLineThree, album.getName());
                }
            } else {
                builder.setContentTitle(getString(R.string.loading));
            }
        }

        if (mShowPlayAction) {
            mBaseTemplate.setImageViewResource(R.id.btnNotifPlayPause, R.drawable.ic_notif_play);
            mExpandedTemplate.setImageViewResource(R.id.btnNotifPlayPause, R.drawable.ic_notif_play);
        } else {
            mBaseTemplate.setImageViewResource(R.id.btnNotifPlayPause, R.drawable.ic_notif_pause);
            mExpandedTemplate.setImageViewResource(R.id.btnNotifPlayPause, R.drawable.ic_notif_pause);
        }

        if (mHasNext) {
            mBaseTemplate.setViewVisibility(R.id.btnNotifNext, View.VISIBLE);
            mExpandedTemplate.setViewVisibility(R.id.btnNotifNext, View.VISIBLE);
        } else {
            mBaseTemplate.setViewVisibility(R.id.btnNotifNext, View.GONE);
            mExpandedTemplate.setViewVisibility(R.id.btnNotifNext, View.GONE);
        }

        if (mCurrentArt != null && !mCurrentArt.isRecycled()) {
            mBaseTemplate.setImageViewBitmap(R.id.ivAlbumArt, mCurrentArt);
            mExpandedTemplate.setImageViewBitmap(R.id.ivAlbumArt, mCurrentArt);
        } else {
            mBaseTemplate.setImageViewBitmap(R.id.ivAlbumArt, mDefaultArt);
            mExpandedTemplate.setImageViewBitmap(R.id.ivAlbumArt, mDefaultArt);
        }

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
        mShowPlayAction = play;

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
        if (!s.equals(mCurrentSong)) {
            mCurrentSong = s;
            updateAlbumArt();
            buildNotification();
        }
    }

    /**
     * Sets whether or not the "Next" action should be enabled or not
     */
    public void setHasNext(boolean hasNext) {
        if (mHasNext != hasNext) {
            mHasNext = hasNext;
            buildNotification();
        }
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
    public Bitmap getAlbumArt() {
        if (mCurrentArt == null || mCurrentArt.isRecycled()) {
            return mDefaultArt;
        } else {
            return mCurrentArt;
        }
    }

    private void updateAlbumArt() {
        if (mCurrentSong != null) {
            if (mCurrentArt != null && mCurrentArt != mDefaultArt) {
                mPendingArt = mCurrentArt;
            }


            mCurrentArt = mDefaultArt;
            AlbumArtHelper.retrieveAlbumArt(this, mCurrentSong, false);
        }
    }

    @Override
    public void onArtLoaded(RefCountedBitmap output, BoundEntity request) {
        if (request.equals(mCurrentSong)) {
            if (mPendingArt != null) {
                //  TODO: recycle causes crash here
                mPendingArt = null;
            }

            if (output == null) {
                mCurrentArt = mDefaultArt;
            } else {
                output.acquire();
                Bitmap outputBitmap = output.get();
                mCurrentArt = outputBitmap.copy(outputBitmap.getConfig(), false);
                output.release();
            }

            buildNotification();
        }
    }

    public interface NotificationChangedListener {
        public void onNotificationChanged(ServiceNotification notification);
    }
}
