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
import java.util.Iterator;
import java.util.List;

/**
 *
 */
public class PlaylistAdapter extends SongsListAdapter {

    private static final String TAG = "PlaylistAdapter";

    private List<Integer> mVisible;
    private List<Integer> mIds;
    private Playlist mPlaylist;

    @Override
    public void notifyDataSetChanged() {
        mSongs.clear();
        Iterator<String> it = mPlaylist.songs();
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        while (it.hasNext()) {
            put(aggregator.retrieveSong(it.next(), mPlaylist.getProvider()));
        }
        super.notifyDataSetChanged();
    }

    public void setPlaylist(Playlist playlist) {
        mPlaylist = playlist;
        notifyDataSetChanged();
    }

    //Calls the proper handler to update the playlist order
    public void updatePlaylist(int oldPosition, int newPosition) {
        ProviderIdentifier providerIdentifier = mPlaylist.getProvider();
        try {
            PluginsLookup.getDefault().getProvider(providerIdentifier).getBinder().onUserSwapPlaylistItem(oldPosition, newPosition, mPlaylist.getRef());
            Log.d(TAG, "swaping " + oldPosition + " and " + newPosition);
            //// resetIds();
        } catch (RemoteException e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    public void delete(int id) {
        ProviderIdentifier providerIdentifier = mPlaylist.getProvider();
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
    public PlaylistAdapter(Context ctx) {
        super(ctx, true);
        mVisible = new ArrayList<Integer>();
        mIds = new ArrayList<Integer>();
    }

    @Override
    public void put(Song p) {
        super.put(p);

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
        View root = super.getView(position, convertView, parent);
        root.setVisibility(mVisible.get(position));
        return root;
    }

}
