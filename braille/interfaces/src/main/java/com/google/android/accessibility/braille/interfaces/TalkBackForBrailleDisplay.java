/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.braille.interfaces;

import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.FocusFinder;

/** Exposes some TalkBack behavior to BrailleDisplay. */
public interface TalkBackForBrailleDisplay {
  /** Performs specific actions for screen reader. */
  boolean performAction(ScreenReaderAction action);

  /** Gets accessibility focus node. */
  AccessibilityNodeInfoCompat getAccessibilityFocusNode(boolean fallbackOnRoot);

  /** Creates {@link FocusFinder} instance. */
  FocusFinder createFocusFinder();

  /** Shows custom label dialog for the Accessibility node to add or edit a label. */
  boolean showLabelDialog(CustomLabelAction action, AccessibilityNodeInfoCompat node);

  /** Gets defined custom label. */
  CharSequence getCustomLabelText(AccessibilityNodeInfoCompat node);

  /** Returns whether {@param AccessibilityNodeInfoCompat node} needs a label. */
  boolean needsLabel(AccessibilityNodeInfoCompat node);

  /** Returns the callback of BrailleIme to BrailleDisplay. */
  @Nullable
  BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay();

  /** Screen reader actions. */
  public enum ScreenReaderAction {
    NEXT_ITEM,
    PREVIOUS_ITEM,
    NEXT_LINE,
    PREVIOUS_LINE,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    NAVIGATE_TO_TOP,
    NAVIGATE_TO_BOTTOM,
    ACTIVATE_CURRENT,
    NEXT_SECTION,
    PREVIOUS_SECTION,
    CONTROL_NEXT,
    CONTROL_PREVIOUS,
    NEXT_LIST,
    PREVIOUS_LIST,
    SCREEN_SEARCH,
    OPEN_TALKBACK_MENU,
    GLOBAL_HOME,
    GLOBAL_BACK,
    GLOBAL_RECENTS,
    GLOBAL_NOTIFICATIONS,
    GLOBAL_QUICK_SETTINGS,
    GLOBAL_ALL_APPS,
  }

  /** Custom label actions. */
  enum CustomLabelAction {
    ADD_LABEL,
    EDIT_LABEL
  }
}
