package org.genecash.garagedoor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class CloseApp extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.close);

        // stop background service process
        sendBroadcast(new Intent(Utilities.ACTION_STOP));
        finish();
    }
}
