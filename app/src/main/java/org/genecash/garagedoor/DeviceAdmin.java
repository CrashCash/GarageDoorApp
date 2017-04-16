package org.genecash.garagedoor;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class DeviceAdmin extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i("garagedoorDeviceAdmin", "onEnabled");
        Toast.makeText(context, "onEnabled", Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Log.i("garagedoorDeviceAdmin", "onDisableRequested");
        return "onDisableRequested";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i("garagedoorDeviceAdmin", "onDisabled");
        Toast.makeText(context, "onDisabled", Toast.LENGTH_LONG).show();
    }
}
