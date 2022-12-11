/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Panel holding a set of verbosity preferences. Recreated when verbosity value changes. */
public class VerbosityPrefFragment extends TalkbackBaseFragment {
  private static final String TAG = "VerbosityPrefFragment";

  // Member data
  private SharedPreferences preferences;
  private String verbosityValue; // String identifier for selected verbosity.

  public VerbosityPrefFragment() {
    super(R.xml.verbosity_preferences);
  }

  public static String getFragmentName() {
    return TAG;
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.pref_verbosity_title);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    updatePreferences();
  }

  /** Collects all verbosity-controlled preferences. */
  private ArrayList<Preference> collectDetailedPreferences() {
    ArrayList<Preference> detailedPrefs = new ArrayList<>();
    PreferenceGroup prefGroup =
        (PreferenceGroup) findPreference(R.string.pref_verbosity_category_preset_settings_key);
    if (prefGroup == null) {
      return detailedPrefs;
    }

    // For each preference... collect
    for (int p = 0; p < prefGroup.getPreferenceCount(); p++) {
      Preference preference = prefGroup.getPreference(p);
      if (preference != null) {
        detailedPrefs.add(preference);
      }
    }
    return detailedPrefs;
  }

  /**
   * Copies new key and new value to Preference list.
   *
   * @param detailedPrefs Preference list which sets up new key and new value.
   */
  private void copyVerbosityToUi(ArrayList<Preference> detailedPrefs) {
    // For each detailed preference...
    for (Preference preference : detailedPrefs) {
      // Change active key to verbosity key.
      String key = preference.getKey();

      // Restores the old key to the custom verbosity pref key, For example,
      // pref_verbosity_preset_value_high_pref_a11y_hints restores to pref_a11y_hints
      key = VerbosityPreferences.restoreToCustomVerbosityPrefKey(getResources(), key);

      // Creates new key by adding prefix verbosityVaule. For example, pref_a11y_hints becomes
      // pref_verbosity_preset_value_low_pref_a11y_hints
      String verbosityPrefKey = VerbosityPreferences.toVerbosityPrefKey(verbosityValue, key);

      preference.setKey(verbosityPrefKey);

      // Retrieve verbosity preference value and update UI element.
      if (preference instanceof SwitchPreference) {
        SwitchPreference prefSwitch = (SwitchPreference) preference;
        boolean value =
            VerbosityPreferences.getPreferenceVerbosityBool(
                preferences,
                getResources(),
                verbosityValue,
                key,
                getDefaultValueForSwitchPreferences(key));
        prefSwitch.setChecked(value);
      } else if (preference instanceof ListPreference) {
        ListPreference prefList = (ListPreference) preference;
        String value =
            VerbosityPreferences.getPreferenceVerbosityString(
                preferences, getResources(), verbosityValue, key, null);
        if (value != null) {
          prefList.setValue(value);
        }
      } else {
        LogUtils.e(TAG, "Unhandled preference type %s", preference.getClass().getSimpleName());
      }
    }
  }

  private boolean isVerbosityValueHighOrLow() {
    return TextUtils.equals(verbosityValue, getString(R.string.pref_verbosity_preset_value_high))
        || TextUtils.equals(verbosityValue, getString(R.string.pref_verbosity_preset_value_low));
  }

  private void setPreferenceDetailsEnable(ArrayList<Preference> detailedPrefs, boolean enable) {
    // For each detailed preference... set preference enable or disable.
    for (Preference preference : detailedPrefs) {
      preference.setEnabled(enable);
    }
  }

  // Returns the default value for the given key.
  private boolean getDefaultValueForSwitchPreferences(String key) {
    if (key.equals(getString(R.string.pref_screenoff_key))) {
      return getResources().getBoolean(R.bool.pref_screenoff_default);
    } else if (key.equals(getString(R.string.pref_a11y_hints_key))) {
      return getResources().getBoolean(R.bool.pref_a11y_hints_default);
    } else if (key.equals(getString(R.string.pref_intonation_key))) {
      return getResources().getBoolean(R.bool.pref_intonation_default);
    } else if (key.equals(getString(R.string.pref_phonetic_letters_key))) {
      return getResources().getBoolean(R.bool.pref_phonetic_letters_default);
    } else if (key.equals(getString(R.string.pref_speak_roles_key))) {
      return getResources().getBoolean(R.bool.pref_speak_roles_default);
    } else if (key.equals(getString(R.string.pref_speak_container_element_positions_key))) {
      return getResources().getBoolean(R.bool.pref_speak_container_element_positions_default);
    } else if (key.equals(getString(R.string.pref_verbose_scroll_announcement_key))) {
      return getResources().getBoolean(R.bool.pref_verbose_scroll_announcement_default);
    } else if (key.equals(getString(R.string.pref_punctuation_key))) {
      return getResources().getBoolean(R.bool.pref_punctuation_default);
    }
    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    String verbosityValueString =
        preferences.getString(
            getString(R.string.pref_verbosity_preset_key),
            getString(R.string.pref_verbosity_preset_value_default));
    updateFragment(verbosityValueString);

    // Attach listeners after verbosity values are copied to active, so that copying verbosity does
    // not invoke preference-change listener.
    attachPreferenceListeners();
  }

  private void attachPreferenceListeners() {
    preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  @Override
  public void onPause() {
    super.onPause();
    preferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  /** Listener for preference changes. */
  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
          if (!isAdded() || (getActivity() == null)) {
            LogUtils.w(
                TAG, "Fragment is not attached to activity, do not update verbosity setting page.");
            return;
          }
          // Handles ListPreference changed case and case where the verbosity is changed
          // using the selector and the fragment is visible.
          if (TextUtils.equals(key, getString(R.string.pref_verbosity_preset_key))) {
            String newValueString =
                preferences.getString(
                    getString(R.string.pref_verbosity_preset_key),
                    getString(R.string.pref_verbosity_preset_value_default));

            updateFragment(newValueString);

            // Announce new verbosity. If the verbosity is changed using the selector,
            // GestureController.changeVerbosity will also call this method. SpeechController
            // will then deduplicate the announcement event so only one is spoken.
            announceVerbosityChange(newValueString);
          } else if (TextUtils.equals(key, getString(R.string.pref_punctuation_key))) {
            SwitchPreference preference =
                (SwitchPreference) findPreference(R.string.pref_punctuation_key);
            boolean punctuationOn =
                prefs.getBoolean(
                    getString(R.string.pref_punctuation_key),
                    getResources().getBoolean(R.bool.pref_punctuation_default));

            if (preference != null) {
              preference.setChecked(punctuationOn);
            }
          }
        }
      };

  /** Replace preference fragment if the verbosity value has changed */
  private void updateFragment(String newValueString) {
    if (TextUtils.equals(verbosityValue, newValueString)) {
      return;
    }

    updatePreferences();
  }

  private void updatePreferences() {
    preferences = SharedPreferencesUtils.getSharedPreferences(getContext());
    verbosityValue =
        SharedPreferencesUtils.getStringPref(
            preferences,
            getResources(),
            R.string.pref_verbosity_preset_key,
            R.string.pref_verbosity_preset_value_default);

    ArrayList<Preference> detailedPrefs = collectDetailedPreferences();
    copyVerbosityToUi(detailedPrefs); // Cheap, just reading preferences.

    // Disable default verbosity preference details.
    if (isVerbosityValueHighOrLow()) {
      setPreferenceDetailsEnable(detailedPrefs, false);
    } else {
      setPreferenceDetailsEnable(detailedPrefs, true);
    }
  }

  private Preference findPreference(int keyId) {
    return getPreferenceScreen().findPreference(getString(keyId));
  }

  private void announceVerbosityChange(String newValueString) {
    Context context = getContext();
    String announcement = getVerbosityChangeAnnouncement(newValueString, context);
    if (announcement == null) {
      return;
    }
    PreferencesActivityUtils.announceText(announcement, context);
  }

  /** Map verbosity value key to verbosity name. */
  public static String verbosityValueToName(String verbosityValueKey, Context context) {
    if (verbosityValueKey.equals(context.getString(R.string.pref_verbosity_preset_value_high))) {
      return context.getString(R.string.pref_verbosity_preset_entry_high);
    } else if (verbosityValueKey.equals(
        context.getString(R.string.pref_verbosity_preset_value_custom))) {
      return context.getString(R.string.pref_verbosity_preset_entry_custom);
    } else if (verbosityValueKey.equals(
        context.getString(R.string.pref_verbosity_preset_value_low))) {
      return context.getString(R.string.pref_verbosity_preset_entry_low);
    } else {
      return null;
    }
  }

  /** Returns announcement for the change of verbosity. */
  public static @Nullable String getVerbosityChangeAnnouncement(
      String verbosityValueKey, Context context) {
    String name = verbosityValueToName(verbosityValueKey, context);
    return TextUtils.isEmpty(name)
        ? null
        : String.format(
            context.getString(R.string.pref_verbosity_preset_change),
            verbosityValueToName(verbosityValueKey, context));
  }
}
