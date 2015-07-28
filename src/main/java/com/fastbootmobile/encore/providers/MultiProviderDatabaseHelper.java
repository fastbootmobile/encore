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

package com.fastbootmobile.encore.providers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.RemoteException;
import android.util.Log;

import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.SearchResult;
import com.fastbootmobile.encore.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by h4o on 01/07/2014.
 */
public class MultiProviderDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "MultiProviderDBHelper";
    private static final int DATABASE_VERSION = 2;

    private static final String DATABASE_NAME = "multiprovider_playlists";

    private static final String TABLE_PLAYLIST = "playlist";
    private static final String TABLE_SONGS = "song";

    private static final String KEY_ID = "id";

    private static final String KEY_PLAYLIST_NAME = "playlist_name";

    private static final String KEY_SONG_REF = "song_ref";
    private static final String KEY_PLAYLIST_ID = "playlist_id";
    private static final String KEY_PACKAGE_NAME = "package_name";
    private static final String KEY_SERVICE = "service";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_POSITION = "position";

    private static final String CREATE_TABLE_PLAYLIST = "CREATE TABLE IF NOT EXISTS " +
            TABLE_PLAYLIST + "(" + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_PLAYLIST_NAME + " TEXT)";
    private static final String CREATE_TABLE_SONGS = "CREATE TABLE IF NOT EXISTS " +
            TABLE_SONGS + "(" + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_PLAYLIST_ID + " INTEGER,"
            + KEY_SONG_REF + " TEXT," + KEY_PACKAGE_NAME + " TEXT," + KEY_SERVICE + " TEXT,"
            + KEY_POSITION + " INTEGER," + KEY_PROVIDER + " TEXT)";

    private HashMap<String, Long> mPlayListRefID;
    private HashMap<String, Long> mSongRefID;
    private HashMap<String, Playlist> mPlaylists;
    private HashMap<String, ProviderIdentifier> mRefProviderId;
    private SQLiteDatabase mDatabase;
    private LocalCallback mCallback;
    private SearchResult mSearchResult;
    private ProviderIdentifier mProviderIdentifier;
    private boolean mFetched;

    public interface LocalCallback {
        void playlistUpdated(final Playlist playlist);
        void playlistRemoved(final String ref);
        void searchFinished(final SearchResult searchResult);
    }

    public MultiProviderDatabaseHelper(Context ctx, LocalCallback localCallback) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        mCallback = localCallback;
        mSongRefID = new HashMap<>();
        mPlaylists = new HashMap<>();
        mPlayListRefID = new HashMap<>();
        mRefProviderId = new HashMap<>();
        mFetched = false;

        try {
            mDatabase = getWritableDatabase();
        } catch (Exception e) {
            Log.e(TAG, "Cannot get writable database", e);
        }
    }

    public void setIdentifier(ProviderIdentifier providerIdentifier) {
        mProviderIdentifier = providerIdentifier;
        if (!mFetched) {
            fetchPlaylists();
            mFetched = true;
        }
    }

    public boolean isSetup() {
        return mFetched;
    }

    public Song getSong(String ref) throws RemoteException {
        ProviderIdentifier providerId = mRefProviderId.get(ref);
        if (providerId != null) {
            ProviderConnection connection = PluginsLookup.getDefault().getProvider(providerId);
            if (connection != null) {
                IMusicProvider binder = connection.getBinder();
                if (binder != null) {
                    return binder.getSong(ref);
                }
            }
        }
        return null;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_PLAYLIST);
        db.execSQL(CREATE_TABLE_SONGS);

    }

    private void fetchPlaylists() {
        final Cursor c = mDatabase.query(TABLE_PLAYLIST, null, null, null, null, null, null);

        final int colindex_id = c.getColumnIndex(KEY_ID);
        final int colindex_name = c.getColumnIndex(KEY_PLAYLIST_NAME);

        if (c.moveToFirst()) {
            do {
                final long playlist_id = c.getLong(colindex_id);

                // Create the Omni entity
                Playlist pl = new Playlist("omni:playlist:" + playlist_id);
                pl.setIsLoaded(true);
                pl.setName(c.getString(colindex_name));
                pl.setProvider(mProviderIdentifier);
                pl.setSourceLogo(MultiProviderPlaylistProvider.LOGO_REF);

                // Ensure songs are fetched
                fetchSongs(pl, playlist_id, mDatabase);

                // Cache it
                mPlaylists.put(pl.getRef(), pl);
                mPlayListRefID.put(pl.getRef(), playlist_id);

                // Notify the app
                mCallback.playlistUpdated(pl);
            } while (c.moveToNext());
        }

        ProviderAggregator.getDefault().getCache().putAllProviderPlaylist(new ArrayList<>(mPlaylists.values()));

        c.close();
    }

    private void fetchSongs(Playlist playlist, long playlist_id, SQLiteDatabase database) {
        Cursor c = database.query(TABLE_SONGS, null, KEY_PLAYLIST_ID + "=?", new String[]{Long.toString(playlist_id)}, null, null, KEY_POSITION);

        final int ci_id = c.getColumnIndex(KEY_ID);
        final int ci_ref = c.getColumnIndex(KEY_SONG_REF);
        final int ci_pck = c.getColumnIndex(KEY_PACKAGE_NAME);
        final int ci_service = c.getColumnIndex(KEY_SERVICE);
        final int ci_provider = c.getColumnIndex(KEY_PROVIDER);

        if (c.moveToFirst()) {
            do {
                final long song_id = c.getLong(ci_id);
                String song = c.getString(ci_ref);

                playlist.addSong(song);

                ProviderIdentifier providerIdentifier = new ProviderIdentifier(c.getString(ci_pck), c.getString(ci_service), c.getString(ci_provider));
                mSongRefID.put(playlist.getRef() + ":" + song, song_id);
                mRefProviderId.put(song, providerIdentifier);
            } while (c.moveToNext());
        }

        c.close();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        if (mProviderIdentifier != null && !mFetched) {
            fetchPlaylists();
            mFetched = true;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // We have no migrating plan for now
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
        onCreate(db);
    }

    public List<Playlist> getPlaylists() {
        return new ArrayList<>(mPlaylists.values());
    }

    public String addPlaylist(String playlist_name) {
        ContentValues cv = new ContentValues();
        cv.put(KEY_PLAYLIST_NAME, playlist_name);
        long playlist_id = mDatabase.insert(TABLE_PLAYLIST, null, cv);

        // Generate the Omni entity
        Playlist pl = new Playlist("omni:playlist:" + playlist_id);
        pl.setName(playlist_name);
        pl.setIsLoaded(true);
        pl.setProvider(mProviderIdentifier);
        pl.setSourceLogo(MultiProviderPlaylistProvider.LOGO_REF);

        mPlaylists.put(pl.getRef(), pl);
        mPlayListRefID.put(pl.getRef(), playlist_id);

        mCallback.playlistUpdated(pl);

        return pl.getRef();
    }

    public boolean addSongToPlaylist(String songref, String playlistref, ProviderIdentifier providerIdentifier) {
        final long playlist_id = Long.parseLong(playlistref.substring(playlistref.lastIndexOf(':') + 1));

        Playlist p = mPlaylists.get(playlistref);

        ContentValues cv = new ContentValues();
        cv.put(KEY_PLAYLIST_ID, playlist_id);
        cv.put(KEY_SONG_REF, songref);
        cv.put(KEY_PACKAGE_NAME, providerIdentifier.mPackage);
        cv.put(KEY_SERVICE, providerIdentifier.mService);
        cv.put(KEY_PROVIDER, providerIdentifier.mName);
        cv.put(KEY_POSITION, mPlaylists.get(playlistref).songsList().size());

        p.addSong(songref);

        final long song_id = mDatabase.insert(TABLE_SONGS, null, cv);

        mSongRefID.put(playlistref + ":" + songref, song_id);
        mCallback.playlistUpdated(p);
        mRefProviderId.put(songref, providerIdentifier);

        return true;
    }

    public boolean deletePlaylist(String playlistref) {
        if (mPlayListRefID.containsKey(playlistref)) {
            long playlist_id = mPlayListRefID.get(playlistref);
            mDatabase.delete(TABLE_PLAYLIST, KEY_ID + " = ?",
                    new String[]{String.valueOf(playlist_id)});
            mDatabase.delete(TABLE_SONGS, KEY_PLAYLIST_ID + " = ?",
                    new String[]{String.valueOf(playlist_id)});
            mPlaylists.remove(playlistref);
            mPlayListRefID.remove(playlistref);
            mCallback.playlistRemoved(playlistref);
            return true;
        }
        return false;
    }

    public boolean renamePlaylist(String playlistRef, String title) {
        if (mPlayListRefID.containsKey(playlistRef)) {
            long playlist_id = mPlayListRefID.get(playlistRef);
            ContentValues cv = new ContentValues(1);
            cv.put(KEY_PLAYLIST_NAME, title);
            mDatabase.update(TABLE_PLAYLIST, cv, KEY_PLAYLIST_ID + " = ?", new String[]{String.valueOf(playlist_id)});
            return true;
        }
        return false;
    }

    public boolean deleteSongFromPlaylist(int songPosition, String playlistRef) {
        String songRef = mPlaylists.get(playlistRef).songsList().get(songPosition);
        if (mSongRefID.containsKey(playlistRef + ":" + songRef)) {
            long song_id = mSongRefID.get(playlistRef + ":" + songRef);
            mDatabase.delete(TABLE_SONGS, KEY_ID + " = ?",
                    new String[]{String.valueOf(song_id)});
            mPlaylists.get(playlistRef).songsList().remove(songPosition);
            mCallback.playlistUpdated(mPlaylists.get(playlistRef));
        }
        return false;
    }

    public boolean swapPlaylistItem(int oldPosition, int newPosition, String playlistRef) {
        String oldSongRef = playlistRef + ":" + mPlaylists.get(playlistRef).songsList().get(oldPosition);
        String newSongRef = playlistRef + ":" + mPlaylists.get(playlistRef).songsList().get(newPosition);
        if (mSongRefID.containsKey(oldSongRef) && mSongRefID.containsKey(newSongRef)) {
            String query = "UPDATE " + TABLE_SONGS + " SET " + KEY_POSITION + "=" + newPosition + " WHERE " + KEY_ID + " = " + mSongRefID.get(oldSongRef);
            mDatabase.rawQuery(query, null);
            query = "UPDATE " + TABLE_SONGS + " SET " + KEY_POSITION + "=" + oldPosition + " WHERE " + KEY_ID + " = " + mSongRefID.get(newSongRef);
            mDatabase.rawQuery(query, null);
            String oldRef = mPlaylists.get(playlistRef).songsList().get(oldPosition);
            String newRef = mPlaylists.get(playlistRef).songsList().get(newPosition);
            mPlaylists.get(playlistRef).songsList().set(oldPosition, newRef);
            mPlaylists.get(playlistRef).songsList().set(newPosition, oldRef);
            mCallback.playlistUpdated(mPlaylists.get(playlistRef));
            return true;
        }
        return false;
    }

    public void startSearch(final String query) {
        if (mSearchResult != null && mSearchResult.getQuery().hashCode() != query.hashCode() || mSearchResult == null) {
            mSearchResult = new SearchResult(query);
            final String regex = ".*" + query + ".*";
            Thread searchThread = new Thread() {
                public void run() {
                    Log.d(TAG, "Searching for '" + query + "'");
                    Pattern p;
                    try {
                        p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    } catch (Exception e) {
                        return;
                    }

                    // MultiProvider only handles playlists, so we only search for that
                    List<String> playlistList = new ArrayList<>();

                    for (Playlist playlist : mPlaylists.values()) {
                        if (p.matcher(playlist.getName()).matches()) {
                            playlistList.add(playlist.getRef());
                        }
                    }
                    if (mSearchResult.getQuery().hashCode() == query.hashCode()) {
                        mSearchResult.setPlaylistList(playlistList);
                        mCallback.searchFinished(mSearchResult);
                    }
                    mSearchResult = null;
                }

            };
            searchThread.start();
        }
    }

}
