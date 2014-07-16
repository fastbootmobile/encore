package org.omnirom.music.app.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.omnirom.music.app.R;
import org.omnirom.music.app.adapters.SongsListAdapter;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.providers.ProviderIdentifier;

import java.security.Provider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by h4o on 26/06/2014.
 */
public class AlbumViewFragment extends  AbstractRootFragment {
    private static final String KEY_ALBUM ="album";
    private SongsListAdapter mAdapter;
    private Album mAlbum;
    private int mScrollState;
    private String TAG = "AlbumViewFragment";
    public static AlbumViewFragment newInstance(Album album){
        AlbumViewFragment albumViewFragment = new AlbumViewFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_ALBUM,album);
        albumViewFragment.setArguments(bundle);
        return albumViewFragment;
    }

    @Override
    public void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        Bundle args = getArguments();
        if(args == null ){
            throw new IllegalArgumentException("This fragment must have a valid album");
        }
        mAlbum = args.getParcelable(KEY_ALBUM);
    }
    private static class ViewHolder {
        public Album album;
        public View vRoot;

    }

    private class BackgroundAsyncTask extends AsyncTask<ViewHolder, Void, BitmapDrawable> {
        private ViewHolder v;

        private Album mAlbum;

        public BackgroundAsyncTask() {

        }

        @Override
        protected BitmapDrawable doInBackground(ViewHolder... params) {
            v = params[0];
            mAlbum = v.album;
            //  Log.e("AlbumAdapter","Fetching an image");


            final Resources res = v.vRoot.getResources();
            final Context ctx = v.vRoot.getContext().getApplicationContext();
            assert res != null;

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_placeholder);
            assert drawable != null;
            drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);

            Bitmap bmp = drawable.getBitmap();

            // Download the art image
            if(mAlbum == null)
                Log.e("AlbumAdapter", "null album !");
            String artKey = cache.getAlbumArtKey(mAlbum);
            String artUrl = null;

            if (artKey == null) {
                StringBuffer urlBuffer = new StringBuffer();
                artKey = AlbumArtCache.getDefault().getArtKey(mAlbum, urlBuffer);
                artUrl = urlBuffer.toString();
            }


            BitmapDrawable output = new BitmapDrawable(res, bmp);


            cache.putAlbumArtKey(mAlbum, artKey);

            return output;
        }

        @Override
        protected void onPostExecute(BitmapDrawable result) {
            super.onPostExecute(result);

            if  ( result != null) {

                v.vRoot.setBackground(result);
            } else if( result != null){
                Log.e(TAG, "we have a result too late...");
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_album_view, container, false);
        assert root != null;
        RelativeLayout relativeLayout = (RelativeLayout) root.findViewById(R.id.rlAlbum);
        TextView albumName = (TextView) relativeLayout.findViewById(R.id.tvAlbumName);
        albumName.setText(mAlbum.getName());
        ViewHolder tag = new ViewHolder();
        tag.vRoot = relativeLayout;
        tag.album = mAlbum;
        final String artKey = ProviderAggregator.getDefault().getCache().getAlbumArtKey(mAlbum);

        final Resources res = root.getResources();
        assert res != null;

        if (artKey != null) {
            Log.e(TAG, "we have an art key " +artKey + " album: "+mAlbum.getName());
            // We already know the album art for this song (keyed in artKey)
            Bitmap cachedImage = ImageCache.getDefault().get(artKey);
            if (cachedImage != null) {
                Log.e(TAG,"There is a cached image for album "+mAlbum.getName());
                tag.vRoot.setBackground(new BitmapDrawable(root.getResources(), cachedImage));
            } else {

                BackgroundAsyncTask task = new BackgroundAsyncTask();
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
                //task.execute(tag);
            }
        } else {
            BackgroundAsyncTask task = new BackgroundAsyncTask();
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, tag);
            //task.execute(tag);
        }

        ListView listView =  (ListView) root.findViewById(R.id.lvPlaylistContents);
        mAdapter = new SongsListAdapter(root.getContext());
        listView.setAdapter(mAdapter);

        ProviderCache cache = ProviderAggregator.getDefault().getCache();
        Iterator<String> songs = mAlbum.songs();
        while (songs.hasNext()){
            String songRef = songs.next();
            Song song = cache.getSong(songRef);

            // If the song isn't loaded, try to get it from the provider, it might be loaded there
            // but not cached for various reasons. For instance, Spotify loads the albums tracks
            // info, but we're not tracking them in metadata_callback, so they're not actually
            // pushed to the app's cache
            if (song == null) {
                ProviderConnection prov = PluginsLookup.getDefault().getProvider(mAlbum.getProvider());
                try {
                    IMusicProvider binder = prov.getBinder();
                    if (binder != null) {
                        song = prov.getBinder().getSong(songRef);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote exception while trying to get track info", e);
                    continue;
                }

                if (song == null) {
                    // Song is still unknown, we skip!
                    continue;
                }
            }

            mAdapter.put(song);
        }
        mAdapter.notifyDataSetChanged();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // Play the song
                Song song = mAdapter.getItem(i);

                if (song != null) {
                    try {
                        PluginsLookup.getDefault().getPlaybackService().playSong(song);
                    } catch (RemoteException e) {
                        Log.e("TEST", "Unable to play song", e);
                    }
                } else {
                    Log.e(TAG, "Trying to play null song!");
                }
            }
        });
        return root;
    }


}
