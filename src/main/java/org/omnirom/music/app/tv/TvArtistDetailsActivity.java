package org.omnirom.music.app.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.omnirom.music.app.R;

public class TvArtistDetailsActivity extends Activity {
    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_COLOR = "color";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_artist_details);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(this, TvSearchActivity.class));
        return true;
    }
}
