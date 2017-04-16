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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.math.RoundingMode;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;

import static org.genecash.garagedoor.Utilities.log;

public class GarageSettings extends Activity {
    private Location location;
    private LocationManager locationManager;
    private LocationListener locationListener;

    // display widgets
    private EditText edIP;
    private EditText edPort;
    private EditText edBtName;
    private EditText edLatitude;
    private EditText edLongitude;
    private EditText edRadiusOpen;
    private EditText edRadiusRate;
    private EditText edRateHi;
    private EditText edRateLo;
    private CheckBox cbData;
    private CheckBox cbGPS;
    private CheckBox cbDebug;
    private CheckBox cbLock;
    private CheckBox cbNoise;
    private Button bGPS;

    private SharedPreferences prefs;
    private GarageSettings ctx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        ctx = this;
        Utilities.setupLogging(this, "prefs");

        log("\n----------------------------------------------------------------");

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
            edIP = (EditText) findViewById(R.id.ip);
            edPort = (EditText) findViewById(R.id.port);
            edBtName = (EditText) findViewById(R.id.bt_name);
            edLatitude = (EditText) findViewById(R.id.latitude);
            edLongitude = (EditText) findViewById(R.id.longitude);
            edRadiusOpen = (EditText) findViewById(R.id.radius_open);
            edRadiusRate = (EditText) findViewById(R.id.radius_rate);
            edRateHi = (EditText) findViewById(R.id.rate_hi);
            edRateLo = (EditText) findViewById(R.id.rate_lo);
            cbData = (CheckBox) findViewById(R.id.check_data);
            cbGPS = (CheckBox) findViewById(R.id.check_gps);
            cbDebug = (CheckBox) findViewById(R.id.check_debug);
            cbLock = (CheckBox) findViewById(R.id.check_lock);
            cbNoise = (CheckBox) findViewById(R.id.check_noise);
            bGPS = (Button) findViewById(R.id.mark);

            // populate fields from current settings
            edIP.setText(prefs.getString(Utilities.PREFS_IP, "99.168.121.221"));
            edPort.setText("" + prefs.getInt(Utilities.PREFS_PORT, 17000));
            edBtName.setText(prefs.getString(Utilities.PREFS_BT_NAME, ""));
            edLatitude.setText("" + prefs.getFloat(Utilities.PREFS_LATITUDE, 28.543808f));
            edLongitude.setText("" + prefs.getFloat(Utilities.PREFS_LONGITUDE, -81.20185f));
            edRadiusOpen.setText("" + prefs.getInt(Utilities.PREFS_RADIUS_OPEN, 60));
            edRadiusRate.setText("" + prefs.getInt(Utilities.PREFS_RADIUS_RATE, 2000));
            edRateHi.setText("" + prefs.getInt(Utilities.PREFS_RATE_HI, 1));
            edRateLo.setText("" + prefs.getInt(Utilities.PREFS_RATE_LO, 30));
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
                        editor.putInt(Utilities.PREFS_RADIUS_RATE, Integer.parseInt(edRadiusRate.getText().toString()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(getApplicationContext(), "Radii must be an integer", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        editor.putInt(Utilities.PREFS_RATE_HI, Integer.parseInt(edRateHi.getText().toString()));
                        editor.putInt(Utilities.PREFS_RATE_LO, Integer.parseInt(edRateLo.getText().toString()));
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
            findViewById(R.id.fetch).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    fetchCert();
                }
            });

            // turn GPS on
            Utilities.setGPSOn(getContentResolver(), true);

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
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5 * Utilities.MILLISECONDS, 0, locationListener);
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
}
