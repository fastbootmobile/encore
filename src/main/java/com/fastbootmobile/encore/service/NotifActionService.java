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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 */
public class NotifActionService extends IntentService {
    public static final String ACTION_TOGGLE_PAUSE = "com.fastbootmobile.encore.action.TOGGLE_PAUSE";
    public static final String ACTION_STOP = "com.fastbootmobile.encore.action.STOP";
    public static final String ACTION_NEXT = "com.fastbootmobile.encore.action.NEXT";
    public static final String ACTION_PREVIOUS = "com.fastbootmobile.encore.action.PREVIOUS";

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

    /**
     * Starts this service to perform action NEXT. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static Intent getIntentPrevious(Context context) {
        Intent intent = new Intent(context, NotifActionService.class);
        intent.setAction(ACTION_PREVIOUS);
        return intent;
    }

    public NotifActionService() {
        super("NotifActionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            switch (action) {
                case ACTION_NEXT:
                    handleActionNext();
                    break;
                case ACTION_TOGGLE_PAUSE:
                    handleActionTogglePause();
                    break;
                case ACTION_STOP:
                    handleActionStop();
                    break;
                case ACTION_PREVIOUS:
                    handleActionPrevious();
                    break;
            }
        }
    }

    /**
     * Handle action NEXT in the provided background thread
     */
    private void handleActionNext() {
        startServiceIntent(PlaybackService.COMMAND_NEXT);
    }

    /**
     * Handle action PREVIOUS in the provided background thread
     */
    private void handleActionPrevious() {
        startServiceIntent(PlaybackService.COMMAND_PREVIOUS);
    }

    /**
     * Handle action STOP in the provided background thread
     */
    private void handleActionStop() {
        startServiceIntent(PlaybackService.COMMAND_STOP);
    }

    /**
     * Handle action TOGGLE_PAUSE in the provided background thread
     */
    private void handleActionTogglePause() {
        startServiceIntent(PlaybackService.COMMAND_PAUSE);
    }

    private void startServiceIntent(final int command) {
        final Intent i = new Intent(this, PlaybackService.class);
        i.setAction(PlaybackService.ACTION_COMMAND);
        i.putExtra(PlaybackService.EXTRA_COMMAND_NAME, command);
        startService(i);
    }

}
