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

package com.fastbootmobile.encore.app.ui;

import android.view.animation.Interpolator;

/**
 * Interpolator that reverses the original one
 */
public class ReverseAnimator implements Interpolator {
    private Interpolator mInterpolator;

    public ReverseAnimator(Interpolator in) {
        mInterpolator = in;
    }

    @Override
    public float getInterpolation(float input) {
        return mInterpolator.getInterpolation(1.0f - input);
    }
}
