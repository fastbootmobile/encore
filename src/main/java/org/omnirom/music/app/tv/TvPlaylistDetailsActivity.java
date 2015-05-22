package org.omnirom.music.app.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.omnirom.music.app.R;

public class TvPlaylistDetailsActivity extends Activity {
    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String EXTRA_PLAYLIST = "playlist";
    public static final String EXTRA_COLOR = "color";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_playlist_details);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(this, TvSearchActivity.class));
        return true;
    }
}
