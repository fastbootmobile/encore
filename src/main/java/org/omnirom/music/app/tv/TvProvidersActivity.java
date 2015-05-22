package org.omnirom.music.app.tv;

import android.app.Activity;
import android.os.Bundle;

import org.omnirom.music.app.R;

public class TvProvidersActivity extends Activity {
    public static final String EXTRA_DSP_MODE = "dsp_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_providers_grid);
    }
}
