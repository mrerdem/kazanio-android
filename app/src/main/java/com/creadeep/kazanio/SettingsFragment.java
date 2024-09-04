package com.creadeep.kazanio;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener{
    Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        // Enable/disable items based on user's current preference
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        ListPreference mpTicketTypePreference = getPreferenceManager().findPreference("defaultMPTicketType");
        ListPreference rowNumPreference = getPreferenceManager().findPreference("defaultRowNumber");
        if (mpTicketTypePreference != null && rowNumPreference != null && sharedPreferences != null) {
            String s = sharedPreferences.getString("defaultGameType", "2");
            if (s != null && s.equals("1")) {
                mpTicketTypePreference.setVisible(true);
                rowNumPreference.setVisible(false);
            } else {
                mpTicketTypePreference.setVisible(false);
                rowNumPreference.setVisible(true);
            }
        }
        // Set listener to update enable disable items if needed after preference change
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        // Update activity title (required after returning from secondary preference screen)
        FragmentActivity fragmentActivity = getActivity();
        if (fragmentActivity != null) {
            fragmentActivity.setTitle(R.string.action_settings);
        }
        super.onResume();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Enable/disable items based on user's current preference
        if (key.equals("defaultGameType")) {
            ListPreference mpTicketTypePreference = getPreferenceManager().findPreference("defaultMPTicketType");
            ListPreference rowNumPreference = getPreferenceManager().findPreference("defaultRowNumber");
            if (mpTicketTypePreference != null && rowNumPreference != null && sharedPreferences != null) {
                String s = sharedPreferences.getString(key, "2");
                if (s != null && s.equals("1")) {
                    mpTicketTypePreference.setVisible(true);
                    rowNumPreference.setVisible(false);
                } else {
                    mpTicketTypePreference.setVisible(false);
                    rowNumPreference.setVisible(true);
                }
            }
        }
        else if (key.equals("switchScreenAwake")) {
            // Respect screen awake preference
            Activity activity = getActivity();
            if (activity != null) {
                Window window = getActivity().getWindow();
                if (window != null) {
                    if (sharedPreferences.getBoolean("switchScreenAwake", true))
                        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //Don't let screen turn off
                    else
                        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        }
        else if (key.equals("exactTime")) {
            if (context != null) {
                AppUtils.Companion.enableReminderService(context);
            }
        }
    }
}
