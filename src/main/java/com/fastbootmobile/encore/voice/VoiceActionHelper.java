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

package com.fastbootmobile.encore.voice;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.fastbootmobile.encore.app.BuildConfig;
import com.fastbootmobile.encore.utils.Utils;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.framework.Suggestor;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class VoiceActionHelper implements VoiceCommander.ResultListener {
    private static final String TAG = "VoiceActionHelper";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int MSG_START_PLAY = 1;
    private static final int MSG_START_PLAY_LIST = 3;

    private int mPendingAction;
    private int mPendingExtra;
    private String[] mPendingParams;
    private Handler mHandler;
    private Message mPendingMessage;
    private Context mContext;
    private List<SearchResult> mPreviousSearchResults;

    private static class VoiceActionHandler extends Handler {
        private WeakReference<VoiceActionHelper> mParent;

        public VoiceActionHandler(WeakReference<VoiceActionHelper> parent) {
            mParent = parent;
        }

        @Override
        public void handleMessage(Message msg) {
            mParent.get().resetPendingValues();

            if (msg.what == MSG_START_PLAY) {
                if (DEBUG) Log.d(TAG, "Final handled action: START_PLAY");

                Song song = (Song) msg.obj;
                PlaybackProxy.playSong(song);
            } else if (msg.what == MSG_START_PLAY_LIST) {
                List<Song> songs = (List<Song>) msg.obj;

                if (DEBUG) Log.d(TAG, "Final handled action: START_PLAY_LIST (" + songs.size() + " songs)");


                PlaybackProxy.clearQueue();
                for (Song song : songs) {
                    PlaybackProxy.queueSong(song, false);
                }
                PlaybackProxy.playAtIndex(0);
            }
        }
    }

    public VoiceActionHelper(Context context) {
        mHandler = new VoiceActionHandler(new WeakReference<>(this));
        mContext = context;
    }

    private void resetPendingValues() {
        mPendingMessage = null;
        mPendingAction = 0;
        mPendingExtra = 0;
        mPendingParams = null;
    }

    @Override
    public void onResult(int action, int extra, String[] params) {
        mPendingAction = action;
        mPendingExtra = extra;
        mPendingParams = params;

        if (DEBUG) Log.d(TAG, "onResult: action=" + action + " extra=" + extra
                + " params=" + Arrays.toString(params));

        switch (action) {
            case VoiceCommander.ACTION_PLAY_ALBUM:
            case VoiceCommander.ACTION_PLAY_ARTIST:
            case VoiceCommander.ACTION_PLAY_PLAYLIST:
            case VoiceCommander.ACTION_PLAY_TRACK:
                if (extra == VoiceCommander.EXTRA_SOURCE) {
                    handlePlay(params[0], params[1]);
                } else {
                    handlePlay(params[0], null);
                }
                break;

            case VoiceCommander.ACTION_PAUSE:
                handlePause();
                break;

            case VoiceCommander.ACTION_NEXT:
                if (extra == VoiceCommander.EXTRA_TIME_SECS) {
                    handleNext(0, Integer.parseInt(params[0]));
                } else if (extra == VoiceCommander.EXTRA_TIME_MINS) {
                    handleNext(Integer.parseInt(params[0]), 0);
                } else {
                    handleNext(0, 0);
                }
                break;

            case VoiceCommander.ACTION_PREVIOUS:
                if (extra == VoiceCommander.EXTRA_TIME_SECS) {
                    handlePrevious(0, Integer.parseInt(params[0]));
                } else if (extra == VoiceCommander.EXTRA_TIME_MINS) {
                    handlePrevious(Integer.parseInt(params[0]), 0);
                } else {
                    handlePrevious(0, 0);
                }
                break;

            case VoiceCommander.ACTION_JUMP:
                handleJump(Integer.parseInt(params[0]));
                break;

            case VoiceCommander.ACTION_GOOGLE:
                handleGoogle(mContext, params[0]);
                break;

            default:
                Log.e(TAG, "Unknown result action " + action);
                break;
        }
    }

    private void handlePlay(String request, String source) {
        if (source != null) {
            // The user specified the source: Only search for a result on this provider
            ProviderConnection conn = PluginsLookup.getDefault().getProviderByName(source);
            if (conn != null) {
                IMusicProvider provider = conn.getBinder();
                if (provider != null) {
                    try {
                        if (DEBUG) Log.d(TAG, "Started searching '" + request + "' on " + source);
                        provider.startSearch(request);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot start search on " + conn.getProviderName());
                    }
                }
            } else {
                Log.e(TAG, "Cannot start search: provider " + source + " not found");
            }
        } else {
            // No source specified: Search globally
            ProviderAggregator.getDefault().startSearch(request);
        }
    }

    private void handlePause() {
        PlaybackProxy.pause();
    }

    private void handleNext(int mins, int secs) {
        if (mins == 0 && secs == 0) {
            PlaybackProxy.next();
        } else if (mins > 0) {
            PlaybackProxy.seek(PlaybackProxy.getCurrentTrackPosition() + mins * 60 * 1000);
        } else if (secs > 0) {
            PlaybackProxy.seek(PlaybackProxy.getCurrentTrackPosition() + secs * 1000);
        }
    }

    private void handlePrevious(int mins, int secs) {
        if (mins == 0 && secs == 0) {
            PlaybackProxy.previous();
        } else if (mins > 0) {
            PlaybackProxy.seek(PlaybackProxy.getCurrentTrackPosition() - mins * 60 * 1000);
        } else if (secs > 0) {
            PlaybackProxy.seek(PlaybackProxy.getCurrentTrackPosition() - secs * 1000);
        }
    }

    private void handleJump(int index) {
        PlaybackProxy.playAtIndex(index - 1);
    }

    private void handleGoogle(Context context, String query) {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        context.startActivity(intent);
    }

    public void onSearchResult(List<SearchResult> results) {
        mPreviousSearchResults = results;

        // Match the result to one or multiple songs. We first try to look for an exact match,
        // and if we have multiple, we'll prefer the following order: Playlist, Artist, Album
        // and then Song.
        // TODO: We don't handle cases where entities might not be loaded
        ProviderAggregator aggr = ProviderAggregator.getDefault();
        Message msg = null;
        String request = mPendingParams[0];
        float bestMatchPercentage = -1;

        for (SearchResult result : results) {
            if (mPendingAction == VoiceCommander.ACTION_PLAY_PLAYLIST) {
                // Playlist
                List<String> playlists = result.getPlaylistList();
                for (String ref : playlists) {
                    Playlist playlist = aggr.retrievePlaylist(ref, result.getIdentifier());
                    if (playlist == null) continue;

                    if (request.equalsIgnoreCase(playlist.getName())) {
                        if (DEBUG) Log.d(TAG, "Got an exact playlist match");
                        msg = mHandler.obtainMessage(MSG_START_PLAY_LIST,
                                Utils.refListToSongList(playlist.songsList(), playlist.getProvider()));
                        bestMatchPercentage = 1;
                        break;
                    } else {
                        float percent = Utils.distancePercentage(request, playlist.getName());
                        if (percent > bestMatchPercentage) {
                            if (DEBUG)
                                Log.d(TAG, "Matched playlist " + playlist.getName() + " (" + percent + ")");
                            bestMatchPercentage = percent;
                            msg = mHandler.obtainMessage(MSG_START_PLAY_LIST,
                                    Utils.refListToSongList(playlist.songsList(), playlist.getProvider()));
                        }
                    }
                }
            } else if (mPendingAction == VoiceCommander.ACTION_PLAY_ARTIST) {
                // Artist
                List<String> artists = result.getArtistList();
                for (String ref : artists) {
                    Artist artist = aggr.retrieveArtist(ref, result.getIdentifier());
                    if (artist == null) continue;

                    if (request.equalsIgnoreCase(artist.getName())) {
                        if (DEBUG) Log.d(TAG, "Got an exact artist match");
                        List<Song> radio = Suggestor.getInstance().buildArtistRadio(artist);
                        bestMatchPercentage = 1;
                        // Radio songs might be null or empty if artist albums aren't loaded, we wait
                        // for artist update in callback then.
                        if (radio != null && radio.size() > 0) {
                            msg = mHandler.obtainMessage(MSG_START_PLAY_LIST, radio);
                            break;
                        } else {
                            Log.w(TAG, "Matched exact artist, but artist radio unavailable");

                            // Ensure album contents are fetched
                            ProviderConnection pc = PluginsLookup.getDefault()
                                    .getProvider(artist.getProvider());
                            if (pc != null) {
                                IMusicProvider binder = pc.getBinder();
                                try {
                                    if (binder != null) {
                                        List<String> albums = artist.getAlbums();
                                        for (String albumRef : albums) {
                                            binder.fetchAlbumTracks(albumRef);
                                        }
                                    }
                                } catch (RemoteException e) {
                                    // ignore
                                }
                            }
                        }
                    } else if (artist.getName() != null) {
                        float percent = Utils.distancePercentage(request, artist.getName());
                        if (percent > bestMatchPercentage) {
                            List<Song> radio = Suggestor.getInstance().buildArtistRadio(artist);
                            bestMatchPercentage = percent;
                            if (DEBUG)
                                Log.d(TAG, "Matched artist " + artist.getName() + " (" + percent + ")");

                            // Radio songs might be null or empty if artist albums aren't loaded, we wait
                            // for artist update in callback then.
                            if (radio != null && radio.size() > 0) {
                                msg = mHandler.obtainMessage(MSG_START_PLAY_LIST, radio);
                            } else {
                                Log.w(TAG, "Matched average artist, but artist radio unavailable");

                                // Ensure album contents are fetched
                                ProviderConnection pc = PluginsLookup.getDefault()
                                        .getProvider(artist.getProvider());
                                if (pc != null) {
                                    IMusicProvider binder = pc.getBinder();
                                    try {
                                        if (binder != null) {
                                            List<String> albums = artist.getAlbums();
                                            for (String albumRef : albums) {
                                                binder.fetchAlbumTracks(albumRef);
                                            }
                                        }
                                    } catch (RemoteException e) {
                                        // ignore
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (mPendingAction == VoiceCommander.ACTION_PLAY_ALBUM) {
                // Album
                List<String> albums = result.getAlbumsList();
                for (String ref : albums) {
                    Album album = aggr.retrieveAlbum(ref, result.getIdentifier());
                    if (album == null) continue;

                    if (request.equalsIgnoreCase(album.getName())) {
                        if (DEBUG) Log.d(TAG, "Got an exact album match");
                        msg = mHandler.obtainMessage(MSG_START_PLAY_LIST,
                                Utils.refIteratorToSongList(album.songs(), album.getProvider()));
                        bestMatchPercentage = 1;
                        break;
                    } else {
                        float percent = Utils.distancePercentage(request, album.getName());
                        if (percent > bestMatchPercentage) {
                            if (DEBUG)
                                Log.d(TAG, "Matched album " + album.getName() + " (" + percent + ")");
                            bestMatchPercentage = percent;
                            msg = mHandler.obtainMessage(MSG_START_PLAY_LIST,
                                    Utils.refIteratorToSongList(album.songs(), album.getProvider()));
                        }
                    }
                }
            } else if (mPendingAction == VoiceCommander.ACTION_PLAY_TRACK) {
                // Song
                List<String> songs = result.getSongsList();
                for (String ref : songs) {
                    Song song = aggr.retrieveSong(ref, result.getIdentifier());
                    if (song == null) continue;

                    if (request.equalsIgnoreCase(song.getTitle())) {
                        if (DEBUG) Log.d(TAG, "Got an exact song title match");
                        msg = mHandler.obtainMessage(MSG_START_PLAY, song);
                        bestMatchPercentage = 1;
                        break;
                    } else {
                        float percent = Utils.distancePercentage(request, song.getTitle());
                        if (percent > bestMatchPercentage) {
                            if (DEBUG)
                                Log.d(TAG, "Matched song " + song.getTitle() + " (" + percent + ")");
                            bestMatchPercentage = percent;
                            msg = mHandler.obtainMessage(MSG_START_PLAY, song);
                        }
                    }
                }
            }
        }

        // If we found a match, go ahead. If we requested a source, post the message
        // immediately. If no exact source was specified, post it 1 second later to allow
        // eventual updates. If we have no exact match, either wait for results in update, or
        // don't do anything.
        if (msg != null) {
            if (mPendingExtra == VoiceCommander.EXTRA_SOURCE) {
                // We're only expecting one source, so run immediately
                msg.sendToTarget();
            } else {
                // Other sources might replay, wait a second and kick the message
                if (mPendingMessage != null) {
                    mHandler.removeMessages(mPendingMessage.what, mPendingMessage.obj);
                }

                mPendingMessage = msg;
                mHandler.sendMessageDelayed(msg, 1000);
            }
        }
    }

    public void onArtistUpdate(List<Artist> artists) {
        if (mPendingAction == VoiceCommander.ACTION_PLAY_ARTIST) {
            if (mPreviousSearchResults != null) {
                if (DEBUG) Log.d(TAG, "Got updated artist information");
                onSearchResult(mPreviousSearchResults);
            } else {
                if (DEBUG) Log.d(TAG, "Got artist update but no pending search results");
            }
        }
    }

    public void onAlbumUpdate(List<Album> albums) {
        if (mPendingAction == VoiceCommander.ACTION_PLAY_ALBUM) {
            if (mPreviousSearchResults != null) {
                if (DEBUG) Log.d(TAG, "Got updated album information");
                onSearchResult(mPreviousSearchResults);
            } else {
                if (DEBUG) Log.d(TAG, "Got album update but no pending search results");
            }
        }
    }

    public void onSongUpdate(List<Song> songs) {
        if (mPendingAction == VoiceCommander.ACTION_PLAY_TRACK) {
            if (mPreviousSearchResults != null) {
                if (DEBUG) Log.d(TAG, "Got updated song information");
                onSearchResult(mPreviousSearchResults);
            } else {
                if (DEBUG) Log.d(TAG, "Got song update but no pending search results");
            }
        }
    }
}
