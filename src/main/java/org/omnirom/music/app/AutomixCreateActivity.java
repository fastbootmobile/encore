package org.omnirom.music.app;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.GeneralCatalog;

import org.omnirom.music.api.echonest.EchoNest;


public class AutomixCreateActivity extends PreferenceActivity {

    private static final String TAG = "AutomixCreateActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.automix_create);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.automix, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.menu_create) {
            new Thread() {
                public void run() {
                    EchoNest echoNest = new EchoNest();
                    try {
                        // Generate a taste profile
                        GeneralCatalog profile = echoNest.createTemporaryTasteProfile();
                        Log.e(TAG, "Taste profile reference: " + profile.getName());
                        //echoNest.createDynamicPlaylist(getSharedPreferences("AUTOMIX", 0));
                        profile.delete();
                    } catch (EchoNestException e) {
                        Log.e(TAG, "EchoNestException!", e);
                    }
                }
            }.start();

        }
        return super.onOptionsItemSelected(item);
    }
}
