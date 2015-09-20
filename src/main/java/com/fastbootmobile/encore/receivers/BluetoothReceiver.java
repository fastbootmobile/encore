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

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;

import com.fastbootmobile.encore.app.DriveModeActivity;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.utils.SettingsKeys;

import java.util.Set;

/**
 * Broadcast receiver handling Bluetooth events (device connected, etc).
 */
public class BluetoothReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothReceiver";

    private Handler mHandler;

    /**
     * When Autoconnect device connects, open Drive Mode
     */
    public static final int BT_AUTOCONNECT_ACTION_OPEN_DRIVE    = 0x01;

    /**
     * When Autoconnect device connects, play the active playback queue
     */
    public static final int BT_AUTOCONNECT_ACTION_PLAY_QUEUE    = 0x02;


    public static ComponentName getComponentName(Context ctx) {
        return new ComponentName(ctx.getPackageName(),
                "com.fastbootmobile.encore.receivers.RemoteControlReceiver");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsKeys.PREF_SETTINGS,
                Context.MODE_PRIVATE);
        mHandler = new Handler();

        boolean autoconnectEnabled = prefs.getBoolean(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_ENABLE,
                false);

        if (autoconnectEnabled) {
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                handleDeviceConnected(context, prefs, device);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                handleDeviceDisconnected(context, prefs, device);
            }
        }
    }

    private void handleDeviceConnected(Context context, SharedPreferences prefs,
                                       BluetoothDevice device) {
        String autoconnectName = prefs.getString(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_NAME, null);

        if (autoconnectName != null && device.getName() != null
                && device.getName().equals(autoconnectName)) {
            // Our autoconnect device plugged in, launch the desired action
            Set<String> actionFlagsStr = prefs.getStringSet(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_ACTION, null);
            int actionFlags = buildActionFlags(actionFlagsStr);

            if ((actionFlags & BT_AUTOCONNECT_ACTION_OPEN_DRIVE) != 0) {
                // Open Drive Mode activity
                Intent intent = new Intent(context, DriveModeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }

            if ((actionFlags & BT_AUTOCONNECT_ACTION_PLAY_QUEUE) != 0) {
                // Plugins can take a couple seconds to be ready (e.g. Spotify can only play
                // music a few seconds after getting connected). We hit up PlaybackProxy to get
                // the PlaybackService and the plugins system up (in case the app was off),
                // then we actually request playback.
                PlaybackProxy.getState();

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Start playing queue
                        PlaybackProxy.play();
                    }
                }, 2000);
            }
        }
    }

    private void handleDeviceDisconnected(Context context, SharedPreferences prefs,
                                          BluetoothDevice device) {
        String autoconnectName = prefs.getString(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_NAME, null);

        if (autoconnectName != null && device.getName() != null
                && device.getName().equals(autoconnectName)) {
            Set<String> actionFlagsStr = prefs.getStringSet(SettingsKeys.KEY_BLUETOOTH_AUTOCONNECT_ACTION, null);
            int actionFlags = buildActionFlags(actionFlagsStr);

            // When autoconnect is enabled, and if "Open Drive Mode" was checked, we close the
            // drive mode activity when the device has disconnected. The "AUDIO_BECOMING_NOISY"
            // intent will stop the playback anyway when the A2DP device is disconnected.
            if ((actionFlags & BT_AUTOCONNECT_ACTION_OPEN_DRIVE) != 0) {
                Intent finish = new Intent(DriveModeActivity.ACTION_FINISH);
                context.sendBroadcast(finish);
            }
        }
    }

    private int buildActionFlags(Set<String> flags) {
        int actionFlags = 0;
        if (flags != null) {
            for (String flag : flags) {
                actionFlags += Integer.parseInt(flag);
            }
        }

        return actionFlags;
    }
}