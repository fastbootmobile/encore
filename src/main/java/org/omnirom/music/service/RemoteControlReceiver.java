package org.omnirom.music.service;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;

import org.omnirom.music.framework.PluginsLookup;

/**
 * Created by Guigui on 25/08/2014.
 */
public class RemoteControlReceiver extends BroadcastReceiver {

    private static final String TAG = "RemoteControlReceiver";


    public static ComponentName getComponentName(Context ctx) {
        return new ComponentName(ctx.getPackageName(),
                "org.omnirom.music.service.RemoteControlReceiver");
    }

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
                        if (service.isPaused()) {
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
                        // TODO: Previous
                        break;
                    case KeyEvent.KEYCODE_MEDIA_CLOSE:
                        service.stop();
                        break;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error handling action", e);
            }

            // Prevent broadcast propagation
            abortBroadcast();
        }
    }
}