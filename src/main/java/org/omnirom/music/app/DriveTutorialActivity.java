package org.omnirom.music.app;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;


public class DriveTutorialActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drive_tutorial);
    }

    public void onClickFinish(View v) {
        finish();
    }
}
