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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;

import org.omnirom.music.framework.PluginsLookup;

/**
 * Broadcast receiver handling media button events (play/pause/skip/... from either lockscreen
 * or press on headset buttons)
 */
public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String TAG = "RemoteControlReceiver";

    public static ComponentName getComponentName(Context ctx) {
        return new ComponentName(ctx.getPackageName(),
                "org.omnirom.music.service.RemoteControlReceiver");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            IPlaybackService service = PluginsLookup.getDefault().getPlaybackService();
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
                return;
            }

            try {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        if (service.getState() == PlaybackService.STATE_PAUSED) {
                            service.play();
                        } else {
                            service.pause();
                        }
                        break;
                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        service.stop();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        service.next();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        service.previous();
                        break;
                    case KeyEvent.KEYCODE_MEDIA_CLOSE:
                        service.stop();
                        break;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error handling action", e);
            }
        }
    }
}