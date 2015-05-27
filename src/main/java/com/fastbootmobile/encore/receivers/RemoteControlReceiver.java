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

package com.fastbootmobile.encore.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.service.PlaybackService;

/**
 * Broadcast receiver handling media button events (play/pause/skip/... from either lockscreen
 * or press on headset buttons)
 */
public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String TAG = "RemoteControlReceiver";

    public static ComponentName getComponentName(Context ctx) {
        return new ComponentName(ctx.getPackageName(),
                "com.fastbootmobile.encore.receivers.RemoteControlReceiver");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN
                    || !PlaybackProxy.isServiceConnected()) {
                return;
            }

            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    if (PlaybackProxy.getState() == PlaybackService.STATE_PAUSED) {
                        PlaybackProxy.play();
                    } else {
                        PlaybackProxy.pause();
                    }
                    break;

                case KeyEvent.KEYCODE_MEDIA_STOP:
                    PlaybackProxy.stop();
                    break;

                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    PlaybackProxy.next();
                    break;

                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    PlaybackProxy.previous();
                    break;

                case KeyEvent.KEYCODE_MEDIA_CLOSE:
                    PlaybackProxy.stop();
                    break;
            }
        }
    }
}