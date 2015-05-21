package org.omnirom.music.app.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.omnirom.music.app.R;

public class TvSearchActivity extends Activity {

    private static final String TAG = "TvSearchActivity";
    private TvSearchFragment mFragment;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_search);

        mFragment = (TvSearchFragment) getFragmentManager().findFragmentById(R.id.search_fragment);
    }

    @Override
    public boolean onSearchRequested() {
        if (mFragment.hasResults()) {
            startActivity(new Intent(this, TvSearchActivity.class));
        } else {
            mFragment.startRecognition();
        }
        return true;
    }
}
