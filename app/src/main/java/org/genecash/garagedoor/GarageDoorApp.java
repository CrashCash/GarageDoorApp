package org.genecash.garagedoor;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import static org.genecash.garagedoor.Utilities.log;

public class GarageDoorApp extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean ok = true;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.app);

        // turn screen on so the GPS wakes up
        // we keep the screen on until we get the first location update
        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Utilities.setupLogging(this, "app");
        log("App started");

        // see if we have permissions
        // this won't actually let you grant WRITE_SECURE_SETTINGS, but it will let you tell if it's granted or not
        // Run the following as root in adb shell to grant it:
        // pm grant org.genecash.garagedoor android.permission.WRITE_SECURE_SETTINGS
        String[] permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.WRITE_SECURE_SETTINGS};
        requestPermissions(permissions, 0);
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, permission + " not granted", Toast.LENGTH_LONG).show();
                ok = false;
                finish();
            }
        }

        // see if we can do superuser stuff
        if (ok) {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "echo"});
                if (process.waitFor() != 0) {
                    Toast.makeText(this, "Unable to perform superuser commands", Toast.LENGTH_LONG).show();
                    ok = false;
                    finish();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Unable to perform superuser commands", Toast.LENGTH_LONG).show();
                ok = false;
                finish();
            }
        }

        // see if we're a device admin
        if (ok) {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName adminName = new ComponentName(this, DeviceAdmin.class);
            if (!devicePolicyManager.isAdminActive(adminName)) {
                // pop up the screen to ask for admin rights
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName);
                // intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Give permission to lock device");
                startActivity(intent);
                ok = false;
                finish();
            }
        }

        // we're finally good to go
        if (ok) {
            // make noise?
            Intent i = getIntent();
            // this extra is only passed from the Bluetooth receiver
            boolean noise = i.getBooleanExtra(Utilities.NOISE_FLAG, false);

            // start background service process
            Intent intent = new Intent(this, GarageDoorService.class);
            intent.putExtra(Utilities.NOISE_FLAG, noise);
            startForegroundService(intent);
        }
        log("App finish");
        finish();
    }

    @Override
    protected void onDestroy() {
        log("App onDestroy");
        Utilities.stopLogging();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //super.onNewIntent(intent);
        log("onNewIntent");
    }
}
