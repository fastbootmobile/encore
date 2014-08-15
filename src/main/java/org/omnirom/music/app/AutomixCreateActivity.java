package org.omnirom.music.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.GeneralCatalog;

import org.omnirom.music.api.echonest.AutoMixBucket;
import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.api.echonest.EchoNest;

import java.util.Set;


public class AutomixCreateActivity extends PreferenceActivity {

    private static final String TAG = "AutomixCreateActivity";

    private static final String KEY_NAME = "automix_bucket_name";
    private static final String KEY_STYLES = "automix_styles";
    private static final String KEY_MOODS = "automix_moods";
    private static final String KEY_TASTE = "automix_use_taste";
    private static final String KEY_ADVENTUROUS = "automix_adventurous";
    private static final String KEY_SONG_TYPES = "automix_song_types";
    private static final String KEY_SPEECHINESS = "automix_target_speechiness";
    private static final String KEY_ENERGY = "automix_target_energy";
    private static final String KEY_FAMILIAR = "automix_target_familiarity";

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
            createBucket();
        }
        return super.onOptionsItemSelected(item);
    }

    private void createBucket() {
        // TODO: Remove the progress dialog and show progress and status in the main Automix
        // fragment to avoid locking the user out of the app for multiple seconds
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(getString(R.string.bucket_creating_dialog));
        progressDialog.show();

        new Thread() {
            public void run() {
                AutoMixManager manager = new AutoMixManager(AutomixCreateActivity.this);

                String name = getPrefStringValue(KEY_NAME);
                String[] styles = getPrefStringArray(KEY_STYLES);
                String[] moods = getPrefStringArray(KEY_MOODS);
                boolean taste = getPrefBool(KEY_TASTE);
                float advent = getPrefFloat(KEY_ADVENTUROUS);
                String[] songtype = getPrefStringArray(KEY_SONG_TYPES);
                float speech = getPrefFloat(KEY_SPEECHINESS);
                float energy = getPrefFloat(KEY_ENERGY);
                float familiar = getPrefFloat(KEY_FAMILIAR);

                if (styles.length == 0 && moods.length == 0 && !taste) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Utils.shortToast(AutomixCreateActivity.this,
                                    R.string.automix_minimal_settings_error);
                        }
                    });
                } else {
                    Log.e(TAG, "Creating bucket...");
                    AutoMixBucket bucket = manager.createBucket(name, styles, moods, taste, advent,
                            songtype, speech, energy, familiar);
                    if (bucket.isPlaylistSessionError()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                Utils.shortToast(AutomixCreateActivity.this,
                                        R.string.bucket_settings_invalid);
                            }
                        });
                    } else {
                        Log.e(TAG, "Bucket created!");
                        manager.startPlay(bucket);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                finish();
                            }
                        });
                    }
                }
            }
        }.start();
    }

    private String getPrefStringValue(String key) {
        PreferenceManager p = getPreferenceManager();
        return ((EditTextPreference) p.findPreference(key)).getText();
    }

    private String[] getPrefStringArray(String key) {
        PreferenceManager p = getPreferenceManager();
        Set<String> stringSet = ((MultiSelectListPreference) p.findPreference(key)).getValues();
        return stringSet.toArray(new String[stringSet.size()]);
    }

    private float getPrefFloat(String key) {
        PreferenceManager p = getPreferenceManager();
        return Float.parseFloat(((ListPreference) p.findPreference(key)).getValue());
    }

    private boolean getPrefBool(String key) {
        PreferenceManager p = getPreferenceManager();
        return ((CheckBoxPreference) p.findPreference(key)).isChecked();
    }
}
