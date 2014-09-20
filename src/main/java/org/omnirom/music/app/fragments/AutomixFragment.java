package org.omnirom.music.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.app.AutomixCreateActivity;
import org.omnirom.music.app.MainActivity;
import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.app.adapters.BucketAdapter;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Song;
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.IPlaybackService;

/**
 * Created by Guigui on 11/08/2014.
 */
public class AutomixFragment extends Fragment {

    private static final String TAG = "AutomixFragment";

    private ListView mListView;
    private BucketAdapter mAdapter;
    private ImageButton mFabCreate;
    private ProgressBar mProgressToHide;
    private AutoMixManager mAutoMixManager = AutoMixManager.getDefault();
    private IPlaybackCallback.Stub mPlaybackCallback = new IPlaybackCallback.Stub() {
        @Override
        public void onSongStarted(boolean buffering, Song s) throws RemoteException {
            if (mProgressToHide != null) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressToHide.setVisibility(View.INVISIBLE);
                        mProgressToHide = null;
                    }
                });
            }
        }

        @Override
        public void onSongScrobble(int timeMs) throws RemoteException {}

        @Override
        public void onPlaybackPause() throws RemoteException {}

        @Override
        public void onPlaybackResume() throws RemoteException {}

        @Override
        public void onPlaybackQueueChanged() throws RemoteException {}
    };

    public AutomixFragment() {

    }

    public static AutomixFragment newInstance() {
        return new AutomixFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_automix, container, false);

        mFabCreate = (ImageButton) rootView.findViewById(R.id.fabCreate);
        mFabCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getActivity(), AutomixCreateActivity.class));
            }
        });
        Utils.setLargeFabOutline(new View[]{mFabCreate});
        Utils.setupLargeFabShadow(mFabCreate);

        mListView = (ListView) rootView.findViewById(R.id.lvBuckets);
        mAdapter = new BucketAdapter();
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                BucketAdapter.ViewHolder holder = (BucketAdapter.ViewHolder) view.getTag();
                holder.pbBucketSpinner.setVisibility(View.VISIBLE);

                if (mProgressToHide != null) {
                    mProgressToHide.setVisibility(View.INVISIBLE);
                }

                mProgressToHide = holder.pbBucketSpinner;

                new Thread() {
                    public void run() {
                        mAutoMixManager.startPlay(mAdapter.getItem(i));
                    }
                }.start();
            }
        });

        IPlaybackService pbService = PluginsLookup.getDefault().getPlaybackService();
        try {
            pbService.addCallback(mPlaybackCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot register as a playback callback");
        }

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        MainActivity mainActivity = (MainActivity) activity;
        mainActivity.onSectionAttached(MainActivity.SECTION_AUTOMIX);
        mainActivity.setContentShadowTop(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBuckets();
    }

    private void updateBuckets() {
        mAdapter.setBuckets(mAutoMixManager.getBuckets());
        mAdapter.notifyDataSetChanged();

        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity.isPlayBarVisible()) {
            mFabCreate.animate()
                    .translationY(-getResources().getDimensionPixelSize(R.dimen.playing_bar_height))
                    .start();
        } else {
            mFabCreate.animate()
                    .translationY(0)
                    .start();
        }
    }
}
