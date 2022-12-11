/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.actor.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityWindow;
import com.google.android.accessibility.utils.Filter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Caches every node in current screen */
final class ScreenNodesCache {
  private final List<AccessibilityNode> cachedNodes = new ArrayList<>();

  public ScreenNodesCache() {}

  @NonNull
  synchronized List<AccessibilityNode> getCachedNodes() {
    return cachedNodes.isEmpty() ? Collections.emptyList() : new ArrayList<>(cachedNodes);
  }

  synchronized void clearCachedNodes() {
    cachedNodes.clear();
  }

  /** Caches nodes in current window with node filter. */
  synchronized void cacheCurrentWindow(
      @Nullable AccessibilityWindow currentWindow, Filter<AccessibilityNodeInfoCompat> filter) {
    // Clears before cached.
    clearCachedNodes();

    if (currentWindow == null) {
      return;
    }

    // Caches nodes matched filter in current window.
    AccessibilityNode root = currentWindow.getRoot();
    if (root != null) {
      cachedNodes.addAll(root.getMatchingDescendantsOrRoot(filter));
    }
  }
}
