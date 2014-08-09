package org.omnirom.music.app.renderscript;

import android.graphics.Bitmap;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

import org.omnirom.music.app.renderscript.ScriptC_grayscale;

/**
 * Created by Guigui on 09/08/2014.
 */
public class GrayscaleRS {

    private final RenderScript renderScript;
    private final ScriptC_grayscale grayscale;

    public GrayscaleRS(RenderScript renderScript) {
        this.renderScript = renderScript;
        this.grayscale = new ScriptC_grayscale(renderScript);
    }

    public Bitmap apply(Bitmap input) {
        Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), input.getConfig());

        int allocationExtraFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            allocationExtraFlags = Allocation.USAGE_SHARED;
        }

        Allocation inputAllocation = Allocation.createFromBitmap(renderScript,
                input, Allocation.MipmapControl.MIPMAP_NONE,
                allocationExtraFlags | Allocation.USAGE_GRAPHICS_TEXTURE | Allocation.USAGE_SCRIPT);
        Allocation outputAllocation = Allocation.createFromBitmap(renderScript, output,
                Allocation.MipmapControl.MIPMAP_NONE,
                allocationExtraFlags | Allocation.USAGE_SCRIPT);

        grayscale.forEach_grayscale(inputAllocation, outputAllocation);
        outputAllocation.copyTo(output);

        return output;
    }
}