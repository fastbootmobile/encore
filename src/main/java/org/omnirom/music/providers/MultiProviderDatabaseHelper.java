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

package org.omnirom.music.providers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.SearchResult;
import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by h4o on 01/07/2014.
 */
public class MultiProviderDatabaseHelper extends SQLiteOpenHelper {

    private final String TAG = "MultiProviderDataBaseHelper";
    private static int DATABASE_VERSION = 2;

    private static String DATABASE_NAME = "multiprovider_playlists";

    private static String TABLE_PLAYLIST = "playlist";
    private static String TABLE_SONGS = "song";

    private static String KEY_ID = "id";

    private static String KEY_PLAYLIST_NAME = "playlist_name";

    private static String KEY_SONG_REF = "song_ref";
    private static String KEY_PLAYLIST_ID = "playlist_id";
    private static String KEY_PACKAGE_NAME = "package_name";
    private static String KEY_SERVICE = "service";
    private static String KEY_PROVIDER = "provider";
    private static String KEY_POSITION = "position";
//    private static String KEY_POSITION = "position";

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
    private SQLiteDatabase db;
    private LocalCallback mCallback;
    private SearchResult mSearchResult;
    private ProviderIdentifier mProviderIdentifier;
    private boolean mFetched;

    public interface LocalCallback {
        public void playlistUpdated(final Playlist playlist);

        public void searchFinished(final SearchResult searchResult);
    }

