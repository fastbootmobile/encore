/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package com.fastbootmobile.encore.api.echonest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.echonest.api.v4.EchoNestException;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.utils.Utils;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.service.BasePlaybackCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages the AutoMix buckets and ensures AutoMix playback
 */
public class AutoMixManager extends BasePlaybackCallback {
    private static final String TAG = "AutoMixManager";

    private static final String SHARED_PREFS = "automix_buckets";
    private static final String PREF_BUCKETS_IDS = "buckets_ids";
    private static final String PREF_PREFIX_NAME = "bucket_name_";
    private static final String PREF_PREFIX_STYLES = "bucket_styles_";
    private static final String PREF_PREFIX_MOODS = "bucket_moods_";
    private static final String PREF_PREFIX_TASTE = "bucket_taste_";
    private static final String PREF_PREFIX_ADVENTUROUS = "bucket_adventurous_";
    private static final String PREF_PREFIX_SONG_TYPES = "bucket_song_types_";
    private static final String PREF_PREFIX_SPEECHINESS = "bucket_speechiness_";
    private static final String PREF_PREFIX_ENERGY = "bucket_energy_";
    private static final String PREF_PREFIX_FAMILIAR = "bucket_familiar_";

    private Context mContext;
    private List<AutoMixBucket> mBuckets;
    private AutoMixBucket mCurrentPlayingBucket;
    private Runnable mGetNextTrackRunnable;
    private Handler mHandler;
    private List<String> mActiveBucketRefHistory;

    private static final AutoMixManager INSTANCE = new AutoMixManager();

