/*
 * Copyright (C) 2021 Google Inc.
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

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.ListPreference;

/**
 * A ListPreference for wear devices which shows a dialog with a large margin and ellipsized title
 * to prevent the title is cropped on the rounded watch.
 */
public class WearListPreference extends ListPreference {

  public WearListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public WearListPreference(Context context) {
    super(context);
  }

  public WearListPreferenceDialogFragmentCompat createDialogFragment() {
    return WearListPreferenceDialogFragmentCompat.create(this);
  }
}
