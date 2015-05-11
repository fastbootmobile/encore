package org.omnirom.music.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import org.omnirom.music.app.fragments.WelcomeFragment;


public class WelcomeActivity extends AppActivity {

    private boolean mHasFirstFragment = false;

    private static final String PREFS_WELCOME = "welcome";
    private static final String KEY_DONE = "done";

    public static boolean hasDoneWelcomeWizard(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_WELCOME, 0);
        return prefs.getBoolean(KEY_DONE, false);
    }

    public static void setDoneWelcomeWizard(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_WELCOME, 0);
        prefs.edit().putBoolean(KEY_DONE, true).apply();
    }

    public static void resetWelcomeWizard(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_WELCOME, 0);
        prefs.edit().putBoolean(KEY_DONE, false).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Create initial step 1 fragment
        showStep(1);
    }

    public void showStep(int step) {
        // Create the fragment
        WelcomeFragment frag = WelcomeFragment.newInstance(step);

        // Add it to the fragment stack
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left,
                R.anim.slide_from_left, R.anim.slide_to_right);
        if (mHasFirstFragment) {
            ft.addToBackStack(null);
        } else {
            mHasFirstFragment = true;
        }
        ft.replace(R.id.container, frag, "F" + step);
        ft.commit();

        if (step == 2) {
            setDoneWelcomeWizard(this);
        }
    }
}
