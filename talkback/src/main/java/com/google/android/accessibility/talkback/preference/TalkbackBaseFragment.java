/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.preference;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;

/** Panel holding a set of base fragment for preferences. */
public class TalkbackBaseFragment extends PreferenceFragmentCompat {
  private static final int INVALID_VALUE = -1;

  private final int xmlResId;

  public TalkbackBaseFragment() {
    xmlResId = INVALID_VALUE;
  }

  public TalkbackBaseFragment(int xmlResId) {
    this.xmlResId = xmlResId;
  }

  /** Preferences managed by this activity. */
  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    if (xmlResId != INVALID_VALUE) {
      PreferenceSettingsUtils.addPreferencesFromResource(this, xmlResId);
      if (getActivity() == null) {
        return;
      }
      TalkBackPreferenceFilter talkBackPreferenceFilter =
          new TalkBackPreferenceFilter(getActivity().getApplicationContext());
      talkBackPreferenceFilter.filterPreferences(getPreferenceScreen());
    }
  }

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    if (preference instanceof WearListPreference) {
      PreferenceDialogFragmentCompat dialogFragment =
          ((WearListPreference) preference).createDialogFragment();
      dialogFragment.setTargetFragment(this, 0);
      dialogFragment.show(getParentFragmentManager(), preference.getKey());
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }
}
