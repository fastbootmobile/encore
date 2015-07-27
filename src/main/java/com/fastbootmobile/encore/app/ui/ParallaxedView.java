/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Nir Hartmann
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.fastbootmobile.encore.app.ui;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;

import java.lang.ref.WeakReference;

public abstract class ParallaxedView {
    static public boolean isAPI11 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    protected WeakReference<View> view;
    protected int lastOffset;

    abstract protected void translatePreICS(View view, float offset);

    public ParallaxedView(View view) {
        this.lastOffset = 0;
        this.view = new WeakReference<>(view);
    }

    public boolean is(View v) {
        return (v != null && view != null && view.get() != null && view.get().equals(v));
    }

    @SuppressLint("NewApi")
    public void setOffset(float offset) {
        View view = this.view.get();
        if (view != null)
            if (isAPI11) {
                view.setTranslationY(offset);
            } else {
                translatePreICS(view, offset);
            }
    }

    public void setView(View view) {
        this.view = new WeakReference<>(view);
    }
}