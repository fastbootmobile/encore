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

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PlaybackProxy;
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

    /**
     * Handle action NEXT in the provided background thread
     */
    private void handleActionNext() {
        PlaybackProxy.next();
    }

    /**
     * Handle action PREVIOUS in the provided background thread
     */
    private void handleActionPrevious() {
        PlaybackProxy.previous();
    }

    /**
     * Handle action STOP in the provided background thread
     */
    private void handleActionStop() {
        PlaybackProxy.stop();
    }

    /**
     * Handle action TOGGLE_PAUSE in the provided background thread
     */
    private void handleActionTogglePause() {
        if (PlaybackProxy.getState() == PlaybackService.STATE_PAUSED) {
            PlaybackProxy.play();
        } else {
            PlaybackProxy.pause();
        }
    }

}
