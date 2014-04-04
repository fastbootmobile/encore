package org.omnirom.music.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlend;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.TypedValue;

import java.util.concurrent.TimeUnit;

/**
 * Utilities
 */
public class Utils {

    /**
     * Calculates and return the action bar height based on the current theme
     * @param theme The active theme
     * @param res The resources context
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

    public static int getStatusBarHeight(Resources res) {
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId);
        } else {
            return 0;
        }
    }

    public static Bitmap blurAndDim(Context context, Bitmap inBmp, float radius) {
        if (inBmp == null) {
            throw new IllegalArgumentException("blurAndDim: The input bitmap is null!");
        }

        RenderScript renderScript = RenderScript.create(context);
        ScriptIntrinsicBlur intrinsicBlur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));

        Allocation input = Allocation.createFromBitmap(renderScript, inBmp);
        Allocation output = Allocation.createTyped(renderScript, input.getType());

        intrinsicBlur.setInput(input);
        intrinsicBlur.setRadius(radius);

        intrinsicBlur.forEach(output);

        // Dim down images with a tint color
        input = Allocation.createFromBitmap(renderScript,
                Bitmap.createScaledBitmap(Bitmap.createBitmap(new int[]{0x70000000},
                                1, 1, Bitmap.Config.ARGB_8888),
                        inBmp.getWidth(),
                        inBmp.getHeight(), false));

        ScriptIntrinsicBlend intrinsicBlend = ScriptIntrinsicBlend.create(renderScript,
                Element.U8_4(renderScript));
        intrinsicBlend.forEachSrcOver(input, output);

        Bitmap outBmp = Bitmap.createBitmap(inBmp.getWidth(), inBmp.getHeight(), inBmp.getConfig());
        output.copyTo(outBmp);

        return outBmp;
    }

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
}