    private AutoMixManager() {
        mGetNextTrackRunnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 2; ++i) {
                    try {
                        // Fetch the next track
                        String nextTrackRef = mCurrentPlayingBucket.getNextTrack();

                        // Queue it
                        Song nextTrack = getSongFromRef(nextTrackRef);
                        if (nextTrack != null) {
                            // TODO: Check if track is available, to avoid queuing tracks that
                            // aren't available. The tricky part here is unloaded tracks (for Spotify
                            // at least) shows as not available until they're loaded. We'd need to
                            // add some callback handling too.
                            PlaybackProxy.queueSong(nextTrack, false);
                        }

                        mActiveBucketRefHistory.add(nextTrackRef);
                    } catch (EchoNestException e) {
                        Log.e(TAG, "Unable to get next track", e);
                    }
                }
            }
        };
    }

    public static AutoMixManager getDefault() {
        return INSTANCE;
    }

    /**
     * Initializes the AutoMix manager
     * @param ctx A valid application context
     */
    public void initialize(Context ctx) {
        mContext = ctx;
        mBuckets = new ArrayList<>();
        readBucketsFromPrefs();
        mHandler = new Handler();
        mActiveBucketRefHistory = new ArrayList<>();
    }

    /**
     * Returns the shared preferences
     * @return The SharedPreferences storing the buckets
     */
    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(SHARED_PREFS, 0);
    }

    /**
     * Restore the AutoMix buckets stored in SharedPreferences
     */
    public void readBucketsFromPrefs() {
        SharedPreferences prefs = mContext.getSharedPreferences(SHARED_PREFS, 0);
        Set<String> buckets = prefs.getStringSet(PREF_BUCKETS_IDS, new TreeSet<String>());

        mBuckets.clear();

        for (String bucketId : buckets) {
            AutoMixBucket bucket = restoreBucketFromId(bucketId);
            mBuckets.add(bucket);
        }
    }

    /**
     * Creates a new AutoMix Bucket with the provided settings
     * @param name The name of the bucket
     * @param styles The EchoNest styles to include in the bucket
     * @param moods The EchoNest moods to include in the bucket
     * @param taste Whether or not to use the user's taste profile
     * @param adventurous The target adventurousness level [0.0-1.0]
     * @param songTypes The EchoNest song types to include
     * @param speechiness The target speechiness level [0.0-1.0]
     * @param energy The target energy level [0.0-1.0]
     * @param familiar The target familiarity level [0.0-1.0]
     * @return The generated {@link com.fastbootmobile.encore.api.echonest.AutoMixBucket}
     */
    public AutoMixBucket createBucket(String name, String[] styles, String[] moods, boolean taste,
                                      float adventurous, String[] songTypes, float speechiness,
                                      float energy, float familiar) {

        AutoMixBucket bucket = new AutoMixBucket(name, styles, moods, taste, adventurous, songTypes,
                speechiness, energy, familiar);
        bucket.createPlaylistSession();
        mBuckets.add(bucket);
        saveBucket(bucket);
        return bucket;
    }

    /**
     * Creates a new AutoMix Bucket with the provided settings that will be used for a static
     * playlist instead of a dynamic bucket
     * @param name The name of the bucket
     * @param styles The EchoNest styles to include in the bucket
     * @param moods The EchoNest moods to include in the bucket
     * @param taste Whether or not to use the user's taste profile
     * @param adventurous The target adventurousness level [0.0-1.0]
     * @param songTypes The EchoNest song types to include
     * @param speechiness The target speechiness level [0.0-1.0]
     * @param energy The target energy level [0.0-1.0]
     * @param familiar The target familiarity level [0.0-1.0]
     * @return The generated {@link com.fastbootmobile.encore.api.echonest.AutoMixBucket}
     */
    public AutoMixBucket createStaticBucket(String name, String[] styles, String[] moods,
                                            boolean taste, float adventurous, String[] songTypes,
                                            float speechiness, float energy, float familiar) {

        return new AutoMixBucket(name, styles, moods, taste, adventurous, songTypes,
                speechiness, energy, familiar);
    }

    /**
     * Saves a bucket (ie. its parameters) to the internal storage
     * @param bucket The bucket to save
     */
    private void saveBucket(AutoMixBucket bucket) {
        if (!bucket.isPlaylistSessionError()) {
            SharedPreferences prefs = getPrefs();
            SharedPreferences.Editor editor = prefs.edit();
            final String id = bucket.getSessionId();

            editor.putString(PREF_PREFIX_NAME + id, bucket.mName);
            editor.putFloat(PREF_PREFIX_ADVENTUROUS + id, bucket.mAdventurousness);
            editor.putFloat(PREF_PREFIX_ENERGY + id, bucket.mEnergy);
            editor.putFloat(PREF_PREFIX_FAMILIAR + id, bucket.mFamiliar);
            editor.putString(PREF_PREFIX_MOODS + id, Utils.implode(bucket.mMoods, ","));
            editor.putString(PREF_PREFIX_SONG_TYPES + id, Utils.implode(bucket.mSongTypes, ","));
            editor.putFloat(PREF_PREFIX_SPEECHINESS + id, bucket.mSpeechiness);
            editor.putString(PREF_PREFIX_STYLES + id, Utils.implode(bucket.mStyles, ","));
            editor.putBoolean(PREF_PREFIX_TASTE + id, bucket.mUseTaste);

            Set<String> set = new TreeSet<>(prefs.getStringSet(PREF_BUCKETS_IDS, new TreeSet<String>()));
            set.add(id);
            editor.putStringSet(PREF_BUCKETS_IDS, set);

            editor.apply();
        } else {
            Log.e(TAG, "Cannot save bucket: playlist session is in error state");
        }
    }

    /**
     * Restores/recreate an AutoMix bucket from an existing session ID
     * @param id The session ID to restore
     * @return An {@link com.fastbootmobile.encore.api.echonest.AutoMixBucket} regenerated from the provided
     *         bucket ID.
     */
    public AutoMixBucket restoreBucketFromId(final String id) {
        SharedPreferences prefs = getPrefs();

        return new AutoMixBucket(
                prefs.getString(PREF_PREFIX_NAME + id, null),
                prefs.getString(PREF_PREFIX_STYLES + id, "").split(","),
                prefs.getString(PREF_PREFIX_MOODS + id, "").split(","),
                prefs.getBoolean(PREF_PREFIX_TASTE + id, false),
                prefs.getFloat(PREF_PREFIX_ADVENTUROUS + id, -1),
                prefs.getString(PREF_PREFIX_SONG_TYPES + id, "").split(","),
                prefs.getFloat(PREF_PREFIX_SPEECHINESS + id, -1),
                prefs.getFloat(PREF_PREFIX_ENERGY + id, -1),
                prefs.getFloat(PREF_PREFIX_FAMILIAR + id, -1),
                id
        );
    }

    /**
     * Returns the list of existing buckets
     * @return The list of existing buckets
     */
    public List<AutoMixBucket> getBuckets() {
        return mBuckets;
    }

    /**
     * @return The currently playing bucket
     */
    public AutoMixBucket getCurrentPlayingBucket() {
        return mCurrentPlayingBucket;
    }

    /**
     * Starts playing a bucket
     * @param bucket The bucket to play
     */
    public void startPlay(AutoMixBucket bucket) {
        // Ensure bucket is ready
        if (!bucket.isPlaylistSessionReady()) {
            Log.e(TAG, "Cannot play bucket " + bucket.getName() + ": Session not ready");
            return;
        }

        // Queue tracks
        try {
            String trackRef = null;

            // Try to get the first track, with 5 tries (in case of bad network or temporary error)
            int tryCount = 0;
            while (trackRef == null && tryCount < 5) {
                trackRef = bucket.getNextTrack();
                tryCount++;
            }

            if (trackRef == null) {
                Log.e(TAG, "Track Reference is still null after 5 attempts");
                mHandler.post(new Runnable() {
                    public void run() {
                        Utils.shortToast(mContext, R.string.bucket_track_failure);
                    }
                });
                mCurrentPlayingBucket = null;
            } else {
                Song s = getSongFromRef(trackRef);

                if (s != null) {
                    mActiveBucketRefHistory.clear();
                    mActiveBucketRefHistory.add(s.getRef());
                    mCurrentPlayingBucket = bucket;
                    PlaybackProxy.playSong(s);
                } else {
                    Log.e(TAG, "Song is null! Cannot find it back");
                }
            }
        } catch (EchoNestException e) {
            if (e.getCode() == 5 && e.getMessage().contains("does not exist")
                    && !bucket.isPlaylistSessionError()) {
                // The bucket has expired, we must recreate it and restart the play procedure
                Log.d(TAG, "The bucket has expired, we must recreate it and restart the play procedure");
                bucket.createPlaylistSession();
                startPlay(bucket);
            } else {
                Log.e(TAG, "Unable to get next track from bucket", e);
            }
        }
    }

    /**
     * Destroys the provided bucket
     *
     * @param bucket The bucket to remove
     */
    public void destroyBucket(AutoMixBucket bucket) {
        SharedPreferences prefs = getPrefs();
        SharedPreferences.Editor editor = prefs.edit();

        Set<String> set = new TreeSet<>(prefs.getStringSet(PREF_BUCKETS_IDS, new TreeSet<String>()));
        set.remove(bucket.getSessionId());
        editor.putStringSet(PREF_BUCKETS_IDS, set);

        editor.apply();

        mBuckets.remove(bucket);
    }

    /**
     * Retrieves a Song from the preferred Rosetta-stone provider
     * @param ref The reference of the song
     * @return A Song, or null if the provider couldn't retrieve it
     */
    private @Nullable Song getSongFromRef(String ref) {
        ProviderAggregator aggregator = ProviderAggregator.getDefault();

        // Try to see if we have that song in our cache already
        Song s = aggregator.retrieveSong(ref, null);

        if (s == null) {
            // The ref is not in cache, try to get it from the preferred rosetta stone provider
            String prefix = aggregator.getPreferredRosettaStonePrefix();

            if (prefix != null) {
                ProviderIdentifier id = aggregator.getRosettaStoneIdentifier(prefix);
                s = aggregator.retrieveSong(ref, id);
            }
        }

        return s;
    }

    @Override
    public void onSongStarted(boolean buffering, Song s) throws RemoteException {
        if (mCurrentPlayingBucket != null) {
            if (buffering) {
                if (!mActiveBucketRefHistory.contains(s.getRef())) {
                    // Song started is not the one we expected from the bucket, cancel automix playback
                    Log.d(TAG, "Cancelling automix playback: Playing " + s.getRef() + ", not in active history");
                    mCurrentPlayingBucket = null;
                } else {
                    // We're playing the song we expected to be played from the bucket, fetch the next one.
                    Log.d(TAG, "Fetching next automix track");
                    new Thread(mGetNextTrackRunnable).start();
                }
            }
        }
    }

}
