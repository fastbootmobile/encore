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

import android.util.Log;

import com.echonest.api.v4.DynamicPlaylistSession;
import com.echonest.api.v4.DynamicPlaylistSteerParams;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.GeneralCatalog;
import com.echonest.api.v4.Playlist;
import com.echonest.api.v4.Song;
import com.echonest.api.v4.Track;

import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.List;

/**
 * Represents a parametrized AutoMix Bucket
 */
public class AutoMixBucket {
    private static final String TAG = "AutoMixBucket";
    private static final boolean DEBUG = true;

    String mName;
    String[] mStyles;
    String[] mMoods;
    boolean mUseTaste;
    float mAdventurousness;
    String[] mSongTypes;
    float mSpeechiness;
    float mEnergy;
    float mFamiliar;

    private DynamicPlaylistSession mPlaylistSession;
    private GeneralCatalog mCatalog;
    private boolean mSessionReady;
    private boolean mSessionError;

    /**
     * Creates a new Automix Bucket without an existing attached session. You should not create
     * manually an automix bucket, but rather use {@link com.fastbootmobile.encore.api.echonest.AutoMixManager}
     * @param name The name of the bucket
     * @param styles The EchoNest styles to include in the bucket
     * @param moods The EchoNest moods to include
     * @param taste Whether or not this bucket uses the user's taste profile. Note that you must
     *              release the catalog once done using releaseCatalog
     * @param adventurous The level of adventurousness [0.0-1.0]
     * @param songTypes The EchoNest types of songs to include
     * @param speechiness The target level of speechiness [0.0-1.0]
     * @param energy The target level of energy [0.0-1.0]
     * @param familiar The target level of familiarity [0.0-1.0]
     */
    AutoMixBucket(String name, String[] styles, String[] moods, boolean taste, float adventurous,
                  String[] songTypes, float speechiness, float energy, float familiar) {
        this(name, styles, moods, taste, adventurous, songTypes, speechiness, energy, familiar, null);
        mSessionReady = false;
        mSessionError = false;
    }

    /**
     * Creates a new Automix Bucket with an existing session ID. You should not create manually an
     * automix bucket, but rather use {@link com.fastbootmobile.encore.api.echonest.AutoMixManager}
     * @param name The name of the bucket
     * @param styles The EchoNest styles to include in the bucket
     * @param moods The EchoNest moods to include
     * @param taste Whether or not this bucket uses the user's taste profile. Note that you must
     *              release the catalog once done using releaseCatalog
     * @param adventurous The level of adventurousness [0.0-1.0]
     * @param songTypes The EchoNest types of songs to include
     * @param speechiness The target level of speechiness [0.0-1.0]
     * @param energy The target level of energy [0.0-1.0]
     * @param familiar The target level of familiarity [0.0-1.0]
     * @param sessionId The existing EchoNest session ID to recover
     */
    AutoMixBucket(String name, String[] styles, String[] moods, boolean taste, float adventurous,
                  String[] songTypes, float speechiness, float energy, float familiar,
                  String sessionId) {
        mName = name;
        mStyles = styles;
        mMoods = moods;
        mUseTaste = taste;
        mAdventurousness = adventurous;
        mSongTypes = songTypes;
        mSpeechiness = speechiness;
        mEnergy = energy;
        mFamiliar = familiar;

        if (sessionId != null) {
            mPlaylistSession = new DynamicPlaylistSession(new EchoNest().getApi(), sessionId);
            mSessionReady = true;
        } else {
            mSessionReady = false;
        }

        mSessionError = false;
    }

