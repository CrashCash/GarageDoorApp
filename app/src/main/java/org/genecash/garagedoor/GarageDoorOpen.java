package org.genecash.garagedoor;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;

import static org.genecash.garagedoor.Utilities.RESPONSE;
import static org.genecash.garagedoor.Utilities.isDataEnabled;
import static org.genecash.garagedoor.Utilities.isNetworkAvailable;
import static org.genecash.garagedoor.Utilities.log;
import static org.genecash.garagedoor.Utilities.logExcept;
import static org.genecash.garagedoor.Utilities.setDataEnabled;
import static org.genecash.garagedoor.Utilities.sleep;

public class GarageDoorOpen extends Activity {
    String hostname;
    int port;
    String password;
    ContentResolver cr;
    Context ctx = this;
    boolean manageData;
    String command = "OPENCLOSE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app);

        Utilities.setupLogging(this, "app");
        log("Action app started");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // pull network preferences
        hostname = prefs.getString(Utilities.PREFS_IP, "127.0.0.1");
        port = prefs.getInt(Utilities.PREFS_PORT, 17000);
        manageData = prefs.getBoolean(Utilities.PREFS_DATA, true);
        password = prefs.getString(Utilities.PREFS_KEYSTORE_PASSWORD, "");

        cr = getContentResolver();
        if (!isDataEnabled(cr) && manageData) {
            setDataEnabled(cr, true);
        }

        // stop background service process
        sendBroadcast(new Intent(Utilities.ACTION_STOP));

        new NetworkSession().execute();
    }

    @Override
    protected void onDestroy() {
        if (manageData && isDataEnabled(cr)) {
            setDataEnabled(cr, false);
        }
        super.onDestroy();
    }

    class NetworkSession extends AsyncTask<Void, String, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            Socket sock;
            SSLSocketFactory sslSocketFactory = Utilities.initSSL(ctx, password);
            while (true) {
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
                        log("OpenDoor response received");
                        break;
                    } else {
                        // boo!
                        log("invalid OpenDoor response: " + response);
                        sock.close();
                        continue;
                    }
                } catch (IOException e) {
                    logExcept("OpenDoor", e);
                    log("Network: " + isNetworkAvailable(ctx));
                    log("Data: " + isDataEnabled(cr));
                    if (!isDataEnabled(cr) && manageData) {
                        setDataEnabled(cr, true);
                    }
                }
                // don't spam the living hell out of the logs
                sleep(DateUtils.SECOND_IN_MILLIS / 2);
            }
            publishProgress();
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            finish();
        }
    }
}
