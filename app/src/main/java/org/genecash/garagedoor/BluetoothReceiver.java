package org.genecash.garagedoor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String name = device.getName();
        String address = device.getAddress();

        Log.i("GarageDoor", "bluetooth action: " + action);
        if (name != null) {
            Log.i("GarageDoor", "bluetooth name: " + name);
        }
        Log.i("GarageDoor", "bluetooth address: " + address);

        if (name != null && action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String bt_name = prefs.getString(Utilities.PREFS_BT_NAME, "");

            if (name.equals(bt_name)) {
                // start up app instead of service, so it can lock the screen
                Log.i("GarageDoor", "starting app from bluetooth button");
                Intent app = new Intent(context, GarageDoorApp.class);
                if (prefs.getBoolean(Utilities.PREFS_DEBUG, false)) {
                    // if we're debugging, launch the status app instead
                    app = new Intent(context, GarageStatus.class);
                }
                app.setAction(Intent.ACTION_MAIN);
                app.putExtra(Utilities.NOISE_FLAG, true);
                app.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.addCategory(Intent.CATEGORY_LAUNCHER);
                context.startActivity(app);

                // we can't just sleep
                new Handler().postDelayed(new RestoreBluetooth(), 1 * Utilities.MILLISECONDS);
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