package com.fastbootmobile.encore.providers.localprovider;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.util.Log;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Genre;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.AudioClientSocket;
import com.fastbootmobile.encore.providers.AudioSocket;
import com.fastbootmobile.encore.providers.Constants;
import com.fastbootmobile.encore.providers.IArtCallback;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.IProviderCallback;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import omnimusic.Plugin;

public class PluginService extends Service implements AudioSocket.ISocketCallback {
    private static final String TAG = "OmniMusic-LocalService";
    public static final String LOGO_REF = "LOCAL_PROVIDER";


    Handler mHandler = new Handler();
    private ProviderIdentifier mIdentifier;
    private final List<IProviderCallback> mCallbacks;
    private final List<IProviderCallback> mCallbacksRemoval;
    private AudioClientSocket mAudioSocket;
    private LocalProvider mLocalProvider;
    private int mRate;
    private int mAudioWritten;
    private final Object mAudioWrittenLock = new Object();
    private byte[] mAudioBuffer;
    private int mAudioBufferIndex = 0;

    private final Thread mWriteAudioThread = new Thread() {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            while (!isInterrupted()) {
                synchronized (mWriteAudioThread) {
                    try {
                        mWriteAudioThread.wait();
                    } catch (InterruptedException e) {
                        continue;
                    }
                    try {
                        try {
                            mAudioSocket.writeAudioData(mAudioBuffer, 0, mAudioBufferIndex);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error while writing audio data", e);
                        mAudioSocket = null;
                        mLocalProvider.pause(false);
                    }
                }
            }
        }
    };

    public PluginService() {
        mCallbacks = new ArrayList<>();
        mCallbacksRemoval = new ArrayList<>();
        mRate = 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Context context = getApplicationContext();
        mLocalProvider = new LocalProvider(uri, getContentResolver(), providerCallback, context);
        mWriteAudioThread.start();


        new Thread() {
            public void run() {
                mLocalProvider.poll();
            }
        }.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWriteAudioThread.interrupt();
    }

