package org.genecash.garagedoor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.math.RoundingMode;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;

import static org.genecash.garagedoor.Utilities.log;

public class GarageSettings extends Activity {
    Location location;
    LocationManager locationManager;
    LocationListener locationListener;

    // display widgets
    EditText edIP, edPort, edBtName, edPassword, edLatitude, edLongitude, edRadiusOpen, edRadiusHigh, edRadiusLow, edRateHigh, edRateMed,
            edRateLow;
    CheckBox cbData, cbGPS, cbDebug, cbLock, cbNoise;
    Button bGPS;

    SharedPreferences prefs;
    GarageSettings ctx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        ctx = this;
        Utilities.setupLogging(this, "settings");

        log("----------------------------------------------------------------");

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
            prefs = PreferenceManager.getDefaultSharedPreferences(this);

            // find fields
            edIP = findViewById(R.id.ip);
            edPort = findViewById(R.id.port);
            edBtName = findViewById(R.id.bt_name);
            edPassword = findViewById(R.id.keystore_password);
            edLatitude = findViewById(R.id.latitude);
            edLongitude = findViewById(R.id.longitude);
            edRadiusOpen = findViewById(R.id.radius_open);
            edRadiusHigh = findViewById(R.id.radius_high);
            edRadiusLow = findViewById(R.id.radius_low);
            edRateHigh = findViewById(R.id.rate_high);
            edRateMed = findViewById(R.id.rate_med);
            edRateLow = findViewById(R.id.rate_low);
            cbData = findViewById(R.id.check_data);
            cbGPS = findViewById(R.id.check_gps);
            cbDebug = findViewById(R.id.check_debug);
            cbLock = findViewById(R.id.check_lock);
            cbNoise = findViewById(R.id.check_noise);
            bGPS = findViewById(R.id.mark);

            // populate fields from current settings
            edIP.setText(prefs.getString(Utilities.PREFS_IP, "99.168.121.221"));
            edPort.setText("" + prefs.getInt(Utilities.PREFS_PORT, 17000));
            edBtName.setText(prefs.getString(Utilities.PREFS_BT_NAME, ""));
            edPassword.setText(prefs.getString(Utilities.PREFS_KEYSTORE_PASSWORD, ""));
            edLatitude.setText("" + prefs.getFloat(Utilities.PREFS_LATITUDE, 28.543808f));
            edLongitude.setText("" + prefs.getFloat(Utilities.PREFS_LONGITUDE, -81.20185f));
            edRadiusOpen.setText("" + prefs.getInt(Utilities.PREFS_RADIUS_OPEN, 60));
            edRadiusHigh.setText("" + prefs.getInt(Utilities.PREFS_RADIUS_HIGH, 1000));
            edRadiusLow.setText("" + prefs.getInt(Utilities.PREFS_RADIUS_LOW, 5000));
            edRateHigh.setText("" + prefs.getInt(Utilities.PREFS_RATE_HIGH, 0));
            edRateMed.setText("" + prefs.getInt(Utilities.PREFS_RATE_MED, 30));
            edRateLow.setText("" + prefs.getInt(Utilities.PREFS_RATE_LOW, 300));
            cbData.setChecked(prefs.getBoolean(Utilities.PREFS_DATA, true));
            cbGPS.setChecked(prefs.getBoolean(Utilities.PREFS_GPS, true));
            cbDebug.setChecked(prefs.getBoolean(Utilities.PREFS_DEBUG, true));
            cbLock.setChecked(prefs.getBoolean(Utilities.PREFS_LOCK, true));
            cbNoise.setChecked(prefs.getBoolean(Utilities.PREFS_NOISE, true));

