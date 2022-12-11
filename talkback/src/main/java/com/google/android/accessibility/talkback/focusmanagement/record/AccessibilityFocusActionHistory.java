/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.record;

import android.os.SystemClock;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.VisibleForTesting;
import androidx.collection.LruCache;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A data structure to cache and query history of accessibility focus action.
 *
 * <p>Accessibility service performs {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS}
 * on nodes via framework, and receives the result {@link
 * AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} event from framework. These two processes are
 * asynchronous. Sometimes we need the information of source action when generating feedback for the
 * result event. This class is used to fill in the gap between actions and events.
 *
 * <p><strong>Usage:</strong>The service should call {@link
 * #onAccessibilityFocusAction(AccessibilityNodeInfoCompat, FocusActionInfo, long, ScreenState)}
 * every time when successfully performing focus action, and query action information with {@link
 * #matchFocusActionRecordFromEvent(AccessibilityEvent)}, {@link
 * #getLastFocusActionRecordInWindow(int, CharSequence)} and {@link
 * #getLastEditableFocusActionRecord()}.
 */
public final class AccessibilityFocusActionHistory {

  //////////////////////////////////////////////////////////////////////////////////////////
  // Restricted read-only interface, for use in actor-state pass-back to interpreters

  /** Read-only limited interface. */
  public class Reader {

    public boolean isEventFromFocusManagement(AccessibilityEvent event) {
      return matchFocusActionRecordFromEvent(event) != null;
    }

    public @Nullable FocusActionInfo getFocusActionInfoFromEvent(AccessibilityEvent event) {
      FocusActionRecord record = matchFocusActionRecordFromEvent(event);
      return (record == null) ? null : record.getExtraInfo();
    }

    public FocusActionRecord matchFocusActionRecordFromEvent(AccessibilityEvent event) {
      return AccessibilityFocusActionHistory.this.matchFocusActionRecordFromEvent(event);
    }

    public @Nullable FocusActionRecord getLastFocusActionRecordInWindow(
        int windowId, CharSequence windowTitle) {
      return AccessibilityFocusActionHistory.this.getLastFocusActionRecordInWindow(
          windowId, windowTitle);
    }

    public @Nullable FocusActionRecord getLastEditableFocusActionRecord() {
      return AccessibilityFocusActionHistory.this.getLastEditableFocusActionRecord();
    }

    public @Nullable FocusActionRecord getLastFocusActionRecord() {
      return AccessibilityFocusActionHistory.this.getLastFocusActionRecord();
    }

    public @Nullable NodePathDescription getLastFocusNodePathDescription() {
      return AccessibilityFocusActionHistory.this.getLastFocusNodePathDescription();
    }

    public boolean lastAccessibilityFocusedNodeEquals(AccessibilityNodeInfoCompat targetNode) {
      return AccessibilityFocusActionHistory.this.lastAccessibilityFocusedNodeEquals(targetNode);
    }
  }

  /** Restricted-access interface for reading focus state. */
  public final Reader reader = new Reader();

  //////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Maximum size of {@link #windowIdTitlePairToFocusActionRecordMap} */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final int MAXIMUM_WINDOW_MAP_SIZE = 10;

  /** Maximum size of {@link #focusActionRecordList} */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final int MAXIMUM_RECORD_QUEUE_SIZE = 5;

  /**
   * Maximum difference between action time and event time within which we think the event might
   * result from the action.
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final int TIMEOUT_TOLERANCE_MS = 300;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  /**
   * A queue of {@link FocusActionRecord} with maximum size of {@link #MAXIMUM_RECORD_QUEUE_SIZE}.
   * The earliest record will be dropped when the queue grows up to its maximum size.
   */
  private final Deque<FocusActionRecord> focusActionRecordList;

  /**
   * Keeps the last focus action record in each window. Map from window ID and title pair to {@link
   * FocusActionRecord} in each window with maximum size of {@link #MAXIMUM_WINDOW_MAP_SIZE}. The
   * eldest-accessed record will be removed when the map grows up to its maximum size.
   */
  private final LruCache<Pair<Integer, CharSequence>, FocusActionRecord>
      windowIdTitlePairToFocusActionRecordMap;

  /** The last {@link FocusActionRecord} on an editable node. */
  private @Nullable FocusActionRecord lastEditableFocusActionRecord;

  private @Nullable AccessibilityNodeInfoCompat cachedNodeToRestoreFocus;

  private @Nullable FocusActionInfo pendingWebFocusActionInfo = null;
  private @Nullable ScreenState pendingScreenState = null;
  private long pendingWebFocusActionTime = -1;

  //////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public AccessibilityFocusActionHistory() {
    focusActionRecordList = new ArrayDeque<>();
    windowIdTitlePairToFocusActionRecordMap =
        new LruCache<Pair<Integer, CharSequence>, FocusActionRecord>(MAXIMUM_WINDOW_MAP_SIZE);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /**
   * Registers the action information. Called immediately after an accessibility focus action is
   * performed.
   *
   * @param node Node being accessibility focused.
   * @param extraData Extra information of the action.
   * @param actionTime {@link SystemClock#uptimeMillis()} right before performing focus action.
   * @param currentScreenState Current {@link ScreenState}.
   */
  public void onAccessibilityFocusAction(
      AccessibilityNodeInfoCompat node,
      FocusActionInfo extraData,
      long actionTime,
      @Nullable ScreenState currentScreenState) {
    // FocusActionRecord handles making a copy of 'node'. We don't nee to call obtain() here.
    FocusActionRecord record = new FocusActionRecord(node, extraData, actionTime);

    // Add to the record queue.
    focusActionRecordList.offer(record);
    if (focusActionRecordList.size() > MAXIMUM_RECORD_QUEUE_SIZE) {
      // Poll the eldest order if the queue grows up its maximum size.
      focusActionRecordList.pollFirst();
    }

    final int windowId = node.getWindowId();
    final CharSequence windowTitle =
        (currentScreenState == null) ? null : currentScreenState.getWindowTitle(windowId);
    // Add to the window record map.
    windowIdTitlePairToFocusActionRecordMap.put(
        Pair.create(windowId, windowTitle), FocusActionRecord.copy(record));

    // Update the last editable node focus action.
    if (node.isEditable() || (Role.getRole(node) == Role.ROLE_EDIT_TEXT)) {
      lastEditableFocusActionRecord = FocusActionRecord.copy(record);
    }
  }

  /**
   * Registers the action information on WebView element when the new focus is known.
   *
   * @param extraData Extra information of the action.
   * @param actionTime {@link SystemClock#uptimeMillis()} right before performing focus action.
   */
  public void onPendingAccessibilityFocusActionOnWebElement(
      FocusActionInfo extraData, long actionTime, ScreenState currentScreenState) {
    pendingWebFocusActionInfo = extraData;
    pendingScreenState = currentScreenState;
    pendingWebFocusActionTime = actionTime;
  }

  public @Nullable FocusActionRecord getLastFocusActionRecordInWindow(
      int windowId, CharSequence windowTitle) {
    return windowIdTitlePairToFocusActionRecordMap.get(Pair.create(windowId, windowTitle));
  }

  public @Nullable FocusActionRecord getLastFocusActionRecordInWindow(int windowId) {
    Map<Pair<Integer, CharSequence>, FocusActionRecord> orderedMap =
        windowIdTitlePairToFocusActionRecordMap.snapshot();
    List<Pair<Integer, CharSequence>> list = new ArrayList<>(orderedMap.keySet());
    // Traverse in reversed order, from MRU to LRU.
    for (int i = list.size() - 1; i >= 0; i--) {
      Pair<Integer, CharSequence> windowIdentifier = list.get(i);
      if (windowIdentifier.first == windowId) {
        return orderedMap.get(windowIdentifier);
      }
    }
    return null;
  }

  /** Returns the last focus action. */
  public @Nullable FocusActionRecord getLastFocusActionRecord() {
    return focusActionRecordList.peekLast();
  }

  @Nullable
  NodePathDescription getLastFocusNodePathDescription() {
    @Nullable FocusActionRecord record = getLastFocusActionRecord();
    return (record == null) ? null : record.getNodePathDescription();
  }

  /** Returns the last focus action on editable node. */
  public @Nullable FocusActionRecord getLastEditableFocusActionRecord() {
    return lastEditableFocusActionRecord;
  }

  /** Returns {@code true} if last accessibility focus is the same as targetNode. */
  public boolean lastAccessibilityFocusedNodeEquals(AccessibilityNodeInfoCompat targetNode) {
    @Nullable FocusActionRecord record = getLastFocusActionRecord();
    return (record != null) && record.focusedNodeEquals(targetNode);
  }

  /**
   * Matches {@link AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} event to the source action.
   *
   * @param event The {@link AccessibilityEvent} queried.
   * @return The matched FocusActionRecord or null.
   */
  public @Nullable FocusActionRecord matchFocusActionRecordFromEvent(AccessibilityEvent event) {
    if (!AccessibilityEventUtils.eventMatchesAnyType(
        event, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
      return null;
    }
    AccessibilityNodeInfoCompat eventNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (eventNode == null) {
      return null;
    }
    long eventTime = event.getEventTime();

    tryMatchingPendingFocusAction(eventNode, eventTime);

    FocusActionRecord result = null;
    // Iterate from the newest(last) record to the eldest(first) record.
    Iterator<FocusActionRecord> iterator = focusActionRecordList.descendingIterator();
    while (iterator.hasNext()) {
      FocusActionRecord record = iterator.next();
      long timeDiff = eventTime - record.getActionTime();
      AccessibilityNodeInfoCompat recordNode = record.getFocusedNode();

      boolean timeMatches = timeDiff >= 0 && timeDiff < TIMEOUT_TOLERANCE_MS;
      boolean nodeMatches = eventNode.equals(recordNode);
      if (timeMatches && nodeMatches) {
        result = FocusActionRecord.copy(record);
        break;
      }
    }
    return result;
  }

  private void tryMatchingPendingFocusAction(
      AccessibilityNodeInfoCompat focusedNode, long focusEventTime) {
    if ((pendingWebFocusActionInfo != null)
        && (focusEventTime - pendingWebFocusActionTime < TIMEOUT_TOLERANCE_MS)
        && (focusEventTime - pendingWebFocusActionTime > 0)
        && WebInterfaceUtils.supportsWebActions(focusedNode)) {
      onAccessibilityFocusAction(
          focusedNode, pendingWebFocusActionInfo, pendingWebFocusActionTime, pendingScreenState);
      clearPendingFocusAction();
    }
  }

  /**
   * Caches current focused node especially for context menu and dialogs, which is used to restore
   * focus when context menu or dialog closes.
   */
  public void cacheNodeToRestoreFocus(AccessibilityNodeInfoCompat focus) {
    cachedNodeToRestoreFocus = focus;
  }

  /** Returns the cached node for context menu/dialog closes and clears it. */
  public @Nullable AccessibilityNodeInfoCompat popCachedNodeToRestoreFocus() {
    AccessibilityNodeInfoCompat nodeToReturn = cachedNodeToRestoreFocus;
    cachedNodeToRestoreFocus = null;
    return nodeToReturn;
  }

  /** Clears all the action records. */
  public void clear() {
    // Clear the record list.
    focusActionRecordList.clear();

    // Clear the LruCache.
    windowIdTitlePairToFocusActionRecordMap.evictAll();

    // Clear editable node focus record.
    lastEditableFocusActionRecord = null;
    cachedNodeToRestoreFocus = null;

    clearPendingFocusAction();
  }

  private void clearPendingFocusAction() {
    pendingWebFocusActionInfo = null;
    pendingWebFocusActionTime = -1;
  }

  // For verification at
  // navigateToHtmlElement_hasNoNewFocusNode_updatePendingWebAccessibilityFocusActionInfo() of
  // FocusActorTest if pendingWebFocusActionInfo is updated by
  // onPendingAccessibilityFocusActionOnWebElement()
  @VisibleForTesting
  public FocusActionInfo getPendingWebFocusActionInfo() {
    return pendingWebFocusActionInfo;
  }
}