    public MultiProviderDatabaseHelper(Context ctx, LocalCallback localCallback) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        mCallback = localCallback;
        mSongRefID = new HashMap<String, Long>();
        mPlaylists = new HashMap<String, Playlist>();
        mPlayListRefID = new HashMap<String, Long>();
        mRefProviderId = new HashMap<String, ProviderIdentifier>();
        mFetched = false;
        try {
            getWritableDatabase();
        } catch (Exception e) {
            Log.e(TAG, "Cannot get writable database", e);
        }
    }

    public void setIdentifier(ProviderIdentifier providerIdentifier) {
        mProviderIdentifier = providerIdentifier;
        if (!mFetched) {
            fetchPlaylists(db);
            mFetched = true;
        }
    }

    public boolean isSetup() {
        return mFetched;
    }

    public Song getSong(String ref) throws RemoteException {
        ProviderIdentifier providerId = mRefProviderId.get(ref);
        if (providerId != null) {
            IMusicProvider binder = PluginsLookup.getDefault().getProvider(providerId).getBinder();
            if (binder != null)
                return binder.getSong(ref);
        }
        return null;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "creating sql db");
        db.execSQL(CREATE_TABLE_PLAYLIST);
        db.execSQL(CREATE_TABLE_SONGS);
        Log.d(TAG, "done");

    }

    private void fetchPlaylists(SQLiteDatabase database) {
        String query = "SELECT * FROM " + TABLE_PLAYLIST;
        Log.d(TAG, "fetching playlist");
        Cursor c = database.rawQuery(query, null);
        int id = c.getColumnIndex(KEY_ID);
        int playlist_name = c.getColumnIndex(KEY_PLAYLIST_NAME);
        if (c.moveToFirst()) {
            do {
                long playlist_id = c.getLong(id);
                Playlist pl = new Playlist("omni:playlist:" + playlist_id);
                pl.setIsLoaded(true);
                pl.setName(c.getString(playlist_name));
                pl.setProvider(mProviderIdentifier);
                fetchSongs(pl, playlist_id, database);
                mCallback.playlistUpdated(pl);
                mPlaylists.put(pl.getRef(), pl);
                mPlayListRefID.put(pl.getRef(), playlist_id);
            } while (c.moveToNext());
        }
        ProviderAggregator.getDefault().getCache().putAllProviderPlaylist(new ArrayList<>(mPlaylists.values()));
        c.close();
    }

    private void fetchSongs(Playlist playlist, long playlist_id, SQLiteDatabase database) {
        String query = "SELECT * FROM " + TABLE_SONGS + " WHERE " + KEY_PLAYLIST_ID + " = " + playlist_id + " ORDER BY " + KEY_POSITION;
        Cursor c = database.rawQuery(query, null);
        int id = c.getColumnIndex(KEY_ID);
        int ref = c.getColumnIndex(KEY_SONG_REF);
        int pck = c.getColumnIndex(KEY_PACKAGE_NAME);
        int service = c.getColumnIndex(KEY_SERVICE);
        int provider = c.getColumnIndex(KEY_PROVIDER);
        if (c.moveToFirst()) {
            do {
                long song_id = c.getLong(id);
                String song = c.getString(ref);
                playlist.addSong(song);
                ProviderIdentifier providerIdentifier = new ProviderIdentifier(c.getString(pck), c.getString(service), c.getString(provider));
                mSongRefID.put(playlist.getRef() + ":" + song, song_id);
                mRefProviderId.put(song, providerIdentifier);
            } while (c.moveToNext());
        }
        c.close();
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        this.db = db;
        Log.d(TAG, "database has been opened");
        if (mProviderIdentifier != null) {
            fetchPlaylists(db);
            mFetched = true;
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //we have no migrating plan for now
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PLAYLIST);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);


        onCreate(db);
    }

    public List<Playlist> getPlaylists() {
        return new ArrayList<Playlist>(mPlaylists.values());
    }

    public String addPlaylist(String playlist_name) {
        Log.d(TAG, "adding the playlist " + playlist_name);
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(KEY_PLAYLIST_NAME, playlist_name);
        Log.d(TAG, "let's do some work");
        long playlist_id = db.insert(TABLE_PLAYLIST, null, cv);
        Log.e(TAG, ".playlist id is: " + playlist_id);
        Playlist pl = new Playlist("omni:playlist:" + playlist_id);
        pl.setName(playlist_name);
        pl.setIsLoaded(true);
        pl.setProvider(mProviderIdentifier);
        mPlaylists.put(pl.getRef(), pl);
        mPlayListRefID.put(pl.getRef(), playlist_id);
        mCallback.playlistUpdated(pl);
        return pl.getRef();
    }

    public boolean addSongToPlaylist(String songref, String playlistref, ProviderIdentifier providerIdentifier) {
        if (mPlayListRefID.containsKey(playlistref)) {
            long playlist_id = mPlayListRefID.get(playlistref);
            Playlist p = mPlaylists.get(playlistref);
            Log.d(TAG, "playlist contains " + p.getSongsCount());
            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(KEY_PLAYLIST_ID, playlist_id);
            cv.put(KEY_SONG_REF, songref);
            cv.put(KEY_PACKAGE_NAME, providerIdentifier.mPackage);
            cv.put(KEY_SERVICE, providerIdentifier.mService);
            cv.put(KEY_PROVIDER, providerIdentifier.mName);
            cv.put(KEY_POSITION, mPlaylists.get(playlistref).songsList().size());
            p.addSong(songref);
            long song_id = db.insert(TABLE_SONGS, null, cv);
            Log.d(TAG, "adding song to playlist, song id:" + song_id);
            Log.d(TAG, "playlist contains now " + p.getSongsCount());
            mSongRefID.put(playlistref + ":" + songref, song_id);
            mCallback.playlistUpdated(p);
            mRefProviderId.put(songref, providerIdentifier);
            return true;
        } else
            return false;
    }

    public boolean deletePlaylist(String playlistref) {
        if (mPlayListRefID.containsKey(playlistref)) {
            long playlist_id = mPlayListRefID.get(playlistref);
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_PLAYLIST, KEY_ID + " = ?",
                    new String[]{String.valueOf(playlist_id)});
            db.delete(TABLE_SONGS, KEY_PLAYLIST_ID + " = ?",
                    new String[]{String.valueOf(playlist_id)});
            mPlaylists.remove(playlistref);
            return true;
        }
        return false;
    }

    public boolean deleteSongFromPlaylist(int songPosition, String playlistRef) {
        String songRef = mPlaylists.get(playlistRef).songsList().get(songPosition);
        if (mSongRefID.containsKey(playlistRef + ":" + songRef)) {
            long song_id = mSongRefID.get(playlistRef + ":" + songRef);
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_SONGS, KEY_ID + " = ?",
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
            SQLiteDatabase db = this.getWritableDatabase();
            String query = "UPDATE " + TABLE_SONGS + " SET " + KEY_POSITION + "=" + newPosition + " WHERE " + KEY_ID + " = " + mSongRefID.get(oldSongRef);
            db.rawQuery(query, null);
            query = "UPDATE " + TABLE_SONGS + " SET " + KEY_POSITION + "=" + oldPosition + " WHERE " + KEY_ID + " = " + mSongRefID.get(newSongRef);
            db.rawQuery(query, null);
            String oldRef = mPlaylists.get(playlistRef).songsList().get(oldPosition);
            String newRef = mPlaylists.get(playlistRef).songsList().get(newPosition);
            mPlaylists.get(playlistRef).songsList().set(oldPosition, newRef);
            mPlaylists.get(playlistRef).songsList().set(newPosition, oldRef);
            mCallback.playlistUpdated(mPlaylists.get(playlistRef));
            return true;
        }
        return false;//not supported yet
    }

    public void startSearch(final String query) {
        Log.d(TAG, "searching for " + query);
        if (mSearchResult != null && mSearchResult.getQuery().hashCode() != query.hashCode() || mSearchResult == null) {
            mSearchResult = new SearchResult(query);
            final String regex = ".*" + query + ".*";
            Thread searchThread = new Thread() {
                public void run() {

                    Log.d(TAG, "starting search thread");
                    Pattern p;
                    try {
                        p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    } catch (Exception e) {
                        return;
                    }

                    // MultiProvider only handles playlists, so we only search for that
                    List<String> playlistList = new ArrayList<String>();

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
            //searchThread.run();

        }

    }


}
