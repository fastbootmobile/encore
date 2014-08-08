package org.omnirom.music.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlend;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Utilities
 */
public class Utils {

    private static final Map<String, Bitmap> mBitmapQueue = new HashMap<String, Bitmap>();

    /**
     * Calculates and return the action bar height based on the current theme
     *
     * @param theme The active theme
     * @param res   The resources context
     * @return The height of the action bar, in pixels
     */
    public static int getActionBarHeight(Resources.Theme theme, Resources res) {
        int actionBarHeight = 0;
        TypedValue tv = new TypedValue();
        if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight += TypedValue.complexToDimensionPixelSize(tv.data, res.getDisplayMetrics());
        }

        // As we are a "fullscreen" activity, the actionbar is also the statusbar
        actionBarHeight += getStatusBarHeight(res);

        return actionBarHeight;
    }

    /**
     * @param res The resources context
     * @return The height of the status bar, in pixels
     */
    public static int getStatusBarHeight(Resources res) {
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId);
        } else {
            return 0;
        }
    }

    /**
     * Blurs and dims (darken) a provided bitmap.
     * Note that this method recreates and reallocates RenderScript data, so it is not a good idea
     * to use it where performance is critical.
     *
     * @param context The application context
     * @param inBmp The input bitmap
     * @param radius The blur radius, max 25
     * @return A blurred and dimmed copy of the input bitmap
     */
    public static Bitmap blurAndDim(Context context, Bitmap inBmp, float radius) {
        if (inBmp == null) {
            throw new IllegalArgumentException("blurAndDim: The input bitmap is null!");
        }

        // RenderScript intrinsics were added in Android 4.2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            RenderScript renderScript = RenderScript.create(context);
            ScriptIntrinsicBlur intrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));

            final int scaledW = inBmp.getWidth() / 2;
            final int scaledH = inBmp.getHeight() / 2;

            Allocation input = Allocation.createFromBitmap(renderScript,
                    Bitmap.createScaledBitmap(inBmp, scaledW, scaledH, false));
            Allocation output = Allocation.createTyped(renderScript, input.getType());

            intrinsicBlur.setInput(input);
            intrinsicBlur.setRadius(radius);

            intrinsicBlur.forEach(output);

            // Dim down images with a tint color
            input.destroy();
            input = Allocation.createFromBitmap(renderScript,
                    Bitmap.createScaledBitmap(Bitmap.createBitmap(new int[]{0x70000000},
                                    1, 1, Bitmap.Config.ARGB_8888),
                            scaledW, scaledH, false
                    )
            );

            ScriptIntrinsicBlend intrinsicBlend = ScriptIntrinsicBlend.create(renderScript,
                    Element.U8_4(renderScript));
            intrinsicBlend.forEachSrcOver(input, output);

            Bitmap outBmp = Bitmap.createBitmap(scaledW, scaledH, inBmp.getConfig());
            output.copyTo(outBmp);

            input.destroy();
            output.destroy();
            intrinsicBlur.destroy();
            intrinsicBlend.destroy();
            renderScript.destroy();

            return outBmp;
        } else {
            // For Android 4.1, we simply return the original bitmap
            return inBmp;
        }
    }

    /**
     * Format milliseconds into an human-readable track length.
     * Examples:
     *  - 01:48:24 for 1 hour, 48 minutes, 24 seconds
     *  - 24:02 for 24 minutes, 2 seconds
     *  - 52s for 52 seconds
     * @param timeMs The time to format, in milliseconds
     * @return A formatted string
     */
    public static String formatTrackLength(int timeMs) {
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
     * @param context The application context
     * @param res The String resource id
     */
    public static void shortToast(Context context, int res) {
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show();
    }

    /**
     * Calculates the optimal size of the text based on the text view width
     * @param textView The text view in which the text should fit
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
     * @param key The key
     * @param bmp The bitmap
     */
    public static void queueBitmap(String key, Bitmap bmp) {
        mBitmapQueue.put(key, bmp);
    }

    /**
     * Retrieve a bitmap from the store, and removes it
     * @param key The key used in queueBitmap
     * @return The bitmap associated with the key
     */
    public static Bitmap dequeueBitmap(String key) {
        Bitmap bmp = mBitmapQueue.get(key);
        mBitmapQueue.remove(key);
        return bmp;
    }

    /**
     * Animate a view expansion
     * @param v The view to animate
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
        }
        else {
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


    public static void setLargeFabOutline(View[] views) {
        setFabOutline(R.dimen.floating_button_large_size, views);
    }

    public static void setSmallFabOutline(View[] views) {
        setFabOutline(R.dimen.floating_button_small_size, views);
    }

    private static void setFabOutline(int dimenRes, View[] views) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            /* Resources res = views[0].getResources();
            int size = res.getDimensionPixelSize(dimenRes);

            Outline outline = new Outline();
            outline.setOval(0, 0, size, size);

            for (View v : views) {
                v.setOutline(outline);
            } */
        }
    }
}
