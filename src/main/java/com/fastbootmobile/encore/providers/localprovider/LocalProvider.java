package com.fastbootmobile.encore.providers.localprovider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;

import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Genre;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IArtCallback;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class LocalProvider {
    private static final String TAG = "LocalProvider";

    private static final String PREFIX_SONG = "local:song:";
    private static final String PREFIX_ALBUM = "local:album:";
    private static final String PREFIX_ARTIST = "local:artist:";
    private static final String PREFIX_PLAYLIST = "local:playlist:";

    private Uri mUri;
    private HashMap<String, LocalSong> mSongs;
    private ContentResolver mContentResolver;
    private HashMap<String, Playlist> mPlaylists;
    private LocalSong mCurrentSong;
    private MediaCodec mDecoder;
    private MediaExtractor mExtractor;
    private boolean mChangeMusic;
    private MediaCodec.BufferInfo mInfo;
    private ByteBuffer[] mOutputBuffers;
    private ByteBuffer[] mInputBuffers;
    private Context mContext;
    private MediaFormat mFormat;
    private LocalCallback mCallback;

    private HashMap<String, Artist> mArtists;
    private HashMap<String, Album> mAlbums;
    private HashMap<String, Genre> mGenres;
    private HashMap<String, Long> mAlbumsId;
    private Handler mHandler = new Handler();
    private boolean mSetup;
    private boolean mPaused;
    private SearchResult mSearchResult;
    private boolean mIsEOS;
    private int mPendingOutIndex = -1;
    private int mPendingSize;


    private final ContentObserver mAlbumContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean self) {
            try {
                fetchAlbums();
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot read albums because of a security exception", e);
            }
        }

        @Override
        public void onChange(boolean self, Uri uri) {
            onChange(self);
        }
    };

    private final ContentObserver mArtistContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean self) {
            try {
                fetchArtists();
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot read artists because of a security exception", e);
            }
        }

        @Override
        public void onChange(boolean self, Uri uri) {
            onChange(self);
        }
    };

    private final ContentObserver mGenreContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean self) {
            try {
                fetchGenres(null);
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot read genres because of a security exception", e);
            }
        }

        @Override
        public void onChange(boolean self, Uri uri) {
            onChange(self);
        }
    };

    public LocalProvider(Uri uri, ContentResolver cr, LocalCallback cb, Context context) {
        mCallback = cb;
        mContentResolver = cr;
        mUri = uri;
        mSongs = new HashMap<>();
        mAlbums = new HashMap<>();
        mArtists = new HashMap<>();
        mPlaylists = new HashMap<>();
        mGenres = new HashMap<>();
        mAlbumsId = new HashMap<>();
        mChangeMusic = true;
        mContext = context;
        mAudioPushRunnable.start();
        mSetup = false;
    }

    public void notifyIdentifier(final ProviderIdentifier id) {
        Set<String> keys = mSongs.keySet();
        for (String key : keys) {
            LocalSong lSong = mSongs.get(key);
            if (lSong != null) {
                lSong.getSong().setProvider(id);
            }
        }

        keys = mAlbums.keySet();
        for (String key : keys) {
            mAlbums.get(key).setProvider(id);
        }

        keys = mArtists.keySet();
        for (String key : keys) {
            mArtists.get(key).setProvider(id);
        }

        keys = mPlaylists.keySet();
        for (String key : keys) {
            mPlaylists.get(key).setProvider(id);
        }

        keys = mGenres.keySet();
        for (String key : keys) {
            mGenres.get(key).setProvider(id);
        }
    }

    /**
     * The main function to use for polling all the local content, use it in a thread so it doesn't slow down the whole application
     */
    public void poll() {
        mSetup = false;
        mContentResolver.registerContentObserver(mUri, true, mSongContentObserver);
        mContentResolver.registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, mPlaylistContentObserver);
        mContentResolver.registerContentObserver(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, true, mGenreContentObserver);
        mContentResolver.registerContentObserver(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, true, mAlbumContentObserver);
        mContentResolver.registerContentObserver(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, true, mArtistContentObserver);
        mSetup = true;

        try {
            fetchAlbums();
            fetchArtists();
            fetchSongs();
            fetchPlaylists(null);
            fetchGenres(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI);
        } catch (SecurityException e) {
            // This happened once on a Nexus Player.
            Log.e(TAG, "Security exception when fetching local provider data! " + e.getMessage());
        }
    }

    private void fetchAlbums() {
        final String[] proj = {"*"};
        final Cursor cur = mContentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, proj, null, null, null);
        if (cur != null) {
            if (cur.moveToFirst()) {
                final int albumName = cur.getColumnIndex(MediaStore.Audio.AlbumColumns.ALBUM);
                final int artistName = cur.getColumnIndex(MediaStore.Audio.AlbumColumns.ARTIST);
                final int albumKey = cur.getColumnIndex(MediaStore.Audio.AlbumColumns.ALBUM_KEY);
                final int yearKey = cur.getColumnIndex(MediaStore.Audio.AlbumColumns.LAST_YEAR);
                final int idKey = cur.getColumnIndex(MediaStore.Audio.Albums._ID);

                do {
                    Album album = new Album(PREFIX_ALBUM + getAlbumUniqueName(cur.getString(albumKey), cur.getString(artistName)));
                    album.setName(cur.getString(albumName));
                    album.setIsLoaded(true);
                    album.setSourceLogo(PluginService.LOGO_REF);
                    album.setYear(cur.getInt(yearKey));

                    // we get the contents of the album
                    mAlbums.put(album.getRef(), album);
                    mAlbumsId.put(album.getRef(), cur.getLong(idKey));
                } while (cur.moveToNext());
            }
            cur.close();
        }

        // first we poll all the musics
        mContentResolver.registerContentObserver(mUri, true, mSongContentObserver);
        mContentResolver.registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, mSongContentObserver);
    }

    public void fetchSongs() {
        // First we poll all the songs
        final Cursor cur = mContentResolver.query(mUri, null,
                MediaStore.Audio.Media.IS_MUSIC + " = 1", null, null);

        if (cur != null) {
            if (cur.moveToFirst()) {
                // Fetch all the columns we are interested in
                int artistKey = cur.getColumnIndex(MediaStore.Audio.Media.ARTIST_KEY);
                int albumKey = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_KEY);
                int titleKey = cur.getColumnIndex(MediaStore.Audio.Media.TITLE_KEY);
                int artistColumn = cur.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int titleColumn = cur.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int albumIdColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int durationColumn = cur.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int idColumn = cur.getColumnIndex(MediaStore.Audio.Media._ID);
                int yearColumn = cur.getColumnIndex(MediaStore.Audio.Media.YEAR);

                do {
                    // We create the unique ID the song have
                    final String uniquename = getSongUniqueName(cur.getString(artistKey), cur.getString(albumKey), cur.getString(titleKey));

                    Song s = new Song(PREFIX_SONG + uniquename);
                    s.setAvailable(true);
                    s.setTitle(cur.getString(titleColumn));

                    String artistSrc = cur.getString(artistKey);
                    if (artistSrc != null) {
                        s.setArtist(PREFIX_ARTIST + getArtistUniqueName(artistSrc));
                    }
                    s.setDuration((int) cur.getLong(durationColumn));
                    s.setAlbum(PREFIX_ALBUM + getAlbumUniqueName(cur.getString(albumKey), cur.getString(artistColumn)));

                    Album album = mAlbums.get(s.getAlbum());
                    if (album != null) {
                        album.addSong(s.getRef());
                    }

                    s.setYear(cur.getInt(yearColumn));
                    s.setIsLoaded(true); // Local songs are always fully loaded
                    s.setOfflineStatus(BoundEntity.OFFLINE_STATUS_READY); // Local songs are always offline
                    s.setSourceLogo(PluginService.LOGO_REF);

                    //we keep LocalSongs so we still have the id informations
                    final Long id = cur.getLong(idColumn);
                    final Long albumId = cur.getLong(albumIdColumn);

                    mSongs.put(s.getRef(), new LocalSong(s, id, albumId));
                    mCallback.songUpdated(s);
                } while (cur.moveToNext());
            }
            cur.close();
        }

        for (Album album : mAlbums.values()) {
            mCallback.albumUpdated(album);
        }
    }

    public void fetchArtists() {
        final String[] proj = {"*"};

        // we poll the artists
        final Cursor cur = mContentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, proj, null, null, null);
        if (cur != null) {
            if (cur.moveToFirst()) {
                final int artistName = cur.getColumnIndex(MediaStore.Audio.ArtistColumns.ARTIST);
                final int artistKey = cur.getColumnIndex(MediaStore.Audio.ArtistColumns.ARTIST_KEY);
                final int artistId = cur.getColumnIndex(MediaStore.Audio.Artists._ID);
                do {
                    String artistKeyStr = cur.getString(artistKey);
                    if (artistKeyStr == null || artistKeyStr.isEmpty()) {
                        artistKeyStr = cur.getString(artistName);
                    }
                    if (artistKeyStr == null || artistKeyStr.isEmpty()) {
                        artistKeyStr = String.valueOf(cur.getLong(artistId));
                    }

                    Artist artist = new Artist(PREFIX_ARTIST + getArtistUniqueName(artistKeyStr));
                    artist.setName(cur.getString(artistName));
                    artist.setIsLoaded(true);

                    // we get the albums from this artist
                    artist = getAlbumsArtists(artist, MediaStore.Audio.Artists.Albums.getContentUri("external", cur.getLong(artistId)));
                    if (artist != null) {
                        artist.setSourceLogo(PluginService.LOGO_REF);
                        mArtists.put(artist.getRef(), artist);
                        mCallback.artistUpdated(artist);
                    }
                } while (cur.moveToNext());
            }

            cur.close();
        }
    }

    public void fetchPlaylists(String idPlaylist) {
        Uri uri;

        uri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        String request = null;
        if (idPlaylist != null) {
            request = MediaStore.Audio.Playlists._ID + " = " + idPlaylist;
        }
        Cursor cur;
        String[] proj = {"*"};

        // We now poll the playlists
        cur = mContentResolver.query(uri, proj,
                request, null, null);

        if (cur != null && cur.moveToFirst()) {
            final int idKey = cur.getColumnIndex(MediaStore.Audio.Playlists._ID);
            final int nameKey = cur.getColumnIndex(MediaStore.Audio.Playlists.NAME);

            do {
                Long id = cur.getLong(idKey);
                String name = cur.getString(nameKey);
                Playlist play = new Playlist(PREFIX_PLAYLIST + getPlaylistUniqueName(Long.toString(id)));
                play.setName(name);
                play.setIsLoaded(true);

                // we get the content of the playlist
                play = getPlaylist(MediaStore.Audio.Playlists.Members.getContentUri("external", id), play);
                if (play != null) {
                    mPlaylists.put(play.getRef(), play);

                    // we give to the app the new playlists when we finish polling it
                    mCallback.playlistUpdated(play);
                }
            } while (cur.moveToNext());
        }

        if (cur != null) {
            cur.close();
        }
    }

    public void fetchGenres(Uri uri) {
        if (uri == null)
            uri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
        Cursor cur;
        String[] proj = {"*"};

        // now we poll the genre
        cur = mContentResolver.query(uri, proj, null, null, null);

        if (cur != null) {
            if (cur.moveToFirst()) {
                final int idKey = cur.getColumnIndex(MediaStore.Audio.Genres._ID);
                final int nameKey = cur.getColumnIndex(MediaStore.Audio.Genres.NAME);
                do {
                    Long id = cur.getLong(idKey);
                    String name = cur.getString(nameKey);
                    Genre genre = new Genre("local:genre:" + MD5(name));
                    genre.setName(name);
                    genre.setIsLoaded(true);
                    getGenreSongs(MediaStore.Audio.Genres.Members.getContentUri("external", id), genre);
                    genre.setSourceLogo(PluginService.LOGO_REF);
                    mGenres.put(genre.getRef(), genre);
                    mCallback.genreUpdated(genre);
                } while (cur.moveToNext());
            }

            cur.close();
        }
    }

    public boolean getSongArt(String songRef, IArtCallback callback) {
        LocalSong ls = getLocalSong(songRef);
        if (ls == null) {
            Log.d(TAG, "Cannot find local song " + songRef + " for art request");
            return false;
        }

        Long albumId = ls.getAlbumId();
        if (albumId == null) {
            return false;
        }

        return getAlbumArt(albumId, callback);
    }

    public boolean getAlbumArt(String albumRef, IArtCallback callback) {
        Long albumId = mAlbumsId.get(albumRef);
        if (albumId == null) {
            return false;
        }

        return getAlbumArt(albumId, callback);
    }

    private boolean getAlbumArt(long albumId, final IArtCallback callback) {
        Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(artworkUri, albumId);

        try {
            final InputStream in = mContentResolver.openInputStream(uri);
            new Thread() {
                public void run() {
                    Bitmap output = BitmapFactory.decodeStream(in);
                    try {
                        in.close();
                    } catch (IOException ignore) {
                    }

                    try {
                        callback.onArtLoaded(output);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Got exception in the app when reporting album art", e);
                    }
                }
            }.start();

            // We have an URI, so in theory we should be good
            return true;
        } catch (Exception e) {
            // we can't get an album art so we return false
            return false;
        }
    }

    ContentObserver mSongContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean self) {
            fetchSongs();
        }

        @Override
        public void onChange(boolean self, Uri uri) {
            fetchSongs();
        }
    };

    ContentObserver mPlaylistContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean self) {
            onChange(self, null);
        }

        @Override
        public void onChange(boolean self, Uri uri) {
            Log.d(TAG, uri.getPath());
            fetchPlaylists(null);
        }
    };

    /**
     * @return if the provider finished polling the content
     */
    public boolean isSetup() {
        return mSetup;
    }

    /**
     * Returns an unique song identifier for the provided values
     * @param artistKey The internal key of the artist
     * @param albumKey The internal key of the album
     * @param titleKey The internal key of the title
     * @return An unique song identifier for the song
     */
    private String getSongUniqueName(String artistKey, String albumKey, String titleKey) {
        return MD5(artistKey + albumKey + titleKey);
    }

    private String getAlbumUniqueName(String albumKey, String artistName) {
        return MD5(albumKey + artistName);
    }

    private String getArtistUniqueName(String artistKey) {
        return MD5(artistKey);
    }

    private String getPlaylistUniqueName(String playlistId) {
        return MD5(playlistId);
    }

    /**
     * Get the albums by the given artist
     *
     * @param artist the artist
     * @param uri    the uri of the artist
     * @return the artist with all its albums
     */
    public Artist getAlbumsArtists(Artist artist, Uri uri) {
        Cursor albums = mContentResolver.query(uri, null, null, null, null);

        if (albums != null) {
            // we only need the name of the album to generate the album local id
            final int albumKey = albums.getColumnIndex(MediaStore.Audio.AlbumColumns.ALBUM_KEY);

            if (albums.moveToFirst()) {
                do {
                    artist.addAlbum(PREFIX_ALBUM + getAlbumUniqueName(albums.getString(albumKey), artist.getName()));
                } while (albums.moveToNext());
            }

            albums.close();
        }
        return artist;
    }

    /**
     * Fetch the playlist content
     *
     * @param uri  the playlist's uri
     * @param play the playlist
     * @return the playlist with its content
     */
    public Playlist getPlaylist(Uri uri, Playlist play) {
        //what we info do we need
        String[] projection = {
                MediaStore.Audio.Playlists.Members.TITLE_KEY,
                MediaStore.Audio.Playlists.Members.ARTIST_KEY,
                MediaStore.Audio.Playlists.Members.ALBUM_KEY
        };
        Cursor tracks = mContentResolver.query(uri, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0 ", null, null);

        if (tracks != null) {
            int artistKeyColumn = tracks.getColumnIndex(MediaStore.Audio.Playlists.Members.ARTIST_KEY);
            int albumKeyColumn = tracks.getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM_KEY);
            int titleKeyColumn = tracks.getColumnIndex(MediaStore.Audio.Playlists.Members.TITLE_KEY);

            if (tracks.moveToFirst()) {
                do {
                    // for each song we get its unique name and we put it in the playlist
                    play.addSong(PREFIX_SONG + getSongUniqueName(tracks.getString(artistKeyColumn),
                            tracks.getString(albumKeyColumn),
                            tracks.getString(titleKeyColumn)));
                } while (tracks.moveToNext());
            }
            tracks.close();
        }
        return play;
    }

    /**
     * Get the songs of the genre given
     *
     * @param uri   the uri of the genre
     * @param genre the genre to add the songs to
     */
    public void getGenreSongs(Uri uri, Genre genre) {
        String[] projection = {
                MediaStore.Audio.Genres.Members.TITLE_KEY,
                MediaStore.Audio.Genres.Members.ARTIST_KEY,
                MediaStore.Audio.Genres.Members.ALBUM_KEY
        };
        Cursor tracks = mContentResolver.query(uri, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0 ", null, null);
        if (tracks != null) {
            int titleKeyColumn = tracks.getColumnIndex(MediaStore.Audio.Genres.Members.TITLE_KEY);
            int artistKeyColumn = tracks.getColumnIndex(MediaStore.Audio.Genres.Members.ARTIST_KEY);
            int albumKeyColumn = tracks.getColumnIndex(MediaStore.Audio.Genres.Members.ALBUM_KEY);
            if (tracks.moveToNext()) {
                do {
                    genre.addSong(PREFIX_SONG + getSongUniqueName(tracks.getString(artistKeyColumn),
                            tracks.getString(albumKeyColumn),
                            tracks.getString(titleKeyColumn)));
                } while (tracks.moveToNext());
            }
            tracks.close();
        }
    }

    /**
     * @return returns a list of the songs
     */
    public List<Song> getSongs(int offset, int range) {
        final ArrayList<Song> songs = new ArrayList<Song>();
        final Collection<LocalSong> localSongs = new ArrayList<>(mSongs.values());

        int index = 0;
        for (LocalSong song : localSongs) {
            if (index < offset) {
                ++index;
                continue;
            }
            Song s = song.getSong();
            if (s != null) {
                s.setSourceLogo(PluginService.LOGO_REF);
                songs.add(s);
            }

            if (index >= offset + range) {
                break;
            }

            ++index;
        }
        return songs;
    }

    /**
     * @return A list of all genres
     */
    public List<Genre> getGenres() {
        return new ArrayList<Genre>(mGenres.values());
    }

    /**
     * @return returns a list of the Artists
     */
    public List<Artist> getArtists() {
        return new ArrayList<Artist>(mArtists.values());
    }

    /**
     * @return returns a list of the Albums
     */
    public List<Album> getAlbums() {
        return new ArrayList<Album>(mAlbums.values());
    }

    /**
     * @param md5 the String to hash
     * @return The String md5 hashed
     */
    public String MD5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte anArray : array) {
                sb.append(Integer.toHexString((anArray & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ignored) {
        }
        return null;
    }

    /**
     * @return returns a list of the Playlists
     */
    public List<Playlist> getPlaylists() {
        return new ArrayList<>(mPlaylists.values());
    }

    /**
     * @param ref the unique reference of the Song wanted
     * @return the song wanted
     */
    public Song getSong(String ref) {
        Song s = null;

        if (!mSetup) {
            Log.e(TAG, "Trying to load song before the end of the initialisation of the plugin, aborting");
            return null;
        }

        try {
            LocalSong lS = mSongs.get(ref);
            if (lS != null) {
                s = lS.getSong();
                if (s != null) {
                    s.setSourceLogo(PluginService.LOGO_REF);
                    mCallback.artistUpdated(mArtists.get(s.getArtist()));
                }
            }
        } catch (Exception e) {
            Log.d("LocalProvider", "Song not found exception " + e.getMessage());
        }

        return s;
    }

    public Artist getArtist(String ref) {
        Artist a = mArtists.get(ref);
        if (a != null) {
            a.setSourceLogo(PluginService.LOGO_REF);
        }
        return a;
    }

    public Album getAlbum(String ref) {
        Album a = mAlbums.get(ref);
        if (a != null) {
            a.setSourceLogo(PluginService.LOGO_REF);
        }
        return a;
    }


    /**
     * @param ref the unique reference of the LocalSong wanted
     * @return the LocalSong wanted
     */
    public LocalSong getLocalSong(String ref) {
        LocalSong s = null;
        try {
            s = mSongs.get(ref);
            if (s.getSong() != null) {
                s.getSong().setSourceLogo(PluginService.LOGO_REF);
            }
        } catch (Exception e) {
            Log.d("LocalProvider", "Song not found", e);
        }

        return s;
    }

    /**
     * @param ref the unique reference of the Playlist
     * @return the playlist
     */
    public Playlist getPlaylist(String ref) {
        return mPlaylists.get(ref);
    }

    /**
     * @param ref the unique reference of the Playlist
     * @return the playlist id
     */
    public long getPlaylistId(String ref) {
        String name = getPlaylist(ref).getName();
        long id = -1;
        Cursor cursor = mContentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Playlists._ID},
                MediaStore.Audio.Playlists.NAME + "=?",
                new String[]{name}, null);
        if (cursor != null && cursor.moveToFirst()) {
            id = cursor.getLong(0);
            cursor.close();
        }
        return id;
    }

    /**
     * Swaps two elements of a playlist
     *
     * @param oldPosition the position of the first element
     * @param newPosition the position of the second element
     * @param playlistRef the reference of the playlist
     * @return true if the change has been saved
     */
    public boolean onUserSwapPlaylistItem(int oldPosition, int newPosition, String playlistRef) {
        long id = getPlaylistId(playlistRef);
        Playlist playlist = getPlaylist(playlistRef);
        //We get the LocalSong corresponding
        LocalSong oldMusic = getLocalSong(playlist.songsList().get(oldPosition));
        LocalSong newMusic = getLocalSong(playlist.songsList().get(newPosition));

        // We modify the playlist corresponding
        playlist.setSong(oldPosition, newMusic.getSong().getRef());
        playlist.setSong(newPosition, oldMusic.getSong().getRef());

        // We update the playlist list and the app list
        mPlaylists.put(playlistRef, playlist);
        mCallback.playlistUpdated(playlist);

        // Now we modify the database
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);

        ContentValues oldValues, newValues;
        oldValues = new ContentValues();
        newValues = new ContentValues();

        // We set the values to update
        oldValues.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, oldPosition);
        oldValues.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, newMusic.getId());
        newValues.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, newPosition);
        newValues.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, oldMusic.getId());

        // We update the database
        mContentResolver.update(uri, oldValues, MediaStore.Audio.Playlists.Members.PLAY_ORDER + "=" + oldPosition, null);
        mContentResolver.update(uri, newValues, MediaStore.Audio.Playlists.Members.PLAY_ORDER + "=" + newPosition, null);

        // Errors aren't supported for now
        return true;
    }

    /**
     * Deletes a playlist
     *
     * @param playlistRef the reference of the playlist
     * @return true if the playlist has been deleted
     */
    public boolean deletePlaylist(String playlistRef) {
        Log.d(TAG, "Deleting playlist " + playlistRef);

        long id = getPlaylistId(playlistRef);
        String where = MediaStore.Audio.Playlists._ID + "=?";
        String[] whereVal = {String.valueOf(id)};

        mContentResolver.delete(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, where, whereVal);

        mPlaylists.remove(playlistRef);
        mCallback.playlistRemoved(playlistRef);

        // Errors aren't supported for now
        return true;
    }

    /**
     * Renames a playlist
     * @param playlistRef The reference of the playlist
     * @param title The new title of the playlist
     * @return true if success
     */
    public boolean renamePlaylist(String playlistRef, String title) {
        long id = getPlaylistId(playlistRef);

        ContentValues values = new ContentValues();
        String where = MediaStore.Audio.Playlists._ID + " =? ";
        String[] whereVal = { Long.toString(id) };
        values.put(MediaStore.Audio.Playlists.NAME, title);
        mContentResolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values, where, whereVal);

        Playlist playlist = mPlaylists.get(playlistRef);
        playlist.setName(title);

        mCallback.playlistUpdated(playlist);

        // Errors aren't supported for now
        return true;
    }

    /**
     * Delete a song from a playlist
     * **Warning** the delete use the song's id and not the song's position for now, so all duplicate will also be deleted
     *
     * @param songPosition the position of the song in the playlist
     * @param playlistRef  the reference of the playlist
     * @return true if the song has been deleted
     */
    public boolean deleteSongFromPlaylist(int songPosition, String playlistRef) {
        long id = getPlaylistId(playlistRef);
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);

        // We update the playlist
        Playlist pl = getPlaylist(playlistRef);
        pl.removeSong(songPosition);
        mPlaylists.put(playlistRef, pl);

        // We update the app
        mCallback.playlistUpdated(pl);

        LocalSong loc = getLocalSong(pl.songsList().get(songPosition));//we need the LocalSong to get the song id
        //See warning: all song with the same AUDIO_ID will be deleted
        int rowsDeleted = mContentResolver.delete(uri, MediaStore.Audio.Playlists.Members.AUDIO_ID + "=" + loc.getId(), null);

        Log.d(TAG, "we delete song " + songPosition + " Rows deleted: " + rowsDeleted);
        //the song have been deleted if rows have been deleted
        return rowsDeleted > 0;
    }

    /**
     * Adds a song to a playlist
     *
     * @param songRef     the reference of the song
     * @param playlistRef the reference of the playlist
     * @return true if the song has been added
     */
    public boolean addSongToPlaylist(String songRef, String playlistRef) {
        //we update the local content
        Playlist pl = getPlaylist(playlistRef);


        long id = getPlaylistId(playlistRef);
        Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
        ContentValues contentValues = new ContentValues();
        String[] cols = new String[]{
                "count(*)"
        };
        //we get the length of the playlist to set the PLAY_ORDER right
        Cursor cur = mContentResolver.query(uri, cols, null, null, null);
        if (cur != null) {
            cur.moveToFirst();
            final int base = cur.getInt(0);
            cur.close();
            contentValues.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, base);
            contentValues.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, getLocalSong(songRef).getId());
            uri = mContentResolver.insert(uri, contentValues);
            if (uri != null) {
                pl.addSong(songRef);
                mPlaylists.put(playlistRef, pl);
                //we update the app
                mCallback.playlistUpdated(pl);
            }
        }

        return uri != null;
    }

    /**
     * Adds a new playlist
     *
     * @param playlistName the name of the playlist
     * @return if the playlist has been added
     */
    public String addPlaylist(String playlistName) {
        // we set the new playlist locally
        ContentValues mInserts = new ContentValues();
        mInserts.put(MediaStore.Audio.Playlists.NAME, playlistName);
        mInserts.put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis());
        mInserts.put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis());
        Uri uri = mContentResolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mInserts);

        if (uri == null) {
            return null;
        } else {
            String ref = PREFIX_PLAYLIST + getPlaylistUniqueName(Long.toString(ContentUris.parseId(uri)));

            // we update the app
            Playlist pl = new Playlist(ref);
            pl.setName(playlistName);
            pl.setIsLoaded(true);
            mPlaylists.put(ref, pl);
            mCallback.playlistUpdated(pl);

            return ref;
        }
    }

    /**
     * Plays the given song
     *
     * @param ref the unique reference of the song
     */
    public void playSong(String ref) {
        // Pause the current song (if any), without notifying the app
        pause(false);

        mCurrentSong = null;
        mChangeMusic = true;//we say that we change music
        mCurrentSong = getLocalSong(ref);//we set the new song

        synchronized (this) {
            // We reset the decoder for this song
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
            }
            getCurrentDecoder();
            mPendingOutIndex = -1;
        }

        mPaused = false;
        mIsEOS = false;

        // we resume the decoder thread
        synchronized (mAudioPushRunnable) {
            mAudioPushRunnable.notify();
            mPendingOutIndex = -1;
        }


        mCallback.songPlaying();

    }

    public void pause(boolean notify) {
        if (mDecoder != null && !mPaused) {
            mPaused = true;
            if (notify) {
                mCallback.songPaused();
            }
        }
    }

    public void resume() {
        if (mDecoder != null && mPaused) {
            mPaused = false;
            if (mIsEOS && mCurrentSong != null) {
                playSong(mCurrentSong.getSong().getRef());
            } else if (mCurrentSong != null) {
                mDecoder.flush();

                synchronized (mAudioPushRunnable) {
                    mAudioPushRunnable.notifyAll();
                }
                mCallback.songPlaying();
            }
        }
    }

    /**
     * Sets a new decoder if the music changed
     */
    private void getCurrentDecoder() {
        synchronized (this) {
            if (mChangeMusic) {
                mChangeMusic = false;
                mExtractor = new MediaExtractor();
                try {
                    mExtractor.setDataSource(mContext, mCurrentSong.getURI(), null);
                } catch (Exception e) {
                    Log.d("LocalProvider", "Data source error", e);
                    return;
                }

                // if there is a track to play, use it as format info
                if (mExtractor.getTrackCount() > 0) {
                    mFormat = mExtractor.getTrackFormat(0);
                } else {
                    Log.e(TAG, "No track in the source file");
                    return;
                }

                // we setup the codec with the type we got
                try {
                    mDecoder = MediaCodec.createDecoderByType(mFormat.getString(MediaFormat.KEY_MIME));
                } catch (Exception e) {
                    // SDK > 19, an IOException might be thrown
                    Log.e(TAG, "Unable to create decoder", e);
                    return;
                }
                mDecoder.configure(mFormat, null, null, 0);

                // get the sample rate to configure AudioTrack
                Log.d(TAG, "Sample rate: " + mFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                mExtractor.selectTrack(0);

                // we get the buffer information to get notified of changes
                mInfo = new MediaCodec.BufferInfo();

                // we start decoding
                mDecoder.start();
                mInputBuffers = mDecoder.getInputBuffers();
                mOutputBuffers = mDecoder.getOutputBuffers();
            }
        }
    }

    public void seekTo(long timeMs) {
        mExtractor.seekTo(timeMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    }

    private final Thread mAudioPushRunnable = new Thread() {
        public void run() {
            mIsEOS = false;
            byte[] outArray = new byte[8192];

            while (!isInterrupted()) {
                synchronized (mAudioPushRunnable) {
                    if (mIsEOS || mCurrentSong == null || mPaused) {
                        try {
                            mAudioPushRunnable.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                    }

                    synchronized (LocalProvider.this) {
                        // if we did not finish the file
                        if (!mIsEOS && !mPaused && mDecoder != null) {

                            // Input decoding
                            int inIndex = -2;
                            try {
                                // Try to dequeue an output buffer (timeout 30ms)
                                inIndex = mDecoder.dequeueInputBuffer(TimeUnit.MILLISECONDS.toMicros(30));
                            } catch (IllegalStateException ignored) {
                            }

                            // if we have a buffer available
                            if (inIndex >= 0) {
                                // we get the buffer
                                ByteBuffer buffer = mInputBuffers[inIndex];

                                // we retrieve the current encoded sample size
                                int sampleSize = mExtractor.readSampleData(buffer, 0);
                                if (sampleSize < 0) {
                                    // we are at the end of the file
                                    mDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                    mIsEOS = true;
                                    mCallback.songFinished();
                                } else {
                                    //we queue the encoded sample in the decoder
                                    try {
                                        mDecoder.queueInputBuffer(inIndex, 0, sampleSize, 0, 0);
                                        mExtractor.advance();
                                    } catch (Exception e) {
                                        Log.d(TAG, e.toString());
                                        continue;
                                    }
                                }
                            }

                            // Output processing
                            int outIndex = -1;
                            if (mPendingOutIndex >= 0) {
                                // We have a pending buffer, we'll dequeue later, but for now try
                                // to write the sample again
                                outIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
                            } else {
                                try {
                                    // Try to dequeue an output buffer (timeout 30ms)
                                    outIndex = mDecoder.dequeueOutputBuffer(mInfo, TimeUnit.MILLISECONDS.toMicros(30));
                                } catch (IllegalStateException ignored) {
                                }
                            }

                            switch (outIndex) {//we act according to the decoder output
                                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                    mOutputBuffers = mDecoder.getOutputBuffers();
                                    break;

                                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                    mFormat = mDecoder.getOutputFormat();
                                    break;

                                case MediaCodec.INFO_TRY_AGAIN_LATER:
                                    // No buffer is available. This might be due to the upstream
                                    // app buffers being full and returning 0 on write operations.
                                    // We ping it and retry to write what we had.
                                    if (mPendingOutIndex >= 0) {
                                        int written = mCallback.musicDelivery(outArray, mPendingSize,
                                                mFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                                mFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));

                                        if (written > 0 || written == -1) {
                                            try {
                                                // We don't need this buffer anymore
                                                mDecoder.releaseOutputBuffer(mPendingOutIndex, true);
                                            } catch (IllegalStateException ignored) {
                                            }
                                            mPendingOutIndex = -1;
                                        }
                                    }
                                    break;

                                default:
                                    ByteBuffer out = mOutputBuffers[outIndex];
                                    int size = mInfo.size;
                                    if (size > 0) {
                                        if (outArray.length < mInfo.offset + mInfo.size) {
                                            outArray = new byte[mInfo.offset + mInfo.size];
                                        }

                                        out.position(mInfo.offset);
                                        out.limit(mInfo.offset + mInfo.size);
                                        out.get(outArray, 0, mInfo.size);

                                        // We deliver the array
                                        int written = mCallback.musicDelivery(outArray, size,
                                                mFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                                mFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));

                                        if (written > 0) {
                                            try {
                                                // We don't need this buffer anymore
                                                mDecoder.releaseOutputBuffer(outIndex, true);
                                            } catch (IllegalStateException ignored) {
                                            }
                                        } else {
                                            mPendingOutIndex = outIndex;
                                            mPendingSize = mInfo.size;
                                        }
                                    }
                                    break;
                            }
                        }
                    }
                }
            }
        }
    };

    public void startSearch(final String query) {
        Log.d(TAG, "Starting search for " + query);

        final Thread searchThread = new Thread() {
            public void run() {
                mSearchResult = new SearchResult(query);

                final List<String> songsList = new ArrayList<String>();
                final List<String> albumList = new ArrayList<String>();
                final List<String> playlistList = new ArrayList<String>();
                final List<String> artistList = new ArrayList<String>();

                final String queryUpper = query.toUpperCase();

                for (LocalSong song : mSongs.values()) {
                    if (song.getSong().getTitle().equalsIgnoreCase(query)) {
                        songsList.add(0, song.getSong().getRef());
                    } else if (song.getSong().getTitle().toUpperCase().contains(queryUpper)) {
                        songsList.add(song.getSong().getRef());
                    }
                }

                for (Album album : mAlbums.values()) {
                    if (album.getName().equalsIgnoreCase(query)) {
                        albumList.add(0, album.getRef());
                    } else if (album.getName().toUpperCase().contains(queryUpper)) {
                        albumList.add(album.getRef());
                    }
                }
                for (Playlist playlist : mPlaylists.values()) {
                    if (playlist.getName().equalsIgnoreCase(query)) {
                        playlistList.add(0, playlist.getRef());
                    } else if (playlist.getName().toUpperCase().contains(queryUpper)) {
                        playlistList.add(playlist.getRef());
                    }
                }
                for (Artist artist : mArtists.values()) {
                    if (artist.getName().equalsIgnoreCase(query)) {
                        artistList.add(0, artist.getRef());
                    } else if (artist.getName().toUpperCase().contains(queryUpper)) {
                        artistList.add(artist.getRef());
                    }
                }

                if (mSearchResult.getQuery().equals(query)) {
                    Log.d(TAG, "Sending result size: "
                            + (songsList.size() + albumList.size() + artistList.size() + playlistList.size()));

                    mSearchResult.setSongsList(songsList);
                    mSearchResult.setAlbumsList(albumList);
                    mSearchResult.setArtistList(artistList);
                    mSearchResult.setPlaylistList(playlistList);

                    mCallback.searchFinished(mSearchResult);
                } else {
                    Log.d(TAG, "Query results dumped - outdated");
                }
            }
        };
        searchThread.start();
    }


    /**
     * A little class to store ids and retrieve uri of a song
     */
    public static class LocalSong {
        private Song mSong;
        private long mId;
        private long mAlbumId;

        public LocalSong(Song song, long id, long albumId) {
            mSong = song;
            mId = id;
            mAlbumId = albumId;
        }

        /**
         * @return the id of the song
         */
        public Long getId() {
            return mId;
        }

        public Long getAlbumId() {
            return mAlbumId;
        }

        /**
         * @return the Song
         */
        public Song getSong() {
            return mSong;
        }

        /**
         * @return the local URI of the song
         */
        public Uri getURI() {
            Uri uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mId);
            Log.d("LocalProvider", "URI:" + uri.toString());
            return uri;
        }
    }


    /**
     * Callback interface to communicate with the service
     */
    public interface LocalCallback {
        int musicDelivery(byte[] data, int frames, int channels, int sampleRate);
        void artistUpdated(final Artist artist);
        void albumUpdated(final Album album);
        void songUpdated(final Song song);
        void playlistUpdated(final Playlist playlist);
        void playlistRemoved(final String playlistRef);
        void genreUpdated(final Genre genre);
        void searchFinished(final SearchResult searchResult);
        void songFinished();
        void songPaused();
        void songPlaying();
    }
}
