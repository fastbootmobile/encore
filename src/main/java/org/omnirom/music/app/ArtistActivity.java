package org.omnirom.music.app;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;

public class ArtistActivity extends Activity {

    private static final String TAG = "ArtistActivity";

    public static final String EXTRA_ARTIST_NAME = "artist_name";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);
        if (savedInstanceState == null) {
            Bitmap hero = Utils.dequeueBitmap();

            PlaceholderFragment fragment = new PlaceholderFragment();
            fragment.setArguments(hero, getIntent());

            getFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }

        // Remove the activity title as we don't want it here
        getActionBar().setTitle("");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.artist, menu);
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
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        private Bitmap mHeroImage;
        private int mBackgroundColor;
        private String mArtistName;

        public PlaceholderFragment() {

        }

        public void setArguments(Bitmap hero, Intent intent) {
            mHeroImage = hero;
            mBackgroundColor = intent.getIntExtra(EXTRA_BACKGROUND_COLOR, 0xFF333333);
            mArtistName = intent.getStringExtra(EXTRA_ARTIST_NAME);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_artist, container, false);

            ImageView heroImage = (ImageView) rootView.findViewById(R.id.ivHero);
            heroImage.setImageBitmap(mHeroImage);

            TextView tvArtist = (TextView) rootView.findViewById(R.id.tvArtist);
            tvArtist.setBackgroundColor(mBackgroundColor);
            tvArtist.setText(mArtistName);

            return rootView;
        }
    }
}
