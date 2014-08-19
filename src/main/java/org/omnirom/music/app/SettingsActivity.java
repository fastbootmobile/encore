package org.omnirom.music.app;

import android.app.ActionBar;
import android.app.Activity;
import android.support.v4.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.balysv.material.drawable.menu.MaterialMenuDrawable;
import com.balysv.material.drawable.menu.MaterialMenuView;

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

        // Setup L-style action bar
        ActionBar actionBar = getActionBar();
        assert actionBar != null;
        actionBar.setHomeButtonEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.action_bar);
        MaterialMenuView toggle = (MaterialMenuView) actionBar.getCustomView().findViewById(R.id.action_bar_menu);
        toggle.setState(MaterialMenuDrawable.IconState.CHECK);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    //finishAfterTransition();
                } else {
                    finish();
                }
            }
        });
    }
}