    /**
     * Creates or reset the EchoNest session for this bucket. This will reset the bucket on the
     * EchoNest end and initialize a new session.
     * @return The generated session
     */
    DynamicPlaylistSession createPlaylistSession() {
        // Reset session status as we could be retrying
        mSessionReady = false;
        mSessionError = false;

        EchoNest echoNest = new EchoNest();
        String type = generateTasteCatalogIfNeededAndGetType(echoNest);

        // Generate the playlist session with the initial parameters
        if (!mSessionError) {
            if (DEBUG) Log.d(TAG, "Creating dynamic playlist.");
            String catalogId = (mCatalog != null ? mCatalog.getID() : null);
            try {
                mPlaylistSession = echoNest.createDynamicPlaylist(type, catalogId, mMoods, mStyles);
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to create the dynamic playlist", e);
                mSessionError = true;
            }
        }

        // If all went well, steer the dynamic playlist with the advanced settings
        if (!mSessionError) {
            DynamicPlaylistSteerParams p = new DynamicPlaylistSteerParams();
            if (mEnergy >= 0) {
                p.addTargetValue(DynamicPlaylistSteerParams.SteeringParameter.energy, mEnergy);
            }
            if (mFamiliar >= 0) {
                p.addTargetValue(DynamicPlaylistSteerParams.SteeringParameter.artist_familiarity,
                        mFamiliar);
            }
            if (mSpeechiness >= 0) {
                p.add("target_speechiness", mSpeechiness);
            }
            if (mAdventurousness >= 0) {
                p.setAdventurousness(mAdventurousness);
            }
            if (mSongTypes != null && mSongTypes.length > 0) {
                for (String songType : mSongTypes) {
                    if (!songType.isEmpty()) {
                        p.add("song_type", songType);
                    }
                }
            }

            try {
                mPlaylistSession.steer(p);
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to steer session data", e);
                mSessionError = true;
            }
        }

        mSessionReady = true;
        return mPlaylistSession;
    }

    /**
     * Generates a taste profile catalog if needed, and return the type of
     * @param echoNest The echonest api connection
     * @return 'catalog-radio' or  'artist-description'
     */
    private String generateTasteCatalogIfNeededAndGetType(EchoNest echoNest) {
        String type;

        if (mUseTaste) {
            // Generate the taste profile first for use in the playlist session
            if (DEBUG) Log.d(TAG, "Generating taste profile...");
            try {
                mCatalog = echoNest.createTemporaryTasteProfile();
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to create the taste profile", e);
                mSessionError = true;
            } finally {
                if ((mMoods == null || mMoods.length == 0)
                        && (mStyles == null || mStyles.length == 0)) {
                    type = "catalog-radio";
                } else {
                    type = "artist-description";
                }
                if (DEBUG) Log.d(TAG, "Taste profile generation succeeded.");
            }
        } else {
            type = "artist-description";
        }

        return type;
    }

    /**
     * When the taste profile is enabled, you MUST release (delete) the catalog, as we have
     * a limit of 1000 catalogs for a non-commercial account (hence why we only allow that
     * for static playlists, so that we can generate that on the fly).
     */
    private void releaseCatalog() {
        if (mCatalog != null) {
            try {
                mCatalog.delete();
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to delete catalog", e);
            }
        }
    }

    public List<String> generateStaticPlaylist() {
        EchoNest echoNest = new EchoNest();
        String type = generateTasteCatalogIfNeededAndGetType(echoNest);
        String seedCatalog = null;

        if (mCatalog != null) {
            seedCatalog = mCatalog.getID();
        }

        try {
            List<String> songs = echoNest.createStaticPlaylist(type, seedCatalog, mStyles, mMoods, mAdventurousness,
                mSongTypes, mSpeechiness, mEnergy, mFamiliar);
            releaseCatalog();
            return songs;
        } catch (EchoNestException e) {
            Log.e(TAG, "Cannot create playlist", e);
            mSessionError = true;
        }

        // If we reach here, we got an error
        return null;
    }

    /**
     * Returns whether or not the playlist session is ready to be played
     * @return True if the playlist session is ready to be played, false otherwise
     */
    public boolean isPlaylistSessionReady() {
        return mSessionReady;
    }

