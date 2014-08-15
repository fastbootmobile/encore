package org.omnirom.music.app;

import android.app.Activity;
import android.app.ActionBar;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.ui.AlbumArtImageView;
import org.omnirom.music.app.ui.MaterialTransitionDrawable;
import org.omnirom.music.app.ui.PlayPauseDrawable;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.service.IPlaybackService;

import java.util.ArrayList;
import java.util.List;

public class PlaybackQueueActivity extends FragmentActivity {

    private static final String TAG = "PlaybackQueueActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback_queue);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new SimpleFragment())
                    .commit();
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.playback_queue, menu);
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
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                // finishAfterTransition();
            } else {
                finish();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Simple fragment for the activity contents
     */
    public static class SimpleFragment extends Fragment {

        private View.OnClickListener mArtClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bitmap hero = ((MaterialTransitionDrawable) ((ImageView) view).getDrawable()).getFinalDrawable().getBitmap();
                Palette palette = Palette.generate(hero);

                PaletteItem darkVibrantColor = palette.getDarkVibrantColor();
                PaletteItem darkMutedColor = palette.getDarkMutedColor();

                int color;
                if (darkVibrantColor != null) {
                    color = darkVibrantColor.getRgb();
                } else if (darkMutedColor != null) {
                    color = darkMutedColor.getRgb();
                } else {
                    color = getResources().getColor(R.color.default_album_art_background);
                }

                ProviderCache cache = ProviderAggregator.getDefault().getCache();
                Song song = (Song) view.getTag();

                Intent intent = AlbumActivity.craftIntent(getActivity(), hero,
                        cache.getAlbum(song.getAlbum()), color);

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    /* ActivityOptions opt = ActivityOptions.makeSceneTransitionAnimation(getActivity(),
                            view, "itemImage");

                    getActivity().startActivity(intent, opt.toBundle()); */
                } else {
                    getActivity().startActivity(intent);
                }
            }
        };

        public SimpleFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_playback_queue, container, false);
            ViewGroup tracksContainer = (ViewGroup) rootView.findViewById(R.id.playingTracksLayout);

            // Set play pause drawable
            ImageView ivPlayPause = (ImageView) rootView.findViewById(R.id.ivPlayPause);
            PlayPauseDrawable playDrawable = new PlayPauseDrawable(getResources());
            playDrawable.setColor(0xAA333333);
            playDrawable.setShape(PlayPauseDrawable.SHAPE_PLAY);
            ivPlayPause.setImageDrawable(playDrawable);

            final IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            try {
                if (playbackService.isPlaying() && !playbackService.isPaused()) {
                    playDrawable.setShape(PlayPauseDrawable.SHAPE_PAUSE);
                }
            } catch (RemoteException e) {
                // ignore
            }

            // Load the current playing track
            try {
                Song currentTrack = playbackService.getCurrentTrack();

                TextView tvCurrentTitle = (TextView) rootView.findViewById(R.id.tvCurrentTitle);
                TextView tvCurrentArtist = (TextView) rootView.findViewById(R.id.tvCurrentArtist);
                AlbumArtImageView ivCurrentPlayAlbumArt = (AlbumArtImageView) rootView.findViewById(R.id.ivCurrentPlayAlbumArt);

                tvCurrentTitle.setText(currentTrack.getTitle());
                tvCurrentArtist.setText(cache.getArtist(currentTrack.getArtist()).getName());
                ivCurrentPlayAlbumArt.loadArtForSong(currentTrack);
            } catch (RemoteException e) {
                // ignore, if the playback service is disconnected we're not playing anything
            }


            // Load and inflate the playback queue
            List<Song> songs;
            try {
                // We make a copy
                songs = new ArrayList<Song>(playbackService.getCurrentPlaybackQueue());
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to get current playback queue", e);
                return rootView;
            }

            // We remove the first song as it's the currently playing song we displayed above
            if (songs.size() > 0) {
                songs.remove(0);

                int i = 1;
                for (Song song : songs) {
                    View itemView = inflater.inflate(R.layout.item_playbar, tracksContainer, false);
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        // itemView.setViewName("playbackqueue:" + i);
                    }
                    TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle),
                            tvArtist = (TextView) itemView.findViewById(R.id.tvArtist);
                    AlbumArtImageView ivCover = (AlbumArtImageView) itemView.findViewById(R.id.ivAlbumArt);

                    Artist artist = cache.getArtist(song.getArtist());

                    tvTitle.setText(song.getTitle());
                    tvArtist.setText(artist.getName());
                    ivCover.loadArtForSong(song);

                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        // ivCover.setViewName("playbackqueue:" + i + ":cover:" + song.getRef());
                    }

                    ivCover.setTag(song);
                    ivCover.setOnClickListener(mArtClickListener);

                    tracksContainer.addView(itemView);
                    i++;
                }
            }

            return rootView;
        }
    }
}
