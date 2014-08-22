package org.omnirom.music.app.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import android.support.v4.app.FragmentActivity;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.BlurCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderIdentifier;
import org.omnirom.music.service.IPlaybackService;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PlaylistAdapter extends BaseAdapter {

    private static final String TAG = "PlaylistAdapter";

    private List<Song> mSongs;
    private List<Integer> mVisible;
    private List<Integer> mIds;
    private int mItemWidth;
    private int mItemHeight;
    private Playlist mPlaylist;

    private View.OnClickListener mOverflowClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final ViewHolder tag = (ViewHolder) v.getTag();
            final Context context = tag.vRoot.getContext();
            Utils.showSongOverflow((FragmentActivity) context, tag.ivOverflow, tag.song);
        }
    };

    public void setPlaylist(Playlist playlist) {
        mPlaylist = playlist;
    }

    // Using an AsyncTask to load the slow images in a background thread
    private class BackgroundAsyncTask extends AsyncTask<ViewHolder, Void, BitmapDrawable> {
        private ViewHolder v;
        private int mPosition;
        private Song mSong;

        public BackgroundAsyncTask(int position) {
            mPosition = position;
        }

        @Override
        protected BitmapDrawable doInBackground(ViewHolder... params) {
            v = params[0];
            mSong = v.song;

            if (v.position != this.mPosition || mSong == null) {
                // Cancel, we moved
                return null;
            }

            final Resources res = v.vRoot.getResources();
            final Context ctx = v.vRoot.getContext().getApplicationContext();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_list_default_bg);
            assert drawable != null;
            drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            Bitmap bmp = drawable.getBitmap();

            // Download the art image
            String artKey = cache.getSongArtKey(mSong);
            String artUrl = null;

            if (artKey == null) {
                StringBuffer urlBuffer = new StringBuffer();
                artKey = AlbumArtCache.getDefault().getArtKey(mSong, urlBuffer);
                artUrl = urlBuffer.toString();
            }

            if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
            }

            if (v.position != this.mPosition) {
                // Cancel, we moved
                return null;
            }

            // We blurAndDim our bitmap, if another executor didn't do it already and if it's not
            // the default art
            BitmapDrawable output;
            if (artKey == null || artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                artKey = AlbumArtCache.DEFAULT_ART;
                output = drawable;
            } else {
                Bitmap blur = BlurCache.getDefault().get(artKey);

                if (blur == null) {
                    Bitmap thumb = ThumbnailUtils.extractThumbnail(bmp, mItemWidth, mItemHeight);

                    if (v.position != this.mPosition) {
                        // Cancel, we moved
                        return null;
                    }

                    blur = Utils.blurAndDim(ctx, thumb, 25);

                    if (v.position != this.mPosition) {
                        // Cancel, we moved
                        return null;
                    }

                    BlurCache.getDefault().put(artKey, blur, true);
                }

                if (v.position != this.mPosition) {
                    // Cancel, we moved
                    return null;
                }

                output = new BitmapDrawable(res, blur);
            }

            cache.putSongArtKey(mSong, artKey);

            return output;
        }

        @Override
        protected void onPostExecute(BitmapDrawable result) {
            super.onPostExecute(result);

            if (v.position == mPosition && v.song == mSong && result != null) {
                // If this item hasn't been recycled already, set and show the image
                // We do a smooth transition from a white/transparent background to our blurred one
               /* TransitionDrawable drawable = new TransitionDrawable(new Drawable[]{
                        v.vRoot.getBackground(),
                        result
                });
*/
                Utils.setViewBackground(v.vRoot, result);
                //drawable.startTransition(200);
            }
        }
    }

    //Calls the proper handler to update the playlist order
    public void updatePlaylist(int oldPosition, int newPosition) {
        ProviderIdentifier providerIdentifier = ProviderAggregator.getDefault().getCache().getRefProvider(mPlaylist.getRef());
        try {
            PluginsLookup.getDefault().getProvider(providerIdentifier).getBinder().onUserSwapPlaylistItem(oldPosition, newPosition, mPlaylist.getRef());
            Log.d(TAG, "swaping " + oldPosition + " and " + newPosition);
            //// resetIds();
        } catch (RemoteException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    public int getSize() {
        return mSongs.size();
    }

    public void delete(int id) {
        ProviderIdentifier providerIdentifier = ProviderAggregator.getDefault().getCache().getRefProvider(mPlaylist.getRef());
        try {
            PluginsLookup.getDefault().getProvider(providerIdentifier).getBinder().deleteSongFromPlaylist(id, mPlaylist.getRef());
            mSongs.remove(id);
            mIds.remove(id);
            mVisible.remove(id);
            resetIds();
        } catch (RemoteException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    private void resetIds() {
        for (int i = 0; i < mIds.size(); i++) {
            mIds.set(i, i);
        }
    }

    //Swaps two elements and their properties
    public void swap(int original, int newPosition) {
        Song temp = mSongs.get(original);
        mSongs.set(original, mSongs.get(newPosition));
        mSongs.set(newPosition, temp);
        int tempVis = mVisible.get(original);
        mVisible.set(original, mVisible.get(newPosition));
        mVisible.set(newPosition, tempVis);
        int tempId = mIds.get(original);
        mIds.set(original, mIds.get(newPosition));
        mIds.set(newPosition, tempId);
    }

    //Sets the visibility of a selected element to visibility
    //Save the visibility to remember when the view is recycled
    public void setVisibility(int position, int visibility) {
        if (position >= 0 && position < mVisible.size()) {
            Log.d(TAG, position + " visibility " + visibility);
            mVisible.set(position, visibility);
        }
    }

    private static class ViewHolder {
        public TextView tvTitle;
        public TextView tvArtist;
        public TextView tvDuration;
        public ImageView ivOverflow;
        public View vCurrentIndicator;
        public View vRoot;
        public int position;
        public Song song;
        public boolean songWasLoaded;
    }

    public PlaylistAdapter(Context ctx) {
        mSongs = new ArrayList<Song>();
        mVisible = new ArrayList<Integer>();
        mIds = new ArrayList<Integer>();
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
        mVisible.add(View.VISIBLE);
        mIds.add(mSongs.size() - 1);
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
        if (position >= 0 && position < mIds.size())
            return mIds.get(position);
        return -1;
    }

    @Override
    public View getView(int position, View convertView, final ViewGroup parent) {
        Context ctx = parent.getContext();
        assert ctx != null;

        final Song song = getItem(position);

        View root = convertView;
        if (convertView == null) {
            // Recycle the existing view
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            root = inflater.inflate(R.layout.item_playlist_view, parent, false);
            assert root != null;

            final ViewHolder holder = new ViewHolder();
            holder.tvTitle = (TextView) root.findViewById(R.id.tvTitle);
            holder.tvArtist = (TextView) root.findViewById(R.id.tvArtist);
            holder.tvDuration = (TextView) root.findViewById(R.id.tvDuration);
            holder.ivOverflow = (ImageView) root.findViewById(R.id.ivOverflow);
            holder.vCurrentIndicator = root.findViewById(R.id.currentSongIndicator);
            holder.vRoot = root;
            holder.songWasLoaded = false;

            holder.tvArtist.setTextColor(0xFFFFFFFF);
            holder.tvTitle.setTextColor(0xFFFFFFFF);
            holder.tvDuration.setTextColor(0xFFFFFFFF);
            holder.ivOverflow.setImageResource(R.drawable.ic_more_vert_light);

            holder.ivOverflow.setOnClickListener(mOverflowClickListener);
            holder.ivOverflow.setTag(holder);

            root.setTag(holder);
        }

        final ViewHolder tag = (ViewHolder) root.getTag();
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();

        boolean change = false;

        // Update tag
        tag.position = position;
        if (tag.song == null || !tag.song.equals(song)) {
            tag.song = song;
            tag.songWasLoaded = false;
            change = true;
        }
        root.setTag(tag);

        // Fill fields
        if (song != null) {
            if (song.isLoaded() && !tag.songWasLoaded) {
                tag.tvTitle.setText(song.getTitle());
                tag.tvDuration.setText(Utils.formatTrackLength(song.getDuration()));

                Artist artist = cache.getArtist(song.getArtist());
                if (artist != null) {
                    tag.tvArtist.setText(artist.getName());
                } else {
                    tag.tvArtist.setText("...");
                }

                tag.songWasLoaded = true;
                change = true;
            } else if (!song.isLoaded()) {
                tag.songWasLoaded = song.isLoaded();
                tag.tvTitle.setText("...");
                tag.tvDuration.setText("...");
                tag.tvArtist.setText("...");
            }
        } else {
            tag.tvTitle.setText("...");
            tag.tvDuration.setText("...");
            tag.tvArtist.setText("...");
        }

        // Set current song indicator
        tag.vCurrentIndicator.setVisibility(View.INVISIBLE);

        final IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
        try {
            List<Song> playbackQueue = pbService.getCurrentPlaybackQueue();
            if (playbackQueue.size() > 0 && playbackQueue.get(0).equals(tag.song)) {
                tag.vCurrentIndicator.setVisibility(View.VISIBLE);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot retrieve playback queue");
        }

        // Fetch background art
        if (change) {
            final String artKey = ProviderAggregator.getDefault().getCache().getSongArtKey(song);

            final Resources res = root.getResources();
            assert res != null;

            if (artKey != null) {
                // We already know the album art for this song (keyed in artKey)
                Bitmap cachedBlur = BlurCache.getDefault().get(artKey);

                if (cachedBlur != null) {
                    Utils.setViewBackground(root, new BitmapDrawable(root.getResources(), cachedBlur));
                } else {
                    Utils.setViewBackground(root, res.getDrawable(R.drawable.album_list_default_bg));
                    BackgroundAsyncTask task = new BackgroundAsyncTask(position);
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
                    //task.execute(tag);
                }
            } else {
                Utils.setViewBackground(root, res.getDrawable(R.drawable.album_list_default_bg));
                BackgroundAsyncTask task = new BackgroundAsyncTask(position);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
                //task.execute(tag);
            }
        }

        root.setVisibility(mVisible.get(position));
        return root;
    }

}
