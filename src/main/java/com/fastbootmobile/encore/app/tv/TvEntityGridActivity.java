package com.fastbootmobile.encore.app.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.fastbootmobile.encore.app.R;

public class TvEntityGridActivity extends Activity {
    public static final String EXTRA_MODE = "mode";

    public static final int MODE_ALBUM = 1;
    public static final int MODE_ARTIST = 2;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_album_list);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(this, TvSearchActivity.class));
        return true;
    }
}