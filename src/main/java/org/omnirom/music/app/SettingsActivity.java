package org.omnirom.music.app;

import android.app.Activity;
import android.support.v4.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;

import org.omnirom.music.app.fragments.SettingsFragment;

public class SettingsActivity extends FragmentActivity {

    private static final String TAG = "SettingsActivity";
    public static final String TAG_FRAGMENT = "fragment_inner";

    private SettingsFragment mActiveFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (SettingsFragment) fm.findFragmentByTag(TAG_FRAGMENT);

        if (mActiveFragment == null) {
            mActiveFragment = new SettingsFragment();
            fm.beginTransaction()
                    .add(R.id.container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }

        // Remove the activity title as we don't want it here
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.settings, menu);
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
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                //finishAfterTransition();
            } else {
                finish();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
