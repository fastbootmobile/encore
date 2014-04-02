package org.omnirom.music.app;

import android.content.res.Resources;
import android.util.TypedValue;

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
        int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            actionBarHeight += res.getDimensionPixelSize(resourceId);
        }

        return actionBarHeight;
    }
}
