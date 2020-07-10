package org.genecash.garagedoor;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

import static org.genecash.garagedoor.Utilities.log;
import static org.genecash.garagedoor.Utilities.setAirplaneModeActive;
import static org.genecash.garagedoor.Utilities.setupLogging;
import static org.genecash.garagedoor.Utilities.stopLogging;

public class GarageStatus extends Activity {
    GarageStatus ctx = this;
    GetStatus taskStatus;
    ImageButton btn_roll, btn_door, btn_armed, btn_beam;
    String hostname;
    InetAddress host;
    int port;
    SSLSocketFactory sslSocketFactory;
    String password;
    Socket sockStatus = null;
    Socket sockCommand = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status);

        setupLogging(this, "status");

        // pull external address & port from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        hostname = prefs.getString(Utilities.PREFS_IP, "");
        port = prefs.getInt(Utilities.PREFS_PORT, 0);
        password = prefs.getString(Utilities.PREFS_KEYSTORE_PASSWORD, "");
        try {
            host = Inet4Address.getByName(hostname);
        } catch (UnknownHostException e) {
            log("unknown host");
            Toast.makeText(this, "Unknown host", Toast.LENGTH_LONG).show();
            finish();
        }

        log("----------------------------------------------------------------");
        log(String.format("host/port: %s/%d", hostname, port));

        // button to open/close garage door & show current state
        btn_roll = findViewById(R.id.status_roll);

        // button to disarm door
        btn_armed = findViewById(R.id.status_armed);

        // button to show current state of back door
        btn_door = findViewById(R.id.status_door);

        // button to show current state of beam
        btn_beam = findViewById(R.id.status_beam);

        // initialize SSL
        sslSocketFactory = Utilities.initSSL(this, password);

        // disable NetworkOnMainThreadException - otherwise the onPause takes a crap when it tries to close sockets
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);

        // turn off airplane mode
        setAirplaneModeActive(getContentResolver(), false);
    }

    @Override
    protected void onStart() {
        taskStatus = new GetStatus();
        taskStatus.execute();
        super.onStart();
    }

    @Override
    protected void onPause() {
        taskStatus.cancel(true);
        if (sockStatus != null) {
            try {
                sockStatus.close();
            } catch (IOException e) {
                log("Close status socket exception: " + e.getMessage());
            }
        }
        if (sockCommand != null) {
            try {
                sockCommand.close();
            } catch (IOException e) {
                log("Close command socket exception: " + e.getMessage());
            }
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        stopLogging();
        super.onDestroy();
    }

    // get a string from the network in a separate thread
    class GetStatus extends AsyncTask<Void, String, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            String status;
            String[] statuses;

            try {
                sockStatus = sslSocketFactory.createSocket(host, port);
                BufferedReader br = new BufferedReader(new InputStreamReader(sockStatus.getInputStream(), "ASCII"));
                if (br.readLine().equals(Utilities.RESPONSE)) {
                    sockStatus.getOutputStream().write("STATUS\n".getBytes());

                    // read status changes until we exit
                    while (!isCancelled()) {
                        try {
                            status = br.readLine();
                            log(status);
                            if (status == null) {
                                // server has closed connection
                                publishProgress("error", "Connection closed");
                                break;
                            }
                            statuses = status.split("\\s+");
                            if (statuses[0].equals("STATUS")) {
                                publishProgress("status", statuses[1], statuses[2], statuses[3], statuses[4]);
                            }
                        } catch (Exception e) {
                            publishProgress("error", e.toString());
                            break;
                        }
                    }
                } else {
                    publishProgress("error", "Invalid response");
                }
                sockStatus.close();
            } catch (Exception e) {
                publishProgress("error", e.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values[0].equals("error")) {
                // error message from exception
                Toast.makeText(ctx, values[1], Toast.LENGTH_LONG).show();
                log("GetStatus error: " + values[1]);
            } else if (values[0].equals("status")) {
                // roll-up door
                String status = values[1];
                if ("TRANSIT".equals(status)) {
                    btn_roll.setImageResource(R.drawable.barberpole_gray);
                } else if ("CLOSED".equals(status)) {
                    btn_roll.setImageResource(R.drawable.solid_green);
                } else if ("OPEN".equals(status)) {
                    btn_roll.setImageResource(R.drawable.solid_red);
                } else {
                    btn_roll.setImageResource(R.drawable.barberpole_red);
                }
                // back door
                status = values[2];
                if ("CLOSED".equals(status)) {
                    btn_door.setImageResource(R.drawable.solid_green);
                } else if ("OPEN".equals(status)) {
                    btn_door.setImageResource(R.drawable.solid_red);
                } else {
                    btn_door.setImageResource(R.drawable.barberpole_red);
                }
                // photoelectric beam
                status = values[3];
                if ("CLEAR".equals(status)) {
                    btn_beam.setImageResource(R.drawable.solid_green);
                } else if ("BLOCKED".equals(status)) {
                    btn_beam.setImageResource(R.drawable.solid_red);
                } else {
                    btn_roll.setImageResource(R.drawable.barberpole_red);
                }
                // armed status
                status = values[4];
                if ("DISARMED".equals(status)) {
                    btn_armed.setImageResource(R.drawable.solid_green);
                } else if ("ARMED".equals(status)) {
                    btn_armed.setImageResource(R.drawable.solid_red);
                } else {
                    btn_roll.setImageResource(R.drawable.barberpole_red);
                }
            }
        }
    }

    // send command to the garage door
    class SendCmd extends AsyncTask<String, String, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                sockCommand = sslSocketFactory.createSocket(host, port);
                if (sockCommand != null) {
                    sockCommand.getOutputStream().write((params[0] + "\n").getBytes());
                } else {
                    publishProgress("No command given!");
                }
                sockCommand.close();
            } catch (Exception e) {
                publishProgress(e.getMessage());
            }
            return null;
        }

        // display error message
        @Override
        protected void onProgressUpdate(String... values) {
            // error message from exception
            Toast.makeText(ctx, values[0], Toast.LENGTH_LONG).show();
            log("SendCmd exception: " + values[0]);
        }
    }

    public void clickRoll(View view) {
        // this is how we have more than one AsyncTask running at a time
        new SendCmd().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (String[]) new String[]{"TOGGLE"});
    }

    public void clickArmed(View view) {
        new SendCmd().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (String[]) new String[]{"ARM"});
    }
}