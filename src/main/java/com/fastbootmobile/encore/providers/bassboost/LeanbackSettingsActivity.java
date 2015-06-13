package com.fastbootmobile.encore.providers.bassboost;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.fastbootmobile.encore.app.R;

import java.util.List;

public class LeanbackSettingsActivity extends Activity {

    private static final int ACTION_FREQUENCY = 0;
    private static final int ACTION_STRENGTH = 1;
    private static final int OPTION_CHECK_SET_ID = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (null == savedInstanceState) {
            GuidedStepFragment.add(getFragmentManager(), new FirstStepFragment());
        }
    }

    private static void addAction(List<GuidedAction> actions, long id, String title, String desc) {
        actions.add(new GuidedAction.Builder()
                .id(id)
                .title(title)
                .description(desc)
                .build());
    }

    private static void addCheckedAction(List<GuidedAction> actions, int iconResId, Context context,
                                         String title, String desc, boolean checked) {
        GuidedAction.Builder guidedActionBuilder = new GuidedAction.Builder()
                .title(title)
                .description(desc)
                .checkSetId(OPTION_CHECK_SET_ID);

        if (iconResId > 0) {
            guidedActionBuilder.iconResourceId(iconResId, context);
        }

        GuidedAction guidedAction = guidedActionBuilder.build();
        guidedAction.setChecked(checked);
        actions.add(guidedAction);
    }

    public static class FirstStepFragment extends GuidedStepFragment {
        @Override
        public int onProvideTheme() {
            return R.style.Theme_OmniMusic_Leanback_GuidedStep;
        }

        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.bass_boost_settings_title);
            String breadcrumb = "";
            String description = "";
            Drawable icon = getActivity().getDrawable(R.mipmap.ic_launcher);
            return new GuidanceStylist.Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            addAction(actions, ACTION_FREQUENCY,
                    getString(R.string.center_frequency_title),
                    getString(R.string.center_frequency_summary));
            addAction(actions, ACTION_STRENGTH,
                    getString(R.string.gain_title),
                    getString(R.string.gain_summary));
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            FragmentManager fm = getFragmentManager();
            if (action.getId() == ACTION_FREQUENCY) {
                GuidedStepFragment.add(fm, new FrequencyPrefFragment());
            } else {
                GuidedStepFragment.add(fm, new StrengthPrefFragment());
            }
        }
    }

    public static class FrequencyPrefFragment extends GuidedStepFragment {
        @Override
        public int onProvideTheme() {
            return R.style.Theme_OmniMusic_Leanback_GuidedStep;
        }

        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.center_frequency_title);
            String breadcrumb = "";
            String description = getString(R.string.center_frequency_summary);
            Drawable icon = getActivity().getDrawable(R.mipmap.ic_launcher);
            return new GuidanceStylist.Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder()
                    .title("Select the frequency")
                    .multilineDescription(false)
                    .infoOnly(true)
                    .enabled(false)
                    .build());

            String[] entries = getResources().getStringArray(R.array.center_frequencies_entries);
            String[] values = getResources().getStringArray(R.array.center_frequencies_values);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            final String curValue = prefs.getString("center_frequency", "55");

            int i = 0;
            for (String entry : entries) {
                addCheckedAction(actions, 0, getActivity(), entry, null, values[i].equals(curValue));
                ++i;
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            final String[] values = getResources().getStringArray(R.array.center_frequencies_values);
            prefs.edit().putString("center_frequency", values[getSelectedActionPosition() - 1]).apply();

            getFragmentManager().popBackStack();
        }

    }

    public static class StrengthPrefFragment extends GuidedStepFragment {
        @Override
        public int onProvideTheme() {
            return R.style.Theme_OmniMusic_Leanback_GuidedStep;
        }

        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.gain_title);
            String breadcrumb = "";
            String description = getString(R.string.gain_summary);
            Drawable icon = getActivity().getDrawable(R.mipmap.ic_launcher);
            return new GuidanceStylist.Guidance(title, description, breadcrumb, icon);
        }

        @Override
        public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder()
                    .title("Select the intensity")
                    .multilineDescription(false)
                    .infoOnly(true)
                    .enabled(false)
                    .build());

            String[] entries = getResources().getStringArray(R.array.gain_entries);
            String[] values = getResources().getStringArray(R.array.gain_values);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            final String curValue = prefs.getString("gain", "0");

            int i = 0;
            for (String entry : entries) {
                addCheckedAction(actions, 0, getActivity(), entry, null, values[i].equals(curValue));
                ++i;
            }
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            final String[] values = getResources().getStringArray(R.array.gain_values);
            prefs.edit().putString("gain", values[getSelectedActionPosition() - 1]).apply();

            getFragmentManager().popBackStack();
        }


    }

}
