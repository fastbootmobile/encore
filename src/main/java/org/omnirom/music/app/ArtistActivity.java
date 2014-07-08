package org.omnirom.music.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.transition.Transition;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.os.Build;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.R;

public class ArtistActivity extends Activity {

    private static final String TAG = "ArtistActivity";

    public static final String EXTRA_ARTIST_NAME = "artist_name";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";

    private PlaceholderFragment mActiveFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        if (savedInstanceState == null) {
            Bitmap hero = Utils.dequeueBitmap();

            mActiveFragment = new PlaceholderFragment();
            mActiveFragment.setArguments(hero, getIntent());

            getFragmentManager().beginTransaction()
                    .add(R.id.container, mActiveFragment)
                    .commit();
        }

        // Remove the activity title as we don't want it here
        getActionBar().setTitle("");
        getActionBar().setDisplayHomeAsUpEnabled(true);

        getWindow().getEnterTransition().addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {
                View fab = mActiveFragment.findViewById(R.id.fabPlay);
                fab.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                View fab = mActiveFragment.findViewById(R.id.fabPlay);
                fab.setVisibility(View.VISIBLE);

                // get the center for the clipping circle
                int cx = fab.getMeasuredWidth()/2;
                int cy = fab.getMeasuredHeight()/2;

                // get the final radius for the clipping circle
                final int finalRadius = fab.getWidth();

                // create and start the animator for this view
                // (the start radius is zero)
                ValueAnimator anim =
                        ViewAnimationUtils.createCircularReveal(fab, cx, cy, 0, finalRadius);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.start();

                fab.setTranslationX(-fab.getMeasuredWidth() / 4.0f);
                fab.setTranslationY(-fab.getMeasuredHeight() / 4.0f);
                fab.animate().translationX(0.0f).translationY(0.0f)
                        .setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                getWindow().getEnterTransition().removeListener(this);
            }

            @Override
            public void onTransitionCancel(Transition transition) {

            }

            @Override
            public void onTransitionPause(Transition transition) {

            }

            @Override
            public void onTransitionResume(Transition transition) {

            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.e("XPLOD", "ON BACK PRESSED");
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
        } else if (id == android.R.id.home) {
            finishAfterTransition();
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
        private View mRootView;
        private Palette mPalette;
        private Handler mHandler;

        public PlaceholderFragment() {

        }

        public View findViewById(int id) {
            return mRootView.findViewById(id);
        }

        public void setArguments(Bitmap hero, Intent intent) {
            mHeroImage = hero;
            mBackgroundColor = intent.getIntExtra(EXTRA_BACKGROUND_COLOR, 0xFF333333);
            mArtistName = intent.getStringExtra(EXTRA_ARTIST_NAME);

            // Prepare the palette to colorize the FAB
            AsyncTask palette = Palette.generateAsync(hero, new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(final Palette palette) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            PaletteItem color = palette.getDarkMutedColor();
                            if (color != null && mRootView != null) {
                                RippleDrawable ripple = (RippleDrawable) mRootView.findViewById(R.id.fabPlay).getBackground();
                                GradientDrawable back = (GradientDrawable) ripple.getDrawable(0);
                                back.setColor(color.getRgb());
                            }
                        }
                    });
                }
            });
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            mHandler = new Handler();

            mRootView = inflater.inflate(R.layout.fragment_artist, container, false);

            ImageView heroImage = (ImageView) mRootView.findViewById(R.id.ivHero);
            heroImage.setImageBitmap(mHeroImage);

            TextView tvArtist = (TextView) mRootView.findViewById(R.id.tvArtist);
            tvArtist.setBackgroundColor(mBackgroundColor);
            tvArtist.setText(mArtistName);

            // Outline is required for the FAB shadow to be actually oval
            setOutlines(mRootView.findViewById(R.id.fabPlay));

            return mRootView;
        }

        private void setOutlines(View v) {
            int size = getResources().getDimensionPixelSize(R.dimen.floating_button_size);

            Outline outline = new Outline();
            outline.setOval(0, 0, size, size);

            v.setOutline(outline);
        }
    }
}