    private void removeCallback(final IProviderCallback cb) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mCallbacksRemoval) {
                    mCallbacksRemoval.add(cb);
                }
            }
        });
    }

    private Runnable mRemoveCallbackRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mCallbacksRemoval) {
                synchronized (mCallbacks) {
                    mCallbacks.removeAll(mCallbacksRemoval);
                }

                mCallbacksRemoval.clear();
            }
        }
    };

    private LocalProvider.LocalCallback providerCallback = new LocalProvider.LocalCallback() {
        @Override
        public int musicDelivery(byte[] data, int frames, int channels, int sampleRate) {
            if (mAudioSocket == null) {
                Log.w(TAG, "Got music delivery without an audio socket set!");
                return 0;
            }

            // If the sample rate changed, update it
            if (mRate != sampleRate) {
                mRate = sampleRate;

                try {
                    mAudioSocket.writeFormatData(channels, sampleRate);
                } catch (IOException e) {
                    Log.e(TAG, "IO error while pushing audio data", e);
                    // Error while pushing audio to the socket, we stop the playback and
                    // turn off the socket
                    mAudioSocket = null;
                    return 0;
                }
            }


            // First, we try to copy the frames to our local buffer. If the local buffer is full,
            // try to send it and empty it - and retry to write the current frames into the buffer
            // on next loop.
            //if (mAudioBufferIndex + frames < mAudioBuffer.length) {
            mAudioBuffer = data;
            mAudioBufferIndex = frames;

            // There's enough data in the buffer, try to send it over to the app
            mAudioWritten = -1;

            synchronized (mWriteAudioThread) {
                mWriteAudioThread.notifyAll();
            }

            synchronized (mAudioWrittenLock) {
                if (mAudioWritten == -1) {
                    // Wait 500ms for a reply
                    try {
                        mAudioWrittenLock.wait(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted while waiting for audio written response");
                    }
                }
            }

            // No response in time, assume the audio wasn't written
            if (mAudioWritten == -1) {
                mAudioWritten = 0;
            }

            if (mAudioWritten > 0) {
                // We could write it, so reset our local buffer
                mAudioBufferIndex = 0;
            }

            return mAudioWritten;
        }

        @Override
        public void artistUpdated(final Artist artist) {
            if (mIdentifier == null) {
                return;
            }

            artist.setProvider(mIdentifier);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onArtistUpdate(mIdentifier, artist);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void playlistUpdated(final Playlist playlist) {
            if (mIdentifier == null) {
                return;
            }

            playlist.setProvider(mIdentifier);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onPlaylistAddedOrUpdated(mIdentifier, playlist);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void playlistRemoved(final String playlistRef) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onPlaylistRemoved(mIdentifier, playlistRef);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void albumUpdated(final Album album) {
            if (mIdentifier == null) {
                return;
            }

            album.setProvider(mIdentifier);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onAlbumUpdate(mIdentifier, album);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void songUpdated(final Song song) {
            if (song == null) {
                throw new IllegalArgumentException("Song cannot be null");
            }
            if (mIdentifier == null) {
                return;
            }

            song.setProvider(mIdentifier);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onSongUpdate(mIdentifier, song);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void genreUpdated(final Genre genre) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    /*synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                               // cb.onGenreUpdate(mIdentifier, genre);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }*/
                }
            });
        }

        @Override
        public void searchFinished(final SearchResult searchResult) {
            searchResult.setIdentifier(mIdentifier);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onSearchResult(searchResult);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void songFinished() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onTrackEnded(mIdentifier);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void songPlaying() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onSongPlaying(mIdentifier);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }
        @Override
        public void songPaused() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (mCallbacks) {
                        for (IProviderCallback cb : mCallbacks) {
                            try {
                                cb.onSongPaused(mIdentifier);
                            } catch (DeadObjectException e) {
                                removeCallback(cb);
                            } catch (RemoteException e) {
                                Log.e(TAG, "RemoteException when notifying a callback", e);
                            }
                        }
                    }
                }
            });
        }

    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*
    * Binder Stub implementation
    */
    private IMusicProvider.Stub mBinder = new IMusicProvider.Stub() {
        /**
         * Returns the API Version of this providers.
         * The current API version is: 1
         */
        @Override
        public int getVersion() {
            return Constants.API_VERSION;
        }


        public ProviderIdentifier getIdentifier() {
            return mIdentifier;
        }

        @Override
        public void setIdentifier(ProviderIdentifier identifier) throws RemoteException {
            mIdentifier = identifier;
            mLocalProvider.notifyIdentifier(mIdentifier);
        }

        /**
         * Register a callback for the app to be notified of events. Remember that the providers calls
         * should all be asynchronous (every request must return immediately, and the result be posted
         * later on to all the callbacks registered here).
         */
        @Override
        public void registerCallback(IProviderCallback cb) {
            synchronized (mCallbacks) {
                mCallbacks.add(cb);
            }
        }

        /**
         * Removes a registered callback
         */
        public void unregisterCallback(IProviderCallback cb) {
            synchronized (mCallbacks) {
                mCallbacks.remove(cb);
            }
        }

        /**
         * Request authenticatication of the user against the providers. It is up to the providers to
         * store the credentials and grab them through a configuration activity. See providers.Constants
         * for more details about the configuration activity.
         *
         * @return true if the authentication request succeeded, false otherwise
         */
        @Override
        public boolean login() throws RemoteException {
            return true;
        }

        /**
         * Returns whether or not the providers is fully setup and ready to use (for example, if the
         * user entered his login and password to authenticate to the service in the configuration
         * activity).
         * As long as this returns false, the app won't try to login or do any action on the providers.
         *
         * @return true if the providers is configured and ready to use
         */
        @Override
        public boolean isSetup() throws RemoteException {
            return mLocalProvider.isSetup();
        }

        /**
         * Indicates whether or not this providers has successfully authenticated against the remote
         * providers servers.
         * In case an authentication is not needed, this method should simply return true at all
         * times. No login attempt will be then made by the app.
         *
         * @return true if this providers is authenticated and ready to be used, false otherwise
         */
        @Override
        public boolean isAuthenticated() throws RemoteException {
            return true;
        }

        /**
         * Informs whether or not this providers is infinite (ie. it's a cloud providers that allows
         * you to access a virtually unlimited number of tracks, such as Spotify or Deezer ; the local
         * storage or a simple storage providers would return false).
         *
         * @return true if there's no defined number of tracks, false otherwise
         */
        @Override
        public boolean isInfinite() throws RemoteException {
            // This is not a cloud provider, thus is not infinite
            return false;
        }

        /**
         * Returns the list of all albums
         * This method call is only valid when isInfinite returns false
         *
         * @return A list of all the albums available on the providers
         */
        @Override
        public List<Album> getAlbums() throws RemoteException {
            List<Album> albums = mLocalProvider.getAlbums();
            for (Album album : albums) {
                if (album.getProvider() == null && mIdentifier != null) {
                    album.setProvider(mIdentifier);
                }
            }

            return albums;
        }

        /**
         * Returns the list of all artists
         * This method call is only valid when isInfinite returns false
         *
         * @return A list of all the artists available on the providers
         */
        @Override
        public List<Artist> getArtists() throws RemoteException {
            List<Artist> artists = mLocalProvider.getArtists();
            for (Artist artist : artists) {
                if (artist.getProvider() == null && mIdentifier != null) {
                    artist.setProvider(mIdentifier);
                }
            }

            return artists;
        }

        /**
         * Returns the list of all songs
         * This method call is only valid when isInfinite returns false
         *
         * @return A list of all songs available on the providers
         */
        @Override
        public List<Song> getSongs(int offset, int range) throws RemoteException {
            List<Song> songs = mLocalProvider.getSongs(offset, range);
            for (Song song : songs) {
                if (song.getProvider() == null && mIdentifier != null) {
                    song.setProvider(mIdentifier);
                }
            }
            return songs;
        }

        /**
         * Returns the list of all playlist on this provider
         * This method is valid for both infinite and defined providers.
         *
         * @return A list of all playlist on this providers
         */
        @Override
        public List<Playlist> getPlaylists() throws RemoteException {
            List<Playlist> playlists = mLocalProvider.getPlaylists();
            for (Playlist playlist : playlists) {
                if (playlist.getProvider() == null && mIdentifier != null) {
                    playlist.setProvider(mIdentifier);
                }
            }
            return playlists;
        }

        /**
         * Returns the list of all genre on this provider
         * Since this is a finite provider, it will populate genre with songs,infinite provider may handle it differently
         *
         * @return a list of all genre
         */
        @Override
        public List<Genre> getGenres() {
            return mLocalProvider.getGenres();
        }

        @Override
        public void startSearch(String query) {
            mLocalProvider.startSearch(query);
        }

        @Override
        public Bitmap getLogo(String ref) throws RemoteException {
            if (LOGO_REF.equals(ref)) {
                return ((BitmapDrawable) getResources().getDrawable(R.drawable.ic_storage)).getBitmap();
            }

            return null;
        }

        @Override
        public List<String> getSupportedRosettaPrefix() throws RemoteException {
            return null;
        }

        @Override
        public void setPlaylistOfflineMode(String ref, boolean offline) throws RemoteException {
            // ignore
        }

        @Override
        public void setOfflineMode(boolean offline) throws RemoteException {
            // ignore
        }

        /**
         * Returns a particular song
         * The providers may not return all the information immediately, and must set the IsLoaded
         * flag accordingly.
         * Song information should be then updated with onSongUpdate callback.
         * It must not return null however.
         *
         * @param ref The reference of the song
         */
        @Override
        public Song getSong(String ref) throws RemoteException {
            Song s = mLocalProvider.getSong(ref);
            if (s != null) {
                s.setProvider(mIdentifier);
                s.setSourceLogo(LOGO_REF);
                s.setOfflineStatus(BoundEntity.OFFLINE_STATUS_READY);
                s.setAvailable(true);
            } else {
                Log.e(TAG, "Cannot find song " + ref);
            }

            return s;
        }

        @Override
        public Artist getArtist(String ref) throws RemoteException {
            Artist a = mLocalProvider.getArtist(ref);
            if (a != null) {
                a.setProvider(mIdentifier);
                a.setSourceLogo(LOGO_REF);
            }
            return a;
        }

        @Override
        public Album getAlbum(String ref) throws RemoteException {
            Album a = mLocalProvider.getAlbum(ref);
            if (a != null) {
                a.setProvider(mIdentifier);
                a.setSourceLogo(LOGO_REF);
            }
            return a;
        }

        @Override
        public Playlist getPlaylist(String ref) throws RemoteException {
            Playlist p = mLocalProvider.getPlaylist(ref);
            if (p != null) {
                p.setProvider(mIdentifier);
                p.setSourceLogo(LOGO_REF);
            }
            return p;
        }

        @Override
        public boolean getArtistArt(Artist entity, IArtCallback callback) throws RemoteException {
            return false;
        }

        @Override
        public boolean getAlbumArt(Album entity, IArtCallback callback) throws RemoteException {
            return mLocalProvider.getAlbumArt(entity.getRef(), callback);
        }

        @Override
        public boolean getPlaylistArt(Playlist entity, IArtCallback callback) throws RemoteException {
            return false;
        }

        @Override
        public boolean getSongArt(Song entity, IArtCallback callback) throws RemoteException {
            return mLocalProvider.getSongArt(entity.getRef(), callback);
        }

        @Override
        public boolean fetchArtistAlbums(String artistRef) {
            return false;
        }

        @Override
        public boolean fetchAlbumTracks(String albumRef) throws RemoteException {
            return false;
        }


        /**
         * Tells the providers the name of the local audio socket to use to push data. This string
         * should be passed to AudioSocket in order to push audio to the proper location. The app
         * manages audio crossfading and properly locks each socket to ensure a smooth playback
         * between the various providers.
         *
         * @param socketName The name of the socket to use
         */
        @Override
        public void setAudioSocketName(final String socketName) {
            try {
                if (mAudioSocket == null) {
                    mAudioSocket = new AudioClientSocket();
                }
                mAudioSocket.connect(socketName);
                mAudioSocket.writeFormatData(2, 44100);
                mAudioSocket.setCallback(PluginService.this);
            } catch (IOException e) {
                Log.e(TAG, "Unable to open the audio socket!", e);
            }
        }

        /**
         * Returns the time, in milliseconds, the providers needs a call to prefetchSong() before the
         * end of the current song.
         * For instance, a cloud providers might need more time to prepare a song than a local providers
         * to ensure smooth and gapless playback.
         */
        @Override
        public long getPrefetchDelay() throws RemoteException {
            // Let's say 10 seconds
            return 10 * 1000;
        }

        /**
         * Requests the providers to prepare the playback of a song (ie. start downloading it and/or
         * caching it in RAM), as it is likely the next song to be played.
         * Providers may choose to implement or not this method - it is called by the app so that
         * the providers can prepare the next song, but no particular result is expected.
         *
         * @param ref The unique reference of the song
         */
        @Override
        public void prefetchSong(String ref) throws RemoteException {
            // TODO
        }

        /**
         * Requests the providers to play the song referenced by the provided ref string.
         *
         * @param ref The unique reference of the song
         * @return false in case of error, otherwise true
         */
        @Override
        public boolean playSong(final String ref) throws RemoteException {
            mLocalProvider.playSong(ref);
            return true;
        }

        @Override
        public void pause() throws RemoteException {
            mLocalProvider.pause(true);

            synchronized (mCallbacks) {
                for (IProviderCallback cb : mCallbacks) {
                    try {
                        cb.onSongPaused(mIdentifier);
                    } catch (DeadObjectException e) {
                        removeCallback(cb);
                    } catch (RemoteException e) {
                        Log.e(TAG, "RemoteException when notifying a callback", e);
                    }
                }
            }
        }

        @Override
        public void resume() throws RemoteException {
            mLocalProvider.resume();
        }

        @Override
        public boolean onUserSwapPlaylistItem(int oldPosition, int newPosition, String playlistRef) {
            return mLocalProvider.onUserSwapPlaylistItem(oldPosition, newPosition, playlistRef);
        }

        @Override
        public boolean deletePlaylist(String playlistRef) {
            return mLocalProvider.deletePlaylist(playlistRef);
        }

        @Override
        public boolean renamePlaylist(String playlistRef, String title) throws RemoteException {
            boolean result = mLocalProvider.renamePlaylist(playlistRef, title);
            if (result) {
                providerCallback.playlistUpdated(getPlaylist(playlistRef));
            }
            return result;
        }

        @Override
        public boolean deleteSongFromPlaylist(int songPosition, String playlistRef) {
            return mLocalProvider.deleteSongFromPlaylist(songPosition, playlistRef);
        }

        @Override
        public boolean addSongToPlaylist(String songRef, String playlistRef, ProviderIdentifier providerIdentifier) {
            return mLocalProvider.addSongToPlaylist(songRef, playlistRef);
        }

        @Override
        public String addPlaylist(String playlistName) {
            return mLocalProvider.addPlaylist(playlistName);
        }

        @Override
        public void seek(long timeMs) {
          mLocalProvider.seekTo(timeMs);
        }
    };

    @Override
    public void onAudioData(AudioSocket socket, Plugin.AudioData.Builder message) {

    }

    @Override
    public void onAudioResponse(AudioSocket socket, Plugin.AudioResponse.Builder message) {
        synchronized (mAudioWrittenLock) {
            mAudioWritten = message.getWritten();
            mAudioWrittenLock.notifyAll();
        }
    }

    @Override
    public void onRequest(AudioSocket socket, Plugin.Request.Builder message) {

    }

    @Override
    public void onFormatInfo(AudioSocket socket, Plugin.FormatInfo.Builder message) {

    }

    @Override
    public void onBufferInfo(AudioSocket socket, Plugin.BufferInfo.Builder message) {

    }
}
