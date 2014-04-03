package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnirom.music.api.common.HttpGet;
import org.omnirom.music.api.musicbrainz.AlbumInfo;
import org.omnirom.music.api.musicbrainz.MusicBrainzClient;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.ui.BlurCache;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PlaylistAdapter extends BaseAdapter {

    private static final String TAG = "PlaylistAdapter";

    private List<Song> mSongs;
    private Handler mHandler;
    private int mItemWidth;
    private int mItemHeight;

    // Using an AsyncTask to load the slow images in a background thread
    private class BackgroundAsyncTask extends AsyncTask<ViewHolder, Void, Bitmap> {
        private ViewHolder v;
        private int position;

        public BackgroundAsyncTask(int position) {
            this.position = position;
        }

        @Override
        protected Bitmap doInBackground(ViewHolder... params) {
            v = params[0];

            if (v.position != this.position) {
                // Cancel, we moved
                Log.e(TAG, "doInBackground cancelled, we moved");
                return null;
            }

            Log.e(TAG, "LOOKING FOR " + position);

            final Resources res = v.vRoot.getResources();
            final Context ctx = v.vRoot.getContext().getApplicationContext();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.test_cover_imagine_dragons);
            Bitmap bmp = drawable.getBitmap();

            Log.e(TAG, "Got default bitmap");

            // Download the art image
            String artKey = cache.getSongArtKey(v.song);

            if (artKey == null) {
                Artist artist = cache.getArtist(v.song.getArtist());
                if (artist != null) {
                    Log.e(TAG, "Got artist, querying MusicBrainz");
                    AlbumInfo albumInfo = MusicBrainzClient.getAlbum(artist.getName(), v.song.getTitle());
                    if (albumInfo != null) {
                        try {
                            artKey = MusicBrainzClient.getAlbumArtUrl(albumInfo.id);

                            if (artKey != null) {
                                byte[] imageData = HttpGet.getBytes(artKey, v.song.getTitle());
                                bmp = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                                cache.putSongArtKey(v.song, artKey);
                            } else {
                                Log.e(TAG, "No art key found for album id " + albumInfo.id);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Unable to get album art", e);
                        }
                    }
                }
            }

            if (v.position != this.position) {
                // Cancel, we moved
                Log.e(TAG, "doInBackground blurAndDim cancelled, we moved");
                return null;
            }

            // We blurAndDim our bitmap, if another executor didn't do it already
            Bitmap blur = BlurCache.getDefault().get(artKey);
            if (blur == null) {
                Bitmap thumb = ThumbnailUtils.extractThumbnail(bmp, mItemWidth, mItemHeight);
                blur = Utils.blurAndDim(ctx, thumb, 25);
                BlurCache.getDefault().put(artKey, blur);
            }

            return blur;

        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            if (v.position == position && result != null) {
                // If this item hasn't been recycled already, set and show the image
                // We do a smooth transition from a white/transparent background to our blurred one
                TransitionDrawable drawable = new TransitionDrawable(new Drawable[]{
                        v.vRoot.getBackground(),
                        new BitmapDrawable(v.vRoot.getResources(), result)
                });
                v.vRoot.setBackground(drawable);
                drawable.startTransition(200);
            }
        }
    }

    private static class ViewHolder {
        public TextView tvTitle;
        public TextView tvArtist;
        public TextView tvDuration;
        public View vRoot;
        public int position;
        public Song song;
    }

    public PlaylistAdapter(Context ctx) {
        mSongs = new ArrayList<Song>();
        mHandler = new Handler();

        final Resources res = ctx.getResources();
        assert res != null;

        // Theoretically, we'd need the width and height of the root view. However, this thread
        // might (and probably will) run before the view has been layout'd by the system, thus
        // getMeasuredXxxx() returns 0, which is invalid for the thumbnail we want. Instead,
        // we use the defined height dimension, and the screen width.
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        mItemWidth = size.x;
        mItemHeight = res.getDimensionPixelSize(R.dimen.playlist_view_item_height);
    }

    public void addItem(Song p) {
        mSongs.add(p);
        notifyDataSetChanged();
    }

    public boolean contains(Playlist p) {
        return mSongs.contains(p);
    }

    @Override
    public int getCount() {
        return mSongs.size();
    }

    @Override
    public Song getItem(int position) {
        return mSongs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mSongs.get(position).getRef().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.item_playlist_view, null);
            assert root != null;

            ViewHolder holder = new ViewHolder();
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvArtist = (TextView) root.findViewById(R.id.tvArtist);
            holder.tvDuration = (TextView) root.findViewById(R.id.tvDuration);
            holder.vRoot = root;

            root.setTag(holder);
        }

        final Song song = getItem(position);
        final ViewHolder tag = (ViewHolder) root.getTag();
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        // Update tag
        tag.position = position;
        tag.song = song;
        root.setTag(tag);

        // Fill fields
        if (song != null && song.isLoaded()) {
            tag.tvTitle.setText(song.getTitle());
            tag.tvDuration.setText(Utils.formatTrackLength(song.getDuration()));

            Artist artist = cache.getArtist(song.getArtist());
            if (artist != null) {
                tag.tvArtist.setText(artist.getName());
            } else {
                tag.tvArtist.setText("...");
            }
        } else {
            tag.tvTitle.setText("...");
            tag.tvDuration.setText("...");
            tag.tvArtist.setText("...");
        }

        // Fetch background art
        final String artKey = ProviderAggregator.getDefault().getCache().getSongArtKey(song);

        if (artKey != null) {
            Bitmap cachedBlur = BlurCache.getDefault().get(artKey);

            if (cachedBlur != null) {
                root.setBackground(new BitmapDrawable(root.getResources(), cachedBlur));
            } else {
                root.setBackground(root.getResources().getDrawable(R.drawable.ab_background_textured_appnavbar));
                new BackgroundAsyncTask(position).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
            }
        } else {
            root.setBackground(root.getResources().getDrawable(R.drawable.ab_background_textured_appnavbar));
            new BackgroundAsyncTask(position).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
        }

/*
          //////////
         // TEST //
        //////////
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Play the first song of the playlist
                String songRef = playlist.songs().next();
                Song song = ProviderAggregator.getDefault().getCache().getSong(songRef);

                try {
                    PluginsLookup.getDefault().getPlaybackService().playSong(song);
                } catch (RemoteException e) {
                    Log.e("TEST", "Unable to play song", e);
                } catch (NullPointerException e) {
                    Log.e("TEST", "SERVICE IS NOT BOUND?!");
                    PluginsLookup.getDefault().connectPlayback();
                }
            }
        });
*/

            return root;
        }

    }
