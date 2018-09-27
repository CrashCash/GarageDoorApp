package org.genecash.garagedoor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;

import javax.net.ssl.SSLSocketFactory;

import static org.genecash.garagedoor.Utilities.RESPONSE;
import static org.genecash.garagedoor.Utilities.isDataEnabled;
import static org.genecash.garagedoor.Utilities.isNetworkAvailable;
import static org.genecash.garagedoor.Utilities.log;
import static org.genecash.garagedoor.Utilities.setDataEnabled;
import static org.genecash.garagedoor.Utilities.sleep;

@SuppressLint("MissingPermission")
public class GarageDoorService extends Service implements LocationListener {
    // locks
    WakeLock cpuLock;
    PowerManager pm;

    // notifications
    NotificationManager notifyManager;
    Builder notifyBuilder;
    Uri uriRingtone;
    Uri uriAlert;
    MediaPlayer player = null;
    boolean sound;
    static final int NOTIFICATION_ID = 1;
    long lastWhistle = 0;

    // network info
    String hostname;
    int port;
    SSLSocketFactory sslSocketFactory;
    Socket sock = null;
    String password;
    long lastPing = 0;

    // do we actually manage the cell data?
    boolean manageData;

    // GPS information
    boolean manageGPS;
    Location destination;
    String rate;
    int radius_open;
    int radius_rate;
    int interval_hi;
    int interval_lo;
    long startGPS;
    boolean rate_low;
    static boolean locationChanged = false;
    LocationManager locationManager;

    // queue of button press times
    Queue<Long> presses = new ArrayDeque<>();

    // log debugging messages
    boolean debug = false;

    // lock screen
    boolean lockScreen;

    // the command to open the door has been sent and successfully processed
    boolean command_sent;
    String command = "OPENCLOSE";

    // "Jane! Stop this crazy thing!"
    boolean stop = false;

