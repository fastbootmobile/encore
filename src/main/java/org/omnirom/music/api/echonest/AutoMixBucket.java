package org.omnirom.music.api.echonest;

import android.util.Log;

import com.echonest.api.v4.DynamicPlaylistSession;
import com.echonest.api.v4.DynamicPlaylistSteerParams;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.GeneralCatalog;
import com.echonest.api.v4.Playlist;
import com.echonest.api.v4.Song;
import com.echonest.api.v4.Track;

import org.omnirom.music.providers.ProviderAggregator;

import java.util.List;

/**
 * Created by Guigui on 14/08/2014.
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
    private boolean mSessionReady;
    private boolean mSessionError;

    AutoMixBucket(String name, String[] styles, String[] moods, boolean taste, float adventurous,
                  String[] songTypes, float speechiness, float energy, float familiar) {
        this(name, styles, moods, taste, adventurous, songTypes, speechiness, energy, familiar, null);
        mSessionReady = false;
        mSessionError = false;
    }

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

    DynamicPlaylistSession createPlaylistSession() {
        // Reset session status as we could be retrying
        mSessionReady = false;
        mSessionError = false;

        EchoNest echoNest = new EchoNest();
        GeneralCatalog catalog = null;
        String type;

        if (mUseTaste) {
            // Generate the taste profile first for use in the playlist session
            if (DEBUG) Log.d(TAG, "Generating taste profile...");
            try {
                catalog = echoNest.createTemporaryTasteProfile();
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to create the taste profile", e);
                mSessionError = true;
            } finally {
                type = "catalog-radio";
                if (DEBUG) Log.d(TAG, "Taste profile generation succeeded.");
            }
        } else {
            type = "artist-description";
        }

        // Generate the playlist session with the initial parameters
        if (!mSessionError) {
            if (DEBUG) Log.d(TAG, "Creating dynamic playlist.");
            String catalogId = (catalog != null ? catalog.getID() : null);
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
            if (mSongTypes != null) {
                for (String songType : mSongTypes) {
                    p.add("song_type", songType);
                }
            }

            try {
                mPlaylistSession.steer(p);
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to steer session data", e);
                mSessionError = true;
            }
        }

        if (catalog != null) {
            try {
                catalog.delete();
            } catch (EchoNestException e) {
                Log.e(TAG, "Unable to delete catalog", e);
            }
        }

        return mPlaylistSession;
    }

    public boolean isPlaylistSessionReady() {
        return mSessionReady;
    }

    public boolean isPlaylistSessionError() {
        return mSessionError;
    }

    public String getSessionId() {
        return mPlaylistSession.getSessionID();
    }

    public String getNextTrack() throws EchoNestException {
        String prefix = ProviderAggregator.getDefault().getPreferredRosettaStonePrefix();
        if (prefix != null) {
            Playlist nextTracks = null;
            int tries = 0;
            while (nextTracks == null) {
                try {
                    nextTracks = mPlaylistSession.next();
                } catch (EchoNestException e) {
                    if (e.getCode() == -1 && e.getMessage().contains("timed out") && tries < 5) {
                        tries++;
                    } else {
                        throw e;
                    }
                }
            }

            List<Song> songs = nextTracks.getSongs();

            if (songs.size() > 0) {
                Song firstSong = songs.get(0);
                Track track = firstSong.getTrack(prefix);
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
}
