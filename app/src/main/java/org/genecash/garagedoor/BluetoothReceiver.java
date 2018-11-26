package org.genecash.garagedoor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.KeyEvent;

import static org.genecash.garagedoor.Utilities.log;
import static org.genecash.garagedoor.Utilities.setupLogging;
import static org.genecash.garagedoor.Utilities.sleep;
import static org.genecash.garagedoor.Utilities.stopLogging;

// To turn off the notification sound that happens when a physical keyboard connects, go to
// Settings -> Apps & notifications -> App info
// Menu -> Show system
// Pick "Android System"
// Pick "App notifications"
// Scroll down to "Physical keyboard" and pick it
// Pick "Sound"
// Select "None"
// Whew.

public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        setupLogging(context, "bluetooth");
        String action = intent.getAction();
        log("bluetooth intent: " + intent);

        if (action == Intent.ACTION_MEDIA_BUTTON) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            log("bluetooth key event: " + event);
            int keyAction = event.getAction();
            String keyString = event.getCharacters();
            log("bluetooth keys: " + keyString);
            if (keyAction == KeyEvent.ACTION_DOWN) {
                log("bluetooth key press");
            }
            if (keyAction == KeyEvent.ACTION_UP) {
                log("bluetooth key release");
            }
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    log("bluetooth key media prev");
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    log("bluetooth key media next");
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    log("bluetooth key media play/pause");
                    break;
            }
            if (event.isAltPressed()) {
                log("bluetooth key alt pressed");
            }
            if (event.isShiftPressed()) {
                log("bluetooth key shift pressed");
            }
            if (event.isCtrlPressed()) {
                log("bluetooth key ctrl pressed");
            }
            return;
        }

        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                return;
            }
            String deviceName = device.getName();
            String address = device.getAddress();
            log("bluetooth address: " + address);

            if (deviceName != null) {
                log("bluetooth name: " + deviceName);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String btName = prefs.getString(Utilities.PREFS_BT_NAME, "");

                if (deviceName.equals(btName)) {
                    // start background service process
                    log("starting service from bluetooth button");
                    Intent service = new Intent(context, GarageDoorService.class);
                    service.putExtra(Utilities.NOISE_FLAG, true);
                    context.startForegroundService(service);

                    // this is no longer necessary in Oreo!
                    // just go to the Bluetooth setting for the paired button, and uncheck all the "Use for" profiles and Oreo will
                    // automatically disconnect it!
                    //
                    // we can't just sleep
                    // new Handler().postDelayed(new RestoreBluetooth(), 1 * DateUtils.SECOND_IN_MILLIS);
                }
            }
        }
        stopLogging();
    }

    // disconnect button so we can use it again
    // ok, if we can't do this the nice way, we'll do it with a baseball bat
    class RestoreBluetooth implements Runnable {
        @Override
        public void run() {
            log("BT shutdown");
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            adapter.disable();
            sleep(5 * DateUtils.SECOND_IN_MILLIS);
            adapter.enable();
        }
    }
}