    // exit when told
    BroadcastReceiver broadcastReceiverStop = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = "Exiting";
            log(msg);
            toast(msg);
            stop = true;
            stopSelf();
        }
    };

    // toggle opening action
    BroadcastReceiver broadcastReceiverToggle = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (command.equals("OPENCLOSE")) {
                command = "OPEN";
            } else {
                command = "OPENCLOSE";
            }
            String msg = "Toggle command: " + command;
            notifyUpdate(msg);
            toast(msg);
        }
    };

    @Override
    public void onCreate() {
        Utilities.setupLogging(this, "service");
        log("Service started");

        // no matter what, log all exceptions
        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                log("onCreate uncaughtException: " + Log.getStackTraceString(paramThrowable));

                if (oldHandler != null) {
                    // delegates to Android's error handling
                    oldHandler.uncaughtException(paramThread, paramThrowable);
                } else {
                    // prevents the service/app from freezing
                    System.exit(2);
                }
            }
        });

        // custom sounds
        uriRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        uriAlert = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/" + R.raw.whistle);

        registerReceiver(broadcastReceiverStop, new IntentFilter(Utilities.ACTION_STOP));
        registerReceiver(broadcastReceiverToggle, new IntentFilter(Utilities.ACTION_TOGGLE));

        // persistent preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // pull network preferences
        hostname = prefs.getString(Utilities.PREFS_IP, "127.0.0.1");
        port = prefs.getInt(Utilities.PREFS_PORT, 17000);
        manageData = prefs.getBoolean(Utilities.PREFS_DATA, true);
        password = prefs.getString(Utilities.PREFS_KEYSTORE_PASSWORD, "");

        // pull GPS preferences
        manageGPS = prefs.getBoolean(Utilities.PREFS_GPS, true);
        float latitude = prefs.getFloat(Utilities.PREFS_LATITUDE, 400);
        float longitude = prefs.getFloat(Utilities.PREFS_LONGITUDE, 400);
        radius_open = prefs.getInt(Utilities.PREFS_RADIUS_OPEN, 0);
        radius_rate = prefs.getInt(Utilities.PREFS_RADIUS_RATE, 0);
        interval_hi = prefs.getInt(Utilities.PREFS_RATE_HI, -1);
        interval_lo = prefs.getInt(Utilities.PREFS_RATE_LO, -1);

        // sanity check
        if (latitude == 400 || longitude == 400 || radius_open == 0 || radius_rate == 0 || interval_hi == -1 || interval_lo == -1) {
            log("preferences not set - exiting");
            toast("Preferences not set - exiting");
            stop = true;
            stopSelf();
            return;
        }

        destination = new Location("destination");
        destination.setLatitude(latitude);
        destination.setLongitude(longitude);

        // user-configurable debugging
        debug = prefs.getBoolean(Utilities.PREFS_DEBUG, false);

        // does user want sounds?
        sound = prefs.getBoolean(Utilities.PREFS_NOISE, true);

        // lock the screen?
        lockScreen = prefs.getBoolean(Utilities.PREFS_LOCK, true);

        // disable NetworkOnMainThreadException
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);

        log("\n----------------------------------------------------------------");
        log(String.format("host/port: %s/%d\n" + "dest: %.4f,%.4f\n" + "rate radius: %d\n" + "open radius: % d\n" + "hi rate: %d\n" +
                          "lo rate: %d\n" + "manage data: %s\n" + "manage GPS: %s\n", hostname, port, latitude, longitude, radius_rate,
                          radius_open, interval_hi, interval_lo, manageData, manageGPS));

        // turn on cell data
        if (manageData) {
            setDataEnabled(getContentResolver(), true);
        }

        // start in foreground so we don't get killed
        notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // create notification channel for Oreo
        String channelID = "GarageDoor";
        NotificationChannel notificationChannel = new NotificationChannel(channelID, "Garage Door", NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        // turn off default sound
        notificationChannel.setSound(null, null);
        notificationChannel.setLightColor(Color.RED);
        notifyManager.createNotificationChannel(notificationChannel);

        notifyBuilder = new Notification.Builder(this)
                .setContentTitle("Garage Door Opener")
                .setContentText("Initializing")
                .setSmallIcon(R.drawable.open_app)
                .setOngoing(true)
                .setStyle(new Notification.BigTextStyle())
                .setChannelId(channelID)
                .setSound(null);
        startForeground(NOTIFICATION_ID, notifyBuilder.build());

        // acquire full lock so GPS works (fuck that partial wake lock shit!)
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        cpuLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "GarageDoorService: GarageCPULock");
        cpuLock.acquire();

        // initialize SSL
        sslSocketFactory = Utilities.initSSL(this, password);

        // turn on GPS
        log("initialize GPS");
        if (!Utilities.getGPSOn(getContentResolver())) {
            Utilities.setGPSOn(getContentResolver(), true);
            sleep(2 * DateUtils.SECOND_IN_MILLIS);
        }

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        setupGPS();

        shutDownApp();
    }

    // we are using the old platform location API instead of the Google Location Services API from
    // Google Play Services, because it's more reliable, and doesn't get randomly screwed by Play updates.
    // http://developer.android.com/guide/topics/location/strategies.html
    @Override
    public void onLocationChanged(Location location) {
        log("onLocationChanged");
        if (stop) {
            return;
        }

        if (!locationChanged) {
            // pretty-print the time it took to acquire the GPS location
            long millis = System.currentTimeMillis() - startGPS;
            int seconds = (int) ((millis / 1000) % 60);
            int minutes = (int) ((millis / (1000 * 60)) % 60);
            // int hours   = (int) ((millis / (1000 * 60 * 60)) % 24);
            String time = String.format("%d:%02d", minutes, seconds);
            notifyUpdate("GPS acquired: " + time, uriRingtone);
            locationChanged = true;
        }

        int distance = (int) location.distanceTo(destination);
        float meters_sec = location.getSpeed();
        float miles_hour = (float) (meters_sec * 2.23694);
        log("GPS: " + location.getLatitude() + "," + location.getLongitude() + " " + distance + "m " + meters_sec + "m/s " +
            miles_hour + "mph");

        if (distance < radius_open) {
            if (!command_sent) {
                // tell the Raspberry Pi to open the door
                notifyUpdate("Opening door", uriRingtone);
                openDoor();
                stop = true;
                log("exiting due to trip complete");

                // give notification sound time to finish
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopSelf();
                    }
                }, 5 * DateUtils.SECOND_IN_MILLIS);
                return;
            }
        } else {
            command_sent = false;
        }

        // tune data rate to preserve battery
        if (distance < radius_rate) {
            // inside the radius, open socket and switch to high rate
            if (sock == null) {
                OpenSocket();
            }
            if (rate_low) {
                notifyUpdate("switching to high rate", uriAlert);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval_hi * DateUtils.SECOND_IN_MILLIS, 0, this);
                rate = "High";
                rate_low = false;
            }

            // keep connection alive
            ping();
        } else {
            // outside the radius, close the socket and switch to low rate
            if (sock != null) {
                closeSocket();
            }
            if (!rate_low) {
                notifyUpdate("switching to low rate");
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval_lo * DateUtils.SECOND_IN_MILLIS, 0, this);
                rate = "Low";
                rate_low = true;
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        String statusString = "GPS Unknown (" + status + ")";
        switch (status) {
            case LocationProvider.AVAILABLE:
                statusString = "GPS Available";
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                statusString = "GPS Temporarily Unavailable";
                break;
            case LocationProvider.OUT_OF_SERVICE:
                statusString = "GPS Out Of Service";
                break;
        }
        log("onStatusChanged: " + statusString);
    }

    @Override
    public void onProviderEnabled(String provider) {
        log("onProviderEnabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        log("onProviderDisabled: " + provider);
    }

    @Override
    public IBinder onBind(Intent intent) {
        log("onBind");
        return null;
    }

    // called every time we're started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand");
        // lying sacks of shit don't always pass extras, or even an intent, as required by the documentation
        Bundle extras = null;
        if (intent != null) {
            extras = intent.getExtras();
        }

        if (extras != null) {
            if (extras.getBoolean(Utilities.NOISE_FLAG, true)) {
                // play whistle sound
                long now = System.currentTimeMillis();
                if (now - lastWhistle > 5 * DateUtils.SECOND_IN_MILLIS) {
                    lastWhistle = now;
                    notifyUpdate("Started by Bluetooth button", uriAlert);
                }
            }
        }

        return START_NOT_STICKY;
    }

    // shut down gracefully
    @Override
    public void onDestroy() {
        log("onDestroy");
        toast("Garage Door application exiting");

        if (sock != null) {
            closeSocket();
        }

        // disconnect GPS
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
        if (manageGPS) {
            Utilities.setGPSOn(getContentResolver(), false);
        }

        // turn off cell data
        if (manageData) {
            setDataEnabled(getContentResolver(), false);
        }

        unregisterReceiver(broadcastReceiverStop);
        unregisterReceiver(broadcastReceiverToggle);

        // turn off notifications
        if (notifyManager != null) {
            notifyManager.cancelAll();
        }
        Utilities.stopLogging();

        if (cpuLock != null) {
            cpuLock.release();
        }
        super.onDestroy();
    }

    void logExcept(String fcn, Exception e) {
        Utilities.logExcept(fcn, e);
        notifyUpdate(fcn + " exception\n" + e.getMessage());
    }

    // update notification
    void notifyUpdate(String msg, Uri audio) {
        log("notifyUpdate: " + msg + " " + audio);
        notifyBuilder.setContentText(msg);
        notifyManager.notify(NOTIFICATION_ID, notifyBuilder.build());
        if (audio != null && sound) {
            // wait for previous sound to finish
            notifyWait();

            // play sound
            if (player != null) {
                player.release();
            }
            try {
                player = new MediaPlayer().create(this, audio);
                player.setLooping(false);
                player.start();
            } catch (Exception e) {
                logExcept("notifyUpdate", e);
            }
        }
        log(msg);
    }

    void notifyUpdate(String msg) {
        notifyUpdate(msg, null);
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // wait for notification sound/vibration to finish playing
    void notifyWait() {
        while (player != null && player.isPlaying()) {
            log("sound_playing");
            sleep(DateUtils.SECOND_IN_MILLIS);
        }
    }

    // request location updates from GPS
    void setupGPS() {
        log("setupGPS");
        // use high rate so we don't get a notification sound if we're already within the rate radius at start
        rate = "High";
        rate_low = false;
        command_sent = false;
        locationChanged = false;
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval_hi * DateUtils.SECOND_IN_MILLIS, 0, this);
        startGPS = System.currentTimeMillis();
        notifyUpdate("Waiting for GPS");
    }

    // open SSL socket with standardized options and do initial handshake
    synchronized void OpenSocket() {
        log("openSocket");
        BufferedReader buffRdr;
        String response;

        while (!stop) {
            log("openSocket loop");
            try {
                sock = sslSocketFactory.createSocket(hostname, port);
                sock.setSoTimeout((int) (10 * DateUtils.SECOND_IN_MILLIS));
                sock.setTcpNoDelay(true);
                buffRdr = new BufferedReader(new InputStreamReader(sock.getInputStream(), "ASCII"));
                response = buffRdr.readLine();
                if (response.equals(RESPONSE)) {
                    break;
                }
                log("invalid response: " + response);
            } catch (Exception e) {
                // sometimes we get a loop of ENETUNREACH (Network is unreachable) even though isDataEnabled() is true
                ContentResolver cr = getContentResolver();
                logExcept("OpenSocket", e);
                log("Network: " + isNetworkAvailable(this));
                log("Data: " + isDataEnabled(cr));
                if (!isDataEnabled(cr) && manageData) {
                    setDataEnabled(cr, true);
                    sleep(15 * DateUtils.SECOND_IN_MILLIS);
                }
                // don't spam the living hell out of the logs
                sleep(5 * DateUtils.SECOND_IN_MILLIS);
            }
        }
        log("end openSocket");
    }

    synchronized void closeSocket() {
        log("closeSocket");
        try {
            sock.close();
        } catch (Exception e) {
        }
        sock = null;
    }

    // bang out the command to open the door
    synchronized void openDoor() {
        log("OpenDoor");
        BufferedReader buffRdr;
        String response;

        if (command_sent || stop) {
            return;
        }
        while (!stop && !command_sent) {
            try {
                if (sock == null) {
                    OpenSocket();
                }
                sock.getOutputStream().write((command + "\n").getBytes());
                buffRdr = new BufferedReader(new InputStreamReader(sock.getInputStream(), "ASCII"));
                response = buffRdr.readLine();
                if (response.equals(command + " DONE")) {
                    log("OpenDoor response received");
                    command_sent = true;
                    break;
                } else {
                    log("invalid OpenDoor response: " + response);
                }
            } catch (Exception e) {
                logExcept("OpenDoor", e);
                OpenSocket();
            }
            sleep(2 * DateUtils.SECOND_IN_MILLIS);
        }
        log("end OpenDoor");
    }

    synchronized void ping() {
        log("ping");
        if (!stop && sock != null) {
            long now = System.currentTimeMillis();
            if (now - lastPing > 5 * DateUtils.SECOND_IN_MILLIS) {
                lastPing = now;
                try {
                    sock.getOutputStream().write(("PING\n").getBytes());
                } catch (Exception e) {
                    logExcept("Ping", e);
                    OpenSocket();
                }
            }
        }
    }

    // lock screen and shut down app
    void shutDownApp() {
        log("ShutDownApp");

        // wait for sound to finish, since locking the screen & app shutdown will interfere
        notifyWait();

        // lock screen
        if (lockScreen) {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            devicePolicyManager.lockNow();
            log("screen locked");
        }

        // shut down app
        sendBroadcast(new Intent(Utilities.ACTION_CLOSE));
    }
}
