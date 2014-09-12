package org.omnirom.music.app.renderscript;

import android.graphics.Bitmap;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

/**
 * Created by Guigui on 09/08/2014.
 */
public class GrayscaleRS {

    private final RenderScript mRenderScript;
    private final ScriptC_grayscale mGrayscale;
    private Allocation mInput;
    private Allocation mOutput;

    public GrayscaleRS(RenderScript renderScript) {
        mRenderScript = renderScript;
        mGrayscale = new ScriptC_grayscale(renderScript);
    }

    public synchronized Bitmap apply(Bitmap input) {
        if (input == null) {
            return null;
        }

        Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), input.getConfig());

        int allocationExtraFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            allocationExtraFlags = Allocation.USAGE_SHARED;
        }

        mInput = Allocation.createFromBitmap(mRenderScript,
                input, Allocation.MipmapControl.MIPMAP_NONE,
                allocationExtraFlags | Allocation.USAGE_SCRIPT);
        mOutput = Allocation.createFromBitmap(mRenderScript, output,
                Allocation.MipmapControl.MIPMAP_NONE,
                allocationExtraFlags | Allocation.USAGE_SCRIPT);

        mGrayscale.forEach_grayscale(mInput, mOutput);
        mOutput.copyTo(output);

        return output;
    }
}