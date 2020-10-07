package org.genecash.garagedoor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;

import static org.genecash.garagedoor.Utilities.RESPONSE;
import static org.genecash.garagedoor.Utilities.log;
import static org.genecash.garagedoor.Utilities.logExcept;
import static org.genecash.garagedoor.Utilities.setAirplaneModeActive;
import static org.genecash.garagedoor.Utilities.setupLogging;
import static org.genecash.garagedoor.Utilities.sleep;
import static org.genecash.garagedoor.Utilities.stopLogging;

public class GarageDoorOpen extends Activity {
    String hostname;
    int port;
    String password;
    Context ctx = this;
    boolean manageData;
    String command = "OPENCLOSE";
    String logname = "arm";
    Socket sock;
    MediaPlayer player = null;
    Uri uriDing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app);

        setupLogging(this, logname);
        uriDing = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // pull network preferences
        hostname = prefs.getString(Utilities.PREFS_IP, "127.0.0.1");
        port = prefs.getInt(Utilities.PREFS_PORT, 17000);
        manageData = prefs.getBoolean(Utilities.PREFS_DATA, true);
        password = prefs.getString(Utilities.PREFS_KEYSTORE_PASSWORD, "");

        // stop background service process
        sendBroadcast(new Intent(Utilities.ACTION_STOP));

        // disable NetworkOnMainThreadException
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);

        SSLSocketFactory sslSocketFactory = Utilities.initSSL(ctx, password);

        // turn off airplane mode
        setAirplaneModeActive(getContentResolver(), false);

        int errors = 10;
        while (errors-- > 0) {
            try {
                // connect
                sock = sslSocketFactory.createSocket(hostname, port);

                // we should get the proper connection response
                BufferedReader buffRdr = new BufferedReader(new InputStreamReader(sock.getInputStream(), "ASCII"));
                String response = buffRdr.readLine();
                if (!response.equals(RESPONSE)) {
                    log("invalid response: " + response);
                    sock.close();
                    continue;
                }

                // send command
                sock.getOutputStream().write((command + "\n").getBytes());

                // we should get the proper command response
                response = buffRdr.readLine();
                if (response.equals(command + " DONE")) {
                    // yay! we're done
                    break;
                } else {
                    // boo!
                    log("invalid OpenDoor response: " + response);
                    sock.close();
                    continue;
                }
            } catch (java.net.ConnectException e) {
                log(e.getMessage());
                errors++;
            } catch (Exception e) {
                logExcept(e);
            }
            // don't spam the living hell out of the logs
            ding();
            sleep(DateUtils.SECOND_IN_MILLIS);
        }

        if (errors < 0) {
            Toast.makeText(this, "Giving up", Toast.LENGTH_LONG).show();
            log("retries exhausted");
        }

        soundWait();
        if (player != null) {
            player.release();
        }
        stopLogging();
        finish();
    }

    void ding() {
        soundWait();

        // play sound
        if (player != null) {
            player.release();
        }
        try {
            player = new MediaPlayer().create(this, uriDing);
            player.setLooping(false);
            player.start();
        } catch (Exception e) {
            logExcept(e);
        }
    }

    // wait for notification sound/vibration to finish playing
    void soundWait() {
        while (player != null && player.isPlaying()) {
            sleep(DateUtils.SECOND_IN_MILLIS);
        }
    }
}


