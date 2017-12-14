package org.genecash.garagedoor;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i("GarageDoor", "bluetooth intent: " + intent);

        if (action == Intent.ACTION_MEDIA_BUTTON) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            Log.i("GarageDoor", "bluetooth key event: " + event);
            int keyAction = event.getAction();
            String keyString = event.getCharacters();
            Log.i("GarageDoor", "bluetooth keys: " + keyString);
            if (keyAction == KeyEvent.ACTION_DOWN) {
                Log.i("GarageDoor", "bluetooth key press");
            }
            if (keyAction == KeyEvent.ACTION_UP) {
                Log.i("GarageDoor", "bluetooth key release");
            }
            int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    Log.i("GarageDoor", "bluetooth key media prev");
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    Log.i("GarageDoor", "bluetooth key media next");
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    Log.i("GarageDoor", "bluetooth key media play/pause");
                    break;
            }
            if (event.isAltPressed()){
                Log.i("GarageDoor", "bluetooth key alt pressed");
            }
            if (event.isShiftPressed()){
                Log.i("GarageDoor", "bluetooth key shift pressed");
            }
            if (event.isCtrlPressed()){
                Log.i("GarageDoor", "bluetooth key ctrl pressed");
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
            Log.i("GarageDoor", "bluetooth address: " + address);

            if (deviceName != null) {
                Log.i("GarageDoor", "bluetooth name: " + deviceName);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String btName = prefs.getString(Utilities.PREFS_BT_NAME, "");

                // determine if service is already running
                boolean running = false;
                if (deviceName.equals(btName)) {
                    ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                        if (GarageDoorService.class.getName().equals(service.service.getClassName())) {
                            running = true;
                        }
                    }

                    if (running) {
                        // service is running, just make noise
                        Log.i("GarageDoor", "starting service from bluetooth button");
                        Intent service = new Intent(context, GarageDoorService.class);
                        service.putExtra(Utilities.NOISE_FLAG, true);
                        context.startService(service);
                    } else {
                        // start up app so it can rouse the device and lock the screen
                        Log.i("GarageDoor", "starting app from bluetooth button");
                        Intent app = new Intent(context, GarageDoorApp.class);
                        app.setAction(Intent.ACTION_MAIN);
                        app.putExtra(Utilities.NOISE_FLAG, true);
                        app.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        app.addCategory(Intent.CATEGORY_LAUNCHER);
                        context.startActivity(app);
                    }

                    // we can't just sleep
                    new Handler().postDelayed(new RestoreBluetooth(), 1 * Utilities.MILLISECONDS);
                }
            }
        }
    }

    // disconnect button so we can use it again
    // ok, if we can't do this the nice way, we'll do it with a baseball bat
    class RestoreBluetooth implements Runnable {
        @Override
        public void run() {
            Log.i("GarageDoor", "BT shutdown");
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            adapter.disable();
            Utilities.sleep(5 * Utilities.MILLISECONDS);
            adapter.enable();
        }
    }
}