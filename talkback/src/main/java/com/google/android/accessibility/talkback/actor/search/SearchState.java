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

import android.text.TextUtils;
import com.google.android.accessibility.talkback.actor.search.StringMatcher.MatchResult;
import com.google.android.accessibility.utils.AccessibilityNode;
import java.util.ArrayList;
import java.util.List;

/** Define necessary data for screen search UI to display. */
final class SearchState {
  /**
   * Data class to hold the matched {@code AccessibilityNode} and the {@code MatchResult} containing
   * the match index information.
   */
  static class MatchedNodeInfo {
    // TODO: [Screen Search] Enables AutoValue for screen search when AOSP verified to
    // allow using AutoValue

    /** Node containing the matched info. */
    private AccessibilityNode node;

    private List<MatchResult> matchResults;

    /**
     * Creates a {@code MatchedNodeInfo}.
     *
     * @param node node containing the matched info.
     * @param matchResults containing the {@code MatchResult} to indicate the keyword match info
     *     presented in the {@code node.getNodeText()}
     */
    public MatchedNodeInfo(AccessibilityNode node, List<MatchResult> matchResults) {
      this.node = node;
      this.matchResults = new ArrayList<>(matchResults);
    }

    /** Gets the {@code AccessibilityNode} that containing the matched content. */
    public AccessibilityNode node() {
      return node;
    }

    public List<MatchResult> matchResults() {
      return new ArrayList<>(matchResults);
    }

    public CharSequence getNodeText() {
      if (node() != null) {
        return node().getNodeText();
      }

      return null;
    }

    /**
     * Checks if the {@code MatchedNodeInfo} has valid {@code AccessibilityNode} and {@code
     * MatchResult}.
     */
    boolean hasMatchedResult() {
      return node != null && !TextUtils.isEmpty(node.getNodeText()) && !matchResults.isEmpty();
    }
  }

  private List<MatchedNodeInfo> result = new ArrayList<>();

  public void addResult(MatchedNodeInfo matchedNodeInfo) {
    if (matchedNodeInfo == null) {
      return;
    }
    result.add(matchedNodeInfo);
  }

  public void clear() {
    result.clear();
  }

  public MatchedNodeInfo getResult(int index) {
    return result.get(index);
  }

  public List<MatchedNodeInfo> getResults() {
    // Do not return copied list to make it immutable since we need to purify the list while
    // extracting to adapter.
    return result;
  }
}
