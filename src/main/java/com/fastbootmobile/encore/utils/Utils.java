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

package com.fastbootmobile.encore.utils;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.AlbumActivity;
import com.fastbootmobile.encore.app.ArtistActivity;
import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.app.fragments.PlaylistChooserFragment;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderIdentifier;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Utilities class used throughout the app
 */
public class Utils {
    private static final String TAG = "Utils";

    private static final Map<String, Bitmap> mBitmapQueue = new HashMap<>();

    /**
     * Format milliseconds into an human-readable track length.
     * Examples:
     * - 01:48:24 for 1 hour, 48 minutes, 24 seconds
     * - 24:02 for 24 minutes, 2 seconds
     * - 52s for 52 seconds
     *
     * @param timeMs The time to format, in milliseconds
     * @return A formatted string
     */
    public static String formatTrackLength(long timeMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(timeMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs)
                - TimeUnit.HOURS.toSeconds(hours)
                - TimeUnit.MINUTES.toSeconds(minutes);

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%02d:%02d", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("%02ds", seconds);
        } else if (seconds == 0) {
            return "-:--";
        } else {
            return "N/A";
        }
    }

    /**
     * Calculates the RMS audio level from the provided short sample extract
     *
     * @param audioData The audio samples
     * @return The RMS level
     */
    public static int calculateRMSLevel(short[] audioData, int numframes) {
        long lSum = 0;
        int numread = 0;
        for (short s : audioData) {
            lSum = lSum + s;
            numread++;
            if (numread == numframes) break;
        }

        double dAvg = lSum / numframes;
        double sumMeanSquare = 0d;

        numread = 0;
        for (short anAudioData : audioData) {
            sumMeanSquare = sumMeanSquare + Math.pow(anAudioData - dAvg, 2d);
            numread++;
            if (numread == numframes) break;
        }

        double averageMeanSquare = sumMeanSquare / numframes;

        return (int) (Math.pow(averageMeanSquare, 0.5d) + 0.5);
    }

    /**
     * Shows a short Toast style message
     *
     * @param context The application context
     * @param res     The String resource id
     */
    public static void shortToast(Context context, int res) {
        if (context != null) {
            Toast.makeText(context, res, Toast.LENGTH_SHORT).show();
        } else {
            Log.e(TAG, "Cannot show toast for text ID " + res + " as context is null");
        }
    }

    /**
     * Calculates the optimal size of the text based on the text view width
     *
     * @param textView     The text view in which the text should fit
     * @param desiredWidth The desired final text width, or -1 for the TextView's getMeasuredWidth
     */
    public static void adjustTextSize(TextView textView, int desiredWidth) {
        if (desiredWidth <= 0) {
            desiredWidth = textView.getMeasuredWidth();

            if (desiredWidth <= 0) {
                // Invalid width, don't do anything
                Log.w("Utils", "adjustTextSize: Not doing anything (measured width invalid)");
                return;
            }
        }

        // Add some margin to width
        desiredWidth *= 0.8f;

        Paint paint = new Paint();
        Rect bounds = new Rect();

        paint.setTypeface(textView.getTypeface());
        float textSize = textView.getTextSize() * 2.0f;
        paint.setTextSize(textSize);
        String text = textView.getText().toString();
        paint.getTextBounds(text, 0, text.length(), bounds);

        while (bounds.width() > desiredWidth) {
            textSize--;
            paint.setTextSize(textSize);
            paint.getTextBounds(text, 0, text.length(), bounds);
        }

        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    /**
     * Temporarily store a bitmap
     *
     * @param key The key
     * @param bmp The bitmap
     */
    public static void queueBitmap(String key, Bitmap bmp) {
        mBitmapQueue.put(key, bmp);
    }

    /**
     * Retrieve a bitmap from the store, and removes it
     *
     * @param key The key used in queueBitmap
     * @return The bitmap associated with the key
     */
    public static Bitmap dequeueBitmap(String key) {
        Bitmap bmp = mBitmapQueue.get(key);
        mBitmapQueue.remove(key);
        return bmp;
    }

    /**
     * Animate a view expansion (unwrapping)
     *
     * @param v      The view to animate
     * @param expand True to animate expanding, false to animate closing
     * @return The animation object created
     */
    public static Animation animateExpand(final View v, final boolean expand) {
        try {
            Method m = v.getClass().getDeclaredMethod("onMeasure", int.class, int.class);
            m.setAccessible(true);
            m.invoke(
                    v,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(((View) v.getParent()).getMeasuredWidth(), View.MeasureSpec.UNSPECIFIED)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        final int initialHeight = v.getMeasuredHeight();

        if (expand) {
            v.getLayoutParams().height = 0;
        } else {
            v.getLayoutParams().height = initialHeight;
        }
        v.setVisibility(View.VISIBLE);

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                int newHeight;
                if (expand) {
                    newHeight = (int) (initialHeight * interpolatedTime);
                } else {
                    newHeight = (int) (initialHeight * (1 - interpolatedTime));
                }
                v.getLayoutParams().height = newHeight;
                v.requestLayout();

                if (interpolatedTime == 1 && !expand)
                    v.setVisibility(View.GONE);
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        a.setDuration(500);
        return a;
    }

    /**
     * Provides an API-independent way to set a Drawable background on a view
     */
    public static void setViewBackground(View v, Drawable d) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            v.setBackground(d);
        } else {
            v.setBackgroundDrawable(d);
        }
    }

    public static int dpToPx(Resources res, int dp) {
        final DisplayMetrics displayMetrics = res.getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    /**
     * Figures out the main artist of an album based on its songs
     *
     * @param a The album
     * @return A reference to the main artist, or null if none
     */
    public static String getMainArtist(Album a) {
        if (a == null) {
            return null;
        }

        HashMap<String, Integer> occurrences = new HashMap<>();
        Iterator<String> it = a.songs();

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        while (it.hasNext()) {
            String songRef = it.next();
            if (songRef == null) {
                Log.e(TAG, "Album '" + a.getName() + "' contains null songs!");
                continue;
            }

            Song song = aggregator.retrieveSong(songRef, a.getProvider());
            if (song != null) {
                String artistRef = song.getArtist();
                Integer count = occurrences.get(artistRef);
                if (count == null) {
                    count = 0;
                }
                count++;
                occurrences.put(artistRef, count);
            }
        }

        // Figure the max
        Map.Entry<String, Integer> maxEntry = null;
        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }

        // If there's more than 5 tracks in the album and the most occurrences is one, consider
        // there's no major artist in the album and return null.
        if (maxEntry != null &&
                ((a.getSongsCount() > 5 && maxEntry.getValue() > 1) || a.getSongsCount() <= 5)) {
            return maxEntry.getKey();
        } else {
            return null;
        }
    }

    /**
     * Figures out the main artist of a playlist based on its songs
     *
     * @param p The playlist
     * @return A reference to the main artist, or null if none
     */
    public static String getMainArtist(Playlist p) {
        HashMap<String, Integer> occurrences = new HashMap<>();
        Iterator<String> it = p.songs();

        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        while (it.hasNext()) {
            String songRef = it.next();
            Song song = aggregator.retrieveSong(songRef, p.getProvider());

            if (song != null) {
                String artistRef = song.getArtist();
                Integer count = occurrences.get(artistRef);
                if (count == null) {
                    count = 0;
                }
                count++;
                occurrences.put(artistRef, count);
            }
        }

        // Figure the max
        Map.Entry<String, Integer> maxEntry = null;
        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            if (maxEntry == null || entry.getValue() > maxEntry.getValue()) {
                maxEntry = entry;
            }
        }

        // If there's more than 5 tracks in the album and the most occurrences is one, consider
        // there's no major artist in the album and return null.
        if (maxEntry != null &&
                ((p.getSongsCount() > 5 && maxEntry.getValue() > 1) || p.getSongsCount() < 5)) {
            return maxEntry.getKey();
        } else {
            return null;
        }
    }

    public static void showSongOverflow(final FragmentActivity context, final View parent,
                                        final Song song, final boolean hideArtist) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.track_overflow);

        if (song.getArtist() == null || hideArtist) {
            // This song has no artist information, hide the entry
            Menu menu = popupMenu.getMenu();
            menu.removeItem(R.id.menu_open_artist);
        }

        if (song.getAlbum() == null) {
            // This song has no album information, hide the entry
            Menu menu = popupMenu.getMenu();
            menu.removeItem(R.id.menu_add_album_to_queue);
        }

        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();

                switch (menuItem.getItemId()) {
                    case R.id.menu_play_now:
                        PlaybackProxy.playSong(song);
                        break;

                    case R.id.menu_play_next:
                        PlaybackProxy.playNext(song);
                        break;

                    case R.id.menu_open_artist:
                        Intent intent = ArtistActivity.craftIntent(context, null, song.getArtist(),
                                song.getProvider(),
                                context.getResources().getColor(R.color.default_album_art_background));
                        context.startActivity(intent);
                        break;

                    case R.id.menu_add_to_queue:
                        PlaybackProxy.queueSong(song, false);
                        Toast.makeText(context, R.string.toast_song_added_to_queue, Toast.LENGTH_SHORT).show();
                        break;

                    case R.id.menu_add_album_to_queue:
                        PlaybackProxy.queueAlbum(aggregator.retrieveAlbum(song.getAlbum(),
                                song.getProvider()), false);
                        Toast.makeText(context, R.string.toast_album_added_to_queue, Toast.LENGTH_SHORT).show();
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                        fragment.show(context.getSupportFragmentManager(), song.getRef());
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    public static void showCurrentSongOverflow(final Context context, final View parent,
                                               final Song song) {
        showCurrentSongOverflow(context, parent, song, false);
    }

    public static void showCurrentSongOverflow(final Context context, final View parent,
                                               final Song song, final boolean showArtist) {
        PopupMenu popupMenu = new PopupMenu(context, parent);
        popupMenu.inflate(R.menu.queue_overflow);
        if (song.getAlbum() == null) {
            Log.d(TAG, "No album information, removing album options");

            // This song has no album information, hide the entries
            Menu menu = popupMenu.getMenu();
            menu.removeItem(R.id.menu_add_album_to_queue);
            menu.removeItem(R.id.menu_open_album);
        }

        if (!showArtist) {
            Menu menu = popupMenu.getMenu();
            menu.removeItem(R.id.menu_open_artist);
        }

        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                final ProviderAggregator aggregator = ProviderAggregator.getDefault();

                switch (menuItem.getItemId()) {
                    case R.id.menu_open_album:
                        final Resources res = context.getResources();
                        Intent intent = AlbumActivity.craftIntent(context,
                                ((BitmapDrawable) res.getDrawable(R.drawable.album_placeholder)).getBitmap(),
                                song.getAlbum(),
                                song.getProvider(),
                                res.getColor(R.color.default_album_art_background));
                        context.startActivity(intent);
                        break;

                    case R.id.menu_open_artist:
                        intent = ArtistActivity.craftIntent(context, null, song.getArtist(),
                                song.getProvider(),
                                context.getResources().getColor(R.color.default_album_art_background));
                        context.startActivity(intent);
                        break;

                    case R.id.menu_add_album_to_queue:
                        PlaybackProxy.queueAlbum(aggregator.retrieveAlbum(song.getAlbum(),
                                song.getProvider()), false);
                        Toast.makeText(context, R.string.toast_album_added_to_queue, Toast.LENGTH_SHORT).show();
                        break;

                    case R.id.menu_add_to_playlist:
                        PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(song);
                        if (context instanceof FragmentActivity) {
                            FragmentActivity act = (FragmentActivity) context;
                            fragment.show(act.getSupportFragmentManager(), song.getRef());
                        } else {
                            throw new IllegalArgumentException("Context must be an instance of FragmentActivity");
                        }
                        break;

                    default:
                        return false;
                }
                return true;
            }
        });
    }

    /**
     * Serializes a string array with the specified separator
     *
     * @param elements  The elements to serialize
     * @param separator The separator to use between the elements
     * @return The elements in a single string, separated with the specified separator, an empty
     * string if elements is empty, or null if elements is null.
     */
    public static String implode(String[] elements, String separator) {
        if (elements == null) {
            return null;
        }

        if (elements.length == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(elements[0]);

        boolean skippedFirst = false;
        for (String s : elements) {
            if (skippedFirst) {
                builder.append(separator);
                builder.append(s);
            } else {
                skippedFirst = true;
            }
        }

        return builder.toString();
    }

    public static void animateScale(View v, boolean animate, boolean visible) {
        v.setPivotX(v.getMeasuredWidth() / 2);
        v.setPivotY(v.getMeasuredHeight() / 2);

        if (visible) {
            if (animate) {
                v.animate().scaleX(1.0f).scaleY(1.0f)
                        .alpha(1.0f)
                        .translationY(0.0f)
                        .setDuration(400)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
                v.setAlpha(1.0f);
                v.setTranslationY(0.0f);
            }
        } else {
            if (animate) {
                v.animate().scaleX(0.0f).scaleY(0.0f)
                        .alpha(0.0f)
                        .translationY(v.getHeight() / 4)
                        .setDuration(400)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                v.setScaleX(0.0f);
                v.setScaleY(0.0f);
                v.setAlpha(0.0f);
                v.setTranslationY(v.getHeight() / 4);
            }
        }
    }

    public static void setChildrenAlpha(ViewGroup root, final float alpha) {
        final int childCount = root.getChildCount();
        for (int i = 0; i < childCount; ++i) {
            root.getChildAt(i).setAlpha(alpha);
        }
    }

    /**
     * Returns whether or not the song can be played. This takes into account the track's
     * availability as reported by the provider, as well as the offline status and mode
     *
     * @param s The song to check
     * @return True if the song can be played right now
     */
    public static boolean canPlaySong(Song s) {
        final boolean offlineMode = ProviderAggregator.getDefault().isOfflineMode();

        return s != null
                && s.isAvailable()
                && (!offlineMode || s.getOfflineStatus() == BoundEntity.OFFLINE_STATUS_READY);
    }

    /**
     * Returns whether or not the album is available offline. For that, the album must be loaded
     * and have at least of track available offline
     *
     * @param a The album
     * @return true if the album is available offline
     */
    public static boolean isAlbumAvailableOffline(Album a) {
        if (a == null) {
            return false;
        } else if (!a.songs().hasNext()) {
            return false;
        } else {
            Iterator<String> songIt = a.songs();
            while (songIt.hasNext()) {
                Song song = ProviderAggregator.getDefault().retrieveSong(songIt.next(), a.getProvider());
                if (canPlaySong(song)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasKitKat() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean hasLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static boolean hasJellyBeanMR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static int distance(String a, String b) {
        a = a.toLowerCase();
        b = b.toLowerCase();
        // i == 0
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            // j == 0; nw = lev(i - 1, j)
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    public static float distancePercentage(String a, String b) {
        float max = Math.max(a.length(), b.length());
        return 1.0f - ((float) distance(a, b)) / max;
    }

    public static List<Song> refIteratorToSongList(Iterator<String> it, ProviderIdentifier id) {
        ProviderAggregator aggr = ProviderAggregator.getDefault();
        List<Song> output = new ArrayList<>();

        while (it.hasNext()) {
            output.add(aggr.retrieveSong(it.next(), id));
        }

        return output;
    }

    public static List<Song> refListToSongList(List<String> refList, ProviderIdentifier id) {
        ProviderAggregator aggr = ProviderAggregator.getDefault();
        List<Song> output = new ArrayList<>();

        for (String ref : refList) {
            output.add(aggr.retrieveSong(ref, id));
        }

        return output;
    }

    public static int getEnclosingCircleRadius(View v, int cx, int cy) {
        int realCenterX = cx + v.getLeft();
        int realCenterY = cy + v.getTop();
        int distanceTopLeft = (int) Math.hypot(realCenterX - v.getLeft(), realCenterY - v.getTop());
        int distanceTopRight = (int) Math.hypot(v.getRight() - realCenterX, realCenterY - v.getTop());
        int distanceBottomLeft = (int) Math.hypot(realCenterX - v.getLeft(), v.getBottom() - realCenterY);
        int distanceBotomRight = (int) Math.hypot(v.getRight() - realCenterX, v.getBottom() - realCenterY);

        int[] distances = new int[]{distanceTopLeft, distanceTopRight, distanceBottomLeft, distanceBotomRight};
        int radius = distances[0];
        for (int i = 1; i < distances.length; i++) {
            if (distances[i] > radius)
                radius = distances[i];
        }
        return radius;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void animateHeadingReveal(final View view, final long duration) {
        final int cx = view.getMeasuredWidth() / 5;
        final int cy = view.getMeasuredHeight() / 2;
        final int radius = Utils.getEnclosingCircleRadius(view, cx, cy);
        if (cx == 0 && cy == 0) {
            Log.w(TAG, "animateHidingReveal: Measured dimensions are zero");
        }
        animateCircleReveal(view, cx, cy, 0, radius, duration);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void animateHeadingReveal(final View view, final int cx, final int cy,
                                            final long duration) {
        final int radius = Utils.getEnclosingCircleRadius(view, cx, cy);
        if (cx == 0 && cy == 0) {
            Log.w(TAG, "animateHidingReveal: Measured dimensions are zero");
        }
        animateCircleReveal(view, cx, cy, 0, radius, duration);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void animateHeadingHiding(final View view, final long duration) {
        final int cx = view.getMeasuredWidth() / 5;
        final int cy = view.getMeasuredHeight() / 2;
        animateHeadingHiding(view, cx, cy, duration);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void animateHeadingHiding(final View view, final int cx, final int cy,
                                            final long duration) {
        final int radius = Utils.getEnclosingCircleRadius(view, cx, cy);
        animateCircleReveal(view, cx, cy, radius, 0, duration);
    }

    public static Animator animateCircleReveal(final View view, final int cx, final int cy,
                                               final int startRadius, final int endRadius,
                                               final long duration) {
        return animateCircleReveal(view, cx, cy, startRadius, endRadius, duration, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Animator animateCircleReveal(final View view, final int cx, final int cy,
                                            final int startRadius, final int endRadius,
                                            final long duration, final long startDelay) {
        Animator animator = ViewAnimationUtils.createCircularReveal(view, cx, cy, startRadius,
                endRadius)
                .setDuration(duration);
        animator.setStartDelay(startDelay);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (startRadius > endRadius) {
                    view.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        animator.start();

        return animator;
    }

    public static int getRandom(int maxExcluded) {
        return new Random().nextInt(maxExcluded);
    }

    public static String getAppNameByPID(Context context, int pid) {
        ActivityManager manager
                = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
            if (processInfo.pid == pid) {
                return processInfo.processName;
            }
        }
        return "";
    }
}
