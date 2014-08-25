package org.omnirom.music.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.framework.AlbumArtHelper;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

/**
 * Created by Guigui on 24/08/2014.
 */
public class ServiceNotification implements AlbumArtHelper.AlbumArtListener {

    private Context mContext;
    private Resources mResources;
    private final Bitmap mDefaultArt;
    private Bitmap mCurrentArt;
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
        mDefaultArt = def.getBitmap();
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
                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(mCurrentArt)
                        .bigLargeIcon(mCurrentArt))
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Set the notification text
        if (mCurrentSong != null) {
            final ProviderCache cache = ProviderAggregator.getDefault().getCache();
            final Artist artist = cache.getArtist(mCurrentSong.getArtist());
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

    private void updateAlbumArt() {
        if (mCurrentSong != null) {
            AlbumArtHelper.retrieveAlbumArt(mContext, this, mCurrentSong);
        }
    }

    @Override
    public void onArtLoaded(Bitmap output, BoundEntity request) {
        if (request.equals(mCurrentSong)) {
            mCurrentArt = output;
            buildNotification();
        }
    }


    public interface NotificationChangedListener {
        public void onNotificationChanged(ServiceNotification notification);
    }
}
