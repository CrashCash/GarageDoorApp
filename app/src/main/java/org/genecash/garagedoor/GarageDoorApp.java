package org.genecash.garagedoor;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import static org.genecash.garagedoor.Utilities.log;

public class GarageDoorApp extends Activity {
    // exit when the service tells us to
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Utilities.ACTION_CLOSE.equals(intent.getAction())) {
                log("onReceive finish");
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app);

        // turn screen on
        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Utilities.setupLogging(this, "app");
        log("Started");

        // get permissions
        String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissions, 1);
        boolean ok = true;
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, permission + " not granted", Toast.LENGTH_LONG).show();
                ok = false;
                finish();
            }
        }
        if (ok) {
            registerReceiver(broadcastReceiver, new IntentFilter(Utilities.ACTION_CLOSE));

            // make noise?
            Intent i = getIntent();
            // this extra is only passed from the Bluetooth receiver
            boolean noise = i.getBooleanExtra(Utilities.NOISE_FLAG, false);

            // start background service process
            Intent intent = new Intent(this, GarageDoorService.class);
            intent.putExtra(Utilities.NOISE_FLAG, noise);
            startService(intent);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {
            String s = "Error in onDestroy(): " + e.getMessage();
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
            log(s);
        }
        Utilities.stopLogging();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //super.onNewIntent(intent);
        log("onNewIntent");
    }
}
