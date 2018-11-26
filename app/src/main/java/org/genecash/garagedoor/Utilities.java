package org.genecash.garagedoor;

import android.content.ContentResolver;
import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class Utilities {
    // our own logging system
    public static final Logger loggerSet = Logger.getLogger("logger");
    static final String DEGREE = "°";

    // setting preferences
    static final String PREFS_IP = "IP";
    static final String PREFS_PORT = "Port";
    static final String PREFS_DATA = "Manage_Data";
    static final String PREFS_GPS = "Manage_GPS";
    static final String PREFS_LATITUDE = "Pos_Latitude";
    static final String PREFS_LONGITUDE = "Pos_Longitude";
    static final String PREFS_RADIUS_OPEN = "Radius_Open";
    static final String PREFS_RADIUS_RATE = "Radius_Rate";
    static final String PREFS_DEBUG = "Debug_Flag";
    static final String PREFS_LOCK = "Lockscreen_Flag";
    static final String PREFS_NOISE = "org.genecash.garagedoor.noise";
    static final String PREFS_RATE_LO = "Rate_Low";
    static final String PREFS_RATE_HI = "Rate_High";
    static final String PREFS_BT_NAME = "Bluetooth_Name";
    static final String PREFS_KEYSTORE_PASSWORD = "Keystore_Password";
    static final String RESPONSE = "GARAGEDOOR";
    static final String SSL_CERT_FILE = "client.p12";

    static final String NOISE_FLAG = "NOISE_FLAG";

    static final String ACTION_STOP = "org.genecash.garagedoor.stop";
    static final String ACTION_TOGGLE = "org.genecash.garagedoor.toggle";

    private static final String TAG = "GarageDoorSSL";

    // initialize SSL
    static SSLSocketFactory initSSL(Context ctx, String password) {
        try {
            // Thanks to Erik Tews for this code
            // https://www.datenzone.de/blog/2012/01/using-ssltls-client-certificate-authentification-in-android-applications/

            // Load local client certificate and key and server certificate
            FileInputStream pkcs12in = ctx.openFileInput(SSL_CERT_FILE);
            KeyStore keyStoreLocal = KeyStore.getInstance("PKCS12");
            keyStoreLocal.load(pkcs12in, password.toCharArray());

            // Build a TrustManager, that trusts only the server certificate
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            KeyStore keyStoreServer = KeyStore.getInstance("BKS");
            keyStoreServer.load(null, null);
            keyStoreServer.setCertificateEntry("Server", keyStoreLocal.getCertificate("Server"));
            tmf.init(keyStoreServer);

            // Build a KeyManager for Client Authentication
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStoreLocal, null);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            return context.getSocketFactory();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + Log.getStackTraceString(e));
            Toast.makeText(ctx, "Could not initialize SSL: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return null;
    }

    // turn GPS on or off
    // Run the following as root in adb shell:
    // pm grant org.genecash.garagedoor android.permission.WRITE_SECURE_SETTINGS
    static void setGPSOn(ContentResolver cr, boolean value) {
        int flag = Settings.Secure.LOCATION_MODE_OFF;
        log("setGPSOn: " + value);
        if (value) {
            // turn on JUST the GPS without that stupid goddamned Google "collect info" agree/disagree dialog
            // it is so nice to see the GPS icon in the power bar just blink on
            flag = Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
        }

        // you should probably handle exceptions from this
        Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, flag);
    }

    // test GPS state
    static boolean getGPSOn(ContentResolver cr) {
        String provider = Settings.Secure.getString(cr, Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        log("location providers: " + provider);
        return provider.contains(LocationManager.GPS_PROVIDER);
    }

    // is any network available?
    static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // bring mobile data connection up or down
    static boolean setDataEnabled(ContentResolver cr, boolean flag) {
        if (isDataEnabled(cr) == flag) {
            return true;
        }
        log("setDataEnabled: " + flag);
        String command;
        if (flag) {
            command = "svc data enable";
        } else {
            command = "svc data disable";
        }
        if (executeCommandViaSu("-c", command)) {
            return true;
        } else {
            log("setDataEnabled unable to set data connection state");
            return false;
        }
    }

    // is mobile data enabled?
    static boolean isDataEnabled(ContentResolver cr) {
        return Settings.Global.getInt(cr, "mobile_data", 0) == 1;
    }

    // execute a superuser command
    static boolean executeCommandViaSu(String option, String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", option, command});
            return process.waitFor() == 0;
        } catch (Exception e) {
            logExcept(e);
            return false;
        }
    }

    // go through the pain of setting up our own logging
    static void setupLogging(Context ctx, String fn) {
        try {
            Handler h = new FileHandler(ctx.getExternalFilesDir(null) + "/" + fn + "%g.txt", 256 * 1024, 100, true);
            h.setFormatter(new CustomLogFormatter());
            loggerSet.addHandler(h);

            // log when it dies horribly
            final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread paramThread, Throwable ex) {
                    for (String line : Log.getStackTraceString(ex).split("\\R+")) {
                        loggerSet.info(line);
                    }
                    // call Android default handler
                    uncaughtExceptionHandler.uncaughtException(paramThread, ex);
                }
            });
        } catch (Exception e) {
            String msg = "Unable to initialize logging\n" + Log.getStackTraceString(e);
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
            Log.e("garagedoor", msg);
        }
    }

    // figure out where info was logged
    static String whereami() {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        String c = ste.getClassName();
        c = c.substring(c.lastIndexOf(".") + 1);
        String m = ste.getMethodName();
        return c + ":" + m + ": ";
    }

    // log to our own file so that messages don't get lost
    static void log(String msg) {
        loggerSet.info(whereami() + msg);
    }

    // log exceptions so everyone sees them
    // split lines so every one gets a timestamp and is sortable
    static void logExcept(Exception e) {
        for (String line : (whereami() + Log.getStackTraceString(e)).split("\\R+")) {
            loggerSet.info(line);
        }
    }

    // close logging file handlers to get rid of "lck" turdlets
    static void stopLogging() {
        for (Handler h : loggerSet.getHandlers()) {
            h.close();
        }
    }

    // sleep w/o the stupid useless exception crap
    static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (Exception e) {
        }
    }
}
