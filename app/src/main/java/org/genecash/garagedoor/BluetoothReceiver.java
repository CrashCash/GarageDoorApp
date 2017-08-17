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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;

public class BluetoothReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String deviceName = device.getName();
        String address = device.getAddress();

        Log.i("GarageDoor", "bluetooth action: " + action);
        if (deviceName != null) {
            Log.i("GarageDoor", "bluetooth name: " + deviceName);
        }
        Log.i("GarageDoor", "bluetooth address: " + address);

        if (deviceName != null && action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String btName = prefs.getString(Utilities.PREFS_BT_NAME, "");

            if (deviceName.equals(btName)) {
                // start up app instead of service, so it can lock the screen
                Log.i("GarageDoor", "starting app from bluetooth button");
                Intent app = new Intent(context, GarageDoorApp.class);
                app.setAction(Intent.ACTION_MAIN);
                app.putExtra(Utilities.NOISE_FLAG, true);
                app.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.addCategory(Intent.CATEGORY_LAUNCHER);
                context.startActivity(app);

                // now find input device
                String hid = null;
                File folder = new File("/sys/devices/virtual/misc/uhid");
                for (File f : folder.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File path, String name) {
                        return name.toLowerCase().startsWith("input");
                    }
                })) {
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(new File(f, "name")));
                        // is this our bluetooth device?
                        if (br.readLine().equals(btName)) {
                            // find name of device
                            File[] dir = f.listFiles(new FilenameFilter() {
                                @Override
                                public boolean accept(File path, String name) {
                                    return name.toLowerCase().startsWith("event");
                                }
                            });
                            hid = "/dev/input/" + dir[0].getName();
                            Log.i("GarageDoor", "HID device: " + hid);
                            break;
                        }
                    } catch (Exception e) {
                    }
                }

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