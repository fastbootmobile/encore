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

import android.content.Context;
import android.content.res.Resources;

import com.fastbootmobile.encore.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class VoiceCommander {
    private static final String TAG = "VoiceCommander";

    public static final int ACTION_PLAY_ALBUM = 1;
    public static final int ACTION_PLAY_ARTIST = 2;
    public static final int ACTION_PLAY_PLAYLIST = 3;
    public static final int ACTION_PLAY_TRACK = 4;
    public static final int EXTRA_SOURCE = 1;

    public static final int ACTION_PAUSE = 5;

    public static final int ACTION_NEXT = 6;
    public static final int EXTRA_TIME_SECS = 2;
    public static final int EXTRA_TIME_MINS = 3;

    public static final int ACTION_JUMP = 7;
    public static final int ACTION_PREVIOUS = 8;
    public static final int ACTION_GOOGLE = 9;


    private final List<Pattern> mPlayPlaylistPatterns = new ArrayList<>();
    private final List<Pattern> mPlayPlaylistSourcePatterns = new ArrayList<>();
    private final List<Pattern> mPlayArtistPatterns = new ArrayList<>();
    private final List<Pattern> mPlayArtistSourcePatterns = new ArrayList<>();
    private final List<Pattern> mPlayAlbumPatterns = new ArrayList<>();
    private final List<Pattern> mPlayAlbumSourcePatterns = new ArrayList<>();
    private final List<Pattern> mPlayTrackPatterns = new ArrayList<>();
    private final List<Pattern> mPlayTrackSourcePatterns = new ArrayList<>();
    private final List<Pattern> mPausePatterns = new ArrayList<>();
    private final List<Pattern> mJumpPatterns = new ArrayList<>();
    private final List<Pattern> mNextPatterns = new ArrayList<>();
    private final List<Pattern> mNextPatternsTimeSecs = new ArrayList<>();
    private final List<Pattern> mNextPatternsTimeMins = new ArrayList<>();
    private final List<Pattern> mPreviousPatterns = new ArrayList<>();
    private final List<Pattern> mPreviousPatternsTimeSecs = new ArrayList<>();
    private final List<Pattern> mPreviousPatternsTimeMins = new ArrayList<>();
    private final List<Pattern> mGooglePatterns = new ArrayList<>();

    public VoiceCommander(Context context) {
        Resources res = context.getResources();
        String[] playWords = res.getStringArray(R.array.voice_play_words);
        String[] playSourceWords = res.getStringArray(R.array.voice_play_source_words);
        String[] playArtistWords = res.getStringArray(R.array.voice_play_artist_words);
        String[] playAlbumWords = res.getStringArray(R.array.voice_play_album_words);
        String[] playPlaylistWords = res.getStringArray(R.array.voice_play_playlist_words);
        String[] playSongWords = res.getStringArray(R.array.voice_play_track_words);
        String[] pauseWords = res.getStringArray(R.array.voice_pause_words);
        String[] nextWords = res.getStringArray(R.array.voice_next_words);
        String[] jumpWords = res.getStringArray(R.array.voice_jump_words);
        String[] previousWords = res.getStringArray(R.array.voice_previous_words);
        String[] timeSeconds = res.getStringArray(R.array.voice_time_seconds);
        String[] timeMinutes = res.getStringArray(R.array.voice_time_minutes);
        String[] googleWords = res.getStringArray(R.array.voice_google_words);

        // Pre-compile patterns
        Pattern pattern;

        // Play patterns
        for (String playWord : playWords) {
            // Play (without source) patterns
            for (String artistWord : playArtistWords) {
                pattern = Pattern.compile("^" + playWord + " " + artistWord
                        + " (.*?)$", Pattern.CASE_INSENSITIVE);
                mPlayArtistPatterns.add(pattern);
            }

            for (String albumWord : playAlbumWords) {
                pattern = Pattern.compile("^" + playWord + " " + albumWord
                        + " (.*?)$", Pattern.CASE_INSENSITIVE);
                mPlayAlbumPatterns.add(pattern);
            }

            for (String playlistWord : playPlaylistWords) {
                pattern = Pattern.compile("^" + playWord + " " + playlistWord
                        + " (.*?)$", Pattern.CASE_INSENSITIVE);
                mPlayPlaylistPatterns.add(pattern);
            }

            for (String trackWord : playSongWords) {
                pattern = Pattern.compile("^" + playWord + " " + trackWord
                        + " (.*?)$", Pattern.CASE_INSENSITIVE);
                mPlayTrackPatterns.add(pattern);
            }
/*
            // Play (with source) patterns
            for (String sourceWord : playSourceWords) {
                pattern = Pattern.compile("^" + playWord + " (.*?) " + sourceWord + " (.*?)$",
                        Pattern.CASE_INSENSITIVE);
                mPlaySourcePatterns.add(pattern);
            }*/
        }

        // Pause patterns
        for (String pauseWord : pauseWords) {
            pattern = Pattern.compile("^" + pauseWord + "$", Pattern.CASE_INSENSITIVE);
            mPausePatterns.add(pattern);
        }

        // Jump patterns
        for (String jumpWord : jumpWords) {
            pattern = Pattern.compile("^" + jumpWord + " ([0-9]+)$", Pattern.CASE_INSENSITIVE);
            mJumpPatterns.add(pattern);
        }

        // Next patterns
        for (String nextWord : nextWords) {
            pattern = Pattern.compile("^" + nextWord + "$", Pattern.CASE_INSENSITIVE);
            mNextPatterns.add(pattern);

            for (String time : timeSeconds) {
                pattern = Pattern.compile("^" + nextWord + " ([0-9]+) " + time + "$",
                        Pattern.CASE_INSENSITIVE);
                mNextPatternsTimeSecs.add(pattern);
            }

            for (String time : timeMinutes) {
                pattern = Pattern.compile("^" + nextWord + " ([0-9]+) " + time + "$",
                        Pattern.CASE_INSENSITIVE);
                mNextPatternsTimeMins.add(pattern);
            }
        }

        // Previous patterns
        for (String previousWord : previousWords) {
            pattern = Pattern.compile("^" + previousWord + "$", Pattern.CASE_INSENSITIVE);
            mPreviousPatterns.add(pattern);

            for (String time : timeSeconds) {
                pattern = Pattern.compile("^" + previousWord + " ([0-9]+) " + time + "$",
                        Pattern.CASE_INSENSITIVE);
                mPreviousPatternsTimeSecs.add(pattern);
            }

            for (String time : timeMinutes) {
                pattern = Pattern.compile("^" + previousWord + " ([0-9]+) " + time + "$",
                        Pattern.CASE_INSENSITIVE);
                mPreviousPatternsTimeMins.add(pattern);
            }
        }

        // Google patterns
        for (String googleWord : googleWords) {
            pattern = Pattern.compile("^" + googleWord + " (.*?)$", Pattern.CASE_INSENSITIVE);
            mGooglePatterns.add(pattern);
        }
    }


    public boolean processResult(List<String> results, ResultListener listener) {
        for (String line : results) {
            // We're going to try to match each sentence, longest first for each prefix
            Matcher matcher;

            // Play with source
            /*
            for (Pattern playSourcePattern : mPlaySourcePatterns) {
                matcher = playSourcePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PLAY, EXTRA_SOURCE,
                            new String[]{matcher.group(1), matcher.group(2)});
                    return true;
                }
            }*/

            // Play without source
            for (Pattern playSourcePattern : mPlayTrackPatterns) {
                matcher = playSourcePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PLAY_TRACK, 0, new String[]{matcher.group(1)});
                    return true;
                }
            }
            for (Pattern playSourcePattern : mPlayPlaylistPatterns) {
                matcher = playSourcePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PLAY_PLAYLIST, 0, new String[]{matcher.group(1)});
                    return true;
                }
            }
            for (Pattern playSourcePattern : mPlayArtistPatterns) {
                matcher = playSourcePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PLAY_ARTIST, 0, new String[]{matcher.group(1)});
                    return true;
                }
            }
            for (Pattern playSourcePattern : mPlayAlbumPatterns) {
                matcher = playSourcePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PLAY_ALBUM, 0, new String[]{matcher.group(1)});
                    return true;
                }
            }

            // Pause
            for (Pattern pausePattern : mPausePatterns) {
                matcher = pausePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PAUSE, 0, null);
                    return true;
                }
            }

            // Jump to ...
            for (Pattern jumpPattern : mJumpPatterns) {
                matcher = jumpPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_JUMP, 0, new String[]{matcher.group(1)});
                    return true;
                }
            }

            // Skip xx seconds
            for (Pattern nextSecsPattern : mNextPatternsTimeSecs) {
                matcher = nextSecsPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_NEXT, EXTRA_TIME_SECS, new String[]{matcher.group(1)});
                    return true;
                }
            }

            // Skip xx minutes
            for (Pattern nextMinsPattern : mNextPatternsTimeMins) {
                matcher = nextMinsPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_NEXT, EXTRA_TIME_MINS, new String[]{matcher.group(1)});
                    return true;
                }
            }

            // Next
            for (Pattern nextPattern : mNextPatterns) {
                matcher = nextPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_NEXT, 0, null);
                    return true;
                }
            }

            // Previous xx seconds
            for (Pattern previousSecsPattern : mPreviousPatternsTimeSecs) {
                matcher = previousSecsPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PREVIOUS, EXTRA_TIME_SECS, new String[]{matcher.group(1)});
                    return true;
                }
            }

            // Previous xx minutes
            for (Pattern previousMinsPattern : mPreviousPatternsTimeMins) {
                matcher = previousMinsPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PREVIOUS, EXTRA_TIME_MINS, new String[]{matcher.group(1)});
                    return true;
                }
            }

            // Previous
            for (Pattern previousPattern : mPreviousPatterns) {
                matcher = previousPattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_PREVIOUS, 0, null);
                    return true;
                }
            }

            // Google
            for (Pattern googlePattern : mGooglePatterns) {
                matcher = googlePattern.matcher(line);
                if (matcher.matches()) {
                    listener.onResult(ACTION_GOOGLE, 0, new String[]{matcher.group(1)});
                    return true;
                }
            }
        }

        return false;
    }

    public interface ResultListener {
        public void onResult(int action, int extra, String[] params);
    }
}
