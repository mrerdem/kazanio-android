package com.creadeep.kazanio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

public class SettingsRemindersFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener{
    private Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_reminders, rootKey);
        boolean val = getPreferenceManager().getSharedPreferences().getBoolean("reminderSwitch", true);
        MultiSelectListPreference activeGames = findPreference("reminderGames");
        if (activeGames != null)
            activeGames.setVisible(val);
        ListPreference reminderTime = findPreference("reminderTime");
        if (reminderTime != null)
            reminderTime.setVisible(val);
        SwitchPreference omitSwitch = findPreference("omitExisting");
        if (omitSwitch != null)
            omitSwitch.setVisible(val);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Update activity title
        FragmentActivity fragmentActivity = getActivity();
        if (fragmentActivity != null) {
            fragmentActivity.setTitle(R.string.pref_title_draw_reminders);
        }
        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            FragmentActivity fragmentActivity = getActivity();
            if (fragmentActivity != null)
                fragmentActivity.onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("reminderSwitch")) {
            boolean val = sharedPreferences.getBoolean("reminderSwitch", true);
            MultiSelectListPreference activeGames = findPreference("reminderGames");
            if (activeGames != null)
                activeGames.setVisible(val);
            ListPreference reminderTime = findPreference("reminderTime");
            if (reminderTime != null)
                reminderTime.setVisible(val);
            SwitchPreference omitSwitch = findPreference("omitExisting");
            if (omitSwitch != null)
                omitSwitch.setVisible(val);
        }
        if (context != null) {
            AppUtils.Companion.enableReminderService(context);
        }
    }
}