            // "mark location" button
            bGPS.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        double lat = location.getLatitude();
                        edLatitude.setText("" + lat);
                        double lon = location.getLongitude();
                        edLongitude.setText("" + lon);
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "GPS not active", Toast.LENGTH_LONG).show();
                    }
                }
            });

            // "save" button.
            findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(Utilities.PREFS_IP, edIP.getText().toString().trim());
                    editor.putString(Utilities.PREFS_BT_NAME, edBtName.getText().toString().trim());
                    editor.putString(Utilities.PREFS_KEYSTORE_PASSWORD, edPassword.getText().toString().trim());
                    editor.putBoolean(Utilities.PREFS_DATA, cbData.isChecked());
                    editor.putBoolean(Utilities.PREFS_GPS, cbGPS.isChecked());
                    editor.putBoolean(Utilities.PREFS_DEBUG, cbDebug.isChecked());
                    editor.putBoolean(Utilities.PREFS_LOCK, cbLock.isChecked());
                    editor.putBoolean(Utilities.PREFS_NOISE, cbNoise.isChecked());
                    try {
                        editor.putInt(Utilities.PREFS_PORT, Integer.parseInt(edPort.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Port must be an integer", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        editor.putInt(Utilities.PREFS_RADIUS_OPEN, Integer.parseInt(edRadiusOpen.getText().toString()));
                        editor.putInt(Utilities.PREFS_RADIUS_HIGH, Integer.parseInt(edRadiusHigh.getText().toString()));
                        editor.putInt(Utilities.PREFS_RADIUS_LOW, Integer.parseInt(edRadiusLow.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Radii must be an integer", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        editor.putInt(Utilities.PREFS_RATE_HIGH, Integer.parseInt(edRateHigh.getText().toString()));
                        editor.putInt(Utilities.PREFS_RATE_MED, Integer.parseInt(edRateMed.getText().toString()));
                        editor.putInt(Utilities.PREFS_RATE_LOW, Integer.parseInt(edRateLow.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "GPS refresh rates must be an integer", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        editor.putFloat(Utilities.PREFS_LATITUDE, (float) Double.parseDouble(edLatitude.getText().toString()));
                        editor.putFloat(Utilities.PREFS_LONGITUDE, (float) Double.parseDouble(edLongitude.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Latitude/Longitude must be a decimal number", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (editor.commit()) {
                        Toast.makeText(getApplicationContext(), "Settings saved", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Settings error", Toast.LENGTH_LONG).show();
                    }
                }
            });

            // "fetch certificate" button.
            findViewById(R.id.fetch_cert).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    fetchCert();
                }
            });

            // "fetch IP" button.
            findViewById(R.id.fetch_ip).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    fetchIP();
                }
            });

            // request location from GPS
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    ctx.location = location;
                    DecimalFormat df = new DecimalFormat("####0.0000");
                    df.setRoundingMode(RoundingMode.HALF_UP);

                    double lat = location.getLatitude();
                    String slat = df.format(Math.abs(lat)) + Utilities.DEGREE;
                    if (lat < 0) {
                        slat += "S";
                    } else {
                        slat += "N";
                    }

                    double lon = location.getLongitude();
                    String slon = df.format(Math.abs(lon)) + Utilities.DEGREE;
                    if (lon < 0) {
                        slon += "W";
                    } else {
                        slon += "E";
                    }

                    String text = slat + ", " + slon;

                    // display distance from current setting
                    Location loc = new Location("none");
                    try {
                        loc.setLatitude(Location.convert((edLatitude.getText().toString())));
                        loc.setLongitude(Location.convert((edLongitude.getText().toString())));
                        float distance = location.distanceTo(loc);
                        df = new DecimalFormat("####0");
                        df.setRoundingMode(RoundingMode.HALF_UP);
                        text += " (" + df.format(distance) + " meters)";
                        bGPS.setText(text);
                    } catch (Exception e) {
                        log("Error setting button: " + Log.getStackTraceString(e));
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    String statusString = "GPS Unknown (" + status + ")";
                    switch (status) {
                        case LocationProvider.AVAILABLE:
                            return;
                        case LocationProvider.TEMPORARILY_UNAVAILABLE:
                            statusString = "GPS Temporarily Unavailable";
                            break;
                        case LocationProvider.OUT_OF_SERVICE:
                            statusString = "GPS Out Of Service";
                            break;
                    }
                    log("onStatusChanged: " + statusString);
                    bGPS.setText(statusString);
                }

                @Override
                public void onProviderEnabled(String provider) {
                    log("onProviderEnabled: " + provider);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    log("onProviderDisabled: " + provider);
                }
            };
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5 * DateUtils.SECOND_IN_MILLIS, 0, locationListener);
            } catch (Exception e) {
                String s = "Error getting location: " + e.getMessage();
                Toast.makeText(this, s, Toast.LENGTH_LONG).show();
                log(s);
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            locationManager.removeUpdates(locationListener);
        } catch (Exception e) {
            String s = "Error in onDestroy(): " + e.getMessage();
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
            log(s);
        }
        Utilities.stopLogging();
        super.onDestroy();
    }

    // fetch certificate from external storage (SD card) and move it to protected directory
    void fetchCert() {
        FileChannel src;
        FileChannel dst;
        File f = new File(Environment.getExternalStorageDirectory(), Utilities.SSL_CERT_FILE);

        // open source if we can
        try {
            src = new FileInputStream(f).getChannel();
        } catch (FileNotFoundException e) {
            String s = "Opening certificate file failed: " + Log.getStackTraceString(e);
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
            log(s);
            return;
        }

        // copy & delete file
        try {
            dst = openFileOutput(Utilities.SSL_CERT_FILE, Context.MODE_PRIVATE).getChannel();
            dst.transferFrom(src, 0, src.size());
            src.close();
            dst.close();
            f.delete();
            Toast.makeText(this, "Certificate fetched!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String s = "Error copying certificate: " + Log.getStackTraceString(e);
            Toast.makeText(this, s, Toast.LENGTH_LONG).show();
            log(s);
        }
    }

    // fetch IP address of external gateway
    // note that traceroute does not offer this functionality
    // this only works on the home network, of course
    //
    // ping -n -c 1 -R ubuntuforums.org
    // -n   numeric addresses (do not resolve hostnames)
    // -c 1 send only one packet
    // -R   record route (ubuntuforums.org respects this option - most hosts ignore it)
    // PING ubuntuforums.org (91.189.94.12) 56(124) bytes of data.
    // 64 bytes from 91.189.94.12: icmp_seq=1 ttl=51 time=348 ms
    // RR:     192.168.1.100 <--- our IP address
    //         50.88.176.21  <--- external IP address of network gateway
    //         72.31.218.195
    //         72.31.218.194
    //         72.31.194.170
    //         4.68.70.154
    //         4.69.182.230
    //         212.187.138.81
    //         91.189.88.3
    //
    //
    // --- ubuntuforums.org ping statistics ---
    // 1 packets transmitted, 1 received, 0% packet loss, time 0ms
    // rtt min/avg/max/mdev = 348.299/348.299/348.299/0.000 ms
    void fetchIP() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"ping", "-n", "-c", "1", "-R", "ubuntuforums.org"});
            int rc = process.waitFor();
            if (rc == 0) {
                boolean flag = false;
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    if (flag) {
                        // this line is IP address
                        edIP.setText(line.trim());
                        Toast.makeText(this, "IP address fetched!", Toast.LENGTH_LONG).show();
                        break;
                    }
                    if (line.startsWith("RR:")) {
                        // next line is IP address
                        flag = true;
                    }
                }
                if (!flag) {
                    Toast.makeText(this, "IP address was not fetched!", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "ping returned: " + rc, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error fetching IP address: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
