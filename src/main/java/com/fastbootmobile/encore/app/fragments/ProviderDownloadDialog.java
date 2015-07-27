/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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

package com.fastbootmobile.encore.app.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.fastbootmobile.encore.app.R;

public class ProviderDownloadDialog extends DialogFragment {
    private static final String TAG = "ProviderDownloadDialog";

    private static final String KEY_DSP = "dsp";

    private boolean mDSP;

    /**
     * Creates the fragment in the perspective of selecting a provider or DSP plugin download source
     * @param dsp True to download DSP effects instead of providers
     * @return The fragment generated
     */
    public static ProviderDownloadDialog newInstance(boolean dsp) {
        ProviderDownloadDialog fragment = new ProviderDownloadDialog();
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_DSP, dsp);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        Bundle args = getArguments();
        if (args == null) {
            throw new IllegalArgumentException("This fragment requires a DSP information");
        }

        mDSP = args.getBoolean(KEY_DSP);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final CharSequence[] choices = {
                "Google Play Store",
        };

        builder.setTitle(getString(R.string.download_from_dlg_title))
                .setItems(choices, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            if (mDSP) {
                                intent.setData(Uri.parse("market://search?q=Encore+DSP"));
                            } else {
                                intent.setData(Uri.parse("market://search?q=Encore+Plugin"));
                            }
                            getActivity().startActivity(intent);
                        }
                    }
                });
        return builder.create();
    }
}