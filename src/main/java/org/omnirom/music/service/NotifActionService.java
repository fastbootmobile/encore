package org.omnirom.music.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class NotifActionService extends IntentService {
    private static final String TAG = "NotifActionService";

    public static final String ACTION_TOGGLE_PAUSE = "org.omnirom.music.action.TOGGLE_PAUSE";
    public static final String ACTION_STOP = "org.omnirom.music.action.STOP";
    public static final String ACTION_NEXT = "org.omnirom.music.action.NEXT";
    public static final String ACTION_PREVIOUS = "org.omnirom.music.action.PREVIOUS";

    /**
     * Starts this service to perform action TOGGLE_PAUSE. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static Intent getIntentTogglePause(Context context) {
        Intent intent = new Intent(context, NotifActionService.class);
        intent.setAction(ACTION_TOGGLE_PAUSE);
        return intent;
    }

    /**
     * Starts this service to perform action STOP. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static Intent getIntentStop(Context context) {
        Intent intent = new Intent(context, NotifActionService.class);
        intent.setAction(ACTION_STOP);
        return intent;
    }

    /**
     * Starts this service to perform action NEXT. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static Intent getIntentNext(Context context) {
        Intent intent = new Intent(context, NotifActionService.class);
        intent.setAction(ACTION_NEXT);
        return intent;
    }

    public NotifActionService() {
        super("NotifActionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_NEXT.equals(action)) {
                handleActionNext();
            } else if (ACTION_TOGGLE_PAUSE.equals(action)) {
                handleActionTogglePause();
            } else if (ACTION_STOP.equals(action)) {
                handleActionStop();
            } else if (ACTION_PREVIOUS.equals(action)) {
                handleActionPrevious();
            }
        }
    }

    private IPlaybackService getPlaybackService() {
        return PluginsLookup.getDefault().getPlaybackService();
    }

    /**
     * Handle action NEXT in the provided background thread
     */
    private void handleActionNext() {
        try {
            IPlaybackService service = getPlaybackService();
            if (service != null) {
                getPlaybackService().next();
            } else {
                Log.e(TAG, "Notification is up but Playback Service is dead?!");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to skip to next song", e);
        }
    }

    /**
     * Handle action PREVIOUS in the provided background thread
     */
    private void handleActionPrevious() {
        try {
            IPlaybackService service = getPlaybackService();
            if (service != null) {
                getPlaybackService().previous();
            } else {
                Log.e(TAG, "Notification is up but Playback Service is dead?!");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to skip to previous song", e);
        }
    }

    /**
     * Handle action STOP in the provided background thread
     */
    private void handleActionStop() {
        try {
            IPlaybackService service = getPlaybackService();
            if (service != null) {
                getPlaybackService().stop();
            } else {
                Log.e(TAG, "Notification is up but Playback Service is dead?!");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to skip to next song", e);
        }
    }

    /**
     * Handle action TOGGLE_PAUSE in the provided background thread
     */
    private void handleActionTogglePause() {
        try {
            IPlaybackService service = getPlaybackService();
            if (service != null) {
                int state = service.getState();
                if (state == PlaybackService.STATE_PAUSED) {
                    service.play();
                } else {
                    service.pause();
                }
            } else {
                Log.e(TAG, "Notification is up but Playback Service is dead?!");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to skip to next song", e);
        }
    }

}
