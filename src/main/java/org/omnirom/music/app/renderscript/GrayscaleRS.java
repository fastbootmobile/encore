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

package org.omnirom.music.app.renderscript;

import android.graphics.Bitmap;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

/**
 * Class allowing a quick and optimized grayscaling of a Bitmap
 */
public class GrayscaleRS {

    private final RenderScript mRenderScript;
    private final ScriptC_grayscale mGrayscale;
    private Allocation mInput;
    private Allocation mOutput;

    /**
     * Default constructor
     * @param renderScript The RenderScript context to use
     */
    public GrayscaleRS(RenderScript renderScript) {
        mRenderScript = renderScript;
        mGrayscale = new ScriptC_grayscale(renderScript);
    }

    /**
     * Grayscales the provided bitmap
     * @param input The input bitmap
     * @return A grayscaled bitmap
     */
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