    /**
     * Returns whether or not the playlist session couldn't be generated
     * @return True if an error occured during {#createPlaylistSession}
     */
    public boolean isPlaylistSessionError() {
        return mSessionError;
    }

    /**
     * Returns the ID of the current playlist session. The session must be ready for this call
     * to be valid.
     * @return The ID of the current session.
     */
    public String getSessionId() {
        return mPlaylistSession.getSessionID();
    }

    /**
     * Advance and fetches a new track from this automix bucket.
     * @return A Rosetta-stone ID of the next track, or null if no provider supports rosetta-stone
     * @throws EchoNestException In case of remote error.
     */
    public String getNextTrack() throws EchoNestException {
        if (mPlaylistSession == null) {
            Log.w(TAG, "getNextTrack called without a playlist session. Creating it");
            createPlaylistSession();
        }

        if (isPlaylistSessionError()) {
            Log.e(TAG, "Cannot get next track because playlist session got in error");
            throw new EchoNestException(2, "Internal error");
        }

        final String prefix = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
        if (prefix != null) {
            Playlist nextTracks = null;
            int tries = 0;
            while (nextTracks == null) {
                try {
                    nextTracks = mPlaylistSession.next();
                } catch (EchoNestException e) {
                    if ((e.getCode() == -1 && e.getMessage().contains("timed out") || e.getMessage().contains("Parse Exception"))
                            && tries < 5) {
                        tries++;
                    } else {
                        throw e;
                    }
                } catch (Exception e) {
                    if (tries < 5) {
                        Log.e(TAG, "Error while parsing response from EchoNest");
                        tries++;
                    } else {
                        return null;
                    }
                }
            }

            final List<Song> songs = nextTracks.getSongs();

            if (songs.size() > 0) {
                final Song firstSong = songs.get(0);
                final Track track = firstSong.getTrack(prefix);
                if (track != null) {
                    return track.getForeignID();
                } else {
                    Log.e(TAG, "Null track!");
                    return null;
                }
            } else {
                Log.e(TAG, "No new track for this bucket!");
            }
        }

        return null;
    }

    /**
     * Returns the name of this bucket
     * @return The name of this bucket
     */
    public String getName() {
        return mName;
    }

    /**
     * Notifies the system that the user liked this song and want more
     */
    public void notifyLike() throws EchoNestException {
        com.fastbootmobile.encore.model.Song currentTrack = PlaybackProxy.getCurrentTrack();
        if (currentTrack != null) {
            String songRef = currentTrack.getRef();
            try {
                mPlaylistSession.feedback(DynamicPlaylistSession.FeedbackType.favorite_song, songRef);
            } catch (Exception e) {
                Log.e(TAG, "Cannot feedback like", e);
            }
        }
    }

    /**
     * Notifies the system that the user disliked this song and don't want it again
     * @throws EchoNestException
     */
    public void notifyDislike() throws EchoNestException {
        com.fastbootmobile.encore.model.Song currentTrack = PlaybackProxy.getCurrentTrack();
        if (currentTrack != null) {
            String songRef = currentTrack.getRef();
            try {
                mPlaylistSession.feedback(DynamicPlaylistSession.FeedbackType.ban_song, songRef);
            } catch (Exception e) {
                Log.e(TAG, "Cannot feedback dislike", e);
            }
        }
    }

    /**
     * Notifies the system that the user skipped the current song so he might not like it
     */
    public void notifySkip() throws EchoNestException {
        com.fastbootmobile.encore.model.Song currentTrack = PlaybackProxy.getCurrentTrack();
        if (currentTrack != null) {
            String songRef = currentTrack.getRef();
            try {
                mPlaylistSession.feedback(DynamicPlaylistSession.FeedbackType.skip_song, songRef);
            } catch (Exception e) {
                Log.e(TAG, "Cannot feedback skip", e);
            }
        }
    }
